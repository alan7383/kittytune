package com.alananasss.kittytune.wear.common

import org.junit.Assert.*
import org.junit.Test

class WearCommandResultTest {
    @Test fun acceptedCommandPreservesAuthoritativeSnapshot() {
        val state = WearPlaybackState(trackId = 123, volume = .73f, isPlaying = true,
            targetType = WearConstants.TARGET_TYPE_DESKTOP, targetDeviceId = "paired-desktop")
        val reply = WearCommandResult.decode(WearCommandResult(state = state).encode())
        assertNull(reply.error)
        assertEquals(state, reply.state)
    }

    @Test fun rejectedCommandDoesNotInventPlaybackState() {
        val reply = WearCommandResult.decode(WearCommandResult(error = "Device is offline").encode())
        assertEquals("Device is offline", reply.error)
        assertNull(reply.state)
    }
}
