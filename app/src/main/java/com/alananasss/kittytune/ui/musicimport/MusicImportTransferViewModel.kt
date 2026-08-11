package com.alananasss.kittytune.ui.musicimport

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.musicimport.MusicApi
import com.alananasss.kittytune.data.musicimport.MusicImportCoordinator
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.PlaylistSyncStatus
import com.alananasss.kittytune.data.musicimport.MusicImportResult
import com.alananasss.kittytune.data.musicimport.MusicImportStorage
import com.alananasss.kittytune.data.musicimport.MusicTransferRequest
import com.alananasss.kittytune.data.musicimport.SoundCloudMusicImportRepository
import com.alananasss.kittytune.data.network.RetrofitClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class TransferPhase { IDLE, STARTING, SYNCING, DONE, ERROR }

class MusicImportTransferViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)
    private val repository = SoundCloudMusicImportRepository(api)
    private val storage = MusicImportStorage(application)

    var phase by mutableStateOf(TransferPhase.IDLE)
        private set
    var request by mutableStateOf<MusicTransferRequest?>(null)
        private set
    var platform by mutableStateOf<MusicApi?>(null)
        private set
    var playlistStatus by mutableStateOf<List<PlaylistSyncStatus>>(emptyList())
        private set
    var likesProgress by mutableStateOf<Int?>(null)
        private set
    var likesSyncing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var pollJob: Job? = null

    val isComplete: Boolean
        get() {
            val hasPlaylists = request?.playlistIds?.isNotEmpty() == true
            val playlistsDone = !hasPlaylists || (
                playlistStatus.isNotEmpty() &&
                playlistStatus.all { it.isSyncing != true && (it.progressPercent ?: 100) >= 100 }
            )
            val likesDone = request?.importLikes != true || (!likesSyncing && (likesProgress ?: 100) >= 100)
            return playlistsDone && likesDone
        }

    val overallProgress: Int
        get() {
            val parts = mutableListOf<Int>()
            playlistStatus.filter { it.isSyncing == true || it.progressPercent != null }
                .forEach { parts.add(it.progressPercent ?: 0) }
            likesProgress?.let { if (likesSyncing || it > 0) parts.add(it) }
            return if (parts.isEmpty()) 0 else parts.average().toInt()
        }

    fun start() {
        val transfer = MusicImportCoordinator.consumeTransfer()
        if (transfer == null) {
            if (phase == TransferPhase.IDLE) {
                // Keep current state if already set via previewMock
            }
            return
        }
        request = transfer
        platform = transfer.platform
        phase = TransferPhase.STARTING
        viewModelScope.launch {
            launchSync(transfer)
        }
    }

    fun previewMock(mockPlatform: MusicApi) {
        pollJob?.cancel()
        platform = mockPlatform
        phase = TransferPhase.SYNCING
        playlistStatus = listOf(
            PlaylistSyncStatus(playlistId = "mock_playlist_1", isSyncing = true, progressPercent = 70)
        )
        likesProgress = 70
        likesSyncing = true
    }

    private suspend fun launchSync(transfer: MusicTransferRequest) {
        var playlistSuccess = false
        var likesSuccess = false
        var hasError = false
        
        if (transfer.playlistIds.isNotEmpty()) {
            when (val result = repository.startPlaylistSync(transfer.userPlatformUuid, transfer.playlistIds)) {
                is MusicImportResult.Success -> {
                    playlistStatus = result.data
                    playlistSuccess = true
                }
                is MusicImportResult.Error -> {
                    phase = TransferPhase.ERROR
                    error = result.message
                    hasError = true
                }
                MusicImportResult.AuthenticationRequired -> {
                    phase = TransferPhase.ERROR
                    error = "authentication_required"
                    hasError = true
                }
            }
        }
        if (transfer.importLikes && !hasError) {
            when (val result = repository.startLikesSync(transfer.userPlatformUuid, importAsPlaylists = false)) {
                is MusicImportResult.Success -> {
                    result.data.firstOrNull()?.let {
                        likesProgress = it.progressPercent
                        likesSyncing = it.isSyncing == true
                    }
                    likesSuccess = true
                }
                is MusicImportResult.Error -> {
                    phase = TransferPhase.ERROR
                    error = result.message
                    hasError = true
                }
                MusicImportResult.AuthenticationRequired -> {
                    phase = TransferPhase.ERROR
                    error = "authentication_required"
                    hasError = true
                }
            }
        }
        
        if (hasError) return
        
        if (playlistSuccess || likesSuccess) {
            startPolling(transfer)
        } else {
            phase = TransferPhase.ERROR
            error = "empty_selection"
        }
    }

    private fun startPolling(transfer: MusicTransferRequest) {
        phase = TransferPhase.SYNCING
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                refreshStatus(transfer)
                if (isComplete && phase == TransferPhase.SYNCING) {
                    phase = TransferPhase.DONE
                    pollJob?.cancel()
                }
            }
        }
    }

    private suspend fun refreshStatus(transfer: MusicTransferRequest) {
        if (transfer.playlistIds.isNotEmpty()) {
            when (val result = repository.playlistSyncStatus(transfer.userPlatformUuid, transfer.playlistIds)) {
                is MusicImportResult.Success -> {
                    if (result.data.isNotEmpty()) playlistStatus = result.data
                }
                is MusicImportResult.Error -> { /* transient; keep last known progress */ }
                MusicImportResult.AuthenticationRequired -> {
                    phase = TransferPhase.ERROR
                    error = "authentication_required"
                    pollJob?.cancel()
                }
            }
        }
        if (transfer.importLikes) {
            when (val result = repository.likesSyncStatus(transfer.userPlatformUuid)) {
                is MusicImportResult.Success -> {
                    result.data.firstOrNull()?.let {
                        likesProgress = it.progressPercent
                        likesSyncing = it.isSyncing == true
                    }
                }
                else -> { /* transient */ }
            }
        }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
