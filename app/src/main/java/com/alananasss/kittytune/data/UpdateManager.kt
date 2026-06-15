package com.alananasss.kittytune.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.alananasss.kittytune.BuildConfig
import com.alananasss.kittytune.data.network.GithubClient
import com.alananasss.kittytune.data.network.GithubRelease
import com.alananasss.kittytune.utils.AppUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

enum class UpdateStatus {
    IDLE, CHECKING, AVAILABLE, DOWNLOADING, READY_TO_INSTALL, ERROR, NO_UPDATE
}

object UpdateManager {
    private val _status = MutableStateFlow(UpdateStatus.IDLE)
    val status = _status.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0f)
    val downloadProgress = _downloadProgress.asStateFlow()

    private val _downloadSize = MutableStateFlow(0L)
    val downloadSize = _downloadSize.asStateFlow()

    var releaseInfo: GithubRelease? = null
    var downloadedApkFile: File? = null

    private const val PREFS_NAME = "update_cache"
    private const val KEY_LAST_CHECK = "last_check_time"

    private val client = OkHttpClient()
    private const val AUTO_CHECK_COOLDOWN_MS = 15 * 60 * 1000L

    suspend fun checkForUpdate(context: Context, isManual: Boolean = false) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        if (!isManual) {
            val lastCheck = prefs.getLong(KEY_LAST_CHECK, 0L)
            val now = System.currentTimeMillis()
            if (now - lastCheck < AUTO_CHECK_COOLDOWN_MS) {
                return
            }
            prefs.edit().putLong(KEY_LAST_CHECK, now).apply()
        }

        _status.value = UpdateStatus.CHECKING

        try {
            val currentVersion = AppUtils.getAppVersion(context).replace("v", "")
            val release = GithubClient.api.getLatestRelease()

            releaseInfo = release
            val remoteVersion = release.tagName.replace("v", "")

            if (isNewerVersion(currentVersion, remoteVersion)) {
                _status.value = UpdateStatus.AVAILABLE
            } else {
                _status.value = if (isManual) UpdateStatus.NO_UPDATE else UpdateStatus.IDLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            _status.value = if (isManual) UpdateStatus.ERROR else UpdateStatus.IDLE
        }
    }

    suspend fun downloadUpdate(context: Context) {
        val asset = releaseInfo?.assets?.find {
            it.name.endsWith(".apk", ignoreCase = true)
        }

        if (asset == null) {
            _status.value = UpdateStatus.ERROR
            return
        }

        _status.value = UpdateStatus.DOWNLOADING
        _downloadProgress.value = 0f
        _downloadSize.value = asset.size

        withContext(Dispatchers.IO) {
            try {
                val noRedirectClient = client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

                val requestGitHub = Request.Builder()
                    .url(asset.browserDownloadUrl)
                    .build()

                var response = noRedirectClient.newCall(requestGitHub).execute()

                if (response.code == 302) {
                    val downloadUrl = response.header("Location")
                    response.close()

                    if (downloadUrl != null) {
                        val requestS3 = Request.Builder()
                            .url(downloadUrl)
                            .build()

                        response = client.newCall(requestS3).execute()
                    } else {
                        throw Exception("Redirect without location")
                    }
                }

                if (!response.isSuccessful) {
                    throw Exception("HTTP Error ${response.code}")
                }

                val body = response.body
                val totalSize = body.contentLength()
                val file = File(context.getExternalFilesDir(null), "update.apk")
                if (file.exists()) file.delete()

                body.byteStream().use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied = 0L
                        var read: Int
                        while (input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalSize > 0) {
                                _downloadProgress.value = bytesCopied.toFloat() / totalSize.toFloat()
                            }
                        }
                        output.flush()
                    }
                }

                downloadedApkFile = file
                _status.value = UpdateStatus.READY_TO_INSTALL

            } catch (e: Exception) {
                e.printStackTrace()
                _status.value = UpdateStatus.ERROR
            } finally {
                _downloadSize.value = 0L
            }
        }
    }

    fun installUpdate(context: Context) {
        val file = downloadedApkFile ?: return
        try {
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
            _status.value = UpdateStatus.ERROR
        }
    }

    fun dismiss() {
        _status.value = UpdateStatus.IDLE
        _downloadProgress.value = 0f
        _downloadSize.value = 0L
    }

    private fun isNewerVersion(current: String, remote: String): Boolean {
        return try {
            val v1 = current.split(".").map { it.toIntOrNull() ?: 0 }
            val v2 = remote.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until max(v1.size, v2.size)) {
                val v1Part = v1.getOrElse(i) { 0 }
                val v2Part = v2.getOrElse(i) { 0 }
                if (v2Part > v1Part) return true
                if (v2Part < v1Part) return false
            }
            false
        } catch (e: Exception) {
            false
        }
    }
}