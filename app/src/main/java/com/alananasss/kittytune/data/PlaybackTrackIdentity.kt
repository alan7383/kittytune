package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.spotify.spotifyCatalogTrackId
import com.alananasss.kittytune.data.spotify.spotifyEntityId
import com.alananasss.kittytune.domain.Track
import kotlin.math.abs

/** Numeric IDs in the queue and database can be SoundCloud IDs or hashes of another catalog's IDs. */
internal data class PlaybackTrackKey(val source: String, val reference: String)

internal fun Track.playbackKey(): PlaybackTrackKey =
    playbackKey(id, source, permalinkUrl, permalink, spotifyCatalogTrackId())

private fun playbackKey(
    id: Long,
    source: String?,
    url: String?,
    permalink: String?,
    spotifyId: String?,
): PlaybackTrackKey {
    if (spotifyId != null) return PlaybackTrackKey("spotify", spotifyId)
    if (source == "youtube" || source == "youtube_music" || YoutubeAudioResolver.isYoutubeUrl(url)) {
        val canonical = YoutubeAudioResolver.videoUrl(url) ?: YoutubeAudioResolver.videoUrl(permalink)
        return PlaybackTrackKey("youtube", canonical?.substringAfter("v=") ?: id.toString())
    }

    val catalog = source?.takeIf { it.isNotBlank() } ?: if (id < 0L) "local" else "soundcloud"
    val reference = if (catalog == "soundcloud" || catalog == "local") id.toString() else {
        url?.substringBefore('?')?.trimEnd('/')?.takeIf { it.isNotBlank() }
            ?: permalink?.takeIf { it.isNotBlank() }
            ?: id.toString()
    }
    return PlaybackTrackKey(catalog, reference)
}

internal fun LocalTrack.matchesPlayback(track: Track): Boolean {
    if (id != track.id) return false
    val saved = playbackKey(id, source, permalinkUrl, null, spotifyEntityId(permalinkUrl, "track"))
    val requested = track.playbackKey()
    if (saved == requested) return true

    // Rows written before catalog references were stored can still be used if their metadata
    // identifies the same recording. Do not trust a coincidentally equal numeric ID.
    if (!permalinkUrl.isNullOrBlank() || (saved.source != requested.source && saved.source != "soundcloud")) return false
    val requestedDuration = track.durationMs ?: 0L
    return title.isNotBlank() && title.trim().equals(track.title?.trim(), ignoreCase = true) &&
        artist.isNotBlank() && artist.trim().equals(track.displayArtist.trim(), ignoreCase = true) &&
        duration > 0L && requestedDuration > 0L && abs(duration - requestedDuration) <= 5_000L
}
