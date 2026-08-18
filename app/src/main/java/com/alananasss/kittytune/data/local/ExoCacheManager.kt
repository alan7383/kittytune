package com.alananasss.kittytune.data.local

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object ExoCacheManager {
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        val existing = simpleCache
        if (existing != null) {
            return try {
                existing.keys
                existing
            } catch (e: IllegalStateException) {
                simpleCache = null
                createCache(context)
            }
        }
        return createCache(context)
    }

    private fun createCache(context: Context): SimpleCache {
        val oldCacheDir = File(context.filesDir, "exo_offline_cache")
        if (oldCacheDir.exists()) {
            oldCacheDir.deleteRecursively()
        }

        val cacheDir = File(context.cacheDir, "exo_offline_cache")
        val databaseProvider = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(
            cacheDir,
            LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024),
            databaseProvider
        )
        simpleCache = cache
        return cache
    }

    @Synchronized
    fun isCacheActive(): Boolean = simpleCache != null

    @Synchronized
    fun releaseCache() {
        try {
            simpleCache?.release()
        } catch (e: Exception) {}
        simpleCache = null
    }
}
