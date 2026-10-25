package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.domain.Track

/**
 * Which tracks can seed a radio, and whose radio it is.
 *
 * Every provider the app plays has a station of its own: SoundCloud has a track
 * station, Spotify a seed radio, YouTube Music a mix, TIDAL a track radio, Deezer
 * an artist radio and Qobuz an album/artist radio. What must never happen is a
 * catalogue id being handed to SoundCloud's `track-stations`: the ids share the
 * numeric space, and the station that comes back has nothing to do with the track
 * — the "Cine n-are dor" filled with Arabic songs bug.
 *
 * Defence lives here so menu visibility, autoplay and the station fetch all
 * agree on the same answer, and so each provider is asked for its own radio
 * instead of somebody else's.
 */
object RadioEligibility {

    enum class Kind {
        /** A SoundCloud track station. */
        SOUND_CLOUD,
        /** A Spotify seed radio. */
        SPOTIFY,
        /** A YouTube Music mix. */
        YOUTUBE,
        /** A TIDAL track radio. */
        TIDAL,
        /** A Deezer artist radio. */
        DEEZER,
        /** A Qobuz album/artist radio. */
        QOBUZ,
        /** No radio at all. */
        NONE,
    }

    private val URN_PROVIDERS = listOf(
        "deezer:" to "deezer",
        "tidal:" to "tidal",
        "qobuz:" to "qobuz",
        "spotify:" to "spotify",
        "spotify_" to "spotify",
        "station_spotify:" to "spotify",
        "youtube:" to "youtube",
        "yt_radio:" to "youtube",
    )

    private val HOST_PROVIDERS = listOf(
        "deezer.com" to "deezer",
        "tidal.com" to "tidal",
        "qobuz.com" to "qobuz",
        "spotify.com" to "spotify",
        "spotify.link" to "spotify",
        "youtube.com" to "youtube",
        "youtu.be" to "youtube",
    )

    /**
     * Every signal the track carries about where it comes from. A track's source
     * alone is not enough: a catalogue track that lost its source still says
     * where it belongs through its permalink or the artist's urn.
     */
    private fun providersOf(track: Track): Set<String> {
        val providers = mutableSetOf<String>()
        track.source?.lowercase()?.trim()?.takeIf { it.isNotBlank() }?.let { providers.add(it) }
        listOfNotNull(track.permalink, track.user?.urn, track.permalinkUrl).forEach { marker ->
            val value = marker.lowercase()
            URN_PROVIDERS.firstOrNull { (prefix, _) -> value.startsWith(prefix) }?.let { providers.add(it.second) }
            HOST_PROVIDERS.firstOrNull { (host, _) -> value.contains(host) }?.let { providers.add(it.second) }
        }
        return providers
    }

    fun radioKind(track: Track?): Kind {
        if (track == null) return Kind.NONE
        val providers = providersOf(track)
        return when {
            "tidal" in providers -> Kind.TIDAL
            "deezer" in providers -> Kind.DEEZER
            "qobuz" in providers -> Kind.QOBUZ
            "spotify" in providers -> Kind.SPOTIFY
            "youtube" in providers -> Kind.YOUTUBE
            else -> Kind.SOUND_CLOUD
        }
    }

    fun canStartRadio(track: Track?): Boolean = radioKind(track) != Kind.NONE

    fun isRadioUnavailable(track: Track?): Boolean = radioKind(track) == Kind.NONE
}
