package com.alananasss.kittytune.data.musicimport

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

/**
 * Persists the connected musicapi integration for each platform, plus the
 * state needed to manage an in-progress transfer (Transfer your gems).
 */
class MusicImportStorage(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("music_import", Context.MODE_PRIVATE)

    private val gson = com.alananasss.kittytune.utils.AppUtils.gson

    private fun platformKey(platform: String): String = "auth_$platform"
    private fun revertKey(platform: String): String = "revert_$platform"

    fun saveAuth(platform: String, auth: MusicApiAuth) {
        prefs.edit().putString(platformKey(platform), gson.toJson(auth)).apply()
    }

    fun getAuth(platform: String): MusicApiAuth? {
        val raw = prefs.getString(platformKey(platform), null) ?: return null
        return runCatching { gson.fromJson(raw, MusicApiAuth::class.java) }.getOrNull()
    }

    fun clearAuth(platform: String) {
        prefs.edit().remove(platformKey(platform)).apply()
    }

    fun setLikesRevertState(platform: String, syncId: String) {
        prefs.edit().putString(revertKey(platform), syncId).apply()
    }

    fun getLikesRevertState(platform: String): String? = prefs.getString(revertKey(platform), null)
}
