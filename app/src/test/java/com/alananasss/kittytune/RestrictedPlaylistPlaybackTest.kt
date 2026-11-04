package com.alananasss.kittytune

import com.alananasss.kittytune.data.StreamResolver
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.local.toTrack
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.zionhuang.innertube.models.SongItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for GitHub issue #75:
 * Restricted playlist tracks (SoundCloud Go+ / SNIP) failing to play online or offline.
 */
class RestrictedPlaylistPlaybackTest {

    private fun createSong(id: String, title: String, durationSec: Int): SongItem {
        return SongItem(
            id = id,
            title = title,
            artists = emptyList(),
            duration = durationSec,
            thumbnail = ""
        )
    }

    @Test
    fun `restricted track with 30s snippet duration successfully matches full YouTube song`() {
        val candidates = listOf(
            createSong("teaser", "Track Teaser", 30),
            createSong("full_song", "Track Full Official Audio", 215)
        )

        // For a restricted track whose known duration is only the snippet (30s),
        // matchSongCandidate must NOT reject the 215s YouTube song.
        // It must select the full-length song (> 45s) over the snippet/teaser.
        val matched = StreamResolver.matchSongCandidate(
            songs = candidates,
            wantedSec = 30L,
            isRestricted = true
        )

        assertNotNull(matched)
        assertEquals("full_song", matched?.id)
        assertEquals(215, matched?.duration)
    }

    @Test
    fun `restricted track with full duration known matches closest full YouTube song`() {
        val candidates = listOf(
            createSong("short", "Track Snippet", 30),
            createSong("live", "Track Live", 300),
            createSong("official", "Track Official Audio", 215)
        )

        // When full duration (212s) is known, match closest within tolerance
        val matched = StreamResolver.matchSongCandidate(
            songs = candidates,
            wantedSec = 212L,
            isRestricted = true
        )

        assertNotNull(matched)
        assertEquals("official", matched?.id)
    }

    @Test
    fun `restricted track with short full duration matches short candidate within tolerance`() {
        val candidates = listOf(
            createSong("short_song", "Punk Interlude", 35),
            createSong("long_video", "Punk Interlude 1 Hour Loop", 3600)
        )

        // When a restricted track has a known short full duration (35s),
        // it must match the authentic 35s candidate rather than incorrectly selecting a >45s video,
        // even when isRestricted is true!
        val matched = StreamResolver.matchSongCandidate(
            songs = candidates,
            wantedSec = 35L,
            isRestricted = true,
            isSnippetDuration = false
        )

        assertNotNull(matched)
        assertEquals("short_song", matched?.id)
        assertEquals(35, matched?.duration)
    }

    @Test
    fun `video candidate with short full duration matches short candidate within tolerance`() {
        val candidates = listOf(
            org.schabi.newpipe.extractor.stream.StreamInfoItem(0, "https://youtube.com/watch?v=short", "Punk Interlude", org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM).apply { duration = 35L },
            org.schabi.newpipe.extractor.stream.StreamInfoItem(0, "https://youtube.com/watch?v=long", "Punk Interlude 1 Hour", org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM).apply { duration = 3600L }
        )

        val matched = StreamResolver.matchVideoCandidate(
            videoResults = candidates,
            wantedSec = 35L,
            isRestricted = true,
            isSnippetDuration = false
        )

        assertNotNull(matched)
        assertEquals("https://youtube.com/watch?v=short", matched?.url)
    }

    @Test
    fun `LocalTrack stub update logic updates 0 duration with short song duration and replaces placeholder title and artist`() {
        val stubTrack = LocalTrack(
            id = 555L,
            title = "",
            artist = "Unknown Artist",
            artworkUrl = "",
            duration = 0L,
            localAudioPath = "",
            localArtworkPath = "",
            source = "soundcloud"
        )

        val hydratedShortSong = Track(
            id = 555L,
            title = "Short Punk Interlude",
            artworkUrl = "https://example.com/cover.jpg",
            durationMs = 35000L,
            fullDuration = 35000L,
            user = User(10L, "Punk Band", null),
            policy = "SNIP",
            monetizationModel = "SUB_HIGH_TIER"
        )

        // Simulate updateStubTrackIfNeeded logic
        var updated = stubTrack
        var needsUpdate = false
        if ((stubTrack.duration <= 0L && hydratedShortSong.actualDurationMs > 0L) ||
            (stubTrack.duration in 1..45000L && hydratedShortSong.actualDurationMs > stubTrack.duration) ||
            (stubTrack.duration != hydratedShortSong.actualDurationMs && hydratedShortSong.actualDurationMs > 45000L)) {
            updated = updated.copy(duration = hydratedShortSong.actualDurationMs)
            needsUpdate = true
        }
        if (stubTrack.title.isBlank() || stubTrack.title == "Untitled Track") {
            hydratedShortSong.title?.takeIf { it.isNotBlank() }?.let {
                updated = updated.copy(title = it)
                needsUpdate = true
            }
        }
        if (stubTrack.artist.isBlank() || stubTrack.artist == "Unknown Artist") {
            val newArtist = hydratedShortSong.displayArtist.ifBlank { hydratedShortSong.user?.username ?: "" }
            if (newArtist.isNotBlank()) {
                updated = updated.copy(artist = newArtist)
                needsUpdate = true
            }
        }
        if (stubTrack.artworkUrl.isBlank() && !hydratedShortSong.fullResArtwork.isNullOrBlank()) {
            updated = updated.copy(artworkUrl = hydratedShortSong.fullResArtwork)
            needsUpdate = true
        }

        assertTrue("Stub track must need update", needsUpdate)
        assertEquals(35000L, updated.duration)
        assertEquals("Short Punk Interlude", updated.title)
        assertEquals("Punk Band", updated.artist)
        assertEquals("https://example.com/cover.jpg", updated.artworkUrl)
    }

    @Test
    fun `non-restricted track rejects candidate outside duration tolerance`() {
        val candidates = listOf(
            createSong("teaser", "Track Teaser", 30)
        )

        // A regular song of 210s must reject a 30s teaser
        val matched = StreamResolver.matchSongCandidate(
            songs = candidates,
            wantedSec = 210L,
            isRestricted = false
        )

        assertNull("Non-restricted track must not match a 30s teaser when 210s was wanted", matched)
    }

    @Test
    fun `SoundCloud Go+ track correctly reports restricted and blocked`() {
        val restrictedTrack = Track(
            id = 1801248981L,
            title = "Fortnight (feat. Post Malone)",
            artworkUrl = "https://example.com/art.jpg",
            durationMs = 30000L,
            user = User(1L, "Taylor Swift", null),
            policy = "SNIP",
            monetizationModel = "SUB_HIGH_TIER",
            fullDuration = 228965L
        )

        assertTrue(StreamResolver.isRestricted(restrictedTrack))
        assertTrue(StreamResolver.isBlockedForAccount(restrictedTrack))
        assertTrue(restrictedTrack.isGoPlus)
        assertTrue(restrictedTrack.isSnipped)
        assertEquals(228965L, restrictedTrack.actualDurationMs)
    }

    @Test
    fun `LocalTrack preserves actualDurationMs so playlist track retains full length`() {
        val restrictedTrack = Track(
            id = 1801248981L,
            title = "Fortnight (feat. Post Malone)",
            artworkUrl = "https://example.com/art.jpg",
            durationMs = 30000L,
            user = User(1L, "Taylor Swift", null),
            policy = "SNIP",
            monetizationModel = "SUB_HIGH_TIER",
            fullDuration = 228965L
        )

        // Saving LocalTrack using actualDurationMs
        val local = LocalTrack(
            id = restrictedTrack.id,
            title = restrictedTrack.title ?: "",
            artist = restrictedTrack.displayArtist,
            artworkUrl = restrictedTrack.artworkUrl ?: "",
            duration = restrictedTrack.actualDurationMs,
            localAudioPath = "/data/user/0/com.alananasss.kittytune/files/Fortnight.m4a",
            localArtworkPath = "",
            source = "soundcloud"
        )

        assertEquals(228965L, local.duration)

        // When restored from Room DB
        val restored = local.toTrack()
        assertEquals(228965L, restored.durationMs)
        assertEquals(228965L, restored.fullDuration)
        assertEquals(228965L, restored.actualDurationMs)
    }

    @Test
    fun `restricted track accepts candidate within extended 60s tolerance for music video intros`() {
        val candidates = listOf(
            createSong("mv_version", "Track Official Video with Intro", 255) // +45s difference
        )

        // For a restricted track with fullDuration 210s, a 255s video (+45s diff)
        // should match under the 60s restricted tolerance.
        val matchedRestricted = StreamResolver.matchSongCandidate(
            songs = candidates,
            wantedSec = 210L,
            isRestricted = true
        )
        assertNotNull("Restricted track should match candidate within 60s tolerance", matchedRestricted)
        assertEquals("mv_version", matchedRestricted?.id)

        // But for a non-restricted track, +45s diff must be rejected (> 25s tolerance)
        val matchedNonRestricted = StreamResolver.matchSongCandidate(
            songs = candidates,
            wantedSec = 210L,
            isRestricted = false
        )
        assertNull("Non-restricted track must reject candidate outside 25s tolerance", matchedNonRestricted)
    }

    @Test
    fun `restricted video candidate accepts candidate within 60s tolerance`() {
        val candidates = listOf(
            org.schabi.newpipe.extractor.stream.StreamInfoItem(0, "https://youtube.com/watch?v=mv", "Track MV", org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM).apply { duration = 250L }
        )

        val matchedRestricted = StreamResolver.matchVideoCandidate(
            videoResults = candidates,
            wantedSec = 210L,
            isRestricted = true
        )
        assertNotNull("Restricted track should match video candidate within 60s tolerance", matchedRestricted)
        assertEquals("https://youtube.com/watch?v=mv", matchedRestricted?.url)

        val matchedNonRestricted = StreamResolver.matchVideoCandidate(
            videoResults = candidates,
            wantedSec = 210L,
            isRestricted = false
        )
        assertNull("Non-restricted track must reject video candidate outside 25s tolerance", matchedNonRestricted)
    }

    @Test
    fun `hydration condition correctly identifies snippet and Go+ tracks lacking fullDuration`() {
        fun needsHydration(t: Track): Boolean {
            return t.user == null ||
                t.user?.id == 0L ||
                t.media == null ||
                t.playbackCount == 0 ||
                t.title.isNullOrBlank() ||
                ((t.isGoPlus || t.isSnipped) && t.fullDuration == null) ||
                (t.durationMs != null && t.durationMs in 1..45000L && t.fullDuration == null)
        }

        val snippetTrack = Track(
            id = 123L,
            title = "Restricted Track",
            artworkUrl = "",
            user = User(1L, "Artist", null),
            durationMs = 30000L,
            fullDuration = null,
            policy = "SNIP",
            monetizationModel = "SUB_HIGH_TIER"
        )
        assertTrue("Snippet track lacking fullDuration must need hydration", needsHydration(snippetTrack))

        val shortUnmarkedTrack = Track(
            id = 124L,
            title = "Unmarked Short Track",
            artworkUrl = "",
            user = User(1L, "Artist", null),
            durationMs = 30000L,
            fullDuration = null
        )
        assertTrue("Unmarked 30s track lacking fullDuration must need hydration", needsHydration(shortUnmarkedTrack))

        val fullyHydratedTrack = Track(
            id = 125L,
            title = "Hydrated Track",
            artworkUrl = "",
            user = User(1L, "Artist", null),
            durationMs = 30000L,
            fullDuration = 215000L,
            policy = "SNIP",
            monetizationModel = "SUB_HIGH_TIER"
        )
        // With user, title, and fullDuration known (even if media/playbackCount are placeholder),
        // it has fullDuration
        assertEquals(215000L, fullyHydratedTrack.actualDurationMs)
    }

    @Test
    fun `free track is not restricted and not blocked for account`() {
        val freeTrack = Track(
            id = 10001L,
            title = "Free Track",
            artworkUrl = "https://example.com/art.jpg",
            durationMs = 180000L,
            user = User(2L, "Indie Artist", null),
            policy = "ALLOW",
            monetizationModel = "BLACK_BOX",
            fullDuration = 180000L
        )

        assertFalse(StreamResolver.isRestricted(freeTrack))
        assertFalse(StreamResolver.isBlockedForAccount(freeTrack))
        assertFalse(freeTrack.isGoPlus)
        assertFalse(freeTrack.isSnipped)
        assertEquals(180000L, freeTrack.actualDurationMs)
    }

    @Test
    fun `isSnippetDuration correctly identifies when duration is preview vs authentic full duration`() {
        fun isSnippetDuration(track: Track): Boolean {
            val isRestrictedTrack = StreamResolver.isRestricted(track) || track.isSnipped || StreamResolver.isKnownRestricted(track) ||
                (track.durationMs != null && track.durationMs in 1..45000L)
            return isRestrictedTrack && track.fullDuration == null
        }

        // Restricted track with only 30s preview snippet duration (fullDuration = null)
        val snippetTrack = Track(
            id = 111L,
            title = "Preview Song",
            artworkUrl = "",
            durationMs = 30000L,
            fullDuration = null,
            policy = "SNIP",
            monetizationModel = "SUB_HIGH_TIER",
            user = User(1L, "Artist", null)
        )
        assertTrue("Restricted track with null fullDuration must be snippet duration", isSnippetDuration(snippetTrack))

        // Restricted track with known 35s full duration
        val shortFullTrack = Track(
            id = 222L,
            title = "Short Punk Interlude",
            artworkUrl = "",
            durationMs = 30000L,
            fullDuration = 35000L,
            policy = "SNIP",
            monetizationModel = "SUB_HIGH_TIER",
            user = User(1L, "Artist", null)
        )
        assertFalse("Restricted track with known fullDuration must NOT be snippet duration", isSnippetDuration(shortFullTrack))

        // Regular track with 35s duration
        val regularShortTrack = Track(
            id = 333L,
            title = "Regular Short Song",
            artworkUrl = "",
            durationMs = 35000L,
            fullDuration = 35000L,
            policy = "ALLOW",
            user = User(1L, "Artist", null)
        )
        assertFalse("Regular short track with fullDuration must NOT be snippet duration", isSnippetDuration(regularShortTrack))
    }

    @Test
    fun `markRestricted marks track ID as known restricted even without policy flags`() {
        val unmarkedTrack = Track(
            id = 999888777L,
            title = "Unmarked Restricted Song",
            artworkUrl = "",
            durationMs = 210000L,
            policy = null,
            monetizationModel = null,
            user = User(1L, "Artist", null)
        )

        assertFalse(StreamResolver.isRestricted(unmarkedTrack))
        assertFalse(StreamResolver.isKnownRestricted(unmarkedTrack))

        StreamResolver.markRestricted(unmarkedTrack.id)

        assertTrue(StreamResolver.isKnownRestricted(unmarkedTrack))
    }

    @Test
    fun `cleanSearchTitle retains inner text when title is entirely enclosed in brackets or parentheses`() {
        assertEquals("Intro", StreamResolver.cleanSearchTitle("[Intro]"))
        assertEquals("Interlude", StreamResolver.cleanSearchTitle("(Interlude)"))
        assertEquals("Snippet", StreamResolver.cleanSearchTitle("[Snippet]"))
        assertEquals("Outro", StreamResolver.cleanSearchTitle("(Outro)"))
        assertEquals("Track 1", StreamResolver.cleanSearchTitle("(Track 1)"))
    }

    @Test
    fun `cleanSearchTitle strips parenthetical metadata for normal titles`() {
        assertEquals("Father Figure", StreamResolver.cleanSearchTitle("Father Figure (Encore Version)"))
        assertEquals("Eldest Daughter", StreamResolver.cleanSearchTitle("Eldest Daughter [Official Video]"))
        assertEquals("Actually Romantic", StreamResolver.cleanSearchTitle("Actually Romantic (feat. Post Malone) [Audio]"))
        assertEquals("", StreamResolver.cleanSearchTitle(""))
        assertEquals("", StreamResolver.cleanSearchTitle(null))
    }

    @Test
    fun `cleanSearchTitle strips bracket tags with trailing hyphens and delimiters properly`() {
        assertEquals("Beat", StreamResolver.cleanSearchTitle("[Intro] - Beat"))
        assertEquals("Beat", StreamResolver.cleanSearchTitle("Beat - [Official Audio]"))
        assertEquals("Snippet", StreamResolver.cleanSearchTitle(" - [Snippet] - "))
        assertEquals("Intro - Snippet", StreamResolver.cleanSearchTitle("[Intro] - [Snippet]"))
    }

    @Test
    fun `cleanSearchTitle returns empty string for empty brackets or whitespace-only brackets`() {
        assertEquals("", StreamResolver.cleanSearchTitle("[ ]"))
        assertEquals("", StreamResolver.cleanSearchTitle("()"))
        assertEquals("", StreamResolver.cleanSearchTitle("(   )"))
        assertEquals("", StreamResolver.cleanSearchTitle("  [  ]  "))
    }

    @Test
    fun `search fallback safely ignores tracks with blank cleanSearchTitle to avoid false song substitution`() {
        // When a track has blank title or title reduced to empty by bracket cleaning,
        // it must be identified as blank so resolveViaNewPipe will not query YouTube with just "$artist audio"
        val blankClean1 = StreamResolver.cleanSearchTitle("")
        val blankClean2 = StreamResolver.cleanSearchTitle("   ")
        val blankClean3 = StreamResolver.cleanSearchTitle("[ ]")
        val blankClean4 = StreamResolver.cleanSearchTitle(null)

        assertTrue(blankClean1.isBlank())
        assertTrue(blankClean2.isBlank())
        assertTrue(blankClean3.isBlank())
        assertTrue(blankClean4.isBlank())
    }

    @Test
    fun `MusicManager clearDrmToken successfully removes cached DRM token`() {
        val testTrackId = 777666555L
        val token = "eyJhbGciOiJkaXIiLCJjdHkiOiJKV1QiLCJlbmMiOiJBMjU2R0..."

        com.alananasss.kittytune.data.MusicManager.putDrmToken(testTrackId, token)
        assertEquals(token, com.alananasss.kittytune.data.MusicManager.getDrmToken(testTrackId))

        com.alananasss.kittytune.data.MusicManager.clearDrmToken(testTrackId)
        assertNull(com.alananasss.kittytune.data.MusicManager.getDrmToken(testTrackId))
    }

    @Test
    fun `LocalTrack with snippet duration leaves fullDuration null when not on disk to enable hydration`() {
        val snippetStub = LocalTrack(
            id = 12345L,
            title = "Unverified Snippet Song",
            artist = "Artist",
            artworkUrl = "",
            duration = 30000L,
            localAudioPath = "",
            localArtworkPath = "",
            source = "soundcloud"
        )

        val restored = snippetStub.toTrack()
        assertEquals(30000L, restored.durationMs)
        assertNull("Unverified <=45s duration must leave fullDuration null to allow hydration", restored.fullDuration)
        assertEquals(30000L, restored.actualDurationMs)

        // Verify it triggers hydration
        val needsHydration = restored.user == null ||
            restored.user?.id == 0L ||
            restored.media == null ||
            restored.playbackCount == 0 ||
            restored.title.isNullOrBlank() ||
            ((restored.isGoPlus || restored.isSnipped) && restored.fullDuration == null) ||
            (restored.durationMs != null && restored.durationMs in 1..45000L && restored.fullDuration == null)
        assertTrue("Restored snippet stub must need hydration", needsHydration)
    }

    @Test
    fun `LocalTrack with full duration preserves fullDuration when greater than 45s`() {
        val fullTrack = LocalTrack(
            id = 67890L,
            title = "Full Length Song",
            artist = "Artist",
            artworkUrl = "",
            duration = 215000L,
            localAudioPath = "",
            localArtworkPath = "",
            source = "soundcloud"
        )

        val restored = fullTrack.toTrack()
        assertEquals(215000L, restored.durationMs)
        assertEquals(215000L, restored.fullDuration)
        assertEquals(215000L, restored.actualDurationMs)
    }
}


