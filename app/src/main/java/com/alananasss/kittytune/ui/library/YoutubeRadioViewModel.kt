    package com.alananasss.kittytune.ui.library
    
    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.zionhuang.innertube.YouTube
    import com.zionhuang.innertube.models.SongItem
    import com.zionhuang.innertube.models.WatchEndpoint
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    
    class YoutubeRadioViewModel(application: Application) : AndroidViewModel(application) {
        val tracks = mutableStateListOf<Track>()
        var isLoading by mutableStateOf(true)
        var isLoadingMore by mutableStateOf(false)
    
        var playlistTitle by mutableStateOf("")
        var playlistCover by mutableStateOf<String?>(null)
        var playlistUser by mutableStateOf<User?>(null)
    
        private var videoId: String? = null
    
        fun loadInitial(youtubeUrl: String) {
            if (tracks.isNotEmpty()) return
            viewModelScope.launch {
                isLoading = true
                withContext(Dispatchers.IO) {
                    try {
                        val cleanId = youtubeUrl.substringAfter("v=").substringBefore("&")
                        videoId = cleanId
    
                        val endpoint = WatchEndpoint(videoId = cleanId)
                        val result = YouTube.next(endpoint).getOrNull()
    
                        val items = result?.items ?: emptyList()
    
                        val newTracks = items.filterIsInstance<SongItem>().map { item ->
                            Track(
                                id = item.id.hashCode().toLong(),
                                title = item.title,
                                user = User(0L, item.artists.firstOrNull()?.name ?: "YouTube", null),
                                artworkUrl = item.thumbnail,
                                durationMs = (item.duration ?: 0) * 1000L,
                                permalinkUrl = "https://youtube.com/watch?v=${item.id}",
                                source = "youtube"
                            )
                        }
    
                        withContext(Dispatchers.Main) {
                            val firstArtist = newTracks.firstOrNull()?.user?.username
                            playlistTitle = if (firstArtist != null) "Mix • $firstArtist" else "YouTube Mix"
                            playlistCover = newTracks.firstOrNull()?.artworkUrl
                            playlistUser = User(0, "YouTube", null)
                            tracks.clear()
                            tracks.addAll(newTracks)
                        }
    
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        withContext(Dispatchers.Main) { isLoading = false }
                    }
                }
            }
        }
    
        fun loadMore() {
            if (isLoadingMore) return
            viewModelScope.launch {
                isLoadingMore = true
                withContext(Dispatchers.IO) {
                    try {
                        val query = tracks.randomOrNull()?.title ?: "music"
                        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
    
                        val newTracks = result?.items?.filterIsInstance<SongItem>()?.map { item ->
                            Track(
                                id = item.id.hashCode().toLong(),
                                title = item.title,
                                user = User(0L, item.artists.firstOrNull()?.name ?: "YouTube", null),
                                artworkUrl = item.thumbnail,
                                durationMs = (item.duration ?: 0) * 1000L,
                                permalinkUrl = "https://youtube.com/watch?v=${item.id}",
                                source = "youtube"
                            )
                        } ?: emptyList()
    
                        withContext(Dispatchers.Main) {
                            val existingIds = tracks.map { it.id }.toSet()
                            tracks.addAll(newTracks.filter { !existingIds.contains(it.id) })
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        withContext(Dispatchers.Main) { isLoadingMore = false }
                    }
                }
            }
        }
    }


