package com.alananasss.kittytune.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The device-sync merge rule (issue #33).
 *
 * The same suite as the desktop's, because [SyncMerge] and [SyncEvent] are the same files: the two
 * apps exchange these events verbatim, so the moment one side's rule differs the totals stop matching.
 *
 * The whole point of syncing an event log rather than totals is that merging can be made
 * idempotent and order-independent. These tests are that claim, written down: the same batch twice
 * must not count twice, a gap must not be skipped over, and two devices must converge whatever
 * order they meet in.
 */
class SyncMergeTest {

    private fun event(device: String, seq: Long, at: Long = seq * 1000) =
        SyncEvent(device, seq, at, "listen", """{"trackId":$seq}""")

    @Test
    fun `an unknown device starts at zero`() {
        assertEquals(0L, SyncMerge.markFor(emptyMap(), "phone"))
        assertEquals(4L, SyncMerge.markFor(mapOf("phone" to 4L), "phone"))
    }

    @Test
    fun `everything is new the first time`() {
        val incoming = listOf(event("phone", 1), event("phone", 2))
        assertEquals(incoming, SyncMerge.selectNew(incoming, emptyMap(), "desktop"))
    }

    @Test
    fun `nothing is new the second time`() {
        val incoming = listOf(event("phone", 1), event("phone", 2))
        val marks = SyncMerge.advance(emptyMap(), incoming)
        assertTrue(SyncMerge.selectNew(incoming, marks, "desktop").isEmpty())
    }

    @Test
    fun `our own events coming back round a loop are not new`() {
        // Three devices: the phone can hand us back an event that started here.
        val incoming = listOf(event("desktop", 1), event("phone", 1))
        val fresh = SyncMerge.selectNew(incoming, emptyMap(), "desktop")
        assertEquals(listOf(event("phone", 1)), fresh)
    }

    @Test
    fun `malformed events are dropped without taking the batch with them`() {
        val incoming = listOf(
            event("phone", 1),
            SyncEvent("", 2, 0, "listen", "{}"),
            SyncEvent("phone", 0, 0, "listen", "{}"),
            SyncEvent("phone", 3, 0, "", "{}"),
            event("phone", 2),
        )
        val fresh = SyncMerge.selectNew(incoming, emptyMap(), "desktop")
        assertEquals(listOf(event("phone", 1), event("phone", 2)), fresh)
    }

    @Test
    fun `duplicates within one batch are collapsed`() {
        val incoming = listOf(event("phone", 1), event("phone", 1))
        assertEquals(1, SyncMerge.selectNew(incoming, emptyMap(), "desktop").size)
    }

    @Test
    fun `events arrive in each device's own order however they were sent`() {
        val incoming = listOf(event("phone", 3), event("tablet", 1), event("phone", 1), event("phone", 2))
        val fresh = SyncMerge.selectNew(incoming, emptyMap(), "desktop")
        assertEquals(listOf(1L, 2L, 3L), fresh.filter { it.deviceId == "phone" }.map { it.seq })
    }

    @Test
    fun `a mark advances over an unbroken run`() {
        val marks = SyncMerge.advance(emptyMap(), listOf(event("phone", 1), event("phone", 2), event("phone", 3)))
        assertEquals(3L, SyncMerge.markFor(marks, "phone"))
    }

    @Test
    fun `a gap stops the mark so the missing event is asked for again`() {
        // 6 is missing. Marking 7 as done would lose it for good.
        val marks = SyncMerge.advance(mapOf("phone" to 4L), listOf(event("phone", 5), event("phone", 7)))
        assertEquals(5L, SyncMerge.markFor(marks, "phone"))
    }

    @Test
    fun `a mark never moves backwards`() {
        val marks = SyncMerge.advance(mapOf("phone" to 9L), listOf(event("phone", 2)))
        assertEquals(9L, SyncMerge.markFor(marks, "phone"))
    }

    @Test
    fun `marks are kept per device`() {
        val marks = SyncMerge.advance(emptyMap(), listOf(event("phone", 1), event("tablet", 1), event("tablet", 2)))
        assertEquals(1L, SyncMerge.markFor(marks, "phone"))
        assertEquals(2L, SyncMerge.markFor(marks, "tablet"))
    }

    @Test
    fun `we send a peer only what it says it lacks`() {
        val local = listOf(event("desktop", 1), event("desktop", 2), event("phone", 1))
        val toSend = SyncMerge.eventsToSend(local, mapOf("desktop" to 1L, "phone" to 1L))
        assertEquals(listOf(event("desktop", 2)), toSend)
    }

    @Test
    fun `a peer is never sent its own events back`() {
        // The desktop's marks only say what it merged from others, so nothing in them ever mentions
        // its own device. Without the explicit rule the phone handed the desktop its own sixteen
        // events on every exchange, applied none, and reported "16 sent" for ever.
        val local = listOf(event("desktop", 1), event("desktop", 2), event("phone", 1))
        val toSend = SyncMerge.eventsToSend(local, emptyMap(), peerDeviceId = "desktop")
        assertEquals(listOf(event("phone", 1)), toSend)
    }

    @Test
    fun `an exchange converges to sending nothing once both sides are level`() {
        val desktopLog = listOf(event("desktop", 1), event("desktop", 2))
        // The phone has merged both, and holds nothing of its own.
        val phoneLog = desktopLog
        val phoneMarks = SyncMerge.advance(emptyMap(), desktopLog)
        assertTrue(SyncMerge.eventsToSend(phoneLog, emptyMap(), peerDeviceId = "desktop").isEmpty())
        assertTrue(SyncMerge.eventsToSend(desktopLog, phoneMarks, peerDeviceId = "phone").isEmpty())
    }

    @Test
    fun `a device relays what it learned from a third one`() {
        // The phone and the tablet never meet; both meet the desktop.
        val local = listOf(event("desktop", 1), event("tablet", 1))
        val toSend = SyncMerge.eventsToSend(local, mapOf("desktop" to 1L))
        assertEquals(listOf(event("tablet", 1)), toSend)
    }

    @Test
    fun `two devices converge whichever way round they exchange`() {
        val desktopLog = listOf(event("desktop", 1), event("desktop", 2))
        val phoneLog = listOf(event("phone", 1))

        // Desktop pulls first, then pushes.
        var desktopMarks = SyncMerge.advance(
            emptyMap(),
            SyncMerge.selectNew(phoneLog, emptyMap(), "desktop"),
        )
        var phoneMarks = SyncMerge.advance(
            emptyMap(),
            SyncMerge.selectNew(desktopLog, emptyMap(), "phone"),
        )
        assertEquals(1L, SyncMerge.markFor(desktopMarks, "phone"))
        assertEquals(2L, SyncMerge.markFor(phoneMarks, "desktop"))

        // The other way round reaches the same place.
        phoneMarks = SyncMerge.advance(emptyMap(), SyncMerge.selectNew(desktopLog, emptyMap(), "phone"))
        desktopMarks = SyncMerge.advance(emptyMap(), SyncMerge.selectNew(phoneLog, emptyMap(), "desktop"))
        assertEquals(1L, SyncMerge.markFor(desktopMarks, "phone"))
        assertEquals(2L, SyncMerge.markFor(phoneMarks, "desktop"))
    }

    @Test
    fun `replaying a whole exchange changes nothing`() {
        val phoneLog = (1L..5L).map { event("phone", it) }
        val marks = SyncMerge.advance(emptyMap(), SyncMerge.selectNew(phoneLog, emptyMap(), "desktop"))
        // Ten more exchanges with no new events on either side.
        var current = marks
        repeat(10) {
            val fresh = SyncMerge.selectNew(phoneLog, current, "desktop")
            assertTrue(fresh.isEmpty())
            current = SyncMerge.advance(current, fresh)
        }
        assertEquals(marks, current)
    }

    @Test
    fun `sequence numbers continue from the log rather than restarting`() {
        val local = listOf(event("desktop", 1), event("desktop", 2), event("phone", 9))
        assertEquals(3L, SyncMerge.nextSeq(local, "desktop"))
        assertEquals(10L, SyncMerge.nextSeq(local, "phone"))
        assertEquals(1L, SyncMerge.nextSeq(local, "tablet"))
    }

    @Test
    fun `one exchange is capped, and the rest follows on the next`() {
        // A first pairing with a year of history must not become one enormous response.
        val local = (1L..1200L).map { event("phone", it) }
        val first = SyncMerge.eventsToSend(local, emptyMap())
        assertEquals(SyncMerge.MAX_EVENTS_PER_EXCHANGE, first.size)
        assertEquals(1L, first.first().seq)

        // The mark advances over exactly what was sent, so the next batch continues from there.
        val marks = SyncMerge.advance(emptyMap(), first)
        assertEquals(SyncMerge.MAX_EVENTS_PER_EXCHANGE.toLong(), SyncMerge.markFor(marks, "phone"))
        val second = SyncMerge.eventsToSend(local, marks)
        assertEquals(SyncMerge.MAX_EVENTS_PER_EXCHANGE + 1L, second.first().seq)
    }

    @Test
    fun `a capped batch still gives each device a contiguous run`() {
        // The cap must not leave a device with a hole, or its mark would stall forever.
        val local = (1L..400L).map { event("phone", it) } + (1L..400L).map { event("tablet", it) }
        val sent = SyncMerge.eventsToSend(local, emptyMap(), limit = 500)
        val marks = SyncMerge.advance(emptyMap(), sent)
        val phoneSeqs = sent.filter { it.deviceId == "phone" }.map { it.seq }
        assertEquals((1L..400L).toList(), phoneSeqs)
        assertEquals(400L, SyncMerge.markFor(marks, "phone"))
        // The tablet got the remaining hundred, contiguously from its start.
        assertEquals(100L, SyncMerge.markFor(marks, "tablet"))
    }

    @Test
    fun `a truncated log does not hand out a number already used`() {
        // Restored from a backup missing the middle: the next number is still past the highest.
        val local = listOf(event("desktop", 1), event("desktop", 7))
        assertEquals(8L, SyncMerge.nextSeq(local, "desktop"))
    }
}
