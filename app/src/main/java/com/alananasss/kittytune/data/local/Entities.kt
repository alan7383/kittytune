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
        val downloadedAt: Long = System.currentTimeMillis(),
        val lufs: Float? = null,
        val truePeak: Float? = null
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
        val permalinkUrl: String? = null,
        val isAlbum: Boolean = false,
        val addedAt: Long = System.currentTimeMillis(),
        val isDownloaded: Boolean = false
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
        val timestamp: Long = System.currentTimeMillis(),
        val isVerified: Boolean = false, // Added field for verification badge
        val source: String = "soundcloud",
        val originalUrl: String? = null
    )

    @Entity(tableName = "recognition_history")
    data class RecognitionHistoryItem(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val trackId: Long?,
        val title: String,
        val artist: String,
        val artworkUrl: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    @Entity(tableName = "listening_stats")
    data class ListeningStatsEvent(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val trackId: Long,
        val trackTitle: String,
        val artistName: String,
        val artistId: Long? = null,
        val artistPermalink: String? = null,
        val artistAvatarUrl: String? = null,
        val artworkUrl: String,
        val source: String = "soundcloud",
        val eventType: String,          // PLAY_COMPLETE, SKIP_NEXT, SKIP_PREVIOUS, MANUAL_REPLAY, REPEAT_ONE_LOOP
        val listenDurationMs: Long = 0,
        val trackDurationMs: Long = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    @Entity(tableName = "library_folders")
    data class LibraryFolder(
        @PrimaryKey(autoGenerate = true) val id: Long = 0,
        val name: String,
        val parentFolderId: Long? = null,
        val isPinned: Boolean = false,
        val createdAt: Long = System.currentTimeMillis()
    )

    @Entity(tableName = "library_item_meta")
    data class LibraryItemMeta(
        @PrimaryKey val itemKey: String,
        val folderId: Long? = null,
        val isPinned: Boolean = false,
        val addedAt: Long = System.currentTimeMillis()
    )


