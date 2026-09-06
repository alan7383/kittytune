package com.alananasss.kittytune.data.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps paired devices in step without being asked (issue #33).
 *
 * The desktop's copy of this file. It used to be a fifteen-minute timer living at the bottom of
 * `SyncPeers.kt`, which is wrong in both directions: far too long to wait to see a track you just finished
 * on the computer, and far too often to be reaching for a device that has been out of the house all day.
 *
 * Pairing happens once; after that there is nothing to press. The reason this is not simply a timer is
 * that a timer is wrong in both directions: fifteen minutes is far too long to wait to see a track you
 * just finished on your phone, and far too often to be sweeping for a device that has been out of the
 * house all day. So the work is driven by things that actually change the answer — a listen was recorded,
 * the app started, a button was pressed — with a slow heartbeat underneath as a backstop.
 *
 * Requests are debounced and coalesced: an album on shuffle records fifteen listens in forty minutes and
 * produces one exchange, not fifteen. Only one exchange runs at a time, so a heartbeat landing on top of
 * a manual sync waits rather than duplicating it.
 *
 * "Not on the same network" is the normal case, not an error — the phone is out most of the day — so it
 * is logged and forgotten rather than surfaced.
 */
object SyncScheduler {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var heartbeat: Job? = null
    private var pending: Job? = null

    /** When the current run of requests began, for the cap on how long a debounce can be pushed back. */
    private var burstStartedAtMs = 0L

    /** One exchange at a time, whoever asked for it. */
    private val gate = Mutex()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    /** When the last exchange with anything succeeded, for a screen that wants to say so. */
    private val _lastSyncAtMs = MutableStateFlow(0L)
    val lastSyncAtMs: StateFlow<Long> = _lastSyncAtMs

    /**
     * The backstop. Long, because it only has to catch what the triggers missed: a device that came back
     * onto the network without anything happening on this one.
     */
    private const val HEARTBEAT_MS = 10 * 60 * 1000L

    /**
     * A moment after launch rather than immediately: the network stack is often not up yet when the app
     * starts, and a failed first attempt would then wait a whole heartbeat.
     */
    private const val FIRST_RUN_DELAY_MS = 12_000L

    /**
     * How long a request waits for company.
     *
     * Long enough that a listen finishing mid-album does not produce its own exchange, short enough that
     * picking up the phone a minute later shows the track.
     */
    private const val DEBOUNCE_MS = 25_000L

    /**
     * How often the loop wakes to look around.
     *
     * Cheap: reading this machine's own addresses touches no network. The heartbeat is still ten minutes
     * apart; this is only how finely a change of network can be noticed.
     */
    private const val TICK_MS = 30_000L

    /** The longest a stream of requests may keep postponing the exchange it is asking for. */
    private const val MAX_DEBOUNCE_MS = 3 * 60 * 1000L

    @Synchronized
    fun start() {
        if (heartbeat != null) return
        heartbeat = scope.launch {
            if (SyncPeers.anyDialable()) {
                runCatching { syncAll("startup") }
            }
            delay(FIRST_RUN_DELAY_MS)
            var lastPassAtMs = System.currentTimeMillis()
            var lastAddress = ""
            while (isActive) {
                val address = runCatching { SyncService.localAddress() }.getOrDefault("")
                val now = System.currentTimeMillis()
                // A changed local address means this device has joined a different network, which is
                // exactly the moment the other one might be reachable again. Waiting out the rest of a
                // ten-minute heartbeat after walking in the front door is what made sync feel absent
                // rather than merely slow (issue #33).
                val movedNetwork = lastAddress.isNotEmpty() && address != lastAddress
                lastAddress = address

                if (movedNetwork || now - lastPassAtMs >= HEARTBEAT_MS) {
                    lastPassAtMs = now
                    runCatching { syncAll(if (movedNetwork) "network changed" else "heartbeat") }
                }
                delay(TICK_MS)
            }
        }
    }

    @Synchronized
    fun stop() {
        heartbeat?.cancel()
        heartbeat = null
        pending?.cancel()
        pending = null
    }

    /**
     * Asks for an exchange soon.
     *
     * Cheap to call from anywhere and on every listen: it collapses into one run. Does nothing at all
     * when there is no device we could call, so an install that has never paired pays nothing.
     */
    @Synchronized
    fun requestSync(reason: String) {
        if (!SyncPeers.anyDialable()) return

        val now = System.currentTimeMillis()
        // A debounce that resets on every request never fires while requests keep coming, and an album of
        // three-minute tracks produces one every three minutes for an hour. So the wait is capped: once the
        // first request of a burst is [MAX_DEBOUNCE_MS] old, later ones no longer push it back.
        val firstOfBurst = pending?.isActive != true
        if (firstOfBurst) burstStartedAtMs = now
        if (!firstOfBurst && now - burstStartedAtMs >= MAX_DEBOUNCE_MS) return

        pending?.cancel()
        pending = scope.launch {
            delay(DEBOUNCE_MS)
            runCatching { syncAll(reason) }
        }
    }

    fun triggerImmediateSync(reason: String = "immediate") {
        if (!SyncPeers.anyDialable()) return
        scope.launch {
            runCatching { syncAll(reason) }
        }
    }

    /**
     * One pass over every device we can call, now.
     *
     * @return how many exchanges succeeded, for a screen that wants to say something after a manual run.
     */
    suspend fun syncAll(reason: String = "manual"): Int = gate.withLock {
        val devices = SyncPeers.all().filter { it.canDial }
        if (devices.isEmpty()) return@withLock 0

        _isSyncing.value = true
        try {
            var succeeded = 0
            for (device in devices) {
                when (val result = drain(device)) {
                    is SyncClient.Result.Success -> {
                        succeeded++
                        _lastSyncAtMs.value = System.currentTimeMillis()
                        if (result.received > 0 || result.sent > 0) {
                            android.util.Log.i(
                                "SyncScheduler",
                                "Synced with ${result.peerName} ($reason): " +
                                    "${result.received} in, ${result.sent} out"
                            )
                        }
                    }

                    SyncClient.Result.Unauthorized -> android.util.Log.w(
                        "SyncScheduler",
                        "${device.label} refused our code; it needs pairing again"
                    )

                    is SyncClient.Result.Failed -> Unit
                }
            }
            succeeded
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Exchanges with one device until there is nothing left to exchange (issue #33).
     *
     * One exchange carries at most [SyncMerge.MAX_EVENTS_PER_EXCHANGE] events, which is what keeps a first
     * pairing from being a single multi-megabyte response that runs past a read timeout. But a cap without
     * a loop is worse than no cap: a year of listening would then need one exchange per heartbeat, so
     * pairing two devices with real histories would take hours to converge and look stuck the whole time.
     *
     * A full batch means "there is more", so the exchange repeats immediately. Bounded, because a peer that
     * keeps reporting a full batch without the marks advancing is misbehaving and must not spin here.
     *
     * @return the last exchange's result, with the totals accumulated across the rounds.
     */
    private suspend fun drain(device: KnownDevice): SyncClient.Result {
        var received = 0
        var sent = 0
        var last: SyncClient.Result = SyncClient.Result.Failed("not attempted")

        repeat(MAX_ROUNDS) {
            // Re-read between rounds: the first one may have found the device at a new address and
            // corrected the entry, and starting the next round from the stale one would spend a connect
            // timeout rediscovering what is already known.
            val current = SyncPeers.find(device.deviceId) ?: device
            val result = SyncClient.exchangeWith(current)
            last = result
            if (result !is SyncClient.Result.Success) return result
            received += result.received
            sent += result.sent
            val more = result.received >= SyncMerge.MAX_EVENTS_PER_EXCHANGE ||
                result.sent >= SyncMerge.MAX_EVENTS_PER_EXCHANGE
            if (!more) {
                return SyncClient.Result.Success(result.peerName, received, sent)
            }
        }
        // Ran out of rounds with the peer still reporting more. Report what was actually moved rather than
        // just the last round's share of it.
        return (last as? SyncClient.Result.Success)
            ?.let { SyncClient.Result.Success(it.peerName, received, sent) }
            ?: last
    }

    /**
     * Enough rounds for a very long history at 500 events each, and few enough that a peer stuck in a loop
     * costs a bounded number of requests rather than an evening.
     */
    private const val MAX_ROUNDS = 40
}
