package com.alananasss.kittytune.data.sync

/**
 * What each device has told us, and how far we have got through it (issue #33).
 *
 * This is the whole of the merge rule, kept apart from storage and from the network so it can be
 * reasoned about and tested on its own. Two devices exchange two things: their marks — "I have every
 * event up to seq N from device D" — and the events the other side is missing.
 *
 * The property that makes this safe is that merging is idempotent. Applying the same event twice
 * must not double a total, so an event is applied only when it is past the mark for its own device,
 * and marks only ever move forward.
 */
object SyncMerge {

    /**
     * How far we have got with each device, keyed by device id. A device absent from the map is one
     * we have never heard from, which is the same as being at 0.
     */
    fun markFor(marks: Map<String, Long>, deviceId: String): Long = marks[deviceId] ?: 0L

    /**
     * The events from [incoming] that are actually new, in the order they must be applied.
     *
     * Malformed entries are dropped rather than rejected wholesale: one bad line in a peer's log
     * should cost that line, not the exchange. Events from our own [selfDeviceId] are dropped too —
     * they came back to us round a loop of three devices and we already have them by definition.
     */
    fun selectNew(
        incoming: List<SyncEvent>,
        marks: Map<String, Long>,
        selfDeviceId: String,
    ): List<SyncEvent> =
        incoming
            .asSequence()
            .filter { it.isWellFormed }
            .filter { it.deviceId != selfDeviceId }
            .filter { it.seq > markFor(marks, it.deviceId) }
            .distinctBy { it.id }
            // Per device, in the device's own order: a later event may depend on an earlier one, and
            // the mark can only advance over a contiguous run.
            .sortedWith(compareBy({ it.deviceId }, { it.seq }))
            .toList()

    /**
     * The marks after applying [applied].
     *
     * A mark advances only over an unbroken run from where it was. A batch that arrives with a gap —
     * seq 5 and 7 when we were at 4 — advances the mark to 5 and leaves 7 to be re-sent, rather than
     * jumping to 7 and losing 6 forever. Marks never move backwards.
     */
    fun advance(marks: Map<String, Long>, applied: List<SyncEvent>): Map<String, Long> {
        val next = marks.toMutableMap()
        applied
            .filter { it.isWellFormed }
            .groupBy { it.deviceId }
            .forEach { (deviceId, events) ->
                var mark = markFor(next, deviceId)
                events.map { it.seq }.distinct().sorted().forEach { seq ->
                    if (seq == mark + 1) mark = seq
                }
                if (mark > markFor(next, deviceId)) next[deviceId] = mark
            }
        return next
    }

    /**
     * What to send a peer that reports [peerMarks].
     *
     * Everything we hold that is past the peer's mark for its origin device — including events that
     * originated on a third device, so two devices that never meet still converge through one they
     * both see.
     */
    fun eventsToSend(
        local: List<SyncEvent>,
        peerMarks: Map<String, Long>,
        peerDeviceId: String? = null,
        limit: Int = MAX_EVENTS_PER_EXCHANGE,
    ): List<SyncEvent> =
        local
            .asSequence()
            .filter { it.isWellFormed }
            // A device already has everything it recorded itself, whatever its marks say. Its marks
            // only describe what it has merged from *others*, so without this a device kept handing
            // its peer's own events back to it on every single exchange — sixteen events sent, zero
            // applied, for ever (issue #33).
            .filter { peerDeviceId == null || it.deviceId != peerDeviceId }
            .filter { it.seq > markFor(peerMarks, it.deviceId) }
            .sortedWith(compareBy({ it.deviceId }, { it.seq }))
            .take(limit)
            .toList()

    /**
     * How many events one exchange carries at most.
     *
     * A first pairing has to hand over everything the other side has never seen, which for a year of
     * listening is a single response of many megabytes — enough to run past a read timeout and leave
     * the pair permanently unable to complete a first sync. Capping it is safe because the marks make
     * resumption exact: the sorted order gives each device a contiguous prefix, [advance] moves its
     * mark over exactly that run, and the next exchange carries on from there. It costs a few extra
     * round trips once, and nothing afterwards.
     */
    const val MAX_EVENTS_PER_EXCHANGE = 500

    /**
     * The next sequence number for [deviceId] given everything already recorded.
     *
     * Derived from the log rather than counted separately, so a log restored from a backup cannot
     * hand out a number that is already in use.
     */
    fun nextSeq(local: List<SyncEvent>, deviceId: String): Long =
        (local.filter { it.deviceId == deviceId }.maxOfOrNull { it.seq } ?: 0L) + 1
}
