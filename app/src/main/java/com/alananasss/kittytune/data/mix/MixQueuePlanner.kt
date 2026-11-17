package com.alananasss.kittytune.data.mix

import android.content.Context
import com.alananasss.kittytune.audio.automix.AutomixManager
import com.alananasss.kittytune.audio.automix.BeatAnalysisPriority
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.sqrt

// Playlist-level Mix Mode: per-track BPM/Camelot profiles and the greedy "smart mix sort".
//
// Two pieces live here:
//
//  - [MixPlaylistAnalyzer] - turns the beat/key analysis [AutomixManager] already produces into
//    a small display profile (`124 BPM • 8A`) per track, with a cold [Flow] the playlist screen
//    can collect per row: read the cache, fire a LOOKAHEAD analysis when empty, re-emit when the
//    analysis lands. No polling, no second analyzer.
//  - [MixQueuePlanner] - the ordering arithmetic. A greedy nearest-neighbour walk over the
//    playlist that minimizes the Euclidean distance in (tempo, Camelot wheel) space, so the
//    queue plays like a DJ set: every step is a same-number, ±1-number or relative-major/minor
//    move where possible, and the tempo drifts by a couple of BPM at a time instead of jumping.
object MixPlaylistAnalyzer {

    // What a list row shows and sorts by.
    data class MixTrackProfile(
        val songId: String,
        // Rounded BPM, as DJs say it. 0 when unknown.
        val bpm: Int,
        // Camelot code ("8A"/"11B") or null when the key is not (yet) known.
        val camelot: String?,
        // 0..1 energy from the beat analysis; null when not (yet) analyzed.
        val energy: Float? = null,
    ) {
        val hasData: Boolean get() = bpm > 0
    }

    // How a row's key sits against the track playing right now - the badge's colour.
    enum class MixKeyFit { SAME, NEIGHBOUR, CLASH, UNKNOWN }

    // Compatibility of [trackKey] against the currently playing [currentKey] by the Camelot
    // rules: same number (any letter - a relative major/minor holds the same notes) is SAME,
    // one step around the wheel is NEIGHBOUR, anything else CLASH. Unknown on either side is
    // UNKNOWN and renders neutral.
    fun keyFit(trackKey: String?, currentKey: String?): MixKeyFit {
        if (trackKey == null || currentKey == null) return MixKeyFit.UNKNOWN
        return when (com.alananasss.kittytune.audio.automix.KeyHarmony.between(currentKey, trackKey)) {
            com.alananasss.kittytune.audio.automix.KeyHarmony.Fit.SAME -> MixKeyFit.SAME
            com.alananasss.kittytune.audio.automix.KeyHarmony.Fit.NEIGHBOUR,
            com.alananasss.kittytune.audio.automix.KeyHarmony.Fit.DIAGONAL -> MixKeyFit.NEIGHBOUR
            else -> MixKeyFit.CLASH
        }
    }

    // Cold flow of a track's profile: the cached value (possibly null) first, then a LOOKAHEAD
    // analysis if there is nothing yet, then a re-read every time any beat analysis completes.
    // Collected per row by the playlist screen; cancellation on dispose is the teardown.
    fun profileFlow(context: Context, track: Track): Flow<MixTrackProfile?> = flow {
        val db = AppDatabase.getDatabase(context)
        val songId = track.id.toString()

        suspend fun read(): MixTrackProfile? = withContext(Dispatchers.IO) {
            val info = runCatching { db.beatInfoDao().getBeatInfo(songId) }.getOrNull()
                ?: return@withContext null
            if (info.bpm <= 0f) null
            else MixTrackProfile(
                songId = songId,
                bpm = info.bpm.toInt(),
                camelot = AutomixManager.camelotCode(info.keyPitchClass, info.keyIsMinor),
                energy = info.energy,
            )
        }

        val cached = read()
        emit(cached)
        if (cached == null) {
            // Row-scoped, low priority: the player's own IMMEDIATE analyses must not queue
            // behind a playlist view, and a LOOKAHEAD that loses the race costs nothing.
            AutomixManager.maybeAnalyzeBeat(track, BeatAnalysisPriority.LOOKAHEAD)
        }
        AutomixManager.onBeatAnalysisCompleted.collect { completedId ->
            if (completedId == songId) emit(read())
        }
    }.distinctUntilChanged().flowOn(Dispatchers.IO)

    // One-shot batch read for the smart sort: whatever is already known, no analysis triggered
    // (the flow above warms the cache while Mix Mode is on; sorting uses what exists).
    suspend fun profilesFor(context: Context, tracks: List<Track>): Map<Long, MixTrackProfile?> =
        withContext(Dispatchers.IO) {
            val db = AppDatabase.getDatabase(context)
            val ids = tracks.map { it.id.toString() }
            val infos = runCatching { db.beatInfoDao().getBeatInfoForSongs(ids) }.getOrDefault(emptyList())
            val byId = infos.associateBy { it.songId }
            tracks.associate { track ->
                val info = byId[track.id.toString()]
                val profile = if (info != null && info.bpm > 0f) {
                    MixTrackProfile(
                        songId = info.songId,
                        bpm = info.bpm.toInt(),
                        camelot = AutomixManager.camelotCode(info.keyPitchClass, info.keyIsMinor),
                        energy = info.energy,
                    )
                } else null
                track.id to profile
            }
        }
}

// The ordering arithmetic of the "smart mix sorting" feature.
//
// Distance between two tracks is Euclidean in a two-dimensional space:
//
//  - **tempo axis**: |bpmA − bpmB| normalized by the mean of the two (so 120->124 and 160->164
//    cost the same - DJs hear *relative* drift);
//  - **harmony axis**: the Camelot wheel cost, where the moves a DJ actually makes are free:
//    same number (A or B - the relative major/minor swap), ±1 number, and one more step
//    (the "energy boost"). Anything further pays proportionally to its wheel distance.
//
// The sort is a greedy nearest-neighbour walk starting from the first track (the one currently
// playing stays where it is); every next slot takes the remaining track closest to the previous
// one. Greedy is O(n²) and never optimal, but for playlist-sized n the 2-opt-style improvements
// buy nothing a listener can hear, and greedy has the property that matters: it is stable and
// predictable - the same playlist always orders the same way.
object MixQueuePlanner {

    // Weight of each axis. Harmony weighs double: a key clash is worse than a small drift.
    const val WEIGHT_TEMPO = 1.0f
    const val WEIGHT_HARMONY = 2.0f

    // Weight of the energy axis: a mood swing matters, but less than a key clash.
    const val WEIGHT_ENERGY = 0.5f

    // The neutral cost when a profile is missing, between a neighbour (0.5) and a far move (1+).
    const val UNKNOWN_HARMONY_COST = 1.0f
    const val UNKNOWN_TEMPO_COST = 0.08f
    const val UNKNOWN_ENERGY_COST = 0.25f

    // Camelot wheel cost of A->B: 0 same number (any letter), 0.5 one step, 0.7 the +2
    // same-letter "energy boost", 0.9 the +7 semitone "super boost" diagonal, then
    // 1 + wheel/12 for anything further. Unknown codes pay [UNKNOWN_HARMONY_COST].
    fun camelotStepDistance(a: String?, b: String?): Float {
        if (a == null || b == null) return UNKNOWN_HARMONY_COST
        val pa = parseCamelot(a) ?: return UNKNOWN_HARMONY_COST
        val pb = parseCamelot(b) ?: return UNKNOWN_HARMONY_COST
        val diff = abs(pa.first - pb.first)
        val wheel = minOf(diff, 12 - diff)
        val sameLetter = pa.second == pb.second
        return when {
            wheel == 0 -> 0f           // same digit: A->B relative swap included
            wheel == 1 -> 0.5f         // ±1 digit: the classic harmonic move (either letter)
            sameLetter && wheel == 2 -> 0.7f   // +2 semitones, same letter: the energy boost
            else -> 1f + wheel / 12f   // further around the wheel, worse the further it is
        }
    }

    private fun parseCamelot(code: String): Pair<Int, Char>? {
        val clean = code.trim().uppercase()
        if (clean.length < 2) return null
        val number = clean.dropLast(1).toIntOrNull() ?: return null
        if (number !in 1..12) return null
        val letter = clean.last()
        return if (letter == 'A' || letter == 'B') number to letter else null
    }

    // Tempo cost: relative drift between the two BPMs (0 when either is unknown).
    //
    // Octave-aware: 140 vs 70 BPM is a mixable pair (the decks lock at 2×/½× tempo), not a
    // 67 % "distance". The cost is the best of the direct and the 2×/½× fold comparisons -
    // matching what a DJ actually does with the pitch fader.
    fun tempoDistance(bpmA: Int, bpmB: Int): Float {
        if (bpmA <= 0 || bpmB <= 0) return UNKNOWN_TEMPO_COST
        fun drift(x: Float, y: Float): Float {
            val mean = (x + y) / 2f
            return abs(x - y) / mean
        }
        val a = bpmA.toFloat()
        val b = bpmB.toFloat()
        return minOf(
            drift(a, b),
            drift(a * 2f, b), // A folded up: 70 BPM track matched at 140
            drift(a, b * 2f),  // B folded up
        )
    }

    // Energy cost: |energy(A) − energy(B)|, 0..1; neutral when unknown.
    fun energyDistance(energyA: Float?, energyB: Float?): Float {
        if (energyA == null && energyB == null) return 0f
        if (energyA == null || energyB == null) return UNKNOWN_ENERGY_COST
        return abs(energyA - energyB).coerceIn(0f, 1f)
    }

    // Euclidean mix distance between two profiles, either possibly null (unknown costs).
    fun mixDistance(from: MixPlaylistAnalyzer.MixTrackProfile?, to: MixPlaylistAnalyzer.MixTrackProfile?): Float {
        if (from === to && from != null) return 0f
        val tempo = if (from != null && to != null) tempoDistance(from.bpm, to.bpm) else UNKNOWN_TEMPO_COST
        val harmony = if (from != null && to != null) camelotStepDistance(from.camelot, to.camelot) else UNKNOWN_HARMONY_COST
        val energy = if (from != null && to != null && from.energy == null && to.energy == null) 0f else energyDistance(from?.energy, to?.energy)
        return sqrt(WEIGHT_TEMPO * WEIGHT_TEMPO * tempo * tempo +
            WEIGHT_HARMONY * WEIGHT_HARMONY * harmony * harmony +
            WEIGHT_ENERGY * WEIGHT_ENERGY * energy * energy)
    }

    // Greedy nearest-neighbour order. The first element keeps its position (it is what is
    // playing, or what the listener put first); every subsequent slot takes the remaining
    // track closest to the previously chosen one. Unknown profiles never block the walk -
    // they just carry the neutral cost, so analyzed tracks are preferred neighbours.

    // Recalculates transitions for altered adjacent pairs after tracks order changes in Mix mode.
    // Only recalculates automatic transitions, leaving manual Custom ones intact.
    fun recomputeTransitionsForReorderedTracks(
        context: Context,
        playlistKey: String?,
        tracks: List<Track>,
        profiles: Map<Long, MixPlaylistAnalyzer.MixTrackProfile?>,
    ) {
        if (tracks.size < 2) return
        for (i in 0 until tracks.lastIndex) {
            val trackA = tracks[i]
            val trackB = tracks[i + 1]
            val existingPlan = com.alananasss.kittytune.audio.automix.TransitionStore.load(context, playlistKey, trackA.id, trackB.id)
            if (existingPlan == null || existingPlan.isAuto) {
                val profA = profiles[trackA.id]
                val profB = profiles[trackB.id]
                val outBpm = profA?.bpm?.toFloat() ?: 0f
                val inBpm = profB?.bpm?.toFloat() ?: 0f
                val basePlan = existingPlan ?: com.alananasss.kittytune.audio.automix.TransitionPlan()
                val resolved = basePlan.resolveAuto(outBpm, inBpm, profA?.camelot, profB?.camelot)
                com.alananasss.kittytune.audio.automix.TransitionStore.save(context, playlistKey, trackA.id, trackB.id, resolved)
            }
        }
    }

    fun <T> greedyMixOrder(
        items: List<T>,
        profileOf: (T) -> MixPlaylistAnalyzer.MixTrackProfile?,
    ): List<T> {
        if (items.size <= 2) return items
        val remaining = items.drop(1).toMutableList()
        val ordered = ArrayList<T>(items.size)
        ordered.add(items.first())
        var currentProfile = profileOf(items.first())
        while (remaining.isNotEmpty()) {
            var bestIndex = 0
            var bestCost = Float.MAX_VALUE
            var bestProfile: MixPlaylistAnalyzer.MixTrackProfile? = null
            for (i in remaining.indices) {
                val candidateProfile = profileOf(remaining[i])
                val cost = mixDistance(currentProfile, candidateProfile)
                if (cost < bestCost) {
                    bestCost = cost
                    bestIndex = i
                    bestProfile = candidateProfile
                }
            }
            val chosen = remaining.removeAt(bestIndex)
            ordered.add(chosen)
            currentProfile = bestProfile
        }
        return ordered
    }
}
