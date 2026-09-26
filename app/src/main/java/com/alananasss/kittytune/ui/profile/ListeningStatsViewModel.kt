package com.alananasss.kittytune.ui.profile

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ListeningReports
import com.alananasss.kittytune.data.stats.ReportPeriod
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZoneId
import java.util.Calendar

enum class StatsPeriod { WEEK, MONTH, YEAR, ALL_TIME }

/** [StatsPeriod] as the report layer names it. */
fun StatsPeriod.toReportPeriod(): ReportPeriod = when (this) {
    StatsPeriod.WEEK -> ReportPeriod.WEEK
    StatsPeriod.MONTH -> ReportPeriod.MONTH
    StatsPeriod.YEAR -> ReportPeriod.YEAR
    StatsPeriod.ALL_TIME -> ReportPeriod.ALL_TIME
}

/**
 * The statistics screen's state.
 *
 * One load produces one [ListeningReport] and the rows behind it, rather than a query per number:
 * the totals, the activity chart, the hours, the streak and the three top lists all read the same
 * span, so they are read once and derived together. A week therefore has a week before it to be
 * compared against, and a year can be asked for at all.
 *
 * It also follows [ListeningStatsRepository.revision], so a sync landing while the screen is open
 * shows up rather than waiting for the next visit.
 */
class ListeningStatsViewModel(application: Application) : AndroidViewModel(application) {

    var selectedPeriod by mutableStateOf(StatsPeriod.WEEK)
        private set

    /** Everything the screen shows for the selected span. Null until the first load lands. */
    var report by mutableStateOf<ListeningReport?>(null)
        private set

    /**
     * The rows the report was built from, newest first.
     *
     * Kept so the "every play" list can show what actually happened rather than only the aggregates:
     * which track, at what time, for how long. It is the same read the report used, not a second one.
     */
    var events by mutableStateOf<List<ListeningStatsEvent>>(emptyList())
        private set

    var isLoading by mutableStateOf(true)
        private set

    private var loadJob: Job? = null

    /** The repository's own split, so the report and the top-artist list cannot disagree. */
    private val splitter = ListeningReports.ArtistSplitter { raw ->
        ListeningStatsRepository.splitArtistNames(raw)
    }

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
     * Cancelling matters: tapping through the periods used to leave several loads racing, and the
     * slowest one won — so the screen could settle on the numbers for a period that was no longer
     * selected.
     */
    private fun load() {
        val period = selectedPeriod
        loadJob?.cancel()
        isLoading = true
        loadJob = viewModelScope.launch {
            val zone = ZoneId.systemDefault()
            val weekStartsOn = firstDayOfWeekAsDayOfWeek()
            val now = System.currentTimeMillis()

            // "All time" has to start where the history does, not at the epoch: the window is what
            // the activity chart is measured against, and a chart that opens on years of empty
            // months says nothing.
            val firstEventMs = if (period == StatsPeriod.ALL_TIME) {
                ListeningStatsRepository.getOldestEventAt()
            } else null

            val window = ListeningReports.windowFor(
                period.toReportPeriod(), now, zone, firstEventMs, weekStartsOn
            )
            val previous = ListeningReports.previousWindow(period.toReportPeriod(), window, zone, weekStartsOn)

            // One read of the rows serves the whole report. Bounded, because the report is built in
            // memory and a heavy year of listening is tens of thousands of rows.
            val rows = ListeningStatsRepository.getRecentEvents(window.startMs, REPORT_EVENT_LIMIT)
            val previousListenMs = previous?.let { ListeningStatsRepository.getTotalListenTime(it.startMs, it.endMs) }

            report = ListeningReports.build(
                period = period.toReportPeriod(),
                window = window,
                events = rows,
                previousListenMs = previousListenMs,
                zone = zone,
                splitter = splitter,
                nowMs = now,
            )
            events = rows
            isLoading = false
        }
    }

    /**
     * The locale's first day of the week, so "this week" starts on Sunday in the US and Monday
     * nearly everywhere else. Guessing Monday would shift every weekly figure for half the users.
     */
    private fun firstDayOfWeekAsDayOfWeek(): DayOfWeek = when (Calendar.getInstance().firstDayOfWeek) {
        Calendar.SUNDAY -> DayOfWeek.SUNDAY
        Calendar.MONDAY -> DayOfWeek.MONDAY
        Calendar.TUESDAY -> DayOfWeek.TUESDAY
        Calendar.WEDNESDAY -> DayOfWeek.WEDNESDAY
        Calendar.THURSDAY -> DayOfWeek.THURSDAY
        Calendar.FRIDAY -> DayOfWeek.FRIDAY
        else -> DayOfWeek.SATURDAY
    }

    private companion object {
        /**
         * Rows read to build the report. It is computed in memory from these, so it is capped rather
         * than pulling in a whole history.
         */
        const val REPORT_EVENT_LIMIT = 40_000
    }
}

/** [ReportPeriod] as the screen's selector names it. */
fun ReportPeriod.toStatsPeriod(): StatsPeriod = when (this) {
    ReportPeriod.WEEK -> StatsPeriod.WEEK
    ReportPeriod.MONTH -> StatsPeriod.MONTH
    ReportPeriod.YEAR -> StatsPeriod.YEAR
    ReportPeriod.ALL_TIME -> StatsPeriod.ALL_TIME
}
