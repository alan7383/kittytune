package com.alananasss.kittytune

import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.ui.library.AuraPalette
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The colour of the liked songs is made of their covers, drawn toward the theme (desktop PR #77 port). */
class AuraPaletteTest {

    private val theme = listOf(Color(0xFF3355FF), Color(0xFFAA33FF), Color(0xFF33AAFF))

    @Test
    fun `the most common cover colours come first, three at most`() {
        val covers = listOf(Color.Red to 5, Color.Green to 30, Color.Blue to 12, Color.Yellow to 2)
        val palette = AuraPalette.of(covers, theme)
        assertEquals(3, palette.colors.size)
        // Green is the biggest group: it leads, still mostly green after the pull toward the theme.
        assertTrue(palette.colors[0].green > palette.colors[0].red)
    }

    @Test
    fun `each colour is drawn toward the theme, not left as it was nor replaced`() {
        val palette = AuraPalette.of(listOf(Color(1f, 0f, 0f) to 10), theme)
        val drawn = palette.colors.single()
        assertTrue(drawn.red < 1f, "less red than the cover")
        assertTrue(drawn.blue > 0f, "some of the theme's blue")
        assertTrue(drawn.red > theme[0].red, "still more red than the theme")
    }

    @Test
    fun `covers too grey to have a colour leave the theme's own`() {
        val palette = AuraPalette.of(emptyList(), theme)
        assertEquals(theme, palette.colors)
        assertEquals(theme[0], palette.mix)
    }

    @Test
    fun `the mix is the colours weighted by how many covers each stands for`() {
        val palette = AuraPalette.of(listOf(Color.Red to 30, Color.Blue to 10), theme)
        assertTrue(palette.mix.red > palette.mix.blue, "three times as many red covers as blue ones")
        assertEquals(1f, palette.mix.alpha)
    }
}
