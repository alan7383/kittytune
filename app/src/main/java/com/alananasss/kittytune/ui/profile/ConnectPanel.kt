package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.sync.ConnectPlatform
import com.alananasss.kittytune.data.sync.SyncPeers
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet

@Composable
fun ConnectButton(
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    onClick: (() -> Unit)? = null
) {
    var opened by remember { mutableStateOf(false) }
    val selectedDevice by ConnectManager.selectedDevice.collectAsState()
    val isRemoteActive = selectedDevice != null
    val buttonTint = if (isRemoteActive) MaterialTheme.colorScheme.primary else tint

    IconButton(
        onClick = {
            if (onClick != null) onClick() else opened = true
        },
        modifier = modifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isRemoteActive) Icons.Rounded.CastConnected else Icons.Rounded.Devices,
                contentDescription = stringResource(R.string.connect_devices_title),
                tint = buttonTint
            )
        }
    }

    if (opened && onClick == null) {
        PlaybackDevicesSheet(onDismissRequest = { opened = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackDevicesSheet(onDismissRequest: () -> Unit) {
    KittyModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        ConnectPanel(onClose = onDismissRequest)
    }
}

@Composable
fun ConnectPanel(onClose: (() -> Unit)? = null) {
    val peers by ConnectManager.peers.collectAsState()
    val feedback by ConnectManager.feedback.collectAsState()
    val selected by ConnectManager.selectedDevice.collectAsState()
    val busy by ConnectManager.transferring.collectAsState()
    val independent by ConnectManager.independent.collectAsState()
    val autoHeadphones by ConnectManager.autoHeadphones.collectAsState()
    var url by remember { mutableStateOf(ConnectManager.relayUrl) }
    var error by remember { mutableStateOf("") }
    var showSettings by remember { mutableStateOf(false) }
    var showRelay by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { ConnectManager.refresh() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Sheet Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (selected != null) Icons.Rounded.CastConnected else Icons.Rounded.Devices,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Column {
                    Text(
                        text = stringResource(R.string.connect_devices_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.connect_devices_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = { showSettings = !showSettings }) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = if (showSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Active Remote Banner (if connected to another device)
        if (selected != null) {
            val currentPeer = SyncPeers.all().firstOrNull { it.deviceId == selected }
            val peerName = currentPeer?.label ?: selected ?: ""
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CastConnected,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = stringResource(R.string.connect_connected_to, peerName),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(
                        onClick = { ConnectManager.switchOutput(null) },
                        enabled = !busy
                    ) {
                        Text(
                            text = stringResource(R.string.connect_resume_local),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Current Local Device Tile
        DeviceChoiceCard(
            title = stringResource(R.string.connect_this_device),
            subtitle = when {
                independent -> stringResource(R.string.connect_independent_title)
                selected == null -> stringResource(R.string.connect_playing_here)
                else -> stringResource(R.string.connect_this_device_desc)
            },
            icon = Icons.Rounded.Smartphone,
            isSelected = selected == null,
            isEnabled = !busy,
            onClick = {
                if (!independent && selected != null) {
                    ConnectManager.switchOutput(null)
                }
            }
        )

        // Paired Devices List
        val known = SyncPeers.all()
        if (known.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DevicesOther,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.connect_no_devices),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(R.string.connect_no_devices_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.connect_paired_devices),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                known.sortedByDescending { peers[it.deviceId]?.connected == true }.forEach { peer ->
                    val live = peers[peer.deviceId]
                    val statusText = when {
                        independent -> stringResource(R.string.connect_independent_desc)
                        live?.connected != true -> stringResource(R.string.connect_status_offline)
                        live.snapshot?.isPlaying == true -> {
                            val trackTitle = live.snapshot.queue.getOrNull(live.snapshot.currentIndex)?.title.orEmpty()
                            stringResource(R.string.connect_status_playing, trackTitle)
                        }
                        live.transport == "LAN" -> stringResource(R.string.connect_status_lan)
                        else -> stringResource(R.string.connect_status_internet)
                    }

                    DeviceChoiceCard(
                        title = peer.label,
                        subtitle = statusText,
                        icon = if (peer.platform == "android") Icons.Rounded.PhoneAndroid else Icons.Rounded.Computer,
                        isSelected = selected == peer.deviceId,
                        isEnabled = !busy && !independent && live?.connected == true,
                        isOnline = live?.connected == true,
                        onClick = { ConnectManager.switchOutput(peer.deviceId) }
                    )
                }
            }
        }

        // Connection Errors or Busy Indicators
        val connectionError = peers.values.firstOrNull { !it.connected && it.error.isNotBlank() }?.error
        if (!independent && connectionError != null) {
            val errorMsg = if (connectionError.startsWith("HTTP")) {
                stringResource(R.string.connect_error_server, connectionError)
            } else {
                stringResource(R.string.connect_error_connection, connectionError)
            }
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        if (busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.5.dp
                )
                Text(
                    text = stringResource(R.string.connect_moving_playback),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (feedback.isNotBlank() && feedback != "✓") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.errorContainer
            ) {
                Text(
                    text = friendlyFeedback(feedback),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        // Advanced Settings Expandable Panel
        AnimatedVisibility(
            visible = showSettings,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // Independent Playback Switch
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(
                                text = stringResource(R.string.connect_independent_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.connect_independent_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = independent,
                            enabled = !busy,
                            onCheckedChange = ConnectManager::setIndependent
                        )
                    }
                }

                // Headphones Hand-off Switch (Mobile only)
                if (ConnectPlatform.mobile) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = stringResource(R.string.connect_headphones_title),
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = stringResource(R.string.connect_headphones_desc),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoHeadphones,
                                    enabled = !busy && !independent,
                                    onCheckedChange = ConnectManager::setAutoHeadphones
                                )
                            }
                            Text(
                                text = stringResource(R.string.connect_headphones_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Internet Relay Expandable Section
                OutlinedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showRelay = !showRelay },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.connect_relay_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = stringResource(R.string.connect_relay_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = if (showRelay) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (showRelay) {
                            OutlinedTextField(
                                value = url,
                                onValueChange = { url = it; error = "" },
                                singleLine = true,
                                label = { Text(stringResource(R.string.connect_relay_url_label)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    runCatching { ConnectManager.relayUrl = url }.onFailure {
                                        error = "error"
                                    }
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text(stringResource(R.string.btn_save))
                            }
                            if (error.isNotBlank()) {
                                Text(
                                    text = stringResource(R.string.connect_relay_url_error),
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
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
private fun DeviceChoiceCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    isEnabled: Boolean,
    isOnline: Boolean = true,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        enabled = isEnabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .heightIn(min = 48.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        !isEnabled || !isOnline -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        !isEnabled || !isOnline -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun friendlyFeedback(message: String): String = when {
    message.contains("Timed out") -> stringResource(R.string.connect_feedback_timeout)
    message == "Device is offline" || message == "Source is unavailable" -> stringResource(R.string.connect_feedback_offline)
    message == "Automatic transfer cancelled" -> stringResource(R.string.connect_feedback_auto_cancelled)
    message == "Local files are unavailable on another device" -> stringResource(R.string.connect_feedback_local_file)
    message == "Track changed; choose the device again" -> stringResource(R.string.connect_feedback_track_changed)
    message == "Unable to prepare this track" -> stringResource(R.string.connect_feedback_prepare_failed)
    else -> message
}
