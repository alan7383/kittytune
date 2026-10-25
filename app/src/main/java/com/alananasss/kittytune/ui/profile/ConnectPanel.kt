package com.alananasss.kittytune.ui.profile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.view.HapticFeedbackConstants
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeMute
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.sync.ConnectPlatform
import com.alananasss.kittytune.data.sync.SyncPeers
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import kotlin.math.roundToInt

@Composable
fun ConnectDialog(onDismissRequest: () -> Unit) {
    PlaybackDevicesSheet(onDismissRequest = onDismissRequest)
}

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
        onClick = hapticAction(HapticKind.UI) {
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
    val context = LocalContext.current
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
    var showSyncScreen by remember { mutableStateOf(false) }

    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    var localVolume by remember {
        mutableFloatStateOf(
            audioManager?.let { am ->
                val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max > 0) cur.toFloat() / max.toFloat() else 0.5f
            } ?: 0.5f
        )
    }

    DisposableEffect(context) {
        val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                audioManager?.let { am ->
                    val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    if (max > 0) localVolume = cur.toFloat() / max.toFloat()
                }
            }
        }
        context.registerReceiver(receiver, filter)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    LaunchedEffect(Unit) { ConnectManager.refresh() }

    val activeVolume = if (selected != null) {
        peers[selected]?.snapshot?.volume ?: localVolume
    } else {
        localVolume
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // 1. Top Bar: Close '✕' on left, Title uppercase in center, Settings cog on right
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = hapticAction(HapticKind.UI) { onClose?.invoke() }) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Text(
                text = stringResource(R.string.slot_devices).uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            IconButton(onClick = hapticAction(HapticKind.UI) { showSettings = !showSettings }) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = stringResource(R.string.settings_title),
                    tint = if (showSettings) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 2. Scrollable Middle Body (Spotify-like Hero Graphic + Devices List)
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // Spotify Hero Graphic: Desktop, Laptop, Speaker, Tablet, Smartphone
            SpotifyDevicesGraphic(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(vertical = 4.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )

            // Hero Title: "Se connecter à un appareil"
            Text(
                text = stringResource(R.string.connect_devices_hero),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                modifier = Modifier.padding(vertical = 4.dp)
            )

            // Active & Available Devices
            val known = remember(peers, showSyncScreen) { SyncPeers.all() }
            val isLocalActive = selected == null

            // --- LOCAL DEVICE ("Ce téléphone") ---
            SpotifyDeviceRow(
                title = stringResource(R.string.connect_this_phone),
                subtitle = when {
                    independent -> stringResource(R.string.connect_independent_title)
                    isLocalActive -> stringResource(R.string.connect_playing_here)
                    else -> stringResource(R.string.connect_service_name)
                },
                icon = Icons.Rounded.Smartphone,
                isActive = isLocalActive,
                isOnline = true,
                isEnabled = !busy,
                isLoading = busy && isLocalActive,
                onClick = hapticAction(HapticKind.UI) {
                    if (!independent && selected != null) {
                        ConnectManager.switchOutput(null)
                    }
                }
            )

            // --- REMOTE PAIRED DEVICES ---
            known.sortedByDescending { peers[it.deviceId]?.connected == true }.forEach { peer ->
                val live = peers[peer.deviceId]
                val isCurrentPeerActive = selected == peer.deviceId
                val statusSubtitle = when {
                    independent -> stringResource(R.string.connect_independent_desc)
                    live?.connected != true -> stringResource(R.string.connect_status_offline)
                    isCurrentPeerActive -> stringResource(R.string.connect_service_name)
                    live.snapshot?.isPlaying == true -> {
                        val trackTitle = live.snapshot.queue.getOrNull(live.snapshot.currentIndex)?.title.orEmpty()
                        stringResource(R.string.connect_status_playing, trackTitle)
                    }
                    else -> stringResource(R.string.connect_service_name)
                }

                SpotifyDeviceRow(
                    title = peer.label,
                    subtitle = statusSubtitle,
                    icon = getDeviceIcon(peer.platform, peer.label),
                    isActive = isCurrentPeerActive,
                    isOnline = live?.connected == true,
                    isEnabled = !busy && !independent && live?.connected == true,
                    isLoading = busy && isCurrentPeerActive,
                    onClick = hapticAction(HapticKind.UI) {
                        ConnectManager.switchOutput(peer.deviceId)
                    }
                )
            }

            // --- IF NO PAIRED DEVICES FOUND ---
            if (known.isEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DevicesFold,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = stringResource(R.string.connect_cant_find),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = stringResource(R.string.connect_pair_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        FilledTonalButton(
                            onClick = hapticAction(HapticKind.UI) { showSyncScreen = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.QrCodeScanner,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.connect_pair_action))
                        }
                    }
                }
            } else {
                // Button to pair more devices
                TextButton(
                    onClick = hapticAction(HapticKind.UI) { showSyncScreen = true },
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QrCodeScanner,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.connect_pair_action),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            // Feedback / Error Messages
            val connectionError = peers.values.firstOrNull { !it.connected && it.error.isNotBlank() }?.error
            if (!independent && connectionError != null) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        text = stringResource(R.string.connect_error_connection, connectionError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
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
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
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
                                    onClick = hapticAction(HapticKind.UI) {
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

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Bottom Volume Slider (Exact Spotify Connect pattern)
        SpotifyVolumeBar(
            volume = activeVolume,
            onVolumeChange = { newVol ->
                if (selected != null) {
                    ConnectManager.command(selected!!, "volume", (newVol * 1000).toLong())
                } else {
                    localVolume = newVol
                    audioManager?.let { am ->
                        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                        am.setStreamVolume(AudioManager.STREAM_MUSIC, (newVol * max).roundToInt(), 0)
                    }
                }
            }
        )
    }

    // Full screen dialog for Pairing / QR scanning
    if (showSyncScreen) {
        Dialog(
            onDismissRequest = { showSyncScreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                SyncDevicesScreen(onBackClick = { showSyncScreen = false })
            }
        }
    }
}

/**
 * Spotify-style Device Item Row with Material 3 styling
 */
@Composable
private fun SpotifyDeviceRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isActive: Boolean,
    isOnline: Boolean = true,
    isEnabled: Boolean = true,
    isLoading: Boolean = false,
    onClick: () -> Unit
) {
    val haptic = LocalView.current
    val itemShape = RoundedCornerShape(16.dp)
    val activeColor = MaterialTheme.colorScheme.primary
    val contentColor = if (isActive) activeColor else MaterialTheme.colorScheme.onSurface
    val subtitleColor = if (isActive) activeColor.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
    val iconColor = if (isActive) activeColor else if (!isOnline) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface

    Surface(
        onClick = {
            if (isEnabled && !isLoading) {
                haptic.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        },
        enabled = isEnabled && !isLoading,
        shape = itemShape,
        color = if (isActive) activeColor.copy(alpha = 0.12f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )

            Column(modifier = Modifier.weight(1f)) {
                if (isActive) {
                    Text(
                        text = stringResource(R.string.connect_listening_on),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        color = activeColor
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (!isActive && isOnline) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = subtitleColor,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    strokeWidth = 2.dp,
                    color = activeColor
                )
            } else if (isActive) {
                EqualizerWaveform(color = activeColor)
            }
        }
    }
}

/**
 * Animated 3-bar equalizer waveform mimicking Spotify's playing device indicator
 */
@Composable
fun EqualizerWaveform(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "equalizer")
    val bar1 by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
        label = "b1"
    )
    val bar2 by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(560, easing = LinearEasing), RepeatMode.Reverse),
        label = "b2"
    )
    val bar3 by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse),
        label = "b3"
    )

    Row(
        modifier = modifier.height(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Box(
            Modifier
                .width(3.2.dp)
                .fillMaxHeight(bar1)
                .clip(RoundedCornerShape(1.6.dp))
                .background(color)
        )
        Box(
            Modifier
                .width(3.2.dp)
                .fillMaxHeight(bar2)
                .clip(RoundedCornerShape(1.6.dp))
                .background(color)
        )
        Box(
            Modifier
                .width(3.2.dp)
                .fillMaxHeight(bar3)
                .clip(RoundedCornerShape(1.6.dp))
                .background(color)
        )
    }
}

/**
 * Spotify-style bottom volume bar with dynamic speaker icon and M3 slider
 */
@Composable
fun SpotifyVolumeBar(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val volumeIcon = when {
        volume <= 0.01f -> Icons.AutoMirrored.Rounded.VolumeMute
        volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
        else -> Icons.AutoMirrored.Rounded.VolumeUp
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = volumeIcon,
            contentDescription = stringResource(R.string.pref_volume_slider_title),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = onVolumeChange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onSurface,
                activeTrackColor = MaterialTheme.colorScheme.onSurface,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        )
    }
}

/**
 * Clean vector illustration depicting a monitor, laptop, hi-fi speaker, tablet, and smartphone
 */
@Composable
fun SpotifyDevicesGraphic(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Icon(
        painter = painterResource(R.drawable.ic_connect_devices_hero),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

/**
 * Determine device icon based on device platform and name
 */
fun getDeviceIcon(platform: String?, label: String): ImageVector {
    val lower = (label + " " + (platform ?: "")).lowercase()
    return when {
        lower.contains("tv") || lower.contains("television") -> Icons.Rounded.Tv
        lower.contains("speaker") || lower.contains("enceinte") || lower.contains("salon") || lower.contains("sdb") -> Icons.Rounded.Speaker
        platform == "android" || lower.contains("phone") || lower.contains("téléphone") || lower.contains("pixel") || lower.contains("galaxy") -> Icons.Rounded.Smartphone
        platform == "desktop" || lower.contains("pc") || lower.contains("computer") || lower.contains("ordinateur") || lower.contains("mac") || lower.contains("laptop") -> Icons.Rounded.Computer
        else -> Icons.Rounded.Devices
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
