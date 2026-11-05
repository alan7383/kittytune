package com.alananasss.kittytune.wear.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WearCodecsTest {

    @Test
    fun testVolumeCodec_floatBinaryRoundTrip() {
        val testVolumes = listOf(0.0f, 0.25f, 0.5f, 0.85f, 1.0f)
        for (v in testVolumes) {
            val encoded = WearCodecs.encodeVolume(v)
            assertEquals(4, encoded.size)
            val decoded = WearCodecs.decodeVolume(encoded)
            assertEquals(v, decoded, 0.001f)
        }
    }

    @Test
    fun testVolumeCodec_clamping() {
        val underflow = WearCodecs.encodeVolume(-0.5f)
        assertEquals(0.0f, WearCodecs.decodeVolume(underflow), 0.001f)

        val overflow = WearCodecs.encodeVolume(1.5f)
        assertEquals(1.0f, WearCodecs.decodeVolume(overflow), 0.001f)
    }

    @Test
    fun testVolumeCodec_nanSanitization() {
        val nanEncoded = WearCodecs.encodeVolume(Float.NaN)
        val nanDecoded = WearCodecs.decodeVolume(nanEncoded)
        assertEquals(0.0f, nanDecoded, 0.0f)

        val rawNanBytes = java.nio.ByteBuffer.allocate(4).order(java.nio.ByteOrder.BIG_ENDIAN).putFloat(Float.NaN).array()
        assertEquals(0.0f, WearCodecs.decodeVolume(rawNanBytes), 0.0f)

        val allOnes = byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertEquals(0.0f, WearCodecs.decodeVolume(allOnes), 0.0f)
    }

    @Test
    fun testVolumeCodec_utf8StringFallback() {
        val strBytes = "0.75".toByteArray(Charsets.UTF_8)
        val decoded = WearCodecs.decodeVolume(strBytes)
        assertEquals(0.75f, decoded, 0.001f)

        val invalidBytes = "invalid".toByteArray(Charsets.UTF_8)
        assertEquals(0.0f, WearCodecs.decodeVolume(invalidBytes), 0.001f)

        assertEquals(0.0f, WearCodecs.decodeVolume(null), 0.001f)
        assertEquals(0.0f, WearCodecs.decodeVolume(ByteArray(0)), 0.001f)
    }

    @Test
    fun testTargetCodec_jsonRoundTrip() {
        val phonePayload = TargetSelectionPayload.phone()
        val phoneEncoded = WearCodecs.encodeTarget(phonePayload)
        val phoneDecoded = WearCodecs.decodeTarget(phoneEncoded)
        assertNotNull(phoneDecoded)
        assertEquals(WearConstants.TARGET_TYPE_PHONE, phoneDecoded?.targetType)

        val desktopPayload = TargetSelectionPayload.desktop(peerId = "peer-xyz-123", peerName = "Work Desktop")
        val desktopEncoded = WearCodecs.encodeTarget(desktopPayload)
        val desktopDecoded = WearCodecs.decodeTarget(desktopEncoded)
        assertNotNull(desktopDecoded)
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, desktopDecoded?.targetType)
        assertEquals("peer-xyz-123", desktopDecoded?.peerId)
        assertEquals("Work Desktop", desktopDecoded?.peerName)
    }

    @Test
    fun testTargetCodec_plainStringFallback() {
        val plainPhone = WearCodecs.decodeTarget("phone".toByteArray(Charsets.UTF_8))
        assertNotNull(plainPhone)
        assertEquals(WearConstants.TARGET_TYPE_PHONE, plainPhone?.targetType)

        val plainDesktop = WearCodecs.decodeTarget("desktop".toByteArray(Charsets.UTF_8))
        assertNotNull(plainDesktop)
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, plainDesktop?.targetType)

        val prefixedDesktop = WearCodecs.decodeTarget("desktop:my-macbook".toByteArray(Charsets.UTF_8))
        assertNotNull(prefixedDesktop)
        assertEquals(WearConstants.TARGET_TYPE_DESKTOP, prefixedDesktop?.targetType)
        assertEquals("my-macbook", prefixedDesktop?.peerId)
    }

    @Test
    fun testTargetsListCodec_roundTrip() {
        val targets = listOf(
            TargetDeviceInfo("phone", "Phone", WearConstants.TARGET_TYPE_PHONE, isConnected = true, isSelected = true),
            TargetDeviceInfo("desktop-1", "Studio PC", WearConstants.TARGET_TYPE_DESKTOP, isConnected = false, isSelected = false),
        )

        val json = WearCodecs.encodeTargetsList(targets)
        val decoded = WearCodecs.decodeTargetsList(json)

        assertEquals(targets.size, decoded.size)
        assertEquals(targets[0].id, decoded[0].id)
        assertEquals(targets[1].id, decoded[1].id)
        assertTrue(decoded[0].isSelected)
    }
}
