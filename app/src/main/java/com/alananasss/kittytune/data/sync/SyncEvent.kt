package com.alananasss.kittytune.data.sync

/**
 * One thing that happened on one device, in a form any device can merge (issue #33).
 *
 * The sync layer deliberately knows nothing about listening statistics. It carries facts of the form
 * "device D said, at time T, that X happened", and [kind] names what X is. Statistics are the first
 * user, not the only intended one, which is why [payload] is opaque here and interpreted by whoever
 * registered the kind.
 *
 * Why an event log rather than syncing the totals: totals cannot be merged. Two devices that each
 * played twenty minutes have to end up at forty, and no rule over two numbers gets there without
 * knowing what has already been counted. Events can be merged, because each one is either already
 * known or not, and [deviceId] with [seq] says which.
 *
 * @param deviceId the device that recorded this. Never reassigned, so an event's identity is stable.
 * @param seq this device's own counter, starting at 1 and never reused. Gaps are allowed — a
 *   truncated log loses events, it must not renumber them.
 * @param timestampMs when it happened, for ordering and display only. Never for identity: clocks
 *   disagree between devices and go backwards on the same one.
 * @param kind what happened, e.g. [SyncKinds.LISTEN].
 * @param payload the kind's own data, as JSON.
 */
data class SyncEvent(
    val deviceId: String,
    val seq: Long,
    val timestampMs: Long,
    val kind: String,
    val payload: String,
) {
    /** Identity, and what deduplication is done on. */
    val id: String get() = "$deviceId#$seq"

    /** A log entry with no origin or no place in its device's order cannot be merged. */
    val isWellFormed: Boolean
        get() = deviceId.isNotBlank() && seq > 0 && kind.isNotBlank()
}

/** The kinds in use. A device ignores kinds it does not know, so this list may differ by version. */
object SyncKinds {

    /** One finished listen. Payload: [ListenPayload]. */
    const val LISTEN = "listen"
}

/**
 * A listen, as it travels between devices.
 *
 * Carries the track's own details rather than only an id, because the other device may have no way
 * to look a SoundCloud id up — it may be a local file, or the account may differ. A statistic that
 * cannot name the artist is not worth merging.
 */
data class ListenPayload(
    val trackId: Long,
    val trackTitle: String,
    val artistName: String,
    val artistId: Long?,
    val artistPermalink: String?,
    val artistAvatarUrl: String?,
    val artworkUrl: String?,
    val source: String,
    val eventType: String,
    val listenDurationMs: Long,
    val trackDurationMs: Long,
    /**
     * How far playback reached, which is what completion is judged on.
     *
     * Added after the first devices shipped. Gson leaves a missing number at zero, and zero is exactly
     * what the aggregates treat as "this row predates the field" — so an older peer's events still
     * merge, they just fall back to the ending label for completion.
     */
    val furthestPositionMs: Long = 0,
)
