package com.alananasss.kittytune.data.tidal

import android.content.Context
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class TidalSearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artists: List<User> = emptyList()
)

object TidalSearchRepository {
    private const val API_BASE = "https://api.tidal.com/v1"
    private const val AUTH_URL = "https://auth.tidal.com/v1/oauth2/token"
    private const val CLIENT_ID = "txNoH4kkV41MfH25"
    private const val CLIENT_SECRET = "dQjy0MinCEvxi1O4UmxvxWnDjt4cgHBPw8ll6nYBk98="

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val tokenMutex = Mutex()
    private var cachedAccessToken: String? = null
    private var tokenExpiryMs: Long = 0L

    private suspend fun getAccessToken(context: Context? = null): String? = tokenMutex.withLock {
        val now = System.currentTimeMillis()
        if (!cachedAccessToken.isNullOrBlank() && now < tokenExpiryMs - 60_000L) {
            return cachedAccessToken
        }

        // Check if user has explicit token/cookie configured
        if (context != null) {
            val userCookie = PlayerPreferences(context).getTidalCookie().trim()
            if (userCookie.isNotBlank() && !userCookie.contains("=") && userCookie.length > 50) {
                cachedAccessToken = userCookie
                tokenExpiryMs = now + 3600_000L
                return cachedAccessToken
            }
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val formBody = FormBody.Builder()
                    .add("grant_type", "client_credentials")
                    .add("client_id", CLIENT_ID)
                    .add("client_secret", CLIENT_SECRET)
                    .build()

                val request = Request.Builder()
                    .url(AUTH_URL)
                    .post(formBody)
                    .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro)")
                    .build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val body = response.body.string()
                    val json = JSONObject(body)
                    val token = json.optString("access_token")
                    val expiresIn = json.optLong("expires_in", 3600L)
                    if (token.isNotBlank()) {
                        cachedAccessToken = token
                        tokenExpiryMs = now + (expiresIn * 1000L)
                        token
                    } else null
                }
            }.getOrNull()
        }
    }

    suspend fun search(
        context: Context,
        query: String,
        limit: Int = 50,
        countryCode: String = "US"
    ): TidalSearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext TidalSearchResult()
        val token = getAccessToken(context) ?: return@withContext TidalSearchResult()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")

        val url = "$API_BASE/search?query=$encodedQuery&types=TRACKS,ALBUMS,PLAYLISTS,ARTISTS&limit=$limit&countryCode=$countryCode&deviceType=BROWSER"
        val root = fetchJson(url, token) ?: return@withContext TidalSearchResult()

        val tracks = root.optJSONObject("tracks")?.optJSONArray("items")?.mapObjects { it.toTidalTrack() } ?: emptyList()
        val albums = root.optJSONObject("albums")?.optJSONArray("items")?.mapObjects { it.toTidalAlbum() } ?: emptyList()
        val playlists = root.optJSONObject("playlists")?.optJSONArray("items")?.mapObjects { it.toTidalPlaylist() } ?: emptyList()
        val artists = root.optJSONObject("artists")?.optJSONArray("items")?.mapObjects { it.toTidalArtist() } ?: emptyList()

        TidalSearchResult(
            tracks = tracks,
            albums = albums,
            playlists = playlists,
            artists = artists
        )
    }

    suspend fun getAlbum(context: Context, albumId: String, countryCode: String = "US"): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix("tidal:album:").trim()
        val token = getAccessToken(context) ?: return@withContext null

        val albumJson = fetchJson("$API_BASE/albums/$cleanId?countryCode=$countryCode", token) ?: return@withContext null
        val tracksJson = fetchJson("$API_BASE/albums/$cleanId/tracks?countryCode=$countryCode", token)
        val tracks = tracksJson?.optJSONArray("items")?.mapObjects { it.toTidalTrack(albumOverride = albumJson) } ?: emptyList()

        val idLong = albumJson.optLong("id")
        val stableId = abs(("tidal:album:$idLong").hashCode().toLong())
        val coverUuid = albumJson.optString("cover")
        val coverUrl = tidalCoverUrl(coverUuid)
        val artistObj = albumJson.optJSONObject("artist")
        val artistName = artistObj?.optString("name") ?: "Tidal"
        val artistId = artistObj?.optLong("id") ?: 0L

        Playlist(
            id = stableId,
            title = albumJson.optString("title"),
            artworkUrl = coverUrl,
            calculatedArtworkUrl = coverUrl,
            trackCount = albumJson.optInt("numberOfTracks", tracks.size),
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = null,
                urn = "tidal:artist:$artistId"
            ),
            tracks = tracks,
            isAlbum = true,
            releaseDate = albumJson.optString("releaseDate"),
            permalink = idLong.toString(),
            permalinkUrl = albumJson.optString("url").ifBlank { "https://tidal.com/browse/album/$idLong" },
            urn = "tidal:album:$idLong"
        )
    }

    suspend fun getPlaylist(context: Context, playlistId: String, countryCode: String = "US"): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("tidal:playlist:").trim()
        val token = getAccessToken(context) ?: return@withContext null

        val playlistJson = fetchJson("$API_BASE/playlists/$cleanId?countryCode=$countryCode", token) ?: return@withContext null
        val itemsJson = fetchJson("$API_BASE/playlists/$cleanId/items?countryCode=$countryCode&limit=100", token)
        val tracks = itemsJson?.optJSONArray("items")?.mapObjects { itemWrapper ->
            itemWrapper.optJSONObject("item")?.toTidalTrack() ?: itemWrapper.toTidalTrack()
        } ?: emptyList()

        val uuid = playlistJson.optString("uuid").ifBlank { cleanId }
        val stableId = abs(("tidal:playlist:$uuid").hashCode().toLong())
        val imageUuid = playlistJson.optString("image").ifBlank { playlistJson.optString("squareImage") }
        val coverUrl = tidalCoverUrl(imageUuid)
        val creatorObj = playlistJson.optJSONObject("creator")
        val creatorName = creatorObj?.optString("name") ?: "Tidal"

        Playlist(
            id = stableId,
            title = playlistJson.optString("title"),
            artworkUrl = coverUrl,
            calculatedArtworkUrl = coverUrl,
            trackCount = playlistJson.optInt("numberOfTracks", tracks.size),
            user = User(
                id = abs(creatorName.hashCode().toLong()),
                username = creatorName,
                avatarUrl = null
            ),
            tracks = tracks,
            isAlbum = false,
            description = playlistJson.optString("description"),
            permalink = uuid,
            permalinkUrl = playlistJson.optString("url").ifBlank { "https://tidal.com/browse/playlist/$uuid" },
            urn = "tidal:playlist:$uuid"
        )
    }

    suspend fun getArtist(context: Context, artistId: String, countryCode: String = "US"): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = artistId.removePrefix("tidal:artist:").trim()
        val token = getAccessToken(context) ?: return@withContext null

        val pageJson = fetchJson("$API_BASE/pages/artist?artistId=$cleanId&countryCode=$countryCode&deviceType=BROWSER", token) ?: return@withContext null
        val rows = pageJson.optJSONArray("rows")

        var artistName = "Tidal Artist"
        var pictureUuid: String? = null
        val topTracks = mutableListOf<Track>()

        if (rows != null) {
            for (i in 0 until rows.length()) {
                val row = rows.optJSONObject(i) ?: continue
                val modules = row.optJSONArray("modules") ?: continue
                for (j in 0 until modules.length()) {
                    val mod = modules.optJSONObject(j) ?: continue
                    val type = mod.optString("type")
                    if (type == "ARTIST_HEADER") {
                        val artistObj = mod.optJSONObject("artist")
                        if (artistObj != null) {
                            artistName = artistObj.optString("name").ifBlank { artistName }
                            pictureUuid = artistObj.optString("picture").takeIf { it.isNotBlank() }
                        }
                    } else if (type == "TRACK_LIST" && topTracks.isEmpty()) {
                        val pagedList = mod.optJSONObject("pagedList")
                        val items = pagedList?.optJSONArray("items")
                        if (items != null) {
                            for (k in 0 until items.length()) {
                                val trackJson = items.optJSONObject(k) ?: continue
                                trackJson.toTidalTrack()?.let { topTracks.add(it) }
                            }
                        }
                    }
                }
            }
        }

        val idLong = cleanId.toLongOrNull() ?: 0L
        val stableId = abs(("tidal:artist:$cleanId").hashCode().toLong())
        val coverUrl = tidalCoverUrl(pictureUuid)

        Playlist(
            id = stableId,
            title = artistName,
            artworkUrl = coverUrl,
            calculatedArtworkUrl = coverUrl,
            trackCount = topTracks.size,
            user = User(
                id = idLong,
                username = artistName,
                avatarUrl = coverUrl,
                permalink = cleanId,
                urn = "tidal:artist:$cleanId"
            ),
            tracks = topTracks,
            isAlbum = false,
            permalink = cleanId,
            permalinkUrl = "https://tidal.com/browse/artist/$cleanId",
            urn = "tidal:artist:$cleanId"
        )
    }

    private fun JSONObject.toTidalTrack(albumOverride: JSONObject? = null): Track? {
        val idLong = optLong("id")
        if (idLong == 0L) return null
        val title = optString("title")
        if (title.isBlank()) return null

        val artistsArray = optJSONArray("artists")
        val firstArtist = artistsArray?.optJSONObject(0) ?: optJSONObject("artist")
        val artistName = firstArtist?.optString("name") ?: "Unknown Artist"
        val artistId = firstArtist?.optLong("id") ?: 0L
        val albumObj = albumOverride ?: optJSONObject("album")
        val albumTitle = albumObj?.optString("title")
        val albumId = albumObj?.optLong("id")?.toString()
        val coverUuid = albumObj?.optString("cover") ?: optString("cover")
        val artworkUrl = tidalCoverUrl(coverUuid)
        val durationSec = optLong("duration")
        val isrc = optString("isrc").takeIf { it.isNotBlank() }
        val explicit = optBoolean("explicit", false)
        val permalink = "tidal:track:$idLong"
        val stableId = abs(permalink.hashCode().toLong())

        return Track(
            id = stableId,
            title = title,
            artworkUrl = artworkUrl,
            durationMs = durationSec * 1000L,
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = null,
                permalink = artistId.toString(),
                urn = "tidal:artist:$artistId"
            ),
            publisherMetadata = TrackPublisherMetadata(
                artist = artistName,
                albumTitle = albumTitle,
                albumId = albumId,
                isrc = isrc,
                explicit = explicit
            ),
            permalink = permalink,
            permalinkUrl = optString("url").ifBlank { "https://tidal.com/browse/track/$idLong" },
            source = "tidal",
            streamable = true
        )
    }

    private fun JSONObject.toTidalAlbum(): Playlist? {
        val idLong = optLong("id")
        if (idLong == 0L) return null
        val title = optString("title")
        if (title.isBlank()) return null
        val coverUuid = optString("cover")
        val coverUrl = tidalCoverUrl(coverUuid)
        val artistsArray = optJSONArray("artists")
        val firstArtist = artistsArray?.optJSONObject(0) ?: optJSONObject("artist")
        val artistName = firstArtist?.optString("name") ?: "Unknown Artist"
        val artistId = firstArtist?.optLong("id") ?: 0L
        val nbTracks = optInt("numberOfTracks")
        val stableId = abs(("tidal:album:$idLong").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = title,
            artworkUrl = coverUrl,
            calculatedArtworkUrl = coverUrl,
            trackCount = nbTracks,
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = null,
                urn = "tidal:artist:$artistId"
            ),
            isAlbum = true,
            permalink = idLong.toString(),
            permalinkUrl = optString("url").ifBlank { "https://tidal.com/browse/album/$idLong" },
            urn = "tidal:album:$idLong"
        )
    }

    private fun JSONObject.toTidalPlaylist(): Playlist? {
        val uuid = optString("uuid").ifBlank { optString("id") }
        if (uuid.isBlank()) return null
        val title = optString("title")
        if (title.isBlank()) return null
        val imageUuid = optString("image").ifBlank { optString("squareImage") }
        val coverUrl = tidalCoverUrl(imageUuid)
        val creatorObj = optJSONObject("creator")
        val creatorName = creatorObj?.optString("name") ?: "Tidal"
        val nbTracks = optInt("numberOfTracks")
        val stableId = abs(("tidal:playlist:$uuid").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = title,
            artworkUrl = coverUrl,
            calculatedArtworkUrl = coverUrl,
            trackCount = nbTracks,
            user = User(
                id = abs(creatorName.hashCode().toLong()),
                username = creatorName,
                avatarUrl = null
            ),
            isAlbum = false,
            permalink = uuid,
            permalinkUrl = optString("url").ifBlank { "https://tidal.com/browse/playlist/$uuid" },
            urn = "tidal:playlist:$uuid"
        )
    }

    private fun JSONObject.toTidalArtist(): User? {
        val idLong = optLong("id")
        if (idLong == 0L) return null
        val name = optString("name")
        if (name.isBlank()) return null
        val pictureUuid = optString("picture")
        val coverUrl = tidalCoverUrl(pictureUuid)
        val stableId = abs(("tidal:artist:$idLong").hashCode().toLong())

        return User(
            id = stableId,
            username = name,
            avatarUrl = coverUrl,
            permalink = idLong.toString(),
            urn = "tidal:artist:$idLong"
        )
    }

    private fun tidalCoverUrl(uuid: String?, size: String = "640x640"): String? {
        if (uuid.isNullOrBlank()) return null
        return if (uuid.startsWith("http", ignoreCase = true)) {
            uuid
        } else {
            "https://resources.tidal.com/images/${uuid.replace("-", "/")}/$size.jpg"
        }
    }

    private fun fetchJson(url: String, token: String): JSONObject? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val body = response.body.string()
                JSONObject(body)
            }
        }.getOrNull()
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
