package com.alananasss.kittytune.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.sync.SyncPeers
import java.util.Locale

private fun label(ru: String, en: String) = if (Locale.getDefault().language == "ru") ru else en

@Composable fun ConnectButton(tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    var opened by remember { mutableStateOf(false) }
    IconButton(onClick = { opened = true }) {
        Icon(Icons.Rounded.Devices, label("Доступные устройства", "Available devices"), tint = tint)
    }
    if (opened) Dialog(onDismissRequest = { opened = false }) {
        Surface(shape = MaterialTheme.shapes.large) {
            Column(Modifier.widthIn(max = 520.dp).heightIn(max = 650.dp)
                .verticalScroll(rememberScrollState()).padding(16.dp)) {
                ConnectPanel()
                TextButton(onClick = { opened = false }) { Text(label("Закрыть", "Close")) }
            }
        }
    }
}

@Composable fun ConnectPanel() {
    val peers by ConnectManager.peers.collectAsState()
    val feedback by ConnectManager.feedback.collectAsState()
    val selected by ConnectManager.selectedDevice.collectAsState()
    val busy by ConnectManager.transferring.collectAsState()
    val independent by ConnectManager.independent.collectAsState()
    val autoHeadphones by ConnectManager.autoHeadphones.collectAsState()
    var url by remember { mutableStateOf(ConnectManager.relayUrl) }
    var error by remember { mutableStateOf("") }
    var settings by remember { mutableStateOf(false) }
    var showRelay by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { ConnectManager.refresh() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(label("Доступные устройства", "Available devices"), Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { settings = !settings }) {
                Icon(Icons.Rounded.Settings, label("Настройки переключения", "Switching settings"))
            }
        }
        Text(label("Нажмите на устройство, чтобы перенести музыку и очередь.",
            "Tap a device to move your music and queue."), style = MaterialTheme.typography.bodySmall)
        DeviceChoice(label("Это устройство", "This device"),
            if (independent) label("Играет отдельно", "Independent playback") else label("Локальное воспроизведение", "Local playback"),
            Icons.Rounded.Speaker, selected == null, !busy) {
            if (!independent && selected != null) ConnectManager.switchOutput(null)
        }
        val known = SyncPeers.all()
        if (known.isEmpty()) Text(label("Свяжите телефон и компьютер в синхронизации устройств.", "Pair your phone and computer in device sync."),
            style = MaterialTheme.typography.bodySmall)
        known.sortedByDescending { peers[it.deviceId]?.connected == true }.forEach { peer ->
            val live = peers[peer.deviceId]
            val status = when {
                independent -> label("Выключите «Играть отдельно» для переключения", "Disable independent playback to switch")
                live?.connected != true -> label("Не в сети", "Offline")
                live.snapshot?.isPlaying == true -> label("Играет", "Playing") + " · " + (live.snapshot.queue.getOrNull(live.snapshot.currentIndex)?.title ?: "")
                live.transport == "LAN" -> label("Домашняя сеть", "Local network")
                else -> label("Интернет", "Internet")
            }
            DeviceChoice(peer.label, status,
                if (peer.platform == "android") Icons.Rounded.PhoneAndroid else Icons.Rounded.Computer,
                selected == peer.deviceId, !busy && !independent && live?.connected == true) {
                ConnectManager.switchOutput(peer.deviceId)
            }
        }
        val connectionError = peers.values.firstOrNull { !it.connected && it.error.isNotBlank() }?.error
        if (!independent && connectionError != null) Text(
            if (connectionError.startsWith("HTTP")) label("Сервер недоступен: ", "Server unavailable: ") + connectionError +
                label(". Проверьте адрес в настройках устройств.", ". Check the address in device settings.")
            else label("Ошибка соединения: ", "Connection error: ") + connectionError,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        if (busy) Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Text(label("Переносим воспроизведение…", "Moving playback…"), style = MaterialTheme.typography.bodySmall)
        }
        if (feedback.isNotBlank() && feedback != "✓") Text(friendlyFeedback(feedback), color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(label("Играть отдельно", "Play independently"), style = MaterialTheme.typography.titleSmall)
                Text(label("Свои трек и очередь. Управление с других устройств выключено.",
                    "Your own track and queue. Remote controls are off."), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked = independent, enabled = !busy, onCheckedChange = ConnectManager::setIndependent)
        }
        if (settings) {
            if (com.alananasss.kittytune.data.sync.ConnectPlatform.mobile) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label("Подхватывать в наушниках", "Continue with headphones"), style = MaterialTheme.typography.titleSmall)
                        Text(label("Подключение — перенос с ПК. Отключение — пауза.",
                            "Connect to move playback from PC. Disconnect to pause."), style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = autoHeadphones, enabled = !busy && !independent, onCheckedChange = ConnectManager::setAutoHeadphones)
                }
                Text(label("При открытом приложении. Если оно выгружено — откройте его для подхвата. Bluetooth-колонки тоже могут определяться как наушники.",
                    "While the app is open. Reopen it after it is stopped to continue. Bluetooth speakers may also be reported as headphones."),
                    style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showRelay = !showRelay }) { Text(label("Подключение через интернет", "Internet connection")) }
            if (showRelay) {
                OutlinedTextField(value = url, onValueChange = { url = it; error = "" }, singleLine = true,
                    label = { Text(label("Адрес сервера", "Server address")) }, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    runCatching { ConnectManager.relayUrl = url }.onFailure {
                        error = label("Нужен адрес https:// или wss://", "Use an https:// or wss:// address")
                    }
                }) { Text(label("Сохранить", "Save")) }
                if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable private fun DeviceChoice(name: String, status: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(12.dp).heightIn(min = 44.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, contentDescription = null, tint = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            if (selected) Icon(Icons.Rounded.CheckCircle, label("Выбрано", "Selected"), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun friendlyFeedback(message: String): String = when {
    message.contains("Timed out") -> label("Устройство не ответило вовремя. Попробуйте ещё раз.", "Device did not respond in time. Try again.")
    message == "Device is offline" || message == "Source is unavailable" -> label("Устройство не в сети.", "Device is offline.")
    message == "Automatic transfer cancelled" -> label("Автоматический перенос отменён.", "Automatic transfer cancelled.")
    message == "Local files are unavailable on another device" -> label("Этот локальный файл есть только на исходном устройстве.", "This local file is only available on the source device.")
    message == "Track changed; choose the device again" -> label("Трек сменился во время переноса. Выберите устройство ещё раз.", "Track changed during transfer. Choose the device again.")
    message == "Unable to prepare this track" -> label("Не удалось открыть трек на выбранном устройстве.", "Could not open the track on this device.")
    else -> message
}
