package com.alananasss.kittytune.wear

import android.graphics.Bitmap
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.wear.data.PhoneWearableController
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import com.alananasss.kittytune.wear.presentation.MainActivity
import com.alananasss.kittytune.wear.presentation.PlaybackViewModel
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.Wearable
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WearAuditWatchDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun inspectRealWatchTransport() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200)
            val report = File(context.getExternalFilesDir(null), "wear-audit-watch-nodes.txt")
            try {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes, 8, TimeUnit.SECONDS)
                report.writeText("Connected phone nodes: ${nodes.map { it.displayName + ':' + it.id }}\n")
            } catch (e: Exception) {
                report.writeText("Node query failed: ${e.javaClass.simpleName}: ${e.message}\n")
            }
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
                val controller = field.get(activity) as PhoneWearableController
                report.appendText("Controller: ${controller.javaClass.name}\nConnection: ${controller.connectionStatus.value}\n")
                report.appendText("Targets: ${controller.availableTargets.value}\nState: ${controller.playbackState.value}\n")
            }
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "wear-audit-idle.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
    }

    @Test fun rejectedVolumeMustRollBackOnTheRealWatch() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            Thread.sleep(1200)
            lateinit var vm: PlaybackViewModel
            var initial = 0f
            var requested = 0f
            scenario.onActivity { activity ->
                val field = MainActivity::class.java.getDeclaredField("controller").apply { isAccessible = true }
                val controller = field.get(activity) as PhoneWearableController
                assumeTrue("This failure-path probe requires a disconnected watch",
                    controller.connectionStatus.value in listOf(ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR))
                vm = ViewModelProvider(activity)[PlaybackViewModel::class.java]
                initial = vm.uiState.value.volume
                requested = if (initial < 0.5f) 0.75f else 0.25f
                vm.onSetVolume(requested)
            }
            Thread.sleep(900)
            var actual = 0f
            scenario.onActivity {
                actual = vm.uiState.value.volume
                File(context.getExternalFilesDir(null), "wear-audit-volume.txt").writeText(
                    "Initial=$initial Requested=$requested Actual=$actual Error=${vm.uiState.value.errorMessage}\n")
            }
            val bitmap = instrumentation.uiAutomation.takeScreenshot()
            File(context.getExternalFilesDir(null), "wear-audit-rejected-volume.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
            assertEquals("A failed volume command must restore the authoritative volume", initial, actual, 0.001f)
        }
    }
}
