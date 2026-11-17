package com.alananasss.kittytune.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * The colour the whole palette is seeded from, taken off the cover that is playing (issue #33).
 *
 * The desktop app has done this for a while and this side had not: `rememberDynamicColorScheme` was already
 * in place, and `rememberDominantColor` already existed — but nothing joined them, so the theme followed the
 * wallpaper or the key colour and never the music. One swatch was all that was missing.
 *
 * Which swatch matters. `vibrantSwatch` is the one a palette is worth seeding from: it is the colour a person
 * would name if asked what colour the cover is. `dominantSwatch` is frequently the background — a sleeve that
 * is mostly black seeds a grey theme from a cover nobody would call grey. So vibrant first, then its muted
 * cousins, and dominant only as a last resort.
 */
internal data class ArtworkColors(
    val seedColor: Int?,
    val meshColors: List<Int> = emptyList()
)

internal object CoverSeed {

    /**
     * @return the seed as an ARGB int, or null when there is nothing worth seeding from.
     */
    suspend fun extract(context: Context, url: String?): Int? {
        return extractColors(context, url).seedColor
    }

    /**
     * Extracts both the primary dynamic theme seed color and the multi-shade mesh palette for the lyrics background.
     */
    suspend fun extractColors(context: Context, url: String?): ArtworkColors {
        if (url.isNullOrBlank()) return ArtworkColors(null, emptyList())
        if (url.contains("picsum.photos")) return ArtworkColors(null, emptyList())

        val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, url) }
            ?: return ArtworkColors(null, emptyList())

        return withContext(Dispatchers.Default) {
            val seed = seedFrom(bitmap)
            val mesh = ArtworkPalette.meshPalette(bitmap)
            ArtworkColors(seedColor = seed, meshColors = mesh)
        }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            // Palette reads pixels back, which a hardware bitmap will not allow.
            .allowHardware(false)
            // Small on purpose: few enough pixels to be fast, enough for stable color histogram & palette
            .size(SAMPLE_PX)
            .build()
        (context.imageLoader.execute(request).drawable as? BitmapDrawable)?.bitmap
    }.getOrNull()

    private suspend fun seedFrom(bitmap: Bitmap): Int? = suspendCancellableCoroutine { cont ->
        runCatching {
            Palette.from(bitmap).clearFilters().generate { palette ->
                val swatch = palette?.vibrantSwatch
                    ?: palette?.lightVibrantSwatch
                    ?: palette?.darkVibrantSwatch
                    ?: palette?.mutedSwatch
                    ?: palette?.dominantSwatch
                if (cont.isActive) cont.resume(swatch?.rgb)
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    /** Enough pixels for a stable answer, few enough to be free. */
    private const val SAMPLE_PX = 96
}
