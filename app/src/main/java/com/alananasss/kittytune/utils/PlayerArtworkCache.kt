package com.alananasss.kittytune.utils

import android.content.Context
import java.io.File

/**
 * Where the player's per-track artwork JPEGs live.
 *
 * The player writes one small re-encoded JPEG per played track so the home-screen widget and a
 * few UI spots can show art without re-decoding the full-resolution source. These files are fully
 * regenerable from the track's own artwork URL, so they belong in the cache directory: the system
 * "clear cache" and the app's storage screen can then shrink them, which keeps long listening
 * sessions from piling up megabytes per track in the permanent app storage.
 *
 * Downloaded tracks keep their art next to the download in the files directory (their lifecycle
 * is the download's own), which is why lookups fall back there.
 */
object PlayerArtworkCache {

    private const val DIR_NAME = "player_art"
    private const val PREFIX = "art_"

    /** The cache-side file for [trackId], regenerable at any time. */
    fun cacheFile(context: Context, trackId: Long): File =
        File(File(context.cacheDir, DIR_NAME), "$PREFIX$trackId.jpg")

    /** The files-side file for [trackId], written by and owned by the download pipeline. */
    fun downloadedFile(context: Context, trackId: Long): File =
        File(context.filesDir, "$PREFIX$trackId.jpg")

    /**
     * The best available artwork path for [trackId]: the regenerable cache copy first, then the
     * download's own copy. Null when neither exists.
     */
    fun pathOrNull(context: Context, trackId: Long): String? {
        cacheFile(context, trackId).takeIf { it.exists() }?.let { return it.absolutePath }
        downloadedFile(context, trackId).takeIf { it.exists() }?.let { return it.absolutePath }
        return null
    }
}
