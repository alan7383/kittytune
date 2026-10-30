package com.alananasss.kittytune.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "queue_history",
    indices = [
        Index("savedAt")
    ]
)
data class QueueHistoryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val trackCount: Int,
    val tracksJson: String,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val contextJson: String?,
    val firstTrackArtworkUrl: String?,
    val firstTrackTitle: String?,
    val firstTrackArtist: String?,
    val savedAt: Long = System.currentTimeMillis()
)

data class QueueHistorySummary(
    val id: String,
    val title: String,
    val trackCount: Int,
    val currentIndex: Int,
    val currentPositionMs: Long,
    val contextJson: String?,
    val firstTrackArtworkUrl: String?,
    val firstTrackTitle: String?,
    val firstTrackArtist: String?,
    val savedAt: Long
)
