package com.alananasss.kittytune.data.wave

import android.content.Context
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.mix.MixEngine
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * What "My Wave" is tuned for, chosen on its card. The names are the ones Yandex Music made familiar.
 */
enum class WaveMode {
    /** Some of your own favourites among mostly new songs that sound like them. */
    BALANCED,

    /** Mostly your favourites, with a few new songs from close by. */
    FAVORITES,

    /** Only songs you have not heard, from your taste's neighbourhood. */
    DISCOVER,

    /** New songs again, the ones many people play first. */
    POPULAR,
}

/**
 * My Wave: one endless stream of music for this listener, the home page's centrepiece (issue #66).
 *
 * The desktop app's version of this feature, brought over whole: the same recipe, the same
 * feedback, the same interleaving. Built on the mix engine's reading of the listener (their plays
 * and likes, seeds sampled from them, expanded through SoundCloud's similarity endpoints), and
 * different from the one-off mix in three ways: it never ends, a batch is fetched whenever the
 * queue runs low; it mixes in the listener's own favourites by the share the [WaveMode] asks
 * for; and it listens back, a song skipped early, liked or thumbed down shifts what comes next
 * through [WaveFeedback].
 */
object MyWave {

    /** How many songs one batch adds. */
    const val BATCH_SIZE = 20

    private val api by lazy { RetrofitClient.create() }

    /**
     * The next songs of the wave, none of them in [exclude] (what is queued or was played this session).
     * Empty only when there is nothing at all to build from and nothing trending to fall back on.
     */
    suspend fun nextBatch(mode: WaveMode, exclude: Set<Long>, size: Int = BATCH_SIZE): List<Track> = withContext(Dispatchers.IO) {
        val fresh = freshSongs(mode, exclude, size)
        val familiar = familiarSongs(exclude)
        val familiarCount = (size * familiarShare(mode)).toInt().coerceAtMost(familiar.size)
        val freshCount = size - familiarCount

        val chosenFresh = fresh.take(freshCount)
        // Short of new songs: the gap is filled with favourites rather than left.
        val chosenFamiliar = familiar.take(familiarCount + (freshCount - chosenFresh.size).coerceAtLeast(0))
        interleave(chosenFresh, chosenFamiliar)
    }

    /**
     * Songs for the first press, ready in a second or two. The full batch reads the listener's whole history and
     * expands nine seeds through SoundCloud, which took the better part of half a minute before anything played; this
     * takes what is near at hand (favourites, songs related to two recent likes, else what is trending) and the full
     * batch follows into the queue.
     */
    suspend fun quickBatch(mode: WaveMode, exclude: Set<Long>, size: Int = QUICK_SIZE): List<Track> = withContext(Dispatchers.IO) {
        val familiar = familiarSongs(exclude)
        val familiarCount = (size * familiarShare(mode)).toInt().coerceAtMost(familiar.size)
        val freshCount = size - familiarCount
        val liked = runCatching { LikeRepository.likedTracks.value }.getOrDefault(emptyList())
        val related = withTimeoutOrNull(QUICK_TIMEOUT_MS) {
            coroutineScope {
                liked.take(RECENT_LIKES).shuffled().take(2).map { seed ->
                    async { runCatching { api.getRelatedTracks(seed.id, limit = 30).collection }.getOrDefault(emptyList()) }
                }.awaitAll().flatten()
            }
        }.orEmpty()
        val pool = related.ifEmpty {
            withTimeoutOrNull(QUICK_TIMEOUT_MS) {
                runCatching {
                    api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = size * 2).collection.mapNotNull { it.track }
                }.getOrDefault(emptyList())
            }.orEmpty()
        }
        val fresh = pool.distinctBy { it.id }.filter { it.id !in exclude && WaveFeedback.isWelcome(it) }.shuffled()
        val chosenFresh = fresh.take(freshCount)
        val chosenFamiliar = familiar.take(familiarCount + (freshCount - chosenFresh.size).coerceAtLeast(0))
        interleave(chosenFresh, chosenFamiliar)
    }

    /** How much of a batch is the listener's own favourites. */
    private fun familiarShare(mode: WaveMode): Float = when (mode) {
        WaveMode.BALANCED -> 0.3f
        WaveMode.FAVORITES -> 0.7f
        WaveMode.DISCOVER -> 0f
        WaveMode.POPULAR -> 0.15f
    }

    private suspend fun freshSongs(mode: WaveMode, exclude: Set<Long>, size: Int): List<Track> {
        val mixed = when (val result = MixEngine.mix(MixEngine.Recipe.MyTaste, size = size * 3)) {
            is MixEngine.Result.Mixed -> result.tracks
            // A listener with no history yet: what is trending, so the wave plays from the first press.
            else -> runCatching {
                api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music", limit = size * 2)
                    .collection.mapNotNull { it.track }
            }.getOrDefault(emptyList())
        }
        val usable = mixed.filter { it.id !in exclude && WaveFeedback.isWelcome(it) }
        val ordered = if (mode == WaveMode.POPULAR) usable.sortedByDescending { it.playbackCount } else usable
        // Artists the listener has warmed to come forward; the order is otherwise the engine's.
        return ordered.sortedByDescending { WaveFeedback.artistScore(it).coerceAtLeast(0f) }
    }

    /** The listener's liked songs, newest likes most likely, none skipped away or excluded. */
    private fun familiarSongs(exclude: Set<Long>): List<Track> {
        val liked = runCatching { LikeRepository.likedTracks.value }.getOrDefault(emptyList())
            .filter { it.id !in exclude && WaveFeedback.isWelcome(it) }
        if (liked.isEmpty()) return emptyList()
        // Recent likes weigh more: the first hundred are drawn from twice as often as the rest.
        val recent = liked.take(RECENT_LIKES).shuffled()
        val older = liked.drop(RECENT_LIKES).shuffled()
        return buildList {
            val r = recent.iterator()
            val o = older.iterator()
            while (r.hasNext() || o.hasNext()) {
                if (r.hasNext()) add(r.next())
                if (r.hasNext()) add(r.next())
                if (o.hasNext()) add(o.next())
            }
        }
    }

    /**
     * Favourites spread evenly through the new songs, by their share of the batch, and no artist twice in a row
     * where it can be helped.
     */
    internal fun interleave(fresh: List<Track>, familiar: List<Track>): List<Track> {
        val merged = mutableListOf<Track>()
        val f = fresh.toMutableList()
        val k = familiar.toMutableList()
        val total = f.size + k.size
        if (total == 0) return emptyList()
        val share = k.size.toFloat() / total
        var familiarUsed = 0
        while (f.isNotEmpty() || k.isNotEmpty()) {
            val wantFamiliar = k.isNotEmpty() && (f.isEmpty() || familiarUsed < share * (merged.size + 1))
            val pool = if (wantFamiliar) k else f
            val last = merged.lastOrNull()?.let(::artistKey)
            val index = pool.indexOfFirst { artistKey(it) != last }.takeIf { it >= 0 } ?: 0
            merged += pool.removeAt(index)
            if (wantFamiliar) familiarUsed++
        }
        return merged.distinctBy { it.id }
    }

    internal fun artistKey(track: Track): String =
        (track.displayArtist.ifBlank { track.user?.username.orEmpty() }).trim().lowercase()

    private const val RECENT_LIKES = 100
    private const val QUICK_SIZE = 8
    private const val QUICK_TIMEOUT_MS = 6_000L
}

/**
 * What the listener told the wave without saying anything: a song left in its first seconds counts against its
 * artist, a like and a song heard to the end count for them, a thumb down shuts the song out. Kept between sessions,
 * and bounded.
 *
 * The desktop app's version, with Android's preferences underneath instead of the desktop's
 * preferences file: the same scores, the same bounds, so a listener's wave behaves the same on
 * both devices.
 */
object WaveFeedback {

    private const val KEY_ARTISTS = "wave_artist_scores"
    private const val KEY_BLOCKED = "wave_blocked_tracks"
    private const val MAX_ARTISTS = 400
    private const val MAX_BLOCKED = 500

    /** At or below this an artist is left out of the wave until they are liked again. */
    private const val SILENCED_AT = -2.5f

    private val gson = Gson()
    private val prefs by lazy {
        KittyTuneApp.instance.getSharedPreferences("wave_state", Context.MODE_PRIVATE)
    }
    private val artists: LinkedHashMap<String, Float> = load(KEY_ARTISTS) ?: LinkedHashMap()
    private val blocked: LinkedHashSet<Long> = prefs.getString(KEY_BLOCKED, null)
        ?.split(',')?.mapNotNull { it.toLongOrNull() }?.toCollection(LinkedHashSet()) ?: LinkedHashSet()

    @Synchronized
    fun artistScore(track: Track): Float = artists[MyWave.artistKey(track)] ?: 0f

    @Synchronized
    fun isWelcome(track: Track): Boolean = track.id !in blocked && artistScore(track) > SILENCED_AT

    /** Left early: how early decides how much it counts. */
    fun onSkipped(track: Track, listenedMs: Long, durationMs: Long) {
        val share = if (durationMs > 0) listenedMs.toFloat() / durationMs else 1f
        val weight = when {
            listenedMs < 10_000 || share < 0.1f -> -1f
            listenedMs < 30_000 || share < 0.3f -> -0.6f
            else -> 0f
        }
        if (weight != 0f) adjust(track, weight)
    }

    fun onCompleted(track: Track) = adjust(track, 0.3f)

    /** "Don't play this artist", from their page: out of the wave until one of their songs is liked. */
    fun dislikeArtist(artistName: String) = adjustKey(artistName.trim().lowercase(), -5f)

    fun onLiked(track: Track) = adjust(track, 1.5f)

    @Synchronized
    fun onDisliked(track: Track) {
        blocked.remove(track.id)
        blocked.add(track.id)
        while (blocked.size > MAX_BLOCKED) blocked.remove(blocked.first())
        prefs.edit().putString(KEY_BLOCKED, blocked.joinToString(",")).apply()
        adjust(track, -1.5f)
    }

    private fun adjust(track: Track, delta: Float) = adjustKey(MyWave.artistKey(track), delta)

    @Synchronized
    private fun adjustKey(key: String, delta: Float) {
        if (key.isEmpty()) return
        val score = ((artists.remove(key) ?: 0f) + delta).coerceIn(-5f, 5f)
        artists[key] = score
        while (artists.size > MAX_ARTISTS) artists.remove(artists.keys.first())
        prefs.edit().putString(KEY_ARTISTS, gson.toJson(artists)).apply()
    }

    private fun load(key: String): LinkedHashMap<String, Float>? = runCatching {
        prefs.getString(key, null)?.let {
            gson.fromJson<LinkedHashMap<String, Float>>(it, object : TypeToken<LinkedHashMap<String, Float>>() {}.type)
        }
    }.getOrNull()
}
