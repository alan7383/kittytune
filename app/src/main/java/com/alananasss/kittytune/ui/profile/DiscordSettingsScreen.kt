package com.alananasss.kittytune.ui.profile

import android.content.Intent
import android.widget.Toast
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
import com.my.kizzy.rpc.KizzyRPC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DiscordSettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val scope = rememberCoroutineScope()

    var token by remember { mutableStateOf(prefs.getDiscordToken()) }
    var username by remember { mutableStateOf(prefs.getDiscordUsername()) }
    var isEnabled by remember { mutableStateOf(prefs.getDiscordRpcEnabled()) }
    var statusDisplay by remember { mutableStateOf(prefs.getDiscordStatusDisplay()) }
    val isLoggedIn = !token.isNullOrEmpty()

    var showStatusDialog by remember { mutableStateOf(false) }
    var showTokenDialog by remember { mutableStateOf(false) }
    var tokenInput by remember { mutableStateOf(token ?: "") }
    var tokenLoading by remember { mutableStateOf(false) }
    var tokenError by remember { mutableStateOf<String?>(null) }

    // Try to resolve username in background if token exists but username is missing
    LaunchedEffect(token) {
        if (!token.isNullOrEmpty() && username.isNullOrEmpty()) {
            val res = withContext(Dispatchers.IO) { KizzyRPC.getUserInfo(token!!) }
            if (res.isSuccess) {
                val info = res.getOrNull()
                val uname = info?.name?.ifBlank { null } ?: info?.username
                if (!uname.isNullOrEmpty()) {
                    prefs.setDiscordUsername(uname)
                    username = uname
                }
            }
        }
    }

    if (showTokenDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!tokenLoading) showTokenDialog = false
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Key,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(stringResource(R.string.discord_manual_token_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.discord_manual_token_desc),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = {
                            tokenInput = it
                            tokenError = null
                        },
                        placeholder = { Text(stringResource(R.string.discord_manual_token_placeholder)) },
                        singleLine = true,
                        isError = tokenError != null,
                        supportingText = tokenError?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = tokenInput.trim().replace("\"", "")
                        if (clean.isBlank()) {
                            tokenError = context.getString(R.string.discord_manual_token_invalid)
                            return@Button
                        }
                        tokenLoading = true
                        scope.launch {
                            val userInfoResult = withContext(Dispatchers.IO) {
                                KizzyRPC.getUserInfo(clean)
                            }
                            tokenLoading = false
                            if (userInfoResult.isSuccess) {
                                val info = userInfoResult.getOrNull()
                                val uname = info?.name?.ifBlank { null } ?: info?.username
                                prefs.setDiscordToken(clean)
                                uname?.let { prefs.setDiscordUsername(it) }
                                prefs.setDiscordRpcEnabled(true)
                                token = clean
                                username = uname
                                isEnabled = true
                                context.startService(Intent(context, PlaybackService::class.java).apply {
                                    action = PlaybackService.ACTION_FORCE_UPDATE
                                })
                                showTokenDialog = false
                                Toast.makeText(context, context.getString(R.string.discord_login_success), Toast.LENGTH_SHORT).show()
                            } else {
                                tokenError = context.getString(R.string.discord_manual_token_invalid)
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !tokenLoading && tokenInput.isNotBlank()
                ) {
                    if (tokenLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.btn_save))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showTokenDialog = false },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !tokenLoading
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

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
            confirmButton = { TextButton(onClick = { showStatusDialog = false }, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.discord_rpc_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                val connectionSubtitle = if (isLoggedIn) {
                    if (!username.isNullOrEmpty()) {
                        stringResource(R.string.discord_connected_as, username!!)
                    } else {
                        stringResource(R.string.discord_token_present)
                    }
                } else {
                    stringResource(R.string.discord_connect_desc)
                }

                SettingsGroup(
                    title = stringResource(R.string.discord_status_header),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = if (isLoggedIn) stringResource(R.string.discord_connected) else stringResource(R.string.discord_not_connected),
                                subtitle = connectionSubtitle,
                                onClick = { if (!isLoggedIn) onNavigateToLogin() }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.discord_manual_token_title),
                                subtitle = if (isLoggedIn) stringResource(R.string.discord_token_present) else stringResource(R.string.discord_manual_token_desc),
                                onClick = {
                                    tokenInput = token ?: ""
                                    tokenError = null
                                    showTokenDialog = true
                                }
                            )
                        },
                        { shape ->
                            if (isLoggedIn) {
                                SettingsItem(
                                    shape = shape,
                                    title = stringResource(R.string.discord_logout),
                                    onClick = {
                                        prefs.setDiscordToken(null)
                                        prefs.setDiscordUsername(null)
                                        prefs.setDiscordRpcEnabled(false)
                                        token = null
                                        username = null
                                        isEnabled = false
                                        context.startService(Intent(context, PlaybackService::class.java).apply {
                                            action = PlaybackService.ACTION_FORCE_UPDATE
                                        })
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
                                    context.startService(Intent(context, PlaybackService::class.java).apply {
                                        action = PlaybackService.ACTION_FORCE_UPDATE
                                    })
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
                                    subtitle = when (statusDisplay) {
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
