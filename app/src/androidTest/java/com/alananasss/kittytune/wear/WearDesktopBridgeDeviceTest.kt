package com.alananasss.kittytune.wear

import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.sync.KnownDevice
import com.alananasss.kittytune.data.sync.SyncService
import com.alananasss.kittytune.data.sync.SyncPeers
import com.alananasss.kittytune.data.sync.SyncPlayback
import com.alananasss.kittytune.wear.common.WearConstants
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Real Desktop fixture uses an isolated profile, local WAV files and an adb reverse TCP route. */
class WearDesktopBridgeDeviceTest {
    @Test fun backgroundPhoneRelaysWatchCommandsToTheRealDesktop() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val desktopId = "wear-live-desktop"
        val previousRelay = ConnectManager.relayUrl.takeUnless { it == "ws://127.0.0.1:47654" }.orEmpty()
        val previousEnabled = SyncPlayback.enabled
        val ready = File(context.getExternalFilesDir(null), "wear-desktop-ready.txt")
        try {
            instrumentation.runOnMainSync {
                check(SyncService.pairingSecret.isNotBlank())
                SyncPlayback.enabled = true
                SyncPeers.remember(KnownDevice(desktopId, "KittyTuneDesktop test", "127.0.0.1",
                    secret = "wear-live-fixture-secret-only", platform = "desktop"))
                ConnectManager.relayUrl = "ws://127.0.0.1:47654"
                WearableStatePublisher.resetTargetForTesting()
            }
            instrumentation.runOnMainSync {
                ConnectManager.publish(null)
                ConnectManager.setForeground(false)
                assertFalse(runCatching { MusicManager.player.isPlaying }.getOrDefault(false))
            }
            ready.writeText("READY:${android.os.Process.myPid()}")
            fun await(description: String, condition: () -> Boolean) {
                val deadline = System.currentTimeMillis() + 120_000
                while (System.currentTimeMillis() < deadline) {
                    if (condition()) return
                    Thread.sleep(50)
                }
                fail("Timeout: $description")
            }
            await("watch selects Desktop") { WearableStatePublisher.activeTargetId == desktopId }
            await("watch completes Desktop controls and returns to phone") {
                WearableStatePublisher.activeTargetType == WearConstants.TARGET_TYPE_PHONE
            }
            assertTrue("Desktop connection must survive an idle background phone",
                ConnectManager.peers.value[desktopId]?.connected == true)
            ready.writeText("PASSED: real watch, background phone, real KittyTuneDesktop player")
        } finally {
            instrumentation.runOnMainSync {
                WearableStatePublisher.resetTargetForTesting()
                SyncPeers.forget(desktopId)
                ConnectManager.relayUrl = previousRelay
                SyncPlayback.enabled = previousEnabled
                ConnectManager.refresh()
            }
        }
    }
}
