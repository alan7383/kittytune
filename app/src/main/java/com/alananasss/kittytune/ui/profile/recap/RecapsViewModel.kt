package com.alananasss.kittytune.ui.profile.recap

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.stats.ListeningRecap
import com.alananasss.kittytune.data.stats.ListeningRecaps
import com.alananasss.kittytune.data.stats.ListeningReports
import com.alananasss.kittytune.data.stats.RecapKind
import com.alananasss.kittytune.data.stats.RecapPeriod
import com.alananasss.kittytune.data.stats.ReportPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.time.ZoneId

/** A finished recap as the carousel shows it: enough for the card, not the whole story. */
data class RecapSummary(
    val period: RecapPeriod,
    val totalListenMs: Long,
    val plays: Int,
    val topArtistName: String?,
    val topArtistImage: String?,
    val topTrackArtwork: String?,
    val isNew: Boolean,
)

/** Rows read to build one recap; a year of heavy listening fits. */
private const val RECAP_EVENT_LIMIT = 60_000

private val splitter = ListeningReports.ArtistSplitter { raw -> ListeningStatsRepository.splitArtistNames(raw) }

/** Which recaps have been opened, so the new ones can say so. Local only: it is a nicety, not data. */
internal class RecapSeenStore(context: Context) {
    private val prefs = context.getSharedPreferences("listening_recaps", Context.MODE_PRIVATE)
    fun seen(): Set<String> = prefs.getStringSet(KEY, emptySet()).orEmpty()
    fun markSeen(id: String) {
        prefs.edit().putStringSet(KEY, seen() + id).apply()
    }

    private companion object {
        const val KEY = "seen_ids"
    }
}

/**
 * The finished recaps — every month, season and year with listening in it — and the ones still in
 * progress.
 *
 * Everything is computed from the local listening history, so it works the same signed out: a guest's
 * listens are recorded like anyone's.
 */
class RecapsViewModel(application: Application) : AndroidViewModel(application) {

    private val seenStore = RecapSeenStore(application)

    var summaries by mutableStateOf<List<RecapSummary>>(emptyList())
        private set

    var upcoming by mutableStateOf<List<RecapPeriod>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch { ListeningStatsRepository.revision.drop(1).collect { load() } }
    }

    fun refresh() = load()

    fun markSeen(id: String) {
        seenStore.markSeen(id)
        summaries = summaries.map { if (it.period.id == id) it.copy(isNew = false) else it }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val now = System.currentTimeMillis()
            val southern = ListeningRecaps.isSouthernHemisphere()
            val first = ListeningStatsRepository.getOldestEventAt()
            upcoming = ListeningRecaps.upcoming(now, zone, southern)
            val seen = seenStore.seen()

            val built = mutableListOf<RecapSummary>()
            for (period in ListeningRecaps.finished(first, now, zone, southern)) {
                val rows = ListeningStatsRepository.getRecentEventsBetween(period.window.startMs, period.window.endMs, RECAP_EVENT_LIMIT)
                if (rows.isEmpty()) continue
                val report = ListeningReports.build(
                    period = if (period.kind == RecapKind.MONTH) ReportPeriod.MONTH else ReportPeriod.YEAR,
                    window = period.window,
                    events = rows,
                    previousListenMs = null,
                    zone = zone,
                    splitter = splitter,
                    topLimit = 1,
                    nowMs = now,
                )
                if (!report.hasData) continue
                val artist = report.topArtists.firstOrNull()
                built += RecapSummary(
                    period = period,
                    totalListenMs = report.totalListenMs,
                    plays = report.plays,
                    topArtistName = artist?.name,
                    topArtistImage = artist?.imageUrl,
                    topTrackArtwork = report.topTracks.firstOrNull()?.artworkUrl,
                    isNew = period.id !in seen,
                )
                // Shown as they come on the first load, which can be long; a reload keeps the old list
                // until the new one is whole, so the carousel does not shrink and grow back.
                if (isLoading) summaries = built.toList()
            }
            summaries = built.toList()
            isLoading = false
        }
    }
}

/**
 * One recap, in full, for the story.
 *
 * Listens recorded before genres were kept have none; the first time a recap is opened, the genres of
 * the tracks it is about are asked for once and written back, and the recap is rebuilt with them.
 */
class RecapStoryViewModel(application: Application) : AndroidViewModel(application) {

    var recap by mutableStateOf<ListeningRecap?>(null)
        private set

    var isMissing by mutableStateOf(false)
        private set

    private var requestedId: String? = null
    private val backfilled = HashSet<String>()

    fun load(id: String) {
        if (id == requestedId && recap != null) return
        requestedId = id
        viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val southern = ListeningRecaps.isSouthernHemisphere()
            val period = parse(id, zone, southern)
            if (period == null) {
                isMissing = true
                return@launch
            }
            val built = build(period, zone, southern)
            recap = built
            isMissing = built == null
            RecapSeenStore(getApplication()).markSeen(id)

            if (built != null && id !in backfilled) {
                backfilled += id
                if (backfillGenres(period)) recap = build(period, zone, southern)
            }
        }
    }

    private suspend fun build(period: RecapPeriod, zone: ZoneId, southern: Boolean): ListeningRecap? {
        val rows = ListeningStatsRepository.getRecentEventsBetween(period.window.startMs, period.window.endMs, RECAP_EVENT_LIMIT)
        if (rows.isEmpty()) return null
        val previous = ListeningRecaps.previous(period, zone, southern)
        val previousMs = ListeningStatsRepository.getTotalListenTime(previous.window.startMs, previous.window.endMs)
        return ListeningRecaps.build(
            period = period,
            events = rows,
            previousListenMs = previousMs,
            zone = zone,
            splitter = splitter,
            knownArtists = ListeningStatsRepository.getArtistNamesBefore(period.window.startMs),
            knownTracks = ListeningStatsRepository.getTrackIdsBefore(period.window.startMs),
        ).takeIf { it.report.hasData }
    }

    /** True when genres were found and written, so the recap is worth rebuilding. */
    private suspend fun backfillGenres(period: RecapPeriod): Boolean {
        val rows = ListeningStatsRepository.getRecentEventsBetween(period.window.startMs, period.window.endMs, RECAP_EVENT_LIMIT)
        val tagged = rows.filter { !it.genre.isNullOrBlank() }.mapTo(HashSet()) { it.trackId }
        // The tracks that carry the listening, most first; the long tail barely moves the shares.
        val missing = rows.asSequence()
            .filter { it.source == "soundcloud" && it.trackId > 0 && it.trackId !in tagged }
            .groupBy { it.trackId }
            .entries.sortedByDescending { (_, list) -> list.sumOf { it.listenDurationMs } }
            .take(GENRE_BACKFILL_TRACKS)
            .map { it.key }
        if (missing.isEmpty()) return false
        val api = RetrofitClient.create(getApplication())
        return ListeningStatsRepository.backfillGenres(missing) { ids ->
            ids.chunked(50).flatMap { chunk ->
                runCatching { api.getTracksByIds(chunk.joinToString(",")) }.getOrDefault(emptyList())
            }.mapNotNull { track -> track.genre?.takeIf { it.isNotBlank() }?.let { track.id to it } }.toMap()
        }
    }

    private companion object {
        const val GENRE_BACKFILL_TRACKS = 100

        fun parse(id: String, zone: ZoneId, southern: Boolean): RecapPeriod? = runCatching {
            when {
                id.startsWith("m-") -> ListeningRecaps.month(YearMonth.parse(id.removePrefix("m-")), zone)
                id.startsWith("s-") -> ListeningRecaps.season(YearMonth.parse(id.removePrefix("s-")), zone, southern)
                id.startsWith("y-") -> ListeningRecaps.year(id.removePrefix("y-").toInt(), zone)
                else -> null
            }
        }.getOrNull()
    }
}
