    package com.alananasss.kittytune.data

    import android.content.Context
    import android.net.Uri
    import android.util.Log
    import androidx.documentfile.provider.DocumentFile
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.local.*
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.data.network.SoundCloudApi
    import com.alananasss.kittytune.domain.*
    import com.mpatric.mp3agic.ID3v24Tag
    import com.mpatric.mp3agic.Mp3File
    import kotlinx.coroutines.*
    import kotlinx.coroutines.flow.*
    import kotlinx.coroutines.sync.Semaphore
    import kotlinx.coroutines.sync.withPermit
    import kotlinx.coroutines.flow.MutableSharedFlow
    import kotlinx.coroutines.flow.asSharedFlow
    import okhttp3.OkHttpClient
    import okhttp3.Request
    import java.io.File
    import java.io.FileInputStream
    import java.io.FileOutputStream
    import java.io.OutputStream

    object DownloadManager {
        const val LIKES_BATCH_ID = -1L

        private const val CONCURRENT_DOWNLOAD_LIMIT = 4
        private val downloadSemaphore = Semaphore(CONCURRENT_DOWNLOAD_LIMIT)

        private lateinit var context: Context
        private lateinit var database: AppDatabase
        private lateinit var prefs: PlayerPreferences

        private val api: SoundCloudApi by lazy { RetrofitClient.create(context) }
        private val scope = CoroutineScope(Dispatchers.IO)
        private val client = OkHttpClient()

        private fun playlistUrn(playlistId: Long): String = "soundcloud:playlists:$playlistId"

        private fun trackUrns(trackIds: List<Long>): List<String> {
            return trackIds.filter { it > 0 }.map { "soundcloud:tracks:$it" }
        }

        private fun appendMissingTrackIds(existingTrackIds: List<Long>, newTrackIds: List<Long>): List<Long> {
            val merged = existingTrackIds.filter { it > 0 }.toMutableList()
            newTrackIds.filter { it > 0 }.forEach { trackId ->
                if (!merged.contains(trackId)) merged.add(trackId)
            }
            return merged
        }

        private fun mergeReorderedTrackIds(remoteTrackIds: List<Long>, reorderedTrackIds: List<Long>): List<Long> {
            val orderedIds = reorderedTrackIds.filter { it > 0 }
            val orderedSet = orderedIds.toSet()
            return orderedIds + remoteTrackIds.filter { it > 0 && it !in orderedSet }
        }

        private fun playlistUpdateRequest(
            playlist: Playlist,
            trackIds: List<Long>,
            title: String = playlist.title.orEmpty(),
            description: String = playlist.description.orEmpty(),
            genre: String = playlist.genre.orEmpty(),
            tagList: String = playlist.tagList.orEmpty(),
            isPublic: Boolean = playlist.sharing == "public"
        ): PlaylistUpdateRequest {
            return PlaylistUpdateRequest(
                trackUrns = trackUrns(trackIds),
                title = title,
                description = description,
                genre = genre,
                tagList = tagList,
                isPublic = isPublic
            )
        }

        private suspend fun updateRemotePlaylist(playlistId: Long, request: PlaylistUpdateRequest) {
            var response = api.updatePlaylist(playlistId, request)
            if (!response.isSuccessful) {
                var errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                val captchaUrl = SessionManager.extractDataDomeCaptchaUrl(errorBody)
                if (response.code() == 403 && captchaUrl != null) {
                    val solved = SessionManager.awaitDataDomeChallenge(context, captchaUrl)
                    if (solved) {
                        response = api.updatePlaylist(playlistId, request)
                        if (response.isSuccessful) return
                        errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                    }
                }
                throw Exception("SoundCloud playlist update failed (${response.code()}): ${errorBody ?: response.message()}")
            }
        }

        private val _downloadProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
        val downloadProgress = _downloadProgress.asStateFlow()

        private val _playlistDownloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
        val playlistDownloadProgress = _playlistDownloadProgress.asStateFlow()

        private val _storageTrigger = MutableStateFlow(0)
        val storageTrigger = _storageTrigger.asStateFlow()

        private val _libraryUpdated = MutableSharedFlow<Unit>(replay = 1)
        val libraryUpdated = _libraryUpdated.asSharedFlow()

        private val _deletedPlaylistIds = MutableStateFlow<Set<Long>>(emptySet())
        val deletedPlaylistIds = _deletedPlaylistIds.asStateFlow()

        lateinit var downloadedIds: StateFlow<Set<Long>>

        private val activeJobs = mutableMapOf<Long, Job>()
        private val activePlaylistJobs = mutableMapOf<Long, Job>()
        private val batchTrackIds = mutableMapOf<Long, Set<Long>>()

        fun init(ctx: Context) {
            context = ctx.applicationContext
            database = AppDatabase.getDatabase(context)
            prefs = PlayerPreferences(context)

            downloadedIds = database.downloadDao().getAllTracks()
                .map { list -> list.map { it.id }.toSet() }
                .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptySet())

            // Cleanup legacy cached playlists from the old background sync feature
            scope.launch {
                try {
                    val dao = database.downloadDao()
                    val allPlaylists = dao.getAllPlaylists().first()
                    allPlaylists.forEach { localPlaylist ->
                        if (!localPlaylist.isUserCreated && localPlaylist.id > 0) {
                            val tracks = dao.getTracksForPlaylistSync(localPlaylist.id)
                            val hasDownloadedTracks = tracks.any { it.localAudioPath.isNotEmpty() }
                            if (!hasDownloadedTracks) {
                                dao.deletePlaylist(localPlaylist.id)
                                dao.deletePlaylistRefs(localPlaylist.id)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DownloadManager", "Failed to cleanup legacy cached playlists", e)
                }
            }
        }

        private fun sanitizeFilename(name: String): String {
            return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
        }

        private fun getOutputStreamForFile(fileName: String, mimeType: String, subDir: String? = null): Pair<OutputStream, String> {
            val customUriStr = prefs.getDownloadLocation()

            if (customUriStr != null) {
                try {
                    val treeUri = Uri.parse(customUriStr)
                    val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                        ?: throw Exception(context.getString(R.string.error_access_external_dir))

                    val targetDir = if (subDir != null) {
                        rootDoc.findFile(subDir) ?: rootDoc.createDirectory(subDir)
                        ?: throw Exception("Impossible de créer le dossier de playlist")
                    } else {
                        rootDoc
                    }

                    val existing = targetDir.findFile(fileName)
                    if (existing != null) existing.delete()

                    val targetDoc = targetDir.createFile(mimeType, fileName)
                        ?: throw Exception(context.getString(R.string.error_create_external_file))

                    val stream = context.contentResolver.openOutputStream(targetDoc.uri)
                        ?: throw Exception(context.getString(R.string.error_open_external_stream))

                    return Pair(stream, targetDoc.uri.toString())
                } catch (e: Exception) { e.printStackTrace(); throw e }
            } else {
                val parentDir = if (subDir != null) {
                    File(context.filesDir, subDir).apply {
                        if (!exists()) mkdirs()
                    }
                } else {
                    context.filesDir
                }

                val file = File(parentDir, fileName)
                return Pair(FileOutputStream(file), file.absolutePath)
            }
        }

        private fun deleteFileByPath(path: String) {
            if (path.isEmpty()) return
            try {
                if (path.startsWith("exo_cache://")) {
                    val parts = path.removePrefix("exo_cache://").split("::", limit = 3)
                    val streamUrl = parts.getOrNull(1)
                    if (streamUrl != null) {
                        val cache = ExoCacheManager.getCache(context)
                        // In Media3, we remove a resource by its cache key, which defaults to its URI.
                        // HLS downloads multiple keys (the playlist, plus all segments).
                        // To remove a full HLS download properly, one should use DownloadManager.
                        // However, as a fallback we remove the main playlist key.
                        // A more robust cleanup would be removing the cache directory.
                        cache.keys.forEach { key ->
                            if (key.contains(streamUrl.substringBeforeLast("/"))) {
                                cache.removeResource(key)
                            }
                        }
                    }
                    return
                }
                if (path.startsWith("content://")) {
                    DocumentFile.fromSingleUri(context, Uri.parse(path))?.delete()
                } else {
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        suspend fun removeAllContent(includeAudio: Boolean, includeImages: Boolean) {
            withContext(Dispatchers.IO) {
                val allTracks = database.downloadDao().getAllTracks().first()
                allTracks.forEach { track ->
                    var updatedTrack = track
                    var changed = false
                    if (includeAudio && track.localAudioPath.isNotEmpty()) {
                        deleteFileByPath(track.localAudioPath)
                        updatedTrack = updatedTrack.copy(localAudioPath = "")
                        changed = true
                    }
                    if (includeImages && track.localArtworkPath.isNotEmpty()) {
                        deleteFileByPath(track.localArtworkPath)
                        updatedTrack = updatedTrack.copy(localArtworkPath = "")
                        changed = true
                    }
                    if (changed) database.downloadDao().updateTrack(updatedTrack)
                }
                _storageTrigger.update { it + 1 }
            }
        }

        suspend fun createUserPlaylist(name: String): Long {
            val tokenManager = TokenManager(context)
            var serverId: Long? = null
            if (!tokenManager.isGuestMode()) {
                try {
                    val req = com.alananasss.kittytune.domain.PlaylistCreateRequest(
                        com.alananasss.kittytune.domain.PlaylistCreatePayload(title = name, isPublic = true)
                    )
                    val response = api.createPlaylist(req)
                    if (response.isSuccessful) {
                        val body = response.body()?.asJsonObject
                        var extractedId = 0L

                        if (body != null) {
                            if (body.has("id")) extractedId = body.get("id").asLong
                            else if (body.has("playlist")) {
                                val pObj = body.getAsJsonObject("playlist")
                                if (pObj.has("id")) extractedId = pObj.get("id").asLong
                                if (extractedId == 0L && pObj.has("urn")) {
                                    extractedId = pObj.get("urn").asString.split(":").lastOrNull()?.toLongOrNull() ?: 0L
                                }
                            }
                            if (extractedId == 0L && body.has("urn")) {
                                extractedId = body.get("urn").asString.split(":").lastOrNull()?.toLongOrNull() ?: 0L
                            }
                        }

                        if (extractedId > 0L) {
                            serverId = extractedId
                            _libraryUpdated.emit(Unit)
                            return extractedId
                        }
                    } else {
                        Log.e("DownloadManager", "Failed to create playlist. Code: ${response.code()}, errorBody: ${response.errorBody()?.string()}")
                    }
                } catch (e: Exception) {
                    Log.e("DownloadManager", "Exception creating playlist on SoundCloud", e)
                }
            }
            val finalId = serverId ?: -(System.currentTimeMillis())
            val playlist = LocalPlaylist(id = finalId, title = name, artist = context.getString(R.string.me_artist), artworkUrl = "", trackCount = 0, isUserCreated = true)
            database.downloadDao().insertPlaylist(playlist)
            _libraryUpdated.emit(Unit)
            return finalId
        }
        fun addTrackToPlaylist(playlistId: Long, track: Track) {
            scope.launch {
                val dao = database.downloadDao()
                val existingTrack = dao.getTrack(track.id)
                if (existingTrack == null) {
                    val localTrack = LocalTrack(
                        id = track.id,
                        title = track.title ?: context.getString(R.string.untitled_track),
                        artist = track.user?.username ?: context.getString(R.string.unknown_artist),
                        artworkUrl = track.fullResArtwork,
                        duration = track.durationMs ?: 0L,
                        localAudioPath = "",
                        localArtworkPath = ""
                    )
                    dao.insertTrack(localTrack)
                }
                dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(playlistId, track.id))
                val playlist = dao.getPlaylist(playlistId)
                if (playlist != null) dao.updatePlaylist(playlist.copy(trackCount = playlist.trackCount + 1))

                if (playlistId > 0 && !TokenManager(context).isGuestMode()) {
                    try {
                        val onlinePlaylist = api.getPlaylist(playlistId)
                        val trackIds = appendMissingTrackIds(
                            existingTrackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id },
                            newTrackIds = listOf(track.id)
                        )
                        val request = playlistUpdateRequest(onlinePlaylist, trackIds)
                        updateRemotePlaylist(playlistId, request)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
        fun removeTrackFromPlaylist(playlistId: Long, trackId: Long, syncToCloud: Boolean = true) {
            scope.launch {
                val dao = database.downloadDao()
                dao.removeTrackFromPlaylist(playlistId, trackId)
                val playlist = dao.getPlaylist(playlistId)
                if (playlist != null) dao.updatePlaylist(playlist.copy(trackCount = (playlist.trackCount - 1).coerceAtLeast(0)))

                if (syncToCloud && playlistId > 0 && !TokenManager(context).isGuestMode()) {
                    try {
                        val onlinePlaylist = api.getPlaylist(playlistId)
                        val trackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id }.toMutableList()
                        trackIds.remove(trackId)
                        val request = playlistUpdateRequest(onlinePlaylist, trackIds)
                        updateRemotePlaylist(playlistId, request)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                _storageTrigger.update { it + 1 }
                _libraryUpdated.tryEmit(Unit)
            }
        }
        fun swapTrackOrder(playlistId: Long, trackId1: Long, trackId2: Long) {
            scope.launch {
                val dao = database.downloadDao()
                val ref1 = dao.getRef(playlistId, trackId1); val ref2 = dao.getRef(playlistId, trackId2)
                if (ref1 != null && ref2 != null) { dao.updatePlaylistTrackRef(ref1.copy(addedAt = ref2.addedAt)); dao.updatePlaylistTrackRef(ref2.copy(addedAt = ref1.addedAt)) }
            }
        }
        fun reorderPlaylistTracks(playlistId: Long, orderedTrackIds: List<Long>) {
            scope.launch {
                val dao = database.downloadDao()
                val baseTime = System.currentTimeMillis()
                orderedTrackIds.forEachIndexed { index, trackId ->
                    val ref = dao.getRef(playlistId, trackId)
                    if (ref != null) {
                        dao.updatePlaylistTrackRef(ref.copy(addedAt = baseTime + index))
                    }
                }
            }
        }
        fun syncPlaylistOrderOnline(playlistId: Long, newOrderIds: List<Long>) {
            scope.launch {
                if (playlistId > 0 && !TokenManager(context).isGuestMode()) {
                    try {
                        val onlinePlaylist = api.getPlaylist(playlistId)
                        val onlineTrackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id }
                        val finalTrackIds = mergeReorderedTrackIds(onlineTrackIds, newOrderIds)
                        val request = playlistUpdateRequest(onlinePlaylist, finalTrackIds)
                        updateRemotePlaylist(playlistId, request)
                    } catch (e: Exception) { e.printStackTrace() }
                }
            }
        }
        fun updatePlaylistCover(playlistId: Long, uri: Uri) {
            scope.launch { try { val inputStream = context.contentResolver.openInputStream(uri); val file = File(context.filesDir, "playlist_cover_${playlistId}.jpg"); inputStream?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }; val playlist = database.downloadDao().getPlaylist(playlistId); if (playlist != null) database.downloadDao().updatePlaylist(playlist.copy(localCoverPath = file.absolutePath)) } catch (e: Exception) { e.printStackTrace() } }
        }
        fun editPlaylistMetadata(
            playlistId: Long, 
            newTitle: String, 
            newDescription: String? = null, 
            newSharing: String? = null, 
            newTagList: String? = null,
            newPermalink: String? = null,
            newGenre: String? = null,
            newSetType: String? = null,
            newReleaseDate: String? = null,
            syncToCloud: Boolean = true
        ) { 
            scope.launch { 
                database.downloadDao().updatePlaylistTitle(playlistId, newTitle) 
                if (syncToCloud && playlistId > 0 && !TokenManager(context).isGuestMode()) {
                    try {
                        val onlinePlaylist = api.getPlaylist(playlistId)
                        val request = playlistUpdateRequest(
                            playlist = onlinePlaylist,
                            trackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id },
                            title = newTitle,
                            description = newDescription ?: onlinePlaylist.description.orEmpty(),
                            genre = newGenre ?: onlinePlaylist.genre.orEmpty(),
                            tagList = newTagList ?: onlinePlaylist.tagList.orEmpty(),
                            isPublic = newSharing?.let { it == "public" } ?: (onlinePlaylist.sharing == "public")
                        )
                        updateRemotePlaylist(playlistId, request)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                _storageTrigger.update { it + 1 }
                _libraryUpdated.tryEmit(Unit)
            } 
        }
        fun getAllPlaylistsFlow() = database.downloadDao().getAllPlaylists()
        fun getUserPlaylistsFlow() = database.downloadDao().getUserPlaylists()
        fun isPlaylistInLibraryFlow(playlistId: Long) = database.downloadDao().getPlaylistFlow(playlistId)

    fun importPlaylistToLibrary(playlist: Playlist, tracks: List<Track>, syncToCloud: Boolean = true) {
        scope.launch {
            val tokenManager = TokenManager(context)
            if (syncToCloud && playlist.id > 0 && !tokenManager.isGuestMode()) {
                val token = tokenManager.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    try {
                        val permalink = playlist.permalinkUrl ?: ""
                        val targetUrn = playlist.urn ?: when {
                            permalink.contains("artist-stations") -> "soundcloud:system-playlists:artist-stations:${playlist.id}"
                            permalink.contains("track-stations") -> "soundcloud:system-playlists:track-stations:${playlist.id}"
                            else -> "soundcloud:playlists:${playlist.id}"
                        }
                        val payload = com.alananasss.kittytune.data.network.PlaylistLikeRequest(
                            likes = listOf(com.alananasss.kittytune.data.network.PlaylistLikeItem(targetUrn))
                        )
                        val response = api.likePlaylist(payload)
                        if (response.code() == 401) {
                            SessionManager.requestSessionRefresh(context, force = true)
                        }
                        Log.d("DownloadManager", "Direct playlist like success status: ${response.code()} for $targetUrn")
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "Direct playlist like failed", e)
                    }
                }
            }

            val dao = database.downloadDao()
            val baseTime = System.currentTimeMillis()

            val localPlaylist = LocalPlaylist(
                id = playlist.id,
                title = playlist.title ?: context.getString(R.string.untitled_track),
                artist = playlist.user?.username ?: context.getString(R.string.unknown_artist),
                artworkUrl = playlist.fullResArtwork,
                trackCount = tracks.size,
                isUserCreated = false,
                permalinkUrl = playlist.permalinkUrl,
                isAlbum = playlist.isAlbum
            )
            dao.insertPlaylist(localPlaylist)

            tracks.forEachIndexed { index, track ->
                val existingTrack = dao.getTrack(track.id)

                if (existingTrack == null) {
                    val localTrack = LocalTrack(
                        id = track.id,
                        title = track.title ?: context.getString(R.string.untitled_track),
                        artist = track.user?.username ?: context.getString(R.string.unknown_artist),
                        artworkUrl = track.fullResArtwork,
                        duration = track.durationMs ?: 0L,
                        localAudioPath = "",
                        localArtworkPath = ""
                    )
                    dao.insertTrack(localTrack)
                }

                dao.insertPlaylistTrackRef(
                    PlaylistTrackCrossRef(
                        playlistId = playlist.id,
                        trackId = track.id,
                        addedAt = baseTime + index
                    )
                )
            }
            _libraryUpdated.tryEmit(Unit)
        }
    }

    fun deletePlaylist(playlistId: Long, syncToCloud: Boolean = true, forceUserCreated: Boolean? = null, forcePermalink: String? = null) {
        scope.launch {
            val dao = database.downloadDao()
            val playlistToDelete = dao.getPlaylist(playlistId)

            val tokenManager = TokenManager(context)
            if (syncToCloud && playlistId > 0 && !tokenManager.isGuestMode()) {
                val token = tokenManager.getAccessToken()
                if (!token.isNullOrEmpty()) {
                    try {
                        val permalink = forcePermalink ?: playlistToDelete?.permalinkUrl ?: ""
                        val targetUrn = when {
                            permalink.contains("artist-stations") -> "soundcloud:system-playlists:artist-stations:$playlistId"
                            permalink.contains("track-stations") -> "soundcloud:system-playlists:track-stations:$playlistId"
                            else -> "soundcloud:playlists:$playlistId"
                        }

                        val isUserCreated = forceUserCreated ?: playlistToDelete?.isUserCreated ?: false

                        if (isUserCreated) {
                            val response = api.deletePlaylist(playlistId)
                            if (response.code() == 401) {
                                SessionManager.requestSessionRefresh(context, force = true)
                            }
                            Log.d("DownloadManager", "Deleted user playlist on SoundCloud: ${response.code()}")
                        } else {
                            val payload = com.alananasss.kittytune.data.network.PlaylistLikeRequest(
                                likes = listOf(com.alananasss.kittytune.data.network.PlaylistLikeItem(targetUrn))
                            )
                            val response = api.unlikePlaylist(payload)
                            if (response.code() == 401) {
                                SessionManager.requestSessionRefresh(context, force = true)
                            }
                            Log.d("DownloadManager", "Direct playlist unlike success status: ${response.code()} for $targetUrn")
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "Failed to sync playlist deletion to server", e)
                    }
                }
            }

            if (playlistToDelete != null) {
                val folderName = sanitizeFilename(playlistToDelete.title)
                val customUriStr = prefs.getDownloadLocation()

                if (customUriStr != null) {
                    try {
                        val treeUri = Uri.parse(customUriStr)
                        val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                        val playlistDir = rootDoc?.findFile(folderName)
                        if (playlistDir != null && playlistDir.isDirectory) {
                            playlistDir.delete()
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "Failed to delete playlist folder from external storage: $folderName", e)
                    }
                } else {
                    try {
                        val playlistDir = File(context.filesDir, folderName)
                        if (playlistDir.exists() && playlistDir.isDirectory) {
                            playlistDir.deleteRecursively()
                        }
                    } catch (e: Exception) {
                        Log.e("DownloadManager", "Failed to delete playlist folder from internal storage: $folderName", e)
                    }
                }
            }
            val playlistTracks = dao.getTracksForPlaylistSync(playlistId)

            dao.deletePlaylist(playlistId)
            dao.deletePlaylistRefs(playlistId)
            if (syncToCloud) {
                HistoryRepository.removeFromHistory(playlistId)
                _deletedPlaylistIds.update { it + playlistId }
            }

            playlistTracks.forEach { track ->
                val remainingRefCount = dao.getPlaylistRefCount(track.id)
                if (remainingRefCount == 0) {
                    val audioExists = when {
                        track.localAudioPath.isEmpty() -> false
                        track.localAudioPath.startsWith("exo_cache://") -> true
                        track.localAudioPath.startsWith("content://") -> {
                            try { DocumentFile.fromSingleUri(context, Uri.parse(track.localAudioPath))?.exists() == true } catch (e: Exception) { false }
                        }
                        else -> File(track.localAudioPath).exists()
                    }
                    if (!audioExists) {
                        deleteFileByPath(track.localArtworkPath)
                        dao.deleteTrack(track.id)
                    }
                }
            }
            dao.cleanUnreferencedEmptyTracks()

            _storageTrigger.update { it + 1 }
            _libraryUpdated.tryEmit(Unit)

        }
    }

        fun clearDeletedPlaylistId(playlistId: Long) {
            _deletedPlaylistIds.update { it - playlistId }
            _libraryUpdated.tryEmit(Unit)
        }

        fun notifyLibraryUpdated() {
            _libraryUpdated.tryEmit(Unit)
        }

        fun removePlaylistDownloads(playlistId: Long) {
            deletePlaylist(playlistId = playlistId, syncToCloud = false)
        }

        fun removeDownloads(tracks: List<Track>) {
            scope.launch {
                val dao = database.downloadDao()
                tracks.forEach { track ->
                    val local = dao.getTrack(track.id)
                    if (local != null) {
                        deleteFileByPath(local.localAudioPath)
                        deleteFileByPath(local.localArtworkPath)
                        val refCount = dao.getPlaylistRefCount(track.id)
                        if (refCount > 0) {
                            dao.updateTrack(local.copy(localAudioPath = "", localArtworkPath = ""))
                        } else {
                            dao.deleteTrack(track.id)
                        }
                    }
                }
                dao.cleanUnreferencedEmptyTracks()
                _storageTrigger.update { it + 1 }
                _libraryUpdated.tryEmit(Unit)
            }
        }

        fun toggleSaveArtist(user: User) {
        scope.launch {
            val dao = database.downloadDao()
        val userId = user.numericId
            val isSaved = dao.getArtist(userId) != null
            if (isSaved) {
                dao.deleteArtist(userId)
            } else {
                dao.insertArtist(LocalArtist(userId, user.username ?: context.getString(R.string.menu_go_artist), user.avatarUrl ?: "", user.trackCount))
            }

            val tokenManager = TokenManager(context)
            if (!tokenManager.isGuestMode()) {
                try {
                    if (isSaved) {
                        api.unfollowUser(userId)
                    } else {
                        api.followUser(userId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }
    fun isArtistSavedFlow(artistId: Long) = database.downloadDao().getArtistFlow(artistId)
    fun getSavedArtists() = database.downloadDao().getAllSavedArtists()

    suspend fun isArtistSaved(artistId: Long): Boolean {
        return database.downloadDao().getArtist(artistId) != null
    }

    suspend fun saveArtist(user: User) {
        database.downloadDao().insertArtist(com.alananasss.kittytune.data.local.LocalArtist(user.numericId, user.username ?: "", user.avatarUrl ?: "", user.trackCount))
    }

    suspend fun deleteArtist(artistId: Long) {
        database.downloadDao().deleteArtist(artistId)
    }

    fun refreshFollowings() {
        scope.launch {
            try {
                val tokenManager = TokenManager(context)
                if (tokenManager.isGuestMode()) return@launch

                val me = api.getMe()
                val allFollowings = mutableListOf<User>()

                var nextCursor: String? = null
                val userSchema = "urn permalink username avatarUrl firstName lastName city country countryCode tracksCount playlistCount followersCount followingsCount verified isPro description userAvatarUrlTemplate visualUrlTemplate stationUrns createdAt badges"
                val followingsQuery = "query UserFollowingsQuery(\$input: UserFollowsInput!) { userFollowings(input: \$input) { pageInfo { endCursor } items { user { $userSchema } } } }"

                val req = GraphQlFollowsRequest(
                    operationName = "UserFollowingsQuery",
                    query = followingsQuery,
                    variables = GraphQlFollowsVariables(
                        input = GraphQlFollowsInput(
                            urn = "soundcloud:users:${me.id}",
                            first = 200,
                            after = null
                        )
                    )
                )
                val firstPage = api.getUserFollowingsGraphQL(req)
                val result = firstPage.data?.userFollowings
                result?.items?.forEach { it.user?.let { u -> allFollowings.add(u) } }
                nextCursor = result?.pageInfo?.endCursor

                var safetyCount = 0
                while (nextCursor != null && safetyCount < 20) {
                    val nextReq = req.copy(
                        variables = GraphQlFollowsVariables(
                            input = GraphQlFollowsInput(
                                urn = "soundcloud:users:${me.id}",
                                first = 200,
                                after = nextCursor
                            )
                        )
                    )
                    val page = api.getUserFollowingsGraphQL(nextReq)
                    val pageResult = page.data?.userFollowings
                    pageResult?.items?.forEach { it.user?.let { u -> allFollowings.add(u) } }
                    nextCursor = pageResult?.pageInfo?.endCursor
                    safetyCount++
                }

                val dao = database.downloadDao()
                // Update local DB with new followings
                allFollowings.forEach { user ->
                    dao.insertArtist(com.alananasss.kittytune.data.local.LocalArtist(user.numericId, user.username ?: "", user.avatarUrl ?: "", user.trackCount))
                }
                // Optional: remove artists that are no longer followed?
                // The official app does clear and sync. For now, we just insert.
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

        fun downloadPlaylist(playlist: Playlist, tracks: List<Track>) {
            importPlaylistToLibrary(playlist, tracks)
            val folderName = sanitizeFilename(playlist.title ?: "Playlist_${playlist.id}")
            downloadBatch(tracks, playlist.id, folderName)
        }

        fun downloadBatch(tracks: List<Track>, batchId: Long, subFolderName: String? = null) {
            val existingJob = activePlaylistJobs[batchId]
            if (existingJob != null && existingJob.isActive) return

            val batchJob = scope.launch {
                val trackIdsToDownload = tracks.map { it.id }.toSet()
                batchTrackIds[batchId] = trackIdsToDownload
                try {
                    _playlistDownloadProgress.update { it + (batchId to 0f) }

                    supervisorScope {
                        val individualJobs = tracks.map { track ->
                            launch {
                                downloadSemaphore.withPermit {
                                    val existing = database.downloadDao().getTrack(track.id)
                                    if (existing == null || existing.localAudioPath.isEmpty()) {
                                        startDownloadJob(track, subFolderName).join()
                                    }
                                }
                            }
                        }

                        val progressJob = launch {
                            combine(_downloadProgress, downloadedIds) { progressMap, downloadedSet ->
                                var totalPercent = 0L
                                trackIdsToDownload.forEach { id ->
                                    val currentProgress = progressMap[id]
                                    when {
                                        currentProgress != null -> totalPercent += currentProgress.toLong()
                                        downloadedSet.contains(id) -> totalPercent += 100L
                                        else -> totalPercent += 0L
                                    }
                                }
                                val count = trackIdsToDownload.size.coerceAtLeast(1)
                                (totalPercent.toFloat() / (count * 100f)).coerceIn(0f, 1f)
                            }.collect { overallPercentage ->
                                _playlistDownloadProgress.update { it + (batchId to overallPercentage) }
                            }
                        }

                        individualJobs.joinAll()
                        progressJob.cancel()
                    }

                    _playlistDownloadProgress.update { it + (batchId to 1f) }
                    delay(500)
                    _storageTrigger.update { it + 1 }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _playlistDownloadProgress.update { it - batchId }
                    activePlaylistJobs.remove(batchId)
                    _storageTrigger.update { it + 1 }
                }
            }
            activePlaylistJobs[batchId] = batchJob
        }

        fun downloadTrack(track: Track) {
            if (activeJobs.containsKey(track.id)) return
            scope.launch {
                val existing = database.downloadDao().getTrack(track.id)
                if (existing != null && existing.localAudioPath.isNotEmpty()) { return@launch }
                startDownloadJob(track, null)
            }
        }

        private fun startDownloadJob(track: Track, subFolderName: String? = null): Job {
            val job = scope.launch {
                var tempAudioFile: File? = null
                var tempImageFile: File? = null
                var taggedAudioFile: File? = null

                try {
                    _downloadProgress.update { it + (track.id to 0) }

                    val resolvedStream = StreamResolver.resolveStreamWithDrm(context, track, forDownload = true)
                    val streamUrl = resolvedStream?.url
                    val licenseAuthToken = resolvedStream?.licenseAuthToken

                    if (streamUrl == null) {
                        Log.e("DownloadManager", "Failed to resolve stream URL for track: ${track.title}")
                        throw Exception("Cannot resolve stream URL for download")
                    }

                    val isHlsStream = streamUrl.contains(".m3u8") || streamUrl.contains("hls")

                    if (isHlsStream) {
                        val internalArtFile = File(context.filesDir, "art_${track.id}.jpg")
                        tempImageFile = File(context.cacheDir, "temp_art_${track.id}.jpg")
                        downloadFileToStream(track.fullResArtwork, FileOutputStream(tempImageFile)) { _ -> }
                        if (tempImageFile.exists()) {
                            tempImageFile.copyTo(internalArtFile, overwrite = true)
                        }

                        val cache = ExoCacheManager.getCache(context)
                        val upstreamFactory = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                            .setUserAgent(com.alananasss.kittytune.utils.Config.USER_AGENT)
                            .setAllowCrossProtocolRedirects(true)

                        val cacheDataSourceFactory = androidx.media3.datasource.cache.CacheDataSource.Factory()
                            .setCache(cache)
                            .setUpstreamDataSourceFactory(upstreamFactory)
                            .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_BLOCK_ON_CACHE)

                        val downloader = androidx.media3.exoplayer.hls.offline.HlsDownloader(
                            androidx.media3.common.MediaItem.fromUri(streamUrl),
                            cacheDataSourceFactory,
                            java.util.concurrent.Executors.newSingleThreadExecutor()
                        )

                        downloader.download { contentLength, bytesDownloaded, percentDownloaded ->
                            if (isActive) {
                                _downloadProgress.update { c -> c + (track.id to percentDownloaded.toInt()) }
                            }
                        }

                        val existingTrack = database.downloadDao().getTrack(track.id)
                        val creationTimestamp = existingTrack?.downloadedAt ?: System.currentTimeMillis()

                        var persistableToken = licenseAuthToken

                        // Replicating SoundCloud's PersistableLicensesRepository logic
                        if (!licenseAuthToken.isNullOrEmpty()) {
                            try {
                                val callback = SoundCloudDrmCallback(licenseAuthToken, isOffline = true)
                                val drmSessionManagerProvider = androidx.media3.exoplayer.drm.DefaultDrmSessionManagerProvider()
                                drmSessionManagerProvider.setDrmHttpDataSourceFactory(androidx.media3.datasource.DefaultHttpDataSource.Factory())

                                val formatBuilder = androidx.media3.common.Format.Builder()

                                // Fetch and parse the HLS playlist to extract the DRM init data (PSSH)
                                val dataSource = upstreamFactory.createDataSource()
                                val dataSpec = androidx.media3.datasource.DataSpec(android.net.Uri.parse(streamUrl))
                                val inputStream = androidx.media3.datasource.DataSourceInputStream(dataSource, dataSpec)
                                val parser = androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser()

                                val playlist = parser.parse(dataSpec.uri, inputStream)
                                if (playlist is androidx.media3.exoplayer.hls.playlist.HlsMediaPlaylist) {
                                    val firstSegment = playlist.segments.firstOrNull()
                                    if (firstSegment != null && firstSegment.drmInitData != null) {
                                        formatBuilder.setDrmInitData(firstSegment.drmInitData)
                                    }
                                }
                                inputStream.close()

                                val format = formatBuilder.build()

                                if (format.drmInitData != null) {
                                    val drmSessionManager = androidx.media3.exoplayer.drm.DefaultDrmSessionManager.Builder()
                                        .setUuidAndExoMediaDrmProvider(androidx.media3.common.C.WIDEVINE_UUID, androidx.media3.exoplayer.drm.FrameworkMediaDrm.DEFAULT_PROVIDER)
                                        .build(callback)

                                    val offlineLicenseHelper = androidx.media3.exoplayer.drm.OfflineLicenseHelper(
                                        drmSessionManager,
                                        androidx.media3.exoplayer.drm.DrmSessionEventListener.EventDispatcher()
                                    )
                                    val keySetId = offlineLicenseHelper.downloadLicense(format)
                                    if (keySetId != null) {
                                        persistableToken = android.util.Base64.encodeToString(keySetId, android.util.Base64.NO_WRAP)
                                        Log.d("DownloadManager", "Offline Widevine License downloaded and stored successfully! keySetId=$persistableToken")
                                    }
                                    offlineLicenseHelper.release()
                                } else {
                                    Log.w("DownloadManager", "Could not extract DRM format from manifest for offline license")
                                }
                            } catch (e: Exception) {
                                Log.e("DownloadManager", "Failed to download offline DRM license: ${e.message}")
                            }
                        }

                        val exoPath = "exo_cache://${track.id}::${streamUrl}::${persistableToken ?: ""}"

                        val localTrack = LocalTrack(
                            id = track.id,
                            title = track.title ?: context.getString(R.string.untitled_track),
                            artist = track.user?.username ?: context.getString(R.string.unknown_artist),
                            artworkUrl = track.fullResArtwork,
                            duration = track.durationMs ?: 0L,
                            localAudioPath = exoPath,
                            localArtworkPath = internalArtFile.absolutePath,
                            downloadedAt = creationTimestamp
                        )

                        val dao = database.downloadDao()
                        if (existingTrack == null) {
                            dao.insertTrack(localTrack)
                        } else {
                            dao.updateTrack(localTrack)
                        }

                        _storageTrigger.update { it + 1 }
                        withContext(Dispatchers.Main) {
                            AchievementManager.increment("download_100")
                            AchievementManager.increment("download_1000")
                        }

                        try {
                            tempImageFile.delete()
                        } catch (e: Exception) {}

                        _downloadProgress.update { it - track.id }
                        activeJobs.remove(track.id)
                        return@launch
                    }

                    val isYoutubeStream = streamUrl.contains("googlevideo.com") || track.source == "youtube"
                    val ext = if (isYoutubeStream) "m4a" else "mp3"
                    val mime = if (isYoutubeStream) "audio/mp4" else "audio/mpeg"

                    tempAudioFile = File(context.cacheDir, "temp_${track.id}.$ext")
                    tempImageFile = File(context.cacheDir, "temp_art_${track.id}.jpg")
                    taggedAudioFile = File(context.cacheDir, "tagged_${track.id}.$ext")
                    val internalArtFile = File(context.filesDir, "art_${track.id}.jpg")

                    downloadFileToStream(streamUrl, FileOutputStream(tempAudioFile)) { p ->
                        if (isActive) {
                            _downloadProgress.update { c -> c + (track.id to p) }
                        }
                    }
                    downloadFileToStream(track.fullResArtwork, FileOutputStream(tempImageFile)) { _ -> }

                    if (tempImageFile.exists()) {
                        tempImageFile.copyTo(internalArtFile, overwrite = true)
                    }

                    if (ext == "mp3") {
                        try {
                            val mp3file = Mp3File(tempAudioFile)
                            val id3v2Tag = if (mp3file.hasId3v2Tag()) mp3file.id3v2Tag else ID3v24Tag()
                            mp3file.id3v2Tag = id3v2Tag
                            id3v2Tag.title = track.title ?: context.getString(R.string.untitled_track)
                            id3v2Tag.artist = track.user?.username ?: context.getString(R.string.unknown_artist)
                            id3v2Tag.album = if(subFolderName != null) subFolderName else context.getString(R.string.app_name)
                            id3v2Tag.comment = context.getString(R.string.download_comment)
                            val imageBytes = tempImageFile.readBytes()
                            id3v2Tag.setAlbumImage(imageBytes, "image/jpeg")
                            mp3file.save(taggedAudioFile.absolutePath)
                        } catch (e: Exception) {
                            tempAudioFile.copyTo(taggedAudioFile, overwrite = true)
                        }
                    } else {
                        tempAudioFile.copyTo(taggedAudioFile, overwrite = true)
                    }

                    val cleanArtist = sanitizeFilename(track.user?.username ?: context.getString(R.string.generic_artist))
                    val cleanTitle = sanitizeFilename(track.title ?: context.getString(R.string.generic_title))
                    val finalFileName = "$cleanArtist - $cleanTitle.$ext"

                    val (audioStream, audioPath) = getOutputStreamForFile(finalFileName, mime, subFolderName)

                    FileInputStream(taggedAudioFile).use { input ->
                        audioStream.use { output -> input.copyTo(output) }
                    }

                    val existingTrack = database.downloadDao().getTrack(track.id)
                    val creationTimestamp = existingTrack?.downloadedAt ?: System.currentTimeMillis()

                    val localTrack = LocalTrack(
                        id = track.id,
                        title = track.title ?: context.getString(R.string.untitled_track),
                        artist = track.user?.username ?: context.getString(R.string.unknown_artist),
                        artworkUrl = track.fullResArtwork,
                        duration = track.durationMs ?: 0L,
                        localAudioPath = audioPath,
                        localArtworkPath = internalArtFile.absolutePath,
                        downloadedAt = creationTimestamp
                    )

                    val dao = database.downloadDao()
                    if (existingTrack == null) {
                        dao.insertTrack(localTrack)
                    } else {
                        dao.updateTrack(localTrack)
                    }

                    _storageTrigger.update { it + 1 }

                    withContext(Dispatchers.Main) {
                        AchievementManager.increment("download_100")
                        AchievementManager.increment("download_1000")
                    }

                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        tempAudioFile?.let { if (it.exists()) it.delete() }
                        tempImageFile?.let { if (it.exists()) it.delete() }
                        taggedAudioFile?.let { if (it.exists()) it.delete() }
                    } catch (e: Exception) {}

                    _downloadProgress.update { it - track.id }
                    activeJobs.remove(track.id)
                }
            }
            activeJobs[track.id] = job
            return job
        }

        private suspend fun downloadFileToStream(url: String, outputStream: OutputStream, onProgress: (Int) -> Unit) {
            val headRequest = Request.Builder()
                .url(url)
                .header("User-Agent", com.alananasss.kittytune.utils.Config.USER_AGENT)
                .header("Range", "bytes=0-0")
                .build()

            var contentLength = -1L
            var acceptRanges = false

            try {
                client.newCall(headRequest).execute().use { response ->
                    if (response.isSuccessful || response.code == 206) {
                        val contentRange = response.header("Content-Range")
                        if (contentRange != null && contentRange.contains("/")) {
                            contentLength = contentRange.substringAfter("/").toLongOrNull() ?: -1L
                        }
                        acceptRanges = response.code == 206 || response.header("Accept-Ranges") == "bytes"
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (contentLength <= 0 || !acceptRanges) {
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", com.alananasss.kittytune.utils.Config.USER_AGENT)
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("HTTP Error ${response.code}")
                    val body = response.body ?: throw Exception(context.getString(R.string.error_empty_body))
                    val total = body.contentLength()

                    body.byteStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var copied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            outputStream.write(buffer, 0, read)
                            copied += read
                            if (total > 0) onProgress(((copied * 100) / total).toInt())
                        }
                        outputStream.flush()
                    }
                }
                return
            }

            val numThreads = 3
            val chunkSize = contentLength / numThreads
            val tempFiles = Array(numThreads) { File(context.cacheDir, "chunk_${System.currentTimeMillis()}_$it.tmp") }
            var totalCopied = 0L

            coroutineScope {
                val jobs = (0 until numThreads).map { i ->
                    async(Dispatchers.IO) {
                        val startByte = i * chunkSize
                        val endByte = if (i == numThreads - 1) contentLength - 1 else (startByte + chunkSize - 1)

                        val chunkRequest = Request.Builder()
                            .url(url)
                            .header("User-Agent", com.alananasss.kittytune.utils.Config.USER_AGENT)
                            .header("Range", "bytes=$startByte-$endByte")
                            .build()

                        client.newCall(chunkRequest).execute().use { response ->
                            if (!response.isSuccessful) throw Exception("HTTP Error ${response.code}")
                            val body = response.body ?: throw Exception("Empty body in chunk $i")

                            tempFiles[i].outputStream().use { fileOut ->
                                val buffer = ByteArray(32 * 1024)
                                var read: Int
                                val input = body.byteStream()

                                while (input.read(buffer).also { read = it } >= 0) {
                                    fileOut.write(buffer, 0, read)

                                    synchronized(this@DownloadManager) {
                                        totalCopied += read
                                        onProgress(((totalCopied * 100) / contentLength).toInt())
                                    }
                                }
                            }
                        }
                    }
                }
                jobs.awaitAll()
            }

            for (tempFile in tempFiles) {
                if (tempFile.exists()) {
                    tempFile.inputStream().use { input ->
                        input.copyTo(outputStream, 64 * 1024)
                    }
                    tempFile.delete()
                }
            }
            outputStream.flush()
        }

        fun deleteTrack(trackId: Long) {
            scope.launch {
                val dao = database.downloadDao()
                val track = dao.getTrack(trackId)
                if (track != null) {
                    deleteFileByPath(track.localAudioPath)
                    deleteFileByPath(track.localArtworkPath)
                    val refCount = dao.getPlaylistRefCount(trackId)
                    if (refCount > 0) {
                        dao.updateTrack(track.copy(localAudioPath = "", localArtworkPath = ""))
                    } else {
                        dao.deleteTrack(trackId)
                    }
                }
                dao.cleanUnreferencedEmptyTracks()
                _storageTrigger.update { it + 1 }
                _libraryUpdated.tryEmit(Unit)
            }
        }

        fun cancelDownload(trackId: Long) {
            activeJobs[trackId]?.cancel()
            activeJobs.remove(trackId)
            _downloadProgress.update { it - trackId }
            try { 
                context.cacheDir.listFiles { file -> file.name.contains("${trackId}") }?.forEach { it.delete() }
            } catch(e: Exception){}
            _storageTrigger.update { it + 1 }
            _libraryUpdated.tryEmit(Unit)
        }

        fun isPlaylistDownloading(playlistId: Long): Boolean = activePlaylistJobs.containsKey(playlistId)
        fun isTrackDownloading(trackId: Long): Boolean = activeJobs.containsKey(trackId)
        suspend fun getLocalTrack(id: Long): LocalTrack? = database.downloadDao().getTrack(id)

        fun addTracksToPlaylistBulk(playlistId: Long, tracks: List<Track>) {
            scope.launch {
                val dao = database.downloadDao()

                tracks.forEach { track ->
                    val existingTrack = dao.getTrack(track.id)
                    if (existingTrack == null) {
                        val localTrack = LocalTrack(
                            id = track.id,
                            title = track.title ?: context.getString(R.string.untitled_track),
                            artist = track.user?.username ?: context.getString(R.string.unknown_artist),
                            artworkUrl = track.fullResArtwork,
                            duration = track.durationMs ?: 0L,
                            localAudioPath = "",
                            localArtworkPath = ""
                        )
                        dao.insertTrack(localTrack)
                    }
                    dao.insertPlaylistTrackRef(PlaylistTrackCrossRef(playlistId, track.id))
                }

                val playlist = dao.getPlaylist(playlistId)
                if (playlist != null) {
                    val finalTrackCount = dao.getTracksForPlaylistSync(playlistId).size
                    dao.updatePlaylist(playlist.copy(trackCount = finalTrackCount))
                }

                if (playlistId > 0 && !TokenManager(context).isGuestMode()) {
                    try {
                        val onlinePlaylist = api.getPlaylist(playlistId)
                        val trackIds = appendMissingTrackIds(
                            existingTrackIds = (onlinePlaylist.tracks ?: emptyList()).map { it.id },
                            newTrackIds = tracks.map { it.id }
                        )
                        val request = playlistUpdateRequest(onlinePlaylist, trackIds)
                        updateRemotePlaylist(playlistId, request)
                    } catch (e: Exception) { e.printStackTrace() }
                }
                _storageTrigger.update { it + 1 }
                _libraryUpdated.tryEmit(Unit)
            }
        }
        fun cancelBatch(batchId: Long) {
            val job = activePlaylistJobs[batchId]
            if (job != null) {
                job.cancel()
                activePlaylistJobs.remove(batchId)
                _playlistDownloadProgress.update { it - batchId }
            }
            batchTrackIds[batchId]?.forEach { trackId ->
                cancelDownload(trackId)
            }
            batchTrackIds.remove(batchId)
            _storageTrigger.update { it + 1 }
            _libraryUpdated.tryEmit(Unit)
        }
    }

