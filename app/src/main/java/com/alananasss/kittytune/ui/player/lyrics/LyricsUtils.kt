package com.alananasss.kittytune.ui.player.lyrics

import com.mpatric.mp3agic.Mp3File
import java.io.File
import java.util.regex.Pattern

// basic holder for a timed line
data class LyricLine(
    val text: String,
    val startTime: Long,
    val endTime: Long
)

object LyricsUtils {

    // standard lrc regex: [mm:ss.xx] lyrics
    private val LRC_PATTERN = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)")

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

                val text = matcher.group(4)?.trim() ?: ""
                val startTime = (min * 60 * 1000) + (sec * 1000) + ms

                if (text.isNotEmpty()) {
                    parsedLines.add(ParsedLineTemp(text, startTime))
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
            LyricLine(current.text, current.startTime, nextTime)
        }
    }

    private data class ParsedLineTemp(val text: String, val startTime: Long)

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