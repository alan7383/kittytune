package com.alananasss.kittytune.ui.profile.recap

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.RecapKind
import com.alananasss.kittytune.data.stats.RecapPeriod
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.profile.stats.StatsCover
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The hub's filter. */
private enum class RecapFilter { ALL, MONTHS, SEASONS, YEARS }

/**
 * The recaps as a carousel, for the top of the statistics screen: the newest first, with a link to all
 * of them. Before the first recap exists, a single card says when it will.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapCarouselSection(
    onOpenRecap: (String) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: RecapsViewModel = viewModel()
    val summaries = viewModel.summaries
    val next = viewModel.upcoming.firstOrNull()

    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.recaps_section),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.recaps_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = hapticAction(HapticKind.UI) { onSeeAll() },
                shapes = ButtonDefaults.shapes(),
                contentPadding = ButtonDefaults.SmallContentPadding,
            ) {
                Text(stringResource(R.string.recaps_see_all))
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
        Spacer(Modifier.height(12.dp))

        if (summaries.isEmpty()) {
            if (viewModel.isLoading) {
                Box(Modifier.fillMaxWidth().height(RECAP_CAROUSEL_HEIGHT), contentAlignment = Alignment.Center) {
                    LoadingIndicator()
                }
            } else if (next != null) {
                UpcomingCard(next, Modifier.fillMaxWidth())
            }
            return@Column
        }

        val state = rememberCarouselState { summaries.size }
        HorizontalMultiBrowseCarousel(
            state = state,
            preferredItemWidth = 220.dp,
            itemSpacing = 8.dp,
            minSmallItemWidth = 40.dp,
            maxSmallItemWidth = 56.dp,
            modifier = Modifier.fillMaxWidth().height(RECAP_CAROUSEL_HEIGHT),
        ) { index ->
            val summary = summaries[index]
            CarouselRecapCard(summary) {
                viewModel.markSeen(summary.period.id)
                onOpenRecap(summary.period.id)
            }
        }
    }
}

private val RECAP_CAROUSEL_HEIGHT = 248.dp

@Composable
private fun CarouselItemScope.CarouselRecapCard(summary: RecapSummary, onClick: () -> Unit) {
    val palette = summary.period.palette(MaterialTheme.colorScheme)
    Surface(
        onClick = hapticAction(HapticKind.UI) { onClick() },
        color = palette.container,
        contentColor = palette.onContainer,
        modifier = Modifier.fillMaxSize().maskClip(RoundedCornerShape(28.dp)),
    ) {
        RecapCardContent(summary, palette, compact = false)
    }
}

/**
 * What a recap card shows: its shape turning in the corner, the period, the top artist in a cookie,
 * and the time. Shared by the carousel and the hub's grid.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecapCardContent(summary: RecapSummary, palette: RecapPalette, compact: Boolean) {
    val period = summary.period
    Box(Modifier.fillMaxSize()) {
        SpinningShape(
            polygon = period.polygon(),
            color = palette.accent.copy(alpha = 0.22f),
            modifier = Modifier
                .size(if (compact) 120.dp else 170.dp)
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-36).dp),
        )
        Column(Modifier.fillMaxSize().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(36.dp).clip(period.polygon().toShape()).background(palette.onContainer.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(period.icon(), contentDescription = null, modifier = Modifier.size(20.dp))
                }
                if (summary.isNew) {
                    Spacer(Modifier.width(8.dp))
                    NewBadge(palette)
                }
            }
            Spacer(Modifier.weight(1f))
            Text(
                period.kindLabel(),
                style = MaterialTheme.typography.labelLarge,
                color = palette.onContainer.copy(alpha = 0.8f),
                maxLines = 1,
            )
            Text(
                period.title(),
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatsCover(
                    summary.topArtistImage,
                    Modifier.size(if (compact) 28.dp else 34.dp).clip(MaterialShapes.Cookie9Sided.toShape()),
                    placeholder = Icons.Rounded.Person,
                )
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    summary.topArtistName?.let {
                        Text(it, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        stringResource(R.string.recap_minutes_short, formatMinutes(summary.totalListenMs)),
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.onContainer.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun NewBadge(palette: RecapPalette) {
    Surface(shape = CircleShape, color = palette.onContainer, contentColor = palette.container) {
        Text(
            stringResource(R.string.recaps_new),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/**
 * A recap still in progress: what it is, how far through it we are as a wavy bar, and when it will
 * be ready.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun UpcomingCard(period: RecapPeriod, modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val now = System.currentTimeMillis()
    val progress = ((now - period.window.startMs).toFloat() / (period.window.endMs - period.window.startMs)).coerceIn(0f, 1f)
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    val readyOn = Instant.ofEpochMilli(period.window.endMs).atZone(zone).toLocalDate()
    val days = ChronoUnit.DAYS.between(today, readyOn).coerceAtLeast(0)

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(period.polygon().toShape()).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(period.icon(), contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(period.title(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    if (days <= 1) stringResource(R.string.recaps_ready_tomorrow) else stringResource(R.string.recaps_ready_in_days, days.toInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                LinearWavyProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

/**
 * Every recap: the ones on the way, then the finished ones by year, filterable by kind. A year's own
 * recap spans the grid as the headline of its section.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecapsScreen(onBackClick: () -> Unit, onOpenRecap: (String) -> Unit) {
    val viewModel: RecapsViewModel = viewModel()
    var filter by rememberSaveable { mutableStateOf(RecapFilter.ALL) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.recaps_title), fontWeight = FontWeight.Bold) },
                subtitle = { Text(stringResource(R.string.recaps_subtitle)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.btn_close))
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        val shown = viewModel.summaries.filter {
            when (filter) {
                RecapFilter.ALL -> true
                RecapFilter.MONTHS -> it.period.kind == RecapKind.MONTH
                RecapFilter.SEASONS -> it.period.kind == RecapKind.SEASON
                RecapFilter.YEARS -> it.period.kind == RecapKind.YEAR
            }
        }
        // Sections by the year a recap ends in, so a winter straddling New Year sits with the January it ends on.
        val byYear = shown.groupBy { it.period.end.year }.toSortedMap(compareByDescending { it })

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 164.dp),
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 180.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "filter") {
                ExpressiveConnectedButtonGroup(
                    options = RecapFilter.entries,
                    selectedOption = filter,
                    onOptionSelected = { filter = it },
                    labelProvider = { option ->
                        Text(
                            stringResource(
                                when (option) {
                                    RecapFilter.ALL -> R.string.recaps_filter_all
                                    RecapFilter.MONTHS -> R.string.recaps_filter_months
                                    RecapFilter.SEASONS -> R.string.recaps_filter_seasons
                                    RecapFilter.YEARS -> R.string.recaps_filter_years
                                }
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    iconProvider = { option ->
                        Icon(
                            when (option) {
                                RecapFilter.ALL -> Icons.Rounded.AutoAwesome
                                RecapFilter.MONTHS -> Icons.Rounded.CalendarMonth
                                RecapFilter.SEASONS -> Icons.Rounded.WbSunny
                                RecapFilter.YEARS -> Icons.Rounded.Celebration
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            if (viewModel.upcoming.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "upcoming") {
                    Column {
                        SectionTitle(stringResource(R.string.recaps_upcoming_title))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(viewModel.upcoming, key = { it.id }) { period ->
                                UpcomingCard(period, Modifier.width(300.dp))
                            }
                        }
                    }
                }
            }

            if (shown.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    AnimatedContent(
                        targetState = viewModel.isLoading,
                        transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(120)) },
                        label = "recapsEmpty",
                    ) { loading ->
                        if (loading) {
                            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                        } else {
                            EmptyRecaps()
                        }
                    }
                }
            }

            byYear.forEach { (year, recaps) ->
                item(span = { GridItemSpan(maxLineSpan) }, key = "year-$year") {
                    SectionTitle(year.toString())
                }
                recaps.forEach { summary ->
                    val isYear = summary.period.kind == RecapKind.YEAR
                    item(
                        key = summary.period.id,
                        span = { if (isYear) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
                    ) {
                        GridRecapCard(summary, hero = isYear) {
                            viewModel.markSeen(summary.period.id)
                            onOpenRecap(summary.period.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GridRecapCard(summary: RecapSummary, hero: Boolean, onClick: () -> Unit) {
    val palette = summary.period.palette(MaterialTheme.colorScheme)
    Surface(
        onClick = hapticAction(HapticKind.UI) { onClick() },
        shape = RoundedCornerShape(if (hero) 32.dp else 24.dp),
        color = palette.container,
        contentColor = palette.onContainer,
        modifier = Modifier.fillMaxWidth().then(if (hero) Modifier.height(200.dp) else Modifier.aspectRatio(0.82f)),
    ) {
        if (hero) {
            Box(Modifier.fillMaxSize()) {
                RecapCardContent(summary, palette, compact = false)
                HeroPlayButton(palette)
            }
        } else {
            RecapCardContent(summary, palette, compact = true)
        }
    }
}

@Composable
private fun BoxScope.HeroPlayButton(palette: RecapPalette) {
    // Decorative: the whole card is the button; this only says what it does.
    Box(
        Modifier
            .align(Alignment.BottomEnd)
            .padding(18.dp)
            .size(56.dp)
            .clip(CircleShape)
            .background(palette.onContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = palette.container, modifier = Modifier.size(30.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun EmptyRecaps() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            SpinningShape(MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.secondaryContainer, Modifier.size(112.dp))
            Icon(
                Icons.Rounded.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(stringResource(R.string.recaps_empty_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.recaps_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Whole minutes, grouped for the locale: "12 345". */
internal fun formatMinutes(ms: Long): String = "%,d".format(ms / 60_000)
