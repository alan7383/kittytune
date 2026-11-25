package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.data.LyricsMatcher
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Normalizer
import kotlin.random.Random

class LyricsRankingParityTest {
    private val target = LyricsMatcher.Target("Déjà Vu", "Beyoncé", 180_000,
        alternativeTitles = listOf("Deja Vu", "Beyoncé - Déjà Vu"),
        alternativeArtists = listOf("Beyonce", "Beyoncé feat. Jay-Z"))

    private fun hit(id: Int, name: String = "Déjà Vu", artist: String = "Beyoncé",
        duration: Double = 180.0, provider: String = "LRCLIB", word: Boolean = false,
        line: Boolean = true, instrumental: Boolean = false, noLyrics: Boolean = false) =
        UnifiedLyricResult(id.toString(), name, artist, null, duration, line, word,
            provider, instrumental = instrumental, noLyrics = noLyrics)

    @Test fun `normalization keeps the previous patterns and locale behavior`() {
        val inputs = listOf("Déjà Vu (Official Video)", "A [live] {demo}", "Artist feat. Guest",
            "İSTANBUL", "東京の夜", "Песня — Артист", "مرحبا بالعالم", "café\t déjà\nvu",
            "", "[](){}", "w. guest", "naïve prod. somebody", "😀 Music 123")
        inputs.forEach { assertEquals(it, legacyNormalize(it), LyricsMatcher.normalize(it)) }
    }

    @Test fun `closest duplicate and selected duplicate keep their previous precedence`() {
        val copies = listOf(hit(1, duration = 185.0), hit(2), hit(3, duration = 181.0))
        assertEquals(listOf(copies[1]), ManualLyricsRanking.merge(emptyList(), copies, target))
        assertEquals(listOf(copies[0]), ManualLyricsRanking.merge(emptyList(), copies, target, copies[0].key))
    }

    @Test fun `stable ties preserve provider arrival order and duplicate keys keep the first value`() {
        val first = hit(1, provider = "GENIUS")
        val second = hit(2, provider = "GENIUS")
        val changedCopy = first.copy(name = "Wrong song")
        assertEquals(listOf(first, second), ManualLyricsRanking.merge(listOf(first), listOf(changedCopy, second), target))
    }

    @Test fun `progressive batches match the old comparator for multilingual mixed results`() {
        val random = Random(6247)
        val titles = listOf("Déjà Vu", "Deja Vu (live)", "Wrong song", "Beyoncé - Déjà Vu", "東京", "", "Déjà Vu feat. Guest")
        val artists = listOf("Beyoncé", "Beyonce", "Uploader", "", "بيونسيه")
        val providers = listOf("LRCLIB", "GENIUS", "MUSIXMATCH", "lrclib", "CUSTOM")
        repeat(12) {
            val results = List(85) { index -> hit(index, titles.random(random), artists.random(random),
                listOf(0.0, 176.0, 180.0, 186.0, 220.0).random(random), providers.random(random),
                random.nextBoolean(), random.nextBoolean(), random.nextBoolean(), random.nextBoolean()) }
            var actual = emptyList<UnifiedLyricResult>()
            var expected = emptyList<UnifiedLyricResult>()
            results.chunked(17).forEach { batch ->
                val active = actual.firstOrNull()?.key
                expected = referenceMerge(expected, batch, target, active)
                actual = ManualLyricsRanking.merge(actual, batch, target, active)
                assertEquals(expected, actual)
            }
        }
    }

    @Test fun `empty inputs and unknown durations match previous behavior`() {
        assertEquals(emptyList<UnifiedLyricResult>(), ManualLyricsRanking.merge(emptyList(), emptyList(), target))
        val unknown = target.copy(durationMs = 0)
        val hits = listOf(hit(1, duration = 0.0), hit(2, provider = "GENIUS"),
            hit(3, instrumental = true, noLyrics = true), hit(4, name = "Other"))
        assertEquals(referenceMerge(emptyList(), hits, unknown), ManualLyricsRanking.merge(emptyList(), hits, unknown))
    }

    companion object {
        private fun legacyNormalize(raw: String): String {
            var text = raw.lowercase()
            text = text.replace(Regex("\\[.*?\\]|\\(.*?\\)|\\{.*?\\}"), " ")
            text = text.replace(Regex("(?i)\\b(feat|ft|featuring|prod|w)\\.?\\s.*$"), " ")
            text = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            text = text.replace(Regex("[^\\p{L}\\p{Nd}]+"), " ")
            return text.trim().replace(Regex("\\s+"), " ")
        }

        // Frozen comparison order from 8db1db4e, independent of the cached-key comparator.
        fun referenceMerge(current: List<UnifiedLyricResult>, incoming: List<UnifiedLyricResult>,
            target: LyricsMatcher.Target, activeKey: String? = null): List<UnifiedLyricResult> {
            fun tier(r: UnifiedLyricResult) = when {
                r.noLyrics -> 0
                r.hasWordSync -> 3
                r.hasLineSync -> 2
                else -> 1
            }
            fun about(r: UnifiedLyricResult) = LyricsMatcher.titleSimilarity(r.name, target) >= LyricsMatcher.CONFIDENT_MATCH
            fun confident(r: UnifiedLyricResult) = about(r) && LyricsMatcher.durationMatches(r.durationSec, target.durationMs)
            fun instrumental(r: UnifiedLyricResult) = r.instrumental && confident(r) &&
                LyricsMatcher.artistSimilarity(r.artistName, target) >= LyricsMatcher.CONFIDENT_MATCH
            fun rank(r: UnifiedLyricResult) = LyricsMatcher.rank(tier(r),
                LyricsMatcher.score(r.name, r.artistName, r.durationSec, target), LyricsMatcher.titleSimilarity(r.name, target))
            val targetSec = target.durationMs / 1000.0
            return (current + incoming).distinctBy { it.key }.groupBy {
                listOf(it.provider, LyricsMatcher.normalize(it.name), LyricsMatcher.normalize(it.artistName),
                    tier(it).toString(), it.instrumental.toString(),
                    if (it.provider.equals("LRCLIB", true)) "" else it.id).joinToString("|")
            }.values.map { copies ->
                copies.firstOrNull { it.key == activeKey } ?: copies.minBy {
                    if (it.durationSec <= 0.0 || targetSec <= 0.0) Double.MAX_VALUE / 2
                    else kotlin.math.abs(it.durationSec - targetSec)
                }
            }.sortedWith(compareByDescending<UnifiedLyricResult> { confident(it) }
                .thenByDescending { instrumental(it) }.thenByDescending { about(it) }
                .thenByDescending { tier(it) }.thenByDescending { rank(it) })
        }
    }
}
