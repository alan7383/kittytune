package com.alananasss.kittytune.audio.providers

import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider
import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves a trusted ISRC for a track from Deezer or caller-supplied candidate.
 */
object IsrcResolver {

    private data class CacheKey(val song: String, val artist: String, val durationSeconds: Int?)

    private const val NEGATIVE_RESULT = "\u0000NEGATIVE_RESULT\u0000"
    private val cache = ConcurrentHashMap<CacheKey, String>()

    suspend fun resolveAndValidate(
        candidateIsrc: String?,
        song: String,
        artist: String,
        durationSeconds: Int?,
    ): String? = withContext(Dispatchers.IO) {
        ProviderIsrc.normalize(candidateIsrc)?.let { return@withContext it }

        if (song.isBlank() || artist.isBlank()) return@withContext null

        val key = CacheKey(song.trim().lowercase(), artist.trim().lowercase(), durationSeconds)
        cache[key]?.let { return@withContext if (it == NEGATIVE_RESULT) null else it }

        val resolved = runCatching {
            resolveViaDeezer(song, artist, durationSeconds)
        }.getOrNull()

        cache[key] = resolved ?: NEGATIVE_RESULT
        resolved
    }

    private fun resolveViaDeezer(song: String, artist: String, durationSeconds: Int?): String? =
        runCatching {
            val query = DeezerAudioProvider.Query(
                mediaId = "",
                title = song,
                artists = listOf(artist),
                album = null,
                isrc = null,
                durationMs = durationSeconds?.toLong()?.times(1000L),
                resolverUrl = DeezerAudioProvider.DEFAULT_RESOLVER_URL,
                quality = DeezerAudioQuality.MP3_128,
                fastMode = false,
                proxyUrl = DeezerAudioProvider.DEFAULT_PROXY_URL,
            )
            DeezerAudioProvider.findBestMatch(query)
                ?.isrc
                ?.let { ProviderIsrc.normalize(it) }
        }.getOrNull()

    fun clearCache() = cache.clear()
}
