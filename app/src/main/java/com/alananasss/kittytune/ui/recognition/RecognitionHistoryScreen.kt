package com.alananasss.kittytune.ui.recognition

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.alananasss.kittytune.R
import coil.compose.AsyncImage
import com.alananasss.kittytune.data.RecognitionHistoryRepository
import com.alananasss.kittytune.data.local.RecognitionHistoryItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionHistoryScreen(
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel
) {
    val historyItems by RecognitionHistoryRepository.getHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    var searchQuery by remember { mutableStateOf("") }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val filteredItems = remember(searchQuery, historyItems) {
        if (searchQuery.isBlank()) {
            historyItems
        } else {
            historyItems.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val groupedItems = remember(filteredItems, context) {
        filteredItems.groupBy { item ->
            val calendar = Calendar.getInstance()
            val today = calendar.get(Calendar.DAY_OF_YEAR)
            val year = calendar.get(Calendar.YEAR)

            calendar.timeInMillis = item.timestamp
            val itemDay = calendar.get(Calendar.DAY_OF_YEAR)
            val itemYear = calendar.get(Calendar.YEAR)

            if (year == itemYear) {
                when (today - itemDay) {
                    0 -> context.getString(R.string.date_today)
                    1 -> context.getString(R.string.date_yesterday)
                    else -> SimpleDateFormat("dd MMMM", Locale.getDefault()).format(item.timestamp)
                }
            } else {
                SimpleDateFormat("dd MMMM yyyy", Locale.getDefault()).format(item.timestamp)
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text(stringResource(R.string.dialog_clear_history_title)) },
            text = { Text(stringResource(R.string.dialog_clear_history_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        RecognitionHistoryRepository.clearHistory()
                        showClearHistoryDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_clear), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognition_history_title)) },
                navigationIcon = {
                    FilledTonalIconButton(
                        onClick = onBackClick,
                        shapes = IconButtonDefaults.shapes(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_back))
                    }
                },
                actions = {
                    if (historyItems.isNotEmpty()) {
                        FilledTonalIconButton(
                            onClick = { showClearHistoryDialog = true },
                            shapes = IconButtonDefaults.shapes(),
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Icon(Icons.Rounded.Delete, contentDescription = stringResource(R.string.desc_clear_history))
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text(stringResource(R.string.search_history_hint)) },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                singleLine = true
            )

            if (filteredItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (searchQuery.isBlank()) stringResource(R.string.no_history) else stringResource(R.string.no_results),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 120.dp)
                ) {
                    groupedItems.forEach { (dateHeader, items) ->
                        item {
                            Text(
                                text = dateHeader,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
                            )
                        }

                        items(items) { item ->
                            HistoryItemRow(
                                item = item,
                                currentlyPlayingTrack = playerViewModel.currentTrack,
                                onClick = {
                                    if (item.trackId != null) {
                                        val trackList = filteredItems.mapNotNull { historyItem ->
                                            historyItem.trackId?.let { id ->
                                                Track(
                                                    id = id,
                                                    title = historyItem.title,
                                                    artworkUrl = historyItem.artworkUrl,
                                                    durationMs = 0L,
                                                    user = User(id = 0, username = historyItem.artist, avatarUrl = null)
                                                )
                                            }
                                        }
                                        val startIndex = trackList.indexOfFirst { it.id == item.trackId }
                                        if (startIndex != -1) {
                                            playerViewModel.playPlaylist(trackList, startIndex)
                                        }
                                    }
                                },
                                onOptionsClick = {
                                    if (item.trackId != null) {
                                        val track = Track(
                                            id = item.trackId,
                                            title = item.title,
                                            artworkUrl = item.artworkUrl,
                                            durationMs = 0,
                                            user = User(id = 0, username = item.artist, avatarUrl = null)
                                        )
                                        playerViewModel.showTrackOptions(track)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    item: RecognitionHistoryItem,
    currentlyPlayingTrack: Track?,
    onClick: () -> Unit,
    onOptionsClick: () -> Unit
) {
    val timeFormatted = remember(item.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(item.timestamp)
    }
    val isCurrent = currentlyPlayingTrack?.id == item.trackId && item.trackId != null
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (item.artworkUrl != null) {
                AsyncImage(
                    model = item.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GraphicEq,
                        contentDescription = "Playing",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${item.artist} • $timeFormatted",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        IconButton(onClick = onOptionsClick) {
            Icon(
                Icons.Rounded.MoreVert,
                contentDescription = "Options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
