package com.alananasss.kittytune

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.alananasss.kittytune.data.ConsolidatedPlayerState
import com.alananasss.kittytune.data.PlaybackService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

class PlayerStateSynchronizationTest {

    private class FakeExoPlayer : InvocationHandler {
        var items: MutableList<MediaItem> = mutableListOf()
        var currentIndex: Int = C.INDEX_UNSET
        var currentItem: MediaItem? = null
        var playbackState: Int = Player.STATE_IDLE
        var isPlaying: Boolean = false
        var playWhenReady: Boolean = false
        var duration: Long = 0L
        var currentPosition: Long = 0L

        val listeners = mutableListOf<Player.Listener>()

        fun createProxy(): Player {
            return Proxy.newProxyInstance(
                Player::class.java.classLoader,
                arrayOf(Player::class.java),
                this
            ) as Player
        }

        fun playInitial(item: MediaItem) {
            items = mutableListOf(item)
            currentIndex = 0
            currentItem = item
            playbackState = Player.STATE_READY
            isPlaying = true
            playWhenReady = true
            duration = 180_000L
            currentPosition = 10_000L
        }

        /**
         * Simulates rapid queue handoff at an arbitrary startIndex with intermediate transient states.
         */
        fun replaceQueueWithHandoff(
            newItems: List<MediaItem>,
            startIndex: Int,
            onIntermediateCallback: () -> Unit
        ) {
            // 1. Batch replacement begins: queue count updates, but index/item are temporarily unset
            items = newItems.toMutableList()
            currentIndex = C.INDEX_UNSET
            currentItem = null
            playbackState = Player.STATE_BUFFERING

            // Notify intermediate transient state to listener
            listeners.forEach {
                it.onPlaybackStateChanged(playbackState)
            }
            onIntermediateCallback()

            // 2. Timeline and position settle authoritatively on startIndex
            currentIndex = startIndex
            currentItem = newItems[startIndex]
            playbackState = Player.STATE_READY
            isPlaying = true
            playWhenReady = true
            duration = 200_000L
            currentPosition = 0L

            listeners.forEach {
                it.onMediaItemTransition(currentItem, Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED)
                it.onPlaybackStateChanged(playbackState)
                it.onIsPlayingChanged(isPlaying)
            }
        }

        override fun invoke(proxy: Any?, method: Method, args: Array<out Any>?): Any? {
            return when (method.name) {
                "getCurrentMediaItem" -> currentItem
                "getCurrentMediaItemIndex" -> currentIndex
                "getMediaItemCount" -> items.size
                "getPlaybackState" -> playbackState
                "isPlaying" -> isPlaying
                "getPlayWhenReady" -> playWhenReady
                "getDuration" -> duration
                "getCurrentPosition" -> currentPosition
                "getMediaMetadata" -> currentItem?.mediaMetadata ?: MediaMetadata.EMPTY
                "addListener" -> {
                    val l = args?.get(0) as? Player.Listener
                    if (l != null) listeners.add(l)
                    null
                }
                "removeListener" -> {
                    val l = args?.get(0) as? Player.Listener
                    if (l != null) listeners.remove(l)
                    null
                }
                "toString" -> "FakeExoPlayer(index=$currentIndex, count=${items.size})"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> proxy === args?.get(0)
                else -> {
                    val returnType = method.returnType
                    when {
                        returnType == Boolean::class.javaPrimitiveType -> false
                        returnType == Int::class.javaPrimitiveType -> 0
                        returnType == Long::class.javaPrimitiveType -> 0L
                        returnType == Float::class.javaPrimitiveType -> 0f
                        returnType == Double::class.javaPrimitiveType -> 0.0
                        else -> null
                    }
                }
            }
        }
    }

    @Before
    fun setup() {
        PlaybackService.resetPlayerState()
    }

    @Test
    fun playerState_isExpertOnCurrentTrack_duringRapidQueueHandoff() {
        val fakePlayer = FakeExoPlayer()
        val player = fakePlayer.createProxy()

        // 1. Initial State: Track A is playing in the old playlist
        val trackA = MediaItem.Builder()
            .setMediaId("track:101")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("Acoustic Ballad")
                    .setArtist("Acoustic Artist")
                    .build()
            )
            .build()

        fakePlayer.playInitial(trackA)
        PlaybackService.updatePlayerState(player)

        val initialState = PlaybackService.playerStateFlow.value
        assertEquals("track:101", initialState.mediaItem?.mediaId)
        assertEquals(0, initialState.mediaItemIndex)
        assertEquals(Player.STATE_READY, initialState.playbackState)
        assertTrue(initialState.isPlaying)

        // 2. Prepare new playlist with arbitrary startIndex = 2 (Track C)
        val newPlaylist = (0 until 5).map { idx ->
            val trackId = 200L + idx
            val title = if (idx == 2) "Heavy Rock Anthem" else "Queue Song #$idx"
            MediaItem.Builder()
                .setMediaId("track:$trackId")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(title)
                        .setArtist("Rock Band")
                        .build()
                )
                .build()
        }
        val targetIndex = 2
        val expectedTargetTrack = newPlaylist[targetIndex]

        val recordedEmissions = mutableListOf<ConsolidatedPlayerState>()

        // 3. Attach listener simulating PlaybackService listener hook
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                PlaybackService.updatePlayerState(player)
                recordedEmissions.add(PlaybackService.playerStateFlow.value)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                PlaybackService.updatePlayerState(player)
                recordedEmissions.add(PlaybackService.playerStateFlow.value)
            }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlaybackService.updatePlayerState(player)
                recordedEmissions.add(PlaybackService.playerStateFlow.value)
            }
        }
        player.addListener(listener)

        // 4. Trigger rapid queue handoff with transient intermediate state
        var intermediateStateFiltered = false
        fakePlayer.replaceQueueWithHandoff(
            newItems = newPlaylist,
            startIndex = targetIndex,
            onIntermediateCallback = {
                // Verify that intermediate state guard suppressed unconfirmed emission
                val rawIntermediateState = PlaybackService.extractConsolidatedPlayerState(player)
                assertNull(
                    "Intermediate State Guard must drop transient state with unset index or null item during batch replacement",
                    rawIntermediateState
                )
                intermediateStateFiltered = true
            }
        )

        assertTrue("Intermediate callback should have been checked", intermediateStateFiltered)

        // 5. Final Authoritative State Verification
        val finalState = PlaybackService.playerStateFlow.value

        // Guarantee that the UI state machine is an authoritative expert on the target track
        assertEquals("track:202", finalState.mediaItem?.mediaId)
        assertEquals("Heavy Rock Anthem", finalState.mediaMetadata.title)
        assertEquals(targetIndex, finalState.mediaItemIndex)
        assertEquals(Player.STATE_READY, finalState.playbackState)
        assertTrue(finalState.isPlaying)

        // Verify that NO intermediate emission ever leaked an invalid index (-1) or re-emitted Track A under index 2
        recordedEmissions.forEach { emission ->
            assertNotNull("Emitted media item must never be null during active playback", emission.mediaItem)
            assertTrue("Emitted index must be non-negative", emission.mediaItemIndex >= 0)
            if (emission.mediaItemIndex == targetIndex) {
                assertEquals(
                    "Target index must strictly emit the target MediaItem without re-emitting the previously active track",
                    expectedTargetTrack.mediaId,
                    emission.mediaItem?.mediaId
                )
            }
        }
    }
}
