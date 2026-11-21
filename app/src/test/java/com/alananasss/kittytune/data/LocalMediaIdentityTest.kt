package com.alananasss.kittytune.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalMediaIdentityTest {
    private val root = "content://com.android.externalstorage.documents"

    @Test
    fun `overlapping grants identify the same document`() {
        assertEquals(
            localMediaIdentity("$root/tree/primary%3AMusic/document/primary%3AMusic%2FAlbum%2FA%2BB.mp3"),
            localMediaIdentity("$root/tree/primary%3AMusic%2FAlbum/document/primary%3AMusic%2FAlbum%2FA+B.mp3"),
        )
    }

    @Test
    fun `different case and different providers do not collapse together`() {
        val path = "/document/a%3Amusic%2FA.mp3"
        val first = localMediaIdentity(root + path)
        assertNotEquals(first, localMediaIdentity(root + path.replace("FA.mp3", "Fa.mp3")))
        assertNotEquals(first, localMediaIdentity("content://other.provider$path"))
    }

    @Test
    fun `file URIs and filesystem paths can identify the same path`() {
        assertEquals(localMediaIdentity("/music/track.mp3"), localMediaIdentity("file:///music/track.mp3"))
    }
}
