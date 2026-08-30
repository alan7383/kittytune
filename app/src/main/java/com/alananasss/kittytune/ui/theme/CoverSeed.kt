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
internal object CoverSeed {

    /**
     * @return the seed as an ARGB int, or null when there is nothing worth seeding from — no cover, a
     *   placeholder, or an image with no colour in it at all. Null means "keep the user's own key colour",
     *   which is a better answer than a grey approximation of a black sleeve.
     */
    suspend fun extract(context: Context, url: String?): Int? {
        if (url.isNullOrBlank()) return null
        // `fullResArtwork` invents a random picsum photo for tracks with no cover, and seeding the whole app
        // from a stranger's landscape is worse than falling back to the chosen colour.
        if (url.contains("picsum.photos")) return null

        val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, url) } ?: return null
        return withContext(Dispatchers.Default) { seedFrom(bitmap) }
    }

    private suspend fun loadBitmap(context: Context, url: String): Bitmap? = runCatching {
        val request = ImageRequest.Builder(context)
            .data(url)
            // Palette reads pixels back, which a hardware bitmap will not allow.
            .allowHardware(false)
            // Small on purpose: the seed is one colour, and decoding a 1000 px sleeve to find it is work
            // done on every track change for no gain.
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
