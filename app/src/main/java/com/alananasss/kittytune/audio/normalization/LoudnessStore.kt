package com.alananasss.kittytune.audio.normalization

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

/**
 * How loud each track is, remembered between plays — the per-track figure a fixed normalization gain
 * is computed from, so a track already met starts at the right level on its very first sample.
 *
 * Three ways in, from least to most trusted: a quick [Source.ESTIMATE] of an upcoming track decoded
 * in the background, a [Source.MEASURED] figure taken while the track actually played, and a
 * [Source.FULL] scan of a downloaded file. A better figure replaces a worse one, never the reverse.
 *
 * Read on the playback thread at every track change, so lookups are served from memory; the file is
 * only loaded once, off the main thread, and written in the background.
 */
object LoudnessStore {

    enum class Source { ESTIMATE, MEASURED, FULL }

    /** [lufs] integrated loudness, [peakDb] the highest sample in dBFS, [seconds] how much was measured. */
    data class Entry(val lufs: Float, val peakDb: Float, val seconds: Float, val source: Source)

    private const val PREFS = "track_loudness_v1"

    private val cache = ConcurrentHashMap<String, Entry>()
    @Volatile private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs != null) return
        val app = context.applicationContext
        thread(name = "LoudnessStore", isDaemon = true) {
            val p = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            p.all.forEach { (key, value) -> decode(value as? String)?.let { cache.putIfAbsent(key, it) } }
            prefs = p
        }
    }

    fun get(key: String?): Entry? = key?.let { cache[it] }

    fun put(key: String?, entry: Entry) {
        if (key.isNullOrBlank() || !entry.lufs.isFinite() || entry.lufs < -70f || entry.lufs > 0f) return
        val old = cache[key]
        val better = old == null ||
            entry.source > old.source ||
            (entry.source == old.source && entry.seconds > old.seconds + 5f)
        if (!better) return
        cache[key] = entry
        prefs?.edit()?.putString(key, encode(entry))?.apply()
    }

    /**
     * The key a track is filed under: its id, whatever the media id around it says. The player's media
     * ids can carry a prefix and a playback context ("yt_123", "track:123:context:…"); the queue's
     * tracks are filed by their bare id.
     */
    fun keyForMediaId(mediaId: String?): String? {
        if (mediaId.isNullOrBlank()) return null
        val id = mediaId
            .removePrefix(com.alananasss.kittytune.data.KittyTuneMediaLibrarySessionCallback.TRACK_PREFIX)
            .substringBefore(com.alananasss.kittytune.data.KittyTuneMediaLibrarySessionCallback.CONTEXT_SEPARATOR)
            .removePrefix("yt_")
        return id.ifBlank { null }
    }

    private fun encode(e: Entry) = "${e.lufs};${e.peakDb};${e.seconds};${e.source.name}"

    private fun decode(raw: String?): Entry? {
        val parts = raw?.split(';') ?: return null
        if (parts.size != 4) return null
        return runCatching {
            Entry(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat(), Source.valueOf(parts[3]))
        }.getOrNull()
    }
}
