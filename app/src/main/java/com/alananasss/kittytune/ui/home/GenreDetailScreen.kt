package com.alananasss.kittytune.ui.home

import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.audio.haptics.performAppHaptic
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.data.CategoryKind
import com.alananasss.kittytune.data.GenreData
import com.alananasss.kittytune.data.OfficialPlaylistsData
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.TrackListItemShimmer
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.SectionTitle
import com.alananasss.kittytune.ui.profile.SquareCard
import java.util.Locale
import com.alananasss.kittytune.ui.design.Button
import com.alananasss.kittytune.ui.design.FilledIconButton
import com.alananasss.kittytune.ui.design.FilledTonalButton
import com.alananasss.kittytune.ui.design.FilledTonalIconButton
import com.alananasss.kittytune.ui.design.IconButton
import com.alananasss.kittytune.ui.design.Surface
import com.alananasss.kittytune.ui.design.TopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenreDetailScreen(
    genreName: String,
    genreQuery: String,
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: GenreDetailViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var showCountrySelector by remember { mutableStateOf(false) }

    LaunchedEffect(genreName, genreQuery) {
        viewModel.loadData(genreName, genreQuery)
    }

    if (showCountrySelector) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showCountrySelector = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.navigationBarsPadding()
            ) {
                itemsIndexed(OfficialPlaylistsData.sources) { index, source ->
                    CountrySelectionCard(
                        countryName = source.countryName,
                        flagEmoji = source.flagEmoji,
                        isSelected = index == viewModel.selectedSourceIndex,
                        onClick = hapticAction(HapticKind.UI) {
                            viewModel.selectedSourceIndex = index
                            viewModel.loadOfficialPlaylists()
                            showCountrySelector = false
                        }
                    )
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.background)) {
                TopAppBar(
                    title = {
                        Text(
                            text = viewModel.genreTitle.ifBlank { genreName },
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBackClick,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    actions = {
                        val allCategoryTracks = remember(
                            viewModel.popularTracks.toList(),
                            viewModel.vibeTracks.toList(),
                            viewModel.introducingTracks.toList(),
                            viewModel.discoverMoreTracks.toList()
                        ) {
                            (viewModel.popularTracks + viewModel.vibeTracks + viewModel.introducingTracks + viewModel.discoverMoreTracks).distinctBy { it.id }
                        }
                        if (allCategoryTracks.isNotEmpty()) {
                            FilledTonalIconButton(
                                onClick = hapticAction(HapticKind.UI) {
                                    playerViewModel.prepareBulkAdd(allCategoryTracks)
                                },
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = stringResource(R.string.menu_add_playlist)
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )

                // Tab Row: "Tout", "En tendance", "Playlists", "Albums" (for genres) or "Tout", "Playlists" (for moods)
                if (viewModel.tabs.isNotEmpty()) {
                    PrimaryTabRow(
                        selectedTabIndex = viewModel.tabs.indexOf(viewModel.selectedTab).coerceAtLeast(0),
                        containerColor = MaterialTheme.colorScheme.background,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
                    ) {
                        viewModel.tabs.forEach { tab ->
                            Tab(
                                selected = viewModel.selectedTab == tab,
                                onClick = hapticAction(HapticKind.UI) { viewModel.selectTab(tab) },
                                text = {
                                    Text(
                                        text = stringResource(tab.titleRes),
                                        fontWeight = if (viewModel.selectedTab == tab) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        if (viewModel.isLoading && viewModel.popularTracks.isEmpty()) {
            LazyColumn(modifier = Modifier.padding(innerPadding)) {
                items(10) { TrackListItemShimmer() }
            }
        } else {
            when (viewModel.selectedTab) {
                CategoryTab.ALL -> {
                    CategoryAllTabContent(
                        innerPadding = innerPadding,
                        viewModel = viewModel,
                        playerViewModel = playerViewModel,
                        onNavigate = onNavigate
                    )
                }
                CategoryTab.TRENDING -> {
                    CategoryTrendingTabContent(
                        innerPadding = innerPadding,
                        viewModel = viewModel,
                        playerViewModel = playerViewModel
                    )
                }
                CategoryTab.PLAYLISTS -> {
                    CategoryPlaylistsTabContent(
                        innerPadding = innerPadding,
                        viewModel = viewModel,
                        onNavigate = onNavigate
                    )
                }
                CategoryTab.ALBUMS -> {
                    CategoryAlbumsTabContent(
                        innerPadding = innerPadding,
                        viewModel = viewModel,
                        onNavigate = onNavigate
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryAllTabContent(
    innerPadding: PaddingValues,
    viewModel: GenreDetailViewModel,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // 1. Hero Header Card
        item {
            CategoryHeroBanner(
                title = viewModel.genreTitle,
                category = viewModel.category,
                categoryKind = viewModel.categoryKind,
                hasPlayableTracks = viewModel.popularTracks.isNotEmpty() || viewModel.vibeTracks.isNotEmpty(),
                onPlayClick = {
                    val tracksToPlay = if (viewModel.vibeTracks.isNotEmpty()) viewModel.vibeTracks else viewModel.popularTracks
                    if (tracksToPlay.isNotEmpty()) {
                        playerViewModel.playPlaylist(tracksToPlay, 0)
                    }
                }
            )
        }

        // 2. Section: "Trending" (Horizontal 3-track column wall)
        val trendingTracks = if (viewModel.vibeTracks.isNotEmpty()) viewModel.vibeTracks else viewModel.popularTracks
        if (trendingTracks.isNotEmpty()) {
            item {
                CategorySectionHeader(
                    title = stringResource(R.string.category_tab_trending),
                    actionText = if (viewModel.tabs.contains(CategoryTab.TRENDING)) stringResource(R.string.category_see_all) else null,
                    onActionClick = hapticAction(HapticKind.UI) { viewModel.selectTab(CategoryTab.TRENDING) }
                )
            }
            item {
                val chunkedTracks = trendingTracks.chunked(3)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(chunkedTracks) { columnTracks ->
                        Column(
                            modifier = Modifier.width(280.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            columnTracks.forEach { track ->
                                TrendingWallTrackItem(
                                    track = track,
                                    isCurrent = playerViewModel.uiCurrentTrack?.id == track.id,
                                    onClick = hapticAction(HapticKind.UI) {
                                        val index = trendingTracks.indexOf(track).coerceAtLeast(0)
                                        playerViewModel.playPlaylist(trendingTracks, index)
                                    },
                                    onOptionClick = hapticAction(HapticKind.UI) { playerViewModel.showTrackOptions(track) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Section: "Introducing" (Spotlight preview card)
        val introPlaylist = viewModel.introducingPlaylist
        if (introPlaylist != null) {
            item {
                CategorySectionHeader(
                    title = stringResource(R.string.category_introducing)
                )
            }
            item {
                CategoryIntroducingCard(
                    playlist = introPlaylist,
                    tracks = viewModel.introducingTracks,
                    genreTitle = viewModel.genreTitle,
                    currentlyPlayingTrack = playerViewModel.uiCurrentTrack,
                    onPlayPlaylist = {
                        if (viewModel.introducingTracks.isNotEmpty()) {
                            playerViewModel.playPlaylist(viewModel.introducingTracks, 0)
                        } else {
                            onNavigate("playlist_detail/${introPlaylist.id}")
                        }
                    },
                    onTrackClick = { track, index ->
                        if (viewModel.introducingTracks.isNotEmpty()) {
                            playerViewModel.playPlaylist(viewModel.introducingTracks, index)
                        }
                    },
                    onTrackOptionClick = { track ->
                        playerViewModel.showTrackOptions(track)
                    },
                    onPlaylistClick = {
                        onNavigate("playlist_detail/${introPlaylist.id}")
                    }
                )
            }
        }

        // 4. Section: "Playlists"
        val displayedPlaylists = if (viewModel.officialPlaylists.isNotEmpty()) {
            viewModel.officialPlaylists
        } else {
            viewModel.communityPlaylists
        }
        if (displayedPlaylists.isNotEmpty()) {
            item {
                CategorySectionHeader(
                    title = stringResource(R.string.category_tab_playlists),
                    actionText = stringResource(R.string.category_see_all),
                    onActionClick = hapticAction(HapticKind.UI) { viewModel.selectTab(CategoryTab.PLAYLISTS) }
                )
            }
            val isMoodOrGallery = viewModel.categoryKind == CategoryKind.MOOD || viewModel.vibeTracks.isEmpty()
            if (isMoodOrGallery) {
                item {
                    val pairs = displayedPlaylists.take(6).chunked(2)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        pairs.forEach { pair ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                pair.forEach { playlist ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        CategoryPlaylistGridCard(
                                            playlist = playlist,
                                            showCloudBadge = viewModel.officialPlaylists.contains(playlist),
                                            onClick = hapticAction(HapticKind.UI) { onNavigate("playlist_detail/${playlist.id}") }
                                        )
                                    }
                                }
                                if (pair.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(displayedPlaylists) { playlist ->
                            CategoryPlaylistCard(
                                playlist = playlist,
                                showCloudBadge = viewModel.officialPlaylists.contains(playlist),
                                onClick = hapticAction(HapticKind.UI) { onNavigate("playlist_detail/${playlist.id}") }
                            )
                        }
                    }
                }
            }
        }

        // 5. Section: "Albums" (genres only)
        if (viewModel.albums.isNotEmpty() && viewModel.categoryKind == CategoryKind.GENRE) {
            item {
                CategorySectionHeader(
                    title = stringResource(R.string.category_tab_albums),
                    actionText = stringResource(R.string.category_see_all),
                    onActionClick = hapticAction(HapticKind.UI) { viewModel.selectTab(CategoryTab.ALBUMS) }
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(viewModel.albums) { album ->
                        CategoryPlaylistCard(
                            playlist = album,
                            showCloudBadge = false,
                            onClick = hapticAction(HapticKind.UI) { onNavigate("playlist_detail/${album.id}") }
                        )
                    }
                }
            }
        }

        // 6. Section: "Profiles" (Artists - genres only)
        if (viewModel.artists.isNotEmpty() && viewModel.categoryKind == CategoryKind.GENRE) {
            item {
                CategorySectionHeader(
                    title = stringResource(R.string.category_profiles)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(viewModel.artists) { artist ->
                        ArtistCircleCard(
                            user = artist,
                            onClick = hapticAction(HapticKind.UI) { onNavigate("profile/${artist.id}") }
                        )
                    }
                }
            }
        }

        // 7. Section: "Discover more Tracks" (genres only)
        if (viewModel.discoverMoreTracks.isNotEmpty() && viewModel.categoryKind == CategoryKind.GENRE) {
            item {
                CategorySectionHeader(
                    title = stringResource(R.string.category_discover_more_tracks)
                )
            }
            itemsIndexed(viewModel.discoverMoreTracks) { index, track ->
                DiscoverMoreTrackItem(
                    track = track,
                    isCurrent = playerViewModel.uiCurrentTrack?.id == track.id,
                    onClick = hapticAction(HapticKind.UI) { playerViewModel.playPlaylist(viewModel.discoverMoreTracks, index) },
                    onOptionClick = hapticAction(HapticKind.UI) { playerViewModel.showTrackOptions(track) }
                )
            }
        }

        val hasAnyContent = trendingTracks.isNotEmpty() ||
                displayedPlaylists.isNotEmpty() ||
                viewModel.albums.isNotEmpty() ||
                viewModel.artists.isNotEmpty() ||
                viewModel.discoverMoreTracks.isNotEmpty()
        if (!hasAnyContent) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.category_no_content),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategorySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (actionText != null && onActionClick != null) {
            FilledTonalButton(
                onClick = onActionClick,
                shapes = ButtonDefaults.shapes(),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TrendingWallTrackItem(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onOptionClick: () -> Unit
) {
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl ?: track.user?.avatarUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isGoPlus) {
                    Spacer(Modifier.width(6.dp))
                    com.alananasss.kittytune.ui.common.GoPlusBadge(compact = true)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.user?.username ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(
            onClick = onOptionClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CategoryIntroducingCard(
    playlist: Playlist,
    tracks: List<Track>,
    genreTitle: String,
    currentlyPlayingTrack: Track?,
    onPlayPlaylist: () -> Unit,
    onTrackClick: (Track, Int) -> Unit,
    onTrackOptionClick: (Track) -> Unit,
    onPlaylistClick: () -> Unit
) {
    val context = LocalContext.current
    val likedPlaylists by com.alananasss.kittytune.data.LikeRepository.likedPlaylists.collectAsState()
    val isLiked = playlist.id in likedPlaylists

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Top row: Artwork on left + Title & Buttons on right
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Artwork with Buzzing badge + Genre label + Cloud logo
                Box(
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onPlaylistClick)
                ) {
                    AsyncImage(
                        model = playlist.calculatedArtworkUrl ?: playlist.artworkUrl,
                        contentDescription = playlist.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    // Buzzing badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF5500),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.category_introducing_buzzing),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    // Cloud icon
                    Icon(
                        painter = painterResource(R.drawable.ic_logo_cloud_light),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(16.dp)
                    )
                    // Category name badge at bottom
                    if (genreTitle.isNotBlank()) {
                        Text(
                            text = genreTitle.uppercase(Locale.ROOT),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(8.dp)
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = playlist.title ?: "",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Like Button
                        FilledIconButton(
                            onClick = hapticAction(HapticKind.UI) {
                                if (!isLiked) {
                                    com.alananasss.kittytune.data.DownloadManager.importPlaylistToLibrary(
                                        playlist = playlist.copy(tracks = tracks.ifEmpty { playlist.tracks }),
                                        tracks = tracks.ifEmpty { playlist.tracks ?: emptyList() },
                                        syncToCloud = true,
                                        likePlaylist = true
                                    )
                                    com.alananasss.kittytune.data.LikeRepository.togglePlaylistLike(
                                        playlistId = playlist.id,
                                        isLiked = true,
                                        permalink = playlist.permalinkUrl,
                                        urn = playlist.urn
                                    )
                                } else {
                                    com.alananasss.kittytune.data.LikeRepository.togglePlaylistLike(
                                        playlistId = playlist.id,
                                        isLiked = false,
                                        permalink = playlist.permalinkUrl,
                                        urn = playlist.urn
                                    )
                                }
                            },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = if (isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                contentDescription = stringResource(R.string.desc_like),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Play Button (White filled circle, black play icon)
                        FilledIconButton(
                            onClick = onPlayPlaylist,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.btn_play),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Preview tracks under header
            if (tracks.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 0.8.dp
                )
                Spacer(Modifier.height(8.dp))
                tracks.take(2).forEachIndexed { index, track ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTrackClick(track, index) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.artworkUrl ?: track.user?.avatarUrl,
                            contentDescription = track.title,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (currentlyPlayingTrack?.id == track.id) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = track.user?.username ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(
                            onClick = hapticAction(HapticKind.UI) { onTrackOptionClick(track) },
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MoreVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPlaylistCard(
    playlist: Playlist,
    onClick: () -> Unit,
    showCloudBadge: Boolean = false
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = playlist.calculatedArtworkUrl ?: playlist.artworkUrl,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (showCloudBadge) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo_cloud_light),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
        val subtitle = playlist.user?.username ?: ""
        if (subtitle.isNotBlank()) {
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

@Composable
private fun CategoryPlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCloudBadge: Boolean = true
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = playlist.calculatedArtworkUrl ?: playlist.artworkUrl,
                contentDescription = playlist.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (showCloudBadge) {
                Icon(
                    painter = painterResource(R.drawable.ic_logo_cloud_light),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title ?: "",
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        val author = playlist.user?.username.orEmpty()
        val countText = stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0)
        val subtitleText = author.ifBlank { countText }
        if (subtitleText.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitleText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DiscoverMoreTrackItem(
    track: Track,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onOptionClick: () -> Unit
) {
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = track.artworkUrl ?: track.user?.avatarUrl,
            contentDescription = track.title,
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = titleColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isGoPlus) {
                    Spacer(Modifier.width(6.dp))
                    com.alananasss.kittytune.ui.common.GoPlusBadge(compact = true)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = track.user?.username ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (track.playbackCount > 0) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = formatPlaybackCount(track.playbackCount),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if ((track.durationMs ?: 0L) > 0) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if ((track.durationMs ?: 0L) > 0) {
                    Text(
                        text = formatTrackDuration(track.durationMs!!),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        IconButton(
            onClick = onOptionClick,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.MoreVert,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatPlaybackCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format(Locale.ROOT, "%.1fM", count / 1_000_000.0).replace(".0M", "M")
        count >= 1_000 -> String.format(Locale.ROOT, "%.1fK", count / 1_000.0).replace(".0K", "K")
        count > 0 -> count.toString()
        else -> ""
    }
}

private fun formatTrackDuration(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
}

@Composable
private fun CategoryHeroBanner(
    title: String,
    category: com.alananasss.kittytune.data.SearchCategory?,
    categoryKind: CategoryKind,
    hasPlayableTracks: Boolean = false,
    onPlayClick: () -> Unit
) {
    val borderColor = GenreData.parseBorderColor(category?.borderColor)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.5.dp, borderColor.copy(alpha = 0.5f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val isDark = isSystemInDarkTheme()
            val artwork = if (!isDark && !category?.artworkUrlLight.isNullOrBlank()) {
                category?.artworkUrlLight
            } else {
                category?.artworkUrl
            }
            if (!artwork.isNullOrBlank()) {
                AsyncImage(
                    model = artwork,
                    contentDescription = null,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp)),
                    contentScale = ContentScale.Crop
                )
            } else if (category?.icon != null) {
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(borderColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = category.icon,
                        contentDescription = null,
                        tint = borderColor,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (categoryKind == CategoryKind.GENRE) {
                    Surface(
                        shape = CircleShape,
                        color = borderColor.copy(alpha = 0.15f),
                        contentColor = borderColor
                    ) {
                        Text(
                            text = stringResource(R.string.category_kind_genre),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (hasPlayableTracks) {
                    Button(
                        onClick = onPlayClick,
                        shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                        modifier = Modifier.height(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.category_play_all),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryTrendingTabContent(
    innerPadding: PaddingValues,
    viewModel: GenreDetailViewModel,
    playerViewModel: PlayerViewModel
) {
    LazyColumn(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Controls header: Play all & Shuffle
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${viewModel.popularTracks.size} ${stringResource(R.string.profile_tab_tracks).lowercase(Locale.getDefault())}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = hapticAction(HapticKind.UI) {
                            if (viewModel.popularTracks.isNotEmpty()) {
                                playerViewModel.playPlaylist(viewModel.popularTracks.shuffled(), 0)
                            }
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Button(
                        onClick = hapticAction(HapticKind.UI) {
                            if (viewModel.popularTracks.isNotEmpty()) {
                                playerViewModel.playPlaylist(viewModel.popularTracks, 0)
                            }
                        },
                        shapes = ButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.category_play_all), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (viewModel.popularTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.category_no_content),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            itemsIndexed(viewModel.popularTracks) { index, track ->
                PopularTrackListItem(
                    rank = index + 1,
                    track = track,
                    currentlyPlayingTrack = playerViewModel.uiCurrentTrack,
                    onClick = hapticAction(HapticKind.UI) { playerViewModel.playPlaylist(viewModel.popularTracks, index) },
                    onOptionClick = hapticAction(HapticKind.UI) { playerViewModel.showTrackOptions(track) }
                )
            }
        }
    }
}

@Composable
private fun CategoryPlaylistsTabContent(
    innerPadding: PaddingValues,
    viewModel: GenreDetailViewModel,
    onNavigate: (String) -> Unit
) {
    val allPlaylists = remember(viewModel.officialPlaylists, viewModel.communityPlaylists) {
        if (viewModel.officialPlaylists.isNotEmpty()) {
            viewModel.officialPlaylists
        } else {
            viewModel.communityPlaylists
        }
    }

    LazyColumn(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Section Header "Playlists" (matching official SoundCloud mobile app)
        item {
            Text(
                text = stringResource(R.string.category_tab_playlists),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (allPlaylists.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.category_no_content),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            // 2-column grid of curated playlists (matching official SoundCloud mobile app)
            val pairs = allPlaylists.chunked(2)
            items(pairs) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { playlist ->
                        val author = playlist.user?.username.orEmpty()
                        val countText = stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0)
                        val subtitleText = author.ifBlank { countText }
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryGridCard(
                                title = playlist.title ?: "",
                                subtitle = subtitleText,
                                artworkUrl = playlist.fullResArtwork,
                                onClick = hapticAction(HapticKind.UI) { onNavigate("playlist_detail/${playlist.id}") },
                                showCloudBadge = true
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryAlbumsTabContent(
    innerPadding: PaddingValues,
    viewModel: GenreDetailViewModel,
    onNavigate: (String) -> Unit
) {
    val allAlbums = viewModel.albums

    LazyColumn(
        modifier = Modifier
            .padding(innerPadding)
            .fillMaxSize(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (allAlbums.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.category_no_content),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            val pairs = allAlbums.chunked(2)
            items(pairs) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    pair.forEach { album ->
                        Box(modifier = Modifier.weight(1f)) {
                            CategoryGridCard(
                                title = album.title ?: "",
                                subtitle = album.user?.username ?: "",
                                artworkUrl = album.fullResArtwork,
                                onClick = hapticAction(HapticKind.UI) { onNavigate("playlist_detail/${album.id}") },
                                showCloudBadge = false
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGridCard(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showCloudBadge: Boolean = false
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            ) {
                AsyncImage(
                    model = artworkUrl,
                    contentDescription = title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (showCloudBadge) {
                    Icon(
                        painter = painterResource(R.drawable.ic_logo_cloud_light),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
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
}

@Composable
private fun ArtistCircleCard(
    user: User,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = user.avatarUrl,
            contentDescription = user.username,
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = user.username ?: stringResource(R.string.unknown_artist),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        FilledTonalButton(
            onClick = onClick,
            shapes = ButtonDefaults.shapes(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
            modifier = Modifier.height(28.dp)
        ) {
            Text(
                text = stringResource(R.string.btn_follow),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PopularTrackListItem(
    track: Track,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    onOptionClick: () -> Unit,
    rank: Int? = null
) {
    val view = LocalView.current
    val blockedTrackIds by com.alananasss.kittytune.data.BlockManager.blockedTrackIdsFlow.collectAsState()
    val blockedArtistIds by com.alananasss.kittytune.data.BlockManager.blockedArtistIdsFlow.collectAsState()
    if (track.id in blockedTrackIds || (track.user?.id != null && track.user.id in blockedArtistIds)) {
        return
    }

    val isCurrent = currentlyPlayingTrack?.id == track.id
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    val likedTracks by com.alananasss.kittytune.data.LikeRepository.likedTracks.collectAsState()
    val isTrackLiked = remember(track.id, likedTracks) { com.alananasss.kittytune.data.LikeRepository.isTrackLiked(track.id) }

    val socialLikersMap by com.alananasss.kittytune.data.SocialProofRepository.socialLikersMap.collectAsState()
    val socialLikers = socialLikersMap[track.id]

    LaunchedEffect(track.id) {
        if (track.id > 0 && track.source != "youtube") {
            com.alananasss.kittytune.data.SocialProofRepository.requestSocialProof(track.id)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS)
                    onOptionClick()
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (rank != null) {
            Text(
                text = rank.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp)
            )
            Spacer(Modifier.width(8.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )

            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = stringResource(R.string.player_playing_now),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = titleColor,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.isGoPlus) {
                    Spacer(Modifier.width(6.dp))
                    com.alananasss.kittytune.ui.common.GoPlusBadge(compact = true)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${track.displayArtist.ifBlank { track.user?.username ?: stringResource(R.string.unknown_artist) }} • ${formatNumber(track.playbackCount)} ${stringResource(R.string.playback_count_formatted)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isTrackLiked) {
                    Spacer(Modifier.width(4.dp))
                    Text(text = "·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                }
                if (!socialLikers.isNullOrEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    Text(text = "·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    com.alananasss.kittytune.ui.common.MiniSocialProofAvatars(likers = socialLikers)
                }
            }
        }

        IconButton(
            onClick = onOptionClick,
            shapes = IconButtonDefaults.shapes()
        ) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.btn_options))
        }
    }
}

private fun formatNumber(count: Int): String {
    if (count < 1000) return count.toString()
    val k = count / 1000.0
    val m = count / 1000000.0
    return when {
        m >= 1.0 -> String.format(Locale.getDefault(), "%.1f M", m)
        k >= 1.0 -> String.format(Locale.getDefault(), "%.1f k", k)
        else -> count.toString()
    }
}

@Composable
private fun CountrySelectionCard(
    countryName: String,
    flagEmoji: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceContainer
    val contentColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = BorderStroke(1.5.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = flagEmoji, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = countryName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = contentColor
                )
            }

            if (isSelected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = contentColor
                )
            }
        }
    }
}
