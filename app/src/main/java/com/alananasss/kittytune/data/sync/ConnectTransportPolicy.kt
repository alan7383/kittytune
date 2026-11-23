package com.alananasss.kittytune.data.sync

/** Which link to a peer survives when there is more than one way to reach it. */
internal object ConnectTransportPolicy {
    fun relayStandby(mobile: Boolean, currentTransport: String?) = !mobile && currentTransport == "LAN"

    /**
     * Both devices dial each other on the LAN (so a firewall that blocks one direction cannot keep them
     * apart), which means both directions can succeed at once. Each side then keeps the link dialed by the
     * smaller device id and drops the other, so the two sides always agree on which socket is live.
     *
     * @return true when [existingDialer]'s live LAN link should be kept and the newcomer refused.
     */
    fun keepExistingLan(
        existingTransport: String?,
        existingDialer: String?,
        existingLive: Boolean,
        newTransport: String,
        newDialer: String,
        selfId: String,
        peerId: String,
    ): Boolean = existingTransport == "LAN" && newTransport == "LAN" && existingLive &&
        existingDialer != newDialer && existingDialer == minOf(selfId, peerId)
}
