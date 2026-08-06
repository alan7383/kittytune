package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SplitSettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.PlayerViewModel

@Composable
fun AudioSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToDrmExplanation: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var autoplayEnabled by remember { mutableStateOf(prefs.getAutoplayEnabled()) }
    var stopOnTaskClear by remember { mutableStateOf(prefs.getStopOnTaskClear()) }
    var persistentQueueEnabled by remember { mutableStateOf(prefs.getPersistentQueueEnabled()) }
    var audioQuality by remember { mutableStateOf(prefs.getAudioQuality()) }

    var youtubeFallbackEnabled by remember { mutableStateOf(prefs.getYouTubeFallbackEnabled()) }
    var downloadDrmEnabled by remember { mutableStateOf(prefs.getDownloadDrmStreamsEnabled()) }
    var fadeEnabled by remember { mutableStateOf(prefs.getSleepTimerFadeEnabled()) }
    var fadeDuration by remember { mutableStateOf(prefs.getSleepTimerFadeDuration()) }
    
    var crossfadeEnabled by remember { mutableStateOf(prefs.getCrossfadeEnabled()) }
    var crossfadeDuration by remember { mutableStateOf(prefs.getCrossfadeDuration()) }

    var showQualityDialog by remember { mutableStateOf(false) }
    var showFadeDurationDialog by remember { mutableStateOf(false) }
    var showCrossfadeDurationDialog by remember { mutableStateOf(false) }
    var showNormalizationDialog by remember { mutableStateOf(false) }
    var showNormalizationInfoDialog by remember { mutableStateOf(false) }

    if (showFadeDurationDialog) {
        AlertDialog(
            onDismissRequest = { showFadeDurationDialog = false },
            title = { Text(stringResource(R.string.sleep_timer_fade_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.sleep_timer_fade_subtitle, fadeDuration),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = fadeDuration.toFloat(),
                        onValueChange = {
                            fadeDuration = it.toInt()
                            prefs.setSleepTimerFadeDuration(it.toInt())
                        },
                        valueRange = PlayerPreferences.SLEEP_TIMER_FADE_DURATION_MIN.toFloat()..PlayerPreferences.SLEEP_TIMER_FADE_DURATION_MAX.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFadeDurationDialog = false }) {
                    Text(stringResource(R.string.btn_ok))
                }
            }
        )
    }

    if (showCrossfadeDurationDialog) {
        AlertDialog(
            onDismissRequest = { showCrossfadeDurationDialog = false },
            title = { Text(stringResource(R.string.pref_crossfade_title)) },
            text = {
                Column {
                    Text(
                        text = "${crossfadeDuration}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    Slider(
                        value = crossfadeDuration.toFloat(),
                        onValueChange = {
                            crossfadeDuration = it.toInt()
                            prefs.setCrossfadeDuration(it.toInt())
                        },
                        valueRange = 1f..12f,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showCrossfadeDurationDialog = false }) {
                    Text(stringResource(R.string.btn_ok))
                }
            }
        )
    }

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

    if (showNormalizationDialog) {
        AlertDialog(
            onDismissRequest = { showNormalizationDialog = false },
            icon = { Icon(Icons.Rounded.Equalizer, null) },
            title = {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = stringResource(R.string.pref_norm_title),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(horizontal = 32.dp)
                    )
                    IconButton(
                        onClick = { showNormalizationInfoDialog = true },
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .size(32.dp),
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = stringResource(R.string.pref_norm_info_title),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pref_norm_sub), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup(
                        options = com.alananasss.kittytune.ui.player.NormalizationLevel.entries,
                        selectedOption = playerViewModel.effectsState.normalizationLevel,
                        onOptionSelected = { level ->
                            playerViewModel.setNormalizationLevel(level)
                            if (!playerViewModel.effectsState.isNormalizationEnabled) playerViewModel.toggleNormalization()
                        },
                        labelProvider = { level ->
                            val isSelected = (level == playerViewModel.effectsState.normalizationLevel)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = when (level) {
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.QUIET -> stringResource(R.string.pref_norm_level_quiet)
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL -> stringResource(R.string.pref_norm_level_normal)
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.LOUD -> stringResource(R.string.pref_norm_level_loud)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                Text(
                                    text = when (level) {
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.QUIET -> "\u221219 LUFS"
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL -> "\u221214 LUFS"
                                        com.alananasss.kittytune.ui.player.NormalizationLevel.LOUD -> "\u221211 LUFS"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.8f),
                                    maxLines = 1
                                )
                            }
                        }
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showNormalizationDialog = false }) { Text(stringResource(R.string.btn_ok)) } }
        )
    }

    if (showNormalizationInfoDialog) {
        AlertDialog(
            onDismissRequest = { showNormalizationInfoDialog = false },
            icon = { Icon(Icons.Rounded.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = {
                Text(
                    text = stringResource(R.string.pref_norm_info_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pref_norm_info_body_1),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.pref_norm_info_body_2),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showNormalizationInfoDialog = false }) {
                    Text(stringResource(R.string.btn_ok))
                }
            }
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
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {



            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_playback))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val totalVisibleItems = 7

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
                            title = stringResource(R.string.pref_stop_on_task_clear),
                            hasSwitch = true,
                            switchState = stopOnTaskClear,
                            onSwitchChange = { stopOnTaskClear = it; prefs.setStopOnTaskClear(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 2),
                            title = stringResource(R.string.pref_persist_queue),
                            subtitle = stringResource(R.string.pref_persist_queue_sub),
                            hasSwitch = true,
                            switchState = persistentQueueEnabled,
                            onSwitchChange = { persistentQueueEnabled = it; prefs.setPersistentQueueEnabled(it) }
                        )

                        var savePositionEnabled by remember { mutableStateOf(prefs.getSavePositionEnabled()) }
                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 3),
                            title = stringResource(R.string.pref_save_position),
                            subtitle = stringResource(R.string.pref_save_position_sub),
                            hasSwitch = true,
                            switchState = savePositionEnabled,
                            onSwitchChange = { savePositionEnabled = it; prefs.setSavePositionEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 4),
                            title = stringResource(R.string.pref_youtube_fallback),
                            subtitle = stringResource(R.string.pref_youtube_fallback_sub),
                            hasSwitch = true,
                            switchState = youtubeFallbackEnabled,
                            onSwitchChange = { youtubeFallbackEnabled = it; prefs.setYouTubeFallbackEnabled(it) }
                        )

                        SplitSettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 5),
                            title = stringResource(R.string.pref_download_drm),
                            subtitle = stringResource(R.string.pref_download_drm_sub),
                            onClick = onNavigateToDrmExplanation,
                            switchState = downloadDrmEnabled,
                            onSwitchChange = { downloadDrmEnabled = it; prefs.setDownloadDrmStreamsEnabled(it) }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 6),
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
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle("Audio DSP")

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val totalVisibleItems = 2

                        SettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 0),
                            title = stringResource(R.string.pref_audio_mono),
                            subtitle = stringResource(R.string.pref_audio_mono_sub),
                            hasSwitch = true,
                            switchState = playerViewModel.effectsState.isMonoEnabled,
                            onSwitchChange = { playerViewModel.toggleMono() }
                        )

                        SplitSettingsItem(
                            shape = getSettingsShape(totalVisibleItems, 1),
                            title = stringResource(R.string.pref_norm_title),
                            subtitle = stringResource(R.string.pref_norm_sub),
                            onClick = { showNormalizationDialog = true },
                            switchState = playerViewModel.effectsState.isNormalizationEnabled,
                            onSwitchChange = { playerViewModel.toggleNormalization() }
                        )
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.sleep_timer_title))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val fadeBottomRadius by animateDpAsState(
                            targetValue = if (fadeEnabled) 4.dp else 24.dp,
                            label = "FadeCornerAnimation"
                        )

                        SettingsItem(
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = fadeBottomRadius,
                                bottomEnd = fadeBottomRadius
                            ),
                            title = stringResource(R.string.sleep_timer_fade_title),
                            subtitle = stringResource(R.string.sleep_timer_fade_subtitle, fadeDuration),
                            hasSwitch = true,
                            switchState = fadeEnabled,
                            onSwitchChange = { 
                                fadeEnabled = it
                                prefs.setSleepTimerFadeEnabled(it)
                            }
                        )

                        AnimatedVisibility(
                            visible = fadeEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 24.dp,
                                    bottomEnd = 24.dp
                                ),
                                title = stringResource(R.string.label_duration),
                                subtitle = stringResource(R.string.sleep_timer_fade_subtitle, fadeDuration),
                                onClick = { showFadeDurationDialog = true }
                            )
                        }
                    }
                }
            }

            item {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    SettingsGroupTitle(stringResource(R.string.pref_crossfade_title))
                    
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val crossfadeBottomRadius by animateDpAsState(
                            targetValue = if (crossfadeEnabled) 4.dp else 24.dp,
                            label = "CrossfadeCornerAnimation"
                        )

                        SettingsItem(
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = crossfadeBottomRadius,
                                bottomEnd = crossfadeBottomRadius
                            ),
                            title = stringResource(R.string.pref_crossfade_title),
                            subtitle = stringResource(R.string.pref_crossfade_sub),
                            hasSwitch = true,
                            switchState = crossfadeEnabled,
                            onSwitchChange = { 
                                crossfadeEnabled = it
                                prefs.setCrossfadeEnabled(it)
                            }
                        )

                        AnimatedVisibility(
                            visible = crossfadeEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            SettingsItem(
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 24.dp,
                                    bottomEnd = 24.dp
                                ),
                                title = stringResource(R.string.label_duration),
                                subtitle = "${crossfadeDuration}s",
                                onClick = { showCrossfadeDurationDialog = true }
                            )
                        }
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
