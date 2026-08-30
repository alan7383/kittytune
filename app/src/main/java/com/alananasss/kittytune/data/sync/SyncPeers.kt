package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.KittyTuneApp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * The devices this one knows, remembered (issue #33).
 *
 * The desktop's copy of this file, with Android's preferences underneath. Kept the same deliberately: the
 * two sides have to agree about what "paired" means, and the last time they did not, one of them thought a
 * device was paired while the other had never recorded it.
 *
 * Pairing is meant to happen once. A code that had to be pasted or scanned before every exchange is not
 * pairing, it is a password prompt — so an exchange in either direction records the other device, and
 * everything after that is automatic.
 *
 * ## One list, not two
 *
 * There used to be two: devices we could call, and devices that had called us. That split existed only
 * because a phone could not be called back, and it leaked into the screen as two lists of the same
 * devices that the reader had to reconcile. Now a caller hands over its own connect-back details as part
 * of the exchange, so both sides end up with the same kind of entry and the screen has one list. An
 * entry that predates that, or comes from a device that cannot listen, simply has [KnownDevice.canDial]
 * false and is synced by the other side calling in.
 *
 * ## Addresses are not identity
 *
 * A device's address changes — DHCP after a router reboot, a laptop moving networks — while its id and
 * secret do not. So the address here is a hint, refreshed whenever the device is heard from, and when it
 * stops answering the device is looked for by id with [SyncDiscovery] rather than being declared gone.
 * That is what makes "paired once" hold instead of "paired until the router restarts".
 */
object SyncPeers {

    private val gson = Gson()
    private val prefs by lazy {
        KittyTuneApp.instance.getSharedPreferences("sync_state", android.content.Context.MODE_PRIVATE)
    }

    private const val KEY_DEVICES = "known_devices"
    private const val KEY_MIGRATED = "known_devices_migrated"

    /** The shapes the two old keys held, read once so an existing pairing survives the change. */
    private const val KEY_OLD_PEERS = "paired_peers"
    private const val KEY_OLD_INBOUND = "inbound_devices"

    private val deviceListType = object : TypeToken<List<KnownDevice>>() {}.type
    private val payloadListType = object : TypeToken<List<PairingPayload>>() {}.type
    private val inboundListType = object : TypeToken<List<LegacyInbound>>() {}.type

    private data class LegacyInbound(
        val deviceId: String = "",
        val deviceName: String = "",
        val lastSyncedAtMs: Long = 0,
    )

    @Synchronized
    fun all(): List<KnownDevice> {
        migrateIfNeeded()
        val raw = prefs.getString(KEY_DEVICES, null) ?: return emptyList()
        return runCatching { gson.fromJson<List<KnownDevice>>(raw, deviceListType) }
            .getOrNull()
            ?.filter { it.deviceId.isNotBlank() }
            ?: emptyList()
    }

    fun isEmpty(): Boolean = all().isEmpty()

    fun find(deviceId: String): KnownDevice? = all().firstOrNull { it.deviceId == deviceId }

    /** Whether there is anything we could start an exchange with ourselves. */
    fun anyDialable(): Boolean = all().any { it.canDial }

    /**
     * Adds or updates a device, keyed on its id.
     *
     * Fields are merged rather than replaced, because the two ways a device is learned about carry
     * different halves of the truth: a pairing code brings the secret, a discovery answer brings the
     * current address, and an exchange brings the name. Whichever arrives, what is already known and not
     * contradicted stays — otherwise remembering that a phone just synced would erase the details needed
     * to call it back.
     */
    @Synchronized
    fun remember(device: KnownDevice) {
        if (device.deviceId.isBlank()) return
        val merged = device.mergedOnto(find(device.deviceId))
        write(all().filter { it.deviceId != merged.deviceId } + merged)
    }

    /** Records that an exchange with [deviceId] just succeeded. */
    @Synchronized
    fun markSynced(deviceId: String, deviceName: String = "", host: String = "") {
        val existing = find(deviceId) ?: KnownDevice(deviceId = deviceId)
        remember(
            existing.copy(
                deviceName = deviceName.ifBlank { existing.deviceName },
                host = host.ifBlank { existing.host },
                lastSyncedAtMs = System.currentTimeMillis(),
            )
        )
    }

    @Synchronized
    fun forget(deviceId: String) {
        write(all().filter { it.deviceId != deviceId })
    }

    @Synchronized
    fun forgetAll() {
        write(emptyList())
    }

    private fun write(devices: List<KnownDevice>) {
        prefs.edit()
            .putString(KEY_DEVICES, gson.toJson(devices.sortedByDescending { it.lastSyncedAtMs }))
            .apply()
    }

    /**
     * Folds the two old keys into the new one, once.
     *
     * An install that had already paired must not be asked to pair again just because the storage
     * changed. Read-and-fold rather than read-through, so this cost is paid once and the old keys can be
     * deleted later without a second migration.
     */
    private fun migrateIfNeeded() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return
        prefs.edit().putBoolean(KEY_MIGRATED, true).apply()

        val fromPeers = runCatching {
            prefs.getString(KEY_OLD_PEERS, null)
                ?.let { gson.fromJson<List<PairingPayload>>(it, payloadListType) }
                .orEmpty()
                .filter { it.deviceId.isNotBlank() }
                .map {
                    KnownDevice(
                        deviceId = it.deviceId,
                        deviceName = it.deviceName,
                        host = it.host,
                        port = it.port,
                        secret = it.secret,
                    )
                }
        }.getOrDefault(emptyList())

        val fromInbound = runCatching {
            prefs.getString(KEY_OLD_INBOUND, null)
                ?.let { gson.fromJson<List<LegacyInbound>>(it, inboundListType) }
                .orEmpty()
                .filter { it.deviceId.isNotBlank() }
                .map {
                    KnownDevice(
                        deviceId = it.deviceId,
                        deviceName = it.deviceName,
                        lastSyncedAtMs = it.lastSyncedAtMs,
                    )
                }
        }.getOrDefault(emptyList())

        if (fromPeers.isEmpty() && fromInbound.isEmpty()) return

        // Dialable entries last, so their secret and address win the merge on an id present in both.
        val folded = LinkedHashMap<String, KnownDevice>()
        for (device in fromInbound + fromPeers) {
            val existing = folded[device.deviceId]
            folded[device.deviceId] = if (existing == null) device else device.copy(
                deviceName = device.deviceName.ifBlank { existing.deviceName },
                lastSyncedAtMs = maxOf(device.lastSyncedAtMs, existing.lastSyncedAtMs),
            )
        }
        prefs.edit().putString(KEY_DEVICES, gson.toJson(folded.values.toList())).apply()
    }
}

/**
 * A device this one knows about.
 *
 * @param host and [port] where it was last seen. Empty when it has only ever called us and did not say
 *   how to call back — an older version, which is the only case that still exists.
 * @param secret what it demands to be shown. Empty means we cannot start an exchange with it, only
 *   answer one.
 * @param lastSyncedAtMs zero until the first successful exchange, which is what tells a paired-but-never
 *   -reached device from a working one.
 */
data class KnownDevice(
    val deviceId: String,
    val deviceName: String = "",
    val host: String = "",
    val port: Int = SyncService.DEFAULT_PORT,
    val secret: String = "",
    val platform: String = "",
    val lastSyncedAtMs: Long = 0,
    val pairedAtMs: Long = 0,
) {
    /** Whether this device can be called, as opposed to only answered. */
    val canDial: Boolean get() = host.isNotBlank() && secret.isNotBlank()

    /**
     * This entry laid over what was already known, field by field.
     *
     * Merged rather than replaced, because the ways a device is learned about each carry a different half
     * of the truth: a pairing code brings the secret, a discovery answer brings the current address, an
     * inbound exchange brings the name and the time. Whichever arrives, what is already known and not
     * contradicted survives — otherwise recording that a phone had just synced would erase the details
     * needed to call it back, and the pairing would quietly become one-directional again (issue #33).
     *
     * A blank field means "I do not know", never "it is empty", which is why every string is merged with
     * [String.ifBlank] rather than taken outright. Times only move forward, and [pairedAtMs] is whichever
     * of the two is earliest and real, so "paired since" does not reset on every sync.
     */
    fun mergedOnto(existing: KnownDevice?): KnownDevice = KnownDevice(
        deviceId = deviceId,
        deviceName = deviceName.ifBlank { existing?.deviceName.orEmpty() },
        host = host.ifBlank { existing?.host.orEmpty() },
        port = port.takeIf { it in 1..65535 } ?: existing?.port ?: SyncService.DEFAULT_PORT,
        secret = secret.ifBlank { existing?.secret.orEmpty() },
        platform = platform.ifBlank { existing?.platform.orEmpty() },
        lastSyncedAtMs = maxOf(lastSyncedAtMs, existing?.lastSyncedAtMs ?: 0L),
        pairedAtMs = existing?.pairedAtMs?.takeIf { it > 0 }
            ?: pairedAtMs.takeIf { it > 0 }
            ?: System.currentTimeMillis(),
    )

    /** What it takes to reach it, or null when it cannot be reached. */
    fun toPairing(): PairingPayload? =
        if (!canDial) null
        else PairingPayload(
            host = host,
            port = port,
            secret = secret,
            deviceId = deviceId,
            deviceName = deviceName,
        )

    val label: String get() = deviceName.ifBlank { host.ifBlank { deviceId.take(8) } }
}
