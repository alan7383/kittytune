package com.alananasss.kittytune

import androidx.media3.common.Player
import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.player.PlayButtonRecovery
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Issue #82: a YouTube Music track gets `abs(videoId.hashCode())` as its id, which is also the id
 * of some unrelated SoundCloud upload. "Routine (feat. David Whistle)" (cjPgaB0EXeU) hashes to
 * 1517756116, a DRM protected SoundCloud track, so looking it up on SoundCloud replaced the song
 * and stalled the player.
 */
class Issue82TrackIdentityTest {

    private val ytmRoutine = Track(
        id = abs("cjPgaB0EXeU".hashCode().toLong()),
        title = "Routine (feat. David Whistle)",
        artworkUrl = null,
        durationMs = 167_000,
        user = User(0, "Alan Walker", null),
        permalinkUrl = "https://youtube.com/watch?v=cjPgaB0EXeU",
        source = "youtube_music",
    )

    @Test fun `the reported youtube music id collides with a real soundcloud id range`() {
        assertTrue(ytmRoutine.id == 1517756116L)
    }

    @Test fun `youtube music track is never treated as a soundcloud id`() {
        assertFalse(ytmRoutine.isSoundCloudCatalogTrack)
        assertTrue(StreamResolver.isYoutubeTrack(ytmRoutine))
    }

    @Test fun `youtube track saved without a source is still recognised by its link`() {
        for (source in listOf(null, "soundcloud", "")) {
            val legacy = ytmRoutine.copy(source = source)
            assertFalse("source=$source", legacy.isSoundCloudCatalogTrack)
            assertTrue("source=$source", StreamResolver.isYoutubeTrack(legacy))
        }
        val shortLink = ytmRoutine.copy(source = null, permalinkUrl = "https://youtu.be/cjPgaB0EXeU")
        assertFalse(shortLink.isSoundCloudCatalogTrack)
        assertTrue(StreamResolver.isYoutubeTrack(shortLink))
        val musicLink = ytmRoutine.copy(source = null, permalinkUrl = "https://music.youtube.com/watch?v=cjPgaB0EXeU")
        assertFalse(musicLink.isSoundCloudCatalogTrack)
        assertTrue(StreamResolver.isYoutubeTrack(musicLink))
    }

    @Test fun `provider tracks are not soundcloud ids`() {
        val base = ytmRoutine.copy(permalinkUrl = null, source = null)
        assertFalse(base.copy(permalink = "deezer:track:123").isSoundCloudCatalogTrack)
        assertFalse(base.copy(permalink = "qobuz:track:123").isSoundCloudCatalogTrack)
        assertFalse(base.copy(permalink = "tidal:track:123").isSoundCloudCatalogTrack)
        assertFalse(base.copy(source = "deezer").isSoundCloudCatalogTrack)
        assertFalse(base.copy(source = "spotify").isSoundCloudCatalogTrack)
        assertFalse(base.copy(source = "vk").isSoundCloudCatalogTrack)
        assertFalse(base.copy(id = -5).isSoundCloudCatalogTrack)
    }

    @Test fun `real soundcloud tracks keep being hydrated`() {
        val api = ytmRoutine.copy(id = 298034631, source = null, permalink = "routine",
            permalinkUrl = "https://soundcloud.com/alanwalker/routine")
        assertTrue(api.isSoundCloudCatalogTrack)
        assertFalse(StreamResolver.isYoutubeTrack(api))
        assertTrue(api.copy(source = "soundcloud").isSoundCloudCatalogTrack)
        assertTrue(api.copy(permalinkUrl = null, permalink = null).isSoundCloudCatalogTrack)
        // A SoundCloud upload whose title mentions YouTube is still SoundCloud.
        assertTrue(api.copy(permalinkUrl = "https://soundcloud.com/u/youtube-com-rip").isSoundCloudCatalogTrack)
    }

    @Test fun `a leftover play wish on a stopped player does not turn the press into pause`() {
        // Exactly the state a failed resolution leaves: flags say "play", nothing is playing.
        assertFalse(PlayButtonRecovery.shouldPause(false, false, Player.STATE_IDLE, isLoading = false, wantsToPlay = true))
        // Restored session: prepared and paused, wish flag still set from before the restart.
        assertFalse(PlayButtonRecovery.shouldPause(false, false, Player.STATE_READY, isLoading = false, wantsToPlay = true))
    }

    @Test fun `press pauses while playing, loading or rebuffering`() {
        assertTrue(PlayButtonRecovery.shouldPause(true, true, Player.STATE_READY, isLoading = false, wantsToPlay = true))
        assertTrue(PlayButtonRecovery.shouldPause(false, false, Player.STATE_IDLE, isLoading = true, wantsToPlay = true))
        assertTrue(PlayButtonRecovery.shouldPause(false, true, Player.STATE_BUFFERING, isLoading = false, wantsToPlay = true))
        assertFalse(PlayButtonRecovery.shouldPause(false, false, Player.STATE_READY, isLoading = false, wantsToPlay = false))
    }

    @Test fun `play on a stopped player reloads the track`() {
        assertTrue(PlayButtonRecovery.mustReload(Player.STATE_IDLE, "42", 42))
    }

    @Test fun `play on the previous track's item reloads the track`() {
        assertTrue(PlayButtonRecovery.mustReload(Player.STATE_READY, "41", 42))
    }

    @Test fun `play on a paused ready player resumes it`() {
        assertFalse(PlayButtonRecovery.mustReload(Player.STATE_READY, "42", 42))
        assertFalse(PlayButtonRecovery.mustReload(Player.STATE_BUFFERING, "42", 42))
        assertFalse(PlayButtonRecovery.mustReload(Player.STATE_READY, "track:abc", 42))
    }
}
