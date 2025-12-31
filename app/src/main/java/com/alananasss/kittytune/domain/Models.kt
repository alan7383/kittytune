package com.alananasss.kittytune.domain

import com.alananasss.kittytune.data.network.LongIdAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

// track details stuff
data class LikerCollection(val collection: List<User>, val next_href: String?)
data class ReposterCollection(val collection: List<User>, val next_href: String?)
data class InPlaylistCollection(val collection: List<Playlist>, val next_href: String?)

// advanced home models (charts & stream)
data class ChartsResponse(
    val collection: List<ChartItem>,
    val next_href: String?
)

data class ChartItem(
    val track: Track?,
    val score: Double?
)

data class StreamResponse(
    val collection: List<StreamItem>,
    val next_href: String?
)

data class StreamItem(
    val type: String, // "track", "track-repost", "playlist", "playlist-repost"
    val track: Track?,
    val playlist: Playlist?,
    val user: User?, // who posted or reposted this
    @SerializedName("created_at") val createdAt: String?
)

// comments section
data class CommentCollection(
    val collection: List<Comment>,
    val next_href: String?
)

data class Comment(
    val id: Long,
    val body: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("timestamp") val trackTimestamp: Long?,
    val user: User?,

    @SerializedName("likes_count", alternate = ["like_count", "favoritings_count", "reposts_count"])
    val likesCount: Int = 0,

    @SerializedName("user_favorite") val isLiked: Boolean = false
)

data class PostCommentRequest(
    val comment: CommentBody
)
data class CommentBody(
    val body: String,
    val timestamp: Long // current position in ms
)

// the main track model
data class Track(
    val id: Long,
    val title: String?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("duration") val durationMs: Long?,
    val user: User?,
    val media: Media? = null,
    @SerializedName("user_favorite") val isLiked: Boolean = false,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("permalink_url") val permalinkUrl: String? = null,

    @SerializedName("description") val description: String? = null,
    @SerializedName("tag_list") val tagList: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("playback_count") val playbackCount: Int = 0,
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("reposts_count") val repostsCount: Int = 0,
    @SerializedName("comment_count") val commentCount: Int = 0
) {
    val fullResArtwork: String
        get() {
            // try to get better quality art
            if (artworkUrl != null) return artworkUrl.replace("large", "t500x500")
            if (user != null && user.avatarUrl != null) return user.avatarUrl.replace("large", "t500x500")
            return "https://picsum.photos/200"
        }
}

// other random models
data class TrackLikesResponse(val collection: List<TrackLikeItem>, val next_href: String?)
data class TrackLikeItem(val track: Track)
data class PlaylistLikesResponse(val collection: List<PlaylistLikeItem>, val next_href: String?)
data class PlaylistLikeItem(val playlist: Playlist, @SerializedName("created_at") val likedAt: String?)
data class UserPlaylistsResponse(val collection: List<Playlist>, val next_href: String?)
data class UserCollection(val collection: List<User>, val next_href: String?)
data class BasicTrackCollection(val collection: List<Track>, val next_href: String?)
data class RepostCollection(val collection: List<RepostItem>, val next_href: String?)
data class RepostItem(val type: String, @SerializedName("created_at") val createdAt: String?, val track: Track?, val playlist: Playlist?)
data class UpdateProfileRequest(val username: String?, val description: String?, val city: String?, @SerializedName("country_code") val countryCode: String?, @SerializedName("first_name") val firstName: String? = null, @SerializedName("last_name") val lastName: String? = null)

data class Playlist(@JsonAdapter(LongIdAdapter::class) val id: Long, val title: String?, @SerializedName("artwork_url") val artworkUrl: String?, @SerializedName("calculated_artwork_url") val calculatedArtworkUrl: String?, @SerializedName("track_count") val trackCount: Int?, val user: User?, val tracks: List<Track>? = null, @SerializedName("is_album") val isAlbum: Boolean = false, @SerializedName("permalink_url") val permalinkUrl: String? = null, @SerializedName("created_at") val createdAt: String? = null, @SerializedName("last_modified") val lastModified: String? = null) {
    // logic to find best available cover art
    val fullResArtwork: String get() { if (!artworkUrl.isNullOrEmpty()) return artworkUrl.replace("large", "t500x500"); if (!calculatedArtworkUrl.isNullOrEmpty()) return calculatedArtworkUrl.replace("large", "t500x500"); if (!tracks.isNullOrEmpty()) { val firstTrackArt = tracks[0].fullResArtwork; if (!firstTrackArt.contains("picsum")) return firstTrackArt }; return user?.avatarUrl?.replace("large", "t500x500") ?: "https://picsum.photos/200" }
}

data class User(val id: Long, val username: String?, @SerializedName("avatar_url") val avatarUrl: String?, val city: String? = null, @SerializedName("country_code") val countryCode: String? = null, @SerializedName("followers_count") val followersCount: Int = 0, @SerializedName("followings_count") val followingsCount: Int = 0, @SerializedName("track_count") val trackCount: Int = 0, @SerializedName("description") val description: String? = null, @SerializedName("permalink_url") val permalinkUrl: String? = null, val visuals: Visuals? = null, @SerializedName("public_favorites_count") private val _publicFavoritesCount: Int? = 0, @SerializedName("likes_count") private val _likesCount: Int? = 0, @SerializedName("favorites_count") private val _favoritesCount: Int? = 0) {
    // api returns different field names for likes sometimes
    val likesCount: Int get() = when { (_publicFavoritesCount ?: 0) > 0 -> _publicFavoritesCount!!; (_likesCount ?: 0) > 0 -> _likesCount!!; (_favoritesCount ?: 0) > 0 -> _favoritesCount!!; else -> 0 }
    val bannerUrl: String? get() = visuals?.visuals?.firstOrNull()?.visualUrl
}

data class Visuals(val visuals: List<VisualItem>?)
data class VisualItem(@SerializedName("visual_url") val visualUrl: String)
data class Media(val transcodings: List<Transcoding>?)
data class Transcoding(val url: String, val preset: String, val format: Format?)
data class Format(val protocol: String?, @SerializedName("mime_type") val mimeType: String?)
data class StreamUrlResponse(val url: String?)