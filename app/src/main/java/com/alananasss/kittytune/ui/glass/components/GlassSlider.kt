package com.alananasss.kittytune.ui.glass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.alananasss.kittytune.ui.glass.LocalGlassBackdrop
import com.alananasss.kittytune.ui.glass.LocalGlassSettings
import com.alananasss.kittytune.ui.glass.isGlassLensSupported
import com.alananasss.kittytune.ui.glass.motion.DampedDragAnimation
import com.alananasss.kittytune.ui.glass.rememberGlassColors
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.runtime.snapshotFlow
import kotlin.math.roundToInt

/**
 * The Liquid Glass slider: a thin track and a white capsule thumb that becomes a magnifying lens
 * while it is dragged, refracting both the track and whatever is behind the slider.
 *
 * [steps] snaps the value the same way as the Material slider, and
 * [onValueChangeFinished] fires once the finger lifts.
 */
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    accentColor: Color = rememberGlassColors().accent,
    trackColor: Color = rememberGlassColors().track,
    trackHeight: Dp = 6.dp,
) {
    val backdrop = LocalGlassBackdrop.current ?: emptyBackdrop()
    val chromatic = LocalGlassSettings.current.chromaticAberration && isGlassLensSupported
    val trackBackdrop = rememberLayerBackdrop()
    val currentOnChange by rememberUpdatedState(onValueChange)
    val currentOnFinished by rememberUpdatedState(onValueChangeFinished)
    val span = (valueRange.endInclusive - valueRange.start).takeIf { it > 0f } ?: 1f

    fun snap(raw: Float): Float {
        val clamped = raw.coerceIn(valueRange)
        if (steps <= 0) return clamped
        val segments = steps + 1
        val fraction = (clamped - valueRange.start) / span
        return valueRange.start + (fraction * segments).roundToInt() / segments.toFloat() * span
    }

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .alpha(if (enabled) 1f else 0.38f)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange, steps)
                if (enabled) {
                    setProgress { target ->
                        currentOnChange(snap(target))
                        currentOnFinished?.invoke()
                        true
                    }
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        // The unsnapped finger position: steps snap what is reported, not what is dragged.
        var rawValue by remember { mutableStateOf(value) }

        val dampedDragAnimation = remember(animationScope, valueRange) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value.coerceIn(valueRange),
                valueRange = valueRange,
                visibilityThreshold = span / 1000f,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = { rawValue = targetValue },
                onDragStopped = {
                    if (didDrag) {
                        val snapped = snap(rawValue)
                        updateValue(snapped)
                        currentOnChange(snapped)
                        currentOnFinished?.invoke()
                        didDrag = false
                    }
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) didDrag = dragAmount.x != 0f
                    val delta = span * (dragAmount.x / trackWidth)
                    rawValue = (if (isLtr) rawValue + delta else rawValue - delta).coerceIn(valueRange)
                    updateValue(if (steps > 0) snap(rawValue) else rawValue)
                    currentOnChange(snap(rawValue))
                }
            )
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { value }.collectLatest { v ->
                if (!didDrag && dampedDragAnimation.targetValue != v) {
                    dampedDragAnimation.updateValue(v)
                }
            }
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .then(
                        if (enabled) {
                            Modifier.pointerInput(animationScope, valueRange, steps) {
                                detectTapGestures { position ->
                                    val delta = span * (position.x / trackWidth)
                                    val target = snap(if (isLtr) valueRange.start + delta else valueRange.endInclusive - delta)
                                    dampedDragAnimation.animateToValue(target)
                                    currentOnChange(target)
                                    currentOnFinished?.invoke()
                                }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .height(trackHeight)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(trackHeight)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress.fastCoerceIn(0f, 1f)).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(if (isLtr) 0 else constraints.maxWidth - width, 0)
                        }
                    }
            )
        }

        Box(
            Modifier
                .graphicsLayer {
                    translationX =
                        (-size.width / 2f + trackWidth * dampedDragAnimation.progress)
                            .fastCoerceIn(-size.width / 4f, trackWidth - size.width * 3f / 4f) *
                            if (isLtr) 1f else -1f
                }
                .then(if (enabled) dampedDragAnimation.modifier else Modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            scale(lerp(2f / 3f, 1f, progress), lerp(0f, 1f, progress)) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8f.dp.toPx() * (1f - progress))
                        lens(
                            10f.dp.toPx() * progress,
                            14f.dp.toPx() * progress,
                            chromaticAberration = chromatic
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = { Shadow(radius = 4f.dp, color = Color.Black.copy(alpha = 0.05f)) },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(radius = 4f.dp * progress, alpha = progress)
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40f.dp, 24f.dp)
        )
    }
}
