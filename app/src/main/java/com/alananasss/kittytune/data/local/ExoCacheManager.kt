package com.alananasss.kittytune.data.local

import android.content.Context
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object ExoCacheManager {
    private const val TAG = "ExoCacheManager"
    private var downloadCache: SimpleCache? = null
    private var playbackCache: SimpleCache? = null

    /**
     * Cache for permanent offline downloads.
     * Stored in [Context.filesDir] with [NoOpCacheEvictor] so downloaded tracks
     * are never purged by LRU eviction or by OS cache sweeps.
     */
    @Synchronized
    fun getDownloadCache(context: Context): SimpleCache {
        val existing = downloadCache
        if (existing != null) {
            return try {
                existing.keys
                existing
            } catch (e: IllegalStateException) {
                downloadCache = null
                createDownloadCache(context)
            }
        }
        return createDownloadCache(context)
    }

    /**
     * Cache for temporary online streaming playback.
     * Stored in [Context.cacheDir] with LRU eviction (250MB limit).
     */
    @Synchronized
    fun getPlaybackCache(context: Context): SimpleCache {
        val existing = playbackCache
        if (existing != null) {
            return try {
                existing.keys
                existing
            } catch (e: IllegalStateException) {
                playbackCache = null
                createPlaybackCache(context)
            }
        }
        return createPlaybackCache(context)
    }

    /**
     * Backward-compatible alias for downloaded tracks cache.
     */
    @Synchronized
    fun getCache(context: Context): SimpleCache = getDownloadCache(context)

    private fun createDownloadCache(context: Context): SimpleCache {
        val targetDir = File(context.filesDir, "exo_offline_cache")
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // Migrate older cache from cacheDir if target is empty
        val oldCacheDir = File(context.cacheDir, "exo_offline_cache")
        if (oldCacheDir.exists() && oldCacheDir.isDirectory) {
            try {
                val oldFiles = oldCacheDir.listFiles().orEmpty()
                if (oldFiles.isNotEmpty()) {
                    Log.i(TAG, "Migrating ${oldFiles.size} offline cache files from cacheDir to filesDir")
                    oldFiles.forEach { file ->
                        val dest = File(targetDir, file.name)
                        if (!dest.exists()) {
                            file.copyRecursively(dest, overwrite = true)
                        }
                    }
                }
                oldCacheDir.deleteRecursively()
            } catch (e: Exception) {
                Log.w(TAG, "Error migrating old cacheDir: ${e.message}")
            }
        }

        val databaseProvider = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(
            targetDir,
            NoOpCacheEvictor(),
            databaseProvider
        )
        downloadCache = cache
        return cache
    }

    private fun createPlaybackCache(context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "exo_playback_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(250 * 1024 * 1024),
            databaseProvider
        )
        playbackCache = cache
        return cache
    }

    @Synchronized
    fun isCacheActive(): Boolean = downloadCache != null || playbackCache != null

    @Synchronized
    fun releaseCache() {
        try {
            downloadCache?.release()
        } catch (_: Exception) {}
        downloadCache = null

        try {
            playbackCache?.release()
        } catch (_: Exception) {}
        playbackCache = null
    }
}
