package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.data.LyricsMatcher
import com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider

data class UnifiedLyricResult(
    val id: String,
    val name: String,
    val artistName: String,
    val albumName: String?,
    val durationSec: Double,
    val hasLineSync: Boolean,
    val hasWordSync: Boolean,
    val provider: String,
    val rawContent: String? = null,
    val previewText: String? = null,
    val noLyrics: Boolean = false,
    val instrumental: Boolean = false,
) {
    val key: String get() = "${PreferredLyricsProvider.fromName(provider)?.name ?: provider}/$id"
}

internal object ManualLyricsRanking {
    private data class RankedResult(
        val result: UnifiedLyricResult,
        val confident: Boolean,
        val instrumental: Boolean,
        val aboutThisSong: Boolean,
        val tier: Int,
        val rank: Float,
    )

    fun merge(
        current: List<UnifiedLyricResult>,
        incoming: List<UnifiedLyricResult>,
        target: LyricsMatcher.Target,
        activeKey: String? = null,
    ): List<UnifiedLyricResult> {
        val targetSec = target.durationMs / 1000.0
        return (current + incoming)
            .distinctBy { it.key }
            .groupBy { duplicateKey(it) }
            .values
            .map { copies ->
                copies.firstOrNull { it.key == activeKey } ?: copies.minBy { lengthMiss(it, targetSec) }
            }
            // Word timings first, then line timings, then plain text, whoever the source is; within a kind the
            // closest to this song. An unrelated song never outranks the right one by its timings alone.
            .map { result ->
                RankedResult(result, isConfidentMatch(result, target),
                    isConfirmedInstrumental(result, target), isAboutThisSong(result, target),
                    syncTier(result), rank(result, target))
            }
            .sortedWith(compareByDescending<RankedResult> { it.confident }
                .thenByDescending { it.instrumental }.thenByDescending { it.aboutThisSong }
                .thenByDescending { it.tier }.thenByDescending { it.rank })
            .map { it.result }
    }

    fun syncTier(result: UnifiedLyricResult): Int = when {
        result.noLyrics -> LyricsMatcher.SYNC_TIER_NONE
        result.hasWordSync -> LyricsMatcher.SYNC_TIER_WORD
        result.hasLineSync -> LyricsMatcher.SYNC_TIER_LINE
        else -> LyricsMatcher.SYNC_TIER_PLAIN
    }

    private fun duplicateKey(result: UnifiedLyricResult): String =
        listOf(
            result.provider,
            LyricsMatcher.normalize(result.name),
            LyricsMatcher.normalize(result.artistName),
            syncTier(result).toString(),
            result.instrumental.toString(),
            if (result.provider.equals("LRCLIB", ignoreCase = true)) "" else result.id,
        ).joinToString("|")

    private fun lengthMiss(result: UnifiedLyricResult, targetSec: Double): Double =
        if (result.durationSec <= 0.0 || targetSec <= 0.0) Double.MAX_VALUE / 2
        else kotlin.math.abs(result.durationSec - targetSec)

    private fun isAboutThisSong(result: UnifiedLyricResult, target: LyricsMatcher.Target): Boolean =
        LyricsMatcher.titleSimilarity(result.name, target) >= LyricsMatcher.CONFIDENT_MATCH

    /** Sure enough to be used without asking: this song's title, and its length when both lengths are known. */
    fun isConfidentMatch(result: UnifiedLyricResult, target: LyricsMatcher.Target): Boolean {
        if (!isAboutThisSong(result, target)) return false
        return LyricsMatcher.durationMatches(result.durationSec, target.durationMs)
    }

    fun isConfirmedInstrumental(result: UnifiedLyricResult, target: LyricsMatcher.Target): Boolean =
        result.instrumental && isConfidentMatch(result, target) &&
            LyricsMatcher.artistSimilarity(result.artistName, target) >= LyricsMatcher.CONFIDENT_MATCH

    private fun rank(result: UnifiedLyricResult, target: LyricsMatcher.Target): Float {
        val score = LyricsMatcher.score(result.name, result.artistName, result.durationSec, target)
        val titleSimilarity = LyricsMatcher.titleSimilarity(result.name, target)
        return LyricsMatcher.rank(syncTier(result), score, titleSimilarity)
    }

}
