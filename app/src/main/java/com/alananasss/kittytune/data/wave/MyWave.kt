package com.alananasss.kittytune.data.wave

import android.content.Context
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.mix.MixEngine
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * MyWave (ported from desktop, PR #77): a personal radio that keeps playing — fresh finds interleaved with
 * familiar likes in a share that follows the mode, every batch one tap away, the queue topped up under the
 * listener's feet, and the choices remembered so skips teach it what to play less of.
 */
object MyWave {

    enum class WaveMode { BALANCED, FAVORITES, DISCOVER, POPULAR }

    private const val BATCH_SIZE = 20
    private const val QUICK_SIZE = 8

    /** How much of a batch is the familiar (liked) side, per mode. */
    private fun familiarShare(mode: WaveMode): Float = when (mode) {
        WaveMode.BALANCED -> 0.30f
        WaveMode.FAVORITES -> 0.70f
        WaveMode.DISCOVER -> 0f
        WaveMode.POPULAR -> 0.15f
    }

    suspend fun nextBatch(mode: WaveMode, exclude: Set<Long>, size: Int = BATCH_SIZE): List<Track> {
        val fresh = freshSongs(mode, exclude, size)
        val familiar = familiarSongs(exclude)
        val familiarCount = (size * familiarShare(mode)).toInt().coerceAtMost(familiar.size)
        val gap = (size - fresh.size).coerceAtLeast(0)
        val familiarUsed = familiar.shuffled().take(familiarCount + gap)
        return interleave(fresh, familiarUsed, familiarShare(mode))
    }

    /** A small first batch that starts fast: related tracks of two recent likes, else trending. */
    suspend fun quickBatch(mode: WaveMode, exclude: Set<Long>, size: Int = QUICK_SIZE): List<Track> = runCatching {
        val api = RetrofitClient.create(KittyTuneApp.instance)
        val seeds = LikeRepository.likedTracks.value.shuffled().take(2)
        val related = if (seeds.isNotEmpty()) {
            // try/catch rather than runCatching: a suspend call cannot sit inside runCatching's lambda.
            seeds.flatMap { seed ->
                try { api.getRelatedTracks(seed.id, limit = 30).collection } catch (e: Exception) { emptyList() }
            }.shuffled()
        } else {
            api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = size * 2)
                .collection.mapNotNull { it.track }.shuffled()
        }
        val welcome = related.filter { it.id !in exclude && WaveFeedback.isWelcome(it) }
        interleave(welcome, emptyList(), 0f).take(size)
    }.getOrDefault(emptyList())

    private suspend fun freshSongs(mode: WaveMode, exclude: Set<Long>, size: Int): List<Track> {
        val mixed = MixEngine.mix(MixEngine.Recipe.MyTaste, size = size * 3)
        val candidates = when (mixed) {
            is MixEngine.Result.Mixed -> mixed.tracks
            else -> {
                // No history yet, or the mixer found nothing: what everyone plays carries the wave.
                val api = RetrofitClient.create(KittyTuneApp.instance)
                runCatching {
                    api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = size * 2)
                        .collection.mapNotNull { it.track }
                }.getOrDefault(emptyList())
            }
        }
        var welcome = candidates.filter { it.id !in exclude && WaveFeedback.isWelcome(it) }
        if (mode == WaveMode.POPULAR) welcome = welcome.sortedByDescending { it.playbackCount }
        return welcome.sortedByDescending { WaveFeedback.artistScore(it).coerceAtLeast(0f) }
    }

    /** The liked side of a batch: the recent hundred twice as often as the rest. */
    private fun familiarSongs(exclude: Set<Long>): List<Track> {
        val likes = LikeRepository.likedTracks.value.filter { it.id !in exclude }
        val recent = likes.take(100).shuffled()
        val rest = likes.drop(100).shuffled()
        return recent + recent + rest
    }

    /**
     * Fresh and familiar woven together: the familiar side keeps its share of what has been placed so far, and
     * no two tracks in a row share an artist, so the wave never settles into one voice.
     */
    internal fun interleave(fresh: List<Track>, familiar: List<Track>, share: Float): List<Track> {
        val merged = mutableListOf<Track>()
        var familiarUsed = 0
        var lastArtistKey: String? = null
        var pool = fresh
        var familiarPool = familiar
        while (pool.isNotEmpty() || familiarPool.isNotEmpty()) {
            val wantFamiliar = familiarPool.isNotEmpty() &&
                share > 0f && familiarUsed < share * (merged.size + 1)
            val from = if (wantFamiliar) familiarPool else pool
            val pick = from.indexOfFirst { artistKey(it) != lastArtistKey }.takeIf { it >= 0 } ?: 0
            val track = from.getOrNull(pick) ?: break
            merged += track
            if (wantFamiliar) {
                familiarPool = familiarPool.filterIndexed { i, _ -> i != pick }
                familiarUsed++
            } else {
                pool = pool.filterIndexed { i, _ -> i != pick }
            }
            lastArtistKey = artistKey(track)
        }
        return merged.distinctBy { it.id }
    }

    private fun artistKey(track: Track): String =
        (track.displayArtist.ifBlank { track.user?.username.orEmpty() }).trim().lowercase()
}

/**
 * What the wave learns (PR #77): every artist carries a score the listener's actions move, every track can be
 * blocked, and below a silenced score an artist is not played until liked again.
 */
object WaveFeedback {

    private const val PREFS = "wave_prefs"
    private const val KEY_ARTIST_SCORES = "wave_artist_scores"
    private const val KEY_BLOCKED_TRACKS = "wave_blocked_tracks"
    private const val MAX_ARTISTS = 400
    private const val MAX_BLOCKED = 500
    private const val SILENCED_AT = -2.5f

    private val gson = Gson()

    private fun prefs(): android.content.SharedPreferences? =
        runCatching {
            KittyTuneApp.instance.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }.getOrNull()

    @Synchronized
    fun artistScore(track: Track): Float {
        val scores = loadScores()
        return scores[artistKey(track)] ?: 0f
    }

    @Synchronized
    fun isWelcome(track: Track): Boolean {
        if (isBlocked(track.id)) return false
        val scores = loadScores()
        return (scores[artistKey(track)] ?: 0f) > SILENCED_AT
    }

    @Synchronized
    fun onSkipped(track: Track, listenedMs: Long, durationMs: Long) {
        val weight = when {
            listenedMs < 10_000L || (durationMs > 0 && listenedMs < durationMs / 10) -> -1f
            listenedMs < 30_000L || (durationMs > 0 && listenedMs < durationMs / 3) -> -0.6f
            else -> 0f
        }
        if (weight != 0f) bump(track, weight)
    }

    @Synchronized
    fun onCompleted(track: Track) = bump(track, 0.3f)

    @Synchronized
    fun onLiked(track: Track) = bump(track, 1.5f)

    @Synchronized
    fun onDisliked(track: Track) {
        bump(track, -1.5f)
        block(track.id)
    }

    @Synchronized
    fun dislikeArtist(name: String) {
        val key = name.trim().lowercase()
        if (key.isEmpty()) return
        val scores = loadScores().toMutableMap()
        scores[key] = (scores[key] ?: 0f).coerceIn(-5f, 5f) - 5f
        saveScores(scores)
    }

    @Synchronized
    fun isBlocked(trackId: Long): Boolean = loadBlocked().contains(trackId)

    @Synchronized
    private fun block(trackId: Long) {
        val blocked = loadBlocked().toMutableSet()
        blocked += trackId
        // Oldest entries drop off first; blocked ids are stored in order.
        val trimmed = if (blocked.size > MAX_BLOCKED) blocked.toList().takeLast(MAX_BLOCKED).toSet() else blocked
        prefs()?.edit()?.putString(KEY_BLOCKED_TRACKS, trimmed.joinToString(","))?.apply()
    }

    @Synchronized
    private fun bump(track: Track, weight: Float) {
        val key = artistKey(track)
        if (key.isEmpty()) return
        val scores = loadScores().toMutableMap()
        scores[key] = ((scores[key] ?: 0f) + weight).coerceIn(-5f, 5f)
        saveScores(scores)
    }

    private fun artistKey(track: Track): String =
        (track.displayArtist.ifBlank { track.user?.username.orEmpty() }).trim().lowercase()

    private fun loadScores(): Map<String, Float> = runCatching {
        prefs()?.getString(KEY_ARTIST_SCORES, null)?.let {
            gson.fromJson<Map<String, Float>>(it, object : TypeToken<Map<String, Float>>() {}.type)
        }
    }.getOrNull().orEmpty()

    private fun saveScores(scores: Map<String, Float>) {
        // The most recent artists are the ones the wave still plays; the stale tail is cut first.
        val trimmed = if (scores.size > MAX_ARTISTS) scores.entries.toList().takeLast(MAX_ARTISTS).associate { it.key to it.value } else scores
        prefs()?.edit()?.putString(KEY_ARTIST_SCORES, gson.toJson(trimmed))?.apply()
    }

    private fun loadBlocked(): Set<Long> = runCatching {
        prefs()?.getString(KEY_BLOCKED_TRACKS, null)
            ?.split(",")
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
    }.getOrNull().orEmpty()
}
