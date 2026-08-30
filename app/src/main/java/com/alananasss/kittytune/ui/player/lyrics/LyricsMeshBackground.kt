package com.alananasss.kittytune.ui.player.lyrics

import android.graphics.Color as AndroidColor
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.alananasss.kittytune.ui.theme.ThemeState
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * The palette the lyrics screen's animated background is built from.
 */
class LyricsMeshPalette(
    val base: Color,
    val mesh: List<Color>,
    val bright: Color = Color.White.copy(alpha = 0.96f),
    val dim: Color = Color.White.copy(alpha = 0.38f),
)

private data class Blob(val baseX: Float, val baseY: Float, val rate: Float, val phase: Float)

private const val WANDER = 0.40f
private const val BLOB_RADIUS = 0.85f
private const val MESH_CYCLE_MS = 26_000f
private const val COLOUR_TRAVEL_MS = 900

/**
 * Continuous smooth drift clock for the ambient background lights.
 */
@Composable
internal fun rememberMeshDrift(): Float {
    var elapsedSeconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastNanos != 0L) {
                    val dt = ((now - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.1f)
                    elapsedSeconds += dt
                }
                lastNanos = now
            }
        }
    }
    return elapsedSeconds / (MESH_CYCLE_MS / 1000f)
}

/**
 * Derives and animates the 5-color palette for the lyrics background from current artwork colors.
 */
@Composable
internal fun rememberLyricsMeshPalette(): LyricsMeshPalette {
    val cover = ThemeState.coverMeshColors
    val fallbackSeed = ThemeState.coverSeedColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    val target = remember(cover, fallbackSeed) {
        if (cover.size >= 2) cover.map { Color(it) } else spreadFrom(fallbackSeed)
    }

    val one by animateColorAsState(target[0], tween(COLOUR_TRAVEL_MS), label = "mesh1")
    val two by animateColorAsState(target.getOrElse(1) { target[0] }, tween(COLOUR_TRAVEL_MS), label = "mesh2")
    val three by animateColorAsState(target.getOrElse(2) { target[0] }, tween(COLOUR_TRAVEL_MS), label = "mesh3")
    val four by animateColorAsState(target.getOrElse(3) { target[0] }, tween(COLOUR_TRAVEL_MS), label = "mesh4")
    val five by animateColorAsState(target.getOrElse(4) { target[0] }, tween(COLOUR_TRAVEL_MS), label = "mesh5")

    return remember(one, two, three, four, five) {
        LyricsMeshPalette(
            base = five,
            mesh = listOf(one, two, three, four),
            bright = Color.White.copy(alpha = 0.96f),
            dim = Color.White.copy(alpha = 0.42f),
        )
    }
}

/**
 * 5 brightness steps of one color for fallback when artwork histogram is not yet ready.
 */
private fun spreadFrom(seed: Color): List<Color> {
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(
        (seed.red * 255f).toInt(),
        (seed.green * 255f).toInt(),
        (seed.blue * 255f).toInt(),
        hsv
    )
    val middle = hsv[2].coerceIn(0.30f, 0.52f)
    return listOf(1.38f, 1.12f, 0.90f, 0.70f, 0.52f).map { factor ->
        val adjustedHsv = floatArrayOf(
            hsv[0],
            max(hsv[1], 0.30f),
            (middle * factor).coerceIn(0.14f, 0.72f)
        )
        Color(AndroidColor.HSVToColor(adjustedHsv))
    }
}

/**
 * Draws the organic animated gradient mesh directly matching Desktop FullPlayer drawMesh logic.
 */
internal fun DrawScope.drawLyricsMesh(
    palette: LyricsMeshPalette,
    drift: Float,
) {
    val w = size.width
    val h = size.height
    val radius = max(size.minDimension * 1.0f, size.maxDimension * 0.58f)

    val lights = listOf(
        Blob(baseX = 0.78f, baseY = 0.12f, rate = 1.00f, phase = 0.00f),
        Blob(baseX = 0.20f, baseY = 0.74f, rate = 0.71f, phase = 0.31f),
        Blob(baseX = 0.92f, baseY = 0.66f, rate = 1.29f, phase = 0.63f),
        Blob(baseX = 0.10f, baseY = 0.22f, rate = 0.53f, phase = 0.86f),
    )

    lights.forEachIndexed { index, blob ->
        val angle = ((drift * blob.rate + blob.phase) * 2f * Math.PI).toFloat()
        val x = w * (blob.baseX + WANDER * cos(angle))
        val y = h * (blob.baseY + WANDER * 0.76f * sin(angle * 1.3f))
        drawRect(
            Brush.radialGradient(
                colors = listOf(palette.mesh[index % palette.mesh.size], Color.Transparent),
                center = Offset(x, y),
                radius = radius,
            )
        )
    }

    // Subtle edge blending for system bars
    drawRect(
        Brush.verticalGradient(
            0.0f to Color.Black.copy(alpha = 0.22f),
            0.06f to Color.Transparent,
            0.94f to Color.Transparent,
            1.0f to Color.Black.copy(alpha = 0.28f),
        )
    )
}

/**
 * Container composable that wraps lyrics content in the animated desktop-style ambient mesh background.
 */
@Composable
fun LyricsMeshBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.(palette: LyricsMeshPalette) -> Unit,
) {
    val palette = rememberLyricsMeshPalette()
    val drift = rememberMeshDrift()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.base)
            .drawBehind { drawLyricsMesh(palette, drift) }
    ) {
        content(palette)
    }
}
