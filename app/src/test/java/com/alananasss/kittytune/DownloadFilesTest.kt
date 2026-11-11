package com.alananasss.kittytune

import com.alananasss.kittytune.data.DownloadFiles
import java.io.File
import java.io.FileNotFoundException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import kotlin.test.assertFailsWith

class DownloadFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun homonymousTracksKeepSeparateAudio() {
        val firstSource = temporary.newFile().apply { writeText("first") }
        val secondSource = temporary.newFile().apply { writeText("second") }
        val first = DownloadFiles.store(firstSource, temporary.root, DownloadFiles.name(1, "Artist", "Song", "m4a"))
        val second = DownloadFiles.store(secondSource, temporary.root, DownloadFiles.name(2, "Artist", "Song", "m4a"))

        assertNotEquals(first, second)
        assertEquals("first", first.readText())
        assertTrue(first.delete())
        assertEquals("second", second.readText())
    }

    @Test
    fun failedCopyPreservesExistingAudioAndRemovesTemporaryFile() {
        val name = DownloadFiles.name(1, "Artist", "Song", "m4a")
        val target = File(temporary.root, name).apply { writeText("complete") }

        assertFailsWith<FileNotFoundException> {
            DownloadFiles.store(File(temporary.root, "missing"), temporary.root, name)
        }

        assertEquals("complete", target.readText())
        assertFalse(temporary.root.listFiles().orEmpty().any { it.extension == "part" })
    }

    @Test
    fun longUnicodeNameKeepsIdAndExtension() {
        val name = DownloadFiles.name(Long.MAX_VALUE, "🎵".repeat(100), "Song", "m4a")

        assertTrue(name.toByteArray(Charsets.UTF_8).size <= 255)
        assertTrue(name.endsWith(" [${Long.MAX_VALUE}].m4a"))
    }
}
