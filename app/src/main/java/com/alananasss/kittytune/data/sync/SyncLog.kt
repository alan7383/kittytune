package com.alananasss.kittytune.data.sync

import com.alananasss.kittytune.KittyTuneApp
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

/**
 * The device's own append-only log, on disk (issue #33).
 *
 * The same file format and the same rules as the desktop's copy, deliberately: the two exchange
 * these lines verbatim, so anything that differs here is a bug on one side or the other. One JSON
 * object per line, appended and never rewritten, and a line that will not parse is skipped rather
 * than fatal — a log that refuses to load would take the whole listening history with it.
 */
object SyncLog {

    private val gson = Gson()

    private val prefs by lazy {
        KittyTuneApp.instance.getSharedPreferences("sync_state", android.content.Context.MODE_PRIVATE)
    }

    private val file: File by lazy { File(KittyTuneApp.instance.filesDir, "sync_log.jsonl") }

    /** In memory as well as on disk: the whole log is read for every merge, and it is small. */
    private val events = mutableListOf<SyncEvent>()
    private val eventIds = HashSet<String>()
    private val maxSeqByDevice = HashMap<String, Long>()
    private var receivedMarks = emptyMap<String, Long>()
    private var loaded = false

    /**
     * This install's identity, generated once.
     *
     * Not derived from anything the system reports: an identity that changes makes every past event
     * look like it came from a device we have never met, and Android identifiers are exactly the kind
     * of thing that changes on a reinstall or a restore.
     */
    val deviceId: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString().also { prefs.edit().putString(KEY_DEVICE_ID, it).apply() }
    }

    /** A name for the pairing screen, so two devices are told apart by something readable. */
    var deviceName: String
        get() = prefs.getString(KEY_DEVICE_NAME, null)?.takeIf { it.isNotBlank() }
            ?: defaultDeviceName()
        set(value) {
            prefs.edit().putString(KEY_DEVICE_NAME, value.trim().take(64)).apply()
        }

    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_DEVICE_NAME = "device_name"
    private const val KEY_MARKS = "marks"

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        events.clear()
        eventIds.clear()
        maxSeqByDevice.clear()
        if (file.exists()) {
            file.forEachLine { line ->
                val event = runCatching { gson.fromJson(line, SyncEvent::class.java) }.getOrNull()
                if (event?.isWellFormed == true) index(event)
            }
        }
        // An older build could acknowledge a write that failed. Ask the peer again for any
        // acknowledged tail that is missing from the copy on disk.
        receivedMarks = readMarks(KEY_MARKS)
            .mapValues { (origin, mark) -> minOf(mark, maxSeqByDevice[origin] ?: 0L) }
            .filterValues { it > 0L }
        loaded = true
    }

    private fun index(event: SyncEvent) {
        if (!eventIds.add(event.id)) return
        val known = maxSeqByDevice[event.deviceId]
        if (known == null || event.seq > known) maxSeqByDevice[event.deviceId] = event.seq
        events.add(event)
    }

    /** Everything the log holds, ours and every peer's. A copy: callers iterate while we append. */
    @Synchronized
    fun all(): List<SyncEvent> {
        ensureLoaded()
        return events.toList()
    }

    @Synchronized
    fun size(): Int {
        ensureLoaded()
        return events.size
    }

    /**
     * Records something that happened here.
     *
     * @return the event as written, with the sequence number it was given.
     */
    @Synchronized
    fun append(kind: String, payload: Any, timestampMs: Long = System.currentTimeMillis()): SyncEvent {
        ensureLoaded()
        val event = SyncEvent(
            deviceId = deviceId,
            seq = (maxSeqByDevice[deviceId] ?: 0L) + 1,
            timestampMs = timestampMs,
            kind = kind,
            payload = gson.toJson(payload),
        )
        write(listOf(event))
        return event
    }

    data class BatchItem(val kind: String, val payload: Any, val timestampMs: Long = System.currentTimeMillis())

    @Synchronized
    fun appendBatch(items: List<BatchItem>): List<SyncEvent> {
        if (items.isEmpty()) return emptyList()
        ensureLoaded()
        var currentSeq = (maxSeqByDevice[deviceId] ?: 0L) + 1
        val created = ArrayList<SyncEvent>(items.size)
        for (item in items) {
            created.add(
                SyncEvent(
                    deviceId = deviceId,
                    seq = currentSeq++,
                    timestampMs = item.timestampMs,
                    kind = item.kind,
                    payload = gson.toJson(item.payload),
                )
            )
        }
        write(created)
        return created
    }

    /**
     * Records events that came from a peer. Already-known ones are skipped, so calling this twice
     * with the same batch is not the same as playing it twice.
     *
     * @return exactly the events that were new, for whoever has to apply them.
     */
    @Synchronized
    fun merge(incoming: List<SyncEvent>): List<SyncEvent> {
        ensureLoaded()
        val candidates = SyncMerge.selectNew(incoming, receivedMarks, deviceId)
        val fresh = candidates.filterNot { it.id in eventIds }
        if (fresh.isNotEmpty()) write(fresh)
        val next = SyncMerge.advance(receivedMarks, candidates)
        if (next != receivedMarks) {
            prefs.edit().putString(KEY_MARKS, gson.toJson(next)).apply()
            receivedMarks = next
        }
        return fresh
    }

    private fun write(batch: List<SyncEvent>) {
        val bytes = batch.joinToString("\n", postfix = "\n") { gson.toJson(it) }.toByteArray()
        file.parentFile?.mkdirs()
        RandomAccessFile(file, "rw").use { output ->
            val originalSize = output.length()
            try {
                if (originalSize > 0) {
                    output.seek(originalSize - 1)
                    // Keep a line interrupted by a crash separate from the new batch.
                    if (output.read() != '\n'.code) output.write('\n'.code)
                }
                output.write(bytes)
                output.fd.sync()
            } catch (failure: IOException) {
                // A failed append must leave the same sequence numbers available for the retry.
                try {
                    output.setLength(originalSize)
                    output.fd.sync()
                } catch (rollback: IOException) {
                    loaded = false
                    failure.addSuppressed(rollback)
                }
                throw failure
            }
        }
        batch.forEach(::index)
    }

    /** How far we have got with each device we have heard from. */
    @Synchronized
    fun marks(): Map<String, Long> {
        ensureLoaded()
        return receivedMarks
    }

    /**
     * What a peer last told us it holds, so the next exchange sends only what is new. Empty for a
     * device we have never talked to, which means "send everything" — correct, because the peer drops
     * what it already has.
     */
    @Synchronized
    fun peerMarks(peerDeviceId: String): Map<String, Long> = readMarks(keyPeerMarks(peerDeviceId))

    @Synchronized
    fun setPeerMarks(peerDeviceId: String, marks: Map<String, Long>) {
        if (peerDeviceId.isBlank()) return
        prefs.edit().putString(keyPeerMarks(peerDeviceId), gson.toJson(marks)).apply()
    }

    private fun readMarks(key: String): Map<String, Long> {
        val raw = prefs.getString(key, null) ?: return emptyMap()
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            (gson.fromJson(raw, Map::class.java) as Map<String, Double>)
                .mapValues { it.value.toLong() }
        }.getOrDefault(emptyMap())
    }

    private fun keyPeerMarks(peerDeviceId: String) = "peer_marks_$peerDeviceId"

    /**
     * Wipes the log and everything remembered about peers. Used by "clear my statistics".
     *
     * The device's own id and name survive on purpose. An id that changed here would make every
     * event this device recorded afterwards look like it came from a stranger, and the peers would
     * count the same listens twice under two identities.
     */
    @Synchronized
    fun clear() {
        java.nio.file.Files.deleteIfExists(file.toPath())
        events.clear()
        eventIds.clear()
        maxSeqByDevice.clear()
        receivedMarks = emptyMap()
        loaded = true
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it == KEY_MARKS || it.startsWith("peer_marks_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    private fun defaultDeviceName(): String =
        listOfNotNull(android.os.Build.MANUFACTURER, android.os.Build.MODEL)
            .joinToString(" ")
            .trim()
            .ifBlank { "Phone" }
}
