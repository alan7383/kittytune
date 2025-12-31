package com.alananasss.kittytune.ui.home

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.HistoryRepository
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ui models
data class HomeSection(
    val title: String,
    val subtitle: String? = null,
    val content: List<Any>,
    val type: SectionType
)

enum class SectionType {
    TRACKS_ROW, ARTISTS_ROW, STATIONS_ROW
}

// cache stuff
data class HomeCacheData(
    val user: User?,
    val sections: List<HomeSectionCache>
)

data class HomeSectionCache(
    val title: String,
    val subtitle: String?,
    val type: SectionType,
    val tracks: List<Track> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val users: List<User> = emptyList()
)

// search modes
enum class SearchFilter {
    ALL, TRACKS, ARTISTS, PLAYLISTS
}

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val api = RetrofitClient.create(application)
    private val prefs = application.getSharedPreferences("home_cache", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val tokenManager = TokenManager(application)

    // helper to get strings easily
    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)
    private fun getString(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)

    var userProfile by mutableStateOf<User?>(null)

    // main home data
    val homeSections = mutableStateListOf<HomeSection>()
    val historyFlow = HistoryRepository.getHistory()

    // --- search state ---
    var isSearching by mutableStateOf(false)
    var searchQuery by mutableStateOf("")
    var activeFilter by mutableStateOf(SearchFilter.ALL)
    var isSearchLoading by mutableStateOf(false)

    // search results
    val searchResultsTracks = mutableStateListOf<Track>()
    val searchResultsArtists = mutableStateListOf<User>()
    val searchResultsPlaylists = mutableStateListOf<Playlist>()

    private var searchJob: Job? = null

    init {
        loadFromCache()
        loadData()
    }

    // --- search logic ---
    fun onSearchQueryChanged(query: String) {
        searchQuery = query
        if (query.isBlank()) { clearSearchResults(); return }
        searchJob?.cancel()
        // debounce logic
        searchJob = viewModelScope.launch { delay(500); performSearch(query) }
    }
    fun activateSearch() { isSearching = true }
    fun clearSearch() { searchQuery = ""; isSearching = false; clearSearchResults() }
    fun onFilterChanged(filter: SearchFilter) { activeFilter = filter; if (searchQuery.isNotBlank()) { searchJob?.cancel(); searchJob = viewModelScope.launch { performSearch(searchQuery) } } }
    private fun clearSearchResults() { searchResultsTracks.clear(); searchResultsArtists.clear(); searchResultsPlaylists.clear() }

    private suspend fun performSearch(query: String) {
        isSearchLoading = true; clearSearchResults()
        try {
            coroutineScope {
                when (activeFilter) {
                    SearchFilter.ALL -> {
                        // parallel requests for all types
                        val tracksDef = async { try { api.searchTracks(query, limit = 5).collection } catch (e: Exception) { emptyList() } }
                        val usersDef = async { try { api.searchUsers(query, limit = 5).collection } catch (e: Exception) { emptyList() } }
                        val playlistsDef = async { try { api.searchPlaylists(query, limit = 5).collection } catch (e: Exception) { emptyList() } }
                        searchResultsTracks.addAll(tracksDef.await())
                        searchResultsArtists.addAll(usersDef.await())
                        searchResultsPlaylists.addAll(playlistsDef.await())
                    }
                    SearchFilter.TRACKS -> searchResultsTracks.addAll(api.searchTracks(query, limit = 30).collection)
                    SearchFilter.ARTISTS -> searchResultsArtists.addAll(api.searchUsers(query, limit = 30).collection)
                    SearchFilter.PLAYLISTS -> searchResultsPlaylists.addAll(api.searchPlaylists(query, limit = 30).collection)
                }
            }
        } catch (e: Exception) { e.printStackTrace() } finally { isSearchLoading = false }
    }

    // --- cache handling ---
    private fun loadFromCache() {
        try {
            val json = prefs.getString("cached_home_data", null)
            if (json != null) {
                val data: HomeCacheData = gson.fromJson(json, object : TypeToken<HomeCacheData>() {}.type)
                userProfile = data.user
                if (data.sections.isNotEmpty()) {
                    homeSections.clear()
                    // rebuild section objects from cache
                    data.sections.forEach { section ->
                        val content: List<Any> = when (section.type) {
                            SectionType.TRACKS_ROW -> section.tracks
                            SectionType.STATIONS_ROW -> section.playlists
                            SectionType.ARTISTS_ROW -> section.users
                        }
                        if (content.isNotEmpty()) homeSections.add(HomeSection(section.title, section.subtitle, content, section.type))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun saveToCache() {
        viewModelScope.launch {
            try {
                // flatten everything for gson
                val sectionsCache = homeSections.map { section -> HomeSectionCache(section.title, section.subtitle, section.type, section.content.filterIsInstance<Track>(), section.content.filterIsInstance<Playlist>(), section.content.filterIsInstance<User>()) }
                val data = HomeCacheData(userProfile, sectionsCache)
                prefs.edit().putString("cached_home_data", gson.toJson(data)).apply()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- loading stuff ---
    fun loadData() {
        viewModelScope.launch {
            val token = tokenManager.getAccessToken()
            // check if we are logged in or just a guest
            if (token.isNullOrEmpty()) loadGuestData() else loadAuthenticatedData()
        }
    }

    // --- smart recommendations based on history ---
    private suspend fun fetchHistoryBasedSection(): HomeSection? {
        return try {
            val history = HistoryRepository.getHistory().first()
            val recentTracks = history.filter { it.type == "TRACK" }.take(10)
            if (recentTracks.isEmpty()) return null

            // pick a random recent track and find similar stuff
            val seedItem = recentTracks.random()
            val related = api.getRelatedTracks(seedItem.numericId, limit = 15).collection

            if (related.isNotEmpty()) {
                HomeSection(
                    title = getString(R.string.home_section_similar, seedItem.title),
                    subtitle = getString(R.string.home_section_similar_sub),
                    content = related,
                    type = SectionType.TRACKS_ROW
                )
            } else null
        } catch (e: Exception) { null }
    }

    private suspend fun fetchPersonalizedSections(sourceTracks: List<Track>, username: String): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        if (sourceTracks.isEmpty()) return sections

        try {
            coroutineScope {
                // fake radio stations
                val stationSeeds = sourceTracks.shuffled().take(6)
                val stationItems = stationSeeds.map { track -> Playlist(track.id, "Station: ${track.title}", track.fullResArtwork, null, 0, track.user, null) }
                if (stationItems.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_section_similar, username), null, stationItems, SectionType.STATIONS_ROW))

                // more tracks you might like
                val seed1 = sourceTracks.take(10).randomOrNull() ?: sourceTracks.first()
                val relatedDef1 = async { try { api.getRelatedTracks(seed1.id, limit = 15).collection } catch (e: Exception) { emptyList() } }

                // discovery artists
                val newCrewDef = async {
                    val artists = sourceTracks.mapNotNull { it.user }.distinctBy { it.id }.shuffled().take(8)
                    val similarArtists = try {
                        val randomLike = sourceTracks.shuffled().first()
                        api.getRelatedTracks(randomLike.id, limit=10).collection.mapNotNull { it.user }
                    } catch(e:Exception) { emptyList() }
                    (artists + similarArtists).distinctBy { it.id }.shuffled().take(10)
                }

                val related1 = relatedDef1.await()
                if (related1.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_section_similar, seed1.title ?: ""), getString(R.string.home_section_similar_sub), related1, SectionType.TRACKS_ROW))

                val newCrew = newCrewDef.await()
                if (newCrew.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_section_new_crew), getString(R.string.home_section_new_crew_sub), newCrew, SectionType.ARTISTS_ROW))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sections
    }

    private suspend fun loadGuestData() {
        try {
            userProfile = null
            val localLikes = LikeRepository.likedTracks.value
            val allSections = mutableListOf<HomeSection>()

            coroutineScope {
                val genericSectionsDef = async { fetchGenericGuestSections() }
                val personalSectionsDef = async {
                    // if they liked stuff locally, we can still personalize
                    if (localLikes.isNotEmpty()) fetchPersonalizedSections(localLikes, getString(R.string.guest_user)) else emptyList()
                }
                val historySectionDef = async { fetchHistoryBasedSection() }

                val genericSections = genericSectionsDef.await()
                val personalSections = personalSectionsDef.await()
                val historySection = historySectionDef.await()

                if (historySection != null) allSections.add(historySection)
                allSections.addAll(personalSections)
                allSections.addAll(genericSections)
            }

            if (allSections.isNotEmpty()) {
                homeSections.clear(); homeSections.addAll(allSections); saveToCache()
            } else {
                // retry if failed first time
                delay(2000)
                if (homeSections.isEmpty()) loadGuestData()
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    private suspend fun fetchGenericGuestSections(): List<HomeSection> {
        val sections = mutableListOf<HomeSection>()
        try {
            // standard soundcloud charts
            coroutineScope {
                val trendingDef = async { try { api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music").collection.mapNotNull { it.track } } catch(e:Exception){ emptyList() } }
                val globalChartsDef = async { try { api.searchPlaylists("Billboard Hot 100", limit = 10).collection } catch(e:Exception){ emptyList() } }
                val hiphopDef = async { try { api.getCharts(kind = "trending", genre = "soundcloud:genres:hiphoprap").collection.mapNotNull { it.track } } catch(e:Exception){ emptyList() } }
                val electroDef = async { try { api.searchPlaylists("Electro House 2025", limit = 10).collection } catch(e:Exception){ emptyList() } }
                val artistsDef = async { try { val l1 = api.searchUsers("Billboard", limit = 5).collection; val l2 = api.searchUsers("Official Music", limit = 5).collection; (l1+l2).distinctBy{it.id}.shuffled() } catch(e:Exception){ emptyList() } }
                val popDef = async { try { api.getCharts(kind = "trending", genre = "soundcloud:genres:pop").collection.mapNotNull { it.track } } catch(e:Exception){ emptyList() } }

                val trending = trendingDef.await()
                if (trending.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_trending), null, trending, SectionType.TRACKS_ROW))
                val globalCharts = globalChartsDef.await()
                if (globalCharts.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_charts), null, globalCharts, SectionType.STATIONS_ROW))
                val hiphop = hiphopDef.await()
                if (hiphop.isNotEmpty()) sections.add(HomeSection("Hip-Hop & Rap US", null, hiphop, SectionType.TRACKS_ROW))
                val techno = electroDef.await()
                if (techno.isNotEmpty()) sections.add(HomeSection("Electro House", null, techno, SectionType.STATIONS_ROW))
                val artists = artistsDef.await()
                if (artists.isNotEmpty()) sections.add(HomeSection(getString(R.string.lib_artists), null, artists, SectionType.ARTISTS_ROW))
                val pop = popDef.await()
                if (pop.isNotEmpty()) sections.add(HomeSection("Pop", null, pop, SectionType.TRACKS_ROW))
            }
        } catch (e: Exception) { e.printStackTrace() }
        return sections
    }

    private suspend fun loadAuthenticatedData() {
        try {
            val me = api.getMe()
            userProfile = me
            val allSections = mutableListOf<HomeSection>()

            coroutineScope {
                // get the user stream (feed)
                val streamDef = async {
                    try {
                        api.getMyStream(limit = 20).collection
                            .filter { it.type == "track" || it.type == "track-repost" }
                            .mapNotNull { it.track }
                            .distinctBy { it.id }
                    } catch (e: Exception) { emptyList() }
                }

                // get likes from api or local if plenty
                val localLikes = LikeRepository.likedTracks.value
                val sourceLikes = if (localLikes.size > 20) localLikes else {
                    try { api.getUserTrackLikes(me.id, limit = 50).collection.map { it.track } } catch(e:Exception) { emptyList() }
                }

                val historySectionDef = async { fetchHistoryBasedSection() }

                val streamTracks = streamDef.await()
                if (streamTracks.isNotEmpty()) {
                    allSections.add(HomeSection("Stream", null, streamTracks, SectionType.TRACKS_ROW))
                }

                val historySection = historySectionDef.await()
                if (historySection != null) allSections.add(historySection)

                if (sourceLikes.isNotEmpty()) {
                    val personalSections = fetchPersonalizedSections(sourceLikes, me.username ?: "User")
                    allSections.addAll(personalSections)
                }
            }

            if (allSections.isNotEmpty()) {
                homeSections.clear()
                homeSections.addAll(allSections)
                saveToCache()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}