package com.alananasss.kittytune.data

import com.alananasss.kittytune.domain.Track
import com.google.gson.Gson
import java.lang.management.ManagementFactory
import java.util.Base64
import kotlin.random.Random

object CompactQueueBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val gson = Gson()
        val codec = QueueSnapshotCodec(gson)
        val unbuffered = UnbufferedQueueSnapshotCodec(gson)
        val allocations = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        check(allocations.isThreadAllocatedMemorySupported)
        allocations.isThreadAllocatedMemoryEnabled = true
        val threadId = Thread.currentThread().threadId()
        var checksum = 0L
        println("process,tracks,shape,round,legacy_chars,compact_chars,legacy_utf8_bytes,compact_utf8_bytes,legacy_encode_ns,compact_encode_ns,legacy_encode_alloc_bytes,compact_encode_alloc_bytes,legacy_decode_ns,compact_decode_ns,legacy_decode_alloc_bytes,compact_decode_alloc_bytes,unbuffered_encode_ns,unbuffered_decode_ns,unbuffered_encode_alloc_bytes,unbuffered_decode_alloc_bytes")
        data class Measurement<T>(val value: T, val ns: Long, val allocated: Long)
        fun <T> measure(block: () -> T): Measurement<T> {
            val before = allocations.getThreadAllocatedBytes(threadId)
            val start = System.nanoTime()
            val value = block()
            val elapsed = System.nanoTime() - start
            return Measurement(value, elapsed, allocations.getThreadAllocatedBytes(threadId) - before)
        }
        for ((count, shape) in listOf(500 to "lean", 5000 to "lean", 5000 to "rich", 20000 to "rich", 5000 to "varied")) {
            val random = Random(417)
            val tracks = (0 until count).map { index ->
                val track = representativeTrack(index)
                when (shape) {
                    "lean" -> track.copy(description = null)
                    "varied" -> track.copy(description = Base64.getEncoder().encodeToString(ByteArray(512).also { random.nextBytes(it) }))
                    else -> track
                }
            }
            val legacy = gson.toJson(tracks)
            val compact = codec.encode(tracks)
            val previous = unbuffered.encode(tracks)
            // Both directions must remain compatible with the stored v1 format.
            check(gson.toJson(codec.decode<Array<Track>>(previous, Array<Track>::class.java)) == legacy)
            check(gson.toJson(unbuffered.decode<Array<Track>>(compact, Array<Track>::class.java)) == legacy)
            repeat(2) {
                checksum += gson.fromJson(legacy, Array<Track>::class.java).size
                checksum += codec.decode<Array<Track>>(compact, Array<Track>::class.java)!!.size
                checksum += gson.toJson(tracks).length + codec.encode(tracks).length
                checksum += unbuffered.encode(tracks).length
                checksum += unbuffered.decode<Array<Track>>(previous, Array<Track>::class.java)!!.size
            }
            repeat(6) { round ->
                val order = List(3) { (it + round) % 3 }
                val encoded = order.associateWith { variant -> measure {
                    when (variant) {
                        0 -> gson.toJson(tracks)
                        1 -> unbuffered.encode(tracks)
                        else -> codec.encode(tracks)
                    }
                } }
                val decoded = order.reversed().associateWith { variant -> measure {
                    when (variant) {
                        0 -> gson.fromJson(legacy, Array<Track>::class.java)
                        1 -> unbuffered.decode<Array<Track>>(previous, Array<Track>::class.java)!!
                        else -> codec.decode<Array<Track>>(compact, Array<Track>::class.java)!!
                    }
                } }
                val oldEncode = encoded.getValue(0)
                val newEncode = encoded.getValue(2)
                val oldDecode = decoded.getValue(0)
                val newDecode = decoded.getValue(2)
                val previousEncode = encoded.getValue(1)
                val previousDecode = decoded.getValue(1)
                check(oldDecode.value.size == newDecode.value.size && newDecode.value.size == count)
                checksum += oldEncode.value.length + newEncode.value.length + oldDecode.value.last().id + newDecode.value.last().id
                checksum += previousEncode.value.length + previousDecode.value.last().id
                println("${args[0]},$count,$shape,$round,${legacy.length},${compact.length},${legacy.toByteArray(Charsets.UTF_8).size},${compact.toByteArray(Charsets.UTF_8).size},${oldEncode.ns},${newEncode.ns},${oldEncode.allocated},${newEncode.allocated},${oldDecode.ns},${newDecode.ns},${oldDecode.allocated},${newDecode.allocated},${previousEncode.ns},${previousDecode.ns},${previousEncode.allocated},${previousDecode.allocated}")
            }
        }
        System.err.println("checksum=$checksum; synthetic queue metadata, actual app Track/Gson/codec; JVM allocations exclude native workspace; no device or whole-app measurement")
    }
}
