package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.alananasss.kittytune.data.local.PlayerPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioSettingsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var autoplayEnabled by remember { mutableStateOf(prefs.getAutoplayEnabled()) }
    var persistentQueueEnabled by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var audioQuality by remember { mutableStateOf(prefs.getAudioQuality()) }

    var showQualityDialog by remember { mutableStateOf(false) }

    if (showQualityDialog) {
        AlertDialog(
            onDismissRequest = { showQualityDialog = false },
            title = { Text(stringResource(R.string.pref_quality)) },
            text = {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                audioQuality = "HIGH"
                                prefs.setAudioQuality("HIGH")
                                showQualityDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioQuality == "HIGH",
                            onClick = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.quality_high), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.quality_high_sub), style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                audioQuality = "LOW"
                                prefs.setAudioQuality("LOW")
                                showQualityDialog = false
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = audioQuality == "LOW",
                            onClick = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(stringResource(R.string.quality_low), fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.quality_low_sub), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showQualityDialog = false }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pref_audio_title)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_close))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                SettingsCategoryHeader(stringResource(R.string.settings_cat_playback))

                SwitchSettingItem(
                    icon = Icons.Rounded.Radio,
                    title = stringResource(R.string.pref_autoplay),
                    subtitle = stringResource(R.string.pref_autoplay_sub),
                    checked = autoplayEnabled,
                    onCheckedChange = {
                        autoplayEnabled = it
                        prefs.setAutoplayEnabled(it)
                    }
                )

                SwitchSettingItem(
                    icon = Icons.Rounded.Save,
                    title = stringResource(R.string.pref_persist_queue),
                    subtitle = stringResource(R.string.pref_persist_queue_sub),
                    checked = persistentQueueEnabled,
                    onCheckedChange = {
                        persistentQueueEnabled = it
                        prefs.setPersistentQueueEnabled(it)
                    }
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                SettingsCategoryHeader(stringResource(R.string.settings_cat_audio))

                SettingsItemWithSubtitle(
                    icon = Icons.Rounded.HighQuality,
                    title = stringResource(R.string.pref_quality),
                    subtitle = if (audioQuality == "HIGH") stringResource(R.string.quality_high) else stringResource(R.string.quality_low),
                    onClick = { showQualityDialog = true }
                )
            }
        }
    }
}