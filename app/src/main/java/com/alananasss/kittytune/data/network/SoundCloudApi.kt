package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.data.upload.ArtworkUploadRequest
import com.alananasss.kittytune.data.upload.CreateTrackGraphQlRequest
import com.alananasss.kittytune.data.upload.CreateTrackGraphQlResponse
import com.alananasss.kittytune.data.upload.CreateTranscodingGraphQlRequest
import com.alananasss.kittytune.data.upload.CreateTranscodingGraphQlResponse
import com.alananasss.kittytune.data.upload.DeleteTrackGraphQlRequest
import com.alananasss.kittytune.data.upload.DeleteTrackGraphQlResponse
import com.alananasss.kittytune.data.upload.EditTrackGraphQlRequest
import com.alananasss.kittytune.data.upload.EditTrackGraphQlResponse
import com.alananasss.kittytune.data.upload.CreateBuyModuleGraphQlRequest
import com.alananasss.kittytune.data.upload.CreateBuyModuleGraphQlResponse
import com.alananasss.kittytune.data.upload.DeleteBuyModuleGraphQlRequest
import com.alananasss.kittytune.data.upload.DeleteBuyModuleGraphQlResponse
import com.alananasss.kittytune.data.upload.FetchBuyModuleGraphQlRequest
import com.alananasss.kittytune.data.upload.FetchBuyModuleGraphQlResponse
import com.alananasss.kittytune.data.upload.FetchEditableTrackGraphQlRequest
import com.alananasss.kittytune.data.upload.FetchEditableTrackGraphQlResponse
import com.alananasss.kittytune.data.upload.TranscodingStatusGraphQlRequest
import com.alananasss.kittytune.data.upload.TranscodingStatusGraphQlResponse
import com.alananasss.kittytune.data.upload.UploadEligibilityResponse
import com.alananasss.kittytune.data.upload.UploadPolicyRequest
import com.alananasss.kittytune.data.upload.UploadPolicyResponse
import com.alananasss.kittytune.domain.*
import com.google.gson.JsonObject
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url
import retrofit2.http.DELETE

interface SoundCloudApi {

    @GET("me")
    suspend fun getMe(): User

    @GET("https://api-mobile.soundcloud.com/me")
    suspend fun getMeMobile(): MeResponse

    @PUT("me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): User

    @POST("https://api-mobile.soundcloud.com/you/profile/avatar")
    suspend fun updateAvatar(@Body body: AvatarUpdateRequest): retrofit2.Response<Unit>

    @DELETE("https://api-mobile.soundcloud.com/you/profile/avatar")
    suspend fun deleteAvatar(): retrofit2.Response<Unit>

    @GET("charts")
    suspend fun getCharts(
        @Query("kind") kind: String = "top",
        @Query("genre") genre: String = "soundcloud:genres:all-music",
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): ChartsResponse

    @GET("stream")
    suspend fun getMyStream(
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): StreamResponse

    @GET("https://api-v2.soundcloud.com/mixed-selections")
    suspend fun getMixedSelections(
        @Query("limit") limit: Int = 10,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): MixedSelectionsResponse

    @GET("activities")
    suspend fun getActivities(
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): ActivitiesResponse

    @GET("tracks/{trackId}/likers")
    suspend fun getTrackLikers(
        @Path("trackId") trackId: Long,
        @Query("limit") limit: Int = 50
    ): LikerCollection

    @GET("tracks/{trackId}/reposters")
    suspend fun getTrackReposters(
        @Path("trackId") trackId: Long,
        @Query("limit") limit: Int = 50
    ): ReposterCollection

    @GET("tracks/{trackId}/playlists")
    suspend fun getTrackInPlaylists(
        @Path("trackId") trackId: Long,
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int? = null
    ): InPlaylistCollection

    @GET("tracks/{trackId}/related")
    suspend fun getRelatedTracks(
        @Path("trackId") trackId: Long,
        @Query("limit") limit: Int = 10
    ): BasicTrackCollection

    @GET("tracks")
    suspend fun getTracksByIds(@Query("ids") ids: String): List<Track>

    @GET("resolve")
    suspend fun resolveUrl(@Query("url") url: String): JsonObject

    @GET
    suspend fun getStreamUrl(@Url url: String): StreamUrlResponse

    @POST("https://api-mobile.soundcloud.com/you/profile/header_image")
    suspend fun updateBanner(@Body body: BannerUploadRequest): retrofit2.Response<Unit>

    @DELETE("https://api-mobile.soundcloud.com/you/profile/header_image")
    suspend fun deleteBanner(): retrofit2.Response<Unit>

    @GET("stream/users/{userId}")
    suspend fun getUserReposts(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 30,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): RepostCollection

    @PUT("me/track_reposts/{trackId}")
    suspend fun repostTrack(@Path("trackId") trackId: Long): retrofit2.Response<Unit>

    @PUT("me/track_reposts/{trackId}/caption")
    suspend fun addRepostCaption(
        @Path("trackId") trackId: Long,
        @Body body: RepostCaptionRequest
    ): retrofit2.Response<Unit>

    @DELETE("me/track_reposts/{trackId}")
    suspend fun deleteRepost(@Path("trackId") trackId: Long): retrofit2.Response<Unit>

    @POST("https://api-mobile.soundcloud.com/likes/tracks/create")
    suspend fun likeTrack(
        @Body body: TrackLikeRequest
    ): retrofit2.Response<Unit>

    @POST("https://api-mobile.soundcloud.com/likes/tracks/delete")
    suspend fun unlikeTrack(
        @Body body: TrackLikeRequest
    ): retrofit2.Response<Unit>

    @POST("https://api-mobile.soundcloud.com/likes/playlists/create")
    suspend fun likePlaylist(
        @Body body: PlaylistLikeRequest
    ): retrofit2.Response<Unit>

    @POST("https://api-mobile.soundcloud.com/likes/playlists/delete")
    suspend fun unlikePlaylist(
        @Body body: PlaylistLikeRequest
    ): retrofit2.Response<Unit>

    @GET("users/{userId}/track_likes")
    suspend fun getUserTrackLikes(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 100,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): TrackLikesResponse

    @GET("users/{userId}/playlist_likes")
    suspend fun getUserPlaylistLikes(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 200,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): PlaylistLikesResponse

    @GET("me/library/all")
    suspend fun getMyLibraryAll(
        @Query("limit") limit: Int = 200,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): StationLibraryResponse

    @GET("search/tracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): BasicTrackCollection

    @GET("search/tracks")
    suspend fun searchTracks(
        @Query("q") query: String,
        @Query("limit") limit: Int = 50,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): BasicTrackCollection

    @GET("search/tracks")
    suspend fun searchTracksStrict(
        @Query("q") query: String = "*",
        @Query("filter.genre_or_tag") tag: String,
        @Query("sort") sort: String,
        @Query("limit") limit: Int = 50,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): BasicTrackCollection

    @GET("recent-tracks/{tag}")
    suspend fun getRecentTracksByTag(
        @Path("tag") tag: String,
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): BasicTrackCollection

    @GET("search/tracks")
    suspend fun searchTracksPop(
        @Query("q") query: String,
        @Query("sort") sort: String = "popular",
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): BasicTrackCollection

    @GET("search/users")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): UserCollection

    @GET("search/playlists")
    suspend fun searchPlaylists(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): UserPlaylistsResponse

    @GET("search/albums")
    suspend fun searchAlbums(
        @Query("q") query: String,
        @Query("limit") limit: Int = 20
    ): UserPlaylistsResponse

    @GET("https://api-mobile.soundcloud.com/conversations")
    suspend fun getInbox(
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): InboxCollection

    @GET("https://api-mobile.soundcloud.com/conversations/unread")
    suspend fun getUnreadConversations(): UnreadConversationsResponse

    @GET("https://api-mobile.soundcloud.com/conversations/{conversationId}/messages")
    suspend fun getConversationMessages(
        @Path("conversationId") conversationId: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): MessageCollection

    @POST("https://api-mobile.soundcloud.com/conversations/{conversationId}")
    suspend fun sendMessage(
        @Path("conversationId") conversationId: String,
        @Body request: SendMessageRequest
    ): MessageSentResponse

    @GET
    suspend fun getLikersNextPage(@Url url: String): LikerCollection

    @GET
    suspend fun getRepostersNextPage(@Url url: String): ReposterCollection

    @GET
    suspend fun getInPlaylistsNextPage(@Url url: String): InPlaylistCollection

    @GET
    suspend fun getRelatedTracksNextPage(@Url url: String): BasicTrackCollection

    @GET
    suspend fun getRepostsNextPage(@Url url: String): RepostCollection

    @GET
    suspend fun getActivitiesNextPage(@Url url: String): ActivitiesResponse

    @GET
    suspend fun getConversationsNextPage(@Url url: String): InboxCollection

    @GET
    suspend fun getMessagesNextPage(@Url url: String): MessageCollection

    @GET
    suspend fun getSearchTracksNextPage(@Url url: String): BasicTrackCollection

    @GET
    suspend fun getSearchPlaylistsNextPage(@Url url: String): UserPlaylistsResponse

    @GET
    suspend fun getSearchUsersNextPage(@Url url: String): UserCollection

    @GET
    suspend fun getTrackLikesNextPage(@Url url: String): TrackLikesResponse

    @GET
    suspend fun getUserTracksNextPage(@Url url: String): BasicTrackCollection

    @GET
    suspend fun getCommentsNextPage(@Url url: String): CommentCollection

    @GET
    suspend fun getPlaylistLikesNextPage(@Url url: String): PlaylistLikesResponse

    @GET("users/{userId}/followings")
    suspend fun getUserFollowings(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 50,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): UserCollection

    @GET
    suspend fun getUserCollectionNextPage(@Url url: String): UserCollection

    @GET("users/{userId}/playlists")
    suspend fun getUserCreatedPlaylists(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 200,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): UserPlaylistsResponse

    @GET("https://api-mobile.soundcloud.com/you/posts_and_reposts/playlists")
    suspend fun getMyPlaylistPosts(
        @Query("limit") limit: Int = 200
    ): RepostCollection

    @GET("https://api-v2.soundcloud.com/playlists/{playlistId}")
    suspend fun getPlaylist(@Path("playlistId") playlistId: Long): Playlist

    @GET("playlists/{playlistId}/likers")
    suspend fun getPlaylistLikers(
        @Path("playlistId") playlistId: Long,
        @Query("limit") limit: Int = 50
    ): LikerCollection

    @GET("playlists/{playlistId}/reposters")
    suspend fun getPlaylistReposters(
        @Path("playlistId") playlistId: Long,
        @Query("limit") limit: Int = 50
    ): ReposterCollection

    @GET("system-playlists/soundcloud:system-playlists:track-stations:{trackId}")
    suspend fun getTrackStation(@Path("trackId") trackId: Long): Playlist

    @GET("system-playlists/soundcloud:system-playlists:artist-stations:{userId}")
    suspend fun getArtistStation(@Path("userId") userId: Long): Playlist

    @GET("system-playlists/{urn}")
    suspend fun getSystemPlaylist(@Path("urn", encoded = true) urn: String): Playlist

    @PUT("https://api-mobile.soundcloud.com/playlists/soundcloud:playlists:{id}")
    suspend fun updatePlaylist(
        @Path("id") id: Long,
        @Body request: PlaylistUpdateRequest
    ): retrofit2.Response<Unit>

    @POST("https://api-mobile.soundcloud.com/playlists")
    suspend fun createPlaylist(
        @Body request: PlaylistCreateRequest
    ): retrofit2.Response<com.google.gson.JsonElement>

    @GET("https://api-v2.soundcloud.com/search/suggest/tags")
    suspend fun searchTags(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): TagSuggestionResponse

    @DELETE("https://api-v2.soundcloud.com/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Long): retrofit2.Response<Unit>

    @POST("https://api-mobile.soundcloud.com/follows/users/soundcloud:users:{userId}")
    suspend fun followUser(
        @Path("userId") userId: Long,
        @Body body: Map<String, String> = emptyMap()
    ): retrofit2.Response<Unit>

    @DELETE("https://api-mobile.soundcloud.com/follows/users/soundcloud:users:{userId}")
    suspend fun unfollowUser(@Path("userId") userId: Long): retrofit2.Response<Unit>

    @GET("https://api.soundcloud.com/me/followings/{userId}")
    suspend fun checkFollowState(@Path("userId") userId: Long): retrofit2.Response<Unit>

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun getUserFollowersGraphQL(
        @Body request: GraphQlFollowsRequest
    ): GraphQlUserFollowersResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun getUserFollowingsGraphQL(
        @Body request: GraphQlFollowsRequest
    ): GraphQlUserFollowingsResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun getUserProfileGraphQL(
        @Body request: GraphQlRequest
    ): GraphQlUserProfileResponse

    @GET("users/{userId}")
    suspend fun getUser(@Path("userId") userId: Long): User

    @GET("users/{userId}/tracks")
    suspend fun getUserTracks(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): BasicTrackCollection

    @GET("users/{userId}/toptracks")
    suspend fun getUserTopTracks(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 10
    ): BasicTrackCollection

    @GET("users/{userId}/albums")
    suspend fun getUserAlbums(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 20
    ): UserPlaylistsResponse

    @GET("tracks/{trackId}/comments")
    suspend fun getTrackComments(
        @Path("trackId") trackId: Long,
        @Query("limit") limit: Int = 50,
        @Query("linked_partitioning") linkedPartitioning: Int = 1,
        @Query("threaded") threaded: Int = 0,
        @Query("filter_replies") filterReplies: Int = 0,
        @Query("representation") representation: String = "full",
        @Query("sort") sort: String? = "newest"
    ): CommentCollection

    @GET("users/{userId}/comments")
    suspend fun getUserComments(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: String? = null,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): CommentCollection

    @GET
    suspend fun getUserCommentsNextPage(@Url url: String): CommentCollection

    @FormUrlEncoded
    @POST("tracks/{trackId}/comments")
    suspend fun postComment(
        @Path("trackId") trackId: Long,
        @Field("body") body: String,
        @Field("timestamp") timestamp: Long,
        @Field("parent_id") parentId: Long? = null
    ): Comment

    @DELETE("comments/{commentId}")
    suspend fun deleteComment(@Path("commentId") commentId: Long): retrofit2.Response<Unit>

    @POST("comments/{commentId}/likes")
    suspend fun likeComment(@Path("commentId") commentId: Long): retrofit2.Response<Unit>

    @DELETE("comments/{commentId}/likes")
    suspend fun unlikeComment(@Path("commentId") commentId: Long): retrofit2.Response<Unit>

    @POST("https://api-mobile.soundcloud.com/recently-played/contexts/v2")
    suspend fun pushRecentlyPlayed(
        @Body request: ApiCollection<ApiRecentlyPlayed>
    ): retrofit2.Response<Unit>

    @GET("https://api-mobile.soundcloud.com/recently-played/contexts/v2")
    suspend fun getRecentlyPlayedContexts(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int? = null
    ): retrofit2.Response<ApiCollection<ApiRecentlyPlayed>>

    @GET
    suspend fun getRecentlyPlayedContextsNext(
        @Url url: String
    ): retrofit2.Response<ApiCollection<ApiRecentlyPlayed>>

    @DELETE("https://api-mobile.soundcloud.com/recently-played/contexts/all")
    suspend fun clearRecentlyPlayedContexts(): retrofit2.Response<Unit>

    @GET("https://api-mobile.soundcloud.com/recently-played/tracks")
    suspend fun getPlayHistory(
        @Query("limit") limit: Int = 100,
        @Query("offset") offset: Int? = null
    ): retrofit2.Response<ApiCollection<ApiRecentlyPlayed>>

    @GET
    suspend fun getPlayHistoryNext(
        @Url url: String
    ): retrofit2.Response<ApiCollection<ApiRecentlyPlayed>>

    @DELETE("https://api-mobile.soundcloud.com/recently-played/tracks/all")
    suspend fun clearPlayHistory(): retrofit2.Response<Unit>


    @POST
    suspend fun postGraphQl(@Url url: String, @Body request: GraphQlRequest): JsonObject

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun postMusicImportGraphQl(@Body request: GraphQlRequest): JsonObject

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun getRelatedLikersGraphQL(
        @Body request: RelatedLikersRequest
    ): RelatedLikersResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun getMyLikesCollectionsGraphQL(
        @Body request: GraphQlLikesCollectionsRequest = GraphQlLikesCollectionsRequest()
    ): GraphQlLikesCollectionsResponse

    @GET("https://api-mobile.soundcloud.com/you/upload-eligibility")
    suspend fun getUploadEligibility(): UploadEligibilityResponse

    @POST("https://api-mobile.soundcloud.com/you/upload-policy")
    suspend fun fetchUploadPolicy(
        @Body request: UploadPolicyRequest
    ): UploadPolicyResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun createTrackGraphQl(
        @Body request: CreateTrackGraphQlRequest
    ): CreateTrackGraphQlResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun createTranscodingGraphQl(
        @Body request: CreateTranscodingGraphQlRequest
    ): CreateTranscodingGraphQlResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun getTranscodingStatusGraphQl(
        @Body request: TranscodingStatusGraphQlRequest
    ): TranscodingStatusGraphQlResponse

    @PUT("https://api-mobile.soundcloud.com/tracks/{trackUrn}/artwork")
    suspend fun uploadTrackArtwork(
        @Path("trackUrn", encoded = true) trackUrn: String,
        @Body request: ArtworkUploadRequest
    ): retrofit2.Response<Unit>

    @PUT("https://api-mobile.soundcloud.com/playlists/{playlistUrn}/artwork")
    suspend fun uploadPlaylistArtwork(
        @Path("playlistUrn", encoded = true) playlistUrn: String,
        @Body request: ArtworkUploadRequest
    ): retrofit2.Response<Unit>

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun fetchEditableTrackGraphQl(
        @Body request: FetchEditableTrackGraphQlRequest
    ): FetchEditableTrackGraphQlResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun editTrackGraphQl(
        @Body request: EditTrackGraphQlRequest
    ): EditTrackGraphQlResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun deleteTrackGraphQl(
        @Body request: DeleteTrackGraphQlRequest
    ): DeleteTrackGraphQlResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun fetchBuyModuleGraphQl(
        @Body request: FetchBuyModuleGraphQlRequest
    ): FetchBuyModuleGraphQlResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun createBuyModuleGraphQl(
        @Body request: CreateBuyModuleGraphQlRequest
    ): CreateBuyModuleGraphQlResponse

    @POST("https://graph.soundcloud.com/graphql")
    suspend fun deleteBuyModuleGraphQl(
        @Body request: DeleteBuyModuleGraphQlRequest
    ): DeleteBuyModuleGraphQlResponse

    @DELETE("https://api-v2.soundcloud.com/tracks/{trackId}")
    suspend fun deleteTrackRest(
        @Path("trackId") trackId: Long
    ): retrofit2.Response<Unit>
}

data class ApiRecentlyPlayed(
    @com.google.gson.annotations.SerializedName("played_at") val playedAt: Long,
    @com.google.gson.annotations.SerializedName("urn") val urn: String
)

data class ApiLink(
    @com.google.gson.annotations.SerializedName("href") val href: String? = null
)

data class ApiCollection<T>(
    @com.google.gson.annotations.SerializedName("collection") val collection: List<T> = emptyList(),
    @com.google.gson.annotations.SerializedName("next_href") val nextHref: String? = null,
    @com.google.gson.annotations.SerializedName("query_urn") val queryUrn: String? = null,
    @com.google.gson.annotations.SerializedName("_links") val links: Map<String, ApiLink>? = null
) {
    val nextUrl: String?
        get() = nextHref ?: links?.get("next")?.href
}

data class TrackLikeItem(
    @com.google.gson.annotations.SerializedName("target_urn") val targetUrn: String
)

data class TrackLikeRequest(
    @com.google.gson.annotations.SerializedName("likes") val likes: List<TrackLikeItem>
)

data class PlaylistLikeItem(
    @com.google.gson.annotations.SerializedName("target_urn") val targetUrn: String
)

data class PlaylistLikeRequest(
    @com.google.gson.annotations.SerializedName("likes") val likes: List<PlaylistLikeItem>
)

