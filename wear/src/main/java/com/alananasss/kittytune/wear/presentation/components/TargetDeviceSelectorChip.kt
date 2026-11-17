package com.alananasss.kittytune.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.minimumInteractiveComponentSize
import com.alananasss.kittytune.wear.R
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import com.alananasss.kittytune.wear.presentation.theme.WearOutlineVariant
import com.alananasss.kittytune.wear.presentation.theme.WearPrimary
import com.alananasss.kittytune.wear.presentation.theme.WearPrimaryContainer
import com.alananasss.kittytune.wear.presentation.theme.WearTertiary
import com.alananasss.kittytune.wear.presentation.theme.WearTertiaryContainer

/**
 * Standard dimension constants for TargetDeviceSelectorChip.
 * Enforces strict compliance with Google Wear OS HIG and WCAG 2.5.5 minimum 48x48dp interactive touch target bounds.
 */
object TargetDeviceSelectorChipDefaults {
    /** Minimum accessible touch target per Wear OS HIG (48x48dp / 2304 dp^2). */
    val MinTouchTargetSize: Dp = 48.dp

    /** Maximum width of the visual pill so long device names cannot reach the round bezel. */
    val MaxVisualWidth: Dp = 96.dp

    /** Player glyph size, kept proportional to the label so it never dominates the pill. */
    val DeviceIconSize: Dp = 12.dp
}

/**
 * Compact, touch-friendly pill displayed below TimeText on the circular watch screen.
 * Shows the active playback target (Phone or Desktop) with a color-coded connection indicator.
 *
 * Touch Target Ergonomics:
 * - Outer container enforces minimum 48x48dp touch bounds via [minimumInteractiveComponentSize]
 *   and [Modifier.defaultMinSize].
 * - Inner visual pill maintains compact vertical padding (4dp) and rounded corner styling (14dp),
 *   satisfying accessibility standards without visually bloating or clipping adjacent UI elements.
 *
 * The label is localized, and the whole pill exposes a single spoken description that includes the
 * connection state, so TalkBack users never have to infer it from the colour of the dot.
 */
@Composable
fun TargetDeviceSelectorChip(
    target: TargetDeviceInfo,
    connectionStatus: ConnectionStatus,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPhone = target.isPhone
    val containerColor = if (isPhone) WearPrimaryContainer.copy(alpha = 0.5f) else WearTertiaryContainer.copy(alpha = 0.5f)
    val contentColor = if (isPhone) WearPrimary else WearTertiary

    val targetStatus = if (connectionStatus == ConnectionStatus.CONNECTED && !target.isConnected && !target.isPhone) {
        ConnectionStatus.DISCONNECTED
    } else connectionStatus
    val indicatorColor = when (targetStatus) {
        ConnectionStatus.CONNECTED -> if (isPhone) WearPrimary else WearTertiary
        ConnectionStatus.CONNECTING -> Color(0xFFFFD54F) // Amber pulse
        ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> WearOutlineVariant
    }

    val fallbackName = stringResource(if (isPhone) R.string.target_phone else R.string.target_desktop)
    val deviceName = target.name.ifBlank { fallbackName }
    val statusLabel = stringResource(
        when (targetStatus) {
            ConnectionStatus.CONNECTED -> R.string.target_status_connected
            ConnectionStatus.CONNECTING -> R.string.target_status_connecting
            ConnectionStatus.DISCONNECTED -> R.string.target_status_disconnected
            ConnectionStatus.ERROR -> R.string.target_status_error
        }
    )
    val description = stringResource(R.string.target_selector_description, deviceName, statusLabel)

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = TargetDeviceSelectorChipDefaults.MinTouchTargetSize,
                minHeight = TargetDeviceSelectorChipDefaults.MinTouchTargetSize
            )
            .minimumInteractiveComponentSize()
            .semantics { contentDescription = description }
            .selectable(
                selected = target.isSelected,
                onClick = onClick,
                role = Role.Button
            ),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = TargetDeviceSelectorChipDefaults.MaxVisualWidth)
                .clip(MaterialTheme.shapes.small)
                .background(containerColor)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Device glyph: rounded-phone / monitor, never an emoji (it renders as tofu on some watches)
            Icon(
                imageVector = if (isPhone) Icons.Filled.Smartphone else Icons.Filled.Computer,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(TargetDeviceSelectorChipDefaults.DeviceIconSize)
            )
            Spacer(modifier = Modifier.width(4.dp))

            // Target device name (clamped so a long peer name cannot clip on the bezel)
            Text(
                text = deviceName,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))

            // Visual connection status dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
        }
    }
}
