package com.alananasss.kittytune.data

import android.content.Context
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object HistoryRepository {
    private lateinit var database: AppDatabase
    private lateinit var appContext: Context
    private val scope = CoroutineScope(Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        database = AppDatabase.getDatabase(context)
        scope.launch {
            database.downloadDao().deleteHistoryItem("playlist:0")
            database.downloadDao().deleteHistoryItem("playlist:history")
            database.downloadDao().deleteHistoryItem("history")
        }
    }

    fun addToHistory(track: Track) {
        scope.launch {
            val safeSource = (track.source as? String) ?: "soundcloud"

            val item = HistoryItem(
                id = "track:${track.id}",
                numericId = track.id,
                title = track.title ?: appContext.getString(R.string.history_untitled_track),
                subtitle = track.user?.username ?: appContext.getString(R.string.history_unknown_artist),
                imageUrl = track.fullResArtwork.takeIf { !it.contains("picsum.photos") } ?: "",
                type = "TRACK",
                isVerified = track.user?.verified == true,
                source = safeSource,
                originalUrl = track.permalinkUrl
            )
            database.downloadDao().insertHistory(item)
        }
    }

    fun addToHistory(playlist: Playlist, isStation: Boolean = false, isProfile: Boolean = false) {
        if (playlist.id == 0L || playlist.title.equals(
                "history",
                ignoreCase = true
            ) || playlist.permalinkUrl == "history"
        ) {
            return
        }
        scope.launch {
            val isYoutubeRadio = playlist.permalinkUrl?.startsWith("yt_radio:") == true
            val (stringId, type) = when {
                isProfile -> "profile:${playlist.id}" to "PROFILE"
                isYoutubeRadio -> playlist.permalinkUrl!! to "STATION"
                isStation -> "station:${playlist.id}" to "STATION"
                playlist.id == -1L -> "likes" to "PLAYLIST"
                playlist.id == -2L -> "downloads" to "PLAYLIST"
                playlist.id < 0 -> "playlist:${playlist.id}" to "PLAYLIST"
                else -> "playlist:${playlist.id}" to "PLAYLIST"
            }

            val finalSubtitle = when {
                isProfile -> appContext.getString(R.string.history_type_artist)
                isYoutubeRadio -> "YouTube"
                isStation -> playlist.user?.username ?: appContext.getString(R.string.history_type_station)
                playlist.id == -1L || playlist.id == -2L -> appContext.getString(R.string.history_source_library)
                playlist.id < 0 -> appContext.getString(R.string.history_type_local_playlist)
                else -> playlist.user?.username ?: appContext.getString(R.string.history_source_soundcloud)
            }
            val finalTitle = when (playlist.id) {
                -1L -> appContext.getString(R.string.history_title_likes)
                -2L -> appContext.getString(R.string.history_title_downloads)
                else -> playlist.title ?: appContext.getString(R.string.history_default_playlist_title)
            }

            val isLocalPlaylist = playlist.id < 0
            val localPlaylist = if (isLocalPlaylist) database.downloadDao().getPlaylist(playlist.id) else null
            val resolvedImageUrl = localPlaylist?.localCoverPath?.takeIf { it.isNotEmpty() }
                ?: playlist.artworkUrl?.takeIf { it.isNotEmpty() && !it.contains("picsum.photos") }
                ?: localPlaylist?.artworkUrl?.takeIf { it.isNotEmpty() && !it.contains("picsum.photos") }
                ?: playlist.fullResArtwork.takeIf { !it.contains("picsum.photos") }
                ?: ""

            val item = HistoryItem(
                id = stringId,
                numericId = playlist.id,
                title = finalTitle,
                subtitle = finalSubtitle,
                imageUrl = resolvedImageUrl,
                type = type,
                isVerified = playlist.user?.verified == true
            )
            database.downloadDao().insertHistory(item)
        }
    }

    fun removeFromHistory(playlistId: Long) {
        scope.launch {
            database.downloadDao().deleteHistoryItem("playlist:$playlistId")
            database.downloadDao().deleteHistoryItem("station:$playlistId")
        }
    }

    suspend fun insertHistoryList(items: List<HistoryItem>) {
        database.downloadDao().insertHistoryList(items)
    }

    fun getHistory() = database.downloadDao().getHistory().map { items ->
        items.map { item ->
            if (item.type == "PLAYLIST" && item.numericId < 0) {
                val local = database.downloadDao().getPlaylist(item.numericId)
                if (local?.localCoverPath?.isNotEmpty() == true) {
                    item.copy(imageUrl = local.localCoverPath)
                } else if (item.imageUrl.contains("picsum.photos")) {
                    item.copy(imageUrl = local?.artworkUrl?.takeIf { !it.contains("picsum.photos") } ?: "")
                } else {
                    item
                }
            } else if (item.imageUrl.contains("picsum.photos")) {
                item.copy(imageUrl = "")
            } else {
                item
            }
        }
    }

    fun clearAllHistory() {
        scope.launch {
            database.downloadDao().clearHistory()
        }
    }

    fun clearTracksHistory() {
        scope.launch {
            database.downloadDao().clearTracksHistory()
        }
    }

    fun clearContextsHistory() {
        scope.launch {
            database.downloadDao().clearContextsHistory()
        }
    }
}

