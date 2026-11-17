package com.alananasss.kittytune

import com.alananasss.kittytune.data.yearlyplayback.ApiYearlyPlaybackTextRun
import com.alananasss.kittytune.data.yearlyplayback.ApiYearlyPlaybackTimelineItem
import com.alananasss.kittytune.data.yearlyplayback.ApiYearlyTrackStub
import com.alananasss.kittytune.data.yearlyplayback.ApiYearlyUserStub
import com.alananasss.kittytune.data.yearlyplayback.ArtBoardTextRun
import com.alananasss.kittytune.data.yearlyplayback.Block
import com.alananasss.kittytune.data.yearlyplayback.ErrorType
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackEvent
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackRepository
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackState
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackTarget
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackVariant
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YearlyPlaybackTest {

    @Test
    fun testYearlyPlaybackTargetAndVariantEnums() {
        assertEquals(2, YearlyPlaybackTarget.entries.size)
        assertTrue(YearlyPlaybackTarget.entries.contains(YearlyPlaybackTarget.FAN))
        assertTrue(YearlyPlaybackTarget.entries.contains(YearlyPlaybackTarget.CREATOR))

        assertEquals(3, YearlyPlaybackVariant.entries.size)
        assertTrue(YearlyPlaybackVariant.entries.contains(YearlyPlaybackVariant.CURRENT_YEAR))
        assertTrue(YearlyPlaybackVariant.entries.contains(YearlyPlaybackVariant.PREVIOUS_YEAR))
        assertTrue(YearlyPlaybackVariant.entries.contains(YearlyPlaybackVariant.MOCK))

        assertEquals(2, ErrorType.entries.size)
        assertTrue(ErrorType.entries.contains(ErrorType.NETWORK))
        assertTrue(ErrorType.entries.contains(ErrorType.SERVER))
    }

    @Test
    fun testBlockTypesAndProperties() {
        val artBoard = Block.ArtBoard(
            artboardId = "Intro_0",
            durationMs = 5000,
            index = 0,
            backgroundTrack = ApiYearlyTrackStub(
                urn = "soundcloud:tracks:123",
                title = "Sunflower",
                user = ApiYearlyUserStub(name = "Post Malone")
            ),
            textRuns = listOf(
                ArtBoardTextRun(key = "title", value = "Your 2025 Story"),
                ArtBoardTextRun(key = "minutes", value = "42,000")
            )
        )
        assertEquals("Intro_0", artBoard.artboardId)
        assertEquals(5000, artBoard.durationMs)
        assertEquals(0, artBoard.index)
        assertEquals("Sunflower", artBoard.backgroundTrack?.title)
        assertEquals(2, artBoard.textRuns.size)
        assertEquals("title", artBoard.textRuns[0].key)
        assertEquals("Your 2025 Story", artBoard.textRuns[0].value)

        val savePlaylist = Block.SavePlaylist(
            artboardId = "SavePlaylist_1",
            durationMs = 6000,
            index = 1,
            backgroundTrack = null,
            playlistArtwork = "https://i1.sndcdn.com/artworks.jpg",
            backgroundImageUrl = "https://i1.sndcdn.com/bg.jpg",
            text = "Your Top Tracks 2025",
            activeTitle = "Save to Your Library",
            inactiveTitle = "Saved to Library",
            playlistUrn = "soundcloud:playlists:999",
            isLiked = false
        )
        assertEquals("SavePlaylist_1", savePlaylist.artboardId)
        assertEquals(6000, savePlaylist.durationMs)
        assertEquals("soundcloud:playlists:999", savePlaylist.playlistUrn)
        assertFalse(savePlaylist.isLiked)

        val openInsights = Block.OpenInsights(
            artboardId = "FinalSlide",
            durationMs = 7000,
            index = 2,
            backgroundTrack = null,
            backgroundImageUrl = "https://i1.sndcdn.com/bg_insights.jpg",
            text = "Check your deep stats",
            title = "That's a wrap!"
        )
        assertEquals("FinalSlide", openInsights.artboardId)
        assertEquals("That's a wrap!", openInsights.title)
    }

    @Test
    fun testApiYearlyPlaybackTimelinePolymorphism() {
        val artboardItem = ApiYearlyPlaybackTimelineItem.ArtboardItem(
            backgroundTrackId = "bg1",
            durationMs = 5000,
            artboardId = "Art1",
            textRuns = listOf(ApiYearlyPlaybackTextRun("k", "v"))
        )
        assertEquals("bg1", artboardItem.backgroundTrackId)
        assertEquals(5000, artboardItem.durationMs)

        val playlistItem = ApiYearlyPlaybackTimelineItem.SavePlaylistItem(
            backgroundTrackId = null,
            durationMs = 6000,
            artworkUrl = "https://art.jpg",
            backgroundImageUrl = "https://bg.jpg",
            text = "Playlist",
            activeTitle = "Save",
            inactiveTitle = "Saved",
            urn = "soundcloud:playlists:1"
        )
        assertEquals(6000, playlistItem.durationMs)

        val insightsItem = ApiYearlyPlaybackTimelineItem.OpenInsightsItem(
            backgroundTrackId = null,
            durationMs = 7000,
            backgroundImageUrl = "https://bg.jpg",
            text = "Insights",
            title = "Summary"
        )
        assertEquals("Summary", insightsItem.title)

        val unknownItem = ApiYearlyPlaybackTimelineItem.Unknown
        assertEquals(5000, unknownItem.durationMs)
    }

    @Test
    fun testParseTimelineGraphQL() {
        val timelineArray = JsonArray().apply {
            // Slide 1: Artboard
            add(JsonObject().apply {
                addProperty("__typename", "ArtboardItem")
                addProperty("artboardId", "IntroSlide")
                addProperty("durationMs", 4500)
                addProperty("backgroundTrackId", "bg_1")
                add("textRuns", JsonArray().apply {
                    add(JsonObject().apply {
                        addProperty("key", "title")
                        addProperty("value", "Hello 2025")
                    })
                })
            })

            // Slide 2: Save Playlist
            add(JsonObject().apply {
                addProperty("__typename", "SavePlaylistItem")
                addProperty("durationMs", 5500)
                addProperty("artworkUrl", "https://art.jpg")
                addProperty("backgroundImageUrl", "https://bg.jpg")
                addProperty("text", "Top 2025")
                addProperty("activeTitle", "Save Playlist")
                addProperty("inactiveTitle", "Saved")
                addProperty("urn", "soundcloud:playlists:777")
            })

            // Slide 3: Open Insights
            add(JsonObject().apply {
                addProperty("__typename", "OpenInsightsItem")
                addProperty("durationMs", 6000)
                addProperty("title", "Stats Recap")
                addProperty("text", "See details")
                addProperty("backgroundImageUrl", "https://bg2.jpg")
            })
        }

        val bgTracksArray = JsonArray().apply {
            add(JsonObject().apply {
                addProperty("backgroundTrackId", "bg_1")
                add("track", JsonObject().apply {
                    addProperty("urn", "soundcloud:tracks:12345")
                    addProperty("title", "Background Hit")
                    addProperty("fullDuration", 180000L)
                    add("user", JsonObject().apply {
                        addProperty("name", "Featured Artist")
                    })
                })
            })
        }

        val blocks = YearlyPlaybackRepository.parseTimeline(timelineArray, bgTracksArray)
        assertEquals(3, blocks.size)

        // Verify ArtBoard Block
        val b0 = blocks[0] as Block.ArtBoard
        assertEquals("IntroSlide", b0.artboardId)
        assertEquals(4500, b0.durationMs)
        assertEquals(0, b0.index)
        assertNotNull(b0.backgroundTrack)
        assertEquals("Background Hit", b0.backgroundTrack?.title)
        assertEquals(1, b0.textRuns.size)
        assertEquals("Hello 2025", b0.textRuns[0].value)

        // Verify SavePlaylist Block
        val b1 = blocks[1] as Block.SavePlaylist
        assertEquals(5500, b1.durationMs)
        assertEquals(1, b1.index)
        assertEquals("soundcloud:playlists:777", b1.playlistUrn)
        assertEquals("Save Playlist", b1.activeTitle)

        // Verify OpenInsights Block
        val b2 = blocks[2] as Block.OpenInsights
        assertEquals(6000, b2.durationMs)
        assertEquals(2, b2.index)
        assertEquals("Stats Recap", b2.title)
    }

    @Test
    fun testYearlyPlaybackEventsAndStates() {
        val block = Block.ArtBoard(
            artboardId = "1",
            durationMs = 5000,
            index = 0,
            backgroundTrack = null,
            textRuns = emptyList()
        )
        val displayState = YearlyPlaybackState.Display(
            block = block,
            totalBlocks = 5,
            durationMs = 5000
        )
        assertEquals("1", displayState.block.artboardId)
        assertEquals(5, displayState.totalBlocks)
        assertEquals(5000, displayState.durationMs)

        assertEquals(YearlyPlaybackState.Empty, YearlyPlaybackState.Empty)
        val errorState = YearlyPlaybackState.Error(ErrorType.NETWORK)
        assertEquals(ErrorType.NETWORK, errorState.errorType)

        val nextEvent = YearlyPlaybackEvent.Next(fromIndex = 0, toIndex = 1)
        val prevEvent = YearlyPlaybackEvent.Previous(fromIndex = 1, toIndex = 0)
        val pauseEvent = YearlyPlaybackEvent.Pause(index = 0)
        val resumeEvent = YearlyPlaybackEvent.Resume(index = 0)
        val shareEvent = YearlyPlaybackEvent.Share(index = 0, blockId = "1")
        val likeEvent = YearlyPlaybackEvent.LikePlaylist(urn = "soundcloud:playlists:123", liked = true)

        assertEquals(0, nextEvent.fromIndex)
        assertEquals(1, nextEvent.toIndex)
        assertEquals(1, prevEvent.fromIndex)
        assertEquals(0, prevEvent.toIndex)
        assertEquals(0, pauseEvent.index)
        assertEquals(0, resumeEvent.index)
        assertEquals("1", shareEvent.blockId)
        assertTrue(likeEvent.liked)
    }

    @Test
    fun testSoundCloudAuthenticPayloadParsing() {
        val jsonPayload = """
        {
          "data": {
            "yearlyFanPlayback2025": {
              "assets": [
                {"assetId":"ysc2025-fan","url":"https://assets.web.soundcloud.cloud/n/animations/ysc2025-fan-v1.riv"},
                {"assetId":"topSongsListImage01","url":"https://i1.sndcdn.com/artworks-HiKhHdq07Ruoe3eL-UKXbbQ-{size}.jpg"},
                {"assetId":"topArtistsImage01","url":"https://i1.sndcdn.com/avatars-cL0d28033dpAmgUq-h2PEtQ-{size}.jpg"}
              ],
              "timeline": [
                {"__typename":"ArtboardItem","backgroundTrackId":"soundcloud:tracks:2128012266","durationMs":7000,"artboardId":"fansIntro","textRuns":null},
                {"__typename":"ArtboardItem","backgroundTrackId":"soundcloud:tracks:2128012266","durationMs":7000,"artboardId":"topSongsIntro","textRuns":null},
                {"__typename":"ArtboardItem","backgroundTrackId":"soundcloud:tracks:2128012266","durationMs":10000,"artboardId":"topSongsList","textRuns":[{"key":"trackName1","value":"click 2 cry!"},{"key":"artistName1","value":"notik!"}]},
                {"__typename":"ArtboardItem","backgroundTrackId":"soundcloud:tracks:1694561784","durationMs":7000,"artboardId":"topArtistIntro","textRuns":null},
                {"__typename":"ArtboardItem","backgroundTrackId":"soundcloud:tracks:1694561784","durationMs":10000,"artboardId":"topArtistsList","textRuns":[{"key":"artistName1","value":"m1v"},{"key":"plays1","value":"479"}]},
                {"__typename":"ArtboardItem","backgroundTrackId":"soundcloud:tracks:359322221","durationMs":7000,"artboardId":"fansListenedTime","textRuns":[{"key":"textTop","value":"56,688"},{"key":"textMid","value":"56,688"},{"key":"textBot","value":"56,688"}]},
                {"__typename":"SavePlaylistItem","backgroundTrackId":"soundcloud:tracks:1852198254","durationMs":15000,"artworkUrl":"https://i1.sndcdn.com/artworks-qqfFEldYxABdSjMz-ADU0sQ-{size}.jpg","backgroundImageUrl":"https://i1.sndcdn.com/artworks-A1jq0ClyIQQslXUu-eyzDzQ-original.jpg","text":"Here’s your 2025 Playback","activeTitle":"Saved","inactiveTitle":"Save this playlist","urn":"soundcloud:system-playlists:your-playback:1204669792:2025"}
              ],
              "backgroundTracks": [
                {
                  "backgroundTrackId":"soundcloud:tracks:2128012266",
                  "track":{
                    "__typename":"Track",
                    "urn":"soundcloud:tracks:2128012266",
                    "title":"click 2 cry!",
                    "fullDuration":100206,
                    "snipDuration":30000,
                    "waveformUrl":"https://wave.sndcdn.com/TJqcIomJ2772_m.json",
                    "artworkUrlTemplate":"https://i1.sndcdn.com/artworks-HiKhHdq07Ruoe3eL-UKXbbQ-{size}.jpg",
                    "user":{"urn":"soundcloud:users:1449371771","name":"","avatarUrl":"https://i1.sndcdn.com/avatars-ylyLXK1XXiRq1NHb-Q5zF1A-large.jpg"}
                  }
                }
              ]
            }
          }
        }
        """.trimIndent()

        val json = com.google.gson.JsonParser.parseString(jsonPayload).asJsonObject
        val timelineArray = json.getAsJsonObject("data").getAsJsonObject("yearlyFanPlayback2025").getAsJsonArray("timeline")
        val bgTracksArray = json.getAsJsonObject("data").getAsJsonObject("yearlyFanPlayback2025").getAsJsonArray("backgroundTracks")

        val blocks = YearlyPlaybackRepository.parseTimeline(timelineArray, bgTracksArray)
        assertEquals(7, blocks.size)

        // Slide 0: fansIntro with textRuns == null
        val s0 = blocks[0] as Block.ArtBoard
        assertEquals("fansIntro", s0.artboardId)
        assertEquals(7000, s0.durationMs)
        assertTrue(s0.textRuns.isEmpty())
        assertEquals("click 2 cry!", s0.backgroundTrack?.title)

        // Slide 2: topSongsList
        val s2 = blocks[2] as Block.ArtBoard
        assertEquals("topSongsList", s2.artboardId)
        assertEquals("click 2 cry!", s2.textRuns.find { it.key == "trackName1" }?.value)

        // Slide 5: fansListenedTime
        val s5 = blocks[5] as Block.ArtBoard
        assertEquals("fansListenedTime", s5.artboardId)
        assertEquals("56,688", s5.textRuns.find { it.key == "textTop" }?.value)

        // Slide 6: SavePlaylistItem
        val s6 = blocks[6] as Block.SavePlaylist
        assertEquals("Here’s your 2025 Playback", s6.text)
        assertEquals("soundcloud:system-playlists:your-playback:1204669792:2025", s6.playlistUrn)
    }

    @Test
    fun testSystemPlaylistIdAndRouting() {
        val urn2024 = "soundcloud:system-playlists:your-playback:1204669792:2024"
        val id2024 = com.alananasss.kittytune.ui.yearlyplayback.YearlyPlaybackViewModel.extractPlaylistId(urn2024)
        assertEquals(1545548310L, id2024)
        assertTrue(id2024 > 0L)

        val permalink = "https://soundcloud.com/discover/sets/your-playback::alananasss:2024"
        val isSystem = permalink.contains("discover/sets/") || permalink.contains("your-playback")
        assertTrue(isSystem)
    }
}
