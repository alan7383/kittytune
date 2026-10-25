package com.alananasss.kittytune.data.qobuz

import android.content.Context
import android.os.Build
import android.util.Log
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.search.ProviderSearchRanker
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import com.alananasss.kittytune.data.zapret.ZapretManager

data class QobuzSearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artists: List<User> = emptyList()
)

object QobuzSearchRepository {
    private const val DEFAULT_BASE = "https://qobuz-dll.vercel.app"
    private const val CATALOG_API_BASE = "https://www.qobuz.com/api.json/0.2"
    private const val CATALOG_APP_ID = "712109809"

    /**
     * The official Qobuz app identifies itself with its own id and secret. The
     * radio endpoints (radio/track, radio/album, radio/artist) and the OAuth
     * login only accept those, so they are needed to reach the real radio.
     */
    private const val OFFICIAL_APP_ID = "312369995"
    private const val OFFICIAL_APP_SECRET = "e79f8b9be485692b0e5f9dd895826368"
    private const val OFFICIAL_OAUTH_SECRET = "828193befd12dc579e9cc1d402d3be1e"

    private val client = ZapretManager.newBuilder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    private fun getBaseUrls(context: Context? = null): List<String> {
        val custom = if (context != null) runCatching { PlayerPreferences(context).getQobuzCustomInstances() }.getOrNull() else null
        val customList = custom.orEmpty()
            .split('\n', '\r', ',', ';', '\t', ' ')
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .map { if (it.contains("://")) it.removeSuffix("/") else "https://${it.removeSuffix("/")}" }

        return (customList + DEFAULT_BASE).distinct()
    }

    suspend fun search(context: Context? = null, query: String, limit: Int = 50): QobuzSearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext QobuzSearchResult()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
        val token = if (context != null) runCatching { PlayerPreferences(context).getQobuzUserAuthToken() }.getOrNull() else null

        // 1. Official Qobuz catalogue search (direct, fast, no proxy limit/402 issues)
        val catalogUrl = buildString {
            append("$CATALOG_API_BASE/catalog/search?query=$encodedQuery&limit=$limit&offset=0&app_id=$CATALOG_APP_ID")
            if (!token.isNullOrBlank()) {
                append("&user_auth_token=${URLEncoder.encode(token, "UTF-8")}")
            }
        }
        val officialRoot = fetchJson(catalogUrl, bearerToken = token)
        if (officialRoot != null) {
            val data = officialRoot.optJSONObject("data") ?: officialRoot
            val tracks = data.optJSONObject("tracks")?.optJSONArray("items")?.mapObjects { it.toQobuzTrack() } ?: emptyList()
            val albums = data.optJSONObject("albums")?.optJSONArray("items")?.mapObjects { it.toQobuzAlbum() } ?: emptyList()
            val artists = data.optJSONObject("artists")?.optJSONArray("items")?.mapObjects { it.toQobuzArtist() } ?: emptyList()
            val playlists = data.optJSONObject("playlists")?.optJSONArray("items")?.mapObjects { it.toQobuzPlaylist() } ?: emptyList()

            if (tracks.isNotEmpty() || albums.isNotEmpty() || artists.isNotEmpty() || playlists.isNotEmpty()) {
                return@withContext QobuzSearchResult(
                    tracks = ProviderSearchRanker.rankTracks(query, tracks),
                    albums = albums,
                    playlists = playlists,
                    artists = artists
                )
            }
        }

        // 2. Fallback to custom/third-party instances if configured
        val baseUrls = getBaseUrls(context)
        for (base in baseUrls) {
            val url = "$base/api/get-music?q=$encodedQuery&offset=0"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue

            val tracks = data.optJSONObject("tracks")?.optJSONArray("items")?.mapObjects { it.toQobuzTrack() } ?: emptyList()
            val albums = data.optJSONObject("albums")?.optJSONArray("items")?.mapObjects { it.toQobuzAlbum() } ?: emptyList()
            val artists = data.optJSONObject("artists")?.optJSONArray("items")?.mapObjects { it.toQobuzArtist() } ?: emptyList()
            val playlists = data.optJSONObject("playlists")?.optJSONArray("items")?.mapObjects { it.toQobuzPlaylist() } ?: emptyList()

            return@withContext QobuzSearchResult(
                tracks = ProviderSearchRanker.rankTracks(query, tracks),
                albums = albums,
                playlists = playlists,
                artists = artists
            )
        }

        QobuzSearchResult()
    }

    /**
     * Resolves one exact Qobuz track. A pasted open.qobuz.com link carries an id,
     * not a title, so searching its text would only return unrelated songs.
     *
     * The catalogue's own track/get endpoint needs an app id but no listener token;
     * it is tried first so this works even when the configured qobuz-dll instance is
     * down. Custom resolver instances are kept as a fallback for deployments that
     * expose a get-track route.
     */
    suspend fun getTrack(context: Context? = null, trackId: String): Track? = withContext(Dispatchers.IO) {
        val cleanId = trackId.removePrefix("qobuz:track:").trim()
        if (cleanId.isBlank()) return@withContext null
        val encodedId = URLEncoder.encode(cleanId, "UTF-8")

        fetchJson("$CATALOG_API_BASE/track/get?track_id=$encodedId&app_id=$CATALOG_APP_ID")?.let { root ->
            val data = root.optJSONObject("data") ?: root
            data.toQobuzTrack()?.let { return@withContext it }
        }

        for (base in getBaseUrls(context)) {
            val root = fetchJson("$base/api/get-track?track_id=$encodedId") ?: continue
            val data = root.optJSONObject("data") ?: continue
            data.toQobuzTrack()?.let { return@withContext it }
        }

        null
    }

    /**
     * Logs in to Qobuz as the official app (using the app's own app_id) and
     * stores the user_auth_token so the official radio endpoints
     * (radio/track, radio/album, radio/artist) become usable.
     *
     * Returns Result.success(token) on success, Result.failure on error with
     * the actual message returned by the Qobuz server.
     */
    suspend fun login(context: Context? = null, email: String, password: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        val cleanPassword = password
        if (cleanEmail.isBlank() || cleanPassword.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Email and password cannot be empty"))
        }

        val md5Password = md5(cleanPassword)
        val timestamp = System.currentTimeMillis() / 1000

        // 1. First attempt: official app's oauth2/login endpoint with MD5 hashed password (exact APK logic from htb.b)
        val md5Sig = officialSignature(
            path = listOf("oauth2", "login"),
            params = mapOf("username" to cleanEmail, "password" to md5Password),
            timestamp = timestamp,
        )
        val oauth2Md5Url = "$CATALOG_API_BASE/oauth2/login".toHttpUrl().newBuilder()
            .addQueryParameter("username", cleanEmail)
            .addQueryParameter("password", md5Password)
            .addQueryParameter("app_id", OFFICIAL_APP_ID)
            .addQueryParameter("request_ts", timestamp.toString())
            .addQueryParameter("request_sig", md5Sig)
            .build()

        val oauth2Md5Result = executeRequest(oauth2Md5Url.toString())
        var token = oauth2Md5Result.json?.let { extractToken(it) }
        var errorMessage = oauth2Md5Result.errorMessage

        // 2. Second attempt: raw password (fallback if account expects plaintext)
        if (token.isNullOrBlank()) {
            val rawSig = officialSignature(
                path = listOf("oauth2", "login"),
                params = mapOf("username" to cleanEmail, "password" to cleanPassword),
                timestamp = timestamp,
            )
            val oauth2RawUrl = "$CATALOG_API_BASE/oauth2/login".toHttpUrl().newBuilder()
                .addQueryParameter("username", cleanEmail)
                .addQueryParameter("password", cleanPassword)
                .addQueryParameter("app_id", OFFICIAL_APP_ID)
                .addQueryParameter("request_ts", timestamp.toString())
                .addQueryParameter("request_sig", rawSig)
                .build()

            val oauth2RawResult = executeRequest(oauth2RawUrl.toString())
            token = oauth2RawResult.json?.let { extractToken(it) }
            if (oauth2RawResult.errorMessage != null) {
                errorMessage = oauth2RawResult.errorMessage
            }
        }

        // 3. Third attempt: user/login with MD5 password
        if (token.isNullOrBlank()) {
            val userLoginSig = officialSignature(
                path = listOf("user", "login"),
                params = mapOf("username" to cleanEmail, "password" to md5Password),
                timestamp = timestamp,
            )
            val userLoginUrl = "$CATALOG_API_BASE/user/login".toHttpUrl().newBuilder()
                .addQueryParameter("username", cleanEmail)
                .addQueryParameter("password", md5Password)
                .addQueryParameter("app_id", OFFICIAL_APP_ID)
                .addQueryParameter("request_ts", timestamp.toString())
                .addQueryParameter("request_sig", userLoginSig)
                .build()

            val userLoginResult = executeRequest(userLoginUrl.toString())
            token = userLoginResult.json?.let { extractToken(it) }
            if (userLoginResult.errorMessage != null) {
                errorMessage = userLoginResult.errorMessage
            }
        }

        // 4. Fourth attempt: fallback if password had accidental whitespace
        if (token.isNullOrBlank() && cleanPassword != password.trim()) {
            val trimmedPassword = password.trim()
            val trimmedMd5 = md5(trimmedPassword)
            val trimmedSig = officialSignature(
                path = listOf("oauth2", "login"),
                params = mapOf("username" to cleanEmail, "password" to trimmedMd5),
                timestamp = timestamp,
            )
            val trimmedUrl = "$CATALOG_API_BASE/oauth2/login".toHttpUrl().newBuilder()
                .addQueryParameter("username", cleanEmail)
                .addQueryParameter("password", trimmedMd5)
                .addQueryParameter("app_id", OFFICIAL_APP_ID)
                .addQueryParameter("request_ts", timestamp.toString())
                .addQueryParameter("request_sig", trimmedSig)
                .build()

            val trimmedResult = executeRequest(trimmedUrl.toString())
            token = trimmedResult.json?.let { extractToken(it) }
            if (trimmedResult.errorMessage != null) {
                errorMessage = trimmedResult.errorMessage
            }
        }

        if (!token.isNullOrBlank()) {
            val prefs = if (context != null) PlayerPreferences(context) else PlayerPreferences()
            prefs.setQobuzUserAuthToken(token)
            Result.success(token)
        } else {
            Result.failure(Exception(errorMessage ?: "Login failed — check your email and password"))
        }
    }

    /**
     * The signature Qobuz asks for on its signed endpoints: the path after the
     * version, then the parameters sorted by name, then the timestamp, then the
     * app secret — hashed with MD5.
     */
    private fun officialSignature(
        path: List<String>,
        params: Map<String, String>,
        timestamp: Long,
    ): String {
        val payload = buildString {
            append(path.joinToString(""))
            params.keys.sorted().forEach { key -> append(key).append(params[key]) }
            append(timestamp)
            append(OFFICIAL_APP_SECRET)
        }
        return md5(payload)
    }

    private fun md5(value: String): String {
        val digest = java.security.MessageDigest.getInstance("MD5")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * Triggers Qobuz's official password reset / creation email.
     * Useful for accounts created with Google Sign-in so the user can set a
     * password and connect directly via email + password.
     */
    suspend fun resetPassword(email: String): Boolean = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (cleanEmail.isBlank()) return@withContext false
        val timestamp = System.currentTimeMillis() / 1000
        val signature = officialSignature(
            path = listOf("user", "resetPassword"),
            params = mapOf("email" to cleanEmail),
            timestamp = timestamp,
        )
        val encodedEmail = cleanEmail.urlEncoded()
        val root = fetchJson(
            "$CATALOG_API_BASE/user/resetPassword?email=$encodedEmail" +
                "&app_id=$OFFICIAL_APP_ID&request_ts=$timestamp&request_sig=$signature"
        ) ?: return@withContext false
        root.optString("status") == "success"
    }

    /**
     * Logs in to Qobuz with a Google identity token, the same call the official
     * app makes once Google sign-in has produced one.
     */
    suspend fun loginWithGoogle(context: Context? = null, identityToken: String): String? =
        loginWithOAuth(context, "google", mapOf("identity_token" to identityToken.trim()))

    /**
     * Logs in to Qobuz with a Facebook access token, the same call the official
     * app makes after the Facebook login dialog.
     */
    suspend fun loginWithFacebook(
        context: Context? = null,
        accessToken: String,
        userId: String,
        expiresAt: Long,
    ): String? = loginWithOAuth(
        context,
        "facebook",
        mapOf(
            "facebook_access_token" to accessToken.trim(),
            "facebook_access_token_expires" to expiresAt.toString(),
            "facebook_user_id" to userId.trim(),
        ),
    )

    /**
     * The Facebook id of the account behind an access token — Qobuz wants both
     * the token and the id when it logs a Facebook session in.
     */
    suspend fun fetchFacebookUserId(accessToken: String): String? = withContext(Dispatchers.IO) {
        val root = fetchJson(
            "https://graph.facebook.com/me?fields=id&access_token=${accessToken.urlEncoded()}"
        ) ?: return@withContext null
        root.optString("id").takeIf { it.isNotBlank() }
    }

    /** The OAuth2 endpoint of the official app, signed the way it expects. */
    private suspend fun loginWithOAuth(
        context: Context?,
        endpoint: String,
        params: Map<String, String>,
    ): String? = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis() / 1000
        val signature = officialSignature(listOf("oauth2", endpoint), params, timestamp)
        val urlBuilder = "$CATALOG_API_BASE/oauth2/$endpoint".toHttpUrl().newBuilder()
        params.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        urlBuilder.addQueryParameter("app_id", OFFICIAL_APP_ID)
        urlBuilder.addQueryParameter("request_ts", timestamp.toString())
        urlBuilder.addQueryParameter("request_sig", signature)

        val result = executeRequest(urlBuilder.build().toString())
        val token = result.json?.let { extractToken(it) }
        if (token != null) {
            val prefs = if (context != null) PlayerPreferences(context) else PlayerPreferences()
            prefs.setQobuzUserAuthToken(token)
        }
        token
    }

    /**
     * The official Qobuz radio around a track. Requires a logged-in Qobuz
     * user (see [login]); without one the endpoint answers 401 and this
     * returns null, so the catalogue-derived radio is used instead.
     */
    suspend fun getOfficialRadio(context: Context? = null, trackId: String): List<Track> = withContext(Dispatchers.IO) {
        val token = if (context != null) PlayerPreferences(context).getQobuzUserAuthToken()
        else PlayerPreferences().getQobuzUserAuthToken()
        if (token.isNullOrBlank()) return@withContext emptyList()
        val cleanId = trackId.removePrefix("qobuz:track:").trim()
        if (cleanId.isBlank()) return@withContext emptyList()
        val root = fetchJson(
            "$CATALOG_API_BASE/radio/track?track_id=${cleanId.urlEncoded()}" +
                "&app_id=$OFFICIAL_APP_ID&user_auth_token=$token",
            bearerToken = token,
        ) ?: return@withContext emptyList()
        val data = root.optJSONObject("data") ?: root
        val items = data.optJSONObject("tracks")?.optJSONArray("items")
            ?: root.optJSONObject("tracks")?.optJSONArray("items")
            ?: return@withContext emptyList()
        items.mapObjects { it.toQobuzTrack() }
    }

    /**
     * A radio around a Qobuz track, built from the catalogue itself: the album
     * the track belongs to, the artist's own tracks, and a few of the artists
     * Qobuz itself lists as similar. Qobuz has no radio endpoint of its own, but
     * everything here comes from the same catalogue the track was found in.
     */
    suspend fun getRadio(trackId: String, limit: Int = 40): List<Track> = withContext(Dispatchers.IO) {
        // Prefer the official radio when the user is logged in to Qobuz.
        val official = getOfficialRadio(null, trackId)
        if (official.isNotEmpty()) return@withContext official
        // ... keep the rest of the catalogue-derived logic unchanged ...
        val cleanId = trackId.removePrefix("qobuz:track:").trim()
        if (cleanId.isBlank()) return@withContext emptyList()

        val seed = fetchJson("$CATALOG_API_BASE/track/get?track_id=${cleanId.urlEncoded()}&app_id=$CATALOG_APP_ID")
        val albumId = seed?.optJSONObject("album")?.optString("id")?.takeIf { it.isNotBlank() && it != "null" }
        val artistId = seed?.optJSONObject("performer")?.optLong("id")
            ?: seed?.optJSONObject("artist")?.optLong("id")
            ?: 0L

        val tracks = mutableListOf<Track>()

        albumId?.let { id ->
            fetchJson("$CATALOG_API_BASE/album/get?album_id=${id.urlEncoded()}&app_id=$CATALOG_APP_ID")
                ?.optJSONObject("tracks")
                ?.optJSONArray("items")
                ?.mapObjects { it.toQobuzTrack() }
                ?.let { tracks.addAll(it) }
        }

        if (artistId > 0L) {
            tracks.addAll(artistTopTracks(artistId))
            similarArtistIds(artistId).take(3).forEach { similar ->
                tracks.addAll(artistTopTracks(similar))
            }
            // A small artist has neither many tracks nor similar artists, so the
            // station would be a handful of songs: the artist's own albums fill it.
            if (tracks.size < 12) {
                artistAlbumIds(artistId).take(2).forEach { albumId ->
                    fetchJson("$CATALOG_API_BASE/album/get?album_id=${albumId.urlEncoded()}&app_id=$CATALOG_APP_ID")
                        ?.optJSONObject("tracks")
                        ?.optJSONArray("items")
                        ?.mapObjects { it.toQobuzTrack() }
                        ?.let { tracks.addAll(it) }
                }
            }
        }

        tracks.distinctBy { it.id }.take(limit)
    }

    /** The albums the catalogue lists for an artist. */
    private suspend fun artistAlbumIds(artistId: Long): List<String> =
        fetchJson("$CATALOG_API_BASE/artist/get?artist_id=$artistId&app_id=$CATALOG_APP_ID&extra=albums")
            ?.optJSONObject("albums")
            ?.optJSONArray("items")
            ?.let { array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("id")?.takeIf { id -> id.isNotBlank() } }
            }
            ?: emptyList()

    /** The tracks the catalogue lists for an artist. */
    private suspend fun artistTopTracks(artistId: Long): List<Track> =
        fetchJson("$CATALOG_API_BASE/artist/get?artist_id=$artistId&app_id=$CATALOG_APP_ID&extra=tracks")
            ?.optJSONObject("tracks")
            ?.optJSONArray("items")
            ?.mapObjects { it.toQobuzTrack() }
            ?: emptyList()

    /** The artists the catalogue considers close to this one. */
    private suspend fun similarArtistIds(artistId: Long): List<Long> =
        fetchJson("$CATALOG_API_BASE/artist/get?artist_id=$artistId&app_id=$CATALOG_APP_ID")
            ?.optJSONArray("similar_artist_ids")
            ?.let { array ->
                (0 until array.length()).mapNotNull { array.optLong(it).takeIf { id -> id > 0L } }
            }
            ?: emptyList()

    private fun String.urlEncoded(): String = URLEncoder.encode(this, "UTF-8")

    suspend fun getAlbum(context: Context? = null, albumId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix("qobuz:album:").trim()
        if (cleanId.isBlank()) return@withContext null
        val encodedId = URLEncoder.encode(cleanId, "UTF-8")

        fun mapAlbum(data: JSONObject): Playlist? {
            val title = data.optString("title")
            if (title.isBlank()) return null
            val tracksArray = data.optJSONObject("tracks")?.optJSONArray("items")
            val tracks = tracksArray?.mapObjects { it.toQobuzTrack(albumOverride = data) } ?: emptyList()

            val artistObj = data.optJSONObject("artist")
            val artistName = artistObj?.extractName() ?: "Qobuz"
            val artistId = artistObj?.optLong("id") ?: 0L
            val imageObj = data.optJSONObject("image")
            val cover = imageObj?.optString("large")
                ?: imageObj?.optString("small")
                ?: imageObj?.optString("thumbnail")
            val stableId = abs(("qobuz:album:$cleanId").hashCode().toLong())

            return Playlist(
                id = stableId,
                title = title,
                artworkUrl = cover,
                calculatedArtworkUrl = cover,
                trackCount = data.optInt("tracks_count", tracks.size),
                user = User(
                    id = artistId,
                    username = artistName,
                    avatarUrl = null,
                    urn = "qobuz:artist:$artistId"
                ),
                tracks = tracks,
                isAlbum = true,
                releaseDate = data.optString("release_date_original"),
                permalink = cleanId,
                permalinkUrl = data.optString("url").ifBlank { "https://www.qobuz.com/album/$cleanId" },
                urn = "qobuz:album:$cleanId"
            )
        }

        fetchJson("$CATALOG_API_BASE/album/get?album_id=$encodedId&app_id=$CATALOG_APP_ID")?.let { root ->
            val data = root.optJSONObject("data") ?: root
            mapAlbum(data)?.let { return@withContext it }
        }

        for (base in getBaseUrls(context)) {
            val url = "$base/api/get-album?album_id=$encodedId"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue
            mapAlbum(data)?.let { return@withContext it }
        }

        null
    }

    suspend fun getArtist(context: Context? = null, artistId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = artistId.removePrefix("qobuz:artist:").trim()
        val baseUrls = getBaseUrls(context)

        val encodedId = URLEncoder.encode(cleanId, "UTF-8")
        fetchJson("$CATALOG_API_BASE/artist/get?artist_id=$encodedId&app_id=$CATALOG_APP_ID&extra=tracks,albums")?.let { artist ->
            val topTracks = artist.optJSONObject("tracks")
                ?.optJSONArray("items")
                ?.mapObjects { it.toQobuzTrack() }
                ?: emptyList()
            val name = artist.extractName() ?: artist.optString("name")
            val picture = extractArtistPicture(artist) ?: topTracks.firstOrNull()?.artworkUrl
            val idLong = artist.optLong("id")
            val stableId = abs(("qobuz:artist:$cleanId").hashCode().toLong())
            if (name.isNotBlank()) {
                return@withContext Playlist(
                    id = stableId,
                    title = name,
                    artworkUrl = picture,
                    calculatedArtworkUrl = picture,
                    trackCount = topTracks.size,
                    user = User(
                        id = idLong,
                        username = name,
                        avatarUrl = picture,
                        permalink = cleanId,
                        urn = "qobuz:artist:$cleanId"
                    ),
                    tracks = topTracks,
                    isAlbum = false,
                    permalink = cleanId,
                    permalinkUrl = artist.optString("url").ifBlank { "https://www.qobuz.com/artist/$cleanId" },
                    urn = "qobuz:artist:$cleanId"
                )
            }
        }

        for (base in baseUrls) {
            val url = "$base/api/get-artist?artist_id=$cleanId"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue
            val artist = data.optJSONObject("artist") ?: continue

            val topTracksArray = artist.optJSONArray("top_tracks")
            var topTracks = topTracksArray?.mapObjects { it.toQobuzTrack() } ?: emptyList()
            if (topTracks.isEmpty()) {
                val releases = artist.optJSONArray("releases")
                if (releases != null) {
                    val fallbackTracks = mutableListOf<Track>()
                    for (i in 0 until releases.length()) {
                        val rel = releases.optJSONObject(i) ?: continue
                        val items = rel.optJSONArray("items") ?: continue
                        for (j in 0 until minOf(items.length(), 2)) {
                            val albumItem = items.optJSONObject(j) ?: continue
                            val albumId = albumItem.optString("id").takeIf { it.isNotBlank() } ?: continue
                            val album = getAlbum(context, albumId)
                            val albumTracks = album?.tracks
                            if (!albumTracks.isNullOrEmpty()) {
                                fallbackTracks.addAll(albumTracks)
                                if (fallbackTracks.size >= 25) break
                            }
                        }
                        if (fallbackTracks.isNotEmpty()) break
                    }
                    if (fallbackTracks.isNotEmpty()) {
                        topTracks = fallbackTracks.distinctBy { it.id }
                    }
                }
            }

            val name = artist.extractName() ?: artist.optString("name")
            val picture = extractArtistPicture(artist) ?: topTracks.firstOrNull()?.artworkUrl
            val idLong = artist.optLong("id")
            val stableId = abs(("qobuz:artist:$cleanId").hashCode().toLong())

            return@withContext Playlist(
                id = stableId,
                title = name,
                artworkUrl = picture,
                calculatedArtworkUrl = picture,
                trackCount = topTracks.size,
                user = User(
                    id = idLong,
                    username = name,
                    avatarUrl = picture,
                    permalink = cleanId,
                    urn = "qobuz:artist:$cleanId"
                ),
                tracks = topTracks,
                isAlbum = false,
                permalink = cleanId,
                permalinkUrl = "https://www.qobuz.com/artist/$cleanId",
                urn = "qobuz:artist:$cleanId"
            )
        }

        null
    }

    suspend fun getPlaylist(context: Context? = null, playlistId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("qobuz:playlist:").trim()
        if (cleanId.isBlank()) return@withContext null
        val encodedId = URLEncoder.encode(cleanId, "UTF-8")

        // The catalogue endpoint resolves public playlists directly, including
        // their tracks, without needing a listener token or the custom resolver.
        fetchJson("$CATALOG_API_BASE/playlist/get?playlist_id=$encodedId&app_id=$CATALOG_APP_ID&extra=tracks")?.let { root ->
            val data = root.optJSONObject("data") ?: root
            data.toQobuzPlaylist()?.let { return@withContext it }
        }

        // If playlist tracks endpoint isn't supported directly by Kenny, fallback to playlist search or placeholder
        val searchRes = search(context, cleanId, limit = 10)
        val matched = searchRes.playlists.firstOrNull { it.permalink == cleanId || it.urn == "qobuz:playlist:$cleanId" }
        matched
    }

    private fun JSONObject.extractName(): String? {
        val direct = optString("name").trim()
        if (direct.isNotBlank() && !direct.startsWith("{") && !direct.equals("null", ignoreCase = true)) {
            return direct
        }
        val nested = optJSONObject("name")?.optString("display")?.trim()
        if (!nested.isNullOrBlank() && !nested.equals("null", ignoreCase = true)) {
            return nested
        }
        return null
    }

    private fun JSONObject.toQobuzTrack(albumOverride: JSONObject? = null): Track? {
        val id = optString("id")
        if (id.isBlank() || id == "null") return null
        val title = optString("title")
        if (title.isBlank()) return null
        val version = optString("version")
        val displayTitle = if (version.isNotBlank() && version != "null") "$title ($version)" else title

        val performerObj = optJSONObject("performer")
        val trackArtistObj = optJSONObject("artist")
        val albumObj = albumOverride ?: optJSONObject("album")
        val albumArtistObj = albumObj?.optJSONObject("artist")
        val artistName = performerObj?.extractName()
            ?: trackArtistObj?.extractName()
            ?: albumArtistObj?.extractName()
            ?: "Unknown Artist"
        val artistId = performerObj?.optLong("id")
            ?: trackArtistObj?.optLong("id")
            ?: albumArtistObj?.optLong("id")
            ?: 0L

        val albumImageObj = albumObj?.optJSONObject("image") ?: optJSONObject("image")
        val cover = albumImageObj?.optString("large")
            ?: albumImageObj?.optString("small")
            ?: albumImageObj?.optString("thumbnail")

        val durationSec = optLong("duration")
        val isrc = optString("isrc").takeIf { it.isNotBlank() && it != "null" }
        val explicit = optBoolean("parental_warning", false)
        val permalink = "qobuz:track:$id"
        val stableId = abs(permalink.hashCode().toLong())

        val hires = optBoolean("hires", false)
        val specs = optString("maximum_technical_specifications").takeIf { it.isNotBlank() }

        return Track(
            id = stableId,
            title = displayTitle,
            artworkUrl = cover,
            durationMs = durationSec * 1000L,
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = null,
                permalink = artistId.toString(),
                urn = "qobuz:artist:$artistId"
            ),
            publisherMetadata = TrackPublisherMetadata(
                artist = artistName,
                albumTitle = albumObj?.optString("title"),
                albumId = albumObj?.optString("id"),
                isrc = isrc,
                explicit = explicit,
                publisher = specs
            ),
            permalink = permalink,
            permalinkUrl = optString("url").ifBlank { "https://open.qobuz.com/track/$id" },
            source = "qobuz",
            streamable = true
        )
    }

    private fun JSONObject.toQobuzAlbum(): Playlist? {
        val id = optString("id")
        if (id.isBlank() || id == "null") return null
        val title = optString("title")
        if (title.isBlank()) return null

        val imageObj = optJSONObject("image")
        val cover = imageObj?.optString("large") ?: imageObj?.optString("small")
        val artistObj = optJSONObject("artist")
        val artistName = artistObj?.extractName() ?: "Unknown Artist"
        val artistId = artistObj?.optLong("id") ?: 0L
        val tracksCount = optInt("tracks_count")
        val stableId = abs(("qobuz:album:$id").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = title,
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = tracksCount,
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = null,
                urn = "qobuz:artist:$artistId"
            ),
            isAlbum = true,
            permalink = id,
            permalinkUrl = optString("url").ifBlank { "https://www.qobuz.com/album/$id" },
            urn = "qobuz:album:$id"
        )
    }

    private fun JSONObject.toQobuzPlaylist(): Playlist? {
        val id = optString("id")
        if (id.isBlank() || id == "null") return null
        val name = optString("name")
        if (name.isBlank()) return null

        val images300 = optJSONArray("images300")
        val images = optJSONArray("images")
        val imageRect = optJSONArray("image_rectangle")
        val cover = images300?.optString(0)?.takeIf { it.isNotBlank() }
            ?: images?.optString(0)?.takeIf { it.isNotBlank() }
            ?: imageRect?.optString(0)?.takeIf { it.isNotBlank() }

        val ownerObj = optJSONObject("owner")
        val ownerName = ownerObj?.optString("name") ?: "Qobuz"
        val ownerId = ownerObj?.optLong("id") ?: abs(ownerName.hashCode().toLong())
        val tracks = optJSONObject("tracks")
            ?.optJSONArray("items")
            ?.mapObjects { it.toQobuzTrack() }
        val tracksCount = optInt("tracks_count", tracks?.size ?: 0)
        val durationMs = optLong("duration").takeIf { it > 0 }?.times(1000L)
        val stableId = abs(("qobuz:playlist:$id").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = name,
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = tracksCount,
            user = User(
                id = ownerId,
                username = ownerName,
                avatarUrl = null
            ),
            tracks = tracks,
            isAlbum = false,
            description = optString("description"),
            permalink = id,
            permalinkUrl = "https://www.qobuz.com/playlist/$id",
            urn = "qobuz:playlist:$id",
            durationMs = durationMs
        )
    }

    private fun extractArtistPicture(artistObj: JSONObject): String? {
        val direct = artistObj.optString("picture").takeIf { it.isNotBlank() && it != "null" && it.startsWith("http") }
        if (direct != null) return direct

        val imageObj = artistObj.optJSONObject("image") ?: artistObj.optJSONObject("images")
        if (imageObj != null) {
            val directUrl = imageObj.optString("large").takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: imageObj.optString("extralarge").takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: imageObj.optString("mega").takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: imageObj.optString("medium").takeIf { it.isNotBlank() && it.startsWith("http") }
                ?: imageObj.optString("small").takeIf { it.isNotBlank() && it.startsWith("http") }
            if (directUrl != null) return directUrl

            for (key in listOf("portrait", "square", "landscape")) {
                val child = imageObj.optJSONObject(key)
                val hash = child?.optString("hash")?.takeIf { it.isNotBlank() && it != "null" }
                if (hash != null) {
                    val format = child.optString("format").ifBlank { "jpg" }
                    return "https://static.qobuz.com/images/artists/covers/large/$hash.$format"
                }
            }
            val directHash = imageObj.optString("hash").takeIf { it.isNotBlank() && it != "null" }
            if (directHash != null) {
                val format = imageObj.optString("format").ifBlank { "jpg" }
                return "https://static.qobuz.com/images/artists/covers/large/$directHash.$format"
            }
        }
        return null
    }

    private fun JSONObject.toQobuzArtist(): User? {
        val id = optString("id")
        if (id.isBlank() || id == "null") return null
        val name = extractName() ?: optString("name")
        if (name.isBlank()) return null

        val picture = extractArtistPicture(this)
        val stableId = abs(("qobuz:artist:$id").hashCode().toLong())

        return User(
            id = stableId,
            username = name,
            avatarUrl = picture,
            permalink = id,
            urn = "qobuz:artist:$id"
        )
    }

    private val deviceManufacturerId: String by lazy {
        val board = Build.BOARD ?: ""
        val brand = Build.BRAND ?: ""
        val abi = Build.SUPPORTED_ABIS?.firstOrNull() ?: ""
        val device = Build.DEVICE ?: ""
        val manufacturer = Build.MANUFACTURER ?: ""
        val model = Build.MODEL ?: ""
        val product = Build.PRODUCT ?: ""
        val str = "35" + (board.length % 10) + (brand.length % 10) +
            (abi.length % 10) + (device.length % 10) +
            (manufacturer.length % 10) + (model.length % 10) + (product.length % 10)
        try {
            java.util.UUID(str.hashCode().toLong(), -905839116L).toString()
        } catch (_: Exception) {
            java.util.UUID.randomUUID().toString()
        }
    }

    private data class ApiResponseResult(
        val code: Int,
        val json: JSONObject?,
        val errorMessage: String? = null,
    )

    private fun executeRequest(url: String, bearerToken: String? = null): ApiResponseResult {
        return runCatching {
            val model = Build.MODEL ?: "Pixel 8 Pro"
            val release = Build.VERSION.RELEASE ?: "14"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) QobuzMobileAndroid/10.1.2.0-b26100110")
                .header("X-App-Version", "10.1.2.0")
                .header("X-Device-Manufacturer-Id", deviceManufacturerId)
                .header("X-Device-Model", model)
                .header("X-Device-Platform", "android")
                .header("X-Device-Os-Version", release)
                .apply { if (!bearerToken.isNullOrBlank()) header("Authorization", "Bearer $bearerToken") }
                .build()
            client.newCall(request).execute().use { response ->
                val bodyStr = response.body.string()
                val json = runCatching { JSONObject(bodyStr) }.getOrNull()
                val errorMsg = if (!response.isSuccessful) {
                    json?.optString("message")?.takeIf { it.isNotBlank() }
                        ?: "HTTP ${response.code}: ${response.message}"
                } else null
                Log.d("Qobuz", "Request $url -> code ${response.code}, body: $bodyStr")
                ApiResponseResult(response.code, json, errorMsg)
            }
        }.getOrElse { ex ->
            Log.e("Qobuz", "Request failed $url", ex)
            ApiResponseResult(0, null, ex.localizedMessage ?: "Network error")
        }
    }

    fun extractToken(root: JSONObject): String? {
        val oauthToken = root.optJSONObject("oauth2")?.optString("access_token")?.takeIf { it.isNotBlank() }
        if (oauthToken != null) return oauthToken
        val userAuthToken = root.optString("user_auth_token").takeIf { it.isNotBlank() }
        if (userAuthToken != null) return userAuthToken
        val accessToken = root.optString("access_token").takeIf { it.isNotBlank() }
        if (accessToken != null) return accessToken
        val credToken = root.optJSONObject("user")?.optString("user_auth_token")?.takeIf { it.isNotBlank() }
        if (credToken != null) return credToken
        val nestedData = root.optJSONObject("data")
        if (nestedData != null) return extractToken(nestedData)
        return null
    }

    private fun fetchJson(url: String, bearerToken: String? = null): JSONObject? {
        val result = executeRequest(url, bearerToken)
        return if (result.code in 200..299) result.json else null
    }

    private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T?): List<T> {
        val list = mutableListOf<T>()
        for (i in 0 until length()) {
            optJSONObject(i)?.let { obj ->
                transform(obj)?.let { list.add(it) }
            }
        }
        return list
    }
}
