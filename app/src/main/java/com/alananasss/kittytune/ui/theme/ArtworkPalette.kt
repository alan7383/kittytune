package com.alananasss.kittytune.ui.theme

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Extracts a palette of distinct shades from an album artwork bitmap for the animated lyrics mesh background.
 *
 * Adapted from KittyTune Desktop's ArtworkPalette to Android.
 */
internal object ArtworkPalette {

    /** Bright enough to read as a light, dark enough that white text still sits on it. */
    private const val MESH_VALUE_MAX = 0.72f
    private const val MESH_VALUE_MIN = 0.14f

    /** Below this difference between the lightest and darkest shade there is no mesh to see. */
    private const val MESH_MIN_SPREAD = 0.30f

    /** Below this a colour reads as grey, and a grey mesh is the flat rectangle this exists to avoid. */
    private const val MESH_SATURATION_FLOOR = 0.30f

    /**
     * How far apart two shades have to be to count as two.
     */
    private const val MIN_SEPARATION = 0.14f

    /**
     * Extracts up to [count] distinct colors from [bitmap] for the animated mesh background.
     * Ordered brightest first, with the darkest shade placed last (as the ground).
     */
    fun meshPalette(bitmap: Bitmap, count: Int = 5): List<Int> {
        val buckets = histogram(bitmap)
        if (buckets.isEmpty()) return emptyList()

        val ranked = buckets.values
            .map { arr ->
                val r = arr[1] / arr[0]
                val g = arr[2] / arr[0]
                val b = arr[3] / arr[0]
                val hsv = FloatArray(3)
                AndroidColor.RGBToHSV(r, g, b, hsv)
                // Normalize hue to 0..1 for distance calculations
                hsv[0] = hsv[0] / 360f
                hsv to (arr[0] * (0.5f + hsv[1]))
            }
            .sortedByDescending { it.second }
            .map { it.first }

        val chosen = mutableListOf<FloatArray>()
        for (hsv in ranked) {
            if (chosen.size == count) break
            if (chosen.none { distance(it, hsv) < MIN_SEPARATION }) {
                chosen.add(hsv)
            }
        }

        var step = 1
        while (chosen.isNotEmpty() && chosen.size < count) {
            val base = chosen.first()
            chosen.add(floatArrayOf(base[0], base[1], (base[2] * (1f - step * 0.18f)).coerceAtLeast(0.14f)))
            step++
        }

        return spreadBrightness(chosen).map { hsv ->
            val sat = max(hsv[1], MESH_SATURATION_FLOOR)
            val colorHsv = floatArrayOf(hsv[0] * 360f, sat, hsv[2])
            AndroidColor.HSVToColor(colorHsv)
        }
    }

    private fun spreadBrightness(shades: List<FloatArray>): List<FloatArray> {
        if (shades.isEmpty()) return shades
        val clamped = shades
            .map { floatArrayOf(it[0], it[1], it[2].coerceIn(MESH_VALUE_MIN, MESH_VALUE_MAX)) }
            .sortedByDescending { it[2] }

        val high = clamped.first()[2]
        val low = clamped.last()[2]
        if (high - low >= MESH_MIN_SPREAD || clamped.size == 1) return clamped

        val mean = clamped.map { it[2] }.average().toFloat()
        val factor = MESH_MIN_SPREAD / (high - low).coerceAtLeast(0.01f)
        return clamped.map {
            floatArrayOf(it[0], it[1], (mean + (it[2] - mean) * factor).coerceIn(MESH_VALUE_MIN, MESH_VALUE_MAX))
        }
    }

    private fun distance(a: FloatArray, b: FloatArray): Float {
        val dh = abs(a[0] - b[0]).let { min(it, 1f - it) }
        val ds = abs(a[1] - b[1])
        val dv = abs(a[2] - b[2])
        return sqrt(dh * dh * 0.6f + ds * ds + dv * dv)
    }

    private fun histogram(bitmap: Bitmap): Map<Int, IntArray> {
        val targetW = min(bitmap.width, 64).coerceAtLeast(1)
        val targetH = min(bitmap.height, 64).coerceAtLeast(1)
        val scaled = if (bitmap.width != targetW || bitmap.height != targetH) {
            Bitmap.createScaledBitmap(bitmap, targetW, targetH, false)
        } else {
            bitmap
        }

        val pixels = IntArray(targetW * targetH)
        scaled.getPixels(pixels, 0, targetW, 0, 0, targetW, targetH)

        val buckets = HashMap<Int, IntArray>()
        val hsv = FloatArray(3)

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            AndroidColor.RGBToHSV(r, g, b, hsv)
            // Normalize hue to 0..1
            val hNorm = hsv[0] / 360f
            if (hsv[2] < 0.15f || (hsv[1] < 0.15f && hsv[2] > 0.9f)) continue

            val key = ((hNorm * 12).toInt().coerceIn(0, 11) shl 8) or
                    ((hsv[1] * 4).toInt().coerceIn(0, 3) shl 4) or
                    (hsv[2] * 4).toInt().coerceIn(0, 3)

            val arr = buckets.getOrPut(key) { IntArray(4) }
            arr[0]++
            arr[1] += r
            arr[2] += g
            arr[3] += b
        }

        if (scaled != bitmap) {
            scaled.recycle()
        }

        return buckets
    }
}
