package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Turns merged events into the local data they describe (issue #33).
 *
 * Two independent things keep a listen from being counted twice: [SyncLog.merge] has already dropped
 * everything known, and the row carries the event's id under a unique index — so even a merge that runs
 * twice over the same batch produces one row.
 *
 * A kind this version does not know is ignored rather than an error, so a phone on an older release still
 * syncs everything it has in common with the desktop and keeps the rest in its log for later.
 */
object SyncApply {

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Re-applies anything the log holds that the statistics table is missing (issue #33).
     *
     * This exists because the two could get out of step, and did — badly. [SyncLog.merge] writes the events
     * and advances the marks; inserting the rows is a separate step afterwards. When that step failed or was
     * cancelled, the marks had already moved: the device believed it held those listens, the peer would never
     * send them again, and the rows were simply gone.
     *
     * Measured on a real pair before this was added: a phone whose log held 71 listens had **5** of them in
     * its table. Sixty-six listens acknowledged and lost.
     *
     * The fix is to stop treating the table as the record and treat it as what it always was — a projection
     * of the log. Reconciling is cheap and idempotent: every row carries its event's id under a unique index,
     * so re-inserting what is already there does nothing.
     *
     * @return how many rows this restored.
     */
    suspend fun reconcile(): Int {
        val restoredLikes = runCatching { SyncLikes.reconcile() }.getOrDefault(0)
        val events = runCatching { SyncLog.all() }.getOrDefault(emptyList())
            .filter { it.kind == SyncKinds.LISTEN }
        if (events.isEmpty()) return restoredLikes
        val rows = events.mapNotNull { toRow(it) }
        val restored = runCatching { insertRows(rows) }
            .onFailure {
                // Not swallowed. A silent failure here is exactly how sixty-six listens went missing without
                // anyone noticing: the marks had advanced, so nothing would ever ask for them again.
                android.util.Log.e("SyncApply", "reconcile failed for ${rows.size} rows", it)
            }
            .getOrDefault(0)
        if (restored > 0) {
            android.util.Log.i("SyncApply", "reconcile restored $restored listens the table was missing")
        }
        if (restored > 0) ListeningStatsRepository.onStatsChanged()
        return restored + restoredLikes
    }

    fun apply(events: List<SyncEvent>) {
        if (events.isEmpty()) return
        scope.launch { applyNow(events) }
    }

    /**
     * The same work, awaited.
     *
     * The exchange reports how many events it applied, and a caller that launched the writes and returned
     * immediately was reporting a number for work that had not happened — so the screen could say
     * "12 received" while the statistics still showed none of them (issue #33).
     */
    suspend fun applyNow(events: List<SyncEvent>) {
        if (events.isEmpty()) return

        val hasLikes = events.any { it.kind == SyncKinds.LIKE || it.kind == SyncKinds.PLAYLIST_LIKE }
        if (hasLikes) {
            runCatching { SyncLikes.reconcile() }
        }

        // Collected first and written in one transaction. A first pairing carries hundreds of rows, and one
        // commit each turns a moment into a visible pause (issue #33).
        val rows = events
            .filter { it.kind == SyncKinds.LISTEN }
            .mapNotNull { toRow(it) }

        val inserted = runCatching { insertRows(rows) }
            .onFailure { android.util.Log.e("SyncApply", "apply failed for ${rows.size} rows", it) }
            .getOrDefault(0)

        // One notification for the batch rather than one per row: each would otherwise invalidate the cache
        // and wake every screen watching it.
        if (inserted > 0) ListeningStatsRepository.onStatsChanged()
    }

    /** One transaction, and the count of rows that were genuinely new — `-1` marks an ignored duplicate. */
    private suspend fun insertRows(rows: List<ListeningStatsEvent>): Int {
        if (rows.isEmpty()) return 0
        val dao = AppDatabase.getDatabase(KittyTuneApp.instance).downloadDao()
        // -1 marks a row the unique event id caused to be ignored, which is the normal case for anything
        // already applied.
        return dao.insertStatsEvents(rows).count { it >= 0 }
    }

    private fun toRow(event: SyncEvent): ListeningStatsEvent? {
        val payload = runCatching {
            gson.fromJson(event.payload, ListenPayload::class.java)
        }.getOrNull() ?: return null

        return ListeningStatsEvent(
            trackId = payload.trackId,
            trackTitle = payload.trackTitle,
            artistName = payload.artistName,
            artistId = payload.artistId,
            artistPermalink = payload.artistPermalink,
            artistAvatarUrl = payload.artistAvatarUrl,
            // The column is not nullable; the other device may have had none.
            artworkUrl = payload.artworkUrl.orEmpty(),
            source = payload.source,
            eventType = payload.eventType,
            listenDurationMs = payload.listenDurationMs,
            trackDurationMs = payload.trackDurationMs,
            // The other device's clock, not ours: a listen belongs to the moment it happened, which is what
            // puts it in the same week on both devices.
            timestamp = event.timestampMs,
            furthestPositionMs = payload.furthestPositionMs,
            // What makes this insert idempotent at the database level rather than at ours.
            syncEventId = event.id,
        )
    }
}
