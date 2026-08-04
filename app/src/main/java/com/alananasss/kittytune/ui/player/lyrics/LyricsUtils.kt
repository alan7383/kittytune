    package com.alananasss.kittytune.ui.player.lyrics
    
    import com.mpatric.mp3agic.Mp3File
    import java.io.File
    import java.util.regex.Pattern
    
    data class LyricWord(
        val word: String,
        val startTime: Long,
        val endTime: Long
    )

    // basic holder for a timed line
    data class LyricLine(
        val text: String,
        val startTime: Long,
        val endTime: Long,
        val words: List<LyricWord>? = null,
        val translation: String? = null,
        val romanization: String? = null
    )
    
    object LyricsUtils {
    
        // standard lrc regex: [mm:ss.xx] lyrics
        private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")
    
        // enhanced lrc word markers: <mm:ss.xx>word
        private val ENHANCED_WORD_PATTERN = Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)")
    
        fun parseLyricsContent(content: String, totalDurationMs: Long): List<LyricLine> {
            return if (content.trim().startsWith("version:")) {
                parseLyricsFile(content, totalDurationMs)
            } else {
                parseLrc(content, totalDurationMs)
            }
        }
    
        // Lightweight parser for the InnerTune-style YAML lyrics format:
        // version: 1
        // lines:
        //   - text: "..."
        //     start_ms: 123
        //     end_ms: 456
        //     words:
        //       - text: "..."
        //         start_ms: 123
        //         end_ms: 234
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
                            LyricWord(wText, wStart, wEnd)
                        }
                        parsedLines.add(LyricLine(text, start, end, words.ifEmpty { null }))
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
                    // handle 2 digit vs 3 digit milliseconds
                    val ms = if (msStr.length == 2) msStr.toLong() * 10 else msStr.toLong()
    
                    val rawText = matcher.group(4)?.trim() ?: ""
                    val startTime = (min * 60 * 1000) + (sec * 1000) + ms
    
                    val words = mutableListOf<LyricWord>()
                    var cleanText = rawText
                    if (rawText.contains("<")) {
                        val wordMatcher = ENHANCED_WORD_PATTERN.matcher(rawText)
                        val extractedWords = mutableListOf<LyricWord>()
                        while (wordMatcher.find()) {
                            val wMin = wordMatcher.group(1)?.toLong() ?: 0
                            val wSec = wordMatcher.group(2)?.toLong() ?: 0
                            val wMsStr = wordMatcher.group(3) ?: "00"
                            val wMs = if (wMsStr.length == 2) wMsStr.toLong() * 10 else wMsStr.toLong()
                            val wText = wordMatcher.group(4) ?: ""
    
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
    
                    if (cleanText.isNotEmpty()) {
                        parsedLines.add(ParsedLineTemp(cleanText, startTime, words))
                    }
                }
            }
    
            if (parsedLines.isEmpty()) return emptyList()
    
            // calculate end times based on the next line
            return parsedLines.mapIndexed { index, current ->
                val nextTime = if (index < parsedLines.size - 1) {
                    parsedLines[index + 1].startTime
                } else {
                    totalDurationMs
                }
                val updatedWords = current.words.map { word ->
                    if (word.endTime == 0L) word.copy(endTime = nextTime) else word
                }
                LyricLine(current.text, current.startTime, nextTime, updatedWords.ifEmpty { null })
            }
        }
    
        private data class ParsedLineTemp(val text: String, val startTime: Long, val words: List<LyricWord> = emptyList())
    
        // --- local extraction ---
        fun extractLocalLyrics(filePath: String): String? {
            return try {
                val file = File(filePath)
                if (!file.exists()) return null
    
                val mp3file = Mp3File(filePath)
                if (mp3file.hasId3v2Tag()) {
                    val tag = mp3file.id3v2Tag
                    // mp3agic handles the uslt tag magic
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

