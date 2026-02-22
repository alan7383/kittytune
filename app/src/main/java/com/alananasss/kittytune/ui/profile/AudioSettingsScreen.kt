package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.YouTubeFallbackMode
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun AudioSettingsScreen(
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    val tokenManager = remember { TokenManager(context) }
    val isLoggedIn = remember {
        !tokenManager.isGuestMode() && !tokenManager.getAccessToken().isNullOrEmpty()
    }

    var autoplayEnabled by remember { mutableStateOf(prefs.getAutoplayEnabled()) }
    var syncLikes by remember { mutableStateOf(prefs.getSyncLikesEnabled()) }
    var persistentQueueEnabled by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var audioQuality by remember { mutableStateOf(prefs.getAudioQuality()) }

    var youtubeFallbackEnabled by remember { mutableStateOf(prefs.getYouTubeFallbackEnabled()) }
    var fallbackMode by remember { mutableStateOf(prefs.getYouTubeFallbackMode()) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showFallbackModeDialog by remember { mutableStateOf(false) }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.pref_quality)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { audioQuality = "HIGH"; prefs.setAudioQuality("HIGH"); showQualityDialog = false }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = audioQuality == "HIGH", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column { Text(stringResource(R.string.quality_high), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.quality_high_sub), style = MaterialTheme.typography.bodySmall) }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { audioQuality = "LOW"; prefs.setAudioQuality("LOW"); showQualityDialog = false }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = audioQuality == "LOW", onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Column { Text(stringResource(R.string.quality_low), fontWeight = FontWeight.SemiBold); Text(stringResource(R.string.quality_low_sub), style = MaterialTheme.typography.bodySmall) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showQualityDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showFallbackModeDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackModeDialog = false },
            title = { Text(stringResource(R.string.pref_fallback_mode)) },
            text = {
                Column {
                    FallbackRadioButton(stringResource(R.string.fallback_auto), YouTubeFallbackMode.AUTOMATIC, fallbackMode) {
                        fallbackMode = it; prefs.setYouTubeFallbackMode(it); showFallbackModeDialog = false
                    }
                    FallbackRadioButton(stringResource(R.string.fallback_newpipe), YouTubeFallbackMode.NEWPIPE, fallbackMode) {
                        fallbackMode = it; prefs.setYouTubeFallbackMode(it); showFallbackModeDialog = false
                    }
                    FallbackRadioButton(stringResource(R.string.fallback_invidious), YouTubeFallbackMode.INVIDIOUS, fallbackMode) {
                        fallbackMode = it; prefs.setYouTubeFallbackMode(it); showFallbackModeDialog = false
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFallbackModeDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_audio_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            if (isLoggedIn) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SettingsGroupTitle(stringResource(R.string.settings_cat_general))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            SettingsItem(
                                shape = com.alananasss.kittytune.ui.common.getSettingsShape(1, 0),
                                title = stringResource(R.string.pref_sync_likes),
                                subtitle = stringResource(R.string.pref_sync_likes_sub),
                                icon = androidx.compose.material.icons.Icons.Rounded.CloudSync,
                                hasSwitch = true,
                                switchState = syncLikes,
                                onSwitchChange = {
                                    syncLikes = it
                                    prefs.setSyncLikesEnabled(it)
                                }
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_playback))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val totalVisibleItems = if (youtubeFallbackEnabled) 5 else 4

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = stringResource(R.string.pref_autoplay),
                            subtitle = stringResource(R.string.pref_autoplay_sub),
                            icon = Icons.Rounded.Radio,
                            hasSwitch = true,
                            switchState = autoplayEnabled,
                            onSwitchChange = { autoplayEnabled = it; prefs.setAutoplayEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 1),
                            title = stringResource(R.string.pref_persist_queue),
                            subtitle = stringResource(R.string.pref_persist_queue_sub),
                            icon = Icons.Rounded.Save,
                            hasSwitch = true,
                            switchState = persistentQueueEnabled,
                            onSwitchChange = { persistentQueueEnabled = it; prefs.setPersistentQueueEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 2),
                            title = stringResource(R.string.pref_youtube_fallback),
                            subtitle = stringResource(R.string.pref_youtube_fallback_sub),
                            icon = Icons.Rounded.LockOpen,
                            hasSwitch = true,
                            switchState = youtubeFallbackEnabled,
                            onSwitchChange = { youtubeFallbackEnabled = it; prefs.setYouTubeFallbackEnabled(it) }
                        )

                        AnimatedVisibility(
                            visible = youtubeFallbackEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = getSettingsShape(5, 3),
                                title = stringResource(R.string.pref_fallback_mode),
                                subtitle = when(fallbackMode) {
                                    YouTubeFallbackMode.AUTOMATIC -> stringResource(R.string.fallback_auto)
                                    YouTubeFallbackMode.NEWPIPE -> stringResource(R.string.fallback_newpipe)
                                    YouTubeFallbackMode.INVIDIOUS -> stringResource(R.string.fallback_invidious)
                                },
                                icon = Icons.Rounded.SettingsEthernet,
                                onClick = { showFallbackModeDialog = true }
                            )
                        }

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, totalVisibleItems - 1),
                            title = stringResource(R.string.pref_precise_speed),
                            subtitle = stringResource(R.string.pref_precise_speed_sub),
                            icon = Icons.Rounded.Speed,
                            hasSwitch = true,
                            switchState = playerViewModel.isPreciseSpeedEnabled,
                            onSwitchChange = { playerViewModel.togglePreciseSpeedEnabled(it) }
                        )
                    }
                }
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_cat_audio),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.pref_quality),
                                subtitle = if (audioQuality == "HIGH") stringResource(R.string.quality_high) else stringResource(R.string.quality_low),
                                icon = Icons.Rounded.HighQuality,
                                onClick = { showQualityDialog = true }
                            )
                        }
                    )
                )
            }
        }
    }
}

@Composable
fun FallbackRadioButton(text: String, mode: YouTubeFallbackMode, selected: YouTubeFallbackMode, onSelect: (YouTubeFallbackMode) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (mode == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}