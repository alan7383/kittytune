/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
package com.alananasss.kittytune.audio.automix

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * High-performance, zero-latency real-time DJ Stem Isolator AudioProcessor for ExoPlayer.
 *
 * Provides instantaneous 3-stem separation and kill/solo switches directly in the PCM stream:
 * 1. [Vocal Stem]: Mid/Side stereo separation matrix + speech bandpass (250 Hz - 3500 Hz).
 *    Levels: 0.0f (Instrumental / Vocal Mute), 1.0f (Normal), 2.0f (Acapella / Vocal Solo).
 * 2. [Bass Stem]: true 4th-order Linkwitz-Riley crossover at 160 Hz, run on L and R
 *    separately so bass that is not perfectly centred is caught too. The low band is
 *    scaled and added back, so level 0.0 is a real -24 dB/oct removal, not a shelf.
 *    Levels: 0.0f (Bass Kill / Sub Swap), 1.0f (Normal), 1.5f (Bass Boost).
 * 3. [Drum / Beat Stem]: Transient envelope detector and high-shelf snap filter.
 *    Levels: 0.0f (Beat Mute / Drum Kill), 1.0f (Normal), 1.5f (Drum Punch).
 *
 * Also supports automated drop stem cuts to eliminate beat clashing during phrase drops.
 */
class DjStemAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var vocalLevel: Float = 1.0f
        private set

    @Volatile
    var drumLevel: Float = 1.0f
        private set

    @Volatile
    var bassLevel: Float = 1.0f
        private set

    @Volatile
    var isEnabled: Boolean = false
        private set

    // Internal smoothed targets to eliminate audio pops / clicks
    private var currentVocal = 1.0f
    private var currentDrum = 1.0f
    private var currentBass = 1.0f

    // Sample rate and biquad filter coefficients
    private var sampleRate = 44100

    companion object {
        /** Bass/rest split point. Standard DJ-mixer low-band corner. */
        const val BASS_CROSSOVER_HZ = 160.0
    }

    /**
     * Bass crossover, one per channel. Running it on L and R rather than on the mono sum is
     * what makes a bass kill actually silent: the previous code filtered only mid, so any
     * stereo-spread low end passed through at full level even at bass = 0.
     */
    private var bassSplitL: LinkwitzRiley4? = null
    private var bassSplitR: LinkwitzRiley4? = null

    // Bandpass filter for Vocal Midrange (300 Hz - 3400 Hz)
    private var bp_b0 = 0f
    private var bp_b1 = 0f
    private var bp_b2 = 0f
    private var bp_a1 = 0f
    private var bp_a2 = 0f
    private var bp_x1 = 0f
    private var bp_x2 = 0f
    private var bp_y1 = 0f
    private var bp_y2 = 0f

    // High-pass filter for Highs / Hi-Hats / Transients (> 4000 Hz)
    private var hp_b0 = 0f
    private var hp_b1 = 0f
    private var hp_b2 = 0f
    private var hp_a1 = 0f
    private var hp_a2 = 0f
    private var hp_x1 = 0f
    private var hp_x2 = 0f
    private var hp_y1 = 0f
    private var hp_y2 = 0f

    // Transient follower for drum hit detection
    private var fastEnv = 0f
    private var slowEnv = 0f

    fun setStems(vocal: Float, drum: Float, bass: Float) {
        this.vocalLevel = vocal.coerceIn(0.0f, 2.5f)
        this.drumLevel = drum.coerceIn(0.0f, 2.0f)
        this.bassLevel = bass.coerceIn(0.0f, 2.0f)
        this.isEnabled = (abs(vocalLevel - 1.0f) > 0.01f ||
                          abs(drumLevel - 1.0f) > 0.01f ||
                          abs(bassLevel - 1.0f) > 0.01f)
    }

    /**
     * True while the smoothed levels have not yet reached the requested ones.
     *
     * Returning to neutral flips [isEnabled] off immediately, but the per-sample smoothing may
     * still be mid-ramp - on a bass swap `currentBass` is typically still near 0. Bypassing at
     * that moment snaps the level back within one buffer, which is a step discontinuity and
     * clicks. Processing continues until the ramp has actually arrived.
     */
    private val isSettling: Boolean
        get() = abs(currentVocal - vocalLevel) > 0.001f ||
                abs(currentDrum - drumLevel) > 0.001f ||
                abs(currentBass - bassLevel) > 0.001f

    fun setStemLevels(vocal: Float, drum: Float, bass: Float) = setStems(vocal, drum, bass)

    fun triggerDropBassCut() {
        setStems(vocal = vocalLevel, drum = drumLevel, bass = 0.0f)
    }

    fun resetStems() {
        setStems(1.0f, 1.0f, 1.0f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        // Filters are built BEFORE the Bit-Perfect early return: a DAC disconnecting
        // mid-stream re-enters queueInput while the sink reconfigures, and queueInput
        // dereferences bassSplitL/R - leaving them null here was an NPE in the
        // AudioTrack thread. Identity configuration for ANY PCM encoding while the DAC
        // owns the output keeps the chain format 1:1 for the byte passthrough.
        setupFilters(inputAudioFormat.sampleRate)
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.DJ_STEM)) {
            return inputAudioFormat
        }
        if (inputAudioFormat.encoding != androidx.media3.common.C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun onFlush() {
        // A flush arrives on seeks - including the phase-lock correction seeks performed
        // mid-transition while stems are active. Zeroing the LR4 and biquad memory then
        // snaps the running filter phase: a click. The state is kept while a stem
        // operation is in flight; a neutral processor keeps the clean-restart contract.
        if (!isEnabled && !isSettling) resetState()
    }

    override fun onReset() {
        resetState()
    }

    private fun resetState() {
        bassSplitL?.reset(); bassSplitR?.reset()
        bp_x1 = 0f; bp_x2 = 0f; bp_y1 = 0f; bp_y2 = 0f
        hp_x1 = 0f; hp_x2 = 0f; hp_y1 = 0f; hp_y2 = 0f
        fastEnv = 0f
        slowEnv = 0f
        currentVocal = vocalLevel
        currentDrum = drumLevel
        currentBass = bassLevel
    }

    private fun setupFilters(sr: Int) {
        sampleRate = if (sr > 0) sr else 44100
        val fs = sampleRate.toDouble()

        // 1. Bass crossover at 160 Hz, 4th-order Linkwitz-Riley, one instance per channel.
        bassSplitL = LinkwitzRiley4(sampleRate, BASS_CROSSOVER_HZ)
        bassSplitR = LinkwitzRiley4(sampleRate, BASS_CROSSOVER_HZ)

        // 2. Band-Pass Biquad for Vocal Speech Range (Center 1200 Hz, Q = 0.8)
        val f_bp = 1200.0
        val w0_bp = 2.0 * PI * f_bp / fs
        val cos_bp = cos(w0_bp)
        val sin_bp = sin(w0_bp)
        val alpha_bp = sin_bp / (2.0 * 0.8)
        val a0_bp = 1.0 + alpha_bp
        bp_b0 = (alpha_bp / a0_bp).toFloat()
        bp_b1 = 0f
        bp_b2 = (-alpha_bp / a0_bp).toFloat()
        bp_a1 = ((-2.0 * cos_bp) / a0_bp).toFloat()
        bp_a2 = ((1.0 - alpha_bp) / a0_bp).toFloat()

        // 3. High-Pass Biquad for Drums / High-Hat Snaps (> 4500 Hz, Q = 0.707)
        val f_hp = 4500.0
        val w0_hp = 2.0 * PI * f_hp / fs
        val cos_hp = cos(w0_hp)
        val sin_hp = sin(w0_hp)
        val alpha_hp = sin_hp / (2.0 * 0.7071)
        val a0_hp = 1.0 + alpha_hp
        hp_b0 = (((1.0 + cos_hp) / 2.0) / a0_hp).toFloat()
        hp_b1 = ((-(1.0 + cos_hp)) / a0_hp).toFloat()
        hp_b2 = (((1.0 + cos_hp) / 2.0) / a0_hp).toFloat()
        hp_a1 = ((-2.0 * cos_hp) / a0_hp).toFloat()
        hp_a2 = ((1.0 - alpha_hp) / a0_hp).toFloat()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        // Bit-Perfect on a USB DAC: bit-transparent copy - stems stay neutral and the
        // PCM bytes reach the DAC untouched. Per-buffer check, no per-sample cost.
        if (!com.alananasss.kittytune.audio.AudioRoutingMatrix.isRouted(com.alananasss.kittytune.audio.AudioStage.DJ_STEM)) {
            val passthrough = replaceOutputBuffer(remaining)
            passthrough.put(inputBuffer)
            passthrough.flip()
            return
        }

        if ((!isEnabled && !isSettling) || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)
        // One-pole level smoothing, sample-rate normalized: ~4.5 ms time constant at any
        // rate (the old fixed 0.005 halved the ramp time at 96 kHz and doubled the click
        // risk at 22.05 kHz).
        val smoothCoeff = (220.5f / sampleRate).coerceIn(0.0005f, 0.5f)
        // Transient-detector envelopes were tuned at 44.1 kHz (0.02 / 0.001 per sample); scale
        // the per-sample coefficients so the time constants (~1.1 ms / ~23 ms) hold at any rate.
        val fastEnvCoeff = (0.02f * 44100f / sampleRate).coerceIn(0.0005f, 0.5f)
        val slowEnvCoeff = (0.001f * 44100f / sampleRate).coerceIn(0.00002f, 0.5f)

        val splitL = bassSplitL ?: LinkwitzRiley4(sampleRate, 160.0).also { bassSplitL = it }
        val splitR = bassSplitR ?: LinkwitzRiley4(sampleRate, 160.0).also { bassSplitR = it }

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.short.toFloat()
            val inR = inputBuffer.short.toFloat()

            currentVocal += smoothCoeff * (vocalLevel - currentVocal)
            currentDrum += smoothCoeff * (drumLevel - currentDrum)
            currentBass += smoothCoeff * (bassLevel - currentBass)

            // Split the low band off each channel first. Everything downstream then works on
            // the high band only, so the bass level is the single owner of < 160 Hz - including
            // the side channel, which the old mid-only path left completely unfiltered.
            val lowL = splitL.low(inL)
            val lowR = splitR.low(inR)
            val highL = splitL.high(inL)
            val highR = splitR.high(inR)

            val mid = 0.5f * (highL + highR)
            val side = 0.5f * (highL - highR)

            val vocalBand = bp_b0 * mid + bp_b1 * bp_x1 + bp_b2 * bp_x2 - bp_a1 * bp_y1 - bp_a2 * bp_y2
            bp_x2 = bp_x1; bp_x1 = mid; bp_y2 = bp_y1; bp_y1 = vocalBand

            val highSig = hp_b0 * mid + hp_b1 * hp_x1 + hp_b2 * hp_x2 - hp_a1 * hp_y1 - hp_a2 * hp_y2
            hp_x2 = hp_x1; hp_x1 = mid; hp_y2 = hp_y1; hp_y1 = highSig

            val absHigh = abs(highSig)
            fastEnv += fastEnvCoeff * (absHigh - fastEnv)
            slowEnv += slowEnvCoeff * (absHigh - slowEnv)
            val isTransient = fastEnv > (slowEnv * 1.6f)
            val drumTransient = if (isTransient) highSig else (highSig * 0.35f)

            // mid no longer carries any bass, so the remainder is just what the vocal band left.
            val remainderMid = mid - vocalBand

            val newMid = (vocalBand * currentVocal) +
                         (drumTransient * (currentDrum - 1.0f)) +
                         (remainderMid * (0.5f + 0.5f * currentDrum))

            val newSide = side * (0.6f + 0.4f * currentVocal)

            // The low band is added back per channel at the bass level. At 0.0 this is a true
            // 24 dB/oct removal (-24.6 dB at 80 Hz), not the -2.8 dB the old shelf managed.
            var outL = newMid + newSide + lowL * currentBass
            var outR = newMid - newSide + lowR * currentBass

            // Full 16-bit range (asymmetric): the old ±32760 shaved hot-master peaks even when
            // the stems were still settling back to neutral.
            outL = max(-32768f, min(32767f, outL))
            outR = max(-32768f, min(32767f, outR))

            buffer.putShort(outL.toInt().toShort())
            buffer.putShort(outR.toInt().toShort())
        }

        // Drain a trailing partial frame (odd byte count or a lone channel): the DSP loop
        // above only consumes full stereo frames, and a non-empty inputBuffer handed back
        // to Media3 makes it spin in while(inputBuffer.hasRemaining()) forever.
        if (inputBuffer.hasRemaining()) {
            inputBuffer.position(inputBuffer.limit())
        }

        buffer.flip()
    }
}
