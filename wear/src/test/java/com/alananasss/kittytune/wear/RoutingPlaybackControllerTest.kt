package com.alananasss.kittytune.wear

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.TargetSelectionPayload
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.data.RoutingPlaybackController
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingPlaybackControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private lateinit var phoneController: FakeWearPlaybackController
    private lateinit var desktopController: FakeWearPlaybackController
    private lateinit var routingController: RoutingPlaybackController

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        phoneController = FakeWearPlaybackController()
        desktopController = FakeWearPlaybackController()
        routingController = RoutingPlaybackController(
            phoneController = phoneController,
            desktopController = desktopController,
            scope = testScope
        )
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state routes to phone controller`() {
        val phoneState = WearPlaybackState(title = "Phone Song", isPlaying = true)
        val desktopState = WearPlaybackState(title = "Desktop Song", isPlaying = false)

        phoneController._playbackState.value = phoneState
        desktopController._playbackState.value = desktopState
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Phone Song", routingController.playbackState.value.title)
    }

    @Test
    fun `selectTarget phone delegates only to phoneController and returns success`() = runTest(testDispatcher) {
        val phonePayload = TargetSelectionPayload.phone()

        val result = routingController.selectTarget(phonePayload)

        assertTrue("Selecting phone must succeed", result.isSuccess)
        assertTrue(phoneController.dispatchedCommands.any { it.startsWith("selectTarget:PHONE") })
        assertFalse("Desktop controller must not be called when selecting phone",
            desktopController.dispatchedCommands.any { it.startsWith("selectTarget") })
    }

    @Test
    fun `selectTarget desktop delegates only to desktopController and returns success`() = runTest(testDispatcher) {
        val desktopPayload = TargetSelectionPayload.desktop("desktop-peer-1", "Studio Mac")

        val result = routingController.selectTarget(desktopPayload)

        assertTrue("Selecting desktop must succeed", result.isSuccess)
        assertTrue(desktopController.dispatchedCommands.any { it.startsWith("selectTarget:DESKTOP:desktop-peer-1") })
        assertFalse("Phone controller must not be called when selecting desktop",
            phoneController.dispatchedCommands.any { it.startsWith("selectTarget") })
    }

    @Test
    fun `selectTarget phone succeeds even when desktop is failing or offline`() = runTest(testDispatcher) {
        // Desktop controller configured to fail any command
        desktopController.shouldFailNextCommand = true

        val phonePayload = TargetSelectionPayload.phone()
        val result = routingController.selectTarget(phonePayload)

        assertTrue("Selecting phone must succeed despite desktop failure", result.isSuccess)
        assertTrue("Phone controller received command", phoneController.dispatchedCommands.isNotEmpty())
        assertTrue("Desktop failure was not triggered", desktopController.dispatchedCommands.isEmpty())
    }

    @Test
    fun `selectTarget desktop rolls back target type on failure`() = runTest(testDispatcher) {
        desktopController.shouldFailNextCommand = true

        val desktopPayload = TargetSelectionPayload.desktop("desktop-peer-err", "Broken Desktop")
        val result = routingController.selectTarget(desktopPayload)

        assertTrue("Result must be failure when desktop fails", result.isFailure)

        // Subsequent playback commands must still route to phone
        routingController.play()
        assertTrue("Play command must route to phone after desktop target failure",
            phoneController.dispatchedCommands.contains("play"))
        assertFalse("Desktop must not receive play command",
            desktopController.dispatchedCommands.contains("play"))
    }

    @Test
    fun `playback commands route dynamically according to active target`() = runTest(testDispatcher) {
        // 1. Play on phone
        routingController.play()
        assertTrue(phoneController.dispatchedCommands.contains("play"))

        // 2. Switch to desktop
        routingController.selectTarget(TargetSelectionPayload.desktop("pc-1", "PC"))

        // 3. Pause and next on desktop
        routingController.pause()
        routingController.next()
        assertTrue(desktopController.dispatchedCommands.contains("pause"))
        assertTrue(desktopController.dispatchedCommands.contains("next"))

        // 4. Switch back to phone
        routingController.selectTarget(TargetSelectionPayload.phone())
        routingController.previous()
        assertTrue(phoneController.dispatchedCommands.contains("previous"))
    }

    @Test
    fun `availableTargets combines phone and desktop targets distinctly`() {
        val phoneTarget = TargetDeviceInfo(WearConstants.TARGET_PHONE_ID, "Phone", WearConstants.TARGET_TYPE_PHONE)
        val desktopTarget1 = TargetDeviceInfo("desk-1", "Studio Mac", WearConstants.TARGET_TYPE_DESKTOP)
        val desktopTarget2 = TargetDeviceInfo("desk-2", "Living Room PC", WearConstants.TARGET_TYPE_DESKTOP)

        phoneController._availableTargets.value = listOf(phoneTarget)
        desktopController._availableTargets.value = listOf(desktopTarget1, desktopTarget2)
        testDispatcher.scheduler.advanceUntilIdle()

        val combined = routingController.availableTargets.value
        assertEquals(3, combined.size)
        assertTrue(combined.any { it.id == WearConstants.TARGET_PHONE_ID })
        assertTrue(combined.any { it.id == "desk-1" })
        assertTrue(combined.any { it.id == "desk-2" })
    }
}
