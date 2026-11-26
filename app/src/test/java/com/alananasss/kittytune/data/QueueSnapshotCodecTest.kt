package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.zip.GZIPInputStream

class QueueSnapshotCodecTest {
    private val gson = Gson()
    private val codec = QueueSnapshotCodec(gson)

    @Test fun legacyRowsRemainReadableWithoutConversion() {
        val legacy = """[{"id":17,"title":"Legacy","duration":12345,"source":"youtube"}]"""
        val expected = gson.fromJson(legacy, Array<Track>::class.java)
        val actual = codec.decode<Array<Track>>(legacy, Array<Track>::class.java)
        assertEquals(gson.toJson(expected), gson.toJson(actual))
    }

    @Test fun fullTrackGraphsRoundTripWithDuplicatesAndOrder() {
        val track = representativeTrack(17)
        val tracks = listOf(track, representativeTrack(29), track)
        val encoded = codec.encode(tracks)
        val decoded = codec.decode<Array<Track>>(encoded, Array<Track>::class.java)!!
        assertEquals(listOf(18L, 30L, 18L), decoded.map { it.id })
        assertEquals(gson.toJson(tracks), gson.toJson(decoded))
    }

    @Test fun unicodeEscapesAndIsolatedSurrogatesAreLossless() {
        val text = "Italia à 日本 🎵 \\ \" <tag>\n\t" + '\uD800' + "end" + '\uDC00'
        val tracks = listOf(representativeTrack(1).copy(title = text, description = text))
        val actual = codec.decode<Array<Track>>(codec.encode(tracks), Array<Track>::class.java)!!
        assertEquals(text, actual.single().title)
        assertEquals(text, actual.single().description)
    }

    @Test fun nullAndEmptyPayloadSemanticsMatchLegacyGson() {
        assertNull(codec.decode<Array<Track>>("null", Array<Track>::class.java))
        assertEquals(0, codec.decode<Array<Track>>(codec.encode(emptyList<Track>()), Array<Track>::class.java)!!.size)
        assertEquals(0, codec.decode<Array<Track>>("[]", Array<Track>::class.java)!!.size)
    }

    @Test fun longValuesAndSurrogatesSurviveBufferBoundariesAndReuse() {
        for (length in listOf(32740, 32767, 32768, 32769, 65536)) {
            val text = "x".repeat(length) + '\uD800' + "🎵" + '\uDC00' + "fine"
            val input = listOf(representativeTrack(1).copy(title = text, description = text))
            val decoded = codec.decode<Array<Track>>(codec.encode(input), Array<Track>::class.java)!!
            assertEquals(text, decoded.single().title)
            assertEquals(text, decoded.single().description)
        }
        val next = listOf(representativeTrack(99))
        assertEquals(gson.toJson(next), gson.toJson(codec.decode<Array<Track>>(codec.encode(next), Array<Track>::class.java)))
    }

    @Test fun gzipContainsTheCompleteOriginalJsonGraph() {
        val tracks = (0 until 50).map { representativeTrack(it) }
        val encoded = codec.encode(tracks)
        val bytes = Base64.getDecoder().decode(encoded.substringAfter("kittytune-queue:gzip:1:"))
        val json = GZIPInputStream(ByteArrayInputStream(bytes)).reader(Charsets.UTF_8).use { it.readText() }
        assertEquals(gson.toJsonTree(tracks), com.google.gson.JsonParser.parseString(json))
    }

    @Test fun damagedAndUnknownEncodingsFailInsteadOfReturningPartialTracks() {
        for (payload in listOf("kittytune-queue:gzip:1:bad!", "kittytune-queue:gzip:2:AAAA", "[{broken")) {
            try {
                codec.decode<Array<Track>>(payload, Array<Track>::class.java)
                fail("Expected invalid payload to fail")
            } catch (_: Exception) { /* Repository turns invalid history into an empty list. */ }
        }
        val encoded = codec.encode((0 until 50).map { representativeTrack(it) })
        try {
            codec.decode<Array<Track>>(encoded.dropLast(12), Array<Track>::class.java)
            fail("Expected truncated gzip stream to fail")
        } catch (_: Exception) { }
    }

    @Test fun repetitiveLargeQueueShrinksWithoutDroppingAnyFields() {
        val tracks = (0 until 5000).map { representativeTrack(it) }
        val original = gson.toJson(tracks)
        val encoded = codec.encode(tracks)
        assertTrue("Fixture must meaningfully exercise compression", encoded.length < original.length / 2)
        val decoded = codec.decode<Array<Track>>(encoded, Array<Track>::class.java)!!
        assertEquals(gson.toJson(tracks), gson.toJson(decoded))
    }
}

/** Actual app model, synthetic metadata only; never claims to represent every user's queue. */
private val fixtureGson = Gson()
internal fun representativeTrack(index: Int): Track = fixtureGson.fromJson("""{
    "id":${index + 1},"title":"Track $index — Live","artwork_url":"https://example.invalid/cover/${index % 40}-t500x500.jpg",
    "duration":240000,"source":"soundcloud","description":"${"Album notes with lyrics metadata and recording information. ".repeat(12)}",
    "permalink_url":"https://example.invalid/tracks/$index","genre":"Electronic","tag_list":"live electronic album",
    "user":{"id":${index % 40 + 1},"username":"Artist ${index % 40}","permalink":"artist-${index % 40}","avatar_url":"https://example.invalid/avatar/${index % 40}.jpg"},
    "media":{"transcodings":[{"url":"https://example.invalid/streams/$index","preset":"mp3_0_1","duration":240000,"snipped":false,"quality":"sq","format":{"protocol":"hls","mime_type":"audio/mpeg"}}]},
    "publisher_metadata":{"artist":"Artist ${index % 40}","album_title":"Album ${index % 400}"},
    "artists":[{"id":"artist-${index % 40}","name":"Artist ${index % 40}","uri":"spotify:artist:synthetic"}],
    "user_favorite":true,"likedAt":1234567890,"playCount":3
} """, Track::class.java)
