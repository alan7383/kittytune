package com.alananasss.kittytune.ui.profile

import android.app.Application
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
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.UpdateProfileRequest
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

// tabs logic handled by view, title stripped
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

    // all the content lists
    val popularTracks = mutableStateListOf<Track>()
    val allTracks = mutableStateListOf<Track>()
    val repostedTracks = mutableStateListOf<Track>()
    val albums = mutableStateListOf<Playlist>()
    val playlists = mutableStateListOf<Playlist>()
    val likedTracks = mutableStateListOf<Track>()
    val similarArtists = mutableStateListOf<User>()

    var artistStationId: Long? = null

    // helper to get strings inside viewmodel
    private fun getString(@StringRes resId: Int): String = getApplication<Application>().getString(resId)
    private fun getString(@StringRes resId: Int, vararg formatArgs: Any): String = getApplication<Application>().getString(resId, *formatArgs)


    // helper to page through all tracks if needed
    private suspend fun fetchAllUserTracks(userId: Long): List<Track> {
        val allUserTracks = mutableListOf<Track>()
        try {
            val firstPage = api.getUserTracks(userId, limit = 200)
            allUserTracks.addAll(firstPage.collection.filterNotNull())
            var nextUrl = firstPage.next_href
            var pageCount = 0
            // safety limit to avoid infinite loops
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
                // check if it's me
                try {
                    val me = api.getMe()
                    if (me.id == userId) {
                        isCurrentUser = true
                    }
                } catch (e: Exception) { /* ignore */ }

                // avoid flickering if we reload same user
                if (user?.id != userId) {
                    user = api.getUser(userId)
                } else {
                    val freshUser = api.getUser(userId)
                    user = freshUser
                }

                coroutineScope {
                    // parallel fetching of everything
                    val popDef = async { try { api.getUserTopTracks(userId).collection.filterNotNull() } catch (_: Exception) { emptyList() } }
                    val tracksDef = async { fetchAllUserTracks(userId) }
                    val repostsDef = async {
                        try {
                            api.getUserReposts(userId, limit = 50).collection
                                .filter { it.type == "track-repost" && it.track != null }
                                .mapNotNull { it.track }
                        } catch (_: Exception) { emptyList() }
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
                            // find related artists via tracks
                            val related = api.getRelatedTracks(station?.tracks?.firstOrNull()?.id ?: 0, limit = 20)
                            artists = related.collection.mapNotNull { it.user }.filter { it.id != userId }.distinctBy { it.id }.shuffled().take(10)
                        } catch (_: Exception) { }
                        artists
                    }

                    popularTracks.clear(); popularTracks.addAll(popDef.await())
                    allTracks.clear(); allTracks.addAll(tracksDef.await())
                    repostedTracks.clear(); repostedTracks.addAll(repostsDef.await())
                    albums.clear(); albums.addAll(albumsDef.await())
                    playlists.clear(); playlists.addAll(playDef.await())
                    likedTracks.clear(); likedTracks.addAll(likesDef.await())
                    similarArtists.clear(); similarArtists.addAll(simDef.await())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
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
            // optimistic update
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
                // rollback on error
                user = oldUser
                Toast.makeText(getApplication(), getString(R.string.profile_update_error, e.message ?: ""), Toast.LENGTH_LONG).show()
            }
        }
    }

    fun onTabSelected(tab: ProfileTab) { selectedTab = tab }
}