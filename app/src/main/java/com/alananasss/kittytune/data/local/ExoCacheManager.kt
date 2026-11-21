package com.alananasss.kittytune.data.local

import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object ExoCacheManager {
    private const val TAG = "ExoCacheManager"
    private var downloadCache: SimpleCache? = null
    private var playbackCache: SimpleCache? = null
    private var analysisCache: SimpleCache? = null

    private const val OFFLINE_DIRECTORY = "exo_offline_cache"
    private const val PLAYBACK_DIRECTORY = "exo_playback_cache"
    private const val ANALYSIS_DIRECTORY = "exo_analysis_cache"

    internal fun isMediaCacheDirectory(name: String): Boolean =
        name == OFFLINE_DIRECTORY || name == PLAYBACK_DIRECTORY || name == ANALYSIS_DIRECTORY

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

    @Synchronized
    fun getAnalysisCacheFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory =
        createTieredFactory(getDownloadCache(context), getAnalysisCache(context), upstream)

    @Synchronized
    fun getPlaybackCacheFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory =
        createTieredFactory(getDownloadCache(context), getPlaybackCache(context), upstream)

    private fun getAnalysisCache(context: Context): SimpleCache =
        analysisCache ?: SimpleCache(
            File(context.cacheDir, ANALYSIS_DIRECTORY),
            LeastRecentlyUsedCacheEvictor(128L * 1024 * 1024),
            StandaloneDatabaseProvider(context),
        ).also { analysisCache = it }

    private fun createTieredFactory(
        downloads: SimpleCache,
        transient: SimpleCache,
        upstream: DataSource.Factory,
    ): DataSource.Factory {
        val transientFactory = CacheDataSource.Factory()
            .setCache(transient)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val offlineFactory = CacheDataSource.Factory()
            .setCache(downloads)
            .setCacheWriteDataSinkFactory(null)
            .setUpstreamDataSourceFactory(transientFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        return DataSource.Factory {
            val directSource = upstream.createDataSource()
            val offlineSource = offlineFactory.createDataSource()
            val transientSource = transientFactory.createDataSource()
            object : DataSource {
                private var active: DataSource = transientSource

                override fun addTransferListener(listener: TransferListener) {
                    directSource.addTransferListener(listener)
                    offlineSource.addTransferListener(listener)
                    transientSource.addTransferListener(listener)
                }

                override fun open(dataSpec: DataSpec): Long {
                    val key = dataSpec.key ?: dataSpec.uri.toString()
                    active = when {
                        dataSpec.uri.scheme in listOf(null, "file", "content") -> directSource
                        key in downloads.keys -> offlineSource
                        else -> transientSource
                    }
                    return active.open(dataSpec)
                }

                override fun getUri(): Uri? = active.uri
                override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    active.read(buffer, offset, length)
                override fun close() = active.close()
            }
        }
    }

    @Synchronized
    fun clearTransientCaches(context: Context) {
        getPlaybackCache(context).let { cache -> cache.keys.forEach(cache::removeResource) }
        getAnalysisCache(context).let { cache -> cache.keys.forEach(cache::removeResource) }
    }

    private fun createDownloadCache(context: Context): SimpleCache {
        val targetDir = File(context.filesDir, OFFLINE_DIRECTORY)
        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }

        // Migrate older cache from cacheDir if target is empty
        val oldCacheDir = File(context.cacheDir, OFFLINE_DIRECTORY)
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
        val cacheDir = File(context.cacheDir, PLAYBACK_DIRECTORY)
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
    fun isCacheActive(): Boolean = downloadCache != null || playbackCache != null || analysisCache != null

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
