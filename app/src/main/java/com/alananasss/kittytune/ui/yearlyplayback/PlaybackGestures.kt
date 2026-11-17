package com.alananasss.kittytune.ui.yearlyplayback

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Touch and gesture controls for the Story Player.
 *
 * Implements 100% parity with SoundCloud's decompiled:
 * com.soundcloud.android.yearlyplayback.ui.PlaybackControlsKt
 *
 * Gesture behavior:
 * - Hold (> 400ms): pauses story progression, Rive animation, and snippet audio.
 * - Release after hold: resumes story progression, Rive animation, and audio.
 * - Tap Left (< 50% width): goes back to previous block.
 * - Tap Right (>= 50% width): advances to next block.
 */
@Composable
fun StoryGestureOverlay(
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnPrevious by rememberUpdatedState(onPrevious)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPause by rememberUpdatedState(onPause)
    val currentOnResume by rememberUpdatedState(onResume)

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        var isPaused = false
                        try {
                            val releasedEarly = withTimeoutOrNull(400L) {
                                tryAwaitRelease()
                            } != null

                            if (!releasedEarly) {
                                // User pressed and held for more than 400ms -> Freeze story
                                isPaused = true
                                currentOnPause()
                                tryAwaitRelease()
                            } else {
                                // Short tap (< 400ms) -> Check left vs right half
                                if (offset.x < size.width * 0.5f) {
                                    currentOnPrevious()
                                } else {
                                    currentOnNext()
                                }
                            }
                        } finally {
                            if (isPaused) {
                                currentOnResume()
                            }
                        }
                    }
                )
            }
    )
}
