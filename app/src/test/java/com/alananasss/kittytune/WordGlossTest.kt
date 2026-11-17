package com.alananasss.kittytune

import com.alananasss.kittytune.util.WordGloss
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WordGlossTest {

    @Test
    fun latin_words_in_a_russian_comment_are_the_foreign_ones() {
        assertEquals(listOf("nice", "bro"), WordGloss.foreignWords("Это nice трек, bro", "ru"))
    }

    @Test
    fun mentions_and_links_are_never_taken_for_words() {
        assertEquals(listOf("nice"), WordGloss.foreignWords("@dj_kitty nice https://example.com/track трек", "ru"))
    }

    @Test
    fun a_cyrillic_word_is_foreign_to_an_english_reader() {
        assertEquals(listOf("привет"), WordGloss.foreignWords("hello привет", "en"))
    }

    @Test
    fun a_comment_in_the_readers_own_script_has_no_foreign_words() {
        assertTrue(WordGloss.foreignWords("Отличный трек, спасибо", "ru").isEmpty())
    }

    @Test
    fun only_a_mix_counts_as_mixed() {
        assertTrue(WordGloss.isMixed("Это nice трек", "ru"))
        assertFalse(WordGloss.isMixed("Это просто трек", "ru"))
        assertFalse(WordGloss.isMixed("nice track bro", "ru"))
    }

    @Test
    fun the_line_reads_word_arrow_translation() {
        assertEquals("nice → хорошо  ·  bro → братан", WordGloss.line(listOf("nice" to "хорошо", "bro" to "братан")))
    }
}
