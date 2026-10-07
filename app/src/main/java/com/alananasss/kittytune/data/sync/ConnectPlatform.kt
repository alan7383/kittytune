package com.alananasss.kittytune.data.sync

import android.content.Context
import com.alananasss.kittytune.KittyTuneApp

internal object ConnectPlatform {
    const val mobile = true
    private val prefs by lazy { KittyTuneApp.instance.getSharedPreferences("sync_state", Context.MODE_PRIVATE) }
    var relayUrl: String
        get() = prefs.getString("connect_relay_url", "").orEmpty()
        set(value) { prefs.edit().putString("connect_relay_url", value).apply() }
    fun startListener() {} // Android uses one outbound duplex connection, including for receiving commands.
    fun setActive(value: Boolean) {
        if (value) { SyncScheduler.start(); SyncService.startIfWanted() }
        else { SyncScheduler.stop(); SyncService.stop() }
    }
}
