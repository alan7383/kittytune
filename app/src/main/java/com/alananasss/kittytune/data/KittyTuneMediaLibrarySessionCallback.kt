    /*
     * Fichier : app/src/main/java/com/alananasss/kittytune/data/KittyTuneMediaLibrarySessionCallback.kt
     */
    package com.alananasss.kittytune.data
    
    import android.content.Context
    import android.net.Uri
    import android.os.Bundle
    import androidx.media3.common.MediaItem
    import androidx.media3.common.MediaMetadata
    import androidx.media3.common.C
    import androidx.media3.common.MimeTypes
    import androidx.media3.session.LibraryResult
    import androidx.media3.session.MediaLibraryService
    import androidx.media3.session.MediaSession
    import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
    import androidx.media3.session.SessionCommand
    import androidx.media3.session.SessionResult
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.network.SoundCloudApi
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.alananasss.kittytune.data.GenreData
    import com.alananasss.kittytune.data.SearchCategory
    import com.alananasss.kittytune.ui.player.PlaybackContext
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.google.common.collect.ImmutableList
    import com.google.common.util.concurrent.Futures
    import com.google.common.util.concurrent.ListenableFuture
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.async
    import kotlinx.coroutines.awaitAll
    import kotlinx.coroutines.guava.future
    import kotlinx.coroutines.launch
    import kotlin.math.min
    import kotlinx.coroutines.coroutineScope
    import android.content.Intent
    import kotlinx.coroutines.withContext
    import kotlinx.coroutines.flow.first
    import kotlinx.coroutines.CoroutineScope
    
    class KittyTuneMediaLibrarySessionCallback(
        private val context: Context,
        private val likeRepository: LikeRepository,
        private val api: SoundCloudApi,
        private val serviceScope: CoroutineScope,
        private val onControllerConnected: () -> Unit = {}
    ) : MediaLibraryService.MediaLibrarySession.Callback {
    
        companion object {
            const val ROOT_ID = "kittytune_root"
            const val LIBRARY_ROOT_ID = "kittytune_library_root"
            const val LIKES_ID = "kittytune_likes"
            const val DOWNLOADS_ID = "kittytune_downloads"
            const val LOCAL_FILES_ID = "kittytune_local_files"
            const val RECOMMENDATIONS_ID = "kittytune_recommendations"
            const val GENRES_ID = "kittytune_genres"
            const val GENRE_PREFIX_ID = "genre_"
            const val PLAYLIST_PREFIX_ID = "playlist_"
            const val TRACK_PREFIX = "track:"
            const val CONTEXT_SEPARATOR = ":context:"
            const val API_BATCH_LIMIT = 50
        }
    
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val connectionResult = super.onConnect(session, controller)
            val availableSessionCommands = connectionResult.availableSessionCommands
                .buildUpon()
                .add(SessionCommand(PlaybackService.CUSTOM_ACTION_LIKE, Bundle.EMPTY))
                .add(SessionCommand(PlaybackService.CUSTOM_ACTION_REPEAT, Bundle.EMPTY))
                .build()
    
            return MediaSession.ConnectionResult.accept(
                availableSessionCommands,
                connectionResult.availablePlayerCommands
            )
        }
    
        override fun onPostConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ) {
            super.onPostConnect(session, controller)
            onControllerConnected()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (customCommand.customAction == PlaybackService.CUSTOM_ACTION_LIKE) {
                val currentTrack = MusicManager.currentTrack
                if (currentTrack != null) {
                    if (likeRepository.isTrackLiked(currentTrack.id)) {
                        likeRepository.removeLike(currentTrack.id)
                    } else {
                        likeRepository.addLike(currentTrack)
                    }
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (customCommand.customAction == PlaybackService.CUSTOM_ACTION_REPEAT) {
                val player = MusicManager.player
                val nextMode = when (player.repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                    else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                }
                player.repeatMode = nextMode
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            return super.onCustomCommand(session, controller, customCommand, args)
        }
    
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaItemsWithStartPosition> {
            return serviceScope.future {
                val item = mediaItems.firstOrNull()
                if (item == null) {
                    return@future MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
                }
    
                val id = item.mediaId
                val defaultResult = MediaItemsWithStartPosition(emptyList(), startIndex, startPositionMs)
    
                try {
                    val queueData: Triple<List<MediaItem>, Int, Long> = when {
                        id.contains(CONTEXT_SEPARATOR) -> {
                            val parts = id.split(CONTEXT_SEPARATOR)
                            val trackPart = parts[0].removePrefix(TRACK_PREFIX)
                            val contextId = parts.getOrNull(1)
    
                            val trackId = trackPart.toLongOrNull()
                            val playlistId = contextId?.toLongOrNull()
    
                            if (trackId != null && playlistId != null) {
                                val tracks: List<Track>
    
                                if (playlistId == -1L) {
                                    tracks = likeRepository.likedTracks.value
                                    val ctx = PlaybackContext(
                                        displayText = context.getString(R.string.lib_liked_tracks),
                                        navigationId = "likes",
                                        imageUrl = null,
                                        artistName = null
                                    )
                                    MusicManager.updateContext(ctx)
                                } else {
                                    val playlist = api.getPlaylist(playlistId)
                                    val rawTracks = playlist.tracks ?: emptyList()
                                    tracks = hydrateTracksSafe(rawTracks)
    
                                    val ctx = PlaybackContext(
                                        displayText = context.getString(R.string.context_playlist, playlist.title ?: "Playlist"),
                                        navigationId = "playlist_detail:${playlist.id}",
                                        imageUrl = playlist.fullResArtwork,
                                        artistName = playlist.user?.username,
                                        isVerified = playlist.user?.verified == true
                                    )
                                    MusicManager.updateContext(ctx)
                                }
    
                                val index = tracks.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
    
                                val startTrack = tracks[index]
                                val resolvedUrl = StreamResolver.resolveStream(context, startTrack)
    
                                val mediaList = tracks.mapIndexed { i, track ->
                                    if (i == index && resolvedUrl != null) {
                                        trackToMediaItem(track, playlistId, resolvedUrl)
                                    } else {
                                        trackToMediaItem(track, playlistId)
                                    }
                                }
    
                                Triple(mediaList, index, C.TIME_UNSET)
                            } else {
                                Triple(emptyList(), 0, C.TIME_UNSET)
                            }
                        }
    
                        id.startsWith(PLAYLIST_PREFIX_ID) -> {
                            val playlistId = id.removePrefix(PLAYLIST_PREFIX_ID).toLongOrNull()
                            if (playlistId != null) {
                                val playlist = api.getPlaylist(playlistId)
                                val ctx = PlaybackContext(
                                    displayText = context.getString(R.string.context_playlist, playlist.title ?: "Playlist"),
                                    navigationId = "playlist_detail:${playlist.id}",
                                    imageUrl = playlist.fullResArtwork,
                                    artistName = playlist.user?.username,
                                    isVerified = playlist.user?.verified == true
                                )
                                MusicManager.updateContext(ctx)
    
                                val rawTracks = playlist.tracks ?: emptyList()
                                val hydrated = hydrateTracksSafe(rawTracks)
    
                                val resolvedUrl = if (hydrated.isNotEmpty()) StreamResolver.resolveStream(context, hydrated[0]) else null
    
                                val items = hydrated.mapIndexed { i, track ->
                                    if (i == 0 && resolvedUrl != null) {
                                        trackToMediaItem(track, playlistId, resolvedUrl)
                                    } else {
                                        trackToMediaItem(track, playlistId)
                                    }
                                }
                                Triple(items, 0, C.TIME_UNSET)
                            } else {
                                Triple(emptyList(), 0, C.TIME_UNSET)
                            }
                        }
    
                        id == LIKES_ID -> {
                            val liked = likeRepository.likedTracks.value
                            val ctx = PlaybackContext(
                                displayText = context.getString(R.string.lib_liked_tracks),
                                navigationId = "likes",
                                imageUrl = null,
                                artistName = null
                            )
                            MusicManager.updateContext(ctx)
    
                            val resolvedUrl = if (liked.isNotEmpty()) StreamResolver.resolveStream(context, liked[0]) else null
    
                            val items = liked.mapIndexed { i, track ->
                                if (i == 0 && resolvedUrl != null) {
                                    trackToMediaItem(track, -1L, resolvedUrl)
                                } else {
                                    trackToMediaItem(track, -1L)
                                }
                            }
                            Triple(items, 0, C.TIME_UNSET)
                        }
    
                        id == RECOMMENDATIONS_ID -> {
                            val response = api.searchTracks("trending", limit = 50)
                            val items = response.collection.map { trackToMediaItem(it, null) }
                            Triple(items, 0, C.TIME_UNSET)
                        }
    
                        else -> {
                            val trackId = id.toLongOrNull()
                            if (trackId != null) {
                                MusicManager.updateContext(null)
                                val trackList = hydrateTracksSafe(listOf(Track(id = trackId, title = "", user = null, artworkUrl = null, permalinkUrl = "", durationMs = 0)))
    
                                val resolvedUrl = if (trackList.isNotEmpty()) StreamResolver.resolveStream(context, trackList[0]) else null
    
                                val items = trackList.map {
                                    trackToMediaItem(it, null, resolvedUrl)
                                }
                                Triple(items, 0, C.TIME_UNSET)
                            } else {
                                Triple(emptyList(), 0, C.TIME_UNSET)
                            }
                        }
                    }
    
                    if (queueData.first.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            val player = mediaSession.player
                            player.setMediaItems(queueData.first, queueData.second, 0L)
                            player.prepare()
                            player.play()
                        }
                        MediaItemsWithStartPosition(queueData.first, queueData.second, queueData.third)
                    } else {
                        defaultResult
                    }
    
                } catch (e: Exception) {
                    e.printStackTrace()
                    defaultResult
                }
            }
        }
        override fun onAddMediaItems(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<List<MediaItem>> {
            return serviceScope.future {
                val updatedList = mutableListOf<MediaItem>()
                for (item in mediaItems) {
                    updatedList.add(item)
                }
                updatedList
            }
        }
    
        override fun onGetLibraryRoot(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(context.getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }
    
        override fun onGetChildren(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return when {
                parentId == ROOT_ID -> getRootChildren(page, pageSize)
                parentId == LIBRARY_ROOT_ID -> getLibraryContent(page, pageSize)
                parentId == LIKES_ID -> getLikedTracks(page, pageSize)
                parentId == RECOMMENDATIONS_ID -> getRecommendations(page, pageSize)
                parentId == GENRES_ID -> getGenres(page, pageSize)
                parentId.startsWith(GENRE_PREFIX_ID) -> {
                    val genreQuery = parentId.removePrefix(GENRE_PREFIX_ID)
                    getPlaylistsForGenre(genreQuery, page, pageSize)
                }
                parentId.startsWith(PLAYLIST_PREFIX_ID) -> {
                    val playlistId = parentId.removePrefix(PLAYLIST_PREFIX_ID).toLongOrNull()
                    if (playlistId != null) getTracksForPlaylist(playlistId, page, pageSize)
                    else Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
                }
                else -> Futures.immediateFuture(LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE))
            }
        }

        private fun getRootChildren(page: Int, pageSize: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val items = mutableListOf<MediaItem>()
            items.add(createBrowsableMediaItem(LIBRARY_ROOT_ID, context.getString(R.string.nav_library)))

            items.add(createBrowsableMediaItem(RECOMMENDATIONS_ID, context.getString(R.string.home_rediscovery_title)))

            items.add(createBrowsableMediaItem(GENRES_ID, context.getString(R.string.search_section_genres)))
            val pagedItems = paginate(items, page, pageSize)
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(pagedItems), null))
        }

        private fun getLibraryContent(page: Int, pageSize: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                val items = mutableListOf<MediaItem>()
                items.add(createBrowsableMediaItem(LIKES_ID, context.getString(R.string.lib_liked_tracks)))
    
                try {
                    val db = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(context).downloadDao()
                    val localPlaylists = db.getAllPlaylists().first()
                    localPlaylists.forEach { local ->
                        val playlist = Playlist(
                            id = local.id,
                            title = local.title,
                            artworkUrl = local.localCoverPath ?: local.artworkUrl,
                            calculatedArtworkUrl = null,
                            trackCount = local.trackCount,
                            user = User(0, local.artist, null),
                            tracks = null
                        )
                        items.add(playlistToMediaItem(playlist))
                    }
                } catch (e: Exception) { e.printStackTrace() }
    
                try {
                    val me = api.getMe()
                    coroutineScope {
                        val createdDef = async { api.getMyPlaylistPosts(limit = 50) }
                        val likedDef = async { api.getUserPlaylistLikes(me.id, limit = 50) }
                        val created = createdDef.await().collection.mapNotNull { it.playlist }
                        val liked = likedDef.await().collection.map { it.playlist }
                        created.forEach { items.add(playlistToMediaItem(it)) }
                        liked.forEach { items.add(playlistToMediaItem(it)) }
                    }
                } catch (e: Exception) { e.printStackTrace() }
    
                val distinctItems = items.distinctBy { it.mediaId }
                val pagedItems = paginate(distinctItems, page, pageSize)
                LibraryResult.ofItemList(ImmutableList.copyOf(pagedItems), null)
            }
        }
    
        private fun getLikedTracks(page: Int, pageSize: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val likedTracks = likeRepository.likedTracks.value
            val pagedTracks = paginate(likedTracks, page, pageSize)
            val mediaItems = pagedTracks.map { trackToMediaItem(it, -1L) }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null))
        }
    
        private fun getRecommendations(page: Int, pageSize: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val likedTracks = likeRepository.likedTracks.value
                    val historyItems = HistoryRepository.getHistory().first()
    
                    val seedTracks = mutableListOf<Track>()
                    seedTracks.addAll(likedTracks)
                    seedTracks.addAll(historyItems
                        .filter { it.type == "TRACK" }
                        .map {
                            Track(id = it.numericId, title = it.title, user = null, artworkUrl = null, durationMs = 0L)
                        }
                    )
    
                    if (seedTracks.isEmpty()) {
                        val response = api.searchTracks("trending", limit = 50)
                        val pagedTracks = paginate(response.collection, page, pageSize)
                        val mediaItems = pagedTracks.map { trackToMediaItem(it, null) }
                        return@future LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
                    }
    
                    val seedsToUse = seedTracks.shuffled().take(5)
    
                    val deferredRecommendations = coroutineScope {
                        seedsToUse.map { seed ->
                            async {
                                try {
                                    api.getRelatedTracks(seed.id, limit = 40).collection
                                } catch (e: Exception) {
                                    emptyList<Track>()
                                }
                            }
                        }
                    }
                    val allRelatedTracks = deferredRecommendations.awaitAll().flatten().distinctBy { it.id }.shuffled()
    
                    val likedIds = likedTracks.map { it.id }.toSet()
                    val historyIds = historyItems.map { it.numericId }.toSet()
    
                    val freshRecommendations = allRelatedTracks.filter {
                        !likedIds.contains(it.id) && !historyIds.contains(it.id)
                    }
    
                    val finalTracks = if (freshRecommendations.isEmpty()) allRelatedTracks else freshRecommendations
    
                    val pagedTracks = paginate(finalTracks, page, pageSize)
                    val mediaItems = pagedTracks.map { trackToMediaItem(it, null) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
    
                } catch (e: Exception) {
                    e.printStackTrace()
                    try {
                        val response = api.searchTracks("trending", limit = 50)
                        val pagedTracks = paginate(response.collection, page, pageSize)
                        val mediaItems = pagedTracks.map { trackToMediaItem(it, null) }
                        LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
                    } catch (e2: Exception) {
                        LibraryResult.ofItemList(ImmutableList.of(), null)
                    }
                }
            }
        }
    
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return serviceScope.future {
                val prefs = PlayerPreferences(context)
    
                val lastTrack = prefs.getLastTrack()
                val lastQueue = prefs.getLastQueue()
                val lastPosition = prefs.getLastPosition()
    
                if (lastTrack == null || lastQueue.isEmpty()) {
                    return@future MediaSession.MediaItemsWithStartPosition(
                        emptyList(),
                        C.INDEX_UNSET,
                        C.TIME_UNSET
                    )
                }
    
                val startIndex = lastQueue.indexOfFirst { it.id == lastTrack.id }.coerceAtLeast(0)
                val mediaItems = lastQueue.map { trackToMediaItem(it, null) }
    
                MediaSession.MediaItemsWithStartPosition(mediaItems, startIndex, lastPosition)
            }
        }
    
        private fun getGenres(page: Int, pageSize: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val allCategories = GenreData.getMoods(context) + GenreData.getGenres(context)
            val pagedCategories = paginate(allCategories, page, pageSize)
            val mediaItems = pagedCategories.map { category -> genreToBrowsableMediaItem(category) }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null))
        }
    
        private fun genreToBrowsableMediaItem(category: SearchCategory): MediaItem {
            val metadata = MediaMetadata.Builder().setTitle(category.title).setIsBrowsable(true).setIsPlayable(false).build()
            return MediaItem.Builder().setMediaId("$GENRE_PREFIX_ID${category.query}").setMediaMetadata(metadata).build()
        }
    
        private fun getPlaylistsForGenre(genreQuery: String, page: Int, pageSize: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val response = api.searchPlaylists(genreQuery, limit = 50)
                    val pagedPlaylists = paginate(response.collection, page, pageSize)
                    val mediaItems = pagedPlaylists.map { playlistToMediaItem(it) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
                } catch (e: Exception) {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
                }
            }
        }
    
        private fun getTracksForPlaylist(playlistId: Long, page: Int, pageSize: Int): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val playlist = api.getPlaylist(playlistId)
                    val rawTracks = playlist.tracks ?: emptyList()
                    val pagedTracks = paginate(rawTracks, page, pageSize)
                    val needsHydration = pagedTracks.any { it.title.isNullOrBlank() || it.user == null }
                    val finalTracks = if (needsHydration) hydrateTracksSafe(pagedTracks) else pagedTracks
                    val mediaItems = finalTracks.map { trackToMediaItem(it, playlistId) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(mediaItems), null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    LibraryResult.ofItemList(ImmutableList.of(), null)
                }
            }
        }
    
        private fun <T> paginate(list: List<T>, page: Int, pageSize: Int): List<T> {
            val fromIndex = page * pageSize
            if (fromIndex >= list.size) return emptyList()
            val toIndex = min(fromIndex + pageSize, list.size)
            return list.subList(fromIndex, toIndex)
        }
    
        private suspend fun hydrateTracksSafe(tracksToHydrate: List<Track>): List<Track> {
            if (tracksToHydrate.isEmpty()) return emptyList()
            val chunks = tracksToHydrate.chunked(API_BATCH_LIMIT)
            val hydratedMap = mutableMapOf<Long, Track>()
            try {
                coroutineScope {
                    val deferredResults = chunks.map { batch ->
                        async {
                            try {
                                val ids = batch.map { it.id }.joinToString(",")
                                api.getTracksByIds(ids)
                            } catch (e: Exception) {
                                e.printStackTrace()
                                emptyList<Track>()
                            }
                        }
                    }
                    val results = deferredResults.awaitAll().flatten()
                    results.forEach { hydratedMap[it.id] = it }
                }
            } catch (e: Exception) { e.printStackTrace() }
            return tracksToHydrate.map { original -> hydratedMap[original.id] ?: original }
        }
    
        override fun onSearch(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            session.notifySearchResultChanged(browser, query, 0, params)
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }
    
        override fun onGetSearchResult(
            session: MediaLibraryService.MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return serviceScope.future {
                try {
                    val response = api.searchTracks(query, limit = 30)
                    val pagedTracks = paginate(response.collection, page, pageSize)
                    val searchResults = pagedTracks.map { trackToMediaItem(it, null) }
                    LibraryResult.ofItemList(ImmutableList.copyOf(searchResults), null)
                } catch (e: Exception) {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_IO)
                }
            }
        }
    
        private fun createBrowsableMediaItem(id: String, title: String): MediaItem {
            val metadata = MediaMetadata.Builder().setTitle(title).setIsBrowsable(true).setIsPlayable(false).build()
            return MediaItem.Builder().setMediaId(id).setMediaMetadata(metadata).build()
        }
    
        private fun trackToMediaItem(track: Track, contextPlaylistId: Long?, urlOverride: String? = null): MediaItem {
            val mediaId = if (contextPlaylistId != null) {
                "$TRACK_PREFIX${track.id}$CONTEXT_SEPARATOR$contextPlaylistId"
            } else {
                track.id.toString()
            }
    
            val uri = if (urlOverride != null) Uri.parse(urlOverride) else Uri.parse("soundtune://track/${track.id}")
    
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(track.title ?: context.getString(R.string.untitled_track))
                .setArtist(track.user?.username ?: context.getString(R.string.unknown_artist))
                .setArtworkUri(Uri.parse(track.fullResArtwork))
                .setIsBrowsable(false)
                .setIsPlayable(true)
    
            val builder = MediaItem.Builder()
                .setMediaId(mediaId)
                .setUri(uri)
                .setMediaMetadata(metadataBuilder.build())
    
            if (urlOverride != null && urlOverride.contains(".m3u8")) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            }
    
            // Configure Widevine DRM if a license token is cached for this track
            val drmToken = MusicManager.getDrmToken(track.id)
            if (drmToken != null) {
                builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                builder.setDrmConfiguration(
                    MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.WIDEVINE_UUID)
                        .build()
                )
            }

            return builder.build()
        }
    
        private fun playlistToMediaItem(playlist: Playlist): MediaItem {
            val metadata = MediaMetadata.Builder()
                .setTitle(playlist.title ?: context.getString(R.string.generic_title))
                .setSubtitle(playlist.user?.username ?: "")
                .setArtworkUri(Uri.parse(playlist.fullResArtwork))
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
            return MediaItem.Builder().setMediaId("$PLAYLIST_PREFIX_ID${playlist.id}").setMediaMetadata(metadata).build()
        }
    }


