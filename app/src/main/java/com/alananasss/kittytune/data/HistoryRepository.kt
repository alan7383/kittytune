package com.alananasss.kittytune.data

import android.content.Context
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.HistoryRoute
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object HistoryRepository {
    private lateinit var database: AppDatabase
    private lateinit var appContext: Context
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init(context: Context) {
        appContext = context.applicationContext
        database = AppDatabase.getDatabase(context)
        scope.launch {
            database.downloadDao().deleteHistoryItem("playlist:0")
            database.downloadDao().deleteHistoryItem("playlist:history")
            database.downloadDao().deleteHistoryItem("history")
            database.downloadDao().deleteMixHistory()
            database.downloadDao().deleteWaveHistory()
            repairTrailerHistoryRows()
        }
    }

    /**
     * Repairs the rows the trailer flow used to write.
     *
     * A trailer was written down as a page of its own, under an id that was a hash of
     * its route, while the artist it belongs to was written under its real id as well:
     * the artist showed up twice in "Recently played", and the copy with the hashed id
     * named no SoundCloud user, so tapping it fell through to the Spotify lookup and
     * opened an unrelated artist.
     *
     * The row is rewritten under the route it came from, which takes the place of the
     * row that was already there, and the Spotify mapping that was derived from the
     * hashed id is dropped: it named an artist nobody asked for.
     */
    private suspend fun repairTrailerHistoryRows() {
        val rows = try {
            database.downloadDao().getTrailerRoutedHistoryItems()
        } catch (_: Exception) {
            emptyList()
        }
        if (rows.isEmpty()) return

        val prefs = PlayerPreferences(appContext)
        for (row in rows) {
            val base = HistoryRoute.unwrap(row.originalUrl.orEmpty())
            val profileId = HistoryRoute.soundCloudProfileId(base)
            val repairedNumericId = profileId?.substringAfter(":")?.toLongOrNull()

            // The row under the hashed id goes either way: it names an artist nobody
            // visited, and the one under the real id is the one that is kept.
            database.downloadDao().deleteHistoryItem(row.id)

            if (profileId != null && repairedNumericId != null) {
                database.downloadDao().insertHistory(
                    row.copy(id = profileId, numericId = repairedNumericId, originalUrl = base)
                )
            }

            // The mapping that was derived from the hashed id points at whatever artist
            // a name search returned: it must not outlive the row it was built from.
            if (row.numericId != 0L && row.numericId != repairedNumericId) {
                prefs.removeSpotifyArtistMapping(row.numericId)
            }
        }
    }

    fun addToHistory(track: Track) {
        try {
            PlayerPreferences(appContext).incrementTracksPlayedCount()
        } catch (_: Exception) {}
        scope.launch {
            val safeSource = (track.source as? String) ?: "soundcloud"

            val item = HistoryItem(
                id = "track:${track.id}",
                numericId = track.id,
                title = track.title ?: appContext.getString(R.string.history_untitled_track),
                subtitle = track.displayArtist.ifBlank { track.user?.username.orEmpty() }.ifBlank { appContext.getString(R.string.history_unknown_artist) },
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
        if ((playlist.id == 0L && playlist.permalinkUrl.isNullOrBlank() && playlist.urn.isNullOrBlank()) ||
            playlist.title.equals("history", ignoreCase = true) ||
            playlist.permalinkUrl == "history" ||
            playlist.permalinkUrl == "your_mix" ||
            playlist.urn == "your_mix" ||
            playlist.permalinkUrl?.contains("your_mix") == true ||
            playlist.urn?.contains("your_mix") == true ||
            playlist.title.equals("MyWave", ignoreCase = true) ||
            playlist.title?.contains("MyWave", ignoreCase = true) == true ||
            playlist.permalinkUrl == "my_wave" ||
            playlist.urn == "my_wave" ||
            playlist.permalinkUrl?.contains("my_wave") == true ||
            playlist.urn?.contains("my_wave") == true ||
            playlist.permalinkUrl?.contains("wave") == true ||
            playlist.urn?.contains("wave") == true
        ) {
            return
        }
        scope.launch {
            // A trailer is the page it was started from: the row has to name that page,
            // or it names nobody.
            val rawNav = HistoryRoute.unwrap(playlist.permalinkUrl ?: playlist.urn ?: "")
            val isYoutubeRadio = rawNav.startsWith("yt_radio:")
            val isSpotifyArtist = isProfile && (rawNav.contains("spotify") || rawNav.startsWith("spotify_artist:"))
            val isSpotifyRadio = isStation && (rawNav.contains("spotify") || rawNav.startsWith("spotify_radio:"))
            val isSpotifyItem = rawNav.contains("spotify") || rawNav.startsWith("spotify_")
            val isSystemPlaylist = rawNav.startsWith("system_playlist:") ||
                    rawNav.startsWith("soundcloud:system-playlists:") ||
                    playlist.urn?.startsWith("soundcloud:system-playlists:") == true ||
                    playlist.permalinkUrl?.contains("system-playlists") == true ||
                    playlist.permalinkUrl?.contains("your-playback") == true

            val systemPlaylistUrn = when {
                playlist.urn?.startsWith("soundcloud:system-playlists:") == true -> playlist.urn
                rawNav.startsWith("system_playlist:") -> rawNav.removePrefix("system_playlist:")
                rawNav.startsWith("soundcloud:system-playlists:") -> rawNav
                else -> playlist.urn ?: rawNav
            }
            val systemPlaylistId = "system_playlist:$systemPlaylistUrn"

            val (stringId, type) = when {
                isSpotifyArtist -> {
                    "spotify_artist:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(rawNav)}" to "PROFILE"
                }
                isSpotifyRadio -> {
                    "spotify_radio:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(rawNav)}" to "STATION"
                }
                isSpotifyItem -> {
                    val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(rawNav)
                    if (rawNav.contains("album")) "spotify:album:$clean" to "PLAYLIST"
                    else "spotify:playlist:$clean" to "PLAYLIST"
                }
                isSystemPlaylist -> systemPlaylistId to "PLAYLIST"
                isProfile -> {
                    // The route carries the SoundCloud user id; the id the caller passed
                    // may be a hash of a route that could not be parsed, and a row that
                    // names nobody opens whatever artist a name search returns.
                    val profileId = HistoryRoute.soundCloudProfileId(rawNav)
                    (profileId ?: "profile:${playlist.id}") to "PROFILE"
                }
                isYoutubeRadio -> rawNav to "STATION"
                isStation -> "station:${playlist.id}" to "STATION"
                playlist.id == -1L -> "likes" to "PLAYLIST"
                playlist.id == -2L -> "downloads" to "PLAYLIST"
                playlist.id < 0 -> "playlist:${playlist.id}" to "PLAYLIST"
                else -> "playlist:${playlist.id}" to "PLAYLIST"
            }

            val finalSubtitle = when {
                isProfile -> appContext.getString(R.string.history_type_artist)
                isYoutubeRadio -> "YouTube"
                isSpotifyRadio || isSpotifyItem -> "Spotify"
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
                numericId = HistoryRoute.numericId(rawNav)
                    ?: if (isSystemPlaylist && playlist.id == 0L) kotlin.math.abs(systemPlaylistUrn.hashCode().toLong()) else playlist.id,
                title = finalTitle,
                subtitle = finalSubtitle,
                imageUrl = resolvedImageUrl,
                type = type,
                isVerified = playlist.user?.verified == true,
                originalUrl = if (isSystemPlaylist) systemPlaylistId else rawNav.ifBlank { null }
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
        items.filterNot { item ->
            item.originalUrl == "your_mix" ||
            item.originalUrl?.contains("your_mix") == true ||
            item.id == "your_mix" ||
            item.id.contains("your_mix") ||
            item.title.equals("your mix", ignoreCase = true) ||
            item.title.equals("ton mix", ignoreCase = true) ||
            item.title.equals("dein mix", ignoreCase = true) ||
            item.title.equals("твой микс", ignoreCase = true) ||
            item.originalUrl == "my_wave" ||
            item.originalUrl?.contains("my_wave") == true ||
            item.originalUrl?.contains("wave") == true ||
            item.id == "my_wave" ||
            item.id.contains("my_wave") ||
            item.id.contains("wave") ||
            item.title.equals("mywave", ignoreCase = true) ||
            item.title.contains("mywave", ignoreCase = true) ||
            item.title.equals("my wave", ignoreCase = true)
        }.map { item ->
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

