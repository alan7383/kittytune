package com.alananasss.kittytune.ui.liquidglass.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.utils.DampedDragAnimation
import com.kyant.backdrop.Backdrop
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
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Authentic Liquid Glass Slider directly mirroring Backdrop Catalog:
 * - Uses DampedDragAnimation with inertia and velocity squish & stretch
 * - Optical backdrop refraction of the track within the thumb
 * - Dynamic lens height, amount and blur expansion on press
 * - Spring release back to unpressed state
 */
@Composable
fun KittyLiquidSlider(
    value: () -> Float,
    onValueChange: (Float) -> Unit,
    backdrop: Backdrop,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    visibilityThreshold: Float = 0.001f,
    accentColor: Color = Color(0xFF0088FF),
    enabled: Boolean = true
) {
    val isLightTheme = !isSystemInDarkTheme()
    val liquidConfig = LocalLiquidGlassConfig.current
    val trackColor =
        if (isLightTheme) Color(0xFF787878).copy(0.2f)
        else Color(0xFF787880).copy(0.36f)

    val trackBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .then(if (!enabled) Modifier.alpha(0.4f) else Modifier),
        contentAlignment = Alignment.CenterStart
    ) {
        val trackWidth = constraints.maxWidth

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var didDrag by remember { mutableStateOf(false) }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = value(),
                valueRange = valueRange,
                visibilityThreshold = visibilityThreshold,
                initialScale = 1f,
                pressedScale = 1.5f,
                onDragStarted = {},
                onDragStopped = {
                    if (didDrag) {
                        onValueChange(targetValue)
                        didDrag = false
                    }
                    onValueChangeFinished?.invoke()
                },
                onDrag = { _, dragAmount ->
                    if (!didDrag) {
                        didDrag = dragAmount.x != 0f
                    }
                    val delta = (valueRange.endInclusive - valueRange.start) * (dragAmount.x / trackWidth)
                    val nextVal = (if (isLtr) targetValue + delta else targetValue - delta).coerceIn(valueRange)
                    val finalValue = if (steps > 0) {
                        val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
                        val stepIndex = kotlin.math.round((nextVal - valueRange.start) / stepSize)
                        (valueRange.start + stepIndex * stepSize).coerceIn(valueRange)
                    } else {
                        nextVal
                    }
                    onValueChange(finalValue)
                }
            )
        }

        val currentValueProvider by rememberUpdatedState(value)
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentValueProvider() }
                .collectLatest { valNow ->
                    val clamped = valNow.coerceIn(valueRange)
                    if (dampedDragAnimation.targetValue != clamped) {
                        dampedDragAnimation.updateValue(clamped)
                    }
                }
        }

        Box(Modifier.layerBackdrop(trackBackdrop)) {
            Box(
                Modifier
                    .clip(Capsule())
                    .background(trackColor)
                    .height(6.dp)
                    .fillMaxWidth()
            )

            Box(
                Modifier
                    .clip(Capsule())
                    .background(accentColor)
                    .height(6.dp)
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val width = (constraints.maxWidth * dampedDragAnimation.progress).fastRoundToInt()
                        layout(width, placeable.height) {
                            placeable.place(0, 0)
                        }
                    }
            )
        }

        // Tap layer sits above the track but below the thumb. Keeping it off the track is
        // important: a full-width tap detector on the track consumes the down and makes the
        // thumb's drag cancel before it can move.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(animationScope, enabled) {
                    if (!enabled) return@pointerInput
                    fun valueAt(x: Float): Float {
                        val delta = (valueRange.endInclusive - valueRange.start) * (x / trackWidth)
                        val targetValue =
                            (if (isLtr) valueRange.start + delta
                            else valueRange.endInclusive - delta)
                                .coerceIn(valueRange)
                        return if (steps > 0) {
                            val stepSize = (valueRange.endInclusive - valueRange.start) / (steps + 1)
                            val stepIndex = kotlin.math.round((targetValue - valueRange.start) / stepSize)
                            (valueRange.start + stepIndex * stepSize).coerceIn(valueRange)
                        } else {
                            targetValue
                        }
                    }
                    val touchSlop = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var dragging = false
                        var lastValue = value()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp()) {
                                val dx = change.position.x - down.position.x
                                val dy = change.position.y - down.position.y
                                if (!dragging && abs(dx) <= touchSlop && abs(dy) <= touchSlop) {
                                    val v = valueAt(change.position.x)
                                    dampedDragAnimation.animateToValue(v)
                                    onValueChange(v)
                                    onValueChangeFinished?.invoke()
                                    change.consume()
                                } else if (dragging) {
                                    dampedDragAnimation.release()
                                    onValueChangeFinished?.invoke()
                                    change.consume()
                                }
                                break
                            }
                            if (change.isConsumed && !dragging) break
                            val dx = change.position.x - down.position.x
                            val dy = change.position.y - down.position.y
                            if (!dragging && abs(dx) > touchSlop && abs(dx) > abs(dy)) {
                                dragging = true
                                dampedDragAnimation.press()
                            }
                            if (dragging) {
                                // Claim diagonal drags from the parent vertical scroll. The thumb
                                // may not be the hit target (it is moved with an offset), so the
                                // full-width layer owns the gesture and feeds DampedDragAnimation.
                                change.consume()
                                val v = valueAt(change.position.x)
                                if (v != lastValue) {
                                    lastValue = v
                                    dampedDragAnimation.updateValue(v)
                                    onValueChange(v)
                                }
                            }
                        }
                    }
                }
        )
        Box(
            Modifier
                .offset {
                    val thumbWidth = 40.dp.toPx()
                    val x = (-thumbWidth / 2f + trackWidth * dampedDragAnimation.progress)
                        .fastCoerceIn(-thumbWidth / 4f, trackWidth - thumbWidth * 3f / 4f) * if (isLtr) 1f else -1f
                    IntOffset(x.roundToInt(), 0)
                }
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 1f, progress)
                            val scaleY = lerp(0f, 1f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { Capsule() },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur((liquidConfig.blurRadiusDp * (1f - progress)).coerceAtLeast(0f).dp.toPx())
                        lens(
                            (liquidConfig.refractionHeightDp * (10f / 24f)).dp.toPx() * progress,
                            (liquidConfig.refractionAmountDp * (14f / 24f)).dp.toPx() * progress,
                            depthEffect = liquidConfig.depthEffect,
                            chromaticAberration = liquidConfig.chromaticAberration > 0f
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = (progress * liquidConfig.highlightIntensity).coerceIn(0f, 1f)
                        )
                    },
                    shadow = if (liquidConfig.shadowsEnabled) {
                        {
                            Shadow(
                                radius = 4.dp,
                                color = Color.Black.copy(alpha = 0.05f)
                            )
                        }
                    } else null,
                    innerShadow = if (liquidConfig.innerShadowEnabled) {
                        {
                            val progress = dampedDragAnimation.pressProgress
                            InnerShadow(
                                radius = 4.dp * progress,
                                alpha = progress
                            )
                        }
                    } else null,
                    layerBlock = if (liquidConfig.interactiveBounce) {
                        {
                            scaleX = dampedDragAnimation.scaleX
                            scaleY = dampedDragAnimation.scaleY
                            val velocity = dampedDragAnimation.velocity / 10f
                            scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                            scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                        }
                    } else null,
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}
