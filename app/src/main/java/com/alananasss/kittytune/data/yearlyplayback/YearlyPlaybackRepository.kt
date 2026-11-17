package com.alananasss.kittytune.data.yearlyplayback

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.GraphQlRequest
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository for official SoundCloud Wrapped / Yearly Playback data.
 *
 * Implements parity with SoundCloud's decompiled repository:
 * - Queries SoundCloud GraphQL (`yearlyFanPlayback2025`, `yearlyCreatorPlayback2025`)
 * - Converts raw Timeline and BackgroundTracks into typed [Block] representations
 */
class YearlyPlaybackRepository(private val context: Context) {

    private val api = RetrofitClient.create(context)
    private val gson = Gson()

    companion object {
        private const val TAG = "YearlyPlaybackRepo"

        val TIMELINE_SCHEMA = """
            __typename
            ... on TimelineItemBase {
                backgroundTrackId
                durationMs
            }
            ... on SavePlaylistItem {
                artworkUrl
                backgroundImageUrl
                text
                activeTitle
                inactiveTitle
                urn
            }
            ... on ArtboardItem {
                artboardId
                textRuns {
                    key
                    value
                }
            }
            ... on OpenInsightsItem {
                backgroundImageUrl
                text
                title
            }
        """.trimIndent()

        val BACKGROUND_TRACKS_SCHEMA = """
            backgroundTrackId
            track {
                __typename
                urn
                title
                permalinkUrl
                fullDuration
                snipDuration
                waveformUrl
                artworkUrlTemplate
                user {
                    urn
                    name
                    username
                    permalinkUrl
                    avatarUrl
                }
            }
        """.trimIndent()

        val FAN_QUERY_2025 = """
            query YearlyPlayback {
                yearlyFanPlayback2025 {
                    assets {
                        assetId
                        url
                    }
                    timeline {
                        $TIMELINE_SCHEMA
                    }
                    backgroundTracks {
                        $BACKGROUND_TRACKS_SCHEMA
                    }
                }
            }
        """.trimIndent()

        val CREATOR_QUERY_2025 = """
            query YearlyPlayback {
                yearlyCreatorPlayback2025 {
                    assets {
                        assetId
                        url
                    }
                    timeline {
                        $TIMELINE_SCHEMA
                    }
                    backgroundTracks {
                        $BACKGROUND_TRACKS_SCHEMA
                    }
                }
            }
        """.trimIndent()

        val YOUR_2024_QUERY = """
            query YearlyPlayback {
                yourSc2024 {
                    assets {
                        assetId
                        url
                    }
                    timeline {
                        $TIMELINE_SCHEMA
                    }
                    backgroundTracks {
                        $BACKGROUND_TRACKS_SCHEMA
                    }
                }
            }
        """.trimIndent()

        fun parseTimeline(
            timelineArray: JsonArray,
            bgTracksArray: JsonArray,
            gson: Gson = Gson()
        ): List<Block> {
            val bgTracksMap = mutableMapOf<String, ApiYearlyTrackStub>()
            bgTracksArray.forEach { elem ->
                if (elem.isJsonObject) {
                    val bgTrackObj = elem.asJsonObject
                    val id = bgTrackObj.get("backgroundTrackId")?.asString
                    val trackObj = if (bgTrackObj.has("track") && !bgTrackObj.get("track").isJsonNull) {
                        bgTrackObj.getAsJsonObject("track")
                    } else null
                    if (id != null && trackObj != null) {
                        runCatching {
                            val stub = gson.fromJson(trackObj, ApiYearlyTrackStub::class.java)
                            bgTracksMap[id] = stub
                        }
                    }
                }
            }

            val blocks = mutableListOf<Block>()
            timelineArray.forEachIndexed { index, elem ->
                if (!elem.isJsonObject) return@forEachIndexed
                val item = elem.asJsonObject
                val typeName = item.get("__typename")?.asString.orEmpty()
                val bgId = item.get("backgroundTrackId")?.asString
                val duration = item.get("durationMs")?.asInt ?: 5000
                val bgTrack = bgId?.let { bgTracksMap[it] }

                when (typeName) {
                    "ArtboardItem" -> {
                        val artboardId = item.get("artboardId")?.asString ?: "Artboard_$index"
                        val textRuns = if (item.has("textRuns") && !item.get("textRuns").isJsonNull && item.get("textRuns").isJsonArray) {
                            item.getAsJsonArray("textRuns").mapNotNull { runElem ->
                                if (runElem.isJsonObject) {
                                    val r = runElem.asJsonObject
                                    val k = r.get("key")?.asString
                                    val v = r.get("value")?.asString
                                    if (k != null && v != null) ArtBoardTextRun(k, v) else null
                                } else null
                            }
                        } else {
                            emptyList()
                        }
                        blocks.add(
                            Block.ArtBoard(
                                artboardId = artboardId,
                                durationMs = duration,
                                index = index,
                                backgroundTrack = bgTrack,
                                textRuns = textRuns
                            )
                        )
                    }
                    "SavePlaylistItem" -> {
                        val rawArtwork = item.get("artworkUrl")?.asString.orEmpty()
                        val artworkUrl = if (rawArtwork.isNotEmpty() && rawArtwork != "null") {
                            rawArtwork
                        } else {
                            bgTrack?.artworkUrlTemplate.orEmpty()
                        }
                        val backgroundImageUrl = item.get("backgroundImageUrl")?.asString.orEmpty()
                        val text = item.get("text")?.asString.orEmpty()
                        val activeTitle = item.get("activeTitle")?.asString ?: "Save to Your Library"
                        val inactiveTitle = item.get("inactiveTitle")?.asString ?: "Saved to Library"
                        val urn = item.get("urn")?.asString.orEmpty()
                        // Use the real numeric ID (not hashCode) so isPlaylistLiked matches
                        // what toggleSavePlaylist stores in LikeRepository.
                        val plId = com.alananasss.kittytune.ui.yearlyplayback.YearlyPlaybackViewModel.extractPlaylistId(urn)
                        val isLiked = com.alananasss.kittytune.data.LikeRepository.isPlaylistLiked(plId)
                        blocks.add(
                            Block.SavePlaylist(
                                artboardId = "SavePlaylist_$index",
                                durationMs = duration,
                                index = index,
                                backgroundTrack = bgTrack,
                                playlistArtwork = artworkUrl,
                                backgroundImageUrl = backgroundImageUrl,
                                text = text,
                                activeTitle = activeTitle,
                                inactiveTitle = inactiveTitle,
                                playlistUrn = urn,
                                isLiked = isLiked
                            )
                        )
                    }
                    "OpenInsightsItem" -> {
                        val title = item.get("title")?.asString.orEmpty()
                        val text = item.get("text")?.asString.orEmpty()
                        val backgroundImageUrl = item.get("backgroundImageUrl")?.asString.orEmpty()
                        blocks.add(
                            Block.OpenInsights(
                                artboardId = "FinalSlide",
                                durationMs = duration,
                                index = index,
                                backgroundTrack = bgTrack,
                                backgroundImageUrl = backgroundImageUrl,
                                text = text,
                                title = title
                            )
                        )
                    }
                }
            }

            return blocks
        }
    }

    suspend fun fetchData(
        target: YearlyPlaybackTarget = YearlyPlaybackTarget.FAN,
        variant: YearlyPlaybackVariant = YearlyPlaybackVariant.CURRENT_YEAR,
        requestedYear: Int = 2025
    ): Result<YearlyPlaybackData> = withContext(Dispatchers.IO) {
        if (requestedYear == 2025) {
            val remoteResult = fetchFromSoundCloud(target, variant)
            if (remoteResult.isSuccess) {
                val data = remoteResult.getOrNull()
                if (data != null && data.blocks.isNotEmpty()) {
                    Log.d(TAG, "Successfully loaded authentic SoundCloud Wrapped: ${data.blocks.size} blocks, riveUrl=${data.riveAssetUrl}")
                    return@withContext Result.success(data)
                } else {
                    return@withContext Result.success(YearlyPlaybackData(emptyList()))
                }
            } else {
                Log.w(TAG, "SoundCloud GraphQL fetch failed: ${remoteResult.exceptionOrNull()?.message}")
                return@withContext Result.failure(remoteResult.exceptionOrNull() ?: IllegalStateException("Failed to load SoundCloud Wrapped"))
            }
        }
        Result.success(YearlyPlaybackData(emptyList()))
    }

    private suspend fun fetchFromSoundCloud(
        target: YearlyPlaybackTarget,
        variant: YearlyPlaybackVariant
    ): Result<YearlyPlaybackData> = runCatching {
        val queryStr = if (target == YearlyPlaybackTarget.FAN) FAN_QUERY_2025 else CREATOR_QUERY_2025

        val request = GraphQlRequest(
            operationName = "YearlyPlayback",
            query = queryStr,
            variables = emptyMap<String, Any>()
        )

        val responseJson = api.postGraphQl("https://graph.soundcloud.com/graphql", request)
        parseSoundCloudResponse(responseJson, target, variant)
            ?: YearlyPlaybackData(emptyList())
    }

    private fun parseSoundCloudResponse(
        json: JsonObject,
        target: YearlyPlaybackTarget,
        variant: YearlyPlaybackVariant
    ): YearlyPlaybackData? {
        val dataObj = json.getAsJsonObject("data") ?: return null
        val key = if (target == YearlyPlaybackTarget.FAN) "yearlyFanPlayback2025" else "yearlyCreatorPlayback2025"

        if (!dataObj.has(key) || dataObj.get(key).isJsonNull) {
            return YearlyPlaybackData(emptyList())
        }

        val playbackData = dataObj.getAsJsonObject(key) ?: return YearlyPlaybackData(emptyList())
        val timelineArray = playbackData.getAsJsonArray("timeline") ?: return YearlyPlaybackData(emptyList())
        val bgTracksArray = playbackData.getAsJsonArray("backgroundTracks") ?: JsonArray()
        val assetsArray = playbackData.getAsJsonArray("assets") ?: JsonArray()

        var riveUrl: String? = null
        val imageAssets = mutableListOf<ApiYearlyPlaybackAsset>()

        assetsArray.forEach { elem ->
            if (elem.isJsonObject) {
                val obj = elem.asJsonObject
                val id = obj.get("assetId")?.asString.orEmpty()
                val url = obj.get("url")?.asString.orEmpty()
                if (url.endsWith(".riv") || id.contains("fan") || id.contains("creator")) {
                    riveUrl = url
                } else if (url.isNotEmpty()) {
                    imageAssets.add(ApiYearlyPlaybackAsset(id, url))
                }
            }
        }

        val blocks = parseTimeline(timelineArray, bgTracksArray, gson)
        return YearlyPlaybackData(
            blocks = blocks,
            riveAssetUrl = riveUrl,
            imageAssets = imageAssets
        )
    }
}
