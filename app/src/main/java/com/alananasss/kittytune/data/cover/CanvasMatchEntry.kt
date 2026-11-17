package com.alananasss.kittytune.data.cover

import java.util.concurrent.ConcurrentHashMap

enum class CanvasMatchTier(val baseConfidence: Int) {
    ISRC_EXACT(100),
    APPLE_CATALOG_ID(90),
    ALBUM_ARTIST_TITLE(75),
    FUZZY(50),
}

data class CanvasMatchEntry(
    val isrc: String?,
    val appleCatalogId: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val sourceUrl: String,
    val matchTier: CanvasMatchTier,
    val confidence: Int,
    val lastMatchedAtMs: Long,
)

object CanvasIndex {

    private val byIsrc = ConcurrentHashMap<String, CanvasMatchEntry>()
    private val bySongArtist = ConcurrentHashMap<String, CanvasMatchEntry>()

    fun getByIsrc(isrc: String): CanvasMatchEntry? = byIsrc[isrc]

    fun getBySongArtist(song: String, artist: String): CanvasMatchEntry? =
        bySongArtist[songArtistKey(song, artist)]

    fun put(entry: CanvasMatchEntry) {
        entry.isrc?.takeIf { it.isNotBlank() }?.let { isrc ->
            val existing = byIsrc[isrc]
            if (existing == null || entry.confidence >= existing.confidence) {
                byIsrc[isrc] = entry
            }
        }
        val saKey = songArtistKey(entry.title, entry.artist)
        val existingSa = bySongArtist[saKey]
        if (existingSa == null || entry.confidence >= existingSa.confidence) {
            bySongArtist[saKey] = entry
        }
    }

    private fun songArtistKey(song: String, artist: String): String =
        (song.trim().lowercase() + "\u001F" + artist.trim().lowercase())
}
