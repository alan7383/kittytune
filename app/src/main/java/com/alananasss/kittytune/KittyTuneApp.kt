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
