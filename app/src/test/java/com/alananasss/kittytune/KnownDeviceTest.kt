package com.alananasss.kittytune

import com.alananasss.kittytune.data.sync.KnownDevice
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * How a remembered device is updated (issue #33).
 *
 * This is where "paired once" is either true or a lie. A device is learned about in three ways that each
 * know a different part of it — a pairing code has the secret, a discovery answer has the current address,
 * an inbound exchange has the name and the time — and the entry has to accumulate all three. The bug this
 * guards against is the plausible one: recording that a phone just synced, and in doing so wiping the
 * secret and address needed to ever call it, so sync silently becomes one-directional and the user is told
 * to scan the code again.
 */
class KnownDeviceTest {

    private val paired = KnownDevice(
        deviceId = "phone",
        deviceName = "Pixel",
        host = "192.168.1.42",
        port = 47653,
        secret = "s3cret",
        platform = "android",
        lastSyncedAtMs = 1_000,
        pairedAtMs = 500,
    )

    @Test
    fun `a device with an address and a secret can be called`() {
        assertTrue(paired.canDial)
        assertEquals("192.168.1.42", paired.toPairing()?.host)
    }

    @Test
    fun `a device we cannot call has nothing to call it with`() {
        assertFalse(paired.copy(secret = "").canDial)
        assertFalse(paired.copy(host = "").canDial)
        assertNull(paired.copy(secret = "").toPairing())
    }

    /** The case that broke pairing: an inbound exchange knows only the name and the time. */
    @Test
    fun `recording a sync keeps the details needed to call back`() {
        val fromInbound = KnownDevice(deviceId = "phone", lastSyncedAtMs = 9_000)
        val merged = fromInbound.mergedOnto(paired)

        assertEquals("s3cret", merged.secret)
        assertEquals("192.168.1.42", merged.host)
        assertEquals("Pixel", merged.deviceName)
        assertEquals("android", merged.platform)
        assertTrue(merged.canDial)
    }

    @Test
    fun `a new address wins over the saved one`() {
        val moved = KnownDevice(deviceId = "phone", host = "192.168.1.99")
        assertEquals("192.168.1.99", moved.mergedOnto(paired).host)
        assertEquals("s3cret", moved.mergedOnto(paired).secret)
    }

    @Test
    fun `the sync time only moves forward`() {
        val stale = KnownDevice(deviceId = "phone", lastSyncedAtMs = 5)
        assertEquals(1_000, stale.mergedOnto(paired).lastSyncedAtMs)
    }

    /** "Paired since" is a fact about the past; it must not reset on every exchange. */
    @Test
    fun `the pairing time is kept`() {
        val later = KnownDevice(deviceId = "phone", pairedAtMs = 9_999)
        assertEquals(500, later.mergedOnto(paired).pairedAtMs)
    }

    @Test
    fun `a device seen for the first time is paired now`() {
        val fresh = KnownDevice(deviceId = "laptop")
        assertTrue(fresh.mergedOnto(null).pairedAtMs > 0)
    }

    /** A nonsense port is not a reason to forget the working one. */
    @Test
    fun `an impossible port falls back to what was known`() {
        assertEquals(47653, KnownDevice(deviceId = "phone", port = 0).mergedOnto(paired).port)
    }

    @Test
    fun `a device with no name is labelled by something a person can read`() {
        assertEquals("Pixel", paired.label)
        assertEquals("192.168.1.42", paired.copy(deviceName = "").label)
        assertEquals(
            "abcdef12",
            KnownDevice(deviceId = "abcdef1234567890").label,
        )
    }
}
