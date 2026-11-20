package com.alananasss.kittytune.data.local

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object ExoCacheManager {
    private const val TAG = "ExoCacheManager"
    private var downloadCache: SimpleCache? = null
    private var playbackCache: SimpleCache? = null
    private var analysisCache: SimpleCache? = null

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

    /**
     * A bounded factory for the analysis players (waveform / beat decoding, transition previews).
     *
     * Analysis used to stream through the download cache, which has no eviction: every analyzed
     * online track left a full permanent copy in the app's files directory, with no way to shrink
     * it afterwards. The analysis cache lives in the cache directory under an LRU cap instead, so
     * it is bounded and clearable. Reads still go to the download cache for tracks that are
     * downloaded (they are not re-fetched); anything else never touches it.
     */
    @Synchronized
    fun getAnalysisCacheFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory {
        val analysis = getAnalysisCache(context)
        val download = getDownloadCache(context)
        val downloadFactory = CacheDataSource.Factory()
            .setCache(download)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val analysisFactory = CacheDataSource.Factory()
            .setCache(analysis)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DataSource.Factory {
            val downloadSource = downloadFactory.createDataSource()
            val analysisSource = analysisFactory.createDataSource()
            object : DataSource {
                private var current: DataSource = analysisSource

                override fun addTransferListener(transferListener: TransferListener) {
                    downloadSource.addTransferListener(transferListener)
                    analysisSource.addTransferListener(transferListener)
                }

                override fun open(dataSpec: androidx.media3.datasource.DataSpec): Long {
                    val key = dataSpec.key ?: dataSpec.uri.toString()
                    val isDownloaded = try {
                        download.keys.contains(key)
                    } catch (_: Exception) {
                        false
                    }
                    current = if (isDownloaded) downloadSource else analysisSource
                    return current.open(dataSpec)
                }

                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current.read(buffer, offset, length)

                override fun getUri(): Uri? = current.uri

                override fun close() {
                    current.close()
                }
            }
        }
    }

    /**
     * Cache for analysis players only: cache directory, LRU-evicted, 128MB.
     */
    @Synchronized
    fun getAnalysisCache(context: Context): SimpleCache {
        val existing = analysisCache
        if (existing != null) {
            return try {
                existing.keys
                existing
            } catch (e: IllegalStateException) {
                analysisCache = null
                createAnalysisCache(context)
            }
        }
        return createAnalysisCache(context)
    }

    private fun createAnalysisCache(context: Context): SimpleCache {
        val cacheDir = File(context.cacheDir, "exo_analysis_cache")
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(128 * 1024 * 1024),
            databaseProvider
        )
        analysisCache = cache
        return cache
    }

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

        try {
            analysisCache?.release()
        } catch (_: Exception) {}
        analysisCache = null
    }
}
