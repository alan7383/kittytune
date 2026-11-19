package com.alananasss.kittytune.data.cover

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Resolves Spotify Canvas videos (vertical MP4 loops) for a track without any Spotify login.
 *
 * Track ID resolution ladder:
 *  1. Spotify catalog search (TOTP -> Embed `__NEXT_DATA__` -> legacy token);
 *  2. MusicBrainz open data by track's own ISRC;
 *  3. Deezer public search -> track ISRC -> MusicBrainz ISRC (`url-rels`) -> Spotify Track ID
 *     (works without Spotify API traffic and for SoundCloud tracks that carry no ISRC);
 *  4. MusicBrainz recording search -> recording `url-rels` -> Spotify Track ID.
 */
object SpotifyCanvasProvider {

    private const val TAG = "SpotifyCanvasProvider"

    private const val WEB_PLAYER_TOKEN_URL =
        "https://open.spotify.com/get_access_token?reason=transport&productType=web_player"
    private const val TOTP_TOKEN_URL = "https://open.spotify.com/api/token"
    private const val EMBED_TOKEN_URL = "https://open.spotify.com/embed/track/4cOdK2wGLETKBW3PvgPWqT"
    private const val SEARCH_URL = "https://api.spotify.com/v1/search"
    private const val CANVAS_MIRROR_URL = "https://spotify-canva.vercel.app/api/canvas"

    private const val DEEZER_SEARCH_URL = "https://api.deezer.com/search"
    private const val DEEZER_TRACK_URL = "https://api.deezer.com/track/"
    private const val MUSICBRAINZ_ISRC_URL = "https://musicbrainz.org/ws/2/isrc/"
    private const val MUSICBRAINZ_RECORDING_URL = "https://musicbrainz.org/ws/2/recording"
    private const val MUSICBRAINZ_UA = "KittyTune/1.0 (https://github.com/alananasss/kittytune)"

    private val NEXT_DATA_REGEX = Regex(
        """<script\s+id="__NEXT_DATA__"[^>]*>(.*?)</script>""",
        setOf(RegexOption.DOT_MATCHES_ALL),
    )
    private val SPOTIFY_TRACK_ID_REGEX = Regex("""open\.spotify\.com/track/([A-Za-z0-9]{22})""")
    private val BRACKETS_REGEX = Regex("""(\[[^]]*]|\([^)]*\))""")

    private const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/135.0.0.0 Mobile Safari/537.36"

    private val resolvedCache = ConcurrentHashMap<String, String>()
    private val negativeCache = ConcurrentHashMap<String, Long>()

    private const val NEGATIVE_CACHE_TTL_MS = 60 * 1000L
    private const val MIRROR_ATTEMPTS = 3
    private const val TOKEN_FAILURES_TO_TRIP = 3
    private const val TOKEN_CIRCUIT_MS = 30 * 60 * 1000L
    private const val SEARCH_CIRCUIT_MS = 10 * 60 * 1000L

    @Volatile
    private var guestToken: String? = null
    @Volatile
    private var guestTokenExpiresAt: Long = 0L

    @Volatile
    private var tokenFailureStreak = 0
    @Volatile
    private var tokenDeadUntilMs = 0L
    @Volatile
    private var searchDeadUntilMs = 0L

    private val client = com.alananasss.kittytune.data.zapret.ZapretManager.newBuilder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    fun dropByUrl(url: String) {
        resolvedCache.entries.removeIf { it.value == url }
    }

    suspend fun resolveCanvas(
        title: String,
        artist: String,
        album: String? = null,
        durationSeconds: Int? = null,
        isrc: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val (cleanArtist, cleanTitle) = normalizeTrackQuery(artist, title)
        if (cleanTitle.isBlank() || cleanArtist.isBlank()) return@withContext null

        val key = "${cleanTitle.lowercase(Locale.ROOT)}|${cleanArtist.lowercase(Locale.ROOT)}"
        resolvedCache[key]?.let { return@withContext it }
        negativeCache[key]?.let { neg ->
            if (System.currentTimeMillis() - neg < NEGATIVE_CACHE_TTL_MS) return@withContext null
            negativeCache.remove(key)
        }

        val canvasUrl = runCatching {
            val trackId = findTrackId(cleanTitle, cleanArtist, album, durationSeconds)
                ?: findTrackIdViaMusicBrainzIsrc(isrc)
                ?: findTrackIdViaDeezerAndMusicBrainz(cleanTitle, cleanArtist, album, durationSeconds)
                ?: findTrackIdViaMusicBrainzSearch(cleanTitle, cleanArtist)
                ?: return@runCatching null
            Log.d(TAG, "Matched Spotify trackId=$trackId for '$cleanArtist - $cleanTitle'")
            fetchCanvasUrl(trackId)
        }.onFailure {
            Log.w(TAG, "resolveCanvas failed for '$cleanTitle': ${it.message}")
        }.getOrNull()

        if (canvasUrl != null) {
            resolvedCache[key] = canvasUrl
            negativeCache.remove(key)
            Log.d(TAG, "Resolved canvas for '$cleanArtist - $cleanTitle'")
        } else {
            negativeCache[key] = System.currentTimeMillis()
        }
        canvasUrl
    }

    private fun normalizeTrackQuery(rawArtist: String, rawTitle: String): Pair<String, String> {
        var artist = rawArtist.trim()
        var title = rawTitle.trim()

        val dashSplit = Regex("""\s+[-–—]\s+""").split(title, limit = 2)
        if (dashSplit.size == 2 && dashSplit[0].isNotBlank() && dashSplit[1].isNotBlank()) {
            artist = dashSplit[0].trim()
            title = dashSplit[1].trim()
        }

        val withoutBrackets = BRACKETS_REGEX.replace(title, " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        if (withoutBrackets.isNotBlank()) {
            title = withoutBrackets
        }
        return artist to title
    }

    // ── Coroutine-friendly OkHttp await ──────────────────────────────────────────────────

    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        enqueue(object : okhttp3.Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isCancelled) return
                continuation.resumeWith(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isCancelled) {
                    response.close()
                    return
                }
                continuation.resumeWith(Result.success(response))
            }
        })
        continuation.invokeOnCancellation { runCatching { cancel() } }
    }

    // ── Step 1: anonymous guest token ────────────────────────────────────────────────────

    private suspend fun guestAccessToken(forceRefresh: Boolean = false): String? {
        if (!forceRefresh) {
            val cached = guestToken
            if (cached != null && System.currentTimeMillis() < guestTokenExpiresAt - 60_000L) return cached
        }
        if (System.currentTimeMillis() < tokenDeadUntilMs) {
            return null
        }
        val token = runCatching { fetchTotpToken() }.getOrNull()
            ?: runCatching { fetchEmbedToken() }.getOrNull()
            ?: runCatching { fetchLegacyToken() }.getOrNull()
        if (token == null) {
            tokenFailureStreak++
            if (tokenFailureStreak >= TOKEN_FAILURES_TO_TRIP) {
                tokenDeadUntilMs = System.currentTimeMillis() + TOKEN_CIRCUIT_MS
                tokenFailureStreak = 0
                Log.w(
                    TAG,
                    "Anonymous token endpoints failed $TOKEN_FAILURES_TO_TRIP times in a row; " +
                        "Spotify token step off for ${TOKEN_CIRCUIT_MS / 60_000L} min",
                )
            }
        } else {
            tokenFailureStreak = 0
        }
        return token
    }

    private suspend fun fetchTotpToken(): String? {
        val code = SpotifyTotp.code()
        val url = TOTP_TOKEN_URL.toHttpUrl().newBuilder()
            .addQueryParameter("reason", "init")
            .addQueryParameter("productType", "web-player")
            .addQueryParameter("totp", code)
            .addQueryParameter("totpServer", code)
            .addQueryParameter("totpVer", SpotifyTotp.VERSION.toString())
            .build()
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "*/*")
                .header("Referer", "https://open.spotify.com/")
                .get()
                .build()
        ).await()
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val token = json.optString("accessToken").takeIf { it.isNotBlank() } ?: return null
            val expiresAt = json.optLong("accessTokenExpirationTimestampMs", 0L)
            guestToken = token
            guestTokenExpiresAt = if (expiresAt > 0L) expiresAt else System.currentTimeMillis() + 3_600_000L
            return token
        }
    }

    private suspend fun fetchEmbedToken(): String? {
        val response = client.newCall(
            Request.Builder()
                .url(EMBED_TOKEN_URL)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml")
                .get()
                .build()
        ).await()
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val html = resp.body?.string() ?: return null
            val jsonText = NEXT_DATA_REGEX.find(html)?.groupValues?.getOrNull(1) ?: return null
            val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
            val session = root.optJSONObject("props")
                ?.optJSONObject("pageProps")
                ?.optJSONObject("state")
                ?.optJSONObject("settings")
                ?.optJSONObject("session")
                ?: return null
            val token = session.optString("accessToken").takeIf { it.isNotBlank() } ?: return null
            val expiresAt = session.optLong("accessTokenExpirationTimestampMs", 0L)
            guestToken = token
            guestTokenExpiresAt = if (expiresAt > 0L) expiresAt else System.currentTimeMillis() + 30 * 60_000L
            return token
        }
    }

    private suspend fun fetchLegacyToken(): String? {
        val response = client.newCall(
            Request.Builder()
                .url(WEB_PLAYER_TOKEN_URL)
                .header("User-Agent", BROWSER_USER_AGENT)
                .header("Accept", "application/json")
                .header("App-Platform", "WebPlayer")
                .get()
                .build()
        ).await()
        response.use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return null
            val token = json.optString("accessToken").takeIf { it.isNotBlank() } ?: return null
            val expiresAt = json.optLong("accessTokenExpirationTimestampMs", 0L)
            guestToken = token
            guestTokenExpiresAt = if (expiresAt > 0L) expiresAt else System.currentTimeMillis() + 3_600_000L
            return token
        }
    }

    // ── Step 2: Spotify search + Deezer ISRC + MusicBrainz fallbacks ─────────────────────

    private suspend fun findTrackId(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int?,
        allowTokenRetry: Boolean = true,
    ): String? {
        if (System.currentTimeMillis() < searchDeadUntilMs) return null
        val token = guestAccessToken(forceRefresh = !allowTokenRetry) ?: return null

        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", "$title $artist")
            .addQueryParameter("type", "track")
            .addQueryParameter("limit", "10")
            .build()

        val response = client.newCall(
            Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("User-Agent", BROWSER_USER_AGENT)
                .get()
                .build()
        ).await()

        var retryAfter401 = false
        val matchedId = response.use { resp ->
            if (resp.code == 401 && allowTokenRetry) {
                guestToken = null
                guestTokenExpiresAt = 0L
                retryAfter401 = true
                return@use null
            }
            if (!resp.isSuccessful) {
                if (resp.code == 429 || resp.code == 401 || resp.code == 403) {
                    val retryAfterSec = resp.header("Retry-After")?.toLongOrNull()
                        ?.coerceIn(60L, 3_600L)
                        ?: SEARCH_CIRCUIT_MS / 1000L
                    searchDeadUntilMs = System.currentTimeMillis() + retryAfterSec * 1000L
                    Log.w(
                        TAG,
                        "Catalog search rejected (HTTP ${resp.code}); " +
                            "canvas search off for ${retryAfterSec / 60L} min",
                    )
                }
                return@use null
            }
            val body = resp.body?.string() ?: return@use null
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return@use null
            val items = json.optJSONObject("tracks")?.optJSONArray("items") ?: return@use null

            var bestId: String? = null
            var bestScore = 0.0
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val foundTitle = item.optString("name")
                val foundDurationMs = item.optLong("duration_ms", 0L)
                val artists = item.optJSONArray("artists") ?: continue
                val artistNames = (0 until artists.length()).mapNotNull { j ->
                    artists.optJSONObject(j)?.optString("name")?.takeIf { it.isNotBlank() }
                }
                val foundArtist = artistNames.joinToString(", ")
                val foundAlbum = item.optJSONObject("album")?.optString("name")
                val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue

                if (matches(
                        wantedTitle = title,
                        wantedArtist = artist,
                        wantedAlbum = album,
                        wantedDurationMs = durationSeconds?.let { it * 1000L },
                        foundTitle = foundTitle,
                        foundArtist = foundArtist,
                        foundAlbum = foundAlbum,
                        foundDurationMs = foundDurationMs,
                    )
                ) {
                    val score = wordSimilarity(title, foundTitle)
                    if (score > bestScore) {
                        bestScore = score
                        bestId = id
                    }
                }
            }
            bestId
        }

        return if (retryAfter401) {
            findTrackId(title, artist, album, durationSeconds, allowTokenRetry = false)
        } else {
            matchedId
        }
    }

    private suspend fun findTrackIdViaMusicBrainzIsrc(isrc: String?): String? {
        val cleanIsrc = isrc?.trim()?.replace("-", "")?.uppercase(Locale.ROOT)
            ?.takeIf { it.length == 12 } ?: return null
        val url = "$MUSICBRAINZ_ISRC_URL$cleanIsrc?inc=url-rels&fmt=json"
        repeat(2) {
            val response = client.newCall(
                Request.Builder()
                    .url(url)
                    .header("User-Agent", MUSICBRAINZ_UA)
                    .get()
                    .build()
            ).await()
            response.use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string()
                    return SPOTIFY_TRACK_ID_REGEX.find(body.orEmpty())?.groupValues?.getOrNull(1)
                }
                if (resp.code != 503 && resp.code != 429) return null
            }
            delay(1_000L)
        }
        return null
    }

    /**
     * Resolves an official ISRC via Deezer's open API (no keys, no OAuth), then maps that ISRC
     * to a Spotify track ID via MusicBrainz open data.
     */
    private suspend fun findTrackIdViaDeezerAndMusicBrainz(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Int?,
    ): String? {
        val searchUrl = DEEZER_SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", "$artist $title")
            .addQueryParameter("limit", "6")
            .build()

        val deezerTrackId = client.newCall(
            Request.Builder()
                .url(searchUrl)
                .header("User-Agent", BROWSER_USER_AGENT)
                .get()
                .build()
        ).await().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            val data = runCatching { JSONObject(body).optJSONArray("data") }.getOrNull()
                ?: return@use null

            var bestId: Long? = null
            var bestScore = 0.0
            for (i in 0 until data.length()) {
                val item = data.optJSONObject(i) ?: continue
                val id = item.optLong("id", 0L).takeIf { it > 0L } ?: continue
                val foundTitle = item.optString("title")
                val foundArtist = item.optJSONObject("artist")?.optString("name").orEmpty()
                val foundAlbum = item.optJSONObject("album")?.optString("title")
                val foundDurationMs = item.optLong("duration", 0L) * 1000L

                if (matches(
                        wantedTitle = title,
                        wantedArtist = artist,
                        wantedAlbum = album,
                        wantedDurationMs = durationSeconds?.let { it * 1000L },
                        foundTitle = foundTitle,
                        foundArtist = foundArtist,
                        foundAlbum = foundAlbum,
                        foundDurationMs = foundDurationMs,
                    )
                ) {
                    val score = wordSimilarity(title, foundTitle)
                    if (score > bestScore) {
                        bestScore = score
                        bestId = id
                    }
                }
            }
            bestId
        } ?: return null

        val isrc = client.newCall(
            Request.Builder()
                .url("$DEEZER_TRACK_URL$deezerTrackId")
                .header("User-Agent", BROWSER_USER_AGENT)
                .get()
                .build()
        ).await().use { resp ->
            if (!resp.isSuccessful) return@use null
            val body = resp.body?.string() ?: return@use null
            runCatching { JSONObject(body).optString("isrc") }.getOrNull()
                ?.takeIf { it.isNotBlank() }
        } ?: return null

        return findTrackIdViaMusicBrainzIsrc(isrc)
    }

    /**
     * Last-resort open-data lookup when no ISRC is linked: search MusicBrainz recordings by
     * title + artist and inspect the top recording's `url-rels` for an open.spotify.com/track link.
     */
    private suspend fun findTrackIdViaMusicBrainzSearch(title: String, artist: String): String? {
        val safeTitle = title.replace("\"", "")
        val safeArtist = artist.replace("\"", "")
        val searchUrl = MUSICBRAINZ_RECORDING_URL.toHttpUrl().newBuilder()
            .addQueryParameter("query", "recording:\"$safeTitle\" AND artist:\"$safeArtist\"")
            .addQueryParameter("limit", "3")
            .addQueryParameter("fmt", "json")
            .build()

        val mbids = client.newCall(
            Request.Builder()
                .url(searchUrl)
                .header("User-Agent", MUSICBRAINZ_UA)
                .get()
                .build()
        ).await().use { resp ->
            if (!resp.isSuccessful) return@use emptyList()
            val body = resp.body?.string() ?: return@use emptyList()
            val recordings = runCatching { JSONObject(body).optJSONArray("recordings") }.getOrNull()
                ?: return@use emptyList()
            (0 until recordings.length()).mapNotNull { i ->
                recordings.optJSONObject(i)?.optString("id")?.takeIf { it.isNotBlank() }
            }
        }

        for (mbid in mbids.take(2)) {
            val relUrl = "$MUSICBRAINZ_RECORDING_URL/$mbid?inc=url-rels&fmt=json"
            val found = client.newCall(
                Request.Builder()
                    .url(relUrl)
                    .header("User-Agent", MUSICBRAINZ_UA)
                    .get()
                    .build()
            ).await().use { resp ->
                if (!resp.isSuccessful) return@use null
                val body = resp.body?.string() ?: return@use null
                SPOTIFY_TRACK_ID_REGEX.find(body)?.groupValues?.getOrNull(1)
            }
            if (found != null) return found
        }
        return null
    }

    // ── Step 3: canvas MP4 for the matched track ─────────────────────────────────────────

    private sealed class MirrorResult {
        data class Found(val url: String) : MirrorResult()
        data object NoCanvas : MirrorResult()
        data object TransientError : MirrorResult()
    }

    private suspend fun fetchCanvasUrl(trackId: String): String? {
        if (!trackId.matches(Regex("[A-Za-z0-9]{22}"))) return null
        val url = CANVAS_MIRROR_URL.toHttpUrl().newBuilder()
            .addQueryParameter("trackId", trackId)
            .build()
        repeat(MIRROR_ATTEMPTS) { attempt ->
            when (val result = runCatching { fetchCanvasUrlOnce(url, trackId) }.getOrDefault(MirrorResult.TransientError)) {
                is MirrorResult.Found -> return result.url
                MirrorResult.NoCanvas -> return null
                MirrorResult.TransientError -> {
                    if (attempt < MIRROR_ATTEMPTS - 1) delay(400L * (attempt + 1))
                }
            }
        }
        return null
    }

    private suspend fun fetchCanvasUrlOnce(url: HttpUrl, trackId: String): MirrorResult {
        val response = client.newCall(
            Request.Builder()
                .url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .get()
                .build()
        ).await()
        response.use { resp ->
            if (!resp.isSuccessful) {
                Log.w(TAG, "Canvas mirror HTTP ${resp.code} for trackId=$trackId")
                return if (resp.code == 404) MirrorResult.NoCanvas else MirrorResult.TransientError
            }
            val body = resp.body?.string() ?: return MirrorResult.TransientError
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return MirrorResult.TransientError
            val canvases: JSONArray = json.optJSONArray("canvasesList")
                ?: json.optJSONObject("data")?.optJSONArray("canvasesList")
                ?: return MirrorResult.NoCanvas

            var fallbackUrl: String? = null
            for (i in 0 until canvases.length()) {
                val entry = canvases.optJSONObject(i) ?: continue
                val candidate = entry.optString("canvasUrl").takeIf { it.isNotBlank() } ?: continue
                if (!isTrustedMedia(candidate)) continue
                val uri = entry.optString("trackUri")
                if (uri.equals("spotify:track:$trackId", ignoreCase = true)) {
                    return MirrorResult.Found(candidate)
                }
                if (fallbackUrl == null && uri.isBlank()) fallbackUrl = candidate
            }
            return fallbackUrl?.let { MirrorResult.Found(it) } ?: MirrorResult.NoCanvas
        }
    }

    private fun isTrustedMedia(url: String): Boolean {
        val parsed = runCatching { url.toHttpUrl() }.getOrNull() ?: return false
        if (parsed.scheme != "https") return false
        val host = parsed.host
        return host == "scdn.co" || host.endsWith(".scdn.co")
    }

    // ── Matching helpers ─────────────────────────────────────────────────────────────────

    private fun wordSet(value: String?): Set<String> =
        Normalizer.normalize(value.orEmpty(), Normalizer.Form.NFKD)
            .replace(Regex("\\p{M}+"), "")
            .lowercase(Locale.ROOT)
            .split(Regex("[^\\p{L}\\p{N}]+"))
            .filter { it.isNotBlank() && it !in NOISE_WORDS }
            .toSet()

    private fun wordSimilarity(left: String, right: String): Double {
        val a = wordSet(left)
        val b = wordSet(right)
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.intersect(b).size.toDouble()
        return common / maxOf(a.size, b.size)
    }

    private fun matches(
        wantedTitle: String,
        wantedArtist: String,
        wantedAlbum: String?,
        wantedDurationMs: Long?,
        foundTitle: String,
        foundArtist: String,
        foundAlbum: String?,
        foundDurationMs: Long,
    ): Boolean {
        val wantedArtistWords = wordSet(wantedArtist)
        val foundArtistWords = wordSet(foundArtist)
        if (wantedArtistWords.isEmpty() || foundArtistWords.isEmpty()) return false
        val primaryWanted = wantedArtistWords.firstOrNull() ?: return false
        val artistOk = wantedArtistWords.intersect(foundArtistWords).isNotEmpty() ||
            foundArtistWords.contains(primaryWanted)
        if (!artistOk) return false
        if (wordSimilarity(wantedTitle, foundTitle) < 0.65) return false
        val durationOk = wantedDurationMs == null || wantedDurationMs <= 0 ||
            foundDurationMs <= 0 || kotlin.math.abs(wantedDurationMs - foundDurationMs) <= 15_000L
        return durationOk
    }

    private val NOISE_WORDS = setOf(
        "feat", "ft", "featuring", "official", "video", "audio",
        "music", "prod", "by", "explicit", "clean", "remastered", "single",
    )
}

private object SpotifyTotp {

    private const val SECRET = ",7/*F(\"rLJ2oxaKL^f+E1xvP@N"
    const val VERSION = 61
    private const val PERIOD_SECONDS = 30L

    fun code(nowEpochMillis: Long = System.currentTimeMillis()): String {
        val bytes = SECRET.mapIndexed { index, character -> character.code xor (index % 33 + 9) }
        val raw = bytes.joinToString("").toByteArray(Charsets.UTF_8)
        return hotp(raw, nowEpochMillis / 1000 / PERIOD_SECONDS)
    }

    private fun hotp(key: ByteArray, counter: Long): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA1")
        mac.init(javax.crypto.spec.SecretKeySpec(key, "HmacSHA1"))
        val data = ByteArray(8)
        for (index in 0 until 8) data[7 - index] = ((counter shr (8 * index)) and 0xFF).toByte()
        val digest = mac.doFinal(data)
        val offset = digest[digest.size - 1].toInt() and 0x0F
        val binary =
            ((digest[offset].toInt() and 0x7F) shl 24) or
                ((digest[offset + 1].toInt() and 0xFF) shl 16) or
                ((digest[offset + 2].toInt() and 0xFF) shl 8) or
                (digest[offset + 3].toInt() and 0xFF)
        return (binary % 1_000_000).toString().padStart(6, '0')
    }
}
