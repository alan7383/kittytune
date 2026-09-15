    package com.alananasss.kittytune.data

    import android.content.Context
    import android.util.Log
    import android.webkit.CookieManager
    import com.alananasss.kittytune.audio.providers.AudioProviderOrderItem
    import com.alananasss.kittytune.audio.providers.ProviderIsrc
    import com.alananasss.kittytune.audio.providers.IsrcResolver
    import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
    import com.alananasss.kittytune.audio.providers.tidal.TidalAudioProvider
    import com.alananasss.kittytune.audio.providers.tidal.TidalAudioQuality
    import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioProvider
    import com.alananasss.kittytune.audio.providers.deezer.DeezerAudioQuality
    import com.alananasss.kittytune.data.local.PlayerPreferences
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.alananasss.kittytune.domain.Track
    import com.alananasss.kittytune.utils.Config
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.withContext
    import okhttp3.Cookie
    import okhttp3.CookieJar
    import okhttp3.HttpUrl
    import okhttp3.OkHttpClient
    import okhttp3.RequestBody.Companion.toRequestBody
    import java.util.concurrent.ConcurrentHashMap
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
        private val baseClient = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()

                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    val host = url.host
                    val existing = cookieStore.getOrPut(host) { mutableListOf() }
                    val updated = existing.associateBy { it.name }.toMutableMap()
                    cookies.forEach { updated[it.name] = it }
                    cookieStore[host] = updated.values.toMutableList()
                }

                override fun loadForRequest(url: HttpUrl): List<Cookie> {
                    val host = url.host
                    val validCookies = mutableListOf<Cookie>()
                    cookieStore.forEach { (domain, domainCookies) ->
                        if (host == domain || host.endsWith(".$domain")) {
                            validCookies.addAll(domainCookies)
                        }
                    }
                    return validCookies
                }
            })
            .addInterceptor { chain ->
                val request = chain.request()
                val host = request.url.host
                if (host.contains("youtube.com") || host.contains("youtu.be")) {
                    val existingCookies = request.headers("Cookie").joinToString("; ")
                    if (!existingCookies.contains("CONSENT=")) {
                        val newCookie = if (existingCookies.isNotEmpty()) "$existingCookies; CONSENT=YES+cb" else "CONSENT=YES+cb"
                        val newRequest = request.newBuilder()
                            .header("Cookie", newCookie)
                            .build()
                        return@addInterceptor chain.proceed(newRequest)
                    }
                }
                chain.proceed(request)
            }
            .build()

        private val client: OkHttpClient
            get() = com.alananasss.kittytune.data.network.ProxyManager.configureOkHttpClient(baseClient.newBuilder()).build()

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
        private val client: OkHttpClient
            get() = com.alananasss.kittytune.data.network.ProxyManager.getOkHttpClient()

        init {
            try {
                NewPipe.init(ExtractorDownloader)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to init NewPipe extractor", e)
            }
        }

        /**
         * Whether SoundCloud will refuse to stream this track to us, so the YouTube fallback is the only
         * way to hear it.
         *
         * Judged on the policy fields alone. An absent media block used to count too, and that is wrong: a
         * `Track` with no transcodings is one nobody has asked the API about yet, not one the API refuses.
         * On the desktop, where liked tracks are slimmed for memory, that sent *every liked track* to
         * YouTube — a three-minute song playing for twenty seconds. This side does not slim, but any
         * partially-hydrated track (a search hit, a restored queue) has the same shape (issue #33).
         *
         * Nothing is lost: the SoundCloud path re-fetches a track whose transcodings are missing and decides
         * from the fresh data, and falls back from there if there is genuinely no usable candidate.
         */
        /**
         * How far a YouTube candidate's length may be from the track's before it is rejected.
         *
         * Wide enough for the usual differences — a fade, trailing silence, an intro on the upload — and far
         * too narrow for a snippet or a teaser to slip through.
         */
        private const val DURATION_TOLERANCE_SEC = 12L

        fun isRestricted(track: Track): Boolean {
            return track.policy == "SNIP" ||
                    track.policy == "BLOCK" ||
                    track.monetizationModel == "SUB_HIGH_TIER"
        }

        suspend fun resolveStream(context: Context, track: Track, forDownload: Boolean = false): String? {
            return resolveStreamWithDrm(context, track, forDownload)?.url
        }

        suspend fun resolveSoundCloudDirect(context: Context, track: Track): ResolvedStream? {
            return withContext(Dispatchers.IO) {
                resolveFromSoundCloudWithDrm(context, track, forDownload = false)
            }
        }

        /**
         * Resolves a stream suitable for audio analysis (BeatAnalyzer).
         * Prioritizes SoundCloud native streams (including Widevine CENC DRM streams),
         * falling back to YouTube NewPipe and other providers if resolution fails.
         */
        suspend fun resolveStreamForBeatAnalysis(context: Context, track: Track): ResolvedStream? {
            return withContext(Dispatchers.IO) {
                // 1. Try SoundCloud stream directly (with Widevine DRM if protected)
                val scStream = try {
                    resolveFromSoundCloudWithDrm(context, track, forDownload = false)
                } catch (e: Exception) {
                    Log.w(TAG, "[BeatAnalysis] Failed resolving SoundCloud stream: ${e.message}")
                    null
                }
                if (scStream != null) {
                    Log.d(TAG, "[BeatAnalysis] Using SoundCloud stream for '${track.title}' (drm=${scStream.isDrmProtected})")
                    return@withContext scStream
                }

                // 2. YouTube NewPipe fallback if SoundCloud resolution failed
                val prefs = PlayerPreferences(context)
                if (prefs.getYouTubeFallbackEnabled()) {
                    Log.d(TAG, "[BeatAnalysis] Trying YouTube NewPipe fallback for '${track.title}'")
                    val ytUrl = try { resolveViaNewPipe(track) } catch (_: Exception) { null }
                    if (ytUrl != null) {
                        Log.d(TAG, "[BeatAnalysis] Got YouTube stream for '${track.title}'")
                        return@withContext ResolvedStream(ytUrl)
                    }
                }

                // 3. Other providers (Deezer, Qobuz, Tidal)
                val providerStream = try {
                    resolveViaProviders(context, track, forDownload = true)
                } catch (_: Exception) { null }
                if (providerStream != null) {
                    Log.d(TAG, "[BeatAnalysis] Using provider stream for '${track.title}'")
                    return@withContext providerStream
                }

                Log.d(TAG, "[BeatAnalysis] No stream found for '${track.title}'")
                null
            }
        }

        suspend fun resolveStreamWithDrm(context: Context, track: Track, forDownload: Boolean = false): ResolvedStream? {
            return withContext(Dispatchers.IO) {
                val resolved: ResolvedStream? = run {
                    try {
                        val localTrack = DownloadManager.getLocalTrack(track.id)
                        if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                            if (localTrack.localAudioPath.startsWith("exo_cache://")) {
                                val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                                val cachedStreamUrl = parts.getOrNull(1)
                                val token = parts.getOrNull(2)
                                if (!cachedStreamUrl.isNullOrEmpty()) {
                                    Log.d(TAG, "Offline mode: Playing from ExoCache -> $cachedStreamUrl")
                                    return@run ResolvedStream(cachedStreamUrl, licenseAuthToken = token)
                                }
                            } else {
                                val isContentUri = localTrack.localAudioPath.startsWith("content://")
                                val fileExists = if (isContentUri) true else java.io.File(localTrack.localAudioPath).exists()

                                if (fileExists) {
                                    Log.d(TAG, "Offline mode: Playing from local storage -> ${localTrack.localAudioPath}")
                                    return@run ResolvedStream(localTrack.localAudioPath)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking local file", e)
                    }
                    if (track.source == "youtube") {
                        Log.d(TAG, "Resolving YouTube track: ${track.title}")
                        val url = resolveFromYoutubeDirect(track)
                        return@run url?.let { ResolvedStream(it) }
                    }

                    if (track.source == "vk") {
                        Log.d(TAG, "Resolving VKontakte track: ${track.title} (${track.id})")
                        val vkUrl = com.alananasss.kittytune.data.vk.VkRepository.resolveStream(context, track)
                        if (vkUrl != null) {
                            return@run ResolvedStream(vkUrl)
                        }
                        // Only reach for YouTube when the user opted into that fallback: silently
                        // playing a different recording is worse than reporting the failure.
                        if (PlayerPreferences(context).getYouTubeFallbackEnabled()) {
                            Log.w(TAG, "VK reload failed, trying the YouTube fallback for: ${track.title}")
                            val fallbackUrl = resolveViaNewPipe(track)
                            if (fallbackUrl != null) {
                                return@run ResolvedStream(fallbackUrl)
                            }
                        }
                        Log.w(TAG, "Could not resolve a VK stream for: ${track.title}")
                        return@run null
                    }

                    if (track.source == "spotify") {
                        Log.d(TAG, "Resolving Spotify track via configured audio providers: ${track.title} by ${track.displayArtist}")
                        val providerStream = resolveViaProviders(context, track, forDownload)
                        if (providerStream != null) {
                            return@run providerStream
                        }
                        val streamUrl = resolveViaNewPipe(track)
                        if (streamUrl != null) {
                            return@run ResolvedStream(streamUrl)
                        }
                        try {
                            val query = "${track.displayArtist} ${track.title}".trim()
                            val scResults = RetrofitClient.create(context).searchTracks(query, limit = 5)
                            val bestScTrack = scResults.collection.firstOrNull()
                            if (bestScTrack != null) {
                                val scStream = resolveFromSoundCloudWithDrm(context, bestScTrack, forDownload)
                                if (scStream != null) return@run scStream
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed SoundCloud fallback for Spotify track: ${e.message}")
                        }
                        return@run null
                    }

                    if (track.source in listOf("deezer", "tidal", "qobuz") ||
                        track.permalink?.startsWith("deezer:") == true ||
                        track.permalink?.startsWith("tidal:") == true ||
                        track.permalink?.startsWith("qobuz:") == true) {
                        Log.d(TAG, "Resolving direct provider track (${track.source}): ${track.title} by ${track.displayArtist}")
                        val providerStream = resolveViaProviders(context, track, forDownload)
                        if (providerStream != null) {
                            return@run providerStream
                        }
                        // Provider failed — fall back to YouTube rather than silently playing
                        // a SoundCloud snippet of a different song (issue #33).
                        val allowYoutubeFallback = PlayerPreferences(context).getYouTubeFallbackEnabled()
                        if (allowYoutubeFallback) {
                            Log.w(TAG, "Provider track (${track.source}) resolution failed, trying YouTube fallback for: ${track.title}")
                            val ytFallback = resolveViaNewPipe(track)
                            if (ytFallback != null) {
                                return@run ResolvedStream(ytFallback)
                            }
                        }
                        Log.w(TAG, "Could not resolve provider track (${track.source}): ${track.title}")
                        return@run null
                    }

                    val prefs = PlayerPreferences(context)
                    val allowYoutube = prefs.getYouTubeFallbackEnabled()

                    if (isRestricted(track)) {
                        val providerStream = resolveViaProviders(context, track, forDownload)
                        if (providerStream != null) {
                            return@run providerStream
                        }
                        if (allowYoutube) {
                            val streamUrl = resolveViaNewPipe(track)
                            if (streamUrl != null) {
                                return@run ResolvedStream(streamUrl)
                            }
                        }
                    }

                    val scStream = resolveFromSoundCloudWithDrm(context, track, forDownload)
                    if (scStream != null) {
                        scStream
                    } else {
                        val providerFallback = resolveViaProviders(context, track, forDownload)
                        if (providerFallback != null) {
                            providerFallback
                        } else if (allowYoutube) {
                            // Last resort: YouTube fallback when both SoundCloud and all
                            // providers have failed (issue #33).
                            Log.w(TAG, "All sources failed for '${track.title}', trying final YouTube fallback")
                            val ytUrl = resolveViaNewPipe(track)
                            ytUrl?.let { ResolvedStream(it) }
                        } else {
                            null
                        }
                    }
                }

                resolved
            }
        }

        suspend fun resolveViaProviders(
            context: Context,
            track: Track,
            forDownload: Boolean = false
        ): ResolvedStream? {
            val prefs = PlayerPreferences(context)
            val configuredOrder = prefs.getAudioProviderOrder()
            val order = when {
                track.source == "deezer" || track.permalink?.startsWith("deezer:") == true -> {
                    listOf(AudioProviderOrderItem.DEEZER) + (configuredOrder - AudioProviderOrderItem.DEEZER)
                }
                track.source == "tidal" || track.permalink?.startsWith("tidal:") == true -> {
                    listOf(AudioProviderOrderItem.TIDAL) + (configuredOrder - AudioProviderOrderItem.TIDAL)
                }
                track.source == "qobuz" || track.permalink?.startsWith("qobuz:") == true -> {
                    listOf(AudioProviderOrderItem.QOBUZ) + (configuredOrder - AudioProviderOrderItem.QOBUZ)
                }
                else -> configuredOrder
            }
            val mediaId = track.permalink?.takeIf { it.isNotBlank() } ?: track.id.toString()
            val title = track.title?.trim().orEmpty()
            val artist = (track.displayArtist.ifBlank { track.user?.username.orEmpty() }).trim()
            val album = (track.publisherMetadata?.albumTitle ?: track.publisherMetadata?.releaseTitle).orEmpty()
            val durationMs = track.durationMs ?: 0L

            var isrc = ProviderIsrc.normalize(track.publisherMetadata?.isrc)
            if (isrc == null && title.isNotBlank() && artist.isNotBlank()) {
                isrc = IsrcResolver.resolveAndValidate(
                    candidateIsrc = null,
                    song = title,
                    artist = artist,
                    durationSeconds = (durationMs / 1000).toInt()
                )
            }
            val explicitArtists = track.artists?.map { it.name.trim() }?.filter { it.isNotBlank() }.orEmpty()
            val splitArtists = artist.split(Regex(""",\s*|&\s*|\s+feat\.?\s+|\s+ft\.?\s+""", RegexOption.IGNORE_CASE))
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val artists = (explicitArtists + listOf(artist) + splitArtists).filter { it.isNotBlank() }.distinct()

            val attempted = mutableSetOf<AudioProviderOrderItem>()
            for (provider in order) {
                if (!attempted.add(provider)) continue
                try {
                    when (provider) {
                        AudioProviderOrderItem.QOBUZ -> {
                            val country = prefs.getQobuzCountry()
                            val customInstances = prefs.getQobuzCustomInstances()
                            val quality = prefs.getQobuzQuality()
                            val query = QobuzAudioProvider.Query(
                                mediaId = mediaId,
                                title = title,
                                artists = artists,
                                album = album.ifBlank { null },
                                isrc = isrc,
                                durationMs = durationMs,
                                countryCode = country,
                                qualityCode = quality,
                                customInstances = customInstances
                            )
                            val resolved = QobuzAudioProvider.resolve(query)
                            if (resolved != null && resolved.mediaUri.isNotBlank()) {
                                Log.i(TAG, "Using Qobuz stream for '${track.title}': ${resolved.label}")
                                return ResolvedStream(resolved.mediaUri)
                            }
                        }
                        AudioProviderOrderItem.TIDAL -> {
                            val quality = prefs.getTidalAudioQuality()
                            val endpoints = prefs.getTidalResolverEndpoints()
                            val query = TidalAudioProvider.Query(
                                mediaId = mediaId,
                                title = title,
                                artists = artists,
                                album = album.ifBlank { null },
                                isrc = isrc,
                                durationMs = durationMs
                            )
                            val resolved = TidalAudioProvider.resolve(
                                query = query,
                                cacheDir = context.cacheDir,
                                preferAtmos = false,
                                preferLiveDash = false,
                                audioQuality = quality,
                                resolverEndpoints = endpoints
                            )
                            if (resolved != null && resolved.mediaUri.isNotBlank()) {
                                Log.i(TAG, "Using Tidal stream for '${track.title}': ${resolved.label}")
                                return ResolvedStream(resolved.mediaUri)
                            }
                        }
                        AudioProviderOrderItem.DEEZER -> {
                            val resolverUrl = prefs.getDeezerResolverUrl()
                            val quality = prefs.getDeezerAudioQuality()
                            val fastMode = prefs.getDeezerFastMode()
                            val proxyUrl = prefs.getDeezerProxyUrl()
                            val cookie = prefs.getDeezerCookie()
                            val useAccount = prefs.getDeezerUseAccount()
                            val query = DeezerAudioProvider.Query(
                                mediaId = mediaId,
                                title = title,
                                artists = artists,
                                album = album.ifBlank { null },
                                isrc = isrc,
                                durationMs = durationMs,
                                resolverUrl = resolverUrl,
                                quality = quality,
                                fastMode = fastMode,
                                proxyUrl = proxyUrl,
                                cookie = cookie,
                                useAccount = useAccount
                            )
                            val resolved = DeezerAudioProvider.resolve(query)
                            if (resolved != null && resolved.mediaUri.isNotBlank()) {
                                Log.i(TAG, "Using Deezer stream for '${track.title}': ${resolved.label}")
                                return ResolvedStream(resolved.mediaUri)
                            }
                        }
                        AudioProviderOrderItem.YOUTUBE_MUSIC -> {
                            val ytUrl = resolveViaNewPipe(track)
                            if (ytUrl != null) {
                                Log.i(TAG, "Using YouTube stream for '${track.title}'")
                                return ResolvedStream(ytUrl)
                            }
                        }
                        AudioProviderOrderItem.SOUNDCLOUD -> {
                            if (track.source == "soundcloud" || track.source.isNullOrEmpty()) {
                                val scStream = resolveFromSoundCloudWithDrm(context, track, forDownload)
                                if (scStream != null) return scStream
                            } else if (track.source in listOf("deezer", "tidal", "qobuz")) {
                                // Skip SoundCloud text-search fallback for provider-sourced tracks:
                                // searching SoundCloud by title+artist frequently returns a completely
                                // different recording — a snippet, a remix, or a "sped up" edit — that
                                // plays as a 30-second preview of the wrong song (issue #33).
                                Log.d(TAG, "Skipping SoundCloud text-search fallback for ${track.source} track: ${track.title}")
                            } else {
                                try {
                                    val q = "$artist $title".trim()
                                    val scResults = RetrofitClient.create(context).searchTracks(q, limit = 5)
                                    val bestScTrack = scResults.collection.firstOrNull()
                                    if (bestScTrack != null) {
                                        val scStream = resolveFromSoundCloudWithDrm(context, bestScTrack, forDownload)
                                        if (scStream != null) return scStream
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "SoundCloud search fallback failed: ${e.message}")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Provider $provider failed for '${track.title}': ${e.message}")
                }
            }
            return null
        }

        private suspend fun resolveViaNewPipe(track: Track): String? {
            return try {
                val cleanTitle = track.title?.replace(Regex("(?i)(\\[.*?\\]|\\(.*?\\))"), "")?.trim() ?: ""
                val artistName = track.displayArtist.ifBlank { track.user?.username ?: "" }
                val query = "$cleanTitle $artistName audio"

                Log.d(TAG, "[NewPipe] Searching for: $query")

                val youtubeService = ServiceList.YouTube
                val searchInfo = SearchInfo.getInfo(youtubeService, youtubeService.searchQHFactory.fromQuery(query, listOf("videos"), ""))
                val videoResults = searchInfo.relatedItems.filterIsInstance<StreamInfoItem>()

                if (videoResults.isEmpty()) {
                    Log.w(TAG, "[NewPipe] No results found.")
                    return null
                }

                // The first hit was taken blindly, and that is how a twenty-second clip comes to stand in
                // for a three-minute song: YouTube's top result for a track name is frequently a snippet, a
                // teaser or a "sped up" edit. A substitute has to be the same length as the thing it
                // replaces (issue #33).
                val wantedSec = (track.durationMs ?: 0L) / 1000
                val firstResultUrl = if (wantedSec <= 0) {
                    videoResults.first().url
                } else {
                    val matched = videoResults
                        .filter { it.duration > 0 && kotlin.math.abs(it.duration - wantedSec) <= DURATION_TOLERANCE_SEC }
                        .minByOrNull { kotlin.math.abs(it.duration - wantedSec) }
                    if (matched == null) {
                        Log.w(
                            TAG,
                            "[NewPipe] no result within ${DURATION_TOLERANCE_SEC}s of ${wantedSec}s for " +
                                    "'${track.title}' — refusing to substitute a different song"
                        )
                        return null
                    }
                    matched.url
                }
                Log.d(TAG, "[NewPipe] Found match: $firstResultUrl")

                val extractor = youtubeService.getStreamExtractor(firstResultUrl)
                extractor.fetchPage()

                try {
                    Log.d(TAG, "[NewPipe] Video name: ${extractor.name}, length: ${extractor.length}s")
                } catch (e: Exception) {
                    Log.w(TAG, "[NewPipe] Could not get video name/length: ${e.message}")
                }

                val audioStreams = try {
                    extractor.audioStreams
                } catch (e: Exception) {
                    Log.w(TAG, "[NewPipe] audioStreams extraction failed: ${e.message}")
                    emptyList()
                }

                val bestAudioStream = audioStreams
                    .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.format == org.schabi.newpipe.extractor.MediaFormat.M4A && it.url != null }
                    .maxByOrNull { it.averageBitrate }
                    ?: audioStreams
                        .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.url != null }
                        .maxByOrNull { it.averageBitrate }
                    ?: audioStreams
                        .filter { it.url != null }
                        .maxByOrNull { it.averageBitrate }

                if (bestAudioStream != null) {
                    Log.d(TAG, "[NewPipe] Audio stream found: ${bestAudioStream.averageBitrate}kbps")
                    return bestAudioStream.url
                }

                Log.d(TAG, "[NewPipe] No audio-only streams, trying muxed video streams...")
                val videoStreams = try {
                    extractor.videoStreams
                } catch (e: Exception) {
                    Log.w(TAG, "[NewPipe] videoStreams extraction failed: ${e.message}")
                    emptyList()
                }

                val bestVideoStream = videoStreams
                    .filter { it.url != null }
                    .minByOrNull { 
                        it.getResolution()?.replace("p", "")?.toIntOrNull() ?: Int.MAX_VALUE
                    }

                if (bestVideoStream != null) {
                    Log.d(TAG, "[NewPipe] Using muxed video stream as audio source: ${bestVideoStream.getResolution()}, format=${bestVideoStream.format}")
                    return bestVideoStream.url
                }

                Log.w(TAG, "[NewPipe] No streams available at all (audio=${audioStreams.size}, video=${videoStreams.size})")
                null
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
                val best = extractor.audioStreams
                    .filter { it.deliveryMethod == org.schabi.newpipe.extractor.stream.DeliveryMethod.PROGRESSIVE_HTTP && it.url != null }
                    .maxByOrNull { it.averageBitrate }
                    ?: extractor.audioStreams
                        .filter { it.url != null }
                        .maxByOrNull { it.averageBitrate }
                if (best != null) return best.url

                val muxed = extractor.videoStreams
                    .filter { it.url != null }
                    .minByOrNull { it.getResolution()?.replace("p", "")?.toIntOrNull() ?: Int.MAX_VALUE }
                muxed?.url
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

            Log.d(TAG, "Track ${track.id} — ${transcodings.size} transcodings available:")
            transcodings.forEachIndexed { i, t ->
                Log.d(TAG, "  [$i] preset=${t.preset}, protocol=${t.format?.protocol}, mime=${t.format?.mimeType}, url=${t.url}")
            }
            Log.d(TAG, "Track ${track.id} — policy=${trackToUse.policy}, monetization=${trackToUse.monetizationModel}")

            val allowDrm = !forDownload || prefs.getDownloadDrmStreamsEnabled()
            val candidates = buildTranscodingCandidates(transcodings, qualityPref, forDownload, allowDrm)

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
                        } else {
                            response.close()
                            continue
                        }
                    }

                    if (!response.isSuccessful) {
                        Log.w(TAG, "Track ${track.id} — transcoding ${candidate.preset}/$protocol failed: code=${response.code}")
                        response.close()
                        continue
                    }

                    val body = response.body.string()
                    Log.d(TAG, "Track ${track.id} — stream API response: ${body.take(500)}")
                    val json = JSONObject(body)
                    val streamInfoUrl = json.getString("url")
                    val licenseAuthToken = if (json.has("licenseAuthToken") && !json.isNull("licenseAuthToken")) json.getString("licenseAuthToken") else null
                    if (!licenseAuthToken.isNullOrEmpty()) {
                        Log.d(TAG, "Track ${track.id} — CENC DRM detected! licenseAuthToken=${licenseAuthToken.take(50)}...")
                    }
                    val isHlsLike = protocol == "hls" || protocol.contains("encrypted-hls")
                    if (isHlsLike) {
                        Log.d(TAG, "Track ${track.id} — HLS resolved: $streamInfoUrl (drm=${!licenseAuthToken.isNullOrEmpty()}, protocol=$protocol)")
                        return ResolvedStream(streamInfoUrl, licenseAuthToken)
                    }
                    Log.d(TAG, "Resolving progressive stream URL: $streamInfoUrl")
                    val finalRequest = okhttp3.Request.Builder().url(streamInfoUrl).build()
                    val finalResponse = client.newCall(finalRequest).execute()
                    finalResponse.body.close()

                    if (!finalResponse.isSuccessful) {
                        Log.e(TAG, "Final resolution of progressive URL failed: ${finalResponse.code}")
                        continue
                    }

                    val finalUrl = finalResponse.request.url.toString()
                    Log.d(TAG, "Final Progressive CDN URL: $finalUrl")
                    return ResolvedStream(finalUrl, licenseAuthToken)

                } catch (e: Exception) {
                    Log.e(TAG, "Track ${track.id} — exception trying ${candidate.preset}/$protocol", e)
                    continue
                }
            }

            Log.e(TAG, "Track ${track.id} — all ${candidates.size} transcoding candidates failed!")
            if (forDownload && prefs.getYouTubeFallbackEnabled()) {
                Log.w(TAG, "Falling back to NewPipe after transcoding failures")
                val url = resolveViaNewPipe(track)
                return url?.let { ResolvedStream(it) }
            }
            return null
        }
        private fun buildTranscodingCandidates(
            transcodings: List<com.alananasss.kittytune.domain.Transcoding>,
            qualityPref: String,
            forDownload: Boolean,
            allowDrm: Boolean
        ): List<com.alananasss.kittytune.domain.Transcoding> {
            val candidates = mutableListOf<com.alananasss.kittytune.domain.Transcoding>()
            transcodings.find { it.format?.protocol == "progressive" }?.let { candidates.add(it) }
            if (qualityPref != "HIGH") {
                transcodings.find { it.format?.protocol == "hls" && it.format.mimeType?.contains("mpeg") == true }?.let { candidates.add(it) }
            }
            transcodings.find { it.format?.protocol == "hls" }?.let {
                if (!candidates.contains(it)) candidates.add(it)
            }

            if (allowDrm) {
                val cencPresets = listOf("aac_160k", "aac_96k", "abr_sq")
                for (preset in cencPresets) {
                    transcodings.find { it.preset == preset && it.format?.protocol == "ctr-encrypted-hls" }?.let { candidates.add(it) }
                    transcodings.find { it.preset == preset && it.format?.protocol == "cbc-encrypted-hls" }?.let { candidates.add(it) }
                }
                transcodings.filter { it.format?.protocol?.contains("encrypted") == true }.forEach {
                    if (!candidates.contains(it)) candidates.add(it)
                }
            }

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

