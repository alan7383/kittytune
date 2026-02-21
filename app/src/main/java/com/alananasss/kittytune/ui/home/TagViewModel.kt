    package com.alananasss.kittytune.ui.home
    
    import android.app.Application
    import androidx.compose.runtime.getValue
    import androidx.compose.runtime.mutableIntStateOf
    import androidx.compose.runtime.mutableStateListOf
    import androidx.compose.runtime.mutableStateOf
    import androidx.compose.runtime.setValue
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Track
    import kotlinx.coroutines.launch
    
    class TagViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)
    
        var selectedTabIndex by mutableIntStateOf(0)
    
        val popularTracks = mutableStateListOf<Track>()
        val recentTracks = mutableStateListOf<Track>()
    
        var uiState by mutableStateOf("LOADING")
    
        private var popularNextUrl: String? = null
        private var recentNextUrl: String? = null
        private var isLoadingMore = false
    
        private var currentTagName: String = ""
    
        fun loadTag(tagName: String) {
            currentTagName = tagName
            loadDataForTab(selectedTabIndex)
        }
    
        fun onTabSelected(index: Int) {
            selectedTabIndex = index
            if (index == 0 && popularTracks.isEmpty()) {
                loadDataForTab(0)
            } else if (index == 1 && recentTracks.isEmpty()) {
                loadDataForTab(1)
            } else {
                uiState = "SUCCESS"
            }
        }
    
        private fun loadDataForTab(tabIndex: Int) {
            if (currentTagName.isBlank()) return
    
            uiState = "LOADING"
            val cleanTag = currentTagName.replace("#", "").trim()
    
            viewModelScope.launch {
                try {
                    if (tabIndex == 0) {
                        val result = api.searchTracksPop(
                            query = cleanTag,
                            sort = "popular",
                            limit = 20
                        )
    
                        popularNextUrl = result.next_href
                        popularTracks.clear()
                        popularTracks.addAll(result.collection)
                        uiState = if (popularTracks.isEmpty()) "EMPTY" else "SUCCESS"
    
                    } else {
                        val result = api.getRecentTracksByTag(
                            tag = cleanTag,
                            limit = 20
                        )
                        recentNextUrl = result.next_href
                        recentTracks.clear()
                        recentTracks.addAll(result.collection)
                        uiState = if (recentTracks.isEmpty()) "EMPTY" else "SUCCESS"
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    uiState = "ERROR"
                }
            }
        }
    
        fun loadMore() {
            val nextHref = if (selectedTabIndex == 0) popularNextUrl else recentNextUrl
    
            if (isLoadingMore || nextHref == null) return
    
            isLoadingMore = true
    
            viewModelScope.launch {
                try {
                    val result = api.getSearchTracksNextPage(nextHref)
    
                    if (selectedTabIndex == 0) {
                        popularTracks.addAll(result.collection)
                        popularNextUrl = result.next_href
                    } else {
                        recentTracks.addAll(result.collection)
                        recentNextUrl = result.next_href
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoadingMore = false
                }
            }
        }
    }


