package com.alananasss.kittytune.ui.player.lyrics.accompanist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.ui.utils.LayerPaint
import com.mocharealm.accompanist.lyrics.ui.utils.easing.Bounce
import com.mocharealm.accompanist.lyrics.ui.utils.easing.DipAndRise
import com.mocharealm.accompanist.lyrics.ui.utils.easing.Swell
import com.mocharealm.accompanist.lyrics.ui.utils.isPunctuation
import com.mocharealm.accompanist.lyrics.ui.utils.isRtl
import kotlin.math.roundToInt

private const val FixedSimpleAnimationDurationMs = 700f
private const val MaxSimpleFloatOffsetY = 4f
private val SimpleFloatEasing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)

/**
 * Creates a horizontal gradient brush that represents the karaoke progress.
 * The gradient moves from inactive color to active color based on the current time.
 *
 * The soft edge sits on the playhead and stays inside the word being sung: it narrows to nothing at the
 * word's start and end, and between words the fill stops dead at the end of the last one.
 */
private fun createLineGradientBrush(
    rowData: RowRenderData,
    currentTimeMs: Int,
    isRtl: Boolean,
    baseColor: Color = Color.White
): Brush {
    val activeColor = baseColor
    val inactiveColor = baseColor.copy(alpha = 0.2f)

    val lineLayout = rowData.rowLayouts
    val totalMinX = rowData.totalMinX
    val totalMaxX = rowData.totalMaxX
    val totalWidth = rowData.totalWidth

    if (totalWidth <= 0f) {
        val isFinished = currentTimeMs >= lineLayout.last().syllable.end
        val color = if (isFinished) activeColor else inactiveColor
        return SolidColor(color)
    }

    val firstSyllableStart = rowData.firstSyllableStart
    val lastSyllableEnd = rowData.lastSyllableEnd

    val isLineSynced = lineLayout.all { it.syllable.start == firstSyllableStart && it.syllable.end == lastSyllableEnd }
    if (isLineSynced) {
        val color = if (currentTimeMs >= firstSyllableStart) activeColor else inactiveColor
        return SolidColor(color)
    }

    if (currentTimeMs <= firstSyllableStart) return SolidColor(inactiveColor)
    if (currentTimeMs >= lastSyllableEnd) return SolidColor(activeColor)

    val activeSyllableLayout = lineLayout.find { currentTimeMs in it.syllable.start until it.syllable.end }
    val playheadX: Float
    val edgeWidth: Float
    if (activeSyllableLayout != null) {
        val progress = activeSyllableLayout.syllable.progress(currentTimeMs)
        val sung = activeSyllableLayout.width * progress
        val left = activeSyllableLayout.width - sung
        playheadX = if (isRtl) {
            activeSyllableLayout.position.x + left
        } else {
            activeSyllableLayout.position.x + sung
        }
        edgeWidth = minOf(SOFT_EDGE_PX, sung * 2f, left * 2f)
    } else {
        val lastFinished = lineLayout.lastOrNull { currentTimeMs >= it.syllable.end }
        playheadX = if (isRtl) {
            lastFinished?.position?.x ?: totalMaxX
        } else {
            lastFinished?.let { it.position.x + it.width } ?: totalMinX
        }
        edgeWidth = 0f
    }

    val at = ((playheadX - totalMinX) / totalWidth).coerceIn(0f, 1f)
    val half = edgeWidth / 2f / totalWidth
    val edgeStart = (at - half).coerceIn(0f, 1f)
    val edgeEnd = (at + half).coerceIn(0f, 1f)

    val colorStops = if (isRtl) {
        arrayOf(
            0.0f to inactiveColor,
            edgeStart to inactiveColor,
            edgeEnd to activeColor,
            1.0f to activeColor
        )
    } else {
        arrayOf(
            0.0f to activeColor,
            edgeStart to activeColor,
            edgeEnd to inactiveColor,
            1.0f to inactiveColor
        )
    }

    return Brush.horizontalGradient(
        colorStops = colorStops,
        startX = totalMinX,
        endX = totalMaxX
    )
}

private const val SOFT_EDGE_PX = 100f

// The mini-style top-to-bottom sweep of [SyncedLineText] drawn over a karaoke line whose syllables all
// light at once - a line-timed line, including one that carries its backing vocals split off
// under it (that is the line kind which never filled before: the sweep only knew plain lines).
// What the band has passed is fully bright; what is below it keeps only [KaraokeSweepSpec.dimColor]'s
// share of the alpha; the whole text is bright once the line is over. The glow grows with the
// sweep exactly like the plain line's. The backing vocals under such a line are a separate
// [KaraokeLineText] without a sweep, so they never fill - they stay dim from start to end.
fun DrawScope.drawLyricsLineSweep(
    rowRenderData: List<RowRenderData>,
    currentTimeMs: Int,
    color: Color,
    blendMode: BlendMode,
    sweep: KaraokeSweepSpec,
    showPhonetic: Boolean = true,
) {
    if (rowRenderData.isEmpty()) return
    val progress = sweep.progress().coerceIn(0f, 1f)
    val textTop = rowRenderData.minOf { row -> row.rowLayouts.minOf { it.position.y } }
    val textBottom = rowRenderData.maxOf { row ->
        row.rowLayouts.maxOf { it.position.y + it.textLayoutResult.size.height.toFloat() }
    }
    val textSpan = (textBottom - textTop).coerceAtLeast(1f)
    val dimRatio = if (sweep.brightColor.alpha > 0f) {
        (sweep.dimColor.alpha / sweep.brightColor.alpha).coerceIn(0f, 1f)
    } else {
        0.45f
    }
    val glow = Shadow(color = sweep.brightColor.copy(alpha = 0.5f), blurRadius = progress * 20f)

    if (progress >= 1f) {
        // The line has finished: fully bright, like SyncedLineText after the band leaves.
        rowRenderData.forEach { row ->
            drawRowText(row.rowLayouts, color, blendMode, false, currentTimeMs, showPhonetic, glow)
        }
        return
    }

    val bandOrigin = textTop + progress * textSpan

    drawIntoCanvas { canvas ->
        canvas.saveLayer(Rect(0f, 0f, size.width, size.height), LayerPaint)
        rowRenderData.forEach { row ->
            drawRowText(row.rowLayouts, color, blendMode, false, currentTimeMs, showPhonetic, glow)
        }
        // Above the band the mask keeps everything (opaque black), below it only dimRatio of
        // the alpha; the 20 px soft edge is the same band SyncedLineText draws.
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Black, Color.Black.copy(alpha = dimRatio)),
                startY = bandOrigin - 10f,
                endY = bandOrigin + 10f
            ),
            blendMode = BlendMode.DstIn
        )
        canvas.restore()
    }
}

fun DrawScope.drawLyricsLine(
    rowRenderData: List<RowRenderData>,
    currentTimeMs: Int,
    color: Color,
    blendMode: BlendMode,
    isRtl: Boolean,
    showDebugRectangles: Boolean = false,
    showPhonetic: Boolean = true
) {
    rowRenderData.forEach { rowData ->
        val rowLayouts = rowData.rowLayouts
        val lastSyllableEnd = rowData.lastSyllableEnd

        if (currentTimeMs >= lastSyllableEnd) {
            drawRowText(
                rowLayouts,
                color,
                blendMode,
                showDebugRectangles,
                currentTimeMs,
                showPhonetic
            )
            return@forEach
        }

        drawIntoCanvas { canvas ->
            val layerBounds = rowData.layerBounds
            canvas.saveLayer(layerBounds, LayerPaint)

            drawRowText(
                rowLayouts,
                color,
                blendMode,
                showDebugRectangles,
                currentTimeMs,
                showPhonetic
            )

            val progressBrush = createLineGradientBrush(rowData, currentTimeMs, isRtl, color)
            drawRect(
                brush = progressBrush,
                topLeft = layerBounds.topLeft,
                size = layerBounds.size,
                blendMode = BlendMode.DstIn
            )
            canvas.restore()
        }
    }
}

private fun DrawScope.drawRowText(
    rowLayouts: List<SyllableLayout>,
    drawColor: Color,
    blendMode: BlendMode,
    showDebugRectangles: Boolean,
    currentTimeMs: Int,
    showPhonetic: Boolean = true,
    glowShadow: Shadow? = null
) {
    rowLayouts.forEachIndexed { index, syllableLayout ->
        val wordAnimInfo = syllableLayout.wordAnimInfo
        val phoneticDrawColor = drawColor.copy(alpha = 0.4f)

        if (wordAnimInfo != null) {
            val fastCharAnimationThresholdMs = 200f
            val awesomeDuration = wordAnimInfo.wordDuration * 0.8f

            val charLayouts = syllableLayout.charLayouts ?: emptyList()
            val charBounds = syllableLayout.charOriginalBounds ?: emptyList()

            val numCharsInWord = wordAnimInfo.wordContent.length
            val earliestStartTime = wordAnimInfo.wordStartTime
            val latestStartTime = wordAnimInfo.wordEndTime - awesomeDuration
            val animationIntensityBase =
                ((wordAnimInfo.wordDuration - fastCharAnimationThresholdMs * numCharsInWord) / 1000)
            val dipAndRise = DipAndRise(dip = (0.5 * animationIntensityBase).coerceIn(0.0, 0.5))
            val swell = Swell((0.1 * animationIntensityBase).coerceIn(0.0, 0.1))

            syllableLayout.syllable.content.forEachIndexed { charIndex, _ ->
                val singleCharLayoutResult =
                    charLayouts.getOrNull(charIndex) ?: return@forEachIndexed
                val charBox = charBounds.getOrNull(charIndex) ?: return@forEachIndexed

                val absoluteCharIndex = syllableLayout.charOffsetInWord + charIndex
                val charRatio =
                    if (numCharsInWord > 1) absoluteCharIndex.toFloat() / (numCharsInWord - 1) else 0.5f
                val awesomeStartTime =
                    (earliestStartTime + (latestStartTime - earliestStartTime) * charRatio).toLong()
                val awesomeProgress =
                    ((currentTimeMs - awesomeStartTime).toFloat() / awesomeDuration).coerceIn(
                        0f, 1f
                    )

                val floatOffset = 4f * dipAndRise.transform(1.0f - awesomeProgress)
                val scale = 1f + swell.transform(awesomeProgress)

                val centeredOffsetX = (charBox.width - singleCharLayoutResult.size.width) / 2f
                val xPos = syllableLayout.position.x + charBox.left + centeredOffsetX
                val baselineCorrection = syllableLayout.firstBaseline - singleCharLayoutResult.firstBaseline
                val yPos = syllableLayout.position.y + baselineCorrection + floatOffset

                val blurRadius = 10f * Bounce.transform(awesomeProgress)
                val shadow = Shadow(
                    color = drawColor.copy(0.4f), offset = Offset(0f, 0f), blurRadius = blurRadius
                )
                withTransform({ scale(scale = scale, pivot = syllableLayout.wordPivot) }) {
                    drawText(
                        textLayoutResult = singleCharLayoutResult,
                        color = drawColor,
                        topLeft = Offset(xPos, yPos),
                        shadow = shadow,
                    )

                    if (showDebugRectangles) {
                        drawRect(
                            color = Color.Red, topLeft = Offset(xPos, yPos), size = Size(
                                singleCharLayoutResult.size.width.toFloat(),
                                singleCharLayoutResult.size.height.toFloat()
                            ), style = Stroke(1f)
                        )
                    }
                }
            }

            if (showPhonetic) {
                syllableLayout.phoneticLayoutResult?.let { phoneticLayout ->
                    val syllableMidIndex =
                        syllableLayout.charOffsetInWord + (syllableLayout.syllable.content.length - 1) / 2f
                    val syllableRatio =
                        if (numCharsInWord > 1) syllableMidIndex / (numCharsInWord - 1) else 0.5f
                    val awesomeStartTime =
                        (earliestStartTime + (latestStartTime - earliestStartTime) * syllableRatio).toLong()
                    val awesomeProgress =
                        ((currentTimeMs - awesomeStartTime).toFloat() / awesomeDuration).coerceIn(
                            0f, 1f
                        )

                    val floatOffset = 4f * dipAndRise.transform(1.0f - awesomeProgress)
                    val scale = 1f + swell.transform(awesomeProgress)

                    val phoneticX = syllableLayout.position.x
                    val phoneticY =
                        syllableLayout.position.y - phoneticLayout.size.height + 4.dp.toPx() + floatOffset

                    withTransform({ scale(scale = scale, pivot = syllableLayout.wordPivot) }) {
                        drawText(
                            textLayoutResult = phoneticLayout,
                            color = phoneticDrawColor,
                            topLeft = Offset(phoneticX, phoneticY)
                        )
                    }
                }
            }
        } else {
            val driverLayout = if (syllableLayout.syllable.content.trim().isPunctuation()) {
                var searchIndex = index - 1
                while (searchIndex >= 0) {
                    val candidate = rowLayouts[searchIndex]
                    if (!candidate.syllable.content.trim().isPunctuation()) {
                        break
                    }
                    searchIndex--
                }
                if (searchIndex < 0) syllableLayout else rowLayouts[searchIndex]
            } else {
                syllableLayout
            }

            val timeSinceStart = currentTimeMs - driverLayout.syllable.start
            val animationProgress =
                (timeSinceStart / FixedSimpleAnimationDurationMs).coerceIn(0f, 1f)

            val floatCurveValue = SimpleFloatEasing.transform(1f - animationProgress)
            val floatOffset = MaxSimpleFloatOffsetY * floatCurveValue

            val finalPosition = syllableLayout.position.copy(
                y = syllableLayout.position.y + floatOffset
            )

            drawText(
                textLayoutResult = syllableLayout.textLayoutResult,
                color = drawColor,
                topLeft = finalPosition,
                shadow = glowShadow,
            )

            if (showPhonetic) {
                syllableLayout.phoneticLayoutResult?.let { phoneticLayout ->
                    val phoneticX = syllableLayout.position.x
                    val phoneticY = finalPosition.y - phoneticLayout.size.height + 4.dp.toPx()
                    drawText(
                        textLayoutResult = phoneticLayout,
                        color = phoneticDrawColor,
                        topLeft = Offset(phoneticX, phoneticY),
                    )
                }
            }

            if (showDebugRectangles) {
                drawRect(
                    color = Color.Green, topLeft = finalPosition, size = Size(
                        syllableLayout.textLayoutResult.size.width.toFloat(),
                        syllableLayout.textLayoutResult.size.height.toFloat()
                    ), style = Stroke(2f)
                )
            }
        }
    }
}

@Composable
fun KaraokeLineText(
    line: KaraokeLine,
    currentTimeProvider: () -> Int,
    modifier: Modifier = Modifier,
    normalLineTextStyle: TextStyle = LocalTextStyle.current,
    accompanimentLineTextStyle: TextStyle = LocalTextStyle.current,
    phoneticTextStyle: TextStyle = LocalTextStyle.current,
    activeColor: Color = Color.White,
    blendMode: BlendMode = BlendMode.SrcOver,
    showDebugRectangles: Boolean = false,
    showTranslation: Boolean = true,
    showPhonetic: Boolean = true,
    precalculatedLayouts: List<SyllableLayout>? = null,
    isDuoView: Boolean = false,
    textMeasurer: TextMeasurer = rememberTextMeasurer(),
    activeScale: Float = 1.0f,
    // Mini-style top-to-bottom sweep fill, for a line-timed line drawn as syllables (all of them light
    // at once - the kind of line a line WITH backing vocals becomes when they are split off).
    // Non-null only while the line is current; null draws the usual per-word gradient fill.
    sweep: KaraokeSweepSpec? = null
) {
    val isLineRtl = remember(line.syllables) { line.syllables.any { it.content.isRtl() } }

    val isCenterAligned = remember(line.alignment) {
        line.alignment == KaraokeAlignment.Unspecified
    }

    val isRightAligned = remember(line.alignment, isLineRtl) {
        if (isCenterAligned) false
        else when (line.alignment) {
            KaraokeAlignment.End -> !isLineRtl
            else -> isLineRtl
        }
    }

    val translationTextAlign = remember(isRightAligned, isCenterAligned) {
        when {
            isCenterAligned -> TextAlign.Center
            isRightAligned -> TextAlign.End
            else -> TextAlign.Start
        }
    }

    val columnHorizontalAlignment = remember(isRightAligned, isCenterAligned) {
        when {
            isCenterAligned -> Alignment.CenterHorizontally
            isRightAligned -> Alignment.End
            else -> Alignment.Start
        }
    }

    val mainLine = line as? KaraokeLine.MainKaraokeLine
    val accompanimentLinesBeforeMain =
        mainLine?.accompanimentLines?.filter { it.start < line.start }.orEmpty()
    val accompanimentLinesAfterMain =
        mainLine?.accompanimentLines?.filter { it.start >= line.start }.orEmpty()

    @Composable
    fun AccompanimentLines(isBefore: Boolean, accompanimentLines: List<KaraokeLine>) {
        accompanimentLines.forEach { bgLine ->
            val isAccompanimentVisible by remember(bgLine) {
                derivedStateOf {
                    val currentTime = currentTimeProvider()
                    currentTime >= (bgLine.start - 600) && currentTime <= (bgLine.end + 600)
                }
            }

            AnimatedVisibility(
                visible = isAccompanimentVisible,
                enter = scaleIn(
                    tween(600), transformOrigin = TransformOrigin(
                        if (isCenterAligned) 0.5f else if (isRightAligned) 1f else 0f, if (isBefore) 1f else 0f
                    )
                ) + fadeIn(tween(600)) + slideInVertically(
                    tween(600)
                ) + expandVertically(tween(600), if (isBefore) Alignment.Top else Alignment.Bottom),
                exit = scaleOut(
                    tween(600), transformOrigin = TransformOrigin(
                        if (isCenterAligned) 0.5f else if (isRightAligned) 1f else 0f, if (isBefore) 1f else 0f
                    )
                ) + fadeOut(tween(600)) + slideOutVertically(
                    tween(600)
                ) + shrinkVertically(tween(600), if (isBefore) Alignment.Top else Alignment.Bottom),
            ) {
                LyricsLineItem(
                    isFocused = true,
                    isRightAligned = isRightAligned,
                    isCenterAligned = isCenterAligned,
                    onLineClicked = { },
                    onLinePressed = { },
                    blurRadius = { 0f },
                    blendMode = blendMode,
                    activeAlpha = 0.6f,
                    inactiveAlpha = 0.2f
                ) {
                    KaraokeLineText(
                        line = bgLine,
                        currentTimeProvider = currentTimeProvider,
                        normalLineTextStyle = normalLineTextStyle,
                        accompanimentLineTextStyle = accompanimentLineTextStyle,
                        phoneticTextStyle = phoneticTextStyle,
                        activeColor = activeColor,
                        blendMode = blendMode,
                        showDebugRectangles = showDebugRectangles,
                        showTranslation = showTranslation,
                        showPhonetic = showPhonetic,
                        textMeasurer = textMeasurer,
                        activeScale = activeScale
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(
            vertical = 8.dp, horizontal = if (line is KaraokeLine.AccompanimentKaraokeLine) 0.dp
            else 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = columnHorizontalAlignment
    ) {
        AccompanimentLines(true, accompanimentLinesBeforeMain)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val density = LocalDensity.current
            val availableWidthPx = with(density) { maxWidth.toPx() }
            val scaleFactor = activeScale.coerceAtLeast(1.0f)
            val effectiveWrapWidth = availableWidthPx / scaleFactor

            val textStyle = remember(normalLineTextStyle, accompanimentLineTextStyle, line is KaraokeLine.AccompanimentKaraokeLine) {
                val baseStyle =
                    if (line is KaraokeLine.AccompanimentKaraokeLine) accompanimentLineTextStyle
                    else normalLineTextStyle
                baseStyle.copy(textDirection = TextDirection.Content)
            }

            val spaceWidth = remember(textMeasurer, textStyle) {
                textMeasurer.measure(" ", textStyle).size.width.toFloat()
            }

            val processedSyllables = remember(line.syllables, line.alignment) {
                if (line.alignment == KaraokeAlignment.End) {
                    line.syllables.dropLastWhile { it.content.isBlank() }
                } else {
                    line.syllables
                }
            }

            val initialLayouts by remember(precalculatedLayouts, textStyle, phoneticTextStyle, spaceWidth) {
                derivedStateOf {
                    precalculatedLayouts ?: measureSyllablesAndDetermineAnimation(
                        syllables = processedSyllables,
                        textMeasurer = textMeasurer,
                        style = textStyle,
                        phoneticStyle = phoneticTextStyle,
                        isAccompanimentLine = line is KaraokeLine.AccompanimentKaraokeLine,
                        spaceWidth = spaceWidth
                    )
                }
            }

            val wrappedLines by remember(initialLayouts, effectiveWrapWidth, textStyle) {
                derivedStateOf {
                    calculateBalancedLines(
                        syllableLayouts = initialLayouts,
                        availableWidthPx = effectiveWrapWidth,
                        textMeasurer = textMeasurer,
                        style = textStyle
                    )
                }
            }

            val lineHeight = remember(textStyle) {
                textMeasurer.measure("M", textStyle).size.height.toFloat()
            }

            val phoneticHeight = remember(phoneticTextStyle) {
                textMeasurer.measure("M", phoneticTextStyle).size.height.toFloat()
            }

            val finalLineLayouts = remember(
                wrappedLines, availableWidthPx, lineHeight, isLineRtl, isRightAligned, isCenterAligned, showPhonetic
            ) {
                calculateStaticLineLayout(
                    wrappedLines = wrappedLines,
                    isLineRightAligned = isRightAligned,
                    isLineCenterAligned = isCenterAligned,
                    canvasWidth = availableWidthPx,
                    lineHeight = lineHeight,
                    phoneticHeight = if (showPhonetic) phoneticHeight else 0f,
                    isRtl = isLineRtl
                )
            }

            val rowRenderData = remember(finalLineLayouts, showPhonetic, density) {
                calculateRowRenderData(
                    lineLayouts = finalLineLayouts,
                    showPhonetic = showPhonetic,
                    density = density.density
                )
            }

            val hasPhonetics = remember(initialLayouts, showPhonetic) {
                showPhonetic && initialLayouts.any { it.phoneticLayoutResult != null }
            }

            val totalHeight = remember(wrappedLines, lineHeight, hasPhonetics, phoneticHeight) {
                var height = lineHeight * wrappedLines.size
                if (hasPhonetics) {
                    height += phoneticHeight * wrappedLines.size
                }
                height
            }

            Canvas(modifier = Modifier.size(maxWidth, (totalHeight.roundToInt() + 8).toDp())) {
                val time = currentTimeProvider()
                if (sweep != null) {
                    // The line-timed line with its backing vocals split off: the same
                    // top-to-bottom sweep a plain SyncedLine gets, drawn over syllables.
                    drawLyricsLineSweep(
                        rowRenderData = rowRenderData,
                        currentTimeMs = time,
                        color = activeColor,
                        blendMode = blendMode,
                        sweep = sweep,
                        showPhonetic = showPhonetic
                    )
                } else {
                    drawLyricsLine(
                        rowRenderData = rowRenderData,
                        currentTimeMs = time,
                        color = activeColor,
                        blendMode = blendMode,
                        isRtl = isLineRtl,
                        showDebugRectangles = showDebugRectangles,
                        showPhonetic = showPhonetic
                    )
                }
            }
        }

        if (showTranslation) {
            val originalText = remember(line.syllables) { line.syllables.joinToString("") { it.content }.trim() }
            line.translation?.takeUnless { com.alananasss.kittytune.ui.player.lyrics.LyricsUtils.isDuplicateTranslation(originalText, it) }?.let { translation ->
                Text(
                    text = translation,
                    color = activeColor.copy(0.4f),
                    modifier = Modifier.fillMaxWidth(1f / activeScale.coerceAtLeast(1.0f)).graphicsLayer {
                        this.blendMode = blendMode
                    },
                    textAlign = translationTextAlign
                )
            }
        }

        if (showPhonetic) {
            line.phonetic?.let { phonetic ->
                Text(
                    text = phonetic,
                    style = phoneticTextStyle,
                    color = activeColor.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth(1f / activeScale.coerceAtLeast(1.0f)).graphicsLayer {
                        this.blendMode = blendMode
                    },
                    textAlign = translationTextAlign
                )
            }
        }
        AccompanimentLines(false, accompanimentLinesAfterMain)
    }
}

@Composable
private fun Int.toDp(): Dp = with(LocalDensity.current) { this@toDp.toDp() }
