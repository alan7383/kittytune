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

        @Query("""
                SELECT DISTINCT P.* FROM downloaded_playlists P
                INNER JOIN playlist_track_cross_ref R ON P.id = R.playlistId
                INNER JOIN downloaded_tracks T ON R.trackId = T.id
                WHERE T.localAudioPath != ''
            """)
        fun getDownloadedPlaylists(): Flow<List<LocalPlaylist>>

        @Query("SELECT * FROM downloaded_playlists WHERE isUserCreated = 1")
        fun getUserPlaylists(): Flow<List<LocalPlaylist>>

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

        @Query("SELECT COUNT(*) FROM playlist_track_cross_ref WHERE trackId = :trackId")
        suspend fun getPlaylistRefCount(trackId: Long): Int

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

        @Query("DELETE FROM play_history WHERE id = :itemId")
        suspend fun deleteHistoryItem(itemId: String)

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
        @Insert
        suspend fun insertStatsEvent(event: ListeningStatsEvent)

        @Query("SELECT * FROM listening_stats WHERE timestamp >= :since ORDER BY timestamp DESC")
        suspend fun getEventsAfter(since: Long): List<ListeningStatsEvent>

        @Query("SELECT trackId, trackTitle, artistName, artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP') GROUP BY trackId ORDER BY playCount DESC LIMIT :limit")
        suspend fun getTopTracksAfter(since: Long, limit: Int = 10): List<TopTrackResult>

        @Query("SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP') GROUP BY artistName HAVING SUM(listenDurationMs) >= 60000 ORDER BY totalListenMs DESC LIMIT :limit")
        suspend fun getTopArtistsAfter(since: Long, limit: Int = 10): List<TopArtistResult>

        @Query("SELECT trackId, trackTitle, artistName, artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND timestamp < :until AND eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP') GROUP BY trackId ORDER BY playCount DESC LIMIT :limit")
        suspend fun getTopTracksBetween(since: Long, until: Long, limit: Int = 1): List<TopTrackResult>

        @Query("SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND timestamp < :until AND eventType IN ('PLAY_COMPLETE', 'MANUAL_REPLAY', 'REPEAT_ONE_LOOP') GROUP BY artistName HAVING SUM(listenDurationMs) >= 60000 ORDER BY totalListenMs DESC LIMIT :limit")
        suspend fun getTopArtistsBetween(since: Long, until: Long, limit: Int = 1): List<TopArtistResult>

        @Query("SELECT COALESCE(SUM(listenDurationMs), 0) FROM listening_stats WHERE timestamp >= :since")
        suspend fun getTotalListenTimeAfter(since: Long): Long

        @Query("SELECT COUNT(*) FROM listening_stats WHERE eventType = :type AND timestamp >= :since")
        suspend fun getEventCountByType(type: String, since: Long): Int

        @Query("SELECT COUNT(*) FROM listening_stats WHERE timestamp >= :since")
        suspend fun getTotalEventsAfter(since: Long): Int

        @Query("SELECT COUNT(*) FROM (SELECT trackId FROM listening_stats WHERE timestamp >= :since GROUP BY trackId HAVING SUM(listenDurationMs) > 3000) AS filtered_tracks")
        suspend fun getUniqueTracksAfter(since: Long): Int

        @Query("SELECT COUNT(DISTINCT artistName) FROM listening_stats WHERE timestamp >= :since")
        suspend fun getUniqueArtistsAfter(since: Long): Int

        @Query("DELETE FROM listening_stats")
        suspend fun clearStats()
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
            LibraryItemMeta::class
        ],
        version = 16,
        exportSchema = false
    )
    abstract class AppDatabase : RoomDatabase() {
        abstract fun downloadDao(): DownloadDao
        abstract fun recognitionHistoryDao(): RecognitionHistoryDao
        abstract fun folderDao(): FolderDao

        companion object {
            @Volatile private var INSTANCE: AppDatabase? = null
            fun getDatabase(context: Context): AppDatabase {
                return INSTANCE ?: synchronized(this) {
                    val instance = Room.databaseBuilder(
                        context.applicationContext,
                        AppDatabase::class.java,
                        "soundtune_db"
                    )
                        .fallbackToDestructiveMigration()
                        .build()
                    INSTANCE = instance
                    instance
                }
            }
        }
    }

