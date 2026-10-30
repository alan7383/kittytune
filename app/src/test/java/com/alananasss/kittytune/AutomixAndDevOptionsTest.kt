package com.alananasss.kittytune

import com.alananasss.kittytune.audio.ai.AiDetectionManager
import com.alananasss.kittytune.ui.profile.SettingsCategory
import com.alananasss.kittytune.ui.profile.SettingsSubPage
import com.alananasss.kittytune.ui.profile.rootGroups
import com.alananasss.kittytune.ui.profile.route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomixAndDevOptionsTest {

    @Test
    fun testDeveloperOptionsRegisteredUnderMisc() {
        // Developer options must exist in miscPages
        assertTrue(
            "MISC_DEVELOPER must be present in miscPages",
            SettingsSubPage.miscPages.contains(SettingsSubPage.MISC_DEVELOPER)
        )

        // Route must point to developer_options
        assertEquals(
            "developer_options",
            SettingsSubPage.MISC_DEVELOPER.route
        )

        // rootGroups must include MISC with developer_options entry
        val miscGroup = rootGroups().find { it.titleRes == SettingsCategory.MISC.titleRes }
        assertNotNull("Misc root group must exist", miscGroup)
        assertTrue(
            "Misc group must have entry for developer_options",
            miscGroup!!.rows.any { it.route == "developer_options" }
        )
    }

    @Test
    fun testAiDetectionClearCache() {
        // Pre-populate dummy cache entry
        val dummyResult = AiDetectionManager.Result(
            score = 0.88f,
            isAi = true,
            status = AiDetectionManager.Status.DONE
        )
        AiDetectionManager.trackVerdictCache[12345L] = dummyResult
        AiDetectionManager.trackScoreSamplesCache[12345L] = mutableListOf(0.85f, 0.90f)

        assertEquals(1, AiDetectionManager.trackVerdictCache.size)
        assertEquals(1, AiDetectionManager.trackScoreSamplesCache.size)

        // Execute clearCache
        AiDetectionManager.clearCache()

        assertEquals(0, AiDetectionManager.trackVerdictCache.size)
        assertEquals(0, AiDetectionManager.trackScoreSamplesCache.size)
        assertEquals(null, AiDetectionManager.result.value)
    }

    @Test
    fun testAllTranslationsAreCompleteAndClean() {
        val resDir = java.io.File("src/main/res").takeIf { it.exists() }
            ?: java.io.File("app/src/main/res")
        val baseFile = java.io.File(resDir, "values/strings.xml")
        assertTrue("Base strings.xml must exist", baseFile.exists())

        fun parseKeys(file: java.io.File): Pair<Set<String>, List<String>> {
            val doc = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
            val nodes = doc.getElementsByTagName("string")
            val keys = mutableSetOf<String>()
            val dups = mutableListOf<String>()
            for (i in 0 until nodes.length) {
                val elem = nodes.item(i) as org.w3c.dom.Element
                val name = elem.getAttribute("name")
                if (!keys.add(name)) {
                    dups.add(name)
                }
            }
            return keys to dups
        }

        val (baseKeys, baseDups) = parseKeys(baseFile)
        assertTrue("Base strings.xml must have 0 duplicate keys, found: $baseDups", baseDups.isEmpty())

        val locales = listOf("fr", "de", "hu", "ru", "vi", "tr")
        for (lang in locales) {
            val locFile = java.io.File(resDir, "values-$lang/strings.xml")
            assertTrue("values-$lang/strings.xml must exist", locFile.exists())
            val (locKeys, locDups) = parseKeys(locFile)
            assertTrue("values-$lang/strings.xml must have 0 duplicate keys, found: $locDups", locDups.isEmpty())

            val missing = baseKeys - locKeys
            val extra = locKeys - baseKeys
            assertTrue("values-$lang/strings.xml is missing keys: $missing", missing.isEmpty())
            assertTrue("values-$lang/strings.xml has orphan keys: $extra", extra.isEmpty())
        }
    }
}
