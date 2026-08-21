package com.alananasss.kittytune.data.spotify

import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import kotlin.math.abs

data class SpotifyArtistRef(
    val id: String,
    val name: String,
    val uri: String? = null,
    val avatarUrl: String? = null,
    val verified: Boolean = false
)

data class SpotifyCreditArtist(
    val id: String,
    val name: String,
    val uri: String? = null,
    val imageUri: String? = null,
    val subroles: List<String> = emptyList()
)

data class SpotifyCreditRole(
    val roleTitle: String,
    val artists: List<SpotifyCreditArtist> = emptyList()
)

data class SpotifyCredits(
    val trackTitle: String,
    val trackUri: String,
    val roles: List<SpotifyCreditRole> = emptyList(),
    val sourceNames: List<String> = emptyList()
)

data class SpotifyTrack(
    val id: String,
    val name: String,
    val durationMs: Long,
    val artists: List<SpotifyArtistRef>,
    val albumName: String? = null,
    val albumId: String? = null,
    val artworkUrl: String? = null,
    val releaseDate: String? = null,
    val explicit: Boolean = false,
    val isPlayable: Boolean = true,
    val shareUrl: String? = null,
    val playCount: Long? = null,
    val publisher: String? = null
) {
    val artistName: String
        get() = artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }

    fun toTrack(): Track {
        val stableId = abs(id.hashCode().toLong() shl 16 or (id.reversed().hashCode().toLong() and 0xFFFFL))
        val firstArtist = artists.firstOrNull()
        return Track(
            id = stableId,
            title = name,
            artworkUrl = artworkUrl,
            durationMs = durationMs,
            user = User(
                id = abs(firstArtist?.id?.hashCode()?.toLong() ?: 0L),
                username = artistName,
                avatarUrl = firstArtist?.avatarUrl ?: artworkUrl,
                permalink = firstArtist?.id,
                urn = firstArtist?.id?.let { "spotify:artist:$it" },
                verified = firstArtist?.verified ?: false
            ),
            publisherMetadata = TrackPublisherMetadata(
                artist = artistName,
                albumTitle = albumName,
                albumId = albumId,
                explicit = explicit,
                publisher = publisher
            ),
            releaseDate = releaseDate,
            permalinkUrl = shareUrl ?: "https://open.spotify.com/track/$id",
            permalink = id,
            source = "spotify",
            streamable = isPlayable,
            playCount = playCount,
            playbackCount = (playCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0),
            artists = artists
        )
    }
}

data class SpotifyAlbum(
    val id: String,
    val name: String,
    val artists: List<SpotifyArtistRef>,
    val artworkUrl: String? = null,
    val releaseDate: String? = null,
    val releaseType: String? = null,
    val totalTracks: Int = 0,
    val tracks: List<SpotifyTrack> = emptyList()
) {
    val artistName: String
        get() = artists.joinToString(", ") { it.name }.ifBlank { "Unknown Artist" }

    val formattedSubtitle: String
        get() {
            val year = releaseDate?.take(4)
            val typeStr = when (releaseType?.uppercase()) {
                "SINGLE" -> "Single"
                "EP" -> "EP"
                "COMPILATION" -> "Compilation"
                "ALBUM" -> "Album"
                else -> releaseType?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Album"
            }
            return if (!year.isNullOrBlank()) "$year • $typeStr" else typeStr
        }

    fun toPlaylist(): com.alananasss.kittytune.domain.Playlist {
        val stableId = abs(id.hashCode().toLong() shl 16 or (id.reversed().hashCode().toLong() and 0xFFFFL))
        return com.alananasss.kittytune.domain.Playlist(
            id = stableId,
            title = name,
            artworkUrl = artworkUrl,
            calculatedArtworkUrl = artworkUrl,
            trackCount = totalTracks.takeIf { it > 0 } ?: tracks.size,
            user = User(
                id = abs(artists.firstOrNull()?.id?.hashCode()?.toLong() ?: 0L),
                username = formattedSubtitle,
                avatarUrl = artworkUrl
            ),
            tracks = tracks.map { it.toTrack() },
            isAlbum = true,
            releaseDate = releaseDate,
            permalink = id,
            permalinkUrl = "https://open.spotify.com/album/$id",
            urn = "spotify:album:$id"
        )
    }
}

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String? = null,
    val ownerName: String? = null,
    val ownerId: String? = null,
    val ownerAvatarUrl: String? = null,
    val artworkUrl: String? = null,
    val totalTracks: Int = 0,
    val followersCount: Long? = null,
    val tracks: List<SpotifyTrack> = emptyList()
) {
    fun toPlaylist(): com.alananasss.kittytune.domain.Playlist {
        val stableId = abs(id.hashCode().toLong() shl 16 or (id.reversed().hashCode().toLong() and 0xFFFFL))
        val ownerNumericId = if (!ownerId.isNullOrBlank()) abs(ownerId.hashCode().toLong()) else 0L
        return com.alananasss.kittytune.domain.Playlist(
            id = stableId,
            title = name,
            artworkUrl = artworkUrl,
            calculatedArtworkUrl = artworkUrl,
            trackCount = totalTracks.takeIf { it > 0 } ?: tracks.size,
            user = User(
                id = ownerNumericId,
                username = ownerName ?: "Spotify",
                avatarUrl = ownerAvatarUrl ?: artworkUrl,
                permalink = ownerId,
                urn = if (!ownerId.isNullOrBlank()) "spotify:user:$ownerId" else null
            ),
            tracks = tracks.map { it.toTrack() },
            isAlbum = false,
            description = description,
            permalink = id,
            permalinkUrl = "https://open.spotify.com/playlist/$id",
            urn = "spotify:playlist:$id",
            likesCount = (followersCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0)
        )
    }
}

data class SpotifyExternalLink(
    val name: String,
    val url: String
)

data class SpotifyConcert(
    val title: String,
    val venue: String,
    val location: String,
    val dateStr: String,
    val ticketerUrl: String? = null
)

data class SpotifyArtist(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val headerImageUrl: String? = null,
    val verified: Boolean = false,
    val monthlyListeners: Long? = null,
    val worldRank: Int? = null,
    val followers: Long? = null,
    val biography: String? = null,
    val topTracks: List<SpotifyTrack> = emptyList(),
    val popularReleases: List<SpotifyAlbum> = emptyList(),
    val albums: List<SpotifyAlbum> = emptyList(),
    val singles: List<SpotifyAlbum> = emptyList(),
    val compilations: List<SpotifyAlbum> = emptyList(),
    val appearsOn: List<SpotifyAlbum> = emptyList(),
    val discoveredOn: List<SpotifyPlaylist> = emptyList(),
    val relatedArtists: List<SpotifyArtistRef> = emptyList(),
    val concerts: List<SpotifyConcert> = emptyList(),
    val externalLinks: List<SpotifyExternalLink> = emptyList()
) {
    fun toUser(): User {
        val stableId = abs(id.hashCode().toLong() shl 16 or (id.reversed().hashCode().toLong() and 0xFFFFL))
        return User(
            id = stableId,
            username = name,
            avatarUrl = avatarUrl,
            followersCount = (monthlyListeners ?: 0L).toInt(),
            permalink = id,
            permalinkUrl = "https://open.spotify.com/artist/$id",
            urn = "spotify:artist:$id",
            verified = verified
        )
    }
}

data class SpotifySearchResults(
    val query: String,
    val tracks: List<SpotifyTrack> = emptyList(),
    val albums: List<SpotifyAlbum> = emptyList(),
    val artists: List<SpotifyArtist> = emptyList(),
    val playlists: List<SpotifyPlaylist> = emptyList(),
    val totalTracks: Int = 0
)

data class SpotifyChart(
    val key: String,
    val name: String,
    val playlistId: String
)
