package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

    var showQualityDialog by remember { mutableStateOf(false) }

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
                        val totalVisibleItems = 4

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = stringResource(R.string.pref_autoplay),
                            subtitle = stringResource(R.string.pref_autoplay_sub),
                            hasSwitch = true,
                            switchState = autoplayEnabled,
                            onSwitchChange = { autoplayEnabled = it; prefs.setAutoplayEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 1),
                            title = stringResource(R.string.pref_persist_queue),
                            subtitle = stringResource(R.string.pref_persist_queue_sub),
                            hasSwitch = true,
                            switchState = persistentQueueEnabled,
                            onSwitchChange = { persistentQueueEnabled = it; prefs.setPersistentQueueEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 2),
                            title = stringResource(R.string.pref_youtube_fallback),
                            subtitle = stringResource(R.string.pref_youtube_fallback_sub),
                            hasSwitch = true,
                            switchState = youtubeFallbackEnabled,
                            onSwitchChange = { youtubeFallbackEnabled = it; prefs.setYouTubeFallbackEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 3),
                            title = stringResource(R.string.pref_precise_speed),
                            subtitle = stringResource(R.string.pref_precise_speed_sub),
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
                                onClick = { showQualityDialog = true }
                            )
                        }
                    )
                )
            }
        }
    }
}
