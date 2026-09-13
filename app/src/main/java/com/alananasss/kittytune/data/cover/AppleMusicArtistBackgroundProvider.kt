package com.alananasss.kittytune.data.cover

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Fetches Apple Music artist motion artwork (HLS / MP4 canvas) for the artist profile screen.
 *
 * 1. Searches for the artist by name on Apple Music catalog.
 * 2. Fetches the artist details with `extend=editorialVideo,editorialArtwork`.
 * 3. Extracts the motion video URL (e.g. motionArtistFullscreen16x9, motionArtistSquare1x1, motionDetailRaw).
 *
 * Results are cached in memory for 24 hours.
 */
object AppleMusicArtistBackgroundProvider {

    private const val TAG = "AppleArtistMotion"
    private const val AMP_BASE = "https://amp-api.music.apple.com"
    private const val DEFAULT_STOREFRONT = "us"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    private data class CacheEntry(
        val videoUrl: String?,
        val expiresAtMs: Long,
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val NEGATIVE_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes

    private val http = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    suspend fun getByArtistName(
        artistName: String,
        storefront: String = DEFAULT_STOREFRONT,
    ): String? = withContext(Dispatchers.IO) {
        if (artistName.isBlank()) return@withContext null

        val key = cacheKey("artist", artistName, storefront)
        cache[key]?.takeIf { it.expiresAtMs > System.currentTimeMillis() }?.let {
            return@withContext it.videoUrl
        }

        val neg = negativeCache[key]
        if (neg != null && System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) {
            return@withContext null
        }

        val result = searchAndFetchArtistMotion(artistName.trim(), storefront)
        if (result != null) {
            cache[key] = CacheEntry(result, System.currentTimeMillis() + CACHE_TTL_MS)
        } else {
            negativeCache[key] = System.currentTimeMillis()
        }
        return@withContext result
    }

    private suspend fun searchAndFetchArtistMotion(
        artistName: String,
        storefront: String,
    ): String? {
        return runCatching {
            var token = AppleMusicCanvasProvider.getToken()
            var matchedArtistId = searchArtistId(artistName, storefront, token)

            if (matchedArtistId == null) {
                // Retry once with a force refreshed token if search returned nothing
                token = AppleMusicCanvasProvider.getToken(forceRefresh = true)
                matchedArtistId = searchArtistId(artistName, storefront, token)
            }

            if (matchedArtistId == null) {
                Log.d(TAG, "No matching Apple Music artist found for: $artistName")
                return@runCatching null
            }

            fetchArtistMotionById(matchedArtistId, storefront, token)
        }.getOrElse { e ->
            Log.w(TAG, "Error fetching artist motion for '$artistName': ${e.message}")
            null
        }
    }

    private fun searchArtistId(
        artistName: String,
        storefront: String,
        token: String,
    ): String? {
        val url = "$AMP_BASE/v1/catalog/$storefront/search".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("term", artistName)
            ?.addQueryParameter("types", "artists")
            ?.addQueryParameter("limit", "3")
            ?.build() ?: return null

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", USER_AGENT)
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val root = JSONObject(bodyString)
            val artistsArray = root.optJSONObject("results")
                ?.optJSONObject("artists")
                ?.optJSONArray("data") ?: return null

            var bestId: String? = null
            var bestScore = -1

            for (i in 0 until artistsArray.length()) {
                val item = artistsArray.optJSONObject(i) ?: continue
                val attributes = item.optJSONObject("attributes") ?: continue
                val name = attributes.optString("name", "")
                if (name.isBlank()) continue

                val id = item.optString("id")
                if (id.isBlank()) continue

                var score = 0
                if (name.equals(artistName, ignoreCase = true)) {
                    score = 10
                } else if (name.contains(artistName, ignoreCase = true) || artistName.contains(name, ignoreCase = true)) {
                    score = 5
                }

                if (score > bestScore) {
                    bestScore = score
                    bestId = id
                }
            }

            return if (bestScore >= 4) bestId else null
        }
    }

    private fun fetchArtistMotionById(
        artistId: String,
        storefront: String,
        token: String,
    ): String? {
        val url = "$AMP_BASE/v1/catalog/$storefront/artists/$artistId".toHttpUrlOrNull()?.newBuilder()
            ?.addQueryParameter("extend", "editorialVideo,editorialArtwork")
            ?.build() ?: return null

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", USER_AGENT)
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val bodyString = response.body?.string() ?: return null
            val root = JSONObject(bodyString)
            val dataArray = root.optJSONArray("data") ?: return null
            if (dataArray.length() == 0) return null

            val artistObj = dataArray.optJSONObject(0) ?: return null
            val attributes = artistObj.optJSONObject("attributes") ?: return null

            // 1. editorialVideo
            val editorialVideo = attributes.optJSONObject("editorialVideo")
            if (editorialVideo != null) {
                val videoUrl = extractEditorialVideoUrl(editorialVideo)
                if (!videoUrl.isNullOrBlank()) return videoUrl
            }

            // 2. editorialArtwork
            val editorialArtwork = attributes.optJSONObject("editorialArtwork")
            if (editorialArtwork != null) {
                val videoUrl = extractEditorialVideoUrl(editorialArtwork)
                if (!videoUrl.isNullOrBlank()) return videoUrl
            }

            return null
        }
    }

    private fun extractEditorialVideoUrl(editorialObj: JSONObject): String? {
        val preferredKeys = listOf(
            "motionArtistFullscreen16x9",
            "motionArtistSquare1x1",
            "motionArtistWide16x9",
            "motionDetailRaw",
            "motionDetailTall",
            "motionDetailSquare",
            "motionSquareVideo1x1",
            "motionTallVideo3x4"
        )

        for (key in preferredKeys) {
            val child = editorialObj.optJSONObject(key) ?: continue
            val video = child.optString("video")
            if (!video.isNullOrBlank()) return video
        }

        val keys = editorialObj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val child = editorialObj.optJSONObject(key) ?: continue
            val video = child.optString("video")
            if (!video.isNullOrBlank()) return video
        }

        return null
    }

    private fun cacheKey(prefix: String, vararg parts: String): String {
        return "$prefix|" + parts.joinToString("|") { it.trim().lowercase(Locale.ROOT) }
    }
}
