package com.alananasss.kittytune.ui.profile

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.audio.haptics.performAppHaptic
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.ui.common.SettingsSwitch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun LyricsProviderOrderDialog(prefs: PlayerPreferences, onDismiss: () -> Unit) {
    val view = LocalView.current
    val dialogListState = rememberLazyListState()
    val currentOrder = remember {
        mutableStateListOf<PreferredLyricsProvider>().apply { addAll(prefs.getLyricsProviderOrder()) }
    }
    var currentEnabled by remember {
        mutableStateOf(
            PreferredLyricsProvider.entries.associateWith { prefs.getLyricsProviderEnabled(it) }
        )
    }
    val reorderState = rememberReorderableLazyListState(
        lazyListState = dialogListState,
        onMove = { from, to ->
            val item = currentOrder.removeAt(from.index)
            currentOrder.add(to.index, item)
            view.performAppHaptic(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pref_lyrics_order)) },
        text = {
            LazyColumn(
                state = dialogListState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(currentOrder, key = { _, provider -> provider.name }) { index, provider ->
                    ReorderableItem(state = reorderState, key = provider.name) { isDragging ->
                        val elevation by animateDpAsState(
                            if (isDragging) 6.dp else 0.dp,
                            label = "providerDrag"
                        )
                        val isEnabled = currentEnabled[provider] ?: true
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                            shadowElevation = elevation,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 4.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .draggableHandle(
                                            onDragStarted = {
                                                view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS)
                                            },
                                            onDragStopped = {
                                                view.performAppHaptic(HapticFeedbackConstants.GESTURE_END)
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragIndicator,
                                        contentDescription = stringResource(R.string.reorder_handle),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .alpha(if (isEnabled) 1f else 0.45f),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(28.dp)
                                    )
                                    Text(
                                        provider.displayName,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                SettingsSwitch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        currentEnabled = currentEnabled + (provider to checked)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                prefs.setLyricsProviderOrder(currentOrder.toList())
                currentEnabled.forEach { (p, enabled) ->
                    prefs.setLyricsProviderEnabled(p, enabled)
                }
                onDismiss()
            }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
            }
        }
    )
}
