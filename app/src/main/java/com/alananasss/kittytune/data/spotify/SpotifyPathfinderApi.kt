package com.alananasss.kittytune.data.spotify

import org.json.JSONObject
import java.net.URLEncoder

object SpotifyPathfinderApi {

    const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"

    object Hashes {
        const val SEARCH_DESKTOP = "eff59fa0a3d026b88b56fddbcf4bdfa16a186b8175a5c1a358c072e053c2e5b0"
        const val GET_TRACK = "612585ae06ba435ad26369870deaae23b5c8800a256cd8a57e08eddc25a37294"
        const val GET_ALBUM = "b9bfabef66ed756e5e13f68a942deb60bd4125ec1f1be8cc42769dc0259b4b10"
        const val FETCH_PLAYLIST = "a65e12194ed5fc443a1cdebed5fabe33ca5b07b987185d63c72483867ad13cb4"
        const val QUERY_ARTIST_OVERVIEW = "ae0e2958a4ab645b35ca19ac04d0495ae12d9c5d7b7286217674801a9aab281a"
        const val QUERY_ARTIST_RELATED = "3d031d6cb22a2aa7c8d203d49b49df731f58b1e2799cc38d9876d58771aa66f3"
        const val SIMILAR_ALBUMS = "1d1f93a737498adca2c892c73af87fc0b052afe4e1a33c989540c32413dfae17"
    }

    val EDITORIAL_CHARTS = listOf(
        SpotifyChart("top-50-global", "Top 50 - Global", "37i9dQZEVXbMDoHDwVN2tF"),
        SpotifyChart("todays-top-hits", "Today's Top Hits", "37i9dQZF1DXcBWIGoYBM5M"),
        SpotifyChart("top-songs-global", "Top Songs - Global", "37i9dQZEVXbNG2KDcFcKOF"),
        SpotifyChart("top-50-france", "Top 50 - France", "37i9dQZEVXbIPW4FssbupI"),
        SpotifyChart("top-50-usa", "Top 50 - USA", "37i9dQZEVXbLRQDuF5jeBp")
    )

    fun buildSearchUrl(query: String, offset: Int = 0, limit: Int = 20): String {
        val variables = JSONObject().apply {
            put("searchTerm", query)
            put("offset", offset)
            put("limit", limit)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", true)
            put("includePreReleases", true)
            put("includeAlbumPreReleases", false)
            put("includeAuthors", false)
            put("includeEpisodeContentRatingsV2", false)
        }
        return buildQueryUrl("searchDesktop", Hashes.SEARCH_DESKTOP, variables)
    }

    fun buildTrackUrl(trackId: String): String {
        val variables = JSONObject().apply {
            put("uri", "spotify:track:$trackId")
        }
        return buildQueryUrl("getTrack", Hashes.GET_TRACK, variables)
    }

    fun buildAlbumUrl(albumId: String, offset: Int = 0, limit: Int = 50): String {
        val variables = JSONObject().apply {
            put("uri", "spotify:album:$albumId")
            put("locale", "")
            put("offset", offset)
            put("limit", limit)
        }
        return buildQueryUrl("getAlbum", Hashes.GET_ALBUM, variables)
    }

    fun buildPlaylistUrl(playlistId: String, offset: Int = 0, limit: Int = 100): String {
        val variables = JSONObject().apply {
            put("uri", "spotify:playlist:$playlistId")
            put("offset", offset)
            put("limit", limit)
            put("enableWatchFeedEntrypoint", false)
        }
        return buildQueryUrl("fetchPlaylist", Hashes.FETCH_PLAYLIST, variables)
    }

    fun buildArtistUrl(artistId: String): String {
        val variables = JSONObject().apply {
            put("uri", "spotify:artist:$artistId")
            put("locale", "")
            put("includePrerelease", false)
        }
        return buildQueryUrl("queryArtistOverview", Hashes.QUERY_ARTIST_OVERVIEW, variables)
    }

    fun buildArtistRelatedUrl(artistId: String): String {
        val variables = JSONObject().apply {
            put("uri", "spotify:artist:$artistId")
        }
        return buildQueryUrl("queryArtistRelated", Hashes.QUERY_ARTIST_RELATED, variables)
    }

    fun buildSimilarAlbumsUrl(trackId: String, limit: Int = 10): String {
        val variables = JSONObject().apply {
            put("uri", "spotify:track:$trackId")
            put("limit", limit)
        }
        return buildQueryUrl("similarAlbumsBasedOnThisTrack", Hashes.SIMILAR_ALBUMS, variables)
    }

    private fun buildQueryUrl(operationName: String, sha256: String, variables: JSONObject): String {
        val extensions = JSONObject().apply {
            val persistedQuery = JSONObject().apply {
                put("version", 1)
                put("sha256Hash", sha256)
            }
            put("persistedQuery", persistedQuery)
        }

        val encodedOp = URLEncoder.encode(operationName, "UTF-8")
        val encodedVars = URLEncoder.encode(variables.toString(), "UTF-8")
        val encodedExt = URLEncoder.encode(extensions.toString(), "UTF-8")

        return "$PATHFINDER_URL?operationName=$encodedOp&variables=$encodedVars&extensions=$encodedExt"
    }
}
