package com.alananasss.kittytune.data.sync

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson

/**
 * Favourites, kept the same on every paired device (issue #33).
 */
object SyncLikes {

    private val gson = Gson()
    private val prefs by lazy {
        KittyTuneApp.instance.getSharedPreferences("sync_state", Context.MODE_PRIVATE)
    }

    /** What the log says about one track, once every event about it has been taken into account. */
    private data class Resolved(val liked: Boolean, val atMs: Long, val track: Track?)

    /**
     * Records that the user liked or unliked [trackId] here, now.
     */
    fun record(trackId: Long, liked: Boolean, track: Track?) {
        runCatching {
            SyncLog.append(
                kind = SyncKinds.LIKE,
                payload = LikePayload(
                    trackId = trackId,
                    liked = liked,
                    atMs = System.currentTimeMillis(),
                    track = track?.takeIf { liked },
                ),
            )
        }.onFailure { Log.w("SyncLikes", "could not record like for $trackId: ${it.message}") }
        SyncScheduler.requestSync("like changed")
    }

    /** The same, for a playlist. */
    fun recordPlaylist(playlistId: Long, liked: Boolean, permalinkUrl: String?, urn: String?) {
        runCatching {
            SyncLog.append(
                kind = SyncKinds.PLAYLIST_LIKE,
                payload = PlaylistLikePayload(
                    playlistId = playlistId,
                    liked = liked,
                    atMs = System.currentTimeMillis(),
                    permalinkUrl = permalinkUrl,
                    urn = urn,
                ),
            )
        }.onFailure { Log.w("SyncLikes", "could not record playlist like: ${it.message}") }
        SyncScheduler.requestSync("playlist like changed")
    }

    /**
     * Gives every like already in the library an event of its own, once per track.
     */
    fun seedMissing(): Int {
        val known = likeEventsByTrack().keys
        val library = runCatching { LikeRepository.likedTracks.value }.getOrDefault(emptyList())
        val missing = library.filter { it.id !in known }
        if (missing.isEmpty()) return 0

        val batch = missing.take(MAX_SEED_PER_PASS)
        val now = System.currentTimeMillis()
        val items = batch.map { track ->
            val atMs = track.likedAt?.takeIf { it in 1..now } ?: now
            SyncLog.BatchItem(
                kind = SyncKinds.LIKE,
                payload = LikePayload(
                    trackId = track.id,
                    liked = true,
                    atMs = atMs,
                    track = track,
                ),
                timestampMs = atMs,
            )
        }
        val added = runCatching { SyncLog.appendBatch(items).size }.getOrDefault(0)
        if (added > 0) {
            Log.i("SyncLikes", "seeded $added existing likes into the sync log (${missing.size - added} left)")
            SyncScheduler.requestSync("likes seeded")
        }
        return added
    }

    /** The same for playlist likes. */
    fun seedMissingPlaylists(): Int {
        val known = playlistEventsById().keys
        val library = runCatching { LikeRepository.likedPlaylists.value }.getOrDefault(emptySet())
        val missing = library.filter { it !in known }
        if (missing.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val items = missing.take(MAX_SEED_PER_PASS).map { id ->
            SyncLog.BatchItem(
                kind = SyncKinds.PLAYLIST_LIKE,
                payload = PlaylistLikePayload(playlistId = id, liked = true, atMs = now),
                timestampMs = now,
            )
        }
        val added = runCatching { SyncLog.appendBatch(items).size }.getOrDefault(0)
        if (added > 0) SyncScheduler.requestSync("playlist likes seeded")
        return added
    }

    /**
     * Makes the library agree with the log, and returns how many tracks it had to change.
     */
    suspend fun reconcile(): Int {
        var changed = 0
        val present = runCatching { LikeRepository.likedTrackIds() }.getOrDefault(emptySet())

        val toAdd = ArrayList<Pair<Track, Long>>()
        val toRemove = HashSet<Long>()

        for ((trackId, resolved) in resolveTracks()) {
            val isPresent = trackId in present
            when {
                resolved.liked && !isPresent -> {
                    val track = resolved.track ?: continue
                    toAdd.add(track to resolved.atMs)
                }

                !resolved.liked && isPresent -> {
                    toRemove.add(trackId)
                }
            }
        }

        if (toAdd.isNotEmpty()) {
            LikeRepository.applyRemoteLikesBatch(toAdd)
            changed += toAdd.size
        }
        if (toRemove.isNotEmpty()) {
            LikeRepository.applyRemoteUnlikesBatch(toRemove)
            changed += toRemove.size
        }

        val likedPlaylists = runCatching { LikeRepository.likedPlaylists.value }.getOrDefault(emptySet())
        for ((playlistId, resolved) in resolvePlaylists()) {
            val isPresent = playlistId in likedPlaylists
            if (resolved.liked != isPresent) {
                LikeRepository.applyRemotePlaylistLike(
                    playlistId = playlistId,
                    liked = resolved.liked,
                    permalinkUrl = resolved.permalinkUrl,
                    urn = resolved.urn,
                )
                changed++
            }
        }

        if (changed > 0) Log.i("SyncLikes", "reconciled $changed favourites against the sync log")
        return changed
    }

    fun likeEventCount(): Int = runCatching {
        SyncLog.all().count { it.kind == SyncKinds.LIKE || it.kind == SyncKinds.PLAYLIST_LIKE }
    }.getOrDefault(0)

    private fun resolveTracks(): Map<Long, Resolved> {
        val winners = HashMap<Long, Pair<SyncEvent, LikePayload>>()
        val events = likeEvents()
        for ((event, payload) in events) {
            val current = winners[payload.trackId]
            if (current == null || beats(payload.atMs, event.deviceId, current.second.atMs, current.first.deviceId)) {
                winners[payload.trackId] = event to payload
            }
        }
        val bestKnownTrack = HashMap<Long, Track>()
        for ((_, payload) in events) {
            payload.track?.let { bestKnownTrack.putIfAbsent(payload.trackId, it) }
        }
        return winners.mapValues { (trackId, won) ->
            Resolved(
                liked = won.second.liked,
                atMs = won.second.atMs,
                track = won.second.track ?: bestKnownTrack[trackId],
            )
        }
    }

    private data class ResolvedPlaylist(
        val liked: Boolean,
        val atMs: Long,
        val permalinkUrl: String?,
        val urn: String?,
    )

    private fun resolvePlaylists(): Map<Long, ResolvedPlaylist> {
        val winners = HashMap<Long, Pair<SyncEvent, PlaylistLikePayload>>()
        for ((event, payload) in playlistEvents()) {
            val current = winners[payload.playlistId]
            if (current == null || beats(payload.atMs, event.deviceId, current.second.atMs, current.first.deviceId)) {
                winners[payload.playlistId] = event to payload
            }
        }
        return winners.mapValues { (_, won) ->
            ResolvedPlaylist(
                liked = won.second.liked,
                atMs = won.second.atMs,
                permalinkUrl = won.second.permalinkUrl,
                urn = won.second.urn,
            )
        }
    }

    private fun beats(atMs: Long, deviceId: String, otherAtMs: Long, otherDeviceId: String): Boolean =
        atMs > otherAtMs || (atMs == otherAtMs && deviceId > otherDeviceId)

    private fun likeEvents(): List<Pair<SyncEvent, LikePayload>> =
        runCatching { SyncLog.all() }.getOrDefault(emptyList())
            .asSequence()
            .filter { it.kind == SyncKinds.LIKE }
            .mapNotNull { event ->
                val payload = runCatching { gson.fromJson(event.payload, LikePayload::class.java) }
                    .getOrNull() ?: return@mapNotNull null
                if (payload.trackId == 0L) null else event to payload
            }
            .toList()

    private fun playlistEvents(): List<Pair<SyncEvent, PlaylistLikePayload>> =
        runCatching { SyncLog.all() }.getOrDefault(emptyList())
            .asSequence()
            .filter { it.kind == SyncKinds.PLAYLIST_LIKE }
            .mapNotNull { event ->
                val payload = runCatching { gson.fromJson(event.payload, PlaylistLikePayload::class.java) }
                    .getOrNull() ?: return@mapNotNull null
                if (payload.playlistId == 0L) null else event to payload
            }
            .toList()

    private fun likeEventsByTrack(): Map<Long, List<LikePayload>> =
        likeEvents().groupBy({ it.second.trackId }, { it.second })

    private fun playlistEventsById(): Map<Long, List<PlaylistLikePayload>> =
        playlistEvents().groupBy({ it.second.playlistId }, { it.second })

    private const val MAX_SEED_PER_PASS = 400
    private const val KEY_SEEDED = "likes_seeded_at"

    var lastSeededAtMs: Long
        get() = prefs.getLong(KEY_SEEDED, 0L)
        set(value) = prefs.edit().putLong(KEY_SEEDED, value).apply()
}
