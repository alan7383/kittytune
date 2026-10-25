package com.alananasss.kittytune.data

import android.content.Context
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality
import com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider
import com.alananasss.kittytune.utils.NetworkUtils

/**
 * The one traffic switch. When it is active the whole resolution pipeline reads "eco"
 * without caring which source the track came from: SoundCloud drops to its smallest
 * transcoding, YouTube/NewPipe picks the smallest audio stream instead of the largest,
 * Qobuz / TIDAL / Deezer are pinned to the bottom rung of their quality ladders, covers
 * are swapped for light variants and animated artwork is skipped entirely.
 *
 * The switch can be limited to metered networks — then Wi-Fi keeps the user's own
 * quality choice and only mobile data gets the trimmed pipeline.
 *
 * Every consumer goes through [isActive] (or one of the `effective*` helpers), so a
 * single pref flip changes the behaviour of every source at once.
 */
object DataSaver {

    /**
     * Bottom rung of the Qobuz ladder (27 → 7 → 6 → 5): MP3 320 kbps. The provider's
     * own fallback machinery walks down from here, so nothing below this exists.
     */
    const val QOBUZ_ECO_QUALITY = 5

    /**
     * Whether the trimmed pipeline is in effect right now. The master switch alone is
     * not enough: if the user scoped the saver to metered networks, it only bites while
     * the device is actually on one.
     */
    fun isActive(context: Context): Boolean {
        // The Coil interceptor asks this on every artwork request in the app; minting a
        // PlayerPreferences each time was one allocation and a map lookup per image for
        // two booleans that live in the same in-memory SharedPreferences anyway.
        val prefs = prefs(context)
        if (!prefs.getDataSaverEnabled()) return false
        if (prefs.getDataSaverMeteredOnly() && !NetworkUtils.isMobileData(context)) return false
        return true
    }

    @Volatile private var saverPrefs: PlayerPreferences? = null

    private fun prefs(context: Context): PlayerPreferences =
        saverPrefs ?: PlayerPreferences(context.applicationContext).also { saverPrefs = it }

    // ─── Lyrics sources ──────────────────────────────────────────────────────────

    /**
     * True when lyrics lookups should stay inside the user's Data Saver allowlist.
     * Like [isActive], this only bites while the saver itself is in effect, so a
     * metered-only saver that is paused on Wi-Fi lets every lyrics source run again.
     */
    fun isLyricsRestrictionActive(context: Context): Boolean =
        isActive(context) && prefs(context).getDataSaverLyricsRestrictEnabled()

    /**
     * Keeps only the lyrics providers selected for Data Saver. The caller keeps its
     * own ordering and enabled-provider filtering; this is the last, narrowest gate.
     */
    fun restrictLyricsProviders(
        context: Context,
        providers: List<PreferredLyricsProvider>,
    ): List<PreferredLyricsProvider> {
        if (!isLyricsRestrictionActive(context)) return providers
        val allowed = prefs(context).getDataSaverLyricsProviders().toSet()
        return providers.filter { it in allowed }
    }

    /** Whether [provider] may be queried while Data Saver is active. */
    fun isLyricsProviderAllowed(context: Context, provider: PreferredLyricsProvider): Boolean =
        !isLyricsRestrictionActive(context) || provider in prefs(context).getDataSaverLyricsProviders()

    /** SoundCloud quality preference as the resolver should see it. */
    fun effectiveSoundCloudQuality(context: Context): String =
        if (isActive(context)) "LOW" else PlayerPreferences(context).getAudioQuality()

    /** True when the YouTube/NewPipe pickers should take the smallest stream, not the largest. */
    fun prefersSmallestStream(context: Context): Boolean = isActive(context)

    /** Qobuz quality the provider query should carry. */
    fun effectiveQobuzQuality(context: Context): Int =
        if (isActive(context)) QOBUZ_ECO_QUALITY else PlayerPreferences(context).getQobuzQuality()

    /** TIDAL quality the provider query should carry. */
    fun effectiveTidalQuality(context: Context): TidalAudioQuality =
        if (isActive(context)) TidalAudioQuality.AAC_320 else PlayerPreferences(context).getTidalAudioQuality()

    /** Deezer quality the provider query should carry. */
    fun effectiveDeezerQuality(context: Context): DeezerAudioQuality =
        if (isActive(context)) DeezerAudioQuality.MP3_128 else PlayerPreferences(context).getDeezerAudioQuality()

    // ─── Light artwork ───────────────────────────────────────────────────────────

    /** `-original`, `-t500x500` and `-t300x300` SoundCloud sizes collapse to `-t200x200`. */
    private val SOUND_CLOUD_SIZE = Regex("""-(original|t500x500|t300x300)\.""")

    /** Apple Music serves covers as `/…/<w>x<h>bb.jpg`; any size folds down to 200x200. */
    private val APPLE_SIZE = Regex("""\d+x\d+bb\.""")

    /**
     * Spotify encodes the size inside the image id: album/podcast covers use the
     * `ab67616d0000…` family (640 → 300 → 64) and artist portraits the
     * `ab6761610000…` family (640 → 320 → 160). Only the 640/1400 variants are
     * rewritten, so an already-small cover is left exactly as it is.
     */
    private val SPOTIFY_LARGE_SIZES = mapOf(
        // Album / track / podcast covers, JPEG and WebP.
        "ab67616d0000b273" to "ab67616d00001e02",
        "ab67616d0000d452" to "ab67616d00001e02",
        "ab67616d000082c1" to "ab67616d00001e02",
        "ab67616d0000a426" to "ab67616d0000ab87",
        "ab67616d0000aa54" to "ab67616d0000ab87",
        "ab67616d0000d619" to "ab67616d0000ab87",
        "ab67616d000075a0" to "ab67616d0000ab87",
        // Artist portraits.
        "ab6761610000e5eb" to "ab67616100005174",
        // Podcast shows.
        "ab6765630000ba8a" to "ab67656300005f1f",
    )

    /**
     * Rewrites a cover URL to its light variant while the data saver is active.
     *
     * Pure string work on purpose: no Context, no I/O, unit-testable on the JVM. The
     * Coil interceptor applies it to every image request in the app, which keeps the
     * UI call sites untouched — one choke point instead of a hundred AsyncImage edits.
     *
     * Non-http data (local files, drawables, ByteArrays) comes back untouched, and URLs
     * from services without a documented size scheme are left alone rather than broken.
     */
    fun lightArtwork(url: String?): String? {
        if (url.isNullOrBlank() || !url.startsWith("http")) return url
        var out: String = url
        when {
            url.contains("sndcdn.com") ->
                out = SOUND_CLOUD_SIZE.replace(out, "-t200x200.")
            url.contains("scdn.co") || url.contains("spotifycdn.com") -> {
                // Spotify has no resizing query parameter: the only way to get the light
                // cover is to swap the size block of a known family for its smaller one.
                for ((large, light) in SPOTIFY_LARGE_SIZES) {
                    if (out.contains(large)) {
                        out = out.replaceFirst(large, light)
                        break
                    }
                }
            }
            url.contains("dzcdn.net") ->
                out = out.replace("1000x1000", "200x200")
                    .replace("500x500", "200x200")
            url.contains("resources.tidal.com") ->
                out = out.replace("1280x1280", "320x320")
                    .replace("640x640", "320x320")
            url.contains("mzstatic.com") ->
                out = APPLE_SIZE.replace(out, "200x200bb.")
            url.contains("ytimg.com") ->
                out = out.replace("maxresdefault", "hqdefault")
                    .replace("sddefault", "hqdefault")
        }
        return out
    }
}
