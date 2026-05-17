package com.alananasss.kittytune.utils

import android.content.Context
import android.util.Log

object Config {
    private const val PREFS_NAME = "app_config"
    private const val KEY_CLIENT_ID = "dynamic_client_id"

    // keeping this public just in case
    const val FALLBACK_ID = "7K3no7iJj8d02d20Z26Z26Z26Z26Z26"
    const val OFFICIAL_CLIENT_ID = "QOFuKCOeAXIph267vzqj3B1wb65cZVAQ"
    const val OFFICIAL_CLIENT_SECRET = "EhBDsGIj9EbuBbRf0QkhH9Fq9BX3yN4B"

    val OFFICIAL_CLIENT_SIGNATURE: String by lazy {
        calculateSignature(OFFICIAL_CLIENT_ID, OFFICIAL_CLIENT_SECRET)
    }

    private fun calculateSignature(clientId: String, clientSecret: String): String {
        try {
            val concat = "$clientId:$clientSecret".toByteArray(Charsets.UTF_8)
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(concat)
            return android.util.Base64.encodeToString(digest, android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP)
        } catch (e: Exception) {
            return "ztQ_RKaMCPavrjcXvMT6t0STPQ0vU2cY4YKGVeLU6Iw"
        }
    }

    const val BASE_URL = "https://api-v2.soundcloud.com/"
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    var CLIENT_ID: String = FALLBACK_ID
        private set

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // try to load saved id, otherwise default to fallback
        CLIENT_ID = prefs.getString(KEY_CLIENT_ID, FALLBACK_ID) ?: FALLBACK_ID
        Log.d("Config", "Client ID initialized: $CLIENT_ID")
    }

    fun updateClientId(context: Context, newId: String) {
        if (newId.isNotBlank() && newId != CLIENT_ID) {
            CLIENT_ID = newId
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CLIENT_ID, newId).apply()
            Log.d("Config", "🔥 FRESH CLIENT ID SNAGGED: $newId")
        }
    }
}


