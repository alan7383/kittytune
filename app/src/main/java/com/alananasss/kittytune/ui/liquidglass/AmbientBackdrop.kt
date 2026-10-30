package com.alananasss.kittytune.ui.liquidglass

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop
import kotlin.math.max

/**
 * The fallback backdrop behind every Liquid Glass surface.
 *
 * It is a canvas rather than a recorded layer on purpose:
 *
 * - it can be sampled from a Dialog, a Popup or a bottom sheet, where a RenderNode owned by the
 *   main window does not render;
 * - it is independent of the composition, so a surface can use it without creating a recursive
 *   backdrop tree.
 *
 * The colours are the theme's seed palette at low alpha over the background: enough variation for
 * the lens, the blur and the chromatic aberration to read as glass, low enough to keep text legible.
 */
private class AmbientBackdrop(
    private val background: Color,
    private val primary: Color,
    private val secondary: Color,
    private val tertiary: Color,
    private val isLight: Boolean,
) : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        drawRect(background)

        val width = size.width
        val height = size.height
        if (width <= 0f || height <= 0f) return

        // A diagonal wash of the three seed colours.
        drawRect(
            Brush.linearGradient(
                colors = listOf(
                    primary.copy(alpha = if (isLight) 0.30f else 0.40f),
                    Color.Transparent,
                    tertiary.copy(alpha = if (isLight) 0.20f else 0.30f),
                ),
                start = Offset(0f, 0f),
                end = Offset(width, height)
            )
        )

        // Two out-of-focus blobs give the lens something with curvature to refract.
        val firstCenter = Offset(width * 0.18f, height * 0.12f)
        val firstRadius = max(width, height) * 0.72f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    secondary.copy(alpha = if (isLight) 0.26f else 0.30f),
                    Color.Transparent
                ),
                center = firstCenter,
                radius = firstRadius
            ),
            radius = firstRadius,
            center = firstCenter
        )

        val secondCenter = Offset(width * 0.92f, height * 0.85f)
        val secondRadius = max(width, height) * 0.62f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = if (isLight) 0.22f else 0.34f),
                    Color.Transparent
                ),
                center = secondCenter,
                radius = secondRadius
            ),
            radius = secondRadius,
            center = secondCenter
        )
    }
}

/**
 * A stable ambient backdrop for the current theme.
 *
 * Keyed on the colour scheme, so light/dark and palette changes recreate it instead of leaving
 * glass components sampling the previous palette.
 */
@Composable
fun rememberAmbientBackdrop(): Backdrop {
    val scheme = MaterialTheme.colorScheme
    val isLight = scheme.background.luminance() > 0.5f
    return remember(scheme, isLight) {
        AmbientBackdrop(
            background = scheme.background,
            primary = scheme.primary,
            secondary = scheme.secondary,
            tertiary = scheme.tertiary,
            isLight = isLight
        )
    }
}

/**
 * The backdrop a glass component should sample: the host's ambient backdrop when one is provided,
 * otherwise this composition's own ambient canvas.
 */
@Composable
fun rememberGlassBackdrop(): Backdrop =
    LocalGlassBackdrop.current ?: rememberAmbientBackdrop()
