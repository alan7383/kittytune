package com.alananasss.kittytune.audio.normalization

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.alananasss.kittytune.ui.player.NormalizationLevel
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Volume normalization by track, first in the effect chain.
 *
 * Each track gets one gain — the target loudness minus the track's own — the way ReplayGain, Spotify
 * and SoundCloud Desktop do it, instead of a level that keeps chasing the music. A track whose
 * loudness is already known (see [LoudnessStore]) plays at its gain from its first sample; the
 * renderer tells this processor exactly where one track ends and the next begins
 * ([onTrackStarted]), so the switch lands on the boundary rather than a second or two into the new
 * track.
 *
 * A track met for the first time starts at the gain recent tracks needed and settles on its own
 * measured loudness within a few seconds: it comes down fast when it is louder than expected and goes
 * up slowly, then holds still so the music's own dynamics are left alone. What it measured is kept
 * for the next time.
 *
 * Its built-in limiter keeps any boost out of clipping; [SafetyLimiterProcessor] at the end of the
 * chain catches what the effects add on top.
 */
@UnstableApi
class TrackNormalizerProcessor : BaseAudioProcessor() {

    @Volatile private var enabled = false
    @Volatile private var targetLufs = NORMAL_TARGET_LUFS

    private var handle = 0L
    private var handleChannels = 0
    private var handleSampleRate = 0

    private var trackKey: String? = null
    private var known: LoudnessStore.Entry? = null
    private var appliedGainDb = 0f
    private var lastStoredSeconds = 0f
    /** True until the first buffer of the track has been processed: a known gain applies at once. */
    private var atTrackStart = true
    private var storeCheckSeconds = 0f
    private val stats = FloatArray(5)

    init {
        try {
            System.loadLibrary("kittytune_audio_dsp")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load kittytune_audio_dsp library", e)
        }
    }

    fun setParameters(enabled: Boolean, level: NormalizationLevel) {
        this.enabled = enabled
        targetLufs = when (level) {
            NormalizationLevel.QUIET -> -19f
            NormalizationLevel.NORMAL -> NORMAL_TARGET_LUFS
            NormalizationLevel.LOUD -> -11f
        }
    }

    /**
     * Called by the audio renderer, on the playback thread, right after the last buffer of the
     * previous track has entered the chain and before the first one of [mediaId]. Repeating the same
     * track keeps its measurement going.
     */
    fun onTrackStarted(mediaId: String?) {
        val key = LoudnessStore.keyForMediaId(mediaId)
        if (key != null && key == trackKey) return
        finishTrack()
        trackKey = key
        known = LoudnessStore.get(key)
        lastStoredSeconds = 0f
        atTrackStart = true
        storeCheckSeconds = 0f
        if (handle != 0L) nativeResetMeter(handle)
        Log.d(TAG, "Track $key: ${known?.let { "known ${"%.1f".format(it.lufs)} LUFS (${it.source})" } ?: "unknown, measuring live"}")
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat =
        // Anything but 16-bit PCM passes by untouched rather than failing the whole sink.
        if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET

    override fun onFlush() {
        val format = inputAudioFormat
        if (format.encoding != C.ENCODING_PCM_16BIT || format.channelCount <= 0) {
            destroyNative()
            return
        }
        if (handle == 0L || format.channelCount != handleChannels || format.sampleRate != handleSampleRate) {
            destroyNative()
            handle = nativeCreate(format.channelCount, format.sampleRate)
            handleChannels = format.channelCount
            handleSampleRate = format.sampleRate
        } else {
            // A seek: the gain and the measurement are the track's, not the position's.
            nativeFlush(handle)
        }
    }

    override fun onReset() {
        finishTrack()
        trackKey = null
        known = null
        destroyNative()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return
        val frameSize = inputAudioFormat.bytesPerFrame
        val frames = remaining / frameSize
        val output = replaceOutputBuffer(remaining)
        if (handle == 0L || frames == 0 || !inputBuffer.isDirect || !output.isDirect) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val seconds = frames.toFloat() / inputAudioFormat.sampleRate
        nativeGetStats(handle, stats)
        // A track met for the first time is measured in the background as soon as it starts; pick the
        // figure up the moment it is filed, so a drop later in the track is already accounted for.
        if (known == null) {
            storeCheckSeconds += seconds
            if (storeCheckSeconds >= 0.5f) {
                storeCheckSeconds = 0f
                known = LoudnessStore.get(trackKey)?.also {
                    Log.d(TAG, "Track $trackKey: loudness arrived while playing, ${"%.1f".format(it.lufs)} LUFS (${it.source})")
                }
            }
        }
        val startGainDb = appliedGainDb
        appliedGainDb = nextGainDb(seconds)
        atTrackStart = false

        nativeProcess(
            handle, inputBuffer, output, frames, inputBuffer.position(),
            /* measure = */ true,
            dbToLinear(startGainDb), dbToLinear(appliedGainDb),
            /* limit = */ enabled,
        )
        inputBuffer.position(inputBuffer.limit())
        output.position(frames * frameSize)
        output.flip()

        maybeStoreMeasurement()

        logSeconds += seconds
        if (com.alananasss.kittytune.BuildConfig.DEBUG && logSeconds >= 2f) {
            logSeconds = 0f
            Log.d(
                TAG,
                "track=$trackKey ${if (known != null) "known" else "live"} gain=${"%.1f".format(appliedGainDb)}dB " +
                    "M=${"%.1f".format(stats[0])} S=${"%.1f".format(stats[1])} I=${"%.1f".format(stats[2])} " +
                    "peak=${"%.1f".format(stats[3])} heard=${stats[4].toInt()}s enabled=$enabled",
            )
        }
    }

    private var logSeconds = 0f

    /** The gain for the stretch of audio about to be processed, moved from the last one. */
    private fun nextGainDb(seconds: Float): Float {
        if (!enabled) return approach(appliedGainDb, 0f, seconds, OFF_TAU_S)

        val momentary = stats[0]
        val shortTerm = stats[1]
        val integrated = stats[2]
        val peakDb = stats[3]
        val measured = stats[4]
        val entry = known

        if (entry != null) {
            var gain = gainFor(entry.lufs, entry.peakDb)
            // Safety net: something heard far louder than the figure on file says (a figure taken
            // from a quiet intro, say) is followed down at once, as if the track were unknown.
            val heard = listOf(integrated, shortTerm - 1f, momentary - 4f).filter { it.isFinite() }.maxOrNull()
            if (heard != null) {
                val heardGain = gainFor(heard, max(peakDb, entry.peakDb))
                if (heardGain < gain - 2f) {
                    val tau = if (heardGain < appliedGainDb - FAR_FALL_DB) FAST_FALL_TAU_S else FALL_TAU_S
                    return approach(appliedGainDb, heardGain, seconds, tau)
                }
            }
            // A partial figure is only corrected upward once most of the track has been heard: twenty
            // seconds into a track with a quiet intro, the live average has heard nothing but the intro.
            val refinable = entry.source == LoudnessStore.Source.ESTIMATE ||
                (entry.source == LoudnessStore.Source.MEASURED && entry.seconds < 90f)
            if (refinable && measured >= 90f && integrated.isFinite()) {
                val live = gainFor(integrated, max(peakDb, entry.peakDb))
                if (abs(live - gain) > 2f) gain = live
            }
            // At the start of a track, straight to it: the move happens across the first buffer, a
            // ramp of a few tens of milliseconds. A figure that arrived while the track was already
            // playing, or a return after the safety net, glides instead of jumping.
            if (atTrackStart || abs(gain - appliedGainDb) < 0.05f) return gain
            return approach(appliedGainDb, gain, seconds, if (measured >= 90f) REFINE_TAU_S else LATE_KNOWN_TAU_S)
        }

        // Unknown: judged on what has been heard of it so far. The track average alone lags far behind
        // a quiet intro turning into the drop, so the louder of it and the last few seconds (or the
        // last instant, with more margin) decides how far down to go — the drop is tamed in well under
        // a second instead of the average's ten.
        val prior = priorGainDb()
        val loudness = listOf(integrated, shortTerm - 1f, momentary - 4f).filter { it.isFinite() }.maxOrNull()
            ?: return approach(appliedGainDb, prior, seconds, RISE_TAU_S)
        val target = gainFor(loudness, peakDb)
        if (target < appliedGainDb - FAR_FALL_DB) return approach(appliedGainDb, target, seconds, FAST_FALL_TAU_S)
        if (target < appliedGainDb) return approach(appliedGainDb, target, seconds, FALL_TAU_S)
        // Going up is slow, and slower still once the track's level is settled, so quiet passages
        // stay quiet; a quiet track still reaches its level within seconds. If a quiet intro turns
        // into a drop, the fall above catches it in a fraction of a second.
        return approach(appliedGainDb, target, seconds, if (measured < SETTLE_S) RISE_TAU_S else SETTLED_RISE_TAU_S)
    }

    /** Target minus loudness, within bounds, never boosting a track's peaks far past the limiter. */
    private fun gainFor(lufs: Float, peakDb: Float): Float {
        var gain = (targetLufs - lufs).coerceIn(MAX_CUT_DB, MAX_BOOST_DB)
        if (gain > 0f && peakDb > -60f) {
            gain = min(gain, max(0f, LIMITER_CEILING_DB - peakDb + BOOST_INTO_LIMITER_DB))
        }
        return gain
    }

    /** Records what was measured of the track playing, at most every 30 s and when it ends. */
    private fun maybeStoreMeasurement() {
        val measured = stats[4]
        if (measured >= MIN_STORE_S && measured - lastStoredSeconds >= 30f) store()
    }

    private fun finishTrack() {
        if (handle != 0L && trackKey != null) {
            nativeGetStats(handle, stats)
            if (stats[4] >= MIN_STORE_S) store()
        }
        if (trackKey != null) rememberGain(appliedGainDb)
    }

    private fun store() {
        val integrated = stats[2]
        if (!integrated.isFinite()) return
        lastStoredSeconds = stats[4]
        LoudnessStore.put(
            trackKey,
            LoudnessStore.Entry(integrated, stats[3], stats[4], LoudnessStore.Source.MEASURED),
        )
    }

    private fun destroyNative() {
        if (handle != 0L) {
            nativeDestroy(handle)
            handle = 0L
        }
    }

    private fun approach(current: Float, target: Float, seconds: Float, tau: Float): Float =
        current + (target - current) * (1f - exp(-seconds / tau))

    private fun dbToLinear(db: Float): Float = 10f.pow(db / 20f)

    companion object {
        private const val TAG = "TrackNormalizer"

        private const val NORMAL_TARGET_LUFS = -14f
        private const val MAX_BOOST_DB = 10f
        private const val MAX_CUT_DB = -18f
        private const val LIMITER_CEILING_DB = -1f
        /** How far a boost may push a track's loudest peak into the limiter. */
        private const val BOOST_INTO_LIMITER_DB = 2f

        /** Coming down: a quarter of a second, so a loud start is tamed almost at once. */
        private const val FALL_TAU_S = 0.25f
        /** Coming down far — a drop after a quiet intro — is done at once rather than smoothly. */
        private const val FAST_FALL_TAU_S = 0.05f
        private const val FAR_FALL_DB = 3f
        /** Going up while the track is still being learnt. */
        private const val RISE_TAU_S = 4f
        /** Going up once the track's loudness is settled: slow enough that quiet passages stay quiet. */
        private const val SETTLED_RISE_TAU_S = 10f
        private const val SETTLE_S = 20f
        private const val REFINE_TAU_S = 4f
        /** A figure arriving while the track plays: a quick glide, never a jump. */
        private const val LATE_KNOWN_TAU_S = 1.5f
        private const val OFF_TAU_S = 0.1f

        private const val MIN_STORE_S = 30f

        /** What recent tracks needed, as the starting point for one never measured. Shared by both decks. */
        private val recentGains = ArrayDeque<Float>()
        private const val DEFAULT_PRIOR_DB = -5f

        @Synchronized
        private fun rememberGain(gainDb: Float) {
            recentGains.addLast(gainDb)
            while (recentGains.size > 8) recentGains.removeFirst()
        }

        @Synchronized
        private fun priorGainDb(): Float {
            if (recentGains.isEmpty()) return DEFAULT_PRIOR_DB
            val sorted = recentGains.sorted()
            return sorted[sorted.size / 2]
        }

        @JvmStatic private external fun nativeCreate(channels: Int, sampleRate: Int): Long
        @JvmStatic private external fun nativeDestroy(handle: Long)
        @JvmStatic private external fun nativeResetMeter(handle: Long)
        @JvmStatic private external fun nativeFlush(handle: Long)
        @JvmStatic private external fun nativeProcess(
            handle: Long,
            input: ByteBuffer,
            output: ByteBuffer,
            frames: Int,
            inOffset: Int,
            measure: Boolean,
            startGain: Float,
            endGain: Float,
            limit: Boolean,
        )
        @JvmStatic private external fun nativeGetStats(handle: Long, out: FloatArray)
    }
}
