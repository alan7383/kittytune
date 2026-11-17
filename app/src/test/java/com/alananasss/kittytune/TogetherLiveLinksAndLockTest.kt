package com.alananasss.kittytune

import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherPlayer
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.ui.player.MenuTiles
import com.google.gson.JsonParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TogetherLiveLinksAndLockTest {

    @Before
    fun setUp() {
        Together.resetForTesting()
    }

    @After
    fun tearDown() {
        Together.resetForTesting()
    }

    @Test
    fun inviteUrlUsesHttpsForDiscordRichPresenceAndExtractsCleanly() {
        val code = "K7M9P3Q4"
        val url = TogetherLinks.inviteUrl(code)
        assertTrue(url.startsWith("https://"))
        assertEquals("https://alan7383.github.io/kittytune-website/?together=K7M9-P3Q4", url)
        assertEquals(code, TogetherLinks.extractCode(url, allowRawCode = false))
    }

    @Test
    fun extractCodeParsesAllSupportedLinkAndCodeFormats() {
        assertEquals("K7M9P3Q4", TogetherLinks.extractCode("kittytune://together/K7M9-P3Q4", allowRawCode = false))
        assertEquals("K7M9P3Q4", TogetherLinks.extractCode("kittytune://listen/k7m9p3q4", allowRawCode = false))
        assertEquals("K7M9P3Q4", TogetherLinks.extractCode("sc://together/K7M9-P3Q4", allowRawCode = false))
        assertEquals(
            "K7M9P3Q4",
            TogetherLinks.extractCode(
                "Listen along live with me on KittyTune!\nhttps://alan7383.github.io/kittytune-website/?together=K7M9-P3Q4\nCode: K7M9-P3Q4",
                allowRawCode = false
            )
        )
        assertEquals("K7M9P3Q4", TogetherLinks.extractCode("k7m9-p3q4", allowRawCode = true))
        assertEquals("K7M9P3Q4", TogetherLinks.extractCode("K7M9P3Q4", allowRawCode = true))

        // Normal search queries must NOT be hijacked when allowRawCode = false
        assertNull(TogetherLinks.extractCode("stardust", allowRawCode = false))
        assertNull(TogetherLinks.extractCode("metallica", allowRawCode = false))
        assertNull(TogetherLinks.extractCode("https://soundcloud.com/artist/track", allowRawCode = false))
    }

    @Test
    fun wireEncryptionPreservesHostLockedAndRemainsDesktopCompatible() {
        val code = "W4X8Y2Z6"
        val track = SharedTrack(42L, "Nightcall", "Kavinsky", null, 258_000L, "https://soundcloud.com/x/y", "soundcloud", 1L, "Kavinsky")
        val state = TogetherMessage.State("host-1", 1000L, track, 12_500L, isPlaying = true, automix = false, queueVersion = 2, hostLocked = true)

        val sealed = TogetherWire.seal(code, state)
        val opened = TogetherWire.open(code, sealed) as? TogetherMessage.State
        assertNotNull(opened)
        assertTrue(opened!!.hostLocked)
        assertEquals(12_500L, opened.positionMs)

        // Legacy / Desktop State JSON without hostLocked defaults to false
        val legacyJson = JsonParser.parseString(
            """{"type":"State","from":"desktop-1","sentAt":2000,"positionMs":5000,"isPlaying":true,"automix":false,"queueVersion":1}"""
        ).asJsonObject
        val parsedLegacy = TogetherMessage.fromJson(legacyJson) as? TogetherMessage.State
        assertNotNull(parsedLegacy)
        assertFalse(parsedLegacy!!.hostLocked)
    }

    @Test
    fun hostLockBlocksStrangerControlTakeoverAndRedirectsPlayNextToSuggestions() = runBlocking {
        val code = "H2J4K6M8"
        var hostControlCalled = false
        var hostPlayNextCalled = false

        val fakePlayer = object : TogetherPlayer {
            override fun sharedCurrent(): SharedTrack? = null
            override fun sharedPositionMs(): Long = 0L
            override fun sharedIsPlaying(): Boolean = true
            override fun sharedUpcoming(): List<SharedTrack> = emptyList()
            override fun sharedAutomix(): Boolean = false
            override fun followHost(track: SharedTrack, positionMs: Long, isPlaying: Boolean, upcoming: List<SharedTrack>?, automix: Boolean) = Unit
            override fun hostControl(action: String, positionMs: Long) { hostControlCalled = true }
            override fun hostPlayNext(track: SharedTrack) { hostPlayNextCalled = true }
            override fun hostPlay(tracks: List<SharedTrack>, index: Int) = Unit
        }
        Together.start(fakePlayer)

        Together.setRoomForTesting(
            code,
            Together.Room(code = code, name = "Live", hostId = Together.memberId, hostSeenAt = System.currentTimeMillis(), hostClaimedAt = 1000L, hostLocked = true)
        )
        Together.setHostLocked(true)

        val strangerId = "stranger-999"
        val suggestedTrack = SharedTrack(99L, "Midnight City", "M83", null, 243_000L, null, "soundcloud", 2L, "M83")

        // 1. Stranger tries to pause -> blocked!
        Together.handle(code, TogetherMessage.Control(strangerId, System.currentTimeMillis(), TogetherMessage.CONTROL_PAUSE, 5000L))
        assertFalse(hostControlCalled)

        // 2. Stranger tries to ClaimHost -> blocked, true host remains host!
        Together.handle(code, TogetherMessage.ClaimHost(strangerId, System.currentTimeMillis() + 50_000L))
        assertTrue(Together.isHostOf(code))

        // 3. Stranger sends PlayNext -> converted into a Suggestion instead of hijacking queue!
        Together.handle(code, TogetherMessage.PlayNext(strangerId, System.currentTimeMillis(), suggestedTrack, "Stranger"))
        assertFalse(hostPlayNextCalled)
        assertEquals(1, Together.rooms.value[code]?.suggestions?.size)
        assertEquals(99L, Together.rooms.value[code]?.suggestions?.single()?.track?.id)
    }

    @Test
    fun trackMenuCatalogueIncludesListenTogetherTile() {
        val tiles = MenuTiles.catalogue(PlayerPreferences.MENU_TRACK)
        assertTrue(tiles.any { it.id == "listen_together" })
    }
}
