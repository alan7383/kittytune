package com.alananasss.kittytune.ui.profile.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportTrack
import kotlin.math.roundToInt

/** A ranked track, with a bar under it for its share of the top track's listening time. */
@Composable
internal fun RankedTrackRow(
    rank: Int,
    track: ReportTrack,
    share: Float,
    onClick: () -> Unit
) {
    val clickable = track.source == "soundcloud"
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = clickable, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )
        Spacer(Modifier.width(6.dp))
        StatsCover(track.artworkUrl, Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                track.artistName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { share.coerceIn(0.02f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                gapSize = 0.dp,
                drawStopIndicator = {}
            )
        }
        Spacer(Modifier.width(12.dp))
        PlaysAndTime(track.plays, track.listenMs)
    }
}

/**
 * The play count over the time, in a column wide enough for the longest time so nothing is cut.
 */
@Composable
internal fun PlaysAndTime(plays: Int, listenMs: Long) {
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 72.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                plays.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            formatDuration(listenMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** A circular artist tile with the rank as a badge. */
@Composable
internal fun ArtistTile(
    rank: Int,
    artist: ReportArtist,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            StatsCover(
                artist.imageUrl,
                Modifier.size(84.dp).clip(CircleShape),
                placeholder = Icons.Rounded.Person
            )
            Surface(
                shape = CircleShape,
                color = if (rank == 1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.align(Alignment.BottomStart).size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        rank.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (rank == 1) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            formatDuration(artist.listenMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The four habits that come out of the report: how much of what was started got finished, how much
 * was skipped, how long a play lasts, and how long the streak has been.
 */
@Composable
internal fun HabitsGrid(report: ListeningReport, perRow: Int = 2, modifier: Modifier = Modifier) {
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        { m ->
            HabitTile(
                Icons.Rounded.CheckCircle,
                "${(report.completionRate * 100).roundToInt()} %",
                stringResource(R.string.listening_stats_completion_rate),
                stringResource(R.string.listening_stats_completion_rate_desc),
                m
            )
        },
        { m ->
            HabitTile(
                Icons.Rounded.SkipNext,
                "${(report.skipRate * 100).roundToInt()} %",
                stringResource(R.string.listening_stats_skip_rate),
                stringResource(R.string.listening_stats_skips_of, report.skips),
                m
            )
        },
        { m ->
            HabitTile(
                Icons.Rounded.Timer,
                formatDuration(report.averageListenMs),
                stringResource(R.string.listening_stats_avg_play),
                stringResource(R.string.listening_stats_avg_play_desc),
                m
            )
        },
        { m ->
            HabitTile(
                Icons.Rounded.LocalFireDepartment,
                report.longestStreakDays.toString(),
                stringResource(R.string.listening_stats_streak),
                stringResource(R.string.listening_stats_active_days_count, report.activeDays),
                m
            )
        }
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(perRow).forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.height(IntrinsicSize.Max)
            ) {
                row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
            }
        }
    }
}

@Composable
private fun HabitTile(
    icon: ImageVector,
    value: String,
    title: String,
    description: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier
    ) {
        Column(Modifier.padding(18.dp)) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
