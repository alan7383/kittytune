package com.alananasss.kittytune.util

import com.alananasss.kittytune.data.network.FreeTranslator
import java.util.Locale

/**
 * Word by word help for a text that mixes the reader's language with another one (round 3 of the tester's list, 27).
 *
 * "Bro, nice track" from a Russian reader is not a sentence to translate: "bro" and "nice" are what they need. So the
 * words written in another script than the reader's are picked out, translated together in one request, and listed
 * under the comment. Mentions and links are names, not words, and are never translated.
 *
 * Only the script is compared, the same way [LanguageDetection] compares it: a Latin word in an English comment is not
 * foreign to an English reader, and a Cyrillic word is not to a Russian one.
 *
 * The desktop app's copy of this file, kept identical on purpose: the two apps should gloss the
 * same words the same way for the same reader.
 */
object WordGloss {

    private val MENTION_OR_LINK = Regex("""(@[\w.\-]+)|(https?://\S+)|(\bwww\.\S+)""")
    private val SPACES = Regex("""\s+""")
    private val NOT_A_WORD = Regex("""[^\p{L}\p{Nd}\s]""")
    private const val MAX_WORDS = 12

    /** Whether [text] has words in both the reader's script and another one. A comment wholly in one is not "mixed". */
    fun isMixed(text: String, readerLanguage: String): Boolean {
        val readerScript = scriptOfLanguageName(readerLanguage) ?: return false
        val scripts = wordsOf(text).mapNotNull { scriptOf(it) }
        return scripts.any { it == readerScript } && scripts.any { it != readerScript }
    }

    /** The words of [text] that are written in another script than the reader's, each once, in order. */
    fun foreignWords(text: String, readerLanguage: String): List<String> {
        val readerScript = scriptOfLanguageName(readerLanguage) ?: return emptyList()
        return wordsOf(text)
            .filter { word -> scriptOf(word) != null && scriptOf(word) != readerScript }
            .distinctBy { it.lowercase() }
            .take(MAX_WORDS)
    }

    /** Each foreign word with its translation, or nothing when the translator has none for it. */
    suspend fun glossFor(text: String, readerLanguage: String): List<Pair<String, String>> {
        val words = foreignWords(text, readerLanguage)
        if (words.isEmpty()) return emptyList()
        val translated = FreeTranslator.translateMissing(words, readerLanguage.substringBefore('-').substringBefore('_').lowercase())
        return words.mapNotNull { word ->
            val translation = translated[word] ?: return@mapNotNull null
            // A translation that is the word itself, or differs only by case, says nothing.
            if (translation.equals(word, ignoreCase = true)) null else word to translation
        }
    }

    /** "nice → хорошо · bro → братан", the line shown under the comment. */
    fun line(glosses: List<Pair<String, String>>): String = glosses.joinToString("  ·  ") { (word, translation) -> "$word → $translation" }

    /** The words of [text] without mentions, links and punctuation. */
    private fun wordsOf(text: String): List<String> = text.replace(MENTION_OR_LINK, " ")
        .split(SPACES)
        .map { it.replace(NOT_A_WORD, "") }
        .filter { word -> word.any { it.isLetter() } }

    private fun scriptOf(word: String): Character.UnicodeScript? =
        word.filter { it.isLetter() }
            .groupingBy { letter ->
                when (val script = Character.UnicodeScript.of(letter.code)) {
                    Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA -> Character.UnicodeScript.HAN
                    else -> script
                }
            }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key

    /** The script the reader's own language is written in, from the language's name in that language. */
    private fun scriptOfLanguageName(languageTag: String): Character.UnicodeScript? {
        val code = languageTag.substringBefore('-').substringBefore('_').lowercase()
        val locale = Locale.forLanguageTag(code)
        return scriptOf(locale.getDisplayLanguage(locale))
    }
}
