package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.BaselineLyricsMatcher
import kotlin.random.Random

object LyricsPerformanceBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val process = args.first().toInt()
        for (size in listOf(40, 150, 400)) {
            val random = Random(421)
            val names = listOf("Déjà Vu", "Deja Vu (Official Audio)", "Beyoncé - Déjà Vu", "Déjà Vu live", "Different Song")
            val artists = listOf("Beyoncé", "Beyonce", "Beyoncé feat. Jay-Z", "Uploader")
            val results = List(size) { n -> UnifiedLyricResult(n.toString(), names.random(random),
                artists.random(random), null, listOf(0.0, 176.0, 180.0, 186.0).random(random),
                random.nextBoolean(), random.nextBoolean(), listOf("LRCLIB", "GENIUS", "MUSIXMATCH").random(random),
                instrumental = n % 19 == 0, noLyrics = n % 23 == 0) }
            val target = LyricsMatcher.Target("Déjà Vu", "Beyoncé", 180_000,
                listOf("Beyoncé - Déjà Vu", "Deja Vu"), listOf("Beyonce"))
            val baselineTarget = BaselineLyricsMatcher.Target(target.title, target.artist, target.durationMs,
                target.alternativeTitles, target.alternativeArtists)
            val baseline = { BaselineLyricsRanking.merge(emptyList(), results, baselineTarget) }
            val candidate = { ManualLyricsRanking.merge(emptyList(), results, target) }
            check(baseline() == candidate()) { "Ranking differs for size $size" }
            repeat(10) { baseline(); candidate() }
            repeat(15) { sample ->
                val order = if ((process + sample) % 2 == 0) listOf("baseline", "candidate") else listOf("candidate", "baseline")
                for (variant in order) {
                    val start = System.nanoTime()
                    val ranked = if (variant == "baseline") baseline() else candidate()
                    val nanos = System.nanoTime() - start
                    println("$process,$size,$sample,$variant,$nanos,${ranked.size},${ranked.map { it.key }.hashCode()}")
                }
            }
        }
    }
}
