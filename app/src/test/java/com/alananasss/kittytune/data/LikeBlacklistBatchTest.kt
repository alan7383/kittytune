package com.alananasss.kittytune.data

import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class LikeBlacklistBatchTest {
    @Test fun `empty batch never touches preferences`() {
        updateLikeBlacklist(emptyList(), true, { error("read") }, { error("write") })
    }

    @Test fun `large batch reads and updates preferences once`() {
        var reads = 0
        var writes = 0
        var result = emptySet<String>()
        updateLikeBlacklist((1L..5000L).toList(), false, { reads++; emptySet() }, { writes++; result = it })
        assertEquals(1, reads)
        assertEquals(1, writes)
        assertEquals(5000, result.size)
    }

    @Test fun `liking removes requested IDs and preserves unrelated exclusions`() {
        var result = setOf("1", "2", "3")
        updateLikeBlacklist(listOf(1L, 2L, 1L), true, { result }, { result = it })
        assertEquals(setOf("3"), result)
    }

    @Test fun `negative IDs and malformed saved values retain legacy behavior`() {
        var result = setOf("-2", "01", "bad", "9223372036854775808")
        updateLikeBlacklist(listOf(-3L), false, { result }, { result = it })
        assertEquals(setOf("-2", "1", "-3"), result)
    }

    @Test fun `unchanged membership still canonicalizes saved values`() {
        var result = setOf("001", "bad")
        updateLikeBlacklist(listOf(99L), true, { result }, { result = it })
        assertEquals(setOf("1"), result)
    }

    @Test fun `random mixed batches match the original per item algorithm`() {
        val random = Random(128)
        repeat(100) {
            var actual = (0..80).map { random.nextInt(-30, 120).toString() }.toSet() + "bad" + "0001"
            var expected = actual
            repeat(8) {
                val ids = List(random.nextInt(0, 70)) { random.nextLong(-50, 150) }
                val liked = random.nextBoolean()
                for (id in ids) {
                    val next = expected.mapNotNull { it.toLongOrNull() }.toMutableSet()
                    if (liked) next.remove(id) else next.add(id)
                    expected = next.map { it.toString() }.toSet()
                }
                updateLikeBlacklist(ids, liked, { actual }, { actual = it })
                assertEquals(expected, actual)
            }
        }
    }
}
