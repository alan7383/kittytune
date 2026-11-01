package com.alananasss.kittytune.ui.widget.pet

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import com.alananasss.kittytune.R
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A per-pixel coat: for every coat pixel of the head, body and tail, whether it is plain coat ('0'),
 * markings ('1') or white ('2'). This is how a photo's little details (a white patch next to one
 * eye, a half-dark face…) survive into the sprite, and what the pixel editor paints on.
 */
data class PetCoat(val head: String, val body: String, val tail: String) {

    fun roleHead(r: Int, c: Int) = head.getOrNull(r * PetCoatPresets.HEAD_W + c) ?: '0'
    fun roleBody(r: Int, c: Int) = body.getOrNull(r * PetCoatPresets.BODY_W + c) ?: '0'
    fun roleTail(i: Int) = tail.getOrNull(i.coerceIn(0, tail.length - 1)) ?: '0'

    fun withHead(r: Int, c: Int, role: Char) = copy(head = head.replaceAt(r * PetCoatPresets.HEAD_W + c, role))
    fun withBody(r: Int, c: Int, role: Char) = copy(body = body.replaceAt(r * PetCoatPresets.BODY_W + c, role))
    fun withTail(i: Int, role: Char) = copy(tail = tail.replaceAt(i, role))

    fun serialize() = "$head|$body|$tail"

    companion object {
        fun parse(value: String?): PetCoat? {
            val parts = value?.split('|') ?: return null
            if (parts.size != 3) return null
            if (parts[0].length != PetCoatPresets.HEAD_W * PetCoatPresets.HEAD_H) return null
            if (parts[1].length != PetCoatPresets.BODY_W * PetCoatPresets.BODY_H) return null
            return PetCoat(parts[0], parts[1], parts[2])
        }

        fun fromPreset(pattern: PetPattern): PetCoat {
            val p = PetCoatPresets.presets[pattern.name] ?: PetCoatPresets.presets.getValue(PetPattern.CLASSIC.name)
            return PetCoat(p.first, p.second, p.third)
        }

        /** Coat pixel of the head sprite (where a role can apply). */
        fun isHeadCoat(r: Int, c: Int) = PetCoatPresets.HEAD_SHAPE.getOrNull(r * PetCoatPresets.HEAD_W + c).let { it == 'W' || it == 'S' }
        fun isBodyCoat(r: Int, c: Int) = PetCoatPresets.BODY_SHAPE.getOrNull(r * PetCoatPresets.BODY_W + c).let { it == 'W' || it == 'S' }
    }
}

private fun String.replaceAt(i: Int, ch: Char): String =
    if (i !in indices) this else StringBuilder(this).also { it.setCharAt(i, ch) }.toString()

/** Draws the markings / white layers of a frame for a custom coat (tinted later like the presets). */
internal object PetCoatRenderer {
    const val PART_HEAD = 0
    const val PART_BODY = 1
    const val PART_LOAF = 2
    const val PART_TAIL = 3
    const val PART_PAW = 4

    /** Visible coat pixels of a frame: x, y, part, r, c packed 5 bytes per pixel. */
    private var partmap: Map<String, ByteArray>? = null

    fun partmap(context: Context): Map<String, ByteArray> {
        partmap?.let { return it }
        val bytes = context.resources.openRawResource(R.raw.pet_partmap).use { it.readBytes() }
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val out = HashMap<String, ByteArray>()
        val frames = buf.short.toInt() and 0xFFFF
        repeat(frames) {
            val nameLen = buf.get().toInt() and 0xFF
            val name = ByteArray(nameLen).also { buf.get(it) }.toString(Charsets.US_ASCII)
            val n = buf.short.toInt() and 0xFFFF
            out[name] = ByteArray(n * 5).also { buf.get(it) }
        }
        partmap = out
        return out
    }

    fun roleAt(coat: PetCoat, part: Int, r: Int, c: Int): Char = when (part) {
        PART_HEAD -> coat.roleHead(r, c)
        PART_BODY -> coat.roleBody(r, c)
        // the loaf is the body seen lying down: map it onto the body grid
        PART_LOAF -> coat.roleBody(minOf(PetCoatPresets.BODY_H - 1, r * PetCoatPresets.BODY_H / 8), c * (PetCoatPresets.BODY_W - 1) / 29)
        PART_TAIL -> coat.roleTail(r)
        PART_PAW -> coat.roleBody(5, 3)
        else -> '0'
    }

    private val cache = object : LinkedHashMap<String, Pair<Bitmap?, Bitmap?>>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Bitmap?, Bitmap?>>?) = size > 48
    }

    /**
     * (markings, white) masks for [frame]. [scale] pixels per sprite pixel. [alphaOnly] gives
     * ALPHA_8 bitmaps (4× lighter, what the widget sends), otherwise white ARGB for Compose.
     */
    @Synchronized
    fun layers(context: Context, frame: String, coat: PetCoat, scale: Int, alphaOnly: Boolean): Pair<Bitmap?, Bitmap?> {
        val key = "$frame|${coat.hashCode()}|$scale|$alphaOnly"
        cache[key]?.let { return it }
        val entries = partmap(context)[frame] ?: return null to null
        val config = if (alphaOnly) Bitmap.Config.ALPHA_8 else Bitmap.Config.ARGB_8888
        var mark: Bitmap? = null
        var white: Bitmap? = null
        var markCanvas: Canvas? = null
        var whiteCanvas: Canvas? = null
        val paint = Paint().apply { color = Color.WHITE; isAntiAlias = false }
        var i = 0
        while (i < entries.size) {
            val x = entries[i].toInt() and 0xFF
            val y = entries[i + 1].toInt() and 0xFF
            val part = entries[i + 2].toInt()
            val r = entries[i + 3].toInt()
            val c = entries[i + 4].toInt()
            i += 5
            val canvas = when (roleAt(coat, part, r, c)) {
                '1' -> markCanvas ?: Canvas(Bitmap.createBitmap(46 * scale, 32 * scale, config).also { mark = it }).also { markCanvas = it }
                '2' -> whiteCanvas ?: Canvas(Bitmap.createBitmap(46 * scale, 32 * scale, config).also { white = it }).also { whiteCanvas = it }
                else -> null
            } ?: continue
            canvas.drawRect(
                (x * scale).toFloat(), (y * scale).toFloat(),
                ((x + 1) * scale).toFloat(), ((y + 1) * scale).toFloat(), paint
            )
        }
        val result = mark to white
        cache[key] = result
        return result
    }
}
