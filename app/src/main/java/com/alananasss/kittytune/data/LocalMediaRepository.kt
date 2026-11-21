package com.alananasss.kittytune.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import androidx.room.withTransaction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object LocalMediaRepository {

    // observable states for the ui
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress = _scanProgress.asStateFlow()

    suspend fun scanLocalMedia(context: Context) {
        if (!_isScanning.compareAndSet(false, true)) return
        _scanProgress.value = context.getString(R.string.pref_local_scan_status_prep)

        try {
            withContext(Dispatchers.IO) {
                val prefs = PlayerPreferences(context)
                val uriSet = prefs.getLocalMediaUris()

                // check if the user actually selected some folders
                if (uriSet.isEmpty()) {
                    _scanProgress.value = context.getString(R.string.pref_local_scan_status_no_folder)
                    return@withContext
                }

                val database = AppDatabase.getDatabase(context)
                val dao = database.downloadDao()
                val stored = dao.getAllStoredTracksList()
                val usedIds = stored.mapTo(hashSetOf()) { it.id }
                val localFiles = stored.filter { it.isUserLocalMedia }
                    .mapNotNullTo(hashSetOf()) { localMediaIdentity(it.localAudioPath) }
                val downloadedFiles = stored.filterNot { it.isUserLocalMedia }
                    .mapNotNullTo(hashSetOf()) { localMediaIdentity(it.localAudioPath) }
                val seen = hashSetOf<String>()
                val foundFiles = mutableListOf<DocumentFile>()

                _scanProgress.value = context.getString(R.string.pref_local_scan_status_searching)

                // dig through the folders recursively
                uriSet.forEach { uriStr ->
                    try {
                        val treeUri = Uri.parse(uriStr)
                        val rootDir = DocumentFile.fromTreeUri(context, treeUri)
                        if (rootDir != null && rootDir.exists()) {
                            collectAudioFiles(rootDir, foundFiles)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (foundFiles.isEmpty()) {
                    _scanProgress.value = context.getString(R.string.pref_local_scan_status_no_files)
                    return@withContext
                }

                _scanProgress.value = context.getString(R.string.pref_local_scan_status_found, foundFiles.size)

                val tracksToInsert = mutableListOf<LocalTrack>()
                var processed = 0

                // now process each file to get metadata
                foundFiles.forEach { file ->
                    processed++
                    if (processed % 10 == 0) {
                        _scanProgress.value = context.getString(R.string.pref_local_scan_status_analyzing, processed, foundFiles.size)
                    }

                    currentCoroutineContext().ensureActive()
                    val fileKey = localMediaIdentity(file.uri.toString()) ?: return@forEach
                    if (fileKey in downloadedFiles || fileKey in seen) return@forEach
                    if (fileKey in localFiles) return@forEach
                    val track = processFile(context, file) ?: return@forEach
                    seen.add(fileKey)
                    var id = track.id
                    while (!usedIds.add(id)) id--
                    tracksToInsert.add(track.copy(id = id))
                }

                _scanProgress.value = context.getString(R.string.pref_local_scan_status_saving)
                database.withTransaction {
                    tracksToInsert.forEach { track -> dao.insertTrack(track) }
                }
                if (tracksToInsert.isNotEmpty()) {
                    DownloadManager.notifyLibraryUpdated()
                    AudioScannerManager.scanDownloadedTracks(context)
                }

                _scanProgress.value = context.getString(R.string.pref_local_scan_status_done)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            _scanProgress.value = context.getString(R.string.pref_local_scan_status_error, e.message ?: "")
        } finally {
            _isScanning.value = false
        }
    }

    // recursive helper to find audio files
    private fun collectAudioFiles(dir: DocumentFile, list: MutableList<DocumentFile>) {
        dir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                collectAudioFiles(file, list)
            } else {
                val type = file.type ?: ""
                val name = file.name?.lowercase() ?: ""

                val isAudioMime = type.startsWith("audio/")

                // check extensions manually just in case
                val isAudioExtension = name.endsWith(".mp3") ||
                        name.endsWith(".flac") ||
                        name.endsWith(".wav") ||
                        name.endsWith(".m4a") ||
                        name.endsWith(".aac") ||
                        name.endsWith(".ogg") ||
                        name.endsWith(".wma") ||
                        name.endsWith(".opus") ||
                        name.endsWith(".amr") ||
                        name.endsWith(".mp4")

                if (isAudioMime || isAudioExtension) {
                    list.add(file)
                }
            }
        }
    }

    private fun processFile(context: Context, file: DocumentFile): LocalTrack? {
        val uri = file.uri
        val retriever = MediaMetadataRetriever()

        return try {
            retriever.setDataSource(context, uri)

            // extract title, artist, cover art...
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?: file.name?.substringBeforeLast(".")
                ?: context.getString(R.string.untitled_track)

            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: context.getString(R.string.unknown_artist)

            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val duration = durationStr?.toLongOrNull() ?: 0L

            val artworkBytes = retriever.embeddedPicture
            var artworkPath = ""

            // save embedded art to disk so glide can load it
            if (artworkBytes != null) {
                val digest = MessageDigest.getInstance("SHA-256").digest(artworkBytes)
                val fileName = "local_art_${digest.joinToString("") { "%02x".format(it) }}.jpg"
                val artFile = File(context.filesDir, fileName)
                if (!artFile.exists()) {
                    FileOutputStream(artFile).use { it.write(artworkBytes) }
                }
                artworkPath = artFile.absolutePath
            }

            // generate a unique negative id for local tracks
            var id = uri.toString().hashCode().toLong()
            if (id > 0) id *= -1
            if (id == 0L) id = -1L

            LocalTrack(
                id = id,
                title = title,
                artist = artist,
                artworkUrl = artworkPath,
                duration = duration,
                localAudioPath = uri.toString(),
                localArtworkPath = artworkPath,
                source = "local"
            )

        } catch (e: Exception) {
            null
        } finally {
            try { retriever.release() } catch (e: Exception) {}
        }
    }

    fun isLocalTrack(id: Long): Boolean {
        // local tracks have negative ids
        return id < 0 && id > -9000000000000000000
    }
}
