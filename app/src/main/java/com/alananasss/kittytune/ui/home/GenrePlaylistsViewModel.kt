    package com.alananasss.kittytune.ui.home
    
    import android.app.Application
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Playlist
    import kotlinx.coroutines.launch
    
    class GenrePlaylistsViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)
    
        val playlists = mutableStateListOf<Playlist>()
        var isLoading by mutableStateOf(true)
        var title by mutableStateOf("")
    
        private var nextHref: String? = null
        var isLoadingMore by mutableStateOf(false)
            private set
    
        fun loadGenre(displayTitle: String, query: String) {
            title = displayTitle
            isLoading = true
            playlists.clear()
            nextHref = null // reset state for fresh search
    
            viewModelScope.launch {
                try {
                    val response = api.searchPlaylists(query, limit = 50) // initial batch limit
                    playlists.addAll(response.collection)
                    nextHref = response.next_href // save next page url
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
            }
        }
    
        // load next page of results
        fun loadMore() {
            // prevent duplicate requests or if end reached
            if (isLoadingMore || nextHref == null) return
    
            viewModelScope.launch {
                isLoadingMore = true
                try {
                    val response = api.getSearchPlaylistsNextPage(nextHref!!)
                    playlists.addAll(response.collection)
                    nextHref = response.next_href // update pointer
                } catch (e: Exception) {
                    e.printStackTrace()
                    nextHref = null // stop on error
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }


