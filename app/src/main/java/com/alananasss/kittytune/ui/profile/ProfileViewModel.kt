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
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.*
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.launch
    import java.io.ByteArrayOutputStream

    // Tab enum
    enum class ProfileTab {
        POPULAR,
        TRACKS,
        ALBUMS,
        PLAYLISTS,
        LIKES,
        REPOSTS
    }

    class ProfileViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)

        var user by mutableStateOf<User?>(null)
        var isCurrentUser by mutableStateOf(false)
        var isLoading by mutableStateOf(true)
        var selectedTab by mutableStateOf(ProfileTab.POPULAR)

        // Content lists
        val popularTracks = mutableStateListOf<Track>()
        val allTracks = mutableStateListOf<Track>()
        val repostedTracks = mutableStateListOf<Track>()
        val albums = mutableStateListOf<Playlist>()
        val playlists = mutableStateListOf<Playlist>()
        val likedTracks = mutableStateListOf<Track>()
        val similarArtists = mutableStateListOf<User>()
        val userComments = mutableStateListOf<Comment>()
        private var commentsNextUrl: String? = null
        var isCommentsLoadingMore by mutableStateOf(false)

        var artistStationId: Long? = null

        // Helper to get strings from resources
        private fun getString(@StringRes resId: Int): String = getApplication<Application>().getString(resId)
        private fun getString(@StringRes resId: Int, vararg formatArgs: Any): String = getApplication<Application>().getString(resId, *formatArgs)

        // Helper to paginate through all user tracks
        private suspend fun fetchAllUserTracks(userId: Long): List<Track> {
            val allUserTracks = mutableListOf<Track>()
            try {
                val firstPage = api.getUserTracks(userId, limit = 200)
                allUserTracks.addAll(firstPage.collection.filterNotNull())
                var nextUrl = firstPage.next_href
                var pageCount = 0
                // Safety limit to avoid infinite loops
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

        fun loadProfile(userId: Long) {
            viewModelScope.launch {
                isLoading = true
                isCurrentUser = false
                try {
                    // Check if current user
                    try {
                        val me = api.getMe()
                        if (me.id == userId) {
                            isCurrentUser = true
                        }
                    } catch (e: Exception) { /* ignore */ }

                    // Avoid flickering if reloading same user
                    if (user?.id != userId) {
                        user = fetchUser(userId)
                    } else {
                        val freshUser = fetchUser(userId)
                        user = freshUser
                    }

                    // We rely on DownloadManager.refreshFollowings() in the background
                    // No need to fetch checkFollowState manually on each profile load.

                    coroutineScope {
                        // Parallel fetching
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

                        // Retrieve collections for separation
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
                                // Find related artists via tracks
                                val related = api.getRelatedTracks(station?.tracks?.firstOrNull()?.id ?: 0, limit = 20)
                                artists = related.collection.mapNotNull { it.user }.filter { it.id != userId }.distinctBy { it.id }.shuffled().take(10)
                            } catch (_: Exception) { }
                            artists
                        }

                        popularTracks.clear(); popularTracks.addAll(popDef.await())
                        allTracks.clear(); allTracks.addAll(tracksDef.await())
                        repostedTracks.clear(); repostedTracks.addAll(repostsDef.await())

                        // STRICT SEPARATION LOGIC
                        val fetchedAlbums = albumsDef.await()
                        val fetchedPlaylists = playDef.await()

                        // Albums list: Only items where isAlbum is true
                        albums.clear()
                        albums.addAll(fetchedAlbums.filter { it.isAlbum })

                        // Playlists list: Exclude anything that is an album
                        playlists.clear()
                        playlists.addAll(fetchedPlaylists.filter { !it.isAlbum })

                        likedTracks.clear(); likedTracks.addAll(likesDef.await())
                        similarArtists.clear(); similarArtists.addAll(simDef.await())
                        userComments.clear()
                        val commentsRes = commentsResponseDef.await()
                        if (commentsRes != null) {
                            val validComments = commentsRes.collection.filter { it.track != null }
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
                // Optimistic update
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
                    // Rollback on error
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
                        // API returns 204 No Content, manual refresh needed
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

        private suspend fun fetchUser(userId: Long): User {
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
                response.data?.user?.copy(id = userId) ?: api.getUser(userId)
            } catch (e: Exception) {
                api.getUser(userId)
            }
        }

        fun onTabSelected(tab: ProfileTab) { selectedTab = tab }
    }
