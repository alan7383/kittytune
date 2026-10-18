package com.alananasss.kittytune.data.vk

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * A VK playlist or album, as the three numbers VK addresses it by.
 *
 * @param accessHash the key a private-but-shared playlist's link carries; empty for public ones.
 */
data class VkPlaylistRef(val ownerId: Long, val playlistId: Long, val accessHash: String = "")

/**
 * Reads a VK playlist out of whatever link the listener pastes.
 *
 * VK has shown the same playlist under half a dozen shapes over the years, and people paste all of them:
 * - `vk.com/music/playlist/-147845620_2949` and `…/music/album/-2000123_456_8f3a…`, the current pages;
 * - `vk.com/audios123?z=audio_playlist123_456%2F8f3a…`, the old modal over a profile;
 * - `m.vk.com/audio?act=audio_playlist123_456&access_hash=8f3a…`, the mobile site;
 * - any of these on `vk.ru`, the domain VK moved to, or with `m.` in front.
 *
 * The owner id is negative for a community or an official album and positive for a person. The hash is
 * what lets a private playlist open from its link, so it is kept whenever the link has one.
 */
object VkPlaylistLink {

    private val HOSTS = Regex("""^(?:[a-z0-9-]+\.)*(?:vk\.com|vk\.ru|vkontakte\.ru)$""")

    /** The owner, the id and an optional hash, after any of the markers VK has used. */
    private val PLAYLIST = Regex(
        """(?:music/(?:playlist|album)/|audio_playlist)(-?\d+)_(\d+)(?:(?:_|/)([0-9a-f]{6,}))?""",
        RegexOption.IGNORE_CASE,
    )
    private val ACCESS_HASH = Regex("""[?&]access_hash=([0-9a-f]+)""", RegexOption.IGNORE_CASE)

    /** Whether [link] points at VK at all, so the caller can tell "not VK" from "VK, but not a playlist". */
    fun isVkLink(link: String): Boolean = host(link)?.let { HOSTS.matches(it) || it == "vk.cc" } ?: false

    /** Whether [link] is a vk.cc short link, which has to be followed before it can be read. */
    fun isShortLink(link: String): Boolean = host(link) == "vk.cc"

    /** The playlist [link] names, or null when it names no playlist. */
    fun parse(link: String): VkPlaylistRef? {
        val text = link.trim()
        if (host(text)?.let { HOSTS.matches(it) } != true) return null
        // The modal links carry the playlist in an encoded query parameter: `%2F` for the slash before the hash.
        val decoded = runCatching { URLDecoder.decode(text, StandardCharsets.UTF_8) }.getOrDefault(text)
        val match = PLAYLIST.find(decoded) ?: return null
        val (owner, id, hashInPath) = match.destructured
        val hash = hashInPath.ifEmpty { ACCESS_HASH.find(decoded)?.groupValues?.get(1).orEmpty() }
        return VkPlaylistRef(owner.toLongOrNull() ?: return null, id.toLongOrNull() ?: return null, hash)
    }

    private fun host(link: String): String? {
        val withScheme = if ("://" in link) link.trim() else "https://${link.trim()}"
        return runCatching { java.net.URI(withScheme).host?.lowercase() }.getOrNull()
    }
}
