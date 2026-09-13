package com.alananasss.kittytune.data.deezer

import android.net.Uri
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.TrackPublisherMetadata
import com.alananasss.kittytune.domain.User
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.abs

data class DeezerSearchResult(
    val tracks: List<Track> = emptyList(),
    val albums: List<Playlist> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val artists: List<User> = emptyList()
)

object DeezerSearchRepository {
    private const val API_BASE = "https://api.deezer.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun search(query: String, limit: Int = 50): DeezerSearchResult = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext DeezerSearchResult()
        val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")

        coroutineScope {
            val tracksDeferred = async { fetchJson("$API_BASE/search/track?q=$encodedQuery&limit=$limit") }
            val albumsDeferred = async { fetchJson("$API_BASE/search/album?q=$encodedQuery&limit=$limit") }
            val playlistsDeferred = async { fetchJson("$API_BASE/search/playlist?q=$encodedQuery&limit=$limit") }
            val artistsDeferred = async { fetchJson("$API_BASE/search/artist?q=$encodedQuery&limit=$limit") }

            val tracksJson = tracksDeferred.await()
            val albumsJson = albumsDeferred.await()
            val playlistsJson = playlistsDeferred.await()
            val artistsJson = artistsDeferred.await()

            val tracks = tracksJson?.optJSONArray("data")?.mapObjects { it.toDeezerTrack() } ?: emptyList()
            val albums = albumsJson?.optJSONArray("data")?.mapObjects { it.toDeezerAlbum() } ?: emptyList()
            val playlists = playlistsJson?.optJSONArray("data")?.mapObjects { it.toDeezerPlaylist() } ?: emptyList()
            val artists = artistsJson?.optJSONArray("data")?.mapObjects { it.toDeezerArtist() } ?: emptyList()

            DeezerSearchResult(
                tracks = tracks,
                albums = albums,
                playlists = playlists,
                artists = artists
            )
        }
    }

    suspend fun getAlbum(albumId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = albumId.removePrefix("deezer:album:").trim()
        val json = fetchJson("$API_BASE/album/$cleanId") ?: return@withContext null
        val tracksArray = json.optJSONObject("tracks")?.optJSONArray("data")
        val tracks = tracksArray?.mapObjects { it.toDeezerTrack(albumOverride = json) } ?: emptyList()

        val idLong = json.optLong("id")
        val stableId = abs(("deezer:album:$idLong").hashCode().toLong())
        val artistObj = json.optJSONObject("artist")
        val cover = json.optString("cover_xl").ifBlank { json.optString("cover_medium") }

        Playlist(
            id = stableId,
            title = json.optString("title"),
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = json.optInt("nb_tracks", tracks.size),
            user = User(
                id = artistObj?.optLong("id") ?: 0L,
                username = artistObj?.optString("name") ?: "Deezer",
                avatarUrl = artistObj?.optString("picture_medium"),
                urn = artistObj?.optLong("id")?.let { "deezer:artist:$it" }
            ),
            tracks = tracks,
            isAlbum = true,
            releaseDate = json.optString("release_date"),
            permalink = idLong.toString(),
            permalinkUrl = json.optString("link").ifBlank { "https://www.deezer.com/album/$idLong" },
            urn = "deezer:album:$idLong"
        )
    }

    suspend fun getPlaylist(playlistId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = playlistId.removePrefix("deezer:playlist:").trim()
        val json = fetchJson("$API_BASE/playlist/$cleanId") ?: return@withContext null
        val tracksArray = json.optJSONObject("tracks")?.optJSONArray("data")
        val tracks = tracksArray?.mapObjects { it.toDeezerTrack() } ?: emptyList()

        val idLong = json.optLong("id")
        val stableId = abs(("deezer:playlist:$idLong").hashCode().toLong())
        val creatorObj = json.optJSONObject("creator")
        val cover = json.optString("picture_xl").ifBlank { json.optString("picture_medium") }

        Playlist(
            id = stableId,
            title = json.optString("title"),
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = json.optInt("nb_tracks", tracks.size),
            user = User(
                id = creatorObj?.optLong("id") ?: 0L,
                username = creatorObj?.optString("name") ?: "Deezer",
                avatarUrl = null
            ),
            tracks = tracks,
            isAlbum = false,
            description = json.optString("description"),
            permalink = idLong.toString(),
            permalinkUrl = json.optString("link").ifBlank { "https://www.deezer.com/playlist/$idLong" },
            urn = "deezer:playlist:$idLong"
        )
    }

    suspend fun getArtist(artistId: String): Playlist? = withContext(Dispatchers.IO) {
        val cleanId = artistId.removePrefix("deezer:artist:").trim()
        val artistJson = fetchJson("$API_BASE/artist/$cleanId") ?: return@withContext null
        val topTracksJson = fetchJson("$API_BASE/artist/$cleanId/top?limit=50")
        val tracks = topTracksJson?.optJSONArray("data")?.mapObjects { it.toDeezerTrack() } ?: emptyList()

        val idLong = artistJson.optLong("id")
        val stableId = abs(("deezer:artist:$idLong").hashCode().toLong())
        val picture = artistJson.optString("picture_xl").ifBlank { artistJson.optString("picture_medium") }

        Playlist(
            id = stableId,
            title = artistJson.optString("name"),
            artworkUrl = picture,
            calculatedArtworkUrl = picture,
            trackCount = tracks.size,
            user = User(
                id = idLong,
                username = artistJson.optString("name"),
                avatarUrl = picture,
                permalink = idLong.toString(),
                urn = "deezer:artist:$idLong"
            ),
            tracks = tracks,
            isAlbum = false,
            permalink = idLong.toString(),
            permalinkUrl = artistJson.optString("link").ifBlank { "https://www.deezer.com/artist/$idLong" },
            urn = "deezer:artist:$idLong"
        )
    }

    private fun JSONObject.toDeezerTrack(albumOverride: JSONObject? = null): Track? {
        val idLong = optLong("id")
        if (idLong == 0L) return null
        val title = optString("title").ifBlank { optString("title_short") }
        if (title.isBlank()) return null

        val artistObj = optJSONObject("artist")
        val albumObj = albumOverride ?: optJSONObject("album")
        val artistName = artistObj?.optString("name") ?: "Unknown Artist"
        val artistId = artistObj?.optLong("id") ?: 0L
        val albumTitle = albumObj?.optString("title")
        val albumId = albumObj?.optLong("id")?.toString()
        val cover = albumObj?.optString("cover_xl")
            ?: albumObj?.optString("cover_medium")
            ?: optString("cover_xl")
            ?: optString("cover_medium")
        val durationSec = optLong("duration")
        val isrc = optString("isrc").takeIf { it.isNotBlank() }
        val explicit = optBoolean("explicit_lyrics", false)
        val permalink = "deezer:track:$idLong"

        val stableId = abs(permalink.hashCode().toLong())

        return Track(
            id = stableId,
            title = title,
            artworkUrl = cover,
            durationMs = durationSec * 1000L,
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = artistObj?.optString("picture_medium"),
                permalink = artistId.toString(),
                urn = "deezer:artist:$artistId"
            ),
            publisherMetadata = TrackPublisherMetadata(
                artist = artistName,
                albumTitle = albumTitle,
                albumId = albumId,
                isrc = isrc,
                explicit = explicit
            ),
            permalink = permalink,
            permalinkUrl = optString("link").ifBlank { "https://www.deezer.com/track/$idLong" },
            source = "deezer",
            streamable = true
        )
    }

    private fun JSONObject.toDeezerAlbum(): Playlist? {
        val idLong = optLong("id")
        if (idLong == 0L) return null
        val title = optString("title")
        if (title.isBlank()) return null
        val cover = optString("cover_xl").ifBlank { optString("cover_medium") }
        val artistObj = optJSONObject("artist")
        val artistName = artistObj?.optString("name") ?: "Unknown Artist"
        val artistId = artistObj?.optLong("id") ?: 0L
        val nbTracks = optInt("nb_tracks")
        val stableId = abs(("deezer:album:$idLong").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = title,
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = nbTracks,
            user = User(
                id = artistId,
                username = artistName,
                avatarUrl = artistObj?.optString("picture_medium"),
                urn = "deezer:artist:$artistId"
            ),
            isAlbum = true,
            permalink = idLong.toString(),
            permalinkUrl = optString("link").ifBlank { "https://www.deezer.com/album/$idLong" },
            urn = "deezer:album:$idLong"
        )
    }

    private fun JSONObject.toDeezerPlaylist(): Playlist? {
        val idLong = optLong("id")
        if (idLong == 0L) return null
        val title = optString("title")
        if (title.isBlank()) return null
        val cover = optString("picture_xl").ifBlank { optString("picture_medium") }
        val creatorObj = optJSONObject("creator")
        val creatorName = creatorObj?.optString("name") ?: "Deezer"
        val creatorId = creatorObj?.optLong("id") ?: 0L
        val nbTracks = optInt("nb_tracks")
        val stableId = abs(("deezer:playlist:$idLong").hashCode().toLong())

        return Playlist(
            id = stableId,
            title = title,
            artworkUrl = cover,
            calculatedArtworkUrl = cover,
            trackCount = nbTracks,
            user = User(
                id = creatorId,
                username = creatorName,
                avatarUrl = null
            ),
            isAlbum = false,
            permalink = idLong.toString(),
            permalinkUrl = optString("link").ifBlank { "https://www.deezer.com/playlist/$idLong" },
            urn = "deezer:playlist:$idLong"
        )
    }

    private fun JSONObject.toDeezerArtist(): User? {
        val idLong = optLong("id")
        if (idLong == 0L) return null
        val name = optString("name")
        if (name.isBlank()) return null
        val picture = optString("picture_xl").ifBlank { optString("picture_medium") }
        val stableId = abs(("deezer:artist:$idLong").hashCode().toLong())

        return User(
            id = stableId,
            username = name,
            avatarUrl = picture,
            permalink = idLong.toString(),
            urn = "deezer:artist:$idLong"
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
