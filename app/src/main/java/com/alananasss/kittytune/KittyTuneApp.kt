package com.alananasss.kittytune

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.map.Mapper
import coil.request.Options
import com.alananasss.kittytune.utils.Config
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeLocale
import java.io.File
import java.util.Locale

class KittyTuneApp : Application(), ImageLoaderFactory {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Config.init(applicationContext)

        YouTube.locale = YouTubeLocale(
            gl = Locale.getDefault().country,
            hl = Locale.getDefault().language
        )
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
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
