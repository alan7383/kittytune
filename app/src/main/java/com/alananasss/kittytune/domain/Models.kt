package com.alananasss.kittytune.domain

import com.alananasss.kittytune.data.network.LongIdAdapter
import com.google.gson.annotations.JsonAdapter
import com.google.gson.annotations.SerializedName

data class LikerCollection(val collection: List<User>, val next_href: String?)
data class ReposterCollection(val collection: List<User>, val next_href: String?)
data class InPlaylistCollection(val collection: List<Playlist>, val next_href: String?)
data class ChartsResponse(val collection: List<ChartItem>, val next_href: String?)
data class ChartItem(val track: Track?, val score: Double?)
data class StreamResponse(val collection: List<StreamItem>, val next_href: String?)
data class StreamItem(
    val type: String,
    val track: Track?,
    val playlist: Playlist?,
    val user: User?,
    @SerializedName("created_at") val createdAt: String?
)

data class CommentCollection(val collection: List<Comment>, val next_href: String?)

data class Comment(
    val id: Long,
    val body: String,
    @SerializedName("created_at") val createdAt: String,
    @SerializedName("timestamp") val trackTimestamp: Long?,
    val user: User?,

    val track: Track? = null,

    @SerializedName("likes_count", alternate = ["favoritings_count"]) val _likesCount: Int? = null,
    @SerializedName("reaction_stats") val reactionStats: ReactionStats? = null,
    @SerializedName("user_favorite") val isLiked: Boolean = false,

    @SerializedName("replies") val replies: List<Comment>? = null
) {
    val likesCount: Int
        get() {
            if (_likesCount != null) return _likesCount
            return reactionStats?.counts?.sumOf { it.count } ?: 0
        }

    fun copy(
        isLiked: Boolean = this.isLiked,
        likesCount: Int = this.likesCount,
        replies: List<Comment>? = this.replies
    ): Comment {
        return Comment(
            id = this.id,
            body = this.body,
            createdAt = this.createdAt,
            trackTimestamp = this.trackTimestamp,
            user = this.user,
            track = this.track,
            _likesCount = likesCount,
            reactionStats = null,
            isLiked = isLiked,
            replies = replies
        )
    }
}

data class RepostCaptionRequest(
    val caption: String
)

data class ActivitiesResponse(val collection: List<ActivityItem>, val next_href: String?)

data class ActivityItem(
    val type: String,
    @SerializedName("created_at") val createdAt: String,
    val user: User?,
    val track: Track?,
    val playlist: Playlist?,
    val comment: Comment?
)


data class InboxCollection(
    val collection: List<InboxConversation>,
    @SerializedName("_links") val links: Map<String, Link>? = null
)

data class Link(
    val href: String?
)

data class InboxConversation(
    val id: String,
    @SerializedName("last_message") val lastMessage: InboxMessage,
    @SerializedName("read") val isRead: Boolean,
    @SerializedName("between") val betweenUsers: List<ConversationParticipant>
) {
    fun getOtherParticipant(myUrn: String): ConversationParticipant? {
        return betweenUsers.find { !it.matches(myUrn) }
    }

    fun getOtherUserUrn(myUrn: String): String? {
        return getOtherParticipant(myUrn)?.let { it.urn ?: "soundcloud:users:${it.id}" }
            ?: run {
                val myId = myUrn.removePrefix("soundcloud:users:")
                id.split(":").find { it != myId }?.let { "soundcloud:users:$it" }
            }
    }

    fun getOtherUsername(myUrn: String): String? {
        return getOtherParticipant(myUrn)?.username ?: "SoundCloud User"
    }

    fun getOtherAvatar(myUrn: String): String? {
        return getOtherParticipant(myUrn)?.avatarUrl
    }
}

data class ConversationParticipant(
    val id: Long? = null,
    @SerializedName("urn") val urn: String? = null,
    val permalink: String?,
    val username: String?,
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null,
    @SerializedName("followers_count") val followersCount: Long = 0,
    @SerializedName("followings_count") val followingsCount: Long = 0,
    val verified: Boolean = false,
    @SerializedName("is_pro") val isPro: Boolean = false,
    val city: String? = null,
    val country: String? = null
) {
    fun matches(myUrn: String): Boolean {
        val normalizedMyUrn = myUrn.removePrefix("soundcloud:users:")
        if (id != null && id.toString() == normalizedMyUrn) return true
        if (urn != null && urn.removePrefix("soundcloud:users:") == normalizedMyUrn) return true
        return false
    }
}

data class MessageCollection(
    val collection: List<InboxMessage>,
    @SerializedName("_links") val links: Map<String, Link>? = null
)

data class InboxMessage(
    val urn: String,
    val content: String,
    @SerializedName("conversation_id") val conversationId: String,
    val sender: InboxSender?,
    @SerializedName("sender_type") val senderType: String? = null,
    @SerializedName("sent_at") val sentAt: String? = null
)

data class InboxSender(
    @SerializedName("avatar_url") val avatarUrl: String? = null,
    val username: String,
    @SerializedName("urn") val urn: String,
    @SerializedName("avatar_url_template") val avatarUrlTemplate: String? = null,
    val badges: List<String>? = null,
    val city: String? = null,
    val country: String? = null,
    @SerializedName("country_code") val countryCode: String? = null,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("followers_count") val followersCount: Int = 0,
    @SerializedName("followings_count") val followingsCount: Int = 0,
    @SerializedName("is_pro") val isPro: Boolean = false,
    @SerializedName("last_name") val lastName: String? = null,
    val permalink: String? = null,
    @SerializedName("tracks_count") val tracksCount: Int = 0,
    val verified: Boolean = false
)

data class MessageSentResponse(
    val urn: String
)

data class SendMessageRequest(
    val contents: String
)

data class CreateConversationRequest(
    val participants: List<String>
)

data class UnreadConversationsResponse(
    @SerializedName("unread_conversation_count") val unreadCount: Int
)

data class CanSendResponse(
    @SerializedName("can_send") val canSend: Boolean,
    val reason: String? = null
)

data class CanCreateResponse(
    @SerializedName("can_create") val canCreate: Boolean,
    val reason: String? = null
)

data class MeResponse(
    val user: User
)

data class ConversationsPreferences(
    val privacy: PrivacySettings
)

data class PrivacySettings(
    @SerializedName("allows_messages_from_unfollowed_users")
    val allowsMessagesFromUnfollowedUsers: Boolean
)

fun parseUserIdFromUrn(urn: String): Long? {
    val prefix = "soundcloud:users:"
    return if (urn.startsWith(prefix)) {
        urn.removePrefix(prefix).toLongOrNull()
    } else null
}

fun formatUserUrn(userId: Long): String = "soundcloud:users:$userId"

data class GraphQlRequest(
    @SerializedName("operationName") val operationName: String,
    @SerializedName("query") val query: String,
    @SerializedName("variables") val variables: Any
)

data class GraphQlVariablesInteraction(
    @SerializedName("input") val input: InteractionInput
)

data class GraphQlVariablesMusicImport(
    @SerializedName("input") val input: Any
)

data class GraphQlVariablesUserCheck(
    @SerializedName("parentUrn") val parentUrn: String,
    @SerializedName("interactionTypeUrn") val interactionTypeUrn: String = "sc:interactiontype:reaction",
    @SerializedName("targetUrns") val targetUrns: List<String>
)

data class GraphQlVariablesReactionCounts(
    @SerializedName("parentUrn") val parentUrn: String,
    @SerializedName("interactionTypeUrn") val interactionTypeUrn: String = "sc:interactiontype:trackreaction"
)

data class GraphQlVariablesReactionUsers(
    @SerializedName("parentUrn") val parentUrn: String,
    @SerializedName("interactionTypeValueUrn") val interactionTypeValueUrn: String,
    @SerializedName("interactionTypeUrn") val interactionTypeUrn: String = "sc:interactiontype:trackreaction",
    @SerializedName("limit") val limit: Int = 50,
    @SerializedName("cursor") val cursor: String = ""
)

data class TrackReactionUserItem(
    val id: String,
    val username: String,
    val avatarUrl: String?,
    val timestampSeconds: Long,
    val userUrn: String? = null
)

data class InteractionInput(
    @SerializedName("parentUrn") val parentUrn: String,
    @SerializedName("targetUrn") val targetUrn: String,
    @SerializedName("interactionTypeUrn") val interactionTypeUrn: String = "sc:interactiontype:reaction",
    @SerializedName("interactionTypeValueUrn") val interactionTypeValueUrn: String = "sc:interactiontypevalue:like"
)

data class GraphQlResponseUserInteractions(
    @SerializedName("data") val data: UserInteractionsData?
)

data class UserInteractionsData(
    @SerializedName("user") val user: List<UserInteractionNode>?
)

data class UserInteractionNode(
    @SerializedName("targetUrn") val targetUrn: String,
    @SerializedName("userInteraction") val userInteraction: Any?,
    @SerializedName("interactionCounts") val interactionCounts: List<InteractionCountNode>?
)

data class InteractionCountNode(
    @SerializedName("count") val count: Int,
    @SerializedName("interactionTypeValueUrn") val type: String
)

data class ReactionStats(val counts: List<ReactionCount>?)
data class ReactionCount(val count: Int, @SerializedName("interaction_type_urn") val urn: String?)

data class RelatedLikersRequest(
    @SerializedName("query") val query: String,
    @SerializedName("variables") val variables: RelatedLikersVariables
)

data class RelatedLikersVariables(
    @SerializedName("input") val input: RelatedLikersInput
)

data class RelatedLikersInput(
    @SerializedName("trackKeys") val trackKeys: List<RelatedLikersTrackKey>
)

data class RelatedLikersTrackKey(
    @SerializedName("urn") val urn: String
)

data class RelatedLikersResponse(
    @SerializedName("data") val data: RelatedLikersData?,
    @SerializedName("errors") val errors: List<GraphQlError>? = null
)

data class GraphQlError(
    @SerializedName("message") val message: String? = null
)

data class RelatedLikersData(
    @SerializedName("allTracks") val allTracks: List<RelatedLikersTrack>?
)

data class RelatedLikersTrack(
    @SerializedName("urn") val urn: String?,
    @SerializedName("relatedLikers") val relatedLikers: RelatedLikersResult?
)

data class RelatedLikersResult(
    @SerializedName("users") val users: List<RelatedLikersApiUser>?
)

data class RelatedLikersApiUser(
    @SerializedName("urn") val urn: String?,
    @SerializedName("username") val username: String?,
    @SerializedName("avatarUrl") val avatarUrl: String?,
    @SerializedName("verified") val verified: Boolean?
)

data class GraphQlLikesCollectionsRequest(
    @SerializedName("query") val query: String = "query MyLikesCollectionsQuery {\n  myLikesCollections {\n    collections {\n      color\n      displayName\n      id\n      size\n      tracks {\n        urn\n      }\n    }\n  }\n}"
)

data class GraphQlLikesCollectionsResponse(
    @SerializedName("data") val data: GraphQlLikesCollectionsData?,
    @SerializedName("errors") val errors: List<GraphQlError>? = null
)

data class GraphQlLikesCollectionsData(
    @SerializedName("myLikesCollections") val myLikesCollections: GraphQlMyLikesCollections?
)

data class GraphQlMyLikesCollections(
    @SerializedName("collections") val collections: List<GraphQlLikesCollection>?
)

data class GraphQlLikesCollection(
    @SerializedName("id") val id: String?,
    @SerializedName("displayName") val displayName: String?,
    @SerializedName("color") val color: String?,
    @SerializedName("size") val size: Int?,
    @SerializedName("tracks") val tracks: List<GraphQlLikesTrackUrn>?
)

data class GraphQlLikesTrackUrn(
    @SerializedName("urn") val urn: String?
)

data class Vibe(
    val id: String,
    val displayName: String,
    val color: String? = null,
    val size: Int = 0,
    val trackIds: Set<Long> = emptySet()
)

data class GraphQlFollowsRequest(
    @SerializedName("operationName") val operationName: String,
    @SerializedName("query") val query: String,
    @SerializedName("variables") val variables: GraphQlFollowsVariables
)

data class GraphQlFollowsVariables(
    @SerializedName("input") val input: GraphQlFollowsInput
)

data class GraphQlFollowsInput(
    @SerializedName("urn") val urn: String,
    @SerializedName("first") val first: Int = 30,
    @SerializedName("after") val after: String? = null
)

data class GraphQlUserFollowersResponse(
    @SerializedName("data") val data: GraphQlUserFollowersData?
)

data class GraphQlUserFollowersData(
    @SerializedName("userFollowers") val userFollowers: GraphQlFollowsResult?
)

data class GraphQlUserFollowingsResponse(
    @SerializedName("data") val data: GraphQlUserFollowingsData?
)

data class GraphQlUserFollowingsData(
    @SerializedName("userFollowings") val userFollowings: GraphQlFollowsResult?
)

data class GraphQlFollowsResult(
    @SerializedName("total") val total: Int,
    @SerializedName("pageInfo") val pageInfo: GraphQlPageInfo?,
    @SerializedName("items") val items: List<GraphQlFollowsItem>?
)

data class GraphQlPageInfo(
    @SerializedName("endCursor") val endCursor: String?
)

data class GraphQlFollowsItem(
    @SerializedName("user") val user: User?
)

data class GraphQlUserProfileResponse(
    @SerializedName("data") val data: GraphQlUserProfileData?
)

data class GraphQlUserProfileData(
    @SerializedName("user") val user: User?
)

data class TrackPublisherMetadata(
    @SerializedName("artist") val artist: String? = null,
    @SerializedName("album_title") val albumTitle: String? = null,
    @SerializedName("contains_music") val containsMusic: Boolean? = true,
    @SerializedName("publisher") val publisher: String? = null,
    @SerializedName("isrc") val isrc: String? = null,
    @SerializedName("iswc") val iswc: String? = null,
    @SerializedName("upc_or_ean") val upcOrEan: String? = null,
    @SerializedName("explicit") val explicit: Boolean? = false,
    @SerializedName("c_line") val cLine: String? = null,
    @SerializedName("p_line") val pLine: String? = null,
    @SerializedName("writer_composer") val composer: String? = null,
    @SerializedName("release_title") val releaseTitle: String? = null,
    @SerializedName("album_id") val albumId: String? = null
)

data class Track(
    val id: Long,
    val title: String?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("duration") val durationMs: Long?,
    val user: User?,
    val media: Media? = null,
    @SerializedName("user_favorite") val isLiked: Boolean = false,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("publisher_metadata") val publisherMetadata: TrackPublisherMetadata? = null,
    @SerializedName("permalink_url") val permalinkUrl: String? = null,
    @SerializedName("permalink") val permalink: String? = null,
    @SerializedName("secret_token") val secretToken: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("tag_list") val tagList: String? = null,
    @SerializedName("label_name") val labelName: String? = null,
    @SerializedName("license") val license: String? = null,
    @SerializedName("purchase_title") val purchaseTitle: String? = null,
    @SerializedName("purchase_url") val purchaseUrl: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("playback_count") val playbackCount: Int = 0,
    @SerializedName("likes_count") val likesCount: Int = 0,
    @SerializedName("reposts_count") val repostsCount: Int = 0,
    @SerializedName("comment_count") val commentCount: Int = 0,

    @SerializedName("policy") val policy: String? = null,
    @SerializedName("monetization_model") val monetizationModel: String? = null,
    @SerializedName("sharing") val sharing: String? = null,
    @SerializedName("downloadable") val downloadable: Boolean? = null,
    @SerializedName("feedable") val feedable: Boolean? = null,
    @SerializedName("embeddable") val embeddable: Boolean? = null,
    @SerializedName("streamable") val streamable: Boolean? = null,
    @SerializedName("commentable") val commentable: Boolean? = null,
    @SerializedName("reveal_comments") val revealComments: Boolean? = null,
    @SerializedName("display_stats") val displayStats: Boolean? = null,

    @SerializedName("waveform_url") val waveformUrl: String? = null,
    @SerializedName("full_duration") val fullDuration: Long? = null,
    val source: String? = "soundcloud",
    val likedAt: Long? = null,
    val playCount: Long? = null,
    val artists: List<com.alananasss.kittytune.data.spotify.SpotifyArtistRef>? = null
) {
    val displayArtist: String
        get() = publisherMetadata?.artist?.takeIf { it.isNotBlank() }
            ?: user?.username?.takeIf { it.isNotBlank() }
            ?: ""

    val fullResArtwork: String
        get() {
            if (artworkUrl != null) return artworkUrl.replace("large", "t500x500")
            if (user != null && user.avatarUrl != null) return user.avatarUrl.replace("large", "t500x500")
            return "https://picsum.photos/200"
        }
}

data class TrackLikesResponse(val collection: List<TrackLikeItem>, val next_href: String?)
data class TrackLikeItem(
    val track: Track,
    @SerializedName("created_at") val createdAt: String?
)

data class PlaylistLikesResponse(val collection: List<PlaylistLikeItem>, val next_href: String?)
data class PlaylistLikeItem(
    val playlist: Playlist?,
    @SerializedName("system_playlist") val systemPlaylist: SystemPlaylist?,
    @SerializedName("created_at") val likedAt: String?
)

data class UserPlaylistsResponse(val collection: List<Playlist>, val next_href: String?)
data class UserCollection(val collection: List<User>, val next_href: String?)
data class BasicTrackCollection(val collection: List<Track>, val next_href: String?)
data class RepostCollection(val collection: List<RepostItem>, val next_href: String?)
data class RepostItem(
    val type: String,
    @SerializedName("created_at") val createdAt: String?,
    val track: Track?,
    val playlist: Playlist?
)

data class StationLibraryResponse(val collection: List<StationLibraryItem>, val next_href: String?)
data class StationLibraryItem(
    @SerializedName("created_at") val createdAt: String?,
    val type: String?,
    @SerializedName("system_playlist") val systemPlaylist: SystemPlaylist?
)

data class SystemPlaylist(
    val urn: String?,
    val permalink: String?,
    @SerializedName("permalink_url") val permalinkUrl: String?,
    val title: String?,
    val description: String?,
    @SerializedName("short_title") val shortTitle: String?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("calculated_artwork_url") val calculatedArtworkUrl: String?,
    @SerializedName("likes_count") val likesCount: Int?,
    val tracks: List<Track>? = null,
    val user: User? = null,
    val id: String? = null
) {
    val numericId: Long
        get() {
            val parts = (urn ?: id ?: "").split(":")
            return parts.lastOrNull()?.toLongOrNull() ?: 0L
        }
    val isArtistStation: Boolean get() = (urn ?: id ?: "").contains("artist-stations")
    val isTrackStation: Boolean get() = (urn ?: id ?: "").contains("track-stations")
    val fullResArtwork: String
        get() {
            if (!artworkUrl.isNullOrEmpty()) return artworkUrl.replace("large", "t500x500")
            if (!calculatedArtworkUrl.isNullOrEmpty()) return calculatedArtworkUrl.replace("large", "t500x500")
            return user?.avatarUrl?.replace("large", "t500x500") ?: "https://picsum.photos/200"
        }
}

data class UpdateProfileRequest(
    val username: String?,
    val description: String?,
    val city: String?,
    @SerializedName("country_code") val countryCode: String?,
    @SerializedName("first_name") val firstName: String? = null,
    @SerializedName("last_name") val lastName: String? = null
)

data class AvatarUpdateRequest(@SerializedName("image_data") val imageData: String)

data class BannerUploadRequest(@SerializedName("image_data") val imageData: String)

data class MixedSelectionsResponse(
    val collection: List<SelectionItem>,
    val next_href: String? = null
)

data class SelectionItem(
    val urn: String?,
    val id: String?,
    val title: String?,
    val description: String?,
    val items: SelectionItems?,
    val kind: String?,
    @SerializedName("tracking_feature_name") val trackingFeatureName: String?
)

data class TagSuggestionResponse(
    val suggestions: List<TagSuggestion>?
)

data class TagSuggestion(
    val query: String,
    val id: String
)

data class PlaylistCreateRequest(
    val playlist: PlaylistCreatePayload,
    @SerializedName("track_urns") val trackUrns: List<String> = emptyList()
)

data class PlaylistCreatePayload(
    val title: String,
    @SerializedName("public") val isPublic: Boolean
)

data class PlaylistUpdateRequest(
    @SerializedName("track_urns") val trackUrns: List<String> = emptyList(),
    val description: String = "",
    val title: String = "",
    val genre: String = "",
    @SerializedName("public") val isPublic: Boolean = false,
    @SerializedName("tag_list") val tagList: String = ""
)

data class SelectionItems(
    val collection: List<com.google.gson.JsonElement>?
)

data class Playlist(
    @JsonAdapter(LongIdAdapter::class) val id: Long,
    val title: String?,
    @SerializedName("artwork_url") val artworkUrl: String?,
    @SerializedName("calculated_artwork_url") val calculatedArtworkUrl: String?,
    @SerializedName("track_count") val trackCount: Int?,
    val user: User?,
    @JsonAdapter(PlaylistTracksAdapter::class) val tracks: List<Track>? = null,
    @SerializedName("is_album") val isAlbum: Boolean = false,
    @SerializedName("permalink_url") val permalinkUrl: String? = null,
    @SerializedName("permalink") val permalink: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("urn") val urn: String? = null,
    @SerializedName("last_modified") val lastModified: String? = null,
    @SerializedName("tag_list") val tagList: String? = null,
    @SerializedName("genre") val genre: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("sharing") val sharing: String? = null,
    @SerializedName("secret_token") val secretToken: String? = null,
    @SerializedName("set_type") val setType: String? = null,
    @SerializedName("playlist_type") val playlistType: String? = null,
    @SerializedName("release_date") val releaseDate: String? = null,
    @SerializedName("likes_count") val likesCount: Int? = 0
) {
    val isRealAlbum: Boolean
        get() = isAlbum || 
                setType?.equals("album", ignoreCase = true) == true || 
                setType?.equals("ep", ignoreCase = true) == true || 
                setType?.equals("single", ignoreCase = true) == true || 
                setType?.equals("compilation", ignoreCase = true) == true ||
                playlistType?.equals("album", ignoreCase = true) == true || 
                playlistType?.equals("ep", ignoreCase = true) == true || 
                playlistType?.equals("single", ignoreCase = true) == true || 
                playlistType?.equals("compilation", ignoreCase = true) == true

    val fullResArtwork: String
        get() {
            if (!artworkUrl.isNullOrEmpty()) return artworkUrl.replace("large", "t500x500")
            if (!calculatedArtworkUrl.isNullOrEmpty()) return calculatedArtworkUrl.replace("large", "t500x500")
            if (!tracks.isNullOrEmpty()) {
                val firstTrackArt = tracks[0].fullResArtwork
                if (!firstTrackArt.contains("picsum")) return firstTrackArt
            }
            return user?.avatarUrl?.replace("large", "t500x500") ?: "https://picsum.photos/200"
        }
}

data class User(
    val id: Long,
    val username: String?,
    @SerializedName("avatar_url", alternate = ["avatarUrl"]) val avatarUrl: String?,
    val city: String? = null,
    val country: String? = null,
    @SerializedName("country_code", alternate = ["countryCode"]) val countryCode: String? = null,
    @SerializedName("first_name", alternate = ["firstName"]) val firstName: String? = null,
    @SerializedName("last_name", alternate = ["lastName"]) val lastName: String? = null,
    @SerializedName("followers_count", alternate = ["followersCount"]) val followersCount: Int = 0,
    @SerializedName("followings_count", alternate = ["followingsCount"]) val followingsCount: Int = 0,
    @SerializedName("track_count", alternate = ["tracksCount", "trackCount"]) val trackCount: Int = 0,
    @SerializedName("playlist_count", alternate = ["playlistCount", "public_playlists_count", "publicPlaylistsCount"]) val playlistCount: Int = 0,
    @SerializedName("description") val description: String? = null,
    @SerializedName("permalink_url", alternate = ["permalinkUrl"]) val permalinkUrl: String? = null,
    @SerializedName("permalink") val permalink: String? = null,
    val visuals: Visuals? = null,
    @SerializedName("verified") val verified: Boolean = false,
    @SerializedName("is_pro", alternate = ["isPro"]) val isPro: Boolean = false,
    @SerializedName("created_at", alternate = ["createdAt"]) val createdAt: String? = null,
    @SerializedName("public_favorites_count") private val _publicFavoritesCount: Int? = 0,
    @SerializedName("likes_count") private val _likesCount: Int? = 0,
    @SerializedName("favorites_count") private val _favoritesCount: Int? = 0,
    @SerializedName("urn") val urn: String? = null
) {
    val isArtist: Boolean
        get() = verified || trackCount > 0 || urn?.startsWith("spotify:artist:") == true

    val likesCount: Int
        get() = when {
            (_publicFavoritesCount ?: 0) > 0 -> _publicFavoritesCount!!
            (_likesCount ?: 0) > 0 -> _likesCount!!
            (_favoritesCount ?: 0) > 0 -> _favoritesCount!!
            else -> 0
        }
    val bannerUrl: String? get() = visuals?.visuals?.firstOrNull()?.visualUrl

    val numericId: Long
        get() {
            if (id != 0L) return id
            return urn?.split(":")?.lastOrNull()?.toLongOrNull() ?: 0L
        }

    val profileNavId: String
        get() = when {
            urn?.startsWith("spotify:artist:") == true -> "spotify_artist:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(urn)}"
            urn?.contains("spotify") == true -> "spotify_artist:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(urn)}"
            permalinkUrl?.contains("spotify") == true -> "spotify_artist:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(permalinkUrl)}"
            !permalink.isNullOrBlank() && id == 0L -> "spotify_artist:${com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(permalink)}"
            id > 0L -> "profile:$id"
            !permalink.isNullOrBlank() -> "profile:$permalink"
            else -> "profile:$id"
        }
}

data class Visuals(val visuals: List<VisualItem>?)
data class VisualItem(@SerializedName("visual_url") val visualUrl: String)
data class Media(val transcodings: List<Transcoding>?)
data class Transcoding(val url: String, val preset: String, val format: Format?)
data class Format(val protocol: String?, @SerializedName("mime_type") val mimeType: String?)
data class StreamUrlResponse(
    val url: String?,
    @SerializedName("licenseAuthToken") val licenseAuthToken: String? = null
)

class PlaylistTracksAdapter : com.google.gson.JsonDeserializer<List<Track>> {
    override fun deserialize(
        json: com.google.gson.JsonElement,
        typeOfT: java.lang.reflect.Type,
        context: com.google.gson.JsonDeserializationContext
    ): List<Track>? {
        if (json.isJsonArray) {
            val list = mutableListOf<Track>()
            json.asJsonArray.forEach {
                list.add(context.deserialize(it, Track::class.java))
            }
            return list
        }
        return null
    }
}

fun String?.isDefaultAvatar(): Boolean {
    if (this.isNullOrBlank()) return true
    if (this.contains("default_avatar", ignoreCase = true)) return true
    if (this.startsWith("https://a1.sndcdn.com/images/default_avatar_")) return true
    return false
}

fun String?.getHighResAvatarUrl(): String? {
    if (this == null || this.isDefaultAvatar()) return null
    return this.replace("large", "t500x500")
}
