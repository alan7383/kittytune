    package com.alananasss.kittytune.ui.track
    
    import androidx.compose.animation.*
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.ExperimentalFoundationApi
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.pager.HorizontalPager
    import androidx.compose.foundation.pager.rememberPagerState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material.icons.filled.Person
    import androidx.compose.material.icons.rounded.Verified
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import coil.compose.AsyncImage
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.DownloadManager
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.domain.User
    import com.alananasss.kittytune.ui.library.TrackListItem
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.profile.ArtistAvatar
    import kotlinx.coroutines.launch
    import java.io.File
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun TrackDetailScreen(
        trackId: Long,
        initialTab: Int = 0,
        onBackClick: () -> Unit,
        onNavigate: (String) -> Unit,
        playerViewModel: PlayerViewModel,
        detailViewModel: TrackDetailViewModel = viewModel()
    ) {
        val pagerState = rememberPagerState(initialPage = initialTab) { 4 }
        val scope = rememberCoroutineScope()
        val tabs = listOf(
            stringResource(R.string.detail_likers),
            stringResource(R.string.detail_reposters),
            stringResource(R.string.detail_in_playlists),
            stringResource(R.string.detail_related)
        )
    
        LaunchedEffect(trackId) {
            detailViewModel.loadTrackDetails(trackId)
        }
    
        val track = detailViewModel.track
    
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.detail_track_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        FilledTonalIconButton(
                            onClick = onBackClick,
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_close))
                        }
                    }
                )
            }
        ) { innerPadding ->
            AnimatedContent(
                targetState = detailViewModel.isLoading || track == null,
                transitionSpec = {
                    (fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.96f))
                        .togetherWith(fadeOut(tween(200)))
                },
                label = "trackDetailContent",
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            ) { isLoadingState ->
                if (isLoadingState) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                } else {
                    Column {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track!!.fullResArtwork,
                                contentDescription = null,
                                modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(track.title ?: "", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(track.user?.username ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    if (track.user?.verified == true) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                                    }
                                }
                            }
                        }
    
                        TabRow(selectedTabIndex = pagerState.currentPage) {
                            tabs.forEachIndexed { index, title ->
                                Tab(
                                    selected = pagerState.currentPage == index,
                                    onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                                    text = { Text(text = title) }
                                )
                            }
                        }
    
                        HorizontalPager(state = pagerState) { page ->
                            when (page) {
                                0 -> UserList(
                                    users = detailViewModel.likers,
                                    onNavigate = onNavigate,
                                    onLoadMore = { detailViewModel.loadMoreLikers() },
                                    isLoadingMore = detailViewModel.isLikersLoadingMore
                                )
                                1 -> UserList(
                                    users = detailViewModel.reposters,
                                    onNavigate = onNavigate,
                                    onLoadMore = { detailViewModel.loadMoreReposters() },
                                    isLoadingMore = detailViewModel.isRepostersLoadingMore
                                )
                                2 -> PlaylistList(
                                    playlists = detailViewModel.inPlaylists,
                                    onNavigate = onNavigate,
                                    onLoadMore = { detailViewModel.loadMorePlaylists() },
                                    isLoadingMore = detailViewModel.isPlaylistsLoadingMore
                                )
                                3 -> TrackList(
                                    tracks = detailViewModel.relatedTracks,
                                    playerViewModel = playerViewModel,
                                    onLoadMore = { detailViewModel.loadMoreRelated() },
                                    isLoadingMore = detailViewModel.isRelatedLoadingMore
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun UserList(
        users: List<User>,
        onNavigate: (String) -> Unit,
        onLoadMore: () -> Unit,
        isLoadingMore: Boolean
    ) {
        if (users.isEmpty() && !isLoadingMore) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_no_one_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 180.dp)) {
                itemsIndexed(users) { index, user ->
                    if (index >= users.size - 5) {
                        LaunchedEffect(Unit) { onLoadMore() }
                    }
    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("profile:${user.id}") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtistAvatar(avatarUrl = user.avatarUrl, modifier = Modifier.size(48.dp).clip(CircleShape))
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(user.username ?: stringResource(R.string.unknown_artist), fontWeight = FontWeight.SemiBold)
                                if (user.verified) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                }
                            }
                            Text(
                                text = "${user.followersCount} ${stringResource(R.string.profile_followers)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun PlaylistList(
        playlists: List<Playlist>,
        onNavigate: (String) -> Unit,
        onLoadMore: () -> Unit,
        isLoadingMore: Boolean
    ) {
        if (playlists.isEmpty() && !isLoadingMore) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_no_public_playlist), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 180.dp)) {
                itemsIndexed(playlists) { index, playlist ->
                    if (index >= playlists.size - 5) {
                        LaunchedEffect(Unit) { onLoadMore() }
                    }
    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("${playlist.id}") }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = playlist.fullResArtwork,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(4.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(playlist.title ?: stringResource(R.string.lib_playlists), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0) + " • " + stringResource(R.string.playlist_by_user, playlist.user?.username ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    fun TrackList(
        tracks: List<Track>,
        playerViewModel: PlayerViewModel,
        onLoadMore: () -> Unit,
        isLoadingMore: Boolean
    ) {
        val downloadProgress by DownloadManager.downloadProgress.collectAsState()
        val context = LocalContext.current
    
        if (tracks.isEmpty() && !isLoadingMore) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_no_similar), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 180.dp)) {
                itemsIndexed(tracks) { index, track ->
                    if (index >= tracks.size - 5) {
                        LaunchedEffect(Unit) { onLoadMore() }
                    }
    
                    val progress = downloadProgress[track.id]
                    val isDownloading = progress != null
                    val isDownloaded = remember(track.id, downloadProgress) {
                        File(context.filesDir, "track_${track.id}.mp3").exists()
                    }
    
                    TrackListItem(
                        track = track,
                        currentlyPlayingTrack = playerViewModel.currentTrack,
                        index = index,
                        isDownloading = isDownloading,
                        isDownloaded = isDownloaded,
                        downloadProgress = progress ?: 0,
                        onClick = { playerViewModel.playPlaylist(tracks, index) },
                        onOptionClick = { playerViewModel.showTrackOptions(track) }
                    )
                }
                if (isLoadingMore) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            LoadingIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }


