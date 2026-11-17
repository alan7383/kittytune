package com.alananasss.kittytune.wear.common

import kotlinx.serialization.Serializable

/**
 * Payload sent over [WearConstants.ACTION_TARGET_SELECT] to change the active playback target.
 *
 * @property targetType [WearConstants.TARGET_TYPE_PHONE] or [WearConstants.TARGET_TYPE_DESKTOP].
 * @property peerId Desktop peer device ID if targetType is DESKTOP, null if PHONE.
 * @property peerName Optional human-readable name of the peer.
 */
@Serializable
data class TargetSelectionPayload(
    val targetType: String,
    val peerId: String? = null,
    val peerName: String? = null,
) {
    companion object {
        fun phone(): TargetSelectionPayload = TargetSelectionPayload(
            targetType = WearConstants.TARGET_TYPE_PHONE,
            peerId = WearConstants.TARGET_PHONE_ID,
            peerName = WearConstants.TARGET_PHONE_LABEL,
        )

        fun desktop(peerId: String, peerName: String? = null): TargetSelectionPayload = TargetSelectionPayload(
            targetType = WearConstants.TARGET_TYPE_DESKTOP,
            peerId = peerId,
            peerName = peerName,
        )
    }
}
