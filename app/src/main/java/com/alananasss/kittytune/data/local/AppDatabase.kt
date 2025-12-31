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
    // --- TRACKS ---
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: LocalTrack)

    @Update
    suspend fun updateTrack(track: LocalTrack)

    @Query("SELECT * FROM downloaded_tracks WHERE id = :trackId")
    suspend fun getTrack(trackId: Long): LocalTrack?

    @Query("DELETE FROM downloaded_tracks WHERE id = :trackId")
    suspend fun deleteTrack(trackId: Long)

    // We only return tracks with a valid (non-empty) audio path.
    // This prevents tracks added to playlists from appearing in "Downloads" if they haven't been downloaded.
    @Query("SELECT * FROM downloaded_tracks WHERE localAudioPath != ''")
    fun getAllTracks(): Flow<List<LocalTrack>>

    // --- PLAYLISTS ---
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

    @Query("SELECT * FROM downloaded_playlists WHERE isUserCreated = 1")
    fun getUserPlaylists(): Flow<List<LocalPlaylist>>

    @Query("SELECT * FROM downloaded_playlists WHERE id = :playlistId")
    suspend fun getPlaylist(playlistId: Long): LocalPlaylist?

    @Query("SELECT * FROM downloaded_playlists WHERE id = :playlistId")
    fun getPlaylistFlow(playlistId: Long): Flow<LocalPlaylist?>

    @Query("UPDATE downloaded_playlists SET title = :newTitle WHERE id = :playlistId")
    suspend fun updatePlaylistTitle(playlistId: Long, newTitle: String)

    @Query("SELECT * FROM downloaded_tracks WHERE id NOT IN (SELECT trackId FROM playlist_track_cross_ref)")
    suspend fun getOrphanTracksList(): List<LocalTrack>

    // --- RELATIONSHIPS ---
    @Transaction
    @Query("""
        SELECT downloaded_tracks.* FROM downloaded_tracks 
        INNER JOIN playlist_track_cross_ref ON downloaded_tracks.id = playlist_track_cross_ref.trackId 
        WHERE playlist_track_cross_ref.playlistId = :playlistId
        ORDER BY playlist_track_cross_ref.addedAt ASC
    """)
    fun getTracksForPlaylist(playlistId: Long): Flow<List<LocalTrack>>

    // --- ARTISTS ---
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

    // --- HISTORY ---
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
""")
    suspend fun getTracksForPlaylistSync(playlistId: Long): List<LocalTrack>

    @Query("DELETE FROM play_history")
    suspend fun clearHistory()
}

@Database(entities = [LocalTrack::class, LocalPlaylist::class, PlaylistTrackCrossRef::class, HistoryItem::class, LocalArtist::class], version = 5, exportSchema = false)
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