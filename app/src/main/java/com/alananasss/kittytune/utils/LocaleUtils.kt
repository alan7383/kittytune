package com.alananasss.kittytune.utils

import android.content.Context
import android.content.res.Configuration
import com.alananasss.kittytune.data.local.AppLanguage
import com.alananasss.kittytune.data.local.PlayerPreferences
import java.util.Locale

object LocaleUtils {
    fun updateBaseContextLocale(context: Context): Context {
        // grab the language pref
        val prefs = PlayerPreferences(context)
        val language = prefs.getAppLanguage()

        // if system default, don't touch anything
        if (language == AppLanguage.SYSTEM) return context

        // otherwise force the locale
        val locale = Locale(language.code)
        Locale.setDefault(locale)

        // create a new config with this locale
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)

        // return the modded context
        return context.createConfigurationContext(config)
    }
}