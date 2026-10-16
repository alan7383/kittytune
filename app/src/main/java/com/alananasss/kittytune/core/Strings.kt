package com.alananasss.kittytune.core

import com.alananasss.kittytune.KittyTuneApp

object Strings {
    fun get(key: String, vararg args: Any?): String {
        val app = KittyTuneApp.instance
        val resId = app.resources.getIdentifier(key, "string", app.packageName)
        if (resId == 0) return key
        return if (args.isEmpty()) {
            app.getString(resId)
        } else {
            val nonNullArgs = args.map { it ?: "" }.toTypedArray()
            app.getString(resId, *nonNullArgs)
        }
    }
}

/** Terse helper mirroring `stringResource(...)` call-sites: `str("app_name")`. */
fun str(key: String): String = Strings.get(key)
fun str(key: String, vararg args: Any?): String = Strings.get(key, *args)
