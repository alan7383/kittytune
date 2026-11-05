package com.alananasss.kittytune.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.presentation.ConnectionStatus
import com.alananasss.kittytune.wear.presentation.theme.WearOutlineVariant
import com.alananasss.kittytune.wear.presentation.theme.WearPrimary
import com.alananasss.kittytune.wear.presentation.theme.WearPrimaryContainer
import com.alananasss.kittytune.wear.presentation.theme.WearTertiary
import com.alananasss.kittytune.wear.presentation.theme.WearTertiaryContainer

/**
 * Compact, touch-friendly pill displayed below TimeText on the circular watch screen.
 * Shows active playback target (Phone vs Desktop) with a color-coded connection indicator.
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

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
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
