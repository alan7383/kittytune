package com.alananasss.kittytune.data.search

import java.net.URI

/**
 * Turns a pasted Qobuz, Tidal or Deezer link into the object it points at.
 *
 * The search field used to send the whole link to the provider's text search, which
 * naturally found nothing related to it. A link carries an exact id, so the only
 * sensible thing to do with one is resolve that id directly — the same way pasted
 * Spotify and YouTube links are handled.
 */
object ProviderLinks {

    enum class Provider(val key: String) {
        DEEZER("deezer"),
        TIDAL("tidal"),
        QOBUZ("qobuz"),
    }

    enum class Kind {
        TRACK,
        ALBUM,
        PLAYLIST,
        ARTIST,
    }

    data class Link(
        val provider: Provider,
        val kind: Kind,
        val id: String,
    ) {
        /** The route used by the rest of the app, e.g. `deezer:album:302127`. */
        val urn: String
            get() = "${provider.key}:${kind.name.lowercase()}:$id"
    }

    private val URN = Regex(
        "^(deezer|tidal|qobuz):(track|album|playlist|artist):([A-Za-z0-9_-]+)$",
        RegexOption.IGNORE_CASE,
    )
    private val ID = Regex("[A-Za-z0-9_-]+")
    private val EMBEDDED_URL = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

    /** Domains whose links need a redirect before the object kind and id are visible. */
    private val SHORT_HOSTS = setOf(
        "deezer.page.link",
        "link.deezer.com",
        "tidal.link",
        "qobuz.app",
    )

    /** Whether [raw] looks like a provider link, even if it is a short one we cannot parse yet. */
    fun looksLikeProviderUrl(raw: String): Boolean {
        val text = raw.trim()
        if (URN.matches(text)) return true
        val host = hostOf(text) ?: return false
        return providerForHost(host) != null
    }

    fun isShortLink(raw: String): Boolean {
        val host = hostOf(raw) ?: return false
        return host in SHORT_HOSTS || host.endsWith(".qobuz.app")
    }

    /**
     * Parses a full link or a provider urn. Returns null for ordinary search text and
     * for short links whose destination is only known after following the redirect.
     */
    fun parse(raw: String): Link? {
        val text = raw.trim()
        URN.matchEntire(text)?.let { match ->
            val provider = providerForKey(match.groupValues[1]) ?: return null
            val kind = kindOf(match.groupValues[2]) ?: return null
            val id = match.groupValues[3].trim()
            if (!isPlausibleId(id)) return null
            return Link(provider, kind, id)
        }

        val uri = runCatching { URI(withScheme(text)) }.getOrNull() ?: return null
        val provider = providerForHost(hostOf(uri) ?: return null) ?: return null
        val segments = uri.path.orEmpty()
            .split('/')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val kindIndex = segments.indexOfFirst { kindOf(it) != null }
        if (kindIndex < 0) return null
        val kind = kindOf(segments[kindIndex]) ?: return null
        val tail = segments.drop(kindIndex + 1)
        if (tail.isEmpty()) return null

        // Qobuz album links end in /album/<slug>/<id>; everything else ends in the id
        // itself, including Tidal's playlist uuid.
        val id = tail.last()
        if (!ID.matches(id)) return null
        if (!isPlausibleId(id)) return null
        return Link(provider, kind, id)
    }

    /**
     * A real id is never one or two characters long. The check keeps a link that is
     * still being typed — `…/album/3` — from being taken for a complete one, which
     * would open an empty album while the rest of the link is on its way.
     */
    private fun isPlausibleId(id: String): Boolean = id.length >= 3

    private fun providerForKey(value: String): Provider? = when (value.lowercase()) {
        "deezer" -> Provider.DEEZER
        "tidal" -> Provider.TIDAL
        "qobuz" -> Provider.QOBUZ
        else -> null
    }

    private fun providerForHost(host: String): Provider? {
        val clean = host.lowercase().removePrefix("www.")
        return when {
            clean == "deezer.com" || clean.endsWith(".deezer.com") ||
                clean == "deezer.page.link" || clean == "link.deezer.com" -> Provider.DEEZER
            clean == "tidal.com" || clean.endsWith(".tidal.com") ||
                clean == "tidal.link" -> Provider.TIDAL
            clean == "qobuz.com" || clean.endsWith(".qobuz.com") ||
                clean == "qobuz.app" || clean.endsWith(".qobuz.app") -> Provider.QOBUZ
            else -> null
        }
    }

    private fun kindOf(segment: String): Kind? = when (segment.lowercase()) {
        "track", "tracks" -> Kind.TRACK
        "album", "albums" -> Kind.ALBUM
        "playlist", "playlists" -> Kind.PLAYLIST
        "artist", "artists" -> Kind.ARTIST
        else -> null
    }

    private fun hostOf(raw: String): String? {
        val uri = runCatching { URI(withScheme(raw.trim())) }.getOrNull() ?: return null
        return hostOf(uri)
    }

    private fun hostOf(uri: URI): String? =
        uri.host?.lowercase()?.removePrefix("www.")?.takeIf { it.isNotBlank() }

    private fun withScheme(raw: String): String {
        val withoutFragment = raw.substringBefore('#')
        val embedded = EMBEDDED_URL.find(withoutFragment)?.value ?: withoutFragment
        val trimmed = embedded.trim().trimEnd('.', ',', ')', ']')
        return when {
            trimmed.startsWith("http://", ignoreCase = true) ||
                trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> "https://${trimmed.removePrefix("//")}"
        }
    }
}
