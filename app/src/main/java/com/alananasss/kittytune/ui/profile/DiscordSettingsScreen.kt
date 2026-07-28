package com.alananasss.kittytune.ui.profile

import android.content.Intent
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.PlaybackService
import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.getSettingsShape

@Composable
fun DiscordSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    var token by remember { mutableStateOf(prefs.getDiscordToken()) }
    var isEnabled by remember { mutableStateOf(prefs.getDiscordRpcEnabled()) }
    var statusDisplay by remember { mutableStateOf(prefs.getDiscordStatusDisplay()) }
    val isLoggedIn = !token.isNullOrEmpty()

    var showStatusDialog by remember { mutableStateOf(false) }

    if (showStatusDialog) {
        AlertDialog(
            onDismissRequest = { showStatusDialog = false },
            title = { Text(stringResource(R.string.pref_discord_status_display)) },
            text = {
                Column {
                    StatusDisplayRadioButton(stringResource(R.string.discord_status_activity), DiscordStatusDisplay.ACTIVITY, statusDisplay) {
                        statusDisplay = it; prefs.setDiscordStatusDisplay(it); showStatusDialog = false
                        context.startService(Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_FORCE_UPDATE })
                    }
                    StatusDisplayRadioButton(stringResource(R.string.discord_status_soundcloud), DiscordStatusDisplay.SOUNDCLOUD, statusDisplay) {
                        statusDisplay = it; prefs.setDiscordStatusDisplay(it); showStatusDialog = false
                        context.startService(Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_FORCE_UPDATE })
                    }
                    StatusDisplayRadioButton(stringResource(R.string.discord_status_artist), DiscordStatusDisplay.ARTIST, statusDisplay) {
                        statusDisplay = it; prefs.setDiscordStatusDisplay(it); showStatusDialog = false
                        context.startService(Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_FORCE_UPDATE })
                    }
                    StatusDisplayRadioButton(stringResource(R.string.discord_status_song), DiscordStatusDisplay.SONG, statusDisplay) {
                        statusDisplay = it; prefs.setDiscordStatusDisplay(it); showStatusDialog = false
                        context.startService(Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_FORCE_UPDATE })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showStatusDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.discord_rpc_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.discord_status_header),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = if(isLoggedIn) stringResource(R.string.discord_connected) else stringResource(R.string.discord_not_connected),
                                subtitle = if(isLoggedIn) stringResource(R.string.discord_token_present) else stringResource(R.string.discord_connect_desc),
                                onClick = { if(!isLoggedIn) onNavigateToLogin() }
                            )
                        },
                        { shape ->
                            if (isLoggedIn) {
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.discord_logout),
                                    onClick = {
                                        prefs.setDiscordToken(null)
                                        prefs.setDiscordRpcEnabled(false)
                                        token = null
                                        isEnabled = false
                                    }
                                )
                            }
                        }
                    )
                )

                if (isLoggedIn) {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        SettingsGroupTitle(stringResource(R.string.discord_options_header))

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            val animatedBottomRadius by animateDpAsState(
                                targetValue = if (isEnabled) 4.dp else 24.dp,
                                animationSpec = tween(400),
                                label = "DiscordRpcCornerAnimation"
                            )

                            SettingsItem(
                                shape = RoundedCornerShape(
                                    topStart = 24.dp,
                                    topEnd = 24.dp,
                                    bottomStart = animatedBottomRadius,
                                    bottomEnd = animatedBottomRadius
                                ),
                                title = stringResource(R.string.discord_enable_rpc),
                                subtitle = stringResource(R.string.discord_enable_rpc_desc),
                                hasSwitch = true,
                                switchState = isEnabled,
                                onSwitchChange = {
                                    isEnabled = it
                                    prefs.setDiscordRpcEnabled(it)
                                }
                            )

                            AnimatedVisibility(
                                visible = isEnabled,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                SettingsItem(
                                    shape = getSettingsShape(2, 1),
                                    title = stringResource(R.string.pref_discord_status_display),
                                    subtitle = when(statusDisplay) {
                                        DiscordStatusDisplay.ACTIVITY -> stringResource(R.string.discord_status_activity)
                                        DiscordStatusDisplay.SOUNDCLOUD -> stringResource(R.string.discord_status_soundcloud)
                                        DiscordStatusDisplay.ARTIST -> stringResource(R.string.discord_status_artist)
                                        DiscordStatusDisplay.SONG -> stringResource(R.string.discord_status_song)
                                    },
                                    onClick = { showStatusDialog = true }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusDisplayRadioButton(text: String, mode: DiscordStatusDisplay, selected: DiscordStatusDisplay, onSelect: (DiscordStatusDisplay) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onSelect(mode) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = (mode == selected), onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(text)
    }
}
