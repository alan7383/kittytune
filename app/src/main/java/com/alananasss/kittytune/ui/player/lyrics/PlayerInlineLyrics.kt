package com.alananasss.kittytune.ui.player.lyrics

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.LyricsFont
import com.alananasss.kittytune.data.local.LyricsUiStyle
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.theme.rememberLyricsFontFamily
import kotlinx.coroutines.isActive

private const val THROTTLED_UPDATE_INTERVAL_MS = 50L

@Composable
fun PlayerInlineLyrics(
    viewModel: PlayerViewModel,
    textColor: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    onClick: (() -> Unit)? = null
) {
    PlayerInlineLyrics(
        lyricsLines = viewModel.lyricsLines,
        positionMs = viewModel.currentPosition + viewModel.lyricsOffset,
        isPlaying = viewModel.isPlaying,
        textColor = textColor,
        modifier = modifier,
        lyricsFont = viewModel.lyricsFont,
        lyricsFontSize = viewModel.lyricsFontSize,
        lyricsAlignment = viewModel.lyricsAlignment,
        isAppleMusicEffect = viewModel.isAppleMusicEffectEnabled || viewModel.lyricsUiStyle == LyricsUiStyle.ENHANCED,
        isWordSyncEnabled = viewModel.isWordSyncEnabled,
        isTranslationEnabled = viewModel.isLyricsTranslationEnabled,
        glowFactor = viewModel.lyricsGlowFactor,
        maxLines = maxLines,
        onClick = onClick
    )
}

@Composable
fun PlayerInlineLyrics(
    lyricsLines: List<LyricLine>,
    positionMs: Long,
    isPlaying: Boolean,
    textColor: Color,
    modifier: Modifier = Modifier,
    lyricsFont: LyricsFont = LyricsFont.APP_DEFAULT,
    lyricsFontSize: Float = 22f,
    lyricsAlignment: LyricsAlignment = LyricsAlignment.LEFT,
    isAppleMusicEffect: Boolean = false,
    isWordSyncEnabled: Boolean = true,
    isTranslationEnabled: Boolean = true,
    glowFactor: Float = 1f,
    maxLines: Int = 2,
    onClick: (() -> Unit)? = null
) {
    if (lyricsLines.isEmpty()) return

    var smoothPositionMs by remember { mutableLongStateOf(positionMs) }
    var positionAnchorMs by remember { mutableLongStateOf(positionMs) }
    var timeAnchorMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }

    LaunchedEffect(positionMs) {
        positionAnchorMs = positionMs
        timeAnchorMs = SystemClock.elapsedRealtime()
        smoothPositionMs = positionMs
    }

    var throttledPositionMs by remember { mutableLongStateOf(positionMs) }

    LaunchedEffect(isPlaying, lyricsLines.isNotEmpty()) {
        if (!isPlaying || lyricsLines.isEmpty()) {
            smoothPositionMs = positionAnchorMs
            throttledPositionMs = positionAnchorMs
            return@LaunchedEffect
        }

        var lastThrottledUpdateMs = 0L
        while (isActive) {
            withFrameMillis { frameTimeMillis ->
                smoothPositionMs = positionAnchorMs + (SystemClock.elapsedRealtime() - timeAnchorMs)
                if (frameTimeMillis - lastThrottledUpdateMs >= THROTTLED_UPDATE_INTERVAL_MS) {
                    lastThrottledUpdateMs = frameTimeMillis
                    throttledPositionMs = smoothPositionMs
                }
            }
        }
    }

    val currentLine = remember(lyricsLines, throttledPositionMs) {
        val adjustedPosition = throttledPositionMs + 80L
        val validLines = lyricsLines.filter { it.text.isNotBlank() && !it.isInstrumental }
        if (validLines.isEmpty()) null
        else {
            val idx = validLines.indexOfLast { it.startTime <= adjustedPosition }
            if (idx >= 0) validLines[idx] else validLines.firstOrNull()
        }
    }

    val lyricsFontFamily = rememberLyricsFontFamily(lyricsFont)
    val effectiveFontSize = (lyricsFontSize * 0.82f).coerceIn(16f, 26f).sp
    val effectiveLineHeight = (effectiveFontSize.value * 1.35f).sp

    val textAlign = when (lyricsAlignment) {
        LyricsAlignment.LEFT -> TextAlign.Start
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.RIGHT -> TextAlign.End
    }
    val hzAlignment = when (lyricsAlignment) {
        LyricsAlignment.LEFT -> Alignment.Start
        LyricsAlignment.CENTER -> Alignment.CenterHorizontally
        LyricsAlignment.RIGHT -> Alignment.End
    }
    val containerAlignment = when (lyricsAlignment) {
        LyricsAlignment.LEFT -> Alignment.CenterStart
        LyricsAlignment.CENTER -> Alignment.Center
        LyricsAlignment.RIGHT -> Alignment.CenterEnd
    }

    val baseStyle = TextStyle(
        fontFamily = lyricsFontFamily,
        fontWeight = FontWeight.ExtraBold,
        fontSize = effectiveFontSize,
        lineHeight = effectiveLineHeight,
        textAlign = textAlign
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { onClick() }
                } else Modifier
            ),
        contentAlignment = containerAlignment
    ) {
        AnimatedContent(
            targetState = currentLine,
            transitionSpec = {
                val isForward = (targetState?.startTime ?: 0L) >= (initialState?.startTime ?: 0L)
                if (isForward) {
                    (
                        slideInVertically(
                            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                            initialOffsetY = { it }
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 260, delayMillis = 40, easing = FastOutSlowInEasing)
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                        )
                    ).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                            targetOffsetY = { -it }
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                        ) + scaleOut(
                            targetScale = 1.02f,
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        )
                    )
                } else {
                    (
                        slideInVertically(
                            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
                            initialOffsetY = { -it }
                        ) + fadeIn(
                            animationSpec = tween(durationMillis = 260, delayMillis = 40, easing = FastOutSlowInEasing)
                        ) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing)
                        )
                    ).togetherWith(
                        slideOutVertically(
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
                            targetOffsetY = { it }
                        ) + fadeOut(
                            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                        ) + scaleOut(
                            targetScale = 1.02f,
                            animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                        )
                    )
                }
            },
            contentAlignment = containerAlignment,
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            label = "PlayerInlineLyricsLine"
        ) { line ->
            if (line != null) {
                val rawCleanText = remember(line.text) { LyricsUtils.decodeHtmlEntities(line.text) }
                val displayWords = remember(line.words, isWordSyncEnabled) {
                    if (isWordSyncEnabled && line.words.isNotEmpty()) {
                        line.words.map { it.copy(word = LyricsUtils.decodeHtmlEntities(it.word)) }
                    } else emptyList()
                }
                val cleanTranslation = remember(line.translation) {
                    line.translation?.let { LyricsUtils.decodeHtmlEntities(it) }
                }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = hzAlignment
                ) {
                    if (isAppleMusicEffect) {
                        SmoothAppleMusicInlineLyricLine(
                            line = line,
                            rawCleanText = rawCleanText,
                            displayWords = displayWords,
                            currentPosMs = smoothPositionMs,
                            textColor = textColor,
                            style = baseStyle,
                            textAlign = textAlign,
                            glowFactor = glowFactor,
                            maxLines = maxLines
                        )
                    } else {
                        val annotatedText = remember(line, throttledPositionMs, textColor, isWordSyncEnabled) {
                            buildInlineLyricsAnnotatedString(
                                rawCleanText = rawCleanText,
                                displayWords = displayWords,
                                positionMs = throttledPositionMs,
                                textColor = textColor,
                                lineStartMs = line.startTime,
                                lineEndMs = line.endTime,
                                glowFactor = glowFactor
                            )
                        }
                        Text(
                            text = annotatedText,
                            style = baseStyle.copy(color = textColor),
                            maxLines = maxLines,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isTranslationEnabled && !cleanTranslation.isNullOrBlank()) {
                        Text(
                            text = cleanTranslation,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = textColor.copy(alpha = 0.7f),
                                fontSize = (effectiveFontSize.value * 0.65f).coerceIn(12f, 15f).sp,
                                fontFamily = lyricsFontFamily,
                                textAlign = textAlign
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmoothAppleMusicInlineLyricLine(
    line: LyricLine,
    rawCleanText: String,
    displayWords: List<LyricWord>,
    currentPosMs: Long,
    textColor: Color,
    style: TextStyle,
    textAlign: TextAlign,
    glowFactor: Float,
    maxLines: Int
) {
    if (displayWords.isEmpty()) {
        SmoothAppleMusicLineSyncedLyricLine(
            line = line,
            rawCleanText = rawCleanText,
            currentPosMs = currentPosMs,
            textColor = textColor,
            style = style,
            textAlign = textAlign,
            glowFactor = glowFactor,
            maxLines = maxLines
        )
        return
    }

    val formattedWords = remember(displayWords, rawCleanText) {
        formatLyricWordContents(rawCleanText, displayWords)
    }

    val reconstructedText = remember(formattedWords, rawCleanText) {
        if (formattedWords.isEmpty()) rawCleanText
        else formattedWords.joinToString("")
    }

    val wordRanges = remember(formattedWords) {
        val ranges = mutableListOf<Pair<Int, Int>>()
        var currentLen = 0
        formattedWords.forEach { w ->
            val start = currentLen
            val end = start + w.length
            ranges.add(start to end)
            currentLen = end
        }
        ranges
    }

    var textLayoutResult by remember(reconstructedText) { mutableStateOf<TextLayoutResult?>(null) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Base dimmed text (unsung)
        Text(
            text = reconstructedText,
            style = style,
            color = textColor.copy(alpha = 0.38f),
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
            onTextLayout = { textLayoutResult = it }
        )

        // Active highlighted text smoothly clipped to sung characters with Apple Music glow
        val shadowBlur = (14f * glowFactor).coerceIn(4f, 28f)
        Text(
            text = reconstructedText,
            style = style.copy(
                shadow = Shadow(
                    color = textColor.copy(alpha = (0.45f * glowFactor).coerceIn(0.1f, 0.8f)),
                    offset = Offset.Zero,
                    blurRadius = shadowBlur
                )
            ),
            color = textColor,
            textAlign = textAlign,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .drawWithContent {
                    val layout = textLayoutResult ?: return@drawWithContent
                    val path = Path()
                    val textLen = reconstructedText.length
                    if (textLen == 0) return@drawWithContent
                    val safeTextLength = (textLen - 1).coerceAtLeast(0)

                    for (i in displayWords.indices) {
                        val w = displayWords[i]
                        val range = wordRanges.getOrNull(i) ?: continue
                        if (range.first >= range.second) continue
                        val fillEnd = if (i < displayWords.lastIndex && i + 1 < wordRanges.size) wordRanges[i + 1].first else range.second

                        if (currentPosMs >= w.endTime) {
                            for (c in range.first until fillEnd) {
                                path.addRect(layout.getBoundingBox(c.coerceIn(0, safeTextLength)))
                            }
                        } else if (currentPosMs >= w.startTime) {
                            val duration = (w.endTime - w.startTime).coerceAtLeast(1L)
                            val progress = ((currentPosMs - w.startTime).toFloat() / duration).coerceIn(0f, 1f)
                            val smoothProgress = progress * progress * (3f - 2f * progress)
                            val exactProgressChars = smoothProgress * (range.second - range.first)
                            val fullySungChars = exactProgressChars.toInt()
                            val charFraction = exactProgressChars - fullySungChars

                            for (c in range.first until range.first + fullySungChars) {
                                path.addRect(layout.getBoundingBox(c.coerceIn(0, safeTextLength)))
                            }
                            val partialCharIdx = range.first + fullySungChars
                            if (partialCharIdx < range.second) {
                                val cBbox = layout.getBoundingBox(partialCharIdx.coerceIn(0, safeTextLength))
                                val cX = cBbox.left + (cBbox.right - cBbox.left) * charFraction
                                path.addRect(Rect(cBbox.left, cBbox.top, cX, cBbox.bottom))
                            }
                        }
                    }

                    clipPath(path) {
                        this@drawWithContent.drawContent()
                    }
                }
        )
    }
}

@Composable
private fun SmoothAppleMusicLineSyncedLyricLine(
    line: LyricLine,
    rawCleanText: String,
    currentPosMs: Long,
    textColor: Color,
    style: TextStyle,
    textAlign: TextAlign,
    glowFactor: Float,
    maxLines: Int
) {
    val isActive = currentPosMs >= line.startTime && (line.endTime <= line.startTime || currentPosMs < line.endTime)
    val targetAlpha = if (isActive) 1.0f else 0.40f
    val targetGlowAlpha = if (isActive) (0.50f * glowFactor).coerceIn(0.12f, 0.85f) else 0f
    val targetScale = if (isActive) 1.0f else 0.98f

    val lineAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "inlineLineAlpha"
    )
    val lineGlowAlpha by animateFloatAsState(
        targetValue = targetGlowAlpha,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "inlineLineGlowAlpha"
    )
    val lineScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
        label = "inlineLineScale"
    )

    val shadowBlur = (16f * glowFactor).coerceIn(4f, 28f)
    val shadow = if (lineGlowAlpha > 0.01f) {
        Shadow(
            color = textColor.copy(alpha = lineGlowAlpha),
            offset = Offset.Zero,
            blurRadius = shadowBlur
        )
    } else null

    Text(
        text = rawCleanText,
        style = style.copy(
            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
            shadow = shadow
        ),
        color = textColor.copy(alpha = lineAlpha),
        textAlign = textAlign,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = lineScale
                scaleY = lineScale
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (textAlign) {
                        TextAlign.Center -> 0.5f
                        TextAlign.End -> 1.0f
                        else -> 0.0f
                    },
                    pivotFractionY = 0.5f
                )
            }
    )
}

private fun buildInlineLyricsAnnotatedString(
    rawCleanText: String,
    displayWords: List<LyricWord>,
    positionMs: Long,
    textColor: Color,
    lineStartMs: Long = 0L,
    lineEndMs: Long = 0L,
    glowFactor: Float = 1f
): AnnotatedString = buildAnnotatedString {
    if (displayWords.isEmpty()) {
        val isActive = positionMs >= lineStartMs && (lineEndMs <= lineStartMs || positionMs < lineEndMs)
        val wordColor = if (isActive) textColor else textColor.copy(alpha = 0.38f)
        val wordWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold
        val wordShadow = if (isActive) {
            Shadow(
                color = textColor.copy(alpha = (0.45f * glowFactor).coerceIn(0.1f, 0.8f)),
                offset = Offset.Zero,
                blurRadius = (12f * glowFactor).coerceIn(4f, 24f)
            )
        } else null
        withStyle(SpanStyle(color = wordColor, fontWeight = wordWeight, shadow = wordShadow)) {
            append(rawCleanText)
        }
        return@buildAnnotatedString
    }

    val formattedWords = formatLyricWordContents(rawCleanText, displayWords)

    displayWords.forEachIndexed { index, word ->
        val wordStartMs = word.startTime
        val wordEndMs = word.endTime.takeIf { it > wordStartMs } ?: (wordStartMs + 380L)
        val isActive = positionMs in wordStartMs..wordEndMs
        val hasPassed = positionMs > wordEndMs
        val rawProgress = when {
            hasPassed -> 1f
            isActive -> ((positionMs - wordStartMs).toFloat() / (wordEndMs - wordStartMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
            else -> 0f
        }
        val smoothProgress = rawProgress * rawProgress * (3f - 2f * rawProgress)
        val wordColor = when {
            hasPassed -> textColor
            isActive -> textColor.copy(alpha = 0.65f + (0.35f * smoothProgress))
            else -> textColor.copy(alpha = 0.38f)
        }
        val wordWeight = when {
            isActive -> FontWeight.Black
            hasPassed -> FontWeight.ExtraBold
            else -> FontWeight.Bold
        }
        val wordShadow = when {
            isActive -> Shadow(
                color = textColor.copy(alpha = (0.28f + (0.22f * smoothProgress)) * glowFactor),
                offset = Offset.Zero,
                blurRadius = (8f + (8f * smoothProgress)) * glowFactor
            )
            hasPassed -> Shadow(
                color = textColor.copy(alpha = 0.12f * glowFactor),
                offset = Offset.Zero,
                blurRadius = 4f * glowFactor
            )
            else -> null
        }

        val wordText = formattedWords.getOrElse(index) { word.word }
        withStyle(SpanStyle(color = wordColor, fontWeight = wordWeight, shadow = wordShadow)) {
            append(wordText)
        }
    }
}
