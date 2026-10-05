package com.alananasss.kittytune.data.spotify

import android.util.Log
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.network.ProxyManager
import com.alananasss.kittytune.utils.NetworkUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.random.Random

object SpotifyRepository {

    private const val TAG = "SpotifyRepository"
    private val gson = Gson()
    private val albumCache = ConcurrentHashMap<String, SpotifyAlbum>()
    private val playlistCache = ConcurrentHashMap<String, SpotifyPlaylist>()

    /**
     * Browser-like User-Agent pool (mirrors the web player fingerprinting).
     * One agent is picked per app session and kept for every request so the
     * identity stays consistent across calls.
     */
    private val USER_AGENTS = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:134.0) Gecko/20100101 Firefox/134.0"
    )
    private val userAgent: String by lazy { USER_AGENTS[Random.nextInt(USER_AGENTS.size)] }

    private const val MAX_ATTEMPTS = 4
    private const val BACKOFF_BASE_MS = 500L
    private const val BACKOFF_MAX_MS = 8_000L

    private enum class FetchOutcome { SUCCESS, RETRY_NEW_TOKEN, RETRY_BACKOFF, GIVE_UP }

    private fun getAlbumCacheFile(id: String): File {
        val dir = File(KittyTuneApp.instance.filesDir, "spotify_albums").apply { mkdirs() }
        return File(dir, "$id.json")
    }

    private fun getPlaylistCacheFile(id: String): File {
        val dir = File(KittyTuneApp.instance.filesDir, "spotify_playlists").apply { mkdirs() }
        return File(dir, "$id.json")
    }

    private val baseClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val client: OkHttpClient
        get() = ProxyManager.configureOkHttpClient(baseClient.newBuilder()).build()

    /**
     * Central GET used by every bearer-authenticated endpoint: one
     * refresh-and-retry on HTTP 401 (the anonymous access token is short-lived
     * and rotating it out silently is what used to leave playlists stuck on
     * "0 tracks"), exponential backoff with jitter on transient failures
     * (429 / 5xx / network), honoring Retry-After, and explicit detection of
     * rotated persisted-query hashes so a contract change is loud in the log
     * instead of showing up as an empty screen.
     */
    private suspend fun <T> execute(
        url: String,
        parse: (JSONObject) -> T?
    ): T? = withContext(Dispatchers.IO) {
        var attempt = 0
        var refreshedAfter401 = false

        while (attempt < MAX_ATTEMPTS) {
            val token = SpotifyTokenManager.getValidAccessToken() ?: return@withContext null
            var outcome = FetchOutcome.GIVE_UP
            var parsed: T? = null
            var retryAfterMs = BACKOFF_BASE_MS

            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .header("app-platform", "WebPlayer")
                    .header("Accept", "application/json")
                    .header("User-Agent", userAgent)
                    .build()

                client.newCall(request).execute().use { response ->
                    when {
                        response.code == 401 -> {
                            // Anonymous token rejected: refresh once, then give up.
                            SpotifyTokenManager.invalidateToken()
                            outcome =
                                if (refreshedAfter401) FetchOutcome.GIVE_UP else FetchOutcome.RETRY_NEW_TOKEN
                            refreshedAfter401 = true
                        }
                        response.code == 429 || response.code >= 500 -> {
                            response.header("Retry-After")?.toLongOrNull()?.let {
                                retryAfterMs = (it * 1000L).coerceAtMost(BACKOFF_MAX_MS)
                            }
                            outcome = FetchOutcome.RETRY_BACKOFF
                        }
                        !response.isSuccessful -> {
                            Log.w(TAG, "Spotify request failed with HTTP ${response.code} for $url")
                            FetchOutcome.GIVE_UP
                        }
                        else -> {
                            val bodyStr = response.body?.string()
                            if (bodyStr.isNullOrBlank()) {
                                outcome = FetchOutcome.GIVE_UP
                            } else {
                                try {
                                    val json = JSONObject(bodyStr)
                                    val errors = json.optJSONArray("errors")
                                    if (errors != null && hasPersistedQueryNotFound(errors)) {
                                        Log.e(
                                            TAG,
                                            "Spotify rejected a persisted-query hash (PersistedQueryNotFound); " +
                                                "the internal API contract changed and needs an update."
                                        )
                                        outcome = FetchOutcome.GIVE_UP
                                    } else {
                                        parsed = parse(json)
                                        outcome = if (parsed != null) FetchOutcome.SUCCESS else FetchOutcome.GIVE_UP
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Failed to parse Spotify response: ${e.message}")
                                    outcome = FetchOutcome.GIVE_UP
                                }
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                outcome = FetchOutcome.RETRY_BACKOFF
            } catch (e: Exception) {
                Log.w(TAG, "Spotify request error: ${e.message}")
                outcome = FetchOutcome.GIVE_UP
            }

            when (outcome) {
                FetchOutcome.SUCCESS -> return@withContext parsed
                FetchOutcome.RETRY_NEW_TOKEN -> attempt++
                FetchOutcome.RETRY_BACKOFF -> {
                    if (++attempt >= MAX_ATTEMPTS) return@withContext null
                    val backoff = (retryAfterMs shl (attempt - 1)).coerceAtMost(BACKOFF_MAX_MS)
                    delay(backoff + Random.nextLong(backoff / 4 + 1))
                }
                FetchOutcome.GIVE_UP -> return@withContext null
            }
        }
        null
    }

    private fun hasPersistedQueryNotFound(errors: JSONArray): Boolean {
        for (i in 0 until errors.length()) {
            if (errors.optJSONObject(i)?.optString("message") == "PersistedQueryNotFound") return true
        }
        return false
    }

    /** Fetch a pathfinder GraphQL query and return its `data` envelope. */
    private suspend fun fetchPathfinderData(url: String): JSONObject? =
        execute(url) { it.optJSONObject("data") }

    /**
     * Fetch any bearer-authenticated JSON endpoint that is not a pathfinder query
     * (the seed-to-playlist and track-credits endpoints). Returns the whole body.
     */
    private suspend fun fetchJsonObject(url: String): JSONObject? = execute(url) { it }

    suspend fun search(query: String, limit: Int = 20, offset: Int = 0): SpotifySearchResults =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@withContext SpotifySearchResults(query = query)

            val url = SpotifyPathfinderApi.buildSearchUrl(trimmed, offset = offset, limit = limit)

            try {
                val searchV2 = fetchPathfinderData(url)?.optJSONObject("searchV2")
                    ?: return@withContext SpotifySearchResults(query = query)

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
            } catch (e: Exception) {
                Log.e(TAG, "Exception during Spotify search: ${e.message}", e)
                SpotifySearchResults(query = query)
            }
        }

    suspend fun getTrack(trackId: String): SpotifyTrack? = withContext(Dispatchers.IO) {
        val cleanId = extractId(trackId)
        if (cleanId.isBlank()) return@withContext null

        try {
            fetchPathfinderData(SpotifyPathfinderApi.buildTrackUrl(cleanId))
                ?.optJSONObject("trackUnion")
                ?.let { return@withContext parseTrackNode(it) }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify track $trackId: ${e.message}")
            null
        }
    }

    suspend fun getAlbum(albumId: String): SpotifyAlbum? = withContext(Dispatchers.IO) {
        val cleanId = extractId(albumId)
        if (cleanId.isBlank()) {
            Log.w(TAG, "Refusing to fetch a Spotify album for blank id '$albumId'")
            return@withContext null
        }
        albumCache[cleanId]?.let { return@withContext it }
        var offline = false
        try {
            val file = getAlbumCacheFile(cleanId)
            if (file.exists()) {
                val cached = gson.fromJson(file.readText(), SpotifyAlbum::class.java)
                if (cached != null) {
                    albumCache[cleanId] = cached
                    if (!NetworkUtils.isInternetAvailable(KittyTuneApp.instance)) {
                        offline = true
                        return@withContext cached
                    }
                }
            }
        } catch (_: Exception) {}

        if (offline) return@withContext null

        return@withContext try {
            val albumUnion = fetchPathfinderData(SpotifyPathfinderApi.buildAlbumUrl(cleanId))
                ?.optJSONObject("albumUnion")
                ?: return@withContext readCachedAlbum(cleanId)

            val rawName = albumUnion.optString("name").trim()
            val name = rawName.ifBlank { "Unknown Album" }
            val coverUrl = extractCoverArt(albumUnion.optJSONObject("coverArt"))
            val dateStr = albumUnion.optJSONObject("date")?.optString("isoString")
            val artists = parseArtistList(albumUnion.optJSONObject("artists")?.optJSONArray("items"))
                .distinctBy { it.id.ifBlank { it.name } }

            val tracksList = mutableListOf<SpotifyTrack>()
            val tracksV2 = albumUnion.optJSONObject("tracksV2")
            val totalCount = tracksV2?.optInt("totalCount", 0) ?: 0

            suspend fun collect(items: JSONArray?) {
                if (items == null) return
                for (i in 0 until items.length()) {
                    val trackNode = items.optJSONObject(i)?.optJSONObject("track") ?: continue
                    parseTrackNode(
                        trackNode,
                        defaultAlbumName = name,
                        defaultArtwork = coverUrl
                    )?.let(tracksList::add)
                }
            }

            collect(tracksV2?.optJSONArray("items"))

            // Paginate through the remaining album tracks (50 per page, hard cap 500).
            var albumOffset = tracksV2?.optJSONArray("items")?.length() ?: 0
            while (albumOffset < totalCount && tracksList.size < 500) {
                val pageItems = fetchPathfinderData(
                    SpotifyPathfinderApi.buildAlbumUrl(cleanId, offset = albumOffset, limit = 50)
                )?.optJSONObject("albumUnion")?.optJSONObject("tracksV2")?.optJSONArray("items")
                if (pageItems == null || pageItems.length() == 0) break
                collect(pageItems)
                albumOffset += pageItems.length()
            }

            // Same empty-shell trap as playlists: never persist an id Spotify rejected.
            if (rawName.isBlank() && tracksList.isEmpty() && totalCount == 0) {
                Log.w(TAG, "Spotify returned an empty shell for album $cleanId; not caching it")
                return@withContext readCachedAlbum(cleanId)
            }

            val album = SpotifyAlbum(
                id = cleanId,
                name = name,
                artists = artists,
                artworkUrl = coverUrl,
                releaseDate = dateStr,
                totalTracks = if (totalCount > 0) totalCount else tracksList.size,
                tracks = tracksList
            )
            albumCache[cleanId] = album
            try {
                getAlbumCacheFile(cleanId).writeText(gson.toJson(album))
            } catch (_: Exception) {}
            album
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify album $albumId: ${e.message}")
            readCachedAlbum(cleanId)
        }
    }

    /** Last-resort read of the on-disk album cache, ignoring any in-memory entry. */
    private fun readCachedAlbum(cleanId: String): SpotifyAlbum? = try {
        val file = getAlbumCacheFile(cleanId)
        if (file.exists()) gson.fromJson(file.readText(), SpotifyAlbum::class.java) else null
    } catch (_: Exception) {
        null
    }

    suspend fun getPlaylist(playlistId: String, maxTracks: Int = 1000): SpotifyPlaylist? = withContext(Dispatchers.IO) {
        val cleanId = extractId(playlistId)
        if (cleanId.isBlank()) {
            Log.w(TAG, "Refusing to fetch a Spotify playlist for blank id '$playlistId'")
            return@withContext null
        }
        playlistCache[cleanId]?.let { return@withContext it }
        var offline = false
        try {
            val file = getPlaylistCacheFile(cleanId)
            if (file.exists()) {
                val cached = gson.fromJson(file.readText(), SpotifyPlaylist::class.java)
                if (cached != null) {
                    playlistCache[cleanId] = cached
                    if (!NetworkUtils.isInternetAvailable(KittyTuneApp.instance)) {
                        offline = true
                        return@withContext cached
                    }
                }
            }
        } catch (_: Exception) {}

        if (offline) return@withContext null

        return@withContext try {
            val playlistV2 = fetchPathfinderData(
                SpotifyPathfinderApi.buildPlaylistUrl(cleanId, offset = 0, limit = 100)
            )?.optJSONObject("playlistV2")
                ?: return@withContext readCachedPlaylist(cleanId)

            val rawName = playlistV2.optString("name").trim()
            val name = rawName.ifBlank { "Spotify Playlist" }
            val description = playlistV2.optString("description").ifBlank { null }
            val ownerData = playlistV2.optJSONObject("ownerV2")?.optJSONObject("data")
            val ownerName = ownerData?.optString("name")
            val ownerUri = ownerData?.optString("uri")
            val ownerId = ownerUri?.let { extractId(it) }?.ifBlank { null }
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

            suspend fun collect(items: JSONArray?) {
                if (items == null) return
                for (i in 0 until items.length()) {
                    val trackData = items.optJSONObject(i)
                        ?.optJSONObject("itemV2")?.optJSONObject("data") ?: continue
                    parseTrackNode(trackData)?.let(tracksList::add)
                }
            }

            collect(content?.optJSONArray("items"))

            // Paginate through the remaining tracks (100 per page).
            var playlistOffset = content?.optJSONArray("items")?.length() ?: 0
            while (playlistOffset < totalCount && tracksList.size < maxTracks) {
                val pageItems = fetchPathfinderData(
                    SpotifyPathfinderApi.buildPlaylistUrl(cleanId, offset = playlistOffset, limit = 100)
                )?.optJSONObject("playlistV2")?.optJSONObject("content")?.optJSONArray("items")
                if (pageItems == null || pageItems.length() == 0) break
                collect(pageItems)
                playlistOffset += pageItems.length()
            }

            // Spotify answers an id it does not recognise with a name-less, content-less
            // shell. Caching that would freeze the failure for the whole session, and it
            // is what made a broken id look like a genuinely empty playlist.
            if (rawName.isBlank() && tracksList.isEmpty() && totalCount == 0) {
                Log.w(TAG, "Spotify returned an empty shell for playlist $cleanId; not caching it")
                return@withContext readCachedPlaylist(cleanId)
            }

            val playlist = SpotifyPlaylist(
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
            playlistCache[cleanId] = playlist
            try {
                getPlaylistCacheFile(cleanId).writeText(gson.toJson(playlist))
            } catch (_: Exception) {}
            playlist
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify playlist $playlistId: ${e.message}")
            readCachedPlaylist(cleanId)
        }
    }

    /** Last-resort read of the on-disk playlist cache, ignoring any in-memory entry. */
    private fun readCachedPlaylist(cleanId: String): SpotifyPlaylist? = try {
        val file = getPlaylistCacheFile(cleanId)
        if (file.exists()) gson.fromJson(file.readText(), SpotifyPlaylist::class.java) else null
    } catch (_: Exception) {
        null
    }


    private val artistAvatarCache = java.util.concurrent.ConcurrentHashMap<String, String>()

    suspend fun getArtistAvatar(artistId: String): String? {
        val cleanId = extractId(artistId)
        if (cleanId.isBlank()) return null
        artistAvatarCache[cleanId]?.let { return it }
        val avatar = getArtist(cleanId)?.avatarUrl?.takeIf { it.isNotBlank() } ?: return null
        artistAvatarCache[cleanId] = avatar
        return avatar
    }

    suspend fun getArtist(artistId: String): SpotifyArtist? = withContext(Dispatchers.IO) {
        val cleanId = extractId(artistId)
        if (cleanId.isBlank()) return@withContext null
        val url = SpotifyPathfinderApi.buildArtistUrl(cleanId)

        try {
            fetchPathfinderData(url)?.optJSONObject("artistUnion")?.let { artistUnion ->
                return@withContext parseArtistUnion(cleanId, artistUnion)
            }
            null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch Spotify artist $artistId: ${e.message}", e)
            null
        }
    }

    private fun parseArtistUnion(cleanId: String, artistUnion: JSONObject): SpotifyArtist {
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

                return SpotifyArtist(
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
        val cleanSeed = when {
            seedUri.startsWith("spotify:track:") || seedUri.startsWith("spotify:artist:") -> seedUri
            seedUri.startsWith("spotify_artist:") -> "spotify:artist:${seedUri.removePrefix("spotify_artist:")}"
            else -> "spotify:track:${extractId(seedUri)}"
        }
        if (cleanSeed.endsWith(":")) return@withContext null
        val url = "https://spclient.wg.spotify.com/inspiredby-mix/v2/seed_to_playlist/$cleanSeed"

        try {
            // The endpoint answers a JSON document whose playlist uri embeds the
            // editorial radio id; extract it from the serialized payload.
            val bodyJson = fetchJsonObject(url) ?: return@withContext null
            val match = Regex("spotify:playlist:(37i9dQZF[a-zA-Z0-9]+)").find(bodyJson.toString())
            return@withContext match?.groupValues?.get(1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve radio playlist ID for $seedUri: ${e.message}")
            null
        }
    }

    suspend fun getRadio(seedId: String, isArtist: Boolean = false): SpotifyPlaylist? = withContext(Dispatchers.IO) {
        val cleanId = extractId(seedId)
        if (cleanId.isBlank()) return@withContext null

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
            val recUrl = "https://api.spotify.com/v1/recommendations?seed_tracks=$cleanId&limit=50"
            val json = fetchJsonObject(recUrl)
            val recTracksArr = json?.optJSONArray("tracks")
            if (recTracksArr != null && recTracksArr.length() > 0) {
                for (i in 0 until recTracksArr.length()) {
                    val item = recTracksArr.optJSONObject(i) ?: continue
                    val t = parseWebApiTrackNode(item)
                    if (t != null && t.id != seedTrack.id) {
                        radioTracks.add(t)
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
        if (cleanId.isBlank()) return@withContext null
        val url = "https://spclient.wg.spotify.com/track-credits-view/v0/experimental/$cleanId/credits"

        try {
            val json = fetchJsonObject(url)
            if (json != null) {
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

                if (trackTitle.isNotBlank() || roles.isNotEmpty()) {
                    return@withContext SpotifyCredits(
                        trackTitle = trackTitle,
                        trackUri = trackUri,
                        roles = roles,
                        sourceNames = sourceNames
                    )
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

        val distinctArtists = artistsList
            .filter { it.name.isNotBlank() }
            .distinctBy { (it.id.ifBlank { it.name }).trim().lowercase() }

        return SpotifyTrack(
            id = id,
            name = name,
            durationMs = durationMs,
            artists = distinctArtists,
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
            .filter { it.name.isNotBlank() }
            .distinctBy { (it.id.ifBlank { it.name }).trim().lowercase() }
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

    /**
     * Normalizes any Spotify identifier form into the raw base62 entity id.
     *
     * Delegates to [SpotifyIds], the single normalization point shared with the
     * rest of the app, so a route can never be half-stripped here after some
     * caller already stripped part of it.
     */
    fun extractId(input: String): String = SpotifyIds.stripPrefixes(input)

    /**
     * Strict variant for callers that need to react to a malformed reference
     * instead of silently continuing with an empty id. A `null` id here means the
     * reference was never a valid Spotify entity, which is worth logging: it is
     * how a half-stripped route announces itself.
     */
    fun resolveId(input: String, typeHint: SpotifyIds.EntityType? = null): SpotifyIds.Result =
        SpotifyIds.parse(input, typeHint)
}
