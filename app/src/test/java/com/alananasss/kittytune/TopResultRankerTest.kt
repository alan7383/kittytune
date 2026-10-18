package com.alananasss.kittytune

import com.alananasss.kittytune.domain.TopResultRanker
import com.alananasss.kittytune.domain.User
import org.junit.Test
import kotlin.test.assertEquals

/** The best search result between accounts that share a name (issue #66). */
class TopResultRankerTest {

    private val smallerFirst = User(id = 1L, username = "Kai Angel", avatarUrl = null, followersCount = 39_000, trackCount = 180)
    private val biggerSecond = User(id = 2L, username = "Kai Angel", avatarUrl = null, followersCount = 95_000, trackCount = 40)

    @Test
    fun `the bigger audience wins over the account listed first with more uploads`() {
        val top = TopResultRanker.findTopArtist(listOf(smallerFirst, biggerSecond), "kai angel")
        assertEquals(biggerSecond.id, top?.id)
    }

    @Test
    fun `an account the listener keeps picking for the query comes first`() {
        val top = TopResultRanker.findTopArtist(listOf(smallerFirst, biggerSecond), "kai angel") { user ->
            if (user.id == smallerFirst.id) 2 else 0
        }
        assertEquals(smallerFirst.id, top?.id)
    }
}
