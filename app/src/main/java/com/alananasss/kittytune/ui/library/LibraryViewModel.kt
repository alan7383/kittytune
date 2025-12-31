package com.alananasss.kittytune.ui.library

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalArtist
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.utils.NetworkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

sealed class LibraryItem(open val timestamp: Long) {
    data class PlaylistItem(val playlist: Playlist, override val timestamp: Long) : LibraryItem(timestamp)
    data class ArtistItem(val artist: LocalArtist, override val timestamp: Long) : LibraryItem(timestamp)
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val prefs = application.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
    private val tokenManager = TokenManager(application)

    var userProfile by mutableStateOf<User?>(null)
    val likedTracks = mutableStateListOf<Track>()

    private val _allItems = mutableStateListOf<LibraryItem>()
    val savedArtists = mutableStateListOf<LocalArtist>()

    var isLoading by mutableStateOf(false)
    var isOfflineMode by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    var isGuestUser by mutableStateOf(false)

    var showLocalMedia by mutableStateOf(false)

    val isSyncing = LikeRepository.isSyncing

    var selectedFilter by mutableStateOf<String?>(null)
    var isGridLayout by mutableStateOf(prefs.getBoolean("is_grid_layout", true))
    var isSortDescending by mutableStateOf(true)

    private var isHydratingLikes = false
    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    val displayedItems: List<LibraryItem>
        get() {
            val playlistsLabel = app.getString(R.string.lib_playlists)
            val artistsLabel = app.getString(R.string.lib_artists)

            val items = _allItems.filter { item ->
                when (item) {
                    is LibraryItem.PlaylistItem -> {
                        val matchesSearch = if (searchQuery.isBlank()) true else {
                            item.playlist.title?.contains(searchQuery, ignoreCase = true) == true ||
                                    item.playlist.user?.username?.contains(searchQuery, ignoreCase = true) == true
                        }
                        val matchesType = selectedFilter == null || selectedFilter == playlistsLabel
                        matchesSearch && matchesType
                    }
                    is LibraryItem.ArtistItem -> {
                        val matchesSearch = if (searchQuery.isBlank()) true else {
                            item.artist.username.contains(searchQuery, ignoreCase = true)
                        }
                        val matchesType = selectedFilter == null || selectedFilter == artistsLabel
                        matchesSearch && matchesType
                    }
                }
            }
            return if (isSortDescending) items.sortedByDescending { it.timestamp } else items.sortedBy { it.timestamp }
        }

    private val api = RetrofitClient.create(application)
    private val db = AppDatabase.getDatabase(application).downloadDao()

    init {
        viewModelScope.launch {
            snapshotFlow { isGridLayout }.collect { isGrid ->
                prefs.edit().putBoolean("is_grid_layout", isGrid).apply()
            }
        }

        viewModelScope.launch {
            LikeRepository.likedTracks.collect { tracksFromRepo ->
                likedTracks.clear()
                likedTracks.addAll(tracksFromRepo)
            }
        }

        viewModelScope.launch {
            db.getAllPlaylists().collect { localPlaylists ->
                val localItems = localPlaylists.map { local ->
                    val p = Playlist(local.id, local.title, local.artworkUrl, null, local.trackCount, User(0, local.artist, null), null)
                    LibraryItem.PlaylistItem(p, local.addedAt)
                }
                val localIds = localPlaylists.map { it.id }.toSet()

                val currentOnlineItems = _allItems.filter { item ->
                    if (item is LibraryItem.PlaylistItem) !localIds.contains(item.playlist.id) else false
                }
                val currentArtists = _allItems.filterIsInstance<LibraryItem.ArtistItem>()

                _allItems.clear()
                _allItems.addAll(currentOnlineItems)
                _allItems.addAll(localItems)
                _allItems.addAll(currentArtists)
            }
        }

        viewModelScope.launch {
            DownloadManager.getSavedArtists().collect { artists ->
                val artistItems = artists.map { LibraryItem.ArtistItem(it, it.savedAt) }
                _allItems.removeAll { it is LibraryItem.ArtistItem }
                _allItems.addAll(artistItems)
            }
        }
    }

    fun loadData(forceRefresh: Boolean = false) {
        if (isLoading && !forceRefresh) return

        val playerPrefs = PlayerPreferences(getApplication<Application>())
        showLocalMedia = playerPrefs.getLocalMediaEnabled()

        isGuestUser = tokenManager.isGuestMode()

        if (isGuestUser) {
            userProfile = User(0, app.getString(R.string.guest_user), null)
            isOfflineMode = false
            isLoading = false
            return
        }

        viewModelScope.launch {
            isLoading = true
            if (NetworkUtils.isInternetAvailable(getApplication())) {
                try {
                    isOfflineMode = false
                    val user = api.getMe()
                    userProfile = user
                    loadOnlineData(user)
                } catch (e: Exception) {
                    Log.e("LibraryVM", "Erreur en ligne ou Non Connecté", e)
                    isLoading = false
                }
            } else {
                isOfflineMode = true
                isLoading = false
            }
        }
    }

    private fun loadOnlineData(user: User) {
        viewModelScope.launch {
            try {
                coroutineScope {
                    val likedPlaylistsDeferred = async { api.getUserPlaylistLikes(user.id) }
                    val createdPlaylistsDeferred = async { api.getUserCreatedPlaylists(user.id) }

                    val likedResponse = likedPlaylistsDeferred.await()
                    val createdResponse = createdPlaylistsDeferred.await()

                    val newOnlineItems = mutableListOf<LibraryItem>()

                    likedResponse.collection.forEach { item ->
                        val date = try { item.likedAt?.let { isoParser.parse(it)?.time } ?: 0L } catch (e: Exception) { 0L }
                        newOnlineItems.add(LibraryItem.PlaylistItem(item.playlist, date))
                    }

                    createdResponse.collection.forEach { playlist ->
                        val date = try {
                            val dateStr = playlist.lastModified ?: playlist.createdAt
                            dateStr?.let { isoParser.parse(it)?.time } ?: 0L
                        } catch (e: Exception) { 0L }
                        newOnlineItems.add(LibraryItem.PlaylistItem(playlist, date))
                    }

                    val dbPlaylists = db.getAllPlaylists().first()
                    val dbIds = dbPlaylists.map { it.id }.toSet()

                    val uniqueOnlineItems = newOnlineItems.filter { item ->
                        if (item is LibraryItem.PlaylistItem) !dbIds.contains(item.playlist.id) else true
                    }

                    _allItems.removeAll { item ->
                        if (item is LibraryItem.PlaylistItem) !dbIds.contains(item.playlist.id) else false
                    }
                    _allItems.addAll(uniqueOnlineItems)

                    if (!isHydratingLikes) {
                        isHydratingLikes = true
                        LikeRepository.setSyncing(true)

                        launch(Dispatchers.IO) {
                            try {
                                val allCollectedLikes = mutableListOf<Track>()
                                var nextUrl: String? = null

                                val firstPage = api.getUserTrackLikes(user.id, limit = 200)
                                allCollectedLikes.addAll(firstPage.collection.map { it.track })
                                nextUrl = firstPage.next_href

                                while (nextUrl != null) {
                                    val page = api.getTrackLikesNextPage(nextUrl!!)
                                    allCollectedLikes.addAll(page.collection.map { it.track })
                                    nextUrl = page.next_href
                                }

                                LikeRepository.replaceAllLikes(allCollectedLikes)

                            } catch (e: Exception) {
                                e.printStackTrace()
                            } finally {
                                isHydratingLikes = false
                                isLoading = false
                                LikeRepository.setSyncing(false)
                            }
                        }
                    } else {
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                isLoading = false
            }
        }
    }
}