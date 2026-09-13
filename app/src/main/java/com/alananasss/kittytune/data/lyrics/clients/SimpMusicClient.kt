package com.alananasss.kittytune.data.lyrics.clients

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.Json
import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.lyrics.models.LyricsData
import com.alananasss.kittytune.data.lyrics.models.SimpMusicApiResponse
import kotlin.math.abs

object SimpMusicClient {
    private const val BASE_URL = "https://api-lyrics.simpmusic.org/v1/"

    private val client by lazy {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                        explicitNulls = false
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url(BASE_URL)
                header(HttpHeaders.Accept, "application/json")
                header(HttpHeaders.UserAgent, "KittyTune/1.0")
                header(HttpHeaders.ContentType, "application/json")
            }

            expectSuccess = false
        }
    }

    suspend fun search(query: String): List<LyricsData> =
        try {
            val cleanQuery = query.trim()
            if (cleanQuery.isBlank()) return emptyList()

            val response = client.get("${BASE_URL}search") {
                parameter("q", cleanQuery)
            }

            if (response.status == HttpStatusCode.OK) {
                val apiResponse = response.body<SimpMusicApiResponse>()
                if (apiResponse.success) {
                    apiResponse.data
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    suspend fun getLyricsByVideoId(videoId: String): List<LyricsData> =
        try {
            val cleanId = videoId.trim()
            if (cleanId.isBlank()) return emptyList()

            val response = client.get(BASE_URL + cleanId)

            if (response.status == HttpStatusCode.OK) {
                val apiResponse = response.body<SimpMusicApiResponse>()
                if (apiResponse.success) {
                    apiResponse.data
                } else {
                    emptyList()
                }
            } else {
                emptyList()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }

    private fun extractLyricsText(track: LyricsData?): String? =
        track?.richSyncLyrics?.takeIf { it.isNotBlank() }
            ?: track?.syncedLyrics?.takeIf { it.isNotBlank() }
            ?: track?.plainLyrics?.takeIf { it.isNotBlank() }

    private fun selectBestTrack(tracks: List<LyricsData>, duration: Int): LyricsData? {
        if (tracks.isEmpty()) return null
        return if (duration > 0 && tracks.size > 1) {
            tracks.minByOrNull { track ->
                abs((track.duration ?: 0) - duration)
            }
        } else {
            tracks.firstOrNull()
        }
    }

    suspend fun getLyrics(
        videoId: String? = null,
        title: String? = null,
        artist: String? = null,
        duration: Int = 0,
    ): Result<String> {
        return try {
            // 1. Direct videoId fetch if it is a valid 11-char YouTube video ID
            val cleanVideoId = videoId?.trim().orEmpty()
            val isValidYoutubeId = cleanVideoId.length == 11 && cleanVideoId.matches(Regex("^[a-zA-Z0-9_-]{11}$"))
            if (isValidYoutubeId) {
                val tracks = getLyricsByVideoId(cleanVideoId)
                if (tracks.isNotEmpty()) {
                    val bestMatch = selectBestTrack(tracks, duration)
                    val text = extractLyricsText(bestMatch)
                    if (!text.isNullOrBlank()) {
                        return Result.success(text)
                    }
                }
            }

            // 2. Search using candidate title and artist
            val cleanTitle = title?.trim().orEmpty()
            val cleanArtist = artist?.trim().orEmpty()
            if (cleanTitle.isBlank() && cleanArtist.isBlank()) {
                return Result.failure(IllegalStateException("Lyrics unavailable from SimpMusic"))
            }

            val target = LyricsMatcher.Target(
                title = cleanTitle,
                artist = cleanArtist,
                durationMs = duration * 1000L,
                alternativeTitles = listOfNotNull(cleanTitle, LyricsMatcher.cleanNoiseAndBrackets(cleanTitle)).filter { it.isNotBlank() }.distinct(),
                alternativeArtists = listOfNotNull(cleanArtist, LyricsMatcher.cleanArtist(cleanArtist)).filter { it.isNotBlank() }.distinct(),
            )

            val candidatePairs = LyricsMatcher.generateCandidatePairs(cleanTitle, cleanArtist)
            val queryList = mutableListOf<String>()
            for ((cTitle, cArtist) in candidatePairs) {
                val combined = "$cTitle $cArtist".trim()
                if (combined.isNotBlank() && combined !in queryList) {
                    queryList.add(combined)
                }
                if (cTitle.isNotBlank() && cTitle !in queryList) {
                    queryList.add(cTitle)
                }
            }

            for (query in queryList.take(3)) {
                if (!currentCoroutineContext().isActive) {
                    throw CancellationException("Lyrics search cancelled")
                }

                val searchResults = search(query)
                if (searchResults.isEmpty()) continue

                val scored = searchResults.mapNotNull { item ->
                    val vId = item.videoId?.trim()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val itemTitle = item.title.orEmpty()
                    val itemArtist = item.artist.orEmpty()
                    val itemDuration = (item.duration ?: 0).toDouble()

                    val score = LyricsMatcher.score(itemTitle, itemArtist, itemDuration, target)
                    val titleSim = LyricsMatcher.titleSimilarity(itemTitle, target)
                    val durDiff = if (duration > 0 && item.duration != null && item.duration > 0) {
                        abs(item.duration - duration)
                    } else 0

                    var totalScore = score * 0.6f + titleSim * 0.4f
                    if (duration > 0 && durDiff > 20) {
                        totalScore -= 0.3f
                    } else if (duration > 0 && durDiff <= 5) {
                        totalScore += 0.1f
                    }

                    Triple(vId, totalScore, durDiff)
                }.sortedByDescending { it.second }

                for ((candidateVideoId, candScore, _) in scored.take(3)) {
                    if (candScore < 0.35f) continue
                    val tracks = getLyricsByVideoId(candidateVideoId)
                    if (tracks.isNotEmpty()) {
                        val best = selectBestTrack(tracks, duration)
                        val text = extractLyricsText(best)
                        if (!text.isNullOrBlank()) {
                            return Result.success(text)
                        }
                    }
                }
            }

            Result.failure(IllegalStateException("Lyrics unavailable from SimpMusic"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
