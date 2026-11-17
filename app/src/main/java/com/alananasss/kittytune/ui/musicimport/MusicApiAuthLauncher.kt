package com.alananasss.kittytune.ui.musicimport

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.browser.customtabs.CustomTabsIntent
import com.alananasss.kittytune.data.musicimport.MusicApi

/**
 * Launches the musicapi connection flow in a Custom Tab, mirroring the
 * official app's WebAuthenticationStarter (which uses a TWA).
 * The flow ends by redirecting to sc://musicapi/auth?data64=...
 */
object MusicApiAuthLauncher {
    private const val TAG = "MusicApiAuthLauncher"
    private const val AUTH_BASE_URL = "https://app.musicapi.com/soundcloud/"
    private const val RETURN_URL = "soundcloud://musicapi/auth"

    fun authUrl(platform: MusicApi): String =
        AUTH_BASE_URL + platform.providerName + "/auth?returnUrl=" + RETURN_URL

    fun launch(context: Context, platform: MusicApi) {
        val uri = Uri.parse(authUrl(platform))
        try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                .build()
                .launchUrl(context, uri)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No Custom Tabs provider, falling back to ACTION_VIEW", e)
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            )
        }
    }
}
