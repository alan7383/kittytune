package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.floor

/**
 * How the lyrics views scroll, shared across player lyrics.
 */
internal object LyricsScrolling {

    /**
     * Auto-scroll rate for lyrics with no timings, at speed 1×, in **lines** per second.
     */
    const val PLAIN_BASE_LINES_PER_SEC = 0.3f

    /**
     * Where the untimed text belongs for a given playback position.
     */
    fun plainScrollTarget(positionMs: Float, speed: Float, lineCount: Int): PlainScrollTarget {
        if (lineCount <= 0) return PlainScrollTarget(0, 0f)
        val lines = PLAIN_BASE_LINES_PER_SEC * speed * (positionMs / 1000f)
        val whole = floor(lines)
        val index = whole.toInt().coerceIn(0, lineCount - 1)
        val fraction = if (index == lineCount - 1) 0f else (lines - whole).coerceIn(0f, 1f)
        return PlainScrollTarget(index, fraction)
    }

    data class PlainScrollTarget(val index: Int, val fraction: Float)

    /** How far one notch moves, in pixels, given the height of a line. */
    fun wheelStepPx(notches: Float, lines: Float, lineHeightPx: Float): Float =
        notches * lines * lineHeightPx

    /** How long a manual scroll holds the automatic one off, counted from when the scrolling stopped. */
    const val PLAIN_PAUSE_MS = 3_000L

    /**
     * How long a synced view leaves the reader alone after they stop scrolling by hand, before it goes
     * back to following the track: three seconds of the reader not touching it.
     */
    const val MANUAL_GRACE_MS = 3_000L
}

/**
 * Keeps [listState] parked on [activeIndex] as the song moves through the lines.
 */
@Composable
internal fun FollowActiveLine(
    listState: LazyListState,
    activeIndex: Int,
    anchorPx: Int = 0,
    centred: Boolean = false,
    contentKey: Any? = null,
): Boolean {
    var lastManualScrollMs by remember { mutableStateOf(0L) }
    var readingByHand by remember { mutableStateOf(false) }

    /** True for exactly as long as the scroll below is ours, so the flag cannot be misattributed. */
    var autoScrolling by remember { mutableStateOf(false) }

    /** The reader's own scroll is under way: a drag held, or touch still moving. */
    var scrollingByHand by remember { mutableStateOf(false) }

    LaunchedEffect(lastManualScrollMs, scrollingByHand) {
        if (lastManualScrollMs == 0L) return@LaunchedEffect
        readingByHand = true
        if (scrollingByHand) return@LaunchedEffect
        delay(LyricsScrolling.MANUAL_GRACE_MS)
        readingByHand = false
    }

    var placed by remember(listState, contentKey) { mutableStateOf(false) }

    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start || interaction is PressInteraction.Press) {
                lastManualScrollMs = System.currentTimeMillis()
            }
        }
    }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !autoScrolling) {
            scrollingByHand = true
            lastManualScrollMs = System.currentTimeMillis()
        } else if (!listState.isScrollInProgress && scrollingByHand) {
            scrollingByHand = false
            lastManualScrollMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(activeIndex, anchorPx, contentKey) {
        if (activeIndex < 0) return@LaunchedEffect

        var waited = false
        while (true) {
            if (scrollingByHand) {
                waited = true
                delay(MANUAL_POLL_MS)
                continue
            }
            val remaining =
                LyricsScrolling.MANUAL_GRACE_MS - (System.currentTimeMillis() - lastManualScrollMs)
            if (remaining <= 0) break
            waited = true
            delay(remaining)
        }
        if (waited && placed) return@LaunchedEffect

        autoScrolling = true
        try {
            if (placed) {
                listState.glideToLine(activeIndex) { followOffset(listState, activeIndex, anchorPx, centred) }
            } else {
                listState.scrollToItem(activeIndex, -anchorPx)
                if (centred) listState.scrollToItem(activeIndex, followOffset(listState, activeIndex, anchorPx, true))
            }
            placed = true
        } finally {
            autoScrolling = false
        }
    }

    val currentActive by rememberUpdatedState(activeIndex)
    LaunchedEffect(readingByHand) {
        if (readingByHand || !placed || currentActive < 0) return@LaunchedEffect
        autoScrolling = true
        try {
            listState.returnToLine(currentActive) { followOffset(listState, currentActive, anchorPx, centred) }
        } finally {
            autoScrolling = false
        }
    }
    return readingByHand
}

private const val MANUAL_POLL_MS = 100L
private const val RETURN_MAX_VIEWPORTS = 2f

/**
 * Slides the list back to line [index] after the reader scrolled away: one eased movement of the whole list.
 */
internal suspend fun LazyListState.returnToLine(index: Int, offset: () -> Int) {
    val info = layoutInfo
    val viewport = (info.viewportEndOffset - info.viewportStartOffset).coerceAtLeast(1)
    var distance = distanceToLine(index, offset) ?: run {
        scrollToItem(index, offset())
        return
    }
    val maxRun = viewport * RETURN_MAX_VIEWPORTS
    if (kotlin.math.abs(distance) > maxRun) {
        scrollBy(distance - kotlin.math.sign(distance) * maxRun)
        withFrameNanos { }
        distance = distanceToLine(index, offset) ?: return
    }
    val durationMs = RETURN_BASE_MS + RETURN_PER_VIEWPORT_MS * kotlin.math.abs(distance) / viewport
    scroll {
        var travelled = 0f
        val startNanos = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val t = ((now - startNanos) / 1_000_000f / durationMs).coerceIn(0f, 1f)
            val left = distanceToLine(index, offset) ?: break
            val wanted = (travelled + left) * FastOutSlowInEasing.transform(t)
            val step = wanted - travelled
            val consumed = scrollBy(step)
            travelled += consumed
            if (t >= 1f || kotlin.math.abs(consumed) + 0.5f < kotlin.math.abs(step)) break
        }
    }
}

private fun LazyListState.distanceToLine(index: Int, offset: () -> Int): Float? {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return null
    visible.firstOrNull { it.index == index }?.let { return (it.offset + offset()).toFloat() }
    val first = visible.first()
    val averageStep = (visible.last().offset + visible.last().size - first.offset).toFloat() / visible.size
    return first.offset + (index - first.index) * averageStep + offset()
}

private const val RETURN_BASE_MS = 520f
private const val RETURN_PER_VIEWPORT_MS = 180f
private const val GLIDE_RUN_UP_LINES = 3

/**
 * Scrolls to [index] with a glide however far away it is.
 */
internal suspend fun LazyListState.glideToLine(index: Int, offset: () -> Int) {
    val visible = layoutInfo.visibleItemsInfo.any { it.index == index }
    if (!visible) {
        val runUp = if (firstVisibleItemIndex < index) index - GLIDE_RUN_UP_LINES else index + GLIDE_RUN_UP_LINES
        scrollToItem(runUp.coerceIn(0, (layoutInfo.totalItemsCount - 1).coerceAtLeast(0)))
    }
    animateScrollToItem(index, offset())
}

/**
 * The line the view is about: the one being sung, or — while the reader scrolls by hand — the one nearest
 * the middle of the viewport.
 */
@Composable
internal fun rememberFocusLine(listState: LazyListState, activeIndex: Int, readingByHand: Boolean): Int {
    val centreLine by remember(listState) { derivedStateOf { listState.lineNearestViewportCentre() } }
    return if (readingByHand && centreLine >= 0) centreLine else activeIndex
}

private fun LazyListState.lineNearestViewportCentre(): Int {
    val info = layoutInfo
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - middle) }?.index ?: -1
}

private fun followOffset(listState: LazyListState, index: Int, anchorPx: Int, centred: Boolean): Int {
    if (!centred) return -anchorPx
    val info = listState.layoutInfo
    val line = info.visibleItemsInfo.firstOrNull { it.index == index } ?: return -anchorPx
    val middle = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return line.size / 2 - middle
}

/**
 * Drives a list of untimed lyrics from the playback position, and steps aside when the reader scrolls.
 */
@Composable
internal fun FollowPlainLyrics(
    listState: LazyListState,
    enabled: Boolean,
    speed: Float,
    lineCount: Int,
    positionMs: () -> Long,
    isPlaying: () -> Boolean,
    playbackSpeed: () -> Float,
    lastManualScrollMs: () -> Long,
) {
    val position by rememberUpdatedState(positionMs)
    val playing by rememberUpdatedState(isPlaying)
    val rate by rememberUpdatedState(playbackSpeed)
    val lastManual by rememberUpdatedState(lastManualScrollMs)

    var autoScrolling by remember { mutableStateOf(false) }
    var lastForeignScrollMs by remember { mutableStateOf(0L) }
    var foreignScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !autoScrolling) {
            foreignScrolling = true
            lastForeignScrollMs = System.currentTimeMillis()
        } else if (!listState.isScrollInProgress && foreignScrolling) {
            foreignScrolling = false
            lastForeignScrollMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(enabled, speed, lineCount) {
        if (!enabled || lineCount <= 0) return@LaunchedEffect

        var reported = position()
        var reportedAtMs = System.currentTimeMillis()

        var appliedIndex = -1
        var appliedOffset = Int.MIN_VALUE
        var returningFromManual = false

        while (true) {
            withFrameNanos { }

            val fresh = position()
            if (fresh != reported) {
                reported = fresh
                reportedAtMs = System.currentTimeMillis()
            }

            val lastTouched = maxOf(lastManual(), lastForeignScrollMs)
            if (foreignScrolling || System.currentTimeMillis() - lastTouched < LyricsScrolling.PLAIN_PAUSE_MS) {
                returningFromManual = true
                continue
            }

            val elapsed = if (playing()) {
                (System.currentTimeMillis() - reportedAtMs).coerceAtMost(MAX_EXTRAPOLATION_MS)
            } else {
                0L
            }
            val estimated = reported + elapsed * rate()

            val target = LyricsScrolling.plainScrollTarget(estimated, speed, lineCount)
            val offset = (target.fraction * lineHeightPx(listState, target.index)).toInt()

            if (target.index == appliedIndex && offset == appliedOffset && !returningFromManual) continue
            appliedIndex = target.index
            appliedOffset = offset

            autoScrolling = true
            try {
                if (returningFromManual) {
                    returningFromManual = false
                    listState.returnToLine(target.index) { offset }
                } else {
                    listState.scrollToItem(target.index, offset)
                }
            } finally {
                autoScrolling = false
            }
        }
    }
}

private fun lineHeightPx(listState: LazyListState, index: Int): Float {
    val visible = listState.layoutInfo.visibleItemsInfo
    val item = visible.firstOrNull { it.index == index } ?: visible.firstOrNull()
    return item?.size?.toFloat() ?: 0f
}

private const val MAX_EXTRAPOLATION_MS = 400L

/**
 * How far the reader may still scroll forward by hand: until the last line has risen to just above the bottom edge.
 */
internal fun LazyListState.roomForwardPx(): Float {
    val info = layoutInfo
    val last = info.visibleItemsInfo.lastOrNull() ?: return 0f
    if (last.index != info.totalItemsCount - 1) return Float.MAX_VALUE
    val bottomEdge = info.viewportEndOffset + info.afterContentPadding
    val keep = (bottomEdge - info.viewportStartOffset) * LAST_LINE_KEEP_FRACTION
    return (last.offset + last.size - (bottomEdge - keep)).coerceAtLeast(0f)
}

private const val LAST_LINE_KEEP_FRACTION = 0.14f

/** Holds back a drag or touch that would scroll past [roomForwardPx]. */
internal fun Modifier.stopAtLastLine(listState: LazyListState): Modifier = nestedScroll(object : NestedScrollConnection {
    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (available.y >= 0f) return Offset.Zero
        val over = -available.y - listState.roomForwardPx()
        return if (over > 0f) Offset(0f, over) else Offset.Zero
    }
})

private val revealedContent: MutableSet<Any> = java.util.Collections.newSetFromMap(
    object : java.util.LinkedHashMap<Any, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Any, Boolean>?) = size > 24
    },
)

private const val REVEAL_MS = 420
private const val REVEAL_PLACEMENT_TIMEOUT_MS = 700L

/**
 * Keeps a lyrics list invisible until it shows the line being sung, then fades it in as one block.
 */
@Composable
internal fun Modifier.revealWhenPlaced(listState: LazyListState, activeIndex: Int, contentKey: Any? = null): Modifier {
    val alpha = remember(listState) { Animatable(0f) }
    val currentActiveIndex by rememberUpdatedState(activeIndex)
    val risePx = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(listState, contentKey) {
        alpha.snapTo(0f)
        withTimeoutOrNull(REVEAL_PLACEMENT_TIMEOUT_MS) {
            snapshotFlow {
                val layout = listState.layoutInfo
                val target = currentActiveIndex
                layout.totalItemsCount > 0 &&
                    !listState.isScrollInProgress &&
                    (target < 0 || layout.visibleItemsInfo.any { it.index == target })
            }.first { it }
            withFrameNanos { }
        }
        if (contentKey != null && !revealedContent.add(contentKey)) {
            alpha.snapTo(1f)
        } else {
            alpha.animateTo(1f, tween(REVEAL_MS, easing = FastOutSlowInEasing))
        }
    }
    return graphicsLayer {
        this.alpha = alpha.value
        translationY = (1f - alpha.value) * risePx
    }
}
