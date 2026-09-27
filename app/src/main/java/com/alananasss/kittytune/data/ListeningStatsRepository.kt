package com.alananasss.kittytune.data

import android.content.Context
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.local.StatsMonth
import com.alananasss.kittytune.data.local.StatsSnapshot
import com.alananasss.kittytune.data.local.TopArtistResult
import com.alananasss.kittytune.data.local.TopTrackResult
import com.alananasss.kittytune.data.sync.ListenPayload
import com.alananasss.kittytune.data.sync.SyncKinds
import com.alananasss.kittytune.data.sync.SyncLog
import com.alananasss.kittytune.data.sync.SyncScheduler
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Listening statistics: recording them, reading them back, and keeping the two devices' numbers equal.
 *
 * The same shape as the desktop's copy on purpose. Two devices that disagree about what a listen is
 * cannot be made to agree by syncing harder, and the two had genuinely drifted: this side counted a
 * listen by which button ended the track, the other by how much was heard (issue #33).
 *
 * Two things here are load-bearing beyond the obvious.
 *
 * **A listen is written to the sync log first, and the log's event id becomes the row's identity.** The
 * log is the record of what happened; the table is what this device shows. Numbering the row with the
 * event means the same listen can never appear twice, whichever device it reaches and however many
 * times: the insert is `INSERT OR IGNORE` on a unique id.
 *
 * **Reads are memoised against [revision].** Every aggregate is a pure function of the table, so a repeat
 * of the same question between two writes has one answer, and the statistics screen asks the same
 * questions every time it opens. Nothing is invalidated by a timer — only by a write — so the cache cannot
 * go stale.
 */
object ListeningStatsRepository {
    private lateinit var database: AppDatabase
    private val scope = CoroutineScope(Dispatchers.IO)

    private val dao get() = database.downloadDao()

    /**
     * Bumped whenever the table changes, by a listen here or a listen merged from the other device.
     *
     * Screens collect it instead of polling, which is what makes a sync that lands while the statistics
     * are open show up rather than waiting for the next visit.
     */
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision

    /**
     * Bounded, because one of the callers keys on "a week ago" computed from the clock — a key that is
     * different every time it is asked.
     */
    private val cache = object : LinkedHashMap<String, Cached>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cached>?) = size > 40
    }

    private data class Cached(val revision: Long, val value: Any?)

    fun init(context: Context) {
        database = AppDatabase.getDatabase(context)
    }

    /** Called after anything that changes the table, from either side. */
    fun onStatsChanged() {
        synchronized(cache) { cache.clear() }
        _revision.value = _revision.value + 1
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun <T> memo(key: String, compute: suspend () -> T): T {
        val current = _revision.value
        synchronized(cache) { cache[key] }?.let { if (it.revision == current) return it.value as T }
        val value = compute()
        // Recorded against the revision the work started from: a write that landed while we were
        // computing must not have its result attributed to the newer state.
        synchronized(cache) { cache[key] = Cached(current, value) }
        return value
    }

    /**
     * Records a finished listen, on this device and in the log the other device reads.
     *
     * @param furthestPositionMs how far playback reached, which is what completion is judged on.
     */
    fun recordEvent(
        track: Track,
        eventType: String,
        listenDurationMs: Long = 0,
        furthestPositionMs: Long = 0,
    ) {
        scope.launch {
            val timestamp = System.currentTimeMillis()
            val effectiveArtist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }.ifBlank { "Unknown" }
            val primaryArtist = track.artists?.firstOrNull()
            val payload = ListenPayload(
                trackId = track.id,
                trackTitle = track.title ?: "Unknown",
                artistName = effectiveArtist,
                artistId = track.user?.id,
                artistPermalink = primaryArtist?.id ?: track.user?.permalinkUrl,
                artistAvatarUrl = primaryArtist?.avatarUrl ?: track.user?.avatarUrl,
                artworkUrl = track.fullResArtwork,
                source = track.source ?: "soundcloud",
                eventType = eventType,
                listenDurationMs = listenDurationMs,
                trackDurationMs = track.durationMs ?: 0L,
                furthestPositionMs = furthestPositionMs,
            )

            // The log first, so the row can carry the event's id and be immune to being applied twice.
            // A log that cannot be written is not a reason to lose the listen, so the id is simply absent
            // in that case and the row goes in unnumbered.
            val eventId = runCatching {
                SyncLog.append(kind = SyncKinds.LISTEN, payload = payload, timestampMs = timestamp).id
            }.getOrNull()

            dao.insertStatsEvent(
                ListeningStatsEvent(
                    trackId = payload.trackId,
                    trackTitle = payload.trackTitle,
                    artistName = payload.artistName,
                    artistId = payload.artistId,
                    artistPermalink = payload.artistPermalink,
                    artistAvatarUrl = payload.artistAvatarUrl,
                    artworkUrl = payload.artworkUrl.orEmpty(),
                    source = payload.source,
                    eventType = payload.eventType,
                    listenDurationMs = payload.listenDurationMs,
                    trackDurationMs = payload.trackDurationMs,
                    timestamp = timestamp,
                    furthestPositionMs = payload.furthestPositionMs,
                    syncEventId = eventId,
                )
            )
            onStatsChanged()
            // The other device should not have to wait for the next heartbeat to hear about this.
            // Debounced and coalesced inside the scheduler, so a shuffled album does not become fifteen
            // exchanges.
            SyncScheduler.requestSync("listen recorded")
        }
    }

    /** Every headline number for a span, in one query and cached until the table changes. */
    suspend fun getSnapshot(since: Long): StatsSnapshot =
        memo("snapshot:$since") {
            val base = dao.getStatsSnapshot(since)
            val distinctRaw = dao.getDistinctArtistNamesAfter(since)
            val calculatedUniqueArtists = distinctRaw
                .flatMap { splitArtistNames(it) }
                .map { it.trim().lowercase() }
                .filter { it.isNotBlank() }
                .distinct()
                .size
            base.copy(uniqueArtists = calculatedUniqueArtists)
        }

    /** Which calendar months hold listens, newest first. */
    suspend fun getMonths(): List<StatsMonth> = memo("months") { dao.getStatsMonths() }

    suspend fun getTopTracks(since: Long, limit: Int = 10): List<TopTrackResult> =
        memo("topTracks:$since:$limit") { dao.getTopTracksAfter(since, limit) }

    suspend fun getTopArtists(since: Long, limit: Int = 10): List<TopArtistResult> =
        memo("topArtists:$since:$limit") {
            val raw = dao.getRawTopArtistsAfter(since)
            aggregateAndSplitTopArtists(raw, limit)
        }

    suspend fun getTopTracksBetween(since: Long, until: Long, limit: Int = 1): List<TopTrackResult> =
        memo("topTracksIn:$since:$until:$limit") { dao.getTopTracksBetween(since, until, limit) }

    suspend fun getTopArtistsBetween(since: Long, until: Long, limit: Int = 1): List<TopArtistResult> =
        memo("topArtistsIn:$since:$until:$limit") {
            val raw = dao.getRawTopArtistsBetween(since, until)
            aggregateAndSplitTopArtists(raw, limit)
        }

    private val PRESERVED_ARTISTS_WITH_COMMA = listOf(
        "Tyler, The Creator",
        "Crosby, Stills, Nash & Young",
        "Crosby, Stills & Nash",
        "Emerson, Lake & Palmer",
        "Blood, Sweat & Tears"
    )

    private val PRESERVED_ARTISTS_WITH_AMP = setOf(
        "earth, wind & fire",
        "crosby, stills, nash & young",
        "crosby, stills & nash",
        "emerson, lake & palmer",
        "blood, sweat & tears",
        "simon & garfunkel",
        "hall & oates",
        "brooks & dunn",
        "above & beyond",
        "iron & wine",
        "florence + the machine",
        "florence and the machine",
        "tom petty and the heartbreakers",
        "bob marley & the wailers",
        "joan jett & the blackhearts",
        "kc and the sunshine band",
        "huey lewis & the news"
    )

    fun splitArtistNames(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()

        val lower = trimmed.lowercase()
        if (PRESERVED_ARTISTS_WITH_AMP.contains(lower)) {
            return listOf(trimmed)
        }

        var protectedStr = trimmed
        val replacements = mutableListOf<Pair<String, String>>()
        for (preserved in PRESERVED_ARTISTS_WITH_COMMA) {
            if (protectedStr.contains(preserved, ignoreCase = true)) {
                val placeholder = "__PRESERVED_${replacements.size}__"
                replacements.add(placeholder to preserved)
                protectedStr = protectedStr.replace(Regex(Regex.escape(preserved), RegexOption.IGNORE_CASE), placeholder)
            }
        }

        val parts = protectedStr.split(Regex(""",\s*|\s+&\s+|\s+(?:feat\.?|ft\.?)\s+""", RegexOption.IGNORE_CASE))
        return parts.mapNotNull { part ->
            var restored = part.trim()
            for ((ph, original) in replacements) {
                restored = restored.replace(ph, original)
            }
            restored.takeIf { it.isNotBlank() }
        }.distinctBy { it.lowercase() }
    }

    fun aggregateAndSplitTopArtists(raw: List<TopArtistResult>, limit: Int): List<TopArtistResult> {
        if (raw.isEmpty()) return emptyList()

        class ArtistAcc(
            var displayName: String,
            var artworkUrl: String? = null,
            var artistId: Long? = null,
            var artistPermalink: String? = null,
            var source: String? = null,
            var playCount: Int = 0,
            var totalListenMs: Long = 0L,
        )

        val accMap = linkedMapOf<String, ArtistAcc>()

        for (item in raw) {
            val names = splitArtistNames(item.artistName)
            val isMulti = names.size > 1
            for (name in names) {
                val key = name.lowercase()
                val acc = accMap.getOrPut(key) {
                    ArtistAcc(displayName = name)
                }
                acc.playCount += item.playCount
                acc.totalListenMs += item.totalListenMs

                if (!isMulti) {
                    if (!item.artworkUrl.isNullOrBlank()) acc.artworkUrl = item.artworkUrl
                    if (item.artistId != null && item.artistId > 0) acc.artistId = item.artistId
                    if (!item.artistPermalink.isNullOrBlank()) acc.artistPermalink = item.artistPermalink
                    if (!item.source.isNullOrBlank()) acc.source = item.source
                    acc.displayName = name
                } else {
                    if (acc.artworkUrl == null && !item.artworkUrl.isNullOrBlank()) acc.artworkUrl = item.artworkUrl
                    if (acc.artistId == null && item.artistId != null && item.artistId > 0) acc.artistId = item.artistId
                    if (acc.artistPermalink == null && !item.artistPermalink.isNullOrBlank()) acc.artistPermalink = item.artistPermalink
                    if (acc.source == null && !item.source.isNullOrBlank()) acc.source = item.source
                }
            }
        }

        return accMap.values
            .sortedWith(compareByDescending<ArtistAcc> { it.totalListenMs }.thenByDescending { it.playCount })
            .take(limit)
            .map {
                TopArtistResult(
                    artistName = it.displayName,
                    artworkUrl = it.artworkUrl,
                    artistId = it.artistId,
                    artistPermalink = it.artistPermalink,
                    source = it.source,
                    playCount = it.playCount,
                    totalListenMs = it.totalListenMs
                )
            }
    }

    suspend fun getTotalListenTime(since: Long): Long = getSnapshot(since).totalListenMs
    suspend fun getTotalEvents(since: Long): Int = getSnapshot(since).rows
    suspend fun getUniqueTracks(since: Long): Int = getSnapshot(since).uniqueTracks
    suspend fun getUniqueArtists(since: Long): Int = getSnapshot(since).uniqueArtists
    suspend fun getEventCount(type: String, since: Long): Int = dao.getEventCountByType(type, since)
    suspend fun getEvents(since: Long): List<ListeningStatsEvent> = dao.getEventsAfter(since)

    /** The most recent [limit] rows from [since], newest first. */
    suspend fun getRecentEvents(since: Long, limit: Int): List<ListeningStatsEvent> =
        dao.getRecentEventsAfter(since, limit)

    /** Listening time inside a closed range, for comparing a span against the one before it. */
    suspend fun getTotalListenTime(since: Long, until: Long): Long =
        dao.getTotalListenTimeBetween(since, until)

    /**
     * When the first listen was recorded, or null with none.
     *
     * "All time" is a window like any other, so it has to begin where the history does rather than
     * at the epoch — otherwise the activity chart opens on years of empty months.
     */
    suspend fun getOldestEventAt(): Long? = getSnapshot(0L).firstAtMs?.takeIf { it > 0L }

    fun clearStats() {
        scope.launch {
            dao.clearStats()
            // The log is the source; leaving it behind would refill the table on the next sync.
            SyncLog.clear()
            onStatsChanged()
        }
    }
}
