package com.alananasss.kittytune.ui.track

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.*
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class TrackDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)

    var track by mutableStateOf<Track?>(null)
    var isLoading by mutableStateOf(true)

    // data holders
    val likers = mutableStateListOf<User>()
    val reposters = mutableStateListOf<User>()
    val inPlaylists = mutableStateListOf<Playlist>()
    val relatedTracks = mutableStateListOf<Track>()

    // pagination cursors (next_href)
    private var likersNextUrl: String? = null
    private var repostersNextUrl: String? = null
    private var playlistsNextUrl: String? = null
    private var relatedNextUrl: String? = null

    // individual loading states for infinite scroll
    var isLikersLoadingMore by mutableStateOf(false)
    var isRepostersLoadingMore by mutableStateOf(false)
    var isPlaylistsLoadingMore by mutableStateOf(false)
    var isRelatedLoadingMore by mutableStateOf(false)

    fun loadTrackDetails(trackId: Long) {
        if (trackId == 0L) return
        viewModelScope.launch {
            isLoading = true
            // clean slate
            likers.clear(); likersNextUrl = null
            reposters.clear(); repostersNextUrl = null
            inPlaylists.clear(); playlistsNextUrl = null
            relatedTracks.clear(); relatedNextUrl = null

            try {
                coroutineScope {
                    val trackDef = async { api.getTracksByIds(trackId.toString()).firstOrNull() }

                    // fetch full objects (not just collection) to get next_href
                    val likersResponseDef = async { try { api.getTrackLikers(trackId) } catch (e: Exception) { null } }
                    val repostersResponseDef = async { try { api.getTrackReposters(trackId) } catch (e: Exception) { null } }
                    val playlistsResponseDef = async { try { api.getTrackInPlaylists(trackId) } catch (e: Exception) { null } }
                    val relatedResponseDef = async { try { api.getRelatedTracks(trackId) } catch (e: Exception) { null } }

                    track = trackDef.await()

                    likersResponseDef.await()?.let {
                        likers.addAll(it.collection)
                        likersNextUrl = it.next_href
                    }
                    repostersResponseDef.await()?.let {
                        reposters.addAll(it.collection)
                        repostersNextUrl = it.next_href
                    }
                    playlistsResponseDef.await()?.let {
                        inPlaylists.addAll(it.collection)
                        playlistsNextUrl = it.next_href
                    }
                    relatedResponseDef.await()?.let {
                        relatedTracks.addAll(it.collection)
                        relatedNextUrl = it.next_href
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    // --- load more functions ---

    fun loadMoreLikers() {
        if (isLikersLoadingMore || likersNextUrl == null) return
        viewModelScope.launch {
            isLikersLoadingMore = true
            try {
                val res = api.getLikersNextPage(likersNextUrl!!)
                likers.addAll(res.collection)
                likersNextUrl = res.next_href
            } catch (e: Exception) { e.printStackTrace() }
            finally { isLikersLoadingMore = false }
        }
    }

    fun loadMoreReposters() {
        if (isRepostersLoadingMore || repostersNextUrl == null) return
        viewModelScope.launch {
            isRepostersLoadingMore = true
            try {
                val res = api.getRepostersNextPage(repostersNextUrl!!)
                reposters.addAll(res.collection)
                repostersNextUrl = res.next_href
            } catch (e: Exception) { e.printStackTrace() }
            finally { isRepostersLoadingMore = false }
        }
    }

    fun loadMorePlaylists() {
        if (isPlaylistsLoadingMore || playlistsNextUrl == null) return
        viewModelScope.launch {
            isPlaylistsLoadingMore = true
            try {
                val res = api.getInPlaylistsNextPage(playlistsNextUrl!!)
                inPlaylists.addAll(res.collection)
                playlistsNextUrl = res.next_href
            } catch (e: Exception) { e.printStackTrace() }
            finally { isPlaylistsLoadingMore = false }
        }
    }

    fun loadMoreRelated() {
        if (isRelatedLoadingMore || relatedNextUrl == null) return
        viewModelScope.launch {
            isRelatedLoadingMore = true
            try {
                val res = api.getRelatedTracksNextPage(relatedNextUrl!!)
                relatedTracks.addAll(res.collection)
                relatedNextUrl = res.next_href
            } catch (e: Exception) { e.printStackTrace() }
            finally { isRelatedLoadingMore = false }
        }
    }
}