    package com.alananasss.kittytune.ui.home
    
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.LazyRow
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material.icons.filled.MoreVert
    import androidx.compose.material.icons.rounded.GraphicEq
    import androidx.compose.material3.*
    import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
    import androidx.compose.runtime.Composable
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.unit.sp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import coil.compose.AsyncImage
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.domain.Playlist
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.ui.common.SquareCardShimmer
    import com.alananasss.kittytune.ui.common.TrackListItemShimmer
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import java.util.Locale
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun NewReleasesScreen(
        onBackClick: () -> Unit,
        onPlaylistClick: (Long) -> Unit,
        playerViewModel: PlayerViewModel,
        viewModel: NewReleasesViewModel = viewModel()
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    
        Scaffold(
            modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.explorer_new_releases),
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-0.5).sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick, shapes = IconButtonDefaults.shapes()) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.largeTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) { innerPadding ->
            if (viewModel.isLoading) {
                // loading state with shimmer placeholders
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    userScrollEnabled = false
                ) {
                    // playlists shimmer
                    item {
                        Text(
                            text = stringResource(R.string.new_releases_all_playlists),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(5) { SquareCardShimmer() }
                        }
                    }
                    // popular tracks shimmer
                    item {
                        Text(
                            text = stringResource(R.string.new_releases_popular_tracks),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(10) { TrackListItemShimmer() }
                }
            } else if (viewModel.playlists.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                // main content in a vertically scrollable column
                LazyColumn(
                    modifier = Modifier.padding(innerPadding),
                    contentPadding = PaddingValues(bottom = 120.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // playlists section in a horizontal row
                    if (viewModel.playlists.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.new_releases_all_playlists),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(viewModel.playlists, key = { it.id }) { playlist ->
                                    NewReleasePlaylistCard(
                                        playlist = playlist,
                                        onClick = { onPlaylistClick(playlist.id) }
                                    )
                                }
                            }
                        }
                    }
    
                    // popular tracks section with horizontal swiping by groups of 5
                    if (viewModel.popularTracks.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.new_releases_popular_tracks),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
                            )
                        }
    
                        // split the list into pages of 5 tracks
                        val pages = viewModel.popularTracks.chunked(5)
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(pages) { pageIndex, trackColumn ->
                                    Column(
                                        modifier = Modifier.width(320.dp), // define the width of each "page"
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        trackColumn.forEachIndexed { itemIndexInColumn, track ->
                                            // calculate the absolute index in the original list for playback
                                            val absoluteIndex = pageIndex * 5 + itemIndexInColumn
                                            PopularTrackRow(
                                                track = track,
                                                rank = absoluteIndex + 1,
                                                currentlyPlayingTrack = playerViewModel.currentTrack,
                                                onClick = { playerViewModel.playPlaylist(viewModel.popularTracks, absoluteIndex) },
                                                onOptionClick = { playerViewModel.showTrackOptions(track) }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    // this is the track row with rank and play count
    @Composable
    fun PopularTrackRow(
        track: Track,
        rank: Int,
        currentlyPlayingTrack: Track?,
        onClick: () -> Unit,
        onOptionClick: () -> Unit
    ) {
        val isCurrent = currentlyPlayingTrack?.id == track.id
        val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp), // no horizontal padding here, it's on the column
            verticalAlignment = Alignment.CenterVertically
        ) {
            // rank index
            Text(
                text = "$rank",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.width(32.dp),
                textAlign = TextAlign.Center
            )
    
            // artwork + playing indicator overlay
            Box(contentAlignment = Alignment.Center) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
    
                if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
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
    
            Spacer(Modifier.width(16.dp))
    
            // track details
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = titleColor
                )
                // artist and play count
                Text(
                    text = "${track.user?.username ?: stringResource(R.string.unknown_artist)} • ${formatNumber(track.playbackCount)} ${stringResource(R.string.playback_count_formatted)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    
            // kebab menu
            IconButton(onClick = onOptionClick) {
                Icon(Icons.Default.MoreVert, stringResource(R.string.btn_options))
            }
        }
    }
    
    
    @Composable
    fun NewReleasePlaylistCard(
        playlist: Playlist,
        onClick: () -> Unit
    ) {
        Card(
            onClick = onClick,
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.width(160.dp)
        ) {
            Column {
                AsyncImage(
                    model = playlist.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // square image
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    Text(
                        text = playlist.title ?: stringResource(R.string.lib_playlists),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
    }
    
    // helper to format big numbers (1k, 1m...)
    private fun formatNumber(count: Int): String {
        if (count < 1000) return count.toString()
        val k = count / 1000.0
        val m = count / 1000000.0
        return when {
            m >= 1.0 -> String.format(Locale.getDefault(), "%.1fM", m)
            k >= 1.0 -> String.format(Locale.getDefault(), "%.1fk", k)
            else -> count.toString()
        }
    }


