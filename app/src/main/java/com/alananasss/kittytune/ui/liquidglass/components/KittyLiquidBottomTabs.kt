package com.alananasss.kittytune.ui.liquidglass.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.utils.DampedDragAnimation
import com.alananasss.kittytune.ui.liquidglass.utils.InteractiveHighlight
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangle
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun KittyLiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    /**
     * The selected-tab bubble doubles as the drag handle for switching tabs. When the current
     * route is not one of the tabs (settings, a detail page...), there is no selected tab: the
     * bubble must not be drawn, otherwise it sits on top of the first tab and swallows its click.
     */
    showIndicator: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val liquidConfig = LocalLiquidGlassConfig.current
    val containerColor =
        if (isLightTheme) MaterialTheme.colorScheme.surface.copy(alpha = liquidConfig.surfaceAlpha)
        else MaterialTheme.colorScheme.surface.copy(alpha = liquidConfig.surfaceAlpha)

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount.coerceAtLeast(1)
        }

        val outerCornerRadius = (32f * liquidConfig.cornerRadiusFactor).dp
        val outerShape = { RoundedRectangle(outerCornerRadius) }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = if (constraints.maxWidth > 0) {
                    (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                } else 0f
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val currentSelectedTabIndex by rememberUpdatedState(selectedTabIndex)
        val currentOnTabSelected by rememberUpdatedState(onTabSelected)

        var currentIndex by remember {
            mutableIntStateOf(selectedTabIndex())
        }
        // Set by a drag release; the currentIndex effect then lets onDragStopped keep its full
        // press/release animation instead of starting a second, press-less glide.
        val dragSelection = remember { mutableStateOf<Int?>(null) }
        val dampedDragAnimation = remember(animationScope, tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).coerceAtLeast(0).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val maxIdx = (tabsCount - 1).coerceAtLeast(0)
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, maxIdx)
                    dragSelection.value = targetIndex
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    // Only the drag path selects a tab here. Taps already call onTabSelected
                    // directly; notifying again from the currentIndex effect used to run
                    // activateSearch() twice, restarting the 400 ms focus delay.
                    if (targetIndex != currentSelectedTabIndex()) {
                        currentOnTabSelected(targetIndex)
                    }
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    val maxIdx = (tabsCount - 1).coerceAtLeast(0)
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth.coerceAtLeast(1f) * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, maxIdx.toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(Unit) {
            snapshotFlow { currentSelectedTabIndex() }
                .collectLatest { index ->
                    if (index >= 0 && currentIndex != index) {
                        currentIndex = index
                    }
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    if (dragSelection.value == index) {
                        dragSelection.value = null
                    } else {
                        // Same press/lens animation as Backdrop for a tap on a tab.
                        dampedDragAnimation.animateToValue(index.toFloat())
                    }
                }
        }

        val interactiveHighlight = remember(animationScope, tabWidth) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, _ ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = outerShape,
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        if (liquidConfig.vibrancyEnabled) {
                            vibrancy()
                        }
                        blur(liquidConfig.blurRadiusDp.dp.toPx())
                        lens(
                            liquidConfig.refractionHeightDp.dp.toPx() * progress,
                            liquidConfig.refractionAmountDp.dp.toPx() * progress,
                            depthEffect = liquidConfig.depthEffect,
                            chromaticAberration = liquidConfig.chromaticAberration > 0f
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = (0.5f * liquidConfig.highlightIntensity).coerceIn(0f, 1f))
                    },
                    shadow = if (liquidConfig.shadowsEnabled) {
                        {
                            Shadow(radius = 8.dp, color = Color.Black.copy(alpha = 0.12f))
                        }
                    } else null,
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = if (size.width > 0) lerp(1f, 1f + 16f.dp.toPx() / size.width, progress) else 1f
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64f.dp)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = outerShape,
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            if (liquidConfig.vibrancyEnabled) {
                                vibrancy()
                            }
                            blur(liquidConfig.blurRadiusDp.dp.toPx())
                            lens(
                                liquidConfig.refractionHeightDp.dp.toPx() * progress,
                                liquidConfig.refractionAmountDp.dp.toPx() * progress,
                                depthEffect = liquidConfig.depthEffect,
                                chromaticAberration = liquidConfig.chromaticAberration > 0f
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = (progress * liquidConfig.highlightIntensity).coerceIn(0f, 1f))
                        },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56f.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        if (tabsCount > 0 && showIndicator) {
            val bubbleFactor = liquidConfig.bubbleWidthFactor.coerceIn(0.5f, 1.5f)
            val indicatorWidth = tabWidth * bubbleFactor
            val indicatorOffsetAdjustment = (tabWidth - indicatorWidth) / 2f
            val indicatorCornerRadius = (28f * liquidConfig.cornerRadiusFactor).dp
            val indicatorShape = { RoundedRectangle(indicatorCornerRadius) }

            Box(
                Modifier
                    .padding(horizontal = 4f.dp)
                    .graphicsLayer {
                        val baseTranslationX = if (isLtr) {
                            dampedDragAnimation.value * tabWidth + panelOffset
                        } else {
                            size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                        }
                        translationX = baseTranslationX + indicatorOffsetAdjustment
                    }
                    .then(interactiveHighlight.gestureModifier)
                    .then(dampedDragAnimation.modifier)
                    .drawBackdrop(
                        backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                        shape = indicatorShape,
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            lens(
                                refractionHeight = (liquidConfig.refractionHeightDp * (10f / 24f)).dp.toPx() * progress,
                                refractionAmount = (liquidConfig.refractionAmountDp * (14f / 24f)).dp.toPx() * progress,
                                depthEffect = liquidConfig.depthEffect,
                                chromaticAberration = liquidConfig.chromaticAberration > 0f
                            )
                        },
                        highlight = {
                            val progress = dampedDragAnimation.pressProgress
                            Highlight.Default.copy(alpha = (progress * liquidConfig.highlightIntensity).coerceIn(0f, 1f))
                        },
                        shadow = if (liquidConfig.shadowsEnabled) {
                            {
                                val progress = dampedDragAnimation.pressProgress
                                Shadow(alpha = progress)
                            }
                        } else null,
                        innerShadow = if (liquidConfig.innerShadowEnabled) {
                            {
                                val progress = dampedDragAnimation.pressProgress
                                InnerShadow(
                                    radius = 8f.dp * progress,
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
                            drawRect(
                                if (isLightTheme) Color.Black.copy(0.08f)
                                else Color.White.copy(0.08f),
                                alpha = 1f - progress
                            )
                            drawRect(Color.Black.copy(alpha = 0.03f * progress))
                        }
                    )
                    .height(56f.dp)
                    .width(with(density) { indicatorWidth.toDp() })
            )
        }
    }
}
