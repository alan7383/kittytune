package com.alananasss.kittytune.data

object LikeBlacklistBenchmark {
    @JvmStatic fun main(args: Array<String>) {
        val process = args.single().toInt()
        for (size in listOf(500, 2000)) for (liked in listOf(false, true)) {
            val seed = (1..size).map { it.toString() }.toSet()
            val ids = if (liked) (1L..size.toLong()).toList() else (size.toLong() + 1..size.toLong() * 2).toList()
            fun apply(baseline: Boolean): Set<String> {
                var saved = seed
                if (baseline) for (id in ids) {
                    val current = saved.mapNotNull { it.toLongOrNull() }.toMutableSet()
                    if (liked) current.remove(id) else current.add(id)
                    saved = current.map { it.toString() }.toSet()
                } else updateLikeBlacklist(ids, liked, { saved }, { saved = it })
                return saved
            }
            check(apply(true) == apply(false))
            repeat(3) { apply(true); apply(false) }
            repeat(7) { sample ->
                for (baseline in if ((process + sample) % 2 == 0) listOf(true, false) else listOf(false, true)) {
                    val start = System.nanoTime()
                    val result = apply(baseline)
                    val elapsed = System.nanoTime() - start
                    println("$process,$size,$liked,$sample,$baseline,$elapsed,${result.hashCode()}")
                }
            }
        }
    }
}
