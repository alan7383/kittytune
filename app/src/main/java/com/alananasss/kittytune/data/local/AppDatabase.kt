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
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alananasss.kittytune.data.stats.StatsSql
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

    @Query("""
        SELECT * FROM downloaded_tracks 
        WHERE localAudioPath != '' 
          AND (
              (LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND LOWER(TRIM(artist)) = LOWER(TRIM(:artist)))
              OR (LOWER(TRIM(title)) = LOWER(TRIM(:title)) AND (:artist = '' OR artist = ''))
          )
        LIMIT 1
    """)
    suspend fun findDownloadedTrack(title: String, artist: String): LocalTrack?

    @Query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC")
    fun getAllTracks(): Flow<List<LocalTrack>>

    @Query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC")
    suspend fun getAllTracksList(): List<LocalTrack>

    @Query("SELECT * FROM downloaded_tracks WHERE localAudioPath != '' AND lufs IS NULL")
    suspend fun getTracksWithNullLufs(): List<LocalTrack>

    @Query("SELECT * FROM downloaded_tracks")
    suspend fun getAllStoredTracksList(): List<LocalTrack>

    // ---- local files deduplication ----
    @Query("SELECT * FROM downloaded_tracks WHERE source = 'local'")
    suspend fun getLocalTracksSnapshot(): List<LocalTrack>

    @Query("UPDATE downloaded_tracks SET audioKey = :audioKey WHERE id = :id")
    suspend fun updateTrackAudioKey(id: Long, audioKey: String?)

    @Query("DELETE FROM playlist_track_cross_ref WHERE trackId IN (:ids)")
    suspend fun deleteCrossRefsForTracks(ids: List<Long>)

    @Query("DELETE FROM downloaded_tracks WHERE id IN (:ids)")
    suspend fun deleteTracksByIds(ids: List<Long>)

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

    @Query("SELECT * FROM play_history WHERE originalUrl LIKE 'trailer:%'")
    suspend fun getTrailerRoutedHistoryItems(): List<HistoryItem>

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

    @Query("DELETE FROM play_history WHERE originalUrl = 'your_mix' OR originalUrl LIKE '%your_mix%' OR id = 'your_mix' OR id LIKE '%your_mix%'")
    suspend fun deleteMixHistory()

    @Query("DELETE FROM play_history WHERE originalUrl = 'my_wave' OR originalUrl LIKE '%my_wave%' OR id = 'my_wave' OR id LIKE '%my_wave%' OR id LIKE '%wave%' OR title = 'MyWave' OR title LIKE '%MyWave%'")
    suspend fun deleteWaveHistory()

    // listening stats
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStatsEvent(event: ListeningStatsEvent)

    @Query("UPDATE listening_stats SET genre = :genre WHERE trackId = :trackId AND genre IS NULL")
    suspend fun setGenreForTrack(trackId: Long, genre: String)

    @Query("SELECT DISTINCT artistName FROM listening_stats WHERE timestamp < :before")
    suspend fun getArtistNamesBefore(before: Long): List<String>

    @Query("SELECT DISTINCT trackId FROM listening_stats WHERE timestamp < :before")
    suspend fun getTrackIdsBefore(before: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertStatsEvents(events: List<ListeningStatsEvent>): List<Long>

    @Query("SELECT * FROM listening_stats WHERE timestamp >= :since ORDER BY timestamp DESC")
    suspend fun getEventsAfter(since: Long): List<ListeningStatsEvent>

    @Query("SELECT * FROM listening_stats WHERE timestamp >= :since ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEventsAfter(since: Long, limit: Int): List<ListeningStatsEvent>

    @Query("SELECT * FROM listening_stats WHERE timestamp >= :since AND timestamp < :until ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentEventsBetween(since: Long, until: Long, limit: Int): List<ListeningStatsEvent>

    @Query("SELECT COALESCE(SUM(listenDurationMs), 0) FROM listening_stats WHERE timestamp >= :since AND timestamp < :until")
    suspend fun getTotalListenTimeBetween(since: Long, until: Long): Long

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

    @Query("SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND ${StatsSql.COUNTS_AS_PLAY} GROUP BY artistName")
    suspend fun getRawTopArtistsAfter(since: Long): List<TopArtistResult>

    @Query("SELECT trackId, trackTitle, artistName, MAX(artworkUrl) as artworkUrl, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND timestamp < :until AND ${StatsSql.COUNTS_AS_PLAY} GROUP BY trackId ORDER BY totalListenMs DESC LIMIT :limit")
    suspend fun getTopTracksBetween(since: Long, until: Long, limit: Int = 1): List<TopTrackResult>

    @Query("SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND timestamp < :until AND ${StatsSql.COUNTS_AS_PLAY} GROUP BY artistName ORDER BY totalListenMs DESC LIMIT :limit")
    suspend fun getTopArtistsBetween(since: Long, until: Long, limit: Int = 1): List<TopArtistResult>

    @Query("SELECT artistName, MAX(artistAvatarUrl) as artworkUrl, MAX(artistId) as artistId, MAX(artistPermalink) as artistPermalink, MAX(source) as source, COUNT(*) as playCount, SUM(listenDurationMs) as totalListenMs FROM listening_stats WHERE timestamp >= :since AND timestamp < :until AND ${StatsSql.COUNTS_AS_PLAY} GROUP BY artistName")
    suspend fun getRawTopArtistsBetween(since: Long, until: Long): List<TopArtistResult>

    @Query("SELECT DISTINCT artistName FROM listening_stats WHERE timestamp >= :since AND ${StatsSql.COUNTS_AS_PLAY}")
    suspend fun getDistinctArtistNamesAfter(since: Long): List<String>

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

data class StatsSnapshot(
    val totalListenMs: Long = 0,
    @androidx.room.ColumnInfo(name = "rowCount") val rows: Int = 0,
    val plays: Int = 0,
    val completed: Int = 0,
    val skips: Int = 0,
    val uniqueTracks: Int = 0,
    val uniqueArtists: Int = 0,
    val replays: Int = 0,
    val loops: Int = 0,
    val firstAtMs: Long? = null,
    val lastAtMs: Long? = null,
) {
    val hasData: Boolean get() = rows > 0

    val completionRate: Float get() = if (rows > 0) completed.toFloat() / rows else 0f

    val skipRate: Float get() = if (rows > 0) skips.toFloat() / rows else 0f
}

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
        TrackTrimRow::class,
        BeatInfoEntity::class,
        BlockedContent::class,
        QueueHistoryEntity::class
    ],
    version = 29,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao
    abstract fun recognitionHistoryDao(): RecognitionHistoryDao
    abstract fun folderDao(): FolderDao
    abstract fun beatInfoDao(): BeatInfoDao
    abstract fun blockedContentDao(): BlockedContentDao
    abstract fun queueHistoryDao(): QueueHistoryDao

    companion object {
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_playlists ADD COLUMN isDownloaded INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN source TEXT NOT NULL DEFAULT 'soundcloud'")
                db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN permalinkUrl TEXT")
                db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN ownerId INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN secretToken TEXT")
            }
        }

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

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS track_trim (" +
                        "trackId INTEGER NOT NULL, mode TEXT NOT NULL, segments TEXT NOT NULL, " +
                        "updatedAt INTEGER NOT NULL, PRIMARY KEY(trackId))"
                )
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS beat_info (" +
                        "songId TEXT NOT NULL, bpm REAL NOT NULL, firstBeatOffsetMs INTEGER NOT NULL, " +
                        "confidence REAL NOT NULL, analyzedAt INTEGER NOT NULL, mixInPointMs INTEGER, " +
                        "mixOutPointMs INTEGER, keyPitchClass INTEGER, keyIsMinor INTEGER, PRIMARY KEY(songId))"
                )
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE beat_info ADD COLUMN downbeatOffsetMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE beat_info ADD COLUMN phraseOffsetMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE beat_info ADD COLUMN downbeatConfidence REAL NOT NULL DEFAULT 0")
                db.execSQL("UPDATE beat_info SET analyzedAt = 0")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE beat_info ADD COLUMN analysisVersion INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE beat_info ADD COLUMN phraseConfidence REAL NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_24_25 = object : Migration(24, 25) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS blocked_content (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "trackId INTEGER, " +
                        "trackTitle TEXT, " +
                        "trackArtworkUrl TEXT, " +
                        "artistId INTEGER, " +
                        "artistName TEXT, " +
                        "artistAvatarUrl TEXT, " +
                        "source TEXT NOT NULL DEFAULT 'soundcloud', " +
                        "reason TEXT NOT NULL DEFAULT 'MANUAL', " +
                        "blockedAt INTEGER NOT NULL DEFAULT 0)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_blocked_content_trackId ON blocked_content(trackId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_blocked_content_artistId ON blocked_content(artistId)")
            }
        }

        val MIGRATION_25_26 = object : Migration(25, 26) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE beat_info ADD COLUMN energy REAL")
            }
        }

        val MIGRATION_26_27 = object : Migration(26, 27) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS queue_history (
                        id TEXT NOT NULL PRIMARY KEY,
                        title TEXT NOT NULL,
                        trackCount INTEGER NOT NULL,
                        tracksJson TEXT NOT NULL,
                        currentIndex INTEGER NOT NULL,
                        currentPositionMs INTEGER NOT NULL,
                        contextJson TEXT,
                        firstTrackArtworkUrl TEXT,
                        firstTrackTitle TEXT,
                        firstTrackArtist TEXT,
                        savedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_queue_history_savedAt ON queue_history(savedAt)")
            }
        }

        val MIGRATION_27_28 = object : Migration(27, 28) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE listening_stats ADD COLUMN genre TEXT")
            }
        }

        /**
         * Adds canonical `audioKey` to `downloaded_tracks` to deduplicate local files discovered
         * across overlapping SAF trees and prevent re-importing already known files.
         */
        val MIGRATION_28_29 = object : Migration(28, 29) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE downloaded_tracks ADD COLUMN audioKey TEXT")

                db.execSQL(
                    """
                    UPDATE downloaded_tracks
                    SET audioKey = LOWER(substr(localAudioPath, instr(localAudioPath, '/document/') + 10))
                    WHERE source = 'local'
                      AND localAudioPath != ''
                      AND instr(localAudioPath, '/document/') > 0
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    DELETE FROM playlist_track_cross_ref
                    WHERE trackId IN (
                        SELECT id FROM downloaded_tracks
                        WHERE audioKey IS NOT NULL
                          AND id NOT IN (
                              SELECT MAX(id) FROM downloaded_tracks
                              WHERE audioKey IS NOT NULL
                              GROUP BY audioKey
                          )
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    DELETE FROM downloaded_tracks
                    WHERE audioKey IS NOT NULL
                      AND id NOT IN (
                          SELECT MAX(id) FROM downloaded_tracks
                          WHERE audioKey IS NOT NULL
                          GROUP BY audioKey
                      )
                    """.trimIndent()
                )

                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_downloaded_tracks_audioKey` " +
                        "ON `downloaded_tracks`(`audioKey`)"
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
                    .addMigrations(
                        MIGRATION_16_17,
                        MIGRATION_17_18,
                        MIGRATION_18_19,
                        MIGRATION_19_20,
                        MIGRATION_20_21,
                        MIGRATION_21_22,
                        MIGRATION_22_23,
                        MIGRATION_23_24,
                        MIGRATION_24_25,
                        MIGRATION_25_26,
                        MIGRATION_26_27,
                        MIGRATION_27_28,
                        MIGRATION_28_29
                    )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
