package com.alananasss.kittytune

import com.alananasss.kittytune.data.search.RecentVisits
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.AppUtils
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentVisitsTest {

    private val gson: Gson = AppUtils.gson

    @Test
    fun kind_from_parsesValidKindsCaseInsensitively() {
        assertEquals(RecentVisits.Kind.ARTIST, RecentVisits.Kind.from("artist"))
        assertEquals(RecentVisits.Kind.ARTIST, RecentVisits.Kind.from("ARTIST"))
        assertEquals(RecentVisits.Kind.TRACK, RecentVisits.Kind.from("Track"))
        assertEquals(RecentVisits.Kind.PLAYLIST, RecentVisits.Kind.from("playlist"))
        assertNull(RecentVisits.Kind.from("unknown"))
        assertNull(RecentVisits.Kind.from(null))
    }

    @Test
    fun visit_deserializesValidJson() {
        val json = """
            {
                "kind": "ARTIST",
                "key": "sc:123",
                "title": "Lil Peep",
                "subtitle": null,
                "imageUrl": "https://example.com/avatar.jpg",
                "destination": "sc:123",
                "isVerified": true,
                "at": 1700000000
            }
        """.trimIndent()

        val visit = gson.fromJson(json, RecentVisits.Visit::class.java)
        assertNotNull(visit)
        assertEquals(RecentVisits.Kind.ARTIST, visit.kind)
        assertEquals("sc:123", visit.key)
        assertEquals("Lil Peep", visit.title)
        assertTrue(visit.isVerified)
    }

    @Test
    fun visit_deserializesLowercaseKindViaAlternate() {
        val json = """
            {
                "kind": "artist",
                "key": "sc:123",
                "title": "Lil Peep"
            }
        """.trimIndent()

        val visit = gson.fromJson(json, RecentVisits.Visit::class.java)
        assertNotNull(visit)
        assertEquals(RecentVisits.Kind.ARTIST, visit.kind)
    }

    @Test
    fun visit_deserializesUnknownOrNullKindSafelyAsNullWithoutCrash() {
        val jsonUnknown = """{"kind": "UNKNOWN_KIND", "key": "sc:123", "title": "Lil Peep"}"""
        val visitUnknown = gson.fromJson(jsonUnknown, RecentVisits.Visit::class.java)
        assertNotNull(visitUnknown)
        assertNull(visitUnknown.kind)

        val jsonNull = """{"kind": null, "key": "sc:123", "title": "Lil Peep"}"""
        val visitNull = gson.fromJson(jsonNull, RecentVisits.Visit::class.java)
        assertNotNull(visitNull)
        assertNull(visitNull.kind)

        val jsonMissing = """{"key": "sc:123", "title": "Lil Peep"}"""
        val visitMissing = gson.fromJson(jsonMissing, RecentVisits.Visit::class.java)
        assertNotNull(visitMissing)
        assertNull(visitMissing.kind)
    }

    @Test
    fun sanitizeVisits_filtersOutCorruptOrMissingKindAndFlagsCorrupt() {
        val validVisit = RecentVisits.Visit(
            kind = RecentVisits.Kind.TRACK,
            key = "12345",
            title = "Star Shopping",
            track = Track(id = 12345L, title = "Star Shopping", artworkUrl = null, durationMs = 120000, user = null),
        )
        val nullKindVisit = RecentVisits.Visit(
            kind = null,
            key = "unknown",
            title = "Corrupt Visit",
        )
        val blankKeyVisit = RecentVisits.Visit(
            kind = RecentVisits.Kind.ARTIST,
            key = "   ",
            title = "No Key",
        )
        val blankTitleVisit = RecentVisits.Visit(
            kind = RecentVisits.Kind.PLAYLIST,
            key = "pl:123",
            title = "",
        )

        val rawList = listOf(validVisit, nullKindVisit, blankKeyVisit, blankTitleVisit)
        val (cleaned, hadCorrupt) = RecentVisits.sanitizeVisits(rawList)

        assertTrue(hadCorrupt)
        assertEquals(1, cleaned.size)
        assertEquals("Star Shopping", cleaned.first().title)
        assertEquals(RecentVisits.Kind.TRACK, cleaned.first().kind)
    }

    @Test
    fun sanitizeVisits_preservesValidListWithoutFlaggingCorrupt() {
        val visits = listOf(
            RecentVisits.Visit(kind = RecentVisits.Kind.ARTIST, key = "sc:1", title = "Artist 1"),
            RecentVisits.Visit(kind = RecentVisits.Kind.TRACK, key = "2", title = "Track 2"),
            RecentVisits.Visit(kind = RecentVisits.Kind.PLAYLIST, key = "pl:3", title = "Playlist 3"),
        )

        val (cleaned, hadCorrupt) = RecentVisits.sanitizeVisits(visits)
        assertFalse(hadCorrupt)
        assertEquals(3, cleaned.size)
    }
}
