package com.alananasss.kittytune.data.spotify

import com.alananasss.kittytune.domain.Track
import org.json.JSONObject
import java.net.URI

private val imageHash = Regex("[a-fA-F0-9]{16,}")

internal fun spotifyEntityId(reference: String?, type: String): String? {
    val resolved = SpotifyIds.parse(reference.orEmpty()) as? SpotifyIds.Result.Resolved ?: return null
    return resolved.id.takeIf { resolved.type == null || resolved.type.urnSegment == type }
}

internal fun Track.spotifyCatalogTrackId(): String? =
    spotifyEntityId(permalinkUrl, "track") ?: if (source == "spotify" ||
        user?.urn?.startsWith("spotify:artist:", ignoreCase = true) == true
    ) {
        spotifyEntityId(permalink, "track")
    } else {
        permalink?.takeIf { it.startsWith("spotify:track:", ignoreCase = true) }
            ?.let { spotifyEntityId(it, "track") }
    }

private fun creditImageUrl(reference: String?): String? {
    val raw = reference?.trim().orEmpty()
    val hash = raw.removePrefix("spotify:image:")
    if (imageHash.matches(hash)) return "https://i.scdn.co/image/$hash"
    val uri = runCatching { URI(raw) }.getOrNull() ?: return null
    return raw.takeIf { uri.host != null && (uri.scheme.equals("https", true) || uri.scheme.equals("http", true)) }
}

internal fun parseSpotifyCredits(requestedId: String, json: JSONObject): SpotifyCredits? {
    val trackUri = json.optString("trackUri")
    if (trackUri.isNotBlank() && spotifyEntityId(trackUri, "track") != requestedId) return null

    val roles = json.optJSONArray("roleCredits")
    val parsedRoles = (0 until (roles?.length() ?: 0)).mapNotNull { index ->
        val role = roles?.optJSONObject(index) ?: return@mapNotNull null
        val artists = role.optJSONArray("artists")
        val parsedArtists = (0 until (artists?.length() ?: 0)).mapNotNull artist@{ i ->
            val artist = artists?.optJSONObject(i) ?: return@artist null
            val name = artist.optString("name").takeIf { it.isNotBlank() } ?: return@artist null
            val uri = artist.optString("uri")
            SpotifyCreditArtist(
                id = spotifyEntityId(uri, "artist").orEmpty(),
                name = name,
                uri = uri.ifBlank { null },
                imageUri = creditImageUrl(artist.optString("imageUri")),
                subroles = artist.strings("subroles"),
            )
        }
        SpotifyCreditRole(role.optString("roleTitle"), mergeCreditArtists(parsedArtists))
    }
    val title = json.optString("trackTitle")
    if (title.isBlank() && parsedRoles.isEmpty()) return null
    return SpotifyCredits(title, "spotify:track:$requestedId", parsedRoles, json.strings("sourceNames"))
}

internal fun mergeCreditArtists(artists: List<SpotifyCreditArtist>): List<SpotifyCreditArtist> =
    artists.groupBy { it.id.ifBlank { it.name.trim().lowercase() } }.values.map { entries ->
        entries.first().copy(
            imageUri = entries.firstNotNullOfOrNull { it.imageUri },
            subroles = entries.flatMap { it.subroles }.distinct(),
        )
    }

private fun JSONObject.strings(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() }
}
