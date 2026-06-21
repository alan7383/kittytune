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
        if (simpleCache == null) {
            // Clean up old cache from User Data if it exists
            val oldCacheDir = File(context.filesDir, "exo_offline_cache")
            if (oldCacheDir.exists()) {
                oldCacheDir.deleteRecursively()
            }
            
            val cacheDir = File(context.cacheDir, "exo_offline_cache")
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(
                cacheDir,
                LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024), // 200 MB limit
                databaseProvider
            )
        }
        return simpleCache!!
    }

    @Synchronized
    fun releaseCache() {
        try {
            simpleCache?.release()
        } catch (e: Exception) {}
        simpleCache = null
    }
}
