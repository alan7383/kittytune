package com.alananasss.kittytune

import com.alananasss.kittytune.ui.home.SearchQueryRepair
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A search retried with the catalogue's spelling when what was typed found nothing like itself (issue #66). */
class SearchQueryRepairTest {

    @Test
    fun `a missing dash is repaired from the catalogue`() {
        assertTrue(SearchQueryRepair.needsRepair("9mice NEWYORK", listOf("New Year" to "someone")))
        assertEquals(
            "9mice NEW-YORK",
            SearchQueryRepair.repairedQuery("9mice NEWYORK", listOf("Other" to "x", "NEW-YORK" to "9mice")),
        )
    }

    @Test
    fun `a search that found the song is left alone`() {
        assertFalse(SearchQueryRepair.needsRepair("9mice new york", listOf("New-York" to "9mice")))
    }

    @Test
    fun `nothing matching in the catalogue gives no repair`() {
        assertNull(SearchQueryRepair.repairedQuery("9mice NEWYORK", listOf("Paris" to "9mice")))
    }
}
