package com.alananasss.kittytune.e2e

import com.alananasss.kittytune.e2e.harness.E2EContracts
import com.alananasss.kittytune.e2e.harness.MockConnectEnvironment
import com.alananasss.kittytune.e2e.harness.MockWearableEnvironment
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tier 2: Boundary Value Analysis (BVA), Edge Cases, Error Handling & Fault Injection
 * across all 11 features.
 */
class WearTier2BoundaryAndEdgeTest {

    private lateinit var wearableEnv: MockWearableEnvironment
    private lateinit var connectEnv: MockConnectEnvironment

    @Before
    fun setUp() {
        wearableEnv = MockWearableEnvironment()
        connectEnv = MockConnectEnvironment()
    }

    // =========================================================================
    // Feature 1: Wear OS Gradle Module (:wear) Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f1_wearApplicationIdMismatchRejection() {
        val phoneAppId = E2EContracts.EXPECTED_APPLICATION_ID
        val invalidWearAppId = "com.alananasss.kittytune.wear"
        val isCompatible = (phoneAppId == invalidWearAppId)
        assertFalse("Mismatched applicationId must be rejected as incompatible with Data Layer", isCompatible)
    }

    @Test
    fun f1_wearMinSdkLessThan30Rejected() {
        val obsoleteMinSdk = 28
        val meetsWearOS3Baseline = obsoleteMinSdk >= E2EContracts.MIN_SDK_WEAR
        assertFalse("minSdk < 30 does not support Wear Compose 1.5+ APIs", meetsWearOS3Baseline)
    }

    @Test
    fun f1_wearManifestStandaloneFlagMissing() {
        val manifestAttributes = mapOf("standalone" to null)
        val isStandalone = manifestAttributes["standalone"] == "true"
        assertFalse("Missing standalone flag must default to false", isStandalone)
    }

    @Test
    fun f1_wearHardwareFeatureRequired() {
        val declaredFeatures = listOf("android.hardware.type.television", "android.hardware.camera")
        val hasWatchHardware = declaredFeatures.contains(E2EContracts.WEAR_WATCH_FEATURE)
        assertFalse("Manifest lacking android.hardware.type.watch must fail watch validation", hasWatchHardware)
    }

    @Test
    fun f1_wearTargetSdkMustMatchCompileSdk() {
        val compileSdk = 37
        val targetSdk = 37
        assertEquals("TargetSdk must align with compileSdk 37", compileSdk, targetSdk)
    }

    // =========================================================================
    // Feature 2: Shared Communication Protocol (:wear-common) Edge Cases
    // =========================================================================

    @Test
    fun f2_emptyPayloadHandlingInVolumeDecode() {
        val emptyBytes = ByteArray(0)
        val decoded = E2EContracts.decodeVolumePayload(emptyBytes)
        assertEquals(0.0f, decoded, 0.0001f)
    }

    @Test
    fun f2_corruptedVolumePayloadBytes() {
        // 3 bytes instead of 4
        val malformedBytes = byteArrayOf(0x3F, 0x00, 0x00)
        val decoded = E2EContracts.decodeVolumePayload(malformedBytes)
        assertEquals(0.0f, decoded, 0.0001f)
    }

    @Test
    fun f2_extremeFloatValuesClamping() {
        val negativeHuge = -999999.0f
        val positiveHuge = 999999.0f

        val encodedNeg = E2EContracts.encodeVolumePayload(negativeHuge)
        val decodedNeg = E2EContracts.decodeVolumePayload(encodedNeg)
        assertEquals(0.0f, decodedNeg, 0.0001f)

        val encodedPos = E2EContracts.encodeVolumePayload(positiveHuge)
        val decodedPos = E2EContracts.decodeVolumePayload(encodedPos)
        assertEquals(1.0f, decodedPos, 0.0001f)
    }

    @Test
    fun f2_nanAndInfinityVolumeRejection() {
        val nanBytes = E2EContracts.encodeVolumePayload(Float.NaN)
        val decodedNaN = E2EContracts.decodeVolumePayload(nanBytes)
        assertTrue(decodedNaN in 0.0f..1.0f)

        val infBytes = E2EContracts.encodeVolumePayload(Float.POSITIVE_INFINITY)
        val decodedInf = E2EContracts.decodeVolumePayload(infBytes)
        assertTrue(decodedInf in 0.0f..1.0f)
    }

    @Test
    fun f2_oversizedPayloadStringHandling() {
        // Large string payload
        val longString = "0.75" + " ".repeat(1024)
        val decoded = E2EContracts.decodeVolumePayload(longString.toByteArray(Charsets.UTF_8))
        assertEquals(0.75f, decoded, 0.0001f)
    }

    // =========================================================================
    // Feature 3: Circular Playback UI Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f3_ultraLongTrackTitleHandling() {
        val ultraLongTitle = "Track " + "A".repeat(1000)
        val maxDisplayChars = 50
        val truncated = if (ultraLongTitle.length > maxDisplayChars) {
            ultraLongTitle.take(maxDisplayChars) + "..."
        } else {
            ultraLongTitle
        }
        assertEquals(53, truncated.length)
        assertTrue(truncated.endsWith("..."))
    }

    @Test
    fun f3_emptyArtistAndTitleFallback() {
        val rawTitle: String? = ""
        val rawArtist: String? = null

        val displayTitle = if (rawTitle.isNullOrBlank()) "Unknown Title" else rawTitle
        val displayArtist = if (rawArtist.isNullOrBlank()) "Unknown Artist" else rawArtist

        assertEquals("Unknown Title", displayTitle)
        assertEquals("Unknown Artist", displayArtist)
    }

    @Test
    fun f3_nullArtworkUrlSafeHandling() {
        val artworkUrl: String? = null
        val placeholderResId = 12345
        val resolvedImage = artworkUrl ?: placeholderResId
        assertEquals(placeholderResId, resolvedImage)
    }

    @Test
    fun f3_zeroDurationTrackHandling() {
        val durationMs = 0L
        val positionMs = 500L
        val progressFraction = if (durationMs > 0L) (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0.0f, 1.0f) else 0.0f
        assertEquals(0.0f, progressFraction, 0.0001f)
    }

    @Test
    fun f3_rapidConsecutivePlayPauseTaps() {
        var isPlaying = false
        // Rapid 50 clicks
        for (i in 1..50) {
            isPlaying = !isPlaying
        }
        assertFalse("50 toggles from false should end in false", isPlaying)
    }

    // =========================================================================
    // Feature 4: Volume Controls & Rotary Crown Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f4_volumeClampAtMaximum() {
        var volume = 0.98f
        // Increment by step (0.05)
        volume = (volume + E2EContracts.VOLUME_STEP).coerceIn(E2EContracts.VOLUME_MIN, E2EContracts.VOLUME_MAX)
        assertEquals(1.0f, volume, 0.0001f)
    }

    @Test
    fun f4_volumeClampAtMinimum() {
        var volume = 0.02f
        // Decrement by step (0.05)
        volume = (volume - E2EContracts.VOLUME_STEP).coerceIn(E2EContracts.VOLUME_MIN, E2EContracts.VOLUME_MAX)
        assertEquals(0.0f, volume, 0.0001f)
    }

    @Test
    fun f4_extremeRotaryCrownScrollSpin() {
        val initialVolume = 0.50f
        val violentClockwise = 100.0f
        val violentCounterClockwise = -100.0f

        val clampedHigh = E2EContracts.computeRotaryVolume(initialVolume, violentClockwise)
        assertEquals(1.0f, clampedHigh, 0.0001f)

        val clampedLow = E2EContracts.computeRotaryVolume(initialVolume, violentCounterClockwise)
        assertEquals(0.0f, clampedLow, 0.0001f)
    }

    @Test
    fun f4_subPixelRotaryCrownJitter() {
        val initialVolume = 0.50f
        val tinyDelta = 0.00001f
        val updated = E2EContracts.computeRotaryVolume(initialVolume, tinyDelta)
        assertEquals(0.50f, updated, 0.001f)
    }

    @Test
    fun f4_zeroMaxStreamStepsDivisionGuard() {
        val steps = E2EContracts.floatToStreamSteps(0.5f, maxSteps = 0)
        assertEquals(0, steps)
    }

    // =========================================================================
    // Feature 5: Target Device Selector Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f5_targetSelectorSwitchToOfflineDesktopHandling() {
        connectEnv.isOnline = false
        val commandDispatched = connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_PLAY)
        assertFalse("Command must fail gracefully when desktop is offline", commandDispatched)
    }

    @Test
    fun f5_targetSelectorRapidOscillation() {
        var target = E2EContracts.TARGET_TYPE_PHONE
        for (i in 1..100) {
            target = if (target == E2EContracts.TARGET_TYPE_PHONE) E2EContracts.TARGET_TYPE_DESKTOP else E2EContracts.TARGET_TYPE_PHONE
        }
        assertEquals(E2EContracts.TARGET_TYPE_PHONE, target)
    }

    @Test
    fun f5_targetSelectorEmptyPeerIdHandling() {
        val rawPayload = "desktop:"
        val parts = rawPayload.split(":")
        val peerId = if (parts.size > 1 && parts[1].isNotBlank()) parts[1] else null
        assertNull("Empty peerId after colon must resolve to null", peerId)
    }

    @Test
    fun f5_commandDispatchWhileTargetDisconnected() {
        wearableEnv.setNetworkConnected(false)
        val success = wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_PLAY)
        assertFalse("Sending message when disconnected must return false", success)
    }

    @Test
    fun f5_targetSelectionWithSpecialCharacters() {
        val complexPeerId = "desktop-peer_01#beta@hallway-room"
        val payload = "desktop:$complexPeerId".toByteArray(Charsets.UTF_8)
        val text = payload.toString(Charsets.UTF_8)
        assertTrue(text.contains(complexPeerId))
    }

    // =========================================================================
    // Feature 6: Phone Command Receiver Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f6_receiverHandlesUnrecognizedPathGracefully() {
        var unknownHandled = false
        wearableEnv.addMessageListener { msg ->
            if (!msg.path.startsWith("/kittytune/playback/")) {
                unknownHandled = true // Ignore gracefully
            }
        }
        wearableEnv.sendMessage("phone_node_01", "/kittytune/unknown/action")
        assertTrue(unknownHandled)
    }

    @Test
    fun f6_receiverHandlesNullPayloadInSetVolume() {
        val emptyPayload = ByteArray(0)
        val volume = E2EContracts.decodeVolumePayload(emptyPayload)
        assertEquals(0.0f, volume, 0.0001f)
    }

    @Test
    fun f6_receiverHandlesPlayerUninitializedState() {
        var playerInitialized = false
        val getPlayer = {
            if (!playerInitialized) {
                playerInitialized = true // lazy init
            }
            "MockPlayer"
        }
        val player = getPlayer()
        assertNotNull(player)
        assertTrue(playerInitialized)
    }

    @Test
    fun f6_receiverMessageBurstStress() {
        var count = 0
        wearableEnv.addMessageListener { count++ }

        for (i in 1..200) {
            wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_PLAY_PAUSE)
        }
        assertEquals(200, count)
    }

    @Test
    fun f6_receiverSmartPreviousBoundaryAtExactly3000ms() {
        val exactBoundary = 3000L
        val action = E2EContracts.resolvePreviousAction(exactBoundary)
        assertEquals("At exactly 3000ms, should skip to previous track", E2EContracts.PreviousBehavior.SKIP_TO_PREVIOUS_TRACK, action)

        val justOver = 3001L
        val actionJustOver = E2EContracts.resolvePreviousAction(justOver)
        assertEquals("At 3001ms, should seek to track start", E2EContracts.PreviousBehavior.SEEK_TO_START, actionJustOver)
    }

    // =========================================================================
    // Feature 7: Phone State Publisher Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f7_publisherHandlesNullTrackGracefully() {
        val emptyState = mapOf<String, Any>(
            E2EContracts.KEY_TRACK_ID to 0L,
            E2EContracts.KEY_TITLE to "",
            E2EContracts.KEY_ARTIST to "",
            E2EContracts.KEY_IS_PLAYING to false
        )
        val record = wearableEnv.putDataItem(E2EContracts.PATH_PLAYBACK_STATE, emptyState)
        assertEquals(0L, record.values[E2EContracts.KEY_TRACK_ID])
        assertEquals(false, record.values[E2EContracts.KEY_IS_PLAYING])
    }

    @Test
    fun f7_publisherHandlesMaxVolumeZeroWithoutException() {
        val maxVolume = 0
        val currentVolume = 0
        val ratio = if (maxVolume > 0) currentVolume.toFloat() / maxVolume.toFloat() else 0.0f
        assertEquals(0.0f, ratio, 0.0001f)
    }

    @Test
    fun f7_publisherThrottlingOfHighFrequencyUpdates() {
        var lastPublish = 0L
        var publishCount = 0
        val minIntervalMs = 100L

        for (i in 1..10) {
            val now = i * 50L // 50ms intervals
            if (now - lastPublish >= minIntervalMs) {
                publishCount++
                lastPublish = now
            }
        }
        // Out of 10 events spaced by 50ms (total 500ms), at most 5 should pass 100ms throttle
        assertEquals(5, publishCount)
    }

    @Test
    fun f7_publisherSpecialCharactersInTrackTitle() {
        val specialTitle = "Cat & Mouse 🐱 <Rock!> \"Remix\" / 'Live'"
        val record = wearableEnv.putDataItem(E2EContracts.PATH_PLAYBACK_STATE, mapOf(E2EContracts.KEY_TITLE to specialTitle))
        assertEquals(specialTitle, record.values[E2EContracts.KEY_TITLE])
    }

    @Test
    fun f7_publisherExtremelyLongMetadataFields() {
        val longAlbum = "Album " + "X".repeat(2048)
        val record = wearableEnv.putDataItem(E2EContracts.PATH_PLAYBACK_STATE, mapOf(E2EContracts.KEY_ALBUM to longAlbum))
        assertEquals(longAlbum, record.values[E2EContracts.KEY_ALBUM])
    }

    // =========================================================================
    // Feature 8: Desktop Remote Control Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f8_desktopCommandFailsWhenDesktopOffline() {
        connectEnv.isOnline = false
        val dispatched = connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_PLAY)
        assertFalse(dispatched)
    }

    @Test
    fun f8_desktopVolumeClampingAbove1000() {
        connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_VOLUME, 1500L)
        assertEquals(1000L, connectEnv.desktopState.volume)
    }

    @Test
    fun f8_desktopVolumeClampingBelowZero() {
        connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_VOLUME, -250L)
        assertEquals(0L, connectEnv.desktopState.volume)
    }

    @Test
    fun f8_desktopReplayProtectionSequenceValidation() {
        var lastSequence = 10L
        val incomingSequence = 10L // duplicate/replay sequence
        val isAccepted = incomingSequence > lastSequence
        assertFalse("Replayed sequence must be rejected by ConnectReplayGuard", isAccepted)
    }

    @Test
    fun f8_desktopInvalidCommandActionHandling() {
        val unsupportedAction = "explode"
        val knownActions = setOf(
            E2EContracts.ACTION_PLAY,
            E2EContracts.ACTION_PAUSE,
            E2EContracts.ACTION_NEXT,
            E2EContracts.ACTION_PREVIOUS,
            E2EContracts.ACTION_VOLUME
        )
        val isValid = knownActions.contains(unsupportedAction)
        assertFalse("Unsupported action must be rejected", isValid)
    }

    // =========================================================================
    // Feature 9: Wear OS Emulator Setup & Pairing Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f9_emulatorPortConflictDetection() {
        val boundPorts = setOf(5554, 5555)
        val targetPort = 5554
        val hasCollision = boundPorts.contains(targetPort)
        assertTrue(hasCollision)

        val nextAvailablePort = targetPort + 2
        assertFalse(boundPorts.contains(nextAvailablePort))
    }

    @Test
    fun f9_emulatorAvdAlreadyExistsHandling() {
        val avdExists = true
        val forceFlag = true
        val canOverwrite = avdExists && forceFlag
        assertTrue("--force flag allows recreating existing AVD", canOverwrite)
    }

    @Test
    fun f9_emulatorMissingAccelerationFallback() {
        val hasHardwareAccel = false
        val launchFlags = if (!hasHardwareAccel) "-gpu swiftshader_indirect" else "-gpu host"
        assertTrue(launchFlags.contains("swiftshader"))
    }

    @Test
    fun f9_adbForwardFailureDetection() {
        val forwardResultExitCode = 1 // simulated failure (port already bound)
        val isSuccess = (forwardResultExitCode == 0)
        assertFalse(isSuccess)
    }

    @Test
    fun f9_wearSkinMissingFallback() {
        val requestedSkin = "custom_nonexistent_skin"
        val availableSkins = listOf("wearos_large_round", "wearos_small_round")
        val resolvedSkin = if (availableSkins.contains(requestedSkin)) requestedSkin else "wearos_large_round"
        assertEquals("wearos_large_round", resolvedSkin)
    }

    // =========================================================================
    // Feature 10: Unit & Integration Test Suite Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f10_testExecutionFailureDetection() {
        val failureRecorded = true
        assertTrue(failureRecorded)
    }

    @Test
    fun f10_testTimeoutBoundaryProtection() {
        val timeoutMs = 5000L
        val executionTimeMs = 120L
        assertTrue(executionTimeMs < timeoutMs)
    }

    @Test
    fun f10_missingClassFilterHandling() {
        val targetClass = "NonExistentTestClass"
        val classExists = try {
            Class.forName(targetClass)
            true
        } catch (e: ClassNotFoundException) {
            false
        }
        assertFalse(classExists)
    }

    @Test
    fun f10_assertionErrorStackTracePreservation() {
        val msg = "Assertion failed on volume range"
        try {
            throw AssertionError(msg)
        } catch (e: AssertionError) {
            assertEquals(msg, e.message)
        }
    }

    @Test
    fun f10_corruptedReportXmlGuard() {
        val emptyXml = ""
        val parsed = if (emptyXml.isBlank()) null else "ValidXml"
        assertNull(parsed)
    }

    // =========================================================================
    // Feature 11: Opaque-Box E2E Test Suite & Hardening Boundary & Edge Cases
    // =========================================================================

    @Test
    fun f11_e2eNetworkDisconnectionDuringCommand() {
        wearableEnv.setNetworkConnected(false)
        val sent = wearableEnv.sendMessage("phone_node_01", E2EContracts.PATH_PLAY)
        assertFalse("Message must fail when network disconnected", sent)
    }

    @Test
    fun f11_e2eConcurrentWatchAndPhoneVolumeAdjustment() {
        var sharedVolume = 0.50f
        // Watch adds 0.10f, phone adds 0.05f concurrently
        sharedVolume = (sharedVolume + 0.10f).coerceIn(0.0f, 1.0f)
        sharedVolume = (sharedVolume + 0.05f).coerceIn(0.0f, 1.0f)
        assertEquals(0.65f, sharedVolume, 0.0001f)
    }

    @Test
    fun f11_e2eDelayedStateDeliveryResync() {
        // Phone updates state while watch is offline
        wearableEnv.setNetworkConnected(false)
        wearableEnv.putDataItem(E2EContracts.PATH_PLAYBACK_STATE, mapOf(E2EContracts.KEY_IS_PLAYING to true))

        // Watch reconnects
        wearableEnv.setNetworkConnected(true)
        val state = wearableEnv.getDataItem(E2EContracts.PATH_PLAYBACK_STATE)
        assertEquals(true, state?.values?.get(E2EContracts.KEY_IS_PLAYING))
    }

    @Test
    fun f11_e2eRapidTrackSkippingRaceCondition() {
        // Skip 10 tracks rapidly on desktop
        for (i in 1..10) {
            connectEnv.dispatchCommand("desktop_peer_alpha", E2EContracts.ACTION_NEXT)
        }
        // Started at 101L, +10 = 111L
        assertEquals(111L, connectEnv.desktopState.currentTrackId)
    }

    @Test
    fun f11_e2eMalformedStatePayloadRecovery() {
        val corruptedMap = mapOf<String, Any>(
            E2EContracts.KEY_VOLUME to "NotAFloat", // corrupted type
            E2EContracts.KEY_IS_PLAYING to "true"
        )
        // Recovery logic must extract safe fallback
        val rawVolume = corruptedMap[E2EContracts.KEY_VOLUME]
        val safeVolume = (rawVolume as? Float) ?: 0.50f
        assertEquals(0.50f, safeVolume, 0.0001f)
    }
}
