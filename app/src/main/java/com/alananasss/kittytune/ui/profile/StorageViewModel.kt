package com.alananasss.kittytune.ui.profile

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.text.format.Formatter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class StorageViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val prefs = PlayerPreferences(context)

    // global stats
    var usedSpace by mutableLongStateOf(0L)
    var freeSpace by mutableLongStateOf(0L)
    var totalSpace by mutableLongStateOf(0L)

    // detailed breakdown
    var audioSize by mutableLongStateOf(0L)
    var imageSize by mutableLongStateOf(0L)
    var cacheSize by mutableLongStateOf(0L)
    var databaseSize by mutableLongStateOf(0L)

    var currentPath by mutableStateOf(context.getString(R.string.storage_loading))
    var isExternal by mutableStateOf(false)

    init {
        refreshStorageInfo()
    }

    fun refreshStorageInfo() {
        viewModelScope.launch(Dispatchers.IO) {
            val uriStr = prefs.getDownloadLocation()

            val targetFile: File? = if (uriStr == null) context.filesDir else null
            val path = targetFile ?: Environment.getDataDirectory()
            val stat = StatFs(path.absolutePath)

            // disk stats
            totalSpace = stat.blockCountLong * stat.blockSizeLong
            freeSpace = stat.availableBlocksLong * stat.blockSizeLong
            usedSpace = totalSpace - freeSpace

            calculateDetailedUsage(uriStr)
            updatePathText(uriStr)
        }
    }

    private fun calculateDetailedUsage(uriStr: String?) {
        var aSize = 0L
        var iSize = 0L

        if (uriStr == null) {
            // internal storage scan
            context.filesDir.listFiles()?.forEach { file ->
                when {
                    file.name.endsWith(".mp3") || file.name.startsWith("track_") -> aSize += file.length()
                    file.name.endsWith(".jpg") || file.name.startsWith("art_") -> iSize += file.length()
                }
            }
        } else {
            // external/custom folder scan
            try {
                val uri = Uri.parse(uriStr)
                val docFile = DocumentFile.fromTreeUri(context, uri)
                docFile?.listFiles()?.forEach { file ->
                    val name = file.name ?: ""
                    when {
                        name.endsWith(".mp3") || name.startsWith("track_") -> aSize += file.length()
                        name.endsWith(".jpg") || name.startsWith("art_") -> iSize += file.length()
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }

        val cSize = getFolderSize(context.cacheDir) + getFolderSize(context.codeCacheDir)
        val dbFile = context.getDatabasePath("kittytune_db")
        val dbSize = if (dbFile.exists()) dbFile.length() else 0L

        audioSize = aSize
        imageSize = iSize
        cacheSize = cSize
        databaseSize = dbSize
    }

    private fun getFolderSize(dir: File): Long {
        var size = 0L
        dir.listFiles()?.forEach {
            size += if (it.isDirectory) getFolderSize(it) else it.length()
        }
        return size
    }

    fun cleanAudio() {
        viewModelScope.launch {
            DownloadManager.removeAllContent(includeAudio = true, includeImages = false)
            refreshStorageInfo()
        }
    }

    fun cleanImages() {
        viewModelScope.launch {
            DownloadManager.removeAllContent(includeAudio = false, includeImages = true)
            refreshStorageInfo()
        }
    }

    fun cleanCache() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.cacheDir.deleteRecursively()
                context.codeCacheDir.deleteRecursively()
            } catch (e: Exception) { e.printStackTrace() }
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