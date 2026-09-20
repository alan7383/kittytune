package com.alananasss.kittytune.ui.share

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.Log
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.google.zxing.qrcode.encoder.Encoder

/**
 * Draws a track's artwork and its link as one square: a QR code whose modules are cut from the
 * cover.
 *
 * The appeal is that the artwork is the code rather than sitting next to one, and the risk is
 * the same thing: a code that carries an image is a code that can stop scanning. Three choices
 * keep it readable.
 *
 * The modules are filled with the cover but forced dark, and the ground is forced light, so the
 * contrast a scanner needs does not depend on which cover it is - a pale cover would otherwise
 * produce light modules on a light ground and fail outright.
 *
 * The three finder patterns are drawn solid instead of textured. A scanner locates the code by
 * those squares before it reads anything, so texture there costs far more than it adds.
 *
 * Error correction runs at H, which recovers about 30% of the code, and the centre disc where
 * the cover shows through unaltered is kept well inside that budget.
 */
object QrCoverRenderer {

    private const val TAG = "QrCoverRenderer"

    /** Quiet zone in modules. Four is what the spec asks for; less and scanners start missing it. */
    private const val QUIET_ZONE = 4

    /** Side of the centre disc, as a fraction of the code. Stays within the H-level budget. */
    private const val CENTRE_FRACTION = 0.26f

    /** How far the module fill is pushed towards black, so a pale cover still reads as dark. */
    private const val MODULE_DARKEN = 0.45f

    /** Luminance band a pixel has to fall in for halftone to have room to push it either way. */
    private const val MID_LOW = 0.15f
    private const val MID_HIGH = 0.85f

    /** How much of the cover must sit in that band before halftone is worth the loss in contrast. */
    private const val HALFTONE_THRESHOLD = 0.60f

    /** Pixels sampled when judging a cover. Enough to be representative, few enough to be instant. */
    private const val SAMPLE_STEPS = 64

    /**
     * How the cover and the code are combined.
     *
     * [SOLID] keeps the code's own contrast and lets the artwork texture the modules. [HALFTONE]
     * shows the cover whole and carries the code in a grid of dots, which is the better picture
     * and the harder scan.
     */
    enum class CoverCodeStyle { SOLID, HALFTONE }

    /**
     * Picks the style this cover can carry.
     *
     * Halftone works by forcing one dot per module to black or white while the rest of the
     * artwork stays as it is, so it needs a cover that has somewhere to be pushed. A picture
     * already crushed to black or blown to white has none: every forced dot lands far from its
     * surroundings, which both looks like damage and leaves the scanner sampling a speck against
     * a field of the opposite value. Judged by how much of the cover sits in the middle of the
     * luminance range, where there is room in both directions.
     */
    fun chooseStyle(cover: Bitmap?): CoverCodeStyle {
        val bitmap = cover ?: return CoverCodeStyle.SOLID
        return try {
            val stepX = (bitmap.width / SAMPLE_STEPS).coerceAtLeast(1)
            val stepY = (bitmap.height / SAMPLE_STEPS).coerceAtLeast(1)
            var mid = 0
            var total = 0
            var y = 0
            while (y < bitmap.height) {
                var x = 0
                while (x < bitmap.width) {
                    val p = bitmap.getPixel(x, y)
                    val l = (0.2126f * Color.red(p) + 0.7152f * Color.green(p) + 0.0722f * Color.blue(p)) / 255f
                    if (l in MID_LOW..MID_HIGH) mid++
                    total++
                    x += stepX
                }
                y += stepY
            }
            val share = if (total == 0) 0f else mid.toFloat() / total
            Log.d(TAG, "Cover mid-tone share %.2f -> %s".format(share, if (share >= HALFTONE_THRESHOLD) "HALFTONE" else "SOLID"))
            if (share >= HALFTONE_THRESHOLD) CoverCodeStyle.HALFTONE else CoverCodeStyle.SOLID
        } catch (e: Exception) {
            Log.w(TAG, "Could not judge the cover, falling back to the robust style", e)
            CoverCodeStyle.SOLID
        }
    }

    /**
     * Renders [content] as a QR code textured with [cover].
     *
     * Returns null rather than a half-drawn square when the content cannot be encoded, so the
     * caller can fall back to the plain cover instead of sharing a code that scans as nothing.
     */
    fun render(
        content: String,
        cover: Bitmap?,
        sizePx: Int,
        groundColor: Int = Color.WHITE,
        moduleColor: Int = Color.BLACK,
        style: CoverCodeStyle = chooseStyle(cover),
    ): Bitmap? {
        if (content.isBlank() || sizePx <= 0) return null

        val matrix = try {
            val hints = mapOf(EncodeHintType.CHARACTER_SET to "UTF-8")
            Encoder.encode(content, ErrorCorrectionLevel.H, hints).matrix
        } catch (e: Exception) {
            Log.w(TAG, "Could not encode '$content' as a QR code", e)
            return null
        } ?: return null

        val modules = matrix.width
        val total = modules + QUIET_ZONE * 2

        if (style == CoverCodeStyle.HALFTONE && cover != null) {
            return renderHalftone(matrix, modules, total, cover, sizePx, groundColor, moduleColor)
        }

        // Snap the module size to whole pixels: a fractional one leaves seams between modules
        // that a scanner reads as noise.
        val moduleSize = (sizePx / total).coerceAtLeast(1)
        val side = moduleSize * total
        val origin = (moduleSize * QUIET_ZONE).toFloat()

        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(groundColor)

        val modulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = moduleColor
            cover?.let {
                shader = darkenedShader(it, side)
                colorFilter = darkenFilter()
            }
        }
        val solidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = moduleColor }

        val radius = moduleSize * 0.5f
        val rect = RectF()

        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (matrix.get(x, y).toInt() != 1) continue
                if (isFinder(x, y, modules)) continue
                if (isCentre(x, y, modules)) continue

                val left = origin + x * moduleSize
                val top = origin + y * moduleSize
                rect.set(left, top, left + moduleSize, top + moduleSize)
                // Rounded modules read as artwork rather than print, and a scanner samples the
                // centre of each cell, which a rounded corner does not touch.
                canvas.drawRoundRect(rect, radius, radius, modulePaint)
            }
        }

        drawFinders(canvas, modules, moduleSize, origin, solidPaint, groundColor)
        if (cover != null) drawCentre(canvas, cover, side, groundColor)

        return out
    }

    /**
     * Draws the cover whole, and carries the code in one forced dot per module.
     *
     * A scanner samples the centre of a module and nothing else, so only that centre has to hold
     * the value. Each module is split three by three and the middle ninth is forced to black or
     * white; the other eight keep the artwork. The reader gets the exact value at every point it
     * looks at, while the eye sees the cover behind a fine grid of dots.
     *
     * The quiet zone stays plain: a border of artwork is the one thing that reliably stops a
     * code from being found at all, whatever the modules do.
     */
    private fun renderHalftone(
        matrix: com.google.zxing.qrcode.encoder.ByteMatrix,
        modules: Int,
        total: Int,
        cover: Bitmap,
        sizePx: Int,
        groundColor: Int,
        moduleColor: Int,
    ): Bitmap {
        // Each module is three sub-cells wide, and all of them whole pixels: a dot landing half
        // on a pixel boundary is a dot the scanner reads as grey.
        val subSize = (sizePx / (total * 3)).coerceAtLeast(1)
        val moduleSize = subSize * 3
        val side = moduleSize * total
        val origin = (moduleSize * QUIET_ZONE).toFloat()
        val codeSide = (moduleSize * modules).toFloat()

        val out = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(groundColor)

        val coverPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val scale = codeSide / minOf(cover.width, cover.height).toFloat()
                setLocalMatrix(
                    Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            origin - (cover.width * scale - codeSide) / 2f,
                            origin - (cover.height * scale - codeSide) / 2f,
                        )
                    }
                )
            }
        }
        canvas.drawRect(origin, origin, origin + codeSide, origin + codeSide, coverPaint)

        val darkDot = Paint().apply { color = moduleColor }
        val lightDot = Paint().apply { color = groundColor }

        for (y in 0 until modules) {
            for (x in 0 until modules) {
                if (isFinder(x, y, modules)) continue
                val isDark = matrix.get(x, y).toInt() == 1
                val left = origin + x * moduleSize + subSize
                val top = origin + y * moduleSize + subSize
                // Square, not round: the sampled area is a cell, and a circle inside it leaves
                // the corners to the artwork, which drags the average back towards the cover.
                canvas.drawRect(
                    left,
                    top,
                    left + subSize,
                    top + subSize,
                    if (isDark) darkDot else lightDot,
                )
            }
        }

        drawFinders(canvas, modules, moduleSize, origin, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = moduleColor }, groundColor)
        return out
    }

    /**
     * A shader that paints the cover across the whole code, pushed towards black.
     *
     * Scaled to the code rather than tiled per module, so each module shows the part of the
     * artwork it sits over and the cover stays recognisable across the square.
     */
    private fun darkenedShader(cover: Bitmap, side: Int): Shader {
        val shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        val scale = side.toFloat() / minOf(cover.width, cover.height).toFloat()
        shader.setLocalMatrix(
            Matrix().apply {
                setScale(scale, scale)
                postTranslate(
                    (side - cover.width * scale) / 2f,
                    (side - cover.height * scale) / 2f,
                )
            }
        )
        return shader
    }

    /** Multiplies the fill towards black so the modules stay dark whatever the cover. */
    private fun darkenFilter(): ColorMatrixColorFilter {
        val k = 1f - MODULE_DARKEN
        return ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    k, 0f, 0f, 0f, 0f,
                    0f, k, 0f, 0f, 0f,
                    0f, 0f, k, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
        )
    }

    private fun isFinder(x: Int, y: Int, modules: Int): Boolean {
        val near = 7
        return (x < near && y < near) ||
            (x >= modules - near && y < near) ||
            (x < near && y >= modules - near)
    }

    private fun isCentre(x: Int, y: Int, modules: Int): Boolean {
        val half = (modules * CENTRE_FRACTION) / 2f
        val mid = modules / 2f
        return kotlin.math.abs(x + 0.5f - mid) < half && kotlin.math.abs(y + 0.5f - mid) < half
    }

    /**
     * Draws the three corner squares solid, as rounded frames with a hole.
     *
     * These are what a scanner looks for first, so they get the full contrast and none of the
     * texture the rest of the code carries.
     */
    private fun drawFinders(
        canvas: Canvas,
        modules: Int,
        moduleSize: Int,
        origin: Float,
        paint: Paint,
        groundColor: Int,
    ) {
        val hole = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = groundColor }
        val corners = listOf(0 to 0, modules - 7 to 0, 0 to modules - 7)
        val rect = RectF()

        corners.forEach { (cx, cy) ->
            val left = origin + cx * moduleSize
            val top = origin + cy * moduleSize
            val outer = (moduleSize * 7).toFloat()

            rect.set(left, top, left + outer, top + outer)
            canvas.drawRoundRect(rect, moduleSize * 1.6f, moduleSize * 1.6f, paint)

            rect.inset(moduleSize.toFloat(), moduleSize.toFloat())
            canvas.drawRoundRect(rect, moduleSize * 1.1f, moduleSize * 1.1f, hole)

            rect.inset(moduleSize.toFloat(), moduleSize.toFloat())
            canvas.drawRoundRect(rect, moduleSize * 0.8f, moduleSize * 0.8f, paint)
        }
    }

    /** Places the cover, unaltered, in the disc left clear at the centre. */
    private fun drawCentre(canvas: Canvas, cover: Bitmap, side: Int, groundColor: Int) {
        val discSide = side * CENTRE_FRACTION
        val left = (side - discSide) / 2f
        val rect = RectF(left, left, left + discSide, left + discSide)

        // A ring of ground around the disc keeps the artwork from touching the nearest modules,
        // which would blur the boundary a scanner samples.
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = groundColor }
        val ringRect = RectF(rect).apply { inset(-side * 0.012f, -side * 0.012f) }
        canvas.drawRoundRect(ringRect, ringRect.width() * 0.22f, ringRect.width() * 0.22f, ring)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            shader = BitmapShader(cover, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                val scale = discSide / minOf(cover.width, cover.height).toFloat()
                setLocalMatrix(
                    Matrix().apply {
                        setScale(scale, scale)
                        postTranslate(
                            rect.left - (cover.width * scale - discSide) / 2f,
                            rect.top - (cover.height * scale - discSide) / 2f,
                        )
                    }
                )
            }
        }
        canvas.drawRoundRect(rect, discSide * 0.2f, discSide * 0.2f, paint)
    }
}
