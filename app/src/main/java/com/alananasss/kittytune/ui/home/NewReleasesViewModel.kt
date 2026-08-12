    package com.alananasss.kittytune.ui.home
    
    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.google.gson.Gson
    import kotlinx.coroutines.async
    import kotlinx.coroutines.awaitAll
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.launch
    
    class NewReleasesViewModel(application: Application) : AndroidViewModel(application) {
        // basic setup for api requests
        private val api = RetrofitClient.create(application)
        private val gson = com.alananasss.kittytune.utils.AppUtils.gson
    
        // ui state management
        var isLoading by mutableStateOf(true)
            private set
        val playlists = mutableStateListOf<Playlist>()
        // holding top tracks, now up to 50
        val popularTracks = mutableStateListOf<Track>()
    
        // source profile for the releases
        private val targetUsername = "buzzing-playlists"
    
        init {
            // fetch all data on viewmodel initialization
            loadPlaylists()
        }
    
        private fun loadPlaylists() {
            viewModelScope.launch {
                isLoading = true
                try {
                    // we need to resolve the username to get the user id first
                    val userUrl = "https://soundcloud.com/$targetUsername"
                    val resolvedUserJson = api.resolveUrl(userUrl)
                    val user = gson.fromJson(resolvedUserJson, User::class.java)
    
                    // once we have the user, fetch their playlists
                    if (user.id != 0L) {
                        val response = api.getUserCreatedPlaylists(userId = user.id, limit = 200)
                        playlists.clear()
                        playlists.addAll(response.collection)
    
                        // after fetching playlists, extract popular tracks from them
                        extractPopularTracks(response.collection)
                    }
                } catch (e: Exception) {
                    // handle any network or parsing errors
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    
        private suspend fun extractPopularTracks(allPlaylists: List<Playlist>) {
            if (allPlaylists.isEmpty()) return
    
            popularTracks.clear()
            try {
                // take a random sample of playlists to avoid fetching tracks from all of them (api limit)
                val playlistsToSample = allPlaylists.shuffled().take(20) // increased sample size for more variety
    
                // fetch full details for the sampled playlists in parallel for speed
                val detailedPlaylists = coroutineScope {
                    playlistsToSample.map { playlist ->
                        async {
                            try {
                                api.getPlaylist(playlist.id)
                            } catch (e: Exception) {
                                null // ignore individual playlist fetch failures
                            }
                        }
                    }.awaitAll().filterNotNull()
                }
    
                // merge all partial tracks from the detailed playlists into a single list
                val partialTracks = detailedPlaylists.flatMap { it.tracks.orEmpty() }
                val trackIds = partialTracks.map { it.id }.distinct()
    
                if (trackIds.isEmpty()) return
    
                // fetch full track details in chunks of 50 (api limit)
                val fullTracks = coroutineScope {
                    trackIds.chunked(50).map { chunk ->
                        async {
                            try {
                                api.getTracksByIds(chunk.joinToString(","))
                            } catch (e: Exception) {
                                emptyList<Track>()
                            }
                        }
                    }.awaitAll().flatten()
                }
    
                // sort the full tracks by playback count, remove duplicates, and take the top 50
                val topTracks = fullTracks
                    .sortedByDescending { it.playbackCount }
                    .distinctBy { it.id }
                    .take(50)
    
                popularTracks.addAll(topTracks)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


