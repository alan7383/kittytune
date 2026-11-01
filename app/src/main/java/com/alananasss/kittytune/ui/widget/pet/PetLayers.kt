package com.alananasss.kittytune.ui.widget.pet

import android.content.Context
import android.graphics.Bitmap

/**
 * A layer of a pet frame: a vector resource, or a bitmap for custom coats, and the tint to apply
 * to it (null = draw as is). res = 0 and no bitmap means "nothing on this layer".
 */
internal data class PetLayer(val res: Int, val tint: Int?, val bitmap: Bitmap? = null)

internal object PetLayers {
    const val COUNT = 8

    /**
     * The 8 stacked layers of [frame] for this look: fur, shade, marks, white, eyes, ink, hat, neck.
     * Custom coats render their marks/white masks at [bitmapScale]; [alphaOnly] for the widget.
     */
    fun of(context: Context, frame: String, s: PetSettings, bitmapScale: Int = 1, alphaOnly: Boolean = false): List<PetLayer> {
        val f = PetFrames.frames.getValue(frame)
        val p = s.palette
        // the user's accessory always wins; otherwise the season may lend one (beanie, Santa hat…)
        val hatId = if (s.hat != PetHat.NONE) s.hat.id else PetBrain.seasonalHat(s)
        val neckId = if (s.neck != PetNeck.NONE) s.neck.id else PetBrain.seasonalNeck(s)
        val hat = if (f.hatOk && hatId != null) PetFrames.hats[hatId]?.getOrNull(f.accKey) ?: 0 else 0
        val neck = if (f.neckOk && neckId != null) PetFrames.necks[neckId]?.getOrNull(f.accKey) ?: 0 else 0
        val markLayer: PetLayer
        val whiteLayer: PetLayer
        if (s.pattern == PetPattern.CUSTOM && s.coat != null) {
            val (m, w) = PetCoatRenderer.layers(context, frame, s.coat, bitmapScale, alphaOnly)
            markLayer = PetLayer(0, p.mark, m)
            whiteLayer = PetLayer(0, p.white, w)
        } else {
            val pi = s.pattern.ordinal.coerceAtMost(f.marks.size - 1)
            markLayer = PetLayer(f.marks[pi], p.mark)
            whiteLayer = PetLayer(f.whites[pi], p.white)
        }
        return listOf(
            PetLayer(f.fur, p.fur),
            PetLayer(f.shade, p.shade),
            markLayer,
            whiteLayer,
            PetLayer(f.eye, s.eyeColor),
            PetLayer(f.ink, null),
            PetLayer(hat, null),
            PetLayer(neck, null),
        )
    }

    /** Wall colour behind the room for [room], honouring the user's wall style (null = Material You). */
    fun wallColor(room: String, s: PetSettings): Long? = when (s.wall) {
        PetWall.AUTO -> PetFrames.wallColors.getValue(room)
        PetWall.MATERIAL -> null
        else -> s.wall.color
    }

    fun isDark(color: Long): Boolean {
        val r = (color shr 16 and 0xFF) / 255.0
        val g = (color shr 8 and 0xFF) / 255.0
        val b = (color and 0xFF) / 255.0
        return 0.2126 * r + 0.7152 * g + 0.0722 * b < 0.45
    }
}
