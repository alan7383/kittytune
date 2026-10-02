package com.alananasss.kittytune

import com.alananasss.kittytune.data.mix.MixArtistMatch
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Choosing which SoundCloud account "in the style of X" meant (issue #33).
 *
 * "J'écris snorunt, un vrai artiste qui existe sur SoundCloud, et il me sort absolument rien alors que c'est un
 * artiste connu quoi… bizarre qu'il trouve rien non ?"
 *
 * It was, and the reason is the first test below — a live capture of what their API answered. `search/users` is
 * not ordered by prominence, so the mix was seeded on somebody with seven followers, no catalogue and no station,
 * and correctly reported that it had found nothing.
 */
class MixArtistMatchTest {

    private fun account(id: Long, name: String?, followers: Long) =
        MixArtistMatch.Account(id = id, username = name, followers = followers)

    /**
     * The real response, verbatim, from `search/users?q=snorunt&limit=3`. Taking the first row picks the account
     * with seven followers; the artist is the second.
     */
    private val snorunt = listOf(
        account(1348994805, "Snorunt", 7),
        account(905453668, "snorunt★", 10_011),
        account(1715787728, "j'aime snorunt", 0),
    )

    @Test
    fun `the artist wins over whoever happens to be listed first`() {
        assertEquals(905453668L, MixArtistMatch.best(snorunt, "snorunt"))
    }

    /** Their own decoration is not a difference: `snorunt★` is snorunt. */
    @Test
    fun `decoration in a username is ignored`() {
        val decorated = listOf(account(1, "Yeat", 10), account(2, "𝐘𝐄𝐀𝐓 ✪", 500_000))
        assertEquals(2L, MixArtistMatch.best(decorated, "yeat"))
    }

    @Test
    fun `case and spacing do not matter`() {
        val accounts = listOf(account(1, "Fred Again..", 900_000), account(2, "notfred", 10))
        assertEquals(1L, MixArtistMatch.best(accounts, "fred again"))
    }

    /**
     * The filter matters as much as the count, and this is why. Without it, a short query finds whichever huge
     * unrelated account happens to contain those letters — the same bug pointing the other way.
     */
    @Test
    fun `a huge unrelated account does not win on followers alone`() {
        val accounts = listOf(
            account(1, "ian", 40_000),
            account(2, "Brian Eno Official Fan Uploads", 4_000_000),
        )
        // "brian" contains "ian", so containment alone would pick the fan page. It does not, because the query is
        // compared both ways and `ian` is the closer of the two.
        assertEquals(1L, MixArtistMatch.best(accounts, "ian"))
    }

    /** A name nobody matched still gets an answer: their best guess beats not building a mix at all. */
    @Test
    fun `nothing resembling the name falls back to the most followed`() {
        val accounts = listOf(account(1, "completely other", 5), account(2, "also unrelated", 900))
        assertEquals(2L, MixArtistMatch.best(accounts, "snorunt"))
    }

    @Test
    fun `an empty search answers nothing rather than guessing`() {
        assertNull(MixArtistMatch.best(emptyList(), "snorunt"))
    }

    /** A blank query is not a reason to crash, and their search sometimes returns nameless accounts. */
    @Test
    fun `blank names and blank queries are survivable`() {
        assertEquals(2L, MixArtistMatch.best(listOf(account(1, null, 10), account(2, "x", 20)), "x"))
        assertEquals(2L, MixArtistMatch.best(listOf(account(1, "a", 1), account(2, "b", 9)), "   "))
    }
}
