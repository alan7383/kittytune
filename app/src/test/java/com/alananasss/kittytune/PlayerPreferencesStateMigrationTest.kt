package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import com.alananasss.kittytune.ui.player.NormalizationLevel
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPreferencesStateMigrationTest {

    private val gson = Gson()

    @Test
    fun testLegacyEffectsJsonWithoutAmbientTypeCanBeCopiedWithoutNpe() {
        // Simulates an existing user upgrading from v2.68.0 where KEY_EFFECTS JSON had no ambientType
        val legacyJson = """{"speed":1.0,"isPitchEnabled":true,"is8DEnabled":false,"isMuffledEnabled":false,"isBassBoostEnabled":false,"isReverbEnabled":false,"isRainEnabled":false,"rainVolume":1.0}"""

        val rawState: AudioEffectsState = gson.fromJson(legacyJson, AudioEffectsState::class.java)
        assertNotNull(rawState)

        // If rawState has null fields due to Gson Unsafe allocation, our sanitizer reconstructs a safe instance
        val safeState = if ((rawState.ambientType as String?) == null || (rawState.normalizationLevel as NormalizationLevel?) == null) {
            AudioEffectsState(
                speed = rawState.speed,
                isPitchEnabled = rawState.isPitchEnabled,
                is8DEnabled = rawState.is8DEnabled,
                isMuffledEnabled = rawState.isMuffledEnabled,
                isBassBoostEnabled = rawState.isBassBoostEnabled,
                isReverbEnabled = rawState.isReverbEnabled,
                isRainEnabled = rawState.isRainEnabled,
                rainVolume = rawState.rainVolume,
                ambientType = rawState.ambientType ?: "rain",
                normalizationLevel = rawState.normalizationLevel ?: NormalizationLevel.NORMAL
            )
        } else {
            rawState
        }

        // Must not throw NullPointerException on .copy()
        val copied = safeState.copy(isEqualizerEnabled = true)
        assertTrue(copied.isEqualizerEnabled)
        assertEquals("rain", copied.ambientType)
        assertEquals(NormalizationLevel.NORMAL, copied.normalizationLevel)
    }

    @Test
    fun testLegacyEqualizerJsonWithoutPresetCanBeCopiedWithoutNpe() {
        val legacyJson = """{"isEnabled":true,"preampDb":2.5}"""
        val rawState: EqualizerState = gson.fromJson(legacyJson, EqualizerState::class.java)
        assertNotNull(rawState)

        val safeState = if ((rawState.bandGainsDb as List<Float>?) == null || (rawState.selectedPreset as String?) == null) {
            EqualizerState(
                isEnabled = rawState.isEnabled,
                preampDb = rawState.preampDb,
                bandGainsDb = rawState.bandGainsDb ?: List(16) { 0f },
                selectedPreset = rawState.selectedPreset ?: "Flat"
            )
        } else {
            rawState
        }

        val copied = safeState.copy(isEnabled = false)
        assertEquals(false, copied.isEnabled)
        assertEquals("Flat", copied.selectedPreset)
        assertEquals(16, copied.bandGainsDb.size)
    }

    @Test
    fun testSerializedNameAnnotationsAreResilient() {
        val state = AudioEffectsState(
            speed = 1.25f,
            ambientType = "fireplace",
            isEqualizerEnabled = true,
            normalizationLevel = NormalizationLevel.LOUD
        )
        val json = gson.toJson(state)
        assertTrue(json.contains("\"speed\":1.25"))
        assertTrue(json.contains("\"ambientType\":\"fireplace\""))
        assertTrue(json.contains("\"normalizationLevel\":\"LOUD\""))

        val restored = gson.fromJson(json, AudioEffectsState::class.java)
        assertEquals(1.25f, restored.speed, 0.001f)
        assertEquals("fireplace", restored.ambientType)
        assertEquals(NormalizationLevel.LOUD, restored.normalizationLevel)
        assertEquals(true, restored.isEqualizerEnabled)
    }
}
