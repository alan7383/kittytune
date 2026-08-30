package com.alananasss.kittytune.data.stats

/**
 * [ListenRules] written once, in SQL (issue #33).
 *
 * The rules exist twice by necessity: in Kotlin, where a listen in progress is judged, and in SQL,
 * where a million finished ones are. Two copies of a rule drift, and when this one drifted the two
 * platforms reported different totals from *identical* data — the desktop counted a listen by how much
 * was heard while the phone counted it by which button ended it. So both copies live here, next to each
 * other, and every query in the app is built from these fragments rather than spelling the rule out
 * again.
 *
 * The values are interpolated rather than bound as parameters because they are compile-time constants
 * from [ListenRules], never user input, and a query built from `?` placeholders could not be shared
 * between the two platforms' very different query builders.
 */
object StatsSql {

    /**
     * Whether a row counts as having listened to something.
     *
     * The same shape as [ListenRules.countsAsPlay]: the absolute threshold, or half of a track too
     * short to reach it.
     */
    const val COUNTS_AS_PLAY: String =
        "(listenDurationMs >= ${ListenRules.MIN_LISTEN_MS} OR " +
            "(trackDurationMs > 0 AND listenDurationMs >= trackDurationMs * ${ListenRules.MIN_LISTEN_FRACTION}))"

    /**
     * Whether a row reached the end of its track.
     *
     * Judged on how far playback got, which is what [ListenRules.isComplete] uses — except for rows
     * written before that was recorded. Those have `furthestPositionMs = 0` and cannot be judged that
     * way at all, so for them the old ending label is honoured instead. Without that fallback, turning
     * the new rule on would have declared every completed track in the existing history unfinished.
     */
    const val IS_COMPLETE: String =
        "((trackDurationMs > 0 AND furthestPositionMs >= trackDurationMs * ${ListenRules.COMPLETION_FRACTION}) OR " +
            "(furthestPositionMs = 0 AND eventType IN ('PLAY_COMPLETE', 'REPEAT_ONE_LOOP')))"

    /** Whether a row is a skip: left early, and not enough of it was heard. */
    const val IS_SKIP: String = "(NOT $IS_COMPLETE AND NOT $COUNTS_AS_PLAY)"
}
