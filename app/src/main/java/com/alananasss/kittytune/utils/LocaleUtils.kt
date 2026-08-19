package com.alananasss.kittytune.utils

import android.content.Context
import android.content.res.Configuration
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.PlayerPreferences
import java.util.Locale

object LocaleUtils {

    fun getLocale(context: Context): Locale {
        val prefs = PlayerPreferences(context)
        val language = prefs.getAppLanguage()
        return if (language == AppLanguage.SYSTEM) {
            Locale.getDefault()
        } else {
            Locale(language.code)
        }
    }

    fun getAcceptLanguage(context: Context): String {
        val prefs = PlayerPreferences(context)
        val language = prefs.getAppLanguage()
        return when (language) {
            AppLanguage.ENGLISH -> "en-US,en;q=0.9"
            AppLanguage.FRENCH -> "fr-FR,fr;q=0.9,en;q=0.8"
            AppLanguage.HUNGARIAN -> "hu-HU,hu;q=0.9,en;q=0.8"
            AppLanguage.RUSSIAN -> "ru-RU,ru;q=0.9,en;q=0.8"
            AppLanguage.SYSTEM -> {
                val defaultLocale = Locale.getDefault()
                val lang = defaultLocale.language.ifBlank { "en" }
                val country = defaultLocale.country
                if (country.isNotBlank()) {
                    "$lang-$country,$lang;q=0.9,en;q=0.8"
                } else {
                    "$lang;q=0.9,en;q=0.8"
                }
            }
        }
    }

    fun applyAppLanguage(context: Context) {
        val prefs = PlayerPreferences(context)
        val language = prefs.getAppLanguage()
        if (language != AppLanguage.SYSTEM) {
            val locale = Locale(language.code)
            Locale.setDefault(locale)

            val res = context.resources
            val config = Configuration(res.configuration)
            config.setLocale(locale)
            config.setLayoutDirection(locale)
            @Suppress("DEPRECATION")
            res.updateConfiguration(config, res.displayMetrics)

            val appCtx = context.applicationContext
            if (appCtx != null && appCtx !== context) {
                val appRes = appCtx.resources
                val appConfig = Configuration(appRes.configuration)
                appConfig.setLocale(locale)
                appConfig.setLayoutDirection(locale)
                @Suppress("DEPRECATION")
                appRes.updateConfiguration(appConfig, appRes.displayMetrics)
            }
        }
    }

    fun updateBaseContextLocale(context: Context): Context {
        val prefs = PlayerPreferences(context)
        val language = prefs.getAppLanguage()
        if (language == AppLanguage.SYSTEM) return context

        val locale = Locale(language.code)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        return context.createConfigurationContext(config)
    }
}
