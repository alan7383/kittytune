import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.stats.ListeningReports
import com.alananasss.kittytune.data.stats.ReportPeriod
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningReportTest {

    private val zone = ZoneId.of("Europe/Moscow")

    /** Mirrors the repository's collaboration split, so the report and the top-artist list agree. */
    private val splitter = ListeningReports.ArtistSplitter { raw ->
        raw.split(",", "&", " feat. ", " ft. ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    private fun listen(
        time: Long,
        trackId: Long = 1,
        artist: String = "A",
        listenMs: Long = 180_000,
        durationMs: Long = 180_000,
        avatar: String? = null,
        artwork: String = "cover$trackId",
    ) = ListeningStatsEvent(
        trackId = trackId, trackTitle = "T$trackId", artistName = artist, artistAvatarUrl = avatar,
        artworkUrl = artwork, eventType = "PLAY_COMPLETE", listenDurationMs = listenMs,
        trackDurationMs = durationMs, timestamp = time, furthestPositionMs = listenMs,
    )

    @Test
    fun theWeekIsTheCalendarWeekFromMonday() {
        // Thursday 25 September 2026.
        val window = ListeningReports.windowFor(ReportPeriod.WEEK, at(2026, 9, 25), zone)
        assertEquals(at(2026, 9, 21, 0), window.startMs)
        assertEquals(at(2026, 9, 28, 0), window.endMs)
    }

    @Test
    fun countsPlaysSkipsAndTheDailyBars() {
        val window = ListeningReports.windowFor(ReportPeriod.WEEK, at(2026, 9, 25), zone)
        val events = listOf(
            listen(at(2026, 9, 21, 9)),
            listen(at(2026, 9, 21, 22), trackId = 2),
            listen(at(2026, 9, 23, 22), trackId = 2, listenMs = 5_000), // a skip
            listen(at(2026, 9, 14)), // last week: not in the span
        )
        val report = ListeningReports.build(ReportPeriod.WEEK, window, events, 180_000, zone, splitter)

        assertEquals(2, report.plays)
        assertEquals(1, report.skips)
        assertEquals(7, report.activity.size)
        assertEquals(360_000L, report.activity[0].listenMs)
        assertEquals(5_000L, report.activity[2].listenMs)
        assertEquals(22, report.peakHour)
        assertEquals(1.0277778f, report.change!!, 0.001f)
    }

    @Test
    fun theLongestStreakCountsConsecutiveDays() {
        val window = ListeningReports.windowFor(ReportPeriod.MONTH, at(2026, 9, 25), zone)
        val days = listOf(1, 2, 3, 10, 11)
        val report = ListeningReports.build(ReportPeriod.MONTH, window, days.map { listen(at(2026, 9, it)) }, null, zone, splitter)
        assertEquals(3, report.longestStreakDays)
        assertEquals(5, report.activeDays)
        assertEquals(30, report.activity.size)
        assertNull(report.change)
    }

    @Test
    fun anArtistWithOnlyTheDefaultAvatarShowsTheirTopCover() {
        val window = ListeningReports.windowFor(ReportPeriod.WEEK, at(2026, 9, 25), zone)
        val events = listOf(
            listen(at(2026, 9, 22), artist = "B", avatar = "https://a1.sndcdn.com/images/default_avatar_large.png", artwork = "bcover"),
            listen(at(2026, 9, 22), trackId = 3, artist = "C", avatar = "https://i1.sndcdn.com/avatars-c.jpg"),
        )
        val report = ListeningReports.build(ReportPeriod.WEEK, window, events, null, zone, splitter)
        assertEquals("bcover", report.topArtists.first { it.name == "B" }.imageUrl)
        assertEquals("https://i1.sndcdn.com/avatars-c.jpg", report.topArtists.first { it.name == "C" }.imageUrl)
    }

    @Test
    fun aCollaborationCountsForEachOfItsArtists() {
        val window = ListeningReports.windowFor(ReportPeriod.WEEK, at(2026, 9, 25), zone)
        val events = listOf(
            listen(at(2026, 9, 22), trackId = 7, artist = "Kai Angel & 9mice", artwork = "collab"),
        )
        val report = ListeningReports.build(ReportPeriod.WEEK, window, events, null, zone, splitter)

        assertEquals(setOf("Kai Angel", "9mice"), report.topArtists.map { it.name }.toSet())
        assertEquals(2, report.uniqueArtists)
        // The avatar belongs to the first-named artist only; the other falls back to the cover.
        assertEquals(1, report.topArtists.first { it.name == "Kai Angel" }.plays)
    }

    @Test
    fun theYearIsTheCalendarYearAndBucketsByMonth() {
        val window = ListeningReports.windowFor(ReportPeriod.YEAR, at(2026, 9, 25), zone)
        assertEquals(at(2026, 1, 1, 0), window.startMs)
        assertEquals(at(2027, 1, 1, 0), window.endMs)

        val events = listOf(listen(at(2026, 2, 3)), listen(at(2026, 2, 20), trackId = 2), listen(at(2026, 5, 1), trackId = 3))
        val report = ListeningReports.build(ReportPeriod.YEAR, window, events, null, zone, splitter)

        // One bar per month of the year, February carrying both of its listens.
        assertEquals(12, report.activity.size)
        assertTrue(report.activity.all { it.isMonth })
        assertEquals(360_000L, report.activity[1].listenMs)
    }

    @Test
    fun thePreviousWindowIsTheSpanBefore() {
        val window = ListeningReports.windowFor(ReportPeriod.MONTH, at(2026, 9, 25), zone)
        val previous = ListeningReports.previousWindow(ReportPeriod.MONTH, window, zone)!!
        assertEquals(at(2026, 8, 1, 0), previous.startMs)
        assertEquals(at(2026, 9, 1, 0), previous.endMs)

        // All time has no equal-length span to compare against.
        val allTime = ListeningReports.windowFor(ReportPeriod.ALL_TIME, at(2026, 9, 25), zone, firstEventMs = at(2024, 3, 2))
        assertNull(ListeningReports.previousWindow(ReportPeriod.ALL_TIME, allTime, zone))
    }

    @Test
    fun partsOfDaySplitsTheDayIntoFourBuckets() {
        val zone2 = zone
        val window = ListeningReports.windowFor(ReportPeriod.WEEK, at(2026, 9, 25), zone2)
        val events = listOf(
            listen(at(2026, 9, 21, 2), trackId = 1), // night
            listen(at(2026, 9, 21, 8), trackId = 2), // morning
            listen(at(2026, 9, 21, 14), trackId = 3), // afternoon
            listen(at(2026, 9, 21, 20), trackId = 4), // evening
        )
        val report = ListeningReports.build(ReportPeriod.WEEK, window, events, null, zone2, splitter)
        assertEquals(4, report.partsOfDay.size)
        assertTrue(report.partsOfDay.all { it == 180_000L })
    }
}

class ListeningReportWeekStartTest {

    private val zone = ZoneId.of("America/New_York")
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) =
        LocalDateTime.of(y, m, d, h, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun theWeekFollowsTheLocalesFirstDay() {
        // Thursday 24 September 2026. In the US the week starts on Sunday the 20th, in most of
        // Europe on Monday the 21st. Assuming one of them shifts every week figure for half the
        // users, so the start day is passed in rather than fixed.
        val now = at(2026, 9, 24)

        val usWeek = ListeningReports.windowFor(ReportPeriod.WEEK, now, zone, weekStartsOn = java.time.DayOfWeek.SUNDAY)
        assertEquals(at(2026, 9, 20, 0), usWeek.startMs)
        assertEquals(at(2026, 9, 27, 0), usWeek.endMs)

        val euWeek = ListeningReports.windowFor(ReportPeriod.WEEK, now, zone, weekStartsOn = java.time.DayOfWeek.MONDAY)
        assertEquals(at(2026, 9, 21, 0), euWeek.startMs)
        assertEquals(at(2026, 9, 28, 0), euWeek.endMs)
    }

    @Test
    fun thePreviousWeekIsTheSameLengthWhicheverDayItStarts() {
        val window = ListeningReports.windowFor(
            ReportPeriod.WEEK, at(2026, 9, 24), zone, weekStartsOn = java.time.DayOfWeek.SUNDAY
        )
        val previous = ListeningReports.previousWindow(
            ReportPeriod.WEEK, window, zone, java.time.DayOfWeek.SUNDAY
        )!!
        val sevenDays = 7L * 24 * 60 * 60 * 1000
        assertEquals(sevenDays, window.endMs - window.startMs)
        assertEquals(sevenDays, previous.endMs - previous.startMs)
        assertEquals(window.startMs, previous.endMs)
    }
}
