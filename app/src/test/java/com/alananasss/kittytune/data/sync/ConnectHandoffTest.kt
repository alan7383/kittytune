package com.alananasss.kittytune.data.sync

import org.junit.Test
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import com.alananasss.kittytune.domain.Track

class ConnectHandoffTest {
    private val state = PlaybackSnapshot("pc", 1, listOf(Track(id = 1, title = "Track", artworkUrl = null, durationMs = 120000, user = null)), 0,
        15000, true, true, "ALL")
    @Test fun remoteToRemoteUsesSourceQueueAndLatestPosition() = runBlocking {
        val calls = mutableListOf<Pair<String?, String>>()
        var prepared: PlaybackSnapshot? = null
        var position = 0L
        ConnectHandoff.move("pc", "tablet", state, { state.copy(positionMs = 16000) }) { device, action, snapshot, value ->
            calls += device to action
            if (action == "transfer") prepared = snapshot
            if (action == "seek") position = value
        }
        assertEquals(listOf<Pair<String?, String>>("tablet" to "transfer", "pc" to "pause", "tablet" to "seek", "tablet" to "play"), calls)
        assertFalse(prepared!!.isPlaying)
        assertEquals(state.queue, prepared!!.queue)
        assertEquals(16000L, position)
    }
    @Test fun failedPreparationLeavesSourcePlaying() = runBlocking {
        val calls = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            ConnectHandoff.move(null, "phone", state, { state }) { _, action, _, _ ->
                calls += action; error("Stream unavailable")
            }
        }
        assertEquals(listOf("transfer"), calls)
    }
    @Test fun pauseDuringPreparationDoesNotAutoplay() = runBlocking {
        val calls = mutableListOf<String>()
        ConnectHandoff.move("pc", null, state, { state.copy(isPlaying = false) }) { _, action, _, _ -> calls += action }
        assertEquals(listOf("transfer", "pause", "seek"), calls)
    }
    @Test fun failedStartConfirmsDestinationPausedBeforeResumingSource() = runBlocking {
        val calls = mutableListOf<Pair<String?, String>>()
        assertFailsWith<IllegalStateException> {
            ConnectHandoff.move("pc", null, state, { state }) { device, action, _, _ ->
                calls += device to action
                if (device == null && action == "play") error("Cannot play")
            }
        }
        assertEquals(listOf<Pair<String?, String>>(null to "transfer", "pc" to "pause", null to "seek", null to "play", null to "pause", "pc" to "play"), calls)
    }
    @Test fun changedTrackIsNotPaused() = runBlocking {
        val calls = mutableListOf<String>()
        assertFailsWith<IllegalStateException> {
            ConnectHandoff.move("pc", null, state, { state.copy(queue = listOf(Track(id = 2, title = "Other", artworkUrl = null, durationMs = 120000, user = null))) }) { _, action, _, _ -> calls += action }
        }
        assertEquals(listOf("transfer"), calls)
    }
    @Test fun localFileIsRejectedBeforeAnyCommand() = runBlocking {
        var commands = 0
        assertFailsWith<IllegalArgumentException> {
            ConnectHandoff.move(null, "phone", state.copy(queue = listOf(Track(id = 1, title = "Local", artworkUrl = null, durationMs = 120000, user = null, source = "local"))), { state }) { _, _, _, _ -> commands++ }
        }
        assertEquals(0, commands)
    }
    @Test fun ambiguousDestinationPauseDoesNotResumeSource() = runBlocking {
        val calls = mutableListOf<Pair<String?, String>>()
        assertFailsWith<IllegalStateException> {
            ConnectHandoff.move("pc", null, state, { state }) { device, action, _, _ ->
                calls += device to action
                if (device == null && action in listOf("play", "pause")) error("Connection lost")
            }
        }
        assertFalse(calls.contains("pc" to "play"))
    }
}
