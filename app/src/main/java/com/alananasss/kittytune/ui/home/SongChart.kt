package com.alananasss.kittytune.ui.home

import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import java.util.Locale

fun ChartRepository.ChartKind.labelRes(): Int = when (this) {
    ChartRepository.ChartKind.COUNTRY -> R.string.chart_kind_country
    ChartRepository.ChartKind.TOP -> R.string.chart_kind_top
    ChartRepository.ChartKind.TRENDING -> R.string.chart_kind_trending
}

fun formatCompactNumber(count: Long): String {
    return when {
        count >= 1_000_000_000 -> String.format(Locale.US, "%.1fB", count / 1_000_000_000.0)
        count >= 1_000_000 -> String.format(Locale.US, "%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format(Locale.US, "%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

fun getCountryStringRes(context: Context, code: String): Int {
    val resId = when (code.lowercase(Locale.ROOT)) {
        "ru" -> R.string.chart_country_ru
        "us" -> R.string.chart_country_us
        "uk" -> R.string.chart_country_uk
        "de" -> R.string.chart_country_de
        "fr" -> R.string.chart_country_fr
        "hu" -> R.string.chart_country_hu
        "vn" -> R.string.chart_country_vn
        "fi" -> R.string.chart_country_fi
        "se" -> R.string.chart_country_se
        "no" -> R.string.chart_country_no
        "pl" -> R.string.chart_country_pl
        "it" -> R.string.chart_country_it
        "es" -> R.string.chart_country_es
        "nl" -> R.string.chart_country_nl
        "ca" -> R.string.chart_country_ca
        "au" -> R.string.chart_country_au
        "jp" -> R.string.chart_country_jp
        "br" -> R.string.chart_country_br
        "mx" -> R.string.chart_country_mx
        "ie" -> R.string.chart_country_ie
        "at" -> R.string.chart_country_at
        "cz" -> R.string.chart_country_cz
        else -> 0
    }
    if (resId != 0) return resId
    val resName = "chart_country_${code.lowercase(Locale.ROOT)}"
    return context.resources.getIdentifier(resName, "string", context.packageName)
}

fun getGenreStringRes(context: Context, id: String): Int {
    val resId = when (id.lowercase(Locale.ROOT)) {
        "all" -> R.string.chart_genre_all
        "pop" -> R.string.chart_genre_pop
        "hiphop" -> R.string.chart_genre_hiphop
        "electronic" -> R.string.chart_genre_electronic
        "rock" -> R.string.chart_genre_rock
        "rnb" -> R.string.chart_genre_rnb
        "country" -> R.string.chart_genre_country
        "latin" -> R.string.chart_genre_latin
        "folk" -> R.string.chart_genre_folk
        else -> 0
    }
    if (resId != 0) return resId
    val resName = "chart_genre_${id.lowercase(Locale.ROOT)}"
    return context.resources.getIdentifier(resName, "string", context.packageName)
}

@Composable
fun rememberFilteredTracks(tracks: List<Track>): List<Track> {
    val blockedTracks by BlockManager.blockedTrackIdsFlow.collectAsState()
    val blockedArtists by BlockManager.blockedArtistIdsFlow.collectAsState()
    val snapshot = tracks.toList()
    return remember(snapshot, blockedTracks, blockedArtists) {
        if (blockedTracks.isEmpty() && blockedArtists.isEmpty()) {
            snapshot
        } else {
            BlockManager.filterBlocked(snapshot)
        }
    }
}

/**
 * A song chart: the songs in order, with their place in the order (ported from KittyTuneDesktop).
 *
 * Uses ExpressiveConnectedButtonGroup for kinds, and FilterChips for countries/genres.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SongChart(
    kind: ChartRepository.ChartKind,
    genre: ChartRepository.ChartGenre,
    genres: List<ChartRepository.ChartGenre>,
    onKindChange: (ChartRepository.ChartKind) -> Unit,
    onGenreChange: (ChartRepository.ChartGenre) -> Unit,
    modifier: Modifier = Modifier,
    showGenreRow: Boolean = true,
    country: ChartRepository.ChartCountry? = null,
    onCountryChange: (ChartRepository.ChartCountry) -> Unit = {},
    isSwitching: Boolean = false,
    compactKinds: Boolean = false,
) {
    val context = LocalContext.current

    Column(modifier = modifier) {
        Box {
            if (compactKinds) {
                val kindScrollState = rememberScrollState()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(kindScrollState),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ChartRepository.ChartKind.entries.forEach { option ->
                        FilterChip(
                            selected = option == kind,
                            onClick = hapticAction(HapticKind.UI) { onKindChange(option) },
                            label = { Text(stringResource(option.labelRes()), maxLines = 1) },
                        )
                    }
                }
            } else {
                ExpressiveConnectedButtonGroup(
                    options = ChartRepository.ChartKind.entries,
                    selectedOption = kind,
                    onOptionSelected = onKindChange,
                    labelProvider = { option ->
                        Text(
                            text = stringResource(option.labelRes()),
                            maxLines = 1,
                        )
                    },
                )
            }

            if (isSwitching) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.Transparent,
                )
            }
        }

        if (country != null && kind == ChartRepository.ChartKind.COUNTRY) {
            Spacer(Modifier.height(4.dp))
            val sortedCountries = remember(country) {
                ChartRepository.ChartCountry.entries.sortedBy { if (it == country) 0 else 1 }
            }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(sortedCountries) { option ->
                    val countryStringRes = getCountryStringRes(context, option.code)
                    val label = if (countryStringRes != 0) stringResource(countryStringRes) else option.deezerName
                    FilterChip(
                        selected = option == country,
                        onClick = hapticAction(HapticKind.UI) { onCountryChange(option) },
                        label = { Text(label, maxLines = 1) },
                        shape = FilterChipDefaults.shape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    )
                }
            }
        }

        if (showGenreRow && kind == ChartRepository.ChartKind.TOP) {
            Spacer(Modifier.height(4.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 0.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(genres) { option ->
                    val selected = option == genre
                    val genreStringRes = getGenreStringRes(context, option.id)
                    val label = if (genreStringRes != 0) stringResource(genreStringRes) else option.id.replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = selected,
                        onClick = hapticAction(HapticKind.UI) { onGenreChange(option) },
                        label = { Text(label, maxLines = 1) },
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
}

/**
 * One row of a chart (desktop parity).
 *
 * The top three are on the podium in accent color, while 4+ use onSurfaceVariant.
 */
@Composable
fun ChartTrackRow(
    track: Track,
    rank: Int,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onArtistClick: ((Track) -> Unit)? = null,
    onOptionClick: (() -> Unit)? = null,
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
            .hapticClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                model = track.thumbnailUrl ?: track.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
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
                text = track.title.orEmpty(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent || onPodium) FontWeight.Bold else FontWeight.Medium,
                color = titleColor,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.displayArtist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .then(
                            if (onArtistClick != null) Modifier.hapticClickable { onArtistClick(track) }
                            else Modifier
                        ),
                )
                if (track.playbackCount > 0) {
                    Text(
                        text = " • ${formatCompactNumber(track.playbackCount.toLong())} ${stringResource(R.string.playback_count_formatted)}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (onOptionClick != null) {
            IconButton(
                onClick = onOptionClick,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.btn_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
