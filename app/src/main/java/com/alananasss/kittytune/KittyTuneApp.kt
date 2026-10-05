package com.alananasss.kittytune

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
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
        com.alananasss.kittytune.utils.AppLogManager.init(this)
        LocaleUtils.applyAppLanguage(this)
        Config.init(applicationContext)

        runCatching {
            app.rive.runtime.kotlin.core.Rive.init(this)
        }

        val activeLocale = LocaleUtils.getLocale(this)
        YouTube.locale = YouTubeLocale(
            gl = activeLocale.country.ifBlank { "US" },
            hl = activeLocale.language.ifBlank { "en" }
        )

        com.alananasss.kittytune.data.network.ProxyManager.init(this)

        // In-app DPI bypass (zapret): no folder, no VPN, no proxy. Installs the fragmenting
        // socket factories and restores the bypass domain list into the dynamic policy.
        com.alananasss.kittytune.data.zapret.ZapretManager.init(this)
        com.zionhuang.innertube.YouTube.socketFactory =
            com.alananasss.kittytune.data.zapret.ZapretManager.bypassSocketFactory()
        // Recognition (Shazam) and KuGou lyrics run on their own Ktor engines in library
        // modules: same dynamic factory, set once, follows toggles without rebuilds.
        com.metrolist.shazamkit.Shazam.socketFactory =
            com.alananasss.kittytune.data.zapret.ZapretManager.bypassSocketFactory()
        com.zionhuang.kugou.KuGou.socketFactory =
            com.alananasss.kittytune.data.zapret.ZapretManager.bypassSocketFactory()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {
                com.alananasss.kittytune.data.zapret.ZapretManager.autoConfigureOnce(this@KittyTuneApp)
            }
        }

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
                // Animated image support (animated playlist covers etc.). Decoders sniff
                // the actual bytes, so a GIF stored with a .jpg extension still animates.
                // ImageDecoderDecoder (API 28+) covers animated GIF/WebP/AVIF via the
                // platform AnimatedImageDrawable; GifDecoder is the software fallback
                // for API 26-27 (app minSdk).
                if (Build.VERSION.SDK_INT >= 28) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                // Data saver choke point for artwork: every image request in the app goes
                // through this interceptor, so a single URL rewrite here lightens covers in
                // lists, players, playlists and widgets at once — no UI call site touched.
                add(object : coil.intercept.Interceptor {
                    override suspend fun intercept(chain: coil.intercept.Interceptor.Chain): coil.request.ImageResult {
                        if (!com.alananasss.kittytune.data.DataSaver.isActive(this@KittyTuneApp)) {
                            return chain.proceed(chain.request)
                        }
                        val data = chain.request.data
                        if (data is String) {
                            val light = com.alananasss.kittytune.data.DataSaver.lightArtwork(data)
                            if (light != null && light != data) {
                                return chain.proceed(chain.request.newBuilder().data(light).build())
                            }
                        }
                        return chain.proceed(chain.request)
                    }
                })
            }
            .diskCache {
                coil.disk.DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .respectCacheHeaders(false)
            .allowRgb565(true)
            .crossfade(true)
            .build()
    }

    companion object {
        lateinit var instance: KittyTuneApp
            private set
    }
}
