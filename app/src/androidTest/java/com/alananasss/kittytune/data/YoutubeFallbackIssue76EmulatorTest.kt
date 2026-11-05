package com.alananasss.kittytune.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.Artist
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Production search + selection on Android, including live responses for both reported songs. */
@RunWith(AndroidJUnit4::class)
class YoutubeFallbackIssue76EmulatorTest {
    private fun track(title: String, artist: String) =
        Track(760001, title, null, 30_000, User(1, artist, null), policy = "SNIP")
    private fun song(id: String, title: String, artist: String, duration: Int = 210) =
        SongItem(id, title, listOf(Artist(artist, null)), duration = duration, thumbnail = "")

    @Test fun reportedRemixIsNotReplacedByTheOriginalOrADifferentRemix() = runBlocking {
        val requested = track("Strongest (Alan Walker Remix)", "Ina Wroldsen")
        var queries = 0
        val selected = StreamResolver.findYoutubeFallbackSong(requested) {
            if (++queries == 1) listOf(song("original", "Strongest", "Ina Wroldsen"),
                song("wrong-remix", "Strongest (Other Artist Remix)", "Ina Wroldsen"))
            else listOf(song("correct-remix", "Strongest (Alan Walker Remix)", "Ina Wroldsen", 216))
        }
        assertEquals("correct-remix", selected?.id)
        assertEquals(2, queries)
    }

    @Test fun sameLengthCoverDoesNotReplaceTatuOriginal() = runBlocking {
        val selected = StreamResolver.findYoutubeFallbackSong(track("All The Things She Said", "t.A.T.u.")) {
            listOf(song("cover", "All The Things She Said", "First To Eleven", 214),
                song("different-song", "All About Us", "t.A.T.u.", 214),
                song("original", "All The Things She Said", "t.A.T.u.", 218))
        }
        assertEquals("original", selected?.id)
    }

    @Test fun liveYoutubeMusicResultsForBothReportedSongsHaveMatchingIdentityAndPlayableAudio() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = File(context.getExternalFilesDir(null), "issue76-live-search.txt")
        report.writeText("Live YouTube Music fallback search on Android\n")
        for (requested in listOf(track("All The Things She Said", "t.A.T.u."),
            track("Strongest (Alan Walker Remix)", "Ina Wroldsen"))) {
            val selected = withTimeout(60_000) {
                StreamResolver.findYoutubeFallbackSong(requested) { query ->
                    val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
                    val songs = result.items.filterIsInstance<SongItem>()
                    report.appendText("\nQUERY: $query\n")
                    songs.forEach { report.appendText("${it.id} | ${it.title} | ${it.artists.joinToString { artist -> artist.name }} | ${it.duration}s\n") }
                    songs
                }
            }
            assertNotNull("No confident live match for ${requested.title}; see ${report.absolutePath}", selected)
            selected!!
            report.appendText("SELECTED: ${selected.id} | ${selected.title} | ${selected.artists.joinToString { it.name }}\n")
            if (requested.title!!.contains("Remix")) {
                assertTrue("The named remix must be preserved", (selected.title + selected.album?.name.orEmpty()).contains("Alan Walker", true))
            } else {
                assertEquals("All The Things She Said", selected.title)
                assertTrue(selected.artists.any { it.name.replace(".", "").equals("tatu", true) })
            }
            val youtubeTrack = requested.copy(id = selected.id.hashCode().toLong(), source = "youtube",
                permalinkUrl = "https://www.youtube.com/watch?v=${selected.id}")
            val stream = withTimeout(90_000) { StreamResolver.resolveStreamWithDrm(context, youtubeTrack) }
            assertNotNull("Selected YouTube recording could not be extracted: ${selected.id}", stream)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            lateinit var player: ExoPlayer
            instrumentation.runOnMainSync {
                player = ExoPlayer.Builder(context).build()
                player.volume = 0f
                player.setMediaItem(MediaItem.Builder().setMediaId(selected.id).setUri(stream!!.url).build())
                player.prepare()
                player.play()
            }
            try {
                val deadline = System.currentTimeMillis() + 30_000
                var decoded = false
                while (!decoded && System.currentTimeMillis() < deadline) {
                    instrumentation.runOnMainSync {
                        assertNull("YouTube playback failed for ${selected.id}", player.playerError)
                        decoded = player.playbackState == Player.STATE_READY && player.currentPosition >= 500
                    }
                    if (!decoded) Thread.sleep(100)
                }
                assertTrue("No decoded playback for ${selected.id}", decoded)
                report.appendText("PLAYBACK: decoded successfully with ExoPlayer\n")
            } finally { instrumentation.runOnMainSync { player.release() } }
        }
    }
}
