package com.alananasss.kittytune.wear.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.alananasss.kittytune.wear.R
import com.alananasss.kittytune.wear.common.TargetDeviceInfo
import com.alananasss.kittytune.wear.presentation.theme.WearOnBackground
import com.alananasss.kittytune.wear.presentation.theme.WearOnPrimaryContainer
import com.alananasss.kittytune.wear.presentation.theme.WearOnSurface
import com.alananasss.kittytune.wear.presentation.theme.WearOnTertiaryContainer
import com.alananasss.kittytune.wear.presentation.theme.WearOutlineVariant
import com.alananasss.kittytune.wear.presentation.theme.WearPrimaryContainer
import com.alananasss.kittytune.wear.presentation.theme.WearSurfaceContainer
import com.alananasss.kittytune.wear.presentation.theme.WearTertiaryContainer

/**
 * Modal dialog presented when the user taps the target chip and several playback devices are reachable.
 *
 * Every entry is a Wear Material 3 [Button], so each row keeps the platform button shape, minimum
 * height and ripple, and the connected state is announced as part of the row's secondary label.
 */
@Composable
fun TargetPickerDialog(
    visible: Boolean,
    currentTargetId: String,
    availableTargets: List<TargetDeviceInfo>,
    onSelectTarget: (TargetDeviceInfo) -> Unit,
    onDismissRequest: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        visible = visible,
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                text = stringResource(R.string.target_picker_title),
                style = MaterialTheme.typography.titleSmall,
                color = WearOnBackground
            )
        }
    ) {
        items(availableTargets, key = { it.id }) { target ->
            val isSelected = target.id == currentTargetId
            val containerColor = if (isSelected) {
                if (target.isPhone) WearPrimaryContainer else WearTertiaryContainer
            } else {
                WearSurfaceContainer
            }
            val contentColor = if (isSelected) {
                if (target.isPhone) WearOnPrimaryContainer else WearOnTertiaryContainer
            } else {
                WearOnSurface
            }
            val connectedColor = if (target.isConnected) Color(0xFF81C784) else WearOutlineVariant

            Button(
                onClick = {
                    onSelectTarget(target)
                    onDismissRequest()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = containerColor,
                    contentColor = contentColor
                ),
                label = {
                    Text(
                        text = target.name.ifBlank {
                            stringResource(if (target.isPhone) R.string.target_phone else R.string.target_desktop)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                secondaryLabel = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(connectedColor)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                if (target.isConnected) R.string.target_picker_connected else R.string.target_picker_offline
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = connectedColor
                        )
                    }
                }
            )
        }
    }
}
