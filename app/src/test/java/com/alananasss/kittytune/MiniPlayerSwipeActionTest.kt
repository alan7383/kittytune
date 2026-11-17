package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.MiniPlayerSwipeAction
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniPlayerSwipeActionTest {

    @Test
    fun testFromStringReturnsCorrectAction() {
        assertEquals(MiniPlayerSwipeAction.CHANGE_TRACK, MiniPlayerSwipeAction.fromString("CHANGE_TRACK"))
        assertEquals(MiniPlayerSwipeAction.DISMISS, MiniPlayerSwipeAction.fromString("DISMISS"))
        assertEquals(MiniPlayerSwipeAction.CHANGE_TRACK, MiniPlayerSwipeAction.fromString(null))
        assertEquals(MiniPlayerSwipeAction.CHANGE_TRACK, MiniPlayerSwipeAction.fromString("INVALID_ACTION"))
    }
}
