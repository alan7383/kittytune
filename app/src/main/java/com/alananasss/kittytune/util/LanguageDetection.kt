package com.alananasss.kittytune.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.Locale

/**
 * Whether a text reads as a language the reader does not (ported from desktop, PR #77 — rewritten for
 * Android's own language identifier).
 *
 * The script decides first: a text in another script always needs translating, and Cyrillic text is Russian
 * when it has no letter outside the Russian alphabet. Same-script text goes to ML Kit's on-device language
 * identifier (the Android-native stand-in for the desktop's lingua engine, with none of its 77 MB of models),
 * wrapped in runCatching so a missing Play-services install degrades to script-only answers rather than
 * failing.
 */
object LanguageDetection {

    private const val MIN_WORDS_TO_DETECT = 3
    private val RUSSIAN_LETTERS = "абвгдеёжзийклмнопрстуфхцчшщъыьэюя"
    private val WHITESPACE = Regex("\\s+")
    /** @mentions and links are names, not prose: "@dj_kitty nice track" is Russian text in Latin letters. */
    private val MENTION_OR_LINK = Regex("(@[\\w.\\-]+)|(https?://\\S+)|(\\bwww\\.\\S+)")
    private val NOT_A_WORD = Regex("[^\\p{L}\\p{Nd}\\s]")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Whether a text is worth offering to translate into [readerLanguage] (an ISO 639-1 or BCP-47 code,
     * a region suffix is ignored). Blocking; call off the UI thread.
     *
     * Asking only "is the best guess the reader's language?" offered to translate Russian
     * into Russian (issue #66). So the alphabet decides first: a text in another script always
     * needs translating, and Cyrillic text is Russian when it has no letter outside the Russian
     * alphabet. A short text in the reader's own script is left alone, and the rest goes to the
     * identifier, which has to name a language other than the reader's.
     */
    fun needsTranslation(text: String, readerLanguage: String): Boolean {
        val words = text.replace(MENTION_OR_LINK, " ").replace(NOT_A_WORD, " ").trim()
        val textScript = dominantScript(words) ?: return false
        val readerCode = readerLanguage.substringBefore('-').substringBefore('_').lowercase()
        val readerLocale = Locale.forLanguageTag(readerCode)
        // A language's own name is written in its own script: "русский", "English", "日本語".
        val readerScript = dominantScript(readerLocale.getDisplayLanguage(readerLocale))
        if (readerScript != null && readerScript != textScript) return true
        if (readerCode == "ru" && textScript == Character.UnicodeScript.CYRILLIC) {
            return words.any { it.isLetter() && it.lowercaseChar() !in RUSSIAN_LETTERS }
        }
        // One or two words are too little to tell "banger" from Dutch, and rarely need translating.
        if (words.split(WHITESPACE).count { word -> word.any { it.isLetter() } } < MIN_WORDS_TO_DETECT) return false

        val identified = identifiedLanguage(words) ?: return false
        // "und" is the identifier saying it does not know; that is not a foreign language.
        if (identified == "und") return false
        return identified.substringBefore('-') != readerCode.substringBefore('-')
    }

    /** The identifier's best BCP-47 tag for [text], or null when it is unavailable or unsure. */
    private fun identifiedLanguage(text: String): String? = runCatching {
        val client = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
        try {
            com.google.android.gms.tasks.Tasks.await(client.identifyLanguage(text))
        } finally {
            client.close()
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

    /** The script most of the letters are in, with the Japanese and Chinese scripts counted as one. */
    private fun dominantScript(text: String): Character.UnicodeScript? =
        text.filter { it.isLetter() }
            .groupingBy { letter ->
                when (val script = Character.UnicodeScript.of(letter.code)) {
                    Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> Character.UnicodeScript.HAN
                    else -> script
                }
            }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key
}
