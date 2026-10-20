package com.alananasss.kittytune.ui.profile

import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.storage.StorageManager
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.ExoCacheManager
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class StorageViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = PlayerPreferences(context)
    private val dao = AppDatabase.getDatabase(context).downloadDao()

    // global stats
    var usedSpace by mutableLongStateOf(0L)
    var freeSpace by mutableLongStateOf(0L)
    var totalSpace by mutableLongStateOf(0L)

    // detailed breakdown
    var audioSize by mutableLongStateOf(0L)
    var imageSize by mutableLongStateOf(0L)
    var cacheSize by mutableLongStateOf(0L)
    var databaseSize by mutableLongStateOf(0L)
    var appCodeSize by mutableLongStateOf(0L)
    var totalAppSize by mutableLongStateOf(0L)

    // Port desktop StorageDownloadsPage: DB-driven counts + lists.
    // The old code scanned filesDir top-level for *.mp3 only, so a big playlist
    // stored in a sub-folder, in .m4a/.flac, or in exo_cache (HLS/DRM) counted as 0.
    var downloadedCount by mutableIntStateOf(0)
    var downloadedTracks by mutableStateOf<List<LocalTrack>>(emptyList())
    var downloadedPlaylists by mutableStateOf<List<LocalPlaylist>>(emptyList())
    var playlistDownloadedCounts by mutableStateOf<Map<Long, Int>>(emptyMap())
    var playlistSizes by mutableStateOf<Map<Long, Long>>(emptyMap())
    var trackSizes by mutableStateOf<Map<Long, Long>>(emptyMap())

    var currentPath by mutableStateOf(context.getString(R.string.storage_loading))
    var isExternal by mutableStateOf(false)

    init {
        refreshStorageInfo()
        observeDownloads()
    }

    /** Live refresh like desktop's collectAsState on getAllTracks(). */
    private fun observeDownloads() {
        viewModelScope.launch(Dispatchers.IO) {
            launch {
                dao.getAllTracks().collect { list ->
                    downloadedTracks = list
                    downloadedCount = list.size
                    recalculateSizes(list)
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                dao.getDownloadedPlaylists().collect { playlists ->
                    downloadedPlaylists = playlists
                    val counts = mutableMapOf<Long, Int>()
                    val sizes = mutableMapOf<Long, Long>()
                    playlists.forEach { pl ->
                        try {
                            val tracks = dao.getTracksForPlaylistSync(pl.id)
                            val downloadedInPl = tracks.filter { it.localAudioPath.isNotEmpty() }
                            counts[pl.id] = downloadedInPl.size
                            sizes[pl.id] = downloadedInPl.sumOf { trackSizes[it.id] ?: audioFileSize(it.localAudioPath) }
                        } catch (_: Exception) {
                            counts[pl.id] = 0
                            sizes[pl.id] = 0L
                        }
                    }
                    playlistDownloadedCounts = counts
                    playlistSizes = sizes
                }
            } catch (_: Exception) { }
        }
        // Keep disk free-space fresh when downloads finish elsewhere.
        viewModelScope.launch {
            try {
                DownloadManager.storageTrigger.collect { refreshDiskStats() }
            } catch (_: Exception) { }
        }
    }

    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            refreshDiskStats()
            val uriStr = prefs.getDownloadLocation()
            try {
                val all = dao.getAllTracksList()
                downloadedTracks = all
                downloadedCount = all.size
                recalculateSizes(all)
                val playlists = try { dao.getDownloadedPlaylists().first() } catch (_: Exception) { emptyList() }
                downloadedPlaylists = playlists
                val counts = mutableMapOf<Long, Int>()
                val sizes = mutableMapOf<Long, Long>()
                playlists.forEach { pl ->
                    try {
                        val tracks = dao.getTracksForPlaylistSync(pl.id)
                        val downloadedInPl = tracks.filter { it.localAudioPath.isNotEmpty() }
                        counts[pl.id] = downloadedInPl.size
                        sizes[pl.id] = downloadedInPl.sumOf { trackSizes[it.id] ?: audioFileSize(it.localAudioPath) }
                    } catch (_: Exception) {
                        counts[pl.id] = 0
                        sizes[pl.id] = 0L
                    }
                }
                playlistDownloadedCounts = counts
                playlistSizes = sizes
            } catch (e: Exception) { e.printStackTrace() }
            updatePathText(uriStr)
        }
    }

    fun deleteTrack(trackId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadManager.deleteTrack(trackId)
            refreshStorageInfo()
        }
    }

    fun deleteTracks(trackIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            trackIds.forEach { id ->
                DownloadManager.deleteTrack(id)
            }
            refreshStorageInfo()
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadManager.removePlaylistDownloads(playlistId)
            refreshStorageInfo()
        }
    }

    fun deletePlaylists(playlistIds: Set<Long>) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistIds.forEach { id ->
                DownloadManager.removePlaylistDownloads(id)
            }
            refreshStorageInfo()
        }
    }

    fun getTracksTotalSize(trackIds: Set<Long>): Long {
        return trackIds.sumOf { trackSizes[it] ?: 0L }
    }

    fun getPlaylistsTotalSize(playlistIds: Set<Long>): Long {
        return playlistIds.sumOf { playlistSizes[it] ?: 0L }
    }

    private fun refreshDiskStats() {
        try {
            val uriStr = prefs.getDownloadLocation()
            val targetFile: File? = if (uriStr == null) context.filesDir else null
            val path = targetFile ?: Environment.getDataDirectory()
            val stat = StatFs(path.absolutePath)
            totalSpace = stat.blockCountLong * stat.blockSizeLong
            freeSpace = stat.availableBlocksLong * stat.blockSizeLong
            usedSpace = totalSpace - freeSpace
        } catch (_: Exception) { }
    }

    /**
     * Desktop counts from the DB (localAudioPath != '') and sizes the download
     * dir recursively. We do the same: per-track file lengths from the DB rows,
     * so sub-folders, .m4a/.flac and content:// (SAF) URIs all count.
     * exo_cache:// rows have no file — their bytes live in exo_offline_cache,
     * attributed to audio when at least one such track exists.
     */
    private fun recalculateSizes(all: List<LocalTrack>) {
        var aSize = 0L
        var iSize = 0L
        val sizes = mutableMapOf<Long, Long>()
        var hasExo = false
        all.forEach { track ->
            val s = audioFileSize(track.localAudioPath)
            sizes[track.id] = s
            aSize += s
            if (track.localAudioPath.startsWith("exo_cache://")) hasExo = true
            iSize += imageFileSize(track.localArtworkPath)
        }
        try {
            context.filesDir.listFiles()?.forEach { f ->
                if (f.isFile && f.name.startsWith("playlist_cover_")) iSize += f.length()
            }
        } catch (_: Exception) { }

        val exoDir = File(context.cacheDir, "exo_offline_cache")
        val exoSize = getFolderSize(exoDir) + getFolderSize(File(context.filesDir, "exo_offline_cache"))
        if (hasExo) aSize += exoSize

        var cSize = getFolderSize(context.cacheDir) +
                context.externalCacheDirs.filterNotNull().sumOf { getFolderSize(it) }
        if (hasExo) cSize = (cSize - exoSize).coerceAtLeast(0L)

        val dbName = "soundtune_db"
        val dbFile = context.getDatabasePath(dbName)
        val dbWal = context.getDatabasePath("$dbName-wal")
        val dbShm = context.getDatabasePath("$dbName-shm")
        val dbSize = (if (dbFile.exists()) dbFile.length() else 0L) +
                (if (dbWal.exists()) dbWal.length() else 0L) +
                (if (dbShm.exists()) dbShm.length() else 0L)

        audioSize = aSize
        imageSize = iSize
        cacheSize = cSize
        databaseSize = dbSize
        trackSizes = sizes
        querySystemStorageStats()

        val calculated = appCodeSize + audioSize + imageSize + cacheSize + databaseSize
        if (totalAppSize < calculated) {
            totalAppSize = calculated
        }
    }

    private fun querySystemStorageStats() {
        try {
            val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as? StorageStatsManager
            val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            val uuid = try {
                storageManager?.getUuidForPath(context.filesDir) ?: StorageManager.UUID_DEFAULT
            } catch (_: Exception) {
                StorageManager.UUID_DEFAULT
            }
            if (storageStatsManager != null) {
                val stats = storageStatsManager.queryStatsForPackage(uuid, context.packageName, Process.myUserHandle())
                appCodeSize = stats.appBytes
                totalAppSize = stats.appBytes + stats.dataBytes
            } else {
                val apkFile = File(context.packageCodePath)
                if (apkFile.exists()) {
                    appCodeSize = apkFile.length()
                }
                totalAppSize = appCodeSize + audioSize + imageSize + cacheSize + databaseSize
            }
        } catch (_: Exception) {
            val apkFile = File(context.packageCodePath)
            if (apkFile.exists()) {
                appCodeSize = apkFile.length()
            }
            totalAppSize = appCodeSize + audioSize + imageSize + cacheSize + databaseSize
        }
    }

    fun audioFileSize(path: String): Long {
        if (path.isEmpty()) return 0L
        if (path.startsWith("exo_cache://")) return 0L // counted via exo dir
        return try {
            if (path.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, Uri.parse(path))?.length() ?: 0L
            } else {
                val f = File(path)
                if (f.exists() && f.isFile) f.length() else 0L
            }
        } catch (_: Exception) { 0L }
    }

    fun imageFileSize(path: String): Long {
        if (path.isEmpty()) return 0L
        return try {
            if (path.startsWith("content://")) {
                DocumentFile.fromSingleUri(context, Uri.parse(path))?.length() ?: 0L
            } else {
                val f = File(path)
                if (f.exists() && f.isFile) f.length() else 0L
            }
        } catch (_: Exception) { 0L }
    }

    fun trackSizeLabel(track: LocalTrack): String {
        val s = trackSizes[track.id] ?: audioFileSize(track.localAudioPath)
        return if (s > 0L) formatSize(s)
        else if (track.localAudioPath.startsWith("exo_cache://")) context.getString(R.string.storage_size_cached)
        else "—"
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        try {
            dir.listFiles()?.forEach {
                size += if (it.isDirectory) getFolderSize(it) else it.length()
            }
        } catch (_: Exception) { }
        return size
    }

    private fun isAudioFileName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".mp4") ||
                n.endsWith(".flac") || n.endsWith(".ogg") || n.endsWith(".opus") ||
                n.endsWith(".wav") || n.startsWith("track_") || n.startsWith("temp_") ||
                n.startsWith("tagged_")
    }

    private fun isImageFileName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") ||
                n.endsWith(".webp") || n.startsWith("art_") || n.startsWith("playlist_cover_")
    }

    private fun deleteRecursiveAudio(dir: File) {
        try {
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) deleteRecursiveAudio(f)
                else if (isAudioFileName(f.name)) try { f.delete() } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    private fun deleteRecursiveImages(dir: File) {
        try {
            dir.listFiles()?.forEach { f ->
                if (f.isDirectory) deleteRecursiveImages(f)
                else if (isImageFileName(f.name)) try { f.delete() } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    private fun deleteRecursiveAudioDoc(dir: DocumentFile) {
        try {
            dir.listFiles().forEach { f ->
                if (f.isDirectory) deleteRecursiveAudioDoc(f)
                else if (isAudioFileName(f.name ?: "")) try { f.delete() } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    private fun deleteRecursiveImagesDoc(dir: DocumentFile) {
        try {
            dir.listFiles().forEach { f ->
                if (f.isDirectory) deleteRecursiveImagesDoc(f)
                else if (isImageFileName(f.name ?: "")) try { f.delete() } catch (_: Exception) { }
            }
        } catch (_: Exception) { }
    }

    fun cleanAudio() {
        viewModelScope.launch(Dispatchers.IO) {
            // we wait for the deletion to complete fully before refreshing
            DownloadManager.removeAllContent(includeAudio = true, includeImages = false)

            // Cleanup orphaned audio files (recursive: playlist sub-folders)
            val uriStr = prefs.getDownloadLocation()
            if (uriStr == null) {
                deleteRecursiveAudio(context.filesDir)
            } else {
                try {
                    val uri = Uri.parse(uriStr)
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    if (docFile != null) deleteRecursiveAudioDoc(docFile)
                } catch (e: Exception) { e.printStackTrace() }
            }
            refreshStorageInfo()
        }
    }

    fun cleanImages() {
        viewModelScope.launch(Dispatchers.IO) {
            DownloadManager.removeAllContent(includeAudio = false, includeImages = true)

            // Cleanup orphaned image files (like playlist covers)
            val uriStr = prefs.getDownloadLocation()
            if (uriStr == null) {
                deleteRecursiveImages(context.filesDir)
            } else {
                try {
                    val uri = Uri.parse(uriStr)
                    val docFile = DocumentFile.fromTreeUri(context, uri)
                    if (docFile != null) deleteRecursiveImagesDoc(docFile)
                } catch (e: Exception) { e.printStackTrace() }
            }
            // Covers always live in filesDir even with a custom download dir.
            deleteRecursiveImages(context.filesDir)
            refreshStorageInfo()
        }
    }

    fun cleanCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                ExoCacheManager.releaseCache()
                try {
                    coil.Coil.imageLoader(context).diskCache?.clear()
                    coil.Coil.imageLoader(context).memoryCache?.clear()
                } catch (_: Exception) {}
                try {
                    android.webkit.WebStorage.getInstance().deleteAllData()
                } catch (_: Exception) {}
                context.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
                context.externalCacheDirs.filterNotNull().forEach { dir ->
                    dir.listFiles()?.forEach { it.deleteRecursively() }
                }
                File(context.filesDir, "exo_offline_cache").deleteRecursively()
                try {
                    File(context.applicationInfo.dataDir, "app_webview/Default/HTTP Cache").deleteRecursively()
                    File(context.applicationInfo.dataDir, "app_webview/Cache").deleteRecursively()
                } catch (_: Exception) {}
            } catch (e: Exception) {
                e.printStackTrace()
            }
            // refresh immediately after deletion logic is done
            refreshStorageInfo()
        }
    }

    private fun updatePathText(uriStr: String?) {
        currentPath = if (uriStr == null) {
            isExternal = false
            context.getString(R.string.storage_internal_mem)
        } else {
            isExternal = true
            try {
                val uri = Uri.parse(uriStr)
                val path = uri.path ?: uri.toString()
                if (path.contains("primary:")) {
                    context.getString(R.string.storage_internal_mem_sub, path.substringAfter("primary:"))
                } else {
                    context.getString(R.string.storage_sd_card)
                }
            } catch (e: Exception) {
                context.getString(R.string.storage_custom_folder)
            }
        }
    }

    fun onFolderSelected(uri: Uri) {
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            // make sure we keep access after reboot
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
        } catch (e: Exception) { e.printStackTrace() }
        prefs.saveDownloadLocation(uri.toString())
        refreshStorageInfo()
    }

    fun resetToDefault() {
        val oldUriStr = prefs.getDownloadLocation()
        if (oldUriStr != null) {
            try {
                // release permission if we don't use it anymore
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(oldUriStr),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (e: Exception) {}
        }
        prefs.saveDownloadLocation(null)
        refreshStorageInfo()
    }

    fun formatSize(size: Long): String = Formatter.formatFileSize(context, size)
}
