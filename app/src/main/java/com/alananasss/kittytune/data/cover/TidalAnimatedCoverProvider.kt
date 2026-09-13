package com.alananasss.kittytune.data.cover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves TIDAL animated album covers (MP4 video covers) via TIDAL catalog API.
 */
object TidalAnimatedCoverProvider {

    private const val TAG = "TidalAnimatedCoverProvider"
    private const val API_BASE_URL = "https://tidal.com/v1"
    private const val PUBLIC_TOKEN = "49YxDN9a2aFV6RTG"
    private const val COUNTRY_CODE = "US"
    private const val LOCALE = "en_US"
    private const val DEVICE_TYPE = "BROWSER"
    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

    private val cache = ConcurrentHashMap<String, String>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private const val NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun resolveAnimatedArtwork(
        title: String,
        artist: String?,
        album: String? = null,
        durationSeconds: Int? = null,
    ): String? = withContext(Dispatchers.IO) {
        val key = cacheKey(title, artist)
        cache[key]?.let { return@withContext it }
        val neg = negativeCache[key]
        if (neg != null && System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) {
            return@withContext null
        }

        runCatching {
            val query = listOfNotNull(title.takeIf { it.isNotBlank() }, artist?.takeIf { it.isNotBlank() }).joinToString(" ")
            if (query.isBlank()) return@withContext null

            val tracks = searchTracks(query)
            val candidate = pickBestTidalCandidate(tracks, title, artist, album, durationSeconds)

            if (candidate != null) {
                cache[key] = candidate
                negativeCache.remove(key)
                candidate
            } else {
                negativeCache[key] = System.currentTimeMillis()
                null
            }
        }.onFailure {
            android.util.Log.e(TAG, "Tidal animated cover fetch failed for \"$title\": ${it.message}")
        }.getOrNull()
    }

    private fun cacheKey(title: String, artist: String?): String =
        (title.trim().lowercase() + "\u001F" + (artist?.trim()?.lowercase().orEmpty()))

    private fun searchTracks(term: String): JSONArray? {
        val url = "$API_BASE_URL/search/tracks"
            .toHttpUrl()
            .newBuilder()
            .addQueryParameter("countryCode", COUNTRY_CODE)
            .addQueryParameter("locale", LOCALE)
            .addQueryParameter("deviceType", DEVICE_TYPE)
            .addQueryParameter("query", term)
            .addQueryParameter("limit", "8")
            .addQueryParameter("offset", "0")
            .build()

        val request = Request.Builder()
            .url(url)
            .get()
            .header("Accept", "application/json")
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("x-tidal-token", PUBLIC_TOKEN)
            .build()

        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body?.string().takeIf { !it.isNullOrBlank() } ?: return@use null
                JSONObject(body).optJSONArray("items")
            }
        }.getOrNull()
    }

    private fun pickBestTidalCandidate(
        items: JSONArray?,
        queryTitle: String,
        queryArtist: String?,
        queryAlbum: String?,
        queryDurationSeconds: Int?,
    ): String? {
        if (items == null || items.length() == 0) return null

        val normTitle = normalize(queryTitle)
        val normArtist = queryArtist?.let { normalize(it) }.orEmpty()

        data class ScoredCandidate(val url: String, val score: Int)
        var best: ScoredCandidate? = null

        fun getValidHash(obj: JSONObject?, key: String): String? {
            if (obj == null || obj.isNull(key)) return null
            val str = obj.optString(key, "").trim()
            return str.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
        }

        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val videoCoverHash = getValidHash(item, "videoCover")
                ?: getValidHash(item.optJSONObject("album"), "videoCover")
                ?: getValidHash(item, "animatedCover")
                ?: getValidHash(item.optJSONObject("album"), "animatedCover")
                ?: continue

            val trackTitle = normalize(item.optString("title"))
            val artistName = normalize(item.optJSONObject("artist")?.optString("name").orEmpty())

            var score = 0
            if (trackTitle == normTitle) {
                score += 100
            } else if (trackTitle.contains(normTitle) || normTitle.contains(trackTitle)) {
                score += 70
            } else {
                continue
            }

            if (normArtist.isNotBlank()) {
                if (artistName == normArtist) {
                    score += 60
                } else if (artistName.contains(normArtist) || normArtist.contains(artistName)) {
                    score += 35
                }
            }

            if (queryDurationSeconds != null && queryDurationSeconds > 0) {
                val durSec = item.optInt("duration")
                if (durSec > 0) {
                    val diff = kotlin.math.abs(queryDurationSeconds - durSec)
                    if (diff <= 3) score += 40
                    else if (diff <= 10) score += 20
                }
            }

            val videoUrl = formatTidalVideoUrl(videoCoverHash)
            if (videoUrl.isBlank()) continue
            if (best == null || score > best.score) {
                best = ScoredCandidate(videoUrl, score)
            }
        }

        return best?.url
    }

    internal fun formatTidalVideoUrl(hashOrUrl: String, size: String = "1280x1280"): String {
        val trimmed = hashOrUrl.trim()
        if (trimmed.isBlank() || trimmed.equals("null", ignoreCase = true)) return ""
        return if (trimmed.startsWith("http", ignoreCase = true)) {
            trimmed
        } else {
            "https://resources.tidal.com/videos/${trimmed.replace("-", "/")}/$size.mp4"
        }
    }

    private val STRIP_REGEX = Regex(
        """\s*(\(|\[)[^)\]]*(remaster|edition|version|feat\.?|ft\.?|with|prod\.?|explicit|clean|live|expanded|single|ep)[^)\]]*(\)|\]).*""",
        RegexOption.IGNORE_CASE
    )

    private fun normalize(s: String): String {
        var result = s.lowercase()
        result = result.replace(STRIP_REGEX, "")
        return result.replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
