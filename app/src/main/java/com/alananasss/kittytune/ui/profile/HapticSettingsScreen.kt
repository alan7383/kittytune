package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import com.alananasss.kittytune.ui.common.Slider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.haptics.PlayerHapticManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlin.math.roundToInt

@Composable
fun HapticSettingsScreen(
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val hapticManager = remember { PlayerHapticManager.getInstance(context) }
    val hasHardware = remember { hapticManager.hasHardwareHaptics() }

    var hapticsEnabled by remember { mutableStateOf(prefs.getHapticsEnabled()) }
    var hapticsStrength by remember { mutableStateOf(prefs.getHapticsStrength()) }
    var hapticsContrast by remember { mutableStateOf(prefs.getHapticContrast()) }
    var playPauseHaptics by remember { mutableStateOf(prefs.getHapticsPlayPause()) }
    var seekHaptics by remember { mutableStateOf(prefs.getHapticsSeek()) }
    var likeHaptics by remember { mutableStateOf(prefs.getHapticsLike()) }
    var queueHaptics by remember { mutableStateOf(prefs.getHapticsQueue()) }

    SettingsScaffold(
        title = stringResource(R.string.pref_haptics_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp, top = 8.dp)
        ) {
            // Master Haptics Section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SettingsGroupTitle(stringResource(R.string.settings_cat_playback))

                    val strengthBottomRadius by animateDpAsState(
                        targetValue = if (hapticsEnabled) 4.dp else 24.dp,
                        label = "HapticsCornerAnim"
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        SettingsItem(
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = strengthBottomRadius,
                                bottomEnd = strengthBottomRadius
                            ),
                            title = stringResource(R.string.pref_haptics_enable),
                            subtitle = if (!hasHardware) {
                                stringResource(R.string.pref_haptics_not_supported)
                            } else {
                                stringResource(R.string.pref_haptics_enable_sub)
                            },
                            hasSwitch = true,
                            switchState = hapticsEnabled,
                            onSwitchChange = { enabled ->
                                if (hasHardware) {
                                    hapticsEnabled = enabled
                                    playerViewModel.toggleHaptics(enabled)
                                    if (enabled) {
                                        hapticManager.triggerTestVibration(hapticsStrength)
                                    }
                                }
                            }
                        )

                        AnimatedVisibility(
                            visible = hapticsEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 4.dp,
                                    topEnd = 4.dp,
                                    bottomStart = 24.dp,
                                    bottomEnd = 24.dp
                                ),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.pref_haptics_strength),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = "${hapticsStrength.roundToInt()}%",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Slider(
                                        value = hapticsStrength,
                                        onValueChange = { newVal ->
                                            hapticsStrength = newVal
                                            playerViewModel.updateHapticsStrength(newVal)
                                        },
                                        onValueChangeFinished = {
                                            hapticManager.triggerTestVibration(hapticsStrength)
                                        },
                                        valueRange = 10f..100f,
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Spacer(Modifier.height(18.dp))

                                    // Contrast, not level. At 0 the motor tracks loudness, so a
                                    // build-up buzzes as hard as the drop it leads into and the
                                    // drop has nowhere left to go.
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.pref_haptics_contrast),
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                        Text(
                                            text = when {
                                                hapticsContrast <= 0.02f ->
                                                    stringResource(R.string.pref_haptics_contrast_off)
                                                hapticsContrast >= 0.98f ->
                                                    stringResource(R.string.pref_haptics_contrast_full)
                                                else -> "${(hapticsContrast * 100).roundToInt()}%"
                                            },
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Text(
                                        text = stringResource(R.string.pref_haptics_contrast_sub),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Slider(
                                        value = hapticsContrast,
                                        onValueChange = { newVal ->
                                            hapticsContrast = newVal
                                            prefs.setHapticContrast(newVal)
                                            hapticManager.setHapticContrast(newVal)
                                        },
                                        valueRange = 0f..1f,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Granular Interaction Haptics
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    SettingsGroupTitle(stringResource(R.string.pref_haptics_interactions))

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val totalItems = 4

                        SettingsItem(
                            shape = getSettingsShape(totalItems, 0),
                            title = stringResource(R.string.pref_haptics_play_pause),
                            subtitle = stringResource(R.string.pref_haptics_play_pause_sub),
                            hasSwitch = true,
                            switchState = playPauseHaptics,
                            onSwitchChange = {
                                playPauseHaptics = it
                                prefs.setHapticsPlayPause(it)
                            }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalItems, 1),
                            title = stringResource(R.string.pref_haptics_seek),
                            subtitle = stringResource(R.string.pref_haptics_seek_sub),
                            hasSwitch = true,
                            switchState = seekHaptics,
                            onSwitchChange = {
                                seekHaptics = it
                                prefs.setHapticsSeek(it)
                            }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalItems, 2),
                            title = stringResource(R.string.pref_haptics_like),
                            subtitle = stringResource(R.string.pref_haptics_like_sub),
                            hasSwitch = true,
                            switchState = likeHaptics,
                            onSwitchChange = {
                                likeHaptics = it
                                prefs.setHapticsLike(it)
                            }
                        )

                        SettingsItem(
                            shape = getSettingsShape(totalItems, 3),
                            title = stringResource(R.string.pref_haptics_queue),
                            subtitle = stringResource(R.string.pref_haptics_queue_sub),
                            hasSwitch = true,
                            switchState = queueHaptics,
                            onSwitchChange = {
                                queueHaptics = it
                                prefs.setHapticsQueue(it)
                            }
                        )
                    }
                }
            }
        }
    }
}
