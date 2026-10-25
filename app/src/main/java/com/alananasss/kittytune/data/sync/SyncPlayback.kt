package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.KittyTuneApp
import android.content.Context
import com.alananasss.kittytune.domain.SafeTrackListAdapter
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Rejects snapshots whose queue was decoded as raw maps (for example after generic
 * signatures were lost during minification). Returning null here keeps a corrupt
 * payload from ever reaching player state, where reading `queue[i].id` would throw
 * `LinkedTreeMap cannot be cast to Track`.
 */
internal fun PlaybackSnapshot.sanitized(): PlaybackSnapshot? {
    val rawQueue = (queue as? List<*>) ?: return null
    if (rawQueue.isEmpty()) return null
    if (rawQueue.any { it !is Track }) return null
    if (currentIndex !in rawQueue.indices) return null
    return this
}

/** The last playable queue and playhead, independent of the append-only listen history. */
data class PlaybackSnapshot(
    @SerializedName("deviceId") val deviceId: String,
    @SerializedName("updatedAtMs") val updatedAtMs: Long,
    @SerializedName("queue") @JsonAdapter(SafeTrackListAdapter::class) val queue: List<Track>,
    @SerializedName("currentIndex") val currentIndex: Int,
    @SerializedName("positionMs") val positionMs: Long,
    @SerializedName("isPlaying") val isPlaying: Boolean,
    @SerializedName("shuffleEnabled") val shuffleEnabled: Boolean,
    @SerializedName("repeatMode") val repeatMode: String,
    @SerializedName("volume") val volume: Float? = null,
    @SerializedName("isLiked") val isLiked: Boolean? = null,
    @SerializedName("isYourMixActive") val isYourMixActive: Boolean? = null,
    @SerializedName("speed") val speed: Float? = null,
    @SerializedName("sleepTimerRemainingMs") val sleepTimerRemainingMs: Long? = null,
    @SerializedName("sleepTimerEndOfTrack") val sleepTimerEndOfTrack: Boolean? = null,
) {
    fun projectedPosition(nowMs: Long): Long {
        val elapsed = if (isPlaying) (nowMs - updatedAtMs).coerceAtLeast(0L) else 0L
        val duration = (queue as? List<*>)?.filterIsInstance<Track>()?.getOrNull(currentIndex)?.durationMs?.takeIf { it > 0L }
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
        set(value) { prefs.edit().putBoolean("playback_sync_enabled", value).apply(); ConnectManager.setEnabled(value) }

    /** A deliberate local playback action takes ownership back from a peer. */
    @Synchronized fun claimLocal() { localOwner = true }

    @Synchronized
    fun publish(queue: List<Track>, currentIndex: Int, positionMs: Long, isPlaying: Boolean,
                shuffleEnabled: Boolean, repeatMode: String, isLiked: Boolean? = null,
                isYourMixActive: Boolean? = null, speed: Float? = null,
                sleepTimerRemainingMs: Long? = null, sleepTimerEndOfTrack: Boolean? = null): PlaybackSnapshot? {
        if (!enabled || !localOwner || queue.isEmpty() || currentIndex !in queue.indices) return null
        // Never publish a queue containing maps or any other non-Track value.
        val cleanQueue = (queue as? List<*>)?.filterIsInstance<Track>() ?: return null
        if (cleanQueue.isEmpty() || currentIndex !in cleanQueue.indices) return null
        val start = if (cleanQueue.size > MAX_QUEUE)
            (currentIndex - MAX_QUEUE / 2).coerceIn(0, cleanQueue.size - MAX_QUEUE) else 0
        val selectedQueue = cleanQueue.drop(start).take(MAX_QUEUE)
        val selectedIndex = currentIndex - start
        val now = System.currentTimeMillis()
        // A snapshot written by an older/minified build can still be sitting in
        // SharedPreferences. Treat it as absent instead of dereferencing its queue.
        val old = _latest.value?.sanitized()?.takeIf(::isValid)
        val oldQueue = (old?.queue as? List<*>)?.filterIsInstance<Track>() ?: emptyList()
        val safeSelectedQueue = (selectedQueue as? List<*>)?.filterIsInstance<Track>() ?: emptyList()
        val queueChanged = old == null || oldQueue.size != safeSelectedQueue.size ||
            oldQueue.map { it.id } != safeSelectedQueue.map { it.id }
        val changed = old == null || old.deviceId != SyncLog.deviceId || old.currentIndex != selectedIndex ||
            old.isPlaying != isPlaying || old.shuffleEnabled != shuffleEnabled ||
            old.repeatMode != repeatMode || old.isLiked != isLiked ||
            old.isYourMixActive != isYourMixActive || old.speed != speed ||
            old.sleepTimerEndOfTrack != sleepTimerEndOfTrack ||
            (old.sleepTimerRemainingMs == null) != (sleepTimerRemainingMs == null) ||
            queueChanged
        val seeked = old != null && kotlin.math.abs(positionMs - old.projectedPosition(now)) > 2_000L
        if (!changed && !seeked &&
            (SyncPeers.isEmpty() || now - lastTransferAtMs < 60_000L)) return old
        val snapshot = PlaybackSnapshot(
            deviceId = SyncLog.deviceId,
            updatedAtMs = maxOf(now, (_latest.value?.updatedAtMs ?: 0L) + 1L),
            queue = selectedQueue,
            currentIndex = selectedIndex,
            positionMs = positionMs.coerceAtLeast(0L),
            isPlaying = isPlaying,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            isLiked = isLiked,
            isYourMixActive = isYourMixActive,
            speed = speed,
            sleepTimerRemainingMs = sleepTimerRemainingMs,
            sleepTimerEndOfTrack = sleepTimerEndOfTrack,
        )
        if (snapshot.currentIndex !in snapshot.queue.indices) return null
        save(snapshot)
        lastTransferAtMs = now
        if ((changed || seeked) && SyncPeers.anyDialable() && !ConnectManager.hasLivePeer()) {
            SyncScheduler.requestSync("playback")
        }
        return snapshot
    }

    @Synchronized
    fun accept(snapshot: PlaybackSnapshot?) {
        if (!enabled) return
        val safe = snapshot?.sanitized() ?: return
        if (!isValid(safe)) return
        val current = _latest.value?.sanitized()?.takeIf(::isValid)
        if (current == null || safe.updatedAtMs > current.updatedAtMs ||
            (safe.updatedAtMs == current.updatedAtMs && safe.deviceId > current.deviceId)) {
            localOwner = safe.deviceId == SyncLog.deviceId
            save(safe)
        }
    }

    fun current(): PlaybackSnapshot? = _latest.value?.sanitized()?.takeIf { enabled && isValid(it) }

    private fun save(snapshot: PlaybackSnapshot) {
        val safe = snapshot.sanitized() ?: return
        prefs.edit().putString(KEY, gson.toJson(safe)).apply()
        _latest.value = safe
    }

    private fun load(): PlaybackSnapshot? = runCatching {
        val raw = prefs.getString(KEY, null) ?: return null
        val snapshot = gson.fromJson(raw, PlaybackSnapshot::class.java)?.sanitized()
        if (snapshot == null || !isValid(snapshot)) {
            prefs.edit().remove(KEY).apply()
            null
        } else {
            snapshot
        }
    }.getOrNull()

    private fun isValid(snapshot: PlaybackSnapshot?): Boolean = runCatching {
        val safe = snapshot?.sanitized()
        safe != null && safe.deviceId.isNotBlank() &&
            safe.queue.isNotEmpty() && safe.queue.size <= MAX_QUEUE &&
            safe.positionMs >= 0L && safe.updatedAtMs > 0L
    }.getOrDefault(false)
}
