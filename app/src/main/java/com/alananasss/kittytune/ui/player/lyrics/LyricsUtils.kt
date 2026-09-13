package com.alananasss.kittytune.ui.player.lyrics

import com.alananasss.kittytune.data.lyrics.parsers.QRCParser
import com.alananasss.kittytune.data.lyrics.parsers.TTMLParser
import com.mpatric.mp3agic.Mp3File
import java.io.File
import java.util.regex.Pattern

data class LyricWord(
    val word: String,
    val startTime: Long,
    val endTime: Long,
    val isBackground: Boolean = false
) {
    val text: String get() = word
}

enum class LyricSinger {
    DEFAULT,
    SINGER_1,
    SINGER_2,
    BOTH
}

data class LyricLine(
    val text: String,
    val startTime: Long,
    val endTime: Long,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val romanization: String? = null,
    val singer: LyricSinger = LyricSinger.DEFAULT,
    val isBackground: Boolean = false,
    val isInstrumental: Boolean = false,
    val durationMs: Long = 0L,
    val agent: String? = null
)

fun isRtlText(text: String): Boolean {
    for (ch in text) {
        when (Character.getDirectionality(ch)) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_EMBEDDING,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_OVERRIDE -> return true
            Character.DIRECTIONALITY_LEFT_TO_RIGHT,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_EMBEDDING,
            Character.DIRECTIONALITY_LEFT_TO_RIGHT_OVERRIDE -> return false
        }
    }
    return false
}

fun String.toLyricsWrappingUnits(): List<String> {
    if (isEmpty()) return emptyList()

    val units = mutableListOf<String>()
    val currentWord = StringBuilder()
    val characterIterator = java.text.BreakIterator.getCharacterInstance(java.util.Locale.ROOT)
    characterIterator.setText(this)

    fun flushCurrentWord() {
        if (currentWord.isNotEmpty()) {
            units += currentWord.toString()
            currentWord.clear()
        }
    }

    var start = characterIterator.first()
    var end = characterIterator.next()
    while (end != java.text.BreakIterator.DONE) {
        val grapheme = substring(start, end)
        val codePoint = grapheme.codePointAt(0)
        when {
            grapheme.all(Char::isWhitespace) -> {
                currentWord.append(grapheme)
                flushCurrentWord()
            }
            codePoint.isCjkCodePoint() -> {
                flushCurrentWord()
                units += grapheme
            }
            else -> {
                currentWord.append(grapheme)
            }
        }
        start = end
        end = characterIterator.next()
    }
    flushCurrentWord()

    return units
}

private fun Int.isCjkCodePoint(): Boolean =
    when (Character.UnicodeScript.of(this)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA -> true
        else -> false
    }

private val NoSpaceAfterChars: Set<Char> = setOf('(', '[', '{', '«', '‹', '“', '‘')

fun shouldAppendWordSpace(
    current: String,
    next: String,
): Boolean {
    if (current.isEmpty() || next.isEmpty()) return false
    val last = current.last()
    val first = next.first()
    if (last.isWhitespace() || first.isWhitespace()) return false
    if (!first.isLetterOrDigit()) return false
    return last !in NoSpaceAfterChars
}

fun formatLyricWordContents(
    lineText: String,
    words: List<LyricWord>,
): List<String> {
    if (words.isEmpty()) return emptyList()

    val decodedWords = words.map { LyricsUtils.decodeHtmlEntities(it.word) }

    if (decodedWords.any { it.endsWith(" ") || it.startsWith(" ") }) {
        return decodedWords
    }

    val cleanLine = LyricsUtils.decodeHtmlEntities(lineText).trim()

    if (decodedWords.joinToString("").trim() == cleanLine) {
        return decodedWords
    }

    val result = ArrayList<String>(decodedWords.size)
    var searchIndex = 0
    for (i in decodedWords.indices) {
        val word = decodedWords[i]
        val pos = if (searchIndex < cleanLine.length) cleanLine.indexOf(word, searchIndex) else -1
        if (pos != -1) {
            searchIndex = pos + word.length
            var hasTrailingSpaceInLine = false
            while (searchIndex < cleanLine.length && cleanLine[searchIndex].isWhitespace()) {
                hasTrailingSpaceInLine = true
                searchIndex++
            }
            if (hasTrailingSpaceInLine) {
                result.add("$word ")
            } else {
                result.add(word)
            }
        } else {
            if (i < decodedWords.size - 1) {
                result.add("$word ")
            } else {
                result.add(word)
            }
        }
    }
    return result
}

object LyricsUtils {

        private val NUMERIC_ENTITY_REGEX = Regex("&#(x|X)?([0-9a-fA-F]+);")

        fun decodeHtmlEntities(text: String): String {
            if (!text.contains('&')) return text
            var out = text
            if (out.contains("&apos;")) out = out.replace("&apos;", "'")
            if (out.contains("&quot;")) out = out.replace("&quot;", "\"")
            if (out.contains("&lt;")) out = out.replace("&lt;", "<")
            if (out.contains("&gt;")) out = out.replace("&gt;", ">")
            if (out.contains("&nbsp;")) out = out.replace("&nbsp;", " ")
            if (out.contains("&copy;")) out = out.replace("&copy;", "©")
            if (out.contains("&trade;")) out = out.replace("&trade;", "™")
            if (out.contains("&ndash;")) out = out.replace("&ndash;", "–")
            if (out.contains("&mdash;")) out = out.replace("&mdash;", "—")
            if (out.contains("&bull;")) out = out.replace("&bull;", "•")
            if (out.contains("&hellip;")) out = out.replace("&hellip;", "…")

            if (out.contains("&#")) {
                out = NUMERIC_ENTITY_REGEX.replace(out) { match ->
                    val isHex = match.groupValues[1].isNotEmpty()
                    val digits = match.groupValues[2]
                    val codePoint = digits.toIntOrNull(if (isHex) 16 else 10)
                    if (codePoint != null && Character.isValidCodePoint(codePoint)) {
                        String(Character.toChars(codePoint))
                    } else {
                        match.value
                    }
                }
            }

            if (out.contains("&amp;")) {
                out = out.replace("&amp;", "&")
                if (out.contains("&#") || out.contains("&quot;") || out.contains("&apos;") || out.contains("&lt;") || out.contains("&gt;")) {
                    return decodeHtmlEntities(out)
                }
            }

            return out
        }

        fun activeLineIndex(lines: List<LyricLine>, positionMs: Long): Int =
            lines.indexOfLast { positionMs >= it.startTime }

        private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

        private val ENHANCED_WORD_PATTERN = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")

        fun insertInstrumentalBreaks(lines: List<LyricLine>): List<LyricLine> {
            if (lines.isEmpty()) return lines
            val result = mutableListOf<LyricLine>()
            if (lines.first().startTime > 5000L) {
                result.add(
                    LyricLine(
                        text = "",
                        startTime = 0L,
                        endTime = lines.first().startTime,
                        isInstrumental = true,
                        durationMs = lines.first().startTime
                    )
                )
            }
            for (i in lines.indices) {
                val current = lines[i]
                result.add(current)
                if (i < lines.size - 1) {
                    val next = lines[i + 1]
                    val gap = next.startTime - current.endTime
                    if (gap >= 4000L) {
                        result.add(
                            LyricLine(
                                text = "",
                                startTime = current.endTime,
                                endTime = next.startTime,
                                isInstrumental = true,
                                durationMs = gap
                            )
                        )
                    }
                }
            }
            return result
        }

        fun parseLyricsContent(content: String, totalDurationMs: Long): List<LyricLine> {
            val decodedContent = decodeHtmlEntities(content)
            val trimmed = decodedContent.trim()
            val parsed = when {
                trimmed.startsWith("<tt") || trimmed.contains("<tt ") || trimmed.contains("<tt:") || trimmed.startsWith("<?xml") -> {
                    parseTtml(trimmed, totalDurationMs)
                }
                QRCParser.isQrc(trimmed) -> {
                    parseQrc(trimmed, totalDurationMs)
                }
                trimmed.startsWith("version:") -> {
                    parseLyricsFile(decodedContent, totalDurationMs)
                }
                else -> {
                    parseLrc(decodedContent, totalDurationMs)
                }
            }
            return insertInstrumentalBreaks(parsed)
        }

        fun parseTtml(ttml: String, totalDurationMs: Long): List<LyricLine> {
            val parsedLines = TTMLParser.parseTTML(ttml)
            if (parsedLines.isEmpty()) return emptyList()
            return parsedLines.map { line ->
                val words = line.words.filter { it.text.isNotEmpty() }.map { word ->
                    LyricWord(
                        word = decodeHtmlEntities(word.text),
                        startTime = (word.startTime * 1000.0).toLong(),
                        endTime = (word.endTime * 1000.0).toLong(),
                    )
                }
                LyricLine(
                    text = decodeHtmlEntities(line.text),
                    startTime = (line.startTime * 1000.0).toLong(),
                    endTime = (line.endTime * 1000.0).toLong(),
                    words = words,
                    translation = line.providerTranslationText?.let(::decodeHtmlEntities),
                    romanization = line.providerRomanizedText?.let(::decodeHtmlEntities),
                    singer = when (line.agent?.lowercase()) {
                        "v1", "singer1" -> LyricSinger.SINGER_1
                        "v2", "singer2" -> LyricSinger.SINGER_2
                        "both", "all", "v1000" -> LyricSinger.BOTH
                        else -> LyricSinger.DEFAULT
                    },
                    isBackground = line.isBackground,
                    agent = line.agent
                )
            }
        }

        fun parseQrc(qrc: String, totalDurationMs: Long): List<LyricLine> {
            val parsedLines = QRCParser.parseQrc(qrc)
            if (parsedLines.isEmpty()) return emptyList()
            return parsedLines.map { line ->
                val words = line.words.filter { it.text.isNotEmpty() }.map { word ->
                    LyricWord(
                        word = decodeHtmlEntities(word.text),
                        startTime = (word.startTime * 1000.0).toLong(),
                        endTime = (word.endTime * 1000.0).toLong(),
                    )
                }
                LyricLine(
                    text = decodeHtmlEntities(line.text),
                    startTime = (line.startTime * 1000.0).toLong(),
                    endTime = (line.endTime * 1000.0).toLong(),
                    words = words,
                    singer = when (line.agent?.lowercase()) {
                        "v1", "singer1" -> LyricSinger.SINGER_1
                        "v2", "singer2" -> LyricSinger.SINGER_2
                        "both", "all", "v1000" -> LyricSinger.BOTH
                        else -> LyricSinger.DEFAULT
                    }
                )
            }
        }

        private fun parseLyricsFile(yamlContent: String, totalDurationMs: Long): List<LyricLine> {
            val parsedLines = mutableListOf<LyricLine>()
            try {
                val rawLines = yamlContent.split("\n")
                    .map { it.trimEnd() }
                    .filter { it.isNotBlank() }
                    .filter { !it.trimStart().startsWith("#") }
                if (rawLines.isEmpty()) return emptyList()

                val listIndent = rawLines.firstOrNull { it.trim() == "lines:" || it.trim() == "words:" }?.let { indentOf(it) }
                val listMarker = rawLines.indexOfFirst { it.trim() == "lines:" || it.trim() == "words:" }
                if (listIndent == null || listMarker < 0) return emptyList()

                val itemIndent = listIndent + 1

                var i = listMarker + 1
                while (i < rawLines.size && indentOf(rawLines[i]) > listIndent) {
                    val line = rawLines[i].trim()
                    if (!line.startsWith("- ")) { i++; continue }

                    val lineMap = HashMap<String, Any>()
                    var key = ""
                    var value: String? = null
                    val kv = parseKeyValue(line.substring(2).trim())
                    if (kv != null) { key = kv.first; value = kv.second }

                    var j = i + 1
                    var isWordList = false
                    val wordMaps = mutableListOf<Map<String, Any>>()
                    var wordMap = HashMap<String, Any>()

                    while (j < rawLines.size && indentOf(rawLines[j]) > listIndent) {
                        val subLine = rawLines[j]
                        val subIndent = indentOf(subLine)
                        val subTrim = subLine.trim()

                        if (subTrim.startsWith("- ") && subIndent > itemIndent) {
                            wordMap = HashMap()
                            val wordKv = parseKeyValue(subTrim.substring(2).trim())
                            if (wordKv != null) wordMap[wordKv.first] = if (wordKv.first == "text") wordKv.second.removeSurrounding("\"").trim() else parseValue(wordKv.second)
                            wordMaps.add(wordMap)
                            j++
                            continue
                        }

                        val subKv = parseKeyValue(subTrim)
                        if (subKv != null) {
                            if (subKv.first == "words") {
                                isWordList = true
                            } else if (isWordList && subIndent > itemIndent) {
                                if (wordMaps.isEmpty()) wordMaps.add(wordMap)
                                wordMap[subKv.first] = if (subKv.first == "text") subKv.second.removeSurrounding("\"").trim() else parseValue(subKv.second)
                            } else {
                                lineMap[subKv.first] = if (subKv.first == "text") subKv.second.removeSurrounding("\"").trim() else parseValue(subKv.second)
                            }
                        }
                        j++
                    }

                    if (key.isNotEmpty()) lineMap[key] = if (key == "text") value?.removeSurrounding("\"")?.trim() ?: "" else parseValue(value ?: "")

                    val text = lineMap["text"] as? String
                    val start = (lineMap["start_ms"] as? Number)?.toLong()
                    if (text != null && start != null) {
                        val end = (lineMap["end_ms"] as? Number)?.toLong() ?: totalDurationMs
                        val words = wordMaps.mapNotNull { w ->
                            val wText = w["text"] as? String ?: return@mapNotNull null
                            val wStart = (w["start_ms"] as? Number)?.toLong() ?: return@mapNotNull null
                            val wEnd = (w["end_ms"] as? Number)?.toLong() ?: end
                            LyricWord(decodeHtmlEntities(wText), wStart, wEnd)
                        }
                        val singer = when ((lineMap["singer"] as? String)?.lowercase()
                            ?: (lineMap["singer"] as? Number)?.toString()) {
                            "1", "singer1", "v1" -> LyricSinger.SINGER_1
                            "2", "singer2", "v2" -> LyricSinger.SINGER_2
                            "both", "3", "v3", "group" -> LyricSinger.BOTH
                            else -> LyricSinger.DEFAULT
                        }
                        parsedLines.add(LyricLine(decodeHtmlEntities(text), start, end, words, singer = singer))
                    }

                    i = j
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return parsedLines
        }

        private fun indentOf(line: String): Int = line.length - line.trimStart().length

        private fun parseKeyValue(part: String): Pair<String, String>? {
            val idx = part.indexOf(":")
            if (idx <= 0) return null
            return part.substring(0, idx).trim() to part.substring(idx + 1).trim()
        }

        private fun parseValue(raw: String): Any {
            val value = raw.removeSurrounding("\"").trim()
            return value.toLongOrNull() ?: value.toDoubleOrNull() ?: value
        }

        fun parseLrc(lrcContent: String, totalDurationMs: Long): List<LyricLine> {
            val lines = lrcContent.split("\n")
            val parsedLines = mutableListOf<ParsedLineTemp>()

            for (line in lines) {
                val matcher = LRC_PATTERN.matcher(line.trim())
                if (matcher.matches()) {
                    val min = matcher.group(1)?.toLong() ?: 0
                    val sec = matcher.group(2)?.toLong() ?: 0
                    val msStr = matcher.group(3) ?: "00"
                    val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()

                    val rawText = decodeHtmlEntities(matcher.group(4)?.trim() ?: "")
                    val startTime = (min * 60 * 1000) + (sec * 1000) + ms

                    var singer = LyricSinger.DEFAULT
                    var processedText = rawText
                    val lower = rawText.trim().lowercase()
                    when {
                        lower.startsWith("v1:") || lower.startsWith("[v1]") || lower.startsWith("(v1)") || lower.startsWith("[singer1]") -> {
                            singer = LyricSinger.SINGER_1
                            processedText = processedText.trim().replaceFirst(Regex("^(?i)(v1:|\\[v1\\]|\\(v1\\)|\\[singer1\\])\\s*"), "")
                        }
                        lower.startsWith("v2:") || lower.startsWith("[v2]") || lower.startsWith("(v2)") || lower.startsWith("[singer2]") -> {
                            singer = LyricSinger.SINGER_2
                            processedText = processedText.trim().replaceFirst(Regex("^(?i)(v2:|\\[v2\\]|\\(v2\\)|\\[singer2\\])\\s*"), "")
                        }
                        lower.startsWith("v3:") || lower.startsWith("[v3]") || lower.startsWith("(v3)") || lower.startsWith("[both]") || lower.startsWith("[all]") -> {
                            singer = LyricSinger.BOTH
                            processedText = processedText.trim().replaceFirst(Regex("^(?i)(v3:|\\[v3\\]|\\(v3\\)|\\[both\\]|\\[all\\])\\s*"), "")
                        }
                        lower.startsWith("[bg:") && lower.endsWith("]") -> {
                            singer = LyricSinger.SINGER_2
                            processedText = processedText.trim().removePrefix("[bg:").removeSuffix("]").trim()
                        }
                    }

                    val words = mutableListOf<LyricWord>()
                    var cleanText = processedText
                    if (processedText.contains("<")) {
                        val wordMatcher = ENHANCED_WORD_PATTERN.matcher(processedText)
                        val extractedWords = mutableListOf<LyricWord>()
                        while (wordMatcher.find()) {
                            val wMin = wordMatcher.group(1)?.toLong() ?: 0
                            val wSec = wordMatcher.group(2)?.toLong() ?: 0
                            val wMsStr = wordMatcher.group(3) ?: "00"
                            val wMs = if (wMsStr.length == 2) wMsStr.toLong() * 10 else wMsStr.toLong()
                            val wText = decodeHtmlEntities(wordMatcher.group(4) ?: "")

                            val wTime = (wMin * 60 * 1000) + (wSec * 1000) + wMs
                            extractedWords.add(LyricWord(wText, wTime, 0L))
                        }
                        if (extractedWords.isNotEmpty()) {
                            cleanText = extractedWords.joinToString("") { it.word }.trim()
                            for (idx in extractedWords.indices) {
                                val current = extractedWords[idx]
                                val nextTime = if (idx < extractedWords.size - 1) extractedWords[idx + 1].startTime else 0L
                                words.add(current.copy(endTime = nextTime))
                            }
                        }
                    }

                    cleanText = decodeHtmlEntities(cleanText)
                    if (cleanText.isNotEmpty()) {
                        parsedLines.add(ParsedLineTemp(cleanText, startTime, words, singer))
                    }
                }
            }

            if (parsedLines.isEmpty()) return emptyList()

            return parsedLines.mapIndexed { index, current ->
                val nextTime = if (index < parsedLines.size - 1) {
                    parsedLines[index + 1].startTime
                } else {
                    totalDurationMs
                }
                val updatedWords = current.words.map { word ->
                    if (word.endTime == 0L) word.copy(endTime = nextTime) else word
                }
                LyricLine(current.text, current.startTime, nextTime, updatedWords, singer = current.singer)
            }
        }

        private data class ParsedLineTemp(
            val text: String,
            val startTime: Long,
            val words: List<LyricWord> = emptyList(),
            val singer: LyricSinger = LyricSinger.DEFAULT
        )

        fun extractLocalLyrics(filePath: String): String? {
            return try {
                val file = File(filePath)
                if (!file.exists()) return null

                val mp3file = Mp3File(filePath)
                if (mp3file.hasId3v2Tag()) {
                    val tag = mp3file.id3v2Tag
                    tag.lyrics
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

