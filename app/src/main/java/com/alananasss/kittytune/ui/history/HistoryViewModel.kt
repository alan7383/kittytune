package com.alananasss.kittytune.ui.history

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.HistoryRepository
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application
    private val tokenManager = TokenManager(application)

    var selectedTab by mutableStateOf(HistoryTab.TRACKS)
    var searchQuery by mutableStateOf("")

    val tracksHistory = mutableStateListOf<HistoryTrackItem>()
    val contextsHistory = mutableStateListOf<HistoryContextItem>()

    var isLoadingTracks by mutableStateOf(false)
    var isLoadingContexts by mutableStateOf(false)
    var isLoadingMoreTracks by mutableStateOf(false)
    var isLoadingMoreContexts by mutableStateOf(false)
    var canLoadMoreTracks by mutableStateOf(true)
    var canLoadMoreContexts by mutableStateOf(true)

    private var tracksNextUrl: String? = null
    private var contextsNextUrl: String? = null

    var isRefreshing by mutableStateOf(false)
    var isClearing by mutableStateOf(false)
    var isGuest by mutableStateOf(tokenManager.isGuestMode())
    var errorMessage by mutableStateOf<String?>(null)

    val displayedTracks: List<HistoryTrackItem>
        get() {
            if (searchQuery.isBlank()) return tracksHistory
            return tracksHistory.filter { item ->
                (item.track.title?.contains(searchQuery, ignoreCase = true) == true) ||
                        (item.track.user?.username?.contains(searchQuery, ignoreCase = true) == true)
            }
        }

    val displayedContexts: List<HistoryContextItem>
        get() {
            if (searchQuery.isBlank()) return contextsHistory
            return contextsHistory.filter { item ->
                item.title.contains(searchQuery, ignoreCase = true) ||
                        item.subtitle.contains(searchQuery, ignoreCase = true)
            }
        }

    init {
        loadData()
    }

    fun loadData(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            isGuest = tokenManager.isGuestMode()
            if (forceRefresh) isRefreshing = true
            try {
                loadTracksHistory(forceRefresh)
                loadContextsHistory(forceRefresh)
            } finally {
                isRefreshing = false
            }
        }
    }

    suspend fun loadTracksHistory(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (tracksHistory.isNotEmpty() && !forceRefresh) return@withContext
        isLoadingTracks = true
        errorMessage = null
        tracksNextUrl = null
        canLoadMoreTracks = true
        try {
            val localItems = try {
                HistoryRepository.getHistory().first().filter { it.type == "TRACK" }
            } catch (e: Exception) {
                emptyList()
            }
            val localMapped = localItems.map { historyItem ->
                val artwork = historyItem.imageUrl.takeIf { it.isNotBlank() && !it.contains("picsum.photos") }
                val track = Track(
                    id = historyItem.numericId,
                    title = historyItem.title,
                    artworkUrl = artwork,
                    durationMs = 0L,
                    user = User(0L, historyItem.subtitle, null, verified = historyItem.isVerified),
                    source = historyItem.source,
                    permalinkUrl = historyItem.originalUrl
                )
                HistoryTrackItem(track = track, playedAt = historyItem.timestamp)
            }.distinctBy { it.track.id }.sortedByDescending { it.playedAt }

            if (localMapped.isNotEmpty() && tracksHistory.isEmpty()) {
                withContext(Dispatchers.Main) {
                    tracksHistory.clear()
                    tracksHistory.addAll(localMapped)
                }
            }

            if (!isGuest) {
                val api = RetrofitClient.create(app)
                val response = api.getPlayHistory(limit = 100)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    tracksNextUrl = body.nextUrl
                    canLoadMoreTracks = (tracksNextUrl != null)

                    val entries = body.collection
                    val trackIds = entries.mapNotNull { entry ->
                        val idPart = entry.urn.substringAfterLast(":")
                        idPart.toLongOrNull()
                    }.distinct()

                    if (trackIds.isNotEmpty()) {
                        val chunkedIds = trackIds.chunked(50)
                        val fetchedTracksMap = mutableMapOf<Long, Track>()
                        for (chunk in chunkedIds) {
                            try {
                                val tracks = api.getTracksByIds(chunk.joinToString(","))
                                tracks.forEach { fetchedTracksMap[it.id] = it }
                            } catch (e: Exception) {
                                Log.e("HistoryViewModel", "Failed to fetch tracks chunk", e)
                            }
                        }

                        val serverList = mutableListOf<HistoryTrackItem>()
                        val dbItemsToCache = mutableListOf<HistoryItem>()
                        for (entry in entries) {
                            val id = entry.urn.substringAfterLast(":").toLongOrNull() ?: continue
                            val track = fetchedTracksMap[id] ?: continue
                            serverList.add(HistoryTrackItem(track = track, playedAt = entry.playedAt))

                            val effectiveArtwork = track.artworkUrl?.takeIf { it.isNotBlank() }
                                ?: track.fullResArtwork.takeIf { it.isNotBlank() && !it.contains("picsum.photos") }
                                ?: track.user?.avatarUrl?.takeIf { it.isNotBlank() }
                                ?: ""

                            dbItemsToCache.add(
                                HistoryItem(
                                    id = "track:${track.id}",
                                    numericId = track.id,
                                    title = track.title ?: app.getString(R.string.history_untitled_track),
                                    subtitle = track.user?.username ?: app.getString(R.string.history_unknown_artist),
                                    imageUrl = effectiveArtwork,
                                    type = "TRACK",
                                    isVerified = track.user?.verified == true,
                                    source = (track.source as? String) ?: "soundcloud",
                                    originalUrl = track.permalinkUrl,
                                    timestamp = entry.playedAt
                                )
                            )
                        }

                        try {
                            HistoryRepository.insertHistoryList(dbItemsToCache)
                        } catch (e: Exception) {
                            Log.e("HistoryViewModel", "Failed to cache history in DB", e)
                        }

                        val serverMap = serverList.associateBy { it.track.id }
                        val localMap = localMapped.associateBy { it.track.id }
                        val allTrackIds = (serverList.map { it.track.id } + localMapped.map { it.track.id }).distinct()

                        val combined = allTrackIds.mapNotNull { id ->
                            val serverItem = serverMap[id]
                            val localItem = localMap[id]
                            if (serverItem != null && localItem != null) {
                                val latestPlayedAt = maxOf(serverItem.playedAt, localItem.playedAt)
                                val isVerified =
                                    serverItem.track.user?.verified == true || localItem.track.user?.verified == true
                                val bestUser =
                                    (serverItem.track.user ?: localItem.track.user)?.copy(verified = isVerified)
                                        ?: User(0L, localItem.track.user?.username ?: "", null, verified = isVerified)
                                val bestArtwork =
                                    if (!serverItem.track.artworkUrl.isNullOrBlank()) serverItem.track.artworkUrl else localItem.track.artworkUrl
                                val baseTrack =
                                    if (!serverItem.track.artworkUrl.isNullOrBlank()) serverItem.track else localItem.track
                                val bestTrack = baseTrack.copy(user = bestUser, artworkUrl = bestArtwork)
                                HistoryTrackItem(track = bestTrack, playedAt = latestPlayedAt)
                            } else {
                                serverItem ?: localItem
                            }
                        }.sortedByDescending { it.playedAt }

                        withContext(Dispatchers.Main) {
                            tracksHistory.clear()
                            tracksHistory.addAll(combined)
                        }
                    } else if (localMapped.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            tracksHistory.clear()
                            canLoadMoreTracks = false
                        }
                    }
                } else {
                    Log.w("HistoryViewModel", "getPlayHistory returned code ${response.code()}")
                    canLoadMoreTracks = false
                }
            } else {
                canLoadMoreTracks = false
            }
        } catch (e: Exception) {
            Log.e("HistoryViewModel", "Error loading tracks history", e)
            errorMessage = e.message
        } finally {
            isLoadingTracks = false
        }
    }

    fun loadMoreTracks() {
        val nextUrl = tracksNextUrl
        if (isLoadingTracks || isLoadingMoreTracks || !canLoadMoreTracks || nextUrl == null || isGuest) return
        viewModelScope.launch(Dispatchers.IO) {
            isLoadingMoreTracks = true
            try {
                val api = RetrofitClient.create(app)
                val response = api.getPlayHistoryNext(nextUrl)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    tracksNextUrl = body.nextUrl
                    canLoadMoreTracks = (tracksNextUrl != null)

                    val entries = body.collection
                    if (entries.isEmpty()) {
                        canLoadMoreTracks = false
                        return@launch
                    }

                    val trackIds = entries.mapNotNull { entry ->
                        entry.urn.substringAfterLast(":").toLongOrNull()
                    }.distinct()

                    if (trackIds.isNotEmpty()) {
                        val chunkedIds = trackIds.chunked(50)
                        val fetchedTracksMap = mutableMapOf<Long, Track>()
                        for (chunk in chunkedIds) {
                            try {
                                val tracks = api.getTracksByIds(chunk.joinToString(","))
                                tracks.forEach { fetchedTracksMap[it.id] = it }
                            } catch (e: Exception) {
                                Log.e("HistoryViewModel", "Failed to fetch tracks chunk", e)
                            }
                        }

                        val resultList = mutableListOf<HistoryTrackItem>()
                        val dbItemsToCache = mutableListOf<HistoryItem>()
                        for (entry in entries) {
                            val id = entry.urn.substringAfterLast(":").toLongOrNull() ?: continue
                            val track = fetchedTracksMap[id] ?: continue
                            resultList.add(HistoryTrackItem(track = track, playedAt = entry.playedAt))

                            dbItemsToCache.add(
                                HistoryItem(
                                    id = "track:${track.id}",
                                    numericId = track.id,
                                    title = track.title ?: app.getString(R.string.history_untitled_track),
                                    subtitle = track.user?.username ?: app.getString(R.string.history_unknown_artist),
                                    imageUrl = track.fullResArtwork ?: "",
                                    type = "TRACK",
                                    isVerified = track.user?.verified == true,
                                    source = (track.source as? String) ?: "soundcloud",
                                    originalUrl = track.permalinkUrl,
                                    timestamp = entry.playedAt
                                )
                            )
                        }

                        try {
                            HistoryRepository.insertHistoryList(dbItemsToCache)
                        } catch (_: Exception) {
                        }

                        val existingTrackIds = tracksHistory.map { it.track.id }.toSet()
                        val newUnique =
                            resultList.distinctBy { it.track.id }.filter { it.track.id !in existingTrackIds }

                        if (newUnique.isEmpty()) {
                            canLoadMoreTracks = false
                        } else {
                            withContext(Dispatchers.Main) {
                                tracksHistory.addAll(newUnique)
                            }
                        }
                    } else {
                        canLoadMoreTracks = false
                    }
                } else {
                    canLoadMoreTracks = false
                }
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error loading more tracks", e)
                canLoadMoreTracks = false
            } finally {
                isLoadingMoreTracks = false
            }
        }
    }

    suspend fun loadContextsHistory(forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (contextsHistory.isNotEmpty() && !forceRefresh) return@withContext
        isLoadingContexts = true
        contextsNextUrl = null
        canLoadMoreContexts = true
        try {
            val localItems = try {
                HistoryRepository.getHistory().first().filter {
                    it.type != "TRACK" && it.id != "playlist:0" && !it.title.equals("history", ignoreCase = true)
                }
            } catch (e: Exception) {
                emptyList()
            }
            val localMapped = localItems.map { historyItem ->
                val type = when (historyItem.type) {
                    "STATION" -> HistoryContextType.ARTIST_STATION
                    "PROFILE" -> HistoryContextType.ARTIST
                    else -> HistoryContextType.PLAYLIST
                }
                val targetNavId = when {
                    historyItem.id == "likes" -> "likes"
                    historyItem.id == "downloads" -> "downloads"
                    historyItem.id.startsWith("yt_radio:") -> historyItem.id
                    historyItem.id.startsWith("spotify_artist:") -> historyItem.id
                    historyItem.id.startsWith("spotify_radio:") -> historyItem.id
                    historyItem.id.startsWith("spotify:artist:") -> "spotify_artist:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(historyItem.id)}"
                    historyItem.id.startsWith("spotify:") || historyItem.id.startsWith("spotify_") -> historyItem.id
                    historyItem.type == "PROFILE" && (historyItem.id.contains("spotify") || historyItem.numericId == 0L) -> {
                        val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(historyItem.id)
                        if (clean.isNotBlank()) "spotify_artist:$clean" else "profile:${historyItem.numericId}"
                    }
                    historyItem.type == "STATION" && historyItem.id.contains("spotify") -> "spotify_radio:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(historyItem.id)}"
                    historyItem.type == "STATION" -> "station:${historyItem.numericId}"
                    historyItem.type == "PROFILE" -> "profile:${historyItem.numericId}"
                    else -> historyItem.numericId.toString()
                }
                HistoryContextItem(
                    id = historyItem.id,
                    urn = historyItem.id,
                    title = historyItem.title,
                    subtitle = historyItem.subtitle,
                    imageUrl = historyItem.imageUrl,
                    type = type,
                    playedAt = historyItem.timestamp,
                    targetNavId = targetNavId,
                    isVerified = historyItem.isVerified
                )
            }.distinctBy { it.id }.sortedByDescending { it.playedAt }

            if (localMapped.isNotEmpty() && contextsHistory.isEmpty()) {
                withContext(Dispatchers.Main) {
                    contextsHistory.clear()
                    contextsHistory.addAll(localMapped)
                }
            }

            if (!isGuest) {
                val api = RetrofitClient.create(app)
                val response = api.getRecentlyPlayedContexts(limit = 100)
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    contextsNextUrl = body.nextUrl
                    canLoadMoreContexts = (contextsNextUrl != null)

                    val entries = body.collection
                    val deferredList = entries.map { entry ->
                        async {
                            resolveContextItem(api, entry.urn, entry.playedAt)
                        }
                    }
                    val resolved = deferredList.awaitAll().filterNotNull()

                    val dbContextsToCache = resolved.map { ctx ->
                        HistoryItem(
                            id = ctx.id,
                            numericId = ctx.targetNavId.substringAfter(":").toLongOrNull() ?: 0L,
                            title = ctx.title,
                            subtitle = ctx.subtitle,
                            imageUrl = ctx.imageUrl ?: "",
                            type = when (ctx.type) {
                                HistoryContextType.ARTIST_STATION, HistoryContextType.TRACK_STATION -> "STATION"
                                HistoryContextType.ARTIST -> "PROFILE"
                                else -> "PLAYLIST"
                            },
                            isVerified = ctx.isVerified,
                            timestamp = ctx.playedAt
                        )
                    }
                    try {
                        HistoryRepository.insertHistoryList(dbContextsToCache)
                    } catch (_: Exception) {
                    }

                    val combined = (resolved + localMapped)
                        .distinctBy { it.id }
                        .sortedByDescending { it.playedAt }

                    withContext(Dispatchers.Main) {
                        contextsHistory.clear()
                        contextsHistory.addAll(combined)
                    }
                } else {
                    Log.w("HistoryViewModel", "getRecentlyPlayedContexts returned code ${response.code()}")
                    canLoadMoreContexts = false
                }
            } else {
                canLoadMoreContexts = false
            }
        } catch (e: Exception) {
            Log.e("HistoryViewModel", "Error loading contexts history", e)
        } finally {
            isLoadingContexts = false
        }
    }

    fun loadMoreContexts() {
        val nextUrl = contextsNextUrl
        if (isLoadingContexts || isLoadingMoreContexts || !canLoadMoreContexts || nextUrl == null || isGuest) return
        viewModelScope.launch(Dispatchers.IO) {
            isLoadingMoreContexts = true
            try {
                val api = RetrofitClient.create(app)
                val response = api.getRecentlyPlayedContextsNext(nextUrl)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    contextsNextUrl = body.nextUrl
                    canLoadMoreContexts = (contextsNextUrl != null)

                    val entries = body.collection
                    if (entries.isEmpty()) {
                        canLoadMoreContexts = false
                        return@launch
                    }

                    val deferredList = entries.map { entry ->
                        async {
                            resolveContextItem(api, entry.urn, entry.playedAt)
                        }
                    }
                    val resolved = deferredList.awaitAll().filterNotNull()

                    val existingContextIds = contextsHistory.map { it.id }.toSet()
                    val newUnique = resolved.distinctBy { it.id }.filter { it.id !in existingContextIds }

                    if (newUnique.isEmpty()) {
                        canLoadMoreContexts = false
                    } else {
                        withContext(Dispatchers.Main) {
                            contextsHistory.addAll(newUnique)
                        }
                    }
                } else {
                    canLoadMoreContexts = false
                }
            } catch (e: Exception) {
                Log.e("HistoryViewModel", "Error loading more contexts", e)
                canLoadMoreContexts = false
            } finally {
                isLoadingMoreContexts = false
            }
        }
    }

    private suspend fun resolveContextItem(
        api: com.alananasss.kittytune.data.network.SoundCloudApi,
        urn: String,
        playedAt: Long
    ): HistoryContextItem? {
        return try {
            when {
                urn.startsWith("soundcloud:playlists:") -> {
                    val id = urn.removePrefix("soundcloud:playlists:").toLongOrNull() ?: return null
                    val playlist = try {
                        api.getPlaylist(id)
                    } catch (_: Exception) {
                        null
                    }
                    HistoryContextItem(
                        id = "playlist:$id",
                        urn = urn,
                        title = playlist?.title ?: app.getString(R.string.history_type_playlist),
                        subtitle = playlist?.user?.username ?: app.getString(R.string.history_source_soundcloud),
                        imageUrl = playlist?.fullResArtwork,
                        type = if (playlist?.isRealAlbum == true) HistoryContextType.ALBUM else HistoryContextType.PLAYLIST,
                        playedAt = playedAt,
                        targetNavId = id.toString(),
                        isVerified = playlist?.user?.verified == true
                    )
                }

                urn.startsWith("soundcloud:system-playlists:artist-stations:") -> {
                    val id =
                        urn.removePrefix("soundcloud:system-playlists:artist-stations:").toLongOrNull() ?: return null
                    val user = try {
                        api.getUser(id)
                    } catch (_: Exception) {
                        null
                    }
                    val artistName = user?.username ?: app.getString(R.string.history_type_artist)
                    HistoryContextItem(
                        id = "station_artist:$id",
                        urn = urn,
                        title = "$artistName Radio",
                        subtitle = app.getString(R.string.history_type_station),
                        imageUrl = user?.avatarUrl,
                        type = HistoryContextType.ARTIST_STATION,
                        playedAt = playedAt,
                        targetNavId = "station_artist:$id",
                        isVerified = user?.verified == true
                    )
                }

                urn.startsWith("soundcloud:system-playlists:track-stations:") -> {
                    val id =
                        urn.removePrefix("soundcloud:system-playlists:track-stations:").toLongOrNull() ?: return null
                    val track = try {
                        api.getTracksByIds(id.toString()).firstOrNull()
                    } catch (_: Exception) {
                        null
                    }
                    val trackTitle = track?.title ?: app.getString(R.string.history_type_station)
                    HistoryContextItem(
                        id = "station:$id",
                        urn = urn,
                        title = "$trackTitle Radio",
                        subtitle = track?.user?.username ?: app.getString(R.string.history_type_station),
                        imageUrl = track?.fullResArtwork,
                        type = HistoryContextType.TRACK_STATION,
                        playedAt = playedAt,
                        targetNavId = "station:$id",
                        isVerified = track?.user?.verified == true
                    )
                }

                urn.startsWith("soundcloud:users:") -> {
                    val id = urn.removePrefix("soundcloud:users:").toLongOrNull() ?: return null
                    val user = try {
                        api.getUser(id)
                    } catch (_: Exception) {
                        null
                    }
                    HistoryContextItem(
                        id = "profile:$id",
                        urn = urn,
                        title = user?.username ?: app.getString(R.string.history_type_artist),
                        subtitle = app.getString(R.string.history_type_artist),
                        imageUrl = user?.avatarUrl,
                        type = HistoryContextType.ARTIST,
                        playedAt = playedAt,
                        targetNavId = "profile:$id",
                        isVerified = user?.verified == true
                    )
                }

                urn.startsWith("soundcloud:liked-tracks:") -> {
                    HistoryContextItem(
                        id = "likes",
                        urn = urn,
                        title = app.getString(R.string.history_type_likes),
                        subtitle = app.getString(R.string.history_source_library),
                        imageUrl = null,
                        type = HistoryContextType.LIKES,
                        playedAt = playedAt,
                        targetNavId = "likes"
                    )
                }

                else -> null
            }
        } catch (e: Exception) {
            Log.e("HistoryViewModel", "Failed to resolve context urn $urn", e)
            null
        }
    }

    fun clearHistoryForCurrentTab() {
        viewModelScope.launch {
            isClearing = true
            try {
                if (selectedTab == HistoryTab.TRACKS) {
                    if (!isGuest) {
                        try {
                            val api = RetrofitClient.create(app)
                            api.clearPlayHistory()
                        } catch (e: Exception) {
                            Log.e("HistoryViewModel", "Failed to clear play history on server", e)
                        }
                    }
                    HistoryRepository.clearTracksHistory()
                    tracksHistory.clear()
                } else {
                    if (!isGuest) {
                        try {
                            val api = RetrofitClient.create(app)
                            api.clearRecentlyPlayedContexts()
                        } catch (e: Exception) {
                            Log.e("HistoryViewModel", "Failed to clear recently played contexts on server", e)
                        }
                    }
                    HistoryRepository.clearContextsHistory()
                    contextsHistory.clear()
                }
            } finally {
                isClearing = false
            }
        }
    }
}
