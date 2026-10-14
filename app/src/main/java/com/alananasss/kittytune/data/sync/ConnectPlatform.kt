package com.alananasss.kittytune.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.alananasss.kittytune.KittyTuneApp

internal object ConnectPlatform {
    fun trace(event: String) { if (com.alananasss.kittytune.BuildConfig.DEBUG) android.util.Log.d("KittyConnect", event) }
    const val mobile = true
    private val prefs by lazy { KittyTuneApp.instance.getSharedPreferences("sync_state", Context.MODE_PRIVATE) }
    var relayUrl: String
        get() = prefs.getString("connect_relay_url", "").orEmpty()
        set(value) { prefs.edit().putString("connect_relay_url", value).apply() }
    var autoHeadphones: Boolean
        get() = prefs.getBoolean("connect_auto_headphones", true)
        set(value) { prefs.edit().putBoolean("connect_auto_headphones", value).apply() }
    private val audio by lazy { KittyTuneApp.instance.getSystemService(android.media.AudioManager::class.java) }
    fun canAutoTransfer() = audio?.mode == android.media.AudioManager.MODE_NORMAL
    private var audioCallback: android.media.AudioDeviceCallback? = null
    @Synchronized fun observeHeadphones(active: Boolean) {
        if (active && autoHeadphones && audioCallback == null) {
            val callback = object : android.media.AudioDeviceCallback() {
                private fun changed() {
                    val connected = audio?.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)?.any { device ->
                        device.type in setOf(android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES, android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
                            android.media.AudioDeviceInfo.TYPE_USB_DEVICE, android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                            android.media.AudioDeviceInfo.TYPE_BLE_HEADSET)
                    } == true
                    ConnectManager.headphonesChanged(connected)
                }
                override fun onAudioDevicesAdded(devices: Array<out android.media.AudioDeviceInfo>) = changed()
                override fun onAudioDevicesRemoved(devices: Array<out android.media.AudioDeviceInfo>) = changed()
            }
            runCatching { audio?.registerAudioDeviceCallback(callback, android.os.Handler(android.os.Looper.getMainLooper()))
                audioCallback = callback }
        } else if (!active || !autoHeadphones) {
            audioCallback?.let { audio?.unregisterAudioDeviceCallback(it) }; audioCallback = null
        }
    }
    fun relayEndpoint() = relayUrl
    fun startListener() {} // Android uses one outbound duplex connection, including for receiving commands.
    private val connectivity by lazy { KittyTuneApp.instance.getSystemService(ConnectivityManager::class.java) }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    fun canUseLan(): Boolean = connectivity?.activeNetwork?.let { connectivity?.getNetworkCapabilities(it) }?.let {
        it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    } == true
    @Synchronized
    fun setActive(value: Boolean) {
        if (value) {
            SyncScheduler.start(); SyncService.startIfWanted()
            if (networkCallback == null) {
                val callback = object : ConnectivityManager.NetworkCallback() {
                    private var current = connectivity?.activeNetwork
                    override fun onAvailable(network: Network) {
                        if (network != current) { current = network; ConnectManager.networkChanged() }
                    }
                    override fun onLost(network: Network) {
                        if (network == current) { current = null; ConnectManager.networkChanged() }
                    }
                }
                runCatching { connectivity?.registerDefaultNetworkCallback(callback); networkCallback = callback }
            }
        } else {
            networkCallback?.let { runCatching { connectivity?.unregisterNetworkCallback(it) } }
            networkCallback = null
            SyncScheduler.stop(); SyncService.stop()
        }
    }
}
