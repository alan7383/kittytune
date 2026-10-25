package com.alananasss.kittytune

import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.library.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryManualOrderTest {

    private fun createPlaylist(id: Long, title: String, timestamp: Long): LibraryItem.PlaylistItem {
        val p = Playlist(
            id = id,
            title = title,
            artworkUrl = null,
            calculatedArtworkUrl = null,
            trackCount = 10,
            user = User(1, "Artist", null),
            tracks = null
        )
        return LibraryItem.PlaylistItem(
            playlist = p,
            timestamp = timestamp,
            key = LibraryItem.getPlaylistCanonicalKey(p),
            isPinned = false
        )
    }

    @Test
    fun `newly liked playlist appears at top in manual mode instead of jumping to the end`() {
        val oldPlaylist1 = createPlaylist(101L, "Old Playlist 1", 1000L)
        val oldPlaylist2 = createPlaylist(102L, "Old Playlist 2", 2000L)
        val manualOrder = listOf(oldPlaylist1.key, oldPlaylist2.key)

        // Newly liked album "Blurryface" has higher timestamp and is NOT yet in manualOrder
        val blurryface = createPlaylist(200L, "Blurryface", 5000L)

        val items = listOf(oldPlaylist1, blurryface, oldPlaylist2)

        val positions = manualOrder.withIndex().associate { it.value to it.index }
        val sorted = items.sortedWith(
            compareBy<LibraryItem> { item ->
                if (positions.containsKey(item.key)) 1 else 0
            }.thenByDescending { item ->
                if (!positions.containsKey(item.key)) item.timestamp else 0L
            }.thenBy { item ->
                positions[item.key] ?: 0
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.key }
        )

        // Blurryface must be first (index 0)
        assertEquals(blurryface.key, sorted[0].key)
        assertEquals(oldPlaylist1.key, sorted[1].key)
        assertEquals(oldPlaylist2.key, sorted[2].key)
    }

    @Test
    fun `multiple unpositioned items sort newest first at the top`() {
        val oldPlaylist = createPlaylist(101L, "Old Playlist", 1000L)
        val manualOrder = listOf(oldPlaylist.key)

        val newAlbum1 = createPlaylist(201L, "Blurryface", 5000L)
        val newAlbum2 = createPlaylist(202L, "Trench", 6000L)

        val items = listOf(oldPlaylist, newAlbum1, newAlbum2)
        val positions = manualOrder.withIndex().associate { it.value to it.index }

        val sorted = items.sortedWith(
            compareBy<LibraryItem> { item ->
                if (positions.containsKey(item.key)) 1 else 0
            }.thenByDescending { item ->
                if (!positions.containsKey(item.key)) item.timestamp else 0L
            }.thenBy { item ->
                positions[item.key] ?: 0
            }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.key }
        )

        assertEquals("Trench (newest) should be first", newAlbum2.key, sorted[0].key)
        assertEquals("Blurryface should be second", newAlbum1.key, sorted[1].key)
        assertEquals("Old playlist should be third", oldPlaylist.key, sorted[2].key)
    }

    @Test
    fun `enable manual order syncs visible order even when stale manualOrder had items in different order`() {
        val oldPlaylist = "playlist_old"
        val blurryface = "playlist_blurryface"
        val scenecore = "playlist_scenecore"

        // Suppose manualOrder on disk previously had oldPlaylist first and blurryface second
        val existingManualOrder = listOf(oldPlaylist, blurryface, scenecore)

        // But in recents, blurryface was just liked so it is displayed first on screen
        val visible = listOf(blurryface, oldPlaylist, scenecore)

        val visibleSet = visible.toSet()
        val missing = visible.filter { it !in existingManualOrder }
        val base = if (missing.isNotEmpty()) {
            missing + existingManualOrder
        } else {
            existingManualOrder.toMutableList()
        }.toMutableList()

        var nextVisible = 0
        for (i in base.indices) {
            if (base[i] in visibleSet && nextVisible < visible.size) {
                base[i] = visible[nextVisible++]
            }
        }
        val synchronizedOrder = base.distinct()

        assertEquals(listOf(blurryface, oldPlaylist, scenecore), synchronizedOrder)
        assertEquals("Blurryface must remain first", blurryface, synchronizedOrder[0])
        assertEquals("Old playlist must be second", oldPlaylist, synchronizedOrder[1])
        assertEquals("Scenecore must be third", scenecore, synchronizedOrder[2])
    }

    @Test
    fun `enable manual order preserves non visible items when syncing`() {
        val hiddenFolder = "folder_1"
        val hiddenArtist = "artist_2"
        val oldPlaylist = "playlist_old"
        val blurryface = "playlist_blurryface"

        val existingManualOrder = listOf(hiddenFolder, oldPlaylist, hiddenArtist, blurryface)

        // Only playlists are visible in current screen filter
        val visible = listOf(blurryface, oldPlaylist)
        val visibleSet = visible.toSet()

        val missing = visible.filter { it !in existingManualOrder }
        val base = if (missing.isNotEmpty()) {
            missing + existingManualOrder
        } else {
            existingManualOrder.toMutableList()
        }.toMutableList()

        var nextVisible = 0
        for (i in base.indices) {
            if (base[i] in visibleSet && nextVisible < visible.size) {
                base[i] = visible[nextVisible++]
            }
        }
        val synchronizedOrder = base.distinct()

        // Hidden items must keep their slots, visible items must follow visible order
        assertEquals(listOf(hiddenFolder, blurryface, hiddenArtist, oldPlaylist), synchronizedOrder)
    }
}
