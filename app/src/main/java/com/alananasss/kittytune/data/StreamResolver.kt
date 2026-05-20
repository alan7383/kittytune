    package com.alananasss.kittytune.data

    import android.content.Context
    import android.util.Log
    import android.webkit.CookieManager
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
            return resolveStreamWithDrm(context, track, forDownload)?.url
        }

        suspend fun resolveStreamWithDrm(context: Context, track: Track, forDownload: Boolean = false): ResolvedStream? {
            return withContext(Dispatchers.IO) {
                try {
                    val localTrack = DownloadManager.getLocalTrack(track.id)
                    if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                        val isContentUri = localTrack.localAudioPath.startsWith("content://")
                        val fileExists = if (isContentUri) true else java.io.File(localTrack.localAudioPath).exists()

                        if (fileExists) {
                            Log.d(TAG, "Offline mode: Playing from local storage -> ${localTrack.localAudioPath}")
                            return@withContext ResolvedStream(localTrack.localAudioPath)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking local file", e)
                }
                if (track.source == "youtube") {
                    Log.d(TAG, "Resolving YouTube track: ${track.title}")
                    val url = resolveFromYoutubeDirect(track)
                    return@withContext url?.let { ResolvedStream(it) }
                }

                val prefs = PlayerPreferences(context)
                val allowYoutube = prefs.getYouTubeFallbackEnabled()

                if (isRestricted(track) && allowYoutube) {
                    val streamUrl = resolveViaNewPipe(track)

                    if (streamUrl != null) {
                        return@withContext ResolvedStream(streamUrl)
                    } else {
                            Log.w(TAG, "Unlock via NewPipe failed, falling back to SoundCloud standard.")
                        }
                }

                return@withContext resolveFromSoundCloudWithDrm(context, track, forDownload)
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
            return resolveFromSoundCloudWithDrm(context, track, forDownload)?.url
        }

        private suspend fun resolveFromSoundCloudWithDrm(context: Context, track: Track, forDownload: Boolean): ResolvedStream? {
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

            // Log all available transcodings for debugging
            Log.d(TAG, "Track ${track.id} — ${transcodings.size} transcodings available:")
            transcodings.forEachIndexed { i, t ->
                Log.d(TAG, "  [$i] preset=${t.preset}, protocol=${t.format?.protocol}, mime=${t.format?.mimeType}, url=${t.url}")
            }
            Log.d(TAG, "Track ${track.id} — policy=${trackToUse.policy}, monetization=${trackToUse.monetizationModel}")

            // Build an ordered list of candidate transcodings to try
            // Priority: progressive > hls > cbc-encrypted-hls > ctr-encrypted-hls
            val candidates = buildTranscodingCandidates(transcodings, qualityPref, forDownload)

            if (candidates.isEmpty()) {
                Log.w(TAG, "Track ${track.id} — no matching transcoding found!")
                if (forDownload && prefs.getYouTubeFallbackEnabled()) {
                    val url = resolveViaNewPipe(track)
                    return url?.let { ResolvedStream(it) }
                }
                return null
            }

            Log.d(TAG, "Track ${track.id} — ${candidates.size} candidates to try: ${candidates.map { "${it.preset}/${it.format?.protocol}" }}")

            val tokenManager = TokenManager(context)
            var token = SessionManager.awaitFreshAccessToken(
                context = context,
                force = tokenManager.shouldRefreshAccessToken()
            ) ?: tokenManager.getAccessToken()

            // Try each candidate transcoding until one works
            for (candidate in candidates) {
                val protocol = candidate.format?.protocol ?: continue
                val apiUrl = candidate.url ?: continue

                val urlWithParams = if (apiUrl.contains("?")) "$apiUrl&client_id=${Config.CLIENT_ID}" else "$apiUrl?client_id=${Config.CLIENT_ID}"

                Log.d(TAG, "Track ${track.id} — trying transcoding: preset=${candidate.preset}, protocol=$protocol")

                try {
                    var response = client.newCall(buildStreamInfoRequest(urlWithParams, token)).execute()

                    if (!response.isSuccessful && isAuthFailure(response.code)) {
                        Log.w(TAG, "Track ${track.id} — auth failure (${response.code}), refreshing token...")
                        val refreshedToken = SessionManager.awaitFreshAccessToken(
                            context = context,
                            staleToken = token,
                            force = true
                        )

                        if (!refreshedToken.isNullOrEmpty() && refreshedToken != token) {
                            response.close()
                            token = refreshedToken
                            response = client.newCall(buildStreamInfoRequest(urlWithParams, token)).execute()
                        } else if (!token.isNullOrEmpty()) {
                            response.close()
                            token = null
                            response = client.newCall(buildStreamInfoRequest(urlWithParams, token)).execute()
                        }
                    }

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Track ${track.id} — transcoding ${candidate.preset}/$protocol failed: code=${response.code}")
                        response.close()
                        continue // Try next candidate
                    }

                    val body = response.body?.string() ?: continue
                    Log.d(TAG, "Track ${track.id} — stream API response: ${body.take(500)}")
                    val json = JSONObject(body)
                    val streamInfoUrl = json.getString("url")

                    // Extract DRM license token if present (CENC-encrypted tracks)
                    val licenseAuthToken = json.optString("licenseAuthToken", null)
                    if (!licenseAuthToken.isNullOrEmpty()) {
                        Log.d(TAG, "Track ${track.id} — CENC DRM detected! licenseAuthToken=${licenseAuthToken.take(50)}...")
                    }

                    // HLS or encrypted-HLS — return directly
                    val isHlsLike = protocol == "hls" || protocol.contains("encrypted-hls")
                    if (isHlsLike) {
                        Log.d(TAG, "Track ${track.id} — HLS resolved: $streamInfoUrl (drm=${!licenseAuthToken.isNullOrEmpty()}, protocol=$protocol)")
                        return ResolvedStream(streamInfoUrl, licenseAuthToken)
                    }

                    // Progressive — follow redirect to get CDN URL
                    Log.d(TAG, "Resolving progressive stream URL: $streamInfoUrl")
                    val finalRequest = okhttp3.Request.Builder().url(streamInfoUrl).build()
                    val finalResponse = client.newCall(finalRequest).execute()
                    finalResponse.body?.close()

                    if (!finalResponse.isSuccessful) {
                        Log.e(TAG, "Final resolution of progressive URL failed: ${finalResponse.code}")
                        continue // Try next candidate
                    }

                    val finalUrl = finalResponse.request.url.toString()
                    Log.d(TAG, "Final Progressive CDN URL: $finalUrl")
                    return ResolvedStream(finalUrl, licenseAuthToken)

                } catch (e: Exception) {
                    Log.e(TAG, "Track ${track.id} — exception trying ${candidate.preset}/$protocol", e)
                    continue // Try next candidate
                }
            }

            Log.e(TAG, "Track ${track.id} — all ${candidates.size} transcoding candidates failed!")
            return null
        }

        /**
         * Builds an ordered list of transcoding candidates to try.
         * For downloads, only progressive is useful (no DRM).
         * For playback, we prefer: progressive > hls > CENC-encrypted HLS.
         */
        private fun buildTranscodingCandidates(
            transcodings: List<com.alananasss.kittytune.domain.Transcoding>,
            qualityPref: String,
            forDownload: Boolean
        ): List<com.alananasss.kittytune.domain.Transcoding> {
            val candidates = mutableListOf<com.alananasss.kittytune.domain.Transcoding>()

            // 1. Progressive (best for downloads, good for playback)
            transcodings.find { it.format?.protocol == "progressive" }?.let { candidates.add(it) }

            if (forDownload) return candidates // Downloads only want progressive

            // 2. Standard HLS (non-encrypted)
            if (qualityPref != "HIGH") {
                transcodings.find { it.format?.protocol == "hls" && it.format.mimeType?.contains("mpeg") == true }?.let { candidates.add(it) }
            }
            transcodings.find { it.format?.protocol == "hls" }?.let {
                if (!candidates.contains(it)) candidates.add(it)
            }

            // 3. CENC encrypted HLS — CTR mode preferred (standard Widevine CENC on Android), then CBC
            // Prefer higher quality presets (aac_160k > aac_96k > abr_sq)
            val cencPresets = listOf("aac_160k", "aac_96k", "abr_sq")
            for (preset in cencPresets) {
                transcodings.find { it.preset == preset && it.format?.protocol == "ctr-encrypted-hls" }?.let { candidates.add(it) }
                transcodings.find { it.preset == preset && it.format?.protocol == "cbc-encrypted-hls" }?.let { candidates.add(it) }
            }
            // Add any remaining encrypted transcodings not yet in the list
            transcodings.filter { it.format?.protocol?.contains("encrypted") == true && !candidates.contains(it) }
                .forEach { candidates.add(it) }

            return candidates
        }



        private fun buildStreamInfoRequest(url: String, token: String?): okhttp3.Request {
            val builder = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", Config.USER_AGENT)
                .header("Accept", "application/json")
                .header("Origin", "https://soundcloud.com")
                .header("Referer", "https://soundcloud.com/")

            if (!token.isNullOrEmpty() && token != "null") {
                builder.header("Authorization", "OAuth $token")
            }

            val cookies = CookieManager.getInstance().getCookie("https://soundcloud.com")
            if (!cookies.isNullOrEmpty()) {
                builder.header("Cookie", cookies)
            }

            return builder.build()
        }

        private fun isAuthFailure(code: Int): Boolean = code == 401 || code == 403
    }


