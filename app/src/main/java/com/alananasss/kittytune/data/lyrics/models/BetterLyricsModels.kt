package com.alananasss.kittytune.data.lyrics.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class TTMLResponse(
    @JsonNames("ttml", "lyrics")
    val ttml: String = "",
    @SerialName("provider")
    val provider: String? = null,
    @SerialName("score")
    val score: Double? = null,
)

@Serializable
data class BetterLyricsSearchResponse(
    val results: List<BetterLyricsTrack> = emptyList(),
)

@Serializable
data class BetterLyricsTrack(
    val title: String,
    val artist: String,
    val album: String? = null,
    val duration: Double = 0.0,
    val lyrics: BetterLyricsData? = null,
)

@Serializable
data class BetterLyricsData(
    val lines: List<BetterLyricsLine> = emptyList(),
)

@Serializable
data class BetterLyricsLine(
    val text: String,
    val startTime: Double,
    val words: List<BetterLyricsWord>? = null,
)

@Serializable
data class BetterLyricsWord(
    val text: String,
    val startTime: Double,
    val endTime: Double,
)
