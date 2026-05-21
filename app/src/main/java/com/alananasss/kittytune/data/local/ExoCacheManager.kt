package com.alananasss.kittytune.data.local

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

object ExoCacheManager {
    private var simpleCache: SimpleCache? = null

    @Synchronized
    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.filesDir, "exo_offline_cache")
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(
                cacheDir,
                NoOpCacheEvictor(),
                databaseProvider
            )
        }
        return simpleCache!!
    }
}
