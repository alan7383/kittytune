package com.alananasss.kittytune.data.lyrics.models

import kotlinx.serialization.Serializable

@Serializable
data class AppleMusicLyricsResponse(
    val type: String? = null,
    val content: List<AppleMusicLine> = emptyList(),
)

@Serializable
data class AppleMusicLine(
    val timestamp: Long = 0,
    val text: List<AppleMusicWord> = emptyList(),
)

@Serializable
data class AppleMusicWord(
    val text: String,
    val timestamp: Long? = null,
)

@Serializable
data class PaxsenixStats(
    val totalTracks: Long = 0,
    val totalLyrics: Long = 0,
)
