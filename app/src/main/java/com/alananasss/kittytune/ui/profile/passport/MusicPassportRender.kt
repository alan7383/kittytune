package com.alananasss.kittytune.ui.profile.passport

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.RoundedPolygon
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.stats.MusicPassport
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws a [MusicPassport] as one story-sized image (1080 × 1920): the passport's data page, edge to
 * edge — security-printed paper, a header in the app's own type and expressive shapes, a photo with
 * its ghost copy, the holder's signature, citizenships as little flags, inked visa and achievement
 * stamps, and a machine-readable zone with real check digits.
 *
 * Pure canvas, like the artist share card, so the preview on screen is the very image that is shared.
 */
object MusicPassportRender {

    private const val W = 1080
    private const val H = 1920
    private const val LEFT = 84f
    private const val RIGHT = 996f
    private const val MRZ_TOP = 1784f

    private val PAPER = Color.rgb(246, 241, 230)
    private val PAPER_DEEP = Color.rgb(236, 227, 209)
    private val INK = Color.rgb(30, 27, 36)
    private val INK_SOFT = Color.rgb(104, 98, 112)

    private class Fonts(val sans: Typeface, val mono: Typeface, val script: Typeface)

    /** The theme accent turned into the tones a printed document can use. */
    private class Palette(val ink: Int, val tint: Int, val stampInks: IntArray)

    fun render(context: Context, passport: MusicPassport, avatar: Bitmap?): Bitmap {
        val fonts = Fonts(
            sans = runCatching { ResourcesCompat.getFont(context, R.font.google_sans_flex) }.getOrNull()
                ?: Typeface.DEFAULT,
            mono = Typeface.MONOSPACE,
            script = Typeface.create("cursive", Typeface.NORMAL),
        )
        val palette = paletteFor(passport.accentArgb)
        val bitmap = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawPaper(canvas, palette)
        drawTitle(canvas, context.getString(R.string.passport_title), palette, fonts)
        drawHeaderRow(canvas, passport, palette, fonts)
        drawPhoto(canvas, passport, avatar, palette, fonts)
        drawFields(canvas, passport, avatar, palette, fonts)
        drawSignature(canvas, passport, palette, fonts)
        drawFigures(canvas, passport, palette, fonts)
        drawCitizenships(canvas, passport, palette, fonts)
        drawStamps(canvas, passport, palette, fonts)
        drawMrz(canvas, passport, fonts)
        return bitmap
    }

    // ── Paper ───────────────────────────────────────────────────────────────────────────────────

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun drawPaper(canvas: Canvas, p: Palette) {
        canvas.drawRect(0f, 0f, W.toFloat(), H.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, H.toFloat(), PAPER, PAPER_DEEP, Shader.TileMode.CLAMP)
        })

        // Large soft expressive shapes in the theme's tint, the way the recaps are decorated.
        val shape = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.tint }
        canvas.drawPath(polygonPath(MaterialShapes.Cookie12Sided, RectF(690f, -170f, 1190f, 330f), 14f), shape.apply { alpha = 120 })
        canvas.drawPath(polygonPath(MaterialShapes.Flower, RectF(-150f, 1460f, 230f, 1840f), -8f), shape.apply { alpha = 70 })

        // Guilloché: the interlaced wave line-work real passports are printed with against forgery.
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.1f; color = p.ink
        }
        val path = Path()
        for (family in 0..1) {
            line.alpha = if (family == 0) 20 else 12
            for (i in 0 until 80) {
                val y0 = -8f + i * 24f
                path.reset()
                var x = 0f
                while (x <= W + 8f) {
                    val y = if (family == 0) {
                        y0 + 8f * sin(x / 46f + i * 0.55f) + 3.5f * sin(x / 15f + i)
                    } else {
                        y0 + 12f + 9f * cos(x / 61f - i * 0.4f)
                    }
                    if (x == 0f) path.moveTo(x, y) else path.lineTo(x, y)
                    x += 8f
                }
                canvas.drawPath(path, line)
            }
        }
        // A rosette watermark behind the details.
        val rosette = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.2f; color = p.ink; alpha = 20
        }
        for (i in 0 until 26) {
            path.reset()
            val r = 40f + i * 10f
            var a = 0.0
            while (a <= Math.PI * 2 + 0.01) {
                val rr = r + 7f * sin(a * 8 + i * 0.35).toFloat()
                val x = 720f + (rr * cos(a)).toFloat()
                val y = 690f + (rr * sin(a)).toFloat()
                if (a == 0.0) path.moveTo(x, y) else path.lineTo(x, y)
                a += Math.PI / 90
            }
            canvas.drawPath(path, rosette)
        }
    }

    /** The document's own heading: an expressive emblem, the issuer, and the title in the app's type. */
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    private fun drawTitle(canvas: Canvas, title: String, p: Palette, f: Fonts) {
        val emblem = RectF(LEFT, 72f, LEFT + 112f, 184f)
        canvas.drawPath(polygonPath(MaterialShapes.Cookie9Sided, emblem, 0f), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.ink })
        // A record in the emblem.
        val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = PAPER }
        canvas.drawCircle(emblem.centerX(), emblem.centerY(), 30f, groove.apply { strokeWidth = 3f })
        canvas.drawCircle(emblem.centerX(), emblem.centerY(), 21f, groove.apply { strokeWidth = 1.5f; alpha = 170 })
        canvas.drawCircle(emblem.centerX(), emblem.centerY(), 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER })

        canvas.drawText("KITTYTUNE", LEFT + 140f, 118f, f.paint(24f, p.ink, weight = 800, spacing = 0.3f))
        canvas.drawText("OFFICIAL LISTENING DOCUMENT", LEFT + 140f, 154f, f.paint(19f, INK_SOFT, weight = 650, spacing = 0.16f))

        val heading = f.paint(96f, INK, weight = 820, spacing = -0.02f)
        canvas.drawText(ellipsize(title, heading, RIGHT - LEFT), LEFT, 296f, heading)
    }

    private fun drawHeaderRow(canvas: Canvas, passport: MusicPassport, p: Palette, f: Fonts) {
        canvas.drawLine(LEFT, 334f, RIGHT, 334f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.ink; alpha = 80; strokeWidth = 2f
        })
        val label = f.label(p)
        val value = f.paint(30f, INK, weight = 650)
        val cols = listOf(LEFT to ("TYPE" to "P"), 196f to ("CODE" to "KTN"), 340f to ("AUTHORITY" to "KITTYTUNE"))
        cols.forEach { (x, pair) ->
            canvas.drawText(pair.first, x, 380f, label)
            canvas.drawText(pair.second, x, 418f, value)
        }
        val rightLabel = f.label(p).apply { textAlign = Paint.Align.RIGHT }
        val rightValue = f.paint(30f, INK, weight = 700, spacing = 0.04f, align = Paint.Align.RIGHT)
        canvas.drawText("PASSPORT NO.", RIGHT, 380f, rightLabel)
        canvas.drawText(passport.passportNo, RIGHT, 418f, rightValue)
        canvas.drawLine(LEFT, 446f, RIGHT, 446f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.ink; alpha = 80; strokeWidth = 2f
        })
    }

    private fun drawPhoto(canvas: Canvas, passport: MusicPassport, avatar: Bitmap?, p: Palette, f: Fonts) {
        val rect = RectF(LEFT, 476f, LEFT + 300f, 836f)
        canvas.drawRoundRect(rect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = PAPER_DEEP
            setShadowLayer(16f, 0f, 6f, Color.argb(70, 0, 0, 0))
        })
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, 22f, 22f, Path.Direction.CW) })
        drawPortrait(canvas, rect, passport, avatar, p, f, grayscale = false)
        // A holographic sheen across the laminate.
        canvas.drawRect(rect, Paint().apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                intArrayOf(Color.TRANSPARENT, Color.argb(70, 255, 255, 255), Color.argb(30, 170, 210, 255), Color.TRANSPARENT),
                floatArrayOf(0.25f, 0.45f, 0.55f, 0.75f),
                Shader.TileMode.CLAMP,
            )
        })
        canvas.restore()
        canvas.drawRoundRect(rect, 22f, 22f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 2f; color = INK; alpha = 55
        })
    }

    /** The portrait, cropped to fill [rect]; the holder's initial on the ink colour without one. */
    private fun drawPortrait(
        canvas: Canvas, rect: RectF, passport: MusicPassport, avatar: Bitmap?,
        p: Palette, f: Fonts, grayscale: Boolean,
    ) {
        if (avatar != null) {
            val scale = maxOf(rect.width() / avatar.width, rect.height() / avatar.height)
            val matrix = Matrix().apply {
                setScale(scale, scale)
                postTranslate(rect.centerX() - avatar.width * scale / 2f, rect.centerY() - avatar.height * scale / 2f)
            }
            val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
                if (grayscale) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            }
            canvas.drawBitmap(avatar, matrix, paint)
        } else {
            canvas.drawRect(rect, Paint().apply {
                shader = LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    ColorUtils.blendARGB(p.ink, Color.WHITE, 0.25f), p.ink, Shader.TileMode.CLAMP,
                )
            })
            val letter = f.paint(rect.height() * 0.5f, Color.WHITE, weight = 800, align = Paint.Align.CENTER)
            val m = letter.fontMetrics
            canvas.drawText(
                passport.holderName.take(1).uppercase(),
                rect.centerX(), rect.centerY() - (m.ascent + m.descent) / 2f, letter,
            )
        }
    }

    private fun drawFields(canvas: Canvas, passport: MusicPassport, avatar: Bitmap?, p: Palette, f: Fonts) {
        val x = 424f
        val label = f.label(p)
        fun field(title: String, value: String, top: Float, size: Float, maxWidth: Float, fx: Float = x) {
            canvas.drawText(title, fx, top, label)
            val paint = f.paint(size, INK, weight = if (size > 40f) 750 else 650)
            canvas.drawText(fit(value, paint, maxWidth, size * 0.7f), fx, top + size + 6f, paint)
        }
        field("HOLDER", passport.holderName, 504f, 50f, RIGHT - x)
        field("NATIONALITY", passport.citizenships.firstOrNull()?.genre ?: "—", 616f, 36f, 440f)
        field("CLASS", passport.travellerClass, 714f, 36f, 440f)
        field("DATE OF ISSUE", stampDate(passport.issuedAtMs), 812f, 34f, 228f)
        field("EXPIRES", "NEVER", 812f, 34f, 200f, fx = 672f)

        // The ghost image: a small, faded second portrait, as on every modern passport.
        val ghost = RectF(884f, 604f, 980f, 728f)
        canvas.saveLayerAlpha(ghost, 85)
        canvas.clipPath(Path().apply { addRoundRect(ghost, 10f, 10f, Path.Direction.CW) })
        drawPortrait(canvas, ghost, passport, avatar, p, f, grayscale = true)
        canvas.restore()
    }

    private fun drawSignature(canvas: Canvas, passport: MusicPassport, p: Palette, f: Fonts) {
        val signature = f.paint(58f, INK, face = f.script).apply { alpha = 225 }
        canvas.drawText(ellipsize(passport.holderName, signature, 280f), LEFT + 14f, 904f, signature)
        canvas.drawLine(LEFT, 918f, LEFT + 300f, 918f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK; alpha = 90; strokeWidth = 1.5f
        })
        canvas.drawText("HOLDER'S SIGNATURE", LEFT, 946f, f.label(p).apply { textSize = 16f })
    }

    private fun drawFigures(canvas: Canvas, passport: MusicPassport, p: Palette, f: Fonts) {
        canvas.drawLine(LEFT, 980f, RIGHT, 980f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.ink; alpha = 90; strokeWidth = 2f; pathEffect = DashPathEffect(floatArrayOf(10f, 8f), 0f)
        })
        val format = NumberFormat.getIntegerInstance()
        val figures = listOf(
            format.format(passport.totalMinutes) to "MINUTES",
            format.format(passport.tracks) to "TRACKS",
            format.format(passport.artists) to "ARTISTS",
            format.format(passport.longestStreakDays) to "DAY STREAK",
        )
        val cell = (RIGHT - LEFT) / figures.size
        val number = f.paint(56f, INK, weight = 780, align = Paint.Align.CENTER)
        val label = f.label(p).apply { textAlign = Paint.Align.CENTER }
        val divider = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = p.ink; alpha = 50; strokeWidth = 1.5f }
        figures.forEachIndexed { i, (value, name) ->
            val cx = LEFT + cell * i + cell / 2f
            canvas.drawText(fit(value, number, cell - 16f, 36f), cx, 1056f, number)
            canvas.drawText(name, cx, 1092f, label)
            if (i > 0) canvas.drawLine(LEFT + cell * i, 1012f, LEFT + cell * i, 1098f, divider)
        }
    }

    private fun drawCitizenships(canvas: Canvas, passport: MusicPassport, p: Palette, f: Fonts) {
        drawSectionTitle(canvas, "CITIZENSHIPS", 1156f, p, f)
        if (passport.citizenships.isEmpty()) {
            canvas.drawText("Citizenship pending — keep listening", LEFT, 1220f, f.paint(28f, INK_SOFT))
            return
        }
        val colWidth = (RIGHT - LEFT) / 2f
        passport.citizenships.take(4).forEachIndexed { i, c ->
            val x = LEFT + (i % 2) * colWidth
            val top = 1182f + (i / 2) * 94f
            drawFlag(canvas, RectF(x, top + 8f, x + 88f, top + 66f), c.genre)
            val name = f.paint(32f, INK, weight = 720)
            canvas.drawText(fit(c.genre, name, colWidth - 124f, 24f), x + 108f, top + 36f, name)
            canvas.drawText("${c.sharePercent}% OF LISTENING", x + 108f, top + 66f, f.label(p).apply {
                color = INK_SOFT; textSize = 17f
            })
        }
    }

    /** A little national flag for a genre: three stripes in colours derived from its name. */
    private fun drawFlag(canvas: Canvas, rect: RectF, genre: String) {
        val hash = genre.lowercase().hashCode() and 0x7fffffff
        val hue = (hash % 360).toFloat()
        val colors = intArrayOf(
            hsl(hue, 0.58f, 0.44f),
            hsl(hue, 0.22f, 0.95f),
            hsl((hue + 38f) % 360f, 0.62f, 0.30f),
        )
        val horizontal = (hash / 360) % 2 == 0
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, 7f, 7f, Path.Direction.CW) })
        val stripe = Paint()
        colors.forEachIndexed { i, color ->
            stripe.color = color
            if (horizontal) {
                val h = rect.height() / 3f
                canvas.drawRect(rect.left, rect.top + h * i, rect.right, rect.top + h * (i + 1), stripe)
            } else {
                val w = rect.width() / 3f
                canvas.drawRect(rect.left + w * i, rect.top, rect.left + w * (i + 1), rect.bottom, stripe)
            }
        }
        canvas.drawRect(rect, Paint().apply {
            shader = LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                Color.argb(55, 255, 255, 255), Color.argb(25, 0, 0, 0), Shader.TileMode.CLAMP,
            )
        })
        canvas.restore()
        canvas.drawRoundRect(rect, 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 1.5f; color = INK; alpha = 60
        })
    }

    // ── Stamps ──────────────────────────────────────────────────────────────────────────────────

    private fun drawStamps(canvas: Canvas, passport: MusicPassport, p: Palette, f: Fonts) {
        drawSectionTitle(canvas, "VISAS & ENTRY STAMPS", 1404f, p, f)
        val visas = passport.visas.take(3)
        val stamps = passport.stamps.take(4)
        if (visas.isEmpty() && stamps.isEmpty()) {
            canvas.drawText("No stamps yet — your first trip awaits", LEFT, 1468f, f.paint(28f, INK_SOFT))
            return
        }
        // Spread over the whole width whatever their number: one band for a few, two rows for many.
        val total = visas.size + stamps.size
        val width = RIGHT - LEFT
        val positions: List<Pair<Float, Float>> = if (total <= 4) {
            val step = width / total
            List(total) { i -> LEFT + step * (i + 0.5f) to if (i % 2 == 0) 1560f else 1640f }
        } else {
            val visaStep = width / visas.size
            val stampStep = width / stamps.size
            List(visas.size) { i -> LEFT + visaStep * (i + 0.5f) to 1514f + (i % 2) * 14f } +
                List(stamps.size) { i -> LEFT + stampStep * (i + 0.5f) to 1680f - (i % 2) * 10f }
        }
        val rotations = floatArrayOf(-6f, 9f, -4f, 7f, -11f, 5f, -8f)
        var ink = 0
        visas.forEachIndexed { i, visa ->
            val (cx, cy) = positions[i]
            val color = p.stampInks[ink++ % p.stampInks.size]
            drawWorn(canvas, cx, cy, rotations[i], 262, 124, seed = i * 31 + 5) { c, x, y ->
                drawVisaStamp(c, x, y, 262f, 124f, visa, color, f)
            }
        }
        stamps.forEachIndexed { i, stamp ->
            val index = visas.size + i
            val (cx, cy) = positions[index]
            val color = p.stampInks[ink++ % p.stampInks.size]
            drawWorn(canvas, cx, cy, rotations[index], 172, 172, seed = i * 17 + 11) { c, x, y ->
                drawRoundStamp(c, x, y, 84f, stamp, color, f)
            }
        }
    }

    private fun drawVisaStamp(canvas: Canvas, cx: Float, cy: Float, w: Float, h: Float, visa: MusicPassport.Visa, color: Int, f: Fonts) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color }
        val outer = RectF(cx - w / 2f + 3f, cy - h / 2f + 3f, cx + w / 2f - 3f, cy + h / 2f - 3f)
        canvas.drawRoundRect(outer, 14f, 14f, stroke.apply { strokeWidth = 5f })
        val inner = RectF(outer.left + 9f, outer.top + 9f, outer.right - 9f, outer.bottom - 9f)
        canvas.drawRoundRect(inner, 8f, 8f, stroke.apply { strokeWidth = 2f })

        val room = inner.width() - 24f
        val name = f.paint(36f, color, weight = 850, spacing = 0.05f, align = Paint.Align.CENTER)
        canvas.drawText(fit(visa.platform.uppercase(), name, room, 22f), cx, cy - 8f, name)
        val small = f.paint(15f, color, weight = 750, spacing = 0.12f, align = Paint.Align.CENTER)
        canvas.drawText(fit("ENTRY ${visa.since}", small, room, 11f), cx, cy + 18f, small)
        val count = f.paint(18f, color, weight = 850, spacing = 0.08f, align = Paint.Align.CENTER)
        canvas.drawText(fit("${compact(visa.discoveries)} TRACKS ADMITTED", count, room, 12f), cx, cy + 42f, count)
    }

    private fun drawRoundStamp(canvas: Canvas, cx: Float, cy: Float, r: Float, stamp: MusicPassport.Stamp, color: Int, f: Fonts) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; this.color = color }
        canvas.drawCircle(cx, cy, r - 3f, stroke.apply { strokeWidth = 5f })
        canvas.drawCircle(cx, cy, r - 14f, stroke.apply { strokeWidth = 2f })

        // The achievement along the top of the ring, reading left to right.
        val top = f.paint(17f, color, weight = 850, spacing = 0.1f)
        val topRadius = r - 31f
        val sweep = 160f
        val topLength = (Math.toRadians(sweep.toDouble()) * topRadius).toFloat()
        val topText = fit(stamp.label, top, topLength, 12f)
        val topPath = Path().apply {
            addArc(RectF(cx - topRadius, cy - topRadius, cx + topRadius, cy + topRadius), 270f - sweep / 2f, sweep)
        }
        canvas.drawTextOnPath(topText, topPath, (topLength - top.measureText(topText)) / 2f, 0f, top)

        // The authority along the bottom, the right way up.
        val bottom = f.paint(13f, color, weight = 850, spacing = 0.32f)
        val bottomRadius = r - 25f
        val bottomSweep = 110f
        val bottomLength = (Math.toRadians(bottomSweep.toDouble()) * bottomRadius).toFloat()
        val bottomPath = Path().apply {
            addArc(RectF(cx - bottomRadius, cy - bottomRadius, cx + bottomRadius, cy + bottomRadius), 90f + bottomSweep / 2f, -bottomSweep)
        }
        canvas.drawTextOnPath("KITTYTUNE", bottomPath, (bottomLength - bottom.measureText("KITTYTUNE")) / 2f, 0f, bottom)

        // The date between two rules, like a dater stamp.
        val half = r - 40f
        val rule = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 2f }
        canvas.drawLine(cx - half, cy - 19f, cx + half, cy - 19f, rule)
        canvas.drawLine(cx - half, cy + 20f, cx + half, cy + 20f, rule)
        if (stamp.date.isNotBlank()) {
            val date = f.paint(19f, color, weight = 850, spacing = 0.02f, align = Paint.Align.CENTER)
            canvas.drawText(fit(stamp.date, date, half * 2f + 8f, 12f), cx, cy + 7f, date)
        } else {
            canvas.drawText("★", cx, cy + 12f, f.paint(32f, color, align = Paint.Align.CENTER))
        }
    }

    /**
     * Draws a stamp on its own layer, wears the ink — specks where the rubber missed and one side
     * pressed lighter — then sets it down rotated, so no two stamps look machine-perfect.
     */
    private fun drawWorn(
        canvas: Canvas, cx: Float, cy: Float, rotation: Float, w: Int, h: Int, seed: Int,
        draw: (Canvas, Float, Float) -> Unit,
    ) {
        val pad = 14
        val layer = Bitmap.createBitmap(w + pad * 2, h + pad * 2, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        draw(lc, layer.width / 2f, layer.height / 2f)

        val rnd = Random(seed)
        val erase = Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) }
        repeat(w * h / 70) {
            lc.drawCircle(rnd.nextFloat() * layer.width, rnd.nextFloat() * layer.height, 0.6f + rnd.nextFloat() * 1.9f, erase)
        }
        val fromLeft = rnd.nextBoolean()
        lc.drawRect(0f, 0f, layer.width.toFloat(), layer.height.toFloat(), Paint().apply {
            shader = LinearGradient(
                if (fromLeft) 0f else layer.width.toFloat(), 0f,
                if (fromLeft) layer.width.toFloat() else 0f, layer.height.toFloat(),
                Color.argb(0, 0, 0, 0), Color.argb(110, 0, 0, 0), Shader.TileMode.CLAMP,
            )
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        })

        canvas.save()
        canvas.rotate(rotation, cx, cy)
        canvas.drawBitmap(layer, cx - layer.width / 2f, cy - layer.height / 2f, Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 228 })
        canvas.restore()
        layer.recycle()
    }

    // ── Machine-readable zone ───────────────────────────────────────────────────────────────────

    private fun drawMrz(canvas: Canvas, passport: MusicPassport, f: Fonts) {
        canvas.drawRect(0f, MRZ_TOP, W.toFloat(), H.toFloat(), Paint().apply {
            color = ColorUtils.blendARGB(PAPER_DEEP, INK, 0.05f)
        })
        canvas.drawLine(0f, MRZ_TOP, W.toFloat(), MRZ_TOP, Paint().apply { color = INK; alpha = 40; strokeWidth = 1.5f })

        val (line1, line2) = mrzLines(passport)
        val paint = f.paint(34f, INK, face = f.mono, spacing = 0.06f)
        while (paint.measureText(line1) > RIGHT - LEFT && paint.textSize > 20f) paint.textSize -= 0.5f
        canvas.drawText(line1, LEFT, MRZ_TOP + 58f, paint)
        canvas.drawText(line2, LEFT, MRZ_TOP + 108f, paint)
    }

    /** Two 44-character lines laid out as a TD3 passport's, with ICAO 9303 check digits. */
    internal fun mrzLines(passport: MusicPassport): Pair<String, String> {
        val name = mrzText(passport.holderName)
        val line1 = ("P<KTN$name<<${mrzText(passport.travellerClass)}").padEnd(44, '<').take(44)
        val number = mrzText(passport.passportNo).replace("<", "").padEnd(9, '<').take(9)
        val issued = DateTimeFormatter.ofPattern("yyMMdd")
            .format(Instant.ofEpochMilli(passport.issuedAtMs).atZone(ZoneId.systemDefault()))
        val genre = mrzText(passport.citizenships.firstOrNull()?.genre.orEmpty())
        val body = "$number${checkDigit(number)}KTN$issued${checkDigit(issued)}X<<<<<<<$genre"
            .padEnd(43, '<').take(43)
        return line1 to body + checkDigit(body)
    }

    private fun mrzText(raw: String): String =
        java.text.Normalizer.normalize(raw.uppercase(), java.text.Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
            .filter { it.code < 128 }
            .map { if (it.isLetterOrDigit()) it else '<' }
            .joinToString("")

    private fun checkDigit(text: String): Int {
        val weights = intArrayOf(7, 3, 1)
        return text.mapIndexed { i, c ->
            val value = when (c) {
                in '0'..'9' -> c - '0'
                in 'A'..'Z' -> c - 'A' + 10
                else -> 0
            }
            value * weights[i % 3]
        }.sum() % 10
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────────

    /** "01 FEB 2025", the way a passport prints its dates. */
    private fun stampDate(ms: Long): String =
        DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH)
            .format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
            .uppercase(Locale.ENGLISH)

    /** An expressive Material shape as a canvas path, fitted into [target] and turned by [rotation] degrees. */
    private fun polygonPath(polygon: RoundedPolygon, target: RectF, rotation: Float): Path {
        val path = Path()
        polygon.cubics.forEachIndexed { i, c ->
            if (i == 0) path.moveTo(c.anchor0X, c.anchor0Y)
            path.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
        }
        path.close()
        val bounds = RectF()
        path.computeBounds(bounds, true)
        path.transform(Matrix().apply {
            setRectToRect(bounds, target, Matrix.ScaleToFit.CENTER)
            postRotate(rotation, target.centerX(), target.centerY())
        })
        return path
    }

    private fun paletteFor(accent: Int): Palette {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(accent, hsl)
        val hue = hsl[0]
        val sat = hsl[1]
        return Palette(
            ink = hsl(hue, sat.coerceIn(0.45f, 0.7f), 0.30f),
            tint = hsl(hue, sat.coerceIn(0.35f, 0.6f), 0.84f),
            stampInks = intArrayOf(
                hsl(hue, sat.coerceIn(0.5f, 0.75f), 0.36f),
                Color.rgb(176, 46, 54),
                Color.rgb(36, 84, 160),
                Color.rgb(30, 118, 84),
                Color.rgb(112, 60, 150),
                Color.rgb(184, 98, 34),
            ),
        )
    }

    private fun hsl(h: Float, s: Float, l: Float): Int = ColorUtils.HSLToColor(floatArrayOf(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f)))

    private fun drawSectionTitle(canvas: Canvas, text: String, baseline: Float, p: Palette, f: Fonts) {
        val paint = f.paint(20f, p.ink, weight = 820, spacing = 0.2f)
        canvas.drawText(text, LEFT, baseline, paint)
        val end = LEFT + paint.measureText(text) + 18f
        canvas.drawLine(end, baseline - 7f, RIGHT, baseline - 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = p.ink; alpha = 70; strokeWidth = 2f
        })
    }

    private fun Fonts.label(p: Palette): Paint = paint(19f, p.ink, weight = 750, spacing = 0.14f)

    private fun Fonts.paint(
        size: Float,
        color: Int,
        weight: Int = 400,
        face: Typeface = sans,
        spacing: Float = 0f,
        align: Paint.Align = Paint.Align.LEFT,
    ): Paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        typeface = face
        textSize = size
        this.color = color
        letterSpacing = spacing
        textAlign = align
        if (face === sans) fontVariationSettings = "'wght' $weight"
    }

    private fun compact(n: Int): String = when {
        n >= 1_000_000 -> "%.1fM".format(n / 1_000_000f)
        n >= 10_000 -> "%.1fK".format(n / 1_000f)
        else -> n.toString()
    }

    /** Shrinks [paint] until [text] fits [maxWidth], down to [minSize]; only then cuts it short. */
    private fun fit(text: String, paint: Paint, maxWidth: Float, minSize: Float): String {
        while (paint.measureText(text) > maxWidth && paint.textSize > minSize) paint.textSize -= 0.5f
        return ellipsize(text, paint, maxWidth)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var cut = text.length
        while (cut > 1 && paint.measureText(text.take(cut) + "…") > maxWidth) cut--
        return text.take(cut) + "…"
    }
}
