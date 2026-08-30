package com.alananasss.kittytune.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.sync.KnownDevice
import com.alananasss.kittytune.data.sync.SyncClient
import com.alananasss.kittytune.data.sync.SyncLog
import com.alananasss.kittytune.data.sync.SyncPeers
import com.alananasss.kittytune.data.sync.SyncScheduler
import com.alananasss.kittytune.data.sync.SyncService
import com.alananasss.kittytune.ui.common.SettingsGroupTitle
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold
import com.alananasss.kittytune.ui.common.getSettingsShape
import kotlinx.coroutines.launch

/**
 * Pairing with a computer, and seeing that it is working (issue #33).
 *
 * The state comes first and in a sentence — in step, or not, and with what — because that is the only
 * question anyone opens this screen with, and the previous version answered it nowhere: it showed a name
 * field, a code field and a count of log entries, none of which say whether sync is happening.
 *
 * There is one action, *pair with a computer*, which is a camera. Everything that is a mechanism rather
 * than a decision is behind **Advanced**, where it can be found when something has gone wrong and ignored
 * the rest of the time. The desktop's screen is laid out the same way, deliberately.
 */
@Composable
fun SyncSettingsScreen(onBackClick: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var devices by remember { mutableStateOf(SyncPeers.all()) }
    var status by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val isSyncing by SyncScheduler.isSyncing.collectAsState()
    val lastSyncAtMs by SyncScheduler.lastSyncAtMs.collectAsState()

    LaunchedEffect(lastSyncAtMs, scanning) { devices = SyncPeers.all() }

    // Android 16 and later drop connections to local addresses unless this is granted, and they drop them
    // silently — the app sees a plain TCP timeout, so without asking for it the whole feature looks like a
    // network fault (issue #33). Older releases have no such permission and report it as granted, so the
    // banner never appears there.
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

    /** One place for both ways in, so scanning and pasting cannot drift apart. */
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
                    // Paired means paired: the listener comes up so the computer can start the next
                    // exchange itself, and the scheduler keeps them in step from here on.
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

    SettingsScaffold(title = stringResource(R.string.sync_title), onBackClick = onBackClick) { padding ->
        // The same shape as every other settings screen: the scaffold's insets applied as padding, and a
        // generous bottom so the last row clears the player bar. This screen only applied the *top* inset and
        // reserved 48 dp at the bottom, which is why it sat tight against the edges and ran under the player
        // (issue #33).
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp),
        ) {
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
                                    // Stays inside the error role it sits in; the default primary fill on
                                    // an errorContainer card is two unrelated hues touching.
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

                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { showAdvanced = !showAdvanced }) {
                        Icon(
                            if (showAdvanced) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.sync_advanced))
                    }
                    AnimatedVisibility(visible = showAdvanced) {
                        AdvancedSection(
                            onPasteCode = { pair(it) },
                            onForgetAll = {
                                SyncPeers.forgetAll()
                                devices = SyncPeers.all()
                            },
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

/**
 * The answer to "is this working?", in one card.
 *
 * Deliberately a sentence rather than a set of fields. What was wrong before was not that the address and
 * the log size were hidden — it is that they were the whole screen, and neither answers the question.
 */
@Composable
private fun StatusCard(
    devices: List<KnownDevice>,
    isSyncing: Boolean,
    onSyncNow: () -> Unit,
) {
    val paired = devices.isNotEmpty()
    val lastSynced = devices.mapNotNull { it.lastSyncedAtMs.takeIf { at -> at > 0 } }.maxOrNull()

    // A plain card with the accent on the icon, not a saturated slab.
    //
    // This was a `primaryContainer` plate, and under a dynamic theme whose primary and primaryContainer are
    // both the same bright colour, the filled button inside it disappeared: two near-identical fills with
    // the label floating between them. Nothing about the state needs a full block of colour to be legible,
    // and the card now uses the same container as every other card on the page, so a normal primary button
    // on top of it has maximum contrast in every theme rather than in most of them.
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
                    // The one spot of accent: coloured when in step, grey when there is nothing to be in
                    // step with. It carries the state without shouting it.
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
                        // Honest rather than a button that does nothing: some devices can only call in.
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

/**
 * The mechanism, for when something has gone wrong.
 *
 * Nothing here is needed to use sync. It is here so a firewall, a computer whose code was regenerated, or
 * a phone with the camera refused can be dealt with — and nowhere near the parts used daily.
 */
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

        // Their own column at 2 dp, which is what the other settings screens use. [getSettingsShape] gives a
        // group large outer corners and small inner ones so the rows read as one block — and that only works
        // if they touch. Inheriting the section's 8 dp pulled them apart into three cards with mismatched
        // corners, which looks like a layout fault rather than a group (issue #33).
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

/**
 * "just now", "3 min", "2 h", "yesterday" — how long ago something happened.
 *
 * Deliberately coarse: the useful question is "is this still happening?", and a timestamp to the second
 * invites staring at it.
 */
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

/**
 * The permission Android 16 introduced for reaching devices on the same network.
 *
 * A literal rather than `Manifest.permission.ACCESS_LOCAL_NETWORK` so this still builds against an older
 * platform jar; the name is stable and the string is what the framework matches on anyway.
 */
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/**
 * @return whether local-network access is available.
 *
 * Always true below SDK 36: the restriction does not exist there, and asking would fail rather than being
 * harmlessly granted.
 */
private fun hasLocalNetworkAccess(context: android.content.Context): Boolean =
    if (android.os.Build.VERSION.SDK_INT < 36) {
        true
    } else {
        androidx.core.content.ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

