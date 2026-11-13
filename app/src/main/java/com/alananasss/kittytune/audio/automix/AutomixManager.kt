package com.alananasss.kittytune.audio.automix

import android.content.Context
import android.net.Uri
import android.util.Log
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.BeatInfoEntity
import com.alananasss.kittytune.data.local.gridTrust
import com.alananasss.kittytune.data.local.trustedAnchorMs
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.pow

object AutomixManager {

    private const val TAG = "AutomixManager"

    /** A blend this long is all a clashing pair can hold: longer and the seam is two songs at once (PR #77). */
    private const val SHORT_BLEND_MS = 6_000L

    /** An energy gap this wide shortens the blend: the pair is a mood change, not a weave. */
    private const val BIG_ENERGY_GAP = 0.35f

    /** A mix-out point further than this from the end is from another upload and not believed. */
    private const val MAX_BELIEVABLE_OUTRO_MS = 60_000L

    // The key's name, as musicians say it: pitch class 9 minor is "A min".
    fun keyName(pitchClass: Int?, isMinor: Boolean?): String? {
        if (pitchClass == null || isMinor == null) return null
        val names = arrayOf("C", "C♯", "D", "D♯", "E", "F", "F♯", "G", "G♯", "A", "A♯", "B")
        val idx = (pitchClass % 12 + 12) % 12
        val name = names.getOrNull(idx) ?: return null
        return if (isMinor) "$name min" else "$name Maj"
    }

    // The key's Camelot notation, as DJs mix by it: pitch class 9 minor (A min) is "8A".
    fun camelotCode(pitchClass: Int?, isMinor: Boolean?): String? {
        if (pitchClass == null || isMinor == null) return null
        val idx = (pitchClass % 12 + 12) % 12
        val majorCamelot = arrayOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
        val minorCamelot = arrayOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")
        return if (isMinor) minorCamelot.getOrNull(idx) else majorCamelot.getOrNull(idx)
    }

    private data class BeatAnalysisHandle(val priority: BeatAnalysisPriority, val job: Job)
    private val beatAnalysisJobs = ConcurrentHashMap<String, BeatAnalysisHandle>()
    private val immediateAnalysisMutex = Mutex()
    private val lookaheadAnalysisMutex = Mutex()
    private val failedSongIds = object : java.util.LinkedHashMap<String, Long>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>?): Boolean = size > 128
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Dedicated background-priority threads for beat/key analysis (two: one per analysis mutex).
    // The old code called Process.setThreadPriority() on whichever Dispatchers.IO thread the
    // coroutine started on and "restored" it in finally - but a coroutine can resume on a
    // different thread after a suspension, so the BACKGROUND hint leaked onto shared IO threads
    // and the DEFAULT reset hit the wrong thread. Priority belongs to the thread, so own it.
    private val analysisDispatcher = Executors.newFixedThreadPool(2) { runnable ->
        Thread({
            runCatching {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            }
            runnable.run()
        }, "automix-analysis").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private var appContext: Context? = null

    private val _automixDebugInfo = MutableStateFlow<AutomixDebugInfo?>(null)
    val automixDebugInfo = _automixDebugInfo.asStateFlow()

    private val _isAutomixing = MutableStateFlow(false)
    val isAutomixing = _isAutomixing.asStateFlow()

    private val _mixBeatsLeft = MutableStateFlow<Int?>(null)
    val mixBeatsLeft = _mixBeatsLeft.asStateFlow()

    private val _mixProgress = MutableStateFlow(0f)
    val mixProgress = _mixProgress.asStateFlow()

    // The transition style the current plan will perform; the Mix panel badge reads this.
    private val _currentTransitionStyle =
        MutableStateFlow(MixTransitionPresets.DEFAULT_STYLE)
    val currentTransitionStyle = _currentTransitionStyle.asStateFlow()

    // UI override from the Mix panel's preset selector: when set, every plan uses this style
    // instead of the Auto decision. Null = Auto (the engine picks per pair).
    @Volatile
    var transitionStyleOverride: MixTransitionPresets.MixTransitionStyle? = null

    // Called by the Mix panel when the user picks a preset; null restores Auto.
    // The property above already synthesizes a JVM setter `setTransitionStyleOverride(...)`;
    // give this helper a distinct JVM name so the two declarations don't collide
    // (CONFLICTING_JVM_DECLARATIONS). Kotlin callers keep using setTransitionStyleOverride(...).
    @JvmName("applyTransitionStyleOverride")
    fun setTransitionStyleOverride(style: MixTransitionPresets.MixTransitionStyle?) {
        transitionStyleOverride = style
        // The plan is rebuilt for the next pair anyway; mirror the choice immediately so the
        // badge never contradicts what the selector shows.
        _currentTransitionStyle.value = style ?: _currentTransitionStyle.value
    }

    private val _onBeatAnalysisCompleted = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val onBeatAnalysisCompleted = _onBeatAnalysisCompleted.asSharedFlow()

    @Volatile
    var currentAutomixPlan: AutomixPlan? = null
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        synchronized(failedSongIds) { failedSongIds.clear() }
        scope.launch {
            try {
                AppDatabase.getDatabase(context).beatInfoDao().clearFailedBeatInfo()
            } catch (_: Exception) {}
        }
    }

    fun setIsAutomixing(active: Boolean) {
        _isAutomixing.value = active
    }

    fun setMixBeatsLeft(beats: Int?) {
        _mixBeatsLeft.value = beats
    }

    fun setMixProgress(progress: Float) {
        _mixProgress.value = progress.coerceIn(0f, 1f)
    }

    fun clearPlan() {
        currentAutomixPlan = null
        _mixBeatsLeft.value = null
        _mixProgress.value = 0f
        if (!_isAutomixing.value) {
            _automixDebugInfo.value = null
        }
    }

        fun cancelAllBeatAnalyses() {
        synchronized(beatAnalysisJobs) {
            beatAnalysisJobs.values.forEach { it.job.cancel() }
            beatAnalysisJobs.clear()
        }
    }

    fun maybeAnalyzeBeat(track: Track, priority: BeatAnalysisPriority = BeatAnalysisPriority.IMMEDIATE) {
        val context = appContext ?: return
        val songId = track.id.toString()

        val lastFailed = synchronized(failedSongIds) { failedSongIds[songId] }
        if (lastFailed != null && android.os.SystemClock.elapsedRealtime() - lastFailed < 45_000L) {
            return
        }

        synchronized(beatAnalysisJobs) {
            val existing = beatAnalysisJobs[songId]
            if (existing != null) {
                if (priority == BeatAnalysisPriority.IMMEDIATE && existing.priority == BeatAnalysisPriority.LOOKAHEAD) {
                    beatAnalysisJobs[songId] = existing.copy(priority = BeatAnalysisPriority.IMMEDIATE)
                }
                return
            }

            // Beat analysis decodes up to 40 s of audio at full MediaCodec speed. At foreground
            // priority it starved the two live audio threads (2 decks × ~44 processors) whenever
            // a decode window overlapped a transition - the "hissy/interrupted" playback right
            // after a skip. It therefore runs on [analysisDispatcher] (background priority).
            val job = scope.launch(analysisDispatcher) {
                val mutex = when (priority) {
                    BeatAnalysisPriority.IMMEDIATE -> immediateAnalysisMutex
                    BeatAnalysisPriority.LOOKAHEAD -> lookaheadAnalysisMutex
                }
                mutex.withLock {
                    try {
                        runBeatAnalysis(context, track, priority)
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        Log.d(TAG, "Beat analysis cancelled for $songId ($priority)")
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Beat analysis failed for $songId: ${e.message}")
                    } finally {
                        synchronized(beatAnalysisJobs) {
                            beatAnalysisJobs.remove(songId)
                        }
                    }
                }
            }
            beatAnalysisJobs[songId] = BeatAnalysisHandle(priority, job)
        }
    }

    private suspend fun runBeatAnalysis(context: Context, track: Track, priority: BeatAnalysisPriority) {
        val songId = track.id.toString()
        val db = AppDatabase.getDatabase(context)
        val existing = db.beatInfoDao().getBeatInfo(songId)
        // A row from an older analyzer carries a beat grid but no classified downbeat, so its
        // cue points cannot be phrase-quantized. Re-analyze instead of mixing off a random beat.
        if (existing != null && existing.bpm > 0f && existing.isCurrentAnalysis) {
            val currentDbg = _automixDebugInfo.value
            val isCurrent = com.alananasss.kittytune.data.MusicManager.currentTrack?.id == track.id
            if (isCurrent && (currentDbg == null || currentDbg.outBpm == null)) {
                _automixDebugInfo.value = (currentDbg ?: AutomixDebugInfo("standby")).copy(
                    outBpm = existing.bpm,
                    outConfidence = existing.confidence,
                    outMixOutMs = existing.mixOutPointMs,
                )
            }
            return
        }

        Log.d(TAG, "Beat analysis starting for $songId - ${track.title} ($priority)")
        val startedAt = android.os.SystemClock.elapsedRealtime()
        // YouTube NewPipe fallback takes ~5-6s to search/resolve + stream fetch time.
        // IMMEDIATE gets a generous budget; LOOKAHEAD also needs enough for NewPipe.
        val timeoutMs = if (priority == BeatAnalysisPriority.IMMEDIATE) 45_000L else 30_000L
        fun timedOutOrCancelled(): Boolean =
            android.os.SystemClock.elapsedRealtime() - startedAt > timeoutMs

        var result: BeatAnalyzer.Result? = null
        var dataComplete = false

        // 1. Check if track is local / downloaded
        var localPath: String? = null
        try {
            val localTrack = db.downloadDao().getTrack(track.id)
            if (localTrack != null && localTrack.localAudioPath.isNotBlank()) {
                localPath = localTrack.localAudioPath
            }
        } catch (_: Exception) {}

        val trackDurationMs = track.actualDurationMs
        if (localPath != null) {
            if (localPath.startsWith("exo_cache://")) {
                val parts = localPath.removePrefix("exo_cache://").split("::", limit = 3)
                val cachedStreamUrl = parts.getOrNull(1)
                val token = parts.getOrNull(2)
                if (!token.isNullOrEmpty()) {
                    try {
                        com.alananasss.kittytune.data.MusicManager.putDrmToken(track.id, token)
                    } catch (_: Exception) {}
                }
                if (!cachedStreamUrl.isNullOrEmpty()) {
                    Log.d(TAG, "Analyzing downloaded ExoCache stream for $songId (hasToken=${!token.isNullOrEmpty()})")
                    val fetched = BeatAnalyzer.analyzeDrmStream(
                        context = context,
                        streamUrl = cachedStreamUrl,
                        licenseAuthToken = token,
                        totalDurationMs = trackDurationMs,
                        shouldCancel = ::timedOutOrCancelled
                    )
                    result = fetched?.result
                    dataComplete = fetched?.complete ?: false
                }
            } else {
                val isContentUri = localPath.startsWith("content://")
                val fileExists = if (isContentUri) true else File(localPath).exists()
                if (fileExists) {
                    result = if (isContentUri) {
                        BeatAnalyzer.analyzeUri(context, Uri.parse(localPath), shouldCancel = ::timedOutOrCancelled)
                    } else {
                        BeatAnalyzer.analyzeFile(localPath, shouldCancel = ::timedOutOrCancelled)
                    }
                    dataComplete = true
                }
            }
        }

        // 2. If not local, resolve a stream URL for analysis.
        if (result == null && !timedOutOrCancelled()) {
            var stream: com.alananasss.kittytune.data.ResolvedStream? = null
            try {
                stream = StreamResolver.resolveStreamForBeatAnalysis(context, track)
            } catch (e: Exception) {
                Log.w(TAG, "Failed resolving stream for analysis of $songId: ${e.message}")
            }

            if (stream != null) {
                if (stream.isDrmProtected && !stream.licenseAuthToken.isNullOrEmpty()) {
                    try {
                        com.alananasss.kittytune.data.MusicManager.putDrmToken(track.id, stream.licenseAuthToken)
                    } catch (_: Exception) {}
                }
                Log.d(TAG, "Analyzing stream for $songId: ${stream.url} (drm=${stream.isDrmProtected})")
                val fetched = if (stream.isDrmProtected || stream.url.contains(".m3u8") || stream.url.contains("cenc")) {
                    BeatAnalyzer.analyzeDrmStream(
                        context = context,
                        streamUrl = stream.url,
                        licenseAuthToken = stream.licenseAuthToken,
                        totalDurationMs = trackDurationMs,
                        shouldCancel = ::timedOutOrCancelled
                    )
                } else {
                    val tempDir = File(context.cacheDir, "automix_beat_tmp").apply { mkdirs() }
                    BeatAnalyzer.analyzeStream(
                        url = stream.url,
                        tempDir = tempDir,
                        context = context,
                        licenseAuthToken = stream.licenseAuthToken,
                        totalDurationMs = trackDurationMs,
                        shouldCancel = ::timedOutOrCancelled
                    )
                }
                result = fetched?.result
                dataComplete = fetched?.complete ?: false
            } else {
                Log.d(TAG, "No stream available for $songId, marking as failed")
                dataComplete = true // treat as definitive failure so we don't keep retrying
            }
        }

        Log.d(
            TAG,
            "Beat analysis done for $songId: " +
                (result?.let { "bpm=%.1f conf=%.2f mixIn=%s mixOut=%s key=%d isMinor=%s".format(it.bpm, it.confidence, it.mixInPointMs, it.mixOutPointMs, it.keyPitchClass, it.keyIsMinor) }
                    ?: "failed (complete=$dataComplete)")
        )

        if (result == null) {
            synchronized(failedSongIds) { failedSongIds[songId] = android.os.SystemClock.elapsedRealtime() }
            return
        }

        val entity = BeatInfoEntity(
            songId = songId,
            bpm = result.bpm,
            firstBeatOffsetMs = result.firstBeatOffsetMs,
            confidence = result.confidence,
            mixInPointMs = result.mixInPointMs ?: -1L,
            mixOutPointMs = result.mixOutPointMs ?: -1L,
            keyPitchClass = result.keyPitchClass,
            keyIsMinor = result.keyIsMinor,
            downbeatOffsetMs = result.downbeatOffsetMs,
            phraseOffsetMs = result.phraseOffsetMs,
            downbeatConfidence = result.downbeatConfidence,
            phraseConfidence = result.phraseConfidence,
            energy = result.energy,
            analysisVersion = BeatInfoEntity.CURRENT_ANALYSIS_VERSION,
        )

        withContext(Dispatchers.IO) {
            db.beatInfoDao().upsert(entity)
            _onBeatAnalysisCompleted.tryEmit(songId)
        }

        val currentDbg = _automixDebugInfo.value
        val isCurrent = com.alananasss.kittytune.data.MusicManager.currentTrack?.id == track.id
        if (isCurrent && (currentDbg == null || currentDbg.outBpm == null)) {
            _automixDebugInfo.value = (currentDbg ?: AutomixDebugInfo("standby")).copy(
                outBpm = result.bpm,
                outConfidence = result.confidence,
                outMixOutMs = result.mixOutPointMs,
            )
        }
    }

    suspend fun computeAutomixPlan(
        currentTrack: Track,
        nextTrack: Track,
        currentPosition: Long,
        trackDuration: Long,
        prefs: PlayerPreferences,
        playlistKey: String? = null,
    ): AutomixPlanResult {
        val context = appContext ?: return AutomixPlanResult(null, false)
        val currentId = currentTrack.id.toString()
        val nextId = nextTrack.id.toString()

        val db = AppDatabase.getDatabase(context)
        val (outBeat, inBeat) = withContext(Dispatchers.IO) {
            db.beatInfoDao().getBeatInfo(currentId) to db.beatInfoDao().getBeatInfo(nextId)
        }

        if (outBeat == null) maybeAnalyzeBeat(currentTrack, BeatAnalysisPriority.IMMEDIATE)
        if (inBeat == null && nextId != currentId) maybeAnalyzeBeat(nextTrack, BeatAnalysisPriority.IMMEDIATE)

        val partialDebug = AutomixDebugInfo(
            status = "",
            outBpm = outBeat?.bpm,
            outConfidence = outBeat?.confidence,
            outMixOutMs = outBeat?.mixOutPointMs,
            inBpm = inBeat?.bpm,
            inConfidence = inBeat?.confidence,
            inMixInMs = inBeat?.mixInPointMs,
        )

        val isOutFailed = synchronized(failedSongIds) { failedSongIds.containsKey(currentId) } || (outBeat != null && outBeat.bpm <= 0f)
        val isInFailed = synchronized(failedSongIds) { failedSongIds.containsKey(nextId) } || (inBeat != null && inBeat.bpm <= 0f)

        if (isOutFailed || isInFailed) {
            // The previous pair's plan must not outlive it into the new pair: each failure used
            // to leave currentAutomixPlan from the PREVIOUS seam, and the ticker kept running its
            // countdown/pre-buffer against someone else's triggerTimeMs (audit E2).
            clearPlan()
            _automixDebugInfo.value = partialDebug.copy(
                status = "fallback: stream unsupported"
            )
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        if (outBeat == null || inBeat == null) {
            clearPlan()
            _automixDebugInfo.value = partialDebug.copy(
                status = "fallback: analysis pending (" +
                    (if (outBeat == null) "current" else "") +
                    (if (outBeat == null && inBeat == null) "+" else "") +
                    (if (inBeat == null) "next" else "") + ")"
            )
            return AutomixPlanResult(plan = null, pairAnalyzed = false)
        }

        if (outBeat.confidence < 0.3f || inBeat.confidence < 0.3f || outBeat.bpm <= 0f || inBeat.bpm <= 0f) {
            clearPlan()
            _automixDebugInfo.value = partialDebug.copy(status = "fallback: low confidence")
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        val periodMs = (60_000f / outBeat.bpm).toDouble()

        // Saved per-pair transition (playlist connector / Edit-transition screen)
        // The chip between two tracks stores one plan per pair. It is the most specific wish
        // the user expressed for this exact seam, so it outranks the Mix panel's global pin
        // and the Auto decision - both the style and the overlap length in the pair's bars.
        // (The store used to be write-only: playback never read it, so a saved Custom always
        // played as Auto. This lookup is the read side of that contract.)
        // Multi-key (audit E5): the plan is stored under several route keys
        // (route id, "playlist_detail:<id>", stableId) - the editor used to save under
        // playlistId while the engine looked under navigationId, so saved plans were
        // simply never found.
        val savedPairPlan = if (playlistKey != null) {
            runCatching { TransitionStore.loadAny(context, playlistKey, currentTrack.id, nextTrack.id) }.getOrNull()
        } else null
        val pairOverrideActive = savedPairPlan != null && !savedPairPlan.isAuto

        // Overlap duration: default 16 beats (4 bars), bounded 6-16s
        val overlapMode = prefs.getAutomixOverlapMode() // 0=Auto (4 bars), 1=2 bars, 2=4 bars, 3=8 bars, 4=Custom
        val baseOverlapMs = when (overlapMode) {
            PlayerPreferences.AUTOMIX_OVERLAP_2BARS -> (8 * periodMs).toLong().coerceIn(4_000L, 10_000L) // 2 bars
            PlayerPreferences.AUTOMIX_OVERLAP_4BARS -> (16 * periodMs).toLong().coerceIn(6_000L, 16_000L) // 4 bars
            PlayerPreferences.AUTOMIX_OVERLAP_8BARS -> (32 * periodMs).toLong().coerceIn(10_000L, 20_000L) // 8 bars
            PlayerPreferences.AUTOMIX_OVERLAP_CUSTOM -> prefs.getAutomixOverlapCustomSec() * 1000L
            else -> (16 * periodMs).toLong().coerceIn(6_000L, 16_000L) // Auto
        }
        var overlapMs = if (overlapMode == PlayerPreferences.AUTOMIX_OVERLAP_CUSTOM) {
            baseOverlapMs.coerceIn(1_000L, 30_000L)
        } else {
            baseOverlapMs.coerceIn(4_000L, 20_000L)
        }
        if (pairOverrideActive) {
            overlapMs = (savedPairPlan!!.bars.beats * periodMs).toLong().coerceIn(2_000L, 30_000L)
            // "No transition" (NO_TRANSITION): this seam has no transition at all -
            // the seam collapses to the minimum, the new track simply takes over.
            if (savedPairPlan.preset == TransitionPreset.NONE) {
                overlapMs = 2_000L
            }
        }

        // How the two keys fit, by the Camelot wheel (PR #77): harmonic mixing sizes the blend on it.
        val keyFit = if (prefs.getAutomixHarmonicMixEnabled()) {
            KeyHarmony.between(camelotCode(outBeat.keyPitchClass, outBeat.keyIsMinor), camelotCode(inBeat.keyPitchClass, inBeat.keyIsMinor))
        } else KeyHarmony.Fit.UNKNOWN

        // Tempo fold FIRST: Spotify-Mix style matching (octaves 2x/0.5x taken into account):
        // match only when the BPM difference is within ±10 BPM AND the resulting stretch is within
        // ±8 % (TransitionPreviewPlayer.MAX_TEMPO_STRETCH). Otherwise play both decks at their own
        // tempo (tempoRatio = 1f) and let the transition style carry the seam.
        var tempoRatio = 1f
        var tempoStretchViable = false
        if (prefs.getAutomixTempoMatchEnabled()) {
            val (canMatch, ratio) = com.alananasss.kittytune.ui.player.mix.TransitionPreviewPlayer.calculateSpotifyBpmMatch(outBeat.bpm, inBeat.bpm)
            tempoStretchViable = canMatch
            tempoRatio = if (canMatch) ratio else 1f
        }

        // Transition style (Spotify-Mix presets)
        // Priority: the Mix panel's live override > the persisted preference > the Auto
        // decision from tempo/key/energy. UNKNOWN fit is passed as null so the Auto rule
        // treats un-analyzed keys as "not a reason to slam", not as a clash.
        val prefStyleResolved = runCatching { prefs.getMixTransitionStyle() }.getOrNull()
            ?.let { name ->
                runCatching { MixTransitionPresets.MixTransitionStyle.valueOf(name) }.getOrNull()
            }
            ?: MixTransitionPresets.MixTransitionStyle.AUTO
        val userPinnedStyle = transitionStyleOverride
            ?: prefStyleResolved.takeIf { it != MixTransitionPresets.MixTransitionStyle.AUTO }
        var resolvedStyle = savedPairPlan?.toEngineStyle() ?: (userPinnedStyle ?: MixTransitionPresets.autoSelect(
            outBpm = outBeat.bpm,
            inBpm = inBeat.bpm,
            keyFit = keyFit.takeIf { it != KeyHarmony.Fit.UNKNOWN },
            energyGap = if (outBeat.energy != null && inBeat.energy != null) {
                abs(outBeat.energy - inBeat.energy)
            } else null,
        ))
        // Stretch refused (> 8 % tempo stretch (or > 10 BPM apart)): a beat-matched BLEND/FADE would hold two
        // unsynchronized grooves over 6-16 s of mud. The seam moves to styles that do not
        // need tempo sync - a filter build-and-drop (RISE) when the energy climbs, an echo
        // wash-out (DISSOLVE) when it falls. Only ever applied to the Auto decision: a
        // preset the user pinned by hand is theirs.
        if (!tempoStretchViable && userPinnedStyle == null && !pairOverrideActive) {
            resolvedStyle = when (resolvedStyle) {
                MixTransitionPresets.MixTransitionStyle.BLEND,
                MixTransitionPresets.MixTransitionStyle.FADE ->
                    if (inBeat.bpm >= outBeat.bpm) MixTransitionPresets.MixTransitionStyle.RISE
                    else MixTransitionPresets.MixTransitionStyle.DISSOLVE
                else -> resolvedStyle
            }
        }
        _currentTransitionStyle.value = resolvedStyle

        // Tempo sync: stretch the incoming deck to the playing tempo for the blend (Sonic does
        // the stretching behind playbackParameters.speed) and release it back afterwards. Only
        // when the fold above produced a usable ratio - otherwise the decks stay at their own
        // tempos and the transition style carries the seam instead.
        val tempoSyncEnabled = prefs.getAutomixTempoMatchEnabled() && tempoRatio != 1f

        // Harmonic correction: only when the keys clash (or either key is unknown) does the incoming track
        // pitch-shift to align, up to ±3 semitones; a pair that already fits is left alone (PR #77).
        var pitchRatio = 1f
        if (prefs.getAutomixHarmonicMixEnabled() && (keyFit == KeyHarmony.Fit.CLASH || keyFit == KeyHarmony.Fit.UNKNOWN)) {
            val outKeyClass = outBeat.keyPitchClass
            val inKeyClass = inBeat.keyPitchClass
            if (outKeyClass != null && inKeyClass != null) {
                val outEffective = if (outBeat.keyIsMinor == true) (outKeyClass + 3) % 12 else outKeyClass
                val inEffective = if (inBeat.keyIsMinor == true) (inKeyClass + 3) % 12 else inKeyClass
                var semitoneShift = (outEffective - inEffective) % 12
                if (semitoneShift > 6) semitoneShift -= 12
                if (semitoneShift < -6) semitoneShift += 12
                if (semitoneShift != 0 && abs(semitoneShift) <= 3) {
                    pitchRatio = 2.0.pow(semitoneShift / 12.0).toFloat()
                }
            }
        }

        // Mix panel's transition-length slider (4/8/16/32 beats) overrides the overlap mode
        // when the user moved it; 0 means "follow the automix overlap setting".
        val mixBeats = runCatching { prefs.getMixTransitionBeats() }.getOrDefault(0)
        if (!pairOverrideActive && (mixBeats == 4 || mixBeats == 8 || mixBeats == 16 || mixBeats == 32)) {
            overlapMs = (mixBeats * periodMs).toLong().coerceIn(2_000L, 30_000L)
        }

        // In Auto mode the blend follows the music: keys that fit earn a longer blend, keys that clash and a
        // big energy gap both cut it short, so the seam never asks more of the pair than it can hold.
        // When harmonic correction aligned the keys, effectiveFit is treated as SAME (PR #77).
        if (overlapMode == 0 && !pairOverrideActive) {
            val effectiveFit = if (pitchRatio != 1f) KeyHarmony.Fit.SAME else keyFit
            val energyGap = if (outBeat.energy != null && inBeat.energy != null) {
                abs(outBeat.energy - inBeat.energy)
            } else 0f
            overlapMs = when (effectiveFit) {
                KeyHarmony.Fit.SAME, KeyHarmony.Fit.NEIGHBOUR -> (overlapMs * 1.2f).toLong()
                // Diagonal / +2 / +7 moves are compatible but raise the stakes: slightly
                // shorter than a perfect fit, far longer than a clash.
                KeyHarmony.Fit.DIAGONAL, KeyHarmony.Fit.LIFT ->
                    (overlapMs * 1.1f).toLong()
                KeyHarmony.Fit.CLASH -> minOf(overlapMs, SHORT_BLEND_MS)
                else -> overlapMs
            }
            if (energyGap > BIG_ENERGY_GAP) overlapMs = (overlapMs * 0.7f).toLong()
            overlapMs = overlapMs.coerceIn(4_000L, 20_000L)
        }

        // Dynamic mix-out: start the transition where the song's body ends (outro begins)
        val latestTrigger = trackDuration - overlapMs
        val mixOut = if (prefs.getAutomixDynamicMixPointsEnabled()) {
            outBeat.mixOutPointMs?.takeIf { it > 0 && it >= trackDuration - MAX_BELIEVABLE_OUTRO_MS }
        } else null
        val effectiveTrigger = mixOut?.coerceAtMost(latestTrigger) ?: latestTrigger

        // Snap the fade start onto an 8-beat phrase boundary of the outgoing track's grid.
        // The anchor is a classified downbeat, so "phrase boundary" means an actual beat 1 -
        // quantizing against the raw beat grid lands the fade 1-3 beats off the One.
        // Half the track's trusted granularity: the fade start wants to be finer than a full
        // phrase, but never finer than the grid can actually support.
        val phraseMs = periodMs * max(2, outBeat.gridTrust.quantizeBeats / 2)
        val outAnchorMs = outBeat.trustedAnchorMs
        val anchor = max(effectiveTrigger, currentPosition + 1000)
        val k = ((anchor - outAnchorMs) / phraseMs).toLong()
        var triggerTime = (outAnchorMs + k * phraseMs).toLong()
        if (triggerTime < anchor) triggerTime = (outAnchorMs + (k + 1) * phraseMs).toLong()
        // Where the loud part ends (a mix-out point is known), the phrase boundary nearest to it is taken, not the next one
        // after it: the blend then covers the last bars of the drop going out, and does not begin in the empty tail.
        if (mixOut != null) {
            val nearest = (outAnchorMs + Math.round((anchor - outAnchorMs) / phraseMs) * phraseMs).toLong()
            if (nearest >= currentPosition + 1000) {
                // Clamped to the latest legal trigger: an upward-rounded phrase boundary
                // past trackDuration - overlapMs used to push remaining/roomMs below the
                // 3 s threshold and fail the whole plan with "trigger out of range".
                triggerTime = nearest.coerceAtMost(latestTrigger)
            }
        }

        // The connector's hand-placed anchor (the Edit-transition drag) pins where in the
        // outgoing track the seam starts - the most specific wish the user expressed for
        // this pair, so it outranks the dynamic mix-out heuristic whenever it is legal.
        // The anchor snaps to the outgoing grid's nearest phrase line (the drag is free,
        // the seam still lands on the One) and is dropped silently when it would fire in
        // the past or crowd the track's tail.
        val pairZoneStartMs = savedPairPlan?.zoneStartMs?.takeIf { it >= 0L }
        if (pairZoneStartMs != null) {
            val snappedZone = if (outBeat.bpm > 0f) {
                (outAnchorMs + Math.round((pairZoneStartMs - outAnchorMs) / phraseMs) * phraseMs).toLong()
            } else {
                pairZoneStartMs
            }
            if (snappedZone >= currentPosition + 1000 && snappedZone <= latestTrigger) {
                triggerTime = snappedZone
            }
        }

        val roomMs = trackDuration - 500 - triggerTime
        val effectiveOverlapMs = overlapMs.coerceAtMost(roomMs)
        if (effectiveOverlapMs < 3000L || triggerTime >= trackDuration - 3000) {
            _automixDebugInfo.value = partialDebug.copy(status = "fallback: trigger out of range")
            return AutomixPlanResult(plan = null, pairAnalyzed = true)
        }

        // Start offset: Auto (dynamic intro skip), Beginning (0:00), or Custom offset (snapped to downbeats)
        // Dynamic mix-in: where the incoming track starts. The offset mode decides whether that is
        // the detected intro skip, the very beginning, or a fixed offset the user set. Whichever it
        // is, the result is snapped onto the incoming track's own trusted phrase anchor rather than
        // an arbitrary beat of the grid, and the phrase length follows how far the downbeat
        // classification could actually be trusted.
        val inPeriodMs = (60_000f / inBeat.bpm).toDouble()
        val inAnchorMs = inBeat.trustedAnchorMs
        val inPhraseMs = inPeriodMs * max(2, inBeat.gridTrust.quantizeBeats / 2)
        val startOffsetMode = prefs.getAutomixStartOffsetMode()
        val incomingStart: Long = when (startOffsetMode) {
            PlayerPreferences.AUTOMIX_START_OFFSET_BEGINNING -> 0L
            PlayerPreferences.AUTOMIX_START_OFFSET_CUSTOM -> {
                val customSec = prefs.getAutomixStartOffsetCustomSec()
                val targetMs = customSec * 1000L
                if (targetMs <= 0L) {
                    0L
                } else if (inBeat.bpm > 0f) {
                    val inBarMs = inPeriodMs * 4
                    val inBars = kotlin.math.round((targetMs - inAnchorMs) / inBarMs).toLong().coerceAtLeast(0)
                    (inAnchorMs + inBars * inBarMs).toLong().coerceAtLeast(0L)
                } else {
                    targetMs
                }
            }
            else -> { // AUTOMIX_START_OFFSET_AUTO
                val rawStart = if (prefs.getAutomixDynamicMixPointsEnabled()) {
                    inBeat.mixInPointMs?.takeIf { it > 0 } ?: inAnchorMs
                } else inAnchorMs
                val inK = ceil((rawStart - inAnchorMs) / inPhraseMs).toLong().coerceAtLeast(0)
                (inAnchorMs + inK * inPhraseMs).toLong()
            }
        }
        // The connector's hand-drawn offset (the Edit-transition drag) pins where the incoming
        // deck enters, snapped to the incoming track's own bar grid - the most specific wish
        // for this seam, so it outranks the global start-offset preference (Auto included).
        val pairOffsetMs = savedPairPlan?.offsetMs?.takeIf { it > 0L }
        val offsetAdjustedStart = if (pairOffsetMs != null) {
            if (inBeat.bpm > 0f) {
                val inBarMs = inPeriodMs * 4
                val inBars = kotlin.math.round((pairOffsetMs - inAnchorMs) / inBarMs).toLong().coerceAtLeast(0)
                (inAnchorMs + inBars * inBarMs).toLong().coerceAtLeast(0L)
            } else {
                pairOffsetMs
            }
        } else {
            incomingStart
        }
        // Never start so close to the end that the track has no room to play.
        val nextDuration = nextTrack.durationMs?.takeIf { it > 5000L }
        val effectiveIncomingStart = if (nextDuration != null) {
            offsetAdjustedStart.coerceIn(0L, nextDuration - 3000L)
        } else {
            offsetAdjustedStart.coerceAtLeast(0L)
        }

        // A hand-shaped Custom seam plays its own drawn curves (volume pair, bass handover,
        // filter legs) instead of the engine style's approximation - the editor showed the
        // shape, playback performs the shape. Named presets keep their beat-quantized
        // engine textures; Auto was already resolved into one above.
        val pairExactCurves = savedPairPlan?.takeIf { it.preset == TransitionPreset.CUSTOM }

        val plan = AutomixPlan(
            currentId = currentId,
            nextId = nextId,
            triggerTimeMs = triggerTime,
            incomingStartMs = effectiveIncomingStart,
            tempoRatio = tempoRatio,
            pitchRatio = pitchRatio,
            overlapMs = effectiveOverlapMs,
            transitionStyle = resolvedStyle,
            tempoSyncEnabled = tempoSyncEnabled,
            exactCurves = pairExactCurves,
        )

        currentAutomixPlan = plan
        _automixDebugInfo.value = partialDebug.copy(
            status = "plan ready (${resolvedStyle.name.lowercase()})",
            triggerTimeMs = plan.triggerTimeMs,
            incomingStartMs = plan.incomingStartMs,
            tempoRatio = plan.tempoRatio,
            pitchRatio = plan.pitchRatio,
            overlapMs = plan.overlapMs,
        )

        return AutomixPlanResult(plan = plan, pairAnalyzed = true)
    }
}
