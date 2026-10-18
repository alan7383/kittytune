package com.alananasss.kittytune.ui.player.lyrics.accompanist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextMotion
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastRoundToInt
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.utils.isRtl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.math.absoluteValue
import com.alananasss.kittytune.ui.player.lyrics.revealWhenPlaced
import com.alananasss.kittytune.ui.player.lyrics.returnToLine
import com.alananasss.kittytune.ui.player.lyrics.LyricsScrolling
import com.alananasss.kittytune.ui.player.lyrics.glideToLine
import kotlinx.coroutines.delay

internal data class FocusState(
    val firstIndex: Int,
    val allIndices: List<Int>,
    val activeInterludeIndex: Int?,
    val activeIntro: Boolean
)

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun KaraokeLyricsView(
    listState: LazyListState,
    lyrics: SyncedLyrics,
    currentPosition: () -> Int,
    onLineClicked: (ISyncedLine) -> Unit,
    onLinePressed: (ISyncedLine) -> Unit,
    modifier: Modifier = Modifier,
    normalLineTextStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 34.sp,
        fontWeight = FontWeight.Bold,
        textMotion = TextMotion.Animated,
    ),
    accompanimentLineTextStyle: TextStyle = LocalTextStyle.current.copy(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        textMotion = TextMotion.Animated,
    ),
    textColor: Color = Color.White,
    breathingDotsDefaults: KaraokeBreathingDotsDefaults = KaraokeBreathingDotsDefaults(),
    phoneticTextStyle: TextStyle = normalLineTextStyle.copy(
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    blendMode: BlendMode = BlendMode.Plus,
    useBlurEffect: Boolean = true,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    offset: Dp = 32.dp,
    startsAtTop: Boolean = false,
    keepAliveZone: Dp = 100.dp,
    blurDelta: Float = 3f,
    isScrubbing: Boolean = false,
    showDebugRectangles: Boolean = false,
    userAlignment: com.alananasss.kittytune.data.local.LyricsAlignment = com.alananasss.kittytune.data.local.LyricsAlignment.LEFT,
    lrcBounceEnabled: Boolean = true,
    bounceFactor: Float = 1f,
    lineSpacing: Dp = 0.dp,
    horizontalMargin: Dp = 0.dp,
    activeScale: Float = 1.00f
) {
    val density = LocalDensity.current
    val stableNormalTextStyle = remember(normalLineTextStyle) { normalLineTextStyle }
    val stableAccompanimentTextStyle =
        remember(accompanimentLineTextStyle) { accompanimentLineTextStyle }
    val stablePhoneticTextStyle = remember(phoneticTextStyle) { phoneticTextStyle }
    val stableOffset = remember(offset) { offset }
    val stableOffsetPx =
        remember(stableOffset) { with(density) { stableOffset.toPx().fastRoundToInt() } }
    val keepAliveZonePx = with(density) { keepAliveZone.toPx() }
    val stableBlendMode = remember(blendMode) { blendMode }

    val textMeasurer = rememberTextMeasurer()
    val layoutCache = remember { mutableStateMapOf<Int, List<SyllableLayout>>() }

    LaunchedEffect(
        lyrics,
        stableNormalTextStyle,
        stableAccompanimentTextStyle,
        stablePhoneticTextStyle
    ) {
        layoutCache.clear()
        withContext(Dispatchers.Default) {
            val normalStyle = stableNormalTextStyle.copy(textDirection = TextDirection.Content)
            val accompanimentStyle =
                stableAccompanimentTextStyle.copy(textDirection = TextDirection.Content)
            val phoneticStyle = stablePhoneticTextStyle.copy(textDirection = TextDirection.Content)

            val normalSpaceWidth = textMeasurer.measure(" ", normalStyle).size.width.toFloat()
            val accompanimentSpaceWidth =
                textMeasurer.measure(" ", accompanimentStyle).size.width.toFloat()

            lyrics.lines.forEachIndexed { index, line ->
                if (!isActive) return@forEachIndexed
                if (line is KaraokeLine) {
                    val style =
                        if (line is KaraokeLine.AccompanimentKaraokeLine) accompanimentStyle else normalStyle
                    val spaceWidth =
                        if (line is KaraokeLine.AccompanimentKaraokeLine) accompanimentSpaceWidth else normalSpaceWidth

                    val processedSyllables = if (line.alignment == KaraokeAlignment.End) {
                        line.syllables.dropLastWhile { it.content.isBlank() }
                    } else {
                        line.syllables
                    }

                    val layout = measureSyllablesAndDetermineAnimation(
                        syllables = processedSyllables,
                        textMeasurer = textMeasurer,
                        style = style,
                        phoneticStyle = phoneticStyle,
                        isAccompanimentLine = line is KaraokeLine.AccompanimentKaraokeLine,
                        spaceWidth = spaceWidth
                    )

                    withContext(Dispatchers.Main) {
                        layoutCache[index] = layout
                    }
                }
            }
        }
    }

    val currentTimeMs: () -> Int = currentPosition
    val timeProvider: () -> Int = currentPosition

    val accompanimentToMainMap = remember(lyrics.lines) {
        val map = mutableMapOf<Int, Int>()
        val mainLinesIndices = lyrics.lines.indices.filter { index ->
            val line = lyrics.lines[index]
            line !is KaraokeLine || line !is KaraokeLine.AccompanimentKaraokeLine
        }
        if (mainLinesIndices.isNotEmpty()) {
            lyrics.lines.forEachIndexed { index, line ->
                if (line is KaraokeLine && line is KaraokeLine.AccompanimentKaraokeLine) {
                    val beforeIdx = mainLinesIndices.findLast { it <= index }
                    val afterIdx = mainLinesIndices.find { it >= index }

                    val anchorIndex = when {
                        beforeIdx != null && afterIdx != null -> {
                            val distBefore =
                                (line.start - lyrics.lines[beforeIdx].start).absoluteValue
                            val distAfter =
                                (lyrics.lines[afterIdx].start - line.start).absoluteValue
                            if (distBefore <= distAfter) beforeIdx else afterIdx
                        }

                        beforeIdx != null -> beforeIdx
                        afterIdx != null -> afterIdx
                        else -> mainLinesIndices.first()
                    }
                    map[index] = anchorIndex
                }
            }
        }
        map
    }
    val effectiveEndTimes = remember(lyrics.lines) {
        IntArray(lyrics.lines.size) { index ->
            val line = lyrics.lines[index]
            var maxEnd = line.end

            if (line is KaraokeLine.MainKaraokeLine) {
                line.accompanimentLines?.forEach { acc ->
                    if (acc.end > maxEnd) maxEnd = acc.end
                }
            }
            maxEnd
        }
    }

    val firstLine = lyrics.lines.firstOrNull()

    val haveDotsIntro by remember(firstLine) {
        derivedStateOf {
            if (firstLine == null) false
            else (firstLine.start > 5000)
        }
    }

    val lyricsFocusState by remember(lyrics, effectiveEndTimes, accompanimentToMainMap, haveDotsIntro) {
        derivedStateOf {
            val time = currentTimeMs()
            val activeIndex = lyrics.lines.indices.findLast { idx ->
                time >= lyrics.lines[idx].start && time < effectiveEndTimes[idx]
            }

            val first = if (activeIndex != null) {
                activeIndex
            } else {
                val nextIdx = lyrics.lines.indexOfFirst { it.start > time }
                if (nextIdx != -1) nextIdx else lyrics.lines.lastIndex
            }

            val base = lyrics.lines.indices.filter { index ->
                time >= lyrics.lines[index].start && time < effectiveEndTimes[index]
            }
            val result = base.toMutableSet()
            base.forEach { index ->
                val line = lyrics.lines.getOrNull(index)
                if (line is KaraokeLine && line is KaraokeLine.AccompanimentKaraokeLine) {
                    accompanimentToMainMap[index]?.let { result.add(it) }
                }
            }

            val activeInterludeIndex = lyrics.lines.indices.find { index ->
                val line = lyrics.lines[index]
                val previousLine = lyrics.lines.getOrNull(index - 1)
                previousLine != null && (line.start - previousLine.end > 5000) && time in previousLine.end..line.start
            }
            val activeIntro = haveDotsIntro && time in 0 until (firstLine?.start ?: 0)

            FocusState(first, result.toList().sorted(), activeInterludeIndex, activeIntro)
        }
    }

    val scrollInCode = remember { mutableStateOf(false) }
    val isSnapScroll = remember { mutableStateOf(false) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }
    var lastFocusedIndex by remember { mutableIntStateOf(-1) }

    LaunchedEffect(lyrics) {
        lastFocusedIndex = -1
        lastScrollTime = 0L
    }

    val isReturning = remember { mutableStateOf(false) }

    val isManualScrolling by remember {
        derivedStateOf {
            (listState.isScrollInProgress && !scrollInCode.value) || isSnapScroll.value || isScrubbing || isReturning.value
        }
    }

    val isUserScrolling by remember { derivedStateOf { listState.isScrollInProgress && !scrollInCode.value } }
    var isReadingByHand by remember { mutableStateOf(false) }
    LaunchedEffect(isUserScrolling) {
        if (isUserScrolling) {
            isReadingByHand = true
        } else if (isReadingByHand) {
            delay(LyricsScrolling.MANUAL_GRACE_MS)
            val focus = lyricsFocusState.firstIndex
            if (focus in lyrics.lines.indices) {
                scrollInCode.value = true
                isReturning.value = true
                try {
                    listState.returnToLine(focus) { -followTopPx(listState, stableOffsetPx, keepAliveZonePx) }
                    lastFocusedIndex = focus
                } finally {
                    isReturning.value = false
                    scrollInCode.value = false
                }
            }
            isReadingByHand = false
        }
    }

    LaunchedEffect(
        lyrics,
        stableOffsetPx,
    ) {
        androidx.compose.runtime.snapshotFlow {
            Triple(lyricsFocusState.firstIndex, isScrubbing, isReadingByHand)
        }.collectLatest { (firstIndex, scrubbing, readingByHand) ->
            if (readingByHand && !scrubbing) return@collectLatest
            if (firstIndex in lyrics.lines.indices) {
                val now = System.currentTimeMillis()
                val timeDelta = now - lastScrollTime
                val isFirstPlacement = lastFocusedIndex < 0
                val indexDelta = if (lastFocusedIndex >= 0) kotlin.math.abs(firstIndex - lastFocusedIndex) else 0
                lastFocusedIndex = firstIndex

                val items = listState.layoutInfo.visibleItemsInfo
                val targetItem = items.firstOrNull { it.index == firstIndex }
                val isRapidClick = timeDelta < 280L
                val isLargeJump = indexDelta > 3 || isFirstPlacement
                val shouldSnap = scrubbing || isRapidClick || isLargeJump

                lastScrollTime = now

                try {
                    scrollInCode.value = true
                    if (shouldSnap) {
                        isSnapScroll.value = true
                    }

                    val desiredOffset = followTopPx(listState, stableOffsetPx, keepAliveZonePx)

                    if (targetItem != null && !isLargeJump) {
                        val scrollOffset = targetItem.offset - desiredOffset
                        if (kotlin.math.abs(scrollOffset) > 1) {
                            listState.scrollBy(scrollOffset.toFloat())
                        }
                    } else if (!isLargeJump && !scrubbing && !isRapidClick) {
                        listState.animateScrollToItem(firstIndex, -desiredOffset)
                    } else {
                        listState.scrollToItem(firstIndex)
                        val refreshed = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == firstIndex }
                        if (refreshed != null) {
                            val correction = refreshed.offset - desiredOffset
                            if (kotlin.math.abs(correction) > 1) {
                                listState.scrollBy(correction.toFloat())
                            }
                        }
                    }

                    if (shouldSnap) {
                        withFrameNanos { }
                    }
                } catch (_: Exception) {
                } finally {
                    isSnapScroll.value = false
                    scrollInCode.value = false
                }
            }
        }
    }

    LookaheadScope {
        Box(modifier = modifier.clipToBounds()) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .revealWhenPlaced(listState, lyricsFocusState.firstIndex, contentKey = lyrics.lines.size to lyrics.lines.firstOrNull()?.start)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .drawWithCache {
                        onDrawWithContent {
                            drawContent()
                            val topFade = 20.dp.toPx() / size.height
                            val bottomFade = 100.dp.toPx() / size.height
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    topFade to Color.Black,
                                    1f - bottomFade to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        }
                    }
                    .layout { measurable, constraints ->
                        val extraHeightPx = (keepAliveZone * 2).roundToPx()

                        val placeable = measurable.measure(
                            constraints.copy(
                                maxHeight = constraints.maxHeight + extraHeightPx
                            )
                        )

                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeable.place(0, -(keepAliveZone.roundToPx()))
                        }
                    },
                contentPadding = PaddingValues(
                    start = horizontalMargin,
                    end = horizontalMargin,
                    top = if (startsAtTop) keepAliveZone + TOP_START_GAP else stableOffset + keepAliveZone,
                    bottom = stableOffset + keepAliveZone,
                ),
                verticalArrangement = Arrangement.spacedBy(lineSpacing)
            ) {
                itemsIndexed(
                    items = lyrics.lines,
                    key = { index, line -> "${line.start}-${line.end}-$index" }
                ) { index, line ->
                    val isCurrentFocusLine = index in lyricsFocusState.allIndices
                    val isLineRtl =
                        when (line) {
                            is KaraokeLine -> {
                                remember(line.syllables) { line.syllables.any { it.content.isRtl() } }
                            }
                            else -> false
                        }
                    val isLineCenterAligned = when (line) {
                        is KaraokeLine -> {
                            remember(line.alignment) { line.alignment == KaraokeAlignment.Unspecified }
                        }
                        is SyncedLine -> {
                            remember(userAlignment) { userAlignment == com.alananasss.kittytune.data.local.LyricsAlignment.CENTER }
                        }
                        else -> false
                    }
                    val isLineRightAligned = when (line) {
                        is KaraokeLine -> {
                            remember(line.alignment) { line.alignment == KaraokeAlignment.End }
                        }
                        is SyncedLine -> {
                            remember(userAlignment) { userAlignment == com.alananasss.kittytune.data.local.LyricsAlignment.RIGHT }
                        }
                        else -> false
                    }
                    val isVisualCenterAligned = isLineCenterAligned
                    val isVisualRightAligned = remember(isLineRightAligned, isVisualCenterAligned, isLineRtl) {
                        if (isVisualCenterAligned) false
                        else if (isLineRightAligned) !isLineRtl
                        else isLineRtl
                    }

                    val distanceWeightState = remember(useBlurEffect, lyricsFocusState) {
                        derivedStateOf {
                            val start = lyricsFocusState.allIndices.firstOrNull() ?: lyricsFocusState.firstIndex
                            val end = lyricsFocusState.allIndices.lastOrNull() ?: lyricsFocusState.firstIndex
                            maxOf(0, start - index, index - end)
                        }
                    }

                    val dynamicStiffness by remember(distanceWeightState.value) {
                        derivedStateOf {
                            (LINE_STIFFNESS - distanceWeightState.value * LINE_STIFFNESS_STEP).coerceAtLeast(LINE_STIFFNESS_MIN)
                        }
                    }

                    val itemHorizontalAlignment = when {
                        isVisualCenterAligned -> Alignment.CenterHorizontally
                        isVisualRightAligned -> Alignment.End
                        else -> Alignment.Start
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .springPlacement(
                                this@LookaheadScope,
                                "${line.start}-${line.end}-$index",
                                isManualScrolling,
                                stiffness = dynamicStiffness
                            ),
                        horizontalAlignment = itemHorizontalAlignment
                    ) {
                        val previousLine = lyrics.lines.getOrNull(index - 1)

                        val showDotsInterlude = lyricsFocusState.activeInterludeIndex == index
                        val showDotsIntro = lyricsFocusState.activeIntro && index == 0

                        AnimatedVisibility(showDotsInterlude || showDotsIntro) {
                            KaraokeBreathingDots(
                                alignment = when (val l = previousLine ?: firstLine) {
                                    is KaraokeLine -> l.alignment
                                    is SyncedLine -> when {
                                        isVisualCenterAligned -> KaraokeAlignment.Unspecified
                                        isVisualRightAligned -> KaraokeAlignment.End
                                        l.content.isRtl() -> KaraokeAlignment.End
                                        else -> KaraokeAlignment.Start
                                    }
                                    else -> KaraokeAlignment.Start
                                },
                                startTimeMs = previousLine?.end ?: 0,
                                endTimeMs = if (showDotsIntro) firstLine!!.start else line.start,
                                currentTimeProvider = timeProvider,
                                defaults = if (breathingDotsDefaults.breathingDotsColor == Color.White && textColor != Color.White) {
                                    breathingDotsDefaults.copy(breathingDotsColor = textColor)
                                } else {
                                    breathingDotsDefaults
                                },
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }

                        val blurRadiusState = animateFloatAsState(
                            targetValue = (
                                    if (!useBlurEffect || isReadingByHand) 0f
                                    else if (distanceWeightState.value > 0) {
                                        distanceWeightState.value * blurDelta
                                    } else 0f),
                            animationSpec = tween(if (isReadingByHand) 250 else 600),
                        )

                        when (line) {
                            is KaraokeLine -> {
                                if (line is KaraokeLine.MainKaraokeLine) {
                                    LyricsLineItem(
                                        isFocused = isCurrentFocusLine,
                                        isRightAligned = isVisualRightAligned,
                                        isCenterAligned = isVisualCenterAligned,
                                        onLineClicked = { onLineClicked(line) },
                                        onLinePressed = { onLinePressed(line) },
                                        blurRadius = { blurRadiusState.value },
                                        blendMode = stableBlendMode,
                                        activeScale = activeScale,
                                    ) {
                                        KaraokeLineText(
                                            line = line,
                                            currentTimeProvider = timeProvider,
                                            normalLineTextStyle = stableNormalTextStyle,
                                            accompanimentLineTextStyle = stableAccompanimentTextStyle,
                                            phoneticTextStyle = stablePhoneticTextStyle,
                                            activeColor = textColor,
                                            blendMode = stableBlendMode,
                                            showDebugRectangles = showDebugRectangles,
                                            showTranslation = showTranslation,
                                            showPhonetic = showPhonetic,
                                            precalculatedLayouts = layoutCache[index],
                                            activeScale = activeScale
                                        )
                                    }
                                }
                            }

                            is SyncedLine -> {
                                val isLineRtlSynced = remember(line.content) { line.content.isRtl() }
                                LyricsLineItem(
                                    isFocused = isCurrentFocusLine,
                                    isRightAligned = isVisualRightAligned,
                                    isCenterAligned = isVisualCenterAligned,
                                    onLineClicked = { onLineClicked(line) },
                                    onLinePressed = { onLinePressed(line) },
                                    blurRadius = { blurRadiusState.value },
                                    blendMode = stableBlendMode,
                                    activeScale = activeScale,
                                ) {
                                    SyncedLineText(
                                        line = line,
                                        isLineRtl = isLineRtlSynced,
                                        isRightAligned = isVisualRightAligned,
                                        isCenterAligned = isVisualCenterAligned,
                                        textStyle = stableNormalTextStyle.copy(lineHeight = 1.2.em),
                                        textColor = textColor,
                                        showTranslation = showTranslation,
                                        activeScale = activeScale,
                                    )
                                }
                            }
                        }
                    }
                }
                item("BottomSpacing") {
                    Spacer(
                        modifier = Modifier.fillMaxWidth().height(2000.dp)
                    )
                }
            }
        }
    }
}

private val TOP_START_GAP = 12.dp
private const val LINE_STIFFNESS = 120f
private const val LINE_STIFFNESS_STEP = 20f
private const val LINE_STIFFNESS_MIN = 20f

private fun followTopPx(listState: LazyListState, stableOffsetPx: Int, keepAliveZonePx: Float): Int =
    (listState.layoutInfo.viewportStartOffset + stableOffsetPx + keepAliveZonePx).toInt()
