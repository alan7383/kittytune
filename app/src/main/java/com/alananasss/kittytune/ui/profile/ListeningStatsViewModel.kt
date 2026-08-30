package com.alananasss.kittytune.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.StatsMonth
import com.alananasss.kittytune.data.local.StatsSnapshot
import com.alananasss.kittytune.data.local.TopArtistResult
import com.alananasss.kittytune.data.local.TopTrackResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * Everything the statistics screen shows for one period.
 *
 * A view over [StatsSnapshot] rather than a second set of numbers: the aggregates are computed in one
 * query, and this only names them for the screen. What it does not do any more is *derive* plays and skips
 * from how the listen ended — that came from a design where a track skipped in its last ten seconds
 * counted for nothing, and it is now decided by how much was heard (issue #33).
 */
data class PeriodStats(
    val snapshot: StatsSnapshot = StatsSnapshot(),
    val topTracks: List<TopTrackResult> = emptyList(),
    val topArtists: List<TopArtistResult> = emptyList(),
) {
    val totalListenTimeMs: Long get() = snapshot.totalListenMs
    val totalEvents: Int get() = snapshot.rows
    val totalPlays: Int get() = snapshot.plays
    val completedSongs: Int get() = snapshot.completed
    val totalSkips: Int get() = snapshot.skips
    val manualReplays: Int get() = snapshot.replays
    val repeatOneLoops: Int get() = snapshot.loops
    val uniqueTracks: Int get() = snapshot.uniqueTracks
    val uniqueArtists: Int get() = snapshot.uniqueArtists
    val skipRate: Float get() = snapshot.skipRate
    val completionRate: Float get() = snapshot.completionRate
}

data class TimelineChunk(
    val startDateMs: Long,
    val endDateMs: Long,
    val topTrack: TopTrackResult?,
    val topArtist: TopArtistResult?,
)

enum class StatsPeriod { WEEK, MONTH, ALL_TIME }

/**
 * The statistics screen's state (issue #33).
 *
 * Three things here were what made the screen take seconds to open and made the period buttons look
 * broken.
 *
 * 1. **Eleven queries per load, one at a time.** Every one of them scanned the same rows to produce a
 *    single number. They are now one query — see [com.alananasss.kittytune.data.local.StatsSnapshot] —
 *    plus the two "top" lists, and all three are memoised in the repository until something is written.
 * 2. **A timeline that discovered the shape of the history by walking it.** It stepped back a month at a
 *    time, counting the *whole table* twice per step to guess whether to continue, and called itself
 *    again when a month came up empty — so a gap in the history could spin. It now asks once which
 *    months hold anything and reads only those.
 * 3. **Loads were not cancelled.** Tapping through the three periods left three of them racing, and the
 *    slowest one won — so the screen could settle on the numbers for a period that was no longer selected.
 *
 * It also follows [ListeningStatsRepository.revision], so a sync landing while the screen is open shows
 * up rather than waiting for the next visit.
 */
class ListeningStatsViewModel(application: Application) : AndroidViewModel(application) {

    var selectedPeriod by mutableStateOf(StatsPeriod.WEEK)
        private set
    var stats by mutableStateOf(PeriodStats())
        private set
    var isLoading by mutableStateOf(true)
        private set

    var timelineChunks by mutableStateOf<List<TimelineChunk>>(emptyList())
        private set
    var isTimelineLoading by mutableStateOf(false)
        private set
    var timelineHasMore by mutableStateOf(false)
        private set

    /** Months known to hold listens, newest first. Read once per revision, not walked. */
    private var months: List<StatsMonth> = emptyList()
    private var monthsShown = 0

    private var loadJob: Job? = null

    init {
        load()
        viewModelScope.launch {
            // drop(1): the current value is what the first load already used.
            ListeningStatsRepository.revision.drop(1).collect { load() }
        }
    }

    fun selectPeriod(period: StatsPeriod) {
        if (period == selectedPeriod) return
        selectedPeriod = period
        load()
    }

    fun refreshStats() = load()

    /**
     * Loads the selected period, replacing any load still in flight.
     *
     * Cancelling matters: tapping through the three periods used to leave three loads racing, and the
     * slowest one won — so the screen could settle on the numbers for a period that was no longer
     * selected.
     */
    private fun load() {
        val period = selectedPeriod
        loadJob?.cancel()
        isLoading = true
        loadJob = viewModelScope.launch {
            val since = sinceFor(period)
            val snapshot = ListeningStatsRepository.getSnapshot(since)
            val tracks = ListeningStatsRepository.getTopTracks(since, TOP_LIMIT)
            val artists = ListeningStatsRepository.getTopArtists(since, TOP_LIMIT)
            stats = PeriodStats(snapshot = snapshot, topTracks = tracks, topArtists = artists)
            isLoading = false

            if (period == StatsPeriod.ALL_TIME) resetTimeline()
        }
    }

    private suspend fun resetTimeline() {
        months = ListeningStatsRepository.getMonths()
        timelineChunks = emptyList()
        monthsShown = 0
        timelineHasMore = months.isNotEmpty()
        loadNextTimelineChunk()
    }

    /**
     * Fills in the next few months of the timeline.
     *
     * Bounded by the months that are known to hold something, so this always terminates and never asks
     * about an empty month.
     */
    fun loadNextTimelineChunk() {
        if (isTimelineLoading || !timelineHasMore) return
        isTimelineLoading = true
        viewModelScope.launch {
            val batch = months.drop(monthsShown).take(MONTHS_PER_PAGE)
            val chunks = batch.mapNotNull { month ->
                val (start, end) = boundsOf(month)
                val topTrack = ListeningStatsRepository.getTopTracksBetween(start, end, 1).firstOrNull()
                val topArtist = ListeningStatsRepository.getTopArtistsBetween(start, end, 1).firstOrNull()
                if (topTrack == null && topArtist == null) null
                else TimelineChunk(start, end, topTrack, topArtist)
            }
            monthsShown += batch.size
            timelineChunks = timelineChunks + chunks
            timelineHasMore = monthsShown < months.size
            isTimelineLoading = false
        }
    }

    /** The first instant of [month], and the first instant of the month after it. */
    private fun boundsOf(month: StatsMonth): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            clear()
            set(month.year, month.month - 1, 1, 0, 0, 0)
        }
        val end = (start.clone() as Calendar).apply { add(Calendar.MONTH, 1) }
        return start.timeInMillis to end.timeInMillis
    }

    private fun sinceFor(period: StatsPeriod): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return when (period) {
            // The current week, from its own first day — which is Monday or Sunday depending on where
            // you are, so the calendar is asked rather than assumed.
            StatsPeriod.WEEK -> cal.apply { set(Calendar.DAY_OF_WEEK, firstDayOfWeek) }.timeInMillis
            StatsPeriod.MONTH -> cal.apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis
            StatsPeriod.ALL_TIME -> 0L
        }
    }

    private companion object {
        const val TOP_LIMIT = 10
        const val MONTHS_PER_PAGE = 6
    }
}
