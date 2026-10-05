package com.alananasss.kittytune.data.spotify

/**
 * Strict normalization of any Spotify reference into a bare entity id.
 *
 * KittyTune reaches the Spotify layer from many directions: web URLs pasted by
 * users, `spotify:` URNs from the SoundCloud/KittyTune APIs, and the app's own
 * internal routes (`spotify_playlist:`, `station_spotify:`, ...). Each screen used
 * to normalize its own input with an ad-hoc chain of `String.replace` calls before
 * handing it to the repository. That is how `spotify:playlist:X` became
 * `playlist:X` and was then sent upstream as `spotify:playlist:playlist:X`: the
 * chain stripped the `spotify:` prefix but left the `playlist:` one, and no
 * validation caught the result. Spotify answered with a name-less, content-less
 * shell which the app rendered as an empty playlist.
 *
 * This object is the single normalization point, so an id is either a valid bare
 * entity id or it is rejected before any network call. It follows the same shape
 * as SpotifyScraper's `urls.py`: a typed parse returning the entity type next to
 * the id, with the id validated against the base62 shape Spotify actually issues.
 *
 * Two entry points, deliberately different in strictness:
 *
 *  - [parse] / [resolve] validate the base62 id shape. Use these before talking
 *    to Spotify, so a malformed reference is a loud rejection rather than a
 *    silent empty screen.
 *  - [stripPrefixes] only removes routing prefixes and makes no claim about the
 *    result being a Spotify id. Some routes legitimately carry a SoundCloud
 *    numeric id that is later handed to a radio lookup, so those callers keep
 *    working through this lenient path.
 */
object SpotifyIds {

    /** The Spotify entity kinds this app fetches. */
    enum class EntityType(val urnSegment: String) {
        TRACK("track"),
        ALBUM("album"),
        ARTIST("artist"),
        PLAYLIST("playlist"),
        EPISODE("episode"),
        SHOW("show");

        companion object {
            fun fromSegment(segment: String): EntityType? =
                entries.firstOrNull { it.urnSegment.equals(segment, ignoreCase = true) }
        }
    }

    /** Outcome of parsing a Spotify reference. */
    sealed interface Result {
        /** The reference was understood: [id] is a bare base62 entity id. */
        data class Resolved(val id: String, val type: EntityType?) : Result

        /** The reference was malformed; [reason] explains why, for logs. */
        data class Invalid(val reason: String) : Result
    }

    /**
     * Spotify entity ids are base62 and always 22 characters. The length is part
     * of the contract, not cosmetic: it is what distinguishes a real id from a
     * SoundCloud numeric id, which is also base62 and would otherwise validate.
     */
    private const val ID_LENGTH = 22
    private val ID_REGEX = Regex("^[0-9A-Za-z]{$ID_LENGTH}$")
    private val INTL_SEGMENT = Regex("^intl-[A-Za-z]+(-[A-Za-z]+)?$")
    private val SPOTIFY_HOSTS = setOf("open.spotify.com", "play.spotify.com")

    /**
     * Every internal route prefix, mapped to the entity type it implies.
     *
     * Order matters: stripping takes the first match, so longer and more specific
     * prefixes must come first. `spotify:user:spotify:playlist:` has to precede
     * `spotify:user:`, which has to precede `spotify:playlist:`.
     */
    private val INTERNAL_PREFIXES: List<Pair<String, EntityType?>> = listOf(
        "spotify:user:spotify:playlist:" to EntityType.PLAYLIST,
        "spotify:user:" to null,
        "spotify:station:track:" to EntityType.TRACK,
        "spotify:station:artist:" to EntityType.ARTIST,
        "spotify:track:" to EntityType.TRACK,
        "spotify:album:" to EntityType.ALBUM,
        "spotify:artist:" to EntityType.ARTIST,
        "spotify:playlist:" to EntityType.PLAYLIST,
        "spotify:episode:" to EntityType.EPISODE,
        "spotify:show:" to EntityType.SHOW,
        "spotify_track:" to EntityType.TRACK,
        "spotify_album:" to EntityType.ALBUM,
        "spotify_artist:" to EntityType.ARTIST,
        "spotify_playlist:" to EntityType.PLAYLIST,
        "spotify_radio:" to null,
        "station_spotify:" to null,
        "station_artist:" to EntityType.ARTIST,
        "station:" to EntityType.TRACK,
        // Bare entity prefixes. A caller that already stripped "spotify:" leaves
        // exactly these behind; they were the precise shape that broke playlist
        // loading before this object existed.
        "playlist:" to EntityType.PLAYLIST,
        "album:" to EntityType.ALBUM,
        "track:" to EntityType.TRACK,
        "artist:" to EntityType.ARTIST,
        "episode:" to EntityType.EPISODE,
        "show:" to EntityType.SHOW,
        "profile:" to EntityType.ARTIST
    )

    /**
     * Classifies [value] and returns its bare entity id, rejecting anything that
     * is not a well-formed Spotify reference.
     *
     * @param typeHint entity type to assume for a bare id, which carries no type
     *   of its own. May be null for references that embed their own type.
     */
    fun parse(value: String, typeHint: EntityType? = null): Result {
        val text = value.trim()
        if (text.isEmpty()) return Result.Invalid("empty reference")

        if (isEntityId(text)) return Result.Resolved(id = text, type = typeHint)

        // Internal app routes ("spotify_playlist:", "playlist:", "station_spotify:",
        // ...) carry no scheme, so the URI parser does not match them, and a compound
        // route like "spotify:user:spotify:playlist:X" has too many segments for it
        // anyway. Handle every prefixed route in one place: strip to the bare id,
        // then validate. Validation is what stops a route that does not collapse to
        // a real id from reaching Spotify as `spotify:playlist:playlist:X`.
        //
        // Gated on a known prefix so a plain URL is never salvaged by taking its last
        // path segment, which would let a non-Spotify host wrongly resolve.
        if (matchesInternalPrefix(text)) {
            val stripped = stripPrefixes(text)
            if (isEntityId(stripped)) {
                return Result.Resolved(id = stripped, type = entityTypeOf(text, typeHint))
            }
            return Result.Invalid("'$value' does not carry a valid Spotify entity id")
        }

        parseUri(text)?.let { return it }
        parseUrl(text, typeHint)?.let { return it }
        return Result.Invalid("not a Spotify id, URI or URL: '$value'")
    }

    private fun matchesInternalPrefix(text: String): Boolean =
        INTERNAL_PREFIXES.any { (prefix, _) -> text.startsWith(prefix, ignoreCase = true) }

    /**
     * The entity type implied by a route's own prefix, if it names one.
     *
     * Only consulted for internal routes. A URL is never routed through here: its
     * type comes from its path, and a non-Spotify host must stay rejected rather
     * than be salvaged by taking its last path segment.
     */
    private fun entityTypeOf(route: String, fallback: EntityType?): EntityType? =
        INTERNAL_PREFIXES.firstOrNull { (prefix, type) ->
            type != null && route.startsWith(prefix, ignoreCase = true)
        }?.second ?: fallback

    /** Returns the bare entity id, or null when [value] is not a valid reference. */
    fun resolve(value: String, typeHint: EntityType? = null): String? =
        when (val result = parse(value, typeHint)) {
            is Result.Resolved -> result.id
            is Result.Invalid -> null
        }

    /**
     * Removes routing prefixes without validating the remaining string.
     *
     * For call sites whose payload is not necessarily a Spotify id: a
     * `station_artist:` route carries a SoundCloud numeric id that is later fed to
     * a radio lookup. Stripping repeats until the string stops shrinking, so a
     * route that already carries a full URN collapses to the bare id rather than
     * leaving `playlist:30KI6...` behind.
     */
    fun stripPrefixes(value: String): String {
        var remaining = value.trim()
        if (remaining.isEmpty()) return ""

        while (true) {
            val prefix = INTERNAL_PREFIXES.firstOrNull { (candidate, _) ->
                remaining.startsWith(candidate, ignoreCase = true)
            }?.first ?: break
            remaining = remaining.substring(prefix.length)
        }

        // A `spotify:` scheme with no recognized type segment, e.g. "spotify:X".
        if (remaining.startsWith("spotify:", ignoreCase = true)) {
            remaining = remaining.substring("spotify:".length)
        }

        // Web URLs: drop query params and keep the last path segment.
        if (remaining.contains('/')) {
            remaining = remaining.substringBefore('?').trimEnd('/').substringAfterLast('/')
        } else {
            remaining = remaining.substringBefore('?')
        }

        return remaining.trim()
    }

    private fun parseUri(text: String): Result? {
        if (!text.startsWith("spotify:", ignoreCase = true)) return null
        val segments = text.split(':')
        if (segments.size != 3) {
            return Result.Invalid("malformed Spotify URI: '$text'")
        }
        val type = EntityType.fromSegment(segments[1])
            ?: return Result.Invalid("unsupported Spotify URI type: '${segments[1]}'")
        return validate(segments[2], type, text)
    }

    private fun parseUrl(text: String, typeHint: EntityType?): Result? {
        val candidate = if ("://" in text) text else "https://$text"
        val uri = runCatching { java.net.URI(candidate) }.getOrNull() ?: return null
        val host = uri.host?.lowercase() ?: return null
        if (host !in SPOTIFY_HOSTS) return null

        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }.toMutableList()

        // Shared links appear as open.spotify.com/embed/<type>/<id> and
        // open.spotify.com/intl-fr/<type>/<id>; both prefixes are dropped.
        if (segments.firstOrNull() == "embed") segments.removeAt(0)
        if (segments.firstOrNull()?.let { INTL_SEGMENT.matches(it) } == true) segments.removeAt(0)

        if (segments.size != 2) {
            return Result.Invalid("unrecognized Spotify URL path: '$text'")
        }
        val type = EntityType.fromSegment(segments[0]) ?: typeHint
            ?: return Result.Invalid("unsupported Spotify URL type: '${segments[0]}'")
        return validate(segments[1], type, text)
    }

    private fun validate(id: String, type: EntityType?, source: String): Result =
        if (isEntityId(id)) {
            Result.Resolved(id = id, type = type)
        } else {
            Result.Invalid("invalid Spotify id '$id' in '$source'")
        }

    /**
     * The check that would have caught the original bug: a bare Spotify entity id
     * is exactly 22 base62 characters, so it never contains a colon, a slash, or a
     * space. `playlist:30KI6...` fails here instead of being shipped upstream as
     * `spotify:playlist:playlist:30KI6...`.
     */
    private fun isEntityId(candidate: String): Boolean = ID_REGEX.matches(candidate)

    /** Whether [id] has the shape Spotify issues. */
    fun hasExpectedLength(id: String): Boolean = id.length == ID_LENGTH
}