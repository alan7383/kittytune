package com.alananasss.kittytune.ui.game

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackHighlightsTest {
    @Test
    fun `starts just before the loudest window`() {
        val samples = FloatArray(120) { if (it in 60 until 80) 1f else 0.1f }

        assertEquals(58_500L, TrackHighlights.startMs(120_000L, samples))
    }

    @Test
    fun `uses an intro skip when the waveform has no usable audio`() {
        assertEquals(36_000L, TrackHighlights.startMs(120_000L, null))
        assertEquals(36_000L, TrackHighlights.startMs(120_000L, FloatArray(120)))
    }

    @Test
    fun `plays short tracks and highlights at the beginning from zero`() {
        assertEquals(0L, TrackHighlights.startMs(60_000L, null))
        assertEquals(0L, TrackHighlights.startMs(120_000L, FloatArray(120) { if (it < 20) 1f else 0f }))
    }
}
