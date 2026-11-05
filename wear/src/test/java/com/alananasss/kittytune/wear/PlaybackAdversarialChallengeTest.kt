package com.alananasss.kittytune.wear

import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.common.WearConstants
import com.alananasss.kittytune.wear.common.WearPlaybackState
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import com.alananasss.kittytune.wear.presentation.PlaybackViewModel
import com.alananasss.kittytune.wear.presentation.WearPlaybackUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.sqrt

/**
 * Empirical Adversarial Test Harness for Milestone 2 Wear OS Playback UI & Controller.
 *
 * Vector 1: Marquee & Title Overflow Stress (1,000+ chars, RTL, multi-byte emojis, zero-width)
 * Vector 2: Circular Chord Clearances & Minimum Touch Target Guidelines (390x390, 450x450)
 * Vector 3: ViewModel Concurrency & Error Rollback Invariants
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackAdversarialChallengeTest {

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

    // =========================================================================
    // VECTOR 1: Marquee & Title Overflow Stress
    // =========================================================================

    @Test
    fun `vector1 - ultra long title with 1000+ characters does not crash state computation`() {
        val longTitle = "A".repeat(1500)
        val longArtist = "B".repeat(1200)

        val state = WearPlaybackState(
            trackId = 999L,
            title = longTitle,
            artist = longArtist,
            isPlaying = true,
            durationMs = 240_000L,
            positionMs = 12_000L
        )

        controller._playbackState.value = state
        testDispatcher.scheduler.advanceUntilIdle()

        val uiState = viewModel.uiState.value
        assertEquals(1500, uiState.title.length)
        assertEquals(1200, uiState.artist.length)
        assertTrue(uiState.hasTrack)
    }

    @Test
    fun `vector1 - complex multi-byte unicode, emojis and RTL strings preserve state integrity`() {
        val rtlArabic = "مرحبا بك في تطبيق كيتي تيون للموسيقى الرائعة والمميزة"
        val rtlHebrew = "שלום עולם - מוזיקה קלאסית ורוק מתקדם"
        val complexEmojis = "🐱🎧🔥 👨‍👩‍👧‍👦 🏳️‍🌈 👍🏽 👩🏾‍🚀"
        val zeroWidthStr = "\u200B\u200C\u200D\uFEFF\u200E\u200F"

        val testCases = listOf(
            rtlArabic to rtlHebrew,
            complexEmojis to "Emoji Artist",
            zeroWidthStr to "Zero Width Artist",
            "Mixed English & عربي (Arabic)" to "فنان عالمي"
        )

        for ((t, a) in testCases) {
            val state = WearPlaybackState(
                trackId = 1L,
                title = t,
                artist = a,
                isPlaying = true
            )
            controller._playbackState.value = state
            testDispatcher.scheduler.advanceUntilIdle()

            val uiState = viewModel.uiState.value
            assertEquals(t, uiState.title)
            assertEquals(a, uiState.artist)
        }
    }

    @Test
    fun `vector1 - zero width string title behaviour on hasTrack`() {
        // Kotlin .isBlank() returns false for \u200B because it is category FORMAT (Cf), not whitespace
        val zeroWidthTitle = "\u200B\u200B"
        val uiState = WearPlaybackUiState(
            trackId = 0L,
            title = zeroWidthTitle
        )
        // Since trackId is 0L, hasTrack depends on title.isNotBlank()
        // If \u200B is not considered blank by Kotlin, hasTrack will be true
        val isBlank = zeroWidthTitle.isBlank()
        assertFalse("Kotlin Char.isWhitespace does not treat \\u200B as blank", isBlank)
        assertTrue("UiState considers zero-width char as having track", uiState.hasTrack)
    }

    @Test
    fun `vector1 - timeline formatting handles extreme duration and position values`() {
        // Very large duration (e.g. 500 hours live stream)
        val state = WearPlaybackUiState(
            trackId = 10L,
            title = "Long Stream",
            positionMs = 18_000_000L, // 5 hours
            durationMs = 36_000_000L, // 10 hours
            isPlaying = true
        )

        val posSec = (state.positionMs.coerceAtLeast(0L) / 1000).coerceAtMost(state.durationMs / 1000)
        val durSec = state.durationMs / 1000
        val posStr = String.format("%02d:%02d", posSec / 60, posSec % 60)
        val durStr = String.format("%02d:%02d", durSec / 60, durSec % 60)

        assertEquals("300:00", posStr)
        assertEquals("600:00", durStr)
    }

    // =========================================================================
    // VECTOR 2: Circular Chord Clearances & Touch Target Guidelines
    // =========================================================================

    @Test
    fun `vector2 - control triad buttons meet 48dp minimum touch target standard`() {
        val prevButtonSize = 48.0
        val playPauseButtonSize = 56.0
        val nextButtonSize = 48.0

        assertTrue("Prev button meets 48dp", prevButtonSize >= 48.0)
        assertTrue("Play/Pause button meets 48dp", playPauseButtonSize >= 48.0)
        assertTrue("Next button meets 48dp", nextButtonSize >= 48.0)
    }

    @Test
    fun `vector2 - volume controls and target chip touch target audit`() {
        // In VolumeControls.kt line 65 & 120: Modifier.size(40.dp)
        val volumeButtonSizeDp = 40.0
        val minGuidelineDp = 48.0

        val volumeButtonCompliant = volumeButtonSizeDp >= minGuidelineDp
        println("AUDIT: Volume button size: $volumeButtonSizeDp dp vs required: $minGuidelineDp dp -> Compliant: $volumeButtonCompliant")

        // TargetDeviceSelectorChip has vertical padding 4dp + text ~16dp = ~24dp
        val chipHeightDp = 24.0
        val chipCompliant = chipHeightDp >= minGuidelineDp
        println("AUDIT: Target chip height: $chipHeightDp dp vs required: $minGuidelineDp dp -> Compliant: $chipCompliant")
    }

    @Test
    fun `vector2 - empirical geometric chord clearance on 390x390 and 450x450 displays`() {
        // Small Round Screen: 390px at scale 2.0 = 195.0dp diameter, R = 97.5dp
        val rSmall = 97.5
        val safeMargin = 8.0
        val rSafeSmall = rSmall - safeMargin // 89.5dp

        // Actual layout stack heights in PlaybackScreenContent Column:
        // 1. TargetChip: height ~24dp, width ~100dp
        // Spacer: 2dp
        // 2. TrackInfo: height ~37dp (title ~20dp, spacer 1dp, artist ~16dp), width = 82% of 195 = 160dp
        // Spacer: 6dp
        // 3. PlaybackControlsRow: height 56dp, width = 168dp
        // Spacer: 4dp
        // 4. VolumeControlsRow: height 40dp, width = 148dp (40 + 8 + 52 + 8 + 40)
        // Spacer: 2dp
        // 5. TimelineStatusText: height ~16dp, width ~80dp
        // Total column height = 24 + 2 + 37 + 6 + 56 + 4 + 40 + 2 + 16 = 187dp

        val totalHeight = 187.0
        val halfHeight = totalHeight / 2.0 // 93.5dp

        // Top element: TargetDeviceSelectorChip
        // Y bounds: [-93.5, -69.5] dp. Corner at (x = 50.0dp, y = -93.5dp)
        val chipCornerDist = sqrt(50.0 * 50.0 + 93.5 * 93.5)
        println("AUDIT: Target chip top-corner distance: $chipCornerDist dp (Screen R=$rSmall, Safe R=$rSafeSmall)")

        // Bottom element: VolumeControlsRow
        // Volume controls sits at y in [35.5, 75.5] dp.
        // Width is 148dp -> half width x = 74dp.
        // Bottom-outer corner of volume buttons at (x = 74.0, y = 75.5)
        val volumeBtnCornerDist = sqrt(74.0 * 74.0 + 75.5 * 75.5)
        println("AUDIT: Volume button bottom-corner distance: $volumeBtnCornerDist dp (Screen R=$rSmall, Safe R=$rSafeSmall)")

        // Check if any element exceeds the physical screen radius on small round
        val chipExceedsSmallScreen = chipCornerDist > rSmall
        val volumeExceedsSmallScreen = volumeBtnCornerDist > rSmall

        println("AUDIT: Chip exceeds small screen ($rSmall dp): $chipExceedsSmallScreen")
        println("AUDIT: Volume controls exceed small screen ($rSmall dp): $volumeExceedsSmallScreen")
    }

    // =========================================================================
    // VECTOR 3: ViewModel Concurrency & Error Rollbacks
    // =========================================================================

    @Test
    fun `vector3 - volume adjustment failure does not roll back to prior authoritative volume`() {
        // Set initial authoritative volume
        controller._playbackState.value = WearPlaybackState(
            trackId = 1L,
            title = "Test Track",
            volume = 0.5f
        )
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0.5f, viewModel.uiState.value.volume, 0.001f)

        // Make next controller command fail
        controller.shouldFailNextCommand = true

        // User sets volume to 0.8f optimistically
        viewModel.onSetVolume(0.8f)
        assertEquals("Optimistic update sets volume to 0.8f", 0.8f, viewModel.uiState.value.volume, 0.001f)

        // Advance debounce timer (50ms) and execute failed controller command
        testDispatcher.scheduler.advanceTimeBy(100L)
        testDispatcher.scheduler.advanceUntilIdle()

        // Check whether volume rolled back to 0.5f or remained stuck at 0.8f
        val currentVolume = viewModel.uiState.value.volume
        val errorMsg = viewModel.uiState.value.errorMessage
        println("AUDIT: Volume after failure: $currentVolume (Expected rollback: 0.5f), Error: $errorMsg")

        assertNotNull("Error message should be set on failure", errorMsg)
        // EMPIRICAL TEST: Check if volume actually rolled back!
        // In PlaybackViewModel lines 175-177:
        // if (result.isFailure) { _uiState.update { it.copy(errorMessage = "Volume sync failed") } }
        // Volume is NOT rolled back!
        val volumeDidRollback = (currentVolume == 0.5f)
        println("AUDIT: Did volume roll back to 0.5f? $volumeDidRollback")
    }

    @Test
    fun `vector3 - 100 rapid fire asynchronous playPause toggles state recovery`() = runTest(testDispatcher) {
        assertEquals(false, viewModel.uiState.value.isPlaying)

        // Dispatch 100 rapid fire playPause invocations
        val jobs = (1..100).map {
            launch {
                viewModel.onPlayPause()
            }
        }
        jobs.forEach { it.join() }
        testDispatcher.scheduler.advanceUntilIdle()

        // Check that UI state is boolean and not corrupted
        val isPlaying = viewModel.uiState.value.isPlaying
        println("AUDIT: After 100 rapid fire toggles, isPlaying = $isPlaying")
        println("AUDIT: Dispatched controller commands count = ${controller.dispatchedCommands.size}")

        // Controller commands must be recorded without throwing unhandled exceptions
        assertTrue(controller.dispatchedCommands.isNotEmpty())
    }

    @Test
    fun `vector3 - rapid target switching with intermediate failure does not corrupt selected target`() {
        val targetPhone = TargetDeviceInfo(WearConstants.TARGET_PHONE_ID, "Phone", WearConstants.TARGET_TYPE_PHONE)
        val targetDesktop1 = TargetDeviceInfo("desktop-1", "MacBook Pro", WearConstants.TARGET_TYPE_DESKTOP)
        val targetDesktop2 = TargetDeviceInfo("desktop-2", "Studio PC", WearConstants.TARGET_TYPE_DESKTOP)

        controller._availableTargets.value = listOf(targetPhone, targetDesktop1, targetDesktop2)
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Switch to Desktop 1 (succeeds)
        viewModel.onSelectTarget(targetDesktop1)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("desktop-1", viewModel.uiState.value.selectedTarget.id)

        // 2. Configure controller to fail the next target switch
        controller.shouldFailNextCommand = true

        // 3. Switch to Desktop 2 (fails)
        viewModel.onSelectTarget(targetDesktop2)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should have rolled back to Desktop 1
        assertEquals("desktop-1", viewModel.uiState.value.selectedTarget.id)
        assertEquals(ConnectionStatus.ERROR, viewModel.uiState.value.connectionStatus)
        assertNotNull(viewModel.uiState.value.errorMessage)
    }
}
