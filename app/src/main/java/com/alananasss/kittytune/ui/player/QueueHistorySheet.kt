package com.alananasss.kittytune.ui.player

import android.text.format.DateUtils
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.haptics.performAppHaptic
import com.alananasss.kittytune.data.QueueHistoryRepository
import com.alananasss.kittytune.data.local.QueueHistorySummary
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.common.SwipeToDeleteItem
import androidx.compose.material3.IconButton
import com.alananasss.kittytune.utils.makeTimeString
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueHistorySheet(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val view = LocalView.current
    val summaries by QueueHistoryRepository.getSummaries().collectAsStateWithLifecycle(initialValue = emptyList())
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var expandedQueueId by remember { mutableStateOf<String?>(null) }
    var snapshotSavedRecently by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text(stringResource(R.string.queue_history_clear_title)) },
            text = { Text(stringResource(R.string.queue_history_clear_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        view.performAppHaptic(HapticFeedbackConstants.CONFIRM)
                        QueueHistoryRepository.clearAll()
                        showClearConfirmDialog = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.queue_history_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    KittyModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.queue_history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (summaries.size == 1) {
                            stringResource(R.string.queue_history_count_one)
                        } else {
                            stringResource(R.string.queue_history_count, summaries.size)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (summaries.isNotEmpty()) {
                    IconButton(
                        onClick = { showClearConfirmDialog = true },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteSweep,
                            contentDescription = stringResource(R.string.queue_history_clear_title),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.btn_close),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Snapshot Current Queue Banner (if active queue has items)
            if (viewModel.uiQueueState.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (snapshotSavedRecently) Icons.Rounded.Check else Icons.Rounded.BookmarkAdd,
                            contentDescription = null,
                            tint = if (snapshotSavedRecently) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.queue_history_snapshot),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (viewModel.uiQueueState.size == 1) {
                                    "1 track currently in queue"
                                } else {
                                    "${viewModel.uiQueueState.size} tracks currently in queue"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                view.performAppHaptic(HapticFeedbackConstants.CONFIRM)
                                viewModel.saveCurrentQueueToHistory()
                                snapshotSavedRecently = true
                            },
                            shapes = ButtonDefaults.shapes(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (snapshotSavedRecently) "Saved" else "Snapshot",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            // Queue History Items List
            if (summaries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.queue_history_empty),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.queue_history_empty_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(
                        items = summaries,
                        key = { it.id }
                    ) { summary ->
                        SwipeToDeleteItem(
                            key = summary.id,
                            onDelete = {
                                view.performAppHaptic(HapticFeedbackConstants.CONFIRM)
                                QueueHistoryRepository.deleteQueue(summary.id)
                            },
                            shape = RoundedCornerShape(16.dp),
                            cardColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            QueueHistoryCard(
                                summary = summary,
                                isExpanded = expandedQueueId == summary.id,
                                onToggleExpand = {
                                    expandedQueueId = if (expandedQueueId == summary.id) null else summary.id
                                },
                                onRestore = {
                                    view.performAppHaptic(HapticFeedbackConstants.CONFIRM)
                                    viewModel.restoreQueueFromHistory(summary.id)
                                    onDismiss()
                                },
                                onDelete = {
                                    view.performAppHaptic(HapticFeedbackConstants.CONFIRM)
                                    QueueHistoryRepository.deleteQueue(summary.id)
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
private fun QueueHistoryCard(
    summary: QueueHistorySummary,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val relativeTime = remember(summary.savedAt) {
        val now = System.currentTimeMillis()
        DateUtils.getRelativeTimeSpanString(summary.savedAt, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }

    var tracksInQueue by remember(summary.id, isExpanded) { mutableStateOf<List<Track>?>(null) }
    val scope = rememberCoroutineScope()

    if (isExpanded && tracksInQueue == null) {
        androidx.compose.runtime.LaunchedEffect(summary.id) {
            val entity = QueueHistoryRepository.getQueueById(summary.id)
            if (entity != null) {
                tracksInQueue = QueueHistoryRepository.deserializeTracks(entity.tracksJson)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onToggleExpand),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover art thumbnail
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (!summary.firstTrackArtworkUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = summary.firstTrackArtworkUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(Modifier.width(12.dp))

                // Title and details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.height(2.dp))

                    val subtitle = buildString {
                        append("${summary.trackCount} tracks")
                        if (!summary.firstTrackTitle.isNullOrBlank()) {
                            append(" • ")
                            append(summary.firstTrackTitle)
                        }
                    }

                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = relativeTime,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Actions: Restore button & Expand toggle & Delete
                FilledTonalIconButton(
                    onClick = onRestore,
                    modifier = Modifier.size(40.dp),
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.queue_history_restore),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onToggleExpand,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.queue_history_delete),
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expanded Preview of Tracks
            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                ) {
                    val tracks = tracksInQueue
                    if (tracks == null) {
                        Text(
                            text = "Loading tracks...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        val previewTracks = tracks.take(8)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            previewTracks.forEachIndexed { idx, track ->
                                val isCurrentTrack = idx == summary.currentIndex
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isCurrentTrack) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                                            else MaterialTheme.colorScheme.surfaceContainer
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${idx + 1}.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.width(24.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = track.title ?: "",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isCurrentTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.displayArtist,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (track.actualDurationMs > 0L) {
                                        Text(
                                            text = makeTimeString(track.actualDurationMs / 1000L),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            if (tracks.size > 8) {
                                Text(
                                    text = "+ ${tracks.size - 8} more tracks",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onRestore,
                                modifier = Modifier.weight(1f),
                                shapes = ButtonDefaults.shapes(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.queue_history_restore))
                            }

                            OutlinedButton(
                                onClick = onDelete,
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Delete,
                                    contentDescription = stringResource(R.string.queue_history_delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
