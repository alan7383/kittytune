package com.alananasss.kittytune.data.musicimport

import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.ExternalLikedTracksInput
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.ExternalLikedTracksResponse
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.ExternalPlaylistsInput
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.ExternalPlaylistsResponse
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.LikesSyncStatusInput
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.LikesSyncStatusResponse
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.PlaylistSyncStatusResponse
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.PlaylistsSyncStatusInput
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.StartLikesSyncInput
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.StartPlaylistSyncInput
import com.alananasss.kittytune.data.network.SoundCloudApi
import com.alananasss.kittytune.domain.GraphQlRequest
import com.alananasss.kittytune.domain.GraphQlVariablesMusicImport
import com.google.gson.JsonObject

sealed interface MusicImportResult<out T> {
    data class Success<T>(val data: T) : MusicImportResult<T>
    data class Error(val message: String?, val retryAfterSeconds: Long?) : MusicImportResult<Nothing>
    object AuthenticationRequired : MusicImportResult<Nothing>
}

/**
 * Fetches playlists / liked tracks from a connected music platform and drives
 * the transfer (start + status polling) through SoundCloud's own GraphQL
 * backend (api-v2.soundcloud.com/graphql), which proxies to musicapi.
 *
 * Mirrors com.soundcloud.android.playlistimport.migrator.data.PlaylistsMigratorRepository.
 */
class SoundCloudMusicImportRepository(
    private val api: SoundCloudApi
) {

    suspend fun externalPlaylists(
        userPlatformUuid: String,
        limit: Int = DEFAULT_PAGE_LIMIT,
        next: String? = null
    ): MusicImportResult<MusicImportGraphQL.ExternalPlaylistsResult> {
        val request = GraphQlRequest(
            operationName = "ExternalPlaylists",
            query = MusicImportGraphQL.QUERY_EXTERNAL_PLAYLISTS,
            variables = GraphQlVariablesMusicImport(ExternalPlaylistsInput(userPlatformUuid, limit, next))
        )
        return runGraphQl(request) { body ->
            gson.fromJson(body, ExternalPlaylistsResponse::class.java)?.data?.externalPlaylists
        }
    }

    suspend fun externalLikedTracks(
        userPlatformUuid: String,
        limit: Int = DEFAULT_PAGE_LIMIT,
        next: String? = null
    ): MusicImportResult<MusicImportGraphQL.ExternalLikedTracksResult> {
        val request = GraphQlRequest(
            operationName = "ExternalLikedTracks",
            query = MusicImportGraphQL.QUERY_EXTERNAL_LIKED_TRACKS,
            variables = GraphQlVariablesMusicImport(ExternalLikedTracksInput(userPlatformUuid, limit, next))
        )
        return runGraphQl(request) { body ->
            gson.fromJson(body, ExternalLikedTracksResponse::class.java)?.data?.externalLikedTracks
        }
    }

    suspend fun startPlaylistSync(
        userPlatformUuid: String,
        playlistIds: List<String>
    ): MusicImportResult<List<MusicImportGraphQL.PlaylistSyncStatus>> {
        val request = GraphQlRequest(
            operationName = "StartPlaylistSync",
            query = MusicImportGraphQL.MUTATION_START_PLAYLIST_SYNC,
            variables = GraphQlVariablesMusicImport(StartPlaylistSyncInput(userPlatformUuid, playlistIds))
        )
        return runGraphQl(request) { body ->
            gson.fromJson(body, PlaylistSyncStatusResponse::class.java)
                ?.data?.startPlaylistSync?.statuses
                ?: emptyList()
        }
    }

    suspend fun startLikesSync(
        userPlatformUuid: String,
        importAsPlaylists: Boolean
    ): MusicImportResult<List<MusicImportGraphQL.LikesSyncStatus>> {
        val request = GraphQlRequest(
            operationName = "StartLikesSync",
            query = MusicImportGraphQL.MUTATION_START_LIKES_SYNC,
            variables = GraphQlVariablesMusicImport(StartLikesSyncInput(userPlatformUuid, importAsPlaylists))
        )
        return runGraphQl(request) { body ->
            gson.fromJson(body, LikesSyncStatusResponse::class.java)
                ?.data?.startLikesSync?.statuses
                ?: emptyList()
        }
    }

    suspend fun playlistSyncStatus(
        userPlatformUuid: String,
        playlistIds: List<String>
    ): MusicImportResult<List<MusicImportGraphQL.PlaylistSyncStatus>> {
        val request = GraphQlRequest(
            operationName = "PlaylistSyncStatus",
            query = MusicImportGraphQL.QUERY_PLAYLIST_SYNC_STATUS,
            variables = GraphQlVariablesMusicImport(PlaylistsSyncStatusInput(userPlatformUuid, playlistIds))
        )
        return runGraphQl(request) { body ->
            gson.fromJson(body, PlaylistSyncStatusResponse::class.java)
                ?.data?.playlistSyncStatus?.statuses
                ?: emptyList()
        }
    }

    suspend fun likesSyncStatus(
        userPlatformUuid: String
    ): MusicImportResult<List<MusicImportGraphQL.LikesSyncStatus>> {
        val request = GraphQlRequest(
            operationName = "LikesSyncStatus",
            query = MusicImportGraphQL.QUERY_LIKES_SYNC_STATUS,
            variables = GraphQlVariablesMusicImport(LikesSyncStatusInput(userPlatformUuid))
        )
        return runGraphQl(request) { body ->
            gson.fromJson(body, LikesSyncStatusResponse::class.java)
                ?.data?.likesSyncStatus?.statuses
                ?: emptyList()
        }
    }

    private suspend fun <T> runGraphQl(
        request: GraphQlRequest,
        mapBody: (JsonObject) -> T?
    ): MusicImportResult<T> {
        return try {
            val body = api.postMusicImportGraphQl(request)
            
            if (body.has("errors")) {
                val errors = body.getAsJsonArray("errors")
                if (errors.size() > 0) {
                    val message = errors.get(0).asJsonObject.get("message")?.asString
                    return MusicImportResult.Error(message, extractRetryAfter(body))
                }
            }

            val mapped = mapBody(body)

            when {
                isAuthenticationError(body) -> MusicImportResult.AuthenticationRequired
                isOtherUnionError(body) -> MusicImportResult.Error(extractUnionErrorMessage(body), extractUnionRetryAfter(body))
                mapped != null -> MusicImportResult.Success(mapped)
                else -> MusicImportResult.Error(extractErrorMessage(body), extractRetryAfter(body))
            }
        } catch (e: Exception) {
            MusicImportResult.Error(e.message, null)
        }
    }

    private fun isAuthenticationError(body: JsonObject): Boolean =
        body.deepAsString("data.externalPlaylists.__typename") == "AuthenticationError" ||
            body.deepAsString("data.externalLikedTracks.__typename") == "AuthenticationError" ||
            body.deepAsString("data.likesSyncStatus.__typename") == "AuthenticationError" ||
            body.deepAsString("data.playlistSyncStatus.__typename") == "AuthenticationError"

    private fun isOtherUnionError(body: JsonObject): Boolean {
        val typenames = listOf(
            body.deepAsString("data.externalPlaylists.__typename"),
            body.deepAsString("data.externalLikedTracks.__typename"),
            body.deepAsString("data.likesSyncStatus.__typename"),
            body.deepAsString("data.playlistSyncStatus.__typename")
        )
        return typenames.any { it == "ClientError" || it == "TooManyRequestsError" }
    }

    private fun extractUnionErrorMessage(body: JsonObject): String? =
        body.deepAsString("data.externalPlaylists.errorMessage") ?:
        body.deepAsString("data.externalLikedTracks.errorMessage") ?:
        body.deepAsString("data.likesSyncStatus.errorMessage") ?:
        body.deepAsString("data.playlistSyncStatus.errorMessage")

    private fun extractUnionRetryAfter(body: JsonObject): Long? =
        body.deepAsLong("data.externalPlaylists.retryAfter") ?:
        body.deepAsLong("data.externalLikedTracks.retryAfter") ?:
        body.deepAsLong("data.likesSyncStatus.retryAfter") ?:
        body.deepAsLong("data.playlistSyncStatus.retryAfter")

    private fun extractErrorMessage(body: JsonObject): String? =
        body.deepString("errorMessage")

    private fun extractRetryAfter(body: JsonObject): Long? =
        body.deepAsLong("retryAfter")

    private fun JsonObject.deepString(path: String): String? {
        var node: com.google.gson.JsonElement = this
        path.split(".").forEach { part ->
            node = (node as? JsonObject)?.get(part) ?: return null
        }
        return node.takeIf { !it.isJsonNull }?.asString
    }

    private fun JsonObject.deepAsString(path: String): String? = deepString(path)

    private fun JsonObject.deepAsLong(path: String): Long? {
        var node: com.google.gson.JsonElement = this
        path.split(".").forEach { part ->
            node = (node as? JsonObject)?.get(part) ?: return null
        }
        return node.takeIf { it.isJsonPrimitive }?.asLong
    }

    private val gson = com.google.gson.Gson()

    companion object {
        const val DEFAULT_PAGE_LIMIT = 50
    }
}
