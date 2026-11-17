package com.alananasss.kittytune.data.auto

import com.alananasss.kittytune.ui.player.lyrics.LyricLine

object AndroidAutoLyrics {
    const val UPDATE_INTERVAL_MS = 250L

    data class CurrentLine(
        val index: Int,
        val segmentIndex: Int,
        val text: String,
        val windowStartMs: Long,
        val windowEndMs: Long,
        val segments: List<AutomotiveLyricSegmenter.TimedSegment>,
    )

    fun currentLine(
        lines: List<LyricLine>,
        positionMs: Long,
        offsetMs: Long = 0L,
        trackDurationMs: Long? = null,
    ): CurrentLine? {
        if (lines.isEmpty()) return null

        val effectivePosition = (positionMs + offsetMs).coerceAtLeast(0L)
        val activeIndices = findActiveLineIndices(lines, effectivePosition)
        val index = activeIndices
            .filterNot { lines[it].isBackground }
            .maxByOrNull { lines[it].startTime }
            ?: findCurrentLineIndex(lines, effectivePosition)
        if (index !in lines.indices) return null

        val line = lines[index]
        if (line.isInstrumental) return null

        val text = AutomotiveLyricSegmenter.normalize(line.text)
        if (text.isEmpty()) return null

        val windowStartMs = line.startTime
        val nextTimestamp = lines.asSequence()
            .drop(index + 1)
            .filterNot { it.isBackground || it.isInstrumental }
            .map { it.startTime }
            .firstOrNull { it > windowStartMs }
        val trackEndInLyricsTime = trackDurationMs
            ?.takeIf { it > 0L }
            ?.plus(offsetMs)
            ?.takeIf { it > windowStartMs }
        val lineEndMs = if (line.endTime > windowStartMs) line.endTime else null
        val windowEndMs = nextTimestamp
            ?: lineEndMs
            ?: trackEndInLyricsTime
            ?: (windowStartMs + AutomotiveLyricSegmenter.LAST_LINE_FALLBACK_DURATION_MS)

        if (effectivePosition >= windowEndMs) return null

        val segments = AutomotiveLyricSegmenter.timedSegments(text, windowStartMs, windowEndMs)
        val segmentIndex = AutomotiveLyricSegmenter.segmentAt(segments, effectivePosition)
        val segment = segments.getOrNull(segmentIndex) ?: return null
        return CurrentLine(
            index = index,
            segmentIndex = segmentIndex,
            text = segment.text,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            segments = segments,
        )
    }

    private fun findCurrentLineIndex(lines: List<LyricLine>, position: Long): Int {
        val threshold = 100L
        for (index in lines.indices) {
            if (lines[index].startTime >= position + threshold) {
                return index - 1
            }
        }
        return lines.lastIndex
    }

    private fun findActiveLineIndices(lines: List<LyricLine>, position: Long): Set<Int> {
        val active = mutableSetOf<Int>()
        for (index in lines.indices) {
            val line = lines[index]
            if (line.startTime > position) break
            val lineEndMs = if (line.endTime > line.startTime) {
                line.endTime
            } else if (!line.words.isNullOrEmpty()) {
                line.words.last().endTime
            } else {
                if (index + 1 < lines.size) lines[index + 1].startTime else Long.MAX_VALUE
            }
            if (position <= lineEndMs) {
                active.add(index)
            }
        }
        return active
    }
}
