package com.alananasss.kittytune.wear.presentation.volume

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.touchTargetAwareSize
import com.alananasss.kittytune.wear.R

/**
 * Standard dimension constants and design defaults for Wear OS volume controls.
 * Ensures strict compliance with Google Wear OS accessibility guidelines (WCAG 2.5.5)
 * requiring minimum 48x48dp interactive touch target bounding boxes while preserving
 * circular display chord safety on standard 390x390 and 450x450 round watch displays.
 */
object VolumeControlsDefaults {
    /** Minimum accessible touch target per Wear OS HIG & WCAG 2.5.5 (48x48dp / 2304 dp^2). */
    val MinTouchTargetSize: Dp = 48.dp

    /** Visual button size matching Wear Compose ExtraSmallButtonSize (32x32dp) for circular bezel fit. */
    val ButtonSize: Dp = 32.dp

    /** Visual glyph size centered within the 32dp button container (16x16dp with 8dp padding). */
    val IconSize: Dp = 16.dp

    /** Horizontal spacing between buttons and center volume speaker indicator. */
    val RowSpacing: Dp = 0.dp

    /** Width allocated for the compact center volume indicator container (speaker glyph). */
    val IndicatorWidth: Dp = 16.dp

    /** Center volume indicator speaker glyph size. */
    val IndicatorIconSize: Dp = 14.dp

    /**
     * Authoritative total visual width of [VolumeControlsRow] (112.dp <= 112.dp chord limit at y in [30, 60]dp).
     * Formula: 2 * MinTouchTargetSize (96dp) + IndicatorWidth (16dp) = 112.dp.
     */
    val RowWidth: Dp = MinTouchTargetSize * 2 + RowSpacing * 2 + IndicatorWidth // 112.dp

    /**
     * Layout height of the volume row. The buttons are 32dp wide but need a 48dp touch target, so
     * the row occupies the full target height: the extra 8dp above each button must not reach the
     * playback triad, which is the neighbouring interactive row.
     */
    val RowHeight: Dp = MinTouchTargetSize // 48.dp
}

/**
 * Compact, circular-safe discrete volume button row for Wear OS.
 *
 * Ergonomics & Accessibility:
 * - Satisfies Google Wear OS HIG and WCAG 2.5.5 with minimum 48x48dp interactive touch targets
 *   via [touchTargetAwareSize] on 32x32dp visual buttons.
 * - Total row width of exactly 112dp fits within allowable circular display chord clearances
 *   at vertical offsets y in [30, 60]dp on 390x390 round displays with >= 8dp safe margin and
 *   >= 16dp physical bezel margin.
 * - Fixed 16dp speaker indicator leaves both buttons their full 48dp touch targets.
 * - Symmetrical 0dp horizontal spacing preserves compact layout hierarchy.
 * - Displays a dynamic speaker icon; the bezel and transient HUD show the volume level.
 * - Buttons use the Wear Material 3 animated [IconButtonDefaults.animatedShapes] so the container
 *   morphs on press, giving tactile visual feedback instead of a flat circle.
 * - Dimmed / disabled styling at boundaries (0% and 100%).
 */
@Composable
fun VolumeControlsRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = VolumeSanitizer.DEFAULT_STEP,
    buttonSize: Dp = VolumeControlsDefaults.ButtonSize
) {
    val haptic = LocalHapticFeedback.current
    val sanitized = VolumeSanitizer.sanitize(volume)
    val percentage = VolumeSanitizer.toPercentage(sanitized)

    val canDecrease = sanitized > VolumeSanitizer.MIN_VOLUME
    val canIncrease = sanitized < VolumeSanitizer.MAX_VOLUME

    val stepPercent = VolumeSanitizer.toPercentage(step).coerceAtLeast(1)
    val decreaseLabel = stringResource(R.string.volume_decrease, stepPercent)
    val increaseLabel = stringResource(R.string.volume_increase, stepPercent)
    val currentLabel = stringResource(R.string.volume_current, percentage)

    Row(
        modifier = modifier.width(VolumeControlsDefaults.RowWidth),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Volume Decrease (-) Button: 32x32dp visual footprint with 48x48dp accessible touch target
        FilledTonalIconButton(
            onClick = {
                val next = VolumeSanitizer.decrement(sanitized, step)
                if (next != sanitized) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onVolumeChange(next)
                }
            },
            enabled = canDecrease,
            shapes = IconButtonDefaults.animatedShapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            modifier = Modifier
                .touchTargetAwareSize(buttonSize)
                .clearAndSetSemantics { contentDescription = decreaseLabel }
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                modifier = Modifier.size(VolumeControlsDefaults.IconSize)
            )
        }

        Spacer(modifier = Modifier.width(VolumeControlsDefaults.RowSpacing))

        // Center volume indicator (glyph + percentage in a fixed 16dp width)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .width(VolumeControlsDefaults.IndicatorWidth)
                .clearAndSetSemantics { contentDescription = currentLabel }
        ) {
            val volumeIcon = when {
                percentage == 0 -> Icons.AutoMirrored.Filled.VolumeMute
                percentage < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                else -> Icons.AutoMirrored.Filled.VolumeUp
            }
            Icon(
                imageVector = volumeIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(VolumeControlsDefaults.IndicatorIconSize)
            )

        }

        Spacer(modifier = Modifier.width(VolumeControlsDefaults.RowSpacing))

        // Volume Increase (+) Button: 32x32dp visual footprint with 48x48dp accessible touch target
        FilledTonalIconButton(
            onClick = {
                val next = VolumeSanitizer.increment(sanitized, step)
                if (next != sanitized) {
                    haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onVolumeChange(next)
                }
            },
            enabled = canIncrease,
            shapes = IconButtonDefaults.animatedShapes(),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            ),
            modifier = Modifier
                .touchTargetAwareSize(buttonSize)
                .clearAndSetSemantics { contentDescription = increaseLabel }
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(VolumeControlsDefaults.IconSize)
            )
        }
    }
}
