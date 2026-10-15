package com.alananasss.kittytune.ui.home

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.util.Locale

/**
 * The charts (issue #66, ported from desktop): what a country is listening to, one list, ranked as the
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
    val context = LocalContext.current
    val entries = viewModel.chartEntries
    val loading = viewModel.isChartLoading

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.home_charts), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.btn_back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item {
                KindChips(
                    selected = viewModel.chartKind,
                    onSelect = viewModel::selectChartKind,
                )
            }
            if (viewModel.chartKind == ChartRepository.ChartKind.COUNTRY) {
                item {
                    CountryChips(
                        countries = remember { countryOrderFor(viewModel.chartCountry) },
                        selected = viewModel.chartCountry,
                        onSelect = viewModel::selectChartCountry,
                    )
                }
            }
            if (viewModel.chartKind == ChartRepository.ChartKind.TOP) {
                item {
                    GenreChips(
                        genres = ChartRepository.chartGenres,
                        selected = viewModel.chartGenre,
                        onSelect = viewModel::selectChartGenre,
                    )
                }
            }
            if (loading && entries.isEmpty()) {
                items(8) {
                    ShimmerChartRow()
                }
            } else if (entries.isEmpty()) {
                item {
                    Text(
                        stringResource(R.string.chart_failed),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    )
                }
            } else {
                itemsIndexed(entries, key = { _, e -> "${e.rank}-${e.track.id}" }) { index, entry ->
                    ChartTrackRow(
                        entry = entry,
                        isPlaying = playerViewModel.currentTrack?.id == entry.track.id,
                        onClick = {
                            val label = when (viewModel.chartKind) {
                                ChartRepository.ChartKind.COUNTRY -> viewModel.chartCountry.deezerName
                                ChartRepository.ChartKind.TOP -> stringResource(R.string.chart_kind_top)
                                ChartRepository.ChartKind.TRENDING -> stringResource(R.string.chart_kind_trending)
                            }
                            playerViewModel.playPlaylist(
                                tracks = entries.map { it.track },
                                startIndex = index,
                                context = PlaybackContext(displayText = label, navigationId = "charts"),
                            )
                        },
                    )
                }
            }
        }
    }
}

/** The device's country first, then the rest: the chart most likely yours is one tap away. */
private fun countryOrderFor(deviceCountry: ChartRepository.ChartCountry): List<ChartRepository.ChartCountry> =
    listOf(deviceCountry) + ChartRepository.ChartCountry.entries.filter { it != deviceCountry }

@Composable
private fun KindChips(
    selected: ChartRepository.ChartKind,
    onSelect: (ChartRepository.ChartKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KindChip(stringResource(R.string.chart_kind_country), selected == ChartRepository.ChartKind.COUNTRY) {
            onSelect(ChartRepository.ChartKind.COUNTRY)
        }
        KindChip(stringResource(R.string.chart_kind_top), selected == ChartRepository.ChartKind.TOP) {
            onSelect(ChartRepository.ChartKind.TOP)
        }
        KindChip(stringResource(R.string.chart_kind_trending), selected == ChartRepository.ChartKind.TRENDING) {
            onSelect(ChartRepository.ChartKind.TRENDING)
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    )
}

@Composable
private fun CountryChips(
    countries: List<ChartRepository.ChartCountry>,
    selected: ChartRepository.ChartCountry,
    onSelect: (ChartRepository.ChartCountry) -> Unit,
) {
    val locale = remember { Locale.getDefault() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        countries.forEach { country ->
            val name = remember(country.code) { Locale("", country.code).getDisplayCountry(locale).ifBlank { country.deezerName } }
            FilterChip(
                selected = country == selected,
                onClick = { onSelect(country) },
                label = { Text("$name ${flagOf(country.code)}", fontWeight = if (country == selected) FontWeight.Bold else FontWeight.Medium) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

@Composable
private fun GenreChips(
    genres: List<ChartRepository.ChartGenre>,
    selected: ChartRepository.ChartGenre,
    onSelect: (ChartRepository.ChartGenre) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        genres.forEach { genre ->
            FilterChip(
                selected = genre == selected,
                onClick = { onSelect(genre) },
                label = { Text(genre.id.replaceFirstChar { it.uppercase() }, fontWeight = if (genre == selected) FontWeight.Bold else FontWeight.Medium) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            )
        }
    }
}

/** The country's flag, from its code: regional-indicator letters, which Android renders as one glyph. */
private fun flagOf(code: String): String =
    code.uppercase().map { Character.toChars(0x1F1E6 + (it.code - 'A'.code))[0] }.joinToString("")

@Composable
private fun ChartTrackRow(entry: ChartRepository.ChartEntry, isPlaying: Boolean, onClick: () -> Unit) {
    val track: Track = entry.track
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.rank.toString(),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = if (entry.rank <= 3 || isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(40.dp),
        )
        AsyncImage(
            model = track.artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title.orEmpty(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    track.displayArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                track.playbackCount.takeIf { it > 0 }?.let { plays ->
                    Text(
                        " · " + stringResource(R.string.chart_plays, plays),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (isPlaying) {
            Icon(
                Icons.Rounded.GraphicEq,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ShimmerChartRow() {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Box(
                    Modifier
                        .fillMaxWidth(0.6f)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.size(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.35f)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}
