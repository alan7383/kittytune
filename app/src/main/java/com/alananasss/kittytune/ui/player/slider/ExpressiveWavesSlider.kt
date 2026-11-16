package com.alananasss.kittytune.ui.player.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

// "Expressive Waves" seek bar - the calm "fluid dune" look.
//
// One coherent filled silhouette rises ABOVE the slider line: a long, slow hill whose
// crest rolls forward while playing. The fill is dense near the line and fades toward
// the crest, and a slightly taller, fainter ghost ridge behind it gives the volume -
// both ride the SAME phase at the SAME speed, so the shape flows as one body instead of
// twitching like separate counter-moving layers (the previous "jerking wave" look).
//
// On pause the dune settles flat; while scrubbing it flattens for precise tracking and
// expanding ripple rings pulse out of the thumb. An invisible Material3 [Slider] owns
// gestures, semantics and accessibility; the Canvas below draws the visuals.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpressiveWavesSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    colors: SliderColors = SliderDefaults.colors(),
    isPlaying: Boolean = true,
    trackHeight: Dp = 4.dp,
    thumbRadius: Dp = 6.dp,
    waveAmplitudeWhenPlaying: Dp = 9.dp,
    waveLength: Dp = 88.dp,
    waveAnimationDuration: Int = 3600,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isInteracting = isDragged || isPressed

    val thumbInteractionFraction by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "ThumbInteractionAnim"
    )

    // Buffering blinks isPlaying for a few frames around track transitions and network
    // hiccups. Without a grace window the dune collapsed into the plain line and grew
    // back on every blink. The wave is allowed to fall asleep only after a pause lasts
    // a full 600 ms; a play flip inside the window cancels the countdown.
    var playingWithGrace by remember { mutableStateOf(isPlaying) }
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            playingWithGrace = true
        } else {
            delay(600)
            playingWithGrace = false
        }
    }

    // The dune flows only while playing and untouched; while scrubbing the bar
    // flattens out immediately so the position tracks the finger exactly.
    val shouldShowWave = playingWithGrace && !isInteracting

    val animatedWaveAmplitude by animateDpAsState(
        targetValue = if (shouldShowWave) waveAmplitudeWhenPlaying else 0.dp,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "WaveAmplitudeAnim"
    )

    // One shared phase for the whole body: the crest and its ghost ridge drift together,
    // so the silhouette stays a single calm shape. The phase keeps growing across cycle
    // boundaries (never folded back), so the motion is bit-exact continuous.
    val basePhase = remember { Animatable(0f) }

    LaunchedEffect(shouldShowWave, waveAnimationDuration) {
        if (shouldShowWave && waveAnimationDuration > 0) {
            val fullRotation = (2 * PI).toFloat()
            while (true) {
                val start = (basePhase.value % fullRotation).let { if (it < 0f) it + fullRotation else it }
                basePhase.snapTo(start)
                basePhase.animateTo(
                    targetValue = start + fullRotation,
                    animationSpec = tween(durationMillis = waveAnimationDuration, easing = LinearEasing)
                )
            }
        }
    }

    // Drag ripples: rings expanding out of the thumb for as long as it is held.
    val ripple = remember { Animatable(1f) }
    LaunchedEffect(isInteracting) {
        if (isInteracting) {
            ripple.snapTo(0f)
            while (true) {
                ripple.animateTo(1f, tween(durationMillis = 1100, easing = LinearEasing))
                ripple.snapTo(0f)
            }
        } else {
            ripple.snapTo(1f)
        }
    }

    val density = LocalDensity.current
    val trackHeightPx = with(density) { trackHeight.toPx() }
    val thumbRadiusPx = with(density) { thumbRadius.toPx() }
    val waveAmplitudePx = with(density) { animatedWaveAmplitude.toPx() }
    val waveLengthPx = with(density) { waveLength.toPx() }
    val waveFrequency = if (waveLengthPx > 0f) ((2 * PI) / waveLengthPx).toFloat() else 0f
    val thumbLineHeightPx = with(density) { 20.dp.toPx() }
    val thumbGapPx = with(density) { 4.dp.toPx() }
    val rippleMaxRadiusPx = with(density) { 24.dp.toPx() }
    val rippleStrokePx = with(density) { 1.5.dp.toPx() }
    val crestHighlightPx = with(density) { 1.4.dp.toPx() }
    val taperInPx = with(density) { 64.dp.toPx() }
    val taperOutPx = with(density) { 24.dp.toPx() }

    val dunePath = remember { Path() }
    val ghostPath = remember { Path() }
    val crestPath = remember { Path() }

    // The line sits at the vertical middle with the dune rising above it. The floor keeps
    // the total height stable across amplitude changes and close to the other M3 slider
    // styles (44dp), so switching styles does not shift the surrounding player layout.
    val sliderVisualHeight = remember(trackHeight, thumbRadius, waveAmplitudeWhenPlaying) {
        (waveAmplitudeWhenPlaying + trackHeight + thumbRadius * 2 + 10.dp).coerceAtLeast(44.dp)
    }

    val hapticFeedback = LocalHapticFeedback.current
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val normalizedValue = if (valueRange.endInclusive == valueRange.start) 0f
    else ((clampedValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)

    // Light follow spring: the caller already feeds a per-frame smooth position, so this
    // only glides the last few pixels after a seek instead of re-smoothing every tick
    // (which was the stop-go "jerking wave"). Reverse positions (seeks back, new track)
    // land without a long glide.
    val animatedNormalizedValue by animateFloatAsState(
        targetValue = normalizedValue,
        animationSpec = if (normalizedValue == 0f) {
            spring(dampingRatio = 0.75f, stiffness = 200f)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 380f)
        },
        label = "ProgressValueAnim"
    )
    val renderNormalizedValue = if (isInteracting) normalizedValue else animatedNormalizedValue

    val activeTrackColor = colors.activeTrackColor
    val inactiveTrackColor = colors.inactiveTrackColor
    val thumbColor = colors.thumbColor

    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val lastHapticStep = remember { mutableIntStateOf(-1) }

        Slider(
            value = clampedValue,
            onValueChange = { newValue ->
                val normalizedNew = if (valueRange.endInclusive == valueRange.start) 0f
                else ((newValue - valueRange.start) / (valueRange.endInclusive - valueRange.start)).coerceIn(0f, 1f)
                val currentStep = (normalizedNew * 50f).roundToInt()
                if (currentStep != lastHapticStep.intValue) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    lastHapticStep.intValue = currentStep
                }
                onValueChange(newValue)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .clearAndSetSemantics {
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = clampedValue,
                        range = valueRange,
                        steps = 0
                    )
                },
            enabled = enabled,
            valueRange = valueRange,
            onValueChangeFinished = onValueChangeFinished,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = Color.Transparent,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )

        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(sliderVisualHeight)
                .drawWithCache {
                    val canvasWidth = size.width
                    // The line sits in the VERTICAL CENTER of the slider, so it always matches
                    // the Material3 Slider's own centered (invisible) track and thumb - the
                    // gesture target and the drawn visuals coincide instead of the drawn line
                    // hanging 10dp below where the finger actually lands. The dune rises above
                    // the line; the box height guarantees headroom for the ghost crest.
                    val baselineY = size.height / 2f
                    val trackStart = thumbRadiusPx
                    val trackEnd = canvasWidth - thumbRadiusPx
                    val trackWidth = (trackEnd - trackStart).coerceAtLeast(0f)
                    val lineTop = baselineY - trackHeightPx / 2f

                    onDrawWithContent {
                        val progressPxEnd = trackStart + trackWidth * renderNormalizedValue
                        val phase = basePhase.value

                        // 1. Inactive track: the flat remainder of the slider line
                        if (progressPxEnd < trackEnd) {
                            drawLine(
                                color = inactiveTrackColor,
                                start = Offset(progressPxEnd, baselineY),
                                end = Offset(trackEnd, baselineY),
                                strokeWidth = trackHeightPx,
                                cap = StrokeCap.Round
                            )
                        }

                        // 2. Active portion: the filled dune above the solid line.
                        if (renderNormalizedValue > 0f) {
                            val activeEnd = progressPxEnd - (thumbGapPx * thumbInteractionFraction)
                            val gradient = Brush.linearGradient(
                                colors = listOf(
                                    activeTrackColor.copy(alpha = 0.35f),
                                    activeTrackColor
                                ),
                                start = Offset(trackStart, baselineY),
                                end = Offset(activeEnd.coerceAtLeast(trackStart), baselineY)
                            )

                            // Solid line under the dune, always drawn
                            if (activeEnd > trackStart) {
                                drawLine(
                                    brush = gradient,
                                    start = Offset(trackStart, baselineY),
                                    end = Offset(activeEnd, baselineY),
                                    strokeWidth = trackHeightPx,
                                    cap = StrokeCap.Round
                                )
                            }

                            if (waveAmplitudePx > 0.01f && waveFrequency > 0f && activeEnd > trackStart + 1f) {
                                // Crest height at [x] for a ridge of [ampPx]: slow hills between
                                // a flat line and the full amplitude, locked to the shared phase,
                                // tapered in from the track start and melted out at the thumb so
                                // the dune is born and dies inside the line.
                                fun ridgeAt(x: Float, ampPx: Float, phaseOffset: Float, endX: Float): Float {
                                    val hill = 0.55f + 0.45f * sin(waveFrequency * x + phase + phaseOffset)
                                    val taperIn = ((x - trackStart) / taperInPx).coerceIn(0f, 1f)
                                    val taperOut = ((endX - x) / taperOutPx).coerceIn(0f, 1f)
                                    return ampPx * hill * taperIn * taperOut
                                }

                                // Fills [path] with a ridge silhouette: along the crest from the
                                // track start to [endX], then down to the line and back.
                                fun buildRidge(path: Path, ampPx: Float, phaseOffset: Float, endX: Float) {
                                    path.reset()
                                    path.moveTo(trackStart, lineTop)
                                    val step = (waveLengthPx / 24f).coerceIn(1.5f, 4f)
                                    var x = trackStart
                                    while (x < endX) {
                                        x = minOf(x + step, endX)
                                        path.lineTo(x, lineTop - ridgeAt(x, ampPx, phaseOffset, endX))
                                    }
                                    path.lineTo(endX, lineTop)
                                    path.close()
                                }

                                // Ghost ridge: a touch taller and fainter, drifting with the
                                // SAME phase - depth without any nervous multi-line motion.
                                buildRidge(ghostPath, waveAmplitudePx * 1.30f, 0.9f, activeEnd)
                                drawPath(
                                    path = ghostPath,
                                    color = activeTrackColor.copy(alpha = 0.20f)
                                )

                                // Main dune: dense near the line, soft towards the crest.
                                buildRidge(dunePath, waveAmplitudePx, 0f, activeEnd)
                                drawPath(
                                    path = dunePath,
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            activeTrackColor.copy(alpha = 0.42f),
                                            activeTrackColor.copy(alpha = 0.88f)
                                        ),
                                        startY = lineTop - waveAmplitudePx,
                                        endY = lineTop
                                    )
                                )

                                // Crest highlight: a thin bright lip along the very top of
                                // the dune. It makes the silhouette read clearly even at the
                                // small preview size in the setup wizard, where the style
                                // used to look indistinguishable from the plain wavy slider.
                                crestPath.reset()
                                val crestStep = (waveLengthPx / 24f).coerceIn(1.5f, 4f)
                                var crestX = trackStart
                                crestPath.moveTo(crestX, lineTop - ridgeAt(crestX, waveAmplitudePx, 0f, activeEnd))
                                while (crestX < activeEnd) {
                                    crestX = minOf(crestX + crestStep, activeEnd)
                                    crestPath.lineTo(crestX, lineTop - ridgeAt(crestX, waveAmplitudePx, 0f, activeEnd))
                                }
                                drawPath(
                                    path = crestPath,
                                    color = activeTrackColor.copy(alpha = 0.55f),
                                    style = Stroke(width = crestHighlightPx, cap = StrokeCap.Round)
                                )
                            }
                        }

                        // 3. Thumb: circle at rest on the line, capsule while interacting,
                        // with a soft press glow and the expanding drag ripples.
                        val thumbCenterX = trackStart + trackWidth * renderNormalizedValue
                        val thumbWidth = thumbRadiusPx * 2f + (trackHeightPx * 1.2f - thumbRadiusPx * 2f) * thumbInteractionFraction
                        val thumbHeight = thumbRadiusPx * 2f + (thumbLineHeightPx - thumbRadiusPx * 2f) * thumbInteractionFraction

                        if (thumbInteractionFraction > 0.01f) {
                            drawCircle(
                                color = thumbColor.copy(alpha = 0.22f * thumbInteractionFraction),
                                radius = thumbRadiusPx + thumbRadiusPx * 1.2f * thumbInteractionFraction,
                                center = Offset(thumbCenterX, baselineY)
                            )
                        }
                        if (isInteracting) {
                            val rippleP = ripple.value
                            for (i in 0 until 3) {
                                val p = (rippleP + i / 3f) % 1f
                                drawCircle(
                                    color = thumbColor.copy(alpha = (1f - p) * 0.4f),
                                    radius = thumbRadiusPx + p * rippleMaxRadiusPx,
                                    center = Offset(thumbCenterX, baselineY),
                                    style = Stroke(width = rippleStrokePx)
                                )
                            }
                        }

                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(
                                thumbCenterX - thumbWidth / 2f,
                                baselineY - thumbHeight / 2f
                            ),
                            size = Size(thumbWidth, thumbHeight),
                            cornerRadius = CornerRadius(thumbWidth / 2f)
                        )
                    }
                }
        )
    }
}
