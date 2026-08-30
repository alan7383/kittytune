package com.alananasss.kittytune.data.local

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import java.io.File

/**
 * Rebuilds a [Track] from its saved row.
 *
 * A track stored in a playlist used to come back as a bare SoundCloud track: the owner id, the share
 * link and the streaming source were all dropped. That is what made VK tracks inside playlists share
 * a soundcloud.com link, fail to play with a misleading network error, and refuse to download.
 * Everything needed to rebuild the track faithfully is now persisted, so it is restored here.
 */
fun LocalTrack.toTrack(
    artworkOverride: String? = null,
    isLiked: Boolean = false,
    description: String? = null
): Track {
    val resolvedArtwork = artworkOverride
        ?: localArtworkPath.takeIf { it.isNotEmpty() && File(it).exists() && File(it).length() > 0 }
        ?: artworkUrl

    val effectiveSource = resolvedSource()

    return Track(
        id = id,
        title = title,
        artworkUrl = resolvedArtwork,
        durationMs = duration,
        user = User(
            id = ownerId,
            username = artist,
            avatarUrl = null,
            urn = if (effectiveSource == "vk") "vk:artist:$artist" else null,
            permalinkUrl = if (effectiveSource == "vk" && ownerId != 0L) {
                "https://vk.com/id$ownerId"
            } else {
                null
            }
        ),
        source = effectiveSource,
        permalinkUrl = permalinkUrl ?: defaultPermalink(effectiveSource),
        secretToken = secretToken,
        isLiked = isLiked,
        description = description,
        fullDuration = duration
    )
}

/**
 * Rows written before the source column existed carry no service name, so the old heuristics are
 * kept as a fallback instead of silently labelling every legacy row as SoundCloud.
 */
private fun LocalTrack.resolvedSource(): String {
    if (source.isNotBlank() && source != "soundcloud") return source
    if (id < 0) return "local"
    if (localAudioPath.startsWith("content://")) return "local"
    val artworkHints = listOf("vkuseraudio", "userapi", "vk.com", "vk.ru")
    if (artworkHints.any { artworkUrl.contains(it, ignoreCase = true) }) return "vk"
    return source.ifBlank { "soundcloud" }
}

private fun LocalTrack.defaultPermalink(effectiveSource: String): String? = when (effectiveSource) {
    "vk" -> if (ownerId != 0L) "https://vk.com/audio${ownerId}_$id" else null
    "local" -> null
    else -> null
}
