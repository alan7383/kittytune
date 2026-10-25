package com.alananasss.kittytune.ui.home

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.CategoryKind
import com.alananasss.kittytune.data.GenreData
import com.alananasss.kittytune.data.OfficialPlaylistsData
import com.alananasss.kittytune.data.SearchCategory
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.JsonObject
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

enum class CategoryTab(val titleRes: Int) {
    ALL(R.string.category_tab_all),
    TRENDING(R.string.category_tab_trending),
    PLAYLISTS(R.string.category_tab_playlists),
    ALBUMS(R.string.category_tab_albums)
}

class GenreDetailViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)
    private val gson = com.alananasss.kittytune.utils.AppUtils.gson

    var isLoading by mutableStateOf(true)
    var genreTitle by mutableStateOf("")
    var genreQuery by mutableStateOf("")
    var category by mutableStateOf<SearchCategory?>(null)
    var categoryKind by mutableStateOf(CategoryKind.GENRE)

    var tabs by mutableStateOf<List<CategoryTab>>(
        listOf(CategoryTab.ALL, CategoryTab.TRENDING, CategoryTab.PLAYLISTS, CategoryTab.ALBUMS)
    )
    var selectedTab by mutableStateOf(CategoryTab.ALL)

    val popularTracks = mutableStateListOf<Track>()
    val vibeTracks = mutableStateListOf<Track>()
    val officialPlaylists = mutableStateListOf<Playlist>()
    val communityPlaylists = mutableStateListOf<Playlist>()
    val albums = mutableStateListOf<Playlist>()
    val artists = mutableStateListOf<User>()
    var introducingPlaylist by mutableStateOf<Playlist?>(null)
    val introducingTracks = mutableStateListOf<Track>()
    val discoverMoreTracks = mutableStateListOf<Track>()

    var selectedSourceIndex by mutableStateOf(0)

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

    fun selectTab(tab: CategoryTab) {
        selectedTab = tab
    }

    fun loadData(name: String, query: String) {
        val decodedName = try { android.net.Uri.decode(name) } catch (e: Exception) { name }
        val decodedQuery = try { android.net.Uri.decode(query) } catch (e: Exception) { query }
        genreTitle = decodedName
        genreQuery = decodedQuery

        val app = getApplication<Application>()
        val cat = GenreData.findCategory(decodedName, decodedQuery, app)
        category = cat

        val kind = cat?.kind ?: if (GenreData.isMoodQuery(decodedQuery, decodedName)) CategoryKind.MOOD else CategoryKind.GENRE
        categoryKind = kind

        tabs = if (kind == CategoryKind.GENRE) {
            listOf(CategoryTab.ALL, CategoryTab.TRENDING, CategoryTab.PLAYLISTS, CategoryTab.ALBUMS)
        } else {
            listOf(CategoryTab.ALL, CategoryTab.PLAYLISTS)
        }
        if (selectedTab !in tabs) {
            selectedTab = CategoryTab.ALL
        }

        val effectiveQuery = cat?.query?.ifBlank { null } ?: decodedQuery

        viewModelScope.launch {
            isLoading = true
            popularTracks.clear()
            vibeTracks.clear()
            officialPlaylists.clear()
            communityPlaylists.clear()
            albums.clear()
            artists.clear()
            introducingPlaylist = null
            introducingTracks.clear()
            discoverMoreTracks.clear()

            // Try authenticated SDUI request if token is present
            var sduiSucceeded = false
            val tokenManager = TokenManager(app)
            val hasAuth = !tokenManager.getAccessToken().isNullOrEmpty() ||
                    !SessionManager.harvestStoredSession(app).isNullOrEmpty()

            if (hasAuth) {
                try {
                    val sduiJson = api.getSearchCategoryPage(query = effectiveQuery)
                    sduiSucceeded = parseSduiCategoryResponse(sduiJson)

                    // Fetch full curated playlists section via resolver (matching official SoundCloud mobile app)
                    try {
                        val playlistsJson = api.getSearchCategoryPage(
                            query = effectiveQuery,
                            resolver = "soundcloud:sections:search_curated_playlists"
                        )
                        val playlistDetails = GenreData.parseSduiCategoryDetails(playlistsJson)
                        if (playlistDetails.curatedPlaylists.isNotEmpty()) {
                            officialPlaylists.clear()
                            officialPlaylists.addAll(playlistDetails.curatedPlaylists)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    // Fetch full albums section via resolver for genres
                    if (categoryKind == CategoryKind.GENRE) {
                        try {
                            val albumsJson = api.getSearchCategoryPage(
                                query = effectiveQuery,
                                resolver = "soundcloud:sections:vibe_albums"
                            )
                            val albumDetails = GenreData.parseSduiCategoryDetails(albumsJson)
                            if (albumDetails.albums.isNotEmpty()) {
                                albums.clear()
                                albums.addAll(albumDetails.albums)
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    sduiSucceeded = false
                }
            }

            // Fallback / guest flow (only if SDUI completely failed to load)
            if (!sduiSucceeded) {
                try {
                    coroutineScope {
                        val isMood = categoryKind == CategoryKind.MOOD
                        if (isMood) {
                            val playlistsJob = async {
                                try {
                                    api.searchPlaylists(query = effectiveQuery, limit = 30).collection
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }
                            val fetchedPlaylists = playlistsJob.await()
                            if (communityPlaylists.isEmpty()) {
                                val realPlaylists = fetchedPlaylists.filter { !it.isRealAlbum }.distinctBy { it.id }
                                communityPlaylists.addAll(realPlaylists)
                            }
                        } else {
                            // Genre fallback: tracks, playlists, albums, artists
                            val tracksJob = async {
                                val sysUrn = cat?.systemPlaylistUrn
                                if (!sysUrn.isNullOrBlank()) {
                                    try {
                                        val sysPlaylist = api.getSystemPlaylist(sysUrn)
                                        val trackIds = sysPlaylist.tracks?.map { it.id }?.filter { it > 0 } ?: emptyList()
                                        if (trackIds.isNotEmpty()) {
                                            val resolved = api.getTracksByIds(trackIds.take(50).joinToString(","))
                                            if (resolved.isNotEmpty()) {
                                                val idToIndex = trackIds.withIndex().associate { it.value to it.index }
                                                return@async resolved.sortedBy { idToIndex[it.id] ?: Int.MAX_VALUE }
                                            }
                                        }
                                    } catch (_: Exception) {}
                                }
                                try {
                                    api.searchTracks(query = effectiveQuery, limit = 50).collection
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }

                            val playlistsJob = async {
                                try {
                                    api.searchPlaylists(query = effectiveQuery, limit = 30).collection
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }

                            val albumsJob = async {
                                try {
                                    api.searchAlbums(query = effectiveQuery, limit = 30).collection
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }

                            val artistsJob = async {
                                try {
                                    api.searchUsers(query = effectiveQuery, limit = 20).collection
                                } catch (_: Exception) {
                                    emptyList()
                                }
                            }

                            val fetchedTracks = tracksJob.await()
                            val fetchedPlaylists = playlistsJob.await()
                            val fetchedAlbums = albumsJob.await()
                            val fetchedArtists = artistsJob.await()

                            if (popularTracks.isEmpty()) {
                                popularTracks.addAll(fetchedTracks)
                            }
                            if (communityPlaylists.isEmpty()) {
                                communityPlaylists.addAll(fetchedPlaylists.filter { !it.isRealAlbum }.distinctBy { it.id })
                            }
                            if (albums.isEmpty()) {
                                albums.addAll((fetchedAlbums + fetchedPlaylists.filter { it.isRealAlbum }).distinctBy { it.id })
                            }
                            if (artists.isEmpty()) {
                                artists.addAll(fetchedArtists.distinctBy { it.id })
                            }
                            if (vibeTracks.isEmpty() && popularTracks.isNotEmpty()) {
                                vibeTracks.addAll(popularTracks.take(15))
                            }
                            if (discoverMoreTracks.isEmpty() && popularTracks.size > 6) {
                                discoverMoreTracks.addAll(popularTracks.drop(6).take(25))
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Only load fallback regional playlists if official curated playlists are empty and kind is GENRE
            if (officialPlaylists.isEmpty() && categoryKind == CategoryKind.GENRE) {
                loadOfficialPlaylists()
            }
            isLoading = false
        }
    }

    private fun parseSduiCategoryResponse(sduiJson: JsonObject): Boolean {
        return try {
            val details = GenreData.parseSduiCategoryDetails(sduiJson)
            var foundAny = false

            if (details.filterChoices.isNotEmpty()) {
                val mappedTabs = details.filterChoices.mapNotNull { key ->
                    when (key.lowercase()) {
                        "all" -> CategoryTab.ALL
                        "trending" -> CategoryTab.TRENDING
                        "playlists" -> CategoryTab.PLAYLISTS
                        "albums" -> CategoryTab.ALBUMS
                        else -> null
                    }
                }
                if (mappedTabs.isNotEmpty()) {
                    tabs = mappedTabs
                    if (selectedTab !in tabs) {
                        selectedTab = CategoryTab.ALL
                    }
                }
                if (details.filterChoices.size == 2 && details.filterChoices.contains("all") && details.filterChoices.contains("playlists") && details.vibeTracks.isEmpty()) {
                    categoryKind = CategoryKind.MOOD
                }
            }

            if (details.tracks.isNotEmpty()) {
                popularTracks.clear()
                popularTracks.addAll(details.tracks)
                foundAny = true
            }
            if (details.vibeTracks.isNotEmpty()) {
                vibeTracks.clear()
                vibeTracks.addAll(details.vibeTracks)
                foundAny = true
            }
            if (details.curatedPlaylists.isNotEmpty()) {
                officialPlaylists.clear()
                officialPlaylists.addAll(details.curatedPlaylists)
                foundAny = true
            }
            if (details.communityPlaylists.isNotEmpty()) {
                communityPlaylists.clear()
                communityPlaylists.addAll(details.communityPlaylists)
                foundAny = true
            }
            if (details.albums.isNotEmpty()) {
                albums.clear()
                albums.addAll(details.albums)
                foundAny = true
            }
            if (details.artists.isNotEmpty()) {
                artists.clear()
                artists.addAll(details.artists)
                foundAny = true
            }
            if (details.introducingPlaylist != null) {
                introducingPlaylist = details.introducingPlaylist
                foundAny = true
            }
            if (details.introducingTracks.isNotEmpty()) {
                introducingTracks.clear()
                introducingTracks.addAll(details.introducingTracks)
                foundAny = true
            }
            if (details.discoverMoreTracks.isNotEmpty()) {
                discoverMoreTracks.clear()
                discoverMoreTracks.addAll(details.discoverMoreTracks)
                foundAny = true
            }
            if (genreTitle.isBlank() && !details.title.isNullOrBlank()) {
                genreTitle = details.title
            }
            if (category == null && (!details.headerDarkArtwork.isNullOrBlank() || !details.headerLightArtwork.isNullOrBlank())) {
                category = SearchCategory(
                    id = genreQuery.lowercase(),
                    title = genreTitle,
                    query = genreQuery,
                    artworkUrl = details.headerDarkArtwork ?: details.headerLightArtwork,
                    artworkUrlLight = details.headerLightArtwork ?: details.headerDarkArtwork,
                    borderColor = "#FF5500",
                    kind = categoryKind
                )
            }
            foundAny
        } catch (e: Exception) {
            false
        }
    }

    fun loadOfficialPlaylists() {
        if (genreQuery.isBlank()) return

        viewModelScope.launch {
            try {
                val source = OfficialPlaylistsData.sources.getOrNull(selectedSourceIndex)
                    ?: return@launch
                val userUrl = "https://soundcloud.com/${source.soundCloudUsername}"
                val resolvedUserJson = api.resolveUrl(userUrl)
                val user = gson.fromJson(resolvedUserJson, User::class.java)
                if (user.id != 0L) {
                    val userPlaylistsResponse = api.getUserCreatedPlaylists(userId = user.id, limit = 200)
                    val catQuery = category?.query
                    val filteredPlaylists = userPlaylistsResponse.collection.filter { playlist ->
                        val title = playlist.title ?: ""
                        title.contains(genreQuery, ignoreCase = true) ||
                                (genreTitle.isNotBlank() && title.contains(genreTitle, ignoreCase = true)) ||
                                (!catQuery.isNullOrBlank() && title.contains(catQuery, ignoreCase = true))
                    }
                    if (filteredPlaylists.isNotEmpty() || officialPlaylists.isEmpty()) {
                        officialPlaylists.clear()
                        officialPlaylists.addAll(filteredPlaylists)
                    }
                }
            } catch (e: Exception) {
                // Ignore regional playlist load errors
            }
        }
    }
}
