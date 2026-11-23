package com.alananasss.kittytune.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.alananasss.kittytune.KittyTuneApp

internal object ConnectPlatform {
    fun trace(event: String) = NetLog.i("connect", event)
    const val mobile = true
    private val prefs by lazy { runCatching { KittyTuneApp.instance.getSharedPreferences("sync_state", Context.MODE_PRIVATE) }.getOrNull() }
    var relayUrl: String
        get() = prefs?.getString("connect_relay_url", "").orEmpty()
        set(value) { prefs?.edit()?.putString("connect_relay_url", value)?.apply() }
    /** Internet control ([ConnectCloud]): null until chosen here or by a paired device. Off by default. */
    var internetControl: Boolean?
        get() = prefs?.takeIf { it.contains("connect_internet_control") }?.getBoolean("connect_internet_control", false)
        set(value) { prefs?.edit()?.apply { if (value == null) remove("connect_internet_control") else putBoolean("connect_internet_control", value) }?.apply() }
    var autoHeadphones: Boolean
        get() = prefs?.getBoolean("connect_auto_headphones", true) ?: true
        set(value) { prefs?.edit()?.putBoolean("connect_auto_headphones", value)?.apply() }
    private val audio by lazy { runCatching { KittyTuneApp.instance.getSystemService(android.media.AudioManager::class.java) }.getOrNull() }
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
    /**
     * The phone also answers on the LAN, so a computer whose firewall drops incoming connections can still
     * reach it: whichever direction works carries the link.
     */
    fun startListener() { if (SyncService.isListenerEnabled && !SyncPeers.isEmpty()) ConnectLanServer.start() }
    private val connectivity by lazy { runCatching { KittyTuneApp.instance.getSystemService(ConnectivityManager::class.java) }.getOrNull() }
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    /**
     * The Wi-Fi or Ethernet network, even when a VPN is the default route. Only the default network used to
     * be checked, so with a VPN app on (ad blockers and DNS filters run as VPNs) the phone never even tried
     * the LAN, or sent LAN traffic into the tunnel where it timed out.
     */
    @Suppress("DEPRECATION")
    fun lanNetwork(): Network? {
        val cm = connectivity ?: return null
        fun isLan(network: Network) = cm.getNetworkCapabilities(network)?.let {
            !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                (it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || it.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET))
        } == true
        cm.activeNetwork?.takeIf(::isLan)?.let { return it }
        return runCatching { cm.allNetworks.firstOrNull(::isLan) }.getOrNull()
    }

    fun canUseLan(): Boolean = lanNetwork() != null

    fun vpnActive(): Boolean = connectivity?.activeNetwork?.let { connectivity?.getNetworkCapabilities(it) }
        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true

    /** Sockets bound to the Wi-Fi network, so LAN traffic goes straight to the other device, not into a VPN. */
    fun lanSocketFactory(): javax.net.SocketFactory? = if (vpnActive()) lanNetwork()?.socketFactory else null
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
