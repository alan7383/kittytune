package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Projects durable sync events into the local statistics and library. */
object SyncApply {
    private val gson = Gson()
    private val gate = Mutex()

    // Retry from the journal if the process stopped or an insert failed after the events landed.
    private var needsListenReplay = true

    suspend fun mergeAndApply(incoming: List<SyncEvent>): List<SyncEvent> = gate.withLock {
        val events = SyncLog.merge(incoming)
        projectListens(if (needsListenReplay) SyncLog.all() else events)
        if (events.any { it.kind == SyncKinds.LIKE || it.kind == SyncKinds.PLAYLIST_LIKE }) {
            runCatching { SyncLikes.reconcile() }
        }
        events
    }

    /** Rebuilds missing rows. Replaying an existing event leaves its statistics unchanged. */
    suspend fun reconcile(): Int = withContext(Dispatchers.IO) {
        val restored = gate.withLock {
            needsListenReplay = true
            projectListens(SyncLog.all())
        }
        val likes = if (SyncPeers.isEmpty()) 0 else runCatching { SyncLikes.reconcile() }.getOrDefault(0)
        restored + likes
    }

    private suspend fun projectListens(events: List<SyncEvent>): Int {
        needsListenReplay = true
        val rows = events.mapNotNull { event -> if (event.kind == SyncKinds.LISTEN) toRow(event) else null }
        val inserted = if (rows.isEmpty()) 0 else withContext(NonCancellable) {
            val dao = AppDatabase.getDatabase(KittyTuneApp.instance).downloadDao()
            val count = dao.insertStatsEvents(rows).count { it >= 0 }
            if (count > 0) ListeningStatsRepository.onStatsChanged()
            count
        }
        needsListenReplay = false
        return inserted
    }

    private fun toRow(event: SyncEvent): ListeningStatsEvent? {
        val payload = runCatching {
            gson.fromJson(event.payload, ListenPayload::class.java)
        }.getOrNull() ?: return null
        if (payload.eventType.isNullOrEmpty() || payload.listenDurationMs < 0) return null

        return ListeningStatsEvent(
            trackId = payload.trackId,
            trackTitle = payload.trackTitle.orEmpty(),
            artistName = payload.artistName.orEmpty(),
            artistId = payload.artistId,
            artistPermalink = payload.artistPermalink,
            artistAvatarUrl = payload.artistAvatarUrl,
            // The column is not nullable; the other device may have had none.
            artworkUrl = payload.artworkUrl.orEmpty(),
            source = payload.source.orEmpty(),
            eventType = payload.eventType,
            listenDurationMs = payload.listenDurationMs,
            trackDurationMs = payload.trackDurationMs,
            // The other device's clock, not ours: a listen belongs to the moment it happened, which is what
            // puts it in the same week on both devices.
            timestamp = event.timestampMs,
            furthestPositionMs = payload.furthestPositionMs,
            genre = payload.genre,
            // What makes this insert idempotent at the database level rather than at ours.
            syncEventId = event.id,
        )
    }
}
