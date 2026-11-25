package com.alananasss.kittytune.ui.player.lyrics

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
