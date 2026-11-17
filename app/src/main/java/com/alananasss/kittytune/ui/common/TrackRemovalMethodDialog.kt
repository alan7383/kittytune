package com.alananasss.kittytune.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.TrackRemovalMethod

fun trackRemovalMethodIcon(method: TrackRemovalMethod): ImageVector = when (method) {
    TrackRemovalMethod.SWIPE_AND_MENU -> Icons.Rounded.DeleteSweep
    TrackRemovalMethod.MENU_ONLY -> Icons.Rounded.MoreVert
    TrackRemovalMethod.DISABLED -> Icons.Rounded.Lock
}

@Composable
fun TrackRemovalMethodDialog(
    selectedMethod: TrackRemovalMethod,
    onMethodSelected: (TrackRemovalMethod) -> Unit,
    onDismissRequest: () -> Unit
) {
    val haptics = rememberAppHaptics()

    AlertDialog(
        onDismissRequest = onDismissRequest,
        icon = {
            Icon(
                imageVector = trackRemovalMethodIcon(selectedMethod),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.pref_track_removal_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = stringResource(R.string.pref_track_removal_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )

                TrackRemovalMethod.entries.forEach { method ->
                    val isSelected = selectedMethod == method
                    val containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
                    }
                    val titleColor = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                    val subtitleColor = if (isSelected) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = containerColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    haptics.perform(HapticKind.UI)
                                    onMethodSelected(method)
                                }
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = null
                            )
                            Spacer(Modifier.width(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = trackRemovalMethodIcon(method),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(method.titleRes),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = titleColor
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = stringResource(method.descRes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = subtitleColor
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = hapticAction(HapticKind.UI, onDismissRequest)) {
                Text(stringResource(R.string.btn_close))
            }
        }
    )
}
