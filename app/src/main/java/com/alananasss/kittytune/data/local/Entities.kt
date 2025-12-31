package com.alananasss.kittytune.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "downloaded_tracks")
data class LocalTrack(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val duration: Long,
    val localAudioPath: String,
    val localArtworkPath: String,
    val downloadedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "downloaded_playlists")
data class LocalPlaylist(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val trackCount: Int,
    val isUserCreated: Boolean = false,
    val localCoverPath: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_track_cross_ref", primaryKeys = ["playlistId", "trackId"])
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_artists")
data class LocalArtist(
    @PrimaryKey val id: Long,
    val username: String,
    val avatarUrl: String,
    val trackCount: Int,
    val savedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "play_history")
data class HistoryItem(
    @PrimaryKey val id: String,
    val numericId: Long,
    val title: String,
    val subtitle: String,
    val imageUrl: String,
    val type: String,
    val timestamp: Long = System.currentTimeMillis()
)