package com.alananasss.kittytune.data

import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.alananasss.kittytune.data.spotify.SpotifyTrack
import com.alananasss.kittytune.data.spotify.parseSpotifyCredits
import com.alananasss.kittytune.data.spotify.spotifyCatalogTrackId
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

class PlaybackIssue139Test {
    private val enemyId = "3CIyK1V4JEJkg02E4EJnDl"
    private val loveId = "6SaEfJw9i6SxmQLJcD6fi7"
    private val enemy = SpotifyTrack(
        id = enemyId,
        name = "Enemy (with JID) - from the series Arcane League of Legends",
        durationMs = 173_000,
        artists = listOf("Imagine Dragons", "JID", "Arcane", "League of Legends")
            .map { SpotifyArtistRef("", it) },
    ).toTrack()
    private val samus = Track(
        id = 114276498,
        title = "Direktor - The Samus Effect",
        artworkUrl = null,
        durationMs = 173_000,
        user = User(1, "Mighty Rare", null),
        source = "soundcloud",
        permalinkUrl = "https://soundcloud.com/mightyrare/the-samus-effect",
    )

    @Test fun aSearchHitForAnUnrelatedRecordingCannotReplaceTheSelection() {
        val unrelated = samus.copy(id = 1, title = "In the Name of Love", user = User(2, "Zak Azoury", null))
        val anotherArtist = samus.copy(id = 2, user = User(3, "Another uploader", null))
        val preview = samus.copy(id = 3, policy = "SNIP", durationMs = 30_000)
        assertEquals(listOf(samus), StreamResolver.soundCloudMatches(samus, listOf(unrelated, anotherArtist, preview, samus)))
    }

    @Test fun enemyMatchesTheJidRecordingAcrossCatalogTitles() {
        val solo = samus.copy(id = 1, title = "Enemy", user = User(2, "Imagine Dragons", null))
        val openingTitle = solo.copy(id = 2, title = "Enemy (Opening Title Version)", user = User(3, "Imagine Dragons & JID", null))
        val correct = solo.copy(
            id = 3, title = "Imagine Dragons & JID - Enemy (from the series Arcane: League of Legends) [Official Video]",
        )
        assertEquals(listOf(correct), StreamResolver.soundCloudMatches(enemy, listOf(solo, openingTitle, correct)))

        val ranked = YoutubeAudioResolver.matchedVideos(
            YoutubeFallbackMatcher.Recording(enemy.title!!, enemy.displayArtist, 173, artists = enemy.artists!!.map { it.name }),
            listOf(
                video("https://www.youtube.com/watch?v=AAAAAAAAAAA", solo.title!!, "Imagine Dragons"),
                video("https://www.youtube.com/watch?v=F5tSoaJ93ac", correct.title!!, "Riot Games Music")
            ),
        )
        assertEquals(listOf("https://www.youtube.com/watch?v=F5tSoaJ93ac"), ranked)
    }

    @Test fun storedIdsAreScopedToTheCatalogAndProviderReference() {
        val youtube = samus.copy(source = "youtube_music", permalinkUrl = "https://music.youtube.com/watch?v=F5tSoaJ93ac&list=RD")
        val shared = youtube.copy(source = "youtube", permalinkUrl = "https://youtu.be/F5tSoaJ93ac?t=10")
        val other = youtube.copy(permalinkUrl = "https://www.youtube.com/watch?v=AAAAAAAAAAA")
        assertNotEquals(samus.playbackKey(), youtube.playbackKey())
        assertEquals(youtube.playbackKey(), shared.playbackKey())
        assertNotEquals(other.playbackKey(), youtube.playbackKey())
        assertEquals("https://www.youtube.com/watch?v=F5tSoaJ93ac", YoutubeAudioResolver.videoUrl(shared.permalinkUrl))
        assertNull(YoutubeAudioResolver.videoUrl("https://www.youtube.com.example.org/watch?v=F5tSoaJ93ac"))

        val stored = LocalTrack(samus.id, samus.title!!, samus.displayArtist, "", 173_000, "download.m4a", "",
            source = "soundcloud", permalinkUrl = samus.permalinkUrl)
        assertTrue(stored.matchesPlayback(samus))
        assertFalse(stored.matchesPlayback(youtube))
        assertTrue(stored.copy(permalinkUrl = null).matchesPlayback(youtube)) // Legacy metadata identifies the same recording.
        assertFalse(stored.copy(permalinkUrl = null, artist = "").matchesPlayback(youtube))
    }

    @Test fun creditsAreBoundToTheirTrackAndArtistIds() {
        val artist1 = "0123456789ABCDEFGHIJKL"
        val artist2 = "1234567890ABCDEFGHIJKL"
        val payload = JSONObject("""
            {
              "trackTitle": "In the Name of Love",
              "trackUri": "spotify:track:$loveId",
              "roleCredits": [{"roleTitle":"Performers","artists":[
                {"name":"Artist", "uri":"spotify:artist:$artist1",
                 "imageUri":"spotify:image:0123456789abcdef0123456789abcdef01234567",
                 "subroles":["Main Artist"]},
                {"name":"Artist", "uri":"spotify:artist:$artist2","subroles":["Featured Artist"]},
                {"name":"Artist", "uri":"spotify:artist:$artist1","subroles":["Vocals"]}
              ]}]
            }
        """.trimIndent())
        assertEquals(enemyId, enemy.spotifyCatalogTrackId())
        assertNull(enemy.copy(permalink = null, permalinkUrl = "spotify:artist:$artist1").spotifyCatalogTrackId())
        assertNull(parseSpotifyCredits(enemyId, payload))
        val people = parseSpotifyCredits(loveId, payload)!!.roles.single().artists
        assertEquals(listOf(artist1, artist2), people.map { it.id })
        assertEquals("https://i.scdn.co/image/0123456789abcdef0123456789abcdef01234567", people[0].imageUri)
        assertEquals(listOf("Main Artist", "Vocals"), people[0].subroles)
        assertNull(people[1].imageUri)
    }

    private fun video(url: String, title: String, channel: String) =
        StreamInfoItem(0, url, title, StreamType.VIDEO_STREAM).apply {
            duration = 173
            uploaderName = channel
        }
}
