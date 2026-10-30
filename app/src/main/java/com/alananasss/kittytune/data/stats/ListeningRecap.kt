package com.alananasss.kittytune.data.stats

import com.alananasss.kittytune.data.local.ListeningStatsEvent
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/** What a recap covers: a calendar month, a season (three months) or a calendar year. */
enum class RecapKind { MONTH, SEASON, YEAR }

/** The four seasons, named for the hemisphere the listener is in. */
enum class Season { WINTER, SPRING, SUMMER, AUTUMN }

/**
 * One recap's span.
 *
 * Seasons are meteorological — whole months, December to February and so on — so a season is always
 * exactly three month recaps and the numbers add up. A season is identified by its first month rather
 * than its name, because the same three months are winter in Paris and summer in Sydney.
 */
data class RecapPeriod(
    val kind: RecapKind,
    /** The first month of the span. */
    val start: YearMonth,
    val window: ReportWindow,
    /** The season's name where the listener lives; null for months and years. */
    val season: Season? = null,
) {
    /** Stable across launches and devices, for navigation and the "already seen" marks. */
    val id: String
        get() = when (kind) {
            RecapKind.MONTH -> "m-$start"
            RecapKind.SEASON -> "s-$start"
            RecapKind.YEAR -> "y-${start.year}"
        }

    /** The months it spans, first inclusive, last inclusive. */
    val end: YearMonth
        get() = when (kind) {
            RecapKind.MONTH -> start
            RecapKind.SEASON -> start.plusMonths(2)
            RecapKind.YEAR -> start.plusMonths(11)
        }
}

/** One genre's slice of a recap. */
data class RecapGenre(val name: String, val listenMs: Long, val share: Float)

/**
 * The kind of listener the span describes, picked from the habits in it. One per recap, the most
 * telling one: a "night owl" who also replays one track is a night owl first.
 */
enum class RecapPersona { NIGHT_OWL, EARLY_BIRD, LOYAL_FAN, REPLAYER, EXPLORER, MARATHONER, DEVOTED, ECLECTIC }

/**
 * Everything one recap shows: the statistics screen's report for the span, plus what only makes sense
 * looking back — discoveries, genres, the rhythm of the week, the longest session, a persona.
 */
data class ListeningRecap(
    val period: RecapPeriod,
    val report: ListeningReport,
    val genres: List<RecapGenre>,
    /** Listening per weekday, Monday first. */
    val weekdays: List<Long>,
    /** Listening between 06:00 and 20:00. */
    val dayMs: Long,
    /** Listening between 20:00 and 06:00. */
    val nightMs: Long,
    val busiestDay: LocalDate?,
    val busiestDayMs: Long,
    /** Artists first heard during the span. */
    val newArtists: Int,
    /** Tracks first heard during the span. */
    val newTracks: Int,
    /** The most listened artist among those first heard during the span. */
    val topNewArtist: ReportArtist?,
    /** The track played the most times, which is not always the one listened to the longest. */
    val mostReplayed: ReportTrack?,
    /** The longest run of listening without a break of more than [ListeningRecaps.SESSION_GAP_MS]. */
    val longestSessionMs: Long,
    /** The first thing heard in the span. */
    val firstTrackTitle: String?,
    val firstTrackArtist: String?,
    val firstTrackArtwork: String?,
    val persona: RecapPersona,
) {
    val nightShare: Float get() = if (dayMs + nightMs == 0L) 0f else nightMs.toFloat() / (dayMs + nightMs)

    /** The top artist's part of all listening, 0..1. */
    val topArtistShare: Float
        get() = report.topArtists.firstOrNull()?.let {
            if (report.totalListenMs == 0L) 0f else (it.listenMs.toFloat() / report.totalListenMs).coerceIn(0f, 1f)
        } ?: 0f

    val busiestWeekday: DayOfWeek? get() = report.busiestWeekday
}

object ListeningRecaps {

    /** A pause longer than this ends a listening session. */
    const val SESSION_GAP_MS = 20 * 60_000L

    /**
     * Countries south of the equator, where December is summer. Going by the locale is a guess, but a
     * far better one than calling January "winter" in Buenos Aires.
     */
    private val SOUTHERN_COUNTRIES = setOf(
        "AR", "AU", "BO", "BR", "BW", "CL", "FJ", "LS", "MG", "MU", "MW", "MZ", "NA", "NZ", "PE", "PY",
        "SZ", "UY", "ZA", "ZM", "ZW", "AO", "ID", "PG",
    )

    fun isSouthernHemisphere(locale: Locale = Locale.getDefault()): Boolean = locale.country.uppercase() in SOUTHERN_COUNTRIES

    /** The season that begins with [firstMonth] (December, March, June or September). */
    fun seasonFor(firstMonth: Int, southern: Boolean): Season {
        val north = when (firstMonth) {
            12 -> Season.WINTER
            3 -> Season.SPRING
            6 -> Season.SUMMER
            else -> Season.AUTUMN
        }
        if (!southern) return north
        return when (north) {
            Season.WINTER -> Season.SUMMER
            Season.SUMMER -> Season.WINTER
            Season.SPRING -> Season.AUTUMN
            Season.AUTUMN -> Season.SPRING
        }
    }

    fun month(month: YearMonth, zone: ZoneId) =
        RecapPeriod(RecapKind.MONTH, month, window(month, month.plusMonths(1), zone))

    fun season(firstMonth: YearMonth, zone: ZoneId, southern: Boolean) = RecapPeriod(
        RecapKind.SEASON, firstMonth, window(firstMonth, firstMonth.plusMonths(3), zone),
        season = seasonFor(firstMonth.monthValue, southern),
    )

    fun year(year: Int, zone: ZoneId): RecapPeriod {
        val first = YearMonth.of(year, 1)
        return RecapPeriod(RecapKind.YEAR, first, window(first, first.plusYears(1), zone))
    }

    /** The season [month] falls in, as the month it starts with. */
    fun seasonStart(month: YearMonth): YearMonth {
        val offset = (month.monthValue % 12) / 3 // Dec, Jan, Feb → 0; Mar..May → 1; …
        val startMonth = if (offset == 0) 12 else offset * 3
        return if (startMonth == 12 && month.monthValue != 12) YearMonth.of(month.year - 1, 12) else YearMonth.of(month.year, startMonth)
    }

    /**
     * Every recap that is finished, newest first: the months, seasons and years that have ended since
     * [firstEventMs]. Spans that ended before listening began are left out; whether a span actually
     * holds any listening is for the caller to decide, since that needs the rows.
     *
     * Where a month, a season and a year end together, the bigger one comes first: on the 1st of
     * January the year is the news, not December.
     */
    fun finished(firstEventMs: Long?, nowMs: Long, zone: ZoneId, southern: Boolean): List<RecapPeriod> {
        if (firstEventMs == null) return emptyList()
        val first = YearMonth.from(Instant.ofEpochMilli(firstEventMs).atZone(zone))
        val current = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
        val result = mutableListOf<RecapPeriod>()
        var month = first
        while (month.isBefore(current)) {
            result += month(month, zone)
            month = month.plusMonths(1)
        }
        var seasonStart = seasonStart(first)
        while (true) {
            val period = season(seasonStart, zone, southern)
            if (period.window.endMs > nowMs) break
            result += period
            seasonStart = seasonStart.plusMonths(3)
        }
        for (year in first.year until current.year) result += year(year, zone)
        return result.sortedWith(
            compareByDescending<RecapPeriod> { it.window.endMs }.thenByDescending { it.kind.ordinal }
        )
    }

    /** The recaps in progress at [nowMs] — this month, this season, this year — soonest first. */
    fun upcoming(nowMs: Long, zone: ZoneId, southern: Boolean): List<RecapPeriod> {
        val current = YearMonth.from(Instant.ofEpochMilli(nowMs).atZone(zone))
        return listOf(
            month(current, zone),
            season(seasonStart(current), zone, southern),
            year(current.year, zone),
        ).sortedWith(compareBy<RecapPeriod> { it.window.endMs }.thenByDescending { it.kind.ordinal })
            .distinctBy { it.window.endMs }
    }

    /** The span of the same kind just before [period], for "more than last month". */
    fun previous(period: RecapPeriod, zone: ZoneId, southern: Boolean): RecapPeriod = when (period.kind) {
        RecapKind.MONTH -> month(period.start.minusMonths(1), zone)
        RecapKind.SEASON -> season(period.start.minusMonths(3), zone, southern)
        RecapKind.YEAR -> year(period.start.year - 1, zone)
    }

    fun build(
        period: RecapPeriod,
        events: List<ListeningStatsEvent>,
        previousListenMs: Long?,
        zone: ZoneId,
        splitter: ListeningReports.ArtistSplitter,
        knownArtists: Set<String>,
        knownTracks: Set<Long>,
        nowMs: Long = System.currentTimeMillis(),
    ): ListeningRecap {
        val reportPeriod = if (period.kind == RecapKind.MONTH) ReportPeriod.MONTH else ReportPeriod.YEAR
        val report = ListeningReports.build(
            period = reportPeriod,
            window = period.window,
            events = events,
            previousListenMs = previousListenMs,
            zone = zone,
            splitter = splitter,
            topLimit = 50,
            nowMs = nowMs,
        )
        val inWindow = events.filter { it.timestamp >= period.window.startMs && it.timestamp < period.window.endMs }
            .sortedBy { it.timestamp }
        val plays = inWindow.filter { ListenRules.countsAsPlay(it.listenDurationMs, it.trackDurationMs) }

        val weekdays = LongArray(7)
        val perDay = HashMap<LocalDate, Long>()
        var dayMs = 0L
        var nightMs = 0L
        for (event in inWindow) {
            val time = Instant.ofEpochMilli(event.timestamp).atZone(zone)
            weekdays[time.dayOfWeek.value - 1] += event.listenDurationMs
            perDay.merge(time.toLocalDate(), event.listenDurationMs, Long::plus)
            if (time.hour in 6 until 20) dayMs += event.listenDurationMs else nightMs += event.listenDurationMs
        }
        val busiest = perDay.maxByOrNull { it.value }?.takeIf { it.value > 0 }

        val newArtists = report.topArtists.filter { it.name.lowercase() !in knownArtists }
        // The report's list is capped; the count of discoveries should not be.
        val allArtistsHeard = plays.flatMap { row -> splitter.split(row.artistName).ifEmpty { listOf(row.artistName) } }
            .mapTo(HashSet()) { it.lowercase() }
        val newTrackCount = plays.mapTo(HashSet()) { it.trackId }.count { it !in knownTracks }

        val first = inWindow.firstOrNull { ListenRules.countsAsPlay(it.listenDurationMs, it.trackDurationMs) } ?: inWindow.firstOrNull()

        val recap = ListeningRecap(
            period = period,
            report = report,
            genres = genres(plays),
            weekdays = weekdays.toList(),
            dayMs = dayMs,
            nightMs = nightMs,
            busiestDay = busiest?.key,
            busiestDayMs = busiest?.value ?: 0L,
            newArtists = allArtistsHeard.count { it !in knownArtists },
            newTracks = newTrackCount,
            topNewArtist = newArtists.firstOrNull(),
            mostReplayed = report.topTracks.maxByOrNull { it.plays }?.takeIf { it.plays > 1 },
            longestSessionMs = longestSession(inWindow),
            firstTrackTitle = first?.trackTitle,
            firstTrackArtist = first?.artistName,
            firstTrackArtwork = first?.artworkUrl?.takeIf { it.isNotBlank() },
            persona = RecapPersona.ECLECTIC, // judged just below, from the recap itself
        )
        return recap.copy(persona = persona(recap))
    }

    /**
     * Genres by listening time. Spellings are merged ("Hip-hop", "hip hop", "HIP-HOP") under the one
     * used most, and the shares are of the listening that has a genre at all, since a share of
     * "unknown" says nothing.
     */
    internal fun genres(plays: List<ListeningStatsEvent>, limit: Int = 5): List<RecapGenre> {
        val tagged = plays.filter { !it.genre.isNullOrBlank() }
        if (tagged.isEmpty()) return emptyList()
        val total = tagged.sumOf { it.listenDurationMs }.coerceAtLeast(1L)
        return tagged.groupBy { genreKey(it.genre!!) }
            .filterKeys { it.isNotEmpty() }
            .map { (_, rows) ->
                val spelling = rows.groupingBy { it.genre!!.trim() }.eachCount().maxBy { it.value }.key
                val ms = rows.sumOf { it.listenDurationMs }
                RecapGenre(prettyGenre(spelling), ms, ms.toFloat() / total)
            }
            .sortedByDescending { it.listenMs }
            .take(limit)
    }

    private fun genreKey(raw: String) = raw.lowercase().filter { it.isLetterOrDigit() }

    private fun prettyGenre(raw: String): String =
        if (raw.any { it.isUpperCase() }) raw
        else raw.split(' ').joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

    /** Listens are recorded when they end; one starts [ListeningStatsEvent.listenDurationMs] before. */
    internal fun longestSession(sorted: List<ListeningStatsEvent>): Long {
        var best = 0L
        var current = 0L
        var lastEnd = Long.MIN_VALUE
        for (event in sorted) {
            val start = event.timestamp - event.listenDurationMs
            current = if (lastEnd != Long.MIN_VALUE && start - lastEnd <= SESSION_GAP_MS) current + event.listenDurationMs else event.listenDurationMs
            lastEnd = maxOf(lastEnd, event.timestamp)
            best = maxOf(best, current)
        }
        return best
    }

    internal fun persona(recap: ListeningRecap): RecapPersona {
        val report = recap.report
        val total = report.totalListenMs.coerceAtLeast(1L)
        val smallHours = (0..4).sumOf { report.hours[it] }.toFloat() / total
        val morning = (5..8).sumOf { report.hours[it] }.toFloat() / total
        val replayRatio = if (report.uniqueTracks == 0) 0f else report.plays.toFloat() / report.uniqueTracks
        val days = report.daysElapsed.coerceAtLeast(1)
        return when {
            smallHours >= 0.25f -> RecapPersona.NIGHT_OWL
            morning >= 0.3f -> RecapPersona.EARLY_BIRD
            recap.topArtistShare >= 0.35f && report.uniqueArtists >= 3 -> RecapPersona.LOYAL_FAN
            replayRatio >= 3f -> RecapPersona.REPLAYER
            report.uniqueArtists >= 10 && recap.newArtists >= report.uniqueArtists / 2 -> RecapPersona.EXPLORER
            recap.longestSessionMs >= 3 * 3_600_000L -> RecapPersona.MARATHONER
            days >= 7 && report.activeDays.toFloat() / days >= 0.8f -> RecapPersona.DEVOTED
            else -> RecapPersona.ECLECTIC
        }
    }

    private fun window(from: YearMonth, until: YearMonth, zone: ZoneId) = ReportWindow(
        from.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
        until.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
    )
}
