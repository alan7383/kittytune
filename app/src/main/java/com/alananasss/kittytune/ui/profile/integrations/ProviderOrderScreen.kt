package com.alananasss.kittytune.ui.profile.integrations

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.providers.AudioProviderOrder
import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.SettingsSwitch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ProviderOrderScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    val prefs = remember { PlayerPreferences(context) }

    val currentList = remember { mutableStateListOf<AudioProviderOrderItem>() }
    var disabledProviders by remember { mutableStateOf(prefs.getDisabledAudioProviders()) }
    var meteredSaver by remember { mutableStateOf(prefs.getDisableProvidersOnMetered()) }

    LaunchedEffect(Unit) {
        currentList.clear()
        currentList.addAll(prefs.getAudioProviderOrder())
    }

    fun persistOrder() {
        prefs.setAudioProviderOrder(currentList.toList())
    }

    fun setProviderEnabled(item: AudioProviderOrderItem, enabled: Boolean) {
        prefs.setAudioProviderDisabled(item, !enabled)
        disabledProviders = prefs.getDisabledAudioProviders()
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    val listState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val moved = currentList.removeAt(from.index)
            currentList.add(to.index, moved)
            persistOrder()
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    )

    SettingsScaffold(
        title = stringResource(R.string.provider_order),
        onBackClick = onBackClick
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.provider_order_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp, bottom = 16.dp)
            )

            // Traffic saver: automatically skip Qobuz / TIDAL / Deezer while on mobile data.
            // On Wi-Fi the providers become available again with full Hi-Res quality.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SignalCellularAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.provider_data_saver_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.provider_data_saver_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                SettingsSwitch(
                    checked = meteredSaver,
                    onCheckedChange = { enabled ->
                        meteredSaver = enabled
                        prefs.setDisableProvidersOnMetered(enabled)
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(currentList, key = { _, item -> item.name }) { index, item ->
                    ReorderableItem(state = reorderableState, key = item.name) { isDragging ->
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                        val backgroundColor = if (isDragging) {
                            MaterialTheme.colorScheme.surfaceVariant
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                        val isDisabled = item in disabledProviders

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(elevation, RoundedCornerShape(16.dp))
                                .clip(RoundedCornerShape(16.dp))
                                .background(backgroundColor)
                                .padding(horizontal = 16.dp, vertical = 14.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(if (isDisabled) 0.45f else 1f)
                            ) {
                                Text(
                                    text = "${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(28.dp)
                                )
                                Icon(
                                    painter = painterResource(
                                        when (item) {
                                            AudioProviderOrderItem.QOBUZ -> R.drawable.ic_logo_qobuz
                                            AudioProviderOrderItem.TIDAL -> R.drawable.ic_logo_tidal
                                            AudioProviderOrderItem.DEEZER -> R.drawable.ic_logo_deezer
                                            AudioProviderOrderItem.YOUTUBE_MUSIC -> R.drawable.ic_logo_youtube_music
                                            AudioProviderOrderItem.SOUNDCLOUD -> R.drawable.ic_soundcloud
                                        }
                                    ),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text(
                                    text = stringResource(
                                        when (item) {
                                            AudioProviderOrderItem.QOBUZ -> R.string.audio_provider_qobuz
                                            AudioProviderOrderItem.TIDAL -> R.string.audio_provider_tidal
                                            AudioProviderOrderItem.DEEZER -> R.string.audio_provider_deezer
                                            AudioProviderOrderItem.YOUTUBE_MUSIC -> R.string.audio_provider_youtube_music
                                            AudioProviderOrderItem.SOUNDCLOUD -> R.string.audio_provider_soundcloud
                                        }
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (item.isDisableable()) {
                                Spacer(modifier = Modifier.width(12.dp))
                                SettingsSwitch(
                                    checked = !isDisabled,
                                    onCheckedChange = { enabled -> setProviderEnabled(item, enabled) }
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = "Move",
                                tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(28.dp)
                                    .draggableHandle(
                                        onDragStarted = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        },
                                        onDragStopped = {
                                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            Text(
                text = stringResource(R.string.provider_enabled_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp)
            )

            TextButton(
                onClick = {
                    currentList.clear()
                    currentList.addAll(AudioProviderOrder.Default)
                    persistOrder()
                    AudioProviderOrder.Disableable.forEach { prefs.setAudioProviderDisabled(it, false) }
                    disabledProviders = prefs.getDisabledAudioProviders()
                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.provider_order_reset))
            }
        }
    }
}
