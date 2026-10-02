package com.alananasss.kittytune.ui.profile.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.data.stats.ReportTrack

/**
 * The span as a recap: big cards — the time, the artist, the track, when, and the habits — in
 * large type on rich gradient colours. For looking back like a Spotify Wrapped.
 */
@Composable
internal fun StoryStats(
    report: ListeningReport,
    period: ReportPeriod,
    onOpen: (StatsListTarget) -> Unit,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item {
            StoryCard(
                background = Brush.linearGradient(listOf(scheme.primaryContainer, scheme.tertiaryContainer)),
                contentColor = scheme.onPrimaryContainer,
                index = 0
            ) {
                Text(stringResource(R.string.stats_story_you_listened), style = MaterialTheme.typography.titleMedium)
                Text(
                    formatDurationShort(report.totalListenMs),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, lineHeight = 62.sp),
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.stats_story_numbers, report.plays, report.uniqueTracks, report.uniqueArtists),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.clickable { onOpen(StatsListTarget.PLAYS) },
                )
                report.change?.let {
                    Spacer(Modifier.height(12.dp))
                    ChangeChip(it, period)
                }
            }
        }
        report.topArtists.firstOrNull()?.let { artist ->
            item {
                StoryCard(
                    background = Brush.linearGradient(listOf(scheme.secondaryContainer, scheme.primaryContainer)),
                    contentColor = scheme.onSecondaryContainer,
                    index = 1
                ) {
                    Text(stringResource(R.string.stats_story_top_artist), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatsCover(
                            artist.imageUrl,
                            Modifier.size(110.dp).clip(CircleShape).clickable { onArtistClick(artist) },
                            placeholder = Icons.Rounded.Person,
                        )
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                artist.name,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(formatDuration(artist.listenMs), style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            report.topArtists.drop(1).take(4).forEachIndexed { index, other ->
                                Text(
                                    "${index + 2}. ${other.name}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clickable { onArtistClick(other) }.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        report.topTracks.firstOrNull()?.let { track ->
            item {
                StoryCard(
                    background = Brush.linearGradient(listOf(scheme.tertiaryContainer, scheme.secondaryContainer)),
                    contentColor = scheme.onTertiaryContainer,
                    index = 2
                ) {
                    Text(stringResource(R.string.stats_story_top_track), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatsCover(
                            track.artworkUrl,
                            Modifier.size(110.dp).clip(RoundedCornerShape(20.dp))
                                .clickable(enabled = track.source == "soundcloud") { onTrackClick(track) },
                        )
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                track.artistName,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                stringResource(R.string.stats_story_track_times, track.plays, formatDuration(track.listenMs)),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
        report.peakHour?.let { hour ->
            item {
                StoryCard(
                    background = Brush.linearGradient(listOf(scheme.surfaceContainerHighest, scheme.primaryContainer)),
                    contentColor = scheme.onSurface,
                    index = 3
                ) {
                    Text(stringResource(R.string.stats_story_when), style = MaterialTheme.typography.titleMedium)
                    Text(
                        hourLabel(hour),
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 56.sp, lineHeight = 62.sp),
                        fontWeight = FontWeight.Black
                    )
                    report.busiestWeekday?.let {
                        Text(
                            stringResource(R.string.listening_stats_busiest_day_named, weekdayName(it)),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
        item {
            StoryCard(
                background = Brush.linearGradient(listOf(scheme.primary, scheme.tertiary)),
                contentColor = scheme.onPrimary,
                index = 4
            ) {
                Text(stringResource(R.string.stats_story_habits), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    BigFigure(report.longestStreakDays.toString(), stringResource(R.string.listening_stats_streak))
                    BigFigure("${(report.completionRate * 100).toInt()} %", stringResource(R.string.listening_stats_completion_rate))
                    BigFigure(report.activeDays.toString(), stringResource(R.string.stats_story_active_days))
                }
            }
        }
    }
}

@Composable
private fun BigFigure(value: String, label: String) {
    Column {
        Text(value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

/** One recap card: a gradient, big type, and a short rise-in as it first appears. */
@Composable
private fun StoryCard(
    background: Brush,
    contentColor: Color,
    index: Int,
    content: @Composable ColumnScope.() -> Unit
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(420, delayMillis = 70 * index)) }
    BoxWithConstraints(Modifier.widthIn(max = 880.dp).fillMaxWidth()) {
        val isNarrow = maxWidth < 400.dp
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = appear.value
                    translationY = (1f - appear.value) * 24.dp.toPx()
                }
                .clip(RoundedCornerShape(32.dp))
                .background(background)
                .padding(if (isNarrow) 20.dp else 28.dp),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                Column(content = content)
            }
        }
    }
}
