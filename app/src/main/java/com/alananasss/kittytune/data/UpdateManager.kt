package com.alananasss.kittytune.data

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.FileProvider
import com.alananasss.kittytune.BuildConfig
import com.alananasss.kittytune.data.network.GithubAsset
import com.alananasss.kittytune.data.network.GithubClient
import com.alananasss.kittytune.data.network.GithubRelease
import com.alananasss.kittytune.utils.AppUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.coroutineContext
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

    private val _isDownloaded = MutableStateFlow(false)
    val isDownloaded = _isDownloaded.asStateFlow()

    var releaseInfo: GithubRelease? = null
    var downloadedApkFile: File? = null

    private const val PREFS_NAME = "update_cache"
    private const val KEY_LAST_CHECK = "last_check_time"
    private const val KEY_CACHED_RELEASE_JSON = "cached_release_json"

    private val client: OkHttpClient
        get() = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient()
    private const val AUTO_CHECK_COOLDOWN_MS = 15 * 60 * 1000L

    @Volatile
    private var activeCall: Call? = null

    fun getUpdatesDir(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(baseDir, "updates")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getApkFileName(tagName: String): String {
        val sanitized = tagName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
        return "update_$sanitized.apk"
    }

    fun isApkValid(
        context: Context,
        file: File,
        expectedSize: Long = 0L,
        expectedVersion: String? = null
    ): Boolean {
        if (!file.exists() || !file.isFile || file.length() == 0L) return false

        if (expectedSize > 0L && file.length() != expectedSize) {
            return false
        }

        return try {
            val pm = context.packageManager
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.absolutePath, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(file.absolutePath, 0)
            } ?: return false

            if (packageInfo.packageName != context.packageName) {
                return false
            }

            val archiveVersion = packageInfo.versionName?.replace("v", "")?.trim() ?: ""
            val currentVersion = AppUtils.getAppVersion(context).replace("v", "").trim()

            if (archiveVersion.isNotEmpty() && !isNewerVersion(currentVersion, archiveVersion)) {
                return false
            }

            if (!expectedVersion.isNullOrBlank()) {
                val targetVersion = expectedVersion.replace("v", "").trim()
                if (archiveVersion.isNotEmpty() && archiveVersion != targetVersion) {
                    return false
                }
            }

            true
        } catch (e: Exception) {
            false
        }
    }

    fun findBestApkAsset(release: GithubRelease?): GithubAsset? {
        val apkAssets = release?.assets?.filter { it.name.endsWith(".apk", ignoreCase = true) } ?: return null
        if (apkAssets.isEmpty()) return null
        if (apkAssets.size == 1) return apkAssets.first()

        val supportedAbis = android.os.Build.SUPPORTED_ABIS
        for (abi in supportedAbis) {
            val match = apkAssets.find { it.name.contains(abi, ignoreCase = true) }
            if (match != null) return match
        }
        return apkAssets.find { it.name.contains("universal", ignoreCase = true) } ?: apkAssets.first()
    }

    fun getValidCachedApk(context: Context, release: GithubRelease?): File? {
        val asset = findBestApkAsset(release)
        val targetTagName = release?.tagName

        val dir = getUpdatesDir(context)

        if (!targetTagName.isNullOrBlank()) {
            val file = File(dir, getApkFileName(targetTagName))
            if (isApkValid(context, file, asset?.size ?: 0L, targetTagName)) {
                return file
            }
        }

        val legacyFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "update.apk")
        if (isApkValid(context, legacyFile, asset?.size ?: 0L, targetTagName)) {
            if (!targetTagName.isNullOrBlank()) {
                val targetFile = File(dir, getApkFileName(targetTagName))
                if (legacyFile.renameTo(targetFile)) {
                    return targetFile
                }
            }
            return legacyFile
        }

        val files = dir.listFiles { f -> f.isFile && f.name.endsWith(".apk") && !f.name.endsWith(".tmp") }
        if (files != null) {
            for (f in files) {
                if (isApkValid(context, f, asset?.size ?: 0L, targetTagName)) {
                    return f
                }
            }
        }

        return null
    }

    fun cleanUpOldUpdates(context: Context, keepVersionTag: String? = null) {
        try {
            val dir = getUpdatesDir(context)
            val keepFileName = keepVersionTag?.let { getApkFileName(it) }

            dir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp") || (keepFileName != null && file.name != keepFileName) || keepFileName == null) {
                    file.delete()
                }
            }

            val legacyFile = File(context.getExternalFilesDir(null) ?: context.filesDir, "update.apk")
            if (legacyFile.exists() && (keepFileName == null || legacyFile.name != keepFileName)) {
                legacyFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

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
            val release = resolveTargetRelease(context, currentVersion)

            releaseInfo = release
            if (release != null) {
                try {
                    prefs.edit().putString(KEY_CACHED_RELEASE_JSON, Gson().toJson(release)).apply()
                } catch (_: Exception) {}

                val cachedFile = getValidCachedApk(context, release)
                if (cachedFile != null) {
                    downloadedApkFile = cachedFile
                    _isDownloaded.value = true
                } else {
                    downloadedApkFile = null
                    _isDownloaded.value = false
                    cleanUpOldUpdates(context, keepVersionTag = release.tagName)
                }
                _status.value = UpdateStatus.AVAILABLE
            } else {
                _isDownloaded.value = false
                downloadedApkFile = null
                cleanUpOldUpdates(context, keepVersionTag = null)
                _status.value = if (isManual) UpdateStatus.NO_UPDATE else UpdateStatus.IDLE
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val cachedRelease = try {
                val json = prefs.getString(KEY_CACHED_RELEASE_JSON, null)
                if (json != null) Gson().fromJson(json, GithubRelease::class.java) else null
            } catch (_: Exception) { null }

            val cachedFile = getValidCachedApk(context, cachedRelease)
            if (cachedFile != null && cachedRelease != null) {
                val currentVersion = AppUtils.getAppVersion(context).replace("v", "")
                val remoteVersion = cachedRelease.tagName.replace("v", "")
                if (isNewerVersion(currentVersion, remoteVersion)) {
                    releaseInfo = cachedRelease
                    downloadedApkFile = cachedFile
                    _isDownloaded.value = true
                    _status.value = UpdateStatus.AVAILABLE
                    return
                }
            }

            _status.value = if (isManual) UpdateStatus.ERROR else UpdateStatus.IDLE
        }
    }

    suspend fun downloadUpdate(context: Context, forceRedownload: Boolean = false) {
        val release = releaseInfo
        val asset = findBestApkAsset(release)

        if (release == null || asset == null) {
            _status.value = UpdateStatus.ERROR
            return
        }

        val dir = getUpdatesDir(context)
        val targetFile = File(dir, getApkFileName(release.tagName))

        if (!forceRedownload) {
            val cachedFile = getValidCachedApk(context, release)
            if (cachedFile != null) {
                downloadedApkFile = cachedFile
                _isDownloaded.value = true
                _status.value = UpdateStatus.READY_TO_INSTALL
                return
            }
        }

        if (targetFile.exists()) {
            targetFile.delete()
        }

        _status.value = UpdateStatus.DOWNLOADING
        _downloadProgress.value = 0f
        _downloadSize.value = asset.size
        _isDownloaded.value = false

        withContext(Dispatchers.IO) {
            val tmpFile = File(dir, "${targetFile.name}.tmp")
            if (tmpFile.exists()) tmpFile.delete()

            try {
                val noRedirectClient = client.newBuilder()
                    .followRedirects(false)
                    .followSslRedirects(false)
                    .build()

                val requestGitHub = Request.Builder()
                    .url(asset.browserDownloadUrl)
                    .build()

                var call = noRedirectClient.newCall(requestGitHub)
                activeCall = call
                var response = call.execute()

                if (response.code == 302) {
                    val downloadUrl = response.header("Location")
                    response.close()

                    if (downloadUrl != null) {
                        val requestS3 = Request.Builder()
                            .url(downloadUrl)
                            .build()

                        call = client.newCall(requestS3)
                        activeCall = call
                        response = call.execute()
                    } else {
                        throw Exception("Redirect without location")
                    }
                }

                if (!response.isSuccessful) {
                    throw Exception("HTTP Error ${response.code}")
                }

                val body = response.body
                val totalSize = if (asset.size > 0L) asset.size else body.contentLength()

                body.byteStream().use { input ->
                    FileOutputStream(tmpFile).use { output ->
                        val buffer = ByteArray(8 * 1024)
                        var bytesCopied = 0L
                        var read = 0
                        while (coroutineContext.isActive && input.read(buffer).also { read = it } >= 0) {
                            output.write(buffer, 0, read)
                            bytesCopied += read
                            if (totalSize > 0L) {
                                _downloadProgress.value = bytesCopied.toFloat() / totalSize.toFloat()
                            }
                        }
                        output.flush()
                    }
                }

                if (!coroutineContext.isActive) {
                    tmpFile.delete()
                    return@withContext
                }

                if (!isApkValid(context, tmpFile, asset.size, release.tagName)) {
                    tmpFile.delete()
                    throw Exception("Downloaded APK failed verification")
                }

                if (targetFile.exists()) targetFile.delete()
                if (!tmpFile.renameTo(targetFile)) {
                    tmpFile.copyTo(targetFile, overwrite = true)
                    tmpFile.delete()
                }

                downloadedApkFile = targetFile
                _isDownloaded.value = true
                _status.value = UpdateStatus.READY_TO_INSTALL

            } catch (e: Exception) {
                e.printStackTrace()
                tmpFile.delete()
                if (_status.value == UpdateStatus.DOWNLOADING) {
                    _status.value = UpdateStatus.ERROR
                }
            } finally {
                activeCall = null
                _downloadSize.value = 0L
            }
        }
    }

    fun installUpdate(context: Context) {
        var file = downloadedApkFile
        if (file == null || !file.exists()) {
            file = getValidCachedApk(context, releaseInfo)
            downloadedApkFile = file
        }
        if (file == null || !file.exists()) {
            _status.value = UpdateStatus.ERROR
            return
        }
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

    fun cancelDownload() {
        try {
            activeCall?.cancel()
        } catch (_: Exception) {}
        activeCall = null
        _status.value = UpdateStatus.IDLE
        _downloadProgress.value = 0f
        _downloadSize.value = 0L
    }

    fun dismiss() {
        if (_status.value == UpdateStatus.DOWNLOADING) {
            cancelDownload()
        } else {
            _status.value = UpdateStatus.IDLE
            _downloadProgress.value = 0f
            _downloadSize.value = 0L
        }
    }

    fun isNewerVersion(current: String, remote: String): Boolean {
        return try {
            val (v1Nums, v1Pre) = splitVersion(current)
            val (v2Nums, v2Pre) = splitVersion(remote)
            for (i in 0 until max(v1Nums.size, v2Nums.size)) {
                val v1Part = v1Nums.getOrElse(i) { 0 }
                val v2Part = v2Nums.getOrElse(i) { 0 }
                if (v2Part > v1Part) return true
                if (v2Part < v1Part) return false
            }
            // Same numeric core: a prerelease is older than its release, and two
            // prereleases compare by suffix (beta.3 < beta.4). A stable is never
            // "older" than a prerelease of the same core.
            if (v1Pre == null && v2Pre == null) return false
            if (v1Pre != null && v2Pre == null) return true
            if (v1Pre == null && v2Pre != null) return false
            comparePreRelease(v1Pre!!, v2Pre!!) < 0
        } catch (e: Exception) {
            false
        }
    }

    private fun splitVersion(version: String): Pair<List<Int>, String?> {
        val clean = version.trim().removePrefix("v")
        val dash = clean.indexOf('-')
        val core = if (dash < 0) clean else clean.substring(0, dash)
        val pre = if (dash < 0) null else clean.substring(dash + 1).takeIf { it.isNotBlank() }
        return core.split(".").map { it.toIntOrNull() ?: 0 } to pre
    }

    private fun comparePreRelease(a: String, b: String): Int {
        val aParts = a.split(".")
        val bParts = b.split(".")
        for (i in 0 until max(aParts.size, bParts.size)) {
            val ap = aParts.getOrElse(i) { "" }
            val bp = bParts.getOrElse(i) { "" }
            if (ap == bp) continue
            val an = ap.toIntOrNull()
            val bn = bp.toIntOrNull()
            if (an != null && bn != null) return an.compareTo(bn)
            return ap.compareTo(bp)
        }
        return 0
    }

    /**
     * Which release the update check targets.
     *
     * Stable channel (default): GitHub's `latest`, which excludes prereleases.
     * Beta channel (opt-in toggle): the newest release — stable or prerelease —
     * newer than the installed version, so beta users still get stables.
     * Same button, same startup auto-check, different channel.
     */
    private suspend fun resolveTargetRelease(context: Context, currentVersion: String): GithubRelease? {
        val betaEnabled = try {
            com.alananasss.kittytune.data.local.PlayerPreferences(context).getBetaUpdatesEnabled()
        } catch (_: Exception) { false }
        if (!betaEnabled) {
            val latest = GithubClient.api.getLatestRelease()
            return latest.takeIf { isNewerVersion(currentVersion, it.tagName.replace("v", "")) }
        }
        val releases = GithubClient.api.listReleases()
        // GitHub returns newest first: first newer release wins.
        return releases.firstOrNull { isNewerVersion(currentVersion, it.tagName.replace("v", "")) }
    }
}
