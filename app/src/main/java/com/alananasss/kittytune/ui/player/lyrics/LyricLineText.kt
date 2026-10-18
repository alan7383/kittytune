package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import com.alananasss.kittytune.data.local.LyricsDisplayStyle
import kotlinx.coroutines.isActive

/**
 * How one line of lyrics looks, in one place.
 *
 * Two things live here as a result: the *decision* about how a line at a given distance from the current one
 * should look ([LyricLineStyling]), and the *drawing* of the line itself ([LyricLineText]).
 */
internal data class LyricLineTreatment(
    val scale: Float,
    val alpha: Float,
    val blur: Dp,
)

/**
 * The visual treatment of a line, by how far it is from the one being sung.
 */
internal object LyricLineStyling {

    /** One line either side of the current one. */
    const val SCALE_NEAR = 0.90f

    /** Two lines away. */
    const val SCALE_MID = 0.82f

    /** Three or more. It stops falling here: past this, lines are decoration and shrinking them further
     *  only costs legibility. */
    const val SCALE_FAR = 0.78f

    /** How far back "focus" pushes everything that is not the current line. */
    const val FOCUS_ALPHA = 0.18f

    /** Already sung, in the two styles that do not override it. Dimmer than what is coming. */
    const val PAST_ALPHA = 0.45f

    /** Still to come — the half worth reading, so it stays brighter than the half already gone. */
    const val UPCOMING_ALPHA = 0.70f

    /**
     * @param distance `index - activeIndex`; zero is the line being sung, negative is already past.
     * @param focusBlur how much to blur the rest under [LyricsDisplayStyle.FOCUS].
     * @param blurEnabled whether to apply blur effect for FOCUS/SCALE_FOCUS styles (when false, blur is 0).
     */
    fun treatmentFor(
        style: LyricsDisplayStyle,
        distance: Int,
        focusBlur: Dp = 2.dp,
        blurEnabled: Boolean = true,
    ): LyricLineTreatment {
        val isActive = distance == 0
        val hasScale = style == LyricsDisplayStyle.SCALE || style == LyricsDisplayStyle.SCALE_FOCUS
        val hasFocus = style == LyricsDisplayStyle.FOCUS || style == LyricsDisplayStyle.SCALE_FOCUS
        val scale = when {
            isActive || !hasScale -> 1f
            else -> when (kotlin.math.abs(distance)) {
                1 -> SCALE_NEAR
                2 -> SCALE_MID
                else -> SCALE_FAR
            }
        }
        val alpha = when {
            isActive -> 1f
            hasFocus -> FOCUS_ALPHA
            distance < 0 -> PAST_ALPHA
            else -> UPCOMING_ALPHA
        }
        val blur = if (!isActive && hasFocus && blurEnabled) focusBlur else 0.dp
        return LyricLineTreatment(scale = scale, alpha = alpha, blur = blur)
    }
}

/**
 * The words of one line, drawn the way the reader asked for.
 *
 * Three renderings, in order of how much the source gives us:
 * 1. **Word timings and the fill effect** — the line is drawn twice, dim underneath and bright on top, and
 *    the bright copy is clipped to a path that grows through the characters with a soft playhead edge.
 * 2. **Word timings alone** — one pass, each word coloured by whether it has been reached.
 * 3. **Neither** — the plain line.
 */
@Composable
internal fun LyricLineText(
    line: LyricLine,
    isActive: Boolean,
    positionMs: () -> Float,
    wordSync: Boolean,
    fillEffect: Boolean,
    activeStyle: TextStyle,
    inactiveStyle: TextStyle,
    activeColor: Color,
    inactiveColor: Color,
    unsungColor: Color,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    textModifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {},
    fillWidth: Boolean = true,
) {
    val words = if (wordSync && isActive) line.words else emptyList()
    val fullWidth = if (fillWidth) Modifier.fillMaxWidth() else Modifier

    if (words.isEmpty()) {
        Text(
            text = line.text,
            style = if (isActive) activeStyle else inactiveStyle,
            color = if (isActive) activeColor else inactiveColor,
            textAlign = textAlign,
            modifier = modifier.then(fullWidth).then(textModifier),
            onTextLayout = onTextLayout,
        )
        return
    }

    val text = remember(words) { words.joinToString("") { it.text } }

    if (!fillEffect) {
        val coloured by remember(words, activeColor, unsungColor, positionMs) {
            derivedStateOf {
                buildAnnotatedString {
                    for (word in words) {
                        val reached = positionMs() >= word.startTime
                        withStyle(SpanStyle(color = if (reached) activeColor else unsungColor)) {
                            append(word.text)
                        }
                    }
                }
            }
        }
        Text(
            text = coloured,
            style = activeStyle,
            textAlign = textAlign,
            modifier = modifier.then(fullWidth).then(textModifier),
            onTextLayout = onTextLayout,
        )
        return
    }

    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val layerPaint = remember { Paint() }
    val keepPath = remember { Path() }
    val ranges = remember(words) {
        var start = 0
        words.map { word ->
            val range = start to start + word.text.length
            start += word.text.length
            range
        }
    }

    Box(modifier.then(fullWidth)) {
        Text(
            text = text,
            style = activeStyle,
            color = unsungColor,
            textAlign = textAlign,
            modifier = fullWidth.then(textModifier),
            onTextLayout = {
                layout = it
                onTextLayout(it)
            },
        )
        Text(
            text = text,
            style = activeStyle,
            color = activeColor,
            textAlign = textAlign,
            modifier = fullWidth.drawWithContent {
                val result = layout ?: return@drawWithContent
                val rawPosition = positionMs()
                val position =
                    if (line.endTime > line.startTime) rawPosition.coerceAtMost(line.endTime.toFloat())
                    else rawPosition
                val region = sungRegion(result, words, ranges, text.length, position)
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), layerPaint)
                    this@drawWithContent.drawContent()
                    keepPath.rewind()
                    keepPath.addPath(region.sung)
                    region.band?.let { keepPath.addRect(it) }
                    clipPath(keepPath, clipOp = ClipOp.Difference) {
                        drawRect(Color.White, size = size, blendMode = BlendMode.DstOut)
                    }
                    region.band?.let { band ->
                        drawRect(
                            brush = Brush.horizontalGradient(0f to Color.Transparent, 1f to Color.White, startX = band.left, endX = band.right),
                            topLeft = band.topLeft,
                            size = band.size,
                            blendMode = BlendMode.DstOut,
                        )
                    }
                    canvas.restore()
                }
            },
        )
    }
}

/** What has been sung of a line: [sung] in full, and [band], the soft edge at the playhead, which fades out to the right. */
private class SungRegion(val sung: Path, val band: Rect?)

/**
 * The region of the line that has been sung by [positionMs].
 */
private fun sungRegion(
    layout: TextLayoutResult,
    words: List<LyricWord>,
    ranges: List<Pair<Int, Int>>,
    textLength: Int,
    positionMs: Float,
): SungRegion {
    val path = Path()
    val lastIndex = (textLength - 1).coerceAtLeast(0)
    var band: Rect? = null

    for (i in words.indices) {
        val word = words[i]
        val (from, to) = ranges[i]
        if (from >= to) continue

        if (positionMs >= word.endTime) {
            for (c in from until to) path.addRect(layout.getBoundingBox(c.coerceIn(0, lastIndex)))
            continue
        }
        if (positionMs < word.startTime) continue

        val span = (word.endTime - word.startTime).coerceAtLeast(1L)
        val progress = ((positionMs - word.startTime) / span).coerceIn(0f, 1f)
        val exact = progress * (to - from)
        val whole = exact.toInt()
        for (c in from until from + whole) path.addRect(layout.getBoundingBox(c.coerceIn(0, lastIndex)))

        val partial = from + whole
        if (partial < to) {
            val box = layout.getBoundingBox(partial.coerceIn(0, lastIndex))
            val edge = box.left + (box.right - box.left) * (exact - whole)
            val wordLeft = layout.getBoundingBox(from.coerceIn(0, lastIndex)).left
            val wordRight = layout.getBoundingBox((to - 1).coerceIn(0, lastIndex)).right
            val soft = if (wordRight > wordLeft) minOf(box.height * SOFT_EDGE_EMS, (edge - wordLeft) * 2f, (wordRight - edge) * 2f) else 0f
            val half = soft.coerceAtLeast(0f) / 2f
            path.addRect(Rect(box.left, box.top, (edge - half).coerceAtLeast(box.left), box.bottom))
            if (half > 0.5f) band = Rect(edge - half, box.top, edge + half, box.bottom)
        }
    }
    return SungRegion(path, band)
}

/** The widest the fill's soft edge gets, as a multiple of the line's height. */
private const val SOFT_EDGE_EMS = 1.6f

/**
 * Interpolated playback position, updated per frame so word-fill and progress move smoothly.
 */
@Composable
internal fun rememberSmoothPosition(
    positionMs: () -> Long,
    isPlaying: Boolean,
    speed: Float,
): State<Float> {
    val initial = positionMs()
    val smooth = remember { mutableFloatStateOf(initial.toFloat()) }
    LaunchedEffect(isPlaying, speed) {
        if (!isPlaying) {
            smooth.floatValue = positionMs().toFloat()
            return@LaunchedEffect
        }
        var startedAtMs = System.currentTimeMillis()
        var startedAt = positionMs().toFloat()
        while (isActive) {
            withFrameMillis {
                val now = System.currentTimeMillis()
                val reported = positionMs().toFloat()
                val extrapolated = startedAt + ((now - startedAtMs) * speed)
                if (kotlin.math.abs(reported - extrapolated) > 200f) {
                    startedAt = reported
                    startedAtMs = now
                    smooth.floatValue = reported
                } else {
                    val elapsed = (now - startedAtMs).coerceAtMost(MAX_EXTRAPOLATION_MS)
                    smooth.floatValue = startedAt + (elapsed * speed)
                }
            }
        }
    }
    return smooth
}

private const val MAX_EXTRAPOLATION_MS = 400L

/**
 * This line with its words timed evenly across it, when it has none of its own timings.
 */
internal fun LyricLine.withEvenWords(fallbackEndMs: Long?): LyricLine {
    if (words.isNotEmpty() || text.isBlank()) return this
    val end = when {
        endTime > startTime -> endTime
        fallbackEndMs != null && fallbackEndMs > startTime -> fallbackEndMs
        else -> startTime + DEFAULT_LINE_MS
    }
    val pieces = Regex("""\S+\s*""").findAll(text).map { it.value }.toList()
    if (pieces.isEmpty()) return this
    val weights = pieces.map { it.trimEnd().length.coerceAtLeast(1) }
    val total = weights.sum().toDouble()
    val span = (end - startTime).toDouble()
    var elapsed = 0
    val timed = pieces.mapIndexed { i, piece ->
        val from = startTime + (span * elapsed / total).toLong()
        elapsed += weights[i]
        val to = startTime + (span * elapsed / total).toLong()
        LyricWord(piece, from, to)
    }
    return copy(words = timed, endTime = end)
}

private const val DEFAULT_LINE_MS = 4_000L
