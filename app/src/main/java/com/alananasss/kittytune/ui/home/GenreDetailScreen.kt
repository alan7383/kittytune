    package com.alananasss.kittytune.ui.home

    import android.net.Uri
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.LazyRow
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material.icons.filled.MoreVert
    import androidx.compose.material.icons.rounded.GraphicEq
    import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Check
    import androidx.compose.material3.*
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.clip
    import androidx.compose.ui.input.nestedscroll.nestedScroll
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.lifecycle.viewmodel.compose.viewModel
    import coil.compose.AsyncImage
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.OfficialPlaylistsData
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.ui.common.TrackListItemShimmer
    import com.alananasss.kittytune.ui.player.PlayerViewModel
    import com.alananasss.kittytune.ui.profile.SquareCard
    import com.alananasss.kittytune.ui.profile.SectionTitle
    import java.util.Locale

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun GenreDetailScreen(
        genreName: String,
        genreQuery: String,
        onBackClick: () -> Unit,
        onNavigate: (String) -> Unit,
        playerViewModel: PlayerViewModel,
        viewModel: GenreDetailViewModel = viewModel()
    ) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
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
                            onClick = {
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
                LargeTopAppBar(
                    title = { Text(viewModel.genreTitle, fontWeight = FontWeight.Bold) },
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
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            if (viewModel.isLoading && viewModel.popularTracks.isEmpty()) {
                LazyColumn(modifier = Modifier.padding(innerPadding)) {
                    items(10) { TrackListItemShimmer() }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    // section 1: popular tracks (multi-column horizontal scroll)
                    if (viewModel.popularTracks.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.profile_tab_popular)) }
                        item {
                            val pages = viewModel.popularTracks.chunked(5)
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                itemsIndexed(pages) { pageIndex, trackColumn ->
                                    Column(
                                        modifier = Modifier.width(300.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        trackColumn.forEachIndexed { itemIndexInColumn, track ->
                                            val absoluteIndex = pageIndex * 5 + itemIndexInColumn
                                            PopularTrackListItem(
                                                track = track,
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

                    // section 2: official playlists - conditional
                    if (viewModel.officialPlaylists.isNotEmpty()) {
                        item {
                            Column {
                                SectionTitle(stringResource(R.string.genre_official_playlists))
                                Surface(
                                    onClick = { showCountrySelector = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(OfficialPlaylistsData.sources[viewModel.selectedSourceIndex].flagEmoji)
                                        Spacer(Modifier.width(8.dp))
                                        Text(OfficialPlaylistsData.sources[viewModel.selectedSourceIndex].countryName, style = MaterialTheme.typography.labelLarge)
                                        Icon(Icons.Rounded.KeyboardArrowDown, null)
                                    }
                                }
                            }
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(viewModel.officialPlaylists) { playlist ->
                                    SquareCard(playlist) { onNavigate("playlist_detail/${playlist.id}") }
                                }
                            }
                        }
                    }

                    // section 3: community playlists
                    if (viewModel.communityPlaylists.isNotEmpty()) {
                        item {
                            SectionTitle(
                                stringResource(R.string.genre_community_playlists),
                                showMore = true,
                                onMoreClick = {
                                    val encodedTitle = Uri.encode(genreName)
                                    val encodedQuery = Uri.encode(genreQuery)
                                    onNavigate("genre_playlists/$encodedTitle/$encodedQuery")
                                }
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(viewModel.communityPlaylists) { playlist ->
                                    SquareCard(playlist) { onNavigate("playlist_detail/${playlist.id}") }
                                }
                            }
                        }
                    }

                    // section 4: albums
                    if (viewModel.albums.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.profile_tab_albums)) }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(viewModel.albums) { album ->
                                    SquareCard(album) { onNavigate("playlist_detail/${album.id}") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun PopularTrackListItem(
        track: Track,
        currentlyPlayingTrack: Track?,
        onClick: () -> Unit,
        onOptionClick: () -> Unit
    ) {
        val isCurrent = currentlyPlayingTrack?.id == track.id
        val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // artwork
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

            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = titleColor
                )
                Text(
                    text = "${track.user?.username ?: stringResource(R.string.unknown_artist)} • ${formatNumber(track.playbackCount)} ${stringResource(R.string.playback_count_formatted)}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // options icon
            IconButton(onClick = onOptionClick) {
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
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
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
