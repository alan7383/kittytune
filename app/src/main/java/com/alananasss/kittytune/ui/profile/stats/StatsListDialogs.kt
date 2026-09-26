package com.alananasss.kittytune.ui.profile.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.ListeningStatsEvent
import com.alananasss.kittytune.data.stats.ReportArtist
import com.alananasss.kittytune.data.stats.ReportTrack
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** How a full list is ordered. */
internal enum class ListSort(val labelKey: Int) {
    TIME(R.string.stats_sort_time),
    PLAYS(R.string.stats_sort_plays),
    NAME(R.string.stats_sort_name)
}

/**
 * A full list, as a sheet: title with its count and a close button, a filter field and the sort
 * chips, then the rows.
 *
 * A bottom sheet rather than the desktop's floating window, because on a phone a centred dialog
 * leaves the list short and the filter field above the keyboard. A sheet takes the whole height and
 * grows from the thumb.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StatsListDialog(
    title: String,
    count: Int,
    onDismiss: () -> Unit,
    sorts: List<ListSort>,
    sort: ListSort?,
    onSort: (ListSort) -> Unit,
    query: String,
    onQuery: (String) -> Unit,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.92f)) {
            Row(
                Modifier.padding(start = 24.dp, end = 12.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(10.dp))
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close))
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                singleLine = true,
                placeholder = { Text(stringResource(R.string.stats_filter_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                shape = CircleShape,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).heightIn(min = 52.dp)
            )
            if (sorts.isNotEmpty() && sort != null) {
                Row(
                    Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sorts.forEach { option ->
                        FilterChip(
                            selected = option == sort,
                            onClick = { onSort(option) },
                            label = { Text(stringResource(option.labelKey)) },
                            leadingIcon = if (option == sort) {
                                { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null
                        )
                    }
                }
            } else {
                Spacer(Modifier.height(10.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 24.dp, bottom = 32.dp),
                content = content
            )
        }
    }
}

private fun String.matches(query: String) = query.isBlank() || contains(query.trim(), ignoreCase = true)

@Composable
internal fun TracksListDialog(
    tracks: List<ReportTrack>,
    onTrackClick: (ReportTrack) -> Unit,
    onDismiss: () -> Unit
) {
    var sort by remember { mutableStateOf(ListSort.TIME) }
    var query by remember { mutableStateOf("") }
    val ranked = remember(tracks) { tracks.mapIndexed { index, track -> index + 1 to track } }
    val shown = remember(ranked, sort, query) {
        ranked.filter { (_, t) -> t.title.matches(query) || t.artistName.matches(query) }
            .let { list ->
                when (sort) {
                    ListSort.TIME -> list
                    ListSort.PLAYS -> list.sortedByDescending { it.second.plays }
                    ListSort.NAME -> list.sortedBy { it.second.title.lowercase() }
                }
            }
    }
    val top = tracks.firstOrNull()?.listenMs?.coerceAtLeast(1L) ?: 1L
    StatsListDialog(
        title = stringResource(R.string.listening_stats_all_tracks),
        count = tracks.size,
        onDismiss = onDismiss,
        sorts = ListSort.entries,
        sort = sort,
        onSort = { sort = it },
        query = query,
        onQuery = { query = it }
    ) {
        items(shown, key = { it.second.trackId }) { (rank, track) ->
            RankedTrackRow(rank, track, share = track.listenMs.toFloat() / top) {
                onDismiss()
                onTrackClick(track)
            }
        }
    }
}

@Composable
internal fun ArtistsListDialog(
    artists: List<ReportArtist>,
    onArtistClick: (ReportArtist) -> Unit,
    onDismiss: () -> Unit
) {
    var sort by remember { mutableStateOf(ListSort.TIME) }
    var query by remember { mutableStateOf("") }
    val ranked = remember(artists) { artists.mapIndexed { index, artist -> index + 1 to artist } }
    val shown = remember(ranked, sort, query) {
        ranked.filter { it.second.name.matches(query) }
            .let { list ->
                when (sort) {
                    ListSort.TIME -> list
                    ListSort.PLAYS -> list.sortedByDescending { it.second.plays }
                    ListSort.NAME -> list.sortedBy { it.second.name.lowercase() }
                }
            }
    }
    val top = artists.firstOrNull()?.listenMs?.coerceAtLeast(1L) ?: 1L
    StatsListDialog(
        title = stringResource(R.string.listening_stats_all_artists),
        count = artists.size,
        onDismiss = onDismiss,
        sorts = ListSort.entries,
        sort = sort,
        onSort = { sort = it },
        query = query,
        onQuery = { query = it }
    ) {
        items(shown, key = { it.second.name.lowercase() }) { (rank, artist) ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        onDismiss()
                        onArtistClick(artist)
                    }
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
                StatsCover(
                    artist.imageUrl,
                    Modifier.size(48.dp).clip(CircleShape),
                    placeholder = Icons.Rounded.Person
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        artist.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (artist.listenMs.toFloat() / top).coerceIn(0.02f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(4.dp),
                        gapSize = 0.dp,
                        drawStopIndicator = {}
                    )
                }
                Spacer(Modifier.width(12.dp))
                PlaysAndTime(artist.plays, artist.listenMs)
            }
        }
    }
}

/** Every listen of the span, newest first, under a heading per day. */
@Composable
internal fun PlaysListDialog(
    events: List<ListeningStatsEvent>,
    onTrackClick: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val zone = remember { ZoneId.systemDefault() }
    val loc = remember { Locale.getDefault() }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dayFormat = remember { DateTimeFormatter.ofPattern("EEEE, d MMMM", loc) }
    val byDay = remember(events, query) {
        events.filter { it.trackTitle.matches(query) || it.artistName.matches(query) }
            .groupBy { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate() }
    }
    val today = LocalDate.now(zone)
    StatsListDialog(
        title = stringResource(R.string.listening_stats_all_plays),
        count = events.size,
        onDismiss = onDismiss,
        sorts = emptyList(),
        sort = null,
        onSort = {},
        query = query,
        onQuery = { query = it }
    ) {
        byDay.forEach { (day, dayEvents) ->
            item(key = "day-$day") {
                val label = when (day) {
                    today -> stringResource(R.string.stats_today)
                    today.minusDays(1) -> stringResource(R.string.sync_yesterday)
                    else -> dayFormat.format(day).replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }
                }
                Row(
                    Modifier.fillMaxWidth().padding(start = 8.dp, top = 14.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        formatDuration(dayEvents.sumOf { it.listenDurationMs }),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(
                dayEvents,
                key = { _, e -> e.id.takeIf { it != 0L } ?: "${e.timestamp}-${e.trackId}" }
            ) { _, event ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(enabled = event.source == "soundcloud") {
                            onDismiss()
                            onTrackClick(event.trackId, event.trackTitle)
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        timeFormat.format(Instant.ofEpochMilli(event.timestamp).atZone(zone)),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(48.dp)
                    )
                    StatsCover(event.artworkUrl, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            event.trackTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            event.artistName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        formatDuration(event.listenDurationMs),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}
