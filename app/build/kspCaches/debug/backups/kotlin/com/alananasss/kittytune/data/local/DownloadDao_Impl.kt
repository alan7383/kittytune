package com.alananasss.kittytune.`data`.local

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import javax.`annotation`.processing.Generated
import kotlin.Boolean
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Generated(value = ["androidx.room.RoomProcessor"])
@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class DownloadDao_Impl(
  __db: RoomDatabase,
) : DownloadDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfLocalTrack: EntityInsertAdapter<LocalTrack>

  private val __insertAdapterOfLocalPlaylist: EntityInsertAdapter<LocalPlaylist>

  private val __insertAdapterOfPlaylistTrackCrossRef: EntityInsertAdapter<PlaylistTrackCrossRef>

  private val __insertAdapterOfLocalArtist: EntityInsertAdapter<LocalArtist>

  private val __insertAdapterOfHistoryItem: EntityInsertAdapter<HistoryItem>

  private val __updateAdapterOfLocalTrack: EntityDeleteOrUpdateAdapter<LocalTrack>

  private val __updateAdapterOfLocalPlaylist: EntityDeleteOrUpdateAdapter<LocalPlaylist>

  private val __updateAdapterOfPlaylistTrackCrossRef:
      EntityDeleteOrUpdateAdapter<PlaylistTrackCrossRef>
  init {
    this.__db = __db
    this.__insertAdapterOfLocalTrack = object : EntityInsertAdapter<LocalTrack>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `downloaded_tracks` (`id`,`title`,`artist`,`artworkUrl`,`duration`,`localAudioPath`,`localArtworkPath`,`downloadedAt`) VALUES (?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LocalTrack) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.artist)
        statement.bindText(4, entity.artworkUrl)
        statement.bindLong(5, entity.duration)
        statement.bindText(6, entity.localAudioPath)
        statement.bindText(7, entity.localArtworkPath)
        statement.bindLong(8, entity.downloadedAt)
      }
    }
    this.__insertAdapterOfLocalPlaylist = object : EntityInsertAdapter<LocalPlaylist>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `downloaded_playlists` (`id`,`title`,`artist`,`artworkUrl`,`trackCount`,`isUserCreated`,`localCoverPath`,`permalinkUrl`,`addedAt`) VALUES (?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LocalPlaylist) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.artist)
        statement.bindText(4, entity.artworkUrl)
        statement.bindLong(5, entity.trackCount.toLong())
        val _tmp: Int = if (entity.isUserCreated) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        val _tmpLocalCoverPath: String? = entity.localCoverPath
        if (_tmpLocalCoverPath == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpLocalCoverPath)
        }
        val _tmpPermalinkUrl: String? = entity.permalinkUrl
        if (_tmpPermalinkUrl == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpPermalinkUrl)
        }
        statement.bindLong(9, entity.addedAt)
      }
    }
    this.__insertAdapterOfPlaylistTrackCrossRef = object : EntityInsertAdapter<PlaylistTrackCrossRef>() {
      protected override fun createQuery(): String = "INSERT OR IGNORE INTO `playlist_track_cross_ref` (`playlistId`,`trackId`,`addedAt`) VALUES (?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: PlaylistTrackCrossRef) {
        statement.bindLong(1, entity.playlistId)
        statement.bindLong(2, entity.trackId)
        statement.bindLong(3, entity.addedAt)
      }
    }
    this.__insertAdapterOfLocalArtist = object : EntityInsertAdapter<LocalArtist>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `saved_artists` (`id`,`username`,`avatarUrl`,`trackCount`,`savedAt`) VALUES (?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: LocalArtist) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.username)
        statement.bindText(3, entity.avatarUrl)
        statement.bindLong(4, entity.trackCount.toLong())
        statement.bindLong(5, entity.savedAt)
      }
    }
    this.__insertAdapterOfHistoryItem = object : EntityInsertAdapter<HistoryItem>() {
      protected override fun createQuery(): String = "INSERT OR REPLACE INTO `play_history` (`id`,`numericId`,`title`,`subtitle`,`imageUrl`,`type`,`timestamp`,`isVerified`,`source`,`originalUrl`) VALUES (?,?,?,?,?,?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: HistoryItem) {
        statement.bindText(1, entity.id)
        statement.bindLong(2, entity.numericId)
        statement.bindText(3, entity.title)
        statement.bindText(4, entity.subtitle)
        statement.bindText(5, entity.imageUrl)
        statement.bindText(6, entity.type)
        statement.bindLong(7, entity.timestamp)
        val _tmp: Int = if (entity.isVerified) 1 else 0
        statement.bindLong(8, _tmp.toLong())
        statement.bindText(9, entity.source)
        val _tmpOriginalUrl: String? = entity.originalUrl
        if (_tmpOriginalUrl == null) {
          statement.bindNull(10)
        } else {
          statement.bindText(10, _tmpOriginalUrl)
        }
      }
    }
    this.__updateAdapterOfLocalTrack = object : EntityDeleteOrUpdateAdapter<LocalTrack>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `downloaded_tracks` SET `id` = ?,`title` = ?,`artist` = ?,`artworkUrl` = ?,`duration` = ?,`localAudioPath` = ?,`localArtworkPath` = ?,`downloadedAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: LocalTrack) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.artist)
        statement.bindText(4, entity.artworkUrl)
        statement.bindLong(5, entity.duration)
        statement.bindText(6, entity.localAudioPath)
        statement.bindText(7, entity.localArtworkPath)
        statement.bindLong(8, entity.downloadedAt)
        statement.bindLong(9, entity.id)
      }
    }
    this.__updateAdapterOfLocalPlaylist = object : EntityDeleteOrUpdateAdapter<LocalPlaylist>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `downloaded_playlists` SET `id` = ?,`title` = ?,`artist` = ?,`artworkUrl` = ?,`trackCount` = ?,`isUserCreated` = ?,`localCoverPath` = ?,`permalinkUrl` = ?,`addedAt` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: LocalPlaylist) {
        statement.bindLong(1, entity.id)
        statement.bindText(2, entity.title)
        statement.bindText(3, entity.artist)
        statement.bindText(4, entity.artworkUrl)
        statement.bindLong(5, entity.trackCount.toLong())
        val _tmp: Int = if (entity.isUserCreated) 1 else 0
        statement.bindLong(6, _tmp.toLong())
        val _tmpLocalCoverPath: String? = entity.localCoverPath
        if (_tmpLocalCoverPath == null) {
          statement.bindNull(7)
        } else {
          statement.bindText(7, _tmpLocalCoverPath)
        }
        val _tmpPermalinkUrl: String? = entity.permalinkUrl
        if (_tmpPermalinkUrl == null) {
          statement.bindNull(8)
        } else {
          statement.bindText(8, _tmpPermalinkUrl)
        }
        statement.bindLong(9, entity.addedAt)
        statement.bindLong(10, entity.id)
      }
    }
    this.__updateAdapterOfPlaylistTrackCrossRef = object : EntityDeleteOrUpdateAdapter<PlaylistTrackCrossRef>() {
      protected override fun createQuery(): String = "UPDATE OR ABORT `playlist_track_cross_ref` SET `playlistId` = ?,`trackId` = ?,`addedAt` = ? WHERE `playlistId` = ? AND `trackId` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: PlaylistTrackCrossRef) {
        statement.bindLong(1, entity.playlistId)
        statement.bindLong(2, entity.trackId)
        statement.bindLong(3, entity.addedAt)
        statement.bindLong(4, entity.playlistId)
        statement.bindLong(5, entity.trackId)
      }
    }
  }

  public override suspend fun insertTrack(track: LocalTrack): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLocalTrack.insert(_connection, track)
  }

  public override suspend fun insertPlaylist(playlist: LocalPlaylist): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLocalPlaylist.insert(_connection, playlist)
  }

  public override suspend fun insertPlaylistTrackRef(ref: PlaylistTrackCrossRef): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfPlaylistTrackCrossRef.insert(_connection, ref)
  }

  public override suspend fun insertArtist(artist: LocalArtist): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfLocalArtist.insert(_connection, artist)
  }

  public override suspend fun insertHistory(item: HistoryItem): Unit = performSuspending(__db, false, true) { _connection ->
    __insertAdapterOfHistoryItem.insert(_connection, item)
  }

  public override suspend fun updateTrack(track: LocalTrack): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfLocalTrack.handle(_connection, track)
  }

  public override suspend fun updatePlaylist(playlist: LocalPlaylist): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfLocalPlaylist.handle(_connection, playlist)
  }

  public override suspend fun updatePlaylistTrackRef(ref: PlaylistTrackCrossRef): Unit = performSuspending(__db, false, true) { _connection ->
    __updateAdapterOfPlaylistTrackCrossRef.handle(_connection, ref)
  }

  public override suspend fun getTrack(trackId: Long): LocalTrack? {
    val _sql: String = "SELECT * FROM downloaded_tracks WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, trackId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfLocalAudioPath: Int = getColumnIndexOrThrow(_stmt, "localAudioPath")
        val _columnIndexOfLocalArtworkPath: Int = getColumnIndexOrThrow(_stmt, "localArtworkPath")
        val _columnIndexOfDownloadedAt: Int = getColumnIndexOrThrow(_stmt, "downloadedAt")
        val _result: LocalTrack?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpDuration: Long
          _tmpDuration = _stmt.getLong(_columnIndexOfDuration)
          val _tmpLocalAudioPath: String
          _tmpLocalAudioPath = _stmt.getText(_columnIndexOfLocalAudioPath)
          val _tmpLocalArtworkPath: String
          _tmpLocalArtworkPath = _stmt.getText(_columnIndexOfLocalArtworkPath)
          val _tmpDownloadedAt: Long
          _tmpDownloadedAt = _stmt.getLong(_columnIndexOfDownloadedAt)
          _result = LocalTrack(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpDuration,_tmpLocalAudioPath,_tmpLocalArtworkPath,_tmpDownloadedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllTracks(): Flow<List<LocalTrack>> {
    val _sql: String = "SELECT * FROM downloaded_tracks WHERE localAudioPath != '' ORDER BY downloadedAt DESC"
    return createFlow(__db, false, arrayOf("downloaded_tracks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfLocalAudioPath: Int = getColumnIndexOrThrow(_stmt, "localAudioPath")
        val _columnIndexOfLocalArtworkPath: Int = getColumnIndexOrThrow(_stmt, "localArtworkPath")
        val _columnIndexOfDownloadedAt: Int = getColumnIndexOrThrow(_stmt, "downloadedAt")
        val _result: MutableList<LocalTrack> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalTrack
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpDuration: Long
          _tmpDuration = _stmt.getLong(_columnIndexOfDuration)
          val _tmpLocalAudioPath: String
          _tmpLocalAudioPath = _stmt.getText(_columnIndexOfLocalAudioPath)
          val _tmpLocalArtworkPath: String
          _tmpLocalArtworkPath = _stmt.getText(_columnIndexOfLocalArtworkPath)
          val _tmpDownloadedAt: Long
          _tmpDownloadedAt = _stmt.getLong(_columnIndexOfDownloadedAt)
          _item = LocalTrack(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpDuration,_tmpLocalAudioPath,_tmpLocalArtworkPath,_tmpDownloadedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getRef(playlistId: Long, trackId: Long): PlaylistTrackCrossRef? {
    val _sql: String = "SELECT * FROM playlist_track_cross_ref WHERE playlistId = ? AND trackId = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, trackId)
        val _columnIndexOfPlaylistId: Int = getColumnIndexOrThrow(_stmt, "playlistId")
        val _columnIndexOfTrackId: Int = getColumnIndexOrThrow(_stmt, "trackId")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: PlaylistTrackCrossRef?
        if (_stmt.step()) {
          val _tmpPlaylistId: Long
          _tmpPlaylistId = _stmt.getLong(_columnIndexOfPlaylistId)
          val _tmpTrackId: Long
          _tmpTrackId = _stmt.getLong(_columnIndexOfTrackId)
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result = PlaylistTrackCrossRef(_tmpPlaylistId,_tmpTrackId,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllPlaylists(): Flow<List<LocalPlaylist>> {
    val _sql: String = "SELECT * FROM downloaded_playlists"
    return createFlow(__db, false, arrayOf("downloaded_playlists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfTrackCount: Int = getColumnIndexOrThrow(_stmt, "trackCount")
        val _columnIndexOfIsUserCreated: Int = getColumnIndexOrThrow(_stmt, "isUserCreated")
        val _columnIndexOfLocalCoverPath: Int = getColumnIndexOrThrow(_stmt, "localCoverPath")
        val _columnIndexOfPermalinkUrl: Int = getColumnIndexOrThrow(_stmt, "permalinkUrl")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<LocalPlaylist> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalPlaylist
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpTrackCount: Int
          _tmpTrackCount = _stmt.getLong(_columnIndexOfTrackCount).toInt()
          val _tmpIsUserCreated: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsUserCreated).toInt()
          _tmpIsUserCreated = _tmp != 0
          val _tmpLocalCoverPath: String?
          if (_stmt.isNull(_columnIndexOfLocalCoverPath)) {
            _tmpLocalCoverPath = null
          } else {
            _tmpLocalCoverPath = _stmt.getText(_columnIndexOfLocalCoverPath)
          }
          val _tmpPermalinkUrl: String?
          if (_stmt.isNull(_columnIndexOfPermalinkUrl)) {
            _tmpPermalinkUrl = null
          } else {
            _tmpPermalinkUrl = _stmt.getText(_columnIndexOfPermalinkUrl)
          }
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = LocalPlaylist(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpTrackCount,_tmpIsUserCreated,_tmpLocalCoverPath,_tmpPermalinkUrl,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getDownloadedPlaylists(): Flow<List<LocalPlaylist>> {
    val _sql: String = """
        |
        |                SELECT DISTINCT P.* FROM downloaded_playlists P
        |                INNER JOIN playlist_track_cross_ref R ON P.id = R.playlistId
        |                INNER JOIN downloaded_tracks T ON R.trackId = T.id
        |                WHERE T.localAudioPath != ''
        |            
        """.trimMargin()
    return createFlow(__db, false, arrayOf("downloaded_playlists", "playlist_track_cross_ref", "downloaded_tracks")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfTrackCount: Int = getColumnIndexOrThrow(_stmt, "trackCount")
        val _columnIndexOfIsUserCreated: Int = getColumnIndexOrThrow(_stmt, "isUserCreated")
        val _columnIndexOfLocalCoverPath: Int = getColumnIndexOrThrow(_stmt, "localCoverPath")
        val _columnIndexOfPermalinkUrl: Int = getColumnIndexOrThrow(_stmt, "permalinkUrl")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<LocalPlaylist> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalPlaylist
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpTrackCount: Int
          _tmpTrackCount = _stmt.getLong(_columnIndexOfTrackCount).toInt()
          val _tmpIsUserCreated: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsUserCreated).toInt()
          _tmpIsUserCreated = _tmp != 0
          val _tmpLocalCoverPath: String?
          if (_stmt.isNull(_columnIndexOfLocalCoverPath)) {
            _tmpLocalCoverPath = null
          } else {
            _tmpLocalCoverPath = _stmt.getText(_columnIndexOfLocalCoverPath)
          }
          val _tmpPermalinkUrl: String?
          if (_stmt.isNull(_columnIndexOfPermalinkUrl)) {
            _tmpPermalinkUrl = null
          } else {
            _tmpPermalinkUrl = _stmt.getText(_columnIndexOfPermalinkUrl)
          }
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = LocalPlaylist(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpTrackCount,_tmpIsUserCreated,_tmpLocalCoverPath,_tmpPermalinkUrl,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getUserPlaylists(): Flow<List<LocalPlaylist>> {
    val _sql: String = "SELECT * FROM downloaded_playlists WHERE isUserCreated = 1"
    return createFlow(__db, false, arrayOf("downloaded_playlists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfTrackCount: Int = getColumnIndexOrThrow(_stmt, "trackCount")
        val _columnIndexOfIsUserCreated: Int = getColumnIndexOrThrow(_stmt, "isUserCreated")
        val _columnIndexOfLocalCoverPath: Int = getColumnIndexOrThrow(_stmt, "localCoverPath")
        val _columnIndexOfPermalinkUrl: Int = getColumnIndexOrThrow(_stmt, "permalinkUrl")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: MutableList<LocalPlaylist> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalPlaylist
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpTrackCount: Int
          _tmpTrackCount = _stmt.getLong(_columnIndexOfTrackCount).toInt()
          val _tmpIsUserCreated: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsUserCreated).toInt()
          _tmpIsUserCreated = _tmp != 0
          val _tmpLocalCoverPath: String?
          if (_stmt.isNull(_columnIndexOfLocalCoverPath)) {
            _tmpLocalCoverPath = null
          } else {
            _tmpLocalCoverPath = _stmt.getText(_columnIndexOfLocalCoverPath)
          }
          val _tmpPermalinkUrl: String?
          if (_stmt.isNull(_columnIndexOfPermalinkUrl)) {
            _tmpPermalinkUrl = null
          } else {
            _tmpPermalinkUrl = _stmt.getText(_columnIndexOfPermalinkUrl)
          }
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _item = LocalPlaylist(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpTrackCount,_tmpIsUserCreated,_tmpLocalCoverPath,_tmpPermalinkUrl,_tmpAddedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getPlaylist(playlistId: Long): LocalPlaylist? {
    val _sql: String = "SELECT * FROM downloaded_playlists WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfTrackCount: Int = getColumnIndexOrThrow(_stmt, "trackCount")
        val _columnIndexOfIsUserCreated: Int = getColumnIndexOrThrow(_stmt, "isUserCreated")
        val _columnIndexOfLocalCoverPath: Int = getColumnIndexOrThrow(_stmt, "localCoverPath")
        val _columnIndexOfPermalinkUrl: Int = getColumnIndexOrThrow(_stmt, "permalinkUrl")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: LocalPlaylist?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpTrackCount: Int
          _tmpTrackCount = _stmt.getLong(_columnIndexOfTrackCount).toInt()
          val _tmpIsUserCreated: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsUserCreated).toInt()
          _tmpIsUserCreated = _tmp != 0
          val _tmpLocalCoverPath: String?
          if (_stmt.isNull(_columnIndexOfLocalCoverPath)) {
            _tmpLocalCoverPath = null
          } else {
            _tmpLocalCoverPath = _stmt.getText(_columnIndexOfLocalCoverPath)
          }
          val _tmpPermalinkUrl: String?
          if (_stmt.isNull(_columnIndexOfPermalinkUrl)) {
            _tmpPermalinkUrl = null
          } else {
            _tmpPermalinkUrl = _stmt.getText(_columnIndexOfPermalinkUrl)
          }
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result = LocalPlaylist(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpTrackCount,_tmpIsUserCreated,_tmpLocalCoverPath,_tmpPermalinkUrl,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getPlaylistFlow(playlistId: Long): Flow<LocalPlaylist?> {
    val _sql: String = "SELECT * FROM downloaded_playlists WHERE id = ?"
    return createFlow(__db, false, arrayOf("downloaded_playlists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfTrackCount: Int = getColumnIndexOrThrow(_stmt, "trackCount")
        val _columnIndexOfIsUserCreated: Int = getColumnIndexOrThrow(_stmt, "isUserCreated")
        val _columnIndexOfLocalCoverPath: Int = getColumnIndexOrThrow(_stmt, "localCoverPath")
        val _columnIndexOfPermalinkUrl: Int = getColumnIndexOrThrow(_stmt, "permalinkUrl")
        val _columnIndexOfAddedAt: Int = getColumnIndexOrThrow(_stmt, "addedAt")
        val _result: LocalPlaylist?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpTrackCount: Int
          _tmpTrackCount = _stmt.getLong(_columnIndexOfTrackCount).toInt()
          val _tmpIsUserCreated: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsUserCreated).toInt()
          _tmpIsUserCreated = _tmp != 0
          val _tmpLocalCoverPath: String?
          if (_stmt.isNull(_columnIndexOfLocalCoverPath)) {
            _tmpLocalCoverPath = null
          } else {
            _tmpLocalCoverPath = _stmt.getText(_columnIndexOfLocalCoverPath)
          }
          val _tmpPermalinkUrl: String?
          if (_stmt.isNull(_columnIndexOfPermalinkUrl)) {
            _tmpPermalinkUrl = null
          } else {
            _tmpPermalinkUrl = _stmt.getText(_columnIndexOfPermalinkUrl)
          }
          val _tmpAddedAt: Long
          _tmpAddedAt = _stmt.getLong(_columnIndexOfAddedAt)
          _result = LocalPlaylist(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpTrackCount,_tmpIsUserCreated,_tmpLocalCoverPath,_tmpPermalinkUrl,_tmpAddedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getOrphanTracksList(): List<LocalTrack> {
    val _sql: String = "SELECT * FROM downloaded_tracks WHERE localAudioPath != '' AND id NOT IN (SELECT trackId FROM playlist_track_cross_ref) ORDER BY downloadedAt DESC"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfLocalAudioPath: Int = getColumnIndexOrThrow(_stmt, "localAudioPath")
        val _columnIndexOfLocalArtworkPath: Int = getColumnIndexOrThrow(_stmt, "localArtworkPath")
        val _columnIndexOfDownloadedAt: Int = getColumnIndexOrThrow(_stmt, "downloadedAt")
        val _result: MutableList<LocalTrack> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalTrack
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpDuration: Long
          _tmpDuration = _stmt.getLong(_columnIndexOfDuration)
          val _tmpLocalAudioPath: String
          _tmpLocalAudioPath = _stmt.getText(_columnIndexOfLocalAudioPath)
          val _tmpLocalArtworkPath: String
          _tmpLocalArtworkPath = _stmt.getText(_columnIndexOfLocalArtworkPath)
          val _tmpDownloadedAt: Long
          _tmpDownloadedAt = _stmt.getLong(_columnIndexOfDownloadedAt)
          _item = LocalTrack(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpDuration,_tmpLocalAudioPath,_tmpLocalArtworkPath,_tmpDownloadedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getTracksForPlaylist(playlistId: Long): Flow<List<LocalTrack>> {
    val _sql: String = """
        |
        |                SELECT downloaded_tracks.* FROM downloaded_tracks 
        |                INNER JOIN playlist_track_cross_ref ON downloaded_tracks.id = playlist_track_cross_ref.trackId 
        |                WHERE playlist_track_cross_ref.playlistId = ?
        |                ORDER BY playlist_track_cross_ref.addedAt ASC
        |            
        """.trimMargin()
    return createFlow(__db, true, arrayOf("downloaded_tracks", "playlist_track_cross_ref")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfLocalAudioPath: Int = getColumnIndexOrThrow(_stmt, "localAudioPath")
        val _columnIndexOfLocalArtworkPath: Int = getColumnIndexOrThrow(_stmt, "localArtworkPath")
        val _columnIndexOfDownloadedAt: Int = getColumnIndexOrThrow(_stmt, "downloadedAt")
        val _result: MutableList<LocalTrack> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalTrack
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpDuration: Long
          _tmpDuration = _stmt.getLong(_columnIndexOfDuration)
          val _tmpLocalAudioPath: String
          _tmpLocalAudioPath = _stmt.getText(_columnIndexOfLocalAudioPath)
          val _tmpLocalArtworkPath: String
          _tmpLocalArtworkPath = _stmt.getText(_columnIndexOfLocalArtworkPath)
          val _tmpDownloadedAt: Long
          _tmpDownloadedAt = _stmt.getLong(_columnIndexOfDownloadedAt)
          _item = LocalTrack(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpDuration,_tmpLocalAudioPath,_tmpLocalArtworkPath,_tmpDownloadedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getArtist(artistId: Long): LocalArtist? {
    val _sql: String = "SELECT * FROM saved_artists WHERE id = ?"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, artistId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUsername: Int = getColumnIndexOrThrow(_stmt, "username")
        val _columnIndexOfAvatarUrl: Int = getColumnIndexOrThrow(_stmt, "avatarUrl")
        val _columnIndexOfTrackCount: Int = getColumnIndexOrThrow(_stmt, "trackCount")
        val _columnIndexOfSavedAt: Int = getColumnIndexOrThrow(_stmt, "savedAt")
        val _result: LocalArtist?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUsername: String
          _tmpUsername = _stmt.getText(_columnIndexOfUsername)
          val _tmpAvatarUrl: String
          _tmpAvatarUrl = _stmt.getText(_columnIndexOfAvatarUrl)
          val _tmpTrackCount: Int
          _tmpTrackCount = _stmt.getLong(_columnIndexOfTrackCount).toInt()
          val _tmpSavedAt: Long
          _tmpSavedAt = _stmt.getLong(_columnIndexOfSavedAt)
          _result = LocalArtist(_tmpId,_tmpUsername,_tmpAvatarUrl,_tmpTrackCount,_tmpSavedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getArtistFlow(artistId: Long): Flow<LocalArtist?> {
    val _sql: String = "SELECT * FROM saved_artists WHERE id = ?"
    return createFlow(__db, false, arrayOf("saved_artists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, artistId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUsername: Int = getColumnIndexOrThrow(_stmt, "username")
        val _columnIndexOfAvatarUrl: Int = getColumnIndexOrThrow(_stmt, "avatarUrl")
        val _columnIndexOfTrackCount: Int = getColumnIndexOrThrow(_stmt, "trackCount")
        val _columnIndexOfSavedAt: Int = getColumnIndexOrThrow(_stmt, "savedAt")
        val _result: LocalArtist?
        if (_stmt.step()) {
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUsername: String
          _tmpUsername = _stmt.getText(_columnIndexOfUsername)
          val _tmpAvatarUrl: String
          _tmpAvatarUrl = _stmt.getText(_columnIndexOfAvatarUrl)
          val _tmpTrackCount: Int
          _tmpTrackCount = _stmt.getLong(_columnIndexOfTrackCount).toInt()
          val _tmpSavedAt: Long
          _tmpSavedAt = _stmt.getLong(_columnIndexOfSavedAt)
          _result = LocalArtist(_tmpId,_tmpUsername,_tmpAvatarUrl,_tmpTrackCount,_tmpSavedAt)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getAllSavedArtists(): Flow<List<LocalArtist>> {
    val _sql: String = "SELECT * FROM saved_artists ORDER BY savedAt DESC"
    return createFlow(__db, false, arrayOf("saved_artists")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfUsername: Int = getColumnIndexOrThrow(_stmt, "username")
        val _columnIndexOfAvatarUrl: Int = getColumnIndexOrThrow(_stmt, "avatarUrl")
        val _columnIndexOfTrackCount: Int = getColumnIndexOrThrow(_stmt, "trackCount")
        val _columnIndexOfSavedAt: Int = getColumnIndexOrThrow(_stmt, "savedAt")
        val _result: MutableList<LocalArtist> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalArtist
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpUsername: String
          _tmpUsername = _stmt.getText(_columnIndexOfUsername)
          val _tmpAvatarUrl: String
          _tmpAvatarUrl = _stmt.getText(_columnIndexOfAvatarUrl)
          val _tmpTrackCount: Int
          _tmpTrackCount = _stmt.getLong(_columnIndexOfTrackCount).toInt()
          val _tmpSavedAt: Long
          _tmpSavedAt = _stmt.getLong(_columnIndexOfSavedAt)
          _item = LocalArtist(_tmpId,_tmpUsername,_tmpAvatarUrl,_tmpTrackCount,_tmpSavedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override fun getHistory(): Flow<List<HistoryItem>> {
    val _sql: String = "SELECT * FROM play_history ORDER BY timestamp DESC LIMIT 20"
    return createFlow(__db, false, arrayOf("play_history")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfNumericId: Int = getColumnIndexOrThrow(_stmt, "numericId")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfSubtitle: Int = getColumnIndexOrThrow(_stmt, "subtitle")
        val _columnIndexOfImageUrl: Int = getColumnIndexOrThrow(_stmt, "imageUrl")
        val _columnIndexOfType: Int = getColumnIndexOrThrow(_stmt, "type")
        val _columnIndexOfTimestamp: Int = getColumnIndexOrThrow(_stmt, "timestamp")
        val _columnIndexOfIsVerified: Int = getColumnIndexOrThrow(_stmt, "isVerified")
        val _columnIndexOfSource: Int = getColumnIndexOrThrow(_stmt, "source")
        val _columnIndexOfOriginalUrl: Int = getColumnIndexOrThrow(_stmt, "originalUrl")
        val _result: MutableList<HistoryItem> = mutableListOf()
        while (_stmt.step()) {
          val _item: HistoryItem
          val _tmpId: String
          _tmpId = _stmt.getText(_columnIndexOfId)
          val _tmpNumericId: Long
          _tmpNumericId = _stmt.getLong(_columnIndexOfNumericId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpSubtitle: String
          _tmpSubtitle = _stmt.getText(_columnIndexOfSubtitle)
          val _tmpImageUrl: String
          _tmpImageUrl = _stmt.getText(_columnIndexOfImageUrl)
          val _tmpType: String
          _tmpType = _stmt.getText(_columnIndexOfType)
          val _tmpTimestamp: Long
          _tmpTimestamp = _stmt.getLong(_columnIndexOfTimestamp)
          val _tmpIsVerified: Boolean
          val _tmp: Int
          _tmp = _stmt.getLong(_columnIndexOfIsVerified).toInt()
          _tmpIsVerified = _tmp != 0
          val _tmpSource: String
          _tmpSource = _stmt.getText(_columnIndexOfSource)
          val _tmpOriginalUrl: String?
          if (_stmt.isNull(_columnIndexOfOriginalUrl)) {
            _tmpOriginalUrl = null
          } else {
            _tmpOriginalUrl = _stmt.getText(_columnIndexOfOriginalUrl)
          }
          _item = HistoryItem(_tmpId,_tmpNumericId,_tmpTitle,_tmpSubtitle,_tmpImageUrl,_tmpType,_tmpTimestamp,_tmpIsVerified,_tmpSource,_tmpOriginalUrl)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getTracksForPlaylistSync(playlistId: Long): List<LocalTrack> {
    val _sql: String = """
        |
        |                SELECT downloaded_tracks.* FROM downloaded_tracks 
        |                INNER JOIN playlist_track_cross_ref ON downloaded_tracks.id = playlist_track_cross_ref.trackId 
        |                WHERE playlist_track_cross_ref.playlistId = ?
        |                ORDER BY playlist_track_cross_ref.addedAt ASC
        |            
        """.trimMargin()
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfTitle: Int = getColumnIndexOrThrow(_stmt, "title")
        val _columnIndexOfArtist: Int = getColumnIndexOrThrow(_stmt, "artist")
        val _columnIndexOfArtworkUrl: Int = getColumnIndexOrThrow(_stmt, "artworkUrl")
        val _columnIndexOfDuration: Int = getColumnIndexOrThrow(_stmt, "duration")
        val _columnIndexOfLocalAudioPath: Int = getColumnIndexOrThrow(_stmt, "localAudioPath")
        val _columnIndexOfLocalArtworkPath: Int = getColumnIndexOrThrow(_stmt, "localArtworkPath")
        val _columnIndexOfDownloadedAt: Int = getColumnIndexOrThrow(_stmt, "downloadedAt")
        val _result: MutableList<LocalTrack> = mutableListOf()
        while (_stmt.step()) {
          val _item: LocalTrack
          val _tmpId: Long
          _tmpId = _stmt.getLong(_columnIndexOfId)
          val _tmpTitle: String
          _tmpTitle = _stmt.getText(_columnIndexOfTitle)
          val _tmpArtist: String
          _tmpArtist = _stmt.getText(_columnIndexOfArtist)
          val _tmpArtworkUrl: String
          _tmpArtworkUrl = _stmt.getText(_columnIndexOfArtworkUrl)
          val _tmpDuration: Long
          _tmpDuration = _stmt.getLong(_columnIndexOfDuration)
          val _tmpLocalAudioPath: String
          _tmpLocalAudioPath = _stmt.getText(_columnIndexOfLocalAudioPath)
          val _tmpLocalArtworkPath: String
          _tmpLocalArtworkPath = _stmt.getText(_columnIndexOfLocalArtworkPath)
          val _tmpDownloadedAt: Long
          _tmpDownloadedAt = _stmt.getLong(_columnIndexOfDownloadedAt)
          _item = LocalTrack(_tmpId,_tmpTitle,_tmpArtist,_tmpArtworkUrl,_tmpDuration,_tmpLocalAudioPath,_tmpLocalArtworkPath,_tmpDownloadedAt)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteTrack(trackId: Long) {
    val _sql: String = "DELETE FROM downloaded_tracks WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, trackId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deletePlaylist(playlistId: Long) {
    val _sql: String = "DELETE FROM downloaded_playlists WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deletePlaylistRefs(playlistId: Long) {
    val _sql: String = "DELETE FROM playlist_track_cross_ref WHERE playlistId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: Long) {
    val _sql: String = "DELETE FROM playlist_track_cross_ref WHERE playlistId = ? AND trackId = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, playlistId)
        _argIndex = 2
        _stmt.bindLong(_argIndex, trackId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun updatePlaylistTitle(playlistId: Long, newTitle: String) {
    val _sql: String = "UPDATE downloaded_playlists SET title = ? WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, newTitle)
        _argIndex = 2
        _stmt.bindLong(_argIndex, playlistId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteArtist(artistId: Long) {
    val _sql: String = "DELETE FROM saved_artists WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, artistId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteHistoryItem(itemId: String) {
    val _sql: String = "DELETE FROM play_history WHERE id = ?"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindText(_argIndex, itemId)
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun clearHistory() {
    val _sql: String = "DELETE FROM play_history"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
