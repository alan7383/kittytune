package com.alananasss.kittytune.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.ClearAll
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.PlayingBars
import com.alananasss.kittytune.utils.formatQueueDuration
import com.alananasss.kittytune.utils.makeTimeString
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import kotlin.math.roundToInt

private sealed interface QueueListItem {
    data class PastHeader(val count: Int, val isExpanded: Boolean) : QueueListItem
    data class TrackItem(val queueIndex: Int, val track: Track) : QueueListItem
}

/** Beyond this many heard tracks, they collapse under an arrow so the queue stays tidy. */
private const val PAST_COLLAPSE_THRESHOLD = 3

/**
 * Full-screen queue view with top app bar, search or bulk-add actions, and queue list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpandedQueueScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val listState = rememberLazyListState()
    val queueState = viewModel.uiQueueState

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(R.string.player_queue),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val contextTitle = viewModel.currentContext?.displayText
                        if (!contextTitle.isNullOrBlank()) {
                            Text(
                                text = contextTitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.btn_close)
                        )
                    }
                },
                actions = {
                    if (queueState.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.prepareBulkAdd(queueState) },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.menu_add_playlist)
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val upNextCount = (queueState.size - viewModel.uiCurrentQueueIndex - 1).coerceAtLeast(0)
            QueueToolbar(viewModel = viewModel, upNextCount = upNextCount)
            QueueListView(
                viewModel = viewModel,
                listState = listState,
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 96.dp)
            )
        }
    }
}

/**
 * Modal bottom sheet queue content, matching Desktop PR #77 design adapted for mobile.
 */
@Composable
fun QueueContent(
    viewModel: PlayerViewModel,
    isQueueOpen: Boolean,
    onCloseQueue: () -> Unit,
    onOpenExpandedQueue: () -> Unit
) {
    val listState = rememberLazyListState()
    val queue = viewModel.uiQueueState
    val upNextCount = (queue.size - viewModel.uiCurrentQueueIndex - 1).coerceAtLeast(0)
    val contextTitle = viewModel.currentContext?.displayText

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.75f)
            .background(MaterialTheme.colorScheme.surfaceContainer)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 6.dp, start = 20.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.player_queue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!contextTitle.isNullOrBlank()) {
                    Text(
                        text = contextTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = {
                    onCloseQueue()
                    onOpenExpandedQueue()
                },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = "Expand Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onCloseQueue,
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.btn_close),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Queue Toolbar
        QueueToolbar(viewModel = viewModel, upNextCount = upNextCount)

        // Queue List
        QueueListView(
            viewModel = viewModel,
            listState = listState,
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 32.dp),
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Queue Toolbar displaying remaining time, shuffle toggle, repeat toggle, and clear upcoming.
 */
@Composable
private fun QueueToolbar(viewModel: PlayerViewModel, upNextCount: Int) {
    val context = LocalContext.current
    val queue = viewModel.uiQueueState
    val current = viewModel.uiCurrentQueueIndex
    val clip = viewModel.uiClipWindow
    val leftMs = remember(queue, current, viewModel.duration, viewModel.currentPosition / 10_000L, clip) {
        val upcoming = queue.drop(current + 1).sumOf { viewModel.clipLengthOf(it) ?: it.actualDurationMs }
        val songEnd = clip?.endMs ?: viewModel.duration
        upcoming + (songEnd - viewModel.currentPosition).coerceAtLeast(0L)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.queue_time_left, formatQueueDuration(context, leftMs)),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Shuffle Toggle Button
        IconButton(
            onClick = { viewModel.toggleShuffle() },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Shuffle,
                contentDescription = stringResource(R.string.queue_shuffle),
                tint = if (viewModel.uiShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(Modifier.width(4.dp))

        // Repeat Toggle Button
        val isRepeatActive = viewModel.uiRepeatMode != RepeatMode.NONE
        IconButton(
            onClick = { viewModel.toggleRepeatMode() },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (viewModel.uiRepeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                contentDescription = stringResource(R.string.queue_repeat),
                tint = if (isRepeatActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }

        // Clear upcoming button
        if (upNextCount > 0) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = { viewModel.clearUpcoming() },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ClearAll,
                    contentDescription = stringResource(R.string.queue_clear_upcoming),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/**
 * Reusable queue list with smooth anchoring, duplicate handling, collapsible past tracks,
 * highlighted current track, and swipe-to-delete on upcoming tracks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueListView(
    viewModel: PlayerViewModel,
    listState: LazyListState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val queue = viewModel.uiQueueState
    val currentIndex = viewModel.uiCurrentQueueIndex
    val keys = remember(queue) { queueItemKeys(queue) }

    val played = viewModel.playedTrackIds
    val pastIndices = remember(queue, currentIndex, played) {
        if (currentIndex <= 0) emptyList()
        else queue.indices.filter { it < currentIndex - 1 && (played.isEmpty() || queue[it].id in played) }
    }
    val pastCount = pastIndices.size
    val firstPast = pastIndices.firstOrNull()

    var pastExpandedByUser by remember { mutableStateOf<Boolean?>(null) }
    val isPastExpanded = pastExpandedByUser ?: (pastCount <= PAST_COLLAPSE_THRESHOLD)

    val listItems = remember(queue, currentIndex, pastIndices, isPastExpanded) {
        buildList {
            for (index in queue.indices) {
                if (index == firstPast && pastCount > 0) {
                    add(QueueListItem.PastHeader(count = pastCount, isExpanded = isPastExpanded))
                }
                val isPast = index in pastIndices
                if (!isPast || isPastExpanded) {
                    add(QueueListItem.TrackItem(queueIndex = index, track = queue[index]))
                }
            }
        }
    }
    val upNextCount = (queue.size - currentIndex - 1).coerceAtLeast(0)

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIndex = keys.indexOf(fromKey)
            val toIndex = keys.indexOf(toKey)
            if (fromIndex >= 0 && toIndex >= 0) {
                viewModel.moveQueueItem(fromIndex, toIndex)
                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
            }
        }
    )

    val currentKey = keys.getOrNull(currentIndex)
    val targetTrackIndex = (currentIndex - 1).coerceAtLeast(0)
    val targetListIndex = listItems.indexOfFirst {
        it is QueueListItem.TrackItem && it.queueIndex == targetTrackIndex
    }.coerceAtLeast(0)

    AnchorCurrentQueueItem(
        listState = listState,
        currentIndex = currentIndex,
        currentTrackId = viewModel.uiCurrentTrack?.id,
        currentKey = currentKey,
        targetIndex = targetListIndex,
    )

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = listItems,
            key = { item ->
                when (item) {
                    is QueueListItem.PastHeader -> "queue_header_past"
                    is QueueListItem.TrackItem -> keys[item.queueIndex]
                }
            }
        ) { item ->
            when (item) {
                is QueueListItem.PastHeader -> {
                    QueueSectionRule(
                        label = stringResource(R.string.queue_played),
                        count = item.count,
                        isCollapsible = true,
                        isExpanded = item.isExpanded,
                        onToggle = { pastExpandedByUser = !item.isExpanded }
                    )
                }
                is QueueListItem.TrackItem -> {
                    val index = item.queueIndex
                    val isCurrent = index == currentIndex
                    val isPast = index in pastIndices

                    ReorderableItem(state = reorderableState, key = keys[index]) { isDragging ->
                        Column {
                            if (currentIndex >= 0 && index == currentIndex + 1) {
                                QueueSectionRule(
                                    label = stringResource(R.string.queue_up_next),
                                    count = upNextCount
                                )
                            }
                            QueueRow(
                                viewModel = viewModel,
                                track = item.track,
                                index = index,
                                isCurrent = isCurrent,
                                isPast = isPast,
                                isDragging = isDragging,
                                scope = this@ReorderableItem,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Heading separating queue sections (Played, Up next).
 */
@Composable
private fun QueueSectionRule(
    label: String,
    count: Int? = null,
    isCollapsible: Boolean = false,
    isExpanded: Boolean = true,
    onToggle: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isCollapsible && onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (count != null && count > 0) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.height(20.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        if (isCollapsible) {
            val arrowRotation by animateFloatAsState(
                targetValue = if (isExpanded) 180f else 0f,
                label = "arrow_rot"
            )
            Icon(
                imageVector = Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(arrowRotation)
            )
        }
    }
}

/**
 * A single row in the queue.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun QueueRow(
    viewModel: PlayerViewModel,
    track: Track,
    index: Int,
    isCurrent: Boolean,
    isPast: Boolean,
    isDragging: Boolean,
    scope: ReorderableCollectionItemScope,
) {
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme

    if (isCurrent) {
        // Current track: featured in a prominent card with PlayingBars
        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation_cur")
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = scheme.secondaryContainer,
            shadowElevation = elevation,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { /* already playing */ },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            viewModel.showTrackOptions(track, fromPlayer = true)
                        }
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Cover with PlayingBars animated overlay
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f))
                    )
                    PlayingBars(
                        isPlaying = viewModel.uiIsPlaying,
                        modifier = Modifier.size(20.dp),
                        color = Color.White
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title ?: stringResource(R.string.generic_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.displayArtist.ifBlank { track.user?.username ?: stringResource(R.string.generic_artist) },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSecondaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (track.user?.verified == true) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                val duration = track.actualDurationMs.takeIf { it > 0 }
                if (duration != null) {
                    Text(
                        text = makeTimeString(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSecondaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                }

                with(scope) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.desc_move),
                        tint = scheme.onSecondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(26.dp)
                            .draggableHandle(
                                onDragStarted = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                                onDragStopped = { view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END) }
                            )
                    )
                }
            }
        }
    } else if (isPast) {
        // Compact already-played track row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(0.55f)
                .clickable { viewModel.skipToQueueItem(index) }
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = track.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(scheme.surfaceVariant)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = track.title ?: stringResource(R.string.generic_title),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            val duration = track.actualDurationMs.takeIf { it > 0 }
            if (duration != null) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = makeTimeString(duration),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    } else {
        // Upcoming track row with swipe to delete
        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation_up")
        val backgroundColor = if (isDragging) scheme.surfaceContainerHigh else Color.Transparent

        SwipeToDeleteItem(onDelete = { viewModel.removeTrackFromQueue(index) }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .shadow(elevation, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .combinedClickable(
                        onClick = { viewModel.skipToQueueItem(index) },
                        onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            viewModel.showTrackOptions(track, fromPlayer = true)
                        }
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = track.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.surfaceVariant)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title ?: stringResource(R.string.generic_title),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.displayArtist.ifBlank { track.user?.username ?: stringResource(R.string.generic_artist) },
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (track.user?.verified == true) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.Verified,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                val duration = track.actualDurationMs.takeIf { it > 0 }
                if (duration != null) {
                    Text(
                        text = makeTimeString(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = scheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                }

                with(scope) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.desc_move),
                        tint = if (isDragging) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(26.dp)
                            .draggableHandle(
                                onDragStarted = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                                onDragStopped = { view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END) }
                            )
                    )
                }
            }
        }
    }
}

/**
 * Swipe to delete container for upcoming queue items.
 */
@Composable
private fun SwipeToDeleteItem(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val dismissThresholdPx = with(density) { 100.dp.toPx() }
    val maxSwipePx = with(density) { 180.dp.toPx() }

    val offsetX = remember { Animatable(0f) }
    val progress = (-offsetX.value / dismissThresholdPx).coerceIn(0f, 1f)

    val bgColor by animateColorAsState(
        targetValue = if (progress > 0f)
            MaterialTheme.colorScheme.errorContainer.copy(alpha = progress.coerceIn(0.1f, 1f))
        else
            Color.Transparent,
        label = "swipe_bg"
    )
    val iconAlpha by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        label = "icon_alpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val velocityTracker = VelocityTracker()
                    val down = awaitFirstDown(requireUnconsumed = false)
                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                    var isHorizontal: Boolean? = null
                    var dragStarted = false

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break

                        val deltaX = change.position.x - change.previousPosition.x
                        val deltaY = change.position.y - change.previousPosition.y

                        if (isHorizontal == null) {
                            val absX = abs(deltaX)
                            val absY = abs(deltaY)
                            if (absX > 3f || absY > 3f) {
                                isHorizontal = absX > absY
                            }
                        }

                        if (isHorizontal == true) {
                            change.consume()
                            dragStarted = true
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            val newOffset = (offsetX.value + deltaX).coerceIn(-maxSwipePx, 0f)
                            coroutineScope.launch { offsetX.snapTo(newOffset) }
                        }
                    }

                    if (dragStarted) {
                        val velocity = velocityTracker.calculateVelocity().x
                        coroutineScope.launch {
                            if (-offsetX.value >= dismissThresholdPx || velocity < -1000f) {
                                offsetX.animateTo(-size.width.toFloat())
                                onDelete()
                                offsetX.snapTo(0f)
                            } else {
                                offsetX.animateTo(0f)
                            }
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(bgColor)
                .padding(end = 20.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = iconAlpha),
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
        ) {
            content()
        }
    }
}
