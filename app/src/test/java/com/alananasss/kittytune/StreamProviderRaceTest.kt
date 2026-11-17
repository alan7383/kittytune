package com.alananasss.kittytune

import com.alananasss.kittytune.data.StreamResolver
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Stream sources asked at once, the user's order still deciding between answers (issue #66). */
class StreamProviderRaceTest {

    private fun after(ms: Long, value: String?): suspend () -> String? = { delay(ms); value }

    @Test
    fun `the first source wins when it answers within the grace`() = runBlocking {
        val result = StreamResolver.raceInOrder(listOf(after(300, "first"), after(10, "second")), graceMs = 1_000)
        assertEquals("first", result)
    }

    @Test
    fun `a slow first source does not hold up an answer below it`() = runBlocking {
        val started = System.currentTimeMillis()
        val result = StreamResolver.raceInOrder(listOf(after(5_000, "first"), after(10, "second")), graceMs = 200)
        assertEquals("second", result)
        assertTrue(System.currentTimeMillis() - started < 2_000)
    }

    @Test
    fun `sources without the track are skipped`() = runBlocking {
        assertEquals("third", StreamResolver.raceInOrder(listOf(after(10, null), after(20, null), after(30, "third"))))
        assertNull(StreamResolver.raceInOrder(listOf(after(10, null), after(20, null))))
    }
}
