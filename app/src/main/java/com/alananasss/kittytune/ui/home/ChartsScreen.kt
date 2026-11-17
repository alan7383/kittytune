package com.alananasss.kittytune.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.ui.common.ShimmerBox
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel

/**
 * The charts screen (desktop parity): what a country is listening to, one list, ranked as the
 * source ranks it. A chart is a queue — tapping a song plays the chart from there.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChartsScreen(
    onBackClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: ChartsViewModel = viewModel(),
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val visibleTracks = rememberFilteredTracks(viewModel.chartEntries.map { it.track })
    val chartEntries = remember(viewModel.chartEntries, visibleTracks) {
        val visibleIds = visibleTracks.map { it.id }.toSet()
        viewModel.chartEntries.filter { it.track.id in visibleIds }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home_charts),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
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
                    if (chartEntries.isNotEmpty()) {
                        FilledTonalIconButton(
                            onClick = hapticAction(HapticKind.UI) {
                                playerViewModel.prepareBulkAdd(chartEntries.map { it.track })
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
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(bottom = 180.dp),
        ) {
            item {
                SongChart(
                    kind = viewModel.chartKind,
                    genre = viewModel.chartGenre,
                    genres = ChartRepository.chartGenres,
                    onKindChange = { viewModel.loadChart(it, viewModel.chartGenre) },
                    onGenreChange = { viewModel.loadChart(viewModel.chartKind, it) },
                    country = viewModel.chartCountry,
                    onCountryChange = { viewModel.selectChartCountry(it) },
                    isSwitching = viewModel.isChartLoading,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (viewModel.isChartLoading && chartEntries.isEmpty()) {
                items(8) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        ShimmerBox(Modifier.fillMaxSize())
                    }
                }
            } else if (chartEntries.isNotEmpty()) {
                itemsIndexed(chartEntries, key = { _, e -> "${e.rank}-${e.track.id}" }) { index, entry ->
                    val label = when (viewModel.chartKind) {
                        ChartRepository.ChartKind.COUNTRY -> viewModel.chartCountry.deezerName
                        ChartRepository.ChartKind.TOP -> stringResource(R.string.chart_kind_top)
                        ChartRepository.ChartKind.TRENDING -> stringResource(R.string.chart_kind_trending)
                    }
                    ChartTrackRow(
                        track = entry.track,
                        rank = entry.rank,
                        currentlyPlayingTrack = playerViewModel.uiCurrentTrack ?: playerViewModel.currentTrack,
                        onClick = hapticAction(HapticKind.UI) {
                            playerViewModel.playPlaylist(
                                tracks = chartEntries.map { it.track },
                                startIndex = index,
                                context = PlaybackContext(
                                    displayText = label,
                                    navigationId = "charts",
                                ),
                            )
                        },
                        onArtistClick = { track ->
                            track.user?.id?.let { artistId ->
                                onNavigate("profile:$artistId")
                            }
                        },
                        onOptionClick = hapticAction(HapticKind.UI) { playerViewModel.showTrackOptions(entry.track) },
                        modifier = Modifier.padding(horizontal = 6.dp),
                    )
                }
            } else {
                item {
                    Text(
                        stringResource(R.string.chart_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                }
            }
        }
    }
}
