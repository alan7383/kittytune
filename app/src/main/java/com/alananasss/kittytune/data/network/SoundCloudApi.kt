package com.alananasss.kittytune.data.network

import com.alananasss.kittytune.domain.*
import retrofit2.http.Body
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

    // --- HOME & DISCOVERY ---
    @GET("charts")
    suspend fun getCharts(
        @Query("kind") kind: String = "top",
        @Query("genre") genre: String = "soundcloud:genres:all-music",
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): ChartsResponse

    // The subscription stream
    @GET("stream")
    suspend fun getMyStream(
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): StreamResponse

    // --- TRACK DETAILS ---
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
        @Query("limit") limit: Int = 50
    ): InPlaylistCollection

    // --- GENERIC PAGINATION ---
    @GET
    suspend fun getLikersNextPage(@Url url: String): LikerCollection

    @GET
    suspend fun getRepostersNextPage(@Url url: String): ReposterCollection

    @GET
    suspend fun getInPlaylistsNextPage(@Url url: String): InPlaylistCollection

    @GET
    suspend fun getRelatedTracksNextPage(@Url url: String): BasicTrackCollection

    @PUT("me")
    suspend fun updateMe(@Body body: UpdateProfileRequest): User

    // --- REPOSTS ---
    @GET("stream/users/{userId}")
    suspend fun getUserReposts(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 30,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): RepostCollection

    @GET
    suspend fun getRepostsNextPage(@Url url: String): RepostCollection

    // --- RESEARCH ---
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

    @GET
    suspend fun getSearchTracksNextPage(@Url url: String): BasicTrackCollection

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

    // --- FAVORITES ---
    @GET("users/{userId}/track_likes")
    suspend fun getUserTrackLikes(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 100,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): TrackLikesResponse

    @GET
    suspend fun getTrackLikesNextPage(@Url url: String): TrackLikesResponse

    @GET("users/{userId}/playlist_likes")
    suspend fun getUserPlaylistLikes(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 50
    ): PlaylistLikesResponse

    @GET("users/{userId}/playlists")
    suspend fun getUserCreatedPlaylists(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 50
    ): UserPlaylistsResponse

    @GET("playlists/{playlistId}")
    suspend fun getPlaylist(@Path("playlistId") playlistId: Long): Playlist

    @GET("tracks")
    suspend fun getTracksByIds(@Query("ids") ids: String): List<Track>

    @GET
    suspend fun getStreamUrl(@Url url: String): StreamUrlResponse

    // --- STATIONS ---
    @GET("system-playlists/soundcloud:system-playlists:track-stations:{trackId}")
    suspend fun getTrackStation(@Path("trackId") trackId: Long): Playlist

    @GET("system-playlists/soundcloud:system-playlists:artist-stations:{userId}")
    suspend fun getArtistStation(@Path("userId") userId: Long): Playlist

    @GET("tracks/{trackId}/related")
    suspend fun getRelatedTracks(
        @Path("trackId") trackId: Long,
        @Query("limit") limit: Int = 10
    ): BasicTrackCollection

    @GET("users/{userId}")
    suspend fun getUser(@Path("userId") userId: Long): User

    @GET("users/{userId}/tracks")
    suspend fun getUserTracks(
        @Path("userId") userId: Long,
        @Query("limit") limit: Int = 20,
        @Query("linked_partitioning") linkedPartitioning: Int = 1
    ): BasicTrackCollection

    @GET
    suspend fun getUserTracksNextPage(@Url url: String): BasicTrackCollection

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

    // --- COMMENTS ---
    @GET("tracks/{trackId}/comments")
    suspend fun getTrackComments(
        @Path("trackId") trackId: Long,
        @Query("limit") limit: Int = 50,
        @Query("linked_partitioning") linkedPartitioning: Int = 1,
        @Query("threaded") threaded: Int = 0,
        @Query("filter_replies") filterReplies: Int = 0,
        @Query("representation") representation: String = "full"
    ): CommentCollection

    @GET
    suspend fun getCommentsNextPage(@Url url: String): CommentCollection

    @POST("comments/{commentId}/likes")
    suspend fun likeComment(@Path("commentId") commentId: Long): retrofit2.Response<Unit>

    @GET("resolve")
    suspend fun resolveUrl(@Query("url") url: String): User

    @DELETE("comments/{commentId}/likes")
    suspend fun unlikeComment(@Path("commentId") commentId: Long): retrofit2.Response<Unit>

    @POST("tracks/{trackId}/comments")
    suspend fun postComment(
        @Path("trackId") trackId: Long,
        @Body request: PostCommentRequest
    ): Comment
}