    package com.alananasss.kittytune.ui.library
    
    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.User
    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.launch
    
    class PlaylistInfoViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)
    
        var isLoading by mutableStateOf(true)
        var playlistDetails by mutableStateOf<Playlist?>(null)
    
        // data lists
        val likers = mutableStateListOf<User>()
        val reposters = mutableStateListOf<User>()
    
        // pagination (next url)
        private var likersNextUrl: String? = null
        private var repostersNextUrl: String? = null
    
        // loading states for "load more" pagination
        var isLikersLoadingMore by mutableStateOf(false)
        var isRepostersLoadingMore by mutableStateOf(false)
    
        // to avoid reloading if returning to the screen
        private var currentPlaylistId: Long = -1L
    
        fun loadPlaylistDetails(playlistId: Long) {
            if (playlistId <= 0) { isLoading = false; return }
            if (currentPlaylistId == playlistId && (likers.isNotEmpty() || reposters.isNotEmpty()) && playlistDetails != null) return
    
            currentPlaylistId = playlistId
    
            viewModelScope.launch {
                isLoading = true
                likers.clear(); likersNextUrl = null
                reposters.clear(); repostersNextUrl = null
                playlistDetails = null
    
                try {
                    coroutineScope {
                        // fetch full objects to get next_href AND playlist details
                        val playlistDef = async { try { api.getPlaylist(playlistId) } catch(e:Exception){null} }
                        val likersResponseDef = async { try { api.getPlaylistLikers(playlistId, limit = 50) } catch (e: Exception) { null } }
                        val repostersResponseDef = async { try { api.getPlaylistReposters(playlistId, limit = 50) } catch (e: Exception) { null } }
    
                        playlistDetails = playlistDef.await()
                        val likersRes = likersResponseDef.await()
                        val repostersRes = repostersResponseDef.await()
    
                        if (likersRes != null) {
                            likers.addAll(likersRes.collection)
                            likersNextUrl = likersRes.next_href
                        }
    
                        if (repostersRes != null) {
                            reposters.addAll(repostersRes.collection)
                            repostersNextUrl = repostersRes.next_href
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    
        // --- likes pagination ---
        fun loadMoreLikers() {
            if (isLikersLoadingMore || likersNextUrl == null) return
    
            viewModelScope.launch {
                isLikersLoadingMore = true
                try {
                    val response = api.getLikersNextPage(likersNextUrl!!)
                    likers.addAll(response.collection)
                    likersNextUrl = response.next_href
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLikersLoadingMore = false
                }
            }
        }
    
        // --- reposts pagination ---
        fun loadMoreReposters() {
            if (isRepostersLoadingMore || repostersNextUrl == null) return
    
            viewModelScope.launch {
                isRepostersLoadingMore = true
                try {
                    val response = api.getRepostersNextPage(repostersNextUrl!!)
                    reposters.addAll(response.collection)
                    repostersNextUrl = response.next_href
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isRepostersLoadingMore = false
                }
            }
        }
    }


