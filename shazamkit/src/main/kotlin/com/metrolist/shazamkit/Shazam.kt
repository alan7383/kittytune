package com.metrolist.shazamkit

import android.util.Log
import com.metrolist.shazamkit.models.RecognitionResult
import com.metrolist.shazamkit.models.ShazamRequestJson
import com.metrolist.shazamkit.models.ShazamResponseJson
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Shazam music recognition with built-in rate limiting, concurrency management and caching.
 */
object Shazam {
    private const val TAG = "ShazamApi"

    // Configuration
    private const val MAX_CONCURRENT_REQUESTS = 2
    private const val MIN_REQUEST_INTERVAL_MS = 1000L
    private const val MAX_RETRIES = 2
    private const val RETRY_DELAY_MS = 1500L
    private const val CACHE_DURATION_MS = 300000L

    // Concurrency & Rate Limiting
    private val activeRequests = AtomicInteger(0)
    private var lastRequestTime = 0L
    private val rateLimitMutex = Mutex()
    private val concurrencySemaphore = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val resultCache = ConcurrentHashMap<String, CachedResult>()

    // HTTP Client Configuration
    private val client by lazy {
        HttpClient(OkHttp) {
            engine {
                config {
                    connectTimeout(8, TimeUnit.SECONDS)
                    readTimeout(8, TimeUnit.SECONDS)
                    writeTimeout(8, TimeUnit.SECONDS)
                }
            }
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    },
                )
            }
            expectSuccess = false
        }
    }

    private val userAgents = listOf(
        "Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)",
        "Dalvik/1.6.0 (Linux; U; Android 4.4.2; SM-T210 Build/KOT49H)",
        "Dalvik/2.1.0 (Linux; U; Android 5.1.1; SM-P905V Build/LMY47X)",
        "Dalvik/2.1.0 (Linux; U; Android 6.0.1; SM-G920F Build/MMB29K)",
        "Dalvik/2.1.0 (Linux; U; Android 5.0; SM-G900F Build/LRX21T)"
    )

    private val timezones = listOf(
        "Europe/Paris", "Europe/London", "America/New_York",
        "America/Los_Angeles", "Asia/Tokyo", "Asia/Dubai"
    )

    /**
     * Recognize music from audio signature
     *
     * @param signature Audio signature in Shazam DejaVu format
     * @param sampleDurationMs Sample duration in milliseconds
     * @return Result containing recognition result or error
     */
    suspend fun recognize(signature: String, sampleDurationMs: Long): Result<RecognitionResult> {
        val cacheKey = generateCacheKey(signature)
        getCachedResult(cacheKey)?.let {
            logDebug("Cache hit for key=$cacheKey")
            return Result.success(it)
        }

        return concurrencySemaphore.withPermit {
            activeRequests.incrementAndGet()
            try {
                withTimeoutOrNull(9000L) {
                    executeRequest(signature, sampleDurationMs)
                } ?: run {
                    logWarn("Recognition timed out after 9s")
                    Result.failure(Exception("Recognition timed out"))
                }
            } finally {
                activeRequests.decrementAndGet()
            }
        }
    }

    fun getPendingRequestsCount(): Int = 0

    fun getActiveRequestsCount(): Int = activeRequests.get()

    fun clearCache() {
        resultCache.clear()
    }

    fun cancelPendingRequests() {
        // Coroutines are automatically canceled by their parent scope
    }

    fun cleanup() {
        clearCache()
        client.close()
    }

    private suspend fun executeRequest(
        signature: String,
        sampleDurationMs: Long
    ): Result<RecognitionResult> {
        var lastException: Exception? = null

        for (attempt in 0 until MAX_RETRIES) {
            try {
                enforceRateLimit()

                val result = performRecognition(signature, sampleDurationMs)
                val cacheKey = generateCacheKey(signature)
                cacheResult(cacheKey, result)
                logDebug("Request succeeded on attempt ${attempt + 1}")
                return Result.success(result)
            } catch (e: Exception) {
                lastException = e
                logWarn("Request attempt ${attempt + 1}/$MAX_RETRIES failed: ${e.message}")

                if (e.message?.contains("429") == true ||
                    e.message?.contains("Too many requests", ignoreCase = true) == true
                ) {
                    if (attempt < MAX_RETRIES - 1) {
                        logDebug("Rate limited (429), retrying in ${RETRY_DELAY_MS}ms")
                        delay(RETRY_DELAY_MS)
                        continue
                    }
                } else {
                    // Non-retryable error (e.g. 404 No match found) fails immediately
                    break
                }
            }
        }

        return Result.failure(lastException ?: Exception("Recognition failed after $MAX_RETRIES attempts"))
    }

    private suspend fun performRecognition(
        signature: String,
        sampleDurationMs: Long
    ): RecognitionResult {
        val timestamp = System.currentTimeMillis() / 1000
        val uuid1 = UUID.randomUUID().toString().uppercase()
        val uuid2 = UUID.randomUUID().toString()

        val request = ShazamRequestJson(
            geolocation = ShazamRequestJson.Geolocation(
                altitude = Random.nextDouble() * 400 + 100,
                latitude = Random.nextDouble() * 180 - 90,
                longitude = Random.nextDouble() * 360 - 180
            ),
            signature = ShazamRequestJson.Signature(
                samplems = sampleDurationMs,
                timestamp = timestamp,
                uri = signature
            ),
            timestamp = timestamp,
            timezone = timezones.random()
        )

        logDebug("Sending recognition request to Shazam API")
        val response = client.post("https://amp.shazam.com/discovery/v5/en/US/android/-/tag/$uuid1/$uuid2") {
            parameter("sync", "true")
            parameter("webv3", "true")
            parameter("sampling", "true")
            parameter("connected", "")
            parameter("shazamapiversion", "v3")
            parameter("sharehub", "true")
            parameter("video", "v3")
            header("User-Agent", userAgents.random())
            header("Content-Language", "en_US")
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            val statusCode = response.status.value
            logWarn("Shazam API returned HTTP $statusCode")
            when (statusCode) {
                429 -> throw Exception("Too many requests")
                404 -> throw Exception("No match found")
                in 500..599 -> throw Exception("Shazam service temporarily unavailable")
                else -> throw Exception("Recognition failed (error $statusCode)")
            }
        }

        val shazamResponse = response.body<ShazamResponseJson>()
        logDebug("Shazam API response received, hasTrack=${shazamResponse.track != null}")
        return shazamResponse.toRecognitionResult()
            ?: throw Exception("No match found")
    }

    private suspend fun enforceRateLimit() {
        rateLimitMutex.withLock {
            val currentTime = System.currentTimeMillis()
            val timeSinceLastRequest = currentTime - lastRequestTime

            if (timeSinceLastRequest < MIN_REQUEST_INTERVAL_MS) {
                val delayTime = MIN_REQUEST_INTERVAL_MS - timeSinceLastRequest
                delay(delayTime)
            }

            lastRequestTime = System.currentTimeMillis()
        }
    }

    private fun generateCacheKey(signature: String): String {
        return signature.hashCode().toString()
    }

    private fun getCachedResult(key: String): RecognitionResult? {
        val cached = resultCache[key] ?: return null
        val currentTime = System.currentTimeMillis()

        if (currentTime - cached.timestamp > CACHE_DURATION_MS) {
            resultCache.remove(key)
            return null
        }

        return cached.result
    }

    private fun cacheResult(key: String, result: RecognitionResult) {
        resultCache[key] = CachedResult(
            timestamp = System.currentTimeMillis(),
            result = result
        )
        cleanupCache()
    }

    private fun cleanupCache() {
        if (resultCache.size < 100) return
        val currentTime = System.currentTimeMillis()
        val iterator = resultCache.entries.iterator()

        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (currentTime - entry.value.timestamp > CACHE_DURATION_MS) {
                iterator.remove()
            }
        }
    }

    private fun logDebug(message: String) {
        Log.d(TAG, message)
        Timber.tag(TAG).d(message)
    }

    private fun logWarn(message: String) {
        Log.w(TAG, message)
        Timber.tag(TAG).w(message)
    }

    private fun ShazamResponseJson.toRecognitionResult(): RecognitionResult? {
        val track = this.track ?: return null

        val songSection = track.sections?.find { it?.type == "SONG" }
        val metadata = songSection?.metadata
        val album = metadata?.find { it?.title == "Album" }?.text
        val label = metadata?.find { it?.title == "Label" }?.text
        val releaseDate = metadata?.find { it?.title == "Released" }?.text

        val lyricsSection = track.sections?.find { it?.type == "LYRICS" }
        val lyrics = lyricsSection?.text

        val appleAction = track.hub?.options?.firstOrNull {
            it?.providername?.contains("apple", ignoreCase = true) == true
        }?.actions?.firstOrNull()

        val spotifyProvider = track.hub?.providers?.find {
            it?.caption?.contains("spotify", ignoreCase = true) == true
        }

        val youtubeAction = track.hub?.options?.find {
            it?.type?.contains("video", ignoreCase = true) == true
        }?.actions?.firstOrNull()

        val youtubeVideoId = youtubeAction?.uri?.let { uri ->
            uri.substringAfterLast("v=", "").takeIf { it.isNotEmpty() }
                ?: uri.substringAfterLast("/", "").takeIf { it.isNotEmpty() && it.length == 11 }
        }

        return RecognitionResult(
            trackId = track.key ?: tagid ?: "",
            title = track.title ?: "",
            artist = track.subtitle ?: "",
            album = album,
            coverArtUrl = track.images?.coverart,
            coverArtHqUrl = track.images?.coverarthq,
            genre = track.genres?.primary,
            releaseDate = releaseDate,
            label = label,
            lyrics = lyrics,
            shazamUrl = track.url,
            appleMusicUrl = appleAction?.uri,
            spotifyUrl = spotifyProvider?.actions?.firstOrNull()?.uri,
            isrc = track.isrc,
            youtubeVideoId = youtubeVideoId
        )
    }

    private data class CachedResult(
        val timestamp: Long,
        val result: RecognitionResult
    )
}
