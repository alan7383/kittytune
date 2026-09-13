package com.alananasss.kittytune.data.lyrics.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UnisonEntry(
    val id: Long,
    @SerialName("videoId") val videoId: String? = null,
    val song: String,
    val artist: String,
    val lyrics: String,
    val format: String,
    val syncType: String,
    val score: Double = 0.0,
    val effectiveScore: Double = 0.0,
    val voteCount: Int = 0,
    val confidence: String = "low",
    val language: String? = null,
)

@Serializable
data class UnisonResponse(
    val success: Boolean = false,
    val data: UnisonEntry? = null,
)

@Serializable
data class UnisonSearchResponse(
    val success: Boolean = false,
    val data: List<UnisonSearchEntry> = emptyList(),
)

@Serializable
data class UnisonSearchEntry(
    val id: Long,
    @SerialName("videoId") val videoId: String? = null,
    val song: String,
    val artist: String,
    val lyrics: String? = null,
    val format: String,
    val syncType: String,
    val score: Double = 0.0,
    val effectiveScore: Double = 0.0,
    val voteCount: Int = 0,
    val confidence: String = "low",
    val language: String? = null,
) {
    fun toEntry(): UnisonEntry? {
        val nonBlankLyrics = lyrics?.takeIf { it.isNotBlank() } ?: return null
        return UnisonEntry(
            id = id,
            videoId = videoId,
            song = song,
            artist = artist,
            lyrics = nonBlankLyrics,
            format = format,
            syncType = syncType,
            score = score,
            effectiveScore = effectiveScore,
            voteCount = voteCount,
            confidence = confidence,
            language = language,
        )
    }
}
