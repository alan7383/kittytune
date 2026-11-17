package com.alananasss.kittytune.data.yearlyplayback

import android.content.Context
import android.util.Log
import app.rive.runtime.kotlin.core.Rive
import com.alananasss.kittytune.data.network.ProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

/**
 * Manages downloading, disk-caching, and Rive C++ engine loading of SoundCloud's
 * authentic Wrapped vector animation file (ysc2025-fan-v1.riv / ysc2025-creator-v1.riv).
 */
class RiveFileManager(
    private val context: Context,
    val imageAssetStore: ImageAssetStore
) {
    suspend fun loadRiveFile(
        target: YearlyPlaybackTarget,
        riveUrl: String?
    ): app.rive.runtime.kotlin.core.File? = withContext(Dispatchers.IO) {
        runCatching {
            Rive.init(context)
            val candidatePaths = listOf(
                "/system/fonts/NotoSansCJK-Regular.ttc",
                "/system/fonts/NotoSansArabic-Regular.ttf"
            )
            for (path in candidatePaths) {
                val f = File(path)
                if (f.exists() && f.canRead()) {
                    val registered = Rive.setFallbackFont(f.readBytes())
                    Log.d(TAG, "Registered fallback font '$path': $registered")
                }
            }
        }.onFailure { err ->
            Log.w(TAG, "Rive.init or fallback font registration error", err)
        }

        val targetName = if (target == YearlyPlaybackTarget.FAN) "fan" else "creator"
        val dir = File(context.cacheDir, "yearly_playback")
        if (!dir.exists()) dir.mkdirs()
        val cacheFile = File(dir, "$targetName-rive.riv")

        val expectedMinBytes = 100_000L
        if (!cacheFile.exists() || cacheFile.length() < expectedMinBytes) {
            val url = riveUrl ?: "https://assets.web.soundcloud.cloud/n/animations/ysc2025-$targetName-v1.riv"
            Log.d(TAG, "Downloading authentic SoundCloud Rive file from $url to ${cacheFile.absolutePath}")
            try {
                val okHttpClient = ProxyManager.getOkHttpClient(context)
                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "SoundCloud/2025.12.10-release (Android 17)")
                    .build()
                val response = okHttpClient.newCall(request).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed downloading Rive file: HTTP ${response.code}")
                    return@withContext null
                }
                val body = response.body ?: return@withContext null
                cacheFile.outputStream().use { out ->
                    body.byteStream().copyTo(out)
                }
                Log.d(TAG, "Rive file downloaded successfully: ${cacheFile.length()} bytes")
            } catch (e: Exception) {
                Log.e(TAG, "Exception downloading Rive file", e)
                cacheFile.delete()
                return@withContext null
            }
        } else {
            Log.d(TAG, "Using cached Rive file: ${cacheFile.absolutePath} (${cacheFile.length()} bytes)")
        }

        runCatching {
            val bytes = cacheFile.readBytes()
            val loader = DefaultFileAssetLoader(imageAssetStore)
            app.rive.runtime.kotlin.core.File(bytes = bytes, fileAssetLoader = loader)
        }.onFailure { err ->
            Log.e(TAG, "Failed to parse Rive file, deleting corrupted cache", err)
            cacheFile.delete()
        }.getOrNull()
    }

    companion object {
        private const val TAG = "RiveFileManager"
    }
}
