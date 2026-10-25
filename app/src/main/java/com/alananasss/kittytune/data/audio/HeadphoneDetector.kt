package com.alananasss.kittytune.data.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The headphones currently plugged in or connected over Bluetooth.
 *
 * [key] is stable for a given product + connection type, which is what lets a saved profile be
 * restored next time the same pair is connected. The name comes from the audio stack itself, so no
 * Bluetooth permission is required and nothing is guessed from a broadcast.
 */
data class ConnectedHeadphone(
    val key: String,
    val name: String,
    val connectionLabel: String,
    val type: Int,
) {
    val isBluetooth: Boolean
        get() = type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            type == AudioDeviceInfo.TYPE_BLE_HEADSET
}

object HeadphoneDetector {
    private val _device = MutableStateFlow<ConnectedHeadphone?>(null)
    val device: StateFlow<ConnectedHeadphone?> = _device.asStateFlow()

    private var audioManager: AudioManager? = null
    private var callback: AudioDeviceCallback? = null

    @Synchronized
    fun start(context: Context) {
        if (callback != null) return
        val manager = context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                refresh()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                refresh()
            }
        }
        manager.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
        audioManager = manager
        callback = cb
        refresh()
    }

    @Synchronized
    fun stop() {
        val manager = audioManager
        val cb = callback
        if (manager != null && cb != null) {
            runCatching { manager.unregisterAudioDeviceCallback(cb) }
        }
        audioManager = null
        callback = null
    }

    fun refresh(): ConnectedHeadphone? {
        val manager = audioManager ?: return _device.value
        val outputs = runCatching {
            manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }.getOrDefault(emptyList())

        val best = outputs
            .filter { it.type in HEADPHONE_TYPES }
            .sortedBy { priority(it.type) }
            .firstOrNull()

        val next = best?.let { info ->
            val productName = info.productName?.toString()?.trim().orEmpty()
                .removePrefix("LE_")
                .removePrefix("LE-")
                .trim()
            val fallback = when (info.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth headphones"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB headphones"
                AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
                else -> "Wired headphones"
            }
            val name = productName.ifBlank { fallback }
            val connection = when (info.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
                AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C"
                AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing aid"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset"
                else -> "3.5 mm jack"
            }
            ConnectedHeadphone(
                key = "${AutoEqRepository.normalize(name)}|${info.type}",
                name = name,
                connectionLabel = connection,
                type = info.type,
            )
        }
        _device.value = next
        return next
    }

    private fun priority(type: Int): Int = when (type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> 0
        AudioDeviceInfo.TYPE_BLE_HEADSET -> 1
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> 2
        AudioDeviceInfo.TYPE_USB_HEADSET -> 3
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> 4
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 5
        AudioDeviceInfo.TYPE_HEARING_AID -> 6
        else -> 9
    }

    private val HEADPHONE_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_HEARING_AID,
    )
}
