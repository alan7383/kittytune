package com.alananasss.kittytune.wear.common

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Serialization helpers for discrete MessageClient command payloads.
 */
object WearCodecs {

    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    // =========================================================================
    // Volume Codec
    // =========================================================================

    /**
     * Encodes a normalized volume fraction (0.0f..1.0f) into a 4-byte Float array.
     */
    fun encodeVolume(volume: Float): ByteArray {
        val clamped = if (volume.isNaN()) 0.0f else volume.coerceIn(0.0f, 1.0f)
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putFloat(clamped).array()
    }

    /**
     * Decodes a volume value from a byte array.
     * Supports both 4-byte Float binary representation and UTF-8 string format (e.g. "0.75").
     * Returns 0.0f if the payload is null, empty, or cannot be parsed.
     */
    fun decodeVolume(bytes: ByteArray?): Float {
        if (bytes == null || bytes.isEmpty()) return 0.0f

        // Check if payload is a UTF-8 string (e.g. "0.75", "1.0", "-0.2")
        val asString = try {
            val s = String(bytes, Charsets.UTF_8).trim()
            if (s.isNotEmpty() && s.all { it in '0'..'9' || it == '.' || it == '-' || it == '+' }) s else null
        } catch (_: Exception) {
            null
        }
        val parsedFromString = asString?.toFloatOrNull()
        if (parsedFromString != null) {
            return if (parsedFromString.isNaN()) 0.0f else parsedFromString.coerceIn(0.0f, 1.0f)
        }

        // If not a numeric string, decode as 4-byte IEEE 754 Float
        if (bytes.size == 4) {
            return try {
                val f = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).float
                if (f.isNaN()) 0.0f else f.coerceIn(0.0f, 1.0f)
            } catch (_: Exception) {
                0.0f
            }
        }

        return 0.0f
    }

    // =========================================================================
    // Target Selection Codec
    // =========================================================================

    /**
     * Encodes a [TargetSelectionPayload] into UTF-8 JSON bytes.
     */
    fun encodeTarget(payload: TargetSelectionPayload): ByteArray {
        return json.encodeToString(payload).toByteArray(Charsets.UTF_8)
    }

    /**
     * Decodes a [TargetSelectionPayload] from a byte array.
     * Falls back to interpreting plain strings ("phone", "desktop", "desktop:peerId")
     * if the incoming payload is not formatted as JSON.
     */
    fun decodeTarget(bytes: ByteArray?): TargetSelectionPayload? {
        if (bytes == null || bytes.isEmpty()) return null
        val str = String(bytes, Charsets.UTF_8).trim()
        if (str.isEmpty()) return null

        return try {
            json.decodeFromString<TargetSelectionPayload>(str)
        } catch (_: Exception) {
            when {
                str.equals("phone", ignoreCase = true) -> TargetSelectionPayload.phone()
                str.equals("desktop", ignoreCase = true) -> TargetSelectionPayload.desktop(peerId = "")
                str.startsWith("desktop:", ignoreCase = true) -> {
                    val peerId = str.substringAfter("desktop:").trim()
                    TargetSelectionPayload.desktop(peerId = peerId)
                }
                else -> TargetSelectionPayload(targetType = WearConstants.TARGET_TYPE_PHONE, peerId = str)
            }
        }
    }

    // =========================================================================
    // Target Devices List Codec
    // =========================================================================

    fun encodeTargetsList(targets: List<TargetDeviceInfo>): String {
        return json.encodeToString(targets)
    }

    fun decodeTargetsList(jsonString: String?): List<TargetDeviceInfo> {
        if (jsonString.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<TargetDeviceInfo>>(jsonString)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
