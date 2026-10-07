package com.alananasss.kittytune.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.sync.KnownDevice
import com.alananasss.kittytune.data.sync.SyncClient
import com.alananasss.kittytune.data.sync.SyncLikes
import com.alananasss.kittytune.data.sync.SyncLog
import com.alananasss.kittytune.data.sync.SyncPeers
import com.alananasss.kittytune.data.sync.SyncPlayback
import com.alananasss.kittytune.data.sync.SyncScheduler
import com.alananasss.kittytune.data.sync.SyncService
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import kotlinx.coroutines.launch

/**
 * The Sync category folder hub:
 * Paired Devices, Sync Data, Advanced Sync.
 */
@Composable
fun SyncSettingsScreen(
    navController: NavController,
    onBackClick: () -> Unit
) {
    SettingsFolderScreen(
        title = stringResource(R.string.sync_title),
        pages = SettingsSubPage.syncPages,
        navController = navController,
        onBackClick = onBackClick
    )
}

/**
 * Paired Devices subpage:
 * Status card, list of connected computers/phones, pair via QR.
 */
@Composable
fun SyncDevicesScreen(onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerPrefs = remember { PlayerPreferences(context) }

    var devices by remember { mutableStateOf(SyncPeers.all()) }
    var status by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var showDisclaimerDialog by remember { mutableStateOf(!playerPrefs.isSyncDisclaimerDismissed()) }

    val isSyncing by SyncScheduler.isSyncing.collectAsState()
    val lastSyncAtMs by SyncScheduler.lastSyncAtMs.collectAsState()

    LaunchedEffect(lastSyncAtMs, scanning) { devices = SyncPeers.all() }

    var localNetworkGranted by remember { mutableStateOf(hasLocalNetworkAccess(context)) }
    val localNetworkLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { localNetworkGranted = it }

    LaunchedEffect(Unit) {
        if (!localNetworkGranted) localNetworkLauncher.launch(LOCAL_NETWORK_PERMISSION)
    }

    val doneTemplate = stringResource(R.string.sync_paired_with)
    val failedTemplate = stringResource(R.string.sync_failed)
    val unauthorized = stringResource(R.string.sync_unauthorized)
    val badCode = stringResource(R.string.sync_bad_code)
    val allDoneTemplate = stringResource(R.string.sync_all_done)

    fun pair(code: String) {
        val peer = SyncService.parsePairingCode(code)
        if (peer == null) {
            status = badCode
            return
        }
        busy = true
        scope.launch {
            val result = SyncClient.exchange(peer)
            busy = false
            devices = SyncPeers.all()
            status = when (result) {
                is SyncClient.Result.Success -> {
                    SyncService.isListenerEnabled = true
                    SyncScheduler.start()
                    String.format(doneTemplate, result.peerName)
                }
                SyncClient.Result.Unauthorized -> unauthorized
                is SyncClient.Result.Failed -> String.format(failedTemplate, result.reason)
            }
        }
    }

    if (scanning) {
        QrScanSheet(
            onCode = { text ->
                scanning = false
                pair(text)
            },
            onDismiss = { scanning = false },
        )
    }

    SettingsScaffold(title = stringResource(R.string.sync_paired_devices_title), onBackClick = onBackClick) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
        ) {
            item { ConnectPanel() }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        stringResource(R.string.sync_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    StatusCard(
                        devices = devices,
                        isSyncing = isSyncing,
                        onSyncNow = {
                            scope.launch {
                                SyncScheduler.syncAll("button")
                                devices = SyncPeers.all()
                                status = allDoneTemplate
                            }
                        },
                    )

                    if (!localNetworkGranted) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    stringResource(R.string.sync_local_network_needed),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = { localNetworkLauncher.launch(LOCAL_NETWORK_PERMISSION) },
                                    shapes = ButtonDefaults.shapes(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError,
                                    ),
                                ) {
                                    Text(stringResource(R.string.sync_local_network_allow))
                                }
                            }
                        }
                    }

                    Button(
                        onClick = { scanning = true },
                        enabled = !busy,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.QrCodeScanner, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_pair_device))
                    }

                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                    status?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    if (devices.isNotEmpty()) {
                        SettingsGroupTitle(stringResource(R.string.sync_devices))
                        devices.forEachIndexed { index, device ->
                            DeviceRow(
                                device = device,
                                shape = getSettingsShape(devices.size, index),
                                onForget = {
                                    SyncPeers.forget(device.deviceId)
                                    devices = SyncPeers.all()
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDisclaimerDialog) {
        var dontShowAgain by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = {
                if (dontShowAgain) playerPrefs.setSyncDisclaimerDismissed(true)
                showDisclaimerDialog = false
            },
            icon = {
                Icon(
                    Icons.Rounded.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            },
            title = {
                Text(
                    text = stringResource(R.string.sync_disclaimer_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.sync_disclaimer_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontShowAgain = !dontShowAgain }
                            .padding(vertical = 4.dp),
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.sync_disclaimer_dont_show_again),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (dontShowAgain) playerPrefs.setSyncDisclaimerDismissed(true)
                        showDisclaimerDialog = false
                    },
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
        )
    }
}

/**
 * Sync Options subpage:
 * Choose what to sync (Liked tracks and playlists).
 */
@Composable
fun SyncOptionsScreen(onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playerPrefs = remember { PlayerPreferences(context) }
    var likesSyncEnabled by remember { mutableStateOf(playerPrefs.getSyncLikesEnabled()) }
    var playbackSyncEnabled by remember { mutableStateOf(SyncPlayback.enabled) }

    SettingsScaffold(title = stringResource(R.string.sync_options_title), onBackClick = onBackClick) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SettingsGroupTitle(stringResource(R.string.sync_options_title))

                    SettingsItem(
                        title = stringResource(R.string.sync_likes_title),
                        subtitle = stringResource(R.string.sync_likes_sub),
                        icon = Icons.Rounded.Favorite,
                        shape = getSettingsShape(2, 0),
                        hasSwitch = true,
                        switchState = likesSyncEnabled,
                        onSwitchChange = {
                            likesSyncEnabled = it
                            playerPrefs.setSyncLikesEnabled(it)
                            if (it) {
                                scope.launch {
                                    SyncLikes.seedMissing()
                                    SyncLikes.seedMissingPlaylists()
                                    SyncScheduler.triggerImmediateSync("likes_toggled")
                                }
                            }
                        }
                    )
                    SettingsItem(
                        title = stringResource(R.string.sync_playback_title),
                        subtitle = stringResource(R.string.sync_playback_sub),
                        icon = Icons.Rounded.Sync,
                        shape = getSettingsShape(2, 1),
                        hasSwitch = true,
                        switchState = playbackSyncEnabled,
                        onSwitchChange = {
                            playbackSyncEnabled = it
                            SyncPlayback.enabled = it
                            if (it) SyncScheduler.triggerImmediateSync("playback enabled")
                        }
                    )
                }
            }
        }
    }
}

/**
 * Advanced Sync subpage:
 * Listener, address, manual peer code, logs.
 */
@Composable
fun SyncAdvancedScreen(onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    var devices by remember { mutableStateOf(SyncPeers.all()) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val doneTemplate = stringResource(R.string.sync_paired_with)
    val failedTemplate = stringResource(R.string.sync_failed)
    val unauthorized = stringResource(R.string.sync_unauthorized)
    val badCode = stringResource(R.string.sync_bad_code)

    fun pair(code: String) {
        val peer = SyncService.parsePairingCode(code)
        if (peer == null) {
            status = badCode
            return
        }
        busy = true
        scope.launch {
            val result = SyncClient.exchange(peer)
            busy = false
            devices = SyncPeers.all()
            status = when (result) {
                is SyncClient.Result.Success -> {
                    SyncService.isListenerEnabled = true
                    SyncScheduler.start()
                    String.format(doneTemplate, result.peerName)
                }
                SyncClient.Result.Unauthorized -> unauthorized
                is SyncClient.Result.Failed -> String.format(failedTemplate, result.reason)
            }
        }
    }

    SettingsScaffold(title = stringResource(R.string.sync_advanced_title), onBackClick = onBackClick) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(top = 8.dp, bottom = 180.dp),
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AdvancedSection(
                        onPasteCode = { pair(it) },
                        onForgetAll = {
                            SyncPeers.forgetAll()
                            devices = SyncPeers.all()
                        },
                    )

                    if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

                    status?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/**
 * The answer to "is this working?", in one card.
 */
@Composable
private fun StatusCard(
    devices: List<KnownDevice>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
) {
    val paired = devices.isNotEmpty()
    val lastSynced = devices.mapNotNull { it.lastSyncedAtMs.takeIf { at -> at > 0 } }.maxOrNull()

    val onContainer = MaterialTheme.colorScheme.onSurface
    val onContainerMuted = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = if (paired) MaterialTheme.colorScheme.primary else onContainerMuted

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = onContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isSyncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Rounded.Sync, null, modifier = Modifier.size(20.dp), tint = accent)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    when {
                        isSyncing -> stringResource(R.string.sync_state_syncing)
                        !paired -> stringResource(R.string.sync_state_not_paired)
                        lastSynced == null -> stringResource(R.string.sync_state_never)
                        else -> stringResource(R.string.sync_state_in_step)
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onContainer,
                )
            }
            Text(
                when {
                    !paired -> stringResource(R.string.sync_state_not_paired_sub)
                    lastSynced == null -> stringResource(R.string.sync_state_never_sub)
                    else -> String.format(
                        stringResource(R.string.sync_last_synced),
                        agoLabel(LocalContext.current, lastSynced),
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = onContainerMuted,
            )
            if (paired) {
                val canDial = devices.any { it.canDial }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onSyncNow,
                        enabled = !isSyncing && canDial,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(R.string.sync_now))
                    }
                    if (!canDial) {
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(R.string.sync_only_inbound),
                            style = MaterialTheme.typography.bodySmall,
                            color = onContainerMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: KnownDevice, shape: Shape, onForget: () -> Unit) {
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (device.platform == SyncService.PLATFORM) Icons.Rounded.PhoneAndroid
                    else Icons.Rounded.Computer,
                    null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    device.label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    if (device.lastSyncedAtMs > 0) String.format(
                        stringResource(R.string.sync_last_synced),
                        agoLabel(LocalContext.current, device.lastSyncedAtMs),
                    ) else stringResource(R.string.sync_state_never_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onForget) { Text(stringResource(R.string.sync_forget_device)) }
        }
    }
}

@Composable
private fun AdvancedSection(
    onPasteCode: (String) -> Unit,
    onForgetAll: () -> Unit,
) {
    var deviceName by remember { mutableStateOf(SyncLog.deviceName) }
    var listenerOn by remember { mutableStateOf(SyncService.isListenerEnabled) }
    var peerCode by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = deviceName,
            onValueChange = {
                deviceName = it
                SyncLog.deviceName = it
            },
            label = { Text(stringResource(R.string.sync_device_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            SettingsItem(
                shape = getSettingsShape(3, 0),
                title = stringResource(R.string.sync_listener),
                subtitle = stringResource(R.string.sync_listener_sub),
                hasSwitch = true,
                switchState = listenerOn,
                onSwitchChange = {
                    listenerOn = it
                    SyncService.isListenerEnabled = it
                },
            )
            SettingsItem(
                shape = getSettingsShape(3, 1),
                title = stringResource(R.string.sync_address),
                subtitle = "${SyncService.localAddress()}:${SyncService.port}",
            )
            SettingsItem(
                shape = getSettingsShape(3, 2),
                title = stringResource(R.string.sync_events_held_title),
                subtitle = String.format(stringResource(R.string.sync_events_held), SyncLog.size()),
            )
        }

        OutlinedTextField(
            value = peerCode,
            onValueChange = { peerCode = it },
            label = { Text(stringResource(R.string.sync_peer_code)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    onPasteCode(peerCode)
                    peerCode = ""
                },
                enabled = peerCode.isNotBlank(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(stringResource(R.string.sync_pair))
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onForgetAll) {
                Text(stringResource(R.string.sync_forget_all))
            }
        }
    }
}

private fun agoLabel(context: android.content.Context, atMs: Long): String {
    val elapsed = (System.currentTimeMillis() - atMs).coerceAtLeast(0L)
    val minutes = elapsed / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> context.getString(R.string.sync_just_now)
        minutes < 60 -> "$minutes min"
        hours < 24 -> "$hours h"
        days == 1L -> context.getString(R.string.sync_yesterday)
        else -> String.format(context.getString(R.string.sync_days_ago), days)
    }
}

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

private fun hasLocalNetworkAccess(context: android.content.Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT < 37) {
        true
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }
