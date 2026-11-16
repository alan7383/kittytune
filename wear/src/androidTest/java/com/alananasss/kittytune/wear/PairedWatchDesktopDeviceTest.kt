package com.alananasss.kittytune.wear

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.data.PhoneWearableController
import com.alananasss.kittytune.wear.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PairedWatchDesktopDeviceTest {
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timeout: $description")
    }

    @Test fun watchControlsTheRealDesktopThroughAnIdleBackgroundPhone() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var controller: PhoneWearableController
            scenario.onActivity {
                controller = MainActivity::class.java.getDeclaredField("controller").apply {
                    isAccessible = true
                }.get(it) as PhoneWearableController
            }
            // Cold API 37 emulator verification can outlast one read-only sync request.
            // Playback commands below still execute exactly once with production timeouts.
            var sync = controller.requestSync()
            repeat(2) { if (sync.isFailure) sync = controller.requestSync() }
            sync.getOrThrow()
            await("real Desktop discovery") {
                controller.availableTargets.value.any { it.id == "wear-live-desktop" && it.isConnected }
            }
            controller.selectTarget(TargetSelectionPayload.desktop("wear-live-desktop", "KittyTuneDesktop test")).getOrThrow()
            await("Desktop queue") { controller.playbackState.value.title.contains("Wear Desktop") }
            // Reuse the already running isolated Desktop without opening or focusing its window.
            // A previous interrupted run may have left its queue at the final fixture track.
            repeat(3) { controller.previous().getOrThrow() }
            await("Desktop fixture queue starts at first track") {
                controller.playbackState.value.title == "Wear Desktop 1"
            }
            val initialId = controller.playbackState.value.trackId
            controller.pause().getOrThrow()
            await("Desktop paused") { !controller.playbackState.value.isPlaying }
            controller.play().getOrThrow()
            await("Desktop audio playing") { controller.playbackState.value.isPlaying }
            controller.next().getOrThrow()
            await("Desktop next track playing") {
                controller.playbackState.value.let { it.trackId != initialId && it.isPlaying }
            }
            controller.previous().getOrThrow()
            await("Desktop previous track playing") {
                controller.playbackState.value.let { it.trackId == initialId && it.isPlaying }
            }
            controller.setVolume(.8f).getOrThrow()
            await("Desktop volume up") { kotlin.math.abs(controller.playbackState.value.volume - .8f) < .01f }
            controller.adjustVolume(-.1f).getOrThrow()
            await("Desktop volume down") { kotlin.math.abs(controller.playbackState.value.volume - .7f) < .01f }
            Thread.sleep(500)
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(instrumentation.targetContext.getExternalFilesDir(null), "wear-desktop-playing.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            File(instrumentation.targetContext.getExternalFilesDir(null), "wear-desktop-playing-ready.txt").writeText("READY:${android.os.Process.myPid()}")
            Thread.sleep(10_000) // Leave time to capture the real Desktop window at the same playback state.
            controller.pause().getOrThrow()
            controller.selectTarget(TargetSelectionPayload.phone()).getOrThrow()
        }
    }
}
