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
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalArtist
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.GraphQlFollowsInput
import com.alananasss.kittytune.domain.GraphQlFollowsRequest
import com.alananasss.kittytune.domain.GraphQlFollowsVariables
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.PlaylistLikeItem
import com.alananasss.kittytune.domain.SystemPlaylist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.utils.NetworkUtils
import com.alananasss.kittytune.data.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

import com.alananasss.kittytune.data.local.LibraryFolder
import com.alananasss.kittytune.data.local.LibraryItemMeta

sealed class LibraryItem(open val timestamp: Long, open val key: String, open val isPinned: Boolean = false) {
    data class FolderItem(
        val folder: LibraryFolder,
        val playlistCount: Int,
        val folderCount: Int,
        override val timestamp: Long = folder.createdAt,
        override val key: String = "folder_${folder.id}",
        override val isPinned: Boolean = folder.isPinned
    ) : LibraryItem(timestamp, key, isPinned)

    data class PlaylistItem(
        val playlist: Playlist,
        override val timestamp: Long,
        override val key: String = getPlaylistCanonicalKey(playlist),
        override val isPinned: Boolean = false
    ) : LibraryItem(timestamp, key, isPinned)

    data class ArtistItem(
        val artist: LocalArtist,
        override val timestamp: Long,
        override val key: String = "artist_${artist.id}",
        override val isPinned: Boolean = false
    ) : LibraryItem(timestamp, key, isPinned)

    companion object {
        fun getPlaylistCanonicalKey(playlist: Playlist): String {
            val permalink = playlist.permalinkUrl
            return if (permalink != null && permalink.startsWith("yt_radio:")) {
                "yt_radio:$permalink"
            } else if (playlist.urn?.startsWith("soundcloud:system-playlists:") == true) {
                "system_playlist:${playlist.urn}"
            } else {
                if (playlist.id < 0) "local_playlist:${playlist.id}" else "playlist_${playlist.id}"
            }
        }
    }
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val prefs = application.getSharedPreferences("library_prefs", Context.MODE_PRIVATE)
    private val tokenManager = TokenManager(application)
    private val gson = com.alananasss.kittytune.utils.AppUtils.gson

    var userProfile by mutableStateOf<User?>(null)
    val likedTracks = mutableStateListOf<Track>()
    val uploadedTracks = mutableStateListOf<Track>()

    private var onlineItemsCache = listOf<LibraryItem>()
    private var localItemsCache = listOf<LibraryItem>()
    private var savedArtistsCache = listOf<LibraryItem>()
    private var allFoldersCache = listOf<LibraryFolder>()
    private val _allItemMetas = MutableStateFlow<Map<String, LibraryItemMeta>>(emptyMap())
    val allItemMetas = _allItemMetas.asStateFlow()
    private var allItemMetasCache: Map<String, LibraryItemMeta>
        get() = _allItemMetas.value
        set(value) {
            _allItemMetas.value = value
        }

    private val _allItems = mutableStateListOf<LibraryItem>()

    var currentFolderId by mutableStateOf<Long?>(null)

    val currentFolder: LibraryFolder?
        get() = currentFolderId?.let { id -> allFoldersCache.find { it.id == id } }

    private val _folderStack = mutableStateListOf<Long>()
    val folderStack: List<Long> get() = _folderStack

    fun navigateToFolder(folder: LibraryFolder) {
        _folderStack.add(folder.id)
        currentFolderId = folder.id
    }

    fun navigateUp(): Boolean {
        if (_folderStack.isNotEmpty()) {
            _folderStack.removeAt(_folderStack.size - 1)
            currentFolderId = _folderStack.lastOrNull()
            return true
        }
        if (currentFolderId != null) {
            currentFolderId = null
            return true
        }
        return false
    }

    fun navigateToRoot() {
        _folderStack.clear()
        currentFolderId = null
    }

    val displayedItems: List<LibraryItem>
        get() {
            val items = _allItems.filter { item ->
                when (item) {
                    is LibraryItem.FolderItem -> {
                        val matchesFolder =
                            if (searchQuery.isNotBlank()) true else item.folder.parentFolderId == currentFolderId
                        val matchesSearch = if (searchQuery.isBlank()) true else {
                            item.folder.name.contains(searchQuery, ignoreCase = true)
                        }
                        val matchesType = selectedFilter == null || selectedFilter == LibraryFilter.PLAYLISTS
                        matchesFolder && matchesSearch && matchesType
                    }

                    is LibraryItem.PlaylistItem -> {
                        val meta = allItemMetasCache[item.key]
                        val matchesFolder = if (searchQuery.isNotBlank()) true else meta?.folderId == currentFolderId
                        val matchesSearch = if (searchQuery.isBlank()) true else {
                            item.playlist.title?.contains(searchQuery, ignoreCase = true) == true ||
                                    item.playlist.user?.username?.contains(searchQuery, ignoreCase = true) == true
                        }
                        val isAlbum = item.playlist.isRealAlbum
                        val isStation = item.playlist.permalinkUrl?.let {
                            it.contains("artist-stations") || it.contains("track-stations")
                        } == true || item.playlist.urn?.let {
                            it.contains("artist-stations") || it.contains("track-stations")
                        } == true
                        val matchesType = when (selectedFilter) {
                            LibraryFilter.PLAYLISTS -> !isAlbum && !isStation
                            LibraryFilter.ALBUMS -> isAlbum && !isStation
                            LibraryFilter.STATIONS -> isStation
                            LibraryFilter.ARTISTS, LibraryFilter.UPLOADS -> false
                            null -> !isStation
                        }
                        val matchesOwnership = if (matchesType && !isStation) {
                            when (ownershipFilter) {
                                OwnershipFilter.ALL -> true
                                OwnershipFilter.CREATED -> item.playlist.user?.id == userProfile?.id || item.playlist.user?.id == 0L || item.playlist.id < 0L
                                OwnershipFilter.LIKED -> LikeRepository.isPlaylistLiked(item.playlist.id)
                            }
                        } else if (matchesType && isStation) {
                            val isArtistStation =
                                item.playlist.permalinkUrl?.contains("artist-stations") == true || item.playlist.urn?.contains(
                                    "artist-stations"
                                ) == true
                            val isTrackStation =
                                item.playlist.permalinkUrl?.contains("track-stations") == true || item.playlist.urn?.contains(
                                    "track-stations"
                                ) == true
                            when (stationFilter) {
                                StationFilter.ALL -> true
                                StationFilter.TRACKS -> isTrackStation || !isArtistStation
                                StationFilter.ARTISTS -> isArtistStation
                            }
                        } else true
                        matchesFolder && matchesSearch && matchesType && matchesOwnership
                    }

                    is LibraryItem.ArtistItem -> {
                        val matchesFolder = if (searchQuery.isNotBlank()) true else currentFolderId == null
                        val matchesSearch = if (searchQuery.isBlank()) true else {
                            item.artist.username.contains(searchQuery, ignoreCase = true)
                        }
                        val matchesType =
                            selectedFilter == LibraryFilter.ARTISTS || (selectedFilter == null && searchQuery.isNotBlank())
                        matchesFolder && matchesSearch && matchesType
                    }
                }
            }
            val sorted = when (sortOption) {
                LibrarySortOption.RECENTS -> {
                    if (isSortDescending) items.sortedByDescending { it.timestamp } else items.sortedBy { it.timestamp }
                }

                LibrarySortOption.DATE_ADDED -> {
                    if (isSortDescending) items.sortedByDescending { it.timestamp } else items.sortedBy { it.timestamp }
                }

                LibrarySortOption.ALPHABETICAL -> {
                    items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { item ->
                        when (item) {
                            is LibraryItem.FolderItem -> item.folder.name
                            is LibraryItem.PlaylistItem -> item.playlist.title.orEmpty()
                            is LibraryItem.ArtistItem -> item.artist.username
                        }
                    })
                }

                LibrarySortOption.CREATOR -> {
                    items.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { item ->
                        when (item) {
                            is LibraryItem.FolderItem -> userProfile?.username.orEmpty()
                            is LibraryItem.PlaylistItem -> item.playlist.user?.username.orEmpty()
                            is LibraryItem.ArtistItem -> item.artist.username
                        }
                    })
                }
            }

            return if (currentFolderId == null) {
                val pinned = sorted.filter { it.isPinned }
                val unpinned = sorted.filter { !it.isPinned }
                pinned + unpinned
            } else {
                sorted
            }
        }

    var isLoading by mutableStateOf(true)
    var isOfflineMode by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var isGuestUser by mutableStateOf(false)
    var showLocalMedia by mutableStateOf(false)
    val isSyncing = LikeRepository.isSyncing
    var selectedFilter by mutableStateOf<LibraryFilter?>(null)
    var ownershipFilter by mutableStateOf(OwnershipFilter.ALL)
    var stationFilter by mutableStateOf(StationFilter.ALL)
    var isGridLayout by mutableStateOf(prefs.getBoolean("is_grid_layout", true))
    var isSortDescending by mutableStateOf(true)
    var sortOption by mutableStateOf(
        try {
            LibrarySortOption.valueOf(
                prefs.getString("library_sort_option", LibrarySortOption.RECENTS.name) ?: LibrarySortOption.RECENTS.name
            )
        } catch (e: Exception) {
            LibrarySortOption.RECENTS
        }
    )
    var uploadsSortOption by mutableStateOf(
        try {
            UploadsSortOption.valueOf(
                prefs.getString("uploads_sort_option", UploadsSortOption.RECENTLY_ADDED.name) ?: UploadsSortOption.RECENTLY_ADDED.name
            )
        } catch (e: Exception) {
            UploadsSortOption.RECENTLY_ADDED
        }
    )
    var uploadsPrivacyFilter by mutableStateOf(
        try {
            UploadsPrivacyFilter.valueOf(
                prefs.getString("uploads_privacy_filter", UploadsPrivacyFilter.ALL.name) ?: UploadsPrivacyFilter.ALL.name
            )
        } catch (e: Exception) {
            UploadsPrivacyFilter.ALL
        }
    )

    private var isHydratingLikes = false
    private val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

    private val api = RetrofitClient.create(application)
    private val db = AppDatabase.getDatabase(application).downloadDao()
    private val folderDao = AppDatabase.getDatabase(application).folderDao()

    init {
        val cachedUploadsJson = prefs.getString("cached_user_uploads", null)
        if (!cachedUploadsJson.isNullOrEmpty()) {
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<Track>>() {}.type
                val cached: List<Track> = gson.fromJson(cachedUploadsJson, listType)
                if (!cached.isNullOrEmpty()) {
                    uploadedTracks.clear()
                    uploadedTracks.addAll(cached)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        viewModelScope.launch {
            snapshotFlow { isGridLayout }.collect { isGrid ->
                prefs.edit().putBoolean("is_grid_layout", isGrid).apply()
            }
        }

        viewModelScope.launch {
            snapshotFlow { sortOption }.collect { opt ->
                prefs.edit().putString("library_sort_option", opt.name).apply()
            }
        }

        viewModelScope.launch {
            snapshotFlow { uploadsSortOption }.collect { opt ->
                prefs.edit().putString("uploads_sort_option", opt.name).apply()
            }
        }

        viewModelScope.launch {
            snapshotFlow { uploadsPrivacyFilter }.collect { filter ->
                prefs.edit().putString("uploads_privacy_filter", filter.name).apply()
            }
        }

        viewModelScope.launch {
            LikeRepository.likedTracks.collect { tracksFromRepo ->
                likedTracks.clear()
                likedTracks.addAll(tracksFromRepo)
            }
        }

        viewModelScope.launch {
            SessionManager.isClientIdValid.collect { isReady ->
                if (isReady) {
                    loadData()
                }
            }
        }

        viewModelScope.launch {
            DownloadManager.libraryUpdated.collect {
                if (SessionManager.isClientIdValid.value) {
                    loadData()
                }
            }
        }

        viewModelScope.launch {
            com.alananasss.kittytune.ui.profile.ProfileViewModel.refreshTrigger.collect {
                if (SessionManager.isClientIdValid.value) {
                    loadData(forceRefresh = true)
                }
            }
        }

        viewModelScope.launch {
            MusicManager.trackUpdatedFlow.collect { updatedTrack ->
                val idx = uploadedTracks.indexOfFirst { it.id == updatedTrack.id }
                if (idx != -1) {
                    uploadedTracks[idx] = updatedTrack
                    prefs.edit().putString("cached_user_uploads", gson.toJson(uploadedTracks.toList())).apply()
                }
            }
        }

        viewModelScope.launch {
            MusicManager.trackDeletedFlow.collect { deletedTrackId ->
                val idx = uploadedTracks.indexOfFirst { it.id == deletedTrackId }
                if (idx != -1) {
                    uploadedTracks.removeAt(idx)
                    prefs.edit().putString("cached_user_uploads", gson.toJson(uploadedTracks.toList())).apply()
                }
            }
        }

        viewModelScope.launch {
            DownloadManager.deletedPlaylistIds.collect {
                rebuildAllItems()
            }
        }

        viewModelScope.launch {
            folderDao.getAllFolders().collect { folders ->
                allFoldersCache = folders
                rebuildAllItems()
            }
        }

        viewModelScope.launch {
            folderDao.getAllItemMetas().collect { metas ->
                allItemMetasCache = metas.associateBy { it.itemKey }
                rebuildAllItems()
            }
        }

        viewModelScope.launch {
            combine(
                db.getAllPlaylists(),
                LikeRepository.likedPlaylists,
                DownloadManager.deletedPlaylistIds
            ) { allLocalPlaylists, likedIds, deletedIds ->
                allLocalPlaylists.filter { local ->
                    !deletedIds.contains(local.id) && (local.id < 0 || local.isDownloaded || likedIds.contains(local.id))
                }
            }.collect { localPlaylists ->
                val localIds = localPlaylists.map { it.id }.toSet()
                DownloadManager.clearDeletedPlaylistIds(localIds)
                localItemsCache = localPlaylists.map { local ->
                    val finalArtwork =
                        if (!local.localCoverPath.isNullOrEmpty()) local.localCoverPath else local.artworkUrl
                    val p = Playlist(
                        id = local.id,
                        title = local.title,
                        artworkUrl = finalArtwork,
                        calculatedArtworkUrl = null,
                        trackCount = local.trackCount,
                        user = User(0, local.artist, null),
                        tracks = null,
                        isAlbum = local.isAlbum,
                        permalinkUrl = local.permalinkUrl
                    )

                    val key = LibraryItem.getPlaylistCanonicalKey(p)
                    val meta = allItemMetasCache[key]
                    val isPinned = if (meta?.folderId == null) (meta?.isPinned == true) else false
                    LibraryItem.PlaylistItem(p, local.addedAt, key, isPinned)
                }
                rebuildAllItems()
            }
        }

        viewModelScope.launch {
            DownloadManager.getSavedArtists().collect { artists ->
                savedArtistsCache = artists.map {
                    LibraryItem.ArtistItem(it, it.savedAt, "artist_${it.id}", false)
                }
                rebuildAllItems()
            }
        }
    }

    private fun rebuildAllItems() {
        val deletedIds = DownloadManager.deletedPlaylistIds.value
        val localIds = localItemsCache
            .filterIsInstance<LibraryItem.PlaylistItem>()
            .map { it.playlist.id }
            .toSet()

        val updatedOnlineItems = onlineItemsCache.map { item ->
            if (item is LibraryItem.PlaylistItem) {
                val meta = allItemMetasCache[item.key]
                val isPinned = if (meta?.folderId == null) (meta?.isPinned == true) else false
                item.copy(isPinned = isPinned)
            } else item
        }

        val updatedLocalItems = localItemsCache.map { item ->
            if (item is LibraryItem.PlaylistItem) {
                val meta = allItemMetasCache[item.key]
                val isPinned = if (meta?.folderId == null) (meta?.isPinned == true) else false
                item.copy(isPinned = isPinned)
            } else item
        }

        val filteredOnlineItems = updatedOnlineItems.filter { item ->
            if (item is LibraryItem.PlaylistItem) !localIds.contains(item.playlist.id) && !deletedIds.contains(item.playlist.id) else true
        }

        val filteredLocalItems = updatedLocalItems

        val folderItems = allFoldersCache.map { folder ->
            val childPlaylistsCount = (filteredOnlineItems + filteredLocalItems).count { item ->
                if (item is LibraryItem.PlaylistItem) {
                    allItemMetasCache[item.key]?.folderId == folder.id
                } else false
            }
            val childFoldersCount = allFoldersCache.count { it.parentFolderId == folder.id }
            val isPinned = if (folder.parentFolderId == null) folder.isPinned else false
            LibraryItem.FolderItem(
                folder = folder,
                playlistCount = childPlaylistsCount,
                folderCount = childFoldersCount,
                timestamp = folder.createdAt,
                key = "folder_${folder.id}",
                isPinned = isPinned
            )
        }

        _allItems.clear()
        _allItems.addAll(folderItems)
        _allItems.addAll(filteredOnlineItems)
        _allItems.addAll(filteredLocalItems)
        _allItems.addAll(savedArtistsCache)
    }

    fun createFolder(
        name: String,
        parentFolderId: Long? = currentFolderId,
        itemToMoveKey: String? = null,
        folderToMoveId: Long? = null
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newFolder = LibraryFolder(
                name = name.trim(),
                parentFolderId = parentFolderId
            )
            val newFolderId = folderDao.insertFolder(newFolder)
            if (itemToMoveKey != null) {
                moveItemToFolder(itemToMoveKey, newFolderId)
            }
            if (folderToMoveId != null) {
                moveFolderToFolder(folderToMoveId, newFolderId)
            }
        }
    }

    fun renameFolder(folderId: Long, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            folderDao.renameFolder(folderId, newName.trim())
        }
    }

    fun deleteFolder(folder: LibraryFolder) {
        viewModelScope.launch {
            folderDao.deleteFolderSafely(folder.id)
            if (currentFolderId == folder.id) {
                navigateUp()
            }
        }
    }

    fun isItemPinned(itemKey: String, defaultPinned: Boolean = false): Boolean {
        return allItemMetasCache[itemKey]?.isPinned ?: defaultPinned
    }

    fun togglePinItem(itemKey: String, defaultPinned: Boolean = false) {
        viewModelScope.launch {
            val currentMeta = allItemMetasCache[itemKey]
            val currentPinned = currentMeta?.isPinned ?: defaultPinned
            val newPinned = !currentPinned
            folderDao.upsertItemMeta(
                LibraryItemMeta(
                    itemKey = itemKey,
                    folderId = currentMeta?.folderId,
                    isPinned = newPinned
                )
            )
        }
    }

    fun togglePinFolder(folderId: Long) {
        viewModelScope.launch {
            val folder = allFoldersCache.find { it.id == folderId } ?: return@launch
            val newPinned = !folder.isPinned
            folderDao.setFolderPinned(folderId, newPinned)
        }
    }

    fun moveItemToFolder(itemKey: String, targetFolderId: Long?) {
        viewModelScope.launch {
            folderDao.upsertItemMeta(
                LibraryItemMeta(
                    itemKey = itemKey,
                    folderId = targetFolderId,
                    isPinned = false
                )
            )
        }
    }

    fun moveFolderToFolder(folderId: Long, targetFolderId: Long?) {
        if (folderId == targetFolderId) return
        viewModelScope.launch {
            folderDao.moveFolder(folderId, targetFolderId)
        }
    }

    fun getAvailableTargetFolders(movingFolderId: Long?): List<LibraryFolder> {
        return allFoldersCache.filter { folder ->
            folder.parentFolderId == currentFolderId && (movingFolderId == null || folder.id != movingFolderId)
        }
    }

    fun getAllPlaylistsInFolder(folderId: Long, recursive: Boolean = false): List<Playlist> {
        if (!recursive) {
            return _allItems.filterIsInstance<LibraryItem.PlaylistItem>().filter { item ->
                allItemMetasCache[item.key]?.folderId == folderId
            }.map { it.playlist }
        }
        val targetFolderIds = mutableSetOf(folderId)
        val queue = ArrayDeque<Long>()
        queue.add(folderId)
        while (queue.isNotEmpty()) {
            val parentId = queue.removeFirst()
            val subFolders = allFoldersCache.filter { it.parentFolderId == parentId }
            for (sub in subFolders) {
                if (targetFolderIds.add(sub.id)) {
                    queue.add(sub.id)
                }
            }
        }
        return _allItems.filterIsInstance<LibraryItem.PlaylistItem>().filter { item ->
            val fId = allItemMetasCache[item.key]?.folderId
            fId != null && targetFolderIds.contains(fId)
        }.map { it.playlist }
    }

    fun playFolder(
        folderId: Long,
        playerViewModel: com.alananasss.kittytune.ui.player.PlayerViewModel,
        shuffle: Boolean = false,
        recursive: Boolean = false
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val playlists = getAllPlaylistsInFolder(folderId, recursive = recursive)
            val allTracks = mutableListOf<Track>()
            playlists.forEach { playlist ->
                val tracks = playlist.tracks
                if (!tracks.isNullOrEmpty()) {
                    allTracks.addAll(tracks)
                } else {
                    val localTracks = db.getTracksForPlaylistSync(playlist.id)
                    if (localTracks.isNotEmpty()) {
                        allTracks.addAll(localTracks.map { local ->
                            Track(
                                id = local.id,
                                title = local.title,
                                user = User(0, local.artist, null),
                                artworkUrl = local.artworkUrl,
                                durationMs = local.duration
                            )
                        })
                    } else if (playlist.id > 0) {
                        try {
                            val online = RetrofitClient.create(getApplication()).getPlaylist(playlist.id)
                            if (!online.tracks.isNullOrEmpty()) {
                                allTracks.addAll(online.tracks!!)
                            }
                        } catch (e: Exception) {
                            Log.e("LibraryVM", "Failed to load online playlist ${playlist.id}", e)
                        }
                    }
                }
            }
            if (allTracks.isNotEmpty()) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    val finalTracks = if (shuffle) allTracks.shuffled() else allTracks
                    playerViewModel.playPlaylist(finalTracks, 0)
                }
            }
        }
    }

    fun loadData(forceRefresh: Boolean = false) {
        if (isLoading && !forceRefresh && userProfile != null) return

        val playerPrefs = PlayerPreferences(getApplication<Application>())
        showLocalMedia = playerPrefs.getLocalMediaEnabled()

        isGuestUser = tokenManager.isGuestMode()
        val token = tokenManager.getAccessToken()
        if (!isGuestUser && token.isNullOrEmpty()) {
            isLoading = false
            return
        }

        if (isGuestUser) {
            userProfile = User(0, app.getString(R.string.guest_user), null)
            uploadedTracks.clear()
            prefs.edit().remove("cached_user_uploads").apply()
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
                    Log.e("LibraryVM", "online error or not connected", e)
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
                    val likedPlaylistsDeferred = async {
                        val allLiked = mutableListOf<PlaylistLikeItem>()
                        try {
                            var resp = api.getUserPlaylistLikes(user.id)
                            allLiked.addAll(resp.collection)
                            while (!resp.next_href.isNullOrEmpty()) {
                                resp = api.getPlaylistLikesNextPage(resp.next_href!!)
                                allLiked.addAll(resp.collection)
                            }
                        } catch (e: Exception) {
                            Log.e("LibraryVM", "Failed to fetch playlist likes", e)
                        }
                        allLiked
                    }
                    val createdPlaylistsDeferred = async {
                        val allCreated = mutableListOf<Playlist>()
                        try {
                            var resp = api.getUserCreatedPlaylists(user.id)
                            allCreated.addAll(resp.collection)
                            while (!resp.next_href.isNullOrEmpty()) {
                                resp = api.getSearchPlaylistsNextPage(resp.next_href!!)
                                allCreated.addAll(resp.collection)
                            }
                        } catch (e: Exception) {
                            Log.e("LibraryVM", "Failed to fetch created playlists", e)
                        }
                        allCreated
                    }
                    val repostedPlaylistsDeferred = async { api.getMyPlaylistPosts() }
                    val uploadedTracksDeferred = async {
                        val tracks = mutableListOf<Track>()
                        try {
                            val firstPage = api.getUserTracks(user.id, limit = 50)
                            tracks.addAll(firstPage.collection.filterNotNull())
                            var nextUrl = firstPage.next_href
                            var pageCount = 0
                            while (nextUrl != null && pageCount < 5) {
                                val nextPage = api.getUserTracksNextPage(nextUrl)
                                tracks.addAll(nextPage.collection.filterNotNull())
                                nextUrl = nextPage.next_href
                                pageCount++
                            }
                        } catch (e: Exception) {
                            Log.e("LibraryVM", "Failed to fetch uploaded tracks", e)
                        }
                        tracks
                    }
                    val libraryAllDeferred = async {
                        try {
                            api.getMyLibraryAll(limit = 200)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val followedArtistsDeferred = async {
                        val allArtists = mutableListOf<User>()
                        try {
                            var resp = api.getUserFollowings(user.id, limit = 50)
                            allArtists.addAll(resp.collection)
                            var pageCount = 0
                            while (!resp.next_href.isNullOrEmpty() && pageCount < 20) {
                                resp = api.getUserCollectionNextPage(resp.next_href!!)
                                allArtists.addAll(resp.collection)
                                pageCount++
                            }
                        } catch (e: Exception) {
                            Log.w("LibraryVM", "REST followings failed, trying GraphQL", e)
                        }

                        if (allArtists.isEmpty()) {
                            try {
                                val userSchema =
                                    "urn permalink username avatarUrl firstName lastName city country countryCode tracksCount playlistCount followersCount followingsCount verified isPro description userAvatarUrlTemplate visualUrlTemplate stationUrns createdAt badges"
                                val followingsQuery =
                                    "query UserFollowingsQuery(\$input: UserFollowsInput!) { userFollowings(input: \$input) { pageInfo { endCursor } items { user { $userSchema } } } }"
                                var nextCursor: String? = null
                                var safetyCount = 0
                                do {
                                    val req = GraphQlFollowsRequest(
                                        operationName = "UserFollowingsQuery",
                                        query = followingsQuery,
                                        variables = GraphQlFollowsVariables(
                                            input = GraphQlFollowsInput(
                                                urn = "soundcloud:users:${user.id}",
                                                first = 50,
                                                after = nextCursor
                                            )
                                        )
                                    )
                                    val page = api.getUserFollowingsGraphQL(req)
                                    val result = page.data?.userFollowings
                                    result?.items?.forEach { it.user?.let { u -> allArtists.add(u) } }
                                    nextCursor = result?.pageInfo?.endCursor
                                    safetyCount++
                                } while (nextCursor != null && safetyCount < 20)
                            } catch (e2: Exception) {
                                Log.e("LibraryVM", "Failed to fetch followings via GraphQL", e2)
                            }
                        }
                        allArtists
                    }

                    val likedResponse = likedPlaylistsDeferred.await()
                    val createdResponse = createdPlaylistsDeferred.await()
                    val repostedResponse = repostedPlaylistsDeferred.await()
                    val libraryAllResponse = libraryAllDeferred.await()
                    val followingsResponse = followedArtistsDeferred.await()
                    val uploadedResponse = uploadedTracksDeferred.await()
                    uploadedTracks.clear()
                    uploadedTracks.addAll(uploadedResponse)
                    prefs.edit().putString("cached_user_uploads", gson.toJson(uploadedResponse)).apply()
                    Log.d("LibraryVM", "Followings fetched count: ${followingsResponse.size}, Uploaded tracks count: ${uploadedResponse.size}")

                    if (followingsResponse.isNotEmpty()) {
                        try {
                            val validArtists = followingsResponse.filter { it.numericId != 0L }
                            val now = System.currentTimeMillis()
                            val localArtists = validArtists.mapIndexed { index, u ->
                                LocalArtist(
                                    id = u.numericId,
                                    username = u.username ?: "Artist",
                                    avatarUrl = u.avatarUrl ?: "",
                                    trackCount = u.trackCount,
                                    savedAt = now - (index * 1000L)
                                )
                            }
                            db.insertArtists(localArtists)
                            savedArtistsCache = localArtists.map {
                                LibraryItem.ArtistItem(it, it.savedAt, "artist_${it.id}", false)
                            }
                        } catch (e: Exception) {
                            Log.e("LibraryVM", "Failed to save artists to DB", e)
                        }
                    }

                    val newOnlineItems = mutableListOf<LibraryItem>()
                    val addedPlaylistIds = mutableSetOf<Long>()
                    val trulyLikedIds = mutableSetOf<Long>()

                    likedResponse.forEach { item ->
                        val pl = item.playlist
                        val sp = item.systemPlaylist

                        if (pl != null && addedPlaylistIds.add(pl.id)) {
                            trulyLikedIds.add(pl.id)
                            val date = try {
                                item.likedAt?.let { isoParser.parse(it)?.time } ?: 0L
                            } catch (e: Exception) {
                                0L
                            }
                            newOnlineItems.add(LibraryItem.PlaylistItem(pl, date))
                        } else if (sp != null) {
                            val numId = sp.urn?.let { kotlin.math.abs(it.hashCode().toLong()) } ?: sp.numericId
                            if (numId != 0L && addedPlaylistIds.add(numId)) {
                                trulyLikedIds.add(numId)
                                val stationPermalink = sp.permalinkUrl
                                    ?: if (sp.isArtistStation) "https://soundcloud.com/discover/sets/artist-stations:${sp.numericId}" else "https://soundcloud.com/discover/sets/track-stations:${sp.numericId}"
                                val fakePlaylist = Playlist(
                                    id = numId,
                                    title = sp.title,
                                    artworkUrl = sp.artworkUrl,
                                    calculatedArtworkUrl = sp.calculatedArtworkUrl,
                                    trackCount = sp.tracks?.size,
                                    user = sp.user,
                                    tracks = null,
                                    permalinkUrl = stationPermalink,
                                    urn = sp.urn
                                )
                                val date = try {
                                    item.likedAt?.let { isoParser.parse(it)?.time } ?: 0L
                                } catch (e: Exception) {
                                    0L
                                }
                                newOnlineItems.add(LibraryItem.PlaylistItem(fakePlaylist, date))
                            }
                        }
                    }

                    createdResponse.forEach { playlist ->
                        if (addedPlaylistIds.add(playlist.id)) {
                            val date = try {
                                val dateStr = playlist.lastModified ?: playlist.createdAt
                                dateStr?.let { isoParser.parse(it)?.time } ?: 0L
                            } catch (e: Exception) {
                                0L
                            }
                            newOnlineItems.add(LibraryItem.PlaylistItem(playlist, date))
                        }
                    }

                    repostedResponse.collection.forEach { item ->
                        val playlist = item.playlist ?: return@forEach
                        if (addedPlaylistIds.add(playlist.id)) {
                            val date = try {
                                val dateStr = playlist.lastModified ?: playlist.createdAt
                                dateStr?.let { isoParser.parse(it)?.time } ?: 0L
                            } catch (e: Exception) {
                                0L
                            }
                            newOnlineItems.add(LibraryItem.PlaylistItem(playlist, date))
                        }
                    }

                    libraryAllResponse?.collection?.forEach { item ->
                        val sp = item.systemPlaylist ?: return@forEach
                        val numId = sp.urn?.let { kotlin.math.abs(it.hashCode().toLong()) } ?: sp.numericId
                        if (numId != 0L && addedPlaylistIds.add(numId)) {
                            trulyLikedIds.add(numId)
                            val stationPermalink = sp.permalinkUrl
                                ?: if (sp.isArtistStation) "https://soundcloud.com/discover/sets/artist-stations:${sp.numericId}" else "https://soundcloud.com/discover/sets/track-stations:${sp.numericId}"
                            val fakePlaylist = Playlist(
                                id = numId,
                                title = sp.title,
                                artworkUrl = sp.artworkUrl,
                                calculatedArtworkUrl = sp.calculatedArtworkUrl,
                                trackCount = sp.tracks?.size,
                                user = sp.user,
                                tracks = null,
                                permalinkUrl = stationPermalink,
                                urn = sp.urn
                            )
                            val date = try {
                                item.createdAt?.let { isoParser.parse(it)?.time } ?: 0L
                            } catch (e: Exception) {
                                0L
                            }
                            newOnlineItems.add(LibraryItem.PlaylistItem(fakePlaylist, date))
                        }
                    }

                    LikeRepository.setLikedPlaylists(trulyLikedIds)

                    onlineItemsCache = newOnlineItems
                    rebuildAllItems()

                    if (!isHydratingLikes) {
                        isHydratingLikes = true
                        LikeRepository.setSyncing(true)

                        launch(Dispatchers.IO) {
                            try {
                                val allCollectedLikes = mutableListOf<Track>()
                                var nextUrl: String? = null

                                val firstPage = api.getUserTrackLikes(user.id, limit = 200)

                                allCollectedLikes.addAll(firstPage.collection.map { item ->
                                    val time = try {
                                        item.createdAt?.let { isoParser.parse(it)?.time }
                                    } catch (e: Exception) {
                                        0L
                                    }
                                    item.track.copy(likedAt = time)
                                })

                                nextUrl = firstPage.next_href

                                while (nextUrl != null) {
                                    val page = api.getTrackLikesNextPage(nextUrl!!)

                                    allCollectedLikes.addAll(page.collection.map { item ->
                                        val time = try {
                                            item.createdAt?.let { isoParser.parse(it)?.time }
                                        } catch (e: Exception) {
                                            0L
                                        }
                                        item.track.copy(likedAt = time)
                                    })

                                    nextUrl = page.next_href
                                }

                                LikeRepository.replaceAllLikes(allCollectedLikes, currentUserId = user.id)

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

enum class LibraryFilter(val stringRes: Int) {
    PLAYLISTS(R.string.lib_playlists),
    ALBUMS(R.string.lib_albums),
    ARTISTS(R.string.lib_artists),
    STATIONS(R.string.lib_stations),
    UPLOADS(R.string.lib_your_uploads)
}

enum class OwnershipFilter { ALL, CREATED, LIKED }

enum class StationFilter(val stringRes: Int) {
    ALL(R.string.filter_all),
    TRACKS(R.string.profile_tab_tracks),
    ARTISTS(R.string.lib_artists)
}

enum class LibrarySortOption {
    RECENTS,
    DATE_ADDED,
    ALPHABETICAL,
    CREATOR
}

enum class UploadsSortOption(val stringRes: Int) {
    RECENTLY_ADDED(R.string.uploads_sort_recently_added),
    FIRST_ADDED(R.string.uploads_sort_first_added),
    TITLE(R.string.uploads_sort_title),
    ARTIST(R.string.uploads_sort_artist)
}

enum class UploadsPrivacyFilter(val stringRes: Int) {
    ALL(R.string.uploads_filter_all),
    PUBLIC(R.string.uploads_filter_public),
    PRIVATE(R.string.uploads_filter_private)
}

