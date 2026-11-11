package com.alananasss.kittytune.ui.player.mix

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.audio.automix.CamelotKey
import com.alananasss.kittytune.audio.automix.EffectTransitionMode
import com.alananasss.kittytune.audio.automix.EqTransitionMode
import com.alananasss.kittytune.audio.automix.FilterTransitionMode
import com.alananasss.kittytune.audio.automix.LoopTransitionMode
import com.alananasss.kittytune.audio.automix.MixTransitionPresets
import com.alananasss.kittytune.audio.automix.TransitionBars
import com.alananasss.kittytune.audio.automix.TransitionConfig
import com.alananasss.kittytune.audio.automix.VolumeCurveMode
import com.alananasss.kittytune.audio.automix.bassLevelAt
import com.alananasss.kittytune.audio.automix.filterInFractionAt
import com.alananasss.kittytune.audio.automix.filterLegIsHighPass
import com.alananasss.kittytune.audio.automix.filterOutFractionAt
import com.alananasss.kittytune.audio.automix.volumeInAt
import com.alananasss.kittytune.audio.automix.volumeOutAt
import com.alananasss.kittytune.utils.makeTimeString
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt

object TransitionCanvasMetrics {
    val TRANSITION_BEAT_WIDTH_DP: Dp = 26.dp
    fun zoneWidthDp(bars: TransitionBars): Dp =
        TRANSITION_BEAT_WIDTH_DP * (bars.bars * 4)
}

// 100% exact Spotify Mix color palette
val TransitionVolumeColor = Color(0xFF4EA8DE)
val TransitionEqColor = Color(0xFFF5A623)
val TransitionFilterColor = Color(0xFFD946EF)
val TransitionPlayheadColor = Color(0xFF1DB954)
val TransitionEditorBackground = Color(0xFF121212)
// The Effects control's white - reverb trails and timed repeats on the outgoing deck.
val TransitionEffectsColor = Color(0xFFF2F2F2)
// The Looping control's orange - the repeated piece of the outgoing deck.
val TransitionLoopColor = Color(0xFFF2994A)

fun camelotWheelColor(code: String?): Color? {
    val key = CamelotKey.parse(code) ?: return null
    return Color(key.badgeColor.toInt())
}

fun Color.needsLightContent(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.6f

@Composable
fun TransitionWaveformCanvas(
    config: TransitionConfig,
    bars: TransitionBars,
    outgoingSamples: FloatArray?,
    incomingSamples: FloatArray?,
    modifier: Modifier = Modifier,
    isPreviewing: Boolean = false,
    previewProgress: Float = 0f,
    outgoingSeed: Long = 0L,
    incomingSeed: Long = 1L,
    incomingStartFraction: Float = 0f,
    outgoingTimecodeMs: Long = 0L,
    incomingTimecodeMs: Long = 0L,
    onOffsetDeltaBeats: (Float) -> Unit = {},
    onOffsetDragEnd: () -> Unit = {},
    onTogglePreview: () -> Unit = {},
) {
    var isDraggingTop by remember { mutableStateOf(false) }
    var isDraggingBottom by remember { mutableStateOf(false) }
    val isDragging = isDraggingTop || isDraggingBottom

    val fromConfig = remember { mutableStateOf(config) }
    val toConfig = remember { mutableStateOf(config) }
    val morph = remember { Animatable(1f) }
    LaunchedEffect(config.preset, config.volumeCurve, config.eqMode, config.filterMode, config.effectMode, config.loopMode) {
        if (fromConfig.value == config && toConfig.value == config) return@LaunchedEffect
        fromConfig.value = toConfig.value
        toConfig.value = config
        morph.snapTo(0f)
        morph.animateTo(
            1f,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        )
    }

    val columns = 160
    val waveOut = remember(outgoingSamples, columns) {
        resampleWave(outgoingSamples ?: synthesizedTransitionWave(outgoingSeed), columns)
    }
    val waveIn = remember(incomingSamples, columns, incomingStartFraction) {
        val src = incomingSamples ?: synthesizedTransitionWave(incomingSeed)
        val start = (incomingStartFraction.coerceIn(0f, 0.95f) * (src.size - 1)).toInt()
        resampleWave(if (start > 0) src.copyOfRange(start, src.size) else src, columns)
    }

    val density = androidx.compose.ui.platform.LocalDensity.current
    val beatWidthPx = with(density) { TransitionCanvasMetrics.TRANSITION_BEAT_WIDTH_DP.toPx() }

    val dragModifier = Modifier.pointerInput(beatWidthPx) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val h = size.height.toFloat()
            if (h <= 0f) return@awaitEachGesture
            val isTop = down.position.y < h * 0.5f
            if (isTop) isDraggingTop = true else isDraggingBottom = true

            val pointerId = down.id
            var active = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                if (!change.pressed) break

                val dx = change.positionChange().x
                if (!active && kotlin.math.abs(dx) > viewConfiguration.touchSlop) {
                    active = true
                }
                if (active) {
                    onOffsetDeltaBeats(dx / beatWidthPx)
                    change.consume()
                }
            }
            isDraggingTop = false
            isDraggingBottom = false
            if (active) onOffsetDragEnd()
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF1E1E1E))
            .then(dragModifier),
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val midY = h / 2f
            val deckCenterOut = h * 0.25f
            val deckCenterIn = h * 0.75f
            val maxAmp = h * 0.20f
            val beatsTotal = (bars.bars * 4).coerceAtLeast(1)
            val beatStep = w / beatsTotal
            val anim = morph.value
            val from = fromConfig.value
            val to = toConfig.value
            val barWidth = w / columns

            // 1. BEAT GRID - HIDDEN at rest, shown ONLY during drag
            if (isDragging) {
                for (beat in 0..beatsTotal) {
                    val x = beat * beatStep
                    val isDownbeat = beat % 4 == 0
                    if (isDownbeat) {
                        // Green vertical line for measure start / downbeat
                        drawLine(
                            color = TransitionPlayheadColor,
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 2.4f,
                        )
                    } else {
                        // 3 thin grey lines between downbeats (beats 2, 3, 4)
                        drawLine(
                            color = Color.White.copy(alpha = 0.28f),
                            start = Offset(x, 0f),
                            end = Offset(x, h),
                            strokeWidth = 1.0f,
                        )
                    }
                }
            }

            // 2. WAVEFORMS
            // Outgoing starts from left (0) and ends at right edge of transition window (w)
            // Incoming starts at left edge of transition window (0) and goes right (w)
            for (i in 0 until columns) {
                val x = i * barWidth
                val xc = (i + 0.5f) / columns
                val envOut = lerp(volumeOutAt(from.volumeCurve, xc), volumeOutAt(to.volumeCurve, xc), anim)
                val envIn = lerp(volumeInAt(from.volumeCurve, xc), volumeInAt(to.volumeCurve, xc), anim)

                val ampOut = waveOut[i] * envOut * maxAmp
                val ampIn = waveIn[i] * envIn * maxAmp

                // Inside transition window: bright / white; outside: muted
                val colorOut = Color.White.copy(alpha = 0.90f)
                val colorIn = Color.White.copy(alpha = 0.90f)

                if (ampOut > 1f) {
                    drawRoundRect(
                        color = colorOut,
                        topLeft = Offset(x + barWidth * 0.18f, deckCenterOut - ampOut),
                        size = Size(barWidth * 0.64f, ampOut * 2f),
                        cornerRadius = CornerRadius(barWidth * 0.3f, barWidth * 0.3f),
                    )
                }
                if (ampIn > 1f) {
                    drawRoundRect(
                        color = colorIn,
                        topLeft = Offset(x + barWidth * 0.18f, deckCenterIn - ampIn),
                        size = Size(barWidth * 0.64f, ampIn * 2f),
                        cornerRadius = CornerRadius(barWidth * 0.3f, barWidth * 0.3f),
                    )
                }
            }

            // 3. CURVES
            // Vertical window boundaries for Volume
            drawLine(TransitionVolumeColor.copy(alpha = 0.5f), Offset(0f, 0f), Offset(0f, h), 2f)
            drawLine(TransitionVolumeColor.copy(alpha = 0.5f), Offset(w, 0f), Offset(w, h), 2f)

            // Volume curves (Blue #4EA8DE)
            val step = w / columns
            var prevOutY: Float? = null
            var prevInY: Float? = null
            for (i in 0..columns) {
                val x = i * step
                val xc = i.toFloat() / columns
                val outY = h * 0.05f + (1f - lerp(volumeOutAt(from.volumeCurve, xc), volumeOutAt(to.volumeCurve, xc), anim)) * (h * 0.40f)
                val inY = h * 0.95f - (1f - lerp(volumeInAt(from.volumeCurve, xc), volumeInAt(to.volumeCurve, xc), anim)) * (h * 0.40f)
                prevOutY?.let { drawLine(TransitionVolumeColor, Offset(x - step, it), Offset(x, outY), 2.8f) }
                prevInY?.let { drawLine(TransitionVolumeColor, Offset(x - step, it), Offset(x, inY), 2.8f) }
                prevOutY = outY
                prevInY = inY
            }

            // EQ Bass level (Yellow #F5A623)
            if (from.eqMode != EqTransitionMode.NONE || to.eqMode != EqTransitionMode.NONE) {
                val yA = midY - h * 0.07f
                val yB = midY + h * 0.07f
                val swapSteps = 64
                var prevY: Float? = null
                for (i in 0..swapSteps) {
                    val x = i * w / swapSteps
                    val xc = i.toFloat() / swapSteps
                    val level = lerp(bassLevelAt(from.eqMode, xc), bassLevelAt(to.eqMode, xc), anim)
                    val y = yA + (yB - yA) * (1f - level)
                    prevY?.let {
                        drawLine(TransitionEqColor, Offset((i - 1) * w / swapSteps, it), Offset(x, y), 3.2f)
                    }
                    prevY = y
                }
            }

            // Filter sweep (Pink #D946EF)
            fun drawFilterLeg(isOutgoingDeck: Boolean) {
                val legHighPassFrom = filterLegIsHighPass(from.filterMode, isOutgoingDeck)
                val legHighPassTo = filterLegIsHighPass(to.filterMode, isOutgoingDeck)
                var prevY: Float? = null
                for (i in 0..columns) {
                    val x = i * step
                    val xc = i.toFloat() / columns
                    val f0 = if (isOutgoingDeck) filterOutFractionAt(from.filterMode, xc) else filterInFractionAt(from.filterMode, xc)
                    val f1 = if (isOutgoingDeck) filterOutFractionAt(to.filterMode, xc) else filterInFractionAt(to.filterMode, xc)
                    val y0 = f0?.let { filterLegY(isOutgoingDeck, legHighPassFrom, it, h) }
                        ?: filterLegY(isOutgoingDeck, legHighPassTo, f1 ?: 1f, h)
                    val y1 = f1?.let { filterLegY(isOutgoingDeck, legHighPassTo, it, h) }
                        ?: filterLegY(isOutgoingDeck, legHighPassFrom, f0 ?: 1f, h)
                    val a0 = if (f0 != null) 1f else 0f
                    val a1 = if (f1 != null) 1f else 0f
                    val y = lerp(y0, y1, anim)
                    val alpha = lerp(a0, a1, anim)
                    if (alpha > 0.02f) {
                        prevY?.let {
                            drawLine(
                                TransitionFilterColor.copy(alpha = alpha),
                                Offset((i - 1) * step, it),
                                Offset(x, y),
                                2.6f,
                            )
                        }
                    }
                    prevY = y
                }
            }
            if (from.filterMode != FilterTransitionMode.NONE || to.filterMode != FilterTransitionMode.NONE) {
                drawFilterLeg(isOutgoingDeck = true)
                drawFilterLeg(isOutgoingDeck = false)
            }

            // Effects wet level (White) - the dramatic exit's tail on the outgoing deck.
            if (from.effectMode != EffectTransitionMode.NONE || to.effectMode != EffectTransitionMode.NONE) {
                var prevFxY: Float? = null
                for (i in 0..columns) {
                    val x = i * step
                    val xc = i.toFloat() / columns
                    val wet = lerp(canvasEffectWetAt(from.effectMode, xc), canvasEffectWetAt(to.effectMode, xc), anim)
                    val y = h * 0.05f + (1f - wet) * (h * 0.14f)
                    prevFxY?.let { drawLine(TransitionEffectsColor, Offset(x - step, it), Offset(x, y), 2.2f) }
                    prevFxY = y
                }
            }

            // Looping region (Orange) - the repeated piece of the outgoing deck, outlined
            // over the first loopBeats of the overlap window.
            val loopBeatsFrom = canvasLoopBeats(from.loopMode).toFloat() / beatsTotal
            val loopBeatsTo = canvasLoopBeats(to.loopMode).toFloat() / beatsTotal
            val loopFraction = lerp(loopBeatsFrom, loopBeatsTo, anim).coerceIn(0f, 1f)
            if (loopFraction > 0.02f) {
                val xEnd = loopFraction * w
                val loopTop = (deckCenterOut - maxAmp - 10f).coerceAtLeast(2f)
                val loopBottom = (deckCenterOut + maxAmp + 10f).coerceAtMost(midY - 6f)
                drawRoundRect(
                    color = TransitionLoopColor.copy(alpha = 0.9f),
                    topLeft = Offset(2f, loopTop),
                    size = Size((xEnd - 2f).coerceAtLeast(6f), loopBottom - loopTop),
                    cornerRadius = CornerRadius(8f, 8f),
                    style = Stroke(width = 2.4f),
                )
                // The loop's end line - where the piece snaps back.
                drawLine(
                    TransitionLoopColor.copy(alpha = 0.75f),
                    Offset(xEnd, loopTop),
                    Offset(xEnd, loopBottom),
                    2f,
                )
            }

            // Separator line between decks
            drawLine(Color.White.copy(alpha = 0.22f), Offset(0f, midY), Offset(w, midY), 1.2f)

            // Playhead
            if (isPreviewing) {
                val x = previewProgress.coerceIn(0f, 1f) * w
                drawLine(TransitionPlayheadColor.copy(alpha = 0.25f), Offset(x, 0f), Offset(x, h), strokeWidth = 8f)
                drawLine(TransitionPlayheadColor, Offset(x, 0f), Offset(x, h), strokeWidth = 2.5f)
            }
        }

        // Center Play/Pause capsule button on the separator line
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = 54.dp, height = 32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                .clickable(onClick = onTogglePreview),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isPreviewing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        // Side nudge buttons and timecode overlay when dragging
        if (isDraggingTop) {
            // Outgoing wave side buttons & timecode
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Box(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            if (outgoingTimecodeMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 36.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = makeTimeString(outgoingTimecodeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }

        if (isDraggingBottom) {
            // Incoming wave side buttons & timecode
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Box(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            if (incomingTimecodeMs >= 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = makeTimeString(incomingTimecodeMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

private fun filterLegY(isOutgoingDeck: Boolean, highPass: Boolean, frac: Float, h: Float): Float {
    val openness = if (highPass) frac else 1f - frac
    return if (isOutgoingDeck) {
        h * 0.12f + (1f - openness) * h * 0.32f
    } else {
        h * 0.88f - (1f - openness) * h * 0.32f
    }
}

fun synthesizedTransitionWave(seed: Long, count: Int = 128): FloatArray {
    val random = java.util.Random(seed)
    val wave = FloatArray(count)
    for (i in 0 until count) {
        val beatPos = i % 4
        val kick = if (beatPos == 0) 0.85f else 0.22f
        val hat = if (beatPos == 2) 0.4f else 0.15f
        val noise = 0.12f + random.nextFloat() * 0.1f
        wave[i] = (kick + hat + noise).coerceIn(0.1f, 1f)
    }
    return wave
}

fun resampleWave(samples: FloatArray, buckets: Int): FloatArray {
    if (buckets <= 0) return FloatArray(0)
    if (samples.isEmpty()) return FloatArray(buckets) { 0f }
    val out = FloatArray(buckets)
    val perBucket = samples.size.toFloat() / buckets
    for (b in 0 until buckets) {
        val from = floor(b * perBucket).toInt()
        val to = floor((b + 1) * perBucket).toInt().coerceAtLeast(from + 1)
        var peak = 0f
        for (i in from until minOf(to, samples.size)) {
            val v = samples[i]
            if (v > peak) peak = v
        }
        out[b] = peak.coerceIn(0f, 1f)
    }
    return out
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

fun zoneWidthPx(bars: TransitionBars, density: Float): Int =
    maxOf(1, (TransitionCanvasMetrics.zoneWidthDp(bars).value * density).roundToInt())

// The effect tail's and loop's shapes, mirrored here (prefixed) so the canvas
// always draws what the engine performs, independent of which model file
// revision the host app carries.

// The outgoing deck's effects wet level (0..1) at [x] - the white curve.
private fun canvasEffectWetAt(mode: EffectTransitionMode, x: Float): Float {
    val p = x.coerceIn(0f, 1f)
    return when (mode) {
        EffectTransitionMode.REVERB_OUT_CENTER ->
            MixTransitionPresets.smoothstep(0.05f, 0.35f, p)
        EffectTransitionMode.REVERB_CUT_END ->
            MixTransitionPresets.smoothstep(0.10f, 0.55f, p) *
                (1f - MixTransitionPresets.smoothstep(0.85f, 0.95f, p))
        EffectTransitionMode.REVERB_OUT_END ->
            MixTransitionPresets.smoothstep(0.35f, 0.90f, p)
        EffectTransitionMode.DELAY_HALF_CUT_END,
        EffectTransitionMode.DELAY_THREE_QUARTER_CUT_END ->
            MixTransitionPresets.smoothstep(0.15f, 0.50f, p) *
                (1f - MixTransitionPresets.smoothstep(0.80f, 0.92f, p))
        EffectTransitionMode.ECHO_HALF_CUT_END ->
            MixTransitionPresets.smoothstep(0.10f, 0.45f, p) *
                (1f - MixTransitionPresets.smoothstep(0.75f, 0.90f, p))
        EffectTransitionMode.ECHO_HALF_OUT_END ->
            MixTransitionPresets.smoothstep(0.30f, 0.95f, p)
        EffectTransitionMode.NONE -> 0f
    }
}

// The loop region's length in beats - the orange control's choice.
private fun canvasLoopBeats(mode: LoopTransitionMode): Int = when (mode) {
    LoopTransitionMode.NONE -> 0
    LoopTransitionMode.BEAT_1 -> 1
    LoopTransitionMode.BEAT_2 -> 2
    LoopTransitionMode.BEAT_4 -> 4
    LoopTransitionMode.BEAT_8 -> 8
    LoopTransitionMode.BEAT_16 -> 16
}
