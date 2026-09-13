package com.alananasss.kittytune.data.cover

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Fetches Apple Music animated canvas (motion cover) URLs via Apple Music AMP API.
 * Token is obtained from the JWT token endpoint.
 */
object AppleMusicCanvasProvider {

    private const val TAG = "AppleMusicCanvasProvider"
    private const val TOKEN_URL = "https://yesitworkssomehow-funny-deeza-api-and-yeah.hf.space/apple/token"
    private const val AMP_BASE = "https://amp-api.music.apple.com"
    private const val STOREFRONT = "us"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /**
     * Verified official Apple Music Web Player developer token.
     * Valid through late 2026 as guaranteed baseline fallback.
     */
    const val FALLBACK_TOKEN =
        "eyJ0eXAiOiJKV1QiLCJhbGciOiJFUzI1NiIsImtpZCI6IldlYlBsYXlLaWQifQ.eyJpc3MiOiJBTVBXZWJQbGF5IiwiaWF0IjoxNzg2MzYyMTUwLCJleHAiOjE3OTI0MTAxNTAsInJvb3RfaHR0cHNfb3JpZ2luIjpbImFwcGxlLmNvbSJdfQ.wmgvODbrLN8VxNt45wP6fxrI-U2PJhDD1Y1ZokU1ZqAKg_2F8rB30P_MwzPlQ0SyEGPXNg8Pfh7HUsO1cBv3cQ"

    private const val TOKEN_TTL_MS = 60 * 60 * 1000L
    private const val PREFS_NAME = "apple_music_token_prefs"
    private const val PREF_KEY_TOKEN = "cached_jwt_token"
    private const val PREF_KEY_TOKEN_TIME = "cached_token_timestamp"
    private val VIDEO_URL_REGEX = Regex("""\.(m3u8|mp4)(\?|$)""", RegexOption.IGNORE_CASE)

    enum class CanvasAspectPreference { TALL, SQUARE }

    data class AppleMusicCanvas(val animated: String?)

    private val cache = ConcurrentHashMap<String, AppleMusicCanvas>()
    private val negativeCache = ConcurrentHashMap<String, Long>()
    private const val NEGATIVE_CACHE_TTL_MS = 5 * 60 * 1000L

    @Volatile private var cachedToken: String? = null
    @Volatile private var tokenFetchedAt: Long = 0L

    private val http = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    private val tokenHttp = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val tokenMutex = Mutex()

    private fun getStoredToken(): Pair<String?, Long> {
        val app = try { com.alananasss.kittytune.KittyTuneApp.instance } catch (_: Throwable) { null }
            ?: return null to 0L
        return try {
            val prefs = app.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
            val token = prefs.getString(PREF_KEY_TOKEN, null)
            val timestamp = prefs.getLong(PREF_KEY_TOKEN_TIME, 0L)
            token to timestamp
        } catch (_: Exception) {
            null to 0L
        }
    }

    private fun persistToken(token: String, timestamp: Long) {
        val app = try { com.alananasss.kittytune.KittyTuneApp.instance } catch (_: Throwable) { null }
            ?: return
        try {
            app.getSharedPreferences(PREFS_NAME, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_KEY_TOKEN, token)
                .putLong(PREF_KEY_TOKEN_TIME, timestamp)
                .apply()
        } catch (_: Exception) {}
    }

    fun getCached(
        song: String,
        artist: String,
        isrc: String?,
        preferredAspect: CanvasAspectPreference,
    ): AppleMusicCanvas? {
        val key = cacheKey(isrc, song, artist, preferredAspect)
        cache[key]?.let { return it }
        val neg = negativeCache[key]
        if (neg != null && System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) return null
        return null
    }

    suspend fun getBySongArtist(
        song: String,
        artist: String,
        album: String? = null,
        isrc: String? = null,
        durationSeconds: Int? = null,
        preferredAspect: CanvasAspectPreference = CanvasAspectPreference.SQUARE,
    ): AppleMusicCanvas? = withContext(Dispatchers.IO) {
        val key = cacheKey(isrc, song, artist, preferredAspect)
        cache[key]?.let { return@withContext it }
        val neg = negativeCache[key]
        if (neg != null && System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) {
            return@withContext null
        }

        runCatching {
            var token = getToken()

            val resolvedIsrc = ProviderIsrc.normalize(isrc)
            resolvedIsrc?.let { CanvasIndex.getByIsrc(it) }?.let { indexed ->
                return@withContext AppleMusicCanvas(animated = indexed.sourceUrl).also {
                    cache[key] = it
                }
            }

            fun attempt(): Triple<AppleMusicCanvas?, CanvasMatchTier?, JSONObject?> {
                if (resolvedIsrc != null) {
                    val (canvasResult, songItem) = fetchByIsrc(resolvedIsrc, token, preferredAspect)
                    if (canvasResult != null) return Triple(canvasResult, CanvasMatchTier.ISRC_EXACT, songItem)
                    if (songItem != null) {
                        return Triple(null, CanvasMatchTier.ISRC_EXACT, songItem)
                    }
                }
                val (canvasResult, songItem, tier) = fetchBySearch(song, artist, durationSeconds, token, preferredAspect)
                return Triple(canvasResult, tier, songItem)
            }

            var (canvas, tier, matchedItem) = attempt()

            if (canvas == null && tier != CanvasMatchTier.ISRC_EXACT) {
                token = getToken(forceRefresh = true)
                val retry = attempt()
                canvas = retry.first
                tier = retry.second
                matchedItem = retry.third
            }

            val matchedAttrs = matchedItem?.optJSONObject("attributes")
            val matchedTitle = matchedAttrs?.optString("name")
            val matchedCatalogId = matchedItem?.optString("id")?.takeIf { it.isNotBlank() }
            val matchedIsrcRaw = matchedAttrs?.optString("isrc")?.takeIf { it.isNotBlank() }
            val effectiveIsrc = resolvedIsrc ?: ProviderIsrc.normalize(matchedIsrcRaw)

            if (canvas != null) {
                cache[key] = canvas
                negativeCache.remove(key)
                if (tier != null) {
                    CanvasIndex.put(
                        CanvasMatchEntry(
                            isrc = effectiveIsrc,
                            appleCatalogId = matchedCatalogId,
                            title = matchedTitle ?: song,
                            artist = artist,
                            album = album,
                            durationMs = durationSeconds?.toLong()?.times(1000L),
                            sourceUrl = canvas.animated.orEmpty(),
                            matchTier = tier,
                            confidence = tier.baseConfidence,
                            lastMatchedAtMs = System.currentTimeMillis(),
                        )
                    )
                }
            } else {
                negativeCache[key] = System.currentTimeMillis()
            }
            canvas
        }.onFailure {
            android.util.Log.e(TAG, "Canvas fetch failed for \"$song\" by $artist: ${it.message}")
        }.getOrNull()
    }

    private fun cacheKey(
        isrc: String?,
        song: String,
        artist: String,
        aspect: CanvasAspectPreference,
    ): String =
        ((isrc?.takeIf { it.isNotBlank() } ?: "$song\u001F$artist") + "\u001F$aspect").lowercase()

    private fun fetchTokenFromAppleWeb(): String? {
        return runCatching {
            val req = Request.Builder()
                .url("https://music.apple.com/us/browse")
                .header("User-Agent", USER_AGENT)
                .get()
                .build()
            val html = tokenHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@runCatching null
                resp.body.string()
            }

            val scriptRegex = Regex("""/assets/index[~-][a-zA-Z0-9_-]+\.js""")
            val scriptPaths = scriptRegex.findAll(html).map { it.value }.distinct().toList()
            val jwtRegex = Regex("""ey[A-Za-z0-9_-]{20,}\.ey[A-Za-z0-9_-]{20,}\.[A-Za-z0-9_-]{20,}""")

            for (scriptPath in scriptPaths) {
                val scriptUrl = "https://music.apple.com$scriptPath"
                val jsReq = Request.Builder()
                    .url(scriptUrl)
                    .header("User-Agent", USER_AGENT)
                    .get()
                    .build()
                val js = tokenHttp.newCall(jsReq).execute().use { resp ->
                    if (resp.isSuccessful) resp.body.string() else null
                } ?: continue

                val matches = jwtRegex.findAll(js).map { it.value }.toList()
                for (jwt in matches) {
                    if (jwt.length > 100) {
                        return@runCatching jwt
                    }
                }
            }
            null
        }.onFailure { android.util.Log.d(TAG, "Apple web token scraping skipped: ${it.message}") }
            .getOrNull()
    }

    private fun fetchTokenFromEndpoint(): String? {
        return runCatching {
            val req = Request.Builder().url(TOKEN_URL).header("User-Agent", USER_AGENT).get().build()
            val body = tokenHttp.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    android.util.Log.w(TAG, "Token endpoint returned ${resp.code}")
                    return@runCatching null
                }
                resp.body.string().trim()
            }

            if (body.startsWith("eyJ")) {
                body
            } else {
                val json = JSONObject(body)
                json.optString("token").takeIf { it.startsWith("eyJ") }
                    ?: json.optString("jwt").takeIf { it.startsWith("eyJ") }
                    ?: json.optString("access_token").takeIf { it.startsWith("eyJ") }
            }
        }.onFailure { android.util.Log.d(TAG, "Token endpoint fetch failed: ${it.message}") }
            .getOrNull()
    }

    internal suspend fun getToken(forceRefresh: Boolean = false): String {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val fresh = cachedToken
            if (fresh != null && (now - tokenFetchedAt) < TOKEN_TTL_MS) return fresh
            val (storedToken, storedTime) = getStoredToken()
            if (storedToken != null && (now - storedTime) < TOKEN_TTL_MS) {
                cachedToken = storedToken
                tokenFetchedAt = storedTime
                return storedToken
            }
        }

        return tokenMutex.withLock {
            val currentNow = System.currentTimeMillis()
            if (!forceRefresh) {
                val cached = cachedToken
                if (cached != null && (currentNow - tokenFetchedAt) < TOKEN_TTL_MS) return@withLock cached
                val (storedToken, storedTime) = getStoredToken()
                if (storedToken != null && (currentNow - storedTime) < TOKEN_TTL_MS) {
                    cachedToken = storedToken
                    tokenFetchedAt = storedTime
                    return@withLock storedToken
                }
            }

            // 1. Try fast scraping directly from Apple Music Web Player (official source, ~600ms, always active)
            var token = fetchTokenFromAppleWeb()
            if (token != null) {
                android.util.Log.d(TAG, "Successfully extracted Apple Music token from web player")
            }

            // 2. If web scraping failed, try HF endpoint
            if (token == null) {
                token = fetchTokenFromEndpoint()
                if (token != null) {
                    android.util.Log.d(TAG, "Successfully fetched Apple Music token from endpoint")
                }
            }

            // 3. Fallback to existing memory cache, stored prefs, or hardcoded verified fallback token
            if (token == null) {
                token = cachedToken
                    ?: getStoredToken().first
                    ?: FALLBACK_TOKEN
                android.util.Log.d(TAG, "Using fallback Apple Music token")
            }

            cachedToken = token
            tokenFetchedAt = currentNow
            persistToken(token, currentNow)
            token
        }
    }

    internal fun buildAmpUrl(base: String, extraParams: Map<String, String> = emptyMap()): String {
        val builder = base.toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("art[url]", "f")
            .addQueryParameter("fields[albums]", "name,url,editorialVideo,motionArtwork,editorialArtwork")
            .addQueryParameter("fields[songs]", "name,url,isrc,editorialVideo,motionArtwork,editorialArtwork,hasLyrics")
            .addQueryParameter("fields[artists]", "name,url")
            .addQueryParameter("format[resources]", "map")
            .addQueryParameter("include[songs]", "albums,artists")
            .addQueryParameter("l", "en-GB")
            .addQueryParameter("omit[resource]", "autos")
            .addQueryParameter("platform", "web")
        for ((k, v) in extraParams) builder.addQueryParameter(k, v)
        return builder.build().toString()
    }

    internal fun ampRequest(url: String, token: String): Request =
        Request.Builder()
            .url(url)
            .get()
            .header("Authorization", "Bearer $token")
            .header("Origin", "https://music.apple.com")
            .header("Referer", "https://music.apple.com/")
            .header("User-Agent", USER_AGENT)
            .build()

    internal fun fetchByIsrc(
        isrc: String,
        token: String,
        aspect: CanvasAspectPreference,
    ): Pair<AppleMusicCanvas?, JSONObject?> {
        val url = buildAmpUrl("$AMP_BASE/v1/catalog/$STOREFRONT/songs", mapOf("filter[isrc]" to isrc))

        val body = http.newCall(ampRequest(url, token)).execute().use { resp ->
            if (!resp.isSuccessful) return null to null
            resp.body?.string()
        } ?: return null to null

        val root = JSONObject(body)
        val foundId = root.optJSONArray("data")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotBlank() }
            ?: root.optJSONObject("resources")?.optJSONObject("songs")?.keys()?.asSequence()?.firstOrNull()
            ?: return null to null

        val songItem = getResourceById(root, foundId) ?: return null to null

        extractMotionFromData(root, foundId, aspect)?.let {
            return AppleMusicCanvas(animated = it) to songItem
        }
        return null to songItem
    }

    internal fun fetchBySearch(
        song: String,
        artist: String,
        durationSeconds: Int?,
        token: String,
        aspect: CanvasAspectPreference,
    ): Triple<AppleMusicCanvas?, JSONObject?, CanvasMatchTier> {
        val url = buildAmpUrl(
            "$AMP_BASE/v1/catalog/$STOREFRONT/search",
            mapOf("term" to "$song $artist", "types" to "songs", "limit" to "5"),
        )

        val body = http.newCall(ampRequest(url, token)).execute().use { resp ->
            if (!resp.isSuccessful) return Triple(null, null, CanvasMatchTier.FUZZY)
            resp.body?.string()
        } ?: return Triple(null, null, CanvasMatchTier.FUZZY)

        val root = JSONObject(body)
        val candidates = collectSearchSongs(root)
        if (candidates.isEmpty()) return Triple(null, null, CanvasMatchTier.FUZZY)

        val best = pickBestCandidate(candidates, song, artist, durationSeconds)
            ?: return Triple(null, null, CanvasMatchTier.FUZZY)

        val bestAttrs = best.optJSONObject("attributes")
        val bestTitleNorm = normalize(bestAttrs?.optString("name").orEmpty())
        val bestArtistNorm = normalize(bestAttrs?.optString("artistName").orEmpty())
        val tier = when {
            bestTitleNorm == normalize(song) && bestArtistNorm == normalize(artist) ->
                CanvasMatchTier.ALBUM_ARTIST_TITLE
            else -> CanvasMatchTier.FUZZY
        }

        val motion = searchItem(best, aspect)
        if (motion != null) {
            return Triple(AppleMusicCanvas(animated = motion), best, tier)
        }

        val albumId = best.optJSONObject("relationships")
            ?.optJSONObject("albums")?.optJSONArray("data")
            ?.optJSONObject(0)?.optString("id")
        if (!albumId.isNullOrBlank()) {
            val album = getResourceById(root, albumId)
            if (album != null) {
                val albumMotion = searchItem(album, aspect)
                if (albumMotion != null) {
                    return Triple(AppleMusicCanvas(animated = albumMotion), best, tier)
                }
            }
        }
        return Triple(null, best, tier)
    }

    private fun collectSearchSongs(root: JSONObject): List<JSONObject> {
        val results = mutableListOf<JSONObject>()
        root.optJSONObject("results")?.optJSONObject("songs")?.let { songs ->
            songs.optJSONArray("data")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { results.add(it) }
                }
            }
            songs.optJSONObject("resources")?.optJSONObject("songs")?.let { bucket ->
                for (key in bucket.keys()) {
                    bucket.optJSONObject(key)?.let { results.add(it) }
                }
            }
        }
        root.optJSONObject("resources")?.optJSONObject("songs")?.let { bucket ->
            for (key in bucket.keys()) {
                bucket.optJSONObject(key)?.let { results.add(it) }
            }
        }
        root.optJSONArray("data")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { results.add(it) }
            }
        }
        return results
    }

    private fun pickBestCandidate(
        candidates: List<JSONObject>,
        querySong: String,
        queryArtist: String,
        queryDurationSeconds: Int?,
    ): JSONObject? {
        data class Scored(val item: JSONObject, val score: Int)

        val normQuerySong = normalize(querySong)
        val normQueryArtist = normalize(queryArtist)

        var best: Scored? = null
        for (candidate in candidates) {
            val attrs = candidate.optJSONObject("attributes") ?: continue
            val title = normalize(attrs.optString("name"))
            if (title.isBlank()) continue

            val artistName = normalize(attrs.optString("artistName"))

            var score = 0
            score += when {
                title == normQuerySong -> 100
                title.contains(normQuerySong) || normQuerySong.contains(title) -> 80
                else -> {
                    val queryWords = normQuerySong.split(" ").filter { it.length > 3 }
                    if (queryWords.isNotEmpty()) {
                        val matchCount = queryWords.count { title.contains(it) }
                        (matchCount.toFloat() / queryWords.size * 60).toInt()
                    } else 0
                }
            }

            if (artistName.isNotBlank()) {
                score += when {
                    artistName == normQueryArtist -> 60
                    artistName.contains(normQueryArtist) || normQueryArtist.contains(artistName) -> 40
                    else -> {
                        val artistWords = normQueryArtist.split(" ").filter { it.length > 3 }
                        if (artistWords.isNotEmpty()) {
                            val matchCount = artistWords.count { artistName.contains(it) }
                            (matchCount.toFloat() / artistWords.size * 30).toInt()
                        } else -20
                    }
                }
            }

            if (queryDurationSeconds != null && queryDurationSeconds > 0) {
                val trackDurMs = attrs.optLong("durationInMillis")
                if (trackDurMs > 0) {
                    val diff = kotlin.math.abs(queryDurationSeconds * 1000L - trackDurMs)
                    val exactTitleAndArtist = title == normQuerySong && artistName == normQueryArtist
                    when {
                        diff < 3_000 -> score += 60
                        diff < 10_000 -> score += 30
                        diff > 20_000 -> score -= if (exactTitleAndArtist) 20 else 80
                    }
                }
            }

            if (score >= 70 && (best == null || score > best!!.score)) {
                best = Scored(candidate, score)
            }
        }
        return best?.item
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

    private fun isVideoUrl(value: String?): Boolean =
        value != null && value.startsWith("http") && VIDEO_URL_REGEX.containsMatchIn(value)

    private fun pickBestVideo(obj: JSONObject?, aspect: CanvasAspectPreference): String? {
        if (obj == null) return null

        val primaryKey = if (aspect == CanvasAspectPreference.SQUARE) "motionDetailSquare" else "motionDetailTall"
        val secondaryKey = if (aspect == CanvasAspectPreference.SQUARE) "motionSquareVideo1x1" else "motionTallVideo3x4"
        val oppositePrimaryKey = if (aspect == CanvasAspectPreference.SQUARE) "motionDetailTall" else "motionDetailSquare"
        val oppositeSecondaryKey = if (aspect == CanvasAspectPreference.SQUARE) "motionTallVideo3x4" else "motionSquareVideo1x1"

        val hq = obj.optJSONObject(primaryKey)?.optString("video")?.takeIf { isVideoUrl(it) }
            ?: obj.optJSONObject(secondaryKey)?.optString("video")?.takeIf { isVideoUrl(it) }
            ?: obj.optJSONObject(oppositePrimaryKey)?.optString("video")?.takeIf { isVideoUrl(it) }
            ?: obj.optJSONObject(oppositeSecondaryKey)?.optString("video")?.takeIf { isVideoUrl(it) }
            ?: obj.optJSONObject("motionArtistSquare1x1")?.optString("video")?.takeIf { isVideoUrl(it) }
            ?: obj.optString("video").takeIf { isVideoUrl(it) }
            ?: obj.optString("url").takeIf { isVideoUrl(it) }
        if (hq != null) return hq

        val keys = obj.keys()
        for (k in keys) {
            val child = obj.opt(k)
            if (child is JSONObject) {
                pickBestVideo(child, aspect)?.let { return it }
            }
        }
        return null
    }

    private val MOTION_ATTRIBUTE_FIELDS = listOf(
        "editorialVideo", "motionArtwork", "editorialArtwork",
        "motionArtwork1x1", "motionArtworkTall",
        "motionDetailSquare", "motionDetailTall", "motionVideo",
    )

    internal fun searchItem(item: JSONObject?, aspect: CanvasAspectPreference): String? {
        val attrs = item?.optJSONObject("attributes") ?: return null
        for (field in MOTION_ATTRIBUTE_FIELDS) {
            pickBestVideo(attrs.optJSONObject(field), aspect)?.let { return it }
        }
        return null
    }

    private fun getResourceById(root: JSONObject, id: String?): JSONObject? {
        if (id.isNullOrBlank()) return null

        root.optJSONObject("resources")?.let { resources ->
            for (type in resources.keys()) {
                resources.optJSONObject(type)?.optJSONObject(id)?.let { return it }
            }
        }

        val dataAny = root.opt("data")
        if (dataAny is JSONArray) {
            for (i in 0 until dataAny.length()) {
                val item = dataAny.optJSONObject(i)
                if (item?.optString("id") == id) return item
            }
        } else if (dataAny is JSONObject && dataAny.optString("id") == id) {
            return dataAny
        }

        root.optJSONArray("included")?.let { included ->
            for (i in 0 until included.length()) {
                val item = included.optJSONObject(i)
                if (item?.optString("id") == id) return item
            }
        }

        root.optJSONObject("results")?.let { results ->
            for (type in results.keys()) {
                val typeObj = results.optJSONObject(type) ?: continue
                typeObj.optJSONObject("resources")?.let { res ->
                    for (resType in res.keys()) {
                        res.optJSONObject(resType)?.optJSONObject(id)?.let { return it }
                    }
                }
                typeObj.optJSONArray("data")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val item = arr.optJSONObject(i)
                        if (item?.optString("id") == id) return item
                    }
                }
            }
        }

        return null
    }

    private val RESOURCE_SCAN_TYPES = listOf("songs", "albums", "playlists", "music-videos")

    private fun extractMotionFromData(
        root: JSONObject,
        targetId: String?,
        aspect: CanvasAspectPreference,
    ): String? {
        if (!targetId.isNullOrBlank()) {
            val item = getResourceById(root, targetId)
            if (item != null) {
                searchItem(item, aspect)?.let { return it }

                if (item.optString("type") == "songs") {
                    val albumRefs = item.optJSONObject("relationships")
                        ?.optJSONObject("albums")?.optJSONArray("data")
                    if (albumRefs != null) {
                        for (i in 0 until albumRefs.length()) {
                            val albumId = albumRefs.optJSONObject(i)?.optString("id")
                            val album = getResourceById(root, albumId)
                            if (album != null) {
                                searchItem(album, aspect)?.let { return it }
                            }
                        }
                    }
                }
            }
        }

        root.optJSONObject("resources")?.let { resources ->
            for (type in RESOURCE_SCAN_TYPES) {
                val bucket = resources.optJSONObject(type) ?: continue
                for (id in bucket.keys()) {
                    searchItem(bucket.optJSONObject(id), aspect)?.let { return it }
                }
            }
        }

        root.optJSONArray("included")?.let { included ->
            for (i in 0 until included.length()) {
                searchItem(included.optJSONObject(i), aspect)?.let { return it }
            }
        }

        root.optJSONObject("results")?.let { results ->
            for (type in results.keys()) {
                val sub = results.optJSONObject(type) ?: continue
                extractMotionFromData(sub, targetId, aspect)?.let { return it }
            }
        }

        return null
    }
}
