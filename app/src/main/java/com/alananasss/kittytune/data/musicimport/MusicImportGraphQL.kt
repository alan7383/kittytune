package com.alananasss.kittytune.data.musicimport

import com.google.gson.annotations.SerializedName

object MusicImportGraphQL {

    val QUERY_EXTERNAL_PLAYLISTS = """
        query ExternalPlaylists(${'$'}input: ExternalPlaylistsInput!) {
            externalPlaylists(input: ${'$'}input) {
                ... on ExternalPlaylists {
                    playlists {
                        id
                        name
                        imageUrl
                        isOwner
                        totalItems
                    }
                    pageInfo {
                        endCursor
                        hasNextPage
                        totalItems
                    }
                }
                ... on ClientError {
                    __typename
                    errorMessage
                }
                ... on AuthenticationError {
                    __typename
                    errorMessage
                }
                ... on TooManyRequestsError {
                    __typename
                    errorMessage
                    retryAfter
                }
            }
        }
    """.trimIndent()

    val QUERY_EXTERNAL_LIKED_TRACKS = """
        query ExternalLikedTracks(${'$'}input: ExternalLikedTracksInput!) {
            externalLikedTracks(input: ${'$'}input) {
                ... on ExternalTracks {
                    tracks {
                        id
                        name
                        isrc
                        artists {
                            name
                        }
                        imageUrl
                    }
                    pageInfo {
                        endCursor
                        hasNextPage
                        totalItems
                    }
                }
                ... on ClientError {
                    __typename
                    errorMessage
                }
                ... on AuthenticationError {
                    __typename
                    errorMessage
                }
                ... on TooManyRequestsError {
                    __typename
                    errorMessage
                    retryAfter
                }
            }
        }
    """.trimIndent()

    val QUERY_LIKES_SYNC_STATUS = """
        query LikesSyncStatus(${'$'}input: LikesSyncStatusInput!) {
            likesSyncStatus(input: ${'$'}input) {
                ... on LikesSyncStatuses {
                    statuses {
                        isSyncing
                        progressPercent
                    }
                }
            }
        }
    """.trimIndent()

    val QUERY_PLAYLIST_SYNC_STATUS = """
        query PlaylistSyncStatus(${'$'}input: PlaylistsSyncStatusInput!) {
            playlistSyncStatus(input: ${'$'}input) {
                ... on PlaylistSyncStatuses {
                    statuses {
                        ... on PlaylistSyncStatus {
                            playlistId
                            isSyncing
                            progressPercent
                        }
                    }
                }
            }
        }
    """.trimIndent()

    val MUTATION_START_LIKES_SYNC = """
        mutation StartLikesSync(${'$'}input: StartLikesSyncInput!) {
            startLikesSync(input: ${'$'}input) {
                ... on LikesSyncStatuses {
                    statuses {
                        isSyncing
                        progressPercent
                    }
                }
            }
        }
    """.trimIndent()

    val MUTATION_START_PLAYLIST_SYNC = """
        mutation StartPlaylistSync(${'$'}input: StartPlaylistSyncInput!) {
            startPlaylistSync(input: ${'$'}input) {
                ... on StartPlaylistSyncStatuses {
                    statuses {
                        ... on PlaylistSyncStatus {
                            playlistId
                            isSyncing
                            progressPercent
                        }
                    }
                }
            }
        }
    """.trimIndent()

    data class ExternalPlaylistsInput(
        @SerializedName("userPlatformUuid") val userPlatformUuid: String,
        @SerializedName("limit") val limit: Int?,
        @SerializedName("next") val next: String?
    )

    data class ExternalLikedTracksInput(
        @SerializedName("userPlatformUuid") val userPlatformUuid: String,
        @SerializedName("limit") val limit: Int?,
        @SerializedName("next") val next: String?
    )

    data class StartPlaylistSyncInput(
        @SerializedName("userPlatformUuid") val userPlatformUuid: String,
        @SerializedName("playlistIds") val playlistIds: List<String>
    )

    data class StartLikesSyncInput(
        @SerializedName("userPlatformUuid") val userPlatformUuid: String,
        @SerializedName("importAsPlaylists") val importAsPlaylists: Boolean
    )

    data class PlaylistsSyncStatusInput(
        @SerializedName("userPlatformUuid") val userPlatformUuid: String,
        @SerializedName("playlistIds") val playlistIds: List<String>
    )

    data class LikesSyncStatusInput(
        @SerializedName("userPlatformUuid") val userPlatformUuid: String
    )

    // --- Responses ---

    data class ExternalPlaylistsResponse(
        @SerializedName("data") val data: ExternalPlaylistsData?
    )

    data class ExternalPlaylistsData(
        @SerializedName("externalPlaylists") val externalPlaylists: ExternalPlaylistsResult?
    )

    data class ExternalPlaylistsResult(
        @SerializedName("playlists") val playlists: List<ExternalPlaylist>?,
        @SerializedName("pageInfo") val pageInfo: PageInfo?,
        @SerializedName("__typename") val typename: String?,
        @SerializedName("errorMessage") val errorMessage: String?,
        @SerializedName("retryAfter") val retryAfter: Long?
    )

    data class ExternalLikedTracksResponse(
        @SerializedName("data") val data: ExternalLikedTracksData?
    )

    data class ExternalLikedTracksData(
        @SerializedName("externalLikedTracks") val externalLikedTracks: ExternalLikedTracksResult?
    )

    data class ExternalLikedTracksResult(
        @SerializedName("tracks") val tracks: List<ExternalTrack>?,
        @SerializedName("pageInfo") val pageInfo: PageInfo?,
        @SerializedName("__typename") val typename: String?,
        @SerializedName("errorMessage") val errorMessage: String?,
        @SerializedName("retryAfter") val retryAfter: Long?
    )

    data class ExternalPlaylist(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("imageUrl") val imageUrl: String?,
        @SerializedName("isOwner") val isOwner: Boolean?,
        @SerializedName("totalItems") val totalItems: Int?
    )

    data class ExternalTrack(
        @SerializedName("id") val id: String,
        @SerializedName("name") val name: String,
        @SerializedName("isrc") val isrc: String?,
        @SerializedName("imageUrl") val imageUrl: String?,
        @SerializedName("artists") val artists: List<ExternalArtist>?
    )

    data class ExternalArtist(
        @SerializedName("name") val name: String
    )

    data class PageInfo(
        @SerializedName("endCursor") val endCursor: String?,
        @SerializedName("hasNextPage") val hasNextPage: Boolean?,
        @SerializedName("totalItems") val totalItems: Int?
    )

    data class LikesSyncStatusResponse(
        @SerializedName("data") val data: LikesSyncStatusData?
    )

    data class LikesSyncStatusData(
        @SerializedName("likesSyncStatus") val likesSyncStatus: LikesSyncStatuses?,
        @SerializedName("startLikesSync") val startLikesSync: LikesSyncStatuses?
    )

    data class LikesSyncStatuses(
        @SerializedName("statuses") val statuses: List<LikesSyncStatus>?
    )

    data class LikesSyncStatus(
        @SerializedName("isSyncing") val isSyncing: Boolean?,
        @SerializedName("progressPercent") val progressPercent: Int?
    )

    data class PlaylistSyncStatusResponse(
        @SerializedName("data") val data: PlaylistSyncStatusData?
    )

    data class PlaylistSyncStatusData(
        @SerializedName("playlistSyncStatus") val playlistSyncStatus: PlaylistSyncStatuses?,
        @SerializedName("startPlaylistSync") val startPlaylistSync: PlaylistSyncStatuses?
    )

    data class PlaylistSyncStatuses(
        @SerializedName("statuses") val statuses: List<PlaylistSyncStatus>?
    )

    data class PlaylistSyncStatus(
        @SerializedName("playlistId") val playlistId: String?,
        @SerializedName("isSyncing") val isSyncing: Boolean?,
        @SerializedName("progressPercent") val progressPercent: Int?
    )
}
