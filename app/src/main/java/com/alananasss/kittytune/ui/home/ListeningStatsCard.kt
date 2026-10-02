package com.alananasss.kittytune.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.local.TopArtistResult
import com.alananasss.kittytune.ui.profile.stats.formatDurationShort
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Summary data for the home screen stats card.
 */
private data class HomeStatsSummary(
    val listenedMs: Long,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
    val topArtist: TopArtistResult?,
)

/**
 * A summary card on the Home screen showing listening activity from the past week.
 * Only shown if the user has recorded listening activity.
 */
@Composable
fun ListeningStatsCard(
    modifier: Modifier = Modifier,
    onNavigateToStats: () -> Unit,
    onNavigateToYearlyPlayback: () -> Unit = {}
) {
    val weekAgo = remember { System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000 }
    val summary by produceState<HomeStatsSummary?>(initialValue = null, key1 = weekAgo) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                HomeStatsSummary(
                    listenedMs = ListeningStatsRepository.getTotalListenTime(weekAgo),
                    uniqueTracks = ListeningStatsRepository.getUniqueTracks(weekAgo),
                    uniqueArtists = ListeningStatsRepository.getUniqueArtists(weekAgo),
                    topArtist = ListeningStatsRepository.getTopArtists(weekAgo, limit = 1).firstOrNull(),
                )
            }.getOrNull()
        }
    }

    val stats = summary ?: return
    if (stats.listenedMs <= 0L) return

    Surface(
        onClick = onNavigateToStats,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.BarChart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.listening_stats_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = stringResource(R.string.listening_stats_period_week),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForwardIos,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(12.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatFigure(
                    value = formatDurationShort(stats.listenedMs),
                    label = stringResource(R.string.listening_stats_time_listened),
                    modifier = Modifier.weight(1f),
                )
                StatFigure(
                    value = stats.uniqueTracks.toString(),
                    label = stringResource(R.string.listening_stats_unique_tracks),
                    modifier = Modifier.weight(1f),
                )
                StatFigure(
                    value = stats.uniqueArtists.toString(),
                    label = stringResource(R.string.listening_stats_unique_artists),
                    modifier = Modifier.weight(1f),
                )
            }

            stats.topArtist?.let { artist ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!artist.artworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = artist.artworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.listening_stats_top_artists),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = artist.artistName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatFigure(value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
