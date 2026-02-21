    package com.alananasss.kittytune.data
    
    import android.content.Context
    import android.content.Intent
    import android.util.Log
    import androidx.core.content.FileProvider
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
        private const val KEY_ETAG = "github_etag"
        private const val KEY_LAST_CHECK = "last_check_time"
        private const val KEY_CACHED_JSON = "cached_release_json"
    
        private val client = OkHttpClient()
        private val gson = Gson()
    
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
    
                val release = fetchLatestReleaseWithETag(context, prefs)
    
                if (release == null) {
                    _status.value = if (isManual) UpdateStatus.ERROR else UpdateStatus.IDLE
                    return
                }
    
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
    
        private suspend fun fetchLatestReleaseWithETag(context: Context, prefs: android.content.SharedPreferences): GithubRelease? {
            return withContext(Dispatchers.IO) {
                val cachedETag = prefs.getString(KEY_ETAG, null)
    
                val requestBuilder = Request.Builder()
                    .url("https://api.github.com/repos/alan7383/kittytune/releases/latest")
                    .header("Accept", "application/vnd.github.v3+json")
    
                if (cachedETag != null) {
                    requestBuilder.header("If-None-Match", cachedETag)
                }
    
                try {
                    val response = client.newCall(requestBuilder.build()).execute()
    
                    when (response.code) {
                        304 -> {
                            Log.d("UpdateManager", "304 Not Modified - Using Cache")
                            val cachedJson = prefs.getString(KEY_CACHED_JSON, null)
                            response.close()
    
                            if (cachedJson != null) {
                                return@withContext gson.fromJson(cachedJson, GithubRelease::class.java)
                            }
                            return@withContext null
                        }
                        200 -> {
                            val newETag = response.header("ETag")
                            val body = response.body?.string()
                            response.close()
    
                            if (body != null) {
                                prefs.edit()
                                    .putString(KEY_ETAG, newETag)
                                    .putString(KEY_CACHED_JSON, body)
                                    .apply()
    
                                Log.d("UpdateManager", "200 OK - Cache Updated")
                                return@withContext gson.fromJson(body, GithubRelease::class.java)
                            }
                        }
                        403 -> {
                            Log.e("UpdateManager", "Rate Limit Exceeded (403)")
                            response.close()
                            val cachedJson = prefs.getString(KEY_CACHED_JSON, null)
                            if (cachedJson != null) {
                                return@withContext gson.fromJson(cachedJson, GithubRelease::class.java)
                            }
                        }
                        else -> {
                            response.close()
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val cachedJson = prefs.getString(KEY_CACHED_JSON, null)
                    if (cachedJson != null) {
                        return@withContext gson.fromJson(cachedJson, GithubRelease::class.java)
                    }
                }
    
                return@withContext null
            }
        }
    
    
        suspend fun downloadUpdate(context: Context) {
            val asset = releaseInfo?.assets?.find {
                it.contentType == "application/vnd.android.package-archive" || it.downloadUrl.endsWith(".apk")
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
                    val request = Request.Builder().url(asset.downloadUrl).build()
                    val response = client.newCall(request).execute()
                    val body = response.body ?: throw Exception("Empty body")
    
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
                                _downloadProgress.value =
                                    if (totalSize > 0) bytesCopied.toFloat() / totalSize.toFloat() else 0f
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
                for (i in 0 until maxOf(v1.size, v2.size)) {
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


