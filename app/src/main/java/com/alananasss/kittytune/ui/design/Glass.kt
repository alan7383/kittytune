package com.alananasss.kittytune.ui.design

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.data.local.UiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.LocalLiquidGlassConfig
import com.alananasss.kittytune.ui.liquidglass.LocalUiDesignStyle
import com.alananasss.kittytune.ui.liquidglass.rememberGlassBackdrop
import com.alananasss.kittytune.ui.liquidglass.utils.InteractiveHighlight
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.RoundedRectangularShape
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tanh

/**
 * How much of the glass treatment a component gets.
 *
 * The same optical model is used everywhere, only the amount of blur/lens/highlight changes:
 * a small chip must not be as loud as a bottom sheet, and a text button should only show glass
 * while it is actually pressed.
 */
enum class GlassEmphasis {
    Subtle,
    Surface,
    Prominent,
}

/**
 * Draws a Liquid Glass surface behind this component, clipped to [shape].
 *
 * This is the single place where the design system decides what "glass" means. Components call it
 * with their own shape and container colour; they never hardcode blur radii, highlights or lens
 * strengths. When the design style is not [UiDesignStyle.LIQUID_GLASS] the modifier is a no-op, so
 * the Material component underneath is untouched.
 *
 * The surface is intentionally translucent: the tint is applied as a wash over the refracted
 * backdrop, not as an opaque fill, otherwise the optics would be invisible.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape,
    tint: Color,
    emphasis: GlassEmphasis = GlassEmphasis.Surface,
    enabled: Boolean = true,
    shadowElevation: Dp = 0.dp,
): Modifier {
    if (!enabled || LocalUiDesignStyle.current != UiDesignStyle.LIQUID_GLASS) return this

    val backdrop = rememberGlassBackdrop()
    val config = LocalLiquidGlassConfig.current

    val scheme = MaterialTheme.colorScheme
    val baseTint = if (tint.isSpecified && tint.alpha > 0.01f) tint else scheme.surface
    val tintAlpha = when (emphasis) {
        GlassEmphasis.Subtle -> 0.22f
        GlassEmphasis.Surface -> 0.38f
        GlassEmphasis.Prominent -> 0.55f
    } * (config.surfaceAlpha / 0.40f)

    // Preserve deliberately faint washes (e.g. a 12% primary chip) instead of forcing them opaque.
    val surfaceAlpha = min(tintAlpha, baseTint.alpha)
        .coerceIn(0.06f, 0.82f)

    val blurScale = when (emphasis) {
        GlassEmphasis.Subtle -> 0.45f
        GlassEmphasis.Surface -> 0.75f
        GlassEmphasis.Prominent -> 1f
    }
    val lensScale = when (emphasis) {
        GlassEmphasis.Subtle -> 0.30f
        GlassEmphasis.Surface -> 0.60f
        GlassEmphasis.Prominent -> 1f
    }
    // The lens shader only knows how to turn rounded rectangles into a signed distance field.
    // Custom outlines (stars, squiggles, generic paths) must keep the blur and the highlight but
    // skip the lens instead of crashing the screen.
    val supportsLens = shape is RoundedRectangularShape ||
        shape is AbsoluteRoundedCornerShape ||
        shape is CornerBasedShape

    val highlight = when (emphasis) {
        GlassEmphasis.Subtle -> Highlight.Plain.copy(alpha = (0.35f * config.highlightIntensity).coerceIn(0f, 1f))
        GlassEmphasis.Surface -> Highlight.Plain.copy(alpha = (0.55f * config.highlightIntensity).coerceIn(0f, 1f))
        GlassEmphasis.Prominent -> Highlight.Ambient.copy(alpha = (0.75f * config.highlightIntensity).coerceIn(0f, 1f))
    }

    return this.drawBackdrop(
        backdrop = backdrop,
        shape = { shape },
        effects = {
            if (config.vibrancyEnabled) {
                vibrancy()
            }
            blur((config.blurRadiusDp * blurScale).coerceAtLeast(1f).dp.toPx())
            if (supportsLens) {
                lens(
                    refractionHeight = (config.refractionHeightDp * lensScale).coerceAtLeast(0f).dp.toPx(),
                    refractionAmount = (config.refractionAmountDp * lensScale).coerceAtLeast(0f).dp.toPx(),
                    depthEffect = config.depthEffect,
                    chromaticAberration = emphasis == GlassEmphasis.Prominent &&
                        config.chromaticAberration > 0f
                )
            }
        },
        highlight = { highlight },
        shadow = if (config.shadowsEnabled && shadowElevation > 0.dp) {
            {
                Shadow(
                    radius = shadowElevation,
                    color = Color.Black.copy(alpha = 0.12f)
                )
            }
        } else {
            null
        },
        innerShadow = if (config.innerShadowEnabled && emphasis != GlassEmphasis.Subtle) {
            {
                InnerShadow(
                    radius = (if (emphasis == GlassEmphasis.Prominent) 6.dp else 4.dp),
                    alpha = 0.18f
                )
            }
        } else {
            null
        },
        onDrawSurface = {
            drawRect(baseTint.copy(alpha = surfaceAlpha))
        }
    )
}


/**
 * The interactive Backdrop button surface.
 *
 * This is the animation from backdrop's LiquidButton: the same [InteractiveHighlight] press spring,
 * finger offset, layer translation/squish and moving white highlight. It is applied behind a
 * Material button so the layout, text, semantics and click handling stay Material's, while the
 * glass moves exactly like the reference component.
 *
 * Disabled buttons get the static surface: there is nothing to track.
 */
@Composable
fun Modifier.interactiveGlassButton(
    shape: Shape,
    tint: Color,
    emphasis: GlassEmphasis = GlassEmphasis.Prominent,
    enabled: Boolean = true,
    shadowElevation: Dp = 8.dp,
    highlightAlpha: Float = 0.75f,
    pressExpansion: Dp = 4.dp,
): Modifier {
    if (LocalUiDesignStyle.current != UiDesignStyle.LIQUID_GLASS) return this
    if (!enabled) {
        return this.glassSurface(
            shape = shape,
            tint = tint,
            emphasis = emphasis,
            enabled = true,
            shadowElevation = shadowElevation
        )
    }

    val scope = rememberCoroutineScope()
    val interactiveHighlight = remember(scope) { InteractiveHighlight(scope) }
    val backdrop = rememberGlassBackdrop()
    val config = LocalLiquidGlassConfig.current

    val scheme = MaterialTheme.colorScheme
    val baseTint = if (tint.isSpecified && tint.alpha > 0.01f) tint else scheme.surface
    val tintAlpha = when (emphasis) {
        GlassEmphasis.Subtle -> 0.22f
        GlassEmphasis.Surface -> 0.38f
        GlassEmphasis.Prominent -> 0.55f
    } * (config.surfaceAlpha / 0.40f)
    val surfaceAlpha = min(tintAlpha, baseTint.alpha).coerceIn(0.06f, 0.82f)

    return this
        .drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (config.vibrancyEnabled) {
                    vibrancy()
                }
                blur((config.blurRadiusDp * 0.25f).coerceAtLeast(0f).dp.toPx())
                lens(
                    refractionHeight = (config.refractionHeightDp * 0.5f).coerceAtLeast(0f).dp.toPx(),
                    refractionAmount = config.refractionAmountDp.coerceAtLeast(0f).dp.toPx(),
                    depthEffect = config.depthEffect,
                    chromaticAberration = config.chromaticAberration > 0f
                )
            },
            highlight = { Highlight.Ambient.copy(alpha = (highlightAlpha * config.highlightIntensity).coerceIn(0f, 1f)) },
            shadow = if (config.shadowsEnabled && shadowElevation > 0.dp) {
                { Shadow(radius = shadowElevation, color = Color.Black.copy(alpha = 0.12f)) }
            } else null,
            innerShadow = if (config.innerShadowEnabled) {
                { InnerShadow(radius = 6.dp, alpha = 0.18f) }
            } else null,
            layerBlock = if (config.interactiveBounce) {
                {
                    val width = size.width
                    val height = size.height
                    if (width > 0f && height > 0f) {
                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + pressExpansion.toPx() / height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = pressExpansion.toPx() / height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).coerceAtMost(1f)
                        scaleY =
                            scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).coerceAtMost(1f)
                    }
                }
            } else null,
            onDrawSurface = {
                drawRect(baseTint.copy(alpha = surfaceAlpha))
            }
        )
        .then(interactiveHighlight.modifier)
        .then(interactiveHighlight.gestureModifier)
}


/**
 * The reference LiquidButton owns its own press feedback, so it disables the Material ripple.
 * Filled/Material buttons hand their indication to this no-op and let [InteractiveHighlight] draw
 * the moving light instead.
 */
private class NoIndicationNode : Modifier.Node(), DrawModifierNode {
    override fun ContentDrawScope.draw() {
        drawContent()
    }
}

private object NoIndication : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = NoIndicationNode()

    override fun hashCode(): Int = -1

    override fun equals(other: Any?): Boolean = other === this
}

@Composable
internal fun LiquidButtonIndication(
    liquid: Boolean,
    content: @Composable () -> Unit,
) {
    if (liquid) {
        CompositionLocalProvider(LocalIndication provides NoIndication, content = content)
    } else {
        content()
    }
}
