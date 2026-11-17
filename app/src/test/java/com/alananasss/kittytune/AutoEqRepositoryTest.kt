package com.alananasss.kittytune

import com.alananasss.kittytune.data.audio.AutoEqProfile
import com.alananasss.kittytune.data.audio.AutoEqRepository
import com.alananasss.kittytune.data.audio.HeadphoneDeviceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqRepositoryTest {

    private fun profile(name: String, path: String, source: String = "oratory1990") = AutoEqProfile(
        id = path,
        name = name,
        path = path,
        source = source,
        rig = null,
        formFactor = "Over-ear",
    )

    @Test
    fun `parses README links with encoded paths and attribution`() {
        val text = """
            # Recommended Results
            - [Sennheiser HD 650](./oratory1990/over-ear/Sennheiser%20HD%20650)
            - [Anker Soundcore Space Q45 (ANC on)](./oratory1990/over-ear/Anker%20Soundcore%20Space%20Q45%20(ANC%20on)) by oratory1990 on GRAS 43AG-7
            - [Sony WH-1000XM4](./oratory1990/over-ear/Sony%20WH-1000XM4) by oratory1990
            - [Google Pixel Buds Pro 2](./DHRME/in-ear/Google%20Pixel%20Buds%20Pro%202) by DHRME
        """.trimIndent()

        val profiles = AutoEqRepository.parseIndex(text)

        assertEquals(4, profiles.size)
        assertEquals("Sennheiser HD 650", profiles[0].name)
        assertEquals("oratory1990", profiles[0].source)
        assertEquals("Over-ear", profiles[0].formFactor)
        assertEquals("Anker Soundcore Space Q45 (ANC on)", profiles[1].name)
        assertEquals("GRAS 43AG-7", profiles[1].rig)
        assertEquals("Sony WH-1000XM4", profiles[2].name)
        assertEquals(null, profiles[2].rig)
        assertEquals("Google Pixel Buds Pro 2", profiles[3].name)
        assertEquals("DHRME", profiles[3].source)
        assertEquals("In-ear", profiles[3].formFactor)
    }

    @Test
    fun `search ranks exact product before variants`() {
        val all = listOf(
            profile("Sony WH-1000XM4 (ANC Off)", "./x/Sony%20WH-1000XM4%20(ANC%20Off)", "HypetheSonics"),
            profile("Sony WH-1000XM4", "./x/Sony%20WH-1000XM4"),
            profile("Sony WH-1000XM5", "./x/Sony%20WH-1000XM5"),
            profile("Sennheiser HD 650", "./x/Sennheiser%20HD%20650"),
        )

        val results = AutoEqRepository.searchIn(all, "sony wh-1000xm4")

        assertEquals("Sony WH-1000XM4", results.first().name)
        assertTrue(results.none { it.name.contains("HD 650") })

        // People type model numbers without the space AutoEq uses.
        assertEquals("Sony WH-1000XM4", AutoEqRepository.searchIn(all, "wh1000xm4").first().name)
    }

    @Test
    fun `search finds Pixel Buds Pro 2 with typos and bluetooth owner prefix`() {
        val all = listOf(
            profile("Google Pixel Buds Pro 2", "./DHRME/in-ear/Google%20Pixel%20Buds%20Pro%202", "DHRME"),
            profile("Google Pixel Buds Pro", "./HypetheSonics/in-ear/Google%20Pixel%20Buds%20Pro", "HypetheSonics"),
            profile("OnePlus Buds Pro 2", "./Rtings/in-ear/OnePlus%20Buds%20Pro%202", "Rtings"),
            profile("Sony WH-1000XM5", "./oratory1990/over-ear/Sony%20WH-1000XM5", "oratory1990"),
        )

        // Exact search
        val resExact = AutoEqRepository.searchIn(all, "pixel buds pro 2")
        assertEquals("Google Pixel Buds Pro 2", resExact.first().name)

        // Typo search: "piixel"
        val resTypo = AutoEqRepository.searchIn(all, "piixel buds pro 2")
        assertEquals("Google Pixel Buds Pro 2", resTypo.first().name)

        // Bluetooth device name with possessive prefix
        val resBt = AutoEqRepository.searchIn(all, "Alan's Pixel Buds Pro 2")
        assertEquals("Google Pixel Buds Pro 2", resBt.first().name)
    }

    @Test
    fun `search expands common abbreviations like qc and xm5`() {
        val all = listOf(
            profile("Bose QuietComfort Ultra", "./Kuulokenurkka/over-ear/Bose%20QuietComfort%20Ultra", "Kuulokenurkka"),
            profile("Sony WH-1000XM5", "./oratory1990/over-ear/Sony%20WH-1000XM5", "oratory1990"),
            profile("Sennheiser HD 650", "./oratory1990/over-ear/Sennheiser%20HD%20650", "oratory1990"),
        )

        val resQc = AutoEqRepository.searchIn(all, "qc ultra")
        assertEquals("Bose QuietComfort Ultra", resQc.first().name)

        val resXm5 = AutoEqRepository.searchIn(all, "xm5")
        assertEquals("Sony WH-1000XM5", resXm5.first().name)
    }

    @Test
    fun `cleanQuery strips bluetooth noise and prefixes`() {
        assertEquals("Pixel Buds Pro 2", AutoEqRepository.cleanQuery("Alan's Pixel Buds Pro 2"))
        assertEquals("Pixel Buds Pro 2", AutoEqRepository.cleanQuery("Écouteurs de Alan - Pixel Buds Pro 2"))
        assertEquals("Galaxy Buds3 Pro", AutoEqRepository.cleanQuery("Galaxy Buds3 Pro (F20A)"))
        assertEquals("AirPods Pro", AutoEqRepository.cleanQuery("AirPods Pro - Find My"))
    }

    @Test
    fun `resamples a graphic EQ onto the fixed bands`() {
        val text = "GraphicEQ: 20 1.0; 32 -2.0; 64 -4.0; 125 -6.0; 20000 -8.0"

        val (gains, preamp) = AutoEqRepository.parseGraphicEq(
            text,
            bands = floatArrayOf(32f, 64f, 125f),
        )

        assertEquals(listOf(-2f, -4f, -6f), gains)
        assertEquals(0f, preamp, 0.001f)
    }

    @Test
    fun `preamp only cuts when the profile boosts`() {
        val text = "GraphicEQ: 20 0; 32 3; 64 6; 125 1; 20000 0"

        val (gains, preamp) = AutoEqRepository.parseGraphicEq(
            text,
            bands = floatArrayOf(32f, 64f, 125f),
        )

        assertEquals(listOf(3f, 6f, 1f), gains)
        assertEquals(-6f, preamp, 0.001f)
    }

    @Test
    fun `respects explicit preamp header in graphic EQ file`() {
        val text = """
            Preamp: -4.5 dB
            GraphicEQ: 20 0; 32 2; 64 4; 125 1; 20000 0
        """.trimIndent()

        val (_, preamp) = AutoEqRepository.parseGraphicEq(
            text,
            bands = floatArrayOf(32f, 64f, 125f),
        )

        assertEquals(-4.5f, preamp, 0.001f)
    }

    @Test
    fun `loadFromText converts custom text into profile data`() {
        val text = "GraphicEQ: 32 -1.5; 64 -2.0; 125 -3.0; 200 -4.0; 20000 -1.0"
        val data = AutoEqRepository.loadFromText("My Custom IEM", text)

        assertEquals("My Custom IEM", data.profile.name)
        assertEquals("Custom GraphicEQ", data.profile.source)
        assertEquals(AutoEqRepository.BAND_COUNT, data.gainsDb.size)
        assertTrue(data.preampDb <= 0f)
    }

    @Test
    fun `builds the raw profile URL without double encoding`() {
        val url = AutoEqRepository.profileFileUrl(
            profile(
                "Sennheiser HD 650",
                "./oratory1990/over-ear/Sennheiser%20HD%20650",
            )
        )

        assertTrue(url.contains("/Sennheiser%20HD%20650/Sennheiser%20HD%20650%20GraphicEQ.txt"))
    }

    @Test
    fun `HeadphoneDeviceType correctly identifies in-ear and over-ear devices`() {
        assertTrue(HeadphoneDeviceType.isInEar("In-ear"))
        assertTrue(HeadphoneDeviceType.isInEar("Earbuds"))
        assertTrue(HeadphoneDeviceType.isInEar(null, "Redmi Buds 6 Pro"))
        assertTrue(HeadphoneDeviceType.isInEar(null, "Apple AirPods Pro 2"))
        assertTrue(HeadphoneDeviceType.isInEar(null, "Sony WF-1000XM5"))

        assertFalse(HeadphoneDeviceType.isInEar("Over-ear"))
        assertFalse(HeadphoneDeviceType.isInEar("On-ear"))
        assertFalse(HeadphoneDeviceType.isInEar(null, "Sony WH-1000XM5"))
        assertFalse(HeadphoneDeviceType.isInEar(null, "Sennheiser HD 650"))
        assertFalse(HeadphoneDeviceType.isInEar(null, null))
    }
}
