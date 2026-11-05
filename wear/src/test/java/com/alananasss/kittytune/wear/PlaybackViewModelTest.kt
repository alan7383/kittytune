package com.alananasss.kittytune.wear

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import com.alananasss.kittytune.wear.presentation.PlaybackViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var controller: FakeWearPlaybackController
    private lateinit var viewModel: PlaybackViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        controller = FakeWearPlaybackController()
        viewModel = PlaybackViewModel(controller)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial uiState defaults to empty track and connected phone target`() {
        val state = viewModel.uiState.value
        assertFalse("Initial state should not be playing", state.isPlaying)
        assertEquals("Initial volume should be 0.0f", 0.0f, state.volume, 0.0f)
        assertTrue("Initial target must be phone", state.isPhone)
        assertEquals(ConnectionStatus.CONNECTED, state.connectionStatus)
    }

    @Test
    fun `authoritative backend state ingestion updates all ui fields`() {
        val backendState = WearPlaybackState(
            trackId = 12345L,
            title = "Bohemian Rhapsody",
            artist = "Queen",
            isPlaying = true,
            volume = 0.75f,
            positionMs = 30_000L,
            durationMs = 354_000L,
            timestamp = 1000L
        )

        controller._playbackState.value = backendState
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Bohemian Rhapsody", state.title)
        assertEquals("Queen", state.artist)
        assertTrue(state.isPlaying)
        assertEquals(0.75f, state.volume, 0.001f)
        assertTrue(state.hasTrack)
        assertTrue("Progress fraction must be > 0", state.progressFraction(nowMs = 1000L) > 0.08f)
    }

    @Test
    fun `optimistic playPause flips isPlaying immediately and dispatches command`() {
        assertFalse(viewModel.uiState.value.isPlaying)

        viewModel.onPlayPause()
        // Optimistic UI update occurs synchronously on caller thread
        assertTrue("Optimistic update must immediately set isPlaying to true", viewModel.uiState.value.isPlaying)

        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue("Controller must have received playPause command", controller.dispatchedCommands.contains("playPause"))
    }

    @Test
    fun `optimistic playPause rolls back on transport failure`() {
        assertFalse(viewModel.uiState.value.isPlaying)
        controller.shouldFailNextCommand = true

        viewModel.onPlayPause()
        assertTrue("Optimistically true initially", viewModel.uiState.value.isPlaying)

        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse("Must rollback to false after failure", viewModel.uiState.value.isPlaying)
        assertNotNull("Error message must be present", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `volume increments clamp to 1_0 and sanitize NaNs`() {
        viewModel.onSetVolume(0.8f)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.8f, viewModel.uiState.value.volume, 0.001f)

        // Increment past 1.0f clamps to 1.0f
        viewModel.onVolumeChange(+0.5f)
        assertEquals(1.0f, viewModel.uiState.value.volume, 0.001f)

        // Decrement below 0.0f clamps to 0.0f
        viewModel.onVolumeChange(-2.0f)
        assertEquals(0.0f, viewModel.uiState.value.volume, 0.001f)

        // NaN change does not corrupt volume
        viewModel.onVolumeChange(Float.NaN)
        assertFalse("Volume must never be NaN", viewModel.uiState.value.volume.isNaN())
        assertEquals(0.0f, viewModel.uiState.value.volume, 0.001f)
    }

    @Test
    fun `target selection updates target optimistically and dispatches target payload`() {
        val desktopTarget = TargetDeviceInfo(
            id = "desktop-peer-42",
            name = "Studio PC",
            type = WearConstants.TARGET_TYPE_DESKTOP
        )

        viewModel.onSelectTarget(desktopTarget)

        val state = viewModel.uiState.value
        assertEquals("Studio PC", state.selectedTarget.name)
        assertTrue(state.isDesktop)
        assertEquals(ConnectionStatus.CONNECTING, state.connectionStatus)

        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(
            "Controller must receive target selection payload",
            controller.dispatchedCommands.any { it.contains("selectTarget:DESKTOP:desktop-peer-42") }
        )
    }

    @Test
    fun `quick toggle switches between phone and desktop when only two targets exist`() {
        val targets = listOf(
            TargetDeviceInfo("phone", "Phone", WearConstants.TARGET_TYPE_PHONE, true, true),
            TargetDeviceInfo("desktop-1", "MacBook Pro", WearConstants.TARGET_TYPE_DESKTOP, true, false)
        )
        controller._availableTargets.value = targets
        testDispatcher.scheduler.advanceUntilIdle()

        // Starting on phone
        assertTrue(viewModel.uiState.value.isPhone)

        // Quick toggle switches to MacBook Pro
        viewModel.onToggleTarget()
        assertTrue("Quick toggle must switch to desktop", viewModel.uiState.value.isDesktop)
        assertEquals("MacBook Pro", viewModel.uiState.value.selectedTarget.name)
        assertFalse("Picker dialog should not open when <= 2 targets", viewModel.uiState.value.isTargetPickerVisible)
    }

    @Test
    fun `quick toggle opens picker dialog when more than two targets exist`() {
        val targets = listOf(
            TargetDeviceInfo("phone", "Phone", WearConstants.TARGET_TYPE_PHONE),
            TargetDeviceInfo("desktop-1", "MacBook Pro", WearConstants.TARGET_TYPE_DESKTOP),
            TargetDeviceInfo("desktop-2", "Living Room PC", WearConstants.TARGET_TYPE_DESKTOP)
        )
        controller._availableTargets.value = targets
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.onToggleTarget()
        assertTrue("Picker dialog must open for 3+ targets", viewModel.uiState.value.isTargetPickerVisible)
    }

    @Test
    fun `onNext and onPrevious dispatch commands`() {
        viewModel.onNext()
        viewModel.onPrevious()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(controller.dispatchedCommands.contains("next"))
        assertTrue(controller.dispatchedCommands.contains("previous"))
    }

    @Test
    fun `onDismissError clears active error message`() {
        controller.shouldFailNextCommand = true
        viewModel.onPlayPause()
        testDispatcher.scheduler.advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.errorMessage)

        viewModel.onDismissError()
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
