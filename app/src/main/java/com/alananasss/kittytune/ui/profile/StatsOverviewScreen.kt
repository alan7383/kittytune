package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportPeriod
import com.alananasss.kittytune.data.stats.ReportTrack
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.profile.stats.ActivityCard
import com.alananasss.kittytune.ui.profile.stats.ArtistTile
import com.alananasss.kittytune.ui.profile.stats.ChangeChip
import com.alananasss.kittytune.ui.profile.stats.HabitsGrid
import com.alananasss.kittytune.ui.profile.stats.HoursCard
import com.alananasss.kittytune.ui.profile.stats.formatDuration
import com.alananasss.kittytune.ui.profile.stats.PlaysListDialog
import com.alananasss.kittytune.ui.profile.stats.RankedTrackRow
import com.alananasss.kittytune.ui.profile.stats.ArtistsListDialog
import com.alananasss.kittytune.ui.profile.stats.StatsCard
import com.alananasss.kittytune.ui.profile.stats.TracksListDialog
import com.alananasss.kittytune.ui.profile.stats.spanLabel

private const val TOP_TRACKS_SHOWN = 5
private const val TOP_ARTISTS_SHOWN = 6

/** Which full list is open, if any. */
private enum class StatsList { NONE, PLAYS, TRACKS, ARTISTS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListeningStatsScreen(
    onBackClick: () -> Unit,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit
) {
    val viewModel: ListeningStatsViewModel = viewModel()
    val report = viewModel.report
    val isLoading = viewModel.isLoading
    val period = viewModel.selectedPeriod.toReportPeriod()
    var openList by remember { mutableStateOf(StatsList.NONE) }

    if (report != null) {
        when (openList) {
            StatsList.PLAYS -> PlaysListDialog(viewModel.events, { id, title ->
                onTrackClick(ReportTrack(id, title, "", null, "soundcloud", 0, 0L))
            }) { openList = StatsList.NONE }
            StatsList.TRACKS -> TracksListDialog(report.topTracks, onTrackClick) { openList = StatsList.NONE }
            StatsList.ARTISTS -> ArtistsListDialog(report.topArtists, onArtistClick) { openList = StatsList.NONE }
            StatsList.NONE -> Unit
        }
    }

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            stringResource(R.string.listening_stats_title),
                            fontWeight = FontWeight.Bold
                        )
                        // Which days the numbers actually cover. A bare total does not say whether
                        // it is three days or three years, which matters once "all time" is one of
                        // the options.
                        Text(
                            report?.let { spanLabel(period, it) } ?: " ",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.btn_close))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            StatsHeader(period = period, onSelect = { viewModel.selectPeriod(it.toStatsPeriod()) })

            AnimatedContent(
                targetState = report?.takeIf { !isLoading },
                transitionSpec = {
                    fadeIn(tween(220, delayMillis = 60)) togetherWith fadeOut(tween(90))
                },
                label = "statsBody",
                modifier = Modifier.fillMaxSize()
            ) { shown ->
                if (shown == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                } else {
                    OverviewStats(shown, period, { openList = it }, onTrackClick, onArtistClick)
                }
            }
        }
    }
}

@Composable
private fun StatsHeader(
    period: ReportPeriod,
    onSelect: (ReportPeriod) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp)
    ) {
        ExpressiveConnectedButtonGroup(
            options = ReportPeriod.entries,
            selectedOption = period,
            onOptionSelected = onSelect,
            modifier = Modifier.fillMaxWidth(),
            labelProvider = { value ->
                Text(
                    stringResource(
                        when (value) {
                            ReportPeriod.WEEK -> R.string.listening_stats_period_week_short
                            ReportPeriod.MONTH -> R.string.listening_stats_period_month_short
                            ReportPeriod.YEAR -> R.string.listening_stats_period_year_short
                            ReportPeriod.ALL_TIME -> R.string.listening_stats_period_all
                        }
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    softWrap = false
                )
            }
        )
    }
}

/**
 * The overview: the headline, its three counts, the two charts, the top lists and the habits.
 *
 * Everything is a target: the three counts open the full lists, the ranked rows and the artist
 * tiles play or navigate. A number you cannot act on is a number you only read.
 */
@Composable
private fun OverviewStats(
    report: ListeningReport,
    period: ReportPeriod,
    onOpen: (StatsList) -> Unit,
    onTrackClick: (ReportTrack) -> Unit,
    onArtistClick: (ReportArtist) -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Two charts sit side by side only where there is genuinely room; a phone stacks them, and
        // so does a narrow desktop window.
        val isWide = maxWidth >= 760.dp
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { SummaryCard(report, period, onOpen) }
            item {
                if (isWide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActivityCard(report, period, Modifier.weight(1.6f))
                        HoursCard(report, Modifier.weight(1f))
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActivityCard(report, period, Modifier.fillMaxWidth())
                        HoursCard(report, Modifier.fillMaxWidth())
                    }
                }
            }
            if (report.topTracks.isNotEmpty()) {
                item {
                    StatsCard(
                        title = stringResource(R.string.listening_stats_top_tracks),
                        action = if (report.topTracks.size > TOP_TRACKS_SHOWN) {
                            { onOpen(StatsList.TRACKS) }
                        } else null
                    ) {
                        val top = report.topTracks.first().listenMs
                        report.topTracks.take(TOP_TRACKS_SHOWN).forEachIndexed { index, track ->
                            RankedTrackRow(index + 1, track, share = track.listenMs.toFloat() / top) {
                                onTrackClick(track)
                            }
                        }
                    }
                }
            }
            if (report.topArtists.isNotEmpty()) {
                item {
                    StatsCard(
                        title = stringResource(R.string.listening_stats_top_artists),
                        action = if (report.topArtists.size > TOP_ARTISTS_SHOWN) {
                            { onOpen(StatsList.ARTISTS) }
                        } else null
                    ) {
                        // Three across in two rows rather than six in one: at a phone's width six
                        // 84 dp tiles leave about 40 dp each, which crops the names to nothing and
                        // puts the rank badge over the face. Six in a row only fits a tablet.
                        val perRow = if (isWide) TOP_ARTISTS_SHOWN else 3
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            report.topArtists.take(TOP_ARTISTS_SHOWN)
                                .mapIndexed { index, artist -> (index + 1) to artist }
                                .chunked(perRow)
                                .forEach { row ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        row.forEach { (rank, artist) ->
                                            ArtistTile(rank, artist, Modifier.weight(1f)) { onArtistClick(artist) }
                                        }
                                        repeat((perRow - row.size).coerceAtLeast(0)) {
                                            Spacer(Modifier.weight(1f))
                                        }
                                    }
                                }
                        }
                    }
                }
            }
            item { HabitsGrid(report, if (isWide) 4 else 2) }
        }
    }
}

/**
 * The headline: the total, the change against the previous span, and the three counts.
 *
 * The counts are tiles rather than labels because each one opens the list behind it, and on a phone
 * they sit three across so the whole summary is one tap-high instead of three stacked rows.
 */
@Composable
private fun SummaryCard(
    report: ListeningReport,
    period: ReportPeriod,
    onOpen: (StatsList) -> Unit
) {
    Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                stringResource(R.string.listening_stats_time_listened),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(report.totalListenMs),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.weight(1f, fill = false)
                )
                report.change?.let {
                    Spacer(Modifier.width(10.dp))
                    ChangeChip(it, period)
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SummaryTile(
                    icon = Icons.Rounded.PlayArrow,
                    value = report.plays.toString(),
                    label = stringResource(R.string.listening_stats_plays),
                    modifier = Modifier.weight(1f)
                ) { onOpen(StatsList.PLAYS) }
                SummaryTile(
                    icon = Icons.Rounded.MusicNote,
                    value = report.uniqueTracks.toString(),
                    label = stringResource(R.string.listening_stats_unique_tracks),
                    modifier = Modifier.weight(1f)
                ) { onOpen(StatsList.TRACKS) }
                SummaryTile(
                    icon = Icons.Rounded.People,
                    value = report.uniqueArtists.toString(),
                    label = stringResource(R.string.listening_stats_unique_artists),
                    modifier = Modifier.weight(1f)
                ) { onOpen(StatsList.ARTISTS) }
            }
        }
    }
}

/**
 * One count, as a tile that opens the list behind it.
 *
 * Laid out as a column rather than the desktop's row: three of them have to fit across a phone, and
 * a chevron on the right of a 100 dp tile has nowhere to go. The tappable surface is the whole tile.
 */
@Composable
private fun SummaryTile(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
        modifier = modifier
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
