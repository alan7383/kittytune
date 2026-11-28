package com.alananasss.kittytune.data.sync

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.stats.ListeningReports
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncListeningStatsEmulatorTest {
    private val app get() = KittyTuneApp.instance
    private val database get() = AppDatabase.getDatabase(app)
    private val dao get() = database.downloadDao()
    private val gson = Gson()
    private var emptyProfile = false

    @Before
    fun prepare() = runBlocking(Dispatchers.IO) {
        assumeTrue("Requires a fresh, disposable app profile", SyncLog.size() == 0 && dao.getTotalEventsAfter(0L) == 0)
        emptyProfile = true
        ListeningStatsRepository.init(app)
        ListeningStatsRepository.onStatsChanged()
    }

    @After
    fun reset() = runBlocking(Dispatchers.IO) {
        if (!emptyProfile) return@runBlocking
        database.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS reject_sync_test_listen")
        SyncLog.clear()
        dao.clearStats()
        ListeningStatsRepository.onStatsChanged()
    }

    @Test
    fun failedProjectionIsRetriedOnTheNextExchange() = runBlocking(Dispatchers.IO) {
        val sunday = Instant.parse("2026-11-22T12:00:00Z").toEpochMilli()
        val monday = Instant.parse("2026-11-23T12:00:00Z").toEpochMilli()
        val lastMonth = Instant.parse("2026-10-17T12:00:00Z").toEpochMilli()
        val twelveHours = 12 * 60 * 60_000L
        val twentyNineMinutes = 29 * 60_000L
        val repo = ListeningStatsRepository
        dao.insertStatsEvent(ListeningStatsEvent(
            trackId = 1, trackTitle = "Local", artistName = "Artist", artworkUrl = "",
            eventType = "PLAY_COMPLETE", listenDurationMs = twelveHours,
            trackDurationMs = twelveHours, timestamp = sunday,
        ))
        repo.onStatsChanged()
        assertEquals(sunday, repo.getOldestEventAt())

        val events = listOf(listen(1, monday, twentyNineMinutes), listen(2, lastMonth, 60_000L))
        val week = ListeningReports.recentWeek(monday, ZoneOffset.UTC)
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL("""
            CREATE TRIGGER reject_sync_test_listen BEFORE INSERT ON listening_stats
            WHEN NEW.syncEventId = 'fixture-pc#2' BEGIN SELECT RAISE(ABORT, 'write failed'); END
        """.trimIndent())
        assertTrue(runCatching { SyncApply.mergeAndApply(events) }.isFailure)
        assertEquals(2L, SyncLog.marks()["fixture-pc"])
        assertEquals(twelveHours, repo.getTotalListenTime(week.startMs, week.endMs))

        sqlite.execSQL("DROP TRIGGER reject_sync_test_listen")
        SyncApply.mergeAndApply(emptyList())
        assertEquals(twelveHours + twentyNineMinutes, repo.getTotalListenTime(week.startMs, week.endMs))
        assertEquals(lastMonth, repo.getOldestEventAt())
        assertEquals(60_000L, repo.getTotalListenTime(lastMonth, lastMonth + 1))

        assertTrue(SyncApply.mergeAndApply(events).isEmpty())
        assertEquals(0, SyncApply.reconcile())
        assertEquals(3, repo.getTotalEvents(0L))
    }

    @Test
    fun journalWriteFailureLeavesThePeerFreeToResend() = runBlocking(Dispatchers.IO) {
        val path = File(app.filesDir, "sync_log.jsonl")
        val event = listen(1, 1_000L, 60_000L)
        assertTrue(path.mkdir())
        try {
            assertTrue(runCatching { SyncApply.mergeAndApply(listOf(event)) }.exceptionOrNull() is IOException)
            assertTrue(SyncLog.marks().isEmpty())
            assertTrue(SyncLog.all().isEmpty())
        } finally {
            assertTrue(path.delete())
        }
        assertEquals(listOf(event), SyncApply.mergeAndApply(listOf(event)))
        assertEquals(60_000L, ListeningStatsRepository.getTotalListenTime(0L))
    }

    private fun listen(seq: Long, timestamp: Long, duration: Long): SyncEvent {
        val payload = ListenPayload(
            trackId = seq + 1, trackTitle = "Remote $seq", artistName = "Artist",
            artistId = null, artistPermalink = null, artistAvatarUrl = null, artworkUrl = null,
            source = "soundcloud", eventType = "PLAY_COMPLETE", listenDurationMs = duration,
            trackDurationMs = duration,
        )
        return SyncEvent("fixture-pc", seq, timestamp, SyncKinds.LISTEN, gson.toJson(payload))
    }
}
