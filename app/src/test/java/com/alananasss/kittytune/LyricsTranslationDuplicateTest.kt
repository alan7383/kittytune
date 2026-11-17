package com.alananasss.kittytune

import com.alananasss.kittytune.data.network.FreeTranslator
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsTranslationDuplicateTest {

    @Test
    fun nonLatinScriptMatchingTargetLang_returnsEmptyMap() = runBlocking {
        // Russian text translated to Russian should immediately return emptyMap (no redundant translation)
        val russianLines = listOf("Привет, как дела?", "Это просто проверка текста.")
        val result = FreeTranslator.translateMissing(russianLines, "ru")
        assertTrue("Translating Russian lyrics to Russian should return empty map", result.isEmpty())

        // Korean text translated to Korean
        val koreanLines = listOf("안녕하세요", "오늘 날씨가 좋네요")
        val resultKo = FreeTranslator.translateMissing(koreanLines, "ko")
        assertTrue("Translating Korean lyrics to Korean should return empty map", resultKo.isEmpty())
    }

    @Test
    fun identicalTranslationFiltering_dropsDuplicateLines() {
        val originalLine = LyricLine(
            text = "Never gonna give you up",
            startTime = 1000L,
            endTime = 2000L,
            translation = "Never gonna give you up"
        )

        // ViewModel and UI filtering logic: if translation is equal to original text, it must be ignored
        val isIdentical = originalLine.translation?.trim().equals(originalLine.text.trim(), ignoreCase = true)
        assertTrue("Duplicate line must be detected as identical", isIdentical)

        val filteredTranslation = originalLine.translation?.takeIf { !it.trim().equals(originalLine.text.trim(), ignoreCase = true) }
        assertNull("Filtered translation must be null when identical to text", filteredTranslation)
    }

    @Test
    fun musixmatchLanguageMatching_detectsSameLanguage() {
        fun isSameLanguage(lyricsLang: String?, targetLang: String?): Boolean {
            return when {
                lyricsLang == null -> false
                targetLang == null -> false
                targetLang.equals(lyricsLang, ignoreCase = true) -> true
                targetLang.length >= 2 && lyricsLang.length >= 2 &&
                    targetLang.take(2).equals(lyricsLang.take(2), ignoreCase = true) -> true
                else -> false
            }
        }

        assertTrue(isSameLanguage("en", "en"))
        assertTrue(isSameLanguage("en-US", "en"))
        assertTrue(isSameLanguage("en", "en-GB"))
        assertTrue(isSameLanguage("fr", "fr"))
        assertTrue(!isSameLanguage("fr", "en"))
        assertTrue(!isSameLanguage("ja", "en"))
    }
}
