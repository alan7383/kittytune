    package com.alananasss.kittytune.data.local

    import androidx.room.Index
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
        val truePeak: Float? = null,
        /**
         * Which service the track came from. Without it a VK track rebuilt from the database looked
         * like a SoundCloud track, so sharing produced a soundcloud.com link and playback tried to
         * stream from SoundCloud and failed.
         */
        val source: String = "soundcloud",
        /** Canonical web link, used for sharing. */
        val permalinkUrl: String? = null,
        /** VK owner id — required to refresh a VK stream URL. */
        val ownerId: Long = 0L,
        /** VK hash bundle (`add/edit/action/delete/replace/url`) needed by `reload_audios`. */
        val secretToken: String? = null
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

    @Entity(
        tableName = "listening_stats",
        indices = [
            Index(value = ["timestamp"]),
            Index(value = ["trackId"]),
            Index(value = ["artistName"]),
            // What makes applying a synced listen twice impossible rather than merely unlikely. SQLite
            // treats NULLs as distinct, so the rows from before sync existed — all of which have no id —
            // do not collide with each other (issue #33).
            Index(value = ["syncEventId"], unique = true),
        ],
    )
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
        val timestamp: Long = System.currentTimeMillis(),
        /**
         * How far playback actually got in the track (issue #33).
         *
         * Separate from [listenDurationMs] because they answer different questions: someone who skips
         * the first minute and listens to the rest heard less than the track lasts but did reach the
         * end, and someone who loops the same chorus ten times heard a great deal without ever getting
         * near it. Completion is judged on this; how much was heard is judged on the other. Zero on rows
         * written before it was recorded, which is what
         * [com.alananasss.kittytune.data.stats.StatsSql.IS_COMPLETE] falls back on the ending label for.
         */
        val furthestPositionMs: Long = 0,
        /**
         * The sync event this row came from, `deviceId#seq`, or null for rows older than sync.
         *
         * Unique, so applying the same event twice cannot produce two rows however the sync bookkeeping
         * is disturbed — a restored backup, cleared preferences, a peer re-sending a batch it already
         * sent. Rows this device recorded carry their own id too, so its own log is equally safe to
         * replay.
         */
        val syncEventId: String? = null
    )

    /**
     * A track's trim as it is stored: the mode by name, the spans as JSON (issue #33).
     *
     * Deliberately dumb. Parsing belongs in the repository, so a row whose JSON has been corrupted costs that
     * one track's trim rather than failing the query.
     */
    @Entity(tableName = "track_trim")
    data class TrackTrimRow(
        @PrimaryKey val trackId: Long,
        val mode: String,
        val segments: String,
        val updatedAt: Long = System.currentTimeMillis()
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


