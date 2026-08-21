    package com.alananasss.kittytune.ui.profile

    import android.app.Application
    import android.content.Context
    import android.graphics.Bitmap
    import android.graphics.ImageDecoder
    import android.net.Uri
    import android.os.Build
    import android.provider.MediaStore
    import android.util.Base64
    import android.widget.Toast
    import androidx.annotation.StringRes
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.MusicManager
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.*
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.flow.MutableSharedFlow
    import kotlinx.coroutines.flow.asSharedFlow
    import kotlinx.coroutines.launch
    import java.io.ByteArrayOutputStream

    enum class ProfileTab {
        POPULAR,
        TRACKS,
        ALBUMS,
        PLAYLISTS,
        LIKES,
        REPOSTS
    }

    class ProfileViewModel(application: Application) : AndroidViewModel(application) {
        companion object {
            private val _refreshTrigger = MutableSharedFlow<Long>(extraBufferCapacity = 1)
            val refreshTrigger = _refreshTrigger.asSharedFlow()

            fun triggerRefresh(userId: Long = 0L) {
                _refreshTrigger.tryEmit(userId)
            }
        }

        private val api = RetrofitClient.create(application)

        var user by mutableStateOf<User?>(null)
        var isCurrentUser by mutableStateOf(false)
        var isSpotifyProfile by mutableStateOf(false)
        var spotifyArtist by mutableStateOf<com.alananasss.kittytune.data.spotify.SpotifyArtist?>(null)
        var isLoading by mutableStateOf(true)
        var selectedTab by mutableStateOf(ProfileTab.POPULAR)

        val popularTracks = mutableStateListOf<Track>()
        val allTracks = mutableStateListOf<Track>()
        val repostedTracks = mutableStateListOf<Track>()
        val popularReleases = mutableStateListOf<Playlist>()
        val albums = mutableStateListOf<Playlist>()
        val singles = mutableStateListOf<Playlist>()
        val compilations = mutableStateListOf<Playlist>()
        val appearsOn = mutableStateListOf<Playlist>()
        val discoveredOn = mutableStateListOf<Playlist>()
        val playlists = mutableStateListOf<Playlist>()
        val likedTracks = mutableStateListOf<Track>()
        val similarArtists = mutableStateListOf<User>()
        val userComments = mutableStateListOf<Comment>()
        private var commentsNextUrl: String? = null
        var isCommentsLoadingMore by mutableStateOf(false)

        var artistStationId: Long? = null

        init {
            viewModelScope.launch {
                _refreshTrigger.collect { targetUserId ->
                    val currentId = user?.id
                    if (currentId != null && (targetUserId == 0L || targetUserId == currentId)) {
                        loadProfile(currentId, forceRefresh = true)
                    }
                }
            }

            viewModelScope.launch {
                MusicManager.trackUpdatedFlow.collect { updatedTrack ->
                    val popIdx = popularTracks.indexOfFirst { it.id == updatedTrack.id }
                    if (popIdx != -1) popularTracks[popIdx] = updatedTrack

                    val allIdx = allTracks.indexOfFirst { it.id == updatedTrack.id }
                    if (allIdx != -1) allTracks[allIdx] = updatedTrack

                    val repIdx = repostedTracks.indexOfFirst { it.id == updatedTrack.id }
                    if (repIdx != -1) repostedTracks[repIdx] = updatedTrack

                    val likeIdx = likedTracks.indexOfFirst { it.id == updatedTrack.id }
                    if (likeIdx != -1) likedTracks[likeIdx] = updatedTrack
                }
            }

            viewModelScope.launch {
                MusicManager.trackDeletedFlow.collect { deletedTrackId ->
                    popularTracks.removeAll { it.id == deletedTrackId }
                    allTracks.removeAll { it.id == deletedTrackId }
                    repostedTracks.removeAll { it.id == deletedTrackId }
                    likedTracks.removeAll { it.id == deletedTrackId }
                }
            }
        }

        private fun getString(@StringRes resId: Int): String = com.alananasss.kittytune.utils.LocaleUtils.updateBaseContextLocale(getApplication()).getString(resId)
        private fun getString(@StringRes resId: Int, vararg formatArgs: Any): String = com.alananasss.kittytune.utils.LocaleUtils.updateBaseContextLocale(getApplication()).getString(resId, *formatArgs)

        private suspend fun fetchAllUserTracks(userId: Long): List<Track> {
            val allUserTracks = mutableListOf<Track>()
            try {
                val firstPage = api.getUserTracks(userId, limit = 200)
                allUserTracks.addAll(firstPage.collection.filterNotNull())
                var nextUrl = firstPage.next_href
                var pageCount = 0
                while (nextUrl != null && pageCount < 20) {
                    val nextPage = api.getUserTracksNextPage(nextUrl)
                    allUserTracks.addAll(nextPage.collection.filterNotNull())
                    nextUrl = nextPage.next_href
                    pageCount++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return allUserTracks
        }

        fun loadProfile(userIdStr: String, forceRefresh: Boolean = false) {
            val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(userIdStr)
            if (userIdStr.startsWith("spotify:") || userIdStr.startsWith("spotify_artist:") || userIdStr.startsWith("spotify")) {
                loadSpotifyArtist(cleanId, forceRefresh)
                return
            }
            if (cleanId.length == 22 && cleanId.all { it.isLetterOrDigit() }) {
                loadSpotifyArtist(cleanId, forceRefresh)
                return
            }
            val id = userIdStr.toLongOrNull()
            if (id != null && id > 0L) {
                val prefs = com.alananasss.kittytune.data.local.PlayerPreferences(getApplication())
                val mappedSpotifyId = prefs.getSpotifyArtistIdForStableId(id)
                if (!mappedSpotifyId.isNullOrBlank()) {
                    loadSpotifyArtist(mappedSpotifyId, forceRefresh)
                    return
                }
                if (id > 1000000000000L) {
                    viewModelScope.launch {
                        isLoading = true
                        val db = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(getApplication())
                        val histItem = db.downloadDao().getHistoryItemById(id, "profile:$id")
                        val artistName = histItem?.title
                        if (!artistName.isNullOrBlank()) {
                            val search = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(artistName)
                            val match = search.artists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                                ?: search.artists.firstOrNull { it.name.contains(artistName, ignoreCase = true) || artistName.contains(it.name, ignoreCase = true) }
                            if (match != null) {
                                prefs.saveSpotifyArtistMapping(id, match.id)
                                loadSpotifyArtist(match.id, forceRefresh)
                                return@launch
                            }
                        }
                        loadProfile(id, forceRefresh)
                    }
                    return
                }
                loadProfile(id, forceRefresh)
            } else if (userIdStr.isNotBlank() && userIdStr != "0") {
                resolveAndLoadProfile(userIdStr, forceRefresh)
            }
        }

        private fun resolveAndLoadProfile(query: String, forceRefresh: Boolean = false) {
            viewModelScope.launch {
                isLoading = true
                user = null
                try {
                    val spotifyResults = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(query)
                    val spotifyArtist = spotifyResults.artists.firstOrNull {
                        it.name.equals(query, ignoreCase = true)
                    } ?: spotifyResults.artists.firstOrNull {
                        it.name.contains(query, ignoreCase = true) || query.contains(it.name, ignoreCase = true)
                    }

                    if (spotifyArtist != null) {
                        loadSpotifyArtist(spotifyArtist.id, forceRefresh)
                        return@launch
                    }

                    val resolved = try {
                        val soundCloudUrl = if (query.startsWith("http")) query else "https://soundcloud.com/$query"
                        api.resolveUrl(soundCloudUrl)
                    } catch (_: Exception) { null }
                    if (resolved != null && resolved.isJsonObject) {
                        val id = resolved.get("id")?.asLong
                        if (id != null && id > 0L) {
                            loadProfile(id, forceRefresh)
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "Failed to resolve profile: $query", e)
                } finally {
                    isLoading = false
                }
            }
        }

        fun loadSpotifyArtist(artistId: String, forceRefresh: Boolean = false) {
            val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(artistId)
            if (cleanId.isBlank()) return
            if (spotifyArtist?.id == cleanId && user != null && !forceRefresh) return

            viewModelScope.launch {
                isSpotifyProfile = true
                isCurrentUser = false
                isLoading = true
                user = null
                popularTracks.clear()
                allTracks.clear()
                repostedTracks.clear()
                popularReleases.clear()
                albums.clear()
                singles.clear()
                compilations.clear()
                appearsOn.clear()
                discoveredOn.clear()
                playlists.clear()
                likedTracks.clear()
                similarArtists.clear()
                userComments.clear()
                commentsNextUrl = null

                try {
                    var artist = if (cleanId.length == 22 && cleanId.all { it.isLetterOrDigit() }) {
                        com.alananasss.kittytune.data.spotify.SpotifyRepository.getArtist(cleanId)
                    } else null

                    if (artist == null) {
                        val searchRes = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(cleanId)
                        val match = searchRes.artists.firstOrNull { it.name.trim().equals(cleanId.trim(), ignoreCase = true) }
                            ?: searchRes.artists.firstOrNull { it.name.trim().startsWith(cleanId.trim(), ignoreCase = true) || cleanId.trim().startsWith(it.name.trim(), ignoreCase = true) }
                            ?: searchRes.artists.firstOrNull { it.name.contains(cleanId, ignoreCase = true) || cleanId.contains(it.name, ignoreCase = true) }
                        if (match != null) {
                            artist = com.alananasss.kittytune.data.spotify.SpotifyRepository.getArtist(match.id)
                        }
                    }
                    if (artist != null) {
                        spotifyArtist = artist
                        val u = artist.toUser()
                        user = u.copy(
                            description = artist.biography
                        )
                        popularTracks.addAll(artist.topTracks.map { it.toTrack() })
                        val popRels = artist.popularReleases.map { it.toPlaylist() }
                        val albRels = artist.albums.map { it.toPlaylist() }
                        val sngRels = artist.singles.map { it.toPlaylist() }
                        val compRels = artist.compilations.map { it.toPlaylist() }

                        albums.addAll(albRels)
                        singles.addAll(sngRels)
                        compilations.addAll(compRels)

                        if (popRels.isNotEmpty()) {
                            popularReleases.addAll(popRels)
                        } else {
                            popularReleases.addAll((albRels.take(5) + sngRels.take(5)))
                        }

                        appearsOn.addAll(artist.appearsOn.map { it.toPlaylist() })
                        discoveredOn.addAll(artist.discoveredOn.map { it.toPlaylist() })
                        similarArtists.addAll(
                            artist.relatedArtists.map { rel ->
                                User(
                                    id = kotlin.math.abs(rel.id.hashCode().toLong()),
                                    username = rel.name,
                                    avatarUrl = rel.avatarUrl,
                                    verified = rel.verified,
                                    urn = "spotify:artist:${rel.id}",
                                    permalink = rel.id
                                )
                            }
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ProfileViewModel", "Failed to load Spotify artist: ${e.message}", e)
                } finally {
                    isLoading = false
                }
            }
        }

        fun loadProfile(userId: Long, forceRefresh: Boolean = false) {
            if (user?.id == userId && !forceRefresh && user != null && !isSpotifyProfile) {
                return
            }

            viewModelScope.launch {
                isSpotifyProfile = false
                spotifyArtist = null
                val isDifferentUser = user?.id != userId
                if (isDifferentUser) {
                    isLoading = true
                    user = null
                    isCurrentUser = false
                    popularTracks.clear()
                    allTracks.clear()
                    repostedTracks.clear()
                    albums.clear()
                    singles.clear()
                    compilations.clear()
                    appearsOn.clear()
                    discoveredOn.clear()
                    playlists.clear()
                    likedTracks.clear()
                    similarArtists.clear()
                    userComments.clear()
                    commentsNextUrl = null
                } else if (forceRefresh) {
                    isLoading = true
                }

                try {
                    try {
                        val me = api.getMe()
                        isCurrentUser = (me.id == userId)
                    } catch (e: Exception) {}

                    val freshUser = fetchUser(userId)
                    if (freshUser != null) {
                        user = freshUser
                    } else {
                        val prefs = com.alananasss.kittytune.data.local.PlayerPreferences(getApplication())
                        val mappedSpotifyId = prefs.getSpotifyArtistIdForStableId(userId)
                        if (!mappedSpotifyId.isNullOrBlank()) {
                            loadSpotifyArtist(mappedSpotifyId, forceRefresh)
                            return@launch
                        }

                        val db = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(getApplication())
                        val histItem = db.downloadDao().getHistoryItemById(userId, "profile:$userId")
                        val artistName = histItem?.title
                        if (!artistName.isNullOrBlank()) {
                            val search = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(artistName)
                            val match = search.artists.firstOrNull { it.name.equals(artistName, ignoreCase = true) }
                                ?: search.artists.firstOrNull { it.name.contains(artistName, ignoreCase = true) || artistName.contains(it.name, ignoreCase = true) }
                            if (match != null) {
                                prefs.saveSpotifyArtistMapping(userId, match.id)
                                loadSpotifyArtist(match.id, forceRefresh)
                                return@launch
                            }
                        }
                        user = null
                        return@launch
                    }

                    coroutineScope {
                        val popDef = async { try { api.getUserTopTracks(userId).collection.filterNotNull() } catch (_: Exception) { emptyList() } }
                        val tracksDef = async { fetchAllUserTracks(userId) }
                        val repostsDef = async {
                            try {
                                api.getUserReposts(userId, limit = 50).collection
                                    .filter { it.type == "track-repost" && it.track != null }
                                    .mapNotNull { it.track }
                            } catch (_: Exception) { emptyList() }
                        }

                        val commentsResponseDef = async {
                            try {
                                api.getUserComments(userId, limit = 20)
                            } catch (_: Exception) {
                                null
                            }
                        }
                        val albumsDef = async { try { api.getUserAlbums(userId).collection.filterNotNull() } catch (_: Exception) { emptyList() } }
                        val playDef = async { try { api.getUserCreatedPlaylists(userId).collection.filterNotNull() } catch (_: Exception) { emptyList() } }

                        val likesDef = async {
                            val allLikes = mutableListOf<Track>()
                            try {
                                var nextUrl: String? = null
                                val firstPage = api.getUserTrackLikes(userId, limit = 50)
                                allLikes.addAll(firstPage.collection.mapNotNull { it.track })
                                nextUrl = firstPage.next_href
                                var safetyCount = 0
                                while (nextUrl != null && safetyCount < 10) {
                                    val page = api.getTrackLikesNextPage(nextUrl!!)
                                    allLikes.addAll(page.collection.mapNotNull { it.track })
                                    nextUrl = page.next_href
                                    safetyCount++
                                }
                            } catch (_: Exception) { }
                            allLikes
                        }
                        val simDef = async {
                            var artists = emptyList<User>()
                            try {
                                val station = try { api.getArtistStation(userId) } catch (e: Exception) { null }
                                if (station != null) artistStationId = station.id
                                val related = api.getRelatedTracks(station?.tracks?.firstOrNull()?.id ?: 0, limit = 20)
                                artists = related.collection.mapNotNull { it.user }.filter { it.id != userId }.distinctBy { it.id }.shuffled().take(10)
                            } catch (_: Exception) { }
                            artists
                        }

                        val fetchedPop = popDef.await()
                        popularTracks.clear(); popularTracks.addAll(fetchedPop)

                        val fetchedTracks = tracksDef.await()
                        allTracks.clear(); allTracks.addAll(fetchedTracks)

                        val fetchedReposts = repostsDef.await()
                        repostedTracks.clear(); repostedTracks.addAll(fetchedReposts)

                        val fetchedAlbums = albumsDef.await()
                        val fetchedPlaylists = playDef.await()

                        albums.clear()
                        albums.addAll(fetchedAlbums.filter { it.isRealAlbum })

                        playlists.clear()
                        playlists.addAll(fetchedPlaylists.filter { !it.isRealAlbum })

                        val fetchedLikes = likesDef.await()
                        likedTracks.clear(); likedTracks.addAll(fetchedLikes)

                        val fetchedArtists = simDef.await()
                        similarArtists.clear(); similarArtists.addAll(fetchedArtists)

                        val commentsRes = commentsResponseDef.await()
                        if (commentsRes != null) {
                            val validComments = commentsRes.collection.filter { it.track != null }
                            userComments.clear()
                            userComments.addAll(validComments)
                            commentsNextUrl = commentsRes.next_href
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }

        fun loadMoreUserComments() {
            if (isCommentsLoadingMore || commentsNextUrl == null) return

            viewModelScope.launch {
                isCommentsLoadingMore = true
                try {
                    val response = api.getUserCommentsNextPage(commentsNextUrl!!)
                    val validComments = response.collection.filter { it.track != null }
                    userComments.addAll(validComments)
                    commentsNextUrl = response.next_href
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isCommentsLoadingMore = false
                }
            }
        }

        fun updateProfile(
            username: String,
            bio: String,
            city: String,
            country: String
        ) {
            val oldUser = user ?: return

            viewModelScope.launch {
                user = oldUser.copy(username = username, description = bio, city = city)

                try {
                    val request = UpdateProfileRequest(
                        username = username,
                        description = bio,
                        city = city,
                        countryCode = null
                    )
                    val updatedUser = api.updateMe(request)

                    if (!updatedUser.username.isNullOrBlank()) {
                        user = updatedUser
                    }

                    Toast.makeText(getApplication(), getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                    user = oldUser
                    Toast.makeText(getApplication(), getString(R.string.profile_update_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                }
            }
        }

        fun updateAvatarFromBitmap(context: Context, bitmap: Bitmap) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()

                    val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    val request = AvatarUpdateRequest(imageData = base64String)
                    val response = api.updateAvatar(request)

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        throw Exception("Avatar upload failed: ${response.code()} - $errorBody")
                    }

                    val updatedUser = api.getMe()
                    user = updatedUser
                    Toast.makeText(getApplication(), getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(getApplication(), getString(R.string.profile_update_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }

        fun updateAvatar(context: Context, uri: Uri) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source)
                    }

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()

                    val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    val request = AvatarUpdateRequest(imageData = base64String)
                    val response = api.updateAvatar(request)

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        throw Exception("Avatar upload failed: ${response.code()} - $errorBody")
                    }

                    val updatedUser = api.getMe()
                    user = updatedUser
                    Toast.makeText(getApplication(), getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(getApplication(), getString(R.string.profile_update_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }

        fun updateBanner(context: Context, uri: Uri) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source)
                    }

                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()

                    val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    val request = BannerUploadRequest(imageData = base64String)
                    val response = api.updateBanner(request)

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        throw Exception("Banner upload failed: ${response.code()} - $errorBody")
                    }

                    val updatedUser = api.getMe()
                    user = updatedUser

                    Toast.makeText(getApplication(), getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(getApplication(), getString(R.string.profile_update_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }

        fun deleteAvatar(context: Context) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val response = api.deleteAvatar()

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        throw Exception("Avatar delete failed: ${response.code()} - $errorBody")
                    }

                    val updatedUser = api.getMe()
                    user = updatedUser
                    Toast.makeText(getApplication(), getString(R.string.profile_avatar_deleted), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(getApplication(), getString(R.string.profile_update_error, e.message ?: ""), Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }

        fun deleteBanner(context: Context) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val response = api.deleteBanner()

                    if (response.isSuccessful) {
                        val updatedUser = api.getMe()
                        user = updatedUser
                        Toast.makeText(getApplication(), getString(R.string.profile_banner_deleted), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(getApplication(), getString(R.string.error_generic), Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(getApplication(), getString(R.string.error_generic), Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }

        fun updateBannerFromBitmap(context: Context, bitmap: Bitmap) {
            viewModelScope.launch {
                isLoading = true
                try {
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()

                    val base64String = Base64.encodeToString(byteArray, Base64.NO_WRAP)
                    val request = BannerUploadRequest(imageData = base64String)
                    val response = api.updateBanner(request)

                    if (!response.isSuccessful) {
                        val errorBody = response.errorBody()?.string()
                        throw Exception("Banner upload failed: ${response.code()} - $errorBody")
                    }

                    val updatedUser = api.getMe()
                    user = updatedUser

                    Toast.makeText(getApplication(), getString(R.string.profile_update_success), Toast.LENGTH_SHORT).show()

                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(getApplication(), getString(R.string.profile_update_error, e.message ?: "Unknown"), Toast.LENGTH_LONG).show()
                } finally {
                    isLoading = false
                }
            }
        }

        private suspend fun fetchUser(userId: Long): User? {
            return try {
                val req = GraphQlRequest(
                    operationName = "UserProfile",
                    query = """
                        query UserProfile(${'$'}urn: ID!) {
                          user(urn: ${'$'}urn) {
                            urn
                            username
                            avatarUrl
                            city
                            countryCode
                            followersCount
                            followingsCount
                            tracksCount
                            description
                            permalinkUrl
                            permalink
                            verified
                          }
                        }
                    """.trimIndent(),
                    variables = mapOf("urn" to "soundcloud:users:$userId")
                )
                val response = api.getUserProfileGraphQL(req)
                response.data?.user?.copy(id = userId) ?: try { api.getUser(userId) } catch (_: Exception) { null }
            } catch (e: Exception) {
                try { api.getUser(userId) } catch (_: Exception) { null }
            }
        }

        fun onTabSelected(tab: ProfileTab) { selectedTab = tab }
    }
