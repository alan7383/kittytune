package com.alananasss.kittytune.utils

import android.content.Context
import android.content.res.Configuration
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.PlayerPreferences
import java.util.Locale

object LocaleUtils {

    private val initialDefaultLocale: Locale = Locale.getDefault()

    fun getSystemLocale(): Locale {
        return try {
            val locales = android.content.res.Resources.getSystem().configuration.locales
            if (!locales.isEmpty) locales[0] else initialDefaultLocale
        } catch (_: Throwable) {
            initialDefaultLocale
        }
    }

    fun getSystemLocaleList(): android.os.LocaleList {
        return try {
            val locales = android.content.res.Resources.getSystem().configuration.locales
            if (!locales.isEmpty) locales else android.os.LocaleList(initialDefaultLocale)
        } catch (_: Throwable) {
            android.os.LocaleList(initialDefaultLocale)
        }
    }

    fun getLocaleForLanguage(language: AppLanguage): Locale {
        return if (language == AppLanguage.SYSTEM) {
            getSystemLocale()
        } else {
            Locale.forLanguageTag(language.code)
        }
    }

    fun getLocaleListForLanguage(language: AppLanguage): android.os.LocaleList {
        return if (language == AppLanguage.SYSTEM) {
            getSystemLocaleList()
        } else {
            android.os.LocaleList(Locale.forLanguageTag(language.code))
        }
    }

    fun getLocale(context: Context): Locale {
        val prefs = PlayerPreferences(context)
        return getLocaleForLanguage(prefs.getAppLanguage())
    }

    fun getLocaleList(context: Context): android.os.LocaleList {
        val prefs = PlayerPreferences(context)
        return getLocaleListForLanguage(prefs.getAppLanguage())
    }

    fun getAcceptLanguageForLanguage(language: AppLanguage): String {
        return when (language) {
            AppLanguage.ENGLISH -> "en-US,en;q=0.9"
            AppLanguage.FRENCH -> "fr-FR,fr;q=0.9,en;q=0.8"
            AppLanguage.HUNGARIAN -> "hu-HU,hu;q=0.9,en;q=0.8"
            AppLanguage.RUSSIAN -> "ru-RU,ru;q=0.9,en;q=0.8"
            AppLanguage.VIETNAMESE -> "vi-VN,vi;q=0.9,en;q=0.8"
            AppLanguage.SYSTEM -> {
                val defaultLocale = getSystemLocale()
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

    fun getAcceptLanguage(context: Context): String {
        val prefs = PlayerPreferences(context)
        return getAcceptLanguageForLanguage(prefs.getAppLanguage())
    }

    fun applyAppLanguage(context: Context) {
        val targetLocale = getLocale(context)
        val targetLocaleList = getLocaleList(context)

        Locale.setDefault(targetLocale)

        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocales(targetLocaleList)
        config.setLayoutDirection(targetLocale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)

        val appCtx = context.applicationContext
        if (appCtx != null && appCtx !== context) {
            val appRes = appCtx.resources
            val appConfig = Configuration(appRes.configuration)
            appConfig.setLocales(targetLocaleList)
            appConfig.setLayoutDirection(targetLocale)
            @Suppress("DEPRECATION")
            appRes.updateConfiguration(appConfig, appRes.displayMetrics)
        }

        try {
            com.zionhuang.innertube.YouTube.locale = com.zionhuang.innertube.models.YouTubeLocale(
                gl = targetLocale.country.ifBlank { "US" },
                hl = targetLocale.language.ifBlank { "en" }
            )
        } catch (_: Throwable) {
        }
    }

    fun updateBaseContextLocale(context: Context): Context {
        val targetLocale = getLocale(context)
        val targetLocaleList = getLocaleList(context)

        Locale.setDefault(targetLocale)

        val config = Configuration(context.resources.configuration)
        config.setLocales(targetLocaleList)
        config.setLayoutDirection(targetLocale)

        @Suppress("DEPRECATION")
        context.resources.updateConfiguration(config, context.resources.displayMetrics)

        val appCtx = context.applicationContext
        if (appCtx != null && appCtx !== context) {
            @Suppress("DEPRECATION")
            appCtx.resources.updateConfiguration(config, appCtx.resources.displayMetrics)
        }

        return context.createConfigurationContext(config)
    }
}
