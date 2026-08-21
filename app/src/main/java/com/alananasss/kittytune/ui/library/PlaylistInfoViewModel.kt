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

    val likers = mutableStateListOf<User>()
    val reposters = mutableStateListOf<User>()

    private var likersNextUrl: String? = null
    private var repostersNextUrl: String? = null

    var isLikersLoadingMore by mutableStateOf(false)
    var isRepostersLoadingMore by mutableStateOf(false)

    private var currentPlaylistIdStr: String = ""

    fun loadPlaylistDetails(playlistIdStr: String) {
        val isSpotify = playlistIdStr.startsWith("spotify:playlist:") ||
                playlistIdStr.startsWith("spotify:album:") ||
                playlistIdStr.startsWith("spotify_playlist:") ||
                playlistIdStr.startsWith("spotify_album:")

        val isSystemPlaylist = playlistIdStr.startsWith("system_playlist:")
        val systemPlaylistUrn = playlistIdStr.removePrefix("system_playlist:")
        val playlistId = playlistIdStr.toLongOrNull() ?: 0L

        if (!isSpotify && !isSystemPlaylist && playlistId <= 0) {
            isLoading = false; return
        }
        if (currentPlaylistIdStr == playlistIdStr && (likers.isNotEmpty() || reposters.isNotEmpty()) && playlistDetails != null) return

        currentPlaylistIdStr = playlistIdStr

        viewModelScope.launch {
            isLoading = true
            likers.clear(); likersNextUrl = null
            reposters.clear(); repostersNextUrl = null
            playlistDetails = null

            if (isSpotify) {
                try {
                    val cleanId = playlistIdStr
                        .removePrefix("spotify:playlist:")
                        .removePrefix("spotify:album:")
                        .removePrefix("spotify_playlist:")
                        .removePrefix("spotify_album:")
                    val isAlbum = playlistIdStr.contains("album")
                    val p = if (isAlbum) {
                        com.alananasss.kittytune.data.spotify.SpotifyRepository.getAlbum(cleanId)?.toPlaylist()
                    } else {
                        com.alananasss.kittytune.data.spotify.SpotifyRepository.getPlaylist(cleanId)?.toPlaylist()
                    }
                    playlistDetails = p
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isLoading = false
                }
                return@launch
            }

            try {
                coroutineScope {
                    val playlistDef = async {
                        try {
                            if (isSystemPlaylist) api.getSystemPlaylist(systemPlaylistUrn)
                            else api.getPlaylist(playlistId)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val likersResponseDef = async {
                        if (isSystemPlaylist) null else try {
                            api.getPlaylistLikers(playlistId, limit = 50)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    val repostersResponseDef = async {
                        if (isSystemPlaylist) null else try {
                            api.getPlaylistReposters(playlistId, limit = 50)
                        } catch (e: Exception) {
                            null
                        }
                    }

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

