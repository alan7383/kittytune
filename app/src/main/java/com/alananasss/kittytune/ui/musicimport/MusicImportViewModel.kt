package com.alananasss.kittytune.ui.musicimport

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.musicimport.MusicApi
import com.alananasss.kittytune.data.musicimport.MusicApiAuth
import com.alananasss.kittytune.data.musicimport.MusicImportCoordinator
import com.alananasss.kittytune.data.musicimport.MusicImportStorage
import kotlinx.coroutines.launch

class MusicImportViewModel(application: Application) : AndroidViewModel(application) {
    private val storage = MusicImportStorage(application)

    var platforms by mutableStateOf(MusicApi.entries)
    var connectedPlatform by mutableStateOf<String?>(null)
        private set
    var connectedAuth by mutableStateOf<MusicApiAuth?>(null)
        private set
    var isConnecting by mutableStateOf(false)
        private set
    var authError by mutableStateOf<String?>(null)
        private set

    init {
        platforms = MusicApi.entries
    }

    fun refreshConnection() {
        connectedAuth = null
        platforms.firstOrNull { storage.getAuth(it.providerName) != null }?.let { p ->
            connectedPlatform = p.providerName
            connectedAuth = storage.getAuth(p.providerName)
        }
    }

    fun isConnected(platform: MusicApi): Boolean = storage.getAuth(platform.providerName) != null

    fun checkPendingAuth() {
        viewModelScope.launch {
            val auth = MusicImportCoordinator.consumeAuth()
            if (auth != null) {
                val platform = auth.integration?.type?.let { MusicApi.fromProviderName(it) } ?: return@launch
                storage.saveAuth(platform.providerName, auth)
                connectedPlatform = platform.providerName
                connectedAuth = auth
            }
        }
    }

    fun markConnecting(connecting: Boolean) {
        isConnecting = connecting
    }

    fun markAuthError(error: String?) {
        authError = error
    }
}
