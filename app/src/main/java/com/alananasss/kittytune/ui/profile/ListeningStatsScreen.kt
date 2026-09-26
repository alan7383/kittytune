package com.alananasss.kittytune.ui.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.TopArtistResult
import com.alananasss.kittytune.data.local.TopTrackResult
import java.util.Locale
import java.util.Calendar
import androidx.compose.ui.platform.LocalContext
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.stats.ListeningReport
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ListeningStatsScreen(
    onBackClick: () -> Unit,
    onTrackClick: (TopTrackResult) -> Unit,
    onArtistClick: (TopArtistResult) -> Unit
) {
    val viewModel: ListeningStatsViewModel = viewModel()
    val stats = viewModel.stats
    val report = viewModel.report
    val selectedPeriod = viewModel.selectedPeriod
    val isLoading = viewModel.isLoading
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var isTrackingEnabled by remember { mutableStateOf(prefs.getListeningStatsEnabled()) }


    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.pref_privacy_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.pref_privacy_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                            Text(
                                text = stringResource(R.string.pref_privacy_tracking_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(R.string.pref_privacy_tracking_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isTrackingEnabled,
                            onCheckedChange = { 
                                isTrackingEnabled = it
                                prefs.setListeningStatsEnabled(it)
                            },
                            thumbContent = {
                                if (isTrackingEnabled) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(SwitchDefaults.IconSize),
                                        tint = MaterialTheme.colorScheme.surfaceContainerHighest
                                    )
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary,
                                uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSettingsDialog = false }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }

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
                        Text(
                            text = stringResource(R.string.listening_stats_subtitle),
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
                        Icon(Icons.Rounded.ArrowBack, stringResource(R.string.btn_close))
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.pref_privacy_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Period Selector
            item {
                PeriodSelector(
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = { viewModel.selectPeriod(it) }
                )
            }

            // "All time" used to be a different screen rather than a longer one: it showed the month-by-month
            // timeline and *nothing else* — no total, no play count, no top lists, no habits. So the period
            // with the most to say showed the least, and a week of listening looked bigger than the whole
            // history. It now shows everything the other periods show, with the timeline added underneath
            // rather than in place of it (issue #33).
            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ContainedLoadingIndicator()
                    }
                }
            } else if (stats.totalEvents == 0) {
                // Empty state
                item {
                    EmptyStatsCard()
                }
            } else {

                // Hero Stats Card
                item {
                    HeroStatsCard(
                        totalListenTimeMs = stats.totalListenTimeMs,
                        totalPlays = stats.totalPlays,
                        uniqueTracks = stats.uniqueTracks,
                        uniqueArtists = stats.uniqueArtists,
                        change = report?.change
                    )
                }

                // Top Tracks
                if (stats.topTracks.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionTitle(stringResource(R.string.listening_stats_top_tracks))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(stats.topTracks) { track ->
                                    TopTrackCard(track, onClick = { if ((track.source ?: "soundcloud") == "soundcloud") onTrackClick(track) })
                                }
                            }
                        }
                    }
                }

                // Top Artists
                if (stats.topArtists.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionTitle(stringResource(R.string.listening_stats_top_artists))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(stats.topArtists) { artist ->
                                    TopArtistCard(artist, onClick = { onArtistClick(artist) })
                                }
                            }
                        }
                    }
                }

                // What the report adds over the aggregate query: when in the day the listening
                // happens, and how consistently. None of it is derivable from a single number.
                report?.let { r ->
                    if (r.partsOfDay.any { it > 0L }) {
                        item {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                SectionTitle(stringResource(R.string.listening_stats_when))
                                PartsOfDayChart(r)
                            }
                        }
                    }

                    if (r.longestStreakDays > 1 || r.busiestWeekday != null || r.peakHour != null) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (r.longestStreakDays > 0) {
                                    HabitCard(
                                        icon = Icons.Rounded.LocalFireDepartment,
                                        title = stringResource(R.string.listening_stats_streak_title),
                                        value = stringResource(
                                            if (r.longestStreakDays == 1) R.string.listening_stats_streak_one
                                            else R.string.listening_stats_streak_days,
                                            r.longestStreakDays
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                r.busiestWeekday?.let { day ->
                                    HabitCard(
                                        icon = Icons.Rounded.CalendarToday,
                                        title = stringResource(R.string.listening_stats_busiest_day),
                                        value = day.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault()),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }

                // Listening Habits Section
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SectionTitle(stringResource(R.string.listening_stats_habits))
                        HabitsGrid(stats)
                    }
                }

                // Fun Facts Section
                if (stats.totalPlays > 0) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            SectionTitle(stringResource(R.string.listening_stats_insights))
                            InsightsSection(stats)
                        }
                    }
                }

                // The timeline, only where it means something: a month-by-month breakdown of "this week" is
                // one row. Appended, so the totals above are what the tab opens on.
                if (selectedPeriod == StatsPeriod.ALL_TIME) {
                    item {
                        SectionTitle(stringResource(R.string.listening_stats_timeline))
                    }

                    items(viewModel.timelineChunks) { chunk ->
                        TimelineChunkCard(
                            chunk = chunk,
                            onTrackClick = onTrackClick,
                            onArtistClick = onArtistClick
                        )
                    }

                    if (viewModel.isTimelineLoading) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    } else if (viewModel.timelineHasMore) {
                        item {
                            LaunchedEffect(Unit) {
                                viewModel.loadNextTimelineChunk()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodSelector(
    selectedPeriod: StatsPeriod,
    onPeriodSelected: (StatsPeriod) -> Unit
) {
    ExpressiveConnectedButtonGroup(
        options = StatsPeriod.entries,
        selectedOption = selectedPeriod,
        onOptionSelected = onPeriodSelected,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        labelProvider = { period ->
            val label = when (period) {
                StatsPeriod.WEEK -> stringResource(R.string.listening_stats_period_week)
                StatsPeriod.MONTH -> stringResource(R.string.listening_stats_period_month)
                StatsPeriod.YEAR -> stringResource(R.string.listening_stats_period_year)
                StatsPeriod.ALL_TIME -> stringResource(R.string.listening_stats_period_all)
            }
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
        }
    )
}

@Composable
private fun EmptyStatsCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Rounded.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            )
            Text(
                stringResource(R.string.listening_stats_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(R.string.listening_stats_empty_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun HeroStatsCard(
    totalListenTimeMs: Long,
    totalPlays: Int,
    uniqueTracks: Int,
    uniqueArtists: Int,
    change: Float? = null
) {
    val targetSeconds = (totalListenTimeMs / 1000f)
    val animatedSeconds by animateFloatAsState(
        targetValue = targetSeconds,
        animationSpec = tween(1500),
        label = "seconds"
    )

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ),
        shape = RoundedCornerShape(32.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column {
                Text(
                    text = stringResource(R.string.listening_stats_time_listened).uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    letterSpacing = 1.sp
                )
                Text(
                    text = formatDurationMs((animatedSeconds * 1000).toLong()),
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-2).sp
                    ),
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                // Against the same length of time just before this one, so a week is compared with
                // the week before it rather than with whatever seven days happen to overlap it.
                // Null for "all time", which has no equal span behind it.
                if (change != null) {
                    Text(
                        text = comparisonLabel(change),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MiniStatChip(
                    icon = Icons.Rounded.MusicNote,
                    value = totalPlays.toString(),
                    label = stringResource(R.string.listening_stats_plays),
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    icon = Icons.Rounded.BarChart,
                    value = uniqueTracks.toString(),
                    label = stringResource(R.string.listening_stats_unique_tracks),
                    modifier = Modifier.weight(1f)
                )
                MiniStatChip(
                    icon = Icons.Rounded.People,
                    value = uniqueArtists.toString(),
                    label = stringResource(R.string.listening_stats_unique_artists),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MiniStatChip(
    icon: ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon, null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopTrackCard(track: TopTrackResult, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .width(170.dp)
            .height(230.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AsyncImage(
                model = track.artworkUrl,
                contentDescription = track.trackTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Column(modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    text = track.trackTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = track.artistName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.listening_stats_play_count, track.playCount),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Text(
                    text = formatDurationMs(track.totalListenMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopArtistCard(artist: TopArtistResult, onClick: () -> Unit = {}) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .width(140.dp)
            .height(180.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AsyncImage(
                model = artist.artworkUrl,
                contentDescription = artist.artistName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = artist.artistName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = formatDurationMs(artist.totalListenMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun HabitsGrid(stats: PeriodStats) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HabitCard(
                icon = Icons.Rounded.Replay,
                title = stringResource(R.string.listening_stats_manual_replays),
                value = stats.manualReplays.toString(),
                subtitle = stringResource(R.string.listening_stats_manual_replays_desc),
                modifier = Modifier.weight(1f)
            )
            HabitCard(
                icon = Icons.Rounded.SkipNext,
                title = stringResource(R.string.listening_stats_skip_rate),
                value = String.format(Locale.US, "%.0f%%", stats.skipRate * 100),
                subtitle = stringResource(R.string.listening_stats_skip_rate_desc, stats.totalSkips),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HabitCard(
                icon = Icons.Rounded.CheckCircle,
                title = stringResource(R.string.listening_stats_completion_rate),
                value = String.format(Locale.US, "%.0f%%", stats.completionRate * 100),
                subtitle = stringResource(R.string.listening_stats_completion_rate_desc),
                modifier = Modifier.weight(1f)
            )
            HabitCard(
                icon = Icons.Rounded.RepeatOne,
                title = stringResource(R.string.listening_stats_repeat_loops),
                value = stats.repeatOneLoops.toString(),
                subtitle = stringResource(R.string.listening_stats_repeat_loops_desc),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HabitCard(
                icon = Icons.Rounded.Timer,
                title = stringResource(R.string.listening_stats_avg_listen),
                value = if (stats.totalEvents > 0) formatDurationMs(stats.totalListenTimeMs / stats.totalEvents) else stringResource(R.string.listening_stats_duration_zero),
                subtitle = stringResource(R.string.listening_stats_avg_listen_desc),
                modifier = Modifier.weight(1f)
            )
            HabitCard(
                icon = Icons.Rounded.TrendingUp,
                title = stringResource(R.string.listening_stats_total_sessions),
                value = stats.totalEvents.toString(),
                subtitle = stringResource(R.string.listening_stats_total_sessions_desc),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HabitCard(
    icon: ImageVector,
    title: String,
    value: String,
    subtitle: String = "",
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = modifier.height(150.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon, null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Column {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 14.sp
                )
            }
        }
    }
}

@Composable
private fun InsightsSection(stats: PeriodStats) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stats.topTracks.isNotEmpty()) {
            val topTrack = stats.topTracks.first()
            InsightCard(
                emoji = "🔥",
                text = stringResource(
                    R.string.listening_stats_insight_top_track,
                    topTrack.trackTitle,
                    topTrack.playCount
                )
            )
        }

        if (stats.manualReplays > 0) {
            InsightCard(
                emoji = "🔁",
                text = stringResource(
                    R.string.listening_stats_insight_replays,
                    stats.manualReplays
                )
            )
        }

        if (stats.totalSkips > 5) {
            InsightCard(
                emoji = "⏭️",
                text = stringResource(
                    R.string.listening_stats_insight_skips,
                    String.format(Locale.US, "%.0f", stats.skipRate * 100)
                )
            )
        }

        if (stats.repeatOneLoops > 0) {
            InsightCard(
                emoji = "🔂",
                text = stringResource(
                    R.string.listening_stats_insight_repeat,
                    stats.repeatOneLoops
                )
            )
        }

        if (stats.uniqueArtists > 5) {
            InsightCard(
                emoji = "🎨",
                text = stringResource(
                    R.string.listening_stats_insight_variety,
                    stats.uniqueArtists
                )
            )
        }
    }
}

@Composable
private fun InsightCard(emoji: String, text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(emoji, fontSize = 28.sp)
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp, top = 8.dp)
    )
}

@Composable
private fun formatDurationMs(ms: Long): String {
    if (ms == 0L) return stringResource(R.string.listening_stats_duration_zero)
    val totalSeconds = ms / 1000
    val days = totalSeconds / 86400
    val hours = (totalSeconds % 86400) / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        days > 0 -> stringResource(R.string.listening_stats_duration_days_hrs, days, hours)
        hours > 0 -> stringResource(R.string.listening_stats_duration_hr_min, hours, minutes)
        minutes > 0 -> stringResource(R.string.listening_stats_duration_min_sec, minutes, seconds)
        else -> stringResource(R.string.listening_stats_duration_sec, seconds)
    }
}

@Composable
private fun TimelineChunkCard(
    chunk: TimelineChunk,
    onTrackClick: (TopTrackResult) -> Unit,
    onArtistClick: (TopArtistResult) -> Unit
) {
    val calendarStart = Calendar.getInstance().apply { timeInMillis = chunk.startDateMs }

    val monthStr = calendarStart.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())
    val yearStr = calendarStart.get(Calendar.YEAR).toString()
    val dateLabel = "$monthStr $yearStr"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = dateLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                chunk.topTrack?.let { track ->
                    TimelineItemRow(
                        label = stringResource(R.string.listening_stats_top_track_label),
                        imageUrl = track.artworkUrl,
                        title = track.trackTitle,
                        subtitle = track.artistName,
                        badgeText = stringResource(R.string.listening_stats_play_count, track.playCount),
                        onClick = { if ((track.source ?: "soundcloud") == "soundcloud") onTrackClick(track) },
                        isCircularImage = false
                    )
                }
                chunk.topArtist?.let { artist ->
                    TimelineItemRow(
                        label = stringResource(R.string.listening_stats_top_artist_label),
                        imageUrl = artist.artworkUrl,
                        title = artist.artistName,
                        subtitle = stringResource(R.string.listening_stats_play_count, artist.playCount),
                        badgeText = formatDurationMs(artist.totalListenMs),
                        onClick = { onArtistClick(artist) },
                        isCircularImage = true
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimelineItemRow(
    label: String,
    imageUrl: String?,
    title: String,
    subtitle: String,
    badgeText: String,
    onClick: () -> Unit,
    isCircularImage: Boolean
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = Color.Transparent
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(if (isCircularImage) CircleShape else RoundedCornerShape(12.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = badgeText,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Listening split across the four parts of the day, as four proportional bars.
 *
 * Drawn from [ListeningReport.partsOfDay] rather than a per-hour chart on purpose: which half of
 * the day someone listens in is the readable part, and the hourly detail is a 24-column chart that
 * answers nothing they asked for.
 */
@Composable
private fun PartsOfDayChart(report: ListeningReport) {
    val parts = report.partsOfDay
    val labels = listOf(
        stringResource(R.string.listening_stats_night),
        stringResource(R.string.listening_stats_morning),
        stringResource(R.string.listening_stats_afternoon),
        stringResource(R.string.listening_stats_evening)
    )
    val total = parts.sum().coerceAtLeast(1L)
    val peak = parts.maxOrNull()?.coerceAtLeast(1L) ?: 1L
    // Read outside the Canvas: the draw lambda is not a composable scope.
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val barColor = MaterialTheme.colorScheme.primary

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            parts.forEachIndexed { index, ms ->
                val fraction = ms.toFloat() / total.toFloat()
                val heightFraction = ms.toFloat() / peak.toFloat()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = labels[index],
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = String.format(Locale.US, "%.0f%%", fraction * 100),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    ) {
                        val radius = CornerRadius(size.height / 2f, size.height / 2f)
                        drawRoundRect(
                            color = trackColor,
                            size = size,
                            cornerRadius = radius
                        )
                        if (ms > 0L) {
                            drawRoundRect(
                                color = barColor,
                                size = Size((size.width * heightFraction).coerceAtLeast(size.height), size.height),
                                cornerRadius = radius
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "vs 25% more" / "vs 10% less", against the span before this one. */
@Composable
private fun comparisonLabel(change: Float): String {
    val percent = String.format(Locale.US, "%.0f%%", kotlin.math.abs(change) * 100)
    return if (change >= 0f) {
        stringResource(R.string.listening_stats_vs_previous, stringResource(R.string.listening_stats_more, percent))
    } else {
        stringResource(R.string.listening_stats_vs_previous, stringResource(R.string.listening_stats_less, percent))
    }
}
