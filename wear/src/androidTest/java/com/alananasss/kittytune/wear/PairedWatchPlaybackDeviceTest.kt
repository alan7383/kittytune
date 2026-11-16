package com.alananasss.kittytune.wear

import android.graphics.Bitmap
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.wear.data.PhoneWearableController
import com.alananasss.kittytune.wear.presentation.MainActivity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Requires the paired phone's pairedWatchControlsBackgroundPhone fixture to be READY. */
class PairedWatchPlaybackDeviceTest {
    private fun await(description: String, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(50)
        }
        fail("Timeout: $description")
    }

    @Test fun commandsCrossTheRealDataLayerAndUpdateTheBackgroundPhone() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var controller: PhoneWearableController
            scenario.onActivity { activity ->
                controller = MainActivity::class.java.getDeclaredField("controller").apply {
                    isAccessible = true
                }.get(activity) as PhoneWearableController
            }
            controller.requestSync().getOrThrow()
            await("fixture track") { controller.playbackState.value.trackId == -880_000L }
            controller.pause().getOrThrow()
            assertFalse(controller.playbackState.value.isPlaying)
            Thread.sleep(700)
            controller.play().getOrThrow()
            await("playing") { controller.playbackState.value.isPlaying }
            Thread.sleep(400)
            controller.next().getOrThrow()
            await("next track") { controller.playbackState.value.let { it.trackId == -880_001L && it.isPlaying } }
            Thread.sleep(400)
            controller.previous().getOrThrow()
            await("previous track") { controller.playbackState.value.let { it.trackId == -880_000L && it.isPlaying } }
            Thread.sleep(350)
            val context = instrumentation.targetContext
            instrumentation.uiAutomation.takeScreenshot().let { bitmap ->
                File(context.getExternalFilesDir(null), "wear-paired-playing.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
            controller.setVolume(.8f).getOrThrow()
            val high = controller.playbackState.value.volume
            assertEquals(.8f, high, .08f)
            Thread.sleep(700)
            controller.adjustVolume(-.1f).getOrThrow()
            assertTrue(controller.playbackState.value.volume < high)
            Thread.sleep(700)
            controller.pause().getOrThrow()
        }
    }
}
