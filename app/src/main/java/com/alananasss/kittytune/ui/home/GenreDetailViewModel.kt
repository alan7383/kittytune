package com.alananasss.kittytune.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.data.OfficialPlaylistsData
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.Gson
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

class GenreDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)
    private val gson = com.alananasss.kittytune.utils.AppUtils.gson

    var isLoading by mutableStateOf(true)
    var genreTitle by mutableStateOf("")

    val popularTracks = mutableStateListOf<Track>()
    val officialPlaylists = mutableStateListOf<Playlist>()
    val communityPlaylists = mutableStateListOf<Playlist>()
    val albums = mutableStateListOf<Playlist>()
    var selectedSourceIndex by mutableStateOf(0)
    private var currentGenreQuery by mutableStateOf("")

    init {
        autodetectCountry()
    }

    private fun autodetectCountry() {
        val appLang = com.alananasss.kittytune.data.local.PlayerPreferences(getApplication()).getAppLanguage()
        val langCountryCode = when (appLang) {
            com.alananasss.kittytune.data.local.AppLanguage.FRENCH -> "fr"
            com.alananasss.kittytune.data.local.AppLanguage.ENGLISH -> "us"
            else -> Locale.getDefault().country.lowercase(Locale.ROOT)
        }
        val matchedIndex = OfficialPlaylistsData.sources.indexOfFirst {
            val sourceCode = it.soundCloudUsername.substringAfter("sc-playlists-").lowercase(Locale.ROOT)
            sourceCode == langCountryCode || (langCountryCode == "gb" && sourceCode == "uk")
        }
        if (matchedIndex != -1) {
            selectedSourceIndex = matchedIndex
        }
    }

    fun loadData(name: String, query: String) {
        genreTitle = name
        currentGenreQuery = query
        viewModelScope.launch {
            isLoading = true
            popularTracks.clear()
            officialPlaylists.clear()
            communityPlaylists.clear()
            albums.clear()

            try {
                coroutineScope {
                    val popularDef = async { api.searchTracks(query = query, limit = 50).collection }
                    val communityDef = async { api.searchPlaylists(query = query, limit = 20).collection }
                    val albumsDef = async { api.searchAlbums(query = query, limit = 20).collection }

                    popularTracks.addAll(popularDef.await())

                    val rawPlaylists = communityDef.await()
                    val rawAlbums = albumsDef.await()

                    val realPlaylists = rawPlaylists.filter { !it.isRealAlbum }
                        .distinctBy { it.id }
                        .take(10)

                    val realAlbums = rawAlbums.filter { it.isRealAlbum }
                        .distinctBy { it.id }
                        .take(10)

                    communityPlaylists.addAll(realPlaylists)
                    albums.addAll(realAlbums)

                    loadOfficialPlaylists()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }

    fun loadOfficialPlaylists() {
        if (currentGenreQuery.isBlank()) return

        viewModelScope.launch {
            if (officialPlaylists.isEmpty()) {
                isLoading = true
            }
            officialPlaylists.clear()

            try {
                val source = OfficialPlaylistsData.sources[selectedSourceIndex]
                val userUrl = "https://soundcloud.com/${source.soundCloudUsername}"
                val resolvedUserJson = api.resolveUrl(userUrl)
                val user = gson.fromJson(resolvedUserJson, User::class.java)
                if (user.id != 0L) {
                    val userPlaylistsResponse = api.getUserCreatedPlaylists(userId = user.id, limit = 200)
                    val filteredPlaylists = userPlaylistsResponse.collection.filter { playlist ->
                        playlist.title?.contains(currentGenreQuery, ignoreCase = true) == true
                    }

                    officialPlaylists.addAll(filteredPlaylists)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    }
}

