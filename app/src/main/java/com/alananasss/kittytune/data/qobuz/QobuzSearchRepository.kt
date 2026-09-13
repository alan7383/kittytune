package com.alananasss.kittytune.data.qobuz

import android.content.Context
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class QobuzSearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artists: List<User> = emptyList()
)

object QobuzSearchRepository {
    private const val DEFAULT_BASE = "https://qobuz-dll.vercel.app"

    private val client = OkHttpClient.Builder()
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
        val baseUrls = getBaseUrls(context)

        for (base in baseUrls) {
            val url = "$base/api/get-music?q=$encodedQuery&offset=0"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue

            val tracks = data.optJSONObject("tracks")?.optJSONArray("items")?.mapObjects { it.toQobuzTrack() } ?: emptyList()
            val albums = data.optJSONObject("albums")?.optJSONArray("items")?.mapObjects { it.toQobuzAlbum() } ?: emptyList()
            val artists = data.optJSONObject("artists")?.optJSONArray("items")?.mapObjects { it.toQobuzArtist() } ?: emptyList()

            return@withContext QobuzSearchResult(
                tracks = tracks,
                albums = albums,
                playlists = emptyList(), // Qobuz public resolver does not support fetching playlist tracks
                artists = artists
            )
        }

        QobuzSearchResult()
    }

    suspend fun getAlbum(context: Context? = null, albumId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix("qobuz:album:").trim()
        val baseUrls = getBaseUrls(context)

        for (base in baseUrls) {
            val url = "$base/api/get-album?album_id=$cleanId"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue

            val tracksArray = data.optJSONObject("tracks")?.optJSONArray("items")
            val tracks = tracksArray?.mapObjects { it.toQobuzTrack(albumOverride = data) } ?: emptyList()

            val title = data.optString("title")
            val artistObj = data.optJSONObject("artist")
            val artistName = artistObj?.extractName() ?: "Qobuz"
            val artistId = artistObj?.optLong("id") ?: 0L
            val imageObj = data.optJSONObject("image")
            val cover = imageObj?.optString("large")
                ?: imageObj?.optString("small")
                ?: imageObj?.optString("thumbnail")
            val stableId = abs(("qobuz:album:$cleanId").hashCode().toLong())

            return@withContext Playlist(
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

        null
    }

    suspend fun getArtist(context: Context? = null, artistId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = artistId.removePrefix("qobuz:artist:").trim()
        val baseUrls = getBaseUrls(context)

        for (base in baseUrls) {
            val url = "$base/api/get-artist?artist_id=$cleanId"
            val root = fetchJson(url) ?: continue
            if (!root.optBoolean("success", false)) continue
            val data = root.optJSONObject("data") ?: continue
            val artist = data.optJSONObject("artist") ?: continue

            val topTracksArray = artist.optJSONArray("top_tracks")
            val topTracks = topTracksArray?.mapObjects { it.toQobuzTrack() } ?: emptyList()

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
            permalinkUrl = optString("url").ifBlank { "https://www.qobuz.com/track/$id" },
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
        val tracksCount = optInt("tracks_count")
        val stableId = abs(("qobuz:playlist:$id").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = name,
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = tracksCount,
            user = User(
                id = abs(ownerName.hashCode().toLong()),
                username = ownerName,
                avatarUrl = null
            ),
            isAlbum = false,
            description = optString("description"),
            permalink = id,
            permalinkUrl = "https://www.qobuz.com/playlist/$id",
            urn = "qobuz:playlist:$id"
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

    private fun fetchJson(url: String): JSONObject? {
        return runCatching {
            val request = Request.Builder()
                .url(url)
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
