package com.alananasss.kittytune

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.alananasss.kittytune.data.CategoryKind
import com.alananasss.kittytune.data.GenreData
import com.alananasss.kittytune.ui.home.CategoryTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SoundCloudCategoriesTest {

    private val dummyContext: Context = object : android.content.ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    @Test
    fun testParseBorderColor() {
        assertEquals(Color(0xFF7C4DFF), GenreData.parseBorderColor("violet"))
        assertEquals(Color(0xFFE91E63), GenreData.parseBorderColor("magenta"))
        assertEquals(Color(0xFFFFB300), GenreData.parseBorderColor("yellow"))
        assertEquals(Color(0xFF009688), GenreData.parseBorderColor("teal"))
        assertEquals(Color(0xFFFF6F00), GenreData.parseBorderColor("orange"))
        assertEquals(Color(0xFF43A047), GenreData.parseBorderColor("green"))
        assertEquals(Color(0xFF1E88E5), GenreData.parseBorderColor("blue"))
        assertEquals(Color(0xFFE53935), GenreData.parseBorderColor("red"))
        assertEquals(Color(0xFF8E24AA), GenreData.parseBorderColor("purple"))
        assertEquals(Color(0xFF757575), GenreData.parseBorderColor("unknown_color"))
        assertEquals(Color(0xFF757575), GenreData.parseBorderColor(null))
    }

    @Test
    fun testIsMoodQuery() {
        assertTrue(GenreData.isMoodQuery("Workout", "Entraînement"))
        assertTrue(GenreData.isMoodQuery("Chill", "Détente"))
        assertTrue(GenreData.isMoodQuery("At Home", "À la maison"))
        assertTrue(GenreData.isMoodQuery("Study", "Révisions"))
        assertTrue(GenreData.isMoodQuery("Party", "Fête"))
        assertTrue(GenreData.isMoodQuery("Feel Good", "Bonne humeur"))
        assertTrue(GenreData.isMoodQuery("Sad", "Triste"))

        assertFalse(GenreData.isMoodQuery("Hip Hop", "Hip-hop & rap"))
        assertFalse(GenreData.isMoodQuery("Rock", "Rock"))
        assertFalse(GenreData.isMoodQuery("Electronic", "Electro"))
        assertFalse(GenreData.isMoodQuery("Techno", "Techno"))
        assertFalse(GenreData.isMoodQuery("House", "House"))
        assertFalse(GenreData.isMoodQuery("Metal", "Metal"))
    }

    @Test
    fun testVisualCategoriesMatchSoundCloudApkLandingGrid() {
        val categories = GenreData.getVisualCategories(dummyContext)
        assertEquals(22, categories.size)

        val expectedIds = listOf(
            "hiphop", "electronic", "pop", "rnb", "chill",
            "party", "workout", "techno", "house", "happy",
            "athome", "sad", "study", "indie", "rock",
            "folk", "metal", "latin", "reggae", "soul",
            "country", "disco"
        )
        val actualIds = categories.map { it.id }
        assertEquals(expectedIds, actualIds)

        for (category in categories) {
            assertNotNull("Category ${category.id} should have artworkUrl", category.artworkUrl)
            assertTrue(
                "Category ${category.id} artworkUrl must point to SoundCloud CDN",
                category.artworkUrl!!.startsWith("https://i1.sndcdn.com/artworks-") &&
                        category.artworkUrl.endsWith("-original.png")
            )
            assertNotNull("Category ${category.id} should have borderColor", category.borderColor)
        }

        // Verify Genres vs Moods
        val hipHop = categories.first { it.id == "hiphop" }
        assertEquals(CategoryKind.GENRE, hipHop.kind)
        assertEquals("soundcloud:system-playlists:trending-by-genre:hip-hop", hipHop.systemPlaylistUrn)

        val workout = categories.first { it.id == "workout" }
        assertEquals(CategoryKind.MOOD, workout.kind)

        val chill = categories.first { it.id == "chill" }
        assertEquals(CategoryKind.MOOD, chill.kind)
        assertEquals("soundcloud:system-playlists:trending-by-genre:ambient", chill.systemPlaylistUrn)
    }

    @Test
    fun testFindCategory() {
        val foundHipHop = GenreData.findCategory("Hip Hop & Rap", "Hip Hop", dummyContext)
        assertNotNull(foundHipHop)
        assertEquals("hiphop", foundHipHop?.id)
        assertEquals(CategoryKind.GENRE, foundHipHop?.kind)

        val foundWorkout = GenreData.findCategory("Entraînement", "Workout", dummyContext)
        assertNotNull(foundWorkout)
        assertEquals("workout", foundWorkout?.id)
        assertEquals(CategoryKind.MOOD, foundWorkout?.kind)

        val foundByQuery = GenreData.findCategory("", "Rock", dummyContext)
        assertNotNull(foundByQuery)
        assertEquals("rock", foundByQuery?.id)

        val notFound = GenreData.findCategory("Unknown 99999", "Random_Query_ZZZ", dummyContext)
        assertNull(notFound)
    }

    @Test
    fun testCategoryTabsHierarchy() {
        // Genres get 4 tabs: Tout, En tendance, Playlists, Albums
        val genreTabs = listOf(CategoryTab.ALL, CategoryTab.TRENDING, CategoryTab.PLAYLISTS, CategoryTab.ALBUMS)
        assertEquals(4, genreTabs.size)
        assertEquals(R.string.category_tab_all, genreTabs[0].titleRes)
        assertEquals(R.string.category_tab_trending, genreTabs[1].titleRes)
        assertEquals(R.string.category_tab_playlists, genreTabs[2].titleRes)
        assertEquals(R.string.category_tab_albums, genreTabs[3].titleRes)

        // Moods get 2 tabs: Tout, Playlists
        val moodTabs = listOf(CategoryTab.ALL, CategoryTab.PLAYLISTS)
        assertEquals(2, moodTabs.size)
        assertEquals(R.string.category_tab_all, moodTabs[0].titleRes)
        assertEquals(R.string.category_tab_playlists, moodTabs[1].titleRes)
    }

    @Test
    fun testParseSduiLandingPageFixture() {
        val stream = javaClass.classLoader?.getResourceAsStream("query_landing_page.json")
            ?: java.io.File("src/test/resources/query_landing_page.json").inputStream()
        val jsonText = stream.bufferedReader().use { it.readText() }
        val json = com.alananasss.kittytune.utils.AppUtils.gson.fromJson(jsonText, com.google.gson.JsonObject::class.java)

        val categories = GenreData.parseSduiLandingCategories(json, dummyContext)
        assertEquals(21, categories.size)

        for (cat in categories) {
            assertTrue("Category title must not be blank", cat.title.isNotBlank())
            assertTrue("Category query must not be blank", cat.query.isNotBlank())
            assertNotNull("Category ${cat.title} should have dark artwork", cat.artworkUrl)
            assertNotNull("Category ${cat.title} should have light artwork", cat.artworkUrlLight)
            assertTrue("Category artwork must be https", cat.artworkUrl!!.startsWith("https://"))
        }

        // Verify specific category details from fixture
        val rap = categories.firstOrNull { it.id == "hiphop" || it.title.contains("Rap", ignoreCase = true) }
        assertNotNull(rap)
        assertEquals("violet", rap?.borderColor)
        assertTrue(rap?.artworkUrl!!.contains("artworks-pR3Ub8FmtdtYbjUL-umUIAw"))

        val chill = categories.firstOrNull { it.id == "chill" || it.title.contains("Chill", ignoreCase = true) }
        assertNotNull(chill)
        assertEquals("teal", chill?.borderColor)
        assertEquals(CategoryKind.MOOD, chill?.kind)
    }

    @Test
    fun testParseSduiCategoryDetailsFixture() {
        val stream = javaClass.classLoader?.getResourceAsStream("query_genre_detail.json")
            ?: java.io.File("src/test/resources/query_genre_detail.json").inputStream()
        val jsonText = stream.bufferedReader().use { it.readText() }
        val json = com.alananasss.kittytune.utils.AppUtils.gson.fromJson(jsonText, com.google.gson.JsonObject::class.java)

        val details = GenreData.parseSduiCategoryDetails(json)
        assertEquals("Folk", details.title)
        assertNotNull(details.headerDarkArtwork)
        assertNotNull(details.headerLightArtwork)
        assertTrue(details.headerDarkArtwork!!.contains("Cz8rQjWTFBErX5lP-GbDjdA"))
        assertTrue(details.headerLightArtwork!!.contains("BXKVWIdiMBPqKGGX-yJ7v5A"))

        // Tracks parsed from vibe_tracks
        assertTrue("Tracks count should be > 20, got ${details.tracks.size}", details.tracks.size >= 20)
        for (track in details.tracks) {
            assertTrue("Track ID must be positive: ${track.id}", track.id > 0)
            assertNotNull("Track title must not be null", track.title)
            assertNotNull("Track user must not be null", track.user)
            assertTrue("Track duration must be positive", (track.durationMs ?: 0L) > 0L)
        }

        // Curated playlists parsed from search_curated_playlists
        assertTrue("Curated playlists should be >= 5, got ${details.curatedPlaylists.size}", details.curatedPlaylists.size >= 5)
        for (pl in details.curatedPlaylists) {
            assertTrue("Playlist ID must be positive: ${pl.id}", pl.id > 0)
            assertNotNull("Playlist title must not be null", pl.title)
            assertFalse("Curated playlists should not be real albums", pl.isRealAlbum)
        }

        // Albums parsed from search_albums_with_genre_filter
        assertTrue("Albums should be >= 15, got ${details.albums.size}", details.albums.size >= 15)
        for (album in details.albums) {
            assertTrue("Album ID must be positive: ${album.id}", album.id > 0)
            assertNotNull("Album title must not be null", album.title)
        }

        // Artists parsed from top_users_from_results
        assertTrue("Artists should be >= 25, got ${details.artists.size}", details.artists.size >= 25)
        for (artist in details.artists) {
            assertTrue("Artist ID must be positive: ${artist.id}", artist.id > 0)
            assertNotNull("Artist username must not be null", artist.username)
        }
    }

    @Test
    fun testSearchTracksTakePriorityOverVibeTracksForTrendingOrder() {
        // Reproduce scenario where vibe_tracks has preview tracks, and search_tracks has canonical tracks like 'safeword'
        val mockJson = com.google.gson.JsonObject().apply {
            val entitiesObj = com.google.gson.JsonObject().apply {
                // vibe track (e.g. Warm Safe Place)
                add("soundcloud:tracks:9999", com.google.gson.JsonObject().apply {
                    addProperty("type", "track")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 9999L)
                        addProperty("title", "Warm Safe Place")
                        addProperty("duration", 210000L)
                    })
                })
                // canonical trending track #1 (safeword)
                add("soundcloud:tracks:2347489763", com.google.gson.JsonObject().apply {
                    addProperty("type", "track")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 2347489763L)
                        addProperty("title", "safeword")
                        addProperty("duration", 185000L)
                    })
                })
                // canonical trending track #2
                add("soundcloud:tracks:2347489764", com.google.gson.JsonObject().apply {
                    addProperty("type", "track")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 2347489764L)
                        addProperty("title", "Kings of the Night")
                        addProperty("duration", 200000L)
                    })
                })
            }
            add("entities", entitiesObj)

            val sectionsArr = com.google.gson.JsonArray().apply {
                // In SDUI, vibe_tracks appears BEFORE search_tracks
                add(com.google.gson.JsonObject().apply {
                    addProperty("urn", "soundcloud:sections:vibe_tracks")
                    add("data", com.google.gson.JsonObject().apply {
                        add("results", com.google.gson.JsonArray().apply {
                            add("soundcloud:tracks:9999")
                        })
                    })
                })
                add(com.google.gson.JsonObject().apply {
                    addProperty("urn", "soundcloud:sections:search_tracks_with_genre_filter")
                    add("data", com.google.gson.JsonObject().apply {
                        add("results", com.google.gson.JsonArray().apply {
                            add("soundcloud:tracks:2347489763")
                            add("soundcloud:tracks:2347489764")
                        })
                    })
                })
            }
            add("sections", sectionsArr)
        }

        val details = GenreData.parseSduiCategoryDetails(mockJson)

        // Track #1 MUST be safeword, matching the official SoundCloud app!
        assertEquals("Canonical track #1 must be 'safeword'", "safeword", details.tracks[0].title)
        assertEquals(2347489763L, details.tracks[0].id)
        assertEquals("Canonical track #2 must be 'Kings of the Night'", "Kings of the Night", details.tracks[1].title)
        assertEquals(2347489764L, details.tracks[1].id)
    }

    @Test
    fun testPlaylistPreviewAndDiscoverMoreTracksParsing() {
        val mockJson = com.google.gson.JsonObject().apply {
            val entitiesObj = com.google.gson.JsonObject().apply {
                // Introducing playlist entity
                add("soundcloud:playlists:1841554947", com.google.gson.JsonObject().apply {
                    addProperty("type", "playlist")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 1841554947L)
                        addProperty("title", "Buzzing Rock")
                        addProperty("artwork_url", "https://i1.sndcdn.com/artworks-test.jpg")
                    })
                })
                // Introducing preview track 1
                add("soundcloud:tracks:2399480928", com.google.gson.JsonObject().apply {
                    addProperty("type", "track")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 2399480928L)
                        addProperty("title", "Forge of Shattered Hopes")
                        addProperty("duration", 180470L)
                    })
                })
                // Discover more track
                add("soundcloud:tracks:2330245394", com.google.gson.JsonObject().apply {
                    addProperty("type", "track")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 2330245394L)
                        addProperty("title", "Laleh")
                        addProperty("duration", 179000L)
                        addProperty("playback_count", 48400)
                    })
                })
            }
            add("entities", entitiesObj)

            val sectionsArr = com.google.gson.JsonArray().apply {
                add(com.google.gson.JsonObject().apply {
                    addProperty("urn", "soundcloud:sections:vibe_buzzing")
                    addProperty("type", "playlist_preview")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("title", "Introducing")
                        addProperty("result", "soundcloud:playlists:1841554947")
                        add("tracks", com.google.gson.JsonArray().apply {
                            add("soundcloud:tracks:2399480928")
                        })
                    })
                })
                add(com.google.gson.JsonObject().apply {
                    addProperty("urn", "soundcloud:sections:vibe_tracks_more")
                    addProperty("type", "simple_list")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("title", "Discover more Tracks")
                        add("results", com.google.gson.JsonArray().apply {
                            add("soundcloud:tracks:2330245394")
                        })
                    })
                })
            }
            add("sections", sectionsArr)
        }

        val details = GenreData.parseSduiCategoryDetails(mockJson)

        assertNotNull(details.introducingPlaylist)
        assertEquals("Buzzing Rock", details.introducingPlaylist?.title)
        assertEquals(1841554947L, details.introducingPlaylist?.id)

        assertEquals(1, details.introducingTracks.size)
        assertEquals("Forge of Shattered Hopes", details.introducingTracks[0].title)
        assertEquals(2399480928L, details.introducingTracks[0].id)

        assertEquals(1, details.discoverMoreTracks.size)
        assertEquals("Laleh", details.discoverMoreTracks[0].title)
        assertEquals(2330245394L, details.discoverMoreTracks[0].id)
        assertEquals(48400, details.discoverMoreTracks[0].playbackCount)
    }

    @Test
    fun testMoodWorkoutCategoryParsingOnlyPlaylistsAndTwoTabs() {
        val mockJson = com.google.gson.JsonObject().apply {
            val entitiesObj = com.google.gson.JsonObject().apply {
                add("soundcloud:playlists:728631420", com.google.gson.JsonObject().apply {
                    addProperty("type", "playlist")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 728631420L)
                        addProperty("title", "New EDM Hits: On The Up")
                        add("user", com.google.gson.JsonObject().apply {
                            addProperty("id", 12345L)
                            addProperty("username", "The Peak: EDM")
                        })
                    })
                })
                add("soundcloud:playlists:1734894207", com.google.gson.JsonObject().apply {
                    addProperty("type", "playlist")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 1734894207L)
                        addProperty("title", "#gym & beyond")
                        add("user", com.google.gson.JsonObject().apply {
                            addProperty("id", 12346L)
                            addProperty("username", "Scenes")
                        })
                    })
                })
                add("soundcloud:playlists:836737019", com.google.gson.JsonObject().apply {
                    addProperty("type", "playlist")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 836737019L)
                        addProperty("title", "New Bass Heat: Bass Flex")
                        add("user", com.google.gson.JsonObject().apply {
                            addProperty("id", 12347L)
                            addProperty("username", "The Peak: EDM")
                        })
                    })
                })
                add("soundcloud:playlists:852912449", com.google.gson.JsonObject().apply {
                    addProperty("type", "playlist")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("id", 852912449L)
                        addProperty("title", "Caribbean Workout: Sweat")
                        add("user", com.google.gson.JsonObject().apply {
                            addProperty("id", 12348L)
                            addProperty("username", "Vibrations: Global Rhythm")
                        })
                    })
                })
            }
            add("entities", entitiesObj)

            val sectionsArr = com.google.gson.JsonArray().apply {
                add(com.google.gson.JsonObject().apply {
                    addProperty("urn", "soundcloud:sections:page_header")
                    addProperty("type", "page_header")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("title", "Entraînement")
                    })
                })
                add(com.google.gson.JsonObject().apply {
                    addProperty("urn", "soundcloud:sections:search_filters_menu")
                    addProperty("type", "horizontal_menu")
                    add("data", com.google.gson.JsonObject().apply {
                        add("choices", com.google.gson.JsonArray().apply {
                            add(com.google.gson.JsonObject().apply {
                                addProperty("key", "all")
                                addProperty("title", "All")
                            })
                            add(com.google.gson.JsonObject().apply {
                                addProperty("key", "playlists")
                                addProperty("title", "Playlists")
                            })
                        })
                    })
                })
                add(com.google.gson.JsonObject().apply {
                    addProperty("urn", "soundcloud:sections:search_curated_playlists")
                    addProperty("type", "gallery")
                    add("data", com.google.gson.JsonObject().apply {
                        addProperty("title", "Playlists")
                        add("results", com.google.gson.JsonArray().apply {
                            add("soundcloud:playlists:728631420")
                            add("soundcloud:playlists:1734894207")
                            add("soundcloud:playlists:836737019")
                            add("soundcloud:playlists:852912449")
                        })
                    })
                })
            }
            add("sections", sectionsArr)
        }

        val details = GenreData.parseSduiCategoryDetails(mockJson)

        assertEquals("Entraînement", details.title)
        assertEquals(listOf("all", "playlists"), details.filterChoices)
        assertEquals(4, details.curatedPlaylists.size)
        assertEquals("New EDM Hits: On The Up", details.curatedPlaylists[0].title)
        assertEquals("#gym & beyond", details.curatedPlaylists[1].title)
        assertEquals("New Bass Heat: Bass Flex", details.curatedPlaylists[2].title)
        assertEquals("Caribbean Workout: Sweat", details.curatedPlaylists[3].title)

        // Workout mood category must have NO tracks, NO albums, NO artists, NO introducing
        assertTrue(details.tracks.isEmpty())
        assertTrue(details.vibeTracks.isEmpty())
        assertTrue(details.albums.isEmpty())
        assertTrue(details.artists.isEmpty())
        assertNull(details.introducingPlaylist)
        assertTrue(details.discoverMoreTracks.isEmpty())
    }
}
