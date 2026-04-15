package com.alananasss.kittytune.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpandedQueueScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val queueState = viewModel.queueState
    val view = LocalView.current
    val listState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            viewModel.moveQueueItem(from.index, to.index)
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    )

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        stringResource(R.string.player_queue),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            Icons.Rounded.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.btn_close)
                        )
                    }
                },
                actions = {
                    val shuffleColor = if (viewModel.shuffleEnabled)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface

                    IconButton(onClick = { viewModel.toggleShuffle() }) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = stringResource(R.string.queue_shuffle),
                            tint = shuffleColor
                        )
                    }

                    val repeatIcon = when (viewModel.repeatMode) {
                        RepeatMode.ONE -> Icons.Rounded.RepeatOne
                        else -> Icons.Rounded.Repeat
                    }
                    val repeatColor = if (viewModel.repeatMode == RepeatMode.NONE)
                        MaterialTheme.colorScheme.onSurface
                    else
                        MaterialTheme.colorScheme.primary

                    IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                        Icon(
                            imageVector = repeatIcon,
                            contentDescription = stringResource(R.string.queue_repeat),
                            tint = repeatColor
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surface),
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            itemsIndexed(
                items = queueState,
                key = { _, track -> track.id }
            ) { index, track ->
                ReorderableItem(
                    state = reorderableState,
                    key = track.id
                ) { isDragging ->
                    val isCurrent = track.id == viewModel.currentTrack?.id
                    val shouldDarken = viewModel.repeatMode == RepeatMode.ONE && !isCurrent
                    val itemAlpha by animateFloatAsState(
                        targetValue = if (shouldDarken) 0.3f else 1.0f,
                        label = "dim_alpha"
                    )
                    val elevation by animateDpAsState(
                        targetValue = if (isDragging) 8.dp else 0.dp,
                        label = "elevation"
                    )
                    val baseColor = if (isDragging)
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    else
                        MaterialTheme.colorScheme.surface

                    SwipeToDeleteItem(
                        onDelete = { viewModel.removeTrackFromQueue(index) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(72.dp)
                                .shadow(elevation)
                                .background(
                                    if (isCurrent)
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    else
                                        baseColor
                                )
                                .clickable { viewModel.skipToQueueItem(index) }
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = track.fullResArtwork,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .alpha(itemAlpha)
                            ) {
                                Text(
                                    text = track.title
                                        ?: stringResource(R.string.generic_title),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )
                                Text(
                                    text = track.user?.username
                                        ?: stringResource(R.string.unknown_artist),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.DragHandle,
                                contentDescription = "Move",
                                tint = if (isDragging)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier
                                    .size(32.dp)
                                    .draggableHandle(
                                        onDragStarted = {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        },
                                        onDragStopped = {
                                            view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                        }
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwipeToDeleteItem(
    onDelete: () -> Unit,
    content: @Composable () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    val dismissThresholdPx = with(density) { 120.dp.toPx() }
    val maxSwipePx = with(density) { 200.dp.toPx() }

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

                    // Premier touch, ne pas consommer pour laisser passer au reorderable
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

                        // Déterminer la direction une seule fois
                        if (isHorizontal == null) {
                            val absX = abs(deltaX)
                            val absY = abs(deltaY)
                            if (absX > 3f || absY > 3f) {
                                isHorizontal = absX > absY
                            }
                        }

                        if (isHorizontal == true) {
                            // Geste horizontal : on consomme et on gère le swipe
                            change.consume()
                            dragStarted = true
                            velocityTracker.addPosition(change.uptimeMillis, change.position)

                            val newOffset = (offsetX.value + deltaX).coerceIn(-maxSwipePx, 0f)
                            coroutineScope.launch {
                                offsetX.snapTo(newOffset)
                            }
                        }
                        // Si vertical : on ne consomme pas → le reorderable reçoit le geste
                    }

                    // Fin du geste
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
        // Background rouge
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(bgColor)
                .padding(end = 24.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            Icon(
                imageVector = Icons.Rounded.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error.copy(alpha = iconAlpha),
                modifier = Modifier.size(24.dp)
            )
        }

        // Contenu décalé
        Box(
            modifier = Modifier.offset { IntOffset(offsetX.value.roundToInt(), 0) }
        ) {
            content()
        }
    }
}
