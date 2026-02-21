    package com.alananasss.kittytune.data
    
    import android.content.Context
    import android.util.Log
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.BuildConfig
    import com.alananasss.kittytune.data.local.YouTubeFallbackMode
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.utils.Config
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.withContext
    import okhttp3.OkHttpClient
    import okhttp3.RequestBody.Companion.toRequestBody
    import org.json.JSONArray
    import org.json.JSONObject
    import org.schabi.newpipe.extractor.NewPipe
    import org.schabi.newpipe.extractor.ServiceList
    import org.schabi.newpipe.extractor.downloader.Downloader
    import org.schabi.newpipe.extractor.downloader.Request
    import org.schabi.newpipe.extractor.downloader.Response
    import org.schabi.newpipe.extractor.search.SearchInfo
    import org.schabi.newpipe.extractor.stream.StreamInfoItem
    import java.io.IOException
    import java.net.URLEncoder
    
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
    
        private const val INVIDIOUS_INSTANCE = BuildConfig.MY_INVIDIOUS_URL
        private const val USER_AGENT_PC = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    
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
    
        suspend fun resolveStream(context: Context, track: Track): String? {
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
                    val mode = prefs.getYouTubeFallbackMode()
                    Log.d(TAG, "Restricted track: ${track.title}. Fallback Mode: $mode")
    
                    val streamUrl = when (mode) {
                        YouTubeFallbackMode.AUTOMATIC -> {
                            resolveViaNewPipe(track) ?: resolveViaInvidious(track)
                        }
                        YouTubeFallbackMode.NEWPIPE -> {
                            resolveViaNewPipe(track)
                        }
                        YouTubeFallbackMode.INVIDIOUS -> {
                            resolveViaInvidious(track)
                        }
                    }
    
                    if (streamUrl != null) {
                        return@withContext streamUrl
                    } else {
                        Log.w(TAG, "Unlock failed with mode $mode, falling back to SoundCloud standard.")
                    }
                }
    
                return@withContext resolveFromSoundCloud(context, track)
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
    
        private suspend fun resolveViaInvidious(track: Track): String? {
            return try {
                val cleanTitle = track.title?.replace(Regex("(?i)(\\[.*?\\]|\\(.*?\\))"), "")?.trim() ?: ""
                val artistName = track.user?.username ?: ""
                val query = URLEncoder.encode("$cleanTitle $artistName audio", "UTF-8")
    
                Log.d(TAG, "[Invidious] Searching on $INVIDIOUS_INSTANCE for: $query")
    
                val searchUrl = "$INVIDIOUS_INSTANCE/api/v1/search?q=$query&type=video&region=US"
                val searchRequest = okhttp3.Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", USER_AGENT_PC)
                    .build()
    
                val searchResponse = client.newCall(searchRequest).execute()
                if (!searchResponse.isSuccessful) return null
    
                val body = searchResponse.body?.string() ?: return null
                val jsonArray = JSONArray(body)
    
                if (jsonArray.length() == 0) {
                    Log.w(TAG, "[Invidious] No results.")
                    return null
                }
    
                val videoId = jsonArray.getJSONObject(0).getString("videoId")
                Log.d(TAG, "[Invidious] Found Video ID: $videoId")
    
                getInvidiousAudioStream(videoId)
            } catch (e: Exception) {
                Log.e(TAG, "[Invidious] Error:", e)
                null
            }
        }
    
        private suspend fun getInvidiousAudioStream(videoId: String): String? {
            try {
                val videoUrl = "$INVIDIOUS_INSTANCE/api/v1/videos/$videoId?local=true"
                val videoRequest = okhttp3.Request.Builder()
                    .url(videoUrl)
                    .header("User-Agent", USER_AGENT_PC)
                    .header("Accept", "application/json")
                    .build()
    
                val videoResponse = client.newCall(videoRequest).execute()
                if (!videoResponse.isSuccessful) return null
    
                val responseBody = videoResponse.body?.string() ?: return null
                val videoJson = JSONObject(responseBody)
    
                if (videoJson.has("adaptiveFormats")) {
                    val formats = videoJson.getJSONArray("adaptiveFormats")
                    var bestUrl: String? = null
                    var bestBitrate = 0
    
                    for (i in 0 until formats.length()) {
                        val fmt = formats.getJSONObject(i)
                        val mime = fmt.optString("mimeType")
                        if (mime.startsWith("audio/") || fmt.optString("type").contains("audio")) {
                            val bitrate = fmt.optInt("bitrate", 0)
                            if (bitrate > bestBitrate) {
                                bestBitrate = bitrate
                                bestUrl = fmt.getString("url")
                            }
                        }
                    }
                    if (bestUrl != null) return fixUrl(bestUrl)
                }
    
                if (videoJson.has("formatStreams")) {
                    val streams = videoJson.getJSONArray("formatStreams")
                    for (i in 0 until streams.length()) {
                        val fmt = streams.getJSONObject(i)
                        if (fmt.optString("container") == "mp4") {
                            return fixUrl(fmt.getString("url"))
                        }
                    }
                    if (streams.length() > 0) return fixUrl(streams.getJSONObject(0).getString("url"))
                }
            } catch (e: Exception) {
                Log.e(TAG, "[Invidious] Stream Extraction Error:", e)
            }
            return null
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
                val videoId = url.substringAfter("v=").substringBefore("&")
                getInvidiousAudioStream(videoId)
            }
        }
    
        private suspend fun resolveFromSoundCloud(context: Context, track: Track): String? {
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
    
            val target = if (qualityPref == "HIGH") {
                transcodings.find { it.format?.protocol == "progressive" }
                    ?: transcodings.find { it.format?.protocol == "hls" }
            } else {
                transcodings.find { it.format?.protocol == "progressive" }
                    ?: transcodings.find { it.format?.protocol == "hls" && it.format.mimeType?.contains("mpeg") == true }
                    ?: transcodings.find { it.format?.protocol == "hls" }
            } ?: return null
    
            val apiUrl = target.url
    
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
    
                if (target.format?.protocol == "hls") {
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
    
        private fun fixUrl(originalUrl: String): String {
            return when {
                originalUrl.startsWith("http") -> originalUrl
                originalUrl.startsWith("//") -> "https:$originalUrl"
                originalUrl.startsWith("/") -> {
                    val base = INVIDIOUS_INSTANCE.removeSuffix("/")
                    "$base$originalUrl"
                }
                else -> originalUrl
            }
        }
    }


