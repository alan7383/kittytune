package com.alananasss.kittytune.domain

import com.alananasss.kittytune.data.spotify.SpotifyRepository

/**
 * Resolves where tapping a playlist's creator name should navigate.
 *
 * A playlist's creator is not necessarily an identity the app can open. The Spotify
 * community-playlist header is built as
 * `User(id = 0L, username = <owner name>, avatarUrl = <cover>)`: no `urn`, no
 * `permalink`, no numeric id. Routing on that name produced `profile:<name>`, and
 * the profile screen resolved it by searching Spotify for an artist of that name,
 * which opened an unrelated verified profile with its own listener count.
 *
 * The rule is therefore that a link needs a real identifier. A bare name yields
 * null, and the caller renders plain text instead of a link that guesses.
 *
 * Mirrors the desktop, which gates the same header block on `id > 0` and leaves the
 * name as non-interactive text otherwise.
 */
fun playlistCreatorNavId(creator: User?): String? {
    if (creator == null) return null

    // A Spotify artist urn names the exact artist; nothing to guess.
    val spotifyArtistUrn = creator.urn
        ?.takeIf { it.startsWith("spotify:artist:", ignoreCase = true) }
    if (spotifyArtistUrn != null) {
        val artistId = SpotifyRepository.extractId(spotifyArtistUrn)
        return if (artistId.isBlank()) null else "spotify_artist:$artistId"
    }

    // Any provider that exposes a real numeric id is safe to open directly.
    if (creator.id > 0L) return "profile:${creator.id}"

    // Name-only: not resolvable, so not linkable.
    return null
}