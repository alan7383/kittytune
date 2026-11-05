package com.alananasss.kittytune.wear.common

import kotlinx.serialization.Serializable

/**
 * Represents a playback target device visible from the Wear OS companion.
 *
 * @property id Unique identifier: "phone" for handheld device, or desktop peer UUID.
 * @property name Human-readable device label (e.g. "Phone", "Studio Desktop").
 * @property type Either [WearConstants.TARGET_TYPE_PHONE] or [WearConstants.TARGET_TYPE_DESKTOP].
 * @property isConnected True if the device is currently online/reachable.
 * @property isSelected True if this device is the currently active playback target.
 */
@Serializable
data class TargetDeviceInfo(
    val id: String,
    val name: String,
    val type: String = WearConstants.TARGET_TYPE_DESKTOP,
    val isConnected: Boolean = true,
    val isSelected: Boolean = false,
) {
    val isPhone: Boolean get() = type == WearConstants.TARGET_TYPE_PHONE || id == WearConstants.TARGET_PHONE_ID
    val isDesktop: Boolean get() = type == WearConstants.TARGET_TYPE_DESKTOP
}
