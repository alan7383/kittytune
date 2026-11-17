package com.alananasss.kittytune.ui.musicimport

import com.alananasss.kittytune.data.vk.VkImportProgress
import com.alananasss.kittytune.data.vk.VkImportResult
import com.alananasss.kittytune.data.vk.VkMusicClient
import com.alananasss.kittytune.data.vk.VkImportedPlaylist
import com.alananasss.kittytune.data.vk.VkPlaylistImporter
import com.alananasss.kittytune.data.vk.VkPlaylistLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Where a VK import is: what the card on the import screen draws. */
sealed interface VkImportState {
    data object Idle : VkImportState
    data object Loading : VkImportState
    data class Problem(val reason: Reason, val detail: String? = null) : VkImportState
    data class Ready(val playlist: VkImportedPlaylist) : VkImportState
    data class Importing(val playlist: VkImportedPlaylist, val progress: VkImportProgress) : VkImportState
    data class Done(val playlist: VkImportedPlaylist, val result: VkImportResult) : VkImportState

    enum class Reason { NOT_VK, NOT_A_PLAYLIST, NOT_AVAILABLE, EMPTY, FAILED }
}

/**
 * One VK import at a time, kept above the screen that started it.
 */
object VkImportSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    private val _state = MutableStateFlow<VkImportState>(VkImportState.Idle)
    val state: StateFlow<VkImportState> = _state

    /** Reads the playlist [link] points at, for the listener to confirm before anything is imported. */
    fun load(link: String) {
        if (_state.value is VkImportState.Importing) return
        job?.cancel()
        _state.value = VkImportState.Loading
        job = scope.launch {
            var target = link.trim()
            if (!VkPlaylistLink.isVkLink(target)) {
                _state.value = VkImportState.Problem(VkImportState.Reason.NOT_VK)
                return@launch
            }
            if (VkPlaylistLink.isShortLink(target)) target = VkMusicClient.expandShortLink(target)
            val ref = VkPlaylistLink.parse(target)
            if (ref == null) {
                _state.value = VkImportState.Problem(VkImportState.Reason.NOT_A_PLAYLIST)
                return@launch
            }
            _state.value = when (val result = VkMusicClient.fetchPlaylist(ref)) {
                is VkMusicClient.Result.Found ->
                    if (result.playlist.tracks.isEmpty()) VkImportState.Problem(VkImportState.Reason.EMPTY)
                    else VkImportState.Ready(result.playlist)
                VkMusicClient.Result.NotAvailable -> VkImportState.Problem(VkImportState.Reason.NOT_AVAILABLE)
                is VkMusicClient.Result.Failed -> VkImportState.Problem(VkImportState.Reason.FAILED, result.message)
            }
        }
    }

    /** Looks every track of the loaded playlist up and saves what was found as a new playlist. */
    fun start() {
        val playlist = (_state.value as? VkImportState.Ready)?.playlist ?: return
        job = scope.launch {
            _state.value = VkImportState.Importing(playlist, VkImportProgress(0, playlist.tracks.size, 0))
            val result = runCatching {
                VkPlaylistImporter.import(playlist) { progress ->
                    _state.value = VkImportState.Importing(playlist, progress)
                }
            }
            _state.value = result.fold(
                onSuccess = { VkImportState.Done(playlist, it) },
                onFailure = { VkImportState.Problem(VkImportState.Reason.FAILED, it.message) },
            )
        }
    }

    fun reset() {
        job?.cancel()
        _state.value = VkImportState.Idle
    }
}
