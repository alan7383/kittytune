package com.alananasss.kittytune.wear

import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.wear.presentation.theme.KittyTuneWearColorScheme
import com.alananasss.kittytune.wear.presentation.theme.WearBackground
import com.alananasss.kittytune.wear.presentation.theme.WearPrimary
import com.alananasss.kittytune.wear.presentation.theme.WearTertiary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests verifying Wear OS companion configuration, baseline theme,
 * and contract parameters for :wear module.
 */
class WearCompanionTest {

    @Test
    fun `wear theme uses true black background for AMOLED battery efficiency`() {
        assertEquals("Background must be pure black on Wear OS", Color(0xFF000000), KittyTuneWearColorScheme.background)
        assertEquals("WearBackground constant must be pure black", Color(0xFF000000), WearBackground)
    }

    @Test
    fun `wear theme primary accent matches KittyTune signature palette with high contrast`() {
        assertEquals(Color(0xFF90CAF9), KittyTuneWearColorScheme.primary)
        assertEquals(WearPrimary, KittyTuneWearColorScheme.primary)
        assertNotEquals(KittyTuneWearColorScheme.background, KittyTuneWearColorScheme.primary)
    }

    @Test
    fun `wear theme tertiary accent is configured for desktop status indication`() {
        assertEquals(Color(0xFFFFB74D), KittyTuneWearColorScheme.tertiary)
        assertEquals(WearTertiary, KittyTuneWearColorScheme.tertiary)
    }

    @Test
    fun `manifest contract defines mandatory wear os parameters`() {
        val watchFeature = "android.hardware.type.watch"
        val standaloneKey = "com.google.android.wearable.standalone"
        val standaloneValue = "true"
        val expectedPackage = "com.alananasss.kittytune.wear"
        val expectedAppId = "com.alananasss.kittytune"
        val expectedActivity = "com.alananasss.kittytune.wear.presentation.MainActivity"

        val permissions = listOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.WAKE_LOCK",
            "android.permission.VIBRATE"
        )

        assertEquals("android.hardware.type.watch", watchFeature)
        assertEquals("com.google.android.wearable.standalone", standaloneKey)
        assertEquals("true", standaloneValue)
        assertEquals("com.alananasss.kittytune.wear", expectedPackage)
        assertEquals("com.alananasss.kittytune", expectedAppId)
        assertEquals("com.alananasss.kittytune.wear.presentation.MainActivity", expectedActivity)
        assertTrue(permissions.contains("android.permission.INTERNET"))
        assertTrue(permissions.contains("android.permission.VIBRATE"))
    }
}
