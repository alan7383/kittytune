package com.alananasss.kittytune.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.alananasss.kittytune.data.local.*
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.utils.Config
import com.mpatric.mp3agic.ID3v24Tag
import com.mpatric.mp3agic.Mp3File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

object DownloadManager {
    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefs: PlayerPreferences

    private val api: SoundCloudApi by lazy { RetrofitClient.create(context) }
    private val scope = CoroutineScope(Dispatchers.IO)
    private val client = OkHttpClient()

    private val _downloadProgress = MutableStateFlow<Map<Long, Int>>(emptyMap())
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _storageTrigger = MutableStateFlow(0)
    val storageTrigger = _storageTrigger.asStateFlow()

    private val activeJobs = mutableMapOf<Long, Job>()

    fun init(ctx: Context) {
        context = ctx.applicationContext
        database = AppDatabase.getDatabase(context)
        prefs = PlayerPreferences(context)
    }

    // --- UTILITY FILE NAME ---
    private fun sanitizeFilename(name: String): String {
        // Remplace les caractères interdits par des underscores
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
    }

    // --- FILE MANAGEMENT (Internal vs External) ---
    private fun getOutputStreamForFile(fileName: String, mimeType: String): Pair<OutputStream, String> {
        val customUriStr = prefs.getDownloadLocation()

        if (customUriStr != null) {
            try {
                val treeUri = Uri.parse(customUriStr)
                val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
                    ?: throw Exception("Impossible d'accéder au dossier externe")

                // If the file already exists, we delete it and recreate it properly
                val existing = rootDoc.findFile(fileName)
                if (existing != null) existing.delete()

                val targetDoc = rootDoc.createFile(mimeType, fileName)
                    ?: throw Exception("Impossible de créer le fichier externe")

                val stream = context.contentResolver.openOutputStream(targetDoc.uri)
                    ?: throw Exception("Impossible d'ouvrir le flux externe")

                return Pair(stream, targetDoc.uri.toString())
            } catch (e: Exception) {
                e.printStackTrace()
                throw e
            }
        } else {
            // Public internal storage (App's Music folder or FilesDir)
            val file = File(context.filesDir, fileName)
            return Pair(FileOutputStream(file), file.absolutePath)
        }
    }

    private fun deleteFileByPath(path: String) {
        if (path.isEmpty()) return

        try {
            if (path.startsWith("content://")) {
                val uri = Uri.parse(path)
                DocumentFile.fromSingleUri(context, uri)?.delete()
            } else {
                val file = File(path)
                if (file.exists()) file.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // --- CLEANING ---
    fun removeAllContent(includeAudio: Boolean, includeImages: Boolean) {
        scope.launch {
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

                if (changed) {
                    database.downloadDao().updateTrack(updatedTrack)
                }
            }
            _storageTrigger.update { it + 1 }
        }
    }

    // --- PLAYLISTS & DB ---
    fun createUserPlaylist(name: String): Long {
        val newId = -(System.currentTimeMillis())
        val playlist = LocalPlaylist(id = newId, title = name, artist = "Moi", artworkUrl = "", trackCount = 0, isUserCreated = true)
        scope.launch { database.downloadDao().insertPlaylist(playlist) }
        return newId
    }
    fun addTrackToPlaylist(playlistId: Long, track: Track) {
        scope.launch {
            val dao = database.downloadDao()
            val existingTrack = dao.getTrack(track.id)
            if (existingTrack == null) {
                val localTrack = LocalTrack(id = track.id, title = track.title ?: "Sans titre", artist = track.user?.username ?: "Inconnu", artworkUrl = track.fullResArtwork, duration = track.durationMs ?: 0L, localAudioPath = "", localArtworkPath = "")
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
            val localPlaylist = LocalPlaylist(id = playlist.id, title = playlist.title ?: "Sans titre", artist = playlist.user?.username ?: "Inconnu", artworkUrl = playlist.fullResArtwork, trackCount = tracks.size, isUserCreated = false)
            database.downloadDao().insertPlaylist(localPlaylist)
            tracks.forEach { addTrackToPlaylist(playlist.id, it) }
        }
    }
    fun deletePlaylist(playlistId: Long) {
        scope.launch {
            val dao = database.downloadDao(); dao.deletePlaylist(playlistId); dao.deletePlaylistRefs(playlistId); HistoryRepository.removeFromHistory(playlistId)
            val orphans = dao.getOrphanTracksList()
            orphans.forEach { track -> deleteFileByPath(track.localAudioPath); deleteFileByPath(track.localArtworkPath); dao.deleteTrack(track.id) }
            _storageTrigger.update { it + 1 }
        }
    }
    fun removePlaylistDownloads(playlistId: Long) {
        scope.launch {
            val tracks = database.downloadDao().getTracksForPlaylistSync(playlistId)
            tracks.forEach { track -> deleteFileByPath(track.localAudioPath); deleteFileByPath(track.localArtworkPath); database.downloadDao().updateTrack(track.copy(localAudioPath = "", localArtworkPath = "")) }
            _storageTrigger.update { it + 1 }
        }
    }
    fun toggleSaveArtist(user: User) { scope.launch { val dao = database.downloadDao(); if (dao.getArtist(user.id) != null) dao.deleteArtist(user.id) else dao.insertArtist(LocalArtist(user.id, user.username ?: "Artiste", user.avatarUrl ?: "", user.trackCount)) } }
    fun isArtistSavedFlow(artistId: Long) = database.downloadDao().getArtistFlow(artistId)
    fun getSavedArtists() = database.downloadDao().getAllSavedArtists()
    fun downloadPlaylist(playlist: Playlist, tracks: List<Track>) { importPlaylistToLibrary(playlist, tracks); scope.launch { tracks.forEach { downloadTrack(it) } } }

    fun downloadTrack(track: Track) {
        if (activeJobs.containsKey(track.id)) return
        scope.launch {
            val existing = database.downloadDao().getTrack(track.id)
            if (existing != null && existing.localAudioPath.isNotEmpty()) { return@launch }
            startDownloadJob(track)
        }
    }

    // --- HEART OF THE DOWNLOAD WITH METADATA & PROPER NAME ---
    private fun startDownloadJob(track: Track) {
        val job = scope.launch {
            // Temporary files in the cache (invisible to the user)
            val tempAudioFile = File(context.cacheDir, "temp_${track.id}.mp3")
            val tempImageFile = File(context.cacheDir, "temp_art_${track.id}.jpg")
            val taggedAudioFile = File(context.cacheDir, "tagged_${track.id}.mp3")

            // Internal image file (For display in the app only)
            val internalArtFile = File(context.filesDir, "art_${track.id}.jpg")

            try {
                _downloadProgress.update { it + (track.id to 0) }
                val streamUrl = resolveStreamUrl(track)

                // 1. Download the raw audio temporarily
                downloadFileToStream(streamUrl, FileOutputStream(tempAudioFile)) { p ->
                    _downloadProgress.update { c -> c + (track.id to p) }
                }

                // 2. Download the raw image temporarily
                downloadFileToStream(track.fullResArtwork, FileOutputStream(tempImageFile)) { _ -> }

                // 3. Save the image INTERNALLY for the app (to avoid cluttering the gallery)
                if (tempImageFile.exists()) {
                    tempImageFile.copyTo(internalArtFile, overwrite = true)
                }

                // 4. INJECTION OF METADATA (ID3 Tags) via mp3agic
                try {
                    val mp3file = Mp3File(tempAudioFile)
                    val id3v2Tag = if (mp3file.hasId3v2Tag()) mp3file.id3v2Tag else ID3v24Tag()
                    mp3file.id3v2Tag = id3v2Tag

                    // Metadata
                    id3v2Tag.title = track.title ?: "Titre Inconnu"
                    id3v2Tag.artist = track.user?.username ?: "Artiste Inconnu"
                    id3v2Tag.album = "SoundTune"
                    id3v2Tag.comment = "Téléchargé avec KittyTune"

                    // Injecting the album art into the MP3
                    val imageBytes = tempImageFile.readBytes()
                    id3v2Tag.setAlbumImage(imageBytes, "image/jpeg")

                    mp3file.save(taggedAudioFile.absolutePath)
                } catch (e: Exception) {
                    Log.e("DownloadManager", "Erreur tagging ID3: ${e.message}")
                    tempAudioFile.copyTo(taggedAudioFile, overwrite = true)
                }

                // 5. Copy to final destination (Public)

                // Creating a clean filename: "Artist - Title.mp3"
                val cleanArtist = sanitizeFilename(track.user?.username ?: "Artiste")
                val cleanTitle = sanitizeFilename(track.title ?: "Titre")
                val finalFileName = "$cleanArtist - $cleanTitle.mp3"

                val (audioStream, audioPath) = getOutputStreamForFile(finalFileName, "audio/mpeg")

                // Copy of the final (tagged) file to user storage
                FileInputStream(taggedAudioFile).use { input ->
                    audioStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val localTrack = LocalTrack(
                    id = track.id,
                    title = track.title ?: "?",
                    artist = track.user?.username ?: "?",
                    artworkUrl = track.fullResArtwork,
                    duration = track.durationMs ?: 0L,
                    localAudioPath = audioPath,
                    localArtworkPath = internalArtFile.absolutePath // Chemin interne caché
                )

                val dao = database.downloadDao()
                if (dao.getTrack(track.id) == null) dao.insertTrack(localTrack)
                else dao.updateTrack(localTrack)

                _storageTrigger.update { it + 1 }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Nettoyage cache
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
    }

    private fun downloadFileToStream(url: String, outputStream: OutputStream, onProgress: (Int) -> Unit) {
        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        val body = response.body ?: throw Exception("Body null")
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

    private suspend fun resolveStreamUrl(track: Track): String {
        var trackToUse = track
        if (track.media == null || track.media.transcodings.isNullOrEmpty()) {
            try {
                val fetchedList = api.getTracksByIds(track.id.toString())
                if (fetchedList.isNotEmpty()) trackToUse = fetchedList[0]
                else throw Exception("Impossible de récupérer les infos")
            } catch (e: Exception) { throw Exception("Erreur API: ${e.message}") }
        }
        val tokenManager = TokenManager(context)
        val accessToken = tokenManager.getAccessToken()
        val transcodings = trackToUse.media?.transcodings ?: throw Exception("Pas de média disponible")

        val qualityPref = prefs.getAudioQuality()

        val target = if (qualityPref == "HIGH") {
            transcodings.find { it.format?.protocol == "progressive" }
                ?: transcodings.find { it.format?.protocol == "hls" }
        } else {
            transcodings.find { it.format?.mimeType?.contains("opus") == true }
                ?: transcodings.find { it.format?.protocol == "hls" }
                ?: transcodings.find { it.format?.protocol == "progressive" }
        } ?: throw Exception("Aucun format supporté")

        val urlWithId = if (target.url.contains("?")) "${target.url}&client_id=${Config.CLIENT_ID}" else "${target.url}?client_id=${Config.CLIENT_ID}"
        val builder = Request.Builder().url(urlWithId).header("User-Agent", Config.USER_AGENT)
        if (!accessToken.isNullOrEmpty()) builder.header("Authorization", "OAuth $accessToken")

        val response = client.newCall(builder.build()).execute()
        if (!response.isSuccessful) throw Exception("Erreur Stream: ${response.code}")
        val body = response.body?.string() ?: throw Exception("Réponse vide")
        return JSONObject(body).getString("url")
    }

    fun deleteTrack(trackId: Long) {
        scope.launch {
            val track = database.downloadDao().getTrack(trackId)
            if (track != null) {
                deleteFileByPath(track.localAudioPath)
                deleteFileByPath(track.localArtworkPath) // Supprime l'image cachée interne
                database.downloadDao().updateTrack(track.copy(localAudioPath = "", localArtworkPath = ""))
            }
            _storageTrigger.update { it + 1 }
        }
    }

    fun cancelDownload(trackId: Long) {
        activeJobs[trackId]?.cancel()
        activeJobs.remove(trackId)
        _downloadProgress.update { it - trackId }
        // Nettoyage potentiel
        try { File(context.cacheDir, "temp_${trackId}.mp3").delete() } catch(e: Exception){}
        _storageTrigger.update { it + 1 }
    }

    fun isTrackDownloading(trackId: Long): Boolean = activeJobs.containsKey(trackId)
    suspend fun getLocalTrack(id: Long): LocalTrack? = database.downloadDao().getTrack(id)
}