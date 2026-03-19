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
    import com.alananasss.kittytune.data.GenreData
    import com.alananasss.kittytune.data.HistoryRepository
    import com.alananasss.kittytune.data.LikeRepository
    import com.alananasss.kittytune.data.SearchCategory
    import com.alananasss.kittytune.data.TokenManager
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.alananasss.kittytune.data.SessionManager
    import com.google.gson.Gson
    import com.google.gson.reflect.TypeToken
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.flow.MutableSharedFlow
    import kotlinx.coroutines.flow.asSharedFlow
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.async
    import kotlinx.coroutines.coroutineScope
    import kotlinx.coroutines.delay
    import kotlinx.coroutines.flow.first
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext
    import com.zionhuang.innertube.YouTube
    import com.zionhuang.innertube.models.SongItem
    import okhttp3.OkHttpClient
    import okhttp3.Request
    import java.net.URLDecoder
    import java.util.Locale
    import java.util.regex.Pattern
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.ui.graphics.vector.ImageVector
    import kotlinx.coroutines.awaitAll
    import com.zionhuang.innertube.models.WatchEndpoint

    data class HomeSection(
        val title: String,
        val subtitle: String? = null,
        val content: List<Any>,
        val type: SectionType
    )
    
    enum class SectionType {
        TRACKS_ROW, ARTISTS_ROW, STATIONS_ROW, DISCOVERY_ROW
    }
    
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
    
    enum class SearchFilter {
        ALL, TRACKS, ARTISTS, PLAYLISTS
    }
    
    enum class SearchSource {
        SOUNDCLOUD, YOUTUBE
    }
    
    class HomeViewModel(application: Application) : AndroidViewModel(application) {
        private val api = RetrofitClient.create(application)
        private val prefs = application.getSharedPreferences("home_cache", Context.MODE_PRIVATE)
        private val gson = Gson()
        private val tokenManager = TokenManager(application)
    
        private val _navigateTo = MutableSharedFlow<String>()
        val navigateTo = _navigateTo.asSharedFlow()
    
        private val _playTrack = MutableSharedFlow<Track>()
        val playTrack = _playTrack.asSharedFlow()
    
        private fun getString(resId: Int): String = getApplication<Application>().getString(resId)
        private fun getString(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)
    
        var userProfile by mutableStateOf<User?>(null)
    
        val homeSections = mutableStateListOf<HomeSection>()
        val historyFlow = HistoryRepository.getHistory()
    
        var isSearching by mutableStateOf(false)
        var searchQuery by mutableStateOf("")
        var activeFilter by mutableStateOf(SearchFilter.ALL)
        var isSearchLoading by mutableStateOf(false)
        var activeSearchSource by mutableStateOf(SearchSource.SOUNDCLOUD)
    
        var isLoading by mutableStateOf(true)
        var isRefreshing by mutableStateOf(false)
    
        val searchResultsTracks = mutableStateListOf<Track>()
        val searchResultsArtists = mutableStateListOf<User>()
        val searchResultsPlaylists = mutableStateListOf<Playlist>()
        val searchResultsYoutube = mutableStateListOf<Track>()
    
        private var tracksNextUrl: String? = null
        private var artistsNextUrl: String? = null
        private var playlistsNextUrl: String? = null
        var isSearchLoadingMore by mutableStateOf(false)
    
        private var searchJob: Job? = null
        val personalizedCategories = mutableStateListOf<SearchCategory>()
    
        val moodCategories = GenreData.getMoods(application)
        val genreCategories = GenreData.getGenres(application)
    
        init {
            viewModelScope.launch {
                SessionManager.isClientIdValid.collect { isReady ->
                    if (isReady) {
                        loadFromCache()
                        loadData()
                    }
                }
            }
            viewModelScope.launch {
                LikeRepository.likedTracks.collect {
                    generatePersonalizedCategories()
                }
            }
        }

        fun onSearchQueryChanged(query: String) {
            searchQuery = query
            searchJob?.cancel()

            val trimmed = query.trim()

            val isSoundCloudUrl = trimmed.startsWith("https://soundcloud.com") || trimmed.startsWith("https://on.soundcloud.com")
            val isYoutubeUrl = trimmed.contains("youtube.com") || trimmed.contains("youtu.be")

            if (isSoundCloudUrl) {
                handleSoundCloudUrl(trimmed)
            } else if (isYoutubeUrl) {
                handleYoutubeUrl(trimmed)
            } else {
                if (trimmed.isBlank()) {
                    clearSearchResults()
                    return
                }
                searchJob = viewModelScope.launch {
                    delay(500)
                    performSearch(trimmed)
                }
            }
        }

        fun refreshData() {
            if (isRefreshing) return
            viewModelScope.launch {
                isRefreshing = true
                val token = tokenManager.getAccessToken()
                if (token.isNullOrEmpty()) loadGuestData() else loadAuthenticatedData()
                isRefreshing = false
            }
        }

        private suspend fun unshortenUrl(shortUrl: String): String = withContext(Dispatchers.IO) {
            try {
                val client = OkHttpClient.Builder().followRedirects(true).followSslRedirects(true).build()
                val request = Request.Builder().url(shortUrl).head().build()
                val response = client.newCall(request).execute()
                response.request.url.toString()
            } catch (e: Exception) {
                shortUrl
            }
        }
    
        private fun handleSoundCloudUrl(url: String) {
            isSearchLoading = true
            clearSearchResults()
            viewModelScope.launch {
                try {
                    var processedUrl = url
                    if (url.contains("on.soundcloud.com")) {
                        processedUrl = unshortenUrl(url)
                    }
                    val decodedUrl = try { URLDecoder.decode(processedUrl, "UTF-8") } catch (e: Exception) { processedUrl }
                    val stationTrackRegex = Regex("track-stations:(\\d+)")
                    val stationArtistRegex = Regex("artist-stations:(\\d+)")
    
                    stationTrackRegex.find(decodedUrl)?.groupValues?.get(1)?.let { id ->
                        _navigateTo.emit("station:$id"); clearSearch(); isSearchLoading = false; return@launch
                    }
                    stationArtistRegex.find(decodedUrl)?.groupValues?.get(1)?.let { id ->
                        _navigateTo.emit("station_artist:$id"); clearSearch(); isSearchLoading = false; return@launch
                    }
                    val cleanUrl = decodedUrl.substringBefore("?")
                    val resolvedObject = api.resolveUrl(cleanUrl)
                    val kind = resolvedObject.get("kind")?.asString ?: ""
                    when (kind) {
                        "track" -> {
                            val track = gson.fromJson(resolvedObject, Track::class.java); _playTrack.emit(track); clearSearch()
                        }
                        "playlist", "album" -> {
                            val playlist = gson.fromJson(resolvedObject, Playlist::class.java); _navigateTo.emit("playlist_detail/${playlist.id}")
                        }
                        "user" -> {
                            val user = gson.fromJson(resolvedObject, User::class.java); _navigateTo.emit("profile/${user.id}")
                        }
                        "system-playlist" -> {
                            val uri = resolvedObject.get("uri")?.asString ?: ""
                            val trackStationId = stationTrackRegex.find(uri)?.groupValues?.get(1)
                            val artistStationId = stationArtistRegex.find(uri)?.groupValues?.get(1)
                            if (trackStationId != null) {
                                _navigateTo.emit("station:$trackStationId"); clearSearch()
                            } else if (artistStationId != null) {
                                _navigateTo.emit("station_artist:$artistStationId"); clearSearch()
                            } else {
                                performSearch(url)
                            }
                        }
                        else -> performSearch(url)
                    }
                } catch (e: Exception) {
                    e.printStackTrace(); performSearch(url)
                }
            }
        }
    
        fun activateSearch() { isSearching = true }
        fun clearSearch() { searchQuery = ""; isSearching = false; clearSearchResults() }
        fun onFilterChanged(filter: SearchFilter) { activeFilter = filter; if (searchQuery.isNotBlank()) { searchJob?.cancel(); searchJob = viewModelScope.launch { performSearch(searchQuery) } } }
    
        fun onSearchSourceChanged(source: SearchSource) {
            if (activeSearchSource == source) return
            activeSearchSource = source
            if (searchQuery.isNotBlank()) {
                searchJob?.cancel()
                searchJob = viewModelScope.launch { performSearch(searchQuery) }
            }
        }
    
        private fun clearSearchResults() {
            searchResultsTracks.clear(); searchResultsArtists.clear(); searchResultsPlaylists.clear(); searchResultsYoutube.clear()
            tracksNextUrl = null; artistsNextUrl = null; playlistsNextUrl = null
        }
    
        private suspend fun performSearch(query: String) {
            isSearchLoading = true; clearSearchResults()
            try {
                when (activeSearchSource) {
                    SearchSource.SOUNDCLOUD -> performSoundCloudSearch(query)
                    SearchSource.YOUTUBE -> performYoutubeSearch(query)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isSearchLoading = false
            }
        }
    
        private suspend fun fetchYoutubeRecommendations(seedTrack: Track): List<Track> {
            return withContext(Dispatchers.IO) {
                try {
                    val cleanTitle = seedTrack.title?.replace(Regex("(?i)(\\[.*?\\]|\\(.*?\\))"), "")?.trim() ?: ""
                    val artistName = seedTrack.user?.username ?: ""
                    val query = "$cleanTitle $artistName audio"
    
                    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                    result?.items?.mapNotNull { item ->
                        if (item is SongItem) {
                            Track(
                                id = item.id.hashCode().toLong(),
                                title = item.title,
                                user = User(0L, item.artists.firstOrNull()?.name ?: "YouTube", null),
                                artworkUrl = item.thumbnail,
                                durationMs = (item.duration ?: 0) * 1000L,
                                permalinkUrl = "https://youtube.com/watch?v=${item.id}",
                                source = "youtube"
                            )
                        } else {
                            try {
                                val id = (item as? Any)?.let {
                                    it.javaClass.getMethod("getId").invoke(it) as? String
                                } ?: return@mapNotNull null
    
                                val title = (item as? Any)?.let {
                                    it.javaClass.getMethod("getTitle").invoke(it) as? String
                                } ?: return@mapNotNull null
    
                                Track(
                                    id = id.hashCode().toLong(),
                                    title = title,
                                    user = User(0L, "YouTube", null),
                                    artworkUrl = null,
                                    durationMs = 0L,
                                    permalinkUrl = "https://youtube.com/watch?v=$id",
                                    source = "youtube"
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    }?.take(5) ?: emptyList()
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
        }
    
        private suspend fun performYoutubeSearch(query: String) {
            withContext(Dispatchers.IO) {
                try {
                    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
    
                    val mappedTracks = result?.items?.mapNotNull { item ->
                        if (item is SongItem) {
                            Track(
                                id = item.id.hashCode().toLong(),
                                title = item.title,
                                user = User(0L, item.artists.firstOrNull()?.name ?: "YouTube", null),
                                artworkUrl = item.thumbnail,
                                durationMs = (item.duration ?: 0) * 1000L,
                                permalinkUrl = "https://youtube.com/watch?v=${item.id}",
                                source = "youtube"
                            )
                        } else {
                            try {
                                val id = (item as? Any)?.let {
                                    it.javaClass.getMethod("getId").invoke(it) as? String
                                } ?: return@mapNotNull null
    
                                val title = (item as? Any)?.let {
                                    it.javaClass.getMethod("getTitle").invoke(it) as? String
                                } ?: return@mapNotNull null
    
                                Track(
                                    id = id.hashCode().toLong(),
                                    title = title,
                                    user = User(0L, "YouTube", null),
                                    artworkUrl = null,
                                    durationMs = 0L,
                                    permalinkUrl = "https://youtube.com/watch?v=$id",
                                    source = "youtube"
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                    } ?: emptyList()
    
                    withContext(Dispatchers.Main) {
                        searchResultsYoutube.clear()
                        searchResultsYoutube.addAll(mappedTracks)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        private suspend fun performSoundCloudSearch(query: String) {
            coroutineScope {
                when (activeFilter) {
                    SearchFilter.ALL -> {
                        val tracksDef = async { try { api.searchTracks(query, limit = 5) } catch (e: Exception) { null } }
                        val usersDef = async { try { api.searchUsers(query, limit = 5) } catch (e: Exception) { null } }
                        val playlistsDef = async { try { api.searchPlaylists(query, limit = 5) } catch (e: Exception) { null } }
    
                        tracksDef.await()?.let { searchResultsTracks.addAll(it.collection); tracksNextUrl = it.next_href }
                        usersDef.await()?.let { searchResultsArtists.addAll(it.collection); artistsNextUrl = it.next_href }
                        playlistsDef.await()?.let { searchResultsPlaylists.addAll(it.collection); playlistsNextUrl = it.next_href }
                    }
                    SearchFilter.TRACKS -> {
                        val response = api.searchTracks(query, limit = 30); searchResultsTracks.addAll(response.collection); tracksNextUrl = response.next_href
                    }
                    SearchFilter.ARTISTS -> {
                        val response = api.searchUsers(query, limit = 30); searchResultsArtists.addAll(response.collection); artistsNextUrl = response.next_href
                    }
                    SearchFilter.PLAYLISTS -> {
                        val response = api.searchPlaylists(query, limit = 30); searchResultsPlaylists.addAll(response.collection); playlistsNextUrl = response.next_href
                    }
                }
            }
        }
    
        fun loadMoreSearchResults() {
            if (isSearchLoadingMore) return
            viewModelScope.launch {
                isSearchLoadingMore = true
                try {
                    when (activeFilter) {
                        SearchFilter.TRACKS -> {
                            if (tracksNextUrl != null) {
                                val response = api.getSearchTracksNextPage(tracksNextUrl!!); searchResultsTracks.addAll(response.collection); tracksNextUrl = response.next_href
                            }
                        }
                        SearchFilter.ARTISTS -> {
                            if (artistsNextUrl != null) {
                                val response = api.getSearchUsersNextPage(artistsNextUrl!!); searchResultsArtists.addAll(response.collection); artistsNextUrl = response.next_href
                            }
                        }
                        SearchFilter.PLAYLISTS -> {
                            if (playlistsNextUrl != null) {
                                val response = api.getSearchPlaylistsNextPage(playlistsNextUrl!!); searchResultsPlaylists.addAll(response.collection); playlistsNextUrl = response.next_href
                            }
                        }
                        else -> {}
                    }
                } catch (e: Exception) { e.printStackTrace() } finally { isSearchLoadingMore = false }
            }
        }
    
        private fun loadFromCache() {
            try {
                val json = prefs.getString("cached_home_data", null)
                if (json != null) {
                    val data: HomeCacheData = gson.fromJson(json, object : TypeToken<HomeCacheData>() {}.type)
                    userProfile = data.user
                    if (data.sections.isNotEmpty()) {
                        homeSections.clear()
                        data.sections.forEach { section ->
                            val content: List<Any> = when (section.type) {
                                SectionType.TRACKS_ROW -> section.tracks
                                SectionType.STATIONS_ROW -> section.playlists
                                SectionType.ARTISTS_ROW -> section.users
                                SectionType.DISCOVERY_ROW -> section.tracks
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
                    val sectionsCache = homeSections.map { section -> HomeSectionCache(section.title, section.subtitle, section.type, section.content.filterIsInstance<Track>(), section.content.filterIsInstance<Playlist>(), section.content.filterIsInstance<User>()) }
                    val data = HomeCacheData(userProfile, sectionsCache)
                    prefs.edit().putString("cached_home_data", gson.toJson(data)).apply()
                } catch (e: Exception) { e.printStackTrace() }
            }
        }

        private fun extractYoutubeVideoId(url: String): String? {
            val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu.be/|/v/|/e/|watch\\?v%3D|watch\\?feature=player_embedded&v=|%2Fvideos%2F|embed%\u200C\u200B2F|youtu.be%2F|%2Fv%2F)[^#&?\\n]*"
            val compiledPattern = Pattern.compile(pattern)
            val matcher = compiledPattern.matcher(url)
            return if (matcher.find()) matcher.group() else null
        }

        private fun handleYoutubeUrl(url: String) {
            isSearchLoading = true
            clearSearchResults()
            viewModelScope.launch {
                try {
                    if (url.contains("list=") || url.contains("radio")) {
                        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                        _navigateTo.emit("playlist_detail/yt_radio:$encodedUrl")
                        clearSearch()
                        isSearchLoading = false
                        return@launch
                    }

                    val videoId = extractYoutubeVideoId(url)
                    if (videoId != null) {
                        val result = withContext(Dispatchers.IO) {
                            try {
                                YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()
                            } catch (e: Exception) {
                                null
                            }
                        }

                        val item = result?.items?.firstOrNull()

                        val title = item?.title ?: "YouTube Track"
                        val author = item?.artists?.firstOrNull()?.name ?: "YouTube"
                        val art = item?.thumbnail ?: "https://img.youtube.com/vi/$videoId/maxresdefault.jpg"

                        val track = Track(
                            id = videoId.hashCode().toLong(),
                            title = title,
                            user = User(0L, author, null),
                            artworkUrl = art,
                            durationMs = 0L,
                            permalinkUrl = url,
                            source = "youtube"
                        )

                        _playTrack.emit(track)
                        clearSearch()
                    } else {
                        performSearch(url)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    performSearch(url)
                } finally {
                    isSearchLoading = false
                }
            }
        }

        fun loadData() {
            viewModelScope.launch {
                val token = tokenManager.getAccessToken()
                if (token.isNullOrEmpty()) loadGuestData() else loadAuthenticatedData()
            }
        }
    
        private suspend fun fetchDiscoverySection(localLikes: List<Track>): HomeSection? {
            return try {
                val seedTrack = if (localLikes.isNotEmpty()) {
                    localLikes.random()
                } else {
                    api.getCharts(limit = 10).collection.mapNotNull { it.track }.randomOrNull()
                }
    
                if (seedTrack == null) return null
    
                val related = api.getRelatedTracks(seedTrack.id, limit = 20)
                val discoveryTracks = related.collection
                    .filter { it.id != seedTrack.id }
                    .shuffled()
                    .take(8)
    
                if (discoveryTracks.isNotEmpty()) {
                    HomeSection(
                        title = getString(R.string.home_discovery_title),
                        subtitle = getString(R.string.home_discovery_subtitle),
                        content = discoveryTracks,
                        type = SectionType.DISCOVERY_ROW
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }
        }
    
        private suspend fun fetchHistoryBasedSection(): HomeSection? {
            return try {
                val history = HistoryRepository.getHistory().first()
                val recentTracks = history.filter { it.type == "TRACK" }.take(10)
                if (recentTracks.isEmpty()) return null
    
                val seedItem = recentTracks.random()
                val seedTrack = Track(
                    id = seedItem.numericId,
                    title = seedItem.title,
                    user = User(0L, seedItem.subtitle, null),
                    artworkUrl = seedItem.imageUrl,
                    durationMs = 0L,
                    source = (seedItem.source as? String) ?: "soundcloud",
                    permalinkUrl = seedItem.originalUrl
                )
    
                coroutineScope {
                    val relatedSCDef = async {
                        try {
                            if (seedTrack.source == "soundcloud") {
                                api.getRelatedTracks(seedTrack.id, limit = 10).collection
                            } else {
                                api.searchTracks(seedTrack.title ?: "", limit = 10).collection
                            }
                        } catch (e: Exception) { emptyList() }
                    }
                    val relatedYTDef = async {
                        fetchYoutubeRecommendations(seedTrack)
                    }
    
                    val relatedSC = relatedSCDef.await()
                    val relatedYT = relatedYTDef.await()
                    val mixed = (relatedSC + relatedYT).shuffled()
    
                    if (mixed.isNotEmpty()) {
                        HomeSection(
                            title = getString(R.string.home_section_similar, seedItem.title),
                            subtitle = getString(R.string.home_section_similar_sub),
                            content = mixed,
                            type = SectionType.TRACKS_ROW
                        )
                    } else null
                }
            } catch (e: Exception) { null }
        }

        private suspend fun fetchPersonalizedSections(sourceTracks: List<Track>, username: String): List<HomeSection> {
            val sections = mutableStateListOf<HomeSection>()

            val historyItems = try { HistoryRepository.getHistory().first() } catch (e: Exception) { emptyList() }

            val recentTracks = historyItems.filter { it.type == "TRACK" }.take(20).map {
                Track(
                    id = it.numericId,
                    title = it.title,
                    artworkUrl = it.imageUrl,
                    durationMs = 0L,
                    user = User(0, it.subtitle, null),
                    source = it.source,
                    permalinkUrl = it.originalUrl
                )
            }

            try {
                coroutineScope {
                    if (recentTracks.isNotEmpty()) {
                        val habitSeeds = recentTracks.distinctBy { it.id }.take(10)
                        val habitStations = habitSeeds.map { track ->
                            val isYoutube = track.source == "youtube" && !track.permalinkUrl.isNullOrEmpty()
                            val permalink = if (isYoutube) "yt_radio:${track.permalinkUrl}" else "track_station_marker"
                            Playlist(
                                id = track.id,
                                title = getString(R.string.home_station_track_title, track.title ?: ""),
                                artworkUrl = track.fullResArtwork,
                                calculatedArtworkUrl = null,
                                trackCount = 0,
                                user = track.user,
                                permalinkUrl = permalink
                            )
                        }
                        if (habitStations.isNotEmpty()) {
                            sections.add(HomeSection(getString(R.string.home_habits_title), getString(R.string.home_habits_sub), habitStations, SectionType.STATIONS_ROW))
                        }
                    }

                    if (sourceTracks.isNotEmpty()) {
                        val rediscoverySeeds = sourceTracks.shuffled().take(10)
                        val rediscoveryStations = rediscoverySeeds.map { track ->
                            val isYoutube = track.source == "youtube" && !track.permalinkUrl.isNullOrEmpty()
                            val permalink = if (isYoutube) "yt_radio:${track.permalinkUrl}" else "track_station_marker"
                            Playlist(
                                id = track.id,
                                title = getString(R.string.home_station_track_title, track.title ?: ""),
                                artworkUrl = track.fullResArtwork,
                                calculatedArtworkUrl = null,
                                trackCount = 0,
                                user = track.user,
                                permalinkUrl = permalink
                            )
                        }
                        if (rediscoveryStations.isNotEmpty()) {
                            sections.add(HomeSection(getString(R.string.home_rediscovery_title), getString(R.string.home_rediscovery_sub), rediscoveryStations, SectionType.STATIONS_ROW))
                        }
                    }

                    val recommendedAlbumsDef = async {
                        val finalAlbumList = mutableListOf<Playlist>()
                        try {
                            val favoriteArtistIds = sourceTracks.mapNotNull { it.user?.id }.distinct().shuffled().take(5)
                            if (favoriteArtistIds.isNotEmpty()) {
                                val artistAlbums = favoriteArtistIds.map { artistId ->
                                    async { try { api.getUserAlbums(artistId).collection } catch (e: Exception) { emptyList() } }
                                }.map { it.await() }.flatten()
                                finalAlbumList.addAll(artistAlbums)
                            }

                            val topGenres = sourceTracks.mapNotNull { it.genre }.filter { it.isNotBlank() }
                                .groupingBy { it }.eachCount()
                                .toList().sortedByDescending { it.second }.take(2).map { it.first }

                            if (topGenres.isNotEmpty()) {
                                val genreAlbums = topGenres.map { genre ->
                                    async { try { api.searchAlbums(genre, limit = 5).collection } catch (e: Exception) { emptyList() } }
                                }.map { it.await() }.flatten()
                                finalAlbumList.addAll(genreAlbums)
                            }
                        } catch (e: Exception) {
                            finalAlbumList.addAll(api.searchAlbums(getString(R.string.home_top_albums_query), limit = 10).collection)
                        }
                        finalAlbumList.distinctBy { it.id }.shuffled().take(10)
                    }

                    val artistStationsDef = async {
                        val artistCandidates = sourceTracks.mapNotNull { it.user }
                            .distinctBy { it.id }
                            .filter { it.id > 0 }
                            .shuffled()
                            .take(5)

                        if (artistCandidates.isNotEmpty()) {
                            artistCandidates.map { artist ->
                                Playlist(
                                    id = artist.id,
                                    title = getString(R.string.home_station_artist_title, artist.username ?: ""),
                                    artworkUrl = artist.avatarUrl,
                                    calculatedArtworkUrl = null,
                                    trackCount = 0,
                                    user = artist,
                                    permalinkUrl = "artist_station_marker"
                                )
                            }
                        } else {
                            emptyList()
                        }
                    }

                    val likedByDef = async {
                        val candidateIds = sourceTracks.mapNotNull { it.user?.id }.distinct().shuffled().take(10)
                        val validatedUsersDeferred = candidateIds.map { userId ->
                            async { try { val userFull = api.getUser(userId); if (userFull.likesCount > 0) userFull else null } catch (e: Exception) { null } }
                        }
                        val validatedUsers = validatedUsersDeferred.mapNotNull { it.await() }

                        if (validatedUsers.isNotEmpty()) {
                            validatedUsers.map { user ->
                                Playlist(
                                    id = user.id,
                                    title = getString(R.string.home_liked_by_user_title, user.username ?: ""),
                                    artworkUrl = user.avatarUrl,
                                    calculatedArtworkUrl = null,
                                    trackCount = user.likesCount,
                                    user = user,
                                    permalinkUrl = "liked_by_marker"
                                )
                            }
                        } else {
                            emptyList()
                        }
                    }

                    val seed1 = sourceTracks.take(10).randomOrNull() ?: sourceTracks.first()
                    val relatedDef1 = async {
                        try {
                            if (seed1.source == "soundcloud") {
                                api.getRelatedTracks(seed1.id, limit = 10).collection
                            } else {
                                api.searchTracks(seed1.title ?: "", limit = 10).collection
                            }
                        } catch (e: Exception) { emptyList() }
                    }

                    val newCrewDef = async {
                        val artists = sourceTracks.mapNotNull { it.user }.distinctBy { it.id }.shuffled().take(8)
                        val similarArtists = try {
                            val randomLike = sourceTracks.shuffled().first()
                            api.getRelatedTracks(randomLike.id, limit=10).collection.mapNotNull { it.user }
                        } catch(e:Exception) { emptyList() }
                        (artists + similarArtists).distinctBy { it.id }.shuffled().take(10)
                    }

                    val recommendedAlbums = recommendedAlbumsDef.await()
                    if (recommendedAlbums.isNotEmpty()) {
                        sections.add(HomeSection(getString(R.string.home_albums_for_you), null, recommendedAlbums, SectionType.STATIONS_ROW))
                    }

                    val artistStations = artistStationsDef.await()
                    if(artistStations.isNotEmpty()){
                        sections.add(HomeSection(getString(R.string.home_discover_stations), getString(R.string.home_section_new_crew_sub), artistStations, SectionType.STATIONS_ROW))
                    }

                    val likedByItems = likedByDef.await()
                    if (likedByItems.isNotEmpty()) {
                        sections.add(HomeSection(getString(R.string.home_liked_by_section_title), getString(R.string.home_liked_by_section_subtitle), likedByItems, SectionType.STATIONS_ROW))
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
                generatePersonalizedCategories()
                val allSections = mutableListOf<HomeSection>()
    
                coroutineScope {
                    val genericSectionsDef = async { fetchGenericGuestSections() }
                    val personalSectionsDef = async {
                        if (localLikes.isNotEmpty()) fetchPersonalizedSections(localLikes, getString(R.string.guest_user)) else emptyList()
                    }
                    val historySectionDef = async { fetchHistoryBasedSection() }
                    val discoverySectionDef = async { fetchDiscoverySection(localLikes) }
                    val recommendationsDef = async { fetchTrackRecommendations(localLikes) }
    
                    val genericSections = genericSectionsDef.await()
                    val personalSections = personalSectionsDef.await()
                    val historySection = historySectionDef.await()
                    val discoverySection = discoverySectionDef.await()
                    val recommendationsSection = recommendationsDef.await()
    
                    if (discoverySection != null) allSections.add(discoverySection)
                    if (recommendationsSection != null) allSections.add(recommendationsSection)
                    if (historySection != null) allSections.add(historySection)
                    allSections.addAll(personalSections)
                    allSections.addAll(genericSections)
                }
    
                if (allSections.isNotEmpty()) {
                    homeSections.clear(); homeSections.addAll(allSections); saveToCache()
                } else {
                    delay(2000)
                    if (homeSections.isEmpty()) loadGuestData()
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    
        private suspend fun fetchGenericGuestSections(): List<HomeSection> {
            val sections = mutableStateListOf<HomeSection>()
            try {
                coroutineScope {
                    val trendingDef = async { try { api.getCharts(kind = "trending", genre = "soundcloud:genres:all-music").collection.mapNotNull { it.track } } catch(e:Exception){ emptyList() } }
                    val albumsDef = async { try { api.searchAlbums(getString(R.string.home_top_albums_query) + " 2026", limit = 10).collection } catch(e:Exception){ emptyList() } }
                    val hiphopDef = async { try { api.searchTracks("Hip-Hop & Rap", limit = 20).collection } catch(e:Exception){ emptyList() } }
                    val popDef = async { try { api.searchTracks("Pop Music Trending", limit = 20).collection } catch(e:Exception){ emptyList() } }
                    val electroDef = async { try { api.searchPlaylists("Electro House 2026", limit = 10).collection } catch(e:Exception){ emptyList() } }
                    val artistsDef = async { try { val l1 = api.searchUsers("Billboard", limit = 5).collection; val l2 = api.searchUsers("Official Music", limit = 5).collection; (l1+l2).distinctBy{it.id}.shuffled() } catch(e:Exception){ emptyList() } }
    
                    val trending = trendingDef.await()
                    if (trending.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_trending), null, trending, SectionType.TRACKS_ROW))
    
                    val albums = albumsDef.await()
                    if (albums.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_albums_for_you), null, albums, SectionType.STATIONS_ROW))
    
                    val hiphop = hiphopDef.await()
                    if (hiphop.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_hiphop), null, hiphop, SectionType.TRACKS_ROW))
    
                    val techno = electroDef.await()
                    if (techno.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_electro), null, techno, SectionType.STATIONS_ROW))
    
                    val artists = artistsDef.await()
                    if (artists.isNotEmpty()) sections.add(HomeSection(getString(R.string.lib_artists), null, artists, SectionType.ARTISTS_ROW))
    
                    val pop = popDef.await()
                    if (pop.isNotEmpty()) sections.add(HomeSection(getString(R.string.home_pop), null, pop, SectionType.TRACKS_ROW))
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
                    val streamDef = async {
                        try {
                            api.getMyStream(limit = 20).collection
                                .filter { it.type == "track" || it.type == "track-repost" }
                                .mapNotNull { it.track }
                                .distinctBy { it.id }
                        } catch (e: Exception) { emptyList() }
                    }
    
                    val localLikes = LikeRepository.likedTracks.value
                    val sourceLikes = if (localLikes.size > 20) localLikes else {
                        try { api.getUserTrackLikes(me.id, limit = 50).collection.map { it.track } } catch(e:Exception) { emptyList() }
                    }
    
                    generatePersonalizedCategories()
    
                    val historySectionDef = async { fetchHistoryBasedSection() }
                    val discoverySectionDef = async { fetchDiscoverySection(sourceLikes) }
                    val recommendationsDef = async { fetchTrackRecommendations(localLikes) }
    
                    val discoverySection = discoverySectionDef.await()
                    if (discoverySection != null) allSections.add(discoverySection)
    
                    val streamTracks = streamDef.await()
                    if (streamTracks.isNotEmpty()) {
                        allSections.add(HomeSection(getString(R.string.home_stream), null, streamTracks, SectionType.TRACKS_ROW))
                    }
    
                    val recommendationsSection = recommendationsDef.await()
                    if (recommendationsSection != null) allSections.add(recommendationsSection)
    
                    val historySection = historySectionDef.await()
                    if (historySection != null) allSections.add(historySection)
    
                    if (sourceLikes.isNotEmpty()) {
                        val personalSections = fetchPersonalizedSections(sourceLikes, me.username ?: getString(R.string.unknown_user))
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
    
        private suspend fun fetchTrackRecommendations(localLikes: List<Track>): HomeSection? {
            return try {
                val historyItems = HistoryRepository.getHistory().first()
    
                val seedTracks = mutableListOf<Track>()
                seedTracks.addAll(localLikes)
                seedTracks.addAll(historyItems
                    .filter { it.type == "TRACK" }
                    .map {
                        Track(id = it.numericId, title = it.title, user = null, artworkUrl = null, durationMs = 0L)
                    }
                )
    
                if (seedTracks.isEmpty()) return null
    
                val seedsToUse = seedTracks.shuffled().take(5)
    
                val recommendedTracks = coroutineScope {
                    val tasks = seedsToUse.map { seed ->
                        async {
                            try {
                                api.getRelatedTracks(seed.id, limit = 20).collection
                            } catch (e: Exception) {
                                emptyList<Track>()
                            }
                        }
                    }
                    tasks.awaitAll().flatten()
                }
    
                val likedIds = localLikes.map { it.id }.toSet()
                val historyIds = historyItems.map { it.numericId }.toSet()
    
                val finalTracks = recommendedTracks
                    .distinctBy { it.id }
                    .filter { !likedIds.contains(it.id) && !historyIds.contains(it.id) }
                    .shuffled()
                    .take(20)
    
                if (finalTracks.isNotEmpty()) {
                    HomeSection(
                        title = getString(R.string.home_recommended_tracks),
                        subtitle = getString(R.string.home_recommended_tracks_sub),
                        content = finalTracks,
                        type = SectionType.TRACKS_ROW
                    )
                } else {
                    null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    
        private fun getIconForGenre(genre: String): ImageVector {
            val lowerCaseGenre = genre.lowercase(Locale.ROOT)
            return when {
                "phonk" in lowerCaseGenre -> Icons.Rounded.TimeToLeave
                "rock" in lowerCaseGenre -> Icons.Rounded.Whatshot
                "hip hop" in lowerCaseGenre || "rap" in lowerCaseGenre -> Icons.Rounded.Mic
                "house" in lowerCaseGenre || "techno" in lowerCaseGenre || "edm" in lowerCaseGenre -> Icons.Rounded.Nightlife
                "ambient" in lowerCaseGenre || "lo-fi" in lowerCaseGenre || "lofi" in lowerCaseGenre -> Icons.Rounded.Spa
                else -> Icons.Rounded.MusicNote
            }
        }
    
        private fun parseSoundCloudTags(tagList: String?): List<String> {
            if (tagList.isNullOrBlank()) return emptyList()
            val tags = mutableListOf<String>()
            val pattern = Pattern.compile("\"([^\"]*)\"|(\\S+)")
            val matcher = pattern.matcher(tagList)
            while (matcher.find()) {
                if (matcher.group(1) != null) {
                    tags.add(matcher.group(1)!!)
                } else {
                    tags.add(matcher.group(2)!!)
                }
            }
            return tags
        }
    
        private fun generatePersonalizedCategories() {
            viewModelScope.launch(Dispatchers.Default) {
                val likedTracks = LikeRepository.likedTracks.value.take(20)
                val historyItems = historyFlow.first().filter { it.type == "TRACK" }.take(20)
    
                val sourceTracks = if (likedTracks.size >= 5) {
                    likedTracks
                } else {
                    val historyTracks = historyItems.map {
                        Track(it.numericId, it.title, null, 0L, User(0, it.subtitle, null), genre = null, tagList = null)
                    }
                    (likedTracks + historyTracks).distinctBy { it.id }.take(20)
                }
    
                if (sourceTracks.isEmpty()) {
                    withContext(Dispatchers.Main) { personalizedCategories.clear() }
                    return@launch
                }
    
                val allTags = mutableListOf<String>()
                val excludedTags = setOf("music", "audio", "soundcloud", "song", "trap", "remix")
    
                sourceTracks.forEach { track ->
                    track.genre?.let { genre ->
                        if (genre.isNotBlank() && genre.length > 2 && !excludedTags.contains(genre.lowercase(Locale.ROOT))) {
                            allTags.add(genre.trim())
                        }
                    }
                    track.tagList?.let { tags ->
                        parseSoundCloudTags(tags).forEach { tag ->
                            if (tag.isNotBlank() && tag.length > 2 && !excludedTags.contains(tag.lowercase(Locale.ROOT))) {
                                allTags.add(tag.trim())
                            }
                        }
                    }
                }
    
                val topTags = allTags
                    .groupingBy { it.lowercase(Locale.ROOT) }
                    .eachCount()
                    .toList()
                    .sortedByDescending { it.second }
                    .take(10)
                    .map { it.first }
    
                val newCategories = topTags.map { tag ->
                    SearchCategory(
                        id = tag,
                        title = tag.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },
                        query = tag,
                        icon = getIconForGenre(tag)
                    )
                }
                withContext(Dispatchers.Main) {
                    personalizedCategories.clear()
                    personalizedCategories.addAll(newCategories)
                }
            }
        }
    }



