package com.alananasss.kittytune

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alananasss.kittytune.data.local.LyricsLineAnimation
import com.alananasss.kittytune.data.local.PlayerPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy

class PlayerPreferencesDefaultsTest {

    @get:Rule val temp = TemporaryFolder()

    private fun createPreferences(initialValues: Map<String, Any> = emptyMap()): Pair<PlayerPreferences, MutableMap<String, Any>> {
        val values = initialValues.toMutableMap()
        val editor = Proxy.newProxyInstance(
            SharedPreferences.Editor::class.java.classLoader,
            arrayOf(SharedPreferences.Editor::class.java)
        ) { proxy, method, args ->
            when (method.name) {
                "putString" -> { values[args!![0] as String] = args[1]; proxy }
                "putBoolean" -> { values[args!![0] as String] = args[1]; proxy }
                "putInt" -> { values[args!![0] as String] = args[1]; proxy }
                "putFloat" -> { values[args!![0] as String] = args[1]; proxy }
                "putLong" -> { values[args!![0] as String] = args[1]; proxy }
                "remove" -> { values.remove(args!![0] as String); proxy }
                "apply", "commit" -> true
                else -> proxy
            }
        } as SharedPreferences.Editor

        val store = Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when {
                method.name == "edit" -> editor
                method.name == "contains" -> values.containsKey(args!![0])
                method.name == "getAll" -> values.toMap()
                method.name.startsWith("get") -> values[args!![0]] ?: args[1]
                else -> null
            }
        } as SharedPreferences

        val prefs = PlayerPreferences(object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int) = store
            override fun getFilesDir() = temp.root
        })
        return prefs to values
    }

    @Test
    fun `bottom menu items default does not include feed`() {
        val (prefs, _) = createPreferences()
        val items = prefs.getBottomMenuItems()
        assertEquals(listOf("home", "search", "genres", "library"), items)
        assertFalse(items.contains("feed"))
    }

    @Test
    fun `feed tab is stripped if previously enabled by migration`() {
        val (prefs, values) = createPreferences(
            mapOf(
                "bottom_menu_items_csv" to "home,feed,search,genres,library",
                "feed_tab_migrated" to true
            )
        )
        val items = prefs.getBottomMenuItems()
        assertEquals(listOf("home", "search", "genres", "library"), items)
        assertFalse(items.contains("feed"))
        assertTrue(values["feed_default_off_cleanup"] as Boolean)
        assertFalse(values.containsKey("feed_tab_migrated"))
    }

    @Test
    fun `lyrics line animation defaults to fade and sweep is disabled`() {
        val (prefs, _) = createPreferences()
        assertFalse(prefs.getLyricsSweepEnabled())
        assertEquals(LyricsLineAnimation.FADE, prefs.getLyricsLineAnimation())
    }

    @Test
    fun `lyrics line animation is force disabled even if user had enabled it`() {
        val (prefs, _) = createPreferences(
            mapOf("lyrics_line_animation_v2" to "SWEEP")
        )
        // First read forces it back to FADE
        assertFalse(prefs.getLyricsSweepEnabled())
        assertEquals(LyricsLineAnimation.FADE, prefs.getLyricsLineAnimation())

        // Explicit re-enable afterwards works as intended
        prefs.setLyricsSweepEnabled(true)
        assertTrue(prefs.getLyricsSweepEnabled())
        assertEquals(LyricsLineAnimation.SWEEP, prefs.getLyricsLineAnimation())
    }
}
