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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text

/**
 * Compact, circular-safe discrete volume button row for Wear OS.
 *
 * Ergonomics:
 * - Minimum accessible touch targets.
 * - Displays active volume percentage (0% to 100%) and dynamic speaker icon in center.
 * - Dimmed / disabled styling at boundaries (0% and 100%).
 */
@Composable
fun VolumeControlsRow(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = VolumeSanitizer.DEFAULT_STEP
) {
    val haptic = LocalHapticFeedback.current
    val sanitized = VolumeSanitizer.sanitize(volume)
    val percentage = VolumeSanitizer.toPercentage(sanitized)

    val canDecrease = sanitized > VolumeSanitizer.MIN_VOLUME
    val canIncrease = sanitized < VolumeSanitizer.MAX_VOLUME

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Volume Decrease (-) Button
        Button(
            onClick = {
                if (canDecrease) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onVolumeChange(VolumeSanitizer.decrement(sanitized, step))
                }
            },
            enabled = canDecrease,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Decrease volume by 5%" },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (canDecrease) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Center Volume Indicator (Icon + Percentage)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.semantics { contentDescription = "Current volume $percentage percent" }
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
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "$percentage%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Volume Increase (+) Button
        Button(
            onClick = {
                if (canIncrease) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onVolumeChange(VolumeSanitizer.increment(sanitized, step))
                }
            },
            enabled = canIncrease,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = "Increase volume by 5%" },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = if (canIncrease) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f),
                disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
