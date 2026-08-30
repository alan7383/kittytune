package com.alananasss.kittytune

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.map.Mapper
import com.alananasss.kittytune.utils.Config
import com.alananasss.kittytune.utils.LocaleUtils
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeLocale
import kotlinx.coroutines.launch
import java.io.File

class KittyTuneApp : Application(), ImageLoaderFactory {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(LocaleUtils.updateBaseContextLocale(base))
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        LocaleUtils.applyAppLanguage(this)
        Config.init(applicationContext)

        val activeLocale = LocaleUtils.getLocale(this)
        YouTube.locale = YouTubeLocale(
            gl = activeLocale.country.ifBlank { "US" },
            hl = activeLocale.language.ifBlank { "en" }
        )

        com.alananasss.kittytune.data.network.ProxyManager.init(this)

        // Paired once, in step from then on. Costs nothing until something is paired: no port is opened
        // and no timer runs on an install that has never paired (issue #33).
        if (!com.alananasss.kittytune.data.sync.SyncPeers.isEmpty()) {
            com.alananasss.kittytune.data.sync.SyncScheduler.start()
            // The half that used to be missing. Without a listener here the computer could never start an
            // exchange, so its "sync now" button could not fetch anything and sync looked one-way — which
            // it was.
            com.alananasss.kittytune.data.sync.SyncService.startIfWanted()
            // Anything the log holds that the statistics table is missing goes back in — see
            // [SyncApply.reconcile] for the data loss this repairs (issue #33).
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                runCatching { com.alananasss.kittytune.data.sync.SyncApply.reconcile() }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .okHttpClient { com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient(this) }
            .components {
                add(Mapper<String, File> { data, _ ->
                    if (data.startsWith("/") && !data.startsWith("http")) File(data) else null
                })
            }
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: KittyTuneApp
            private set
    }
}
