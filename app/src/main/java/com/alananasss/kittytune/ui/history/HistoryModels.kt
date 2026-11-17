package com.alananasss.kittytune.ui.history

import com.alananasss.kittytune.domain.Track

enum class HistoryTab {
    TRACKS,
    CONTEXTS
}

enum class HistoryContextType {
    PLAYLIST,
    ALBUM,
    ARTIST_STATION,
    TRACK_STATION,
    ARTIST,
    LIKES,
    DOWNLOADS,
    UNKNOWN
}

data class HistoryTrackItem(
    val track: Track,
    val playedAt: Long
)

data class HistoryContextItem(
    val id: String,
    val urn: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val type: HistoryContextType,
    val playedAt: Long,
    val targetNavId: String,
    val isVerified: Boolean = false
)
