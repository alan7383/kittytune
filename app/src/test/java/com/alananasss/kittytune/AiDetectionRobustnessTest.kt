package com.alananasss.kittytune

import com.alananasss.kittytune.audio.ai.AiDetectionAudioProcessor
import com.alananasss.kittytune.audio.ai.AiDetectionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import kotlin.math.roundToInt

class AiDetectionRobustnessTest {

    @Before
    fun setup() {
        AiDetectionManager.resetResult()
        AiDetectionManager.trackVerdictCache.clear()
        AiDetectionManager.trackScoreSamplesCache.clear()
    }

    @Test
    fun testDefaultThresholdMatchesDesktop() {
        assertEquals(0.85f, AiDetectionManager.DEFAULT_THRESHOLD, 0.001f)
    }

    @Test
    fun testIntroFalsePositiveProtection() {
        // Human track with synthetic intro: high intro score, low body scores
        val scores = listOf(0.78f, 0.12f, 0.15f, 0.08f)
        val verdict = AiDetectionManager.calculateVerdictFromScores(scores, threshold = 0.85f)

        assertFalse("Human track with synthetic intro must not be flagged as AI", verdict.isAi)
        assertEquals(AiDetectionManager.Status.DONE, verdict.status)
        // Score should be median of the points
        assertTrue("Verdict score should be low median", verdict.score < 0.20f)
    }

    @Test
    fun testLateOnsetAiDetection() {
        // AI track that starts with acoustic/clean human-like intro, then AI vocals/elements kick in
        val scores = listOf(0.05f, 0.88f, 0.91f, 0.85f)
        val verdict = AiDetectionManager.calculateVerdictFromScores(scores, threshold = 0.85f)

        assertTrue("Late-onset AI track must be identified as AI", verdict.isAi)
        assertEquals(AiDetectionManager.Status.DONE, verdict.status)
        assertTrue("Verdict score should reflect strong AI confidence", verdict.score >= 0.85f)
    }

    @Test
    fun testExtremeForensicScoreConfirmsAi() {
        // Single microscopic codec anomaly with extreme forensic certainty >= 0.95
        val scores = listOf(0.10f, 0.96f, 0.12f)
        val verdict = AiDetectionManager.calculateVerdictFromScores(scores, threshold = 0.85f)

        assertTrue("Extreme forensic score >= 0.95 must confirm AI", verdict.isAi)
        assertTrue(verdict.score >= 0.95f)
    }

    @Test
    fun testVocalTransitionAiDetection() {
        // 1 point >= 0.90 with elevated neighbor >= 0.35 (e.g. vocal transition build-up)
        val scores = listOf(0.20f, 0.91f, 0.40f, 0.15f)
        val verdict = AiDetectionManager.calculateVerdictFromScores(scores, threshold = 0.85f)

        assertTrue("Vocal transition with high peak and elevated neighbor must confirm AI", verdict.isAi)
    }

    @Test
    fun testPeakScoreRetentionNeverRegresses() {
        AiDetectionManager.resetResult()

        // Checkpoint 0: low intro score
        AiDetectionManager.recordCheckpointScore(0.02f)
        assertEquals(0.02f, AiDetectionManager.getPeakScore(), 0.001f)
        assertFalse(AiDetectionManager.isAiConfirmed())

        // Checkpoint 1: strong AI detection
        AiDetectionManager.recordCheckpointScore(0.95f)
        assertEquals(0.95f, AiDetectionManager.getPeakScore(), 0.001f)
        assertTrue(AiDetectionManager.isAiConfirmed())
        assertTrue(AiDetectionManager.result.value?.isAi == true)

        // Checkpoint 2: subsequent low score must NOT regress peak score or flip isAi back to false
        AiDetectionManager.recordCheckpointScore(0.10f)
        assertEquals(0.95f, AiDetectionManager.getPeakScore(), 0.001f)
        assertTrue(AiDetectionManager.isAiConfirmed())
        assertTrue(AiDetectionManager.result.value?.isAi == true)
    }

    @Test
    fun testVerdictLockingPreventsFluctuation() {
        AiDetectionManager.resetResult()

        // 2 checkpoints >= threshold confirm AI and lock the verdict
        AiDetectionManager.recordCheckpointScore(0.86f, threshold = 0.85f)
        AiDetectionManager.recordCheckpointScore(0.89f, threshold = 0.85f)

        assertTrue("Verdict must be locked as final", AiDetectionManager.isVerdictFinal())
        assertTrue(AiDetectionManager.isAiConfirmed())
        val lockedScore = AiDetectionManager.result.value?.score ?: 0f

        // Any further chunks must be ignored
        AiDetectionManager.recordCheckpointScore(0.01f, threshold = 0.85f)
        assertEquals(lockedScore, AiDetectionManager.result.value?.score ?: 0f, 0.001f)
        assertTrue(AiDetectionManager.result.value?.isAi == true)
    }

    @Test
    fun testConsecutiveAiCheckpointsConfirmAi() {
        AiDetectionManager.resetResult()

        AiDetectionManager.recordCheckpointScore(0.86f, threshold = 0.85f)
        assertFalse("Single chunk >= 0.85 (< 0.90) does not lock verdict", AiDetectionManager.isVerdictFinal())

        AiDetectionManager.recordCheckpointScore(0.87f, threshold = 0.85f)
        assertTrue("Consecutive elevated chunks must confirm AI", AiDetectionManager.isAiConfirmed())
        assertTrue(AiDetectionManager.isVerdictFinal())
    }

    @Test
    fun testCacheHitInstantVerdict() {
        val testTrackId = 424242L
        val cachedVerdict = AiDetectionManager.Result(score = 0.94f, isAi = true, status = AiDetectionManager.Status.DONE)
        AiDetectionManager.trackVerdictCache[testTrackId] = cachedVerdict

        // Change track to cached track
        AiDetectionManager.onTrackChanged(testTrackId)

        assertTrue(AiDetectionManager.isVerdictFinal())
        assertTrue(AiDetectionManager.isAiConfirmed())
        assertEquals(0.94f, AiDetectionManager.getPeakScore(), 0.001f)
        assertEquals(cachedVerdict, AiDetectionManager.result.value)
    }

    @Test
    fun testAudioProcessorNotifySeekReEnablesSampling() {
        AiDetectionManager.resetResult()
        val processor = AiDetectionAudioProcessor()
        processor.resetForNewTrack()

        assertFalse(processor.isAllCheckpointsDone())
        assertEquals(0.0, processor.getCurrentPositionSec(), 0.001)

        // Notify seek to 45 seconds
        processor.notifySeek(45_000L)
        assertEquals(45.0, processor.getCurrentPositionSec(), 0.001)
        assertFalse(processor.isAllCheckpointsDone())
    }

    @Test
    fun testVerdictNeverFluctuatesFromHumanToAiToAnotherScore() {
        AiDetectionManager.resetResult()
        AiDetectionManager.onTrackChanged(99999L)
        assertEquals(AiDetectionManager.Status.ANALYZING, AiDetectionManager.result.value?.status)

        // Checkpoint 0 (0..4s quiet intro): must NOT prematurely emit DONE "Human" that would later flip
        AiDetectionManager.recordCheckpointScore(0.04f, trackId = 99999L)
        assertEquals(
            "Single intro checkpoint must stay ANALYZING so badge never flips from Human to AI",
            AiDetectionManager.Status.ANALYZING,
            AiDetectionManager.result.value?.status
        )

        // Checkpoint 1 (4..8s): confirms Human and locks verdict as final
        AiDetectionManager.recordCheckpointScore(0.06f, trackId = 99999L)
        val lockedResult = AiDetectionManager.result.value
        assertNotNull(lockedResult)
        assertEquals(AiDetectionManager.Status.DONE, lockedResult!!.status)
        assertTrue("Verdict must be locked once DONE is published", AiDetectionManager.isVerdictFinal())
        assertFalse(lockedResult.isAi)

        // Subsequent checkpoints must NEVER change the score or verdict
        AiDetectionManager.recordCheckpointScore(0.88f, trackId = 99999L)
        assertEquals(lockedResult, AiDetectionManager.result.value)
    }

    @Test
    fun testHlsPlaylistParsingAndSunoAi86PercentParity() {
        // Build a 22-segment SoundCloud HLS playlist matching track 1791593689 (216.8s)
        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:7")
        sb.appendLine("#EXT-X-MAP:URI=\"https://cdn.example.com/init.mp4\"")
        for (i in 0 until 21) {
            sb.appendLine("#EXTINF:10.000,")
            sb.appendLine("https://cdn.example.com/seg_$i.m4s")
        }
        sb.appendLine("#EXTINF:6.836,")
        sb.appendLine("https://cdn.example.com/seg_21.m4s")

        val playlist = AiDetectionManager.parseHlsPlaylist("https://cdn.example.com/playlist.m3u8", sb.toString())
        assertEquals("https://cdn.example.com/init.mp4", playlist.initSegmentUrl)
        assertEquals(22, playlist.segments.size)
        assertEquals(216_836L, playlist.totalDurationMs)

        val ratios = listOf(0.10, 0.35, 0.60, 0.85)
        val selectedIndices = ratios.map { r ->
            playlist.startIndexFor((playlist.totalDurationMs * r).toLong())
        }
        assertEquals(listOf(2, 7, 13, 18), selectedIndices)

        // Exact ArtifactNet float scores from segments [2, 7, 13, 18] of Suno AI - Give Me Hope 2.0:
        val hlsScores = listOf(0.9563f, 0.4676f, 0.9967f, 0.9994f)
        val verdict = AiDetectionManager.calculateVerdictFromScores(hlsScores, threshold = 0.85f)
        assertTrue(verdict.isAi)
        assertEquals(86, (verdict.score * 100).roundToInt())

        // Also verify local 64kbps m4a bookend consensus scores:
        val localScores = listOf(0.7210f, 0.0274f, 0.0070f, 0.0003f, 0.0003f, 0.9963f)
        val localVerdict = AiDetectionManager.calculateVerdictFromScores(localScores, threshold = 0.85f)
        assertTrue(localVerdict.isAi)
        assertEquals(86, (localVerdict.score * 100).roundToInt())
    }
}
