package com.alananasss.kittytune.util

import com.alananasss.kittytune.data.network.FreeTranslator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Translation of lyrics that touches only what the reader cannot already read (round 3 of the tester's list, 19).
 *
 * The old way sent every line to the translator, so a Russian reader got Russian lines "translated" into Russian and
 * a line with one English word turned into a rewritten sentence. Here each line is one of three things:
 *
 *  - **in the reader's language**: left alone, nothing is shown under it;
 *  - **a foreign sentence**: translated whole, by its meaning, and shown under it;
 *  - **the reader's language with a few foreign words in it**: only those words are glossed, `word — meaning`.
 *
 * The desktop app's copy of this file, kept identical on purpose: the same reader should see the
 * same translations on the phone as on the desktop.
 */
object SmartTranslation {

    /** How a line stands against the reader's language. */
    enum class Kind { NATIVE, FOREIGN, MIXED }

    /**
     * What to show under each line that needs it, keyed by the line's trimmed text; lines that are the reader's own
     * are absent.
     *
     * @param known whole-sentence translations already at hand (the lyrics provider's own), preferred over the machine's.
     */
    suspend fun forLines(lines: List<String>, readerLanguage: String, known: Map<String, String> = emptyMap()): Map<String, String> =
        withContext(Dispatchers.Default) {
            val reader = readerLanguage.substringBefore('-').substringBefore('_').lowercase()
            val texts = lines.map { it.trim() }.filter { it.isNotBlank() }.distinct()
            if (texts.isEmpty()) return@withContext emptyMap()

            // The song as a whole decides the short lines: "yeah" cannot be placed alone, but in a Spanish song it is not Spanish
            // for the reader either way, and in a Russian one it is a foreign word in Russian.
            val songIsForeign = runCatching { LanguageDetection.needsTranslation(texts.joinToString(". "), reader) }.getOrDefault(false)
            val kinds = texts.associateWith { classify(it, reader, songIsForeign) }

            val result = mutableMapOf<String, String>()

            val whole = kinds.filterValues { it == Kind.FOREIGN }.keys.toList()
            whole.forEach { line -> known[line]?.takeIf { it.isNotBlank() }?.let { result[line] = it } }
            val toTranslate = whole.filter { it !in result }
            if (toTranslate.isNotEmpty()) {
                FreeTranslator.translateMissing(toTranslate, reader).forEach { (line, translation) ->
                    // A translation that is the line again says nothing, so it is not shown.
                    if (!translation.trim().equals(line, ignoreCase = true)) result[line] = translation.trim()
                }
            }

            val mixed = kinds.filterValues { it == Kind.MIXED }.keys.toList()
            if (mixed.isNotEmpty()) {
                val wordsOf = mixed.associateWith { WordGloss.foreignWords(it, reader) }
                val all = wordsOf.values.flatten().distinctBy { it.lowercase() }
                val meanings = if (all.isEmpty()) emptyMap() else FreeTranslator.translateMissing(all, reader)
                for ((line, words) in wordsOf) {
                    val pairs = words.mapNotNull { word ->
                        val meaning = (meanings[word] ?: meanings[word.trim()])?.trim().orEmpty()
                        if (meaning.isBlank() || meaning.equals(word, ignoreCase = true)) null else word to meaning
                    }
                    if (pairs.isNotEmpty()) result[line] = pairs.joinToString("   ·   ") { (word, meaning) -> "$word — $meaning" }
                }
            }
            result
        }

    /** Which of the three a line is. Public so the choice can be tested without a network. */
    fun classify(line: String, reader: String, songIsForeign: Boolean): Kind = when {
        WordGloss.isMixed(line, reader) -> Kind.MIXED
        runCatching { LanguageDetection.needsTranslation(line, reader) }.getOrDefault(false) -> Kind.FOREIGN
        // Too short for the detector to name, in a song that is not the reader's language: it is not theirs either.
        songIsForeign && line.any { it.isLetter() } && wordCount(line) < SHORT_LINE_WORDS -> Kind.FOREIGN
        else -> Kind.NATIVE
    }

    private fun wordCount(line: String): Int = line.split(Regex("""\s+""")).count { word -> word.any { it.isLetter() } }

    /** Under this many words the detector will not name a language. */
    private const val SHORT_LINE_WORDS = 3
}
