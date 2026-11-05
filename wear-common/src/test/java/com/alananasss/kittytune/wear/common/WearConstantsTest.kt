package com.alananasss.kittytune.wear.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WearConstantsTest {

    @Test
    fun testDataClientPaths_startWithPrefix() {
        assertTrue(WearConstants.PATH_PLAYBACK_STATE.startsWith("/kittytune/"))
        assertTrue(WearConstants.PATH_AVAILABLE_TARGETS.startsWith("/kittytune/"))
        assertEquals("/kittytune/playback_state", WearConstants.PATH_PLAYBACK_STATE)
    }

    @Test
    fun testMessageClientActionPaths_startWithPlaybackOrTargetPrefix() {
        val actions = listOf(
            WearConstants.ACTION_PLAY,
            WearConstants.ACTION_PAUSE,
            WearConstants.ACTION_PLAY_PAUSE,
            WearConstants.ACTION_NEXT,
            WearConstants.ACTION_PREVIOUS,
            WearConstants.ACTION_VOLUME_UP,
            WearConstants.ACTION_VOLUME_DOWN,
            WearConstants.ACTION_SET_VOLUME,
            WearConstants.ACTION_LIKE_TOGGLE,
            WearConstants.ACTION_SHUFFLE_TOGGLE,
            WearConstants.ACTION_REPEAT_CYCLE,
            WearConstants.ACTION_SYNC_REQUEST,
            WearConstants.ACTION_TARGET_SELECT,
        )

        for (action in actions) {
            assertTrue("Action $action should start with /kittytune/", action.startsWith("/kittytune/"))
        }

        // Ensure all actions are distinct
        assertEquals(actions.size, actions.toSet().size)
    }

    @Test
    fun testDataMapKeys_areNonEmptyAndDistinct() {
        val keys = listOf(
            WearConstants.KEY_TRACK_ID,
            WearConstants.KEY_TITLE,
            WearConstants.KEY_ARTIST,
            WearConstants.KEY_ALBUM,
            WearConstants.KEY_ARTWORK_URL,
            WearConstants.KEY_IS_PLAYING,
            WearConstants.KEY_IS_LOADING,
            WearConstants.KEY_POSITION_MS,
            WearConstants.KEY_DURATION_MS,
            WearConstants.KEY_VOLUME,
            WearConstants.KEY_TARGET_DEVICE,
            WearConstants.KEY_TARGET_TYPE,
            WearConstants.KEY_TARGET_DEVICE_ID,
            WearConstants.KEY_IS_LIKED,
            WearConstants.KEY_SHUFFLE_ENABLED,
            WearConstants.KEY_REPEAT_MODE,
            WearConstants.KEY_AVAILABLE_TARGETS,
            WearConstants.KEY_TIMESTAMP,
        )

        for (key in keys) {
            assertTrue("Key must not be empty", key.isNotEmpty())
        }

        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun testTargetDeviceConstants() {
        assertEquals("PHONE", WearConstants.TARGET_TYPE_PHONE)
        assertEquals("DESKTOP", WearConstants.TARGET_TYPE_DESKTOP)
        assertNotEquals(WearConstants.TARGET_TYPE_PHONE, WearConstants.TARGET_TYPE_DESKTOP)
    }
}
