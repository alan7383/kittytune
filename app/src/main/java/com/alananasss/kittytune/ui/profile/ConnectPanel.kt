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

@Composable fun ConnectButton() {
    var opened by remember { mutableStateOf(false) }
    IconButton(onClick = { opened = true }) {
        Icon(Icons.Rounded.Devices, label("Устройства воспроизведения", "Playback devices"))
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
    var url by remember { mutableStateOf(ConnectManager.relayUrl) }
    var error by remember { mutableStateOf("") }
    var showRelay by remember { mutableStateOf(false) }
    val selected by ConnectManager.selectedDevice.collectAsState()
    // This UI never starts a timer outside composition. Networking uses event-driven messages.
    LaunchedEffect(Unit) { ConnectManager.refresh() }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(label("Устройства воспроизведения", "Playback devices"), style = MaterialTheme.typography.titleMedium)
            Text(label("Выберите устройство. Управление и очередь находятся в основном плеере.",
                "Choose a device. Use the main player controls and queue."), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { ConnectManager.selectDevice(null) }) {
                Text((if (selected == null) "✓ " else "") + label("Это устройство", "This device"))
            }
            val known = SyncPeers.all()
            if (known.isEmpty()) Text(label("Сначала свяжите устройства", "Pair devices first"))
            known.forEach { peer ->
                val live = peers[peer.deviceId]
                TextButton(enabled = live?.connected == true || selected == peer.deviceId,
                    onClick = { ConnectManager.selectDevice(peer.deviceId) }) {
                    Text((if (selected == peer.deviceId) "✓ " else "") + peer.label + " · " + when {
                        live?.connected != true -> label("Не в сети", "Offline")
                        live.transport == "LAN" -> label("Домашняя сеть", "Local network")
                        else -> label("Интернет", "Internet")
                    })
                }
                if (selected == peer.deviceId && live?.connected == true) {
                    TextButton(onClick = { ConnectManager.transferHere(peer.deviceId) }) {
                        Text(label("Продолжить на этом устройстве", "Continue on this device"))
                    }
                    TextButton(onClick = { ConnectManager.transferThere(peer.deviceId) }) {
                        Text(label("Перенести местное воспроизведение на выбранное устройство", "Move local playback to the selected device"))
                    }
                }
            }
            if (feedback.isNotBlank()) Text(feedback, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { showRelay = !showRelay }) { Text(label("Подключение через интернет", "Internet connection")) }
            if (showRelay) {
                Text(label("Адрес вашего сервера. Укажите одинаковый адрес на обоих устройствах.",
                    "Your server address. Use the same address on both devices."), style = MaterialTheme.typography.bodySmall)
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

