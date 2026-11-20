package com.alananasss.kittytune

import com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider
import com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.net.InetSocketAddress

class SearchRepositoriesTest {

    @Test
    fun testDeezerTrackParsing() {
        val trackJsonStr = """
        {
            "id": 1109731,
            "title": "Harder, Better, Faster, Stronger",
            "duration": 224,
            "artist": {
                "id": 27,
                "name": "Daft Punk",
                "picture_medium": "https://e-cdns-images.dzcdn.net/images/artist/f2bc007e9133c946ac3c3f5ed515d902/250x250-000000-80-0-0.jpg"
            },
            "album": {
                "id": 302127,
                "title": "Discovery",
                "cover_big": "https://e-cdns-images.dzcdn.net/images/cover/2e018122cb56986277102d2041a592c8/500x500-000000-80-0-0.jpg"
            }
        }
        """.trimIndent()

        val json = JSONObject(trackJsonStr)
        val id = json.getLong("id")
        val title = json.getString("title")
        val artist = json.getJSONObject("artist").getString("name")
        val album = json.getJSONObject("album").getString("title")

        assertEquals(1109731L, id)
        assertEquals("Harder, Better, Faster, Stronger", title)
        assertEquals("Daft Punk", artist)
        assertEquals("Discovery", album)
    }

    @Test
    fun testTidalSearchParsing() {
        val tidalJsonStr = """
        {
            "tracks": {
                "items": [
                    {
                        "id": 123456,
                        "title": "Starboy",
                        "duration": 230,
                        "artist": {
                            "id": 4567,
                            "name": "The Weeknd"
                        },
                        "album": {
                            "id": 7890,
                            "title": "Starboy",
                            "cover": "1234-5678-abcd"
                        }
                    }
                ]
            },
            "artists": {
                "items": [
                    {
                        "id": 4567,
                        "name": "The Weeknd",
                        "picture": "abcd-ef01-2345"
                    }
                ]
            }
        }
        """.trimIndent()

        val json = JSONObject(tidalJsonStr)
        val tracks = json.getJSONObject("tracks").getJSONArray("items")
        assertEquals(1, tracks.length())
        val firstTrack = tracks.getJSONObject(0)
        assertEquals(123456L, firstTrack.getLong("id"))
        assertEquals("Starboy", firstTrack.getString("title"))

        val artists = json.getJSONObject("artists").getJSONArray("items")
        assertEquals(1, artists.length())
        val firstArtist = artists.getJSONObject(0)
        assertEquals(4567L, firstArtist.getLong("id"))
        assertEquals("The Weeknd", firstArtist.getString("name"))
    }

    @Test
    fun testQobuzSearchParsing() {
        val qobuzJsonStr = """
        {
            "status": "success",
            "data": {
                "tracks": {
                    "items": [
                        {
                            "id": 999888,
                            "title": "Around The World",
                            "duration": 429,
                            "performer": {
                                "id": 27,
                                "name": "Daft Punk"
                            },
                            "album": {
                                "id": 111222,
                                "title": "Homework",
                                "image": {
                                    "large": "https://static.qobuz.com/images/covers/large.jpg"
                                }
                            }
                        }
                    ]
                }
            }
        }
        """.trimIndent()

        val json = JSONObject(qobuzJsonStr)
        val data = json.getJSONObject("data")
        val tracks = data.getJSONObject("tracks").getJSONArray("items")
        assertEquals(1, tracks.length())
        val track = tracks.getJSONObject(0)
        assertEquals(999888L, track.getLong("id"))
        assertEquals("Around The World", track.getString("title"))
        assertEquals("Daft Punk", track.getJSONObject("performer").getString("name"))
    }

    @Test
    fun testQobuzResolutionRejectsSampleWhenDisallowed() = withQobuzServer { server, base ->
        installStreamResponse(server, base, sample = true)
        val query = testQuery("qobuz:track:321", base, allowPreview = false)

        val error = assertThrows(QobuzAudioProvider.QobuzResolutionException::class.java) {
            QobuzAudioProvider.resolve(query, base)
        }
        assertTrue(error.message.orEmpty().contains("sample preview"))
    }

    @Test
    fun testQobuzResolutionWithPreviewAllowed() = withQobuzServer { server, base ->
        installStreamResponse(server, base, sample = true)
        server.createContext("/track/search") { it.respond(200, catalogue) }

        val result = QobuzAudioProvider.resolve(testQuery("metadata-match", base, allowPreview = true), base)
        assertEquals("321", result.trackId)
        assertEquals("$base/track.flac", result.mediaUri)
        assertEquals("audio/flac", result.mimeType)
    }

    @Test
    fun testQobuzDirectTrackResolution() = withQobuzServer { server, base ->
        installStreamResponse(server, base, sample = false, trackId = 322)
        val result = QobuzAudioProvider.resolve(testQuery("qobuz:track:322", base, allowPreview = false), base)
        assertEquals("322", result.trackId)
        assertEquals("$base/track.flac", result.mediaUri)
    }

    @Test
    fun testQobuzCatalogueSearch() = withQobuzServer { server, base ->
        server.createContext("/catalog/search") { it.respond(200, catalogue) }
        val result = runBlocking { QobuzSearchRepository.search("Test Artist", 5, base, emptyList()) }
        assertEquals("Test Song", result.tracks.single().title)
        assertEquals("Test Album", result.albums.single().title)
        assertEquals("Test Artist", result.artists.single().username)
    }

    private fun testQuery(mediaId: String, base: String, allowPreview: Boolean) = QobuzAudioProvider.Query(
        mediaId = mediaId,
        title = "Test Song",
        artists = listOf("Test Artist"),
        album = "Test Album",
        isrc = null,
        durationMs = 238_000L,
        customInstances = base,
        userAuthToken = "local-test-token",
        allowPreview = allowPreview,
    )

    private fun withQobuzServer(test: (HttpServer, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.start()
        try {
            test(server, "http://127.0.0.1:${server.address.port}")
        } finally {
            server.stop(0)
        }
    }

    private fun installStreamResponse(server: HttpServer, base: String, sample: Boolean, trackId: Int = 321) {
        server.createContext("/track/getFileUrl") {
            if (it.requestHeaders.getFirst("X-User-Auth-Token") == "local-test-token" &&
                it.requestURI.query.contains("track_id=$trackId")
            ) {
                it.respond(200, """{"url":"$base/track.flac","sample":$sample,"mime_type":"audio/flac"}""")
            } else {
                it.respond(401, "{}")
            }
        }
    }

    private fun HttpExchange.respond(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private val catalogue = """
        {
            "tracks": {"items": [{
                "id": 321, "title": "Test Song", "duration": 238, "streamable": true,
                "performer": {"id": 27, "name": "Test Artist"},
                "album": {"id": "111222", "title": "Test Album"}
            }]},
            "albums": {"items": [{"id": "111222", "title": "Test Album", "artist": {"id": 27, "name": "Test Artist"}}]},
            "artists": {"items": [{"id": 27, "name": "Test Artist"}]}
        }
    """.trimIndent()

    @Test
    fun testQobuzLiveCatalogue() = runBlocking {
        assumeTrue("Set KITTYTUNE_TEST_LIVE_SERVICES=1 to check live endpoints",
            System.getenv("KITTYTUNE_TEST_LIVE_SERVICES") == "1")
        val searchResult = QobuzSearchRepository.search(null, "Daft Punk")
        println("Qobuz search result: tracks=${searchResult.tracks.size}, albums=${searchResult.albums.size}, artists=${searchResult.artists.size}, playlists=${searchResult.playlists.size}")
        assertNotNull(searchResult)
        org.junit.Assert.assertTrue(searchResult.tracks.isNotEmpty())
        org.junit.Assert.assertTrue(searchResult.albums.isNotEmpty())
        org.junit.Assert.assertTrue(searchResult.artists.isNotEmpty())
        org.junit.Assert.assertTrue(searchResult.playlists.isNotEmpty())
        println("Qobuz search: found ${searchResult.tracks.size} tracks, ${searchResult.albums.size} albums, ${searchResult.artists.size} artists, playlists=${searchResult.playlists.size}")

        val album = QobuzSearchRepository.getAlbum(null, "0886443927087")
        assertNotNull(album)
        assertEquals("Random Access Memories", album?.title)
        org.junit.Assert.assertTrue((album?.tracks?.size ?: 0) > 0)
        println("Qobuz album: ${album?.title} by ${album?.user?.username}, tracks=${album?.tracks?.size}")

        val artist = QobuzSearchRepository.getArtist(null, "36819")
        assertNotNull(artist)
        assertEquals("Daft Punk", artist?.title)
        assertNotNull(artist?.artworkUrl)
        org.junit.Assert.assertTrue(artist?.artworkUrl?.startsWith("https://static.qobuz.com") == true)
        org.junit.Assert.assertTrue((artist?.tracks?.size ?: 0) > 0)
        println("Qobuz artist Daft Punk: ${artist?.title}, avatar=${artist?.artworkUrl}, top tracks=${artist?.tracks?.size}")

        val snorunt = QobuzSearchRepository.getArtist(null, "11170794")
        assertNotNull(snorunt)
        assertEquals("snorunt", snorunt?.title)
        assertNotNull(snorunt?.artworkUrl)
        org.junit.Assert.assertTrue(snorunt?.artworkUrl?.startsWith("https://static.qobuz.com") == true)
        org.junit.Assert.assertTrue((snorunt?.tracks?.size ?: 0) > 0)
        println("Qobuz artist snorunt: ${snorunt?.title}, avatar=${snorunt?.artworkUrl}, top tracks=${snorunt?.tracks?.size}")
    }
}

