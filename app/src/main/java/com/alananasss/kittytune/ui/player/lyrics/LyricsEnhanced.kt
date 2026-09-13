package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.theme.LyricsFontFamily
import com.alananasss.kittytune.ui.theme.rememberLyricsFontFamily
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val LRC_LEAD_MS = 300L
private const val WORD_SYNC_LEAD_MS = 0L
private const val MANUAL_SCROLL_TIMEOUT_MS = 3000L
private const val MANUAL_SCROLL_DEBOUNCE_MS = 50L
private const val LYRIC_FOCUS_ANCHOR_RATIO = 0.42f
private const val LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO = 0.35f
private const val LYRIC_FOCUS_TOP_GUARD_RATIO = 0.18f
private const val LYRIC_FOCUS_BOTTOM_GUARD_RATIO = 0.24f
private const val LYRIC_FOCUS_MIN_SCROLL_PX = 6
private const val LYRIC_FOCUS_ANIMATED_DISTANCE = 12
private const val LYRIC_FOCUS_SCROLL_DURATION_MS = 520
private const val MIN_KARAOKE_SYLLABLE_DURATION_MS = 1

@Composable
fun LyricsEnhancedView(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier,
    textColorOverride: Color? = null,
    onLineClick: ((Long) -> Unit)? = null
) {
    val rawLines = viewModel.lyricsLines
    val isSynced = rawLines.any { it.endTime > 0 }
    val isWordSyncedFormat = viewModel.isWordSyncEnabled && rawLines.any { it.words.isNotEmpty() }
    val lyricsClick = true
    val lyricsTextSize = viewModel.lyricsFontSize
    val lyricsLineBlur = viewModel.lyricsLineBlurEnabled
    val textColor = textColorOverride ?: Color.White

    val showTranslations = viewModel.isLyricsTranslationEnabled && rawLines.any { !it.translation.isNullOrBlank() }
    val showPhonetics = viewModel.isRomanizationEnabled && rawLines.any { !it.romanization.isNullOrBlank() }

    val baseLayoutDirection = LocalLayoutDirection.current
    val lyricsLayoutDirection = remember(rawLines, baseLayoutDirection) {
        if (rawLines.firstOrNull { it.text.isNotBlank() }?.let { isRtlText(it.text) } == true) {
            LayoutDirection.Rtl
        } else {
            baseLayoutDirection
        }
    }

    val lyricsSessionKey = remember(viewModel.currentTrack?.id, rawLines.size) {
        "${viewModel.currentTrack?.id ?: 0L}_${rawLines.size}"
    }

    val isDuetEnabled = viewModel.isDuetViewEnabled
    val syncedLyrics = remember(
        viewModel.lyricsRevision,
        rawLines.size,
        isWordSyncedFormat,
        isDuetEnabled,
        showTranslations,
        showPhonetics
    ) {
        buildSyncedLyrics(rawLines, isWordSyncedFormat, isDuetEnabled)
    }

    val leadMs = if (isWordSyncedFormat) WORD_SYNC_LEAD_MS else LRC_LEAD_MS
    val latestLyricsSyncOffset = rememberUpdatedState(viewModel.lyricsOffset)
    val latestLeadMs = rememberUpdatedState(leadMs)
    val latestPlaybackSpeed = rememberUpdatedState(viewModel.effectsState.speed)

    val playbackPositionMs = remember(viewModel.currentTrack?.id) {
        mutableLongStateOf(MusicManager.player.currentPosition.coerceAtLeast(0L))
    }
    var isManualScrolling by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }
    val listState = key(lyricsSessionKey) { rememberLazyListState() }

    LaunchedEffect(lyricsSessionKey) {
        playbackPositionMs.longValue = MusicManager.player.currentPosition.coerceAtLeast(0L)
        isManualScrolling = false
        lastManualScrollTime = 0L
    }

    // High-precision smooth frame interpolation loop with PLL drift tracking
    LaunchedEffect(viewModel.currentTrack?.id) {
        var smoothPositionMs = MusicManager.player.currentPosition.coerceAtLeast(0L).toDouble()
        var lastOutputPositionMs = smoothPositionMs.toLong()
        var lastFrameNanos = 0L

        while (isActive) {
            val isSliderActive = viewModel.isScrubbing
            val rawPosition = if (isSliderActive) {
                viewModel.currentPosition
            } else {
                MusicManager.player.currentPosition.coerceAtLeast(0L)
            }
            val isPlaying = MusicManager.player.isPlaying

            if (isSliderActive || !isPlaying) {
                smoothPositionMs = rawPosition.toDouble()
                lastOutputPositionMs = rawPosition
                lastFrameNanos = 0L
                if (playbackPositionMs.longValue != rawPosition) {
                    playbackPositionMs.longValue = rawPosition
                }
                delay(50L)
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }

                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameNanos
                    smoothPositionMs = rawPosition.toDouble()
                    lastOutputPositionMs = rawPosition
                } else {
                    val elapsedNanos = frameNanos - lastFrameNanos
                    lastFrameNanos = frameNanos

                    val currentSpeed = latestPlaybackSpeed.value
                    val deltaMs = (elapsedNanos / 1_000_000.0).coerceIn(0.0, 100.0) * currentSpeed
                    val driftMs = rawPosition.toDouble() - smoothPositionMs

                    // Discontinuity check: true seek, skip, or track loop.
                    if (kotlin.math.abs(driftMs) > 300.0 || rawPosition < lastOutputPositionMs - 500L) {
                        smoothPositionMs = rawPosition.toDouble()
                        lastOutputPositionMs = rawPosition
                    } else {
                        // Phase-Locked Loop (PLL) gentle frequency adjustment:
                        // Pulls smoothPosition towards rawPosition smoothly over ~250ms.
                        // Rate correction is bounded so effective speed is always positive (>= 0.2x).
                        val rateCorrection = (driftMs / 250.0).coerceIn(-0.5, 0.5)
                        val effectiveSpeed = (1.0 + rateCorrection).coerceIn(0.2, 1.8)
                        smoothPositionMs += deltaMs * effectiveSpeed

                        // Enforce strict monotonicity during forward playback:
                        val targetMs = smoothPositionMs.roundToLong()
                        if (targetMs >= lastOutputPositionMs) {
                            lastOutputPositionMs = targetMs
                        } else {
                            smoothPositionMs = lastOutputPositionMs.toDouble()
                        }
                    }
                }

                if (playbackPositionMs.longValue != lastOutputPositionMs) {
                    playbackPositionMs.longValue = lastOutputPositionMs
                }
            }
        }
    }

    val playbackSyncPosition: () -> Int = remember {
        {
            (playbackPositionMs.longValue +
                latestLyricsSyncOffset.value.toLong() +
                latestLeadMs.value).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
        }
    }

    val lineFocusPosition: () -> Int = remember(syncedLyrics) {
        {
            syncedLyrics.positionForStableLineFocus(playbackSyncPosition())
        }
    }

    val nestedScrollConnection = remember {
        var lastUserScrollEventMs = 0L
        object : NestedScrollConnection {
            private fun markManualScroll() {
                val now = System.currentTimeMillis()
                if (now - lastUserScrollEventMs >= MANUAL_SCROLL_DEBOUNCE_MS) {
                    isManualScrolling = true
                    lastManualScrollTime = now
                    lastUserScrollEventMs = now
                }
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    markManualScroll()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (isManualScrolling) {
                    lastManualScrollTime = System.currentTimeMillis()
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(isManualScrolling, lastManualScrollTime) {
        if (isManualScrolling) {
            delay(MANUAL_SCROLL_TIMEOUT_MS)
            isManualScrolling = false
        }
    }

    LaunchedEffect(lyricsSessionKey, syncedLyrics, isSynced) {
        if (!isSynced || syncedLyrics.lines.isEmpty()) return@LaunchedEffect
        snapshotFlow {
            listState.layoutInfo.viewportEndOffset > listState.layoutInfo.viewportStartOffset
        }.first { it }

        var forceNextScroll = true
        snapshotFlow {
            if (isManualScrolling) {
                null
            } else {
                syncedLyrics
                    .getCurrentFirstHighlightLineIndexByTime(lineFocusPosition())
                    .takeIf { index -> index in syncedLyrics.lines.indices }
            }
        }.distinctUntilChanged()
            .collectLatest { index ->
                if (index == null) {
                    forceNextScroll = true
                    return@collectLatest
                }

                listState.scrollLyricIntoFocus(
                    index = index,
                    animateToNearbyItem = !forceNextScroll,
                    force = forceNextScroll,
                    alignByItemCenter = isWordSyncedFormat,
                )
                forceNextScroll = false
            }
    }

    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
            .nestedScroll(nestedScrollConnection)
    ) {
        val isCompact = maxHeight < 450.dp
        val effectiveFontSize = lyricsTextSize
        val lyricsViewportOffset = if (isCompact) (maxHeight * 0.22f) else (maxHeight * 0.38f)

        val lyricsFontFamily = rememberLyricsFontFamily(viewModel.lyricsFont)
        val normalTextStyle = MaterialTheme.typography.headlineMedium.copy(
            fontSize = effectiveFontSize.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = lyricsFontFamily
        )
        val accompanimentTextStyle = MaterialTheme.typography.titleLarge.copy(
            fontSize = (effectiveFontSize * 0.82f).sp,
            fontFamily = lyricsFontFamily
        )
        val phoneticTextStyle = MaterialTheme.typography.bodyMedium.copy(
            fontSize = (effectiveFontSize * 0.55f).sp,
            fontWeight = FontWeight.Normal,
            fontFamily = lyricsFontFamily
        )

        if (syncedLyrics.lines.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.lyrics_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            CompositionLocalProvider(LocalLayoutDirection provides lyricsLayoutDirection) {
                key(lyricsSessionKey, syncedLyrics, showTranslations, showPhonetics) {
                    KaraokeLyricsView(
                        listState = listState,
                        lyrics = syncedLyrics,
                        currentPosition = playbackSyncPosition,
                        onLineClicked = { line ->
                            if (lyricsClick && isSynced && line.start > 0) {
                                val seekTarget = line.start.toLong()
                                onLineClick?.invoke(seekTarget) ?: viewModel.seekTo(seekTarget)
                            }
                        },
                        onLinePressed = { },
                        textColor = textColor,
                        normalLineTextStyle = normalTextStyle,
                        accompanimentLineTextStyle = accompanimentTextStyle,
                        phoneticTextStyle = phoneticTextStyle,
                        blendMode = BlendMode.SrcOver,
                        useBlurEffect = lyricsLineBlur,
                        showTranslation = showTranslations,
                        showPhonetic = showPhonetics,
                        offset = lyricsViewportOffset,
                        keepAliveZone = if (isCompact) 36.dp else 72.dp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private suspend fun LazyListState.scrollLyricIntoFocus(
    index: Int,
    animateToNearbyItem: Boolean,
    force: Boolean,
    alignByItemCenter: Boolean,
) {
    val itemCount = layoutInfo.totalItemsCount
    if (itemCount == 0) return

    val targetIndex = index.coerceIn(0, itemCount - 1)
    var itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    if (itemInfo == null) {
        val distance = abs(targetIndex - firstVisibleItemIndex)
        if (animateToNearbyItem && distance <= LYRIC_FOCUS_ANIMATED_DISTANCE) {
            animateScrollToItem(targetIndex)
        } else {
            scrollToItem(targetIndex)
        }
        withFrameNanos { }
        itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { item -> item.index == targetIndex }
    }

    itemInfo ?: return

    val viewportStart = layoutInfo.viewportStartOffset
    val viewportEnd = layoutInfo.viewportEndOffset
    val viewportHeight = viewportEnd - viewportStart
    if (viewportHeight <= 0) return

    val itemFocusPoint = if (alignByItemCenter) {
        itemInfo.offset + itemInfo.size / 2
    } else {
        itemInfo.offset
    }
    val topGuard = viewportStart + (viewportHeight * LYRIC_FOCUS_TOP_GUARD_RATIO).roundToInt()
    val bottomGuard = viewportEnd - (viewportHeight * LYRIC_FOCUS_BOTTOM_GUARD_RATIO).roundToInt()
    if (!force && itemFocusPoint in topGuard..bottomGuard) return

    val anchorRatio = if (alignByItemCenter) {
        LYRIC_FOCUS_ANCHOR_RATIO
    } else {
        LYRIC_LINE_SYNC_TOP_ANCHOR_RATIO
    }
    val targetFocusPoint = viewportStart + (viewportHeight * anchorRatio).roundToInt()
    val scrollDelta = itemFocusPoint - targetFocusPoint
    if (abs(scrollDelta) > LYRIC_FOCUS_MIN_SCROLL_PX) {
        animateScrollBy(
            value = scrollDelta.toFloat(),
            animationSpec = tween(
                durationMillis = LYRIC_FOCUS_SCROLL_DURATION_MS,
                easing = FastOutSlowInEasing,
            ),
        )
    }
}

private fun SyncedLyrics.positionForStableLineFocus(time: Int): Int {
    if (lines.isEmpty()) return time
    val index = findLastStartedLineIndex(time)
    if (index < 0) return time

    val line = lines[index]
    if (time < line.end) return time

    return (line.end - 1).coerceAtLeast(line.start)
}

private fun SyncedLyrics.findLastStartedLineIndex(time: Int): Int {
    var low = 0
    var high = lines.lastIndex
    var result = -1

    while (low <= high) {
        val mid = low + (high - low) / 2
        if (lines[mid].start <= time) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }

    return result
}

private fun buildSyncedLyrics(
    lines: List<LyricLine>,
    isWordSynced: Boolean,
    isDuetEnabled: Boolean = true
): SyncedLyrics {
    if (lines.isEmpty()) return SyncedLyrics(emptyList())
    val resultLines = mutableListOf<ISyncedLine>()

    lines.forEachIndexed { index, line ->
        if (line.startTime < 0L) return@forEachIndexed
        if (line.isInstrumental) return@forEachIndexed
        if (line.text.isBlank() && line.words.isEmpty()) return@forEachIndexed

        val effectiveSinger = if (isDuetEnabled) {
            line.singer.takeIf { it != LyricSinger.DEFAULT } ?: when (line.agent?.trim()?.lowercase()) {
                "v2", "singer2", "2" -> LyricSinger.SINGER_2
                "v1", "singer1", "1" -> LyricSinger.SINGER_1
                "both", "group", "all", "v1000", "v2000", "3", "v3" -> LyricSinger.BOTH
                else -> LyricSinger.DEFAULT
            }
        } else {
            LyricSinger.DEFAULT
        }

        val alignment = when (effectiveSinger) {
            LyricSinger.SINGER_2 -> KaraokeAlignment.End
            else -> KaraokeAlignment.Start
        }

        if (isWordSynced && line.words.isNotEmpty()) {
            val mainWords = line.words.filter { !it.isBackground }
            val bgWords = line.words.filter { it.isBackground }

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else line.words
            val formattedMainWords = formatLyricWordContents(line.text, wordsForMain)
            val mainSyllables = wordsForMain.mapIndexed { idx, word ->
                val content = formattedMainWords.getOrElse(idx) { LyricsUtils.decodeHtmlEntities(word.word) }
                KaraokeSyllable(
                    content = content,
                    start = word.startTime.toInt(),
                    end = word.endTime.toInt().coerceAtLeast(word.startTime.toInt() + MIN_KARAOKE_SYLLABLE_DURATION_MS),
                    phonetic = null
                )
            }

            val lineStart = line.startTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            val lineEnd = if (line.durationMs > 0L) {
                (line.startTime + line.durationMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } else if (line.endTime > line.startTime) {
                line.endTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } else {
                mainSyllables.maxOfOrNull { it.end } ?: (lineStart + 1000)
            }
            if (lineEnd <= lineStart) return@forEachIndexed

            val accompanimentLines = if (mainWords.isNotEmpty() && bgWords.isNotEmpty()) {
                val formattedBgWords = formatLyricWordContents("", bgWords)
                val bgSyllables = bgWords.mapIndexed { idx, word ->
                    val content = formattedBgWords.getOrElse(idx) { LyricsUtils.decodeHtmlEntities(word.word) }
                    KaraokeSyllable(
                        content = content,
                        start = word.startTime.toInt(),
                        end = word.endTime.toInt().coerceAtLeast(word.startTime.toInt() + MIN_KARAOKE_SYLLABLE_DURATION_MS),
                        phonetic = null
                    )
                }
                val bgStart = bgSyllables.minOf { it.start }
                val bgEnd = bgSyllables.maxOf { it.end }
                if (bgEnd > bgStart) {
                    listOf(
                        KaraokeLine.AccompanimentKaraokeLine(
                            syllables = bgSyllables,
                            translation = null,
                            alignment = alignment,
                            start = bgStart,
                            end = bgEnd,
                            phonetic = null
                        )
                    )
                } else null
            } else null

            resultLines.add(
                KaraokeLine.MainKaraokeLine(
                    syllables = mainSyllables,
                    translation = line.translation?.let(LyricsUtils::decodeHtmlEntities),
                    alignment = alignment,
                    start = lineStart,
                    end = lineEnd,
                    phonetic = line.romanization?.let(LyricsUtils::decodeHtmlEntities),
                    accompanimentLines = accompanimentLines
                )
            )
        } else {
            val nextLine = lines.getOrNull(index + 1)
            val lineEnd = if (line.endTime > line.startTime) {
                line.endTime.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } else if (line.durationMs > 0L) {
                (line.startTime + line.durationMs).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            } else if (nextLine != null && nextLine.startTime > line.startTime) {
                val gap = nextLine.startTime - line.startTime
                if (gap > 3000L) {
                    minOf((nextLine.startTime - 1L).toInt(), (line.startTime + 4000L).toInt())
                        .coerceAtLeast(line.startTime.toInt() + 1)
                } else {
                    (nextLine.startTime - 1L).coerceAtLeast(line.startTime + 1L).toInt()
                }
            } else {
                (line.startTime + 4000L).toInt()
            }

            val cleanLineText = LyricsUtils.decodeHtmlEntities(line.text)
            val cleanTranslation = line.translation?.let(LyricsUtils::decodeHtmlEntities)
            val cleanRomanization = line.romanization?.let(LyricsUtils::decodeHtmlEntities)

            if (cleanRomanization != null) {
                val syllables = buildWrappingKaraokeSyllables(
                    content = cleanLineText,
                    romanizedText = cleanRomanization,
                    start = line.startTime.toInt(),
                    end = lineEnd
                )
                resultLines.add(
                    KaraokeLine.MainKaraokeLine(
                        syllables = syllables,
                        translation = cleanTranslation,
                        alignment = alignment,
                        start = line.startTime.toInt(),
                        end = lineEnd
                    )
                )
            } else if (alignment == KaraokeAlignment.End) {
                // Mocharealm's KaraokeLyricsView only supports right-aligning KaraokeLine (not SyncedLine).
                // For duet line-synced lines sung by singer 2, wrap into a single-syllable KaraokeLine.
                val syllables = listOf(
                    KaraokeSyllable(
                        content = cleanLineText,
                        start = line.startTime.toInt(),
                        end = lineEnd,
                        phonetic = null
                    )
                )
                resultLines.add(
                    KaraokeLine.MainKaraokeLine(
                        syllables = syllables,
                        translation = cleanTranslation,
                        alignment = KaraokeAlignment.End,
                        start = line.startTime.toInt(),
                        end = lineEnd
                    )
                )
            } else {
                resultLines.add(
                    SyncedLine(
                        content = cleanLineText,
                        translation = cleanTranslation,
                        start = line.startTime.toInt(),
                        end = lineEnd
                    )
                )
            }
        }
    }
    return SyncedLyrics(lines = resultLines)
}

private fun buildWrappingKaraokeSyllables(
    content: String,
    romanizedText: String,
    start: Int,
    end: Int,
): List<KaraokeSyllable> {
    val contentUnits = content.toLyricsWrappingUnits().ifEmpty { listOf(content) }
    val phoneticWords = romanizedText.split(Regex("\\s+")).filter(String::isNotEmpty)
    val phoneticAnchorIndices = contentUnits.indices.filter { index ->
        contentUnits[index].any(Char::isLetterOrDigit)
    }
    val phoneticsByUnit = MutableList<String?>(contentUnits.size) { null }

    if (phoneticAnchorIndices.isNotEmpty()) {
        phoneticWords.forEachIndexed { wordIndex, word ->
            val anchorIndex = wordIndex * phoneticAnchorIndices.size / phoneticWords.size
            val unitIndex = phoneticAnchorIndices[anchorIndex]
            phoneticsByUnit[unitIndex] = listOfNotNull(phoneticsByUnit[unitIndex], word).joinToString(" ")
        }
    }

    val duration = (end - start).coerceAtLeast(contentUnits.size)
    return contentUnits.mapIndexed { index, unit ->
        val unitStart = start + (duration.toLong() * index / contentUnits.size).toInt()
        val unitEnd = start + (duration.toLong() * (index + 1) / contentUnits.size).toInt()
        KaraokeSyllable(
            content = unit,
            start = unitStart,
            end = unitEnd.coerceAtLeast(unitStart + MIN_KARAOKE_SYLLABLE_DURATION_MS),
            phonetic = phoneticsByUnit[index],
        )
    }
}
