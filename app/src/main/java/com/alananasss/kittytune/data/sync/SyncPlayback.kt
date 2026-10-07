package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.KittyTuneApp
import android.content.Context
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** The last playable queue and playhead, independent of the append-only listen history. */
data class PlaybackSnapshot(
    val deviceId: String,
    val updatedAtMs: Long,
    val queue: List<Track>,
    val currentIndex: Int,
    val positionMs: Long,
    val isPlaying: Boolean,
    val shuffleEnabled: Boolean,
    val repeatMode: String,
) {
    fun projectedPosition(nowMs: Long): Long {
        val elapsed = if (isPlaying) (nowMs - updatedAtMs).coerceAtLeast(0L) else 0L
        val duration = queue.getOrNull(currentIndex)?.durationMs?.takeIf { it > 0L }
        val projected = positionMs.coerceAtLeast(0L) + elapsed
        return if (duration != null) projected.coerceAtMost(duration) else projected
    }
}

/** A last-writer-wins register. Older peers ignore the optional exchange field. */
object SyncPlayback {
    private const val KEY = "playback_snapshot"
    private const val MAX_QUEUE = 500
    private val gson = Gson()
    private val prefs by lazy { KittyTuneApp.instance.getSharedPreferences("sync_state", Context.MODE_PRIVATE) }
    private val _latest = MutableStateFlow(load())
    val latest: StateFlow<PlaybackSnapshot?> = _latest
    private var localOwner = _latest.value?.deviceId == null || _latest.value?.deviceId == SyncLog.deviceId
    private var lastTransferAtMs = 0L
    var enabled: Boolean
        get() = prefs.getBoolean("playback_sync_enabled", true)
        set(value) { prefs.edit().putBoolean("playback_sync_enabled", value).apply() }

    /** A deliberate local playback action takes ownership back from a peer. */
    @Synchronized fun claimLocal() { localOwner = true }

    @Synchronized
    fun publish(queue: List<Track>, currentIndex: Int, positionMs: Long, isPlaying: Boolean,
                shuffleEnabled: Boolean, repeatMode: String): PlaybackSnapshot? {
        if (!enabled || !localOwner || queue.isEmpty() || currentIndex !in queue.indices) return null
        val start = if (queue.size > MAX_QUEUE)
            (currentIndex - MAX_QUEUE / 2).coerceIn(0, queue.size - MAX_QUEUE) else 0
        val selectedQueue = queue.drop(start).take(MAX_QUEUE)
        val selectedIndex = currentIndex - start
        val now = System.currentTimeMillis()
        val old = _latest.value
        val changed = old == null || old.deviceId != SyncLog.deviceId || old.currentIndex != selectedIndex ||
            old.isPlaying != isPlaying || old.shuffleEnabled != shuffleEnabled ||
            old.repeatMode != repeatMode || old.queue.map { it.id } != selectedQueue.map { it.id }
        val seeked = old != null && kotlin.math.abs(positionMs - old.projectedPosition(now)) > 2_000L
        if (!changed && !seeked &&
            (SyncPeers.isEmpty() || now - lastTransferAtMs < 15_000L)) return old
        val snapshot = PlaybackSnapshot(
            deviceId = SyncLog.deviceId,
            updatedAtMs = maxOf(now, (_latest.value?.updatedAtMs ?: 0L) + 1L),
            queue = selectedQueue,
            currentIndex = selectedIndex,
            positionMs = positionMs.coerceAtLeast(0L),
            isPlaying = isPlaying,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
        )
        if (snapshot.currentIndex !in snapshot.queue.indices) return null
        save(snapshot)
        lastTransferAtMs = now
        if (SyncPeers.anyDialable()) {
            SyncScheduler.triggerImmediateSync("playback")
        }
        return snapshot
    }

    @Synchronized
    fun accept(snapshot: PlaybackSnapshot?) {
        if (!enabled || !isValid(snapshot)) return
        snapshot ?: return
        val current = _latest.value
        if (current == null || snapshot.updatedAtMs > current.updatedAtMs ||
            (snapshot.updatedAtMs == current.updatedAtMs && snapshot.deviceId > current.deviceId)) {
            localOwner = snapshot.deviceId == SyncLog.deviceId
            save(snapshot)
        }
    }

    fun current(): PlaybackSnapshot? = _latest.value.takeIf { enabled }

    private fun save(snapshot: PlaybackSnapshot) {
        prefs.edit().putString(KEY, gson.toJson(snapshot)).apply()
        _latest.value = snapshot
    }

    private fun load(): PlaybackSnapshot? = runCatching {
        prefs.getString(KEY, null)?.let { gson.fromJson(it, PlaybackSnapshot::class.java) }
    }.getOrNull()?.takeIf(::isValid)

    private fun isValid(snapshot: PlaybackSnapshot?): Boolean = runCatching {
        snapshot != null && snapshot.deviceId.isNotBlank() && snapshot.queue.isNotEmpty() &&
            snapshot.queue.size <= MAX_QUEUE && snapshot.currentIndex in snapshot.queue.indices &&
            snapshot.positionMs >= 0L && snapshot.updatedAtMs > 0L
    }.getOrDefault(false)
}
