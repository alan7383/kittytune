package com.alananasss.kittytune

import com.alananasss.kittytune.data.ContributorCategory
import com.alananasss.kittytune.data.CreditsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CreditsRepositoryTest {

    private val all = CreditsRepository.all()

    @Test
    fun testTheFileIsFoundAndRead() {
        assertTrue(
            "credits.json should be present in assets or resources",
            File("src/main/resources/credits.json").exists() || File("src/main/assets/credits.json").exists()
        )
        assertTrue("The credits should not be empty", all.isNotEmpty())
        assertEquals("Expected 12 contributors", 12, all.size)
    }

    @Test
    fun testEveryEntryHasValidUrlAndName() {
        all.forEach { person ->
            assertTrue("A contributor with no name: $person", person.name.isNotBlank())
            assertTrue(
                "The link has to be a real URL: ${person.url}",
                person.url.startsWith("https://")
            )
        }
    }

    @Test
    fun testOrderIsExplicitAndSorted() {
        val file = File("src/main/resources/credits.json").readText()
        val orders = Regex("\"order\": (\\d+)").findAll(file).map { it.groupValues[1].toInt() }.toList()
        assertEquals(orders.size, orders.distinct().size)
        assertEquals(orders.sorted(), orders)
    }

    @Test
    fun testAllCategoriesAreCovered() {
        assertTrue(all.any { it.category == ContributorCategory.DEV })
        assertTrue(all.any { it.category == ContributorCategory.TRANSLATION })
        assertTrue(all.any { it.category == ContributorCategory.COMMUNITY })
    }

    @Test
    fun testKeyContributorsPresent() {
        assertNotNull(all.firstOrNull { it.name.contains("alananasss") })
        assertNotNull(all.firstOrNull { it.name.contains("imsawiq") })
        assertNotNull(all.firstOrNull { it.name.contains("VeX") })
        assertNotNull(all.firstOrNull { it.name.contains("jason-fastner007") })
        assertNotNull(all.firstOrNull { it.name.contains("mattdotcat") })
        assertNotNull(all.firstOrNull { it.name.contains("kivoyoso") })
        assertNotNull(all.firstOrNull { it.name.contains("wynriu") })
        assertNotNull(all.firstOrNull { it.name.contains("dyr00l") })
    }

    @Test
    fun testGermanTranslatorIsCreditedAsDev() {
        val jason = all.firstOrNull { it.name.contains("jason-fastner007") }
        assertNotNull("jason-fastner007 should be credited", jason)
        assertEquals(ContributorCategory.DEV, jason?.category)
    }
}
