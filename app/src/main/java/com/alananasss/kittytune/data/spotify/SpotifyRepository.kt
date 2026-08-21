package com.alananasss.kittytune.data.spotify

import android.util.Log
import com.alananasss.kittytune.data.network.ProxyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object SpotifyRepository {

    private const val TAG = "SpotifyRepository"

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val client: OkHttpClient
        get() = ProxyManager.configureOkHttpClient(baseClient.newBuilder()).build()

    suspend fun search(query: String, limit: Int = 20, offset: Int = 0): SpotifySearchResults =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext SpotifySearchResults(query = query)

            val token =
                SpotifyTokenManager.getValidAccessToken() ?: return@withContext SpotifySearchResults(query = query)
            val url = SpotifyPathfinderApi.buildSearchUrl(trimmed, offset = offset, limit = limit)

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("app-platform", "WebPlayer")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                    )
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.code == 401) {
                        SpotifyTokenManager.invalidateToken()
                        return@withContext SpotifySearchResults(query = query)
                    }
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Search request failed with HTTP ${response.code}")
                        return@withContext SpotifySearchResults(query = query)
                    }

                    val bodyStr = response.body?.string() ?: return@withContext SpotifySearchResults(query = query)
                    val json = JSONObject(bodyStr)
                    val data = json.optJSONObject("data") ?: return@withContext SpotifySearchResults(query = query)
                    val searchV2 =
                        data.optJSONObject("searchV2") ?: return@withContext SpotifySearchResults(query = query)

                    val tracks = parseSearchTracks(searchV2.optJSONObject("tracksV2"))
                    val albums = parseSearchAlbums(searchV2.optJSONObject("albumsV2"))
                    val artists = parseSearchArtists(searchV2.optJSONObject("artists"))
                    val playlists = parseSearchPlaylists(searchV2.optJSONObject("playlists"))

                    return@withContext SpotifySearchResults(
                        query = trimmed,
                        tracks = tracks,
                        albums = albums,
                        artists = artists,
                        playlists = playlists,
                        totalTracks = tracks.size
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Spotify search: ${e.message}", e)
                SpotifySearchResults(query = query)
            }
        }

    suspend fun getTrack(trackId: String): SpotifyTrack? = withContext(Dispatchers.IO) {
        val cleanId = extractId(trackId)
        val token = SpotifyTokenManager.getValidAccessToken() ?: return@withContext null
        val url = SpotifyPathfinderApi.buildTrackUrl(cleanId)

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("app-platform", "WebPlayer")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    SpotifyTokenManager.invalidateToken()
                    return@withContext null
                }
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val data = json.optJSONObject("data") ?: return@withContext null
                val trackUnion = data.optJSONObject("trackUnion") ?: return@withContext null

                return@withContext parseTrackNode(trackUnion)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify track $trackId: ${e.message}")
            null
        }
    }

    suspend fun getAlbum(albumId: String): SpotifyAlbum? = withContext(Dispatchers.IO) {
        val cleanId = extractId(albumId)
        val token = SpotifyTokenManager.getValidAccessToken() ?: return@withContext null
        val url = SpotifyPathfinderApi.buildAlbumUrl(cleanId)

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("app-platform", "WebPlayer")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    SpotifyTokenManager.invalidateToken()
                    return@withContext null
                }
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val data = json.optJSONObject("data") ?: return@withContext null
                val albumUnion = data.optJSONObject("albumUnion") ?: return@withContext null

                val name = albumUnion.optString("name", "Unknown Album")
                val coverUrl = extractCoverArt(albumUnion.optJSONObject("coverArt"))
                val dateStr = albumUnion.optJSONObject("date")?.optString("isoString")
                val artists = parseArtistList(albumUnion.optJSONObject("artists")?.optJSONArray("items"))

                val tracksList = mutableListOf<SpotifyTrack>()
                val tracksV2 = albumUnion.optJSONObject("tracksV2")
                val totalCount = tracksV2?.optInt("totalCount", 0) ?: 0
                val items = tracksV2?.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val trackNode = item.optJSONObject("track") ?: continue
                        val track = parseTrackNode(trackNode, defaultAlbumName = name, defaultArtwork = coverUrl)
                        if (track != null) tracksList.add(track)
                    }
                }

                var albumOffset = items?.length() ?: 0
                while (albumOffset < totalCount && tracksList.size < 500) {
                    try {
                        val pageUrl = SpotifyPathfinderApi.buildAlbumUrl(cleanId, offset = albumOffset, limit = 50)
                        val pageReq = Request.Builder()
                            .url(pageUrl)
                            .header("Authorization", "Bearer $token")
                            .header("app-platform", "WebPlayer")
                            .build()
                        val pageRes = client.newCall(pageReq).execute()
                        if (!pageRes.isSuccessful) break
                        val pageBody = pageRes.body?.string() ?: break
                        val pageJson = JSONObject(pageBody)
                        val pageItems = pageJson.optJSONObject("data")
                            ?.optJSONObject("albumUnion")
                            ?.optJSONObject("tracksV2")
                            ?.optJSONArray("items") ?: break
                        if (pageItems.length() == 0) break
                        for (i in 0 until pageItems.length()) {
                            val item = pageItems.optJSONObject(i) ?: continue
                            val trackNode = item.optJSONObject("track") ?: continue
                            val track = parseTrackNode(trackNode, defaultAlbumName = name, defaultArtwork = coverUrl)
                            if (track != null) tracksList.add(track)
                        }
                        albumOffset += pageItems.length()
                    } catch (e: Exception) {
                        Log.w(TAG, "Album pagination stopped early at offset $albumOffset", e)
                        break
                    }
                }

                return@withContext SpotifyAlbum(
                    id = cleanId,
                    name = name,
                    artists = artists,
                    artworkUrl = coverUrl,
                    releaseDate = dateStr,
                    totalTracks = if (totalCount > 0) totalCount else tracksList.size,
                    tracks = tracksList
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify album $albumId: ${e.message}")
            null
        }
    }

    suspend fun getPlaylist(playlistId: String, maxTracks: Int = 1000): SpotifyPlaylist? = withContext(Dispatchers.IO) {
        val cleanId = extractId(playlistId)
        val token = SpotifyTokenManager.getValidAccessToken() ?: return@withContext null
        val url = SpotifyPathfinderApi.buildPlaylistUrl(cleanId, offset = 0, limit = 100)

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("app-platform", "WebPlayer")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    SpotifyTokenManager.invalidateToken()
                    return@withContext null
                }
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val data = json.optJSONObject("data") ?: return@withContext null
                val playlistV2 = data.optJSONObject("playlistV2") ?: return@withContext null

                val name = playlistV2.optString("name", "Spotify Playlist")
                val description = playlistV2.optString("description").ifBlank { null }
                val ownerData = playlistV2.optJSONObject("ownerV2")?.optJSONObject("data")
                val ownerName = ownerData?.optString("name")
                val ownerUri = ownerData?.optString("uri")
                val ownerId = ownerUri?.removePrefix("spotify:user:")?.removePrefix("spotify:artist:")
                    ?: ownerData?.optString("username")?.takeIf { it.isNotBlank() }
                val ownerAvatarUrl = extractImageFromItems(ownerData?.optJSONObject("avatar")?.optJSONArray("sources"))
                    ?: extractImageFromItems(ownerData?.optJSONObject("images")?.optJSONArray("items"))

                val followersCount = playlistV2.optJSONObject("followers")?.optLong("totalCount")
                    ?: playlistV2.optLong("likesCount", 0L).takeIf { it > 0L }
                    ?: playlistV2.optLong("followersCount", 0L).takeIf { it > 0L }

                val artworkUrl = extractImageFromItems(playlistV2.optJSONObject("images")?.optJSONArray("items"))

                val tracksList = mutableListOf<SpotifyTrack>()
                val content = playlistV2.optJSONObject("content")
                val totalCount = content?.optInt("totalCount", 0) ?: 0
                val items = content?.optJSONArray("items")
                if (items != null) {
                    for (i in 0 until items.length()) {
                        val item = items.optJSONObject(i) ?: continue
                        val itemV2 = item.optJSONObject("itemV2") ?: continue
                        val trackData = itemV2.optJSONObject("data") ?: continue
                        val track = parseTrackNode(trackData)
                        if (track != null) tracksList.add(track)
                    }
                }

                // Paginate to fetch all tracks if playlist has more than 100 tracks (e.g. 400+ songs)
                var playlistOffset = items?.length() ?: 0
                while (playlistOffset < totalCount && tracksList.size < maxTracks) {
                    try {
                        val pageUrl = SpotifyPathfinderApi.buildPlaylistUrl(cleanId, offset = playlistOffset, limit = 100)
                        val pageReq = Request.Builder()
                            .url(pageUrl)
                            .header("Authorization", "Bearer $token")
                            .header("app-platform", "WebPlayer")
                            .build()
                        val pageRes = client.newCall(pageReq).execute()
                        if (!pageRes.isSuccessful) break
                        val pageBody = pageRes.body?.string() ?: break
                        val pageJson = JSONObject(pageBody)
                        val pageItems = pageJson.optJSONObject("data")
                            ?.optJSONObject("playlistV2")
                            ?.optJSONObject("content")
                            ?.optJSONArray("items") ?: break
                        if (pageItems.length() == 0) break
                        for (i in 0 until pageItems.length()) {
                            val item = pageItems.optJSONObject(i) ?: continue
                            val itemV2 = item.optJSONObject("itemV2") ?: continue
                            val trackData = itemV2.optJSONObject("data") ?: continue
                            val track = parseTrackNode(trackData)
                            if (track != null) tracksList.add(track)
                        }
                        playlistOffset += pageItems.length()
                    } catch (e: Exception) {
                        Log.w(TAG, "Playlist pagination stopped early at offset $playlistOffset", e)
                        break
                    }
                }

                return@withContext SpotifyPlaylist(
                    id = cleanId,
                    name = name,
                    description = description,
                    ownerName = ownerName,
                    ownerId = ownerId,
                    ownerAvatarUrl = ownerAvatarUrl,
                    artworkUrl = artworkUrl,
                    totalTracks = if (totalCount > 0) totalCount else tracksList.size,
                    followersCount = followersCount,
                    tracks = tracksList
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify playlist $playlistId: ${e.message}")
            null
        }
    }


    suspend fun getArtist(artistId: String): SpotifyArtist? = withContext(Dispatchers.IO) {
        val cleanId = extractId(artistId)
        val token = SpotifyTokenManager.getValidAccessToken() ?: return@withContext null
        val url = SpotifyPathfinderApi.buildArtistUrl(cleanId)

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("app-platform", "WebPlayer")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (response.code == 401) {
                    SpotifyTokenManager.invalidateToken()
                    return@withContext null
                }
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val json = JSONObject(bodyStr)
                val data = json.optJSONObject("data") ?: return@withContext null
                val artistUnion = data.optJSONObject("artistUnion") ?: return@withContext null

                val profile = artistUnion.optJSONObject("profile")
                val name = profile?.optString("name") ?: "Unknown Artist"
                val verification = artistUnion.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
                val isReputationVerified = verification?.optBoolean("isVerified", false) == true
                val isProfileVerified = profile?.optBoolean("isVerified", false) == true
                val isUnionVerified = artistUnion.optBoolean("isVerified", false)
                val verified = isReputationVerified || isProfileVerified || isUnionVerified
                val biography = profile?.optJSONObject("biography")?.optString("text")?.ifBlank { null }

                val visuals = artistUnion.optJSONObject("visuals")
                val avatarUrl = extractImageFromSources(visuals?.optJSONObject("avatarImage")?.optJSONArray("sources"))
                val headerImageUrl =
                    extractImageFromSources(visuals?.optJSONObject("headerImage")?.optJSONArray("sources"))

                val stats = artistUnion.optJSONObject("stats")
                val monthlyListeners = stats?.optLong("monthlyListeners")
                val worldRank = stats?.optInt("worldRank", 0)?.takeIf { it > 0 }
                val followers = stats?.optLong("followers")

                val discography = artistUnion.optJSONObject("discography")

                val topTracks = mutableListOf<SpotifyTrack>()
                val topTrackItems = discography?.optJSONObject("topTracks")?.optJSONArray("items")
                if (topTrackItems != null) {
                    for (i in 0 until topTrackItems.length()) {
                        val item = topTrackItems.optJSONObject(i) ?: continue
                        val trackNode = item.optJSONObject("track") ?: item
                        val track = parseTrackNode(trackNode, defaultAlbumName = null, defaultArtwork = avatarUrl)
                        if (track != null) topTracks.add(track)
                    }
                }

                val popularReleases = parseReleasesGroup(
                    discography?.optJSONObject("popularReleasesV2") ?: discography?.optJSONObject("popularReleases"),
                    name,
                    avatarUrl
                )
                val albums = parseReleasesGroup(discography?.optJSONObject("albums"), name, avatarUrl)
                val singles = parseReleasesGroup(discography?.optJSONObject("singles"), name, avatarUrl)
                val compilations = parseReleasesGroup(discography?.optJSONObject("compilations"), name, avatarUrl)

                val relatedContent = artistUnion.optJSONObject("relatedContent")
                val relatedArtists = mutableListOf<SpotifyArtistRef>()
                val relItems = relatedContent?.optJSONObject("relatedArtists")?.optJSONArray("items")
                if (relItems != null) {
                    for (i in 0 until relItems.length()) {
                        val item = relItems.optJSONObject(i) ?: continue
                        val relProfile = item.optJSONObject("profile")
                        val relName = relProfile?.optString("name") ?: item.optString("name")
                        val relUri = item.optString("uri")
                        val relId = item.optString("id").ifBlank { extractId(relUri) }
                        val relVisuals = item.optJSONObject("visuals")
                        val relAvatar =
                            extractImageFromSources(relVisuals?.optJSONObject("avatarImage")?.optJSONArray("sources"))
                        val relVerification =
                            item.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
                        val relVerified = relVerification?.optBoolean("isVerified", false) == true
                                || relProfile?.optBoolean("isVerified", false) == true
                                || item.optBoolean("isVerified", false)
                        if (relName.isNotBlank()) {
                            relatedArtists.add(
                                SpotifyArtistRef(
                                    id = relId,
                                    name = relName,
                                    uri = relUri,
                                    avatarUrl = relAvatar,
                                    verified = relVerified
                                )
                            )
                        }
                    }
                }

                val appearsOn = parseReleasesGroup(relatedContent?.optJSONObject("appearsOn"), name, avatarUrl)

                val discoveredOn = mutableListOf<SpotifyPlaylist>()
                val discGroups = listOfNotNull(
                    relatedContent?.optJSONObject("featuringV2")?.optJSONArray("items"),
                    relatedContent?.optJSONObject("discoveredOn")?.optJSONArray("items"),
                    relatedContent?.optJSONObject("discoveredOnV2")?.optJSONArray("items")
                )
                for (discItems in discGroups) {
                    for (i in 0 until discItems.length()) {
                        val item = discItems.optJSONObject(i) ?: continue
                        val playData = item.optJSONObject("data") ?: item
                        val pUri = playData.optString("uri")
                        val pName = playData.optString("name", "Spotify Playlist")
                        val pDesc = playData.optString("description").ifBlank { null }
                        val pOwner = playData.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name")
                        val pArt = extractImageFromItems(playData.optJSONObject("images")?.optJSONArray("items"))
                            ?: extractCoverArt(playData.optJSONObject("coverArt"))
                        val pId = extractId(pUri).ifBlank { playData.optString("id") }
                        if (pId.isNotBlank()) {
                            discoveredOn.add(
                                SpotifyPlaylist(
                                    id = pId,
                                    name = pName,
                                    description = pDesc,
                                    ownerName = pOwner,
                                    artworkUrl = pArt
                                )
                            )
                        }
                    }
                }

                val externalLinks = mutableListOf<SpotifyExternalLink>()
                val linkItems = profile?.optJSONObject("externalLinks")?.optJSONArray("items")
                if (linkItems != null) {
                    for (i in 0 until linkItems.length()) {
                        val item = linkItems.optJSONObject(i) ?: continue
                        val linkName = item.optString("name")
                        val linkUrl = item.optString("url")
                        if (linkName.isNotBlank() && linkUrl.isNotBlank()) {
                            externalLinks.add(SpotifyExternalLink(linkName, linkUrl))
                        }
                    }
                }

                return@withContext SpotifyArtist(
                    id = cleanId,
                    name = name,
                    avatarUrl = avatarUrl,
                    headerImageUrl = headerImageUrl,
                    verified = verified,
                    monthlyListeners = monthlyListeners,
                    worldRank = worldRank,
                    followers = followers,
                    biography = biography,
                    topTracks = topTracks,
                    popularReleases = popularReleases,
                    albums = albums.distinctBy { it.id },
                    singles = singles.distinctBy { it.id },
                    compilations = compilations.distinctBy { it.id },
                    appearsOn = appearsOn.distinctBy { it.id },
                    discoveredOn = discoveredOn.distinctBy { it.id },
                    relatedArtists = relatedArtists.distinctBy { it.id },
                    externalLinks = externalLinks
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify artist $artistId: ${e.message}", e)
            null
        }
    }

    fun getCharts(): List<SpotifyChart> {
        return SpotifyPathfinderApi.EDITORIAL_CHARTS
    }

    private fun parseReleasesGroup(
        groupNode: JSONObject?,
        defaultArtist: String,
        defaultArtwork: String?
    ): List<SpotifyAlbum> {
        if (groupNode == null) return emptyList()
        val items = groupNode.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyAlbum>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val releases = item.optJSONObject("releases")?.optJSONArray("items")
            val releaseNode = releases?.optJSONObject(0) ?: item.optJSONObject("data") ?: item
            val uri = releaseNode.optString("uri")
            val id = releaseNode.optString("id").ifBlank { extractId(uri) }
            val name = releaseNode.optString("name", "Unknown Release")
            val coverUrl = extractCoverArt(releaseNode.optJSONObject("coverArt"))
                ?: extractImageFromSources(releaseNode.optJSONArray("images"))
                ?: defaultArtwork
            val dateNode = releaseNode.optJSONObject("date")
            val dateStr = dateNode?.optString("year")
                ?: dateNode?.optInt("year", 0)?.takeIf { it > 0 }?.toString()
                ?: dateNode?.optString("isoString")?.take(4)
                ?: releaseNode.optString("releaseDate").take(4)
            val totalTracks = releaseNode.optJSONObject("tracks")?.optInt("totalCount", 0) ?: 0
            val releaseType = releaseNode.optString("type").ifBlank {
                item.optString("type")
            }.ifBlank {
                if (totalTracks == 1) "SINGLE" else if (totalTracks in 2..6) "EP" else "ALBUM"
            }
            val artists = parseArtistList(releaseNode.optJSONObject("artists")?.optJSONArray("items"))
                .ifEmpty { listOf(SpotifyArtistRef(id = "", name = defaultArtist)) }

            list.add(
                SpotifyAlbum(
                    id = id,
                    name = name,
                    artists = artists,
                    artworkUrl = coverUrl,
                    releaseDate = dateStr,
                    releaseType = releaseType,
                    totalTracks = totalTracks
                )
            )
        }
        return list
    }

    private fun parseSearchTracks(node: JSONObject?): List<SpotifyTrack> {
        if (node == null) return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyTrack>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("item")?.optJSONObject("data") ?: item.optJSONObject("data") ?: continue
            val track = parseTrackNode(data)
            if (track != null) list.add(track)
        }
        return list
    }

    private fun parseSearchAlbums(node: JSONObject?): List<SpotifyAlbum> {
        if (node == null) return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyAlbum>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data") ?: continue
            val uri = data.optString("uri")
            val id = extractId(uri)
            val name = data.optString("name", "Unknown Album")
            val coverUrl = extractCoverArt(data.optJSONObject("coverArt"))
            val artists = parseArtistList(data.optJSONObject("artists")?.optJSONArray("items"))
            val dateStr = data.optJSONObject("date")?.optString("year")

            list.add(
                SpotifyAlbum(
                    id = id,
                    name = name,
                    artists = artists,
                    artworkUrl = coverUrl,
                    releaseDate = dateStr
                )
            )
        }
        return list
    }

    private fun parseSearchArtists(node: JSONObject?): List<SpotifyArtist> {
        if (node == null) return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyArtist>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data") ?: continue
            val uri = data.optString("uri")
            val id = extractId(uri)
            val name = data.optJSONObject("profile")?.optString("name") ?: data.optString("name", "Unknown Artist")
            val avatarUrl = extractImageFromSources(
                data.optJSONObject("visuals")?.optJSONObject("avatarImage")?.optJSONArray("sources")
            )
            val verification = data.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
            val isReputationVerified = verification?.optBoolean("isVerified", false) == true
            val isProfileVerified = data.optJSONObject("profile")?.optBoolean("isVerified", false) == true
            val isDataVerified = data.optBoolean("isVerified", false)
            val verified = isReputationVerified || isProfileVerified || isDataVerified

            list.add(
                SpotifyArtist(
                    id = id,
                    name = name,
                    avatarUrl = avatarUrl,
                    verified = verified
                )
            )
        }
        return list
    }

    private fun parseSearchPlaylists(node: JSONObject?): List<SpotifyPlaylist> {
        if (node == null) return emptyList()
        val items = node.optJSONArray("items") ?: return emptyList()
        val list = mutableListOf<SpotifyPlaylist>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val data = item.optJSONObject("data") ?: continue
            val uri = data.optString("uri")
            val id = extractId(uri)
            val name = data.optString("name", "Spotify Playlist")
            val description = data.optString("description").ifBlank { null }
            val owner = data.optJSONObject("ownerV2")?.optJSONObject("data")?.optString("name")
            val artworkUrl = extractImageFromItems(data.optJSONObject("images")?.optJSONArray("items"))

            list.add(
                SpotifyPlaylist(
                    id = id,
                    name = name,
                    description = description,
                    ownerName = owner,
                    artworkUrl = artworkUrl
                )
            )
        }
        return list
    }

    suspend fun getRadioPlaylistId(seedUri: String): String? = withContext(Dispatchers.IO) {
        val token = SpotifyTokenManager.getValidAccessToken() ?: return@withContext null
        val cleanSeed = when {
            seedUri.startsWith("spotify:track:") || seedUri.startsWith("spotify:artist:") -> seedUri
            seedUri.startsWith("spotify_artist:") -> "spotify:artist:${seedUri.removePrefix("spotify_artist:")}"
            else -> "spotify:track:${extractId(seedUri)}"
        }
        val url = "https://spclient.wg.spotify.com/inspiredby-mix/v2/seed_to_playlist/$cleanSeed"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("app-platform", "WebPlayer")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val match = Regex("spotify:playlist:(37i9dQZF[a-zA-Z0-9]+)").find(bodyStr)
                return@withContext match?.groupValues?.get(1)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve radio playlist ID for $seedUri: ${e.message}")
            null
        }
    }

    suspend fun getRadio(seedId: String, isArtist: Boolean = false): SpotifyPlaylist? = withContext(Dispatchers.IO) {
        val cleanId = extractId(seedId)

        if (cleanId.startsWith("37i9dQZF")) {
            val playlist = getPlaylist(cleanId)
            if (playlist != null && playlist.tracks.isNotEmpty()) return@withContext playlist
        }

        if (isArtist) {
            val artist = getArtist(cleanId)
            if (artist != null) {
                val radioFromArtist = artist.discoveredOn.firstOrNull {
                    it.id.startsWith("37i9dQZF1E4") || (it.name.contains(
                        "Radio",
                        ignoreCase = true
                    ) && !it.name.contains("This Is", ignoreCase = true))
                } ?: artist.discoveredOn.firstOrNull { it.id.startsWith("37i9dQZF") }

                if (radioFromArtist != null) {
                    val fullPlaylist = getPlaylist(radioFromArtist.id)
                    if (fullPlaylist != null && fullPlaylist.tracks.isNotEmpty()) {
                        return@withContext fullPlaylist
                    }
                }

                val searchRes = search("${artist.name} Radio")
                val searchRadio = searchRes.playlists.firstOrNull {
                    it.id.startsWith("37i9dQZF1E4") || (it.name.contains(
                        "Radio",
                        ignoreCase = true
                    ) && it.name.contains(artist.name, ignoreCase = true))
                } ?: searchRes.playlists.firstOrNull {
                    it.name.contains("Radio", ignoreCase = true) || it.id.startsWith("37i9dQZF")
                }

                if (searchRadio != null) {
                    val fullPlaylist = getPlaylist(searchRadio.id)
                    if (fullPlaylist != null && fullPlaylist.tracks.isNotEmpty()) {
                        return@withContext fullPlaylist
                    }
                }

                val thisIsSearch = search("This Is ${artist.name}")
                val thisIsPl = thisIsSearch.playlists.firstOrNull { it.name.contains("This Is", ignoreCase = true) }
                if (thisIsPl != null) {
                    val fullPlaylist = getPlaylist(thisIsPl.id)
                    if (fullPlaylist != null && fullPlaylist.tracks.isNotEmpty()) {
                        return@withContext fullPlaylist
                    }
                }
            }

            val seedUri = "spotify:artist:$cleanId"
            val radioPlaylistId = getRadioPlaylistId(seedUri)
            if (!radioPlaylistId.isNullOrBlank()) {
                val playlist = getPlaylist(radioPlaylistId)
                if (playlist != null && playlist.tracks.isNotEmpty()) return@withContext playlist
            }

            if (artist != null && artist.topTracks.isNotEmpty()) {
                val dynamicTracks = mutableListOf<SpotifyTrack>()
                dynamicTracks.addAll(artist.topTracks)
                for (rel in artist.relatedArtists.take(5)) {
                    val relArtist = getArtist(rel.id)
                    if (relArtist != null) {
                        dynamicTracks.addAll(relArtist.topTracks.take(4))
                    }
                }
                return@withContext SpotifyPlaylist(
                    id = "spotify_radio:$cleanId",
                    name = "${artist.name} Radio",
                    description = "Radio inspired by ${artist.name}",
                    artworkUrl = artist.avatarUrl ?: artist.headerImageUrl,
                    ownerName = "Spotify",
                    tracks = dynamicTracks.distinctBy { it.id }
                )
            }
        } else {
            val seedUri = "spotify:track:$cleanId"
            val radioPlaylistId = getRadioPlaylistId(seedUri)
            if (!radioPlaylistId.isNullOrBlank()) {
                val playlist = getPlaylist(radioPlaylistId)
                if (playlist != null && playlist.tracks.isNotEmpty()) return@withContext playlist
            }
            val recTracks = getRadioTracks(cleanId)
            if (recTracks.isNotEmpty()) {
                val seedTrack = recTracks.firstOrNull()
                return@withContext SpotifyPlaylist(
                    id = "spotify_radio:$cleanId",
                    name = if (seedTrack != null) "${seedTrack.name} Radio" else "Spotify Radio",
                    artworkUrl = seedTrack?.artworkUrl,
                    ownerName = "Spotify",
                    tracks = recTracks
                )
            }
        }
        return@withContext null
    }

    suspend fun getRadioTracks(trackId: String): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val cleanId = extractId(trackId)
        val seedTrack = getTrack(cleanId) ?: return@withContext emptyList()
        val radioTracks = mutableListOf<SpotifyTrack>()
        radioTracks.add(seedTrack)

        try {
            val token = SpotifyTokenManager.getValidAccessToken()
            if (!token.isNullOrEmpty()) {
                val recUrl = "https://api.spotify.com/v1/recommendations?seed_tracks=$cleanId&limit=50"
                val request = Request.Builder()
                    .url(recUrl)
                    .header("Authorization", "Bearer $token")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                    )
                    .build()

                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyStr = response.body?.string()
                        if (!bodyStr.isNullOrBlank()) {
                            val json = JSONObject(bodyStr)
                            val recTracksArr = json.optJSONArray("tracks")
                            if (recTracksArr != null && recTracksArr.length() > 0) {
                                for (i in 0 until recTracksArr.length()) {
                                    val item = recTracksArr.optJSONObject(i) ?: continue
                                    val t = parseWebApiTrackNode(item)
                                    if (t != null && t.id != seedTrack.id) {
                                        radioTracks.add(t)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Spotify recommendations API failed: ${e.message}")
        }

        if (radioTracks.size < 10) {
            for (artistRef in seedTrack.artists) {
                if (artistRef.id.isNotBlank()) {
                    val artistObj = getArtist(artistRef.id)
                    if (artistObj != null) {
                        val otherTracks = artistObj.topTracks.filter { it.id != seedTrack.id }
                        radioTracks.addAll(otherTracks)

                        for (rel in artistObj.relatedArtists.take(5)) {
                            val relObj = getArtist(rel.id)
                            if (relObj != null) {
                                radioTracks.addAll(relObj.topTracks.take(4))
                            }
                        }
                    }
                }
            }
        }

        return@withContext radioTracks.distinctBy { it.id }
    }

    private fun parseWebApiTrackNode(node: JSONObject): SpotifyTrack? {
        val uri = node.optString("uri")
        val name = node.optString("name")
        if (name.isBlank()) return null
        val id = node.optString("id").ifBlank { extractId(uri) }
        val durationMs = node.optLong("duration_ms", 0L)
        val explicit = node.optBoolean("explicit", false)
        val isPlayable = node.optBoolean("is_playable", true)

        val albumNode = node.optJSONObject("album")
        val albumName = albumNode?.optString("name")
        val albumId = albumNode?.optString("id")
        val artworkUrl = extractImageFromSources(albumNode?.optJSONArray("images"))
        val releaseDate = albumNode?.optString("release_date")

        val artistsArr = node.optJSONArray("artists")
        val artistsList = mutableListOf<SpotifyArtistRef>()
        if (artistsArr != null) {
            for (i in 0 until artistsArr.length()) {
                val artObj = artistsArr.optJSONObject(i) ?: continue
                val artName = artObj.optString("name")
                val artId = artObj.optString("id")
                val artUri = artObj.optString("uri")
                if (artName.isNotBlank()) {
                    artistsList.add(SpotifyArtistRef(id = artId, name = artName, uri = artUri))
                }
            }
        }

        return SpotifyTrack(
            id = id,
            name = name,
            durationMs = durationMs,
            artists = artistsList,
            albumName = albumName,
            albumId = albumId,
            artworkUrl = artworkUrl,
            releaseDate = releaseDate,
            explicit = explicit,
            isPlayable = isPlayable,
            shareUrl = "https://open.spotify.com/track/$id"
        )
    }

    suspend fun getCredits(trackId: String): SpotifyCredits? = withContext(Dispatchers.IO) {
        val cleanId = extractId(trackId)
        val token = SpotifyTokenManager.getValidAccessToken() ?: return@withContext null
        val url = "https://spclient.wg.spotify.com/track-credits-view/v0/experimental/$cleanId/credits"

        try {
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .header("app-platform", "WebPlayer")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
                )
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyStr = response.body?.string()
                    if (!bodyStr.isNullOrBlank()) {
                        val json = JSONObject(bodyStr)
                        val trackTitle = json.optString("trackTitle")
                        val trackUri = json.optString("trackUri")

                        val sourceNames = mutableListOf<String>()
                        val sourcesArr = json.optJSONArray("sourceNames")
                        if (sourcesArr != null) {
                            for (i in 0 until sourcesArr.length()) {
                                val s = sourcesArr.optString(i)
                                if (s.isNotBlank()) sourceNames.add(s)
                            }
                        }

                        val roles = mutableListOf<SpotifyCreditRole>()
                        val roleCreditsArr = json.optJSONArray("roleCredits")
                        if (roleCreditsArr != null) {
                            for (i in 0 until roleCreditsArr.length()) {
                                val roleObj = roleCreditsArr.optJSONObject(i) ?: continue
                                val roleTitle = roleObj.optString("roleTitle")
                                val artistsList = mutableListOf<SpotifyCreditArtist>()
                                val artistsArr = roleObj.optJSONArray("artists")
                                if (artistsArr != null) {
                                    for (j in 0 until artistsArr.length()) {
                                        val artObj = artistsArr.optJSONObject(j) ?: continue
                                        val name = artObj.optString("name")
                                        val uri = artObj.optString("uri")
                                        val id = extractId(uri)
                                        val img = artObj.optString("imageUri").ifBlank { null }
                                        val subroles = mutableListOf<String>()
                                        val subArr = artObj.optJSONArray("subroles")
                                        if (subArr != null) {
                                            for (k in 0 until subArr.length()) {
                                                val sub = subArr.optString(k)
                                                if (sub.isNotBlank()) subroles.add(sub)
                                            }
                                        }
                                        artistsList.add(
                                            SpotifyCreditArtist(
                                                id = id,
                                                name = name,
                                                uri = uri,
                                                imageUri = img,
                                                subroles = subroles
                                            )
                                        )
                                    }
                                }
                                roles.add(SpotifyCreditRole(roleTitle = roleTitle, artists = artistsList))
                            }
                        }

                        return@withContext SpotifyCredits(
                            trackTitle = trackTitle,
                            trackUri = trackUri,
                            roles = roles,
                            sourceNames = sourceNames
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify credits for $trackId: ${e.message}")
        }

        val track = getTrack(cleanId) ?: return@withContext null
        val fallbackRoles = mutableListOf<SpotifyCreditRole>()
        if (track.artists.isNotEmpty()) {
            val perfArtists = track.artists.mapIndexed { idx, art ->
                SpotifyCreditArtist(
                    id = art.id,
                    name = art.name,
                    uri = art.uri,
                    imageUri = art.avatarUrl,
                    subroles = if (idx == 0) listOf("Main Artist") else listOf("Featured Artist")
                )
            }
            fallbackRoles.add(SpotifyCreditRole(roleTitle = "Performers", artists = perfArtists))
        }

        val fallbackSources = listOfNotNull(track.publisher?.takeIf { it.isNotBlank() })

        return@withContext SpotifyCredits(
            trackTitle = track.name,
            trackUri = "spotify:track:$cleanId",
            roles = fallbackRoles,
            sourceNames = fallbackSources
        )
    }

    private fun parseTrackNode(
        node: JSONObject,
        defaultAlbumName: String? = null,
        defaultArtwork: String? = null
    ): SpotifyTrack? {
        val uri = node.optString("uri")
        val name = node.optString("name")
        if (uri.isBlank() || name.isBlank()) return null

        val id = node.optString("id").ifBlank { extractId(uri) }
        val durationMs = node.optJSONObject("duration")?.optLong("totalMilliseconds")
            ?: node.optLong("duration", 0L)

        val isPlayable = node.optJSONObject("playability")?.optBoolean("playable", true) ?: true
        val explicit = node.optJSONObject("contentRating")?.optString("label") == "EXPLICIT"

        val albumNode = node.optJSONObject("albumOfTrack") ?: node.optJSONObject("album")
        val albumName = albumNode?.optString("name") ?: defaultAlbumName
        val albumId = albumNode?.optString("uri")?.let { extractId(it) } ?: albumNode?.optString("id")
        val artworkUrl = extractCoverArt(albumNode?.optJSONObject("coverArt"))
            ?: extractImageFromSources(albumNode?.optJSONArray("images"))
            ?: defaultArtwork

        val releaseDate = albumNode?.optJSONObject("date")?.optString("isoString")
            ?: albumNode?.optJSONObject("date")?.optString("year")
            ?: albumNode?.optString("release_date")
            ?: node.optJSONObject("date")?.optString("isoString")

        val playCount = node.optString("playcount").toLongOrNull()
            ?: node.optLong("playcount", 0L).takeIf { it > 0 }

        val label = albumNode?.optString("label")
        val copyrightItems = albumNode?.optJSONObject("copyright")?.optJSONArray("items")
        val copyrightText = if (copyrightItems != null && copyrightItems.length() > 0) {
            copyrightItems.optJSONObject(0)?.optString("text")
        } else null
        val publisher = label ?: copyrightText

        val artistsList = mutableListOf<SpotifyArtistRef>()
        val firstArtistItems = node.optJSONObject("firstArtist")?.optJSONArray("items")
        val otherArtistItems = node.optJSONObject("otherArtists")?.optJSONArray("items")
        if (firstArtistItems != null || otherArtistItems != null) {
            artistsList.addAll(parseArtistList(firstArtistItems))
            artistsList.addAll(parseArtistList(otherArtistItems))
        }
        if (artistsList.isEmpty()) {
            val generalArtists = node.optJSONObject("artists")?.optJSONArray("items")
            artistsList.addAll(parseArtistList(generalArtists))
        }

        return SpotifyTrack(
            id = id,
            name = name,
            durationMs = durationMs,
            artists = artistsList,
            albumName = albumName,
            albumId = albumId,
            artworkUrl = artworkUrl,
            releaseDate = releaseDate,
            explicit = explicit,
            isPlayable = isPlayable,
            shareUrl = "https://open.spotify.com/track/$id",
            playCount = playCount,
            publisher = publisher
        )
    }

    private fun parseArtistList(items: JSONArray?): List<SpotifyArtistRef> {
        if (items == null) return emptyList()
        val list = mutableListOf<SpotifyArtistRef>()
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val profile = item.optJSONObject("profile") ?: item
            val name = profile.optString("name")
            val uri = item.optString("uri").ifBlank { profile.optString("uri") }
            val id = extractId(uri)
            val visuals = item.optJSONObject("visuals") ?: profile.optJSONObject("visuals")
            val avatarUrl =
                extractImageFromSources(visuals?.optJSONObject("avatarImage")?.optJSONArray("sources"))
            val verification = item.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
                ?: profile.optJSONObject("onPlatformReputationTrait")?.optJSONObject("verification")
            val verified = verification?.optBoolean("isVerified", false) == true
                    || profile.optBoolean("isVerified", false)
                    || item.optBoolean("isVerified", false)
            if (name.isNotBlank()) {
                list.add(
                    SpotifyArtistRef(
                        id = id,
                        name = name,
                        uri = uri,
                        avatarUrl = avatarUrl,
                        verified = verified
                    )
                )
            }
        }
        return list
    }

    private fun extractCoverArt(coverArtNode: JSONObject?): String? {
        if (coverArtNode == null) return null
        val sources = coverArtNode.optJSONArray("sources") ?: return null
        return extractImageFromSources(sources)
    }

    private fun extractImageFromSources(sources: JSONArray?): String? {
        if (sources == null || sources.length() == 0) return null
        var bestUrl: String? = null
        var maxWidth = 0
        for (i in 0 until sources.length()) {
            val s = sources.optJSONObject(i) ?: continue
            val url = s.optString("url")
            val width = s.optInt("width", 0)
            if (url.isNotBlank() && (bestUrl == null || width > maxWidth)) {
                bestUrl = url
                maxWidth = width
            }
        }
        return bestUrl
    }

    private fun extractImageFromItems(items: JSONArray?): String? {
        if (items == null || items.length() == 0) return null
        val first = items.optJSONObject(0) ?: return null
        return extractImageFromSources(first.optJSONArray("sources"))
    }

    fun extractId(input: String): String {
        return input.trim()
            .removePrefix("spotify:track:")
            .removePrefix("spotify:album:")
            .removePrefix("spotify:artist:")
            .removePrefix("spotify:playlist:")
            .removePrefix("spotify:user:spotify:playlist:")
            .removePrefix("spotify:")
            .removePrefix("spotify_artist:")
            .removePrefix("spotify_radio:")
            .removePrefix("spotify_album:")
            .removePrefix("spotify_playlist:")
            .removePrefix("station_artist:")
            .removePrefix("station_spotify:")
            .removePrefix("station:")
            .removePrefix("profile:")
            .substringAfterLast("/")
            .substringBefore("?")
            .trim()
    }
}
