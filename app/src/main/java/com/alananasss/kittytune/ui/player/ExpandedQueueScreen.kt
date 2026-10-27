package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.audio.haptics.performAppHaptic
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForwardIos
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
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.TrackRemovalMethod
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.PlayingBars
import com.alananasss.kittytune.ui.common.SwipeToDeleteItem
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.utils.formatQueueDuration
import com.alananasss.kittytune.utils.makeTimeString
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableCollectionItemScope
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import kotlin.math.roundToInt
import com.alananasss.kittytune.ui.design.IconButton
import com.alananasss.kittytune.ui.design.Surface
import com.alananasss.kittytune.ui.design.CenterAlignedTopAppBar

private sealed interface QueueListItem {
    data class PastHeader(val count: Int, val isExpanded: Boolean) : QueueListItem
    data class UpNextHeader(val count: Int) : QueueListItem
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
    onClose: () -> Unit,
    bottomContentPadding: androidx.compose.ui.unit.Dp = 96.dp
) {
    val listState = rememberLazyListState()
    val queueState = viewModel.uiQueueState

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    val contextTitle = viewModel.currentContext?.displayText
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = if (!contextTitle.isNullOrBlank()) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .hapticClickable {
                                    onClose()
                                    viewModel.navigateToContext()
                                }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        } else Modifier
                    ) {
                        Text(
                            text = stringResource(R.string.player_queue),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (!contextTitle.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = contextTitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(9.dp)
                                )
                            }
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
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = bottomContentPadding)
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
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (!contextTitle.isNullOrBlank()) {
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .hapticClickable {
                                    onCloseQueue()
                                    viewModel.navigateToContext()
                                }
                        } else Modifier
                    )
            ) {
                Text(
                    text = stringResource(R.string.player_queue),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!contextTitle.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = contextTitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForwardIos,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                            modifier = Modifier.size(10.dp)
                        )
                    }
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
        else queue.indices.filter { it < currentIndex - 1 && queue[it].id in played }
    }
    val pastCount = pastIndices.size
    val firstPast = pastIndices.firstOrNull()
    val upNextCount = (queue.size - currentIndex - 1).coerceAtLeast(0)

    var pastExpandedByUser by remember { mutableStateOf<Boolean?>(null) }
    val isPastExpanded = pastExpandedByUser ?: (pastCount <= PAST_COLLAPSE_THRESHOLD)

    val listItems = remember(queue, currentIndex, pastIndices, isPastExpanded, upNextCount) {
        buildList {
            for (index in queue.indices) {
                if (index == firstPast && pastCount > 0) {
                    add(QueueListItem.PastHeader(count = pastCount, isExpanded = isPastExpanded))
                }
                if (currentIndex >= 0 && index == currentIndex + 1 && upNextCount > 0) {
                    add(QueueListItem.UpNextHeader(count = upNextCount))
                }
                val isPast = index in pastIndices
                if (!isPast || isPastExpanded) {
                    add(QueueListItem.TrackItem(queueIndex = index, track = queue[index]))
                }
            }
        }
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            val fromKey = from.key as? String ?: return@rememberReorderableLazyListState
            val toKey = to.key as? String ?: return@rememberReorderableLazyListState
            val fromIndex = keys.indexOf(fromKey)
            val toIndex = keys.indexOf(toKey)
            if (fromIndex >= 0 && toIndex >= 0) {
                viewModel.moveQueueItem(fromIndex, toIndex)
                view.performAppHaptic(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
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
                    is QueueListItem.UpNextHeader -> "queue_header_up_next"
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
                is QueueListItem.UpNextHeader -> {
                    QueueSectionRule(
                        label = stringResource(R.string.queue_up_next),
                        count = item.count
                    )
                }
                is QueueListItem.TrackItem -> {
                    val index = item.queueIndex
                    val isCurrent = index == currentIndex
                    val isPast = index in pastIndices

                    ReorderableItem(
                        state = reorderableState,
                        key = keys[index],
                        animateItemModifier = Modifier,
                    ) { isDragging ->
                        QueueRow(
                            viewModel = viewModel,
                            track = item.track,
                            index = index,
                            itemKey = keys[index],
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
    itemKey: String = track.id.toString(),
    isCurrent: Boolean,
    isPast: Boolean,
    isDragging: Boolean,
    scope: ReorderableCollectionItemScope,
) {
    val view = LocalView.current
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val trackRemovalMethod by prefs.getTrackRemovalMethodFlow().collectAsState(initial = prefs.getTrackRemovalMethod())
    val isSwipeRemovalEnabled = trackRemovalMethod == TrackRemovalMethod.SWIPE_AND_MENU

    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
    val background by animateColorAsState(
        targetValue = when {
            isDragging -> scheme.surfaceContainerHighest
            isCurrent -> scheme.secondaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "queue_row_background"
    )
    val rowShape = RoundedCornerShape(12.dp)

    val rowContent = @Composable {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (!isCurrent) {
                            viewModel.skipToQueueItem(index)
                        }
                    },
                    onLongClick = {
                        view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS)
                        viewModel.showTrackOptions(track, fromPlayer = true, queueIndex = index)
                    }
                )
                .padding(start = 10.dp, end = if (isPast) 10.dp else 4.dp, top = if (isPast) 4.dp else 6.dp, bottom = if (isPast) 4.dp else 6.dp)
                .then(if (isPast) Modifier.alpha(0.55f) else Modifier),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (isPast) 32.dp else 44.dp)
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
                if (isCurrent) {
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
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title ?: stringResource(R.string.generic_title),
                        style = if (isPast) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isGoPlus) {
                        Spacer(Modifier.width(6.dp))
                        com.alananasss.kittytune.ui.common.GoPlusBadge(compact = true)
                    }
                }
                if (!isPast) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = track.displayArtist.ifBlank { track.user?.username ?: stringResource(R.string.generic_artist) },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrent) scheme.onSecondaryContainer.copy(alpha = 0.8f) else scheme.onSurfaceVariant,
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
            }

            Spacer(Modifier.width(8.dp))

            val duration = (viewModel.clipLengthOf(track) ?: track.actualDurationMs).takeIf { it > 0 }
            if (duration != null && !isPast) {
                Text(
                    text = makeTimeString(duration),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isCurrent) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(elevation, rowShape)
            .clip(rowShape)
            .background(background),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f)) {
            SwipeToDeleteItem(
                key = itemKey,
                enabled = isSwipeRemovalEnabled && !isCurrent && !isPast && !isDragging,
                clipContent = !isDragging,
                onDelete = { viewModel.removeTrackFromQueue(index) },
                shape = rowShape,
                horizontalPadding = 0.dp
            ) {
                rowContent()
            }
        }

        if (!isPast) {
            with(scope) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .padding(end = 6.dp)
                        .draggableHandle(
                            onDragStarted = { view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS) },
                            onDragStopped = { view.performAppHaptic(HapticFeedbackConstants.GESTURE_END) }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.desc_move),
                        tint = if (isCurrent) scheme.onSecondaryContainer.copy(alpha = 0.6f)
                               else if (isDragging) scheme.primary
                               else scheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
