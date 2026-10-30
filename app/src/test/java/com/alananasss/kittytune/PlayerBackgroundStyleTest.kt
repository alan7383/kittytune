package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.FullPlayerBgStyle
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerDesign
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerBackgroundStyleTest {

    private fun mapPlayerBackgroundStyleToFull(style: PlayerBackgroundStyle): FullPlayerBgStyle = when (style) {
        PlayerBackgroundStyle.APPLE_MUSIC -> FullPlayerBgStyle.APPLE_MUSIC
        PlayerBackgroundStyle.BLUR -> FullPlayerBgStyle.BLUR
        PlayerBackgroundStyle.GRADIENT -> FullPlayerBgStyle.GRADIENT
        PlayerBackgroundStyle.THEME -> FullPlayerBgStyle.PURE_BLACK
    }

    private fun mapFullPlayerBgStyleToBackground(style: FullPlayerBgStyle): PlayerBackgroundStyle = when (style) {
        FullPlayerBgStyle.APPLE_MUSIC -> PlayerBackgroundStyle.APPLE_MUSIC
        FullPlayerBgStyle.BLUR -> PlayerBackgroundStyle.BLUR
        FullPlayerBgStyle.GRADIENT -> PlayerBackgroundStyle.GRADIENT
        FullPlayerBgStyle.PURE_BLACK -> PlayerBackgroundStyle.THEME
    }

    private fun isEffectiveBlurMode(bgStyle: FullPlayerBgStyle, isBlurModeParam: Boolean): Boolean {
        return isBlurModeParam || (bgStyle == FullPlayerBgStyle.BLUR ||
            bgStyle == FullPlayerBgStyle.APPLE_MUSIC ||
            bgStyle == FullPlayerBgStyle.GRADIENT ||
            bgStyle == FullPlayerBgStyle.PURE_BLACK)
    }

    @Test
    fun testPlayerBackgroundStyleToFullPlayerBgStyleMapping() {
        assertEquals(FullPlayerBgStyle.APPLE_MUSIC, mapPlayerBackgroundStyleToFull(PlayerBackgroundStyle.APPLE_MUSIC))
        assertEquals(FullPlayerBgStyle.BLUR, mapPlayerBackgroundStyleToFull(PlayerBackgroundStyle.BLUR))
        assertEquals(FullPlayerBgStyle.GRADIENT, mapPlayerBackgroundStyleToFull(PlayerBackgroundStyle.GRADIENT))
        assertEquals(FullPlayerBgStyle.PURE_BLACK, mapPlayerBackgroundStyleToFull(PlayerBackgroundStyle.THEME))
    }

    @Test
    fun testFullPlayerBgStyleToPlayerBackgroundStyleMapping() {
        assertEquals(PlayerBackgroundStyle.APPLE_MUSIC, mapFullPlayerBgStyleToBackground(FullPlayerBgStyle.APPLE_MUSIC))
        assertEquals(PlayerBackgroundStyle.BLUR, mapFullPlayerBgStyleToBackground(FullPlayerBgStyle.BLUR))
        assertEquals(PlayerBackgroundStyle.GRADIENT, mapFullPlayerBgStyleToBackground(FullPlayerBgStyle.GRADIENT))
        assertEquals(PlayerBackgroundStyle.THEME, mapFullPlayerBgStyleToBackground(FullPlayerBgStyle.PURE_BLACK))
    }

    @Test
    fun testEveryPlayerBackgroundStyleHasMapping() {
        for (style in PlayerBackgroundStyle.entries) {
            val fullStyle = mapPlayerBackgroundStyleToFull(style)
            val mappedBack = mapFullPlayerBgStyleToBackground(fullStyle)
            assertEquals("Round-trip mapping failed for $style", style, mappedBack)
        }
    }

    @Test
    fun testEffectiveBlurModeResolution() {
        // All four standard styles require high contrast (white text/icons)
        assertTrue(isEffectiveBlurMode(FullPlayerBgStyle.APPLE_MUSIC, false))
        assertTrue(isEffectiveBlurMode(FullPlayerBgStyle.BLUR, false))
        assertTrue(isEffectiveBlurMode(FullPlayerBgStyle.GRADIENT, false))
        assertTrue(isEffectiveBlurMode(FullPlayerBgStyle.PURE_BLACK, false))

        // If explicitly forced by parameter
        assertTrue(isEffectiveBlurMode(FullPlayerBgStyle.APPLE_MUSIC, true))
    }

    @Test
    fun testTabletSmallestWidthCalculation() {
        // Tablet requires smallest width >= 600dp
        fun isTablet(widthDp: Int, heightDp: Int): Boolean = minOf(widthDp, heightDp) >= 600

        // Phone portrait (e.g. 411 x 891)
        assertFalse(isTablet(411, 891))
        // Phone landscape (e.g. 891 x 411)
        assertFalse(isTablet(891, 411))
        // Tablet portrait (e.g. 800 x 1280)
        assertTrue(isTablet(800, 1280))
        // Tablet landscape (e.g. 1280 x 800)
        assertTrue(isTablet(1280, 800))
        // Tablet split-screen 50/50 (e.g. 450 x 800)
        assertFalse(isTablet(450, 800))
    }
}
