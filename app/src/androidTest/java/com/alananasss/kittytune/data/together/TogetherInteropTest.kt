package com.alananasss.kittytune.data.together

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.alananasss.kittytune.data.together.SharedTrack
import com.alananasss.kittytune.data.together.Together
import com.alananasss.kittytune.data.together.TogetherMessage
import com.alananasss.kittytune.data.together.TogetherPlayer
import com.alananasss.kittytune.data.together.TogetherReaction
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private class TogetherInteropScenario(private val code: String) {
    private val current = track(1)
    private val playlist = (10L..62L).map(::track)
    private val savedTrack = track(99)
    private val rejectedTrack = track(100)
    private val player = TestPlayer(current)
    private val reaction = TogetherReaction.entries.first()

    suspend fun run(role: String) {
        Together.start(player)
        try {
            when (role) {
                "host" -> host()
                "listener" -> listen()
                else -> error("Expected host or listener")
            }
        } finally {
            Together.forget(code)
        }
    }

    private suspend fun host() = coroutineScope {
        Together.setHostLocked(false)
        check(Together.join(code) != null)
        Together.startListening(code)
        await("MQTT connection") { Together.isConnected.value }
        val receivedReaction = async(start = CoroutineStart.UNDISPATCHED) {
            withTimeoutOrNull(30_000) { Together.reactions.first { it.code == code } }
        }
        await("listener arrival") { room()?.listeners?.any { it.id != Together.memberId } == true }
        val listenerId = checkNotNull(room()).listeners.first { it.id != Together.memberId }.id
        await("ordered playlist spanning MQTT batches") { player.queue.map { it.id } == playlist.map { it.id } }
        await("remote pause and seek") { !player.playing && player.position == 24_000L }
        check(receivedReaction.await()?.reaction == reaction) { "Listener reaction was not received" }
        Together.setHostLocked(true)
        await("locked queue suggestion") {
            room()?.suggestions?.count { it.forQueue } == 2
        }
        check(!player.playing && player.position == 24_000L) { "Locked playback accepted remote controls" }
        check(player.queue.map { it.id } == playlist.map { it.id }) { "Locked playback directly queued a suggestion" }
        val suggestions = checkNotNull(room()).suggestions
        Together.acceptSuggestion(code, suggestions.first { it.forQueue && it.track.id == savedTrack.id })
        Together.declineSuggestion(code, suggestions.first { it.forQueue && it.track.id == rejectedTrack.id })
        await("shared playlist suggestion") { room()?.suggestions?.any { !it.forQueue && it.track == savedTrack } == true }
        Together.acceptSuggestion(code, checkNotNull(room()).suggestions.first { !it.forQueue && it.track.id == savedTrack.id })
        await("listener leaves the original room") { room()?.members?.get(listenerId)?.isListening == false }
        check(player.queue.map { it.id } == (playlist + savedTrack).map { it.id }) { "Approval or rejection produced the wrong queue" }
        check(Together.saved.value.first { it.code == code }.tracks == listOf(savedTrack))
        Together.forget(code)
        delay(500)
    }

    private suspend fun listen() {
        check(Together.joinAndListen(code) != null)
        check(Together.saved.value.any { it.code == code })
        await("host playback") { player.followedTrack == current && player.playing }
        check(!Together.isHostOf(code))
        check(!Together.mayChangeAutomix())
        val local = track(-1).copy(source = "local")
        Together.proposePlaylist(code, (playlist + local).map(SharedTrack::toTrack))
        await("synchronized queue") { player.followedQueue?.map { it.id } == playlist.take(30).map { it.id } }
        // Reactions are transient, so allow the broker a few chances to deliver one.
        repeat(3) {
            Together.sendReaction(code, reaction)
            delay(150)
        }
        Together.control(TogetherMessage.CONTROL_PAUSE, 0)
        Together.control(TogetherMessage.CONTROL_SEEK, 24_000)
        await("synchronized pause, seek and host lock") {
            !player.playing && player.position == 24_000L && room()?.hostLocked == true
        }
        Together.control(TogetherMessage.CONTROL_PLAY, 0)
        Together.proposePlaylist(code, listOf(savedTrack.toTrack(), rejectedTrack.toTrack()))
        Together.suggest(code, savedTrack)
        await("approved shared playlist available to listener") { room()?.playlist == listOf(savedTrack) }
        check(Together.saved.value.first { it.code == code }.tracks == listOf(savedTrack))
        val nextRoom = Together.create("Next Together room")
        try {
            Together.startListening(nextRoom.code)
            check(Together.active.value == nextRoom.code)
            check(Together.isHostOf(nextRoom.code))
            await("previous host ends live broadcast") { room()?.hasLiveHost == false }
            Together.forget(code)
            check(Together.rooms.value[code] == null)
            check(Together.previewRoom(code) == code)
            await("shared playlist snapshot without a live host") { room()?.playlist == listOf(savedTrack) }
            Together.closePreview(code)
        } finally {
            Together.forget(nextRoom.code)
        }
    }

    private fun room() = Together.rooms.value[code]

    private suspend fun await(description: String, condition: () -> Boolean) {
        check(withTimeoutOrNull(45_000) {
            while (!condition()) delay(50)
            true
        } == true) { "Timed out waiting for $description, MQTT connected=${Together.isConnected.value}, " +
            "host queue=${player.queue.map { it.id }}, followed queue=${player.followedQueue?.map { it.id }}, " +
            "playlist=${room()?.playlist}, suggestions=${room()?.suggestions}" }
        println("Together: $description")
    }

    private fun track(id: Long) = SharedTrack(id, "Interop $id", "KittyTune", null, 120_000L, null, "soundcloud", 1L, "KittyTune")

    private class TestPlayer(private val current: SharedTrack) : TogetherPlayer {
        @Volatile var playing = true
        @Volatile var position = 0L
        @Volatile var queue = emptyList<SharedTrack>()
        @Volatile var followedTrack: SharedTrack? = null
        @Volatile var followedQueue: List<SharedTrack>? = null

        override fun sharedCurrent() = current
        override fun sharedPositionMs() = position
        override fun sharedIsPlaying() = playing
        override fun sharedUpcoming() = queue
        override fun sharedAutomix() = false

        override fun followHost(track: SharedTrack, positionMs: Long, isPlaying: Boolean, upcoming: List<SharedTrack>?, automix: Boolean) {
            followedTrack = track
            position = positionMs
            playing = isPlaying
            followedQueue = upcoming
        }

        override fun hostControl(action: String, positionMs: Long) {
            when (action) {
                TogetherMessage.CONTROL_PAUSE -> playing = false
                TogetherMessage.CONTROL_PLAY -> playing = true
                TogetherMessage.CONTROL_SEEK -> position = positionMs
            }
            Together.onHostPlaybackChanged()
        }

        override fun hostPlayNext(track: SharedTrack) = hostPlayNext(listOf(track))

        override fun hostPlayNext(tracks: List<SharedTrack>) {
            queue = queue + tracks
            Together.onHostPlaybackChanged()
        }

        override fun hostPlay(tracks: List<SharedTrack>, index: Int) = Unit
    }
}

/** Pass togetherCode and togetherRole (host or listener) to am instrument for each peer. */
@RunWith(AndroidJUnit4::class)
class TogetherInteropTest {
    @Test
    fun exchangeTogetherMessagesWithPeer() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        val code = args.getString("togetherCode")
        val role = args.getString("togetherRole")
        assumeTrue("Requires a second client with the same room code", code != null && role != null)
        args.getString("togetherBroker")?.let { System.setProperty("kittytune.together.testBroker", it) }
        Together.init(InstrumentationRegistry.getInstrumentation().targetContext)
        TogetherInteropScenario(checkNotNull(code)).run(checkNotNull(role))
    }
}
