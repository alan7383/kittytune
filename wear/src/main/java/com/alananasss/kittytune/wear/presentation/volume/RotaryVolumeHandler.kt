package com.alananasss.kittytune.wear.presentation.volume

import androidx.compose.foundation.focusable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.abs

/**
 * Modifier extension that equips any Wear OS layout container with rotary crown volume handling.
 *
 * Features:
 * - Immediate active focus acquisition via [FocusRequester] and [LaunchedEffect].
 * - Auto re-focus on Activity/Screen resume ([Lifecycle.Event.ON_RESUME]).
 * - Detent accumulation preventing jitter and Bluetooth Data Layer flooding.
 * - Robust NaN / Infinite scroll event delta protection preserving accumulator state.
 * - Haptic tactile feedback on each discrete detent tick.
 * - Boundary clamping at 0.0f and 1.0f via [VolumeSanitizer].
 *
 * @param currentVolume Current normalized volume fraction (0.0f..1.0f).
 * @param onVolumeChange Callback triggered when volume changes, supplying the new sanitized volume.
 * @param stepVolume Volume change per detent notch (default: 0.05f = 5%).
 * @param detentThresholdPx Pixel scroll threshold required to trigger 1 step (default: 24.0f).
 * @param reverseDirection Invert rotation mapping if required by hardware.
 */
fun Modifier.rotaryVolumeControl(
    currentVolume: Float,
    onVolumeChange: (Float) -> Unit,
    stepVolume: Float = VolumeSanitizer.DEFAULT_STEP,
    detentThresholdPx: Float = 24.0f,
    reverseDirection: Boolean = false
): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val currentOnVolumeChange by rememberUpdatedState(onVolumeChange)
    val latestVolume by rememberUpdatedState(currentVolume)

    var accumulatedPixels by remember { mutableFloatStateOf(0.0f) }

    // Request active focus immediately on composition
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Re-request focus when returning from background / ambient mode
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                focusRequester.requestFocus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val safeThresholdPx = if (detentThresholdPx.isNaN() || detentThresholdPx.isInfinite() || detentThresholdPx <= 0.0f) {
        24.0f
    } else {
        detentThresholdPx
    }

    this
        .focusRequester(focusRequester)
        .focusable()
        .onRotaryScrollEvent { event ->
            val scrollDelta = event.verticalScrollPixels
            // Guard against IEEE-754 NaN or Infinite scroll deltas from hardware/emulator glitches
            if (scrollDelta.isNaN() || scrollDelta.isInfinite()) {
                return@onRotaryScrollEvent true // safely ignore corrupt delta and consume event
            }

            // Self-heal accumulator if it was ever corrupted
            if (accumulatedPixels.isNaN() || accumulatedPixels.isInfinite()) {
                accumulatedPixels = 0.0f
            }

            val rawDelta = if (reverseDirection) -scrollDelta else scrollDelta
            accumulatedPixels += rawDelta

            if (abs(accumulatedPixels) >= safeThresholdPx) {
                val stepCount = (accumulatedPixels / safeThresholdPx).toInt()
                accumulatedPixels -= (stepCount * safeThresholdPx)

                val oldVol = VolumeSanitizer.sanitize(latestVolume)
                val newVol = VolumeSanitizer.sanitize(oldVol + (stepCount * stepVolume))

                if (newVol != oldVol) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    currentOnVolumeChange(newVol)
                }
            }
            true // consume rotary scroll event
        }
}
