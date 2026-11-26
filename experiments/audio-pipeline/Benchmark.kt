package com.alananasss.kittytune.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.alananasss.kittytune.audio.AudioMode
import com.alananasss.kittytune.audio.AudioRoutingMatrix
import java.lang.management.BufferPoolMXBean
import java.lang.management.ManagementFactory

object AudioPipelineBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        AudioRoutingMatrix.modeProvider = { AudioMode.STANDARD }
        println("process,frames,preset,round,ungrouped_ns,grouped_ns,blocks,ungrouped_direct_buffers,grouped_direct_buffers,ungrouped_direct_bytes,grouped_direct_bytes")
        val pool = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean::class.java).first { it.name == "direct" }
        var checksum = 0L
        val retained = mutableListOf<Any>()
        // Retain ALL scenarios until the final measurement, including their direct input buffers.
        for (frames in listOf(1024, 4096)) for (preset in listOf("off", "two_effects")) {
            val old = playerEffects()
            val fresh = playerEffects()
            if (preset == "two_effects") {
                listOf(old, fresh).forEach { enable(it[20], true); enable(it[23], true) }
            }
            val format = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
            old.forEach { it.configure(format); it.flush() }
            val group = PlayerEffectsAudioProcessor(fresh)
            group.configure(format)
            group.flush()
            val input = pcm(frames)
            retained.addAll(listOf(old, fresh, group, input))
            val count0 = pool.count
            val memory0 = pool.memoryUsed
            reference(old, input).also { checksum += it.get(0); it.position(it.limit()) }
            val oldCount = pool.count - count0
            val oldBytes = pool.memoryUsed - memory0
            input.rewind()
            val count1 = pool.count
            val memory1 = pool.memoryUsed
            group.queueInput(input)
            group.output.also { checksum += it.get(0); it.position(it.limit()) }
            val newCount = pool.count - count1
            val newBytes = pool.memoryUsed - memory1
            fun run(grouped: Boolean, blocks: Int): Long {
                val start = System.nanoTime()
                repeat(blocks) {
                    input.rewind()
                    val output = if (grouped) { group.queueInput(input); group.output } else reference(old, input)
                    checksum += output.get(0)
                    output.position(output.limit())
                }
                return System.nanoTime() - start
            }
            repeat(3) { run(false, 400); run(true, 400) }
            repeat(9) { round ->
                val blocks = 2000
                val first = run(round % 2 == 0, blocks)
                val second = run(round % 2 != 0, blocks)
                val oldNs = if (round % 2 == 0) second else first
                val newNs = if (round % 2 == 0) first else second
                println("${args[0]},$frames,$preset,$round,$oldNs,$newNs,$blocks,$oldCount,$newCount,$oldBytes,$newBytes")
            }
            // Keep both pipelines reachable through the last memory/time sample.
            check(old.size + fresh.size == 62)
        }
        java.lang.ref.Reference.reachabilityFence(retained)
        System.err.println("checksum=$checksum; JVM DSP pipeline only; not Android CPU/RAM/battery measurements")
    }
}
