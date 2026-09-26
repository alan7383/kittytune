package com.alananasss.kittytune.data.stats

import com.alananasss.kittytune.data.local.ListeningStatsEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * The spans the statistics screen can show.
 *
 * Calendar spans, not rolling ones: "this week" starts on Monday rather than seven days ago, so
 * every Monday-to-Sunday figure is comparable with the last one.
 */
enum class ReportPeriod { WEEK, MONTH, YEAR, ALL_TIME }

/** A calendar span, [startMs] inclusive and [endMs] exclusive. */
data class ReportWindow(val startMs: Long, val endMs: Long)

/** One bar of the activity chart: a day, or a month for the longer spans. */
data class ActivityBucket(val startMs: Long, val listenMs: Long, val isMonth: Boolean)

data class ReportTrack(
    val trackId: Long,
    val title: String,
    val artistName: String,
    val artworkUrl: String?,
    val source: String,
    val plays: Int,
    val listenMs: Long,
)

data class ReportArtist(
    val name: String,
    /** The artist's avatar, or — when they have none but SoundCloud's grey default — their top track's cover. */
    val imageUrl: String?,
    val artistId: Long?,
    /** SoundCloud profile URL, or `spotify:artist:…` for Spotify artists. */
    val permalink: String? = null,
    val source: String,
    val plays: Int,
    val listenMs: Long,
)

/**
 * Everything the statistics screen shows for one span, computed from the span's listens in one pass.
 *
 * Built in memory rather than by a query per number: the chart, the hours, the streak and the top
 * lists all need the same rows, and a year of listening is a few thousand small rows.
 */
data class ListeningReport(
    val window: ReportWindow,
    val totalListenMs: Long,
    /** Listening time in the span before this one, of the same length; null for "all time". */
    val previousListenMs: Long?,
    val plays: Int,
    val skips: Int,
    val completed: Int,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
    val activity: List<ActivityBucket>,
    /** Listening time per hour of the day, 0..23, local time. */
    val hours: List<Long>,
    val topTracks: List<ReportTrack>,
    val topArtists: List<ReportArtist>,
    /** Consecutive days with any listening, the longest run inside the span. */
    val longestStreakDays: Int,
    val activeDays: Int,
    /** Calendar days of the span that have begun, for "per day" averages. */
    val daysElapsed: Int = 1,
    /** The weekday with the most listening over the span, or null with none. */
    val busiestWeekday: DayOfWeek? = null,
) {
    /** Average listening per day of the span so far. */
    val averagePerDayMs: Long get() = totalListenMs / daysElapsed.coerceAtLeast(1)

    /** Listening in the night (0–6), morning (6–12), afternoon (12–18) and evening (18–24). */
    val partsOfDay: List<Long> get() = listOf(0..5, 6..11, 12..17, 18..23).map { range -> range.sumOf { hours[it] } }

    val hasData: Boolean get() = totalListenMs > 0 || plays > 0
    val skipRate: Float get() = if (plays + skips == 0) 0f else skips.toFloat() / (plays + skips)
    val completionRate: Float get() = if (plays == 0) 0f else completed.toFloat() / plays
    val averageListenMs: Long get() = if (plays == 0) 0L else totalListenMs / plays

    /** The hour with the most listening, or null with none. */
    val peakHour: Int? get() = hours.withIndex().maxByOrNull { it.value }?.takeIf { it.value > 0 }?.index

    /** Change against the previous span as a fraction (0.25 = 25 % more), or null when there is nothing to compare. */
    val change: Float?
        get() {
            val previous = previousListenMs ?: return null
            if (previous <= 0L) return null
            return (totalListenMs - previous).toFloat() / previous
        }
}

object ListeningReports {

    /**
     * Collapses a collaboration into the people in it, so "Kai Angel & 9mice" counts as two artists.
     *
     * Injected rather than reached for, because the split rules live with the repository that also
     * uses them for the top-artist list, and a report built from a different set of them would
     * disagree with the list next to it.
     */
    fun interface ArtistSplitter {
        fun split(raw: String): List<String>
    }

    /**
     * The calendar span [period] covers at [nowMs]; "all time" starts at [firstEventMs].
     *
     * [weekStartsOn] is a parameter rather than a constant because "this week" has to start on the
     * user's first day of the week — Monday in most of the world, Sunday in the US — or every
     * Monday-to-Sunday figure would be shifted for half the users.
     */
    @JvmOverloads
    fun windowFor(
        period: ReportPeriod,
        nowMs: Long,
        zone: ZoneId,
        firstEventMs: Long? = null,
        weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    ): ReportWindow {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val start = when (period) {
            ReportPeriod.WEEK -> today.with(TemporalAdjusters.previousOrSame(weekStartsOn))
            ReportPeriod.MONTH -> today.withDayOfMonth(1)
            ReportPeriod.YEAR -> today.withDayOfYear(1)
            ReportPeriod.ALL_TIME -> firstEventMs?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate().withDayOfMonth(1) } ?: today.withDayOfMonth(1)
        }
        val end = when (period) {
            ReportPeriod.WEEK -> start.plusWeeks(1)
            ReportPeriod.MONTH -> start.plusMonths(1)
            ReportPeriod.YEAR -> start.plusYears(1)
            ReportPeriod.ALL_TIME -> today.plusDays(1)
        }
        return ReportWindow(start.startMs(zone), end.startMs(zone))
    }

    /**
     * The span of the same length just before [window]: last week, last month, last year.
     *
     * [weekStartsOn] only matters for the week, and only because a week stepped back from a
     * Sunday-start week must still end on a Sunday.
     */
    @JvmOverloads
    fun previousWindow(
        period: ReportPeriod,
        window: ReportWindow,
        zone: ZoneId,
        weekStartsOn: DayOfWeek = DayOfWeek.MONDAY,
    ): ReportWindow? {
        val start = Instant.ofEpochMilli(window.startMs).atZone(zone).toLocalDate()
        val previousStart = when (period) {
            ReportPeriod.WEEK -> start.minusWeeks(1)
            ReportPeriod.MONTH -> start.minusMonths(1)
            ReportPeriod.YEAR -> start.minusYears(1)
            ReportPeriod.ALL_TIME -> return null
        }
        return ReportWindow(previousStart.startMs(zone), window.startMs)
    }

    fun build(
        period: ReportPeriod,
        window: ReportWindow,
        events: List<ListeningStatsEvent>,
        previousListenMs: Long?,
        zone: ZoneId,
        splitter: ArtistSplitter,
        topLimit: Int = 50,
        nowMs: Long = System.currentTimeMillis(),
    ): ListeningReport {
        val inWindow = events.filter { it.timestamp >= window.startMs && it.timestamp < window.endMs }
        val playRows = inWindow.filter { ListenRules.countsAsPlay(it.listenDurationMs, it.trackDurationMs) }

        val hours = LongArray(24)
        val perDay = HashMap<LocalDate, Long>()
        for (event in inWindow) {
            val time = Instant.ofEpochMilli(event.timestamp).atZone(zone)
            hours[time.hour] += event.listenDurationMs
            perDay.merge(time.toLocalDate(), event.listenDurationMs, Long::plus)
        }

        val tracks = playRows.groupBy { it.trackId }.map { (id, rows) ->
            val first = rows.first()
            ReportTrack(
                trackId = id,
                title = first.trackTitle,
                artistName = first.artistName,
                artworkUrl = rows.firstNotNullOfOrNull { it.artworkUrl.takeIf(String::isNotBlank) },
                source = first.source,
                plays = rows.size,
                listenMs = rows.sumOf { it.listenDurationMs },
            )
        }.sortedWith(compareByDescending<ReportTrack> { it.listenMs }.thenByDescending { it.plays })

        // A collaboration counts for each of its artists ("Kai Angel & 9mice" is two people). A row's
        // avatar and id belong to its first-named artist only, so the others are identified from rows
        // of their own.
        val credits = playRows.flatMap { row ->
            val names = splitter.split(row.artistName).ifEmpty { listOf(row.artistName) }
            names.mapIndexed { index, name -> Triple(name, row, index == 0) }
        }
        val artists = credits.groupBy { it.first.lowercase() }.map { (_, entries) ->
            val name = entries.first().first
            val own = entries.filter { it.third }.map { it.second }
            val avatar = own.firstNotNullOfOrNull { row -> row.artistAvatarUrl?.takeIf { isRealAvatar(it) } }
            val cover = tracks.firstOrNull { track -> entries.any { it.second.trackId == track.trackId } }?.artworkUrl
            ReportArtist(
                name = name,
                imageUrl = avatar ?: cover,
                artistId = own.firstNotNullOfOrNull { it.artistId },
                permalink = own.firstNotNullOfOrNull { it.artistPermalink },
                source = entries.first().second.source,
                plays = entries.size,
                listenMs = entries.sumOf { it.second.listenDurationMs },
            )
        }.sortedWith(compareByDescending<ReportArtist> { it.listenMs }.thenByDescending { it.plays })

        return ListeningReport(
            window = window,
            totalListenMs = inWindow.sumOf { it.listenDurationMs },
            previousListenMs = previousListenMs,
            plays = playRows.size,
            skips = inWindow.count { !isComplete(it) && !ListenRules.countsAsPlay(it.listenDurationMs, it.trackDurationMs) },
            completed = playRows.count { isComplete(it) },
            uniqueTracks = tracks.size,
            uniqueArtists = artists.size,
            activity = activity(period, window, perDay, zone),
            hours = hours.toList(),
            topTracks = tracks.take(topLimit),
            topArtists = artists.take(topLimit),
            longestStreakDays = longestStreak(perDay.filterValues { it > 0 }.keys),
            activeDays = perDay.count { it.value > 0 },
            daysElapsed = daysElapsed(window, nowMs, zone),
            busiestWeekday = perDay.entries.groupBy({ it.key.dayOfWeek }, { it.value })
                .mapValues { it.value.sum() }.filterValues { it > 0 }.maxByOrNull { it.value }?.key,
        )
    }

    /** Rows written before playback position was recorded are judged on how they ended, as in [StatsSql]. */
    private fun isComplete(event: ListeningStatsEvent): Boolean =
        if (event.furthestPositionMs > 0) ListenRules.isComplete(event.furthestPositionMs, event.trackDurationMs)
        else event.eventType == "PLAY_COMPLETE" || event.eventType == "REPEAT_ONE_LOOP"

    /** SoundCloud serves a grey silhouette to accounts without a picture; it is not worth showing. */
    private fun isRealAvatar(url: String): Boolean = url.isNotBlank() && !url.contains("default_avatar")

    private fun activity(period: ReportPeriod, window: ReportWindow, perDay: Map<LocalDate, Long>, zone: ZoneId): List<ActivityBucket> {
        val start = Instant.ofEpochMilli(window.startMs).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(window.endMs).atZone(zone).toLocalDate()
        return when (period) {
            ReportPeriod.WEEK, ReportPeriod.MONTH ->
                generateSequence(start) { it.plusDays(1) }.takeWhile { it.isBefore(end) }
                    .map { day -> ActivityBucket(day.startMs(zone), perDay[day] ?: 0L, isMonth = false) }
                    .toList()
            ReportPeriod.YEAR, ReportPeriod.ALL_TIME -> {
                val perMonth = perDay.entries.groupBy({ YearMonth.from(it.key) }, { it.value }).mapValues { it.value.sum() }
                val last = YearMonth.from(end.minusDays(1))
                // All time can be years long; the chart keeps the most recent two years of months.
                val first = YearMonth.from(start).let { if (period == ReportPeriod.ALL_TIME) maxOf(it, last.minusMonths(23)) else it }
                generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(last) }
                    .map { month -> ActivityBucket(month.atDay(1).startMs(zone), perMonth[month] ?: 0L, isMonth = true) }
                    .toList()
            }
        }
    }

    private fun daysElapsed(window: ReportWindow, nowMs: Long, zone: ZoneId): Int {
        val start = Instant.ofEpochMilli(window.startMs).atZone(zone).toLocalDate()
        val end = Instant.ofEpochMilli(minOf(nowMs, window.endMs - 1)).atZone(zone).toLocalDate()
        return (java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1).toInt().coerceAtLeast(1)
    }

    private fun longestStreak(days: Set<LocalDate>): Int {
        var best = 0
        for (day in days) {
            if (day.minusDays(1) in days) continue
            var length = 1
            while (day.plusDays(length.toLong()) in days) length++
            best = maxOf(best, length)
        }
        return best
    }

    private fun LocalDate.startMs(zone: ZoneId): Long = atStartOfDay(zone).toInstant().toEpochMilli()
}
