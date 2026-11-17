package com.alananasss.kittytune.ui.player

import android.content.ContextWrapper
import android.content.SharedPreferences
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Track
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.lang.reflect.Proxy

/** Exercise the real preference and JSON persistence paths with an in-memory Android store. */
class QueuePersistenceTest {
    @get:Rule val temp = TemporaryFolder()
    private val values = mutableMapOf<String, Any?>()
    private val editor: SharedPreferences.Editor = Proxy.newProxyInstance(
        SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)
    ) { proxy, method, args ->
        when {
            method.name.startsWith("put") -> { values[args!![0] as String] = args[1]; proxy }
            method.name == "remove" -> { values.remove(args!![0] as String); proxy }
            method.name == "clear" -> { values.clear(); proxy }
            method.name == "commit" -> true
            else -> null
        }
    } as SharedPreferences.Editor
    private val store: SharedPreferences = Proxy.newProxyInstance(
        SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)
    ) { _, method, args ->
        when {
            method.name == "edit" -> editor
            method.name == "contains" -> values.containsKey(args!![0])
            method.name == "getAll" -> values.toMap()
            method.name.startsWith("get") -> values[args!![0]] ?: args[1]
            else -> null
        }
    } as SharedPreferences

    private fun preferences() = PlayerPreferences(object : ContextWrapper(null) {
        override fun getSharedPreferences(name: String?, mode: Int) = store
        override fun getFilesDir() = temp.root
    })
    private val repeated = Track(5, "Repeated", null, 180_000, null)
    private val queue get() = listOf(repeated, Track(2, "Other", null, 180_000, null), repeated.copy())

    @Test fun `saving and recreating preferences restores the second occurrence`() {
        val prefs = preferences()
        prefs.savePlaybackState(queue[2], 1200, queue, null, false, RepeatMode.NONE, 2)
        val restored = preferences()
        assertEquals(2, restored.getLastQueueIndex())
        assertEquals(listOf(5L, 2L, 5L), restored.getLastQueue().map { it.id })
        assertEquals(2, resolveQueueIndex(restored.getLastQueue(), restored.getLastTrack()?.id, restored.getLastQueueIndex()))
    }

    @Test fun `legacy saved state without a position remains readable`() {
        preferences().savePlaybackState(repeated, 0, queue, null, false, RepeatMode.NONE)
        values.remove("last_queue_index")
        val restored = preferences()
        assertEquals(-1, restored.getLastQueueIndex())
        assertEquals(0, resolveQueueIndex(restored.getLastQueue(), repeated.id, restored.getLastQueueIndex()))
    }

    @Test fun `clearing the queue does not resurrect the previous JSON file`() {
        val prefs = preferences()
        prefs.savePlaybackState(repeated, 0, queue, null, false, RepeatMode.NONE, 2)
        prefs.savePlaybackState(null, 0, emptyList(), null, false, RepeatMode.NONE, -1)
        assertTrue(preferences().getLastQueue().isEmpty())
        assertNull(preferences().getLastTrack())
        assertEquals(-1, preferences().getLastQueueIndex())
    }

    @Test fun `disabling queue persistence removes the stored index`() {
        val prefs = preferences()
        prefs.savePlaybackState(repeated, 0, queue, null, false, RepeatMode.NONE, 2)
        prefs.setPersistentQueueEnabled(false)
        prefs.savePlaybackState(repeated, 0, queue, null, false, RepeatMode.NONE, 2)
        assertFalse(values.containsKey("last_queue_index"))
        assertEquals(-1, preferences().getLastQueueIndex())
        assertTrue(preferences().getLastQueue().isEmpty())
    }
}
