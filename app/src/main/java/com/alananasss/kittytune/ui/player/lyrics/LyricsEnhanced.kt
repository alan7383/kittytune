package com.alananasss.kittytune.ui.player.lyrics

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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.theme.LyricsFontFamily
import com.alananasss.kittytune.ui.theme.rememberLyricsFontFamily
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.alananasss.kittytune.ui.player.lyrics.accompanist.KaraokeLyricsView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

private const val LRC_LEAD_MS = 300L
private const val WORD_SYNC_LEAD_MS = 0L
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

    val lyricsSessionKey = remember(viewModel.uiCurrentTrack?.id, rawLines.size) {
        "${viewModel.uiCurrentTrack?.id ?: 0L}_${rawLines.size}"
    }

    val isDuetEnabled = viewModel.isDuetActiveForTrack(viewModel.uiCurrentTrack)
    val userAlignment = viewModel.lyricsAlignment
    val splitBacking = viewModel.isSplitBackingVocalsEnabled
    val revealWords = viewModel.isRevealWordsEnabled
    val syncedLyrics = remember(
        viewModel.lyricsRevision,
        rawLines.size,
        isWordSyncedFormat,
        isDuetEnabled,
        userAlignment,
        showTranslations,
        showPhonetics,
        splitBacking,
        revealWords
    ) {
        buildSyncedLyrics(rawLines, isWordSyncedFormat, isDuetEnabled, userAlignment, splitBacking, revealWords)
    }

    val leadMs = if (isWordSyncedFormat) WORD_SYNC_LEAD_MS else LRC_LEAD_MS
    val latestLyricsSyncOffset = rememberUpdatedState(viewModel.lyricsOffset)
    val latestLeadMs = rememberUpdatedState(leadMs)
    val latestPlaybackSpeed = rememberUpdatedState(viewModel.effectsState.speed)

    val playbackPositionMs = remember(viewModel.uiCurrentTrack?.id) {
        mutableLongStateOf(MusicManager.player.currentPosition.coerceAtLeast(0L))
    }
    val listState = key(lyricsSessionKey) { rememberLazyListState() }

    LaunchedEffect(lyricsSessionKey) {
        playbackPositionMs.longValue = MusicManager.player.currentPosition.coerceAtLeast(0L)
    }

    // High-precision smooth frame interpolation loop with PLL drift tracking
    LaunchedEffect(viewModel.uiCurrentTrack?.id) {
        var smoothPositionMs = MusicManager.player.currentPosition.coerceAtLeast(0L).toDouble()
        var lastOutputPositionMs = smoothPositionMs.toLong()
        var lastFrameNanos = 0L

        while (isActive) {
            val isSliderActive = viewModel.isScrubbing
            val rawPosition = if (isSliderActive) {
                viewModel.uiCurrentPosition
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
    BoxWithConstraints(
        contentAlignment = Alignment.TopCenter,
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 12.dp)
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
                key(
                    lyricsSessionKey,
                    syncedLyrics,
                    showTranslations,
                    showPhonetics,
                    userAlignment,
                    viewModel.lyricsActiveScale,
                    viewModel.lyricsLineSpacing,
                    viewModel.lyricsLrcBounceEnabled
                ) {
                    KaraokeLyricsView(
                        listState = listState,
                        lyrics = syncedLyrics,
                        userAlignment = userAlignment,
                        currentPosition = playbackSyncPosition,
                        onLineClicked = { line ->
                            if (lyricsClick && isSynced && line.start >= 0) {
                                val target = line.start.toLong() - viewModel.lyricsOffsetAtLyricTime(line.start.toLong())
                                if (viewModel.uiDuration > 0L && target >= viewModel.uiDuration) return@KaraokeLyricsView
                                val finalTarget = target.coerceAtLeast(0L)
                                playbackPositionMs.longValue = finalTarget
                                onLineClick?.invoke(finalTarget) ?: viewModel.seekTo(finalTarget)
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
                        isScrubbing = viewModel.isScrubbing,
                        lrcBounceEnabled = viewModel.lyricsLrcBounceEnabled,
                        bounceFactor = viewModel.lyricsBounceFactor,
                        lineSpacing = if (viewModel.lyricsLineSpacing > 0f) viewModel.lyricsLineSpacing.dp else (if (isCompact) 0.dp else (effectiveFontSize * 0.22f).dp),
                        activeScale = viewModel.lyricsActiveScale,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}


internal fun buildSyncedLyrics(
    lines: List<LyricLine>,
    isWordSynced: Boolean,
    isDuetEnabled: Boolean = true,
    userAlignment: LyricsAlignment = LyricsAlignment.LEFT,
    /** Backing vocals in round brackets go on a line of their own; see [splitBackingVocals]. */
    splitBacking: Boolean = false,
    /** Line-timed lines light up word by word as they start; see [revealSyllables]. */
    revealWords: Boolean = false
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

        val alignment = if (isDuetEnabled && effectiveSinger != LyricSinger.DEFAULT) {
            when (effectiveSinger) {
                LyricSinger.SINGER_2 -> KaraokeAlignment.End
                LyricSinger.SINGER_1 -> KaraokeAlignment.Start
                LyricSinger.BOTH -> KaraokeAlignment.Unspecified
                else -> when (userAlignment) {
                    LyricsAlignment.RIGHT -> KaraokeAlignment.End
                    LyricsAlignment.CENTER -> KaraokeAlignment.Unspecified
                    LyricsAlignment.LEFT -> KaraokeAlignment.Start
                }
            }
        } else {
            when (userAlignment) {
                LyricsAlignment.RIGHT -> KaraokeAlignment.End
                LyricsAlignment.CENTER -> KaraokeAlignment.Unspecified
                LyricsAlignment.LEFT -> KaraokeAlignment.Start
            }
        }

        if (isWordSynced && line.words.isNotEmpty()) {
            val markedBackground = line.words.filter { it.isBackground }
            // Backing vocals the source wrote in brackets rather than marked, when the reader wants them apart.
            val (ownWords, bracketGroups) = if (splitBacking && markedBackground.isEmpty()) {
                splitBackingWords(line.words)
            } else {
                line.words to emptyList()
            }
            val mainWords = ownWords.filter { !it.isBackground }
            val bgWords = markedBackground.ifEmpty { bracketGroups.flatten() }
            val mainText = if (bracketGroups.isNotEmpty()) splitBackingVocals(line.text).first else line.text

            val wordsForMain = if (mainWords.isNotEmpty()) mainWords else line.words
            val formattedMainWords = formatLyricWordContents(mainText, wordsForMain)
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
                val formattedBgWords = if (bracketGroups.isNotEmpty()) {
                    backingGroupContents(bracketGroups)
                } else {
                    formatLyricWordContents("", bgWords)
                }
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
                ?.takeIf { !it.trim().equals(cleanLineText.trim(), ignoreCase = true) }
            val cleanRomanization = line.romanization?.let(LyricsUtils::decodeHtmlEntities)
                ?.takeIf { !it.trim().equals(cleanLineText.trim(), ignoreCase = true) }

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
            } else {
                val lineStart = line.startTime.toInt()
                val isRightDuet = alignment == KaraokeAlignment.End
                val (mainText, backing) = if (splitBacking) splitBackingVocals(cleanLineText) else cleanLineText to null
                if (!isRightDuet && backing == null && !revealWords) {
                    resultLines.add(
                        SyncedLine(
                            content = cleanLineText,
                            translation = cleanTranslation,
                            start = lineStart,
                            end = lineEnd
                        )
                    )
                } else {
                    val syllables = when {
                        revealWords -> revealSyllables(mainText, lineStart, lineEnd)
                        // Word by word, so the line can wrap. As one syllable holding the whole line it could not
                        // break anywhere, and a long right-aligned line ran off the left edge (issue #66).
                        isRightDuet && backing == null -> buildWrappingKaraokeSyllables(
                            content = cleanLineText,
                            romanizedText = "",
                            start = lineStart,
                            end = lineEnd,
                        )
                        else -> instantSyllables(mainText, lineStart, lineEnd)
                    }
                    // Sung over the same line, so it shows while the line does, smaller and under it.
                    val backingLine = backing?.let { text ->
                        val backingStart = if (revealWords) {
                            (lineStart + revealDurationMs(mainText)).coerceAtMost(lineEnd - 1)
                        } else {
                            lineStart
                        }
                        KaraokeLine.AccompanimentKaraokeLine(
                            syllables = if (revealWords) revealSyllables(text, backingStart, lineEnd)
                            else instantSyllables(text, lineStart, lineEnd),
                            translation = null,
                            alignment = alignment,
                            start = lineStart,
                            end = lineEnd,
                            phonetic = null,
                        )
                    }
                    resultLines.add(
                        KaraokeLine.MainKaraokeLine(
                            syllables = syllables,
                            translation = cleanTranslation,
                            alignment = alignment,
                            start = lineStart,
                            end = lineEnd,
                            phonetic = null,
                            accompanimentLines = backingLine?.let(::listOf),
                        )
                    )
                }
            }
        }
    }
    return SyncedLyrics(lines = resultLines)
}

/**
 * What each backing word shows: the word and a space, and a comma after the last word of a group when another
 * group follows, so "(oh no)" and "(yeah)" read as "oh no, yeah".
 */
private fun backingGroupContents(groups: List<List<LyricWord>>): List<String> =
    groups.flatMapIndexed { groupIndex, group ->
        group.mapIndexed { wordIndex, word ->
            val text = LyricsUtils.decodeHtmlEntities(word.text)
            when {
                wordIndex < group.lastIndex -> "$text "
                groupIndex < groups.lastIndex -> text + BACKING_SEPARATOR
                else -> text
            }
        }
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
