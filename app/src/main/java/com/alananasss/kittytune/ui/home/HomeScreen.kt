package com.alananasss.kittytune.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.ArtistCircleShimmer
import com.alananasss.kittytune.ui.common.ShimmerBox
import com.alananasss.kittytune.ui.common.ShimmerLine
import com.alananasss.kittytune.ui.common.SquareCardShimmer
import com.alananasss.kittytune.ui.library.DynamicPlaylistCard
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.ArtistAvatar
import com.alananasss.kittytune.ui.profile.SquareCard
import java.io.File

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

    val isKeyboardOpen = WindowInsets.isImeVisible

    BackHandler(enabled = homeViewModel.isSearching) {
        if (isKeyboardOpen) {
            focusManager.clearFocus()
        } else {
            homeViewModel.clearSearch()
            focusManager.clearFocus()
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
                    }
                )

                AnimatedVisibility(visible = homeViewModel.isSearching) {
                    SearchFilters(
                        activeFilter = homeViewModel.activeFilter,
                        onFilterSelected = homeViewModel::onFilterChanged
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->

        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (homeViewModel.isSearching) {
                if (homeViewModel.isSearchLoading) {
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
            } else if (homeViewModel.homeSections.isEmpty()) {
                HomeScreenShimmer()
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

@Composable
fun HomeContent(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    history: List<HistoryItem>,
    onNavigate: (String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {

        if (history.isNotEmpty()) {
            item {
                SectionHeader(stringResource(R.string.home_recently_played))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(history) { item ->
                        HistoryCard(item) {
                            when {
                                item.id == "likes" -> onNavigate("likes")
                                item.id == "downloads" -> onNavigate("downloads")
                                item.type == "STATION" -> onNavigate(item.id)
                                item.type == "PROFILE" -> onNavigate(item.id)
                                item.type == "PLAYLIST" -> onNavigate(item.id.replace("playlist:", ""))
                                item.type == "TRACK" -> {
                                    val trackToPlay = Track(
                                        id = item.numericId,
                                        title = item.title,
                                        artworkUrl = item.imageUrl,
                                        durationMs = 0L,
                                        user = User(0L, item.subtitle, null)
                                    )
                                    playerViewModel.playPlaylist(listOf(trackToPlay), 0)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(32.dp))
            }
        }

        items(homeViewModel.homeSections) { section ->
            SectionHeader(section.title, section.subtitle)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                when (section.type) {
                    SectionType.STATIONS_ROW -> {
                        val playlists = section.content.filterIsInstance<Playlist>()
                        items(playlists) { playlist ->
                            val isRealStation = playlist.title?.startsWith("Station:") == true
                            val navId = if (isRealStation) "station:${playlist.id}" else playlist.id.toString()
                            StationCard(playlist) { onNavigate(navId) }
                        }
                    }
                    SectionType.TRACKS_ROW -> {
                        val tracks = section.content.filterIsInstance<Track>()
                        items(tracks) { track ->
                            TrackCardBig(track) { playerViewModel.playPlaylist(listOf(track), 0, null) }
                        }
                    }
                    SectionType.ARTISTS_ROW -> {
                        val artists = section.content.filterIsInstance<User>()
                        items(artists) { artist ->
                            ArtistCircle(artist) { onNavigate("profile:${artist.id}") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
fun StationCard(playlist: Playlist, onClick: () -> Unit) {
    val isRadio = playlist.title?.startsWith("Station:") == true
    val displayTitle = if (isRadio) playlist.title?.removePrefix("Station: ") ?: stringResource(R.string.radio) else playlist.title ?: stringResource(R.string.app_name)
    val subtitle = if(isRadio) stringResource(R.string.home_section_similar, playlist.user?.username ?: "?") else playlist.user?.username ?: stringResource(R.string.lib_playlists)

    Column(modifier = Modifier.width(160.dp).clickable { onClick() }) {
        Box {
            AsyncImage(
                model = playlist.fullResArtwork, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(160.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Box(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp)).padding(4.dp)) {
                Icon(if (isRadio) Icons.Default.Radio else Icons.Default.QueueMusic, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = displayTitle, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(text = title, style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        if (subtitle != null) {
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun HistoryCard(item: HistoryItem, onClick: () -> Unit) {
    val imageShape = if (item.type == "PROFILE") CircleShape else RoundedCornerShape(12.dp)

    Column(modifier = Modifier.width(140.dp).clickable { onClick() }) {
        Box {
            AsyncImage(
                model = item.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(140.dp)
                    .clip(imageShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (item.type == "STATION" || item.type == "PLAYLIST") {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(4.dp)) {
                    Icon(if(item.type=="STATION") Icons.Default.Radio else Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(text = item.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = item.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun TrackCardBig(track: Track, onClick: () -> Unit) {
    Column(modifier = Modifier.width(160.dp).clickable { onClick() }) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = track.fullResArtwork, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(160.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(text = track.title ?: stringResource(R.string.untitled_track), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = track.user?.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun ArtistCircle(user: User, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(120.dp).clickable { onClick() }) {
        ArtistAvatar(
            avatarUrl = user.avatarUrl,
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.height(8.dp))
        Text(text = user.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
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
    onProfileClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isSearching) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_close), tint = MaterialTheme.colorScheme.onSurface)
            }
        } else {
            IconButton(onClick = {
                onSearchFocus()
                focusRequester.requestFocus()
            }) {
                Icon(Icons.Default.Search, stringResource(R.string.search_hint), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        TextField(
            value = query,
            onValueChange = {
                if (!isSearching) onSearchFocus()
                onQueryChange(it)
            },
            placeholder = { Text(stringResource(R.string.search_hint), maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                .focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
        )

        if (query.isNotEmpty()) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(Icons.Default.Close, stringResource(R.string.btn_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
fun SearchFilters(
    activeFilter: SearchFilter,
    onFilterSelected: (SearchFilter) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(SearchFilter.entries) { filter ->
            val label = when(filter) {
                SearchFilter.ALL -> stringResource(R.string.all_filters)
                SearchFilter.TRACKS -> stringResource(R.string.profile_tracks)
                SearchFilter.ARTISTS -> stringResource(R.string.lib_artists)
                SearchFilter.PLAYLISTS -> stringResource(R.string.lib_playlists)
            }
            FilterChip(
                selected = activeFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(label, maxLines = 1) },
                shape = RoundedCornerShape(20.dp),
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = null
            )
        }
    }
}

@Composable
fun SearchResultsList(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onNavigate: (String) -> Unit
) {
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()

    LazyColumn(
        contentPadding = PaddingValues(bottom = 120.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        if (homeViewModel.searchResultsArtists.isNotEmpty()) {
            if (homeViewModel.activeFilter == SearchFilter.ALL) {
                item { Text(stringResource(R.string.lib_artists), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(16.dp)) }
            }

            if (homeViewModel.activeFilter == SearchFilter.ARTISTS) {
                items(homeViewModel.searchResultsArtists) { artist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("profile:${artist.id}") }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtistAvatar(
                            avatarUrl = artist.avatarUrl,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(artist.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(R.string.profile_followers, artist.followersCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(homeViewModel.searchResultsArtists) { artist ->
                            ArtistCircle(artist) { onNavigate("profile:${artist.id}") }
                        }
                    }
                }
            }
        }

        if (homeViewModel.searchResultsTracks.isNotEmpty()) {
            if (homeViewModel.activeFilter == SearchFilter.ALL) {
                item { Text(stringResource(R.string.profile_tracks), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)) }
            }
            itemsIndexed(homeViewModel.searchResultsTracks) { index, track ->
                TrackListItem(
                    track = track,
                    currentlyPlayingTrack = playerViewModel.currentTrack,
                    index = index,
                    isDownloading = downloadProgress[track.id] != null,
                    isDownloaded = File(LocalContext.current.filesDir, "track_${track.id}.mp3").exists(),
                    downloadProgress = downloadProgress[track.id] ?: 0,
                    onClick = { playerViewModel.playPlaylist(homeViewModel.searchResultsTracks, index) },
                    onOptionClick = { playerViewModel.showTrackOptions(track) }
                )
            }
        }

        if (homeViewModel.searchResultsPlaylists.isNotEmpty()) {
            if (homeViewModel.activeFilter == SearchFilter.ALL) {
                item { Text(stringResource(R.string.lib_playlists), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)) }
            }

            if (homeViewModel.activeFilter == SearchFilter.PLAYLISTS) {
                items(homeViewModel.searchResultsPlaylists) { playlist ->
                    DynamicPlaylistCard(playlist, isGrid = false) { onNavigate(playlist.id.toString()) }
                }
            } else {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(homeViewModel.searchResultsPlaylists) { playlist ->
                            SquareCard(playlist) { onNavigate(playlist.id.toString()) }
                        }
                    }
                }
            }
        }

        if (homeViewModel.searchResultsTracks.isEmpty() && homeViewModel.searchResultsArtists.isEmpty() && homeViewModel.searchResultsPlaylists.isEmpty()) {
            item {
                Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun HomeScreenShimmer() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = false
    ) {
        item {
            Spacer(modifier = Modifier.height(28.dp))
            ShimmerLine(modifier = Modifier.padding(horizontal = 16.dp).width(200.dp).height(24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) { items(5) { SquareCardShimmer() } }
            Spacer(Modifier.height(32.dp))
        }
        item {
            ShimmerLine(modifier = Modifier.padding(horizontal = 16.dp).width(250.dp).height(24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) { items(5) { SquareCardShimmer() } }
            Spacer(Modifier.height(40.dp))
        }
        item {
            ShimmerLine(modifier = Modifier.padding(horizontal = 16.dp).width(180.dp).height(24.dp))
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), userScrollEnabled = false) { items(5) { ArtistCircleShimmer() } }
            Spacer(Modifier.height(40.dp))
        }
    }
}