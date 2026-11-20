package com.alananasss.kittytune

import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherReaction
import com.alananasss.kittytune.data.together.TogetherReactionEvent
import com.alananasss.kittytune.data.together.TogetherReactionLimiter
import com.alananasss.kittytune.data.together.TogetherWire
import com.google.gson.JsonParser
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TogetherReactionsTest {
    private val room = "K7M9P3Q4"
    private val otherRoom = "W4X8Y2Z6"

    @Test
    fun sendsAnImmediateBurstAndRefillsWithoutBankingUnlimitedTaps() {
        val limiter = TogetherReactionLimiter()
        repeat(120) { assertTrue(limiter.tryAcquire(0L)) }
        assertFalse(limiter.tryAcquire(0L))
        assertFalse(limiter.tryAcquire(19_000_000L))
        assertTrue(limiter.tryAcquire(20_000_000L))
        assertFalse(limiter.tryAcquire(20_000_000L))

        repeat(120) { assertTrue(limiter.tryAcquire(60_000_000_000L)) }
        assertFalse(limiter.tryAcquire(60_000_000_000L))
    }

    @Test
    fun reactionKeepsTheSameWireFormatAcrossPlatforms() {
        val message = TogetherMessage.Reaction("listener", 120L, "drooling")
        val json = JsonParser.parseString(
            """{"type":"Reaction","from":"listener","sentAt":120,"reactionId":"drooling"}""",
        ).asJsonObject
        assertEquals(json, message.toJson())
        assertEquals(message, TogetherMessage.fromJson(json))

        val payload = TogetherWire.seal(room, message)
        assertEquals(message, TogetherWire.open(room, payload))
        assertNull(TogetherWire.open(otherRoom, payload))
    }

    @Test
    fun receivesOnlyLiveSupportedReactionsInActiveRoom() = runBlocking {
        Together.resetForTesting()
        val received = mutableListOf<TogetherReactionEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            Together.reactions.collect { received.add(it) }
        }
        val now = System.currentTimeMillis()
        val message = TogetherMessage.Reaction("friend", now, "drooling")
        try {
            Together.handle(room, message)
            Together.handle(room, TogetherMessage.ClaimHost("host", now))
            Together.startListening(room)
            Together.handle(otherRoom, message)
            Together.handle(room, message, isSnapshot = true)
            Together.handle(room, message.copy(from = Together.memberId))
            Together.handle(room, message.copy(reactionId = "arbitrary text"))
            Together.handle(room, message.copy(reactionId = null))
            Together.handle(room, message)
            Together.handle(room, message)
            Together.handle(room, message.copy(reactionId = "fire"))
            Together.stopListening()
            Together.handle(room, message)

            yield()
            assertEquals(
                listOf(
                    TogetherReactionEvent(room, TogetherReaction.DROOLING),
                    TogetherReactionEvent(room, TogetherReaction.DROOLING),
                    TogetherReactionEvent(room, TogetherReaction.FIRE),
                ),
                received,
            )
            assertTrue(Together.reactions.replayCache.isEmpty())
        } finally {
            collector.cancel()
            Together.resetForTesting()
        }
    }
}
