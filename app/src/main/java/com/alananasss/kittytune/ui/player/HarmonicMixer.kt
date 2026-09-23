/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.domain.Track
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Harmonic and tempo scoring engine for evaluating candidate tracks in DJ Flow mode.
 *
 * Scoring breakdown (0 to 100 points):
 * - Camelot Harmony (up to 40 pts): Favors identical, relative major/minor, and adjacent wheel keys.
 * - BPM Compatibility (up to 40 pts): Penalizes tempo discrepancies beyond +/- 8% tolerance.
 * - Energy Mode Alignment (up to 20 pts): Tailors candidate selection to BUILD_UP, HOLD, or WIND_DOWN.
 */
object HarmonicMixer {

    const val MAX_BPM_TOLERANCE_RATIO = 0.08f // +/- 8% standard DJ pitch fader range

    /**
     * Canonical BPM normalizer for modern electronic and dance music [90..180 BPM].
     * Folds sub-90 BPM detections into full time (e.g. 80.3 -> 160.6 BPM) and hyper-tempo > 180 BPM.
     */
    fun normalizeBpm(bpm: Float): Float {
        if (bpm <= 0f) return 0f
        var b = bpm
        while (b < 90f) b *= 2f
        while (b > 180f) b /= 2f
        return b
    }

    /**
     * Evaluates a single candidate track against the currently playing track.
     */
    fun scoreCandidate(
        currentBpm: Float,
        currentKey: CamelotKey?,
        candidateTrack: Track,
        candidateBpm: Float,
        candidateKey: CamelotKey?,
        mode: EnergyMode = EnergyMode.HOLD,
        mixInPointMs: Long? = null,
        mixOutPointMs: Long? = null
    ): TrackMatch {
        val hasBpmData = currentBpm > 0f && candidateBpm > 0f
        val hasKeyData = currentKey != null && candidateKey != null

        // --- 1. BPM Matching (0..40 pts) ---
        // Normalize both tempos into canonical electronic range [90..180] before comparison
        val normCurrentBpm = normalizeBpm(currentBpm)
        val normCandidateBpm = normalizeBpm(candidateBpm)

        val bestRatio = if (hasBpmData) normCandidateBpm / normCurrentBpm else 1.0f
        val bestDeltaPct = bestRatio - 1.0f

        val absDeltaPct = abs(bestDeltaPct)
        val bpmScore: Float = if (!hasBpmData) {
            20f // Neutral when BPM analysis is unavailable
        } else if (absDeltaPct <= MAX_BPM_TOLERANCE_RATIO) {
            // Smoothly reward within the allowed +/- 8% window
            40f * (1f - (absDeltaPct / MAX_BPM_TOLERANCE_RATIO) * 0.75f)
        } else {
            // Beyond +/- 8% is not permitted for seamless matching
            0f
        }

        // --- 2. Harmonic Key Scoring (0..40 pts) ---
        val harmonicDistance = if (currentKey != null && candidateKey != null) currentKey.distanceTo(candidateKey) else 3
        val isHarmonic = currentKey != null && candidateKey != null && currentKey.isHarmonicWith(candidateKey)

        val harmonicScore: Float = if (currentKey == null || candidateKey == null) {
            20f // Neutral when tonal analysis is unavailable
        } else {
            when {
                currentKey.isExactMatch(candidateKey) -> 40f
                currentKey.isRelativeMajorMinor(candidateKey) -> 38f
                currentKey.isAdjacentOnWheel(candidateKey) -> 36f
                harmonicDistance == 2 -> 24f
                harmonicDistance == 3 -> 10f
                else -> 0f
            }
        }

        // --- 3. Energy Mode Alignment (0..20 pts) ---
        val energyScore: Float = if (!hasBpmData) {
            10f
        } else {
            when (mode) {
                EnergyMode.BUILD_UP -> {
                    when {
                        bestDeltaPct in 0.005f..0.035f -> 20f // Ideal: +0.5% to +3.5% BPM boost
                        bestDeltaPct in 0.0f..0.005f -> 15f
                        bestDeltaPct in 0.035f..MAX_BPM_TOLERANCE_RATIO -> 12f
                        bestDeltaPct < 0f -> maxOf(0f, 8f + bestDeltaPct * 150f) // Penalize slowing down
                        else -> 0f
                    }
                }
                EnergyMode.HOLD -> {
                    when {
                        absDeltaPct <= 0.015f -> 20f // Ideal: near zero BPM difference
                        absDeltaPct <= 0.04f -> 15f
                        absDeltaPct <= MAX_BPM_TOLERANCE_RATIO -> maxOf(5f, 20f * (1f - (absDeltaPct / MAX_BPM_TOLERANCE_RATIO)))
                        else -> 0f
                    }
                }
                EnergyMode.WIND_DOWN -> {
                    when {
                        bestDeltaPct in -0.035f..-0.005f -> 20f // Ideal: -0.5% to -3.5% BPM reduction
                        bestDeltaPct in -0.005f..0.0f -> 15f
                        bestDeltaPct in -MAX_BPM_TOLERANCE_RATIO..-0.035f -> 12f
                        bestDeltaPct > 0f -> maxOf(0f, 8f - bestDeltaPct * 150f) // Penalize speeding up
                        else -> 0f
                    }
                }
            }
        }

        var totalScore = (bpmScore + harmonicScore + energyScore).coerceIn(0f, 100f)

        // Strictest enforcement: If tempo delta exceeds +/- 8%, the candidate cannot be seamlessly
        // beat-matched without unmusical time-stretching or trainwrecking.
        // Cap overall score to <= 20.0f so it never beats tempo-aligned tracks.
        if (hasBpmData && absDeltaPct > MAX_BPM_TOLERANCE_RATIO) {
            totalScore = minOf(totalScore * 0.25f, 20.0f)
        }

        val rawBpmDelta = if (hasBpmData) normCandidateBpm - normCurrentBpm else 0f

        // Construct human-readable explanation
        val descParts = mutableListOf<String>()
        if (hasKeyData) {
            when {
                currentKey.isExactMatch(candidateKey) -> descParts.add("Same Key (${candidateKey.code})")
                currentKey.isRelativeMajorMinor(candidateKey) -> descParts.add("Relative Key (${currentKey.code}→${candidateKey.code})")
                currentKey.isAdjacentOnWheel(candidateKey) -> descParts.add("Harmonic Wheel (${currentKey.code}→${candidateKey.code})")
                else -> descParts.add("Key Shift (${currentKey.code}→${candidateKey.code})")
            }
        } else if (candidateKey != null) {
            descParts.add("Key ${candidateKey.code}")
        }

        if (hasBpmData) {
            val deltaSign = if (rawBpmDelta >= 0f) "+" else ""
            val deltaFormatted = "%.1f".format(rawBpmDelta)
            descParts.add("$deltaSign$deltaFormatted BPM")
        }

        val explanation = if (descParts.isNotEmpty()) descParts.joinToString(" • ") else "Candidate match"

        // Dynamic energy estimation (1..10) based on tempo and tonal brightness
        val baseEnergy = if (candidateBpm > 0f) {
            val bpmFactor = ((candidateBpm - 70f) / 10f).coerceIn(1f, 8f)
            val keyBonus = if (candidateKey != null && !candidateKey.isMinor) 1.5f else 0.5f
            (bpmFactor + keyBonus).roundToInt().coerceIn(1, 10)
        } else {
            5
        }

        // Intelligently select transition archetype based on musical harmony and tempo delta
        val transitionStyle = when {
            harmonicDistance >= 3 || absDeltaPct > 0.05f -> TransitionStyle.POWER_SWAP
            harmonicDistance == 2 -> TransitionStyle.SMART_BASS_CROSS
            mode == EnergyMode.BUILD_UP && bestDeltaPct > 0.01f -> TransitionStyle.ENERGY_DROP_CUT
            harmonicDistance <= 1 && absDeltaPct <= 0.015f && mode == EnergyMode.HOLD -> TransitionStyle.HARMONIC_CLUB_BLEND
            else -> TransitionStyle.HARMONIC_CLUB_BLEND
        }

        return TrackMatch(
            track = candidateTrack,
            score = (totalScore * 10f).roundToInt() / 10f,
            bpm = candidateBpm,
            key = candidateKey,
            bpmDelta = rawBpmDelta,
            bpmPercentDelta = bestDeltaPct,
            harmonicDistance = harmonicDistance,
            isHarmonic = isHarmonic,
            matchDescription = explanation,
            mixInPointMs = mixInPointMs,
            mixOutPointMs = mixOutPointMs,
            energyLevel = baseEnergy,
            transitionStyle = transitionStyle
        )
    }

    /**
     * Ranks a collection of candidate tracks from highest to lowest compatibility.
     */
    fun rankCandidates(
        currentBpm: Float,
        currentKey: CamelotKey?,
        candidates: List<Triple<Track, Float, CamelotKey?>>,
        mode: EnergyMode = EnergyMode.HOLD
    ): List<TrackMatch> {
        return candidates.map { (track, bpm, key) ->
            scoreCandidate(
                currentBpm = currentBpm,
                currentKey = currentKey,
                candidateTrack = track,
                candidateBpm = bpm,
                candidateKey = key,
                mode = mode
            )
        }.sortedByDescending { it.score }
    }
}
