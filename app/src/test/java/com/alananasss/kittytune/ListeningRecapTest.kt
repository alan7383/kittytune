import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.stats.ListeningRecaps
import com.alananasss.kittytune.data.stats.ListeningReports
import com.alananasss.kittytune.data.stats.RecapKind
import com.alananasss.kittytune.data.stats.RecapPersona
import com.alananasss.kittytune.data.stats.Season
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningRecapTest {

    private val zone = ZoneId.of("Europe/Paris")

    private val splitter = ListeningReports.ArtistSplitter { raw ->
        raw.split(",", "&").map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) =
        LocalDateTime.of(y, m, d, h, min).atZone(zone).toInstant().toEpochMilli()

    private fun listen(
        time: Long,
        trackId: Long = 1,
        artist: String = "A",
        listenMs: Long = 180_000,
        genre: String? = null,
    ) = ListeningStatsEvent(
        trackId = trackId, trackTitle = "T$trackId", artistName = artist, artworkUrl = "cover$trackId",
        eventType = "PLAY_COMPLETE", listenDurationMs = listenMs, trackDurationMs = 180_000,
        timestamp = time, furthestPositionMs = listenMs, genre = genre,
    )

    @Test
    fun seasonsStartOnTheMeteorologicalMonths() {
        assertEquals(YearMonth.of(2025, 12), ListeningRecaps.seasonStart(YearMonth.of(2026, 1)))
        assertEquals(YearMonth.of(2025, 12), ListeningRecaps.seasonStart(YearMonth.of(2026, 2)))
        assertEquals(YearMonth.of(2026, 12), ListeningRecaps.seasonStart(YearMonth.of(2026, 12)))
        assertEquals(YearMonth.of(2026, 3), ListeningRecaps.seasonStart(YearMonth.of(2026, 5)))
        assertEquals(YearMonth.of(2026, 6), ListeningRecaps.seasonStart(YearMonth.of(2026, 8)))
        assertEquals(YearMonth.of(2026, 9), ListeningRecaps.seasonStart(YearMonth.of(2026, 11)))
    }

    @Test
    fun theSouthernHemisphereHasSummerInDecember() {
        assertEquals(Season.WINTER, ListeningRecaps.seasonFor(12, southern = false))
        assertEquals(Season.SUMMER, ListeningRecaps.seasonFor(12, southern = true))
        assertEquals(Season.AUTUMN, ListeningRecaps.seasonFor(3, southern = true))
    }

    @Test
    fun onlyFinishedSpansSinceTheFirstListenAreRecaps() {
        // History from 10 May 2026, today 30 October 2026.
        val periods = ListeningRecaps.finished(at(2026, 5, 10), at(2026, 10, 30), zone, southern = false)
        val months = periods.filter { it.kind == RecapKind.MONTH }.map { it.start }
        assertEquals((5..9).map { YearMonth.of(2026, it) }.reversed(), months)
        // Spring (Mar–May) is finished and has listening; summer too; autumn is not over.
        val seasons = periods.filter { it.kind == RecapKind.SEASON }.map { it.season }
        assertEquals(listOf(Season.SUMMER, Season.SPRING), seasons)
        assertTrue(periods.none { it.kind == RecapKind.YEAR })
        // Newest first; on the same end date the season comes before the month.
        assertEquals("m-2026-09", periods.first().id)
        assertEquals(listOf("s-2026-06", "m-2026-08"), periods.drop(1).take(2).map { it.id })
    }

    @Test
    fun theYearComesFirstOnNewYearsDay() {
        val periods = ListeningRecaps.finished(at(2025, 11, 3), at(2026, 1, 1, 9), zone, southern = false)
        assertEquals(listOf("y-2025", "m-2025-12"), periods.take(2).map { it.id })
        // Winter 2025–26 is still going.
        assertTrue(periods.none { it.id == "s-2025-12" })
    }

    @Test
    fun upcomingListsTheSpansInProgressSoonestFirst() {
        val upcoming = ListeningRecaps.upcoming(at(2026, 10, 30), zone, southern = false)
        assertEquals(listOf("m-2026-10", "s-2026-09", "y-2026"), upcoming.map { it.id })
    }

    @Test
    fun winterIsLabelledWithBothYearsAndEndsInMarch() {
        val winter = ListeningRecaps.season(YearMonth.of(2025, 12), zone, southern = false)
        assertEquals(at(2025, 12, 1, 0), winter.window.startMs)
        assertEquals(at(2026, 3, 1, 0), winter.window.endMs)
        assertEquals(YearMonth.of(2026, 2), winter.end)
    }

    @Test
    fun buildsDiscoveriesGenresAndTheRhythm() {
        val period = ListeningRecaps.month(YearMonth.of(2026, 9), zone)
        val events = listOf(
            listen(at(2026, 9, 7, 23), trackId = 1, artist = "Old", genre = "hip-hop"), // Monday night
            listen(at(2026, 9, 7, 23, 4), trackId = 2, artist = "New & Old", genre = "Hip Hop"),
            listen(at(2026, 9, 8, 10), trackId = 3, artist = "Fresh", genre = "House", listenMs = 60_000),
            listen(at(2026, 8, 30), trackId = 9, artist = "Elsewhere"), // previous month
        )
        val recap = ListeningRecaps.build(
            period, events, previousListenMs = 0L, zone = zone, splitter = splitter,
            knownArtists = setOf("old"), knownTracks = setOf(1L), nowMs = at(2026, 10, 30),
        )

        assertEquals(3, recap.report.plays)
        assertEquals(2, recap.newArtists) // "New" and "Fresh"
        assertEquals(2, recap.newTracks)
        // Both spellings of hip-hop are one genre, ahead of house.
        assertEquals(2, recap.genres.size)
        assertEquals(360_000L, recap.genres.first().listenMs)
        assertEquals(360_000L, recap.nightMs)
        assertEquals(60_000L, recap.dayMs)
        assertEquals(360_000L, recap.weekdays[0])
        assertEquals("T1", recap.firstTrackTitle)
        // The two night listens are four minutes apart, so they are one session.
        assertEquals(360_000L, recap.longestSessionMs)
        assertNull(recap.mostReplayed)
    }

    @Test
    fun aRecapAtTwoInTheMorningIsANightOwls() {
        val period = ListeningRecaps.month(YearMonth.of(2026, 9), zone)
        val events = (1..10).map { listen(at(2026, 9, it, 2), trackId = it.toLong(), artist = "A$it") }
        val recap = ListeningRecaps.build(
            period, events, null, zone, splitter, emptySet(), emptySet(), nowMs = at(2026, 10, 30),
        )
        assertEquals(RecapPersona.NIGHT_OWL, recap.persona)
        assertTrue(recap.genres.isEmpty())
    }
}
