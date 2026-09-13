package com.alananasss.kittytune

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

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
    fun testQobuzResolutionWithRealInstance() {
        val query = com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.Query(
            mediaId = "test_media_1",
            title = "Creep",
            artists = listOf("Radiohead"),
            album = "Pablo Honey",
            isrc = null,
            durationMs = 238_000L,
            countryCode = "US",
            qualityCode = 27,
            customInstances = "https://qobuz-dll.vercel.app/"
        )
        val resolved = com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.resolve(query)
        println("Resolved: mediaUri=${resolved.mediaUri}, label=${resolved.label}, bitrate=${resolved.bitrate}")
        assertNotNull(resolved)
        assertNotNull(resolved.mediaUri)
        org.junit.Assert.assertTrue(resolved.mediaUri.startsWith("http"))
    }

    @Test
    fun testQobuzDirectTrackResolution() {
        val query = com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.Query(
            mediaId = "qobuz:track:33933680",
            title = "Creep",
            artists = listOf("Radiohead"),
            album = null,
            isrc = null,
            durationMs = 238_000L,
            countryCode = "US",
            qualityCode = 27,
            customInstances = "https://qobuz-dll.vercel.app/"
        )
        val resolved = com.alananasss.kittytune.audio.providers.qobuz.QobuzAudioProvider.resolve(query)
        println("Direct resolved: mediaUri=${resolved.mediaUri}, label=${resolved.label}")
        assertNotNull(resolved)
        assertNotNull(resolved.mediaUri)
        org.junit.Assert.assertTrue(resolved.mediaUri.startsWith("http"))
    }

    @Test
    fun testQobuzSearchRepository() = kotlinx.coroutines.runBlocking {
        val searchResult = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository.search(null, "Daft Punk")
        assertNotNull(searchResult)
        org.junit.Assert.assertTrue(searchResult.tracks.isNotEmpty())
        org.junit.Assert.assertTrue(searchResult.albums.isNotEmpty())
        org.junit.Assert.assertTrue(searchResult.artists.isNotEmpty())
        org.junit.Assert.assertTrue(searchResult.playlists.isEmpty())
        println("Qobuz search: found ${searchResult.tracks.size} tracks, ${searchResult.albums.size} albums, ${searchResult.artists.size} artists, playlists=${searchResult.playlists.size}")

        val album = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository.getAlbum(null, "0886443927087")
        assertNotNull(album)
        assertEquals("Random Access Memories", album?.title)
        org.junit.Assert.assertTrue((album?.tracks?.size ?: 0) > 0)
        println("Qobuz album: ${album?.title} by ${album?.user?.username}, tracks=${album?.tracks?.size}")

        val artist = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository.getArtist(null, "36819")
        assertNotNull(artist)
        assertEquals("Daft Punk", artist?.title)
        assertNotNull(artist?.artworkUrl)
        org.junit.Assert.assertTrue(artist?.artworkUrl?.startsWith("https://static.qobuz.com") == true)
        org.junit.Assert.assertTrue((artist?.tracks?.size ?: 0) > 0)
        println("Qobuz artist Daft Punk: ${artist?.title}, avatar=${artist?.artworkUrl}, top tracks=${artist?.tracks?.size}")

        val snorunt = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository.getArtist(null, "11170794")
        assertNotNull(snorunt)
        assertEquals("snorunt", snorunt?.title)
        assertNotNull(snorunt?.artworkUrl)
        org.junit.Assert.assertTrue(snorunt?.artworkUrl?.startsWith("https://static.qobuz.com") == true)
        org.junit.Assert.assertTrue((snorunt?.tracks?.size ?: 0) > 0)
        println("Qobuz artist snorunt: ${snorunt?.title}, avatar=${snorunt?.artworkUrl}, top tracks=${snorunt?.tracks?.size}")
    }
}

