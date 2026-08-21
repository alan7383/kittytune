package com.alananasss.kittytune.ui.home

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.SearchCategory
import kotlinx.coroutines.Dispatchers
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.ArtistCircleShimmer
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.ShimmerLine
import com.alananasss.kittytune.ui.common.SquareCardShimmer
import com.alananasss.kittytune.ui.library.DynamicPlaylistCard
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.ArtistAvatar
import com.alananasss.kittytune.ui.profile.SquareCard
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.absoluteValue
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.drawBehind
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel,
    onNavigate: (String) -> Unit
) {
    val history by homeViewModel.historyFlow.collectAsState(initial = emptyList())
    val userProfile = homeViewModel.userProfile
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }

    val isKeyboardOpen = WindowInsets.isImeVisible

    LaunchedEffect(Unit) {
        homeViewModel.navigateTo.collect { route ->
            onNavigate(route)
            homeViewModel.clearSearch()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(Unit) {
        homeViewModel.playTrack.collect { track ->
            playerViewModel.playPlaylist(listOf(track), 0)
            homeViewModel.clearSearch()
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(homeViewModel.isSearching, homeViewModel.searchTrigger) {
        if (homeViewModel.isSearching) {
            delay(400)
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            focusManager.clearFocus()
        }
    }

    // Live network observer
    val context = LocalContext.current
    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                homeViewModel.isOfflineMode = false
                homeViewModel.refreshData()
            }

            override fun onLost(network: Network) {
                homeViewModel.isOfflineMode = true
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    BackHandler(enabled = homeViewModel.isSearching) {
        if (isKeyboardOpen) {
            focusManager.clearFocus()
        } else {
            homeViewModel.clearSearch()
        }
    }

    Scaffold(
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            ) {
                Column {
                    // Animated Offline Banner
                    AnimatedVisibility(
                        visible = homeViewModel.isOfflineMode,
                        enter = fadeIn(tween(300)) + expandVertically(tween(300)),
                        exit = fadeOut(tween(300)) + shrinkVertically(tween(300))
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .clickable { onNavigate("downloads") },
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudOff,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = stringResource(R.string.home_offline_banner),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = stringResource(R.string.offline_banner_action_downloads),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    val isSearching = homeViewModel.isSearching
                    val searchBarPadding by animateDpAsState(
                        targetValue = if (isSearching) 0.dp else 16.dp,
                        label = "SearchBarPadding"
                    )

                    val searchContainerColor by animateColorAsState(
                        targetValue = if (isSearching) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surfaceContainerHigh,
                        label = "SearchBarBg"
                    )

                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = homeViewModel.searchQuery,
                                onQueryChange = homeViewModel::onSearchQueryChanged,
                                onSearch = { focusManager.clearFocus() },
                                expanded = isSearching,
                                onExpandedChange = {
                                    if (it) homeViewModel.activateSearch() else {
                                        homeViewModel.clearSearch()
                                        focusManager.clearFocus()
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = stringResource(R.string.search_hint),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                modifier = Modifier.focusRequester(focusRequester),
                                leadingIcon = {
                                    if (isSearching) {
                                        IconButton(
                                            onClick = {
                                                homeViewModel.clearSearch()
                                                focusManager.clearFocus()
                                            },
                                            shapes = IconButtonDefaults.shapes()
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.ArrowBack,
                                                stringResource(R.string.btn_close)
                                            )
                                        }
                                    } else {
                                        IconButton(onClick = { homeViewModel.activateSearch() }) {
                                            Icon(
                                                Icons.Default.Search,
                                                null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                },
                                trailingIcon = {
                                    if (isSearching) {
                                        if (homeViewModel.searchQuery.isNotEmpty()) {
                                            IconButton(onClick = { homeViewModel.onSearchQueryChanged("") }) {
                                                Icon(Icons.Default.Close, stringResource(R.string.btn_close))
                                            }
                                        }
                                    } else {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { onNavigate("recognition") }) {
                                                Icon(
                                                    imageVector = Icons.Rounded.GraphicEq,
                                                    contentDescription = stringResource(R.string.recognize_music),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            IconButton(onClick = {
                                                if (userProfile != null) {
                                                    onNavigate("my_profile_menu")
                                                } else {
                                                    onNavigate("login_required")
                                                }
                                            }) {
                                                if (userProfile?.avatarUrl != null) {
                                                    ArtistAvatar(
                                                        avatarUrl = userProfile.avatarUrl,
                                                        modifier = Modifier.size(32.dp).clip(CircleShape),
                                                        enableViewer = false
                                                    )
                                                } else {
                                                    Icon(
                                                        imageVector = Icons.Default.AccountCircle,
                                                        contentDescription = stringResource(R.string.guest_user),
                                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.size(32.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        },
                        expanded = isSearching,
                        onExpandedChange = {
                            if (it) homeViewModel.activateSearch() else {
                                homeViewModel.clearSearch()
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = searchBarPadding)
                            .padding(bottom = 8.dp),
                        colors = SearchBarDefaults.colors(
                            containerColor = searchContainerColor,
                            dividerColor = Color.Transparent
                        ),
                        content = {
                            Column(modifier = Modifier.fillMaxSize()) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp)
                                        .padding(top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AnimatedVisibility(
                                        visible = homeViewModel.activeSearchSource == SearchSource.SOUNDCLOUD || homeViewModel.activeSearchSource == SearchSource.SPOTIFY,
                                        enter = fadeIn(),
                                        exit = fadeOut(),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        SearchFilters(
                                            activeFilter = homeViewModel.activeFilter,
                                            onFilterSelected = homeViewModel::onFilterChanged
                                        )
                                    }
                                    if (homeViewModel.activeSearchSource == SearchSource.YOUTUBE) {
                                        Spacer(Modifier.weight(1f))
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    SearchSourceSelector(
                                        selectedSource = homeViewModel.activeSearchSource,
                                        onSelect = homeViewModel::onSearchSourceChanged
                                    )
                                }

                                // Determine the search display state for AnimatedContent
                                val searchDisplayState = when {
                                    homeViewModel.searchQuery.isEmpty() -> "categories"
                                    homeViewModel.isSearchLoading -> "loading"
                                    else -> "results_${homeViewModel.activeFilter}"
                                }

                                AnimatedContent(
                                    targetState = searchDisplayState,
                                    transitionSpec = {
                                        val enter = fadeIn(
                                            animationSpec = tween(
                                                320,
                                                easing = androidx.compose.animation.core.FastOutSlowInEasing
                                            )
                                        ) +
                                                scaleIn(
                                                    initialScale = 0.96f,
                                                    animationSpec = tween(
                                                        320,
                                                        easing = androidx.compose.animation.core.FastOutSlowInEasing
                                                    )
                                                )
                                        val exit = fadeOut(animationSpec = tween(200))
                                        enter.togetherWith(exit)
                                    },
                                    label = "SearchStateAnimation",
                                    modifier = Modifier.fillMaxSize()
                                ) { state ->
                                    when {
                                        state == "categories" -> {
                                            SearchCategoriesGrid(
                                                moods = homeViewModel.moodCategories,
                                                genres = homeViewModel.genreCategories,
                                                onCategoryClick = { category: SearchCategory ->
                                                    val encodedTitle = Uri.encode(category.title)
                                                    val encodedQuery = Uri.encode(category.query)
                                                    onNavigate("genre_playlists/$encodedTitle/$encodedQuery")
                                                }
                                            )
                                        }

                                        state == "loading" -> {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ContainedLoadingIndicator()
                                            }
                                        }

                                        else -> {
                                            val currentFilter = homeViewModel.activeFilter
                                            SearchResultsList(
                                                homeViewModel = homeViewModel,
                                                playerViewModel = playerViewModel,
                                                onNavigate = onNavigate,
                                                activeFilter = currentFilter
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
            ) {
                if (homeViewModel.isLoading && homeViewModel.homeSections.isEmpty() && !homeViewModel.isOfflineMode) {
                    HomeScreenShimmer()
                } else if (homeViewModel.isOfflineMode && homeViewModel.homeSections.isEmpty()) {
                    OfflineHomeView(
                        onRetry = { homeViewModel.refreshData() },
                        onGoToDownloads = { onNavigate("downloads") }
                    )
                } else {
                    val pullRefreshState = rememberPullToRefreshState()

                    PullToRefreshBox(
                        isRefreshing = homeViewModel.isRefreshing,
                        onRefresh = { homeViewModel.refreshData() },
                        state = pullRefreshState,
                        modifier = Modifier.fillMaxSize(),
                        indicator = {
                            if (homeViewModel.isRefreshing || pullRefreshState.distanceFraction > 0f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .padding(top = 16.dp)
                                        .graphicsLayer {
                                            val fraction = pullRefreshState.distanceFraction
                                            alpha = fraction.coerceIn(0f, 1f)
                                            translationY = (fraction * 20.dp.toPx()) - 20.dp.toPx()
                                        }
                                ) {
                                    ContainedLoadingIndicator()
                                }
                            }
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val fraction = pullRefreshState.distanceFraction

                                    translationY = fraction * 80.dp.toPx()

                                    val scale = 1f - (fraction * 0.02f).coerceIn(0f, 0.05f)
                                    scaleX = scale
                                    scaleY = scale
                                }
                        ) {
                            HomeContent(
                                homeViewModel = homeViewModel,
                                playerViewModel = playerViewModel,
                                history = history,
                                onNavigate = onNavigate
                            )
                        }
                    }
                }
            }
        }
    )
}

@Composable
fun HomeContent(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    history: List<HistoryItem>,
    onNavigate: (String) -> Unit
) {
    val scrollState = rememberLazyListState()
    val allSections = homeViewModel.homeSections

    val titleRecommended = stringResource(R.string.home_recommended_tracks)

    val titleRediscover = stringResource(R.string.home_rediscovery_title)
    val titleHabits = stringResource(R.string.home_habits_title)

    val titleStations = stringResource(R.string.home_discover_stations)
    val titleAlbums = stringResource(R.string.home_albums_for_you)
    val titleSimilarPrefix = stringResource(R.string.home_section_similar, "").trim()

    val discoverySection = allSections.find { it.type == SectionType.DISCOVERY_ROW }
    val recommendedSection = allSections.find { it.title == titleRecommended }

    val rediscoverSection = allSections.find { it.title == titleRediscover }
    val habitsSection = allSections.find { it.title == titleHabits }

    val stationsSection = allSections.find { it.title == titleStations }
    val albumsSection = allSections.find { it.title == titleAlbums }
    val similarSection = allSections.find { it.title.startsWith(titleSimilarPrefix) }

    val usedSections = setOfNotNull(
        discoverySection,
        recommendedSection,
        rediscoverSection,
        habitsSection,
        stationsSection,
        albumsSection,
        similarSection
    )
    val remainingSections = allSections.filter { !usedSections.contains(it) }

    LazyColumn(
        state = scrollState,
        contentPadding = PaddingValues(bottom = 180.dp),
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(32.dp)
    ) {
        item {
            val categoriesToShow = if (homeViewModel.personalizedCategories.isNotEmpty()) {
                homeViewModel.personalizedCategories
            } else {
                homeViewModel.moodCategories.take(10)
            }

            HomeFilterRow(
                categories = categoriesToShow,
                onCategoryClick = { category ->
                    val encodedTitle = Uri.encode(category.title)
                    val encodedQuery = Uri.encode(category.query)
                    onNavigate("genre_playlists/$encodedTitle/$encodedQuery")
                }
            )
        }

        val cleanHistory = history.filter { it.id != "playlist:0" && !it.title.equals("history", ignoreCase = true) }
        if (cleanHistory.isNotEmpty()) {
            item {
                StandardHorizontalSection(
                    title = stringResource(R.string.home_recently_played),
                    subtitle = null,
                    onTitleClick = { onNavigate("history") }
                ) {
                    items(cleanHistory.take(8)) { historyItem ->
                        HistoryCard(
                            item = historyItem,
                            onClick = {
                                when {
                                    historyItem.id == "likes" -> onNavigate("likes")
                                    historyItem.id == "downloads" -> onNavigate("downloads")
                                    historyItem.id.startsWith("yt_radio:") -> onNavigate(historyItem.id)
                                    historyItem.id.startsWith("spotify_artist:") -> onNavigate(historyItem.id)
                                    historyItem.id.startsWith("spotify_radio:") -> onNavigate(historyItem.id)
                                    historyItem.id.startsWith("spotify:") -> onNavigate(historyItem.id)
                                    historyItem.type == "STATION" && historyItem.id.contains("spotify") -> {
                                        val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(historyItem.id)
                                        onNavigate("spotify_radio:$clean")
                                    }
                                    historyItem.type == "PROFILE" -> {
                                        val clean = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(historyItem.id)
                                        if (clean.isNotBlank() && clean != "0" && (historyItem.id.contains("spotify") || clean.length == 22)) {
                                            onNavigate("spotify_artist:$clean")
                                        } else if (historyItem.id == "profile:0" || historyItem.numericId == 0L) {
                                            if (historyItem.title.isNotBlank()) {
                                                onNavigate("profile:${historyItem.title}")
                                            } else {
                                                onNavigate(historyItem.id)
                                            }
                                        } else {
                                            onNavigate(historyItem.id)
                                        }
                                    }
                                    historyItem.type == "STATION" -> onNavigate(historyItem.id)
                                    historyItem.type == "PLAYLIST" -> onNavigate(
                                        historyItem.id.replace(
                                            "playlist:",
                                            ""
                                        )
                                    )

                                    historyItem.type == "TRACK" -> {
                                        val trackToPlay = Track(
                                            id = historyItem.numericId,
                                            title = historyItem.title,
                                            artworkUrl = historyItem.imageUrl,
                                            durationMs = 0L,
                                            user = User(0L, historyItem.subtitle, null),
                                            source = historyItem.source,
                                            permalinkUrl = historyItem.originalUrl
                                        )
                                        playerViewModel.playPlaylist(listOf(trackToPlay), 0)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        habitsSection?.let {
            RenderHomeSection(it, onNavigate, playerViewModel)
        }

        rediscoverSection?.let {
            RenderHomeSection(it, onNavigate, playerViewModel)
        }

        stationsSection?.let {
            RenderHomeSection(it, onNavigate, playerViewModel)
        }
        albumsSection?.let {
            RenderHomeSection(it, onNavigate, playerViewModel)
        }
        similarSection?.let {
            RenderHomeSection(it, onNavigate, playerViewModel)
        }
        discoverySection?.let {
            item {
                DiscoverySectionCarousel(
                    title = it.title,
                    subtitle = it.subtitle,
                    tracks = it.content.filterIsInstance<Track>(),
                    onTrackClick = { track ->
                        playerViewModel.playPlaylist(listOf(track), 0, null)
                    }
                )
            }
        }
        recommendedSection?.let {
            RenderHomeSection(it, onNavigate, playerViewModel)
        }
        remainingSections.forEach { section ->
            if (section.type != SectionType.DISCOVERY_ROW) {
                RenderHomeSection(section, onNavigate, playerViewModel)
            }
        }
        item {
            ExplorerSection(onNavigate)
        }
    }
}

fun LazyListScope.RenderHomeSection(
    section: HomeSection,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel
) {
    item {
        when (section.type) {
            SectionType.STATIONS_ROW -> {
                StandardHorizontalSection(
                    title = section.title,
                    subtitle = section.subtitle
                ) {
                    val playlists = section.content.filterIsInstance<Playlist>()
                    items(playlists) { playlist ->
                        val navId = getStationNavId(playlist)
                        StationCardLarge(playlist) { onNavigate(navId) }
                    }
                }
            }

            SectionType.TRACKS_ROW -> {
                StandardHorizontalSection(
                    title = section.title,
                    subtitle = section.subtitle
                ) {
                    val tracks = section.content.filterIsInstance<Track>()
                    items(tracks) { track ->
                        TrackCardModern(track) { playerViewModel.playPlaylist(listOf(track), 0, null) }
                    }
                }
            }

            SectionType.ARTISTS_ROW -> {
                StandardHorizontalSection(
                    title = section.title,
                    subtitle = section.subtitle
                ) {
                    val artists = section.content.filterIsInstance<User>()
                    items(artists) { artist ->
                        ArtistCircle(artist) { onNavigate(artist.profileNavId) }
                    }
                }
            }

            SectionType.HIGHLIGHT_ROW -> {
                StandardHorizontalSection(
                    title = section.title,
                    subtitle = section.subtitle
                ) {
                    val tracks = section.content.filterIsInstance<Track>()
                    items(tracks) { track ->
                        HighlightTrackCard(track) { playerViewModel.playPlaylist(listOf(track), 0, null) }
                    }
                }
            }

            else -> {}
        }
    }
}

@Composable
fun HighlightTrackCard(track: Track, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .width(200.dp)
            .height(280.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.5f),
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            Text(
                text = "NEW TRACK",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                ),
                color = Color.White,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.user?.avatarUrl ?: "https://picsum.photos/100",
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = track.user?.username ?: stringResource(R.string.unknown_user),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.user?.verified == true) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = Color(0xFF1DA1F2),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ExplorerSection(onNavigate: (String) -> Unit) {
    val windowSizeInfo = com.alananasss.kittytune.ui.common.rememberWindowSizeInfo()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp)
    ) {
        Text(
            text = stringResource(R.string.explorer_title),
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        Row(
            modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 480.dp) else Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            ExplorerButton(
                icon = Icons.Rounded.NewReleases,
                label = stringResource(R.string.explorer_new_releases),
                baseColor = MaterialTheme.colorScheme.tertiary,
                onClick = { onNavigate("new_releases") },
                modifier = Modifier.weight(1f)
            )

            ExplorerButton(
                icon = Icons.Rounded.TrendingUp,
                label = stringResource(R.string.explorer_charts),
                baseColor = MaterialTheme.colorScheme.primary,
                onClick = { onNavigate("charts") },
                modifier = Modifier.weight(1f)
            )

            ExplorerButton(
                icon = Icons.Rounded.Mood,
                label = stringResource(R.string.explorer_moods_genres),
                baseColor = MaterialTheme.colorScheme.secondary,
                onClick = { onNavigate("genres") },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun ExplorerButton(
    icon: ImageVector,
    label: String,
    baseColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = baseColor.copy(alpha = 0.12f)
    val contentColor = baseColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top,
        modifier = modifier
    ) {
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(28.dp),
            color = containerColor,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = contentColor
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp
            ),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface,
            lineHeight = 18.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeFilterRow(
    categories: List<SearchCategory>,
    onCategoryClick: (SearchCategory) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        items(categories) { category ->
            Button(
                onClick = { onCategoryClick(category) },
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                modifier = Modifier.height(40.dp)
            ) {
                Text(
                    text = if (category.titleRes != 0) stringResource(category.titleRes) else category.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun QuickHistorySection(
    history: List<HistoryItem>,
    onItemClick: (HistoryItem) -> Unit
) {
    val cleanList = remember(history) {
        history.filter { it.id != "playlist:0" && !it.title.equals("history", ignoreCase = true) }
    }
    if (cleanList.isEmpty()) return

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                Icons.Rounded.History,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.home_recently_played),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(cleanList) { item ->
                QuickHistoryTile(item = item, onClick = { onItemClick(item) })
            }
        }
    }
}

@Composable
fun QuickHistoryTile(item: HistoryItem, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .size(110.dp)
                .aspectRatio(1f)
        ) {
            when (item.id) {
                "likes" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Favorite,
                            null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                "downloads" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                else -> {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = item.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun StandardHorizontalSection(
    title: String,
    subtitle: String?,
    onTitleClick: (() -> Unit)? = null,
    content: LazyListScope.() -> Unit
) {
    Column {
        Column(
            modifier = Modifier
                .then(if (onTitleClick != null) Modifier.clickable { onTitleClick() } else Modifier)
                .padding(horizontal = 24.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (onTitleClick != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun DiscoverySectionCarousel(
    title: String,
    subtitle: String?,
    tracks: List<Track>,
    onTrackClick: (Track) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { minOf(tracks.size, 8) })

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        HorizontalPager(
            state = pagerState,
            contentPadding = PaddingValues(horizontal = 32.dp),
            pageSpacing = 16.dp,
            pageSize = PageSize.Fill,
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
        ) { page ->
            val track = tracks[page]

            val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val scaleFactor = lerp(
                start = 0.92f,
                stop = 1f,
                fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
            )
            val alphaFactor = lerp(
                start = 0.7f,
                stop = 1f,
                fraction = 1f - pageOffset.absoluteValue.coerceIn(0f, 1f)
            )

            DiscoveryBigCard(
                track = track,
                scale = scaleFactor,
                alpha = alphaFactor,
                onClick = { onTrackClick(track) }
            )
        }
    }
}

@Composable
fun DiscoveryBigCard(
    track: Track,
    scale: Float,
    alpha: Float,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clickable { onClick() }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f),
                                Color.Black.copy(alpha = 0.9f)
                            )
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 32.sp
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.user?.username ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        maxLines = 1
                    )
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Rounded.Verified,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(16.dp)
                                .background(Color.White, CircleShape)
                                .padding(1.dp)
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val contextText = if (!track.genre.isNullOrBlank()) {
                    stringResource(R.string.home_discovery_context_genre, track.genre)
                } else {
                    stringResource(R.string.home_section_similar, track.user?.username ?: "Music")
                }

                Text(
                    text = contextText,
                    style = MaterialTheme.typography.labelMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun StationCardLarge(playlist: Playlist, onClick: () -> Unit) {
    val isLikedBy = playlist.permalinkUrl == "liked_by_marker"
    val isArtistStation = playlist.permalinkUrl == "artist_station_marker"

    val title = when {
        isLikedBy -> playlist.title
        isArtistStation -> playlist.user?.username
        else -> playlist.title
    } ?: stringResource(R.string.untitled_track)
    val subtitle = when {
        isLikedBy -> stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0)
        isArtistStation -> stringResource(R.string.home_artist_station_subtitle)
        else -> playlist.user?.username ?: stringResource(R.string.lib_playlists)
    }

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.width(180.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .height(180.dp)
                    .fillMaxWidth()
            ) {
                AsyncImage(
                    model = playlist.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.8f), CircleShape)
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = if (isArtistStation) Icons.Default.Radio else Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun TrackCardModern(track: Track, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(160.dp)
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = track.title ?: stringResource(R.string.untitled_track),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.user?.username ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun SearchSourceSelector(
    selectedSource: SearchSource,
    onSelect: (SearchSource) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var isSourceMenuExpanded by remember { mutableStateOf(false) }

    Box {
        FilledTonalIconButton(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isSourceMenuExpanded = true
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(44.dp)
        ) {
            val iconRes = when (selectedSource) {
                SearchSource.SOUNDCLOUD -> R.drawable.ic_soundcloud
                SearchSource.YOUTUBE -> R.drawable.ic_logo_youtube
                SearchSource.SPOTIFY -> R.drawable.ic_logo_spotify
            }
            Icon(
                painter = androidx.compose.ui.res.painterResource(iconRes),
                contentDescription = "Change Search Source",
                modifier = Modifier.size(22.dp)
            )
        }

        DropdownMenu(
            expanded = isSourceMenuExpanded,
            onDismissRequest = { isSourceMenuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_source_soundcloud)) },
                leadingIcon = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_soundcloud),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(SearchSource.SOUNDCLOUD)
                    isSourceMenuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_source_youtube)) },
                leadingIcon = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_logo_youtube),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(SearchSource.YOUTUBE)
                    isSourceMenuExpanded = false
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.search_source_spotify)) },
                leadingIcon = {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_logo_spotify),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onSelect(SearchSource.SPOTIFY)
                    isSourceMenuExpanded = false
                }
            )
        }
    }
}

fun getStationNavId(playlist: Playlist): String {
    val isLikedBy = playlist.permalinkUrl == "liked_by_marker"
    val isArtistStation = playlist.permalinkUrl == "artist_station_marker"
    val isTrackStation = playlist.permalinkUrl == "track_station_marker"
    val isYoutubeRadio = playlist.permalinkUrl?.startsWith("yt_radio:") == true
    val isSpotifyRadio = playlist.permalinkUrl?.startsWith("spotify_radio:") == true || playlist.permalinkUrl?.startsWith("spotify:") == true
    val isSystemPlaylist = playlist.urn?.startsWith("soundcloud:system-playlists:") == true

    return when {
        isSystemPlaylist -> "system_playlist:${playlist.urn}"
        isLikedBy -> "liked_by:${playlist.id}"
        isArtistStation -> "station_artist:${playlist.id}"
        isYoutubeRadio -> playlist.permalinkUrl!!
        isSpotifyRadio -> playlist.permalinkUrl!!
        isTrackStation -> "station:${playlist.id}"
        else -> playlist.id.toString()
    }
}

@Composable
fun SearchCategoriesGrid(
    moods: List<SearchCategory>,
    genres: List<SearchCategory>,
    onCategoryClick: (SearchCategory) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.search_section_moods),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )
        }

        items(moods) { category ->
            SearchCategoryCard(category = category) { onCategoryClick(category) }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = stringResource(R.string.search_section_genres),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp)
            )
        }

        items(genres) { category ->
            SearchCategoryCard(category = category) { onCategoryClick(category) }
        }
    }
}

@Composable
fun SearchCategoryCard(
    category: SearchCategory,
    onClick: () -> Unit
) {
    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Card(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(110.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = category.icon,
                contentDescription = null,
                tint = contentColor.copy(alpha = 0.15f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(100.dp)
                    .offset(x = 20.dp, y = 20.dp)
                    .graphicsLayer {
                        rotationZ = -10f
                        alpha = 0.5f
                    }
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = if (category.titleRes != 0) stringResource(category.titleRes) else category.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.5.sp
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(
                            color = contentColor.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = contentColor
                    )
                }
            }
        }
    }
}

@Composable
fun SearchFilters(activeFilter: SearchFilter, onFilterSelected: (SearchFilter) -> Unit) {
    val filters = SearchFilter.entries
    ExpressiveConnectedButtonGroup(
        options = filters,
        selectedOption = activeFilter,
        onOptionSelected = onFilterSelected,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
        labelProvider = { filter ->
            Text(
                text = when (filter) {
                    SearchFilter.ALL -> stringResource(R.string.all_filters)
                    SearchFilter.TRACKS -> stringResource(R.string.profile_tracks)
                    SearchFilter.ARTISTS -> stringResource(R.string.lib_artists)
                    SearchFilter.PLAYLISTS -> stringResource(R.string.lib_playlists)
                },
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}

@Composable
fun SearchResultsList(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit,
    activeFilter: SearchFilter = homeViewModel.activeFilter
) {
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val downloadedIds by DownloadManager.downloadedIds.collectAsState()
    val context = LocalContext.current
    var selectedPlaylistForMenu by remember { mutableStateOf<Playlist?>(null) }
    when (homeViewModel.activeSearchSource) {
        SearchSource.YOUTUBE -> {
            val listState = rememberLazyListState()
            if (homeViewModel.searchResultsYoutube.isEmpty()) Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            else LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 180.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                val isScrolling = listState.isScrollInProgress
                itemsIndexed(homeViewModel.searchResultsYoutube) { index, track ->
                    StaggeredItem(index, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                        val isDownloaded = downloadedIds.contains(track.id)
                        TrackListItem(
                            track = track,
                            currentlyPlayingTrack = playerViewModel.currentTrack,
                            index = index,
                            isDownloading = false,
                            isDownloaded = isDownloaded,
                            downloadProgress = 0,
                            onClick = { playerViewModel.playPlaylist(listOf(track), 0) },
                            onOptionClick = { playerViewModel.showTrackOptions(track) })
                    }
                }
            }
        }

        SearchSource.SPOTIFY -> {
            val listState = rememberLazyListState()
            val isScrolling = listState.isScrollInProgress
            var globalIndex = 0

            val hasTracks = homeViewModel.searchResultsSpotify.isNotEmpty()
            val hasArtists = homeViewModel.searchResultsSpotifyArtists.isNotEmpty()
            val hasPlaylistsOrAlbums = homeViewModel.searchResultsSpotifyPlaylists.isNotEmpty() || homeViewModel.searchResultsSpotifyAlbums.isNotEmpty()

            if (!hasTracks && !hasArtists && !hasPlaylistsOrAlbums) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 180.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // ARTISTS SECTION
                    if (hasArtists && (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.ARTISTS)) {
                        if (activeFilter == SearchFilter.ALL) {
                            item {
                                val idx = globalIndex++
                                StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                                    Text(
                                        stringResource(R.string.lib_artists),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(16.dp)
                                    )
                                }
                            }
                            item {
                                val idx = globalIndex++
                                StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        items(homeViewModel.searchResultsSpotifyArtists) { artist ->
                                            ArtistCircle(artist.toUser()) {
                                                onNavigate("spotify_artist:${artist.id}")
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (activeFilter == SearchFilter.ARTISTS) {
                            items(homeViewModel.searchResultsSpotifyArtists) { artist ->
                                val idx = globalIndex++
                                StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onNavigate("spotify_artist:${artist.id}")
                                            }
                                            .padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ArtistAvatar(
                                            avatarUrl = artist.avatarUrl,
                                            enableViewer = false,
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(CircleShape)
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    artist.name,
                                                    style = MaterialTheme.typography.bodyLarge,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (artist.verified) {
                                                    Spacer(Modifier.width(6.dp))
                                                    Icon(
                                                        Icons.Rounded.Verified,
                                                        contentDescription = null,
                                                        tint = Color(0xFF1DB954),
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                            if (artist.monthlyListeners != null && artist.monthlyListeners > 0) {
                                                Text(
                                                    text = "${artist.monthlyListeners} ${stringResource(R.string.profile_followers)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // TRACKS SECTION
                    if (hasTracks && (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.TRACKS)) {
                        if (activeFilter == SearchFilter.ALL) {
                            item {
                                val idx = globalIndex++
                                StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                                    Text(
                                        stringResource(R.string.profile_tracks),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                                    )
                                }
                            }
                        }
                        itemsIndexed(homeViewModel.searchResultsSpotify) { index, track ->
                            val idx = globalIndex++
                            StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                                val isDownloaded = downloadedIds.contains(track.id)
                                TrackListItem(
                                    track = track,
                                    currentlyPlayingTrack = playerViewModel.currentTrack,
                                    index = index,
                                    isDownloading = false,
                                    isDownloaded = isDownloaded,
                                    downloadProgress = 0,
                                    onClick = { playerViewModel.playPlaylist(listOf(track), 0) },
                                    onOptionClick = { playerViewModel.showTrackOptions(track) }
                                )
                            }
                        }
                    }

                    // PLAYLISTS & ALBUMS SECTION
                    if (hasPlaylistsOrAlbums && (activeFilter == SearchFilter.ALL || activeFilter == SearchFilter.PLAYLISTS)) {
                        val allPlaylists = homeViewModel.searchResultsSpotifyPlaylists.map { it.toPlaylist() } +
                                homeViewModel.searchResultsSpotifyAlbums.map { it.toPlaylist() }

                        if (activeFilter == SearchFilter.ALL) {
                            item {
                                val idx = globalIndex++
                                StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                                    Text(
                                        stringResource(R.string.lib_playlists),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                                    )
                                }
                            }
                            item {
                                val idx = globalIndex++
                                StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                                    LazyRow(
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                         items(allPlaylists) { playlist ->
                                            SquareCard(playlist) {
                                                val navId = playlist.urn ?: if (playlist.isAlbum) "spotify:album:${playlist.permalink ?: playlist.id}" else "spotify:playlist:${playlist.permalink ?: playlist.id}"
                                                onNavigate(navId)
                                            }
                                        }
                                    }
                                }
                            }
                        } else if (activeFilter == SearchFilter.PLAYLISTS) {
                            items(allPlaylists) { playlist ->
                                val idx = globalIndex++
                                StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                                    DynamicPlaylistCard(
                                        playlist = playlist,
                                        isGrid = false,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        onOptionClick = { selectedPlaylistForMenu = playlist },
                                        onClick = {
                                            val navId = playlist.urn ?: if (playlist.isAlbum) "spotify:album:${playlist.permalink ?: playlist.id}" else "spotify:playlist:${playlist.permalink ?: playlist.id}"
                                            onNavigate(navId)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SearchSource.SOUNDCLOUD -> {
            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo;
                    val totalItems = layoutInfo.totalItemsCount;
                    val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
                        ?: 0; totalItems > 0 && lastVisibleItemIndex >= totalItems - 5 && activeFilter != SearchFilter.ALL
                }
            }
            LaunchedEffect(shouldLoadMore) { if (shouldLoadMore && !homeViewModel.isSearchLoadingMore) homeViewModel.loadMoreSearchResults() }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = 180.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                var globalIndex = 0
                val isScrolling = listState.isScrollInProgress
                if (homeViewModel.searchResultsArtists.isNotEmpty()) {
                    if (activeFilter == SearchFilter.ALL) item {
                        val idx = globalIndex++; StaggeredItem(
                        idx,
                        key = homeViewModel.searchQuery,
                        isScrolling = isScrolling
                    ) {
                        Text(
                            stringResource(R.string.lib_artists),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    }
                    if (activeFilter == SearchFilter.ARTISTS) items(homeViewModel.searchResultsArtists) { artist ->
                        val idx = globalIndex++; StaggeredItem(
                        idx,
                        key = homeViewModel.searchQuery,
                        isScrolling = isScrolling
                    ) {
                        Row(
                            modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate(artist.profileNavId) }
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            ArtistAvatar(
                                avatarUrl = artist.avatarUrl, enableViewer = false, modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                            ); Spacer(Modifier.width(16.dp)); Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    artist.username ?: stringResource(R.string.unknown_artist),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold
                                ); if (artist.verified) {
                                Spacer(Modifier.width(4.dp)); Icon(
                                    Icons.Rounded.Verified,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            }; Text(
                            text = "${artist.followersCount} ${stringResource(R.string.profile_followers)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        }
                        }
                    }
                    }
                    else item {
                        val idx = globalIndex++; StaggeredItem(
                        idx,
                        key = homeViewModel.searchQuery,
                        isScrolling = isScrolling
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) { items(homeViewModel.searchResultsArtists) { artist -> ArtistCircle(artist) { onNavigate(artist.profileNavId) } } }
                    }
                    }
                }
                if (homeViewModel.searchResultsTracks.isNotEmpty()) {
                    val isScrolling = listState.isScrollInProgress
                    if (activeFilter == SearchFilter.ALL) item {
                        val idx = globalIndex++; StaggeredItem(
                        idx,
                        key = homeViewModel.searchQuery,
                        isScrolling = isScrolling
                    ) {
                        Text(
                            stringResource(R.string.profile_tracks),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }
                    }
                    itemsIndexed(homeViewModel.searchResultsTracks) { index, track ->
                        val idx = globalIndex++; StaggeredItem(
                        idx,
                        key = homeViewModel.searchQuery,
                        isScrolling = isScrolling
                    ) {
                        TrackListItem(
                            track = track,
                            currentlyPlayingTrack = playerViewModel.currentTrack,
                            index = index,
                            isDownloading = downloadProgress[track.id] != null,
                            isDownloaded = downloadedIds.contains(track.id),
                            downloadProgress = downloadProgress[track.id] ?: 0,
                            onClick = { playerViewModel.playPlaylist(listOf(track), 0) },
                            onOptionClick = { playerViewModel.showTrackOptions(track) })
                    }
                    }
                }
                if (homeViewModel.searchResultsPlaylists.isNotEmpty()) {
                    val isScrolling = listState.isScrollInProgress
                    if (activeFilter == SearchFilter.ALL) item {
                        val idx = globalIndex++; StaggeredItem(
                        idx,
                        key = homeViewModel.searchQuery,
                        isScrolling = isScrolling
                    ) {
                        Text(
                            stringResource(R.string.lib_playlists),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                        )
                    }
                    }
                    if (activeFilter == SearchFilter.PLAYLISTS) items(homeViewModel.searchResultsPlaylists) { playlist ->
                        val idx = globalIndex++
                        StaggeredItem(idx, key = homeViewModel.searchQuery, isScrolling = isScrolling) {
                            DynamicPlaylistCard(
                                playlist = playlist,
                                isGrid = false,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                onOptionClick = { selectedPlaylistForMenu = playlist },
                                onClick = { onNavigate(playlist.id.toString()) }
                            )
                        }
                    }
                    else item {
                        val idx = globalIndex++; StaggeredItem(
                        idx,
                        key = homeViewModel.searchQuery,
                        isScrolling = isScrolling
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(homeViewModel.searchResultsPlaylists) { playlist ->
                                SquareCard(playlist) {
                                    onNavigate(
                                        playlist.id.toString()
                                    )
                                }
                            }
                        }
                    }
                    }
                }
                if (homeViewModel.searchResultsTracks.isEmpty() && homeViewModel.searchResultsArtists.isEmpty() && homeViewModel.searchResultsPlaylists.isEmpty()) item {
                    Box(
                        modifier = Modifier.fillParentMaxSize(),
                        contentAlignment = Alignment.Center
                    ) { Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                if (homeViewModel.isSearchLoadingMore) item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp), contentAlignment = Alignment.Center
                    ) { ContainedLoadingIndicator() }
                }
            }
        }
    }

    if (selectedPlaylistForMenu != null) {
        val playlist = selectedPlaylistForMenu!!
        val permalink =
            playlist.permalinkUrl ?: if (playlist.id > 0) "https://soundcloud.com/playlists/${playlist.id}" else ""
        val likedPlaylistsRepo by LikeRepository.likedPlaylists.collectAsState()
        val isPlaylistLiked = remember(playlist.id, likedPlaylistsRepo) {
            LikeRepository.isPlaylistLiked(playlist.id)
        }
        val primaryColor = MaterialTheme.colorScheme.primary

        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { selectedPlaylistForMenu = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    AsyncImage(
                        model = playlist.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stringResource(R.string.lib_playlists)} • ${
                                playlist.user?.username ?: stringResource(
                                    R.string.me_artist
                                )
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                val coroutineScope = rememberCoroutineScope()
                fun playHelper(shuffle: Boolean = false, insertNext: Boolean = false, addToQueue: Boolean = false) {
                    coroutineScope.launch(Dispatchers.IO) {
                        val tracks: List<Track> = if (!playlist.tracks.isNullOrEmpty()) {
                            playlist.tracks!!
                        } else if (playlist.id > 0) {
                            try {
                                val online = com.alananasss.kittytune.data.network.RetrofitClient.create(context)
                                    .getPlaylist(playlist.id)
                                online.tracks ?: emptyList()
                            } catch (e: Exception) {
                                emptyList()
                            }
                        } else emptyList()

                        if (tracks.isNotEmpty()) {
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                if (shuffle) {
                                    playerViewModel.playPlaylist(tracks.shuffled(), 0)
                                } else if (insertNext) {
                                    playerViewModel.insertNext(tracks)
                                } else if (addToQueue) {
                                    playerViewModel.addToQueue(tracks)
                                } else {
                                    playerViewModel.playPlaylist(tracks, 0)
                                }
                            }
                        }
                    }
                }

                val playlistActionItems = remember(playlist, isPlaylistLiked, primaryColor) {
                    listOf(
                        Triple(Icons.Rounded.PlayArrow, context.getString(R.string.btn_play)) {
                            playHelper(shuffle = false)
                            selectedPlaylistForMenu = null
                        },
                        Triple(Icons.Default.Shuffle, context.getString(R.string.btn_shuffle)) {
                            playHelper(shuffle = true)
                            selectedPlaylistForMenu = null
                        },
                        Triple(Icons.AutoMirrored.Rounded.PlaylistPlay, context.getString(R.string.menu_play_next)) {
                            playHelper(insertNext = true)
                            selectedPlaylistForMenu = null
                        },
                        Triple(Icons.AutoMirrored.Rounded.QueueMusic, context.getString(R.string.menu_add_queue)) {
                            playHelper(addToQueue = true)
                            selectedPlaylistForMenu = null
                        },
                        Triple(
                            if (isPlaylistLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            if (isPlaylistLiked) context.getString(R.string.action_unlike) else context.getString(R.string.player_like_action)
                        ) {
                            if (!isPlaylistLiked) {
                                    val isSpotify = playlist.permalinkUrl?.contains("spotify") == true || playlist.urn?.startsWith("spotify:") == true
                                    com.alananasss.kittytune.data.DownloadManager.importPlaylistToLibrary(
                                        playlist = playlist,
                                        tracks = playlist.tracks ?: emptyList(),
                                        syncToCloud = !isSpotify
                                    )
                            } else {
                                LikeRepository.togglePlaylistLike(
                                    playlist.id,
                                    false,
                                    playlist.permalinkUrl ?: "",
                                    playlist.urn ?: ""
                                )
                            }
                        },
                        Triple(Icons.Outlined.Share, context.getString(R.string.btn_share)) {
                            if (permalink.isNotEmpty()) {
                                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, permalink)
                                }
                                context.startActivity(
                                    android.content.Intent.createChooser(
                                        shareIntent,
                                        context.getString(R.string.btn_share)
                                    )
                                )
                            }
                            selectedPlaylistForMenu = null
                        }
                    )
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(playlistActionItems) { (icon, text, action) ->
                        val tint =
                            if (text == context.getString(R.string.action_unlike)) primaryColor else MaterialTheme.colorScheme.onSurface
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { action() }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = text,
                                modifier = Modifier.size(32.dp),
                                tint = tint
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = text,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = tint
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StaggeredItem(
    index: Int,
    key: Any? = null,
    isScrolling: Boolean = false,
    content: @Composable () -> Unit
) {
    var hasAnimated by rememberSaveable(key) { mutableStateOf(false) }

    val skipAnimation = hasAnimated || index >= 8 || isScrolling

    val alpha = remember(key) { Animatable(if (skipAnimation) 1f else 0f) }
    val offsetY = remember(key) { Animatable(if (skipAnimation) 0f else 20f) }
    val scale = remember(key) { Animatable(if (skipAnimation) 1f else 0.95f) }

    LaunchedEffect(key) {
        if (!skipAnimation) {
            val staggerDelay = index * 30L
            delay(staggerDelay)
            val a = launch { alpha.animateTo(1f, animationSpec = tween(200)) }
            val o = launch { offsetY.animateTo(0f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) }
            val s = launch { scale.animateTo(1f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) }
            a.join()
            o.join()
            s.join()
            hasAnimated = true
        }
    }

    Box(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha.value
                this.translationY = offsetY.value * density
                this.scaleX = scale.value
                this.scaleY = scale.value
            }
    ) {
        content()
    }
}

@Composable
fun rememberMonetColor(key: String): Color {
    val isDark = isSystemInDarkTheme()
    return remember(key, isDark) {
        val hue = abs(key.hashCode()) % 360f
        val saturation = if (isDark) 0.5f else 0.6f
        val lightness = if (isDark) 0.35f else 0.85f
        Color.hsl(hue, saturation, lightness)
    }
}

@Composable
fun getCategoryGradient(seedColor: Color): Brush {
    val isDark = isSystemInDarkTheme()
    val startAlpha = if (isDark) 0.4f else 0.3f
    val endAlpha = if (isDark) 0.1f else 0.05f
    return Brush.linearGradient(
        colors = listOf(seedColor.copy(alpha = startAlpha), seedColor.copy(alpha = endAlpha)),
        start = androidx.compose.ui.geometry.Offset.Zero,
        end = androidx.compose.ui.geometry.Offset.Infinite
    )
}

@Composable
fun ArtistCircle(user: User, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }) {
        ArtistAvatar(
            avatarUrl = user.avatarUrl, enableViewer = false, modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Text(
                text = user.username ?: stringResource(R.string.unknown_artist),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (user.verified) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Rounded.Verified,
                    null,
                    tint = if (user.urn?.startsWith("spotify") == true) Color(0xFF1DB954) else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
fun HomeScreenShimmer() {
    LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
        item {
            Spacer(modifier = Modifier.height(28.dp)); ShimmerLine(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(200.dp)
                .height(24.dp)
        ); Spacer(modifier = Modifier.height(16.dp)); LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) { items(5) { SquareCardShimmer() } }; Spacer(Modifier.height(32.dp))
        }
        item {
            ShimmerLine(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(250.dp)
                    .height(24.dp)
            ); Spacer(modifier = Modifier.height(16.dp)); LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) { items(5) { SquareCardShimmer() } }; Spacer(Modifier.height(40.dp))
        }
        item {
            ShimmerLine(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .width(180.dp)
                    .height(24.dp)
            ); Spacer(modifier = Modifier.height(16.dp)); LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) { items(5) { ArtistCircleShimmer() } }; Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun HistoryCard(
    item: HistoryItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.width(280.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                when (item.id) {
                    "likes" -> Icon(
                        Icons.Rounded.Favorite,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )

                    "downloads" -> Icon(
                        Icons.Rounded.Folder,
                        null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(32.dp)
                    )

                    else -> {
                        if (item.type == "PROFILE") {
                            ArtistAvatar(
                                avatarUrl = item.imageUrl,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                enableViewer = false
                            )
                        } else if (!item.imageUrl.isNullOrBlank()) {
                            val imageModel = if (item.imageUrl.startsWith("/")) {
                                java.io.File(item.imageUrl)
                            } else {
                                item.imageUrl
                            }
                            AsyncImage(
                                model = imageModel,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun OfflineHomeView(
    onRetry: () -> Unit,
    onGoToDownloads: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            text = stringResource(R.string.home_offline_title),
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.5).sp
            ),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(12.dp))

        Text(
            text = stringResource(R.string.home_offline_desc),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(48.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            Icon(Icons.Rounded.Refresh, null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.home_offline_btn_retry), fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        FilledTonalButton(
            onClick = onGoToDownloads,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shapes = ButtonDefaults.shapes(),
            contentPadding = PaddingValues(horizontal = 24.dp)
        ) {
            Icon(Icons.Rounded.Download, null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.home_offline_btn_downloads), fontWeight = FontWeight.Bold)
        }
    }
}

