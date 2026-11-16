package com.alananasss.kittytune.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.minimumInteractiveComponentSize
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
}

/**
 * Compact, touch-friendly pill displayed below TimeText on the circular watch screen.
 * Shows active playback target (Phone vs Desktop) with a color-coded connection indicator.
 *
 * Touch Target Ergonomics:
 * - Outer container enforces minimum 48x48dp touch bounds via [minimumInteractiveComponentSize]
 *   and [Modifier.defaultMinSize].
 * - Inner visual pill maintains compact vertical padding (4dp) and rounded corner styling (14dp),
 *   satisfying accessibility standards without visually bloating or clipping adjacent UI elements.
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

    val indicatorColor = when (connectionStatus) {
        ConnectionStatus.CONNECTED -> if (isPhone) WearPrimary else WearTertiary
        ConnectionStatus.CONNECTING -> Color(0xFFFFD54F) // Amber pulse
        ConnectionStatus.DISCONNECTED, ConnectionStatus.ERROR -> WearOutlineVariant
    }

    Box(
        modifier = modifier
            .defaultMinSize(
                minWidth = TargetDeviceSelectorChipDefaults.MinTouchTargetSize,
                minHeight = TargetDeviceSelectorChipDefaults.MinTouchTargetSize
            )
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(containerColor)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Target Device Icon Emoji
            Text(
                text = if (isPhone) "📱" else "💻",
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(modifier = Modifier.width(4.dp))

            // Target Device Name (clamped to avoid edge clipping)
            Text(
                text = target.name.ifBlank { if (isPhone) "Phone" else "Desktop" },
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))

            // Visual Connection Status Dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
        }
    }
}
