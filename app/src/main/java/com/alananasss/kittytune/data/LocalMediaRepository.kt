package com.alananasss.kittytune.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

object LocalMediaRepository {

    /** Where extracted local covers live, inside the app's files directory. */
    const val LOCAL_ART_DIR = "local_art"
    private const val LOCAL_ART_PREFIX = "local_art_"

    /** Covers never need more pixels than this on any screen the app draws them on. */
    private const val MAX_ART_DIMENSION = 1200

    /** Embedded pictures smaller than this are stored as-is; bigger ones are re-encoded. */
    private const val RAW_ART_KEEP_BYTES = 400_000

    // observable states for the ui
    private val _isScanning = MutableStateFlow(false)
    val isScanning = _isScanning.asStateFlow()

    private val _scanProgress = MutableStateFlow("")
    val scanProgress = _scanProgress.asStateFlow()

    suspend fun scanLocalMedia(context: Context) {
        if (_isScanning.value) return
        _isScanning.value = true
        _scanProgress.value = context.getString(R.string.pref_local_scan_status_prep)

        withContext(Dispatchers.IO) {
            try {
                val prefs = PlayerPreferences(context)
                val uriSet = prefs.getLocalMediaUris()

                // check if the user actually selected some folders
                if (uriSet.isEmpty()) {
                    _scanProgress.value = context.getString(R.string.pref_local_scan_status_no_folder)
                    _isScanning.value = false
                    return@withContext
                }

                val db = AppDatabase.getDatabase(context).downloadDao()

                // 1. Clean up any existing duplicate local rows and normalize their audioKey
                val allStoredTracks = db.getAllStoredTracksList()
                val existingLocal = allStoredTracks.filter { it.source == "local" }

                val groupedLocal = existingLocal
                    .mapNotNull { row ->
                        val canonical = safIdentity(row.localAudioPath) ?: row.audioKey
                        canonical?.let { it to row }
                    }
                    .groupBy({ it.first }, { it.second })

                val dupIds = groupedLocal.flatMap { (_, rows) ->
                    rows.sortedBy { it.id }.dropLast(1).map { it.id }
                }
                if (dupIds.isNotEmpty()) {
                    db.deleteCrossRefsForTracks(dupIds)
                    db.deleteTracksByIds(dupIds)
                }

                groupedLocal.forEach { (canonicalKey, rows) ->
                    val survivor = rows.maxByOrNull { it.id } ?: return@forEach
                    if (survivor.audioKey != canonicalKey) {
                        runCatching { db.updateTrackAudioKey(survivor.id, canonicalKey) }
                    }
                }

                // 2. Also remove local rows that are actually duplicates of tracks downloaded by the app
                val downloadedNonLocal = allStoredTracks.filter {
                    it.source != "local" && it.localAudioPath.isNotEmpty()
                }
                val appDownloadedFileKeys = downloadedNonLocal
                    .mapNotNull { safIdentity(it.localAudioPath) }
                    .toHashSet()
                val appDownloadedMetaKeys = downloadedNonLocal
                    .mapNotNull { metadataKey(it) }
                    .toHashSet()

                val localShadowingDownloads = existingLocal
                    .filter { it.id !in dupIds }
                    .filter { localRow ->
                        val key = safIdentity(localRow.localAudioPath) ?: localRow.audioKey
                        val meta = metadataKey(localRow)
                        (key != null && key in appDownloadedFileKeys) ||
                            (meta != null && meta in appDownloadedMetaKeys)
                    }
                    .map { it.id }

                if (localShadowingDownloads.isNotEmpty()) {
                    db.deleteCrossRefsForTracks(localShadowingDownloads)
                    db.deleteTracksByIds(localShadowingDownloads)
                }

                val removedIds = (dupIds + localShadowingDownloads).toHashSet()
                val remainingTracks = allStoredTracks.filter { it.id !in removedIds }

                // 3. Exclude the app's download folder (works for both /tree/ and /document/ URIs)
                val downloadIdentity = prefs.getDownloadLocation()?.let { safIdentity(it) }

                val knownAudioKeys = buildSet {
                    remainingTracks.forEach { row ->
                        row.audioKey?.let { add(it) }
                        safIdentity(row.localAudioPath)?.let { add(it) }
                    }
                }
                val knownMetaKeys = remainingTracks.mapNotNull { metadataKey(it) }.toHashSet()

                val scanAudioKeys = mutableSetOf<String>()
                val scanMetaKeys = mutableSetOf<String>()
                val foundFiles = mutableListOf<DocumentFile>()

                _scanProgress.value = context.getString(R.string.pref_local_scan_status_searching)

                // dig through the folders recursively, skipping the download folder
                uriSet.forEach { uriStr ->
                    try {
                        val treeIdent = safIdentity(uriStr)
                        if (downloadIdentity != null && treeIdent != null &&
                            (treeIdent == downloadIdentity || treeIdent.startsWith("$downloadIdentity/"))
                        ) {
                            return@forEach
                        }
                        val treeUri = Uri.parse(uriStr)
                        val rootDir = DocumentFile.fromTreeUri(context, treeUri)
                        if (rootDir != null && rootDir.exists()) {
                            collectAudioFiles(rootDir, foundFiles, downloadIdentity)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                if (foundFiles.isEmpty()) {
                    _scanProgress.value = context.getString(R.string.pref_local_scan_status_no_files)
                    _isScanning.value = false
                    return@withContext
                }

                _scanProgress.value = context.getString(R.string.pref_local_scan_status_found, foundFiles.size)

                val tracksToInsert = mutableListOf<LocalTrack>()
                var processed = 0

                // now process each file to get metadata
                foundFiles.forEach { file ->
                    processed++
                    if (processed % 10 == 0) {
                        _scanProgress.value = context.getString(
                            R.string.pref_local_scan_status_analyzing,
                            processed,
                            foundFiles.size
                        )
                    }

                    try {
                        val fileKey = safIdentity(file.uri.toString())
                        if (fileKey != null) {
                            if (fileKey in knownAudioKeys || fileKey in scanAudioKeys) return@forEach
                        }

                        val track = processFile(context, file, fileKey) ?: return@forEach

                        val meta = metadataKey(track)
                        if (meta != null && (meta in knownMetaKeys || meta in scanMetaKeys)) {
                            return@forEach
                        }

                        fileKey?.let { scanAudioKeys.add(it) }
                        meta?.let { scanMetaKeys.add(it) }
                        tracksToInsert.add(track)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                _scanProgress.value = context.getString(R.string.pref_local_scan_status_saving)
                tracksToInsert.forEach { track ->
                    db.insertTrack(track)
                }

                if (tracksToInsert.isNotEmpty() || removedIds.isNotEmpty()) {
                    DownloadManager.notifyLibraryUpdated()
                    AudioScannerManager.scanDownloadedTracks(context)
                }

                _scanProgress.value = context.getString(R.string.pref_local_scan_status_done)
            } catch (e: Exception) {
                e.printStackTrace()
                _scanProgress.value = context.getString(R.string.pref_local_scan_status_error, e.message ?: "")
            } finally {
                _isScanning.value = false
            }
        }
    }

    // recursive helper to find audio files
    private fun collectAudioFiles(
        dir: DocumentFile,
        list: MutableList<DocumentFile>,
        excludedIdentity: String? = null
    ) {
        dir.listFiles()?.forEach { file ->
            val ident = if (excludedIdentity != null) safIdentity(file.uri.toString()) else null
            if (excludedIdentity != null && ident != null &&
                (ident == excludedIdentity || ident.startsWith("$excludedIdentity/"))
            ) {
                return@forEach
            }

            if (file.isDirectory) {
                collectAudioFiles(file, list, excludedIdentity)
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

    private fun processFile(
        context: Context,
        file: DocumentFile,
        precomputedKey: String? = null
    ): LocalTrack? {
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

            // save embedded art to disk so coil/glide can load it
            if (artworkBytes != null) {
                artworkPath = saveEmbeddedArt(context, artworkBytes)
            }

            // generate a deterministic negative id based on the canonical file key
            val canonicalKey = precomputedKey ?: safIdentity(uri.toString())
            var id = (canonicalKey ?: uri.toString()).hashCode().toLong()
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
                source = "local",
                audioKey = canonicalKey
            )
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Saves a track's embedded picture once per distinct image: the file is named after the
     * picture's own digest, so every track of an album shares one file instead of carrying its
     * own multi-megabyte copy. Oversized covers are re-encoded down to [MAX_ART_DIMENSION]
     * pixels, so a large local library no longer costs gigabytes of storage.
     */
    private fun saveEmbeddedArt(context: Context, bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val name = digest.joinToString("") { "%02x".format(it) }
        val artFile = File(File(context.filesDir, LOCAL_ART_DIR), "$LOCAL_ART_PREFIX$name.jpg")
        if (!artFile.exists()) {
            artFile.parentFile?.mkdirs()
            val toWrite = downscaleIfNeeded(bytes)
            FileOutputStream(artFile).use { it.write(toWrite) }
        }
        return artFile.absolutePath
    }

    /** The original bytes when they are already light; a re-encoded, shrunk JPEG otherwise. */
    private fun downscaleIfNeeded(bytes: ByteArray): ByteArray {
        if (bytes.size <= RAW_ART_KEEP_BYTES) return bytes
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching bytes

            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_ART_DIMENSION) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return@runCatching bytes

            val scale = minOf(1f, MAX_ART_DIMENSION.toFloat() / maxOf(bitmap.width, bitmap.height))
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 88, out)
            if (scaled !== bitmap) bitmap.recycle()
            scaled.recycle()
            val reencoded = out.toByteArray()
            if (reencoded.size < bytes.size) reencoded else bytes
        }.getOrDefault(bytes)
    }

    /**
     * Canonical identity of a SAF file or tree URI (`<authority>|<decoded document/tree id>`).
     * Works for both `/document/...` file URIs and `/tree/...` directory URIs, so the same file
     * opened from parent or child folder trees yields the exact same key.
     */
    internal fun safIdentity(uriString: String): String? {
        if (uriString.isBlank()) return null
        if (!uriString.startsWith("content://")) {
            return runCatching { File(uriString).canonicalPath.lowercase() }.getOrNull()
        }
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        val rawDocId = when {
            uriString.contains("/document/") -> uriString.substringAfter("/document/", "")
            uriString.contains("/tree/") -> uriString.substringAfter("/tree/", "").substringBefore("/")
            else -> ""
        }
        if (rawDocId.isEmpty()) return null
        val decoded = runCatching { Uri.decode(rawDocId) }.getOrDefault(rawDocId)
            .trimEnd('/')
            .lowercase()
        val authority = (uri.authority ?: "").lowercase()
        return "$authority|$decoded"
    }

    /** Metadata key for fallback deduplication: `artist|title|durationSeconds`. */
    private fun metadataKey(track: LocalTrack): String? {
        if (track.duration <= 0L) return null
        val title = track.title.trim().lowercase()
        val artist = track.artist.trim().lowercase()
        if (title.isEmpty() || artist.isEmpty()) return null
        return "$artist|$title|${track.duration / 1000L}"
    }

    fun isLocalTrack(id: Long): Boolean {
        // local tracks have negative ids
        return id < 0 && id > -9000000000000000000
    }
}
