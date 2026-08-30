    package com.alananasss.kittytune.data.local

    import android.content.Context
    import androidx.room.Dao
    import androidx.room.Database
    import androidx.room.Insert
    import androidx.room.OnConflictStrategy
    import androidx.room.Query
    import androidx.room.Room
    import androidx.room.RoomDatabase
    import androidx.room.Transaction
    import androidx.room.Update
    import androidx.room.migration.Migration
    import com.alananasss.kittytune.data.stats.StatsSql
    import androidx.sqlite.db.SupportSQLiteDatabase
    import kotlinx.coroutines.flow.Flow

    @Dao
    interface DownloadDao {
        // tracks
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertTrack(track: LocalTrack)

        @Update
        suspend fun updateTrack(track: LocalTrack)

        @Query("SELECT * FROM downloaded_tracks WHERE id = :trackId")
        suspend fun getTrack(trackId: Long): LocalTrack?

        @Query("DELETE FROM downloaded_tracks WHERE id = :trackId")
        suspend fun deleteTrack(trackId: Long)

        @Query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC")
        fun getAllTracks(): Flow<List<LocalTrack>>

        @Query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC")
        suspend fun getAllTracksList(): List<LocalTrack>

        @Query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' AND lufs IS NULL")
        suspend fun getTracksWithNullLufs(): List<LocalTrack>

        @Query("SELECT * FROM downloaded_tracks")
        suspend fun getAllStoredTracksList(): List<LocalTrack>

        // playlists
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertPlaylist(playlist: LocalPlaylist)

        @Update
        suspend fun updatePlaylist(playlist: LocalPlaylist)

        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertPlaylistTrackRef(ref: PlaylistTrackCrossRef)

        @Update
        suspend fun updatePlaylistTrackRef(ref: PlaylistTrackCrossRef)

        @Query("SELECT * FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
        suspend fun getRef(playlistId: Long, trackId: Long): PlaylistTrackCrossRef?

        @Query("DELETE FROM downloaded_playlists WHERE id = :playlistId")
        suspend fun deletePlaylist(playlistId: Long)

        @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId")
        suspend fun deletePlaylistRefs(playlistId: Long)

        @Query("DELETE FROM playlist_track_cross_ref WHERE playlistId = :playlistId AND trackId = :trackId")
        suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long)

        @Query("SELECT * FROM downloaded_playlists")
        fun getAllPlaylists(): Flow<List<LocalPlaylist>>

        @Query("SELECT * FROM downloaded_playlists")
        suspend fun getAllPlaylistsList(): List<LocalPlaylist>

        @Query("UPDATE downloaded_playlists SET isDownloaded = :isDownloaded WHERE id = :playlistId")
        suspend fun setPlaylistDownloaded(playlistId: Long, isDownloaded: Boolean)

        @Query("""
            SELECT DISTINCT P.* FROM downloaded_playlists P
            LEFT JOIN playlist_track_cross_ref R ON R.playlistId = P.id
            LEFT JOIN downloaded_tracks T ON T.id = R.trackId AND T.localAudioPath != ''
            WHERE P.isDownloaded = 1 OR T.id IS NOT NULL
            ORDER BY P.addedAt DESC
        """)
        fun getDownloadedPlaylists(): Flow<List<LocalPlaylist>>

        @Query("""
            SELECT COUNT(*) FROM playlist_track_cross_ref R
            INNER JOIN downloaded_playlists P ON R.playlistId = P.id
            WHERE R.trackId = :trackId AND P.id != :excludePlaylistId AND P.isDownloaded = 1
        """)
        suspend fun getDownloadedPlaylistRefCount(trackId: Long, excludePlaylistId: Long): Int

        @Query("SELECT * FROM downloaded_playlists WHERE isUserCreated = 1 OR id < 0")
        fun getUserPlaylists(): Flow<List<LocalPlaylist>>

        @Query("UPDATE downloaded_playlists SET isUserCreated = 1 WHERE id < 0")
        suspend fun fixNegativeIdPlaylistsUserCreated()

        @Query("SELECT * FROM downloaded_playlists WHERE id = :playlistId")
        suspend fun getPlaylist(playlistId: Long): LocalPlaylist?

        @Query("SELECT * FROM downloaded_playlists WHERE id = :playlistId")
        fun getPlaylistFlow(playlistId: Long): Flow<LocalPlaylist?>

        @Query("UPDATE downloaded_playlists SET title = :newTitle WHERE id = :playlistId")
        suspend fun updatePlaylistTitle(playlistId: Long, newTitle: String)

        @Query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' AND id NOT IN (SELECT trackId FROM playlist_track_cross_ref) ORDER BY downloadedAt DESC")
        suspend fun getOrphanTracksList(): List<LocalTrack>

        @Query("DELETE FROM downloaded_tracks WHERE localAudioPath = '' AND id NOT IN (SELECT trackId FROM playlist_track_cross_ref)")
        suspend fun cleanUnreferencedEmptyTracks()

        @Query("DELETE FROM downloaded_playlists WHERE id > 0 AND isDownloaded = 0 AND (permalinkUrl IS NULL OR permalinkUrl NOT LIKE '%spotify%')")
        suspend fun deleteNonDownloadedOnlinePlaylists()

        @Query("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE trackId = :trackId")
        suspend fun getPlaylistRefCount(trackId: Long): Int

        @Query("""
            UPDATE downloaded_playlists
            SET isDownloaded = 0
            WHERE isDownloaded = 1
              AND id NOT IN (
                SELECT DISTINCT R.playlistId
                FROM playlist_track_cross_ref R
                INNER JOIN downloaded_tracks T ON T.id = R.trackId
                WHERE T.localAudioPath != ''
              )
        """)
        suspend fun cleanEmptyDownloadedPlaylists()

        // relationships
        @Transaction
        @Query("""
                SELECT downloaded_tracks.* FROM downloaded_tracks 
                INNER JOIN playlist_track_cross_ref ON downloaded_tracks.id = playlist_track_cross_ref.trackId 
                WHERE playlist_track_cross_ref.playlistId = :playlistId
                ORDER BY playlist_track_cross_ref.addedAt ASC
            """)
        fun getTracksForPlaylist(playlistId: Long): Flow<List<LocalTrack>>

        // artists
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertArtist(artist: LocalArtist)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertArtists(artists: List<LocalArtist>)

        @Query("DELETE FROM saved_artists WHERE id = :artistId")
        suspend fun deleteArtist(artistId: Long)

        @Query("SELECT * FROM saved_artists WHERE id = :artistId")
        suspend fun getArtist(artistId: Long): LocalArtist?

        @Query("SELECT * FROM saved_artists WHERE id = :artistId")
        fun getArtistFlow(artistId: Long): Flow<LocalArtist?>

        @Query("SELECT * FROM saved_artists ORDER BY savedAt DESC")
        fun getAllSavedArtists(): Flow<List<LocalArtist>>

        // history
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertHistory(item: HistoryItem)

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertHistoryList(items: List<HistoryItem>)

        @Query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT 1000")
        fun getHistory(): Flow<List<HistoryItem>>

        @Query("SELECT * FROM play_history WHERE numericId = :numericId OR id = :id LIMIT 1")
        suspend fun getHistoryItemById(numericId: Long, id: String): HistoryItem?

        @Query("DELETE FROM play_history WHERE id = :itemId")
        suspend fun deleteHistoryItem(itemId: String)

        @Query("UPDATE play_history SET imageUrl = :newImageUrl WHERE id = :itemId")
        suspend fun updateHistoryItemImageUrl(itemId: String, newImageUrl: String)

        @Query("""
                SELECT downloaded_tracks.* FROM downloaded_tracks 
                INNER JOIN playlist_track_cross_ref ON downloaded_tracks.id = playlist_track_cross_ref.trackId 
                WHERE playlist_track_cross_ref.playlistId = :playlistId
                ORDER BY playlist_track_cross_ref.addedAt ASC
            """)
        suspend fun getTracksForPlaylistSync(playlistId: Long): List<LocalTrack>

        @Query("DELETE FROM play_history")
        suspend fun clearHistory()

        @Query("DELETE FROM play_history WHERE type = 'TRACK'")
        suspend fun clearTracksHistory()

        @Query("DELETE FROM play_history WHERE type != 'TRACK'")
        suspend fun clearContextsHistory()

        // listening stats
        //
        // Every aggregate below asks the same question — was enough of this track heard? — instead of
        // asking how it ended (issue #33). The filter used to be
        // `eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP')`, so a track played to its
        // last ten seconds and then skipped counted for nothing while one that ran out on its own counted
        // fully. Same listening, different statistics, depending on which button was pressed.
        //
        // The rule is interpolated from StatsSql rather than written out, so there is one definition of it
        // in the whole app and the two platforms cannot drift again. Room accepts it because a template of
        // `const val`s is still a compile-time constant, which is all its processor needs.

        /**
         * `OR IGNORE`, keyed on the unique sync event id.
         *
         * Applying a synced listen twice is not merely unlikely, it is impossible: the second attempt is
         * dropped by the database rather than by whatever bookkeeping happened to be intact.
         */
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertStatsEvent(event: ListeningStatsEvent)

        /**
         * A whole batch in one transaction, for what arrives from the other device.
         *
         * Room wraps a list insert in a single transaction; a loop of single inserts would be one commit
         * each, which turns a first pairing carrying hundreds of rows into a visible pause. The returned
         * row ids are `-1` for the ones the unique id caused to be ignored, so the caller can report what
         * it actually received rather than what it was offered (issue #33).
         */
        @Insert(onConflict = OnConflictStrategy.IGNORE)
        suspend fun insertStatsEvents(events: List<ListeningStatsEvent>): List<Long>

        @Query("SELECT * FROM listening_stats WHERE timestamp >= :since ORDER BY timestamp DESC")
        suspend fun getEventsAfter(since: Long): List<ListeningStatsEvent>

        /**
         * Every number the statistics header shows, in one query.
         *
         * This replaces nine separate scalar queries. Nine round trips each rescanning the same rows is
         * what made the screen take seconds to open, and it got worse with every listen — and worse again
         * once the other device's history started landing in the same table.
         */
        @Query("""
            SELECT
              COALESCE(SUM(listenDurationMs), 0) AS totalListenMs,
              COUNT(*) AS rowCount,
              COALESCE(SUM(CASE WHEN ${StatsSql.COUNTS_AS_PLAY} THEN 1 ELSE 0 END), 0) AS plays,
              COALESCE(SUM(CASE WHEN ${StatsSql.IS_COMPLETE} THEN 1 ELSE 0 END), 0) AS completed,
              COALESCE(SUM(CASE WHEN NOT ${StatsSql.IS_COMPLETE} AND NOT ${StatsSql.COUNTS_AS_PLAY} THEN 1 ELSE 0 END), 0) AS skips,
              COUNT(DISTINCT CASE WHEN ${StatsSql.COUNTS_AS_PLAY} THEN trackId END) AS uniqueTracks,
              COUNT(DISTINCT CASE WHEN ${StatsSql.COUNTS_AS_PLAY} THEN artistName END) AS uniqueArtists,
              COALESCE(SUM(CASE WHEN eventType = 'MANUAL_REPLAY' THEN 1 ELSE 0 END), 0) AS replays,
              COALESCE(SUM(CASE WHEN eventType = 'REPEAT_ONE_LOOP' THEN 1 ELSE 0 END), 0) AS loops,
              MIN(timestamp) AS firstAtMs,
              MAX(timestamp) AS lastAtMs
            FROM listening_stats WHERE timestamp >= :since
        """)
        suspend fun getStatsSnapshot(since: Long): StatsSnapshot

        /**
         * Which months hold anything, newest first.
         *
         * The timeline used to discover this by walking a month at a time and counting the whole table
         * twice per step to guess whether to keep going — and when a month turned up empty it called
         * itself again, so a gap in the history could spin.
         */
        @Query("""
            SELECT
              CAST(strftime('%Y', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS year,
              CAST(strftime('%m', timestamp / 1000, 'unixepoch', 'localtime') AS INTEGER) AS month,
              COUNT(*) AS plays
            FROM listening_stats
            WHERE ${StatsSql.COUNTS_AS_PLAY}
            GROUP BY year, month
            ORDER BY year DESC, month DESC
        """)
        suspend fun getStatsMonths(): List<StatsMonth>

        @Query("SELECT trackId, trackTitle, artistName, MAX(artworkUrl) as artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND ${StatsSql.COUNTS_AS_PLAY} GROUP BY trackId ORDER BY totalListenMs DESC LIMIT :limit")
        suspend fun getTopTracksAfter(since: Long, limit: Int = 10): List<TopTrackResult>

        @Query("SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND ${StatsSql.COUNTS_AS_PLAY} GROUP BY artistName ORDER BY totalListenMs DESC LIMIT :limit")
        suspend fun getTopArtistsAfter(since: Long, limit: Int = 10): List<TopArtistResult>

        @Query("SELECT trackId, trackTitle, artistName, MAX(artworkUrl) as artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND timestamp < :until AND ${StatsSql.COUNTS_AS_PLAY} GROUP BY trackId ORDER BY totalListenMs DESC LIMIT :limit")
        suspend fun getTopTracksBetween(since: Long, until: Long, limit: Int = 1): List<TopTrackResult>

        @Query("SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND timestamp < :until AND ${StatsSql.COUNTS_AS_PLAY} GROUP BY artistName ORDER BY totalListenMs DESC LIMIT :limit")
        suspend fun getTopArtistsBetween(since: Long, until: Long, limit: Int = 1): List<TopArtistResult>

        @Query("SELECT COALESCE(SUM(listenDurationMs), 0) FROM listening_stats WHERE timestamp >= :since")
        suspend fun getTotalListenTimeAfter(since: Long): Long

        @Query("SELECT COUNT(*) FROM listening_stats WHERE eventType = :type AND timestamp >= :since")
        suspend fun getEventCountByType(type: String, since: Long): Int

        @Query("SELECT COUNT(*) FROM listening_stats WHERE timestamp >= :since")
        suspend fun getTotalEventsAfter(since: Long): Int

        @Query("SELECT COUNT(DISTINCT trackId) FROM listening_stats WHERE timestamp >= :since AND ${StatsSql.COUNTS_AS_PLAY}")
        suspend fun getUniqueTracksAfter(since: Long): Int

        @Query("SELECT COUNT(DISTINCT artistName) FROM listening_stats WHERE timestamp >= :since AND ${StatsSql.COUNTS_AS_PLAY}")
        suspend fun getUniqueArtistsAfter(since: Long): Int

        @Query("DELETE FROM listening_stats")
        suspend fun clearStats()

        // --- trim / smart skip -------------------------------------------------------------------
        @Query("SELECT * FROM track_trim WHERE trackId = :trackId")
        suspend fun getTrackTrim(trackId: Long): TrackTrimRow?

        @Query("SELECT trackId FROM track_trim")
        suspend fun getTrimmedTrackIds(): List<Long>

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun putTrackTrim(row: TrackTrimRow)

        @Query("DELETE FROM track_trim WHERE trackId = :trackId")
        suspend fun deleteTrackTrim(trackId: Long)
    }

    @Dao
    interface RecognitionHistoryDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertItem(item: RecognitionHistoryItem)

        @Query("SELECT * FROM recognition_history ORDER BY timestamp DESC")
        fun getAllItems(): Flow<List<RecognitionHistoryItem>>

        @Query("DELETE FROM recognition_history")
        suspend fun clearHistory()

        @Query("DELETE FROM recognition_history WHERE id = :itemId")
        suspend fun deleteItem(itemId: Long)
    }

    @Dao
    interface FolderDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun insertFolder(folder: LibraryFolder): Long

        @Update
        suspend fun updateFolder(folder: LibraryFolder)

        @Query("UPDATE library_folders SET name = :newName WHERE id = :folderId")
        suspend fun renameFolder(folderId: Long, newName: String)

        @Query("UPDATE library_folders SET isPinned = :isPinned WHERE id = :folderId")
        suspend fun setFolderPinned(folderId: Long, isPinned: Boolean)

        @Query("UPDATE library_folders SET parentFolderId = :newParentFolderId, isPinned = 0 WHERE id = :folderId")
        suspend fun moveFolder(folderId: Long, newParentFolderId: Long?)

        @Query("SELECT * FROM library_folders ORDER BY createdAt DESC")
        fun getAllFolders(): Flow<List<LibraryFolder>>

        @Query("SELECT * FROM library_folders WHERE id = :folderId")
        suspend fun getFolder(folderId: Long): LibraryFolder?

        @Query("DELETE FROM library_folders WHERE id = :folderId")
        suspend fun deleteFolderDirect(folderId: Long)

        @Query("UPDATE library_item_meta SET folderId = :newParentFolderId WHERE folderId = :deletedFolderId")
        suspend fun reassignItemsFromDeletedFolder(deletedFolderId: Long, newParentFolderId: Long?)

        @Query("UPDATE library_folders SET parentFolderId = :newParentFolderId WHERE parentFolderId = :deletedFolderId")
        suspend fun reassignFoldersFromDeletedFolder(deletedFolderId: Long, newParentFolderId: Long?)

        @Transaction
        suspend fun deleteFolderSafely(folderId: Long) {
            val folder = getFolder(folderId) ?: return
            val parentId = folder.parentFolderId
            reassignItemsFromDeletedFolder(folderId, parentId)
            reassignFoldersFromDeletedFolder(folderId, parentId)
            deleteFolderDirect(folderId)
        }

        @Insert(onConflict = OnConflictStrategy.REPLACE)
        suspend fun upsertItemMeta(meta: LibraryItemMeta)

        @Query("SELECT * FROM library_item_meta")
        fun getAllItemMetas(): Flow<List<LibraryItemMeta>>

        @Query("SELECT * FROM library_item_meta WHERE itemKey = :itemKey")
        suspend fun getItemMeta(itemKey: String): LibraryItemMeta?

        @Query("UPDATE library_item_meta SET folderId = :folderId, isPinned = 0 WHERE itemKey = :itemKey")
        suspend fun moveItemToFolder(itemKey: String, folderId: Long?)

        @Query("UPDATE library_item_meta SET isPinned = :isPinned WHERE itemKey = :itemKey")
        suspend fun setItemPinned(itemKey: String, isPinned: Boolean)

        @Query("DELETE FROM library_item_meta WHERE itemKey = :itemKey")
        suspend fun deleteItemMeta(itemKey: String)
    }

    /**
     * Every headline number for one span of time, from one query (issue #33).
     *
     * Exists because the statistics screen wanted nine scalars and asked for them one at a time,
     * rescanning the same rows for each. They are all sums over the same set, so they come back together.
     *
     * @param rows every recorded listen in the span, including the ones too short to count. Kept because
     *   skip rates need a denominator that includes what was skipped.
     * @param plays the ones that count, by [com.alananasss.kittytune.data.stats.ListenRules].
     * @param completed the ones that reached the end of the track.
     * @param skips left early, without enough being heard. Not simply "did not complete": pausing halfway
     *   through and coming back tomorrow is neither a completion nor a skip.
     */
    data class StatsSnapshot(
        val totalListenMs: Long = 0,
        @androidx.room.ColumnInfo(name = "rowCount") val rows: Int = 0,
        val plays: Int = 0,
        val completed: Int = 0,
        val skips: Int = 0,
        val uniqueTracks: Int = 0,
        val uniqueArtists: Int = 0,
        /** Still counted by how the listen ended, because that is what these two actually are. */
        val replays: Int = 0,
        val loops: Int = 0,
        val firstAtMs: Long? = null,
        val lastAtMs: Long? = null,
    ) {
        val hasData: Boolean get() = rows > 0

        val completionRate: Float get() = if (rows > 0) completed.toFloat() / rows else 0f

        val skipRate: Float get() = if (rows > 0) skips.toFloat() / rows else 0f
    }

    /** One calendar month that holds listens, for the timeline. */
    data class StatsMonth(
        val year: Int,
        val month: Int,
        val plays: Int,
    )

    data class TopTrackResult(
        val trackId: Long,
        val trackTitle: String,
        val artistName: String,
        val artworkUrl: String?,
        val source: String?,
        val playCount: Int,
        val totalListenMs: Long
    )

    data class TopArtistResult(
        val artistName: String,
        val artworkUrl: String?,
        val artistId: Long?,
        val artistPermalink: String?,
        val source: String?,
        val playCount: Int,
        val totalListenMs: Long
    )

    @Database(
        entities = [
            LocalTrack::class,
            LocalPlaylist::class,
            PlaylistTrackCrossRef::class,
            HistoryItem::class,
            LocalArtist::class,
            ListeningStatsEvent::class,
            RecognitionHistoryItem::class,
            LibraryFolder::class,
            LibraryItemMeta::class,
            TrackTrimRow::class
        ],
        version = 20,
        exportSchema = false
    )
    abstract class AppDatabase : RoomDatabase() {
        abstract fun downloadDao(): DownloadDao
        abstract fun recognitionHistoryDao(): RecognitionHistoryDao
        abstract fun folderDao(): FolderDao

        companion object {
            val MIGRATION_16_17 = object : Migration(16, 17) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE downloaded_playlists ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
                }
            }

            /**
             * Carries the streaming source with every saved track. Playlists created before this
             * migration keep working: existing rows default to SoundCloud, and VK tracks are
             * recognised again as soon as they are re-added or re-resolved.
             */
            val MIGRATION_17_18 = object : Migration(17, 18) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN source TEXT NOT NULL DEFAULT 'soundcloud'")
                    db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN permalinkUrl TEXT")
                    db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN ownerId INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN secretToken TEXT")
                }
            }

            /**
             * Carries how far playback reached, and the identity of the sync event a row came from.
             *
             * Both are additive, so nothing in the existing history is touched: old rows get
             * `furthestPositionMs = 0`, which the aggregates read as "judge this one on how it ended"
             * rather than as "this track was never finished". The unique index is what makes applying a
             * synced listen twice impossible; SQLite counts NULLs as distinct, so the rows that predate
             * sync do not collide with one another (issue #33).
             */
            val MIGRATION_18_19 = object : Migration(18, 19) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE listening_stats ADD COLUMN furthestPositionMs INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE listening_stats ADD COLUMN syncEventId TEXT")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_stats_timestamp ON listening_stats(timestamp)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_stats_trackId ON listening_stats(trackId)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_listening_stats_artistName ON listening_stats(artistName)")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_listening_stats_syncEventId ON listening_stats(syncEventId)")
                }
            }

            /**
             * The table behind "trim this track" (issue #33).
             *
             * Additive and empty on creation: an install that never trims a track carries one unused table and
             * nothing else changes.
             */
            val MIGRATION_19_20 = object : Migration(19, 20) {
                override fun migrate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS track_trim (" +
                            "trackId INTEGER NOT NULL, mode TEXT NOT NULL, segments TEXT NOT NULL, " +
                            "updatedAt INTEGER NOT NULL, PRIMARY KEY(trackId))"
                    )
                }
            }

            @Volatile private var INSTANCE: AppDatabase? = null
            fun getDatabase(context: Context): AppDatabase {
                return INSTANCE ?: synchronized(this) {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "soundtune_db"
                    )
                        .addMigrations(MIGRATION_16_17, MIGRATION_17_18, MIGRATION_18_19, MIGRATION_19_20)
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }

