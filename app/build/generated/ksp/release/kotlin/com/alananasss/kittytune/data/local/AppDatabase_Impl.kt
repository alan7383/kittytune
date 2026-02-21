package com.alananasss.kittytune.`data`.local

import androidx.room.InvalidationTracker
import androidx.room.RoomOpenDelegate
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.room.util.TableInfo
import androidx.room.util.TableInfo.Companion.read
import androidx.room.util.dropFtsSyncTriggers
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import javax.`annotation`.processing.Generated
import kotlin.Lazy
import kotlin.String
import kotlin.Suppress
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableList
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.Set
import kotlin.collections.mutableListOf
import kotlin.collections.mutableMapOf
import kotlin.collections.mutableSetOf
import kotlin.reflect.KClass

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class AppDatabase_Impl : AppDatabase() {
  private val _downloadDao: Lazy<DownloadDao> = lazy {
    DownloadDao_Impl(this)
  }

  protected override fun createOpenDelegate(): RoomOpenDelegate {
    val _openDelegate: RoomOpenDelegate = object : RoomOpenDelegate(8, "78ff6b058066375898658479b57b22f8", "5afa8792520dc30b301f1be874ebb3df") {
      public override fun createAllTables(connection: SQLiteConnection) {
        connection.execSQL("CREATE TABLE IF NOT EXISTS `downloaded_tracks` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, `duration` INTEGER NOT NULL, `localAudioPath` TEXT NOT NULL, `localArtworkPath` TEXT NOT NULL, `downloadedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `downloaded_playlists` (`id` INTEGER NOT NULL, `title` TEXT NOT NULL, `artist` TEXT NOT NULL, `artworkUrl` TEXT NOT NULL, `trackCount` INTEGER NOT NULL, `isUserCreated` INTEGER NOT NULL, `localCoverPath` TEXT, `permalinkUrl` TEXT, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `playlist_track_cross_ref` (`playlistId` INTEGER NOT NULL, `trackId` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `trackId`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `play_history` (`id` TEXT NOT NULL, `numericId` INTEGER NOT NULL, `title` TEXT NOT NULL, `subtitle` TEXT NOT NULL, `imageUrl` TEXT NOT NULL, `type` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `isVerified` INTEGER NOT NULL, `source` TEXT NOT NULL, `originalUrl` TEXT, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS `saved_artists` (`id` INTEGER NOT NULL, `username` TEXT NOT NULL, `avatarUrl` TEXT NOT NULL, `trackCount` INTEGER NOT NULL, `savedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))")
        connection.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)")
        connection.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '78ff6b058066375898658479b57b22f8')")
      }

      public override fun dropAllTables(connection: SQLiteConnection) {
        connection.execSQL("DROP TABLE IF EXISTS `downloaded_tracks`")
        connection.execSQL("DROP TABLE IF EXISTS `downloaded_playlists`")
        connection.execSQL("DROP TABLE IF EXISTS `playlist_track_cross_ref`")
        connection.execSQL("DROP TABLE IF EXISTS `play_history`")
        connection.execSQL("DROP TABLE IF EXISTS `saved_artists`")
      }

      public override fun onCreate(connection: SQLiteConnection) {
      }

      public override fun onOpen(connection: SQLiteConnection) {
        internalInitInvalidationTracker(connection)
      }

      public override fun onPreMigrate(connection: SQLiteConnection) {
        dropFtsSyncTriggers(connection)
      }

      public override fun onPostMigrate(connection: SQLiteConnection) {
      }

      public override fun onValidateSchema(connection: SQLiteConnection): RoomOpenDelegate.ValidationResult {
        val _columnsDownloadedTracks: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDownloadedTracks.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedTracks.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedTracks.put("artist", TableInfo.Column("artist", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedTracks.put("artworkUrl", TableInfo.Column("artworkUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedTracks.put("duration", TableInfo.Column("duration", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedTracks.put("localAudioPath", TableInfo.Column("localAudioPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedTracks.put("localArtworkPath", TableInfo.Column("localArtworkPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedTracks.put("downloadedAt", TableInfo.Column("downloadedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDownloadedTracks: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDownloadedTracks: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDownloadedTracks: TableInfo = TableInfo("downloaded_tracks", _columnsDownloadedTracks, _foreignKeysDownloadedTracks, _indicesDownloadedTracks)
        val _existingDownloadedTracks: TableInfo = read(connection, "downloaded_tracks")
        if (!_infoDownloadedTracks.equals(_existingDownloadedTracks)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |downloaded_tracks(com.alananasss.kittytune.data.local.LocalTrack).
              | Expected:
              |""".trimMargin() + _infoDownloadedTracks + """
              |
              | Found:
              |""".trimMargin() + _existingDownloadedTracks)
        }
        val _columnsDownloadedPlaylists: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsDownloadedPlaylists.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedPlaylists.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedPlaylists.put("artist", TableInfo.Column("artist", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedPlaylists.put("artworkUrl", TableInfo.Column("artworkUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedPlaylists.put("trackCount", TableInfo.Column("trackCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedPlaylists.put("isUserCreated", TableInfo.Column("isUserCreated", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedPlaylists.put("localCoverPath", TableInfo.Column("localCoverPath", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedPlaylists.put("permalinkUrl", TableInfo.Column("permalinkUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsDownloadedPlaylists.put("addedAt", TableInfo.Column("addedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysDownloadedPlaylists: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesDownloadedPlaylists: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoDownloadedPlaylists: TableInfo = TableInfo("downloaded_playlists", _columnsDownloadedPlaylists, _foreignKeysDownloadedPlaylists, _indicesDownloadedPlaylists)
        val _existingDownloadedPlaylists: TableInfo = read(connection, "downloaded_playlists")
        if (!_infoDownloadedPlaylists.equals(_existingDownloadedPlaylists)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |downloaded_playlists(com.alananasss.kittytune.data.local.LocalPlaylist).
              | Expected:
              |""".trimMargin() + _infoDownloadedPlaylists + """
              |
              | Found:
              |""".trimMargin() + _existingDownloadedPlaylists)
        }
        val _columnsPlaylistTrackCrossRef: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlaylistTrackCrossRef.put("playlistId", TableInfo.Column("playlistId", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylistTrackCrossRef.put("trackId", TableInfo.Column("trackId", "INTEGER", true, 2, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlaylistTrackCrossRef.put("addedAt", TableInfo.Column("addedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlaylistTrackCrossRef: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPlaylistTrackCrossRef: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPlaylistTrackCrossRef: TableInfo = TableInfo("playlist_track_cross_ref", _columnsPlaylistTrackCrossRef, _foreignKeysPlaylistTrackCrossRef, _indicesPlaylistTrackCrossRef)
        val _existingPlaylistTrackCrossRef: TableInfo = read(connection, "playlist_track_cross_ref")
        if (!_infoPlaylistTrackCrossRef.equals(_existingPlaylistTrackCrossRef)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |playlist_track_cross_ref(com.alananasss.kittytune.data.local.PlaylistTrackCrossRef).
              | Expected:
              |""".trimMargin() + _infoPlaylistTrackCrossRef + """
              |
              | Found:
              |""".trimMargin() + _existingPlaylistTrackCrossRef)
        }
        val _columnsPlayHistory: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsPlayHistory.put("id", TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("numericId", TableInfo.Column("numericId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("title", TableInfo.Column("title", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("subtitle", TableInfo.Column("subtitle", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("imageUrl", TableInfo.Column("imageUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("type", TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("timestamp", TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("isVerified", TableInfo.Column("isVerified", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("source", TableInfo.Column("source", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsPlayHistory.put("originalUrl", TableInfo.Column("originalUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysPlayHistory: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesPlayHistory: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoPlayHistory: TableInfo = TableInfo("play_history", _columnsPlayHistory, _foreignKeysPlayHistory, _indicesPlayHistory)
        val _existingPlayHistory: TableInfo = read(connection, "play_history")
        if (!_infoPlayHistory.equals(_existingPlayHistory)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |play_history(com.alananasss.kittytune.data.local.HistoryItem).
              | Expected:
              |""".trimMargin() + _infoPlayHistory + """
              |
              | Found:
              |""".trimMargin() + _existingPlayHistory)
        }
        val _columnsSavedArtists: MutableMap<String, TableInfo.Column> = mutableMapOf()
        _columnsSavedArtists.put("id", TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedArtists.put("username", TableInfo.Column("username", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedArtists.put("avatarUrl", TableInfo.Column("avatarUrl", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedArtists.put("trackCount", TableInfo.Column("trackCount", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        _columnsSavedArtists.put("savedAt", TableInfo.Column("savedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY))
        val _foreignKeysSavedArtists: MutableSet<TableInfo.ForeignKey> = mutableSetOf()
        val _indicesSavedArtists: MutableSet<TableInfo.Index> = mutableSetOf()
        val _infoSavedArtists: TableInfo = TableInfo("saved_artists", _columnsSavedArtists, _foreignKeysSavedArtists, _indicesSavedArtists)
        val _existingSavedArtists: TableInfo = read(connection, "saved_artists")
        if (!_infoSavedArtists.equals(_existingSavedArtists)) {
          return RoomOpenDelegate.ValidationResult(false, """
              |saved_artists(com.alananasss.kittytune.data.local.LocalArtist).
              | Expected:
              |""".trimMargin() + _infoSavedArtists + """
              |
              | Found:
              |""".trimMargin() + _existingSavedArtists)
        }
        return RoomOpenDelegate.ValidationResult(true, null)
      }
    }
    return _openDelegate
  }

  protected override fun createInvalidationTracker(): InvalidationTracker {
    val _shadowTablesMap: MutableMap<String, String> = mutableMapOf()
    val _viewTables: MutableMap<String, Set<String>> = mutableMapOf()
    return InvalidationTracker(this, _shadowTablesMap, _viewTables, "downloaded_tracks", "downloaded_playlists", "playlist_track_cross_ref", "play_history", "saved_artists")
  }

  public override fun clearAllTables() {
    super.performClear(false, "downloaded_tracks", "downloaded_playlists", "playlist_track_cross_ref", "play_history", "saved_artists")
  }

  protected override fun getRequiredTypeConverterClasses(): Map<KClass<*>, List<KClass<*>>> {
    val _typeConvertersMap: MutableMap<KClass<*>, List<KClass<*>>> = mutableMapOf()
    _typeConvertersMap.put(DownloadDao::class, DownloadDao_Impl.getRequiredConverters())
    return _typeConvertersMap
  }

  public override fun getRequiredAutoMigrationSpecClasses(): Set<KClass<out AutoMigrationSpec>> {
    val _autoMigrationSpecsSet: MutableSet<KClass<out AutoMigrationSpec>> = mutableSetOf()
    return _autoMigrationSpecsSet
  }

  public override fun createAutoMigrations(autoMigrationSpecs: Map<KClass<out AutoMigrationSpec>, AutoMigrationSpec>): List<Migration> {
    val _autoMigrations: MutableList<Migration> = mutableListOf()
    return _autoMigrations
  }

  public override fun downloadDao(): DownloadDao = _downloadDao.value
}
