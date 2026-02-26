    package com.alananasss.kittytune.ui.home
    
    import android.net.Uri
    import androidx.activity.compose.BackHandler
    import androidx.compose.animation.*
    import androidx.compose.animation.core.Spring
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
    import androidx.compose.material.icons.automirrored.rounded.QueueMusic
    import androidx.compose.material.icons.filled.*
    import androidx.compose.material.icons.rounded.*
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
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
    import androidx.compose.ui.text.input.ImeAction
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.lerp
    import androidx.compose.ui.unit.sp
    import androidx.compose.ui.util.lerp
    import coil.compose.AsyncImage
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.DownloadManager
    import com.alananasss.kittytune.data.SearchCategory
    import com.alananasss.kittytune.data.local.HistoryItem
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import androidx.compose.foundation.lazy.grid.itemsIndexed
    import com.alananasss.kittytune.ui.common.ArtistCircleShimmer
    import com.alananasss.kittytune.ui.common.ShimmerLine
    import com.alananasss.kittytune.ui.common.SquareCardShimmer
    import com.alananasss.kittytune.ui.library.DynamicPlaylistCard
    import com.alananasss.kittytune.ui.library.TrackListItem
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.profile.ArtistAvatar
    import com.alananasss.kittytune.ui.profile.SquareCard
    import kotlinx.coroutines.delay
    import java.io.File
    import kotlin.math.abs
    import kotlin.math.absoluteValue
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
    
        LaunchedEffect(homeViewModel.isSearching) {
            if (homeViewModel.isSearching) {
                delay(100)
                focusRequester.requestFocus()
                keyboardController?.show()
            } else {
                focusManager.clearFocus()
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
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .statusBarsPadding()
                        .padding(bottom = 8.dp)
                        .animateContentSize(animationSpec = tween(durationMillis = 400))
                ) {
                    HomeSearchBar(
                        query = homeViewModel.searchQuery,
                        onQueryChange = homeViewModel::onSearchQueryChanged,
                        isSearching = homeViewModel.isSearching,
                        onSearchFocus = { homeViewModel.activateSearch() },
                        onBackClick = {
                            homeViewModel.clearSearch()
                            focusManager.clearFocus()
                        },
                        avatarUrl = userProfile?.avatarUrl,
                        onProfileClick = {
                            if (userProfile != null) {
                                onNavigate("my_profile_menu")
                            } else {
                                onNavigate("login_required")
                            }
                        },
                        focusRequester = focusRequester
                    )
    
                    AnimatedVisibility(visible = homeViewModel.isSearching) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = homeViewModel.activeSearchSource == SearchSource.SOUNDCLOUD,
                                enter = fadeIn(),
                                exit = fadeOut()
                            ) {
                                SearchFilters(
                                    activeFilter = homeViewModel.activeFilter,
                                    onFilterSelected = homeViewModel::onFilterChanged
                                )
                            }
    
                            Spacer(Modifier.weight(1f))
    
                            SearchSourceSelector(
                                selectedSource = homeViewModel.activeSearchSource,
                                onSelect = homeViewModel::onSearchSourceChanged
                            )
                        }
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
    
            Box(modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()) {
                if (homeViewModel.isLoading && homeViewModel.homeSections.isEmpty()) {
                    HomeScreenShimmer()
                } else {
                    Crossfade(
                        targetState = homeViewModel.isSearching,
                        animationSpec = tween(durationMillis = 400),
                        label = "HomeSearchCrossfade"
                    ) { isSearching ->
                        if (isSearching) {
                            if (homeViewModel.searchQuery.isEmpty()) {
                                SearchCategoriesGrid(
                                    moods = homeViewModel.moodCategories,
                                    genres = homeViewModel.genreCategories,
                                    onCategoryClick = { category ->
                                        val encodedTitle = Uri.encode(category.title)
                                        val encodedQuery = Uri.encode(category.query)
                                        onNavigate("genre_playlists/$encodedTitle/$encodedQuery")
                                    }
                                )
                            } else if (homeViewModel.isSearchLoading) {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator()
                                }
                            } else {
                                SearchResultsList(
                                    homeViewModel = homeViewModel,
                                    playerViewModel = playerViewModel,
                                    onNavigate = onNavigate
                                )
                            }
                        } else {
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

        val usedSections = setOfNotNull(discoverySection, recommendedSection, rediscoverSection, habitsSection, stationsSection, albumsSection, similarSection)
        val remainingSections = allSections.filter { !usedSections.contains(it) }

        LazyColumn(
            state = scrollState,
            contentPadding = PaddingValues(bottom = 120.dp),
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

            if (history.isNotEmpty()) {
                item {
                    StandardHorizontalSection(
                        title = stringResource(R.string.home_recently_played),
                        subtitle = null
                    ) {
                        items(history.take(8)) { historyItem ->
                            HistoryCard(
                                item = historyItem,
                                onClick = {
                                    when {
                                        historyItem.id == "likes" -> onNavigate("likes")
                                        historyItem.id == "downloads" -> onNavigate("downloads")
                                        historyItem.id.startsWith("yt_radio:") -> onNavigate(historyItem.id)
                                        historyItem.type == "STATION" -> onNavigate(historyItem.id)
                                        historyItem.type == "PROFILE" -> onNavigate(historyItem.id)
                                        historyItem.type == "PLAYLIST" -> onNavigate(historyItem.id.replace("playlist:", ""))
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
                            ArtistCircle(artist) { onNavigate("profile:${artist.id}") }
                        }
                    }
                }
                else -> {}
            }
        }
    }
    
    @Composable
    fun ExplorerSection(onNavigate: (String) -> Unit) {
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
                modifier = Modifier.fillMaxWidth(),
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
                .clip(RoundedCornerShape(20.dp))
                .clickable { onClick() }
        ) {
            Surface(
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
                SuggestionChip(
                    onClick = { onCategoryClick(category) },
                    label = { Text(category.title, fontWeight = FontWeight.Medium) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        labelColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = null,
                    shape = RoundedCornerShape(12.dp)
                )
            }
        }
    }
    
    @Composable
    fun QuickHistorySection(
        history: List<HistoryItem>,
        onItemClick: (HistoryItem) -> Unit
    ) {
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
                items(history) { item ->
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
        content: LazyListScope.() -> Unit
    ) {
        Column {
            Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                Box(modifier = Modifier
                    .height(180.dp)
                    .fillMaxWidth()) {
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
                            imageVector = if(isArtistStation) Icons.Default.Radio else Icons.AutoMirrored.Rounded.QueueMusic,
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
        Column(modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)) {
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
        var isSourceMenuExpanded by remember { mutableStateOf(false) }
    
        Box {
            FilledTonalIconButton(
                onClick = { isSourceMenuExpanded = true },
                shape = CircleShape,
                modifier = Modifier.size(44.dp)
            ) {
                val icon = if (selectedSource == SearchSource.SOUNDCLOUD) Icons.Default.CloudQueue else Icons.Default.SmartDisplay
                Icon(icon, contentDescription = "Change Search Source", modifier = Modifier.size(24.dp))
            }
    
            DropdownMenu(
                expanded = isSourceMenuExpanded,
                onDismissRequest = { isSourceMenuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_source_soundcloud)) },
                    leadingIcon = { Icon(Icons.Default.CloudQueue, null) },
                    onClick = {
                        onSelect(SearchSource.SOUNDCLOUD)
                        isSourceMenuExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_source_youtube)) },
                    leadingIcon = { Icon(Icons.Default.SmartDisplay, null) },
                    onClick = {
                        onSelect(SearchSource.YOUTUBE)
                        isSourceMenuExpanded = false
                    }
                )
            }
        }
    }
    
    @Composable
    fun HomeSearchBar(
        query: String,
        onQueryChange: (String) -> Unit,
        isSearching: Boolean,
        onSearchFocus: () -> Unit,
        onBackClick: () -> Unit,
        avatarUrl: String?,
        onProfileClick: () -> Unit,
        focusRequester: FocusRequester
    ) {
        val focusManager = LocalFocusManager.current
    
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedContent(
                targetState = isSearching,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                },
                label = "SearchIconAnimation"
            ) { searching ->
                if (searching) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.btn_close),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                } else {
                    IconButton(onClick = onSearchFocus) {
                        Icon(
                            Icons.Default.Search,
                            stringResource(R.string.search_hint),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
    
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = {
                    Text(
                        stringResource(R.string.search_hint),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused) {
                            onSearchFocus()
                        }
                    },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )
    
            AnimatedContent(
                targetState = query.isNotEmpty(),
                transitionSpec = {
                    fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                            fadeOut(animationSpec = tween(90))
                },
                label = "RightIconAnimation"
            ) { hasQuery ->
                if (hasQuery) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Default.Close,
                            stringResource(R.string.btn_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    IconButton(onClick = onProfileClick) {
                        if (avatarUrl != null) {
                            ArtistAvatar(
                                avatarUrl = avatarUrl,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Default.AccountCircle,
                                contentDescription = stringResource(R.string.guest_user),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }
        }
    }
    fun getStationNavId(playlist: Playlist): String {
        val isLikedBy = playlist.permalinkUrl == "liked_by_marker"
        val isArtistStation = playlist.permalinkUrl == "artist_station_marker"
        val isTrackStation = playlist.permalinkUrl == "track_station_marker"
    
        return when {
            isLikedBy -> "liked_by:${playlist.id}"
            isArtistStation -> "station_artist:${playlist.id}"
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
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            // --- Section MOODS ---
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.search_section_moods),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
                )
            }
    
            // Utilisation simple de 'items'
            items(moods) { category ->
                SearchCategoryCard(category = category) { onCategoryClick(category) }
            }
    
            // --- Section GENRES ---
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
        // --- CHANGEMENT ICI : On utilise la variante SECONDAIRE ---
        val containerColor = MaterialTheme.colorScheme.secondaryContainer
        val contentColor = MaterialTheme.colorScheme.onSecondaryContainer
    
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(28.dp), // Forme Expressive
            colors = CardDefaults.cardColors(
                containerColor = containerColor,
                contentColor = contentColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Icône en filigrane (Watermark)
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
                    // Titre
                    Text(
                        text = category.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.5.sp
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
    
                    // Bouton flèche
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
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            items(SearchFilter.entries) { filter ->
                val label = when (filter) {
                    SearchFilter.ALL -> stringResource(R.string.all_filters)
                    SearchFilter.TRACKS -> stringResource(R.string.profile_tracks)
                    SearchFilter.ARTISTS -> stringResource(R.string.lib_artists)
                    SearchFilter.PLAYLISTS -> stringResource(R.string.lib_playlists)
                }
                FilterChip(
                    selected = activeFilter == filter, onClick = { onFilterSelected(filter) }, label = { Text(label, maxLines = 1) },
                    shape = CircleShape, colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer, selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer), border = null
                )
            }
        }
    }
    
    @Composable
    fun SearchResultsList(homeViewModel: HomeViewModel, playerViewModel: PlayerViewModel, onNavigate: (String) -> Unit) {
        val downloadProgress by DownloadManager.downloadProgress.collectAsState()
        val context = LocalContext.current
        when (homeViewModel.activeSearchSource) {
            SearchSource.YOUTUBE -> {
                if (homeViewModel.searchResultsYoutube.isEmpty()) Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                else LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(homeViewModel.searchResultsYoutube) { index, track ->
                        TrackListItem(track = track, currentlyPlayingTrack = playerViewModel.currentTrack, index = index, isDownloading = false, isDownloaded = false, downloadProgress = 0, onClick = { playerViewModel.playPlaylist(listOf(track), 0)  }, onOptionClick = { playerViewModel.showTrackOptions(track) })
                    }
                }
            }
            SearchSource.SOUNDCLOUD -> {
                val listState = rememberLazyListState()
                val shouldLoadMore by remember { derivedStateOf { val layoutInfo = listState.layoutInfo; val totalItems = layoutInfo.totalItemsCount; val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0; totalItems > 0 && lastVisibleItemIndex >= totalItems - 5 && homeViewModel.activeFilter != SearchFilter.ALL } }
                LaunchedEffect(shouldLoadMore) { if (shouldLoadMore && !homeViewModel.isSearchLoadingMore) homeViewModel.loadMoreSearchResults() }
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
                    if (homeViewModel.searchResultsArtists.isNotEmpty()) {
                        if (homeViewModel.activeFilter == SearchFilter.ALL) item { Text(stringResource(R.string.lib_artists), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(16.dp)) }
                        if (homeViewModel.activeFilter == SearchFilter.ARTISTS) items(homeViewModel.searchResultsArtists) { artist -> Row(modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("profile:${artist.id}") }
                            .padding(16.dp), verticalAlignment = Alignment.CenterVertically) { ArtistAvatar(avatarUrl = artist.avatarUrl, modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)); Spacer(Modifier.width(16.dp)); Column { Row(verticalAlignment = Alignment.CenterVertically) { Text(artist.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold); if (artist.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp)) } };             Text(
                            text = "${artist.followersCount} ${stringResource(R.string.profile_followers)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ) } } }
                        else item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(homeViewModel.searchResultsArtists) { artist -> ArtistCircle(artist) { onNavigate("profile:${artist.id}") } } } }
                    }
                    if (homeViewModel.searchResultsTracks.isNotEmpty()) {
                        if (homeViewModel.activeFilter == SearchFilter.ALL) item { Text(stringResource(R.string.profile_tracks), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)) }
                        itemsIndexed(homeViewModel.searchResultsTracks) { index, track -> TrackListItem(track = track, currentlyPlayingTrack = playerViewModel.currentTrack, index = index, isDownloading = downloadProgress[track.id] != null, isDownloaded = File(context.filesDir, "track_${track.id}.mp3").exists(), downloadProgress = downloadProgress[track.id] ?: 0, onClick = { playerViewModel.playPlaylist(listOf(track), 0)  }, onOptionClick = { playerViewModel.showTrackOptions(track) }) }
                    }
                    if (homeViewModel.searchResultsPlaylists.isNotEmpty()) {
                        if (homeViewModel.activeFilter == SearchFilter.ALL) item { Text(stringResource(R.string.lib_playlists), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)) }
                        if (homeViewModel.activeFilter == SearchFilter.PLAYLISTS) items(homeViewModel.searchResultsPlaylists) { playlist -> DynamicPlaylistCard(playlist, isGrid = false) { onNavigate(playlist.id.toString()) } }
                        else item { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { items(homeViewModel.searchResultsPlaylists) { playlist -> SquareCard(playlist) { onNavigate(playlist.id.toString()) } } } }
                    }
                    if (homeViewModel.searchResultsTracks.isEmpty() && homeViewModel.searchResultsArtists.isEmpty() && homeViewModel.searchResultsPlaylists.isEmpty()) item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                    if (homeViewModel.isSearchLoadingMore) item { Box(modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                }
            }
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
        return Brush.linearGradient(colors = listOf(seedColor.copy(alpha = startAlpha), seedColor.copy(alpha = endAlpha)), start = androidx.compose.ui.geometry.Offset.Zero, end = androidx.compose.ui.geometry.Offset.Infinite)
    }
    
    @Composable
    fun ArtistCircle(user: User, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }) {
            ArtistAvatar(avatarUrl = user.avatarUrl, modifier = Modifier
                .size(120.dp)
                .clip(CircleShape))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                Text(text = user.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false))
                if (user.verified) { Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp)) }
            }
        }
    }
    
    @Composable
    fun HomeScreenShimmer() {
        LazyColumn(modifier = Modifier.fillMaxSize(), userScrollEnabled = false) {
            item { Spacer(modifier = Modifier.height(28.dp)); ShimmerLine(modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(200.dp)
                .height(24.dp)); Spacer(modifier = Modifier.height(16.dp)); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) { items(5) { SquareCardShimmer() } }; Spacer(Modifier.height(32.dp)) }
            item { ShimmerLine(modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(250.dp)
                .height(24.dp)); Spacer(modifier = Modifier.height(16.dp)); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) { items(5) { SquareCardShimmer() } }; Spacer(Modifier.height(40.dp)) }
            item { ShimmerLine(modifier = Modifier
                .padding(horizontal = 16.dp)
                .width(180.dp)
                .height(24.dp)); Spacer(modifier = Modifier.height(16.dp)); LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) { items(5) { ArtistCircleShimmer() } }; Spacer(Modifier.height(40.dp)) }
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
                        "likes" -> Icon(Icons.Rounded.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                        "downloads" -> Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(32.dp))
                        else -> {
                            if (item.type == "PROFILE") {
                                ArtistAvatar(
                                    avatarUrl = item.imageUrl,
                                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp))
                                )
                            } else {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
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


