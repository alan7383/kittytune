package com.alananasss.kittytune.ui.track

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.ChatBubble
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.ArtistAvatar
import com.alananasss.kittytune.ui.profile.ReleaseDate
import kotlinx.coroutines.launch

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
                    TrackDetailHeader(track!!, playerViewModel)
                    DetailTabs(
                        selected = pagerState.currentPage,
                        tabs = listOf(
                            DetailTab(Icons.Rounded.Favorite, tabs[0], track.likesCount),
                            DetailTab(Icons.Rounded.Repeat, tabs[1], track.repostsCount),
                            DetailTab(Icons.AutoMirrored.Rounded.QueueMusic, tabs[2], null),
                            DetailTab(Icons.Rounded.AutoAwesome, tabs[3], null),
                        ),
                        onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    )

                    HorizontalPager(state = pagerState) { page ->
                        when (page) {
                            0 -> UserList(
                                users = detailViewModel.likers,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMoreLikers() },
                                isLoadingMore = detailViewModel.isLikersLoadingMore,
                                isSortedByFollowers = detailViewModel.isUsersSortedByFollowers,
                                onToggleSort = { detailViewModel.toggleUsersSort() },
                                isSortLoading = detailViewModel.isUsersSortLoading,
                            )
                            1 -> UserList(
                                users = detailViewModel.reposters,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMoreReposters() },
                                isLoadingMore = detailViewModel.isRepostersLoadingMore,
                                isSortedByFollowers = detailViewModel.isUsersSortedByFollowers,
                                onToggleSort = { detailViewModel.toggleUsersSort() },
                                isSortLoading = detailViewModel.isUsersSortLoading,
                            )
                            2 -> PlaylistList(
                                playlists = detailViewModel.inPlaylists,
                                onNavigate = onNavigate,
                                onLoadMore = { detailViewModel.loadMorePlaylists() },
                                isLoadingMore = detailViewModel.isPlaylistsLoadingMore,
                                isSortedByLikes = detailViewModel.isPlaylistsSortedByLikes,
                                onToggleSort = { detailViewModel.toggleSortPlaylists() },
                                isSortLoading = detailViewModel.isPlaylistsSortLoading,
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

/** The track as the head of its page: cover over a blur of itself, title, artist, and compact stat chips. */
@Composable
private fun TrackDetailHeader(track: Track, playerViewModel: PlayerViewModel) {
    Box(Modifier.fillMaxWidth().height(180.dp)) {
        AsyncImage(
            model = track.fullResArtwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize().blur(40.dp),
        )
        Box(
            Modifier.matchParentSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.background.copy(alpha = 0.35f),
                    1f to MaterialTheme.colorScheme.background,
                )
            )
        )
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .viewableCover(track.fullResArtwork),
                contentScale = ContentScale.Crop,
            )
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    track.title ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { playerViewModel.navigateToTrackArtist(track) }
                ) {
                    Text(
                        track.displayArtist.ifBlank { track.user?.username.orEmpty() },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    val dateText = remember(track.id, track.releaseDate, track.createdAt) {
                        ReleaseDate.shortText(track.releaseDate ?: track.createdAt)
                    }
                    if (dateText.isNotEmpty()) {
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CalendarToday, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(dateText, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    listOf(
                        Icons.Rounded.PlayArrow to track.playbackCount,
                        Icons.Rounded.Favorite to track.likesCount,
                        Icons.Rounded.Repeat to track.repostsCount,
                        Icons.Rounded.ChatBubble to track.commentCount,
                    ).filter { it.second > 0 }.forEach { (icon, count) ->
                        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                            Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(compactCount(count.toLong()), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class DetailTab(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val count: Int?)

@Composable
private fun DetailTabs(selected: Int, tabs: List<DetailTab>, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selected
            val container by androidx.compose.animation.animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "detailTab",
            )
            Surface(
                onClick = { onSelect(index) },
                shape = CircleShape,
                color = container,
                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(tab.icon, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(tab.label, style = MaterialTheme.typography.labelLarge, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium)
                    if (tab.count != null && tab.count > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(compactCount(tab.count.toLong()), style = MaterialTheme.typography.labelSmall)
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
    isLoadingMore: Boolean,
    isSortedByFollowers: Boolean? = null,
    onToggleSort: () -> Unit = {},
    isSortLoading: Boolean = false,
) {
    if (users.isEmpty() && !isLoadingMore && !isSortLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.detail_no_one_yet), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else Column(Modifier.fillMaxSize()) {
        if (isSortedByFollowers != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = isSortedByFollowers,
                    onClick = onToggleSort,
                    label = { Text(stringResource(R.string.track_sorted_by_followers)) },
                    leadingIcon = {
                        Icon(
                            if (isSortedByFollowers) Icons.Rounded.Check else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    shape = CircleShape,
                )
                if (isSortLoading) {
                    Spacer(Modifier.width(8.dp))
                    ContainedLoadingIndicator(modifier = Modifier.size(18.dp))
                }
            }
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 180.dp), modifier = Modifier.weight(1f)) {
            itemsIndexed(users, key = { _, user -> user.id }) { index, user ->
                if (index >= users.size - 5) {
                    LaunchedEffect(users.size, isLoadingMore) { onLoadMore() }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigate("profile:${user.numericId}") }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
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
                            text = "${compactCount(user.followersCount.toLong())} ${stringResource(R.string.profile_followers)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (isLoadingMore) {
                item {
                    Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
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
    isLoadingMore: Boolean,
    isSortedByLikes: Boolean,
    onToggleSort: () -> Unit,
    isSortLoading: Boolean = false,
) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = isSortedByLikes,
                onClick = onToggleSort,
                label = { Text(stringResource(R.string.track_sorted_by_popularity)) },
                leadingIcon = {
                    Icon(
                        if (isSortedByLikes) Icons.Rounded.Check else Icons.Rounded.Favorite,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
                shape = CircleShape,
                interactionSource = interactionSource,
            )
            if (isSortLoading) {
                Spacer(Modifier.width(8.dp))
                ContainedLoadingIndicator(modifier = Modifier.size(18.dp))
            }
        }

        if (playlists.isEmpty() && !isLoadingMore && !isSortLoading) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.detail_no_public_playlist), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = 180.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(playlists, key = { _, playlist -> playlist.id }) { index, playlist ->
                    if (index >= playlists.size - 5) {
                        LaunchedEffect(playlists.size, isLoadingMore) { onLoadMore() }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNavigate("${playlist.id}") }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = playlist.fullResArtwork,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(playlist.title ?: stringResource(R.string.lib_playlists), fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0) + " • " + stringResource(R.string.playlist_by_user, playlist.user?.username ?: "") + if (playlist.likesCount != null && playlist.likesCount > 0) " • ${compactCount(playlist.likesCount.toLong())} likes" else "",
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
                            ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
                        }
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
    val downloadedIds by DownloadManager.downloadedIds.collectAsState()

    if (tracks.isEmpty() && !isLoadingMore) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.detail_no_similar), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 180.dp)) {
            itemsIndexed(tracks) { index, track ->
                if (index >= tracks.size - 5) {
                    LaunchedEffect(tracks.size) { onLoadMore() }
                }

                val progress = downloadProgress[track.id]
                val isDownloading = progress != null
                val isDownloaded = remember(track.id, downloadedIds) {
                    downloadedIds.contains(track.id)
                }

                TrackListItem(
                    track = track,
                    currentlyPlayingTrack = playerViewModel.uiCurrentTrack,
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
                        ContainedLoadingIndicator(modifier = Modifier.size(28.dp))
                    }
                }
            }
        }
    }
}

private fun compactCount(value: Long): String {
    return when {
        value >= 1_000_000_000L -> String.format(java.util.Locale.getDefault(), "%.1fB", value / 1_000_000_000.0)
        value >= 1_000_000L -> String.format(java.util.Locale.getDefault(), "%.1fM", value / 1_000_000.0)
        value >= 1_000L -> String.format(java.util.Locale.getDefault(), "%.1fK", value / 1_000.0)
        else -> java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).format(value)
    }
}
