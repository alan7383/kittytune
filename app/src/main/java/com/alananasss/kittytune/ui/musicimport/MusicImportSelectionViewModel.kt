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
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.ExternalPlaylist
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.ExternalTrack
import com.alananasss.kittytune.data.musicimport.MusicImportResult
import com.alananasss.kittytune.data.musicimport.MusicImportStorage
import com.alananasss.kittytune.data.musicimport.SoundCloudMusicImportRepository
import com.alananasss.kittytune.data.musicimport.MusicTransferRequest
import com.alananasss.kittytune.data.network.RetrofitClient
import kotlinx.coroutines.launch

class MusicImportSelectionViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)
    private val repository = SoundCloudMusicImportRepository(api)
    private val storage = MusicImportStorage(application)

    var platform by mutableStateOf<MusicApi?>(null)
        private set
    var auth by mutableStateOf<MusicApiAuth?>(null)
        private set

    var playlists by mutableStateOf<List<ExternalPlaylist>>(emptyList())
        private set
    var likedTracksCount by mutableStateOf(0)
        private set
    var selectedPlaylistIds by mutableStateOf(setOf<String>())
        private set
    var includeLikes by mutableStateOf(false)
        private set

    var isLoading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var retryAfter by mutableStateOf<Long?>(null)
        private set
    var isImporting by mutableStateOf(false)
        private set

    fun init(platformProviderName: String) {
        if (platform != null) return
        val resolvedPlatform = MusicApi.fromProviderName(platformProviderName) ?: return
        platform = resolvedPlatform
        auth = storage.getAuth(resolvedPlatform.providerName)
        loadExternalContent()
    }

    fun loadExternalContent() {
        val userPlatformUuid = auth?.integrationUserUUID ?: return
        viewModelScope.launch {
            isLoading = true
            error = null
            retryAfter = null
            var collected = mutableListOf<ExternalPlaylist>()
            var next: String? = null
            var fetchFailed = false
            do {
                when (val result = repository.externalPlaylists(userPlatformUuid, next = next)) {
                    is MusicImportResult.Success -> {
                        result.data.playlists?.let(collected::addAll)
                        val pageInfo = result.data.pageInfo
                        next = if (pageInfo?.hasNextPage == true) pageInfo.endCursor else null
                        if (collected.size >= pageInfo?.totalItems ?: Int.MAX_VALUE) next = null
                    }
                    is MusicImportResult.Error -> {
                        error = result.message ?: "Unknown error"
                        retryAfter = result.retryAfterSeconds
                        fetchFailed = true
                    }
                    MusicImportResult.AuthenticationRequired -> {
                        error = "authentication_required"
                        fetchFailed = true
                    }
                }
                if (fetchFailed) break
            } while (next != null)
            playlists = collected
            isLoading = false
            loadLikedTracksCount()
        }
    }

    private fun loadLikedTracksCount() {
        val userPlatformUuid = auth?.integrationUserUUID ?: return
        viewModelScope.launch {
            when (val result = repository.externalLikedTracks(userPlatformUuid, limit = 50)) {
                is MusicImportResult.Success -> {
                    likedTracksCount = result.data.pageInfo?.totalItems
                        ?: result.data.tracks?.size ?: 0
                }
                is MusicImportResult.Error -> {
                    error = result.message ?: "Unknown error"
                    retryAfter = result.retryAfterSeconds
                    likedTracksCount = 0
                }
                else -> likedTracksCount = 0
            }
        }
    }

    fun togglePlaylist(id: String) {
        selectedPlaylistIds = if (id in selectedPlaylistIds) {
            selectedPlaylistIds - id
        } else {
            selectedPlaylistIds + id
        }
    }

    fun toggleLikes() {
        includeLikes = !includeLikes
    }

    fun importSelected(onStarted: () -> Unit) {
        val userPlatformUuid = auth?.integrationUserUUID ?: return
        val selectedPlatform = platform ?: return
        val selected = selectedPlaylistIds.toList()
        if (selected.isEmpty() && !includeLikes) return
        if (isImporting) return

        isImporting = true
        viewModelScope.launch {
            MusicImportCoordinator.startTransfer(
                MusicTransferRequest(
                    platform = selectedPlatform,
                    userPlatformUuid = userPlatformUuid,
                    playlistIds = selected,
                    importLikes = includeLikes,
                    likedTrackCount = if (includeLikes) likedTracksCount else 0
                )
            )
            isImporting = false
            onStarted()
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        val selectedPlatform = platform ?: return
        storage.clearAuth(selectedPlatform.providerName)
        onLoggedOut()
    }
}
