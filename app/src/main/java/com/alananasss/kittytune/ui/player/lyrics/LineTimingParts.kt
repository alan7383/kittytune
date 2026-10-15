package com.alananasss.kittytune.ui.player.lyrics

import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable

/**
 * The backing vocals of a lyrics line, taken out of it (issue #66, ported from desktop).
 *
 * "I'm falling (falling down)" is sung as "I'm falling" with "falling down" behind it: the main line carries the
 * words sung in front, the backing line the ones in brackets or marked by the source, and the views that draw a
 * line as text show the one under the other while the line is sung.
 */

/**
 * The line's backing vocals, written in round brackets, taken out of it. Several sets of brackets give one
 * backing line, in the order they are sung, separated by commas. Returns the line as it is, and null, when there
 * is nothing in brackets or nothing outside them, since a line that is all backing vocal is still the line.
 */
internal fun splitBackingVocals(text: String): Pair<String, String?> {
    val backing = BACKING_VOCAL.findAll(text).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
    if (backing.isEmpty()) return text to null
    val main = text.replace(BACKING_VOCAL, " ").replace(SPACES, " ").trim()
    if (main.none { it.isLetterOrDigit() }) return text to null
    return main to backing.joinToString(BACKING_SEPARATOR)
}

/**
 * The same for a line timed word by word, whose source did not mark its backing vocals: the words between
 * round brackets, with their own timings, brackets dropped, one list per set of brackets in the order they
 * are sung. Sources other than Apple's write backing vocals this way, and the line used to show them in
 * brackets inside it (issue #66).
 *
 * @return the line's own words and the backing groups; the words unchanged and no groups when nothing is in
 *   brackets or everything is.
 */
internal fun splitBackingWords(words: List<LyricWord>): Pair<List<LyricWord>, List<List<LyricWord>>> {
    val main = mutableListOf<LyricWord>()
    val groups = mutableListOf<List<LyricWord>>()
    var current = mutableListOf<LyricWord>()
    var depth = 0
    for (word in words) {
        val opens = word.text.count { it == '(' }
        val closes = word.text.count { it == ')' }
        val inBrackets = depth > 0 || opens > 0
        val bare = word.text.replace("(", "").replace(")", "")
        if (inBrackets) {
            if (bare.isNotBlank()) current += word.copy(word = bare.trim())
            depth = (depth + opens - closes).coerceAtLeast(0)
            if (depth == 0 && current.isNotEmpty()) {
                groups += current
                current = mutableListOf()
            }
        } else {
            main += word
        }
    }
    if (current.isNotEmpty()) groups += current
    if (groups.isEmpty() || main.none { w -> w.text.any { it.isLetterOrDigit() } }) return words to emptyList()
    return main to groups
}

/** What goes between two backing-vocal groups drawn on one line. */
internal const val BACKING_SEPARATOR = ", "

/**
 * [line] with its backing vocals as a line of their own, for the views that draw a line as text: the words in
 * brackets, or the words the source marked as background, in the order sung and separated by commas. Returns
 * the line unchanged and null when it has none.
 */
internal fun separateBackingVocals(line: LyricLine): Pair<LyricLine, LyricLine?> {
    if (line.words.isNotEmpty()) {
        val marked = line.words.filter { it.isBackground }
        val (own, groups) = if (marked.isNotEmpty()) {
            line.words.filterNot { it.isBackground } to listOf(marked)
        } else {
            splitBackingWords(line.words)
        }
        if (groups.isEmpty() || own.isEmpty()) return line to null
        val backingWords = groups.flatMapIndexed { groupIndex, group ->
            group.mapIndexed { wordIndex, word ->
                val text = word.text.trim()
                word.copy(
                    word = when {
                        wordIndex < group.lastIndex -> "$text "
                        groupIndex < groups.lastIndex -> text + BACKING_SEPARATOR
                        else -> text
                    },
                )
            }
        }
        val main = line.copy(text = own.joinToString("") { it.text }.trim(), words = own)
        val backing = LyricLine(
            text = backingWords.joinToString("") { it.text },
            startTime = backingWords.first().startTime,
            endTime = backingWords.maxOf { it.endTime },
            words = backingWords,
            isBackground = true,
        )
        return main to backing
    }
    val (mainText, backingText) = splitBackingVocals(line.text)
    if (backingText == null) return line to null
    return line.copy(text = mainText) to
        LyricLine(text = backingText, startTime = line.startTime, endTime = line.endTime, isBackground = true)
}

private val BACKING_VOCAL = Regex("""\(([^()]*)\)""")
private val SPACES = Regex("""\s{2,}""")

/** The words of [text], all lit the moment the line starts: a line-timed line that is drawn as karaoke. */
internal fun instantSyllables(text: String, start: Int, end: Int): List<KaraokeSyllable> =
    text.toLyricsWrappingUnits().ifEmpty { listOf(text) }.map { word ->
        KaraokeSyllable(content = word, start = start, end = (start + 1).coerceAtMost(end), phonetic = null)
    }

/**
 * The words of [text] lighting up one after another from [start], quickly, the way a word-timed line does but
 * over a fraction of a second, so the line arrives instead of snapping to white.
 */
internal fun revealSyllables(text: String, start: Int, end: Int): List<KaraokeSyllable> {
    val words = text.toLyricsWrappingUnits().ifEmpty { listOf(text) }
    val step = revealStepMs(words.size)
    return words.mapIndexed { index, word ->
        val wordStart = (start + index * step).coerceAtMost(end - 1).coerceAtLeast(start)
        // Each word takes two steps to fill, so the next one has already begun: a wave, not a ticker.
        val wordEnd = (wordStart + step * 2).coerceAtMost(end).coerceAtLeast(wordStart + 1)
        KaraokeSyllable(content = word, start = wordStart, end = wordEnd, phonetic = null)
    }
}

/** How long [revealSyllables] takes to light [text] completely, for whatever follows it. */
internal fun revealDurationMs(text: String): Int {
    val count = text.toLyricsWrappingUnits().size.coerceAtLeast(1)
    return revealStepMs(count) * (count + 1)
}

private fun revealStepMs(wordCount: Int): Int =
    (REVEAL_TOTAL_MS / wordCount.coerceAtLeast(1)).coerceIn(REVEAL_MIN_STEP_MS, REVEAL_MAX_STEP_MS)

/** The whole line lights within about this long, however many words it has. */
private const val REVEAL_TOTAL_MS = 700
private const val REVEAL_MIN_STEP_MS = 30
private const val REVEAL_MAX_STEP_MS = 90
