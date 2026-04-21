    package com.alananasss.kittytune.data

    import android.content.Context
    import android.util.Log
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.utils.Config
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.withContext
    import okhttp3.OkHttpClient
    import okhttp3.RequestBody.Companion.toRequestBody
    import org.json.JSONObject
    import org.schabi.newpipe.extractor.NewPipe
    import org.schabi.newpipe.extractor.ServiceList
    import org.schabi.newpipe.extractor.downloader.Downloader
    import org.schabi.newpipe.extractor.downloader.Request
    import org.schabi.newpipe.extractor.downloader.Response
    import org.schabi.newpipe.extractor.search.SearchInfo
    import org.schabi.newpipe.extractor.stream.StreamInfoItem
    import java.io.IOException

    private object ExtractorDownloader : Downloader() {
        private val client = OkHttpClient()

        @Throws(IOException::class)
        override fun execute(request: Request): Response {
            val okHttpRequest = okhttp3.Request.Builder().url(request.url())
            request.headers().forEach { (key, values) ->
                values.forEach { value -> okHttpRequest.addHeader(key, value) }
            }

            when (request.httpMethod()) {
                "GET" -> okHttpRequest.get()
                "HEAD" -> okHttpRequest.head()
                "POST" -> {
                    val body = request.dataToSend()?.toRequestBody() ?: byteArrayOf().toRequestBody()
                    okHttpRequest.post(body)
                }
                else -> throw IOException("unsupported http method: ${request.httpMethod()}")
            }

            val response = client.newCall(okHttpRequest.build()).execute()
            return Response(
                response.code,
                response.message,
                response.headers.toMultimap(),
                response.body?.string(),
                response.request.url.toString()
            )
        }
    }

    object StreamResolver {

        private const val TAG = "StreamResolver"
        private val client = OkHttpClient()

        init {
            try {
                NewPipe.init(ExtractorDownloader)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init NewPipe extractor", e)
            }
        }

        fun isRestricted(track: Track): Boolean {
            return track.policy == "SNIP" ||
                    track.policy == "BLOCK" ||
                    track.monetizationModel == "SUB_HIGH_TIER" ||
                    track.media?.transcodings.isNullOrEmpty()
        }

        suspend fun resolveStream(context: Context, track: Track, forDownload: Boolean = false): String? {
            return withContext(Dispatchers.IO) {
                try {
                    val localTrack = DownloadManager.getLocalTrack(track.id)
                    if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                        val isContentUri = localTrack.localAudioPath.startsWith("content://")
                        val fileExists = if (isContentUri) true else java.io.File(localTrack.localAudioPath).exists()

                        if (fileExists) {
                            Log.d(TAG, "Offline mode: Playing from local storage -> ${localTrack.localAudioPath}")
                            return@withContext localTrack.localAudioPath
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking local file", e)
                }
                if (track.source == "youtube") {
                    Log.d(TAG, "Resolving YouTube track: ${track.title}")
                    return@withContext resolveFromYoutubeDirect(track)
                }

                val prefs = PlayerPreferences(context)
                val allowYoutube = prefs.getYouTubeFallbackEnabled()

                if (isRestricted(track) && allowYoutube) {
                    val streamUrl = resolveViaNewPipe(track)

                    if (streamUrl != null) {
                        return@withContext streamUrl
                    } else {
                            Log.w(TAG, "Unlock via NewPipe failed, falling back to SoundCloud standard.")
                        }
                }

                return@withContext resolveFromSoundCloud(context, track, forDownload)
            }
        }

        private suspend fun resolveViaNewPipe(track: Track): String? {
            return try {
                val cleanTitle = track.title?.replace(Regex("(?i)(\\[.*?\\]|\\(.*?\\))"), "")?.trim() ?: ""
                val artistName = track.user?.username ?: ""
                val query = "$cleanTitle $artistName audio"

                Log.d(TAG, "[NewPipe] Searching for: $query")

                val youtubeService = ServiceList.YouTube
                val searchInfo = SearchInfo.getInfo(youtubeService, youtubeService.searchQHFactory.fromQuery(query, listOf("videos"), ""))
                val videoResults = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>()

                if (videoResults.isEmpty()) {
                    Log.w(TAG, "[NewPipe] No results found.")
                    return null
                }

                val firstResultUrl = videoResults.first().url
                Log.d(TAG, "[NewPipe] Found match: $firstResultUrl")

                val extractor = youtubeService.getStreamExtractor(firstResultUrl)
                extractor.fetchPage()

                val bestAudioStream = extractor.audioStreams
                    .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.format == org.schabi.newpipe.extractor.MediaFormat.M4A && it.url != null }
                    .maxByOrNull { it.averageBitrate }
                    ?: extractor.audioStreams
                        .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.url != null }
                        .maxByOrNull { it.averageBitrate }

                if (bestAudioStream == null) {
                    Log.w(TAG, "[NewPipe] No valid audio stream found.")
                    return null
                }

                Log.d(TAG, "[NewPipe] Success: ${bestAudioStream.averageBitrate}kbps")
                bestAudioStream.url
            } catch (e: Exception) {
                Log.e(TAG, "[NewPipe] Error:", e)
                null
            }
        }

        private suspend fun resolveFromYoutubeDirect(track: Track): String? {
            val url = track.permalinkUrl ?: return null
            return try {
                val service = ServiceList.YouTube
                val extractor = service.getStreamExtractor(url)
                extractor.fetchPage()
                extractor.audioStreams
                    .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.url != null }
                    .maxByOrNull { it.averageBitrate }
                    ?.url
            } catch (e: Exception) {
                Log.e(TAG, "[YouTube] Failed to extract direct stream: ${e.message}")
                null
            }
        }

        private suspend fun resolveFromSoundCloud(context: Context, track: Track, forDownload: Boolean): String? {
            val prefs = PlayerPreferences(context)
            val api = RetrofitClient.create(context)
            var trackToUse = track

            if (track.media == null || track.media.transcodings.isNullOrEmpty()) {
                try {
                    val fetched = api.getTracksByIds(track.id.toString())
                    if (fetched.isNotEmpty()) trackToUse = fetched[0] else return null
                } catch (e: Exception) {
                    return null
                }
            }

            val transcodings = trackToUse.media?.transcodings ?: return null
            val qualityPref = prefs.getAudioQuality()

            val target = if (forDownload) {
                transcodings.find { it.format?.protocol == "progressive" }
            } else if (qualityPref == "HIGH") {
                transcodings.find { it.format?.protocol == "progressive" }
                    ?: transcodings.find { it.format?.protocol == "hls" }
            } else {
                transcodings.find { it.format?.protocol == "progressive" }
                    ?: transcodings.find { it.format?.protocol == "hls" && it.format.mimeType?.contains("mpeg") == true }
                    ?: transcodings.find { it.format?.protocol == "hls" }
            }

            if (target == null) {
                if (forDownload && prefs.getYouTubeFallbackEnabled()) {
                    return resolveViaNewPipe(track)
                }
                return null
            }

            val apiUrl = target?.url ?: return null

            val urlWithParams = if (apiUrl.contains("?")) "$apiUrl&client_id=${Config.CLIENT_ID}" else "$apiUrl?client_id=${Config.CLIENT_ID}"
            val builder = okhttp3.Request.Builder().url(urlWithParams).header("User-Agent", Config.USER_AGENT)

            val token = TokenManager(context).getAccessToken()
            if (!token.isNullOrEmpty() && token != "null") {
                builder.header("Authorization", "OAuth $token")
            }

            try {
                val response = client.newCall(builder.build()).execute()
                if (!response.isSuccessful) {
                    Log.e(TAG, "API request for stream URL failed with code: ${response.code}")
                    return null
                }
                val body = response.body?.string() ?: return null
                val streamInfoUrl = JSONObject(body).getString("url")

                if (target?.format?.protocol == "hls") {
                    Log.d(TAG, "HLS Playlist URL resolved: $streamInfoUrl")
                    return streamInfoUrl
                }

                Log.d(TAG, "Resolving progressive stream URL: $streamInfoUrl")

                val finalRequest = okhttp3.Request.Builder().url(streamInfoUrl).build()
                val finalResponse = client.newCall(finalRequest).execute()

                finalResponse.body?.close()

                if (!finalResponse.isSuccessful) {
                    Log.e(TAG, "Final resolution of progressive URL failed: ${finalResponse.code}")
                    return null
                }

                val finalUrl = finalResponse.request.url.toString()
                Log.d(TAG, "Final Progressive CDN URL: $finalUrl")
                return finalUrl

            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }


