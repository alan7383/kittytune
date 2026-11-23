package com.alananasss.kittytune

import com.alananasss.kittytune.data.discord.DiscordRemoteAuthManager
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.together.SharedListener
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherLinks
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherPlayer
import com.alananasss.kittytune.data.together.TogetherRelay
import com.alananasss.kittytune.data.together.TogetherWire
import com.alananasss.kittytune.ui.player.MenuTiles
import com.google.gson.JsonParser
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
        assertEquals("https://kittytune-invite.kt-net.workers.dev/?together=K7M9-P3Q4", url.substringBefore("&from="))
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

    @Test
    fun wireRoundTripAndLegacyCompatibilityForHelloAndStateAvatars() {
        val code = "A2B3C4D5"
        val scAvatar = "https://i1.sndcdn.com/avatars-000123-abc-t200x200.jpg"
        val dcAvatar = "https://cdn.discordapp.com/avatars/80351110224678912/8342729096ea3675442027381ff50dfe.png?size=128"

        val hello = TogetherMessage.Hello(
            from = "listener-1",
            sentAt = 1000L,
            name = "Alice",
            isListening = true,
            avatarUrl = scAvatar,
        )
        val helloJson = hello.toJson()
        assertEquals("Hello", helloJson.get("type")?.asString)
        assertEquals(scAvatar, helloJson.get("avatarUrl")?.asString)

        val openedHello = TogetherWire.open(code, TogetherWire.seal(code, hello)) as? TogetherMessage.Hello
        assertNotNull(openedHello)
        assertEquals("Alice", openedHello!!.name)
        assertEquals(scAvatar, openedHello.avatarUrl)

        val track = SharedTrack(7L, "Genesis", "Justice", null, 234_000L, null, "soundcloud", 3L, "Justice")
        val state = TogetherMessage.State(
            from = "host-1",
            sentAt = 2000L,
            track = track,
            positionMs = 42_000L,
            isPlaying = true,
            automix = true,
            queueVersion = 4,
            hostLocked = true,
            hostName = "Bob",
            hostAvatarUrl = dcAvatar,
        )
        val stateJson = state.toJson()
        assertEquals("State", stateJson.get("type")?.asString)
        assertEquals("Bob", stateJson.get("hostName")?.asString)
        assertEquals(dcAvatar, stateJson.get("hostAvatarUrl")?.asString)

        val openedState = TogetherWire.open(code, TogetherWire.seal(code, state)) as? TogetherMessage.State
        assertNotNull(openedState)
        assertEquals("Bob", openedState!!.hostName)
        assertEquals(dcAvatar, openedState.hostAvatarUrl)

        // Legacy Hello & State payloads without avatar fields deserialize cleanly with null avatar URLs
        val legacyHelloJson = JsonParser.parseString(
            """{"type":"Hello","from":"legacy-peer","sentAt":3000,"name":"OldClient","isListening":true}"""
        ).asJsonObject
        val parsedLegacyHello = TogetherMessage.fromJson(legacyHelloJson) as? TogetherMessage.Hello
        assertNotNull(parsedLegacyHello)
        assertEquals("OldClient", parsedLegacyHello!!.name)
        assertNull(parsedLegacyHello.avatarUrl)

        val legacyStateJson = JsonParser.parseString(
            """{"type":"State","from":"legacy-host","sentAt":4000,"positionMs":1000,"isPlaying":false,"automix":false,"queueVersion":0,"hostLocked":false,"hostName":"OldHost"}"""
        ).asJsonObject
        val parsedLegacyState = TogetherMessage.fromJson(legacyStateJson) as? TogetherMessage.State
        assertNotNull(parsedLegacyState)
        assertEquals("OldHost", parsedLegacyState!!.hostName)
        assertNull(parsedLegacyState.hostAvatarUrl)
    }

    @Test
    fun avatarPriorityResolvesSoundCloudThenDiscordThenNull() {
        assertNull(Together.myAvatarUrl)
        assertNull(Together.myAvatarUrlFlow.value)

        val discordUrl = DiscordRemoteAuthManager.buildAvatarUrl("80351110224678912", "8342729096ea3675442027381ff50dfe")
        assertEquals(
            "https://cdn.discordapp.com/avatars/80351110224678912/8342729096ea3675442027381ff50dfe.png?size=128",
            discordUrl
        )
        assertNull(DiscordRemoteAuthManager.buildAvatarUrl("80351110224678912", null))
        assertNull(DiscordRemoteAuthManager.buildAvatarUrl("80351110224678912", "   "))
        assertNull(DiscordRemoteAuthManager.buildAvatarUrl("80351110224678912", "null"))

        // 1. Both SoundCloud and Discord present -> SoundCloud wins and upgrades -large to -t200x200
        Together.discordAvatarUrl = discordUrl
        Together.soundCloudAvatarUrl = "https://i1.sndcdn.com/avatars-000555-xyz-large.jpg"
        assertEquals("https://i1.sndcdn.com/avatars-000555-xyz-t200x200.jpg", Together.myAvatarUrl)
        assertEquals("https://i1.sndcdn.com/avatars-000555-xyz-t200x200.jpg", Together.myAvatarUrlFlow.value)

        // 2. SoundCloud placeholder (default_avatar) -> falls back to Discord
        Together.soundCloudAvatarUrl = "https://a1.sndcdn.com/images/default_avatar_large.png"
        assertEquals(discordUrl, Together.myAvatarUrl)

        // 3. Non-HTTPS SoundCloud URL -> falls back to Discord
        Together.soundCloudAvatarUrl = "http://i1.sndcdn.com/avatars-000555-xyz-large.jpg"
        assertEquals(discordUrl, Together.myAvatarUrl)

        // 4. SoundCloud cleared (guest mode / logout) -> falls back to Discord
        Together.soundCloudAvatarUrl = null
        assertEquals(discordUrl, Together.myAvatarUrl)

        // 5. Non-HTTPS or blank Discord URL -> falls back to null (initial letter)
        Together.discordAvatarUrl = "http://cdn.discordapp.com/avatars/1/abc.png"
        assertNull(Together.myAvatarUrl)
        Together.discordAvatarUrl = "   "
        assertNull(Together.myAvatarUrl)
        Together.discordAvatarUrl = null
        assertNull(Together.myAvatarUrl)
        assertNull(Together.myAvatarUrlFlow.value)
    }

    @Test
    fun handleStoresIncomingHelloAndStateAvatarsOnMembersAndRejectsPlaceholders() = runBlocking {
        val code = "P5Q6R7S8"
        Together.setRoomForTesting(code, Together.Room(code = code, name = "Avatars"))

        val peerAvatar = "https://i1.sndcdn.com/avatars-000777-def-t200x200.jpg"
        val hostAvatar = "https://cdn.discordapp.com/avatars/999/hash123.png?size=128"

        Together.handle(code, TogetherMessage.Hello("peer-1", 1000L, "PeerOne", isListening = true, avatarUrl = peerAvatar))
        assertEquals(peerAvatar, Together.rooms.value[code]?.members?.get("peer-1")?.avatarUrl)

        // Placeholder SoundCloud avatar from remote peer is sanitized to null so UI shows initial letter
        Together.handle(
            code,
            TogetherMessage.Hello(
                "peer-2",
                1000L,
                "PeerTwo",
                isListening = true,
                avatarUrl = "https://a1.sndcdn.com/images/default_avatar_large.png"
            )
        )
        assertNull(Together.rooms.value[code]?.members?.get("peer-2")?.avatarUrl)

        // Host State populates host's Member entry with hostName and hostAvatarUrl
        Together.handle(
            code,
            TogetherMessage.State(
                from = "host-9",
                sentAt = System.currentTimeMillis(),
                track = null,
                positionMs = 0L,
                isPlaying = false,
                automix = false,
                queueVersion = 0,
                hostLocked = false,
                hostName = "HostNine",
                hostAvatarUrl = hostAvatar,
            )
        )
        val hostMember = Together.rooms.value[code]?.members?.get("host-9")
        assertNotNull(hostMember)
        assertEquals("HostNine", hostMember?.name)
        assertEquals(hostAvatar, hostMember?.avatarUrl)

        // Subsequent State without hostAvatarUrl preserves known avatarUrl
        Together.handle(
            code,
            TogetherMessage.State(
                from = "host-9",
                sentAt = System.currentTimeMillis(),
                track = null,
                positionMs = 1000L,
                isPlaying = false,
                automix = false,
                queueVersion = 0,
                hostLocked = false,
                hostName = "HostNine",
                hostAvatarUrl = null,
            )
        )
        assertEquals(hostAvatar, Together.rooms.value[code]?.members?.get("host-9")?.avatarUrl)
    }

    @Test
    fun snapshotTopicsDeriveDeterministicallyAndRelayResolvesLiveAndSnapshotTopics() {
        val raw = "k7m9-p3q4"
        val normalized = "K7M9P3Q4"
        val expectedHash = MessageDigest.getInstance("SHA-256")
            .digest(("snap:$normalized").toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(32)

        assertEquals("kittytune/together/snap/$expectedHash/state", TogetherWire.stateSnapshotTopicFor(raw))
        assertEquals("kittytune/together/snap/$expectedHash/queue", TogetherWire.queueSnapshotTopicFor(raw))
        assertEquals("kittytune/together/snap/$expectedHash/#", TogetherWire.snapshotWildcardTopicFor(raw))
        assertEquals(TogetherWire.stateSnapshotTopicFor(normalized), TogetherWire.stateSnapshotTopicFor(raw))
        assertNotEquals(
            TogetherWire.topicFor(normalized).removePrefix("kittytune/together/"),
            expectedHash
        )

        val relay = TogetherRelay("test-relay-snap")
        try {
            relay.follow(raw)
            assertTrue(relay.isFollowing(normalized))
            assertEquals(normalized, relay.resolveCodeForTopic(TogetherWire.topicFor(normalized)))
            assertEquals(normalized, relay.resolveCodeForTopic(TogetherWire.stateSnapshotTopicFor(normalized)))
            assertEquals(normalized, relay.resolveCodeForTopic(TogetherWire.queueSnapshotTopicFor(normalized)))
            assertNull(relay.resolveCodeForTopic(TogetherWire.stateSnapshotTopicFor("ZZZZ9999")))

            relay.unfollow(raw)
            assertFalse(relay.isFollowing(normalized))
            assertNull(relay.resolveCodeForTopic(TogetherWire.topicFor(normalized)))
            assertNull(relay.resolveCodeForTopic(TogetherWire.stateSnapshotTopicFor(normalized)))
            assertNull(relay.resolveCodeForTopic(TogetherWire.queueSnapshotTopicFor(normalized)))
        } finally {
            relay.close()
        }
    }

    @Test
    fun stateListenersRoundTripOnWireAndPopulateAllRoomListenersOnFirstPacket() = runBlocking {
        val code = "M3N4P5Q6"
        val hostAvatar = "https://i1.sndcdn.com/avatars-000111-host-t200x200.jpg"
        val aliceAvatar = "https://cdn.discordapp.com/avatars/12345/alicehash.png?size=128"
        val placeholderAvatar = "https://a1.sndcdn.com/images/default_avatar_large.png"
        val track = SharedTrack(88L, "Veridis Quo", "Daft Punk", null, 345_000L, null, "soundcloud", 9L, "Daft Punk")

        val sharedListeners = listOf(
            SharedListener(id = "host-42", name = "HostDJ", avatarUrl = hostAvatar),
            SharedListener(id = "listener-alice", name = "Alice", avatarUrl = aliceAvatar),
            SharedListener(id = "listener-bob", name = "Bob", avatarUrl = placeholderAvatar),
            SharedListener(id = Together.memberId, name = "SelfEcho", avatarUrl = aliceAvatar),
        )
        val state = TogetherMessage.State(
            from = "host-42",
            sentAt = System.currentTimeMillis(),
            track = track,
            positionMs = 15_000L,
            isPlaying = true,
            automix = false,
            queueVersion = 3,
            hostLocked = false,
            hostName = "HostDJ",
            hostAvatarUrl = hostAvatar,
            listeners = sharedListeners,
        )

        val json = state.toJson()
        val listenersJson = json.getAsJsonArray("listeners")
        assertNotNull(listenersJson)
        assertEquals(4, listenersJson.size())
        assertEquals("listener-alice", listenersJson[1].asJsonObject.get("id").asString)
        assertEquals("Alice", listenersJson[1].asJsonObject.get("name").asString)
        assertEquals(aliceAvatar, listenersJson[1].asJsonObject.get("avatarUrl").asString)

        val opened = TogetherWire.open(code, TogetherWire.seal(code, state)) as? TogetherMessage.State
        assertNotNull(opened)
        assertEquals(4, opened!!.listeners?.size)
        assertEquals("Alice", opened.listeners?.get(1)?.name)

        // Legacy State payload without listeners still deserializes cleanly with null listeners
        val legacyStateJson = JsonParser.parseString(
            """{"type":"State","from":"legacy-host","sentAt":5000,"positionMs":0,"isPlaying":true,"automix":false,"queueVersion":1}"""
        ).asJsonObject
        val legacyState = TogetherMessage.fromJson(legacyStateJson) as? TogetherMessage.State
        assertNotNull(legacyState)
        assertNull(legacyState!!.listeners)

        // Applying State populates host + remote listeners immediately without waiting 8s for Hello
        Together.setRoomForTesting(code, Together.Room(code = code, name = "InstantRoom"))
        Together.handle(code, opened)

        val room = Together.rooms.value[code]
        assertNotNull(room)
        assertTrue(room!!.hasLiveHost)
        assertEquals(3, room.listeners.size)
        assertEquals(hostAvatar, room.members["host-42"]?.avatarUrl)
        assertEquals("Alice", room.members["listener-alice"]?.name)
        assertEquals(aliceAvatar, room.members["listener-alice"]?.avatarUrl)
        assertEquals("Bob", room.members["listener-bob"]?.name)
        assertNull(room.members["listener-bob"]?.avatarUrl)
        assertNull(room.members[Together.memberId])
    }

    @Test
    fun staleRetainedSnapshotIsRejectedWhileFreshRetainedSnapshotPopulatesRoom() = runBlocking {
        val code = "S7T8U9V2"
        Together.setRoomForTesting(code, Together.Room(code = code, name = "SnapshotGuard"))

        val track = SharedTrack(101L, "Instant Crush", "Daft Punk", null, 337_000L, null, "soundcloud", 9L, "Daft Punk")
        val now = System.currentTimeMillis()

        // 1. Stale snapshot (> 60s old) from crashed host must be ignored
        val stalePast = TogetherMessage.State(
            from = "crashed-host",
            sentAt = now - 120_000L,
            track = track,
            positionMs = 30_000L,
            isPlaying = true,
            automix = false,
            queueVersion = 1,
            hostName = "GhostHost",
            listeners = listOf(SharedListener("crashed-host", "GhostHost")),
        )
        Together.handle(code, stalePast, isSnapshot = true)
        assertNull(Together.rooms.value[code]?.hostId)
        assertNull(Together.rooms.value[code]?.nowPlaying)
        assertTrue(Together.rooms.value[code]?.listeners.isNullOrEmpty())

        // 2. Future-skewed stale snapshot (abs(now - sentAt) > 60s) is also ignored
        val staleFuture = stalePast.copy(sentAt = now + 120_000L)
        Together.handle(code, staleFuture, isSnapshot = true)
        assertNull(Together.rooms.value[code]?.hostId)

        // 3. Fresh retained snapshot (< 60s old) immediately populates host, track, and listeners
        val freshSnapshot = TogetherMessage.State(
            from = "live-host",
            sentAt = now - 3_000L,
            track = track,
            positionMs = 45_000L,
            isPlaying = true,
            automix = true,
            queueVersion = 2,
            hostLocked = true,
            hostName = "LiveHost",
            hostAvatarUrl = "https://i1.sndcdn.com/avatars-000999-live-t200x200.jpg",
            listeners = listOf(
                SharedListener("live-host", "LiveHost", "https://i1.sndcdn.com/avatars-000999-live-t200x200.jpg"),
                SharedListener("peer-7", "Chloe", "https://cdn.discordapp.com/avatars/77/chloe.png?size=128"),
            ),
        )
        Together.handle(code, freshSnapshot, isSnapshot = true)

        val room = Together.rooms.value[code]
        assertNotNull(room)
        assertEquals("live-host", room!!.hostId)
        assertTrue(room.hasLiveHost)
        assertEquals(101L, room.nowPlaying?.id)
        assertEquals(2, room.listeners.size)
        assertEquals("Chloe", room.members["peer-7"]?.name)
    }
}
