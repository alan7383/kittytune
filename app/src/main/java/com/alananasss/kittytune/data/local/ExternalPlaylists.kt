package com.alananasss.kittytune.data.local

import kotlin.math.abs

/**
 * Helpers for playlists that do not live on SoundCloud (Spotify, Deezer, Tidal,
 * Qobuz, VK, ...).
 *
 * These playlists use hash-based positive Long ids that share the same numeric
 * space as SoundCloud ids. Every place that treats "id > 0" as "a SoundCloud id
 * we can refresh/like/unlike/delete through the SoundCloud API" must first
 * exclude these, otherwise a restart overwrites them with a random SoundCloud
 * playlist (rename) and then filters/deletes them (disappearance).
 */
object ExternalPlaylists {

    fun isExternalPermalink(url: String?): Boolean {
        if (url == null) return false
        val lower = url.lowercase()
        return lower.contains("spotify") ||
            lower.contains("deezer.com") ||
            lower.contains("tidal.com") ||
            lower.contains("qobuz") ||
            lower.startsWith("vk_playlist:") ||
            lower == "vk_likes" ||
            lower.contains("vk.com/")
    }

    fun isExternalUrn(urn: String?): Boolean {
        if (urn == null) return false
        val lower = urn.lowercase()
        return lower.startsWith("spotify:") ||
            lower.startsWith("deezer:") ||
            lower.startsWith("tidal:") ||
            lower.startsWith("qobuz:")
    }

    fun isExternalProvider(permalinkUrl: String?, urn: String?): Boolean {
        return isExternalPermalink(permalinkUrl) || isExternalUrn(urn)
    }

    fun isExternalRoute(route: String): Boolean {
        val lower = route.lowercase()
        return lower.startsWith("spotify") ||
            lower.startsWith("station_spotify") ||
            lower.startsWith("deezer:") ||
            lower.startsWith("tidal:") ||
            lower.startsWith("qobuz:") ||
            lower.startsWith("vk_playlist:") ||
            lower == "vk_likes"
    }

    /**
     * Must stay byte-for-byte identical to
     * [com.alananasss.kittytune.data.spotify.SpotifyModels] id generation:
     * `abs(id.hashCode() shl 16 or (reversed.hashCode() and 0xFFFF))`.
     */
    fun spotifyStableId(rawId: String): Long {
        val id = rawId.trim()
        return abs(id.hashCode().toLong() shl 16 or (id.reversed().hashCode().toLong() and 0xFFFFL))
    }

    fun deezerPlaylistStableId(id: String): Long =
        abs(("deezer:playlist:${id.trim()}").hashCode().toLong())

    fun deezerAlbumStableId(id: String): Long =
        abs(("deezer:album:${id.trim()}").hashCode().toLong())

    fun deezerArtistStableId(id: String): Long =
        abs(("deezer:artist:${id.trim()}").hashCode().toLong())

    fun tidalPlaylistStableId(id: String): Long =
        abs(("tidal:playlist:${id.trim()}").hashCode().toLong())

    fun tidalAlbumStableId(id: String): Long =
        abs(("tidal:album:${id.trim()}").hashCode().toLong())

    fun tidalArtistStableId(id: String): Long =
        abs(("tidal:artist:${id.trim()}").hashCode().toLong())

    fun qobuzPlaylistStableId(id: String): Long =
        abs(("qobuz:playlist:${id.trim()}").hashCode().toLong())

    fun qobuzAlbumStableId(id: String): Long =
        abs(("qobuz:album:${id.trim()}").hashCode().toLong())

    fun qobuzArtistStableId(id: String): Long =
        abs(("qobuz:artist:${id.trim()}").hashCode().toLong())

    /**
     * Legacy (pre-fix) detail-screen id for a raw external id.
     *
     * The detail screen used to compute `abs(cleanId.hashCode())` (or the raw
     * numeric id for Deezer) instead of the provider-canonical hash, so likes
     * created from the detail screen landed under a different id than likes
     * created from search/home. Both ids must resolve to the same entry.
     */
    fun legacyDetailStableId(cleanId: String, currentIdLong: Long, isSystemRoute: Boolean): Long {
        return if (currentIdLong != 0L && !isSystemRoute) currentIdLong
        else abs(cleanId.hashCode().toLong())
    }

    /**
     * Canonical id for a locally stored external playlist, or null when the
     * row is not a recognised external playlist or already canonical.
     *
     * Used once at startup to migrate detail-screen legacy ids (B) to the
     * provider-canonical ids (A) that search/home use, so one Spotify playlist
     * is one library entry with one liked flag.
     */
    fun canonicalIdForLocal(id: Long, permalinkUrl: String?, isAlbum: Boolean): Long? {
        if (permalinkUrl == null) return null
        val lower = permalinkUrl.lowercase()
        return try {
            when {
                lower.contains("open.spotify.com") || lower.contains("play.spotify.com") -> {
                    val raw = permalinkUrl.substringAfterLast("/").substringBefore("?").substringBefore("/").trim()
                    if (raw.isEmpty()) return null
                    val canonical = spotifyStableId(raw)
                    if (canonical != id) canonical else null
                }
                lower.contains("deezer.com") -> {
                    val raw = permalinkUrl.substringAfterLast("/").substringBefore("?").trim()
                    if (raw.isEmpty()) return null
                    val canonical = if (isAlbum || lower.contains("/album/")) deezerAlbumStableId(raw)
                    else deezerPlaylistStableId(raw)
                    if (canonical != id) canonical else null
                }
                lower.contains("tidal.com") -> {
                    val raw = permalinkUrl.substringAfterLast("/").substringBefore("?").trim()
                    if (raw.isEmpty()) return null
                    val canonical = if (isAlbum || lower.contains("/album/")) tidalAlbumStableId(raw)
                    else tidalPlaylistStableId(raw)
                    if (canonical != id) canonical else null
                }
                lower.contains("qobuz") -> {
                    val raw = permalinkUrl.substringAfterLast("/").substringBefore("?").trim()
                    if (raw.isEmpty()) return null
                    val canonical = if (isAlbum) qobuzAlbumStableId(raw)
                    else qobuzPlaylistStableId(raw)
                    if (canonical != id) canonical else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }
}
