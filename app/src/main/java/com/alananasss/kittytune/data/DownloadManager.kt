    package com.alananasss.kittytune.data
    
    import android.content.Context
    import android.net.Uri
    import android.util.Log
    import androidx.documentfile.provider.DocumentFile
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.local.*
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.data.network.SoundCloudApi
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
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
    
        private val _downloadProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
        val downloadProgress = _downloadProgress.asStateFlow()
    
        private val _playlistDownloadProgress = MutableStateFlow<Map<Long, Float>>(emptyMap())
        val playlistDownloadProgress = _playlistDownloadProgress.asStateFlow()
    
        private val _storageTrigger = MutableStateFlow(0)
        val storageTrigger = _storageTrigger.asStateFlow()
    
        private val _libraryUpdated = MutableSharedFlow<Unit>(replay = 1)
        val libraryUpdated = _libraryUpdated.asSharedFlow()
    
        lateinit var downloadedIds: StateFlow<Set<Long>>
    
        private val activeJobs = mutableMapOf<Long, Job>()
        private val activePlaylistJobs = mutableMapOf<Long, Job>()
    
        fun init(ctx: Context) {
            context = ctx.applicationContext
            database = AppDatabase.getDatabase(context)
            prefs = PlayerPreferences(context)
    
            downloadedIds = database.downloadDao().getAllTracks()
                .map { list -> list.map { it.id }.toSet() }
                .stateIn(scope = scope, started = SharingStarted.Eagerly, initialValue = emptySet())
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
    
        fun createUserPlaylist(name: String): Long {
            val newId = -(System.currentTimeMillis())
            val playlist = LocalPlaylist(id = newId, title = name, artist = context.getString(R.string.me_artist), artworkUrl = "", trackCount = 0, isUserCreated = true)
            scope.launch { database.downloadDao().insertPlaylist(playlist) }
            return newId
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
            }
        }
        fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
            scope.launch {
                val dao = database.downloadDao()
                dao.removeTrackFromPlaylist(playlistId, trackId)
                val playlist = dao.getPlaylist(playlistId)
                if (playlist != null) dao.updatePlaylist(playlist.copy(trackCount = (playlist.trackCount - 1).coerceAtLeast(0)))
            }
        }
        fun swapTrackOrder(playlistId: Long, trackId1: Long, trackId2: Long) {
            scope.launch {
                val dao = database.downloadDao()
                val ref1 = dao.getRef(playlistId, trackId1); val ref2 = dao.getRef(playlistId, trackId2)
                if (ref1 != null && ref2 != null) { dao.updatePlaylistTrackRef(ref1.copy(addedAt = ref2.addedAt)); dao.updatePlaylistTrackRef(ref2.copy(addedAt = ref1.addedAt)) }
            }
        }
        fun updatePlaylistCover(playlistId: Long, uri: Uri) {
            scope.launch { try { val inputStream = context.contentResolver.openInputStream(uri); val file = File(context.filesDir, "playlist_cover_${playlistId}.jpg"); inputStream?.use { input -> FileOutputStream(file).use { output -> input.copyTo(output) } }; val playlist = database.downloadDao().getPlaylist(playlistId); if (playlist != null) database.downloadDao().updatePlaylist(playlist.copy(localCoverPath = file.absolutePath)) } catch (e: Exception) { e.printStackTrace() } }
        }
        fun renamePlaylist(playlistId: Long, newTitle: String) { scope.launch { database.downloadDao().updatePlaylistTitle(playlistId, newTitle) } }
        fun getAllPlaylistsFlow() = database.downloadDao().getAllPlaylists()
        fun getUserPlaylistsFlow() = database.downloadDao().getUserPlaylists()
        fun isPlaylistInLibraryFlow(playlistId: Long) = database.downloadDao().getPlaylistFlow(playlistId)
    
        fun importPlaylistToLibrary(playlist: Playlist, tracks: List<Track>) {
            scope.launch {
                val dao = database.downloadDao()
                val baseTime = System.currentTimeMillis()
    
                val localPlaylist = LocalPlaylist(
                    id = playlist.id,
                    title = playlist.title ?: context.getString(R.string.untitled_track),
                    artist = playlist.user?.username ?: context.getString(R.string.unknown_artist),
                    artworkUrl = playlist.fullResArtwork,
                    trackCount = tracks.size,
                    isUserCreated = false,
                    permalinkUrl = playlist.permalinkUrl
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
    
        fun deletePlaylist(playlistId: Long) {
            scope.launch {
                val dao = database.downloadDao()
    
                val playlistToDelete = dao.getPlaylist(playlistId)
    
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
    
                dao.deletePlaylist(playlistId)
                dao.deletePlaylistRefs(playlistId)
                HistoryRepository.removeFromHistory(playlistId)
    
                val orphans = dao.getOrphanTracksList()
                orphans.forEach { track ->
                    deleteFileByPath(track.localAudioPath)
                    deleteFileByPath(track.localArtworkPath)
                    dao.deleteTrack(track.id)
                }
    
                _storageTrigger.update { it + 1 }
            }
        }
    
        fun removePlaylistDownloads(playlistId: Long) {
            scope.launch {
                val localTracks = database.downloadDao().getTracksForPlaylistSync(playlistId)
                val domainTracks = localTracks.map { local ->
                    Track(
                        id = local.id,
                        title = local.title,
                        artworkUrl = local.artworkUrl,
                        durationMs = local.duration,
                        user = User(0, local.artist, null)
                    )
                }
                removeDownloads(domainTracks)
            }
        }
    
        fun removeDownloads(tracks: List<Track>) {
            scope.launch {
                tracks.forEach { track ->
                    val local = database.downloadDao().getTrack(track.id)
                    if (local != null) {
                        deleteFileByPath(local.localAudioPath)
                        deleteFileByPath(local.localArtworkPath)
                        database.downloadDao().updateTrack(local.copy(localAudioPath = "", localArtworkPath = ""))
                    }
                }
                _storageTrigger.update { it + 1 }
            }
        }
    
        fun toggleSaveArtist(user: User) { scope.launch { val dao = database.downloadDao(); if (dao.getArtist(user.id) != null) dao.deleteArtist(user.id) else dao.insertArtist(LocalArtist(user.id, user.username ?: context.getString(R.string.menu_go_artist), user.avatarUrl ?: "", user.trackCount)) } }
        fun isArtistSavedFlow(artistId: Long) = database.downloadDao().getArtistFlow(artistId)
        fun getSavedArtists() = database.downloadDao().getAllSavedArtists()
    
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
                val tempAudioFile = File(context.cacheDir, "temp_${track.id}.mp3")
                val tempImageFile = File(context.cacheDir, "temp_art_${track.id}.jpg")
                val taggedAudioFile = File(context.cacheDir, "tagged_${track.id}.mp3")
                val internalArtFile = File(context.filesDir, "art_${track.id}.jpg")
    
                try {
                    _downloadProgress.update { it + (track.id to 0) }
    
                    val streamUrl = StreamResolver.resolveStream(context, track)
    
                    if (streamUrl == null) {
                        Log.e("DownloadManager", "Failed to resolve stream URL for track: ${track.title}")
                        return@launch
                    }
    
                    downloadFileToStream(streamUrl, FileOutputStream(tempAudioFile)) { p ->
                        _downloadProgress.update { c -> c + (track.id to p) }
                    }
                    downloadFileToStream(track.fullResArtwork, FileOutputStream(tempImageFile)) { _ -> }
    
                    if (tempImageFile.exists()) {
                        tempImageFile.copyTo(internalArtFile, overwrite = true)
                    }
    
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
    
                    val cleanArtist = sanitizeFilename(track.user?.username ?: context.getString(R.string.generic_artist))
                    val cleanTitle = sanitizeFilename(track.title ?: context.getString(R.string.generic_title))
                    val finalFileName = "$cleanArtist - $cleanTitle.mp3"
    
                    val (audioStream, audioPath) = getOutputStreamForFile(finalFileName, "audio/mpeg", subFolderName)
    
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
                        if (tempAudioFile.exists()) tempAudioFile.delete()
                        if (tempImageFile.exists()) tempImageFile.delete()
                        if (taggedAudioFile.exists()) taggedAudioFile.delete()
                    } catch (e: Exception) {}
    
                    _downloadProgress.update { it - track.id }
                    activeJobs.remove(track.id)
                }
            }
            activeJobs[track.id] = job
            return job
        }
    
        private fun downloadFileToStream(url: String, outputStream: OutputStream, onProgress: (Int) -> Unit) {
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            val body = response.body ?: throw Exception(context.getString(R.string.error_empty_body))
            val total = body.contentLength()
    
            body.byteStream().use { input ->
                outputStream.use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var copied = 0L
                    var read: Int
                    while (input.read(buffer).also { read = it } >= 0) {
                        output.write(buffer, 0, read)
                        copied += read
                        if (total > 0) onProgress(((copied * 100) / total).toInt())
                    }
                    output.flush()
                }
            }
        }
    
        fun deleteTrack(trackId: Long) {
            scope.launch {
                val track = database.downloadDao().getTrack(trackId)
                if (track != null) {
                    deleteFileByPath(track.localAudioPath)
                    deleteFileByPath(track.localArtworkPath)
                    database.downloadDao().updateTrack(track.copy(localAudioPath = "", localArtworkPath = ""))
                }
                _storageTrigger.update { it + 1 }
            }
        }
    
        fun cancelDownload(trackId: Long) {
            activeJobs[trackId]?.cancel()
            activeJobs.remove(trackId)
            _downloadProgress.update { it - trackId }
            try { File(context.cacheDir, "temp_${trackId}.mp3").delete() } catch(e: Exception){}
            _storageTrigger.update { it + 1 }
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
            }
        }
        fun cancelBatch(batchId: Long) {
            val job = activePlaylistJobs[batchId]
            if (job != null) {
                job.cancel()
                activePlaylistJobs.remove(batchId)
                _playlistDownloadProgress.update { it - batchId }
                _storageTrigger.update { it + 1 }
            }
        }
    }


