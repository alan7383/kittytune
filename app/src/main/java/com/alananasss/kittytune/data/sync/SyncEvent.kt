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

    /** One track liked or unliked. Payload: [LikePayload]. */
    const val LIKE = "like"

    /** One playlist liked or unliked. Payload: [PlaylistLikePayload]. */
    const val PLAYLIST_LIKE = "playlist_like"
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

/**
 * A track being liked or unliked, as it travels between devices (issue #33).
 *
 * ## Why a like is an event and not a number
 *
 * A count cannot be merged — the report that started this was 142 favourites on the phone against 90 on
 * the desktop, and no rule over the two totals gets to the right answer, because the difference is not
 * arithmetic. It is a set of specific tracks that one device knows about and the other does not: local
 * files and Spotify tracks SoundCloud never held, plus likes that were made while the two were apart.
 *
 * So each like and each unlike is its own fact, timestamped, and the state of a track is whichever fact
 * about it is newest by [LikePayload.atMs]. That is a last-writer-wins register per track id, and it is chosen
 * because it is the only rule that needs no coordination: two devices that cannot see each other can both
 * be edited, in any order, and still agree afterwards. Ties — the same millisecond on two devices — fall
 * back to the device id, so both sides break them the same way rather than converging to different states.
 *
 * ## Why the whole track and not just its id
 *
 * The other device may have no way to look the id up. A local file has no catalogue entry at all, and a
 * Spotify or YouTube id means nothing to SoundCloud's API — so a like carrying only an id would arrive as
 * a row the receiving device could name, but not play or draw. [track] is the track as the sending device
 * held it, which is enough to add it to a library outright.
 *
 * @param trackId what this is about, and the key the last-writer-wins rule is applied per.
 * @param liked true for a like, false for an unlike.
 * @param atMs when the user did it, on the recording device's clock. This is the whole of the merge rule,
 *   so it is carried in the payload rather than read from [SyncEvent.timestampMs] — a log line's time is
 *   for display, and a seeded like is deliberately stamped with when the like *happened* rather than when
 *   the event was written.
 * @param track the track itself, for a like. Absent for an unlike, which needs nothing but the id.
 */
data class LikePayload(
    val trackId: Long,
    val liked: Boolean,
    val atMs: Long,
    val track: com.alananasss.kittytune.domain.Track? = null,
)

/**
 * A playlist being liked or unliked. Same last-writer-wins rule as [LikePayload], keyed on the playlist.
 *
 * [permalinkUrl] and [urn] ride along because unliking on SoundCloud needs the urn and it cannot always be
 * derived from the id — a system playlist's urn names its kind ("artist-stations", "track-stations"), and
 * guessing wrong makes the request a no-op that looks like a successful sync.
 */
data class PlaylistLikePayload(
    val playlistId: Long,
    val liked: Boolean,
    val atMs: Long,
    val permalinkUrl: String? = null,
    val urn: String? = null,
)
