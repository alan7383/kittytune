package com.alananasss.kittytune.data.vk

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.domain.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

class VkRepository(private val context: Context) {

    private val api = VkApi(context)
    val tokenManager = VkTokenManager(context)

    suspend fun getUserAudios(offset: Int = 0, count: Int = VkApi.DEFAULT_PAGE): VkAudioSectionResult {
        return api.getUserAudios(tokenManager.userId, offset, count)
    }

    suspend fun getPlaylists(): List<VkPlaylist> = api.getPlaylists(tokenManager.userId)

    suspend fun getPlaylistAudios(
        ownerId: Long,
        playlistId: Long,
        accessHash: String = ""
    ): List<Track> = api.getPlaylistAudios(ownerId, playlistId, accessHash).map { it.toTrack() }

    suspend fun search(query: String, offset: Int = 0): VkSearchResult = api.searchAudios(query, offset)

    suspend fun getArtist(name: String): VkArtistPage? = api.getArtist(name)

    suspend fun getRecommendations(): List<Track> =
        api.getRecommendations(tokenManager.userId).map { it.toTrack() }

    suspend fun getExplore(): List<Track> = api.getExplore(tokenManager.userId).map { it.toTrack() }

    suspend fun likeTrack(track: Track): Boolean {
        val hashes = VkHashes.parse(track.secretToken)
        return api.addAudio(
            track.id,
            ownerIdOf(track),
            hashes.add,
            VkHashes.trackCodeOf(track.secretToken)
        )
    }

    suspend fun unlikeTrack(track: Track): Boolean {
        val hashes = VkHashes.parse(track.secretToken)
        return api.deleteAudio(
            track.id,
            ownerIdOf(track),
            hashes.delete,
            VkHashes.trackCodeOf(track.secretToken)
        )
    }

    suspend fun getLyrics(track: Track): String? {
        val ownerId = ownerIdOf(track)
        if (ownerId == 0L || track.id == 0L) return null
        return api.getLyrics(ownerId, track.id)
    }

    suspend fun refreshUserProfile(): VkUser? {
        if (!tokenManager.isLoggedIn()) return null
        val user = api.fetchUserProfile(tokenManager.userId)
        if (user != null) {
            if (user.firstName.isNotBlank()) tokenManager.userFirstName = user.firstName
            if (user.lastName.isNotBlank()) tokenManager.userLastName = user.lastName
            user.photoMax?.let { tokenManager.userPhoto = it }
            user.screenName?.let { tokenManager.userScreenName = it }
        }
        return user
    }

    private fun ownerIdOf(track: Track): Long =
        track.user?.id?.takeIf { it != 0L } ?: ownerFromPermalink(track) ?: tokenManager.userId

    companion object {
        private const val TAG = "VkRepository"
        private val streamCache = ConcurrentHashMap<String, Pair<String, Long>>()

        /** VK stream URLs are signed and short-lived, so they are cached only briefly. */
        private const val CACHE_EXPIRY_MS = 20 * 60 * 1000L

        private val AUDIO_ID_REGEX = Regex("""audio(-?\d+)_(\d+)""")

        @Volatile
        private var INSTANCE: VkRepository? = null

        fun getInstance(context: Context): VkRepository =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: VkRepository(context.applicationContext).also { INSTANCE = it }
            }

        private fun ownerFromPermalink(track: Track): Long? {
            val source = track.permalink ?: track.permalinkUrl ?: return null
            return AUDIO_ID_REGEX.find(source)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }

        /**
         * Resolves a playable URL for a VK track.
         *
         * The refresh goes through `reload_audios` with the full composite id
         * (`owner_id`, `audio_id`, `action_hash`, `url_hash`) — VK answers `no_audios` without the
         * two hashes, which is why VK-only tracks used to fail with a bogus connection error.
         */
        suspend fun resolveStream(context: Context, track: Track): String? = withContext(Dispatchers.IO) {
            val tokenManager = VkTokenManager(context)
            val ownerId = track.user?.id?.takeIf { it != 0L }
                ?: ownerFromPermalink(track)
                ?: 0L

            if (track.id == 0L) {
                Log.w(TAG, "Cannot resolve a VK stream without a track id")
                return@withContext null
            }

            val cacheKey = "${ownerId}_${track.id}"
            streamCache[cacheKey]?.let { (url, storedAt) ->
                if (System.currentTimeMillis() - storedAt < CACHE_EXPIRY_MS) return@withContext url
            }

            val existing = track.media?.transcodings?.firstOrNull()?.url
            if (!existing.isNullOrBlank() && existing.startsWith("http") &&
                !VkAudioDecoder.isMaskedUrl(existing)
            ) {
                streamCache[cacheKey] = existing to System.currentTimeMillis()
                return@withContext existing
            }

            if (ownerId == 0L) {
                Log.w(TAG, "Cannot resolve a VK stream without an owner id (track ${track.id})")
                return@withContext null
            }

            val hashes = VkHashes.parse(track.secretToken)
            try {
                val api = VkApi(context)
                // Tracks saved before the hash bundle was persisted have no action/url hash, so the
                // web endpoint cannot refresh them; the official API only needs the id pair.
                val reloaded = api.reloadAudio(ownerId, track.id, hashes.action, hashes.url)
                    ?: api.getAudioById(ownerId, track.id)

                val url = reloaded?.url?.takeIf { it.isNotBlank() && it.startsWith("http") }
                    ?: VkAudioDecoder
                        .exposeSource(reloaded?.url, tokenManager.userId)
                        .takeIf { it.startsWith("http") }

                if (url != null) {
                    streamCache[cacheKey] = url to System.currentTimeMillis()
                    return@withContext url
                }
                Log.w(TAG, "VK returned no stream for $cacheKey")
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving VK stream ($cacheKey): ${e.message}", e)
            }
            null
        }
    }
}
