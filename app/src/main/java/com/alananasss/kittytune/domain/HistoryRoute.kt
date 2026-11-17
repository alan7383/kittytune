package com.alananasss.kittytune.domain

/**
 * How a playback context is written down in the listening history.
 *
 * The history is what the user taps to go back where they were, so a row has to carry
 * the route of the page it describes: "profile:1345345683" for a SoundCloud artist,
 * "spotify_artist:..." for a Spotify one.
 *
 * A trailer breaks that rule. Playing an artist's teaser is not a page of its own: it
 * is the artist page with a clip on top, so the trailer wraps the route it was started
 * from ("trailer:profile:1345345683"). The wrapped route carries no id of its own, and
 * the id the row was given was a hash of the route — an id that names no SoundCloud
 * user. The artist was therefore written down twice: once under its real id, and once
 * under the hash. The copy with the hash pointed at nobody, and tapping it fell
 * through to the Spotify lookup, where a name that is only a substring of the artist's
 * name matched an unrelated artist.
 *
 * So a trailer is unwrapped before anything else looks at a route, and an id is only
 * ever read from a route that carries one.
 */
object HistoryRoute {

    /** The prefix a trailer wraps the route it was started from with. */
    const val TRAILER_PREFIX = "trailer:"

    /**
     * The route a context is really about: a trailer is the page it was started from.
     */
    fun unwrap(route: String): String =
        if (route.startsWith(TRAILER_PREFIX, ignoreCase = true)) {
            route.substring(TRAILER_PREFIX.length)
        } else {
            route
        }

    /**
     * The numeric id a route carries, or null when it carries none.
     *
     * "profile:1345345683" carries 1345345683. A Spotify id, a radio route or a
     * system playlist carries none, and must not be given a made-up one: the hash that
     * stood in for it is what pointed the history at artists nobody had visited.
     */
    fun numericId(route: String): Long? =
        unwrap(route).substringAfter(":").toLongOrNull()

    /**
     * The history id of a SoundCloud profile route, or null when the route is not one.
     *
     * The id in the route is the SoundCloud user id, so it is the only id the row may
     * carry: a row that says "profile:1345345683" opens that artist and nothing else.
     */
    fun soundCloudProfileId(route: String): String? {
        val unwrapped = unwrap(route)
        if (!unwrapped.startsWith("profile:", ignoreCase = true)) return null
        val id = unwrapped.substringAfter(":").toLongOrNull()?.takeIf { it > 0L } ?: return null
        return "profile:$id"
    }
}
