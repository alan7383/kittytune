package com.alananasss.kittytune.data

import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Disk cache for resolved lyrics, keyed by track.
 *
 * Resolving lyrics costs up to a dozen HTTP round trips: several generated queries, each hitting
 * two providers, then a second pass for translation. Doing that from scratch on every play is why
 * lyrics arrived ten seconds late and why the screen flashed "unavailable" in the meantime
 * (issue #33). A hit here is a single small file read, so the lyrics are on screen in the same
 * frame the track starts.
 *
 * An entry records the settings it was resolved under — provider preference, translation target,
 * romanisation — because those change the *content*, not just the presentation. Changing any of
 * them misses the cache and re-resolves, which is the correct behaviour rather than a wasted
 * lookup.
 *
 * Misses are cached too, with a much shorter life: a track with no lyrics anywhere should not pay
 * for the full search on every play, but lyrics do get uploaded, so the answer has to expire.
 */
object LyricsCache {

    /** A resolved lookup: either the lyrics, or the recorded fact that there were none. */
    data class Entry(
        val found: Boolean,
        val lines: List<LyricLine> = emptyList(),
        val plain: String? = null,
        /** Which provider the content came from, for the "wrong lyrics" affordance. */
        val provider: String? = null,
        val providerPreference: String? = null,
        val translationLang: String? = null,
        val romanized: Boolean = false,
        val savedAtMs: Long = System.currentTimeMillis(),
    )

    private const val MAX_MEMORY_ENTRIES = 64
    private val FOUND_TTL_MS = 90L * 24 * 60 * 60 * 1000 // 90 days
    private val MISS_TTL_MS = 12L * 60 * 60 * 1000 // 12 hours

    private val gson = Gson()
    private val entryType = object : TypeToken<Entry>() {}.type

    /**
     * Android's own cache directory rather than the desktop's [java.io.File]-based one. Same contents, same
     * file names: the platform decides where a cache lives, and on Android that is a directory the system may
     * reclaim — which is exactly right for something rebuildable from the network.
     */
    private val dir: File by lazy { File(KittyTuneApp.instance.cacheDir, "lyrics").apply { mkdirs() } }

    /** Insertion-ordered so the oldest read is the one evicted; guarded by [memory] itself. */
    private val memory = ConcurrentHashMap<Long, Entry>()
    private val memoryOrder = ArrayDeque<Long>()

    /**
     * @return the cached lookup for [trackId] if it was resolved under these same settings and
     *   has not expired, otherwise null.
     */
    fun get(
        trackId: Long,
        providerPreference: String?,
        translationLang: String?,
        romanized: Boolean,
    ): Entry? {
        val entry = memory[trackId] ?: readFromDisk(trackId)?.also { remember(trackId, it) } ?: return null
        if (entry.providerPreference != providerPreference) return null
        if (entry.translationLang != translationLang) return null
        if (entry.romanized != romanized) return null
        val ttl = if (entry.found) FOUND_TTL_MS else MISS_TTL_MS
        if (System.currentTimeMillis() - entry.savedAtMs > ttl) return null
        return entry
    }

    fun put(trackId: Long, entry: Entry) {
        remember(trackId, entry)
        runCatching { File(dir, "$trackId.json").writeText(gson.toJson(entry)) }
    }

    /** Drops the entry for one track, so the next play resolves it again from the network. */
    fun invalidate(trackId: Long) {
        memory.remove(trackId)
        synchronized(memoryOrder) { memoryOrder.remove(trackId) }
        runCatching { File(dir, "$trackId.json").delete() }
    }

    fun clear() {
        memory.clear()
        synchronized(memoryOrder) { memoryOrder.clear() }
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }

    private fun readFromDisk(trackId: Long): Entry? = runCatching {
        val file = File(dir, "$trackId.json")
        if (!file.exists()) return null
        gson.fromJson<Entry>(file.readText(), entryType)?.takeIf { it.isWellFormed() }
    }.getOrNull()

    /**
     * Gson fills fields by reflection and will happily leave a Kotlin non-null property null when
     * the JSON is truncated — a half-written file after a crash, say. Catching that here keeps the
     * damage to one cache miss instead of a null surfacing while the lyrics are being drawn.
     */
    @Suppress("SENSELESS_COMPARISON")
    private fun Entry.isWellFormed(): Boolean = runCatching {
        lines.all { it.text != null && it.words != null }
    }.getOrDefault(false)

    private fun remember(trackId: Long, entry: Entry) {
        memory[trackId] = entry
        synchronized(memoryOrder) {
            memoryOrder.remove(trackId)
            memoryOrder.addLast(trackId)
            while (memoryOrder.size > MAX_MEMORY_ENTRIES) {
                memory.remove(memoryOrder.removeFirst())
            }
        }
    }
}
