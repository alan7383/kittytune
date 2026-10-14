package com.alananasss.kittytune.ui.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.TrackListItemShimmer
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.util.Locale

/**
 * The charts screen: the songs in order, with their place in the order.
 *
 * The chart itself lives in [ChartsViewModel]; this screen is the picker (which list, which country,
 * which genre) and the rows. The rank is the row's identity, so it is set in the accent colour and
 * never shares a column with the play count — a chart you have to count down to read is a list.
 */
@Composable
fun ChartsScreen(
    onBackClick: () -> Unit,
    onPlaylistClick: (Long) -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    viewModel: ChartsViewModel = viewModel()
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.home_charts),
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                scrollBehavior = scrollBehavior
            )
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.padding(innerPadding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                // Which list: the country's chart, SoundCloud's curated Top 50, or the live trending feed.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChartKind.entries.forEach { option ->
                        FilterChip(
                            selected = option == viewModel.chartKind,
                            onClick = { viewModel.loadChart(option, viewModel.chartGenre) },
                            label = { Text(stringResource(option.labelKey()), maxLines = 1) },
                            shape = FilterChipDefaults.shape,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        )
                    }
                }
                // A switch is in flight, so the list below is about to be replaced.
                if (viewModel.isChartLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(2.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.Transparent,
                    )
                }

                if (viewModel.chartKind == ChartKind.COUNTRY) {
                    // Which country: the listener's own first, then the rest in their usual order.
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ChartCountry.entries.sortedBy {
                            if (it == ChartCountry.forDevice(Locale.getDefault().language)) 0 else 1
                        }) { option ->
                            FilterChip(
                                selected = option == viewModel.chartCountry,
                                onClick = { viewModel.selectChartCountry(option) },
                                label = { Text(stringResource(chartCountryLabel(option)), maxLines = 1) },
                                shape = FilterChipDefaults.shape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                }

                if (viewModel.chartKind == ChartKind.TOP) {
                    // Only for the curated Top 50. The trending feed has no genre to pick — asking it for one
                    // returns an empty chart — so a genre row shown there would be a control that does nothing.
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(ChartsViewModel.chartGenres) { option ->
                            FilterChip(
                                selected = option == viewModel.chartGenre,
                                onClick = { viewModel.loadChart(viewModel.chartKind, option) },
                                label = { Text(stringResource(chartGenreLabel(option)), maxLines = 1) },
                                shape = FilterChipDefaults.shape,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                ),
                            )
                        }
                    }
                }
            }

            if (viewModel.isChartLoading && viewModel.chartEntries.isEmpty()) {
                items(8) { TrackListItemShimmer() }
            } else {
                items(viewModel.chartEntries, key = { it.rank }) { entry ->
                    ChartTrackRow(
                        track = entry.track,
                        rank = entry.rank,
                        currentlyPlayingTrack = playerViewModel.currentTrack,
                        onClick = {
                            // A chart is a queue: pressing a song plays the chart from that place.
                            val tracks = viewModel.chartEntries.map { it.track }
                            val index = viewModel.chartEntries.indexOfFirst { it.rank == entry.rank }
                            if (index >= 0) {
                                playerViewModel.playPlaylist(tracks, index)
                            }
                        },
                    )
                }
            }
        }
    }
}

/** The label of a chart country, as the [R.string.chart_country_ru]-style keys spell it. */
private fun chartCountryLabel(country: ChartCountry): Int = when (country) {
    ChartCountry.RU -> R.string.chart_country_ru
    ChartCountry.US -> R.string.chart_country_us
    ChartCountry.UK -> R.string.chart_country_uk
    ChartCountry.DE -> R.string.chart_country_de
    ChartCountry.FR -> R.string.chart_country_fr
    ChartCountry.HU -> R.string.chart_country_hu
    ChartCountry.VN -> R.string.chart_country_vn
    ChartCountry.FI -> R.string.chart_country_fi
    ChartCountry.SE -> R.string.chart_country_se
    ChartCountry.NO -> R.string.chart_country_no
    ChartCountry.PL -> R.string.chart_country_pl
    ChartCountry.IT -> R.string.chart_country_it
    ChartCountry.ES -> R.string.chart_country_es
    ChartCountry.NL -> R.string.chart_country_nl
    ChartCountry.CA -> R.string.chart_country_ca
    ChartCountry.AU -> R.string.chart_country_au
    ChartCountry.JP -> R.string.chart_country_jp
    ChartCountry.BR -> R.string.chart_country_br
    ChartCountry.MX -> R.string.chart_country_mx
    ChartCountry.IE -> R.string.chart_country_ie
    ChartCountry.AT -> R.string.chart_country_at
    ChartCountry.CZ -> R.string.chart_country_cz
}

/** The label of a chart genre, as the [R.string.chart_genre_all]-style keys spell it. */
private fun chartGenreLabel(genre: ChartGenre): Int = when (genre.id) {
    "all" -> R.string.chart_genre_all
    "pop" -> R.string.chart_genre_pop
    "hiphop" -> R.string.chart_genre_hiphop
    "electronic" -> R.string.chart_genre_electronic
    "rock" -> R.string.chart_genre_rock
    "rnb" -> R.string.chart_genre_rnb
    "country" -> R.string.chart_genre_country
    "latin" -> R.string.chart_genre_latin
    "folk" -> R.string.chart_genre_folk
    else -> R.string.chart_genre_all
}

/**
 * One row of a chart.
 *
 * The top three are the ones a listener looks for, so they are set in the accent colour and given the
 * emphasised title; from fourth down the rank steps back to a supporting tone, which is what turns a
 * column of fifty numbers into a podium with a long tail behind it.
 */
@Composable
private fun ChartTrackRow(
    track: Track,
    rank: Int,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val onPodium = rank <= 3

    val rankColor by animateColorAsState(
        targetValue = when {
            isCurrent -> MaterialTheme.colorScheme.primary
            onPodium -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = "chartRankColor",
    )
    val titleColor by animateColorAsState(
        targetValue = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        label = "chartTitleColor",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style = if (onPodium) {
                MaterialTheme.typography.titleLarge
            } else {
                MaterialTheme.typography.titleSmall
            },
            fontWeight = FontWeight.Bold,
            color = rankColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(34.dp),
        )

        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title ?: stringResource(R.string.untitled_track),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent || onPodium) FontWeight.Bold else FontWeight.Medium,
                color = titleColor,
            )
            Text(
                text = "${
                    formatCompactNumber(track.playbackCount.toLong())
                } ${stringResource(R.string.playback_count_formatted)} • ${track.displayArtist}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatCompactNumber(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        count > 0 -> count.toString()
        else -> "0"
    }
}
