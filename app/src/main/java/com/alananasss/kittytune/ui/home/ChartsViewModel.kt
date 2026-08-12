    package com.alananasss.kittytune.ui.home
    
    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.ChartsData
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.google.gson.Gson
    import kotlinx.coroutines.async
    import kotlinx.coroutines.awaitAll
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.launch
    
    data class ArtistRanking(
        val user: User,
        val score: Long, // Keep the score for sorting, but we will display the followers
        val rank: Int
    )
    
    class ChartsViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)
        private val gson = com.alananasss.kittytune.utils.AppUtils.gson
    
        var selectedCountryIndex by mutableStateOf(0)
        val chartPlaylists = mutableStateListOf<Playlist>()
        val topArtists = mutableStateListOf<ArtistRanking>()
    
        var isLoading by mutableStateOf(false)
    
        init {
            loadCountryCharts(0)
        }
    
        fun loadCountryCharts(index: Int) {
            selectedCountryIndex = index
            val countryData = ChartsData.charts[index]
    
            viewModelScope.launch {
                isLoading = true
                chartPlaylists.clear()
                topArtists.clear()
    
                try {
                    // Parallel playlist retrieval
                    val playlists = coroutineScope {
                        countryData.playlistUrls.map { url ->
                            async {
                                try {
                                    val resolvedJson = api.resolveUrl(url)
                                    val kind = resolvedJson.get("kind")?.asString
                                    if (kind == "playlist") {
                                        gson.fromJson(resolvedJson, Playlist::class.java)
                                    } else {
                                        null
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
    
                    chartPlaylists.addAll(playlists)
                    calculateTopArtists(playlists)
    
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    
        private fun calculateTopArtists(playlists: List<Playlist>) {
            val userMap = mutableMapOf<Long, User>()
            val playCounts = mutableMapOf<Long, Long>()
    
            playlists.forEach { playlist ->
                playlist.tracks?.forEach { track ->
                    val user = track.user
                    if (user != null && user.id > 0) {
                        // Keep the user object as complete as possible
                        if (!userMap.containsKey(user.id) || (user.followersCount > (userMap[user.id]?.followersCount ?: 0))) {
                            userMap[user.id] = user
                        }
                        // Sort by popularity in the charts (plays) anyway
                        val current = playCounts.getOrDefault(user.id, 0L)
                        playCounts[user.id] = current + track.playbackCount.toLong()
                    }
                }
            }
    
            val sorted = playCounts.entries
                .sortedByDescending { it.value }
                .take(40)
                .mapIndexed { index, entry ->
                    val user = userMap[entry.key]!!
                    ArtistRanking(
                        user = user,
                        score = entry.value,
                        rank = index + 1
                    )
                }
    
            topArtists.addAll(sorted)
        }
    
        fun fetchArtistTopTracks(userId: Long, onResult: (List<Track>) -> Unit) {
            viewModelScope.launch {
                try {
                    val tracks = api.getUserTopTracks(userId, limit = 20).collection
                    onResult(tracks)
                } catch (e: Exception) {
                    e.printStackTrace()
                    onResult(emptyList())
                }
            }
        }
    }


