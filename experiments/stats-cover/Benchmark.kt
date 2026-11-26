package com.alananasss.kittytune.data.stats

import kotlin.random.Random

object ArtistCoverBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val process = args.single().toInt()
        for (artists in listOf(100, 500)) {
            val tracks = (0 until artists * 10).map { it.toLong() to if (it % 17 == 0) null else "cover$it" }
                .shuffled(Random(143))
            val credits = List(artists) { artist -> List(100) { artist * 10L + it % 10 } }
            fun select(baseline: Boolean): List<String?> {
                if (baseline) return credits.map { entries ->
                    tracks.firstOrNull { track -> entries.any { it == track.first } }?.second
                }
                val index = ArtistCoverIndex(tracks)
                return credits.map { index.coverFor(it) }
            }
            check(select(true) == select(false))
            repeat(5) { select(true); select(false) }
            repeat(10) { sample ->
                for (baseline in if ((process + sample) % 2 == 0) listOf(true, false) else listOf(false, true)) {
                    val start = System.nanoTime()
                    val result = select(baseline)
                    val elapsed = System.nanoTime() - start
                    println("$process,$artists,$sample,$baseline,$elapsed,${result.hashCode()}")
                }
            }
        }
    }
}
