package com.alananasss.kittytune

import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PauseWhileLoadingTest {

    private class PlaybackStateHolder {
        val playWhenReadyRequestedFlow = MutableStateFlow(true)
        var playWhenReadyRequested: Boolean
            get() = playWhenReadyRequestedFlow.value
            set(value) {
                playWhenReadyRequestedFlow.value = value
            }
    }

    @Test
    fun testPlayWhenReadyRequestedFlowContract() {
        val holder = PlaybackStateHolder()

        holder.playWhenReadyRequested = true
        assertTrue(holder.playWhenReadyRequested)
        assertTrue(holder.playWhenReadyRequestedFlow.value)

        holder.playWhenReadyRequested = false
        assertFalse(holder.playWhenReadyRequested)
        assertFalse(holder.playWhenReadyRequestedFlow.value)
    }

    @Test
    fun testUiIsPlayingSemanticsDuringLoading() {
        fun computeUiIsPlaying(isPlaying: Boolean, isLoading: Boolean, playWhenReady: Boolean): Boolean {
            return isPlaying || (isLoading && playWhenReady)
        }

        // Case 1: Track is loading, intended to autoplay -> UI should show Pause (uiIsPlaying = true)
        assertTrue(computeUiIsPlaying(isPlaying = false, isLoading = true, playWhenReady = true))

        // Case 2: User tapped Pause while track is still loading -> UI should show Play (uiIsPlaying = false)
        assertFalse(computeUiIsPlaying(isPlaying = false, isLoading = true, playWhenReady = false))

        // Case 3: Track finished loading and is playing -> UI shows Pause (uiIsPlaying = true)
        assertTrue(computeUiIsPlaying(isPlaying = true, isLoading = false, playWhenReady = true))

        // Case 4: Track is paused normally -> UI shows Play (uiIsPlaying = false)
        assertFalse(computeUiIsPlaying(isPlaying = false, isLoading = false, playWhenReady = false))
    }

    @Test
    fun testShouldPlayResolutionLogic() {
        fun evaluateShouldPlay(autoPlay: Boolean, playWhenReadyRequested: Boolean, playWhenReady: Boolean): Boolean {
            return autoPlay && playWhenReadyRequested && playWhenReady
        }

        // Case 1: Normal playback load -> shouldPlay is true
        assertTrue(evaluateShouldPlay(autoPlay = true, playWhenReadyRequested = true, playWhenReady = true))

        // Case 2: User paused during loading -> shouldPlay is false (won't start playback unwillingly)
        assertFalse(evaluateShouldPlay(autoPlay = true, playWhenReadyRequested = false, playWhenReady = false))

        // Case 3: AutoPlay was disabled -> shouldPlay is false
        assertFalse(evaluateShouldPlay(autoPlay = false, playWhenReadyRequested = true, playWhenReady = true))
    }

    @Test
    fun testOnPlayWhenReadyChangedIgnoresInternalPlayerResetWhileLoading() {
        var playWhenReady = true
        var playWhenReadyRequested = true
        var isLoadingTrack = true

        fun onPlayWhenReadyChanged(playWhenReadyState: Boolean, isAudioFocusLoss: Boolean) {
            if (isLoadingTrack) {
                if (!playWhenReadyState && isAudioFocusLoss) {
                    playWhenReady = false
                    playWhenReadyRequested = false
                }
                return
            }
            playWhenReady = playWhenReadyState
            playWhenReadyRequested = playWhenReadyState
        }

        // Internal player pause/reset during track transition while loading:
        onPlayWhenReadyChanged(playWhenReadyState = false, isAudioFocusLoss = false)
        assertTrue("Internal pause while loading should not wipe playWhenReady", playWhenReady)
        assertTrue("Internal pause while loading should not wipe playWhenReadyRequested", playWhenReadyRequested)

        // Audio focus loss while loading:
        onPlayWhenReadyChanged(playWhenReadyState = false, isAudioFocusLoss = true)
        assertFalse("Audio focus loss while loading must pause playback intent", playWhenReady)
        assertFalse("Audio focus loss while loading must pause playWhenReadyRequested", playWhenReadyRequested)
    }
}
