package com.alananasss.kittytune.wear

import com.alananasss.kittytune.data.sync.ConnectPolicy
import org.junit.Assert.*
import org.junit.Test

class WearDesktopConnectionPolicyTest {
    @Test fun watchCanRelayCommandsWhilePhoneIsIdleInBackground() {
        assertFalse(ConnectPolicy.shouldConnect(true, true, true, false, false))
        assertTrue(ConnectPolicy.shouldConnect(true, true, true, false, false, wearControl = true))
    }

    @Test fun watchSessionRespectsDisabledPlaybackSync() {
        assertFalse(ConnectPolicy.shouldConnect(true, false, true, false, false, wearControl = true))
        assertFalse(ConnectPolicy.shouldConnect(false, true, true, false, false, wearControl = true))
    }
}
