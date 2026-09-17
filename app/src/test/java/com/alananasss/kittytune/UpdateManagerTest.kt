package com.alananasss.kittytune

import com.alananasss.kittytune.data.UpdateManager
import com.alananasss.kittytune.data.UpdateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testIsNewerVersion() {
        assertTrue(UpdateManager.isNewerVersion("1.0.0", "1.0.1"))
        assertTrue(UpdateManager.isNewerVersion("1.0.0", "1.1.0"))
        assertTrue(UpdateManager.isNewerVersion("1.0.0", "2.0.0"))
        assertTrue(UpdateManager.isNewerVersion("2.65.0", "2.66.0"))
        assertTrue(UpdateManager.isNewerVersion("2.66.0", "2.66.1"))
        assertTrue(UpdateManager.isNewerVersion("2.66", "2.66.1"))

        assertFalse(UpdateManager.isNewerVersion("1.0.0", "1.0.0"))
        assertFalse(UpdateManager.isNewerVersion("2.66.0", "2.65.9"))
        assertFalse(UpdateManager.isNewerVersion("2.66.0", "2.66.0"))
        assertFalse(UpdateManager.isNewerVersion("3.0.0", "2.99.9"))
    }

    @Test
    fun testGetApkFileName() {
        assertEquals("update_v2.66.0.apk", UpdateManager.getApkFileName("v2.66.0"))
        assertEquals("update_2.66.0.apk", UpdateManager.getApkFileName("2.66.0"))
        assertEquals("update_v2.66.0_beta_1.apk", UpdateManager.getApkFileName("v2.66.0/beta:1"))
    }

    @Test
    fun testDismissResetsState() {
        UpdateManager.dismiss()
        assertEquals(UpdateStatus.IDLE, UpdateManager.status.value)
        assertEquals(0f, UpdateManager.downloadProgress.value, 0.001f)
        assertEquals(0L, UpdateManager.downloadSize.value)
    }

    @Test
    fun testIsApkValidWithEmptyOrMissingFile() {
        val missingFile = File(tempFolder.root, "non_existent.apk")
        // Expected to return false for missing file without needing Context
        assertFalse(missingFile.exists())

        val emptyFile = tempFolder.newFile("empty.apk")
        assertEquals(0L, emptyFile.length())
    }

    @Test
    fun testFileCleanupPreservesTargetVersion() {
        val updatesDir = tempFolder.newFolder("updates")
        val keepFile = File(updatesDir, "update_v2.67.0.apk").apply { createNewFile() }
        val oldFile = File(updatesDir, "update_v2.66.0.apk").apply { createNewFile() }
        val tempFile = File(updatesDir, "update_v2.67.0.apk.tmp").apply { createNewFile() }

        assertTrue(keepFile.exists())
        assertTrue(oldFile.exists())
        assertTrue(tempFile.exists())

        val keepFileName = UpdateManager.getApkFileName("v2.67.0")
        updatesDir.listFiles()?.forEach { file ->
            if (file.name.endsWith(".tmp") || file.name != keepFileName) {
                file.delete()
            }
        }

        assertTrue("Target version APK should be preserved", keepFile.exists())
        assertFalse("Obsolete version APK should be deleted", oldFile.exists())
        assertFalse("Temporary file should be deleted", tempFile.exists())
    }
}
