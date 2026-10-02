package com.alananasss.kittytune.ui.profile.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.data.stats.ReportTrack
import kotlin.math.roundToInt

private const val COMPACT_ROWS = 10

/**
 * Everything at a glance: the numbers in one strip, a small activity strip, and the top ten tracks and artists
 * as tight tables. Ideal for looking something up quickly.
 */
@Composable
internal fun CompactStats(
    report: ListeningReport,
    period: ReportPeriod,
    onOpen: (StatsListTarget) -> Unit,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val isWide = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { NumbersStrip(report, period, onOpen) }
            item { ActivityStrip(report) }
            item {
                val tracks: @Composable (Modifier) -> Unit = { m ->
                    StatsCard(
                        stringResource(R.string.listening_stats_top_tracks),
                        m,
                        action = if (report.topTracks.size > COMPACT_ROWS) ({ onOpen(StatsListTarget.TRACKS) }) else null
                    ) {
                        report.topTracks.take(COMPACT_ROWS).forEachIndexed { index, track ->
                            CompactRow(
                                rank = index + 1,
                                title = track.title,
                                subtitle = track.artistName,
                                trailing = formatDurationShort(track.listenMs),
                                onClick = if (track.source == "soundcloud") ({ onTrackClick(track) }) else null,
                            )
                        }
                    }
                }
                val artists: @Composable (Modifier) -> Unit = { m ->
                    StatsCard(
                        stringResource(R.string.listening_stats_top_artists),
                        m,
                        action = if (report.topArtists.size > COMPACT_ROWS) ({ onOpen(StatsListTarget.ARTISTS) }) else null
                    ) {
                        report.topArtists.take(COMPACT_ROWS).forEachIndexed { index, artist ->
                            CompactRow(
                                rank = index + 1,
                                title = artist.name,
                                subtitle = null,
                                trailing = formatDurationShort(artist.listenMs),
                                imageUrl = artist.imageUrl,
                                onClick = { onArtistClick(artist) },
                            )
                        }
                    }
                }
                if (isWide) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(IntrinsicSize.Max)
                    ) {
                        tracks(Modifier.weight(1f).fillMaxHeight())
                        artists(Modifier.weight(1f).fillMaxHeight())
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        tracks(Modifier.fillMaxWidth())
                        artists(Modifier.fillMaxWidth())
                    }
                }
            }
            item { HabitsLine(report) }
        }
    }
}

@Composable
private fun NumbersStrip(
    report: ListeningReport,
    period: ReportPeriod,
    onOpen: (StatsListTarget) -> Unit
) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth < 520.dp) {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        NumberCell(
                            formatDurationShort(report.totalListenMs),
                            stringResource(R.string.listening_stats_time_listened),
                            Modifier.weight(1f),
                            null
                        )
                        NumberCell(
                            report.plays.toString(),
                            stringResource(R.string.listening_stats_plays),
                            Modifier.weight(1f)
                        ) { onOpen(StatsListTarget.PLAYS) }
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                        NumberCell(
                            report.uniqueTracks.toString(),
                            stringResource(R.string.listening_stats_unique_tracks),
                            Modifier.weight(1f)
                        ) { onOpen(StatsListTarget.TRACKS) }
                        NumberCell(
                            report.uniqueArtists.toString(),
                            stringResource(R.string.listening_stats_unique_artists),
                            Modifier.weight(1f)
                        ) { onOpen(StatsListTarget.ARTISTS) }
                    }
                }
                return@BoxWithConstraints
            }
            Row(
                Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                NumberCell(
                    formatDurationShort(report.totalListenMs),
                    stringResource(R.string.listening_stats_time_listened),
                    Modifier.weight(1.4f),
                    null
                )
                VerticalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                NumberCell(
                    report.plays.toString(),
                    stringResource(R.string.listening_stats_plays),
                    Modifier.weight(1f)
                ) { onOpen(StatsListTarget.PLAYS) }
                VerticalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                NumberCell(
                    report.uniqueTracks.toString(),
                    stringResource(R.string.listening_stats_unique_tracks),
                    Modifier.weight(1f)
                ) { onOpen(StatsListTarget.TRACKS) }
                VerticalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f))
                NumberCell(
                    report.uniqueArtists.toString(),
                    stringResource(R.string.listening_stats_unique_artists),
                    Modifier.weight(1f)
                ) { onOpen(StatsListTarget.ARTISTS) }
            }
        }
    }
    report.change?.let {
        Box(Modifier.padding(top = 8.dp, start = 4.dp)) { ChangeChip(it, period) }
    }
}

@Composable
private fun NumberCell(value: String, label: String, modifier: Modifier, onClick: (() -> Unit)?) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** A thin row of bars, one per day, today in the primary colour. */
@Composable
private fun ActivityStrip(report: ListeningReport) {
    val values = report.activity.map { it.listenMs }
    val max = (values.maxOrNull() ?: 0L).coerceAtLeast(1L)
    val current = report.activity.indexOfFirst { isCurrent(it) }
    val strong = MaterialTheme.colorScheme.primary
    val soft = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val empty = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                stringResource(R.string.listening_stats_activity_avg_day, formatDurationShort(report.averagePerDayMs)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(40.dp)) {
                if (values.isEmpty()) return@Canvas
                val slot = size.width / values.size
                val barWidth = (slot * 0.7f).coerceAtMost(20.dp.toPx())
                values.forEachIndexed { index, value ->
                    val left = slot * index + (slot - barWidth) / 2
                    val height = if (value > 0) (size.height * value / max).coerceAtLeast(3.dp.toPx()) else 3.dp.toPx()
                    val color = when {
                        value <= 0 -> empty
                        index == current -> strong
                        else -> soft
                    }
                    drawRoundRect(
                        color,
                        Offset(left, size.height - height),
                        Size(barWidth, height),
                        CornerRadius(barWidth / 3)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactRow(
    rank: Int,
    title: String,
    subtitle: String?,
    trailing: String,
    imageUrl: String? = null,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            rank.toString(),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(26.dp),
        )
        if (imageUrl != null || subtitle == null) {
            StatsCover(imageUrl, Modifier.size(28.dp).clip(CircleShape), placeholder = Icons.Rounded.Person)
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            trailing,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** The habits as one line of chips. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HabitsLine(report: ListeningReport) {
    val chips = buildList {
        add(stringResource(R.string.listening_stats_completion_rate) + ": ${(report.completionRate * 100).roundToInt()} %")
        add(stringResource(R.string.listening_stats_skip_rate) + ": ${(report.skipRate * 100).roundToInt()} %")
        add(stringResource(R.string.listening_stats_streak) + ": ${report.longestStreakDays}")
        report.peakHour?.let { add(stringResource(R.string.listening_stats_peak_hour_label, hourLabel(it))) }
    }
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        chips.forEach { text ->
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Text(
                    text,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }
}
