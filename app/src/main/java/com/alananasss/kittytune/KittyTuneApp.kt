package com.alananasss.kittytune

import android.app.Application
import com.alananasss.kittytune.utils.Config
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.YouTubeLocale
import java.util.Locale

class KittyTuneApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
        Config.init(applicationContext)

        YouTube.locale = YouTubeLocale(
            gl = Locale.getDefault().country,
            hl = Locale.getDefault().language
        )
    }

    companion object {
        lateinit var instance: KittyTuneApp
            private set
    }
}
