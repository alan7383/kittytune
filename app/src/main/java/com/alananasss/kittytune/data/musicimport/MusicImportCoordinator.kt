package com.alananasss.kittytune.data.musicimport

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MusicTransferRequest(
    val platform: MusicApi,
    val userPlatformUuid: String,
    val playlistIds: List<String>,
    val importLikes: Boolean,
    val likedTrackCount: Int = 0
)

/**
 * App-level bridge between the `sc://musicapi/auth` deep link handled in
 * MainActivity, the import screens and the transfer progress screen.
 * Mirrors AuthFlowManager.
 */
object MusicImportCoordinator {
    private val _pendingAuth = MutableStateFlow<MusicApiAuth?>(null)
    val pendingAuth: StateFlow<MusicApiAuth?> = _pendingAuth.asStateFlow()

    private val _pendingTransfer = MutableStateFlow<MusicTransferRequest?>(null)
    val pendingTransfer: StateFlow<MusicTransferRequest?> = _pendingTransfer.asStateFlow()

    fun deliverAuth(auth: MusicApiAuth) {
        _pendingAuth.value = auth
    }

    fun consumeAuth(): MusicApiAuth? {
        val auth = _pendingAuth.value
        _pendingAuth.value = null
        return auth
    }

    fun clearAuth() {
        _pendingAuth.value = null
    }

    fun startTransfer(request: MusicTransferRequest) {
        _pendingTransfer.value = request
    }

    fun consumeTransfer(): MusicTransferRequest? {
        val request = _pendingTransfer.value
        _pendingTransfer.value = null
        return request
    }

    fun clearTransfer() {
        _pendingTransfer.value = null
    }
}
