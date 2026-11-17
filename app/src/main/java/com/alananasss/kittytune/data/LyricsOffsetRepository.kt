package com.alananasss.kittytune.data

import android.content.Context
import androidx.collection.LruCache
import androidx.core.content.edit
import com.alananasss.kittytune.KittyTuneApp

/**
 * A track's persistent lyrics synchronization: one offset, or two points the offset drifts between
 * (see [LyricsSync]).
 *
 * When a user adjusts lyrics timing for a track (e.g. +1s, -0.1s), the sync is remembered per track so
 * returning to the track or replaying it preserves it.
 */
object LyricsOffsetRepository {

    private val cache = LruCache<Long, LyricsSync>(256)
    private val prefs by lazy {
        KittyTuneApp.instance.getSharedPreferences("lyrics_offsets", Context.MODE_PRIVATE)
    }

    suspend fun get(trackId: Long): LyricsSync {
        cache.get(trackId)?.let { return it }
        val raw = prefs.getString(trackId.toString(), null)
        val sync = if (raw != null) {
            runCatching {
                val parts = raw.split(",")
                val offset = parts[0].toLong()
                val anchor = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                val endAt = parts.getOrNull(2)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                val endOffset = parts.getOrNull(3)?.takeIf { it.isNotBlank() }?.toLongOrNull()
                LyricsSync(offset, anchor, endAt, endOffset)
            }.getOrDefault(LyricsSync.NONE)
        } else {
            LyricsSync.NONE
        }
        cache.put(trackId, sync)
        return sync
    }

    suspend fun put(trackId: Long, sync: LyricsSync) {
        if (sync.isNone) {
            remove(trackId)
            return
        }
        val clamped = sync.copy(
            offsetMs = sync.offsetMs.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS),
            endOffsetMs = sync.endOffsetMs?.coerceIn(MIN_OFFSET_MS, MAX_OFFSET_MS),
        )
        cache.put(trackId, clamped)
        val value = "${clamped.offsetMs},${clamped.anchorMs},${clamped.endAtMs ?: ""},${clamped.endOffsetMs ?: ""}"
        prefs.edit { putString(trackId.toString(), value) }
    }

    suspend fun remove(trackId: Long) {
        cache.put(trackId, LyricsSync.NONE)
        prefs.edit { remove(trackId.toString()) }
    }

    const val MIN_OFFSET_MS = -120_000L
    const val MAX_OFFSET_MS = 120_000L
}
