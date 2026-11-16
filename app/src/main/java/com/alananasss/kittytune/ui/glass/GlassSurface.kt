package com.alananasss.kittytune.ui.glass

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.alananasss.kittytune.ui.glass.motion.InteractiveHighlight
import com.alananasss.kittytune.ui.glass.motion.applyGlassPress
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.BackdropEffectScope
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.emptyBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import com.kyant.shapes.Capsule
import com.kyant.shapes.RoundedRectangularShape
import kotlin.math.min

/**
 * The thickness of a glass surface. Every component in the app is one of these four; the optics
 * (blur, lens, colour controls, highlight) are decided here and nowhere else.
 */
enum class GlassLevel {
    /** Nearly invisible material: outlined and text buttons, the rim is what you see. */
    Clear,

    /** Buttons, chips, cards and other controls. */
    Regular,

    /** Floating navigation and the mini player, over busy scrolling content. */
    Chrome,

    /** Dialogs, sheets and menus: blurred enough that text on them is always legible. */
    Thick,
}

enum class GlassShadow { None, Soft, Floating }

/** The smooth (G2-continuous) shape glass should use for [shape]. */
@Composable
fun rememberGlassShape(shape: Shape): Shape = remember(shape) {
    when (shape) {
        CircleShape -> Capsule()
        else -> shape
    }
}

private fun Shape.supportsLens(): Boolean =
    this is RoundedRectangularShape || this is CornerBasedShape

/**
 * Draws a Liquid Glass surface behind this node, following the user's [GlassSettings].
 *
 * - [backdrop] is what the glass refracts; see [LocalGlassBackdrop]. Without one, the surface is
 *   pure material: tint, rim light, shadow and press optics over the real pixels underneath.
 * - [tint] colours the glass the way iOS tints a prominent button: the backdrop takes the tint's
 *   hue, then a 75 % wash of the tint is laid over it.
 * - [highlight] makes it interactive: it swells and leans under the finger, and a light follows it.
 * - [visibility] fades the whole material in and out, for controls whose glass only appears while
 *   they are pressed.
 * - [exportedBackdrop] records the surface itself so glass children can refract it.
 */
@Composable
fun Modifier.glass(
    shape: Shape,
    level: GlassLevel = GlassLevel.Regular,
    backdrop: Backdrop? = LocalGlassBackdrop.current,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    highlight: InteractiveHighlight? = null,
    pressExpansion: Dp = 4.dp,
    shadow: GlassShadow = GlassShadow.None,
    visibility: (() -> Float)? = null,
    exportedBackdrop: LayerBackdrop? = null,
    extraLayer: (GraphicsLayerScope.() -> Unit)? = null,
): Modifier {
    val settings = LocalGlassSettings.current
    val colors = rememberGlassColors()
    val glassShape = rememberGlassShape(shape)
    val lensable = glassShape.supportsLens() && glassShape != RectangleShape
    val hasBackdrop = backdrop != null && isGlassBlurSupported
    val refraction = settings.refraction.scale
    val chromatic = settings.chromaticAberration
    val isDark = colors.isDark

    val baseSurface = when {
        surfaceColor.isSpecified -> surfaceColor
        else -> when (level) {
            GlassLevel.Clear -> colors.clear
            GlassLevel.Regular -> colors.surface
            GlassLevel.Chrome -> colors.chrome
            GlassLevel.Thick -> colors.thick
        }
    }

    val effects: BackdropEffectScope.() -> Unit = if (!hasBackdrop) {
        {}
    } else {
        {
            val v = visibility?.invoke() ?: 1f
            val minDimension = size.minDimension
            when (level) {
                GlassLevel.Thick -> {
                    colorControls(brightness = if (isDark) 0f else 0.2f, saturation = 1.5f)
                    blur((if (isDark) 8f else 16f).dp.toPx())
                    if (lensable && isGlassLensSupported) {
                        lens(
                            refractionHeight = min(24f.dp.toPx() * refraction, minDimension / 2f),
                            refractionAmount = min(48f.dp.toPx() * refraction, minDimension),
                            depthEffect = true,
                        )
                    }
                }

                GlassLevel.Chrome -> {
                    vibrancy()
                    blur(8f.dp.toPx())
                    if (lensable && isGlassLensSupported) {
                        lens(
                            refractionHeight = min(24f.dp.toPx() * refraction, minDimension * 0.4f),
                            refractionAmount = min(24f.dp.toPx() * refraction, minDimension * 0.5f),
                            chromaticAberration = chromatic,
                        )
                    }
                }

                GlassLevel.Regular, GlassLevel.Clear -> {
                    vibrancy()
                    blur((if (level == GlassLevel.Clear) 1f else 2f).dp.toPx() * v)
                    if (lensable && isGlassLensSupported) {
                        lens(
                            refractionHeight = min(12f.dp.toPx() * refraction, minDimension * 0.3f) * v,
                            refractionAmount = min(24f.dp.toPx() * refraction, minDimension * 0.5f) * v,
                            chromaticAberration = chromatic && highlight != null,
                        )
                    }
                }
            }
        }
    }

    val rimHighlight: (() -> Highlight?)? = if (!settings.specularHighlights) {
        null
    } else {
        {
            val v = visibility?.invoke() ?: 1f
            when (level) {
                GlassLevel.Thick -> Highlight.Plain
                GlassLevel.Clear -> Highlight.Default.copy(alpha = 0.8f * v)
                else -> Highlight.Default.copy(alpha = v)
            }.takeIf { v > 0.001f }
        }
    }

    val dropShadow: (() -> Shadow?)? = when (shadow) {
        GlassShadow.None -> null
        GlassShadow.Soft -> {
            {
                val v = visibility?.invoke() ?: 1f
                Shadow(radius = 10.dp, color = Color.Black.copy(alpha = if (isDark) 0.18f else 0.06f), alpha = v)
            }
        }
        GlassShadow.Floating -> {
            { Shadow(radius = 24.dp, color = Color.Black.copy(alpha = if (isDark) 0.28f else 0.10f)) }
        }
    }

    val pressShadow: (() -> InnerShadow?)? = if (highlight != null && level != GlassLevel.Thick) {
        {
            val p = highlight.pressProgress
            if (p > 0.001f) InnerShadow(radius = 6.dp * p, alpha = 0.5f * p) else null
        }
    } else {
        null
    }

    val motion = settings.interactiveMotion
    val layerBlock: (GraphicsLayerScope.() -> Unit)? = when {
        highlight != null && motion && extraLayer != null -> {
            {
                applyGlassPress(highlight, pressExpansion)
                extraLayer()
            }
        }
        highlight != null && motion -> { { applyGlassPress(highlight, pressExpansion) } }
        else -> extraLayer
    }

    return this
        .drawBackdrop(
            backdrop = if (hasBackdrop) backdrop!! else emptyBackdrop(),
            shape = { glassShape },
            effects = effects,
            highlight = rimHighlight,
            shadow = dropShadow,
            innerShadow = pressShadow,
            layerBlock = layerBlock,
            exportedBackdrop = exportedBackdrop,
            onDrawSurface = {
                val v = visibility?.invoke() ?: 1f
                if (v <= 0.001f) return@drawBackdrop
                if (tint.isSpecified) {
                    if (hasBackdrop) drawRect(tint, blendMode = BlendMode.Hue, alpha = v)
                    drawRect(tint.copy(alpha = tint.alpha * if (hasBackdrop) 0.78f else 0.92f), alpha = v)
                } else {
                    drawRect(baseSurface, alpha = v)
                }
            }
        )
        .then(if (highlight != null && motion) highlight.modifier else Modifier)
}

/** A press highlight remembered in this composition, or `null` when motion is turned off. */
@Composable
fun rememberGlassHighlight(enabled: Boolean = true): InteractiveHighlight? {
    val scope = rememberCoroutineScope()
    val motion = LocalGlassSettings.current.interactiveMotion
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    return if (enabled && motion) highlight else null
}
