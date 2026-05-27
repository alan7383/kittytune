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
    
        @Query("SELECT * FROM play_history ORDER BY timestamp DESC LIMIT 20")
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

    @Database(entities = [LocalTrack::class, LocalPlaylist::class, PlaylistTrackCrossRef::class, HistoryItem::class, LocalArtist::class, ListeningStatsEvent::class], version = 13, exportSchema = false)
    abstract class AppDatabase : RoomDatabase() {
        abstract fun downloadDao(): DownloadDao
    
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


