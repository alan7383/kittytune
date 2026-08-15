package com.alananasss.kittytune.ui.player.audio

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class EightDAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var time: Double = 0.0
    private var rotationSpeed: Double = 0.00001

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) time = 0.0
    }

    fun setSpeed(normalizedSpeed: Float) {
        rotationSpeed = (0.000002 + normalizedSpeed * 0.000038).coerceIn(0.000002, 0.00004)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    override fun onFlush() {
        if (enabled) {
            time = 0.0
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        while (inputBuffer.hasRemaining()) {

            if (inputAudioFormat.channelCount == 2) {

                val left = inputBuffer.getShort().toFloat()
                val right = inputBuffer.getShort().toFloat()

                time += rotationSpeed
                val pan = sin(time)

                val leftVol = (1.0 - pan) / 2.0
                val rightVol = (1.0 + pan) / 2.0

                val newLeft = (left * leftVol).toInt().toShort()
                val newRight = (right * rightVol).toInt().toShort()

                buffer.putShort(newLeft)
                buffer.putShort(newRight)
            } else {

                buffer.putShort(inputBuffer.getShort())
            }
        }
        buffer.flip()
    }
}

class FxAudioProcessor : BaseAudioProcessor() {

    private var isMuffled = false
    private var isBassBoost = false
    private var bassBoostGain = 10f
    private var muffledCutoff = 800f

    private var b0_m = 0f;
    private var b1_m = 0f;
    private var b2_m = 0f
    private var a1_m = 0f;
    private var a2_m = 0f

    private var x1_m = 0f;
    private var x2_m = 0f
    private var y1_m = 0f;
    private var y2_m = 0f

    private var b0_b = 0f;
    private var b1_b = 0f;
    private var b2_b = 0f
    private var a1_b = 0f;
    private var a2_b = 0f

    private var x1_b = 0f;
    private var x2_b = 0f
    private var y1_b = 0f;
    private var y2_b = 0f

    fun setEffects(muffled: Boolean, bassBoost: Boolean) {
        if (this.isMuffled != muffled || this.isBassBoost != bassBoost) {
            this.isMuffled = muffled
            this.isBassBoost = bassBoost

            resetStates()
            calculateCoefficients()
        }
    }

    fun setBassBoostGain(normalizedIntensity: Float) {
        val newGain = (4f + normalizedIntensity * 12f).coerceIn(4f, 64f)
        if (newGain != bassBoostGain) {
            bassBoostGain = newGain
            if (isBassBoost) {
                resetStates()
                calculateCoefficients()
            }
        }
    }

    fun setMuffledCutoff(normalizedIntensity: Float) {
        val newCutoff = (400f + normalizedIntensity * 1100f).coerceIn(400f, 1500f)
        if (newCutoff != muffledCutoff) {
            muffledCutoff = newCutoff
            if (isMuffled) {
                resetStates()
                calculateCoefficients()
            }
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        resetStates()
        calculateCoefficients()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetStates()
    }

    private fun resetStates() {
        x1_m = 0f; x2_m = 0f; y1_m = 0f; y2_m = 0f
        x1_b = 0f; x2_b = 0f; y1_b = 0f; y2_b = 0f
    }

    private fun calculateCoefficients() {
        val fs = inputAudioFormat.sampleRate.toFloat().coerceAtLeast(44100f)

        if (isMuffled) {
            val f0 = muffledCutoff
            val q = 0.707f
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val alpha = (sin(w0) / (2.0 * q)).toFloat()
            val cosW0 = cos(w0).toFloat()

            val a0 = 1f + alpha
            b0_m = ((1f - cosW0) / 2f) / a0
            b1_m = (1f - cosW0) / a0
            b2_m = ((1f - cosW0) / 2f) / a0
            a1_m = (-2f * cosW0) / a0
            a2_m = (1f - alpha) / a0
        }

        if (isBassBoost) {
            val f0 = 100f
            val gain = bassBoostGain
            val S = 1f
            val A = Math.pow(10.0, gain / 40.0).toFloat()
            val w0 = (2.0 * PI * f0 / fs).toFloat()
            val sinW0 = sin(w0).toFloat()
            val cosW0 = cos(w0).toFloat()
            val alpha = sinW0 / 2f * Math.sqrt(((A + 1f / A) * (1f / S - 1f) + 2f).toDouble()).toFloat()
            val beta = 2f * Math.sqrt(A.toDouble()).toFloat() * alpha

            val a0 = (A + 1f) + (A - 1f) * cosW0 + beta
            b0_b = (A * ((A + 1f) - (A - 1f) * cosW0 + beta)) / a0
            b1_b = (2f * A * ((A - 1f) - (A + 1f) * cosW0)) / a0
            b2_b = (A * ((A + 1f) - (A - 1f) * cosW0 - beta)) / a0
            a1_b = (-2f * ((A - 1f) + (A + 1f) * cosW0)) / a0
            a2_b = ((A + 1f) + (A - 1f) * cosW0 - beta) / a0
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!isMuffled && !isBassBoost) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        while (inputBuffer.hasRemaining()) {

            var x = inputBuffer.getShort().toFloat()

            if (isMuffled) {
                val y = b0_m * x + b1_m * x1_m + b2_m * x2_m - a1_m * y1_m - a2_m * y2_m

                x2_m = x1_m; x1_m = x
                y2_m = y1_m; y1_m = y

                x = y
            }

            if (isBassBoost) {
                val y = b0_b * x + b1_b * x1_b + b2_b * x2_b - a1_b * y1_b - a2_b * y2_b

                x2_b = x1_b; x1_b = x
                y2_b = y1_b; y1_b = y

                x = y
            }

            val out = x.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            buffer.putShort(out)
        }
        buffer.flip()
    }
}

class ReverbAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var buffer: ShortArray = ShortArray(0)
    private var cursor = 0

    private val delayMs = 150
    private var decay = 0.5f

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) {
            this.enabled = enabled

            if (!enabled) buffer = ShortArray(0)
        }
    }

    fun setDecay(normalizedIntensity: Float) {
        decay = (0.2f + normalizedIntensity * 0.6f).coerceIn(0.2f, 0.8f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {

        val bufferSize = (inputAudioFormat.sampleRate * (delayMs / 1000.0) * inputAudioFormat.channelCount).toInt()
        buffer = ShortArray(bufferSize)
        cursor = 0
        return inputAudioFormat
    }

    override fun onFlush() {
        cursor = 0
        if (buffer.isNotEmpty()) {
            buffer.fill(0)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val outputBuffer = replaceOutputBuffer(remaining)
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        val outputBuffer = replaceOutputBuffer(remaining)

        while (inputBuffer.hasRemaining()) {
            val inputSample = inputBuffer.getShort()

            val delayedSample = buffer[cursor]

            val outputSample =
                (inputSample + delayedSample * decay).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    .toShort()

            outputBuffer.putShort(outputSample)

            buffer[cursor] = outputSample

            cursor++
            if (cursor >= buffer.size) cursor = 0
        }
        outputBuffer.flip()
    }
}

class EarrapeAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var intensity = 1.0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    fun setIntensity(intensity: Float) {
        this.intensity = intensity.coerceIn(0.05f, 5.0f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val stage1Gain = (40f * intensity).coerceIn(2f, 300f)
        val stage2Gain = (20f * intensity).coerceIn(1f, 150f)
        val outputGain = if (intensity <= 1.0f) {
            0.25f + (1.0f - intensity) * 0.45f
        } else {
            (0.25f + (intensity - 1.0f) * 0.06f).coerceIn(0.25f, 0.55f)
        }

        while (inputBuffer.hasRemaining()) {
            val inputSample = inputBuffer.getShort().toFloat()

            var s = inputSample * stage1Gain
            s = s.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())

            s *= stage2Gain
            s = s.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat())

            val outputSample =
                (s * outputGain).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outputSample)
        }
        buffer.flip()
    }
}

class MonoAudioProcessor : BaseAudioProcessor() {
    private var enabled = false

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        while (inputBuffer.remaining() >= 4) {
            val left = inputBuffer.getShort().toInt()
            val right = inputBuffer.getShort().toInt()

            val mixed = ((left + right) / 2).toShort()

            buffer.putShort(mixed)
            buffer.putShort(mixed)
        }
        buffer.flip()
    }
}

class R128AudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var level = com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL
    private var nativeHandle: Long = 0
    private var lastChannels = 0
    private var lastSampleRate = 0

    init {
        System.loadLibrary("kittytune_audio_dsp")
    }

    fun setParameters(enabled: Boolean, level: com.alananasss.kittytune.ui.player.NormalizationLevel) {
        this.enabled = enabled
        this.level = level
        if (nativeHandle != 0L) {
            nativeSetTargetLevel(nativeHandle, getTargetLufs())
        }
    }

    private fun getTargetLufs(): Float = when (level) {
        com.alananasss.kittytune.ui.player.NormalizationLevel.QUIET -> -19f
        com.alananasss.kittytune.ui.player.NormalizationLevel.NORMAL -> -14f
        com.alananasss.kittytune.ui.player.NormalizationLevel.LOUD -> -11f
    }

    private fun ensureNativeHandle(channels: Int, sampleRate: Int) {
        if (nativeHandle == 0L || channels != lastChannels || sampleRate != lastSampleRate) {
            destroyNative()
            lastChannels = channels
            lastSampleRate = sampleRate
            nativeHandle = nativeInit(channels, sampleRate)
            nativeSetTargetLevel(nativeHandle, getTargetLufs())
        }
    }

    private fun destroyNative() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        ensureNativeHandle(inputAudioFormat.channelCount, inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun onFlush() {
        if (nativeHandle != 0L) {
            nativeResetLoudness(nativeHandle)
        }
    }

    override fun onReset() {
        destroyNative()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || nativeHandle == 0L) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val numFrames = remaining / (inputAudioFormat.channelCount * 2)

        if (inputBuffer.isDirect && buffer.isDirect && numFrames > 0) {

            val inOffset = inputBuffer.position()
            nativeProcessShort(nativeHandle, inputBuffer, buffer, numFrames, inOffset)
            inputBuffer.position(inputBuffer.limit())
            buffer.position(remaining)
            buffer.flip()
        } else {

            buffer.put(inputBuffer)
            buffer.flip()
        }
    }

    fun getIntegratedLoudness(): Float {
        if (nativeHandle == 0L) return -70f
        return nativeGetIntegratedLoudness(nativeHandle)
    }

    fun getMaxTruePeakDb(): Float {
        if (nativeHandle == 0L) return -120f
        return nativeGetTruePeakDb(nativeHandle)
    }

    private external fun nativeInit(channels: Int, sampleRate: Int): Long
    private external fun nativeDestroy(handle: Long)
    private external fun nativeSetTargetLevel(handle: Long, targetLUFS: Float)
    private external fun nativeProcessShort(
        handle: Long,
        inputBuffer: ByteBuffer,
        outputBuffer: ByteBuffer,
        numFrames: Int,
        inOffset: Int
    )

    private external fun nativeResetLoudness(handle: Long)
    private external fun nativeGetShortTermLoudness(handle: Long): Float
    private external fun nativeGetIntegratedLoudness(handle: Long): Float
    private external fun nativeGetTruePeakDb(handle: Long): Float
    private external fun nativeGetCurrentGainDb(handle: Long): Float
}

class VintageMp3AudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var compression = 0.5f

    private var sampleCounter = 0
    private var heldLeft: Short = 0
    private var heldRight: Short = 0

    private var lpLeft = 0f
    private var lpRight = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            sampleCounter = 0
            heldLeft = 0
            heldRight = 0
            lpLeft = 0f
            lpRight = 0f
        }
    }

    fun setCompression(compression: Float) {
        this.compression = compression.coerceIn(0.0f, 1.0f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    override fun onFlush() {
        sampleCounter = 0
        heldLeft = 0
        heldRight = 0
        lpLeft = 0f
        lpRight = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val holdFactor = (1 + (compression * 9.0f).toInt()).coerceIn(1, 10)

        val bitDepth = (12.0f - compression * 8.5f).coerceIn(3.5f, 12.0f)
        val step = (1 shl (16 - bitDepth.toInt())).coerceAtLeast(1)

        val lpAlpha = (0.85f - compression * 0.65f).coerceIn(0.18f, 0.90f)

        val channelCount = inputAudioFormat.channelCount

        if (channelCount == 2) {
            while (inputBuffer.remaining() >= 4) {
                var left = inputBuffer.getShort().toFloat()
                var right = inputBuffer.getShort().toFloat()

                lpLeft += lpAlpha * (left - lpLeft)
                lpRight += lpAlpha * (right - lpRight)

                left = lpLeft
                right = lpRight

                if (sampleCounter % holdFactor == 0) {

                    val qLeft = (((left + 32768f) / step).toInt() * step - 32768).coerceIn(-32768, 32767)
                    val qRight = (((right + 32768f) / step).toInt() * step - 32768).coerceIn(-32768, 32767)

                    heldLeft = qLeft.toShort()
                    heldRight = qRight.toShort()
                }
                sampleCounter++

                buffer.putShort(heldLeft)
                buffer.putShort(heldRight)
            }
        } else {

            while (inputBuffer.remaining() >= 2) {
                var sample = inputBuffer.getShort().toFloat()

                lpLeft += lpAlpha * (sample - lpLeft)
                sample = lpLeft

                if (sampleCounter % holdFactor == 0) {
                    val qSample = (((sample + 32768f) / step).toInt() * step - 32768).coerceIn(-32768, 32767)
                    heldLeft = qSample.toShort()
                }
                sampleCounter++

                buffer.putShort(heldLeft)
            }
        }
        buffer.flip()
    }
}

class VocalRemoverAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var suppressionLevel = 1.0f

    private var sampleRate = 44100

    private var lp_b0 = 0f;
    private var lp_b1 = 0f;
    private var lp_b2 = 0f
    private var lp_a1 = 0f;
    private var lp_a2 = 0f

    private var lp_x1_1 = 0f;
    private var lp_x2_1 = 0f;
    private var lp_y1_1 = 0f;
    private var lp_y2_1 = 0f
    private var lp_x1_2 = 0f;
    private var lp_x2_2 = 0f;
    private var lp_y1_2 = 0f;
    private var lp_y2_2 = 0f

    private var n1_b0 = 0f;
    private var n1_b1 = 0f;
    private var n1_b2 = 0f
    private var n1_a1 = 0f;
    private var n1_a2 = 0f
    private var n1_x1 = 0f;
    private var n1_x2 = 0f;
    private var n1_y1 = 0f;
    private var n1_y2 = 0f

    private var n2_b0 = 0f;
    private var n2_b1 = 0f;
    private var n2_b2 = 0f
    private var n2_a1 = 0f;
    private var n2_a2 = 0f
    private var n2_x1 = 0f;
    private var n2_x2 = 0f;
    private var n2_y1 = 0f;
    private var n2_y2 = 0f

    private var n3_b0 = 0f;
    private var n3_b1 = 0f;
    private var n3_b2 = 0f
    private var n3_a1 = 0f;
    private var n3_a2 = 0f
    private var n3_x1 = 0f;
    private var n3_x2 = 0f;
    private var n3_y1 = 0f;
    private var n3_y2 = 0f

    private var ap_a1 = 0f;
    private var ap_a3 = 0f;
    private var ap_a5 = 0f
    private var ap_x_a1 = 0f;
    private var ap_y_a1 = 0f
    private var ap_x_a3 = 0f;
    private var ap_y_a3 = 0f
    private var ap_x_a5 = 0f;
    private var ap_y_a5 = 0f

    private var ap_a2 = 0f;
    private var ap_a4 = 0f;
    private var ap_a6 = 0f
    private var ap_x_b2 = 0f;
    private var ap_y_b2 = 0f
    private var ap_x_b4 = 0f;
    private var ap_y_b4 = 0f
    private var ap_x_b6 = 0f;
    private var ap_y_b6 = 0f

    private var envL = 100f
    private var envR = 100f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetFilterState()
    }

    fun setSuppressionLevel(level: Float) {
        this.suppressionLevel = level.coerceIn(0.0f, 1.0f)
    }

    private fun calcAllpassAlpha(fc: Double, fs: Double): Float {
        val tanW = kotlin.math.tan(PI * fc / fs)
        return ((tanW - 1.0) / (tanW + 1.0)).toFloat()
    }

    private fun computeNotch(freq: Double, q: Double, fs: Double): FloatArray {
        val omega = 2.0 * PI * freq / fs
        val sinO = sin(omega)
        val cosO = cos(omega)
        val alpha = sinO / (2.0 * q)
        val a0 = 1.0 + alpha
        return floatArrayOf(
            (1.0 / a0).toFloat(),
            ((-2.0 * cosO) / a0).toFloat(),
            (1.0 / a0).toFloat(),
            ((-2.0 * cosO) / a0).toFloat(),
            ((1.0 - alpha) / a0).toFloat()
        )
    }

    private fun updateCoefficients(sr: Int) {
        sampleRate = sr
        val fs = sr.toDouble()

        val omegaL = 2.0 * PI * 75.0 / fs
        val sinL = sin(omegaL)
        val cosL = cos(omegaL)
        val alphaL = sinL / (2.0 * 0.70710678)
        val a0_L = 1.0 + alphaL

        lp_b0 = (((1.0 - cosL) / 2.0) / a0_L).toFloat()
        lp_b1 = ((1.0 - cosL) / a0_L).toFloat()
        lp_b2 = (((1.0 - cosL) / 2.0) / a0_L).toFloat()
        lp_a1 = ((-2.0 * cosL) / a0_L).toFloat()
        lp_a2 = ((1.0 - alphaL) / a0_L).toFloat()

        val c1 = computeNotch(1800.0, 0.8, fs)
        n1_b0 = c1[0]; n1_b1 = c1[1]; n1_b2 = c1[2]; n1_a1 = c1[3]; n1_a2 = c1[4]

        val c2 = computeNotch(3200.0, 0.6, fs)
        n2_b0 = c2[0]; n2_b1 = c2[1]; n2_b2 = c2[2]; n2_a1 = c2[3]; n2_a2 = c2[4]

        val c3 = computeNotch(5500.0, 0.7, fs)
        n3_b0 = c3[0]; n3_b1 = c3[1]; n3_b2 = c3[2]; n3_a1 = c3[3]; n3_a2 = c3[4]

        ap_a1 = calcAllpassAlpha(150.0, fs)
        ap_a3 = calcAllpassAlpha(1250.0, fs)
        ap_a5 = calcAllpassAlpha(9800.0, fs)

        ap_a2 = calcAllpassAlpha(430.0, fs)
        ap_a4 = calcAllpassAlpha(3500.0, fs)
        ap_a6 = calcAllpassAlpha(16000.0, fs)
    }

    private fun resetFilterState() {
        lp_x1_1 = 0f; lp_x2_1 = 0f; lp_y1_1 = 0f; lp_y2_1 = 0f
        lp_x1_2 = 0f; lp_x2_2 = 0f; lp_y1_2 = 0f; lp_y2_2 = 0f
        n1_x1 = 0f; n1_x2 = 0f; n1_y1 = 0f; n1_y2 = 0f
        n2_x1 = 0f; n2_x2 = 0f; n2_y1 = 0f; n2_y2 = 0f
        n3_x1 = 0f; n3_x2 = 0f; n3_y1 = 0f; n3_y2 = 0f
        ap_x_a1 = 0f; ap_y_a1 = 0f
        ap_x_a3 = 0f; ap_y_a3 = 0f
        ap_x_a5 = 0f; ap_y_a5 = 0f
        ap_x_b2 = 0f; ap_y_b2 = 0f
        ap_x_b4 = 0f; ap_y_b4 = 0f
        ap_x_b6 = 0f; ap_y_b6 = 0f
        envL = 100f
        envR = 100f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        updateCoefficients(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun onFlush() {
        resetFilterState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)
        val makeupGain = 1.0f + 1.15f * suppressionLevel
        val notchBlend = (suppressionLevel * 0.55f).coerceIn(0f, 0.55f)

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val absL = kotlin.math.abs(inL)
            val absR = kotlin.math.abs(inR)
            envL += 0.0003f * (absL - envL)
            envR += 0.0003f * (absR - envR)
            val balance = (envL / (envR + 1e-4f)).coerceIn(0.75f, 1.33f)

            val mid = 0.5f * (inL + inR)
            val lp1 = lp_b0 * mid + lp_b1 * lp_x1_1 + lp_b2 * lp_x2_1 - lp_a1 * lp_y1_1 - lp_a2 * lp_y2_1
            lp_x2_1 = lp_x1_1; lp_x1_1 = mid; lp_y2_1 = lp_y1_1; lp_y1_1 = lp1

            val subBass = lp_b0 * lp1 + lp_b1 * lp_x1_2 + lp_b2 * lp_x2_2 - lp_a1 * lp_y1_2 - lp_a2 * lp_y2_2
            lp_x2_2 = lp_x1_2; lp_x1_2 = lp1; lp_y2_2 = lp_y1_2; lp_y1_2 = subBass

            var side = 0.5f * (inL - inR * balance)

            val out1 = n1_b0 * side + n1_b1 * n1_x1 + n1_b2 * n1_x2 - n1_a1 * n1_y1 - n1_a2 * n1_y2
            n1_x2 = n1_x1; n1_x1 = side; n1_y2 = n1_y1; n1_y1 = out1

            val out2 = n2_b0 * side + n2_b1 * n2_x1 + n2_b2 * n2_x2 - n2_a1 * n2_y1 - n2_a2 * n2_y2
            n2_x2 = n2_x1; n2_x1 = side; n2_y2 = n2_y1; n2_y1 = out2

            val out3 = n3_b0 * side + n3_b1 * n3_x1 + n3_b2 * n3_x2 - n3_a1 * n3_y1 - n3_a2 * n3_y2
            n3_x2 = n3_x1; n3_x1 = side; n3_y2 = n3_y1; n3_y1 = out3

            val notched = out1 * 0.33f + out2 * 0.33f + out3 * 0.34f
            side = side * (1f - notchBlend) + notched * notchBlend

            val y_a1 = ap_a1 * (side - ap_y_a1) + ap_x_a1; ap_x_a1 = side; ap_y_a1 = y_a1
            val y_a3 = ap_a3 * (y_a1 - ap_y_a3) + ap_x_a3; ap_x_a3 = y_a1; ap_y_a3 = y_a3
            val sideA = ap_a5 * (y_a3 - ap_y_a5) + ap_x_a5; ap_x_a5 = y_a3; ap_y_a5 = sideA

            val y_b2 = ap_a2 * (side - ap_y_b2) + ap_x_b2; ap_x_b2 = side; ap_y_b2 = y_b2
            val y_b4 = ap_a4 * (y_b2 - ap_y_b4) + ap_x_b4; ap_x_b4 = y_b2; ap_y_b4 = y_b4
            val sideB = ap_a6 * (y_b4 - ap_y_b6) + ap_x_b6; ap_x_b6 = y_b4; ap_y_b6 = sideB

            val cleanL = subBass + sideA * makeupGain
            val cleanR = subBass + sideB * makeupGain

            val outL = ((inL * (1f - suppressionLevel)) + cleanL * suppressionLevel)
                .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = ((inR * (1f - suppressionLevel)) + cleanR * suppressionLevel)
                .coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class VocalBoostAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var intensity = 0.75f

    private var sampleRate = 44100

    private var hp_b0 = 0f;
    private var hp_b1 = 0f;
    private var hp_b2 = 0f
    private var hp_a1 = 0f;
    private var hp_a2 = 0f
    private var hp_x1 = 0f;
    private var hp_x2 = 0f;
    private var hp_y1 = 0f;
    private var hp_y2 = 0f

    private var peak_b0 = 0f;
    private var peak_b1 = 0f;
    private var peak_b2 = 0f
    private var peak_a1 = 0f;
    private var peak_a2 = 0f
    private var peak_x1 = 0f;
    private var peak_x2 = 0f;
    private var peak_y1 = 0f;
    private var peak_y2 = 0f

    private var hs_b0 = 0f;
    private var hs_b1 = 0f;
    private var hs_b2 = 0f
    private var hs_a1 = 0f;
    private var hs_a2 = 0f
    private var hs_x1 = 0f;
    private var hs_x2 = 0f;
    private var hs_y1 = 0f;
    private var hs_y2 = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetFilterState()
    }

    fun setIntensity(intensity: Float) {
        this.intensity = intensity.coerceIn(0.0f, 1.0f)
        updateCoefficients(sampleRate)
    }

    private fun updateCoefficients(sr: Int) {
        sampleRate = sr
        val fs = sr.toDouble()

        val omegaH = 2.0 * PI * 110.0 / fs
        val sinH = sin(omegaH)
        val cosH = cos(omegaH)
        val alphaH = sinH / (2.0 * 0.70710678)
        val a0_H = 1.0 + alphaH

        hp_b0 = (((1.0 + cosH) / 2.0) / a0_H).toFloat()
        hp_b1 = ((-(1.0 + cosH)) / a0_H).toFloat()
        hp_b2 = (((1.0 + cosH) / 2.0) / a0_H).toFloat()
        hp_a1 = ((-2.0 * cosH) / a0_H).toFloat()
        hp_a2 = ((1.0 - alphaH) / a0_H).toFloat()

        val peakGainDb = 3.0 + 4.5 * intensity.toDouble()
        val A_peak = 10.0.pow(peakGainDb / 40.0)
        val omegaP = 2.0 * PI * 2800.0 / fs
        val sinP = sin(omegaP)
        val cosP = cos(omegaP)
        val alphaP = sinP / (2.0 * 1.1)
        val a0_P = 1.0 + alphaP / A_peak

        peak_b0 = ((1.0 + alphaP * A_peak) / a0_P).toFloat()
        peak_b1 = ((-2.0 * cosP) / a0_P).toFloat()
        peak_b2 = ((1.0 - alphaP * A_peak) / a0_P).toFloat()
        peak_a1 = ((-2.0 * cosP) / a0_P).toFloat()
        peak_a2 = ((1.0 - alphaP / A_peak) / a0_P).toFloat()

        val hsGainDb = 1.5 + 2.5 * intensity.toDouble()
        val A_hs = 10.0.pow(hsGainDb / 40.0)
        val omegaS = 2.0 * PI * 5500.0 / fs
        val sinS = sin(omegaS)
        val cosS = cos(omegaS)
        val alphaS = sinS / (2.0 * 0.7071)
        val sqrtA_hs = sqrt(A_hs)
        val a0_S = (A_hs + 1.0) - (A_hs - 1.0) * cosS + 2.0 * sqrtA_hs * alphaS

        hs_b0 = ((A_hs * ((A_hs + 1.0) + (A_hs - 1.0) * cosS + 2.0 * sqrtA_hs * alphaS)) / a0_S).toFloat()
        hs_b1 = ((-2.0 * A_hs * ((A_hs - 1.0) + (A_hs + 1.0) * cosS)) / a0_S).toFloat()
        hs_b2 = ((A_hs * ((A_hs + 1.0) + (A_hs - 1.0) * cosS - 2.0 * sqrtA_hs * alphaS)) / a0_S).toFloat()
        hs_a1 = ((2.0 * ((A_hs - 1.0) - (A_hs + 1.0) * cosS)) / a0_S).toFloat()
        hs_a2 = (((A_hs + 1.0) - (A_hs - 1.0) * cosS - 2.0 * sqrtA_hs * alphaS) / a0_S).toFloat()
    }

    private fun resetFilterState() {
        hp_x1 = 0f; hp_x2 = 0f; hp_y1 = 0f; hp_y2 = 0f
        peak_x1 = 0f; peak_x2 = 0f; peak_y1 = 0f; peak_y2 = 0f
        hs_x1 = 0f; hs_x2 = 0f; hs_y1 = 0f; hs_y2 = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        updateCoefficients(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun onFlush() {
        resetFilterState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val sideGain = 1.0f - (intensity * 0.65f)

        val centerGain = 1.0f + (intensity * 0.40f)

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val mid = 0.5f * (inL + inR)
            val side = 0.5f * (inL - inR)

            val hpOut = hp_b0 * mid + hp_b1 * hp_x1 + hp_b2 * hp_x2 - hp_a1 * hp_y1 - hp_a2 * hp_y2
            hp_x2 = hp_x1; hp_x1 = mid; hp_y2 = hp_y1; hp_y1 = hpOut

            val peakOut =
                peak_b0 * hpOut + peak_b1 * peak_x1 + peak_b2 * peak_x2 - peak_a1 * peak_y1 - peak_a2 * peak_y2
            peak_x2 = peak_x1; peak_x1 = hpOut; peak_y2 = peak_y1; peak_y1 = peakOut

            val hsOut = hs_b0 * peakOut + hs_b1 * hs_x1 + hs_b2 * hs_x2 - hs_a1 * hs_y1 - hs_a2 * hs_y2
            hs_x2 = hs_x1; hs_x1 = peakOut; hs_y2 = hs_y1; hs_y1 = hsOut

            val boostedMid = hsOut * centerGain

            val attenuatedSide = side * sideGain

            val cleanL = boostedMid + attenuatedSide
            val cleanR = boostedMid - attenuatedSide

            val outL = cleanL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = cleanR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class FlangerAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var intensity = 0.75f
    private var speed = 0.50f

    private var sampleRate = 44100
    private val bufferSize = 4096
    private val bufferMask = bufferSize - 1

    private var delayBufferL = FloatArray(bufferSize)
    private var delayBufferR = FloatArray(bufferSize)
    private var writePos = 0

    private var lfoPhase = 0.0

    private var fbHpfL_x = 0f
    private var fbHpfL_y = 0f
    private var fbHpfR_x = 0f
    private var fbHpfR_y = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetFilterState()
    }

    fun setIntensity(intensity: Float) {
        this.intensity = intensity.coerceIn(0.0f, 1.0f)
    }

    fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.0f, 1.0f)
    }

    private fun resetFilterState() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        writePos = 0
        lfoPhase = 0.0
        fbHpfL_x = 0f
        fbHpfL_y = 0f
        fbHpfR_x = 0f
        fbHpfR_y = 0f
    }

    private fun softSaturate(x: Float): Float {
        val norm = x / 24000f
        val sat = norm / (1.0f + kotlin.math.abs(norm))
        return sat * 24000f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetFilterState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetFilterState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val lfoRateHz = 0.05 + 0.85 * speed.toDouble()
        val lfoInc = 2.0 * PI * lfoRateHz / sampleRate.toDouble()

        val minDelaySamples = sampleRate * 0.00016
        val maxDelaySamples = sampleRate * 0.00920
        val sweepRatio = maxDelaySamples / minDelaySamples

        val feedbackGain = (0.40f + 0.53f * kotlin.math.sqrt(intensity)).coerceIn(0.0f, 0.93f)

        val dryGain = (1.0f - 0.70f * intensity).coerceAtLeast(0.18f)
        val wetGain = 0.95f + 0.65f * intensity
        val outputGainComp = 1.0f / (1.0f + 0.35f * intensity)

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val modL = 0.5 * (1.0 + sin(lfoPhase))
            val modR = 0.5 * (1.0 + sin(lfoPhase + PI * 0.5))

            val delayL = minDelaySamples * sweepRatio.pow(modL)
            val delayR = minDelaySamples * sweepRatio.pow(modR)

            val readPosL = (writePos.toDouble() - delayL + bufferSize * 2) % bufferSize
            val idxL0 = readPosL.toInt() and bufferMask
            val fracL = (readPosL - readPosL.toInt()).toFloat()
            val idxL1 = (idxL0 + 1) and bufferMask
            val delayedL = delayBufferL[idxL0] * (1f - fracL) + delayBufferL[idxL1] * fracL

            val readPosR = (writePos.toDouble() - delayR + bufferSize * 2) % bufferSize
            val idxR0 = readPosR.toInt() and bufferMask
            val fracR = (readPosR - readPosR.toInt()).toFloat()
            val idxR1 = (idxR0 + 1) and bufferMask
            val delayedR = delayBufferR[idxR0] * (1f - fracR) + delayBufferR[idxR1] * fracR

            val fbRawL = delayedL * feedbackGain
            val fbHpfL = fbRawL - fbHpfL_x + 0.982f * fbHpfL_y
            fbHpfL_x = fbRawL
            fbHpfL_y = fbHpfL

            val fbRawR = delayedR * feedbackGain
            val fbHpfR = fbRawR - fbHpfR_x + 0.982f * fbHpfR_y
            fbHpfR_x = fbRawR
            fbHpfR_y = fbHpfR

            delayBufferL[writePos] = inL + softSaturate(fbHpfL)
            delayBufferR[writePos] = inR + softSaturate(fbHpfR)

            writePos = (writePos + 1) and bufferMask

            lfoPhase += lfoInc
            if (lfoPhase >= 2.0 * PI) {
                lfoPhase -= 2.0 * PI
            }

            val outLFloat = (inL * dryGain + delayedL * wetGain) * outputGainComp
            val outRFloat = (inR * dryGain + delayedR * wetGain) * outputGainComp

            val outL = outLFloat.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = outRFloat.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class PartyNextDoorAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var isolation = 0.60f
    private var reverb = 0.50f
    private var bassRumble = 0.70f

    private var sampleRate = 44100

    private var lpfB0 = 0.0;
    private var lpfB1 = 0.0;
    private var lpfB2 = 0.0
    private var lpfA1 = 0.0;
    private var lpfA2 = 0.0

    private var lpf1_s1_L = 0.0;
    private var lpf1_s2_L = 0.0
    private var lpf1_s1_R = 0.0;
    private var lpf1_s2_R = 0.0
    private var lpf2_s1_L = 0.0;
    private var lpf2_s2_L = 0.0
    private var lpf2_s1_R = 0.0;
    private var lpf2_s2_R = 0.0

    private var subB0 = 1.0;
    private var subB1 = 0.0;
    private var subB2 = 0.0
    private var subA1 = 0.0;
    private var subA2 = 0.0
    private var sub_s1_L = 0.0;
    private var sub_s2_L = 0.0
    private var sub_s1_R = 0.0;
    private var sub_s2_R = 0.0

    private val combLengthsL = intArrayOf(1116, 1188, 1277, 1356)
    private val combLengthsR = intArrayOf(1139, 1211, 1300, 1379)
    private val combBuffersL = Array(4) { FloatArray(1400) }
    private val combBuffersR = Array(4) { FloatArray(1400) }
    private val combIndicesL = IntArray(4)
    private val combIndicesR = IntArray(4)
    private val combFiltersL = FloatArray(4)
    private val combFiltersR = FloatArray(4)

    private val apLengthL1 = 225;
    private val apBufferL1 = FloatArray(250);
    private var apIndexL1 = 0
    private val apLengthR1 = 245;
    private val apBufferR1 = FloatArray(250);
    private var apIndexR1 = 0
    private val apLengthL2 = 556;
    private val apBufferL2 = FloatArray(600);
    private var apIndexL2 = 0
    private val apLengthR2 = 576;
    private val apBufferR2 = FloatArray(600);
    private var apIndexR2 = 0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetFilterState()
        else updateCoefficients()
    }

    fun setIsolation(isolation: Float) {
        this.isolation = isolation.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    fun setReverb(reverb: Float) {
        this.reverb = reverb.coerceIn(0.0f, 1.0f)
    }

    fun setBassRumble(bassRumble: Float) {
        this.bassRumble = bassRumble.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val fc = 260.0 + (1.0 - isolation.toDouble()).pow(1.8) * 840.0
        val omega = 2.0 * PI * fc / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val q = 0.7071
        val alpha = sn / (2.0 * q)

        val b0 = (1.0 - cs) / 2.0
        val b1 = 1.0 - cs
        val b2 = (1.0 - cs) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cs
        val a2 = 1.0 - alpha

        lpfB0 = b0 / a0
        lpfB1 = b1 / a0
        lpfB2 = b2 / a0
        lpfA1 = a1 / a0
        lpfA2 = a2 / a0

        val subFc = 75.0
        val subGainDb = 2.0 + bassRumble.toDouble() * 8.0
        val bigA = 10.0.pow(subGainDb / 40.0)
        val subOmega = 2.0 * PI * subFc / sampleRate
        val subSn = sin(subOmega)
        val subCs = cos(subOmega)
        val subQ = 1.2
        val subAlpha = subSn / (2.0 * subQ)

        val sB0 = 1.0 + subAlpha * bigA
        val sB1 = -2.0 * subCs
        val sB2 = 1.0 - subAlpha * bigA
        val sA0 = 1.0 + subAlpha / bigA
        val sA1 = -2.0 * subCs
        val sA2 = 1.0 - subAlpha / bigA

        subB0 = sB0 / sA0
        subB1 = sB1 / sA0
        subB2 = sB2 / sA0
        subA1 = sA1 / sA0
        subA2 = sA2 / sA0
    }

    private fun resetFilterState() {
        lpf1_s1_L = 0.0; lpf1_s2_L = 0.0
        lpf1_s1_R = 0.0; lpf1_s2_R = 0.0
        lpf2_s1_L = 0.0; lpf2_s2_L = 0.0
        lpf2_s1_R = 0.0; lpf2_s2_R = 0.0

        sub_s1_L = 0.0; sub_s2_L = 0.0
        sub_s1_R = 0.0; sub_s2_R = 0.0

        for (i in 0 until 4) {
            combBuffersL[i].fill(0f)
            combBuffersR[i].fill(0f)
            combIndicesL[i] = 0
            combIndicesR[i] = 0
            combFiltersL[i] = 0f
            combFiltersR[i] = 0f
        }

        apBufferL1.fill(0f); apIndexL1 = 0
        apBufferR1.fill(0f); apIndexR1 = 0
        apBufferL2.fill(0f); apIndexL2 = 0
        apBufferR2.fill(0f); apIndexR2 = 0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetFilterState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetFilterState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val combFeedback = 0.72f + reverb * 0.16f
        val combDamp = 0.45f
        val wetReverbMix = reverb * 0.65f
        val doorLeakGain = 0.045f * (1.0f - isolation * 0.75f)

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val lpf1_out_L = lpfB0 * inL + lpf1_s1_L
            lpf1_s1_L = lpfB1 * inL - lpfA1 * lpf1_out_L + lpf1_s2_L
            lpf1_s2_L = lpfB2 * inL - lpfA2 * lpf1_out_L

            val lpf1_out_R = lpfB0 * inR + lpf1_s1_R
            lpf1_s1_R = lpfB1 * inR - lpfA1 * lpf1_out_R + lpf1_s2_R
            lpf1_s2_R = lpfB2 * inR - lpfA2 * lpf1_out_R

            val lpf2_out_L = lpfB0 * lpf1_out_L + lpf2_s1_L
            lpf2_s1_L = lpfB1 * lpf1_out_L - lpfA1 * lpf2_out_L + lpf2_s2_L
            lpf2_s2_L = lpfB2 * lpf1_out_L - lpfA2 * lpf2_out_L

            val lpf2_out_R = lpfB0 * lpf1_out_R + lpf2_s1_R
            lpf2_s1_R = lpfB1 * lpf1_out_R - lpfA1 * lpf2_out_R + lpf2_s2_R
            lpf2_s2_R = lpfB2 * lpf1_out_R - lpfA2 * lpf2_out_R

            val sub_out_L = subB0 * lpf2_out_L + sub_s1_L
            sub_s1_L = subB1 * lpf2_out_L - subA1 * sub_out_L + sub_s2_L
            sub_s2_L = subB2 * lpf2_out_L - subA2 * sub_out_L

            val sub_out_R = subB0 * lpf2_out_R + sub_s1_R
            sub_s1_R = subB1 * lpf2_out_R - subA1 * sub_out_R + sub_s2_R
            sub_s2_R = subB2 * lpf2_out_R - subA2 * sub_out_R

            val leakL = inL * doorLeakGain
            val leakR = inR * doorLeakGain

            val directWallL = (sub_out_L.toFloat() + leakL)
            val directWallR = (sub_out_R.toFloat() + leakR)

            val revInL = directWallL * 0.35f
            val revInR = directWallR * 0.35f

            var combSumL = 0f
            var combSumR = 0f

            for (i in 0 until 4) {
                val lenL = combLengthsL[i]
                val idxL = combIndicesL[i]
                val delayedL = combBuffersL[i][idxL]
                combFiltersL[i] = delayedL * (1f - combDamp) + combFiltersL[i] * combDamp
                combBuffersL[i][idxL] = revInL + combFiltersL[i] * combFeedback
                combIndicesL[i] = (idxL + 1) % lenL
                combSumL += delayedL

                val lenR = combLengthsR[i]
                val idxR = combIndicesR[i]
                val delayedR = combBuffersR[i][idxR]
                combFiltersR[i] = delayedR * (1f - combDamp) + combFiltersR[i] * combDamp
                combBuffersR[i][idxR] = revInR + combFiltersR[i] * combFeedback
                combIndicesR[i] = (idxR + 1) % lenR
                combSumR += delayedR
            }

            val ap1OutL = -combSumL * 0.5f + apBufferL1[apIndexL1]
            apBufferL1[apIndexL1] = combSumL + ap1OutL * 0.5f
            apIndexL1 = (apIndexL1 + 1) % apLengthL1

            val ap2OutL = -ap1OutL * 0.5f + apBufferL2[apIndexL2]
            apBufferL2[apIndexL2] = ap1OutL + ap2OutL * 0.5f
            apIndexL2 = (apIndexL2 + 1) % apLengthL2

            val ap1OutR = -combSumR * 0.5f + apBufferR1[apIndexR1]
            apBufferR1[apIndexR1] = combSumR + ap1OutR * 0.5f
            apIndexR1 = (apIndexR1 + 1) % apLengthR1

            val ap2OutR = -ap1OutR * 0.5f + apBufferR2[apIndexR2]
            apBufferR2[apIndexR2] = ap1OutR + ap2OutR * 0.5f
            apIndexR2 = (apIndexR2 + 1) % apLengthR2

            val mixL = directWallL * (1.15f - wetReverbMix * 0.35f) + ap2OutL * wetReverbMix
            val mixR = directWallR * (1.15f - wetReverbMix * 0.35f) + ap2OutR * wetReverbMix

            val satL = (mixL / (1.0f + kotlin.math.abs(mixL) / 45000.0f)) * 1.35f
            val satR = (mixR / (1.0f + kotlin.math.abs(mixR) / 45000.0f)) * 1.35f

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class SuperWideAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var width = 0.70f
    private var depth = 0.50f

    private var sampleRate = 44100

    private val delayBufferMask = 4095
    private val spatialDelayBufferL = FloatArray(4096)
    private val spatialDelayBufferR = FloatArray(4096)
    private var delayWritePos = 0

    private var synthAp1_in = 0f;
    private var synthAp1_out = 0f
    private var synthAp2_in = 0f;
    private var synthAp2_out = 0f

    private var hpfB0 = 1.0;
    private var hpfB1 = -2.0;
    private var hpfB2 = 1.0
    private var hpfA1 = 0.0;
    private var hpfA2 = 0.0
    private var sideHpf_s1 = 0.0;
    private var sideHpf_s2 = 0.0

    private var pinnaB0 = 1.0;
    private var pinnaB1 = 0.0;
    private var pinnaB2 = 0.0
    private var pinnaA1 = 0.0;
    private var pinnaA2 = 0.0
    private var pinna_s1_L = 0.0;
    private var pinna_s2_L = 0.0
    private var pinna_s1_R = 0.0;
    private var pinna_s2_R = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setWidth(width: Float) {
        this.width = width.coerceIn(0.0f, 1.0f)
    }

    fun setDepth(depth: Float) {
        this.depth = depth.coerceIn(0.0f, 1.0f)
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val hpfFc = 160.0
        val omegaH = 2.0 * PI * hpfFc / sampleRate
        val snH = sin(omegaH)
        val csH = cos(omegaH)
        val qH = 0.7071
        val alphaH = snH / (2.0 * qH)

        val hb0 = (1.0 + csH) / 2.0
        val hb1 = -(1.0 + csH)
        val hb2 = (1.0 + csH) / 2.0
        val ha0 = 1.0 + alphaH
        val ha1 = -2.0 * csH
        val ha2 = 1.0 - alphaH

        hpfB0 = hb0 / ha0
        hpfB1 = hb1 / ha0
        hpfB2 = hb2 / ha0
        hpfA1 = ha1 / ha0
        hpfA2 = ha2 / ha0

        val pinnaFc = 4200.0
        val pinnaGainDb = 5.5
        val bigA = 10.0.pow(pinnaGainDb / 40.0)
        val omegaP = 2.0 * PI * pinnaFc / sampleRate
        val snP = sin(omegaP)
        val csP = cos(omegaP)
        val qP = 1.5
        val alphaP = snP / (2.0 * qP)

        val pb0 = 1.0 + alphaP * bigA
        val pb1 = -2.0 * csP
        val pb2 = 1.0 - alphaP * bigA
        val pa0 = 1.0 + alphaP / bigA
        val pa1 = -2.0 * csP
        val pa2 = 1.0 - alphaP / bigA

        pinnaB0 = pb0 / pa0
        pinnaB1 = pb1 / pa0
        pinnaB2 = pb2 / pa0
        pinnaA1 = pa1 / pa0
        pinnaA2 = pa2 / pa0
    }

    private fun resetState() {
        spatialDelayBufferL.fill(0f)
        spatialDelayBufferR.fill(0f)
        delayWritePos = 0
        synthAp1_in = 0f; synthAp1_out = 0f
        synthAp2_in = 0f; synthAp2_out = 0f
        sideHpf_s1 = 0.0; sideHpf_s2 = 0.0
        pinna_s1_L = 0.0; pinna_s2_L = 0.0
        pinna_s1_R = 0.0; pinna_s2_R = 0.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val sideGain = 1.0f + width * 2.85f
        val midGain = 1.0f - width * 0.18f

        val dL1 = (sampleRate * (0.0085 + depth * 0.0095)).toInt().coerceIn(10, 4000)
        val dL2 = (sampleRate * (0.0172 + depth * 0.0125)).toInt().coerceIn(10, 4000)
        val dR1 = (sampleRate * (0.0118 + depth * 0.0105)).toInt().coerceIn(10, 4000)
        val dR2 = (sampleRate * (0.0234 + depth * 0.0145)).toInt().coerceIn(10, 4000)

        val binauralReflectGain = 0.45f * depth
        val synthSideAmount = 0.38f * depth

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val mid = (inL + inR) * 0.5f
            val rawSide = (inL - inR) * 0.5f

            val hpSide = hpfB0 * rawSide + sideHpf_s1
            sideHpf_s1 = hpfB1 * rawSide - hpfA1 * hpSide + sideHpf_s2
            sideHpf_s2 = hpfB2 * rawSide - hpfA2 * hpSide

            val ap1 = -0.62f * mid + synthAp1_in + 0.62f * synthAp1_out
            synthAp1_in = mid
            synthAp1_out = ap1

            val ap2 = -0.38f * ap1 + synthAp2_in + 0.38f * synthAp2_out
            synthAp2_in = ap1
            synthAp2_out = ap2

            val generatedSide = ap2 * synthSideAmount

            val totalSide = hpSide.toFloat() + generatedSide

            val pinnaL = pinnaB0 * totalSide + pinna_s1_L
            pinna_s1_L = pinnaB1 * totalSide - pinnaA1 * pinnaL + pinna_s2_L
            pinna_s2_L = pinnaB2 * totalSide - pinnaA2 * pinnaL

            val pinnaR = pinnaB0 * totalSide + pinna_s1_R
            pinna_s1_R = pinnaB1 * totalSide - pinnaA1 * pinnaR + pinna_s2_R
            pinna_s2_R = pinnaB2 * totalSide - pinnaA2 * pinnaR

            val sculptedSide = (pinnaL.toFloat() * 0.6f + totalSide * 0.4f)

            spatialDelayBufferL[delayWritePos] = inL * 0.5f + sculptedSide * 0.5f
            spatialDelayBufferR[delayWritePos] = inR * 0.5f - sculptedSide * 0.5f

            val tapL1 = spatialDelayBufferL[(delayWritePos - dL1) and delayBufferMask]
            val tapL2 = spatialDelayBufferL[(delayWritePos - dL2) and delayBufferMask]
            val tapR1 = spatialDelayBufferR[(delayWritePos - dR1) and delayBufferMask]
            val tapR2 = spatialDelayBufferR[(delayWritePos - dR2) and delayBufferMask]

            delayWritePos = (delayWritePos + 1) and delayBufferMask

            val crossReflectionL = (-tapR1 * 0.65f + tapL2 * 0.35f) * binauralReflectGain
            val crossReflectionR = (tapL1 * 0.65f - tapR2 * 0.35f) * binauralReflectGain

            val expandedSideL = sculptedSide * sideGain + crossReflectionL
            val expandedSideR = sculptedSide * sideGain + crossReflectionR

            val outLFloat = mid * midGain + expandedSideL
            val outRFloat = mid * midGain - expandedSideR

            val satL = (outLFloat / (1.0f + kotlin.math.abs(outLFloat) / 50000.0f)) * 1.05f
            val satR = (outRFloat / (1.0f + kotlin.math.abs(outRFloat) / 50000.0f)) * 1.05f

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class VinylLoFiAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var crackles = 0.65f
    private var flutter = 0.50f

    private var sampleRate = 44100

    private val flutterMask = 1023
    private val flutterBufferL = FloatArray(1024)
    private val flutterBufferR = FloatArray(1024)
    private var flutterWritePos = 0

    private var wowPhase = 0.0
    private var flutterPhase = 0.0

    private var lpfPrevL = 0f
    private var lpfPrevR = 0f

    private var rngState = 123456789L
    private var crackleFilterL = 0f
    private var crackleFilterR = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setCrackles(crackles: Float) {
        this.crackles = crackles.coerceIn(0.0f, 1.0f)
    }

    fun setFlutter(flutter: Float) {
        this.flutter = flutter.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        flutterBufferL.fill(0f)
        flutterBufferR.fill(0f)
        flutterWritePos = 0
        wowPhase = 0.0
        flutterPhase = 0.0
        lpfPrevL = 0f
        lpfPrevR = 0f
        crackleFilterL = 0f
        crackleFilterR = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    private fun nextRandomFloat(): Float {
        rngState = (rngState * 1664525L + 1013904223L) and 0xFFFFFFFFL
        return (rngState.toFloat() / 4294967296.0f) * 2.0f - 1.0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val wowInc = 2.0 * PI * 0.55 / sampleRate
        val flutterInc = 2.0 * PI * 4.8 / sampleRate
        val maxDelay = (sampleRate * 0.005).toDouble()
        val flutterDepth = flutter.toDouble() * (sampleRate * 0.0028)
        val wowDepth = flutter.toDouble() * (sampleRate * 0.0022)

        val lpfAlpha = (2.0 * PI * 6500.0 / sampleRate).coerceIn(0.1, 0.9).toFloat()

        val crackleProb = 0.00045f + crackles * 0.0028f
        val crackleGain = crackles * 4500.0f
        val grooveHissGain = crackles * 45.0f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            flutterBufferL[flutterWritePos] = inL
            flutterBufferR[flutterWritePos] = inR

            val currentDelay = maxDelay + sin(wowPhase) * wowDepth + sin(flutterPhase) * flutterDepth
            wowPhase += wowInc
            if (wowPhase >= 2.0 * PI) wowPhase -= 2.0 * PI

            flutterPhase += flutterInc
            if (flutterPhase >= 2.0 * PI) flutterPhase -= 2.0 * PI

            val delayInt = currentDelay.toInt()
            val delayFrac = (currentDelay - delayInt).toFloat()

            val r0 = (flutterWritePos - delayInt) and flutterMask
            val r1 = (flutterWritePos - delayInt - 1) and flutterMask

            val delayedL = flutterBufferL[r0] * (1.0f - delayFrac) + flutterBufferL[r1] * delayFrac
            val delayedR = flutterBufferR[r0] * (1.0f - delayFrac) + flutterBufferR[r1] * delayFrac

            flutterWritePos = (flutterWritePos + 1) and flutterMask

            val tapeL = lpfPrevL + lpfAlpha * (delayedL - lpfPrevL)
            lpfPrevL = tapeL
            val tapeR = lpfPrevR + lpfAlpha * (delayedR - lpfPrevR)
            lpfPrevR = tapeR

            var cracklePulseL = 0f
            var cracklePulseR = 0f
            val randVal = (nextRandomFloat() + 1.0f) * 0.5f
            if (randVal < crackleProb) {
                val popAmp = nextRandomFloat().pow(3) * crackleGain
                cracklePulseL = popAmp
                cracklePulseR = popAmp * (0.6f + nextRandomFloat() * 0.4f)
            }

            crackleFilterL = crackleFilterL * 0.72f + cracklePulseL
            crackleFilterR = crackleFilterR * 0.72f + cracklePulseR

            val grooveHissL = nextRandomFloat() * grooveHissGain
            val grooveHissR = nextRandomFloat() * grooveHissGain

            val combinedL = tapeL + crackleFilterL + grooveHissL
            val combinedR = tapeR + crackleFilterR + grooveHissR

            val normL = combinedL / 32768.0f
            val satL = (normL - (normL * normL * normL) / 4.0f) * 32768.0f

            val normR = combinedR / 32768.0f
            val satR = (normR - (normR * normR * normR) / 4.0f) * 32768.0f

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class PhaserAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var speed = 0.50f
    private var feedback = 0.65f

    private var sampleRate = 44100
    private var lfoPhase = 0.0

    private val apPrevInL = FloatArray(4)
    private val apPrevOutL = FloatArray(4)
    private val apPrevInR = FloatArray(4)
    private val apPrevOutR = FloatArray(4)

    private var lastOutputL = 0f
    private var lastOutputR = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.0f, 1.0f)
    }

    fun setFeedback(feedback: Float) {
        this.feedback = feedback.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        lfoPhase = 0.0
        apPrevInL.fill(0f); apPrevOutL.fill(0f)
        apPrevInR.fill(0f); apPrevOutR.fill(0f)
        lastOutputL = 0f; lastOutputR = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val lfoRateHz = 0.12 + speed * 3.4
        val lfoInc = 2.0 * PI * lfoRateHz / sampleRate
        val fbGain = (feedback * 0.82f).coerceIn(0.0f, 0.88f)

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val lfoL = (sin(lfoPhase) + 1.0) * 0.5
            val lfoR = (sin(lfoPhase + PI * 0.5) + 1.0) * 0.5

            val freqL = 320.0 * 2.0.pow(lfoL * 3.6)
            val freqR = 320.0 * 2.0.pow(lfoR * 3.6)

            val tanL = kotlin.math.tan(PI * freqL / sampleRate)
            val coeffL = ((tanL - 1.0) / (tanL + 1.0)).toFloat()

            val tanR = kotlin.math.tan(PI * freqR / sampleRate)
            val coeffR = ((tanR - 1.0) / (tanR + 1.0)).toFloat()

            lfoPhase += lfoInc
            if (lfoPhase >= 2.0 * PI) lfoPhase -= 2.0 * PI

            var sigL = inL + lastOutputL * fbGain
            for (i in 0 until 4) {
                val out = coeffL * (sigL - apPrevOutL[i]) + apPrevInL[i]
                apPrevInL[i] = sigL
                apPrevOutL[i] = out
                sigL = out
            }
            lastOutputL = sigL

            var sigR = inR + lastOutputR * fbGain
            for (i in 0 until 4) {
                val out = coeffR * (sigR - apPrevOutR[i]) + apPrevInR[i]
                apPrevInR[i] = sigR
                apPrevOutR[i] = out
                sigR = out
            }
            lastOutputR = sigR

            val mixL = inL * 0.5f + sigL * 0.65f
            val mixR = inR * 0.5f + sigR * 0.65f

            val outL = mixL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = mixR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class MegaphoneRadioAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var tone =
        0.50f
    private var drive = 0.60f

    private var sampleRate = 44100

    private var hpfB0 = 1.0;
    private var hpfB1 = -2.0;
    private var hpfB2 = 1.0
    private var hpfA1 = 0.0;
    private var hpfA2 = 0.0
    private var hpf_s1_L = 0.0;
    private var hpf_s2_L = 0.0

    private var lpfB0 = 1.0;
    private var lpfB1 = 2.0;
    private var lpfB2 = 1.0
    private var lpfA1 = 0.0;
    private var lpfA2 = 0.0
    private var lpf_s1_L = 0.0;
    private var lpf_s2_L = 0.0

    private var resB0 = 1.0;
    private var resB1 = 0.0;
    private var resB2 = 0.0
    private var resA1 = 0.0;
    private var resA2 = 0.0
    private var res_s1_L = 0.0;
    private var res_s2_L = 0.0

    private val hornMask = 1023
    private val hornBuffer = FloatArray(1024)
    private var hornWritePos = 0

    private var radioRng = 543219876L
    private var whistlePhase = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setTone(tone: Float) {
        this.tone = tone.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    fun setDrive(drive: Float) {
        this.drive = drive.coerceIn(0.0f, 1.0f)
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val hpfFc = if (tone < 0.35f) {
            220.0
        } else if (tone < 0.75f) {
            780.0
        } else {
            480.0
        }

        val omegaH = 2.0 * PI * hpfFc / sampleRate
        val snH = sin(omegaH);
        val csH = cos(omegaH);
        val alphaH = snH / (2.0 * 0.7071)
        val hb0 = (1.0 + csH) / 2.0;
        val hb1 = -(1.0 + csH);
        val hb2 = (1.0 + csH) / 2.0
        val ha0 = 1.0 + alphaH;
        val ha1 = -2.0 * csH;
        val ha2 = 1.0 - alphaH
        hpfB0 = hb0 / ha0; hpfB1 = hb1 / ha0; hpfB2 = hb2 / ha0; hpfA1 = ha1 / ha0; hpfA2 = ha2 / ha0

        val lpfFc = if (tone < 0.35f) {
            1850.0
        } else if (tone < 0.75f) {
            2400.0
        } else {
            4200.0
        }
        val omegaL = 2.0 * PI * lpfFc / sampleRate
        val snL = sin(omegaL);
        val csL = cos(omegaL);
        val alphaL = snL / (2.0 * 0.7071)
        val lb0 = (1.0 - csL) / 2.0;
        val lb1 = 1.0 - csL;
        val lb2 = (1.0 - csL) / 2.0
        val la0 = 1.0 + alphaL;
        val la1 = -2.0 * csL;
        val la2 = 1.0 - alphaL
        lpfB0 = lb0 / la0; lpfB1 = lb1 / la0; lpfB2 = lb2 / la0; lpfA1 = la1 / la0; lpfA2 = la2 / la0

        val resFc = if (tone < 0.35f) 850.0 else if (tone < 0.75f) 1550.0 else 2100.0
        val resGainDb = if (tone < 0.35f) 5.0 else if (tone < 0.75f) 8.0 else 22.0
        val qRes = if (tone < 0.35f) 1.0 else if (tone < 0.75f) 2.2 else 4.5
        val bigA = 10.0.pow(resGainDb / 40.0)
        val omegaR = 2.0 * PI * resFc / sampleRate
        val snR = sin(omegaR);
        val csR = cos(omegaR);
        val alphaR = snR / (2.0 * qRes)
        val rb0 = 1.0 + alphaR * bigA;
        val rb1 = -2.0 * csR;
        val rb2 = 1.0 - alphaR * bigA
        val ra0 = 1.0 + alphaR / bigA;
        val ra1 = -2.0 * csR;
        val ra2 = 1.0 - alphaR / bigA
        resB0 = rb0 / ra0; resB1 = rb1 / ra0; resB2 = rb2 / ra0; resA1 = ra1 / ra0; resA2 = ra2 / ra0
    }

    private fun resetState() {
        hpf_s1_L = 0.0; hpf_s2_L = 0.0
        lpf_s1_L = 0.0; lpf_s2_L = 0.0
        res_s1_L = 0.0; res_s2_L = 0.0
        hornBuffer.fill(0f)
        hornWritePos = 0
        whistlePhase = 0.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val driveGain = 1.0f + drive * 5.0f

        val isMegaphoneMode = tone >= 0.75f
        val isWalkieMode = tone in 0.35f..0.74f
        val isRadioMode = tone < 0.35f

        val staticNoiseLevel = when {
            isWalkieMode -> 550.0f
            isRadioMode -> 250.0f
            else -> 0.0f
        }

        val whistleGain = if (isRadioMode) 125.0f else 0.0f
        val whistleInc = 2.0 * PI * 3150.0 / sampleRate

        val hornDelaySamples = (sampleRate * 0.0155).toInt().coerceIn(10, 1000)

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val monoIn = (inL + inR) * 0.5f

            val hpOut = hpfB0 * monoIn + hpf_s1_L
            hpf_s1_L = hpfB1 * monoIn - hpfA1 * hpOut + hpf_s2_L
            hpf_s2_L = hpfB2 * monoIn - hpfA2 * hpOut

            val lpOut = lpfB0 * hpOut + lpf_s1_L
            lpf_s1_L = lpfB1 * hpOut - lpfA1 * lpOut + lpf_s2_L
            lpf_s2_L = lpfB2 * hpOut - lpfA2 * lpOut

            val resOut = resB0 * lpOut + res_s1_L
            res_s1_L = resB1 * lpOut - resA1 * resOut + res_s2_L
            res_s2_L = resB2 * lpOut - resA2 * resOut

            val driven = (resOut.toFloat() * driveGain) / 28000.0f
            var clipped = kotlin.math.tanh(driven * 2.6f) * 32768.0f * 0.82f

            if (isWalkieMode) {

                val quantStep = 180.0f
                clipped = kotlin.math.round(clipped / quantStep) * quantStep
            } else if (isMegaphoneMode) {

                hornBuffer[hornWritePos] = clipped
                val delayedHorn = hornBuffer[(hornWritePos - hornDelaySamples) and hornMask]
                hornWritePos = (hornWritePos + 1) and hornMask
                clipped = clipped * 0.72f + delayedHorn * 0.42f
            }

            radioRng = (radioRng * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val noise = ((radioRng.toFloat() / 4294967296.0f) * 2.0f - 1.0f) * staticNoiseLevel

            val whistle = if (whistleGain > 0f) {
                val w = sin(whistlePhase).toFloat() * whistleGain
                whistlePhase += whistleInc
                if (whistlePhase >= 2.0 * PI) whistlePhase -= 2.0 * PI
                w
            } else 0f

            val finalSample =
                (clipped + noise + whistle).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt()
                    .toShort()

            buffer.putShort(finalSample)
            buffer.putShort(finalSample)
        }
        buffer.flip()
    }
}

class RobotVocoderAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var frequency = 0.40f
    private var mix = 0.75f

    private var sampleRate = 44100
    private var carrierPhase = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) carrierPhase = 0.0
    }

    fun setFrequency(frequency: Float) {
        this.frequency = frequency.coerceIn(0.0f, 1.0f)
    }

    fun setMix(mix: Float) {
        this.mix = mix.coerceIn(0.0f, 1.0f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        carrierPhase = 0.0
        return inputAudioFormat
    }

    override fun onFlush() {
        carrierPhase = 0.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val carrierFreqHz = 110.0 + frequency.toDouble() * 440.0
        val carrierInc = 2.0 * PI * carrierFreqHz / sampleRate
        val wetGain = mix
        val dryGain = 1.0f - mix * 0.65f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val carrier = sin(carrierPhase).toFloat()
            carrierPhase += carrierInc
            if (carrierPhase >= 2.0 * PI) carrierPhase -= 2.0 * PI

            val ringL = inL * carrier * 1.35f
            val ringR = inR * carrier * 1.35f

            val outLFloat = inL * dryGain + ringL * wetGain
            val outRFloat = inR * dryGain + ringR * wetGain

            val satL = outLFloat / (1.0f + kotlin.math.abs(outLFloat) / 50000.0f)
            val satR = outRFloat / (1.0f + kotlin.math.abs(outRFloat) / 50000.0f)

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class ChorusAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var rate = 0.30f
    private var depth = 0.70f

    private var sampleRate = 44100
    private val bufferSize = 2048
    private val bufferMask = bufferSize - 1

    private val delayBufferL = FloatArray(bufferSize)
    private val delayBufferR = FloatArray(bufferSize)
    private var writePos = 0

    private var lfoPhase = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setRate(rate: Float) {
        this.rate = rate.coerceIn(0.0f, 1.0f)
    }

    fun setDepth(depth: Float) {
        this.depth = depth.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        writePos = 0
        lfoPhase = 0.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val lfoRateHz = 0.20 + rate.toDouble() * 2.80
        val lfoInc = 2.0 * PI * lfoRateHz / sampleRate

        val baseDelaySamples = sampleRate * 0.016
        val modDepthSamples = depth.toDouble() * (sampleRate * 0.0075)

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            delayBufferL[writePos] = inL
            delayBufferR[writePos] = inR

            val modL1 = sin(lfoPhase) * modDepthSamples
            val modL2 = sin(lfoPhase + PI * 0.5) * modDepthSamples * 0.75
            val modR1 = sin(lfoPhase + PI) * modDepthSamples
            val modR2 = sin(lfoPhase + PI * 1.5) * modDepthSamples * 0.75

            val dL1 = baseDelaySamples + modL1
            val dL2 = baseDelaySamples * 1.25 + modL2
            val dR1 = baseDelaySamples + modR1
            val dR2 = baseDelaySamples * 1.25 + modR2

            val rL1_0 = (writePos.toDouble() - dL1 + bufferSize * 2) % bufferSize
            val idxL1_0 = rL1_0.toInt() and bufferMask
            val fracL1 = (rL1_0 - rL1_0.toInt()).toFloat()
            val voiceL1 = delayBufferL[idxL1_0] * (1f - fracL1) + delayBufferL[(idxL1_0 + 1) and bufferMask] * fracL1

            val rL2_0 = (writePos.toDouble() - dL2 + bufferSize * 2) % bufferSize
            val idxL2_0 = rL2_0.toInt() and bufferMask
            val fracL2 = (rL2_0 - rL2_0.toInt()).toFloat()
            val voiceL2 = delayBufferL[idxL2_0] * (1f - fracL2) + delayBufferL[(idxL2_0 + 1) and bufferMask] * fracL2

            val rR1_0 = (writePos.toDouble() - dR1 + bufferSize * 2) % bufferSize
            val idxR1_0 = rR1_0.toInt() and bufferMask
            val fracR1 = (rR1_0 - rR1_0.toInt()).toFloat()
            val voiceR1 = delayBufferR[idxR1_0] * (1f - fracR1) + delayBufferR[(idxR1_0 + 1) and bufferMask] * fracR1

            val rR2_0 = (writePos.toDouble() - dR2 + bufferSize * 2) % bufferSize
            val idxR2_0 = rR2_0.toInt() and bufferMask
            val fracR2 = (rR2_0 - rR2_0.toInt()).toFloat()
            val voiceR2 = delayBufferR[idxR2_0] * (1f - fracR2) + delayBufferR[(idxR2_0 + 1) and bufferMask] * fracR2

            writePos = (writePos + 1) and bufferMask

            lfoPhase += lfoInc
            if (lfoPhase >= 2.0 * PI) lfoPhase -= 2.0 * PI

            val chorusL = (voiceL1 + voiceL2 * 0.65f) * 0.60f
            val chorusR = (voiceR1 + voiceR2 * 0.65f) * 0.60f

            val mixL = inL * 0.55f + chorusL
            val mixR = inR * 0.55f + chorusR

            val outL = mixL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = mixR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class UnderwaterAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var depth = 0.60f
    private var bubbles = 0.40f

    private var sampleRate = 44100

    private var lpB0 = 0.0;
    private var lpB1 = 0.0;
    private var lpB2 = 0.0
    private var lpA1 = 0.0;
    private var lpA2 = 0.0
    private var s1_1_L = 0.0;
    private var s2_1_L = 0.0
    private var s1_1_R = 0.0;
    private var s2_1_R = 0.0
    private var s1_2_L = 0.0;
    private var s2_2_L = 0.0
    private var s1_2_R = 0.0;
    private var s2_2_R = 0.0

    private var subB0 = 1.0;
    private var subB1 = 0.0;
    private var subB2 = 0.0
    private var subA1 = 0.0;
    private var subA2 = 0.0
    private var sub_s1_L = 0.0;
    private var sub_s2_L = 0.0
    private var sub_s1_R = 0.0;
    private var sub_s2_R = 0.0

    private var hydroPhase = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setDepth(depth: Float) {
        this.depth = depth.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    fun setBubbles(bubbles: Float) {
        this.bubbles = bubbles.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val fc = 180.0 + (1.0 - depth.toDouble()).pow(1.8) * 670.0
        val q = 0.7071 + bubbles.toDouble() * 1.15
        val omega = 2.0 * PI * fc / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * q)

        val b0 = (1.0 - cs) / 2.0
        val b1 = 1.0 - cs
        val b2 = (1.0 - cs) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cs
        val a2 = 1.0 - alpha

        lpB0 = b0 / a0
        lpB1 = b1 / a0
        lpB2 = b2 / a0
        lpA1 = a1 / a0
        lpA2 = a2 / a0

        val subFc = 58.0
        val subGainDb = 3.0 + depth.toDouble() * 5.5
        val bigA = 10.0.pow(subGainDb / 40.0)
        val subOmega = 2.0 * PI * subFc / sampleRate
        val subSn = sin(subOmega)
        val subCs = cos(subOmega)
        val subQ = 1.3
        val subAlpha = subSn / (2.0 * subQ)

        val sB0 = 1.0 + subAlpha * bigA
        val sB1 = -2.0 * subCs
        val sB2 = 1.0 - subAlpha * bigA
        val sA0 = 1.0 + subAlpha / bigA
        val sA1 = -2.0 * subCs
        val sA2 = 1.0 - subAlpha / bigA

        subB0 = sB0 / sA0
        subB1 = sB1 / sA0
        subB2 = sB2 / sA0
        subA1 = sA1 / sA0
        subA2 = sA2 / sA0
    }

    private fun resetState() {
        s1_1_L = 0.0; s2_1_L = 0.0
        s1_1_R = 0.0; s2_1_R = 0.0
        s1_2_L = 0.0; s2_2_L = 0.0
        s1_2_R = 0.0; s2_2_R = 0.0
        sub_s1_L = 0.0; sub_s2_L = 0.0
        sub_s1_R = 0.0; sub_s2_R = 0.0
        hydroPhase = 0.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val hydroInc = 2.0 * PI * 0.28 / sampleRate
        val makeupGain = 1.45f + depth * 0.45f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val lp1_L = lpB0 * inL + s1_1_L
            s1_1_L = lpB1 * inL - lpA1 * lp1_L + s2_1_L
            s2_1_L = lpB2 * inL - lpA2 * lp1_L

            val lp1_R = lpB0 * inR + s1_1_R
            s1_1_R = lpB1 * inR - lpA1 * lp1_R + s2_1_R
            s2_1_R = lpB2 * inR - lpA2 * lp1_R

            val lp2_L = lpB0 * lp1_L + s1_2_L
            s1_2_L = lpB1 * lp1_L - lpA1 * lp2_L + s2_2_L
            s2_2_L = lpB2 * lp1_L - lpA2 * lp2_L

            val lp2_R = lpB0 * lp1_R + s1_2_R
            s1_2_R = lpB1 * lp1_R - lpA1 * lp2_R + s2_2_R
            s2_2_R = lpB2 * lp1_R - lpA2 * lp2_R

            val sub_L = subB0 * lp2_L + sub_s1_L
            sub_s1_L = subB1 * lp2_L - subA1 * sub_L + sub_s2_L
            sub_s2_L = subB2 * lp2_L - subA2 * sub_L

            val sub_R = subB0 * lp2_R + sub_s1_R
            sub_s1_R = subB1 * lp2_R - subA1 * sub_R + sub_s2_R
            sub_s2_R = subB2 * lp2_R - subA2 * sub_R

            val hydroMod = 1.0 + sin(hydroPhase) * (0.08 * bubbles.toDouble())
            hydroPhase += hydroInc
            if (hydroPhase >= 2.0 * PI) hydroPhase -= 2.0 * PI

            val outLFloat = (sub_L.toFloat() * hydroMod.toFloat() * makeupGain)
            val outRFloat = (sub_R.toFloat() * hydroMod.toFloat() * makeupGain)

            val satL = outLFloat / (1.0f + kotlin.math.abs(outLFloat) / 42000.0f)
            val satR = outRFloat / (1.0f + kotlin.math.abs(outRFloat) / 42000.0f)

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class TranceGateAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var speed = 0.50f
    private var pattern = 0.85f
    private var mix = 0.90f

    private var sampleRate = 44100
    private var lfoPhase = 0.0
    private var smoothedGain = 1.0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.0f, 1.0f)
    }

    fun setPattern(pattern: Float) {
        this.pattern = pattern.coerceIn(0.0f, 1.0f)
    }

    fun setMix(mix: Float) {
        this.mix = mix.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        lfoPhase = 0.0
        smoothedGain = 1.0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val gateFreqHz = 1.0 + speed.toDouble() * 13.0
        val lfoInc = 2.0 * PI * gateFreqHz / sampleRate

        val slewAlpha = (2.0 * PI * 550.0 / sampleRate).coerceIn(0.02, 0.50).toFloat()

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val sinVal = sin(lfoPhase)
            val sineTarget = (sinVal + 1.0) * 0.5
            val squareTarget = if (sinVal >= 0.0) 1.0 else 0.0

            val target = (sineTarget * (1.0 - pattern.toDouble()) + squareTarget * pattern.toDouble()).toFloat()

            smoothedGain += slewAlpha * (target - smoothedGain)

            lfoPhase += lfoInc
            if (lfoPhase >= 2.0 * PI) lfoPhase -= 2.0 * PI

            val effectiveGain = (1.0f - mix) + mix * smoothedGain

            val outL =
                (inL * effectiveGain).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR =
                (inR * effectiveGain).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class PingPongDelayAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var delayTime = 0.45f
    private var feedback = 0.60f

    private var sampleRate = 44100
    private val bufferSize = 32768
    private val bufferMask = bufferSize - 1

    private val delayBufferL = FloatArray(bufferSize)
    private val delayBufferR = FloatArray(bufferSize)
    private var writePos = 0

    private var fbLpL = 0f
    private var fbLpR = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setDelayTime(delayTime: Float) {
        this.delayTime = delayTime.coerceIn(0.0f, 1.0f)
    }

    fun setFeedback(feedback: Float) {
        this.feedback = feedback.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        delayBufferL.fill(0f)
        delayBufferR.fill(0f)
        writePos = 0
        fbLpL = 0f
        fbLpR = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val delaySamples = (sampleRate * (0.08 + delayTime.toDouble() * 0.47)).toInt().coerceIn(100, bufferSize - 10)
        val fbGain = (0.15f + feedback * 0.62f).coerceIn(0.10f, 0.78f)
        val tapeAlpha = (2.0 * PI * 4200.0 / sampleRate).coerceIn(0.10, 0.85).toFloat()

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val readPos = (writePos - delaySamples + bufferSize) and bufferMask
            val delayedL = delayBufferL[readPos]
            val delayedR = delayBufferR[readPos]

            fbLpL += tapeAlpha * (delayedL - fbLpL)
            fbLpR += tapeAlpha * (delayedR - fbLpR)

            val fbInL = inL + fbLpR * fbGain
            val fbInR = inR + fbLpL * fbGain

            delayBufferL[writePos] = fbInL / (1.0f + kotlin.math.abs(fbInL) / 45000.0f)
            delayBufferR[writePos] = fbInR / (1.0f + kotlin.math.abs(fbInR) / 45000.0f)

            writePos = (writePos + 1) and bufferMask

            val mixL = inL * 0.85f + delayedL * 0.70f
            val mixR = inR * 0.85f + delayedR * 0.70f

            val outL = mixL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = mixR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class ChiptuneAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var bits = 0.50f
    private var sampleRateDown = 0.55f

    private var sampleCounter = 0
    private var heldLeft: Short = 0
    private var heldRight: Short = 0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) {
            sampleCounter = 0
            heldLeft = 0
            heldRight = 0
        }
    }

    fun setBits(bits: Float) {
        this.bits = bits.coerceIn(0.0f, 1.0f)
    }

    fun setSampleRateDown(sr: Float) {
        this.sampleRateDown = sr.coerceIn(0.0f, 1.0f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return inputAudioFormat
    }

    override fun onFlush() {
        sampleCounter = 0
        heldLeft = 0
        heldRight = 0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val holdFactor = (1 + (sampleRateDown * 13.0f).toInt()).coerceIn(1, 14)
        val bitDepth = (10.0f - bits * 7.0f).coerceIn(3.0f, 10.0f)
        val step = (1 shl (16 - bitDepth.toInt())).coerceAtLeast(1)

        val channelCount = inputAudioFormat.channelCount

        if (channelCount == 2) {
            while (inputBuffer.remaining() >= 4) {
                val inL = inputBuffer.getShort().toFloat()
                val inR = inputBuffer.getShort().toFloat()

                if (sampleCounter % holdFactor == 0) {

                    val qL = (((inL + 32768f) / step).toInt() * step - 32768).coerceIn(-32768, 32767)
                    val qR = (((inR + 32768f) / step).toInt() * step - 32768).coerceIn(-32768, 32767)

                    heldLeft = qL.toShort()
                    heldRight = qR.toShort()
                }
                sampleCounter++

                buffer.putShort(heldLeft)
                buffer.putShort(heldRight)
            }
        } else {
            while (inputBuffer.remaining() >= 2) {
                val inM = inputBuffer.getShort().toFloat()

                if (sampleCounter % holdFactor == 0) {
                    val qM = (((inM + 32768f) / step).toInt() * step - 32768).coerceIn(-32768, 32767)
                    heldLeft = qM.toShort()
                }
                sampleCounter++

                buffer.putShort(heldLeft)
            }
        }
        buffer.flip()
    }
}

class ShimmerReverbAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var size = 0.65f
    private var shimmerMix = 0.60f

    private var sampleRate = 44100

    private val combLengthsL = intArrayOf(1553, 1787, 2011, 2339)
    private val combLengthsR = intArrayOf(1579, 1811, 2039, 2371)
    private val combBuffersL = Array(4) { FloatArray(2400) }
    private val combBuffersR = Array(4) { FloatArray(2400) }
    private val combIndicesL = IntArray(4)
    private val combIndicesR = IntArray(4)
    private val combFiltersL = FloatArray(4)
    private val combFiltersR = FloatArray(4)

    private val apLength1 = 347;
    private val apBufL1 = FloatArray(360);
    private var apIdxL1 = 0
    private val apLength2 = 797;
    private val apBufL2 = FloatArray(820);
    private var apIdxL2 = 0
    private val apLengthR1 = 367;
    private val apBufR1 = FloatArray(380);
    private var apIdxR1 = 0
    private val apLengthR2 = 821;
    private val apBufR2 = FloatArray(850);
    private var apIdxR2 = 0

    private val pitchBufSize = 2048
    private val pitchBufMask = pitchBufSize - 1
    private val pitchBufL = FloatArray(pitchBufSize)
    private val pitchBufR = FloatArray(pitchBufSize)
    private var pitchWrite = 0
    private var pitchPhase = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setSize(size: Float) {
        this.size = size.coerceIn(0.0f, 1.0f)
    }

    fun setShimmerMix(mix: Float) {
        this.shimmerMix = mix.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        for (i in 0 until 4) {
            combBuffersL[i].fill(0f); combBuffersR[i].fill(0f)
            combIndicesL[i] = 0; combIndicesR[i] = 0
            combFiltersL[i] = 0f; combFiltersR[i] = 0f
        }
        apBufL1.fill(0f); apIdxL1 = 0
        apBufL2.fill(0f); apIdxL2 = 0
        apBufR1.fill(0f); apIdxR1 = 0
        apBufR2.fill(0f); apIdxR2 = 0
        pitchBufL.fill(0f); pitchBufR.fill(0f)
        pitchWrite = 0; pitchPhase = 0.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val feedback = (0.75f + size * 0.20f).coerceIn(0.70f, 0.95f)
        val damp = 0.35f
        val wetMix = (0.45f + size * 0.35f).coerceIn(0.30f, 0.80f)
        val shimmerGain = shimmerMix * 0.65f

        val grainLen = 1024.0
        val pitchInc = 1.0 / grainLen

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            pitchBufL[pitchWrite] = inL * 0.4f
            pitchBufR[pitchWrite] = inR * 0.4f

            val readOffset1 = (pitchPhase * grainLen).toInt()
            val readOffset2 = (((pitchPhase + 0.5) % 1.0) * grainLen).toInt()

            val win1 = (sin(pitchPhase * PI)).toFloat()
            val win2 = (sin(((pitchPhase + 0.5) % 1.0) * PI)).toFloat()

            val pL = (pitchBufL[(pitchWrite - readOffset1 * 2 + pitchBufSize * 2) and pitchBufMask] * win1 +
                    pitchBufL[(pitchWrite - readOffset2 * 2 + pitchBufSize * 2) and pitchBufMask] * win2)
            val pR = (pitchBufR[(pitchWrite - readOffset1 * 2 + pitchBufSize * 2) and pitchBufMask] * win1 +
                    pitchBufR[(pitchWrite - readOffset2 * 2 + pitchBufSize * 2) and pitchBufMask] * win2)

            pitchWrite = (pitchWrite + 1) and pitchBufMask
            pitchPhase += pitchInc
            if (pitchPhase >= 1.0) pitchPhase -= 1.0

            val revInL = (inL * 0.35f + pL * shimmerGain)
            val revInR = (inR * 0.35f + pR * shimmerGain)

            var combSumL = 0f
            var combSumR = 0f

            for (i in 0 until 4) {
                val lenL = combLengthsL[i]
                val idxL = combIndicesL[i]
                val dL = combBuffersL[i][idxL]
                combFiltersL[i] = dL * (1f - damp) + combFiltersL[i] * damp
                combBuffersL[i][idxL] = revInL + combFiltersL[i] * feedback
                combIndicesL[i] = (idxL + 1) % lenL
                combSumL += dL

                val lenR = combLengthsR[i]
                val idxR = combIndicesR[i]
                val dR = combBuffersR[i][idxR]
                combFiltersR[i] = dR * (1f - damp) + combFiltersR[i] * damp
                combBuffersR[i][idxR] = revInR + combFiltersR[i] * feedback
                combIndicesR[i] = (idxR + 1) % lenR
                combSumR += dR
            }

            val ap1L = -combSumL * 0.5f + apBufL1[apIdxL1]
            apBufL1[apIdxL1] = combSumL + ap1L * 0.5f
            apIdxL1 = (apIdxL1 + 1) % apLength1

            val ap2L = -ap1L * 0.5f + apBufL2[apIdxL2]
            apBufL2[apIdxL2] = ap1L + ap2L * 0.5f
            apIdxL2 = (apIdxL2 + 1) % apLength2

            val ap1R = -combSumR * 0.5f + apBufR1[apIdxR1]
            apBufR1[apIdxR1] = combSumR + ap1R * 0.5f
            apIdxR1 = (apIdxR1 + 1) % apLengthR1

            val ap2R = -ap1R * 0.5f + apBufR2[apIdxR2]
            apBufR2[apIdxR2] = ap1R + ap2R * 0.5f
            apIdxR2 = (apIdxR2 + 1) % apLengthR2

            val mixL = inL * (1.0f - wetMix * 0.35f) + ap2L * wetMix
            val mixR = inR * (1.0f - wetMix * 0.35f) + ap2R * wetMix

            val satL = mixL / (1.0f + kotlin.math.abs(mixL) / 45000.0f)
            val satR = mixR / (1.0f + kotlin.math.abs(mixR) / 45000.0f)

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class RotarySpeakerAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var speed = 0.40f
    private var depth = 0.75f

    private var sampleRate = 44100
    private val bufferSize = 2048
    private val bufferMask = bufferSize - 1

    private val hornDelayL = FloatArray(bufferSize)
    private val hornDelayR = FloatArray(bufferSize)
    private var writePos = 0

    private var hornPhase = 0.0
    private var drumPhase = 0.0

    private var lpL = 0f;
    private var lpR = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setSpeed(speed: Float) {
        this.speed = speed.coerceIn(0.0f, 1.0f)
    }

    fun setDepth(depth: Float) {
        this.depth = depth.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        hornDelayL.fill(0f); hornDelayR.fill(0f)
        writePos = 0
        hornPhase = 0.0; drumPhase = 0.0
        lpL = 0f; lpR = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val hornFreqHz = 0.60 + speed.toDouble() * 6.20
        val drumFreqHz = hornFreqHz * 0.82
        val hornInc = 2.0 * PI * hornFreqHz / sampleRate
        val drumInc = 2.0 * PI * drumFreqHz / sampleRate

        val baseDelaySamples = sampleRate * 0.0055
        val dopplerModSamples = depth.toDouble() * (sampleRate * 0.0022)

        val crossoverAlpha = (2.0 * PI * 800.0 / sampleRate).coerceIn(0.05, 0.85).toFloat()

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            lpL += crossoverAlpha * (inL - lpL)
            lpR += crossoverAlpha * (inR - lpR)

            val highL = inL - lpL
            val highR = inR - lpR
            val lowL = lpL
            val lowR = lpR

            hornDelayL[writePos] = highL
            hornDelayR[writePos] = highR

            val dL = baseDelaySamples + sin(hornPhase) * dopplerModSamples
            val dR = baseDelaySamples + sin(hornPhase + PI) * dopplerModSamples

            val rL = (writePos.toDouble() - dL + bufferSize * 2) % bufferSize
            val idxL = rL.toInt() and bufferMask
            val fracL = (rL - rL.toInt()).toFloat()
            val delayedHornL = hornDelayL[idxL] * (1f - fracL) + hornDelayL[(idxL + 1) and bufferMask] * fracL

            val rR = (writePos.toDouble() - dR + bufferSize * 2) % bufferSize
            val idxR = rR.toInt() and bufferMask
            val fracR = (rR - rR.toInt()).toFloat()
            val delayedHornR = hornDelayR[idxR] * (1f - fracR) + hornDelayR[(idxR + 1) and bufferMask] * fracR

            writePos = (writePos + 1) and bufferMask

            val amHornL = (0.55 + 0.45 * sin(hornPhase)).toFloat()
            val amHornR = (0.55 + 0.45 * sin(hornPhase + PI)).toFloat()

            val amDrumL = (0.65 + 0.35 * sin(drumPhase)).toFloat()
            val amDrumR = (0.65 + 0.35 * sin(drumPhase + PI)).toFloat()

            hornPhase += hornInc
            if (hornPhase >= 2.0 * PI) hornPhase -= 2.0 * PI

            drumPhase += drumInc
            if (drumPhase >= 2.0 * PI) drumPhase -= 2.0 * PI

            val rotL = delayedHornL * amHornL + lowL * amDrumL
            val rotR = delayedHornR * amHornR + lowR * amDrumR

            val satL = rotL / (1.0f + kotlin.math.abs(rotL) / 45000.0f)
            val satR = rotR / (1.0f + kotlin.math.abs(rotR) / 45000.0f)

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class TapeSaturationAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var warmth = 0.65f
    private var exciter = 0.50f

    private var sampleRate = 44100

    private var hsB0 = 1.0;
    private var hsB1 = 0.0;
    private var hsB2 = 0.0
    private var hsA1 = 0.0;
    private var hsA2 = 0.0
    private var hs_s1_L = 0.0;
    private var hs_s2_L = 0.0
    private var hs_s1_R = 0.0;
    private var hs_s2_R = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setWarmth(warmth: Float) {
        this.warmth = warmth.coerceIn(0.0f, 1.0f)
    }

    fun setExciter(exciter: Float) {
        this.exciter = exciter.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val hsFc = 8200.0
        val hsGainDb = 1.5 + exciter.toDouble() * 4.5
        val bigA = 10.0.pow(hsGainDb / 40.0)
        val omega = 2.0 * PI * hsFc / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * 0.7071)
        val sqrtA = sqrt(bigA)

        val a0 = (bigA + 1.0) - (bigA - 1.0) * cs + 2.0 * sqrtA * alpha
        val b0 = bigA * ((bigA + 1.0) + (bigA - 1.0) * cs + 2.0 * sqrtA * alpha)
        val b1 = -2.0 * bigA * ((bigA - 1.0) + (bigA + 1.0) * cs)
        val b2 = bigA * ((bigA + 1.0) + (bigA - 1.0) * cs - 2.0 * sqrtA * alpha)
        val a1 = 2.0 * ((bigA - 1.0) - (bigA + 1.0) * cs)
        val a2 = (bigA + 1.0) - (bigA - 1.0) * cs - 2.0 * sqrtA * alpha

        hsB0 = b0 / a0
        hsB1 = b1 / a0
        hsB2 = b2 / a0
        hsA1 = a1 / a0
        hsA2 = a2 / a0
    }

    private fun resetState() {
        hs_s1_L = 0.0; hs_s2_L = 0.0
        hs_s1_R = 0.0; hs_s2_R = 0.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val driveGain = 1.0f + warmth * 1.85f
        val evenHarmonicGain = warmth * 0.22f
        val normGain = 1.0f / (1.0f + warmth * 0.40f)

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val hsL = hsB0 * inL + hs_s1_L
            hs_s1_L = hsB1 * inL - hsA1 * hsL + hs_s2_L
            hs_s2_L = hsB2 * inL - hsA2 * hsL

            val hsR = hsB0 * inR + hs_s1_R
            hs_s1_R = hsB1 * inR - hsA1 * hsR + hs_s2_R
            hs_s2_R = hsB2 * inR - hsA2 * hsR

            val xL = (hsL.toFloat() * driveGain) / 32768.0f
            val xR = (hsR.toFloat() * driveGain) / 32768.0f

            val satL =
                (kotlin.math.tanh(xL * 1.4) + evenHarmonicGain * (xL * xL) * (if (xL > 0) 1 else -1)).toFloat() * 32768.0f * normGain
            val satR =
                (kotlin.math.tanh(xR * 1.4) + evenHarmonicGain * (xR * xR) * (if (xR > 0) 1 else -1)).toFloat() * 32768.0f * normGain

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class SubOctaverAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var subLevel = 0.70f
    private var subCutoff = 0.50f

    private var sampleRate = 44100

    private var prevSign = 1
    private var flipFlop = 1f
    private var subWaveL = 0f
    private var subWaveR = 0f

    private var lpB0 = 0.0;
    private var lpB1 = 0.0;
    private var lpB2 = 0.0
    private var lpA1 = 0.0;
    private var lpA2 = 0.0
    private var sub_s1_1_L = 0.0;
    private var sub_s2_1_L = 0.0
    private var sub_s1_2_L = 0.0;
    private var sub_s2_2_L = 0.0
    private var sub_s1_1_R = 0.0;
    private var sub_s2_1_R = 0.0
    private var sub_s1_2_R = 0.0;
    private var sub_s2_2_R = 0.0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setSubLevel(level: Float) {
        this.subLevel = level.coerceIn(0.0f, 1.0f)
    }

    fun setSubCutoff(cutoff: Float) {
        this.subCutoff = cutoff.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val fc = 48.0 + subCutoff.toDouble() * 50.0
        val omega = 2.0 * PI * fc / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * 0.7071)

        val b0 = (1.0 - cs) / 2.0
        val b1 = 1.0 - cs
        val b2 = (1.0 - cs) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cs
        val a2 = 1.0 - alpha

        lpB0 = b0 / a0
        lpB1 = b1 / a0
        lpB2 = b2 / a0
        lpA1 = a1 / a0
        lpA2 = a2 / a0
    }

    private fun resetState() {
        prevSign = 1
        flipFlop = 1f
        subWaveL = 0f
        subWaveR = 0f
        sub_s1_1_L = 0.0; sub_s2_1_L = 0.0
        sub_s1_2_L = 0.0; sub_s2_2_L = 0.0
        sub_s1_1_R = 0.0; sub_s2_1_R = 0.0
        sub_s1_2_R = 0.0; sub_s2_2_R = 0.0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val subGain = subLevel * 1.55f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val monoIn = (inL + inR) * 0.5f

            val currentSign = if (monoIn >= 0f) 1 else -1
            if (currentSign == 1 && prevSign == -1) {
                flipFlop = -flipFlop
            }
            prevSign = currentSign

            val env = kotlin.math.abs(monoIn)
            val rawSub = flipFlop * env

            val lp1 = lpB0 * rawSub + sub_s1_1_L
            sub_s1_1_L = lpB1 * rawSub - lpA1 * lp1 + sub_s2_1_L
            sub_s2_1_L = lpB2 * rawSub - lpA2 * lp1

            val lp2 = lpB0 * lp1 + sub_s1_2_L
            sub_s1_2_L = lpB1 * lp1 - lpA1 * lp2 + sub_s2_2_L
            sub_s2_2_L = lpB2 * lp1 - lpA2 * lp2

            val deepSub = lp2.toFloat() * subGain

            val mixL = inL + deepSub
            val mixR = inR + deepSub

            val satL = mixL / (1.0f + kotlin.math.abs(mixL) / 48000.0f)
            val satR = mixR / (1.0f + kotlin.math.abs(mixR) / 48000.0f)

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class EmptyMallAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var distance = 0.65f
    private var glassReverb = 0.55f

    private var sampleRate = 44100

    private var lpB0 = 0.0;
    private var lpB1 = 0.0;
    private var lpB2 = 0.0
    private var lpA1 = 0.0;
    private var lpA2 = 0.0
    private var dist_s1_L = 0.0;
    private var dist_s2_L = 0.0
    private var dist_s1_R = 0.0;
    private var dist_s2_R = 0.0

    private val combLengthsL = intArrayOf(1423, 1619, 1867, 2131, 2477, 2789)
    private val combLengthsR = intArrayOf(1447, 1637, 1889, 2153, 2503, 2819)
    private val combBuffersL = Array(6) { FloatArray(3000) }
    private val combBuffersR = Array(6) { FloatArray(3000) }
    private val combIndicesL = IntArray(6)
    private val combIndicesR = IntArray(6)
    private val combFiltersL = FloatArray(6)
    private val combFiltersR = FloatArray(6)

    private val apLen1 = 443;
    private val apBufL1 = FloatArray(460);
    private var apIdxL1 = 0
    private val apLen2 = 919;
    private val apBufL2 = FloatArray(940);
    private var apIdxL2 = 0
    private val apLenR1 = 461;
    private val apBufR1 = FloatArray(480);
    private var apIdxR1 = 0
    private val apLenR2 = 941;
    private val apBufR2 = FloatArray(960);
    private var apIdxR2 = 0

    private var hvacRng = 987654321L

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setDistance(distance: Float) {
        this.distance = distance.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    fun setGlassReverb(reverb: Float) {
        this.glassReverb = reverb.coerceIn(0.0f, 1.0f)
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val fc = 650.0 + (1.0 - distance.toDouble()).pow(1.6) * 1550.0
        val omega = 2.0 * PI * fc / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * 0.7071)

        val b0 = (1.0 - cs) / 2.0
        val b1 = 1.0 - cs
        val b2 = (1.0 - cs) / 2.0
        val a0 = 1.0 + alpha
        val a1 = -2.0 * cs
        val a2 = 1.0 - alpha

        lpB0 = b0 / a0
        lpB1 = b1 / a0
        lpB2 = b2 / a0
        lpA1 = a1 / a0
        lpA2 = a2 / a0
    }

    private fun resetState() {
        dist_s1_L = 0.0; dist_s2_L = 0.0
        dist_s1_R = 0.0; dist_s2_R = 0.0
        for (i in 0 until 6) {
            combBuffersL[i].fill(0f); combBuffersR[i].fill(0f)
            combIndicesL[i] = 0; combIndicesR[i] = 0
            combFiltersL[i] = 0f; combFiltersR[i] = 0f
        }
        apBufL1.fill(0f); apIdxL1 = 0
        apBufL2.fill(0f); apIdxL2 = 0
        apBufR1.fill(0f); apIdxR1 = 0
        apBufR2.fill(0f); apIdxR2 = 0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val feedback = 0.78f + glassReverb * 0.16f
        val damp = 0.22f
        val wetReverbMix = glassReverb * 0.75f
        val hvacAirGain = distance * 22.0f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val distL = lpB0 * inL + dist_s1_L
            dist_s1_L = lpB1 * inL - lpA1 * distL + dist_s2_L
            dist_s2_L = lpB2 * inL - lpA2 * distL

            val distR = lpB0 * inR + dist_s1_R
            dist_s1_R = lpB1 * inR - lpA1 * distR + dist_s2_R
            dist_s2_R = lpB2 * inR - lpA2 * distR

            val revInL = distL.toFloat() * 0.40f
            val revInR = distR.toFloat() * 0.40f

            var combSumL = 0f
            var combSumR = 0f

            for (i in 0 until 6) {
                val lenL = combLengthsL[i]
                val idxL = combIndicesL[i]
                val dL = combBuffersL[i][idxL]
                combFiltersL[i] = dL * (1f - damp) + combFiltersL[i] * damp
                combBuffersL[i][idxL] = revInL + combFiltersL[i] * feedback
                combIndicesL[i] = (idxL + 1) % lenL
                combSumL += dL

                val lenR = combLengthsR[i]
                val idxR = combIndicesR[i]
                val dR = combBuffersR[i][idxR]
                combFiltersR[i] = dR * (1f - damp) + combFiltersR[i] * damp
                combBuffersR[i][idxR] = revInR + combFiltersR[i] * feedback
                combIndicesR[i] = (idxR + 1) % lenR
                combSumR += dR
            }

            val ap1L = -combSumL * 0.5f + apBufL1[apIdxL1]
            apBufL1[apIdxL1] = combSumL + ap1L * 0.5f
            apIdxL1 = (apIdxL1 + 1) % apLen1

            val ap2L = -ap1L * 0.5f + apBufL2[apIdxL2]
            apBufL2[apIdxL2] = ap1L + ap2L * 0.5f
            apIdxL2 = (apIdxL2 + 1) % apLen2

            val ap1R = -combSumR * 0.5f + apBufR1[apIdxR1]
            apBufR1[apIdxR1] = combSumR + ap1R * 0.5f
            apIdxR1 = (apIdxR1 + 1) % apLenR1

            val ap2R = -ap1R * 0.5f + apBufR2[apIdxR2]
            apBufR2[apIdxR2] = ap1R + ap2R * 0.5f
            apIdxR2 = (apIdxR2 + 1) % apLenR2

            hvacRng = (hvacRng * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val hvacNoise = ((hvacRng.toFloat() / 4294967296.0f) * 2.0f - 1.0f) * hvacAirGain

            val mixL = distL.toFloat() * (1.1f - wetReverbMix * 0.4f) + ap2L * wetReverbMix + hvacNoise
            val mixR = distR.toFloat() * (1.1f - wetReverbMix * 0.4f) + ap2R * wetReverbMix + hvacNoise

            val satL = (mixL / (1.0f + kotlin.math.abs(mixL) / 45000.0f)) * 1.30f
            val satR = (mixR / (1.0f + kotlin.math.abs(mixR) / 45000.0f)) * 1.30f

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class GramophoneAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var shellacAge = 0.65f
    private var hornResonance = 0.60f

    private var sampleRate = 44100

    private var hpfB0 = 1.0;
    private var hpfB1 = -2.0;
    private var hpfB2 = 1.0
    private var hpfA1 = 0.0;
    private var hpfA2 = 0.0
    private var hpf_s1 = 0.0;
    private var hpf_s2 = 0.0

    private var lpfB0 = 1.0;
    private var lpfB1 = 2.0;
    private var lpfB2 = 1.0
    private var lpfA1 = 0.0;
    private var lpfA2 = 0.0
    private var lpf_s1 = 0.0;
    private var lpf_s2 = 0.0

    private var resB0 = 1.0;
    private var resB1 = 0.0;
    private var resB2 = 0.0
    private var resA1 = 0.0;
    private var resA2 = 0.0
    private var res_s1 = 0.0;
    private var res_s2 = 0.0

    private val wobbleMask = 1023
    private val wobbleBuffer = FloatArray(1024)
    private var wobbleWrite = 0
    private var wobblePhase = 0.0

    private var gramoRng = 456789123L
    private var crackleFilter = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setShellacAge(age: Float) {
        this.shellacAge = age.coerceIn(0.0f, 1.0f)
    }

    fun setHornResonance(resonance: Float) {
        this.hornResonance = resonance.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val hpfFc = 340.0
        val omegaH = 2.0 * PI * hpfFc / sampleRate
        val snH = sin(omegaH);
        val csH = cos(omegaH);
        val alphaH = snH / (2.0 * 0.7071)
        val hb0 = (1.0 + csH) / 2.0;
        val hb1 = -(1.0 + csH);
        val hb2 = (1.0 + csH) / 2.0
        val ha0 = 1.0 + alphaH;
        val ha1 = -2.0 * csH;
        val ha2 = 1.0 - alphaH
        hpfB0 = hb0 / ha0; hpfB1 = hb1 / ha0; hpfB2 = hb2 / ha0; hpfA1 = ha1 / ha0; hpfA2 = ha2 / ha0

        val lpfFc = 2900.0
        val omegaL = 2.0 * PI * lpfFc / sampleRate
        val snL = sin(omegaL);
        val csL = cos(omegaL);
        val alphaL = snL / (2.0 * 0.7071)
        val lb0 = (1.0 - csL) / 2.0;
        val lb1 = 1.0 - csL;
        val lb2 = (1.0 - csL) / 2.0
        val la0 = 1.0 + alphaL;
        val la1 = -2.0 * csL;
        val la2 = 1.0 - alphaL
        lpfB0 = lb0 / la0; lpfB1 = lb1 / la0; lpfB2 = lb2 / la0; lpfA1 = la1 / la0; lpfA2 = la2 / la0

        val resFc = 1450.0
        val resGainDb = 10.0 + hornResonance.toDouble() * 10.0
        val bigA = 10.0.pow(resGainDb / 40.0)
        val omegaR = 2.0 * PI * resFc / sampleRate
        val snR = sin(omegaR);
        val csR = cos(omegaR);
        val alphaR = snR / (2.0 * 2.8)
        val rb0 = 1.0 + alphaR * bigA;
        val rb1 = -2.0 * csR;
        val rb2 = 1.0 - alphaR * bigA
        val ra0 = 1.0 + alphaR / bigA;
        val ra1 = -2.0 * csR;
        val ra2 = 1.0 - alphaR / bigA
        resB0 = rb0 / ra0; resB1 = rb1 / ra0; resB2 = rb2 / ra0; resA1 = ra1 / ra0; resA2 = ra2 / ra0
    }

    private fun resetState() {
        hpf_s1 = 0.0; hpf_s2 = 0.0
        lpf_s1 = 0.0; lpf_s2 = 0.0
        res_s1 = 0.0; res_s2 = 0.0
        wobbleBuffer.fill(0f)
        wobbleWrite = 0
        wobblePhase = 0.0
        crackleFilter = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val wobbleInc = 2.0 * PI * 1.30 / sampleRate
        val wobbleDepth = (sampleRate * 0.0035).toDouble()
        val maxDelay = (sampleRate * 0.006).toDouble()

        val crackleProb = 0.00065f + shellacAge * 0.0038f
        val crackleGain = shellacAge * 5200.0f
        val needleHissGain = shellacAge * 85.0f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val monoIn = (inL + inR) * 0.5f

            val hpOut = hpfB0 * monoIn + hpf_s1
            hpf_s1 = hpfB1 * monoIn - hpfA1 * hpOut + hpf_s2
            hpf_s2 = hpfB2 * monoIn - hpfA2 * hpOut

            val lpOut = lpfB0 * hpOut + lpf_s1
            lpf_s1 = lpfB1 * hpOut - lpfA1 * lpOut + lpf_s2
            lpf_s2 = lpfB2 * hpOut - lpfA2 * lpOut

            val resOut = resB0 * lpOut + res_s1
            res_s1 = resB1 * lpOut - resA1 * resOut + res_s2
            res_s2 = resB2 * lpOut - resA2 * resOut

            wobbleBuffer[wobbleWrite] = resOut.toFloat()

            val currentDelay = maxDelay + sin(wobblePhase) * wobbleDepth
            wobblePhase += wobbleInc
            if (wobblePhase >= 2.0 * PI) wobblePhase -= 2.0 * PI

            val dInt = currentDelay.toInt()
            val dFrac = (currentDelay - dInt).toFloat()
            val r0 = (wobbleWrite - dInt + 1024) and wobbleMask
            val r1 = (wobbleWrite - dInt - 1 + 1024) and wobbleMask
            val wobbled = wobbleBuffer[r0] * (1.0f - dFrac) + wobbleBuffer[r1] * dFrac

            wobbleWrite = (wobbleWrite + 1) and wobbleMask

            gramoRng = (gramoRng * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val randVal = (gramoRng.toFloat() / 4294967296.0f)

            var cracklePulse = 0f
            if (randVal < crackleProb) {
                cracklePulse = ((randVal / crackleProb) * 2.0f - 1.0f).pow(3) * crackleGain
            }
            crackleFilter = crackleFilter * 0.75f + cracklePulse

            val needleHiss = ((gramoRng.toFloat() / 4294967296.0f) * 2.0f - 1.0f) * needleHissGain

            val driven = (wobbled * 1.35f + crackleFilter + needleHiss) / 26000.0f
            val clipped = kotlin.math.tanh(driven * 2.4f) * 32768.0f * 0.85f

            val finalSample = clipped.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(finalSample)
            buffer.putShort(finalSample)
        }
        buffer.flip()
    }
}

class ReverseEchoAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var time = 0.50f
    private var feedback = 0.55f

    private var sampleRate = 44100
    private val bufferSize = 65536
    private val bufferMask = bufferSize - 1

    private val recordBufferL = FloatArray(bufferSize)
    private val recordBufferR = FloatArray(bufferSize)
    private var writePos = 0

    private var grainPhase = 0.0
    private var fbLpL = 0f
    private var fbLpR = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setTime(time: Float) {
        this.time = time.coerceIn(0.0f, 1.0f)
    }

    fun setFeedback(feedback: Float) {
        this.feedback = feedback.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        recordBufferL.fill(0f); recordBufferR.fill(0f)
        writePos = 0
        grainPhase = 0.0
        fbLpL = 0f; fbLpR = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val grainLen = (sampleRate * (0.15 + time.toDouble() * 0.50)).toInt().coerceIn(2000, 32000)
        val grainInc = 1.0 / grainLen.toDouble()
        val fbGain = (0.15f + feedback * 0.60f).coerceIn(0.10f, 0.75f)
        val tapeAlpha = (2.0 * PI * 3800.0 / sampleRate).coerceIn(0.10, 0.85).toFloat()

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val p1 = (grainPhase * grainLen).toInt()
            val p2 = (((grainPhase + 0.5) % 1.0) * grainLen).toInt()

            val win1 = (sin(grainPhase * PI)).toFloat()
            val win2 = (sin(((grainPhase + 0.5) % 1.0) * PI)).toFloat()

            val readPos1 = (writePos - grainLen + (grainLen - p1) + bufferSize * 2) and bufferMask
            val readPos2 = (writePos - grainLen + (grainLen - p2) + bufferSize * 2) and bufferMask

            val revL = recordBufferL[readPos1] * win1 + recordBufferL[readPos2] * win2
            val revR = recordBufferR[readPos1] * win1 + recordBufferR[readPos2] * win2

            fbLpL += tapeAlpha * (revL - fbLpL)
            fbLpR += tapeAlpha * (revR - fbLpR)

            val fbInL = inL + fbLpL * fbGain
            val fbInR = inR + fbLpR * fbGain

            recordBufferL[writePos] = fbInL / (1.0f + kotlin.math.abs(fbInL) / 45000.0f)
            recordBufferR[writePos] = fbInR / (1.0f + kotlin.math.abs(fbInR) / 45000.0f)

            writePos = (writePos + 1) and bufferMask

            grainPhase += grainInc
            if (grainPhase >= 1.0) grainPhase -= 1.0

            val mixL = inL * 0.85f + revL * 0.70f
            val mixR = inR * 0.85f + revR * 0.70f

            val outL = mixL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = mixR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class StadiumAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var stadiumSize = 0.65f
    private var atmosphere = 0.60f

    private var sampleRate = 44100

    private val erBufferSize = 32768
    private val erBufferMask = erBufferSize - 1
    private val erBufferL = FloatArray(erBufferSize)
    private val erBufferR = FloatArray(erBufferSize)
    private var erWrite = 0

    private val erTapsMs = doubleArrayOf(32.0, 68.0, 105.0, 142.0, 185.0, 228.0, 275.0, 320.0)
    private val erGains = floatArrayOf(0.45f, 0.38f, 0.32f, 0.28f, 0.22f, 0.18f, 0.14f, 0.10f)

    private val combLengthsL = intArrayOf(2687, 3163, 3719, 4273)
    private val combLengthsR = intArrayOf(2711, 3191, 3761, 4327)
    private val combBuffersL = Array(4) { FloatArray(5000) }
    private val combBuffersR = Array(4) { FloatArray(5000) }
    private val combIndicesL = IntArray(4)
    private val combIndicesR = IntArray(4)
    private val combFiltersL = FloatArray(4)
    private val combFiltersR = FloatArray(4)

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
    }

    fun setStadiumSize(size: Float) {
        this.stadiumSize = size.coerceIn(0.0f, 1.0f)
    }

    fun setAtmosphere(atmosphere: Float) {
        this.atmosphere = atmosphere.coerceIn(0.0f, 1.0f)
    }

    private fun resetState() {
        erBufferL.fill(0f); erBufferR.fill(0f)
        erWrite = 0
        for (i in 0 until 4) {
            combBuffersL[i].fill(0f); combBuffersR[i].fill(0f)
            combIndicesL[i] = 0; combIndicesR[i] = 0
            combFiltersL[i] = 0f; combFiltersR[i] = 0f
        }
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val sizeFactor = 0.5f + stadiumSize * 0.75f
        val erGainMult = 0.35f + stadiumSize * 0.45f
        val reverbFeedback = (0.75f + atmosphere * 0.17f).coerceIn(0.70f, 0.94f)
        val atmosphericDamp = 0.42f
        val wetMix = 0.25f + atmosphere * 0.45f

        val tapSamples = IntArray(8) { i ->
            ((erTapsMs[i] * sizeFactor * 0.001 * sampleRate).toInt()).coerceIn(100, erBufferSize - 1)
        }

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            erBufferL[erWrite] = inL
            erBufferR[erWrite] = inR

            var erSumL = 0f
            var erSumR = 0f

            for (i in 0 until 8) {
                val rPos = (erWrite - tapSamples[i] + erBufferSize) and erBufferMask
                val g = erGains[i] * erGainMult
                if (i % 2 == 0) {
                    erSumL += erBufferL[rPos] * g
                    erSumR += erBufferR[rPos] * (g * 0.7f)
                } else {
                    erSumL += erBufferL[rPos] * (g * 0.7f)
                    erSumR += erBufferR[rPos] * g
                }
            }

            erWrite = (erWrite + 1) and erBufferMask

            val revInL = (inL + erSumL) * 0.25f
            val revInR = (inR + erSumR) * 0.25f

            var tailL = 0f
            var tailR = 0f

            for (i in 0 until 4) {
                val lenL = combLengthsL[i]
                val idxL = combIndicesL[i]
                val dL = combBuffersL[i][idxL]
                combFiltersL[i] = dL * (1f - atmosphericDamp) + combFiltersL[i] * atmosphericDamp
                combBuffersL[i][idxL] = revInL + combFiltersL[i] * reverbFeedback
                combIndicesL[i] = (idxL + 1) % lenL
                tailL += dL

                val lenR = combLengthsR[i]
                val idxR = combIndicesR[i]
                val dR = combBuffersR[i][idxR]
                combFiltersR[i] = dR * (1f - atmosphericDamp) + combFiltersR[i] * atmosphericDamp
                combBuffersR[i][idxR] = revInR + combFiltersR[i] * reverbFeedback
                combIndicesR[i] = (idxR + 1) % lenR
                tailR += dR
            }

            val outDirectL = inL * (1.0f - wetMix * 0.3f)
            val outDirectR = inR * (1.0f - wetMix * 0.3f)

            val wetL = erSumL + tailL * 0.55f
            val wetR = erSumR + tailR * 0.55f

            val mixL = outDirectL + wetL * wetMix
            val mixR = outDirectR + wetR * wetMix

            val satL = mixL / (1.0f + kotlin.math.abs(mixL) / 48000.0f)
            val satR = mixR / (1.0f + kotlin.math.abs(mixR) / 48000.0f)

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class CassetteWalkmanAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var drive = 0.65f
    private var tapeHiss = 0.40f

    private var sampleRate = 44100

    private var midB0 = 1.0;
    private var midB1 = 0.0;
    private var midB2 = 0.0
    private var midA1 = 0.0;
    private var midA2 = 0.0
    private var mid_s1_L = 0.0;
    private var mid_s2_L = 0.0
    private var mid_s1_R = 0.0;
    private var mid_s2_R = 0.0

    private var rollB0 = 1.0;
    private var rollB1 = 0.0;
    private var rollB2 = 0.0
    private var rollA1 = 0.0;
    private var rollA2 = 0.0
    private var roll_s1_L = 0.0;
    private var roll_s2_L = 0.0
    private var roll_s1_R = 0.0;
    private var roll_s2_R = 0.0

    private var hissRng = 135792468L
    private var hissFilterL = 0f
    private var hissFilterR = 0f

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setDrive(drive: Float) {
        this.drive = drive.coerceIn(0.0f, 1.0f)
    }

    fun setTapeHiss(hiss: Float) {
        this.tapeHiss = hiss.coerceIn(0.0f, 1.0f)
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val fcMid = 1800.0
        val gainDbMid = 3.5
        val bigAMid = 10.0.pow(gainDbMid / 40.0)
        val omegaM = 2.0 * PI * fcMid / sampleRate
        val snM = sin(omegaM);
        val csM = cos(omegaM);
        val alphaM = snM / (2.0 * 1.4)
        val mb0 = 1.0 + alphaM * bigAMid;
        val mb1 = -2.0 * csM;
        val mb2 = 1.0 - alphaM * bigAMid
        val ma0 = 1.0 + alphaM / bigAMid;
        val ma1 = -2.0 * csM;
        val ma2 = 1.0 - alphaM / bigAMid
        midB0 = mb0 / ma0; midB1 = mb1 / ma0; midB2 = mb2 / ma0; midA1 = ma1 / ma0; midA2 = ma2 / ma0

        val fcRoll = 10500.0
        val omegaR = 2.0 * PI * fcRoll / sampleRate
        val snR = sin(omegaR);
        val csR = cos(omegaR);
        val alphaR = snR / (2.0 * 0.7071)
        val rb0 = (1.0 - csR) / 2.0;
        val rb1 = 1.0 - csR;
        val rb2 = (1.0 - csR) / 2.0
        val ra0 = 1.0 + alphaR;
        val ra1 = -2.0 * csR;
        val ra2 = 1.0 - alphaR
        rollB0 = rb0 / ra0; rollB1 = rb1 / ra0; rollB2 = rb2 / ra0; rollA1 = ra1 / ra0; rollA2 = ra2 / ra0
    }

    private fun resetState() {
        mid_s1_L = 0.0; mid_s2_L = 0.0
        mid_s1_R = 0.0; mid_s2_R = 0.0
        roll_s1_L = 0.0; roll_s2_L = 0.0
        roll_s1_R = 0.0; roll_s2_R = 0.0
        hissFilterL = 0f; hissFilterR = 0f
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val tapeGain = 1.0f + drive * 1.6f
        val hissAmp = tapeHiss * 95.0f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val midL = midB0 * inL + mid_s1_L
            mid_s1_L = midB1 * inL - midA1 * midL + mid_s2_L
            mid_s2_L = midB2 * inL - midA2 * midL

            val midR = midB0 * inR + mid_s1_R
            mid_s1_R = midB1 * inR - midA1 * midR + mid_s2_R
            mid_s2_R = midB2 * inR - midA2 * midR

            val rollL = rollB0 * midL + roll_s1_L
            roll_s1_L = rollB1 * midL - rollA1 * rollL + roll_s2_L
            roll_s2_L = rollB2 * midL - rollA2 * rollL

            val rollR = rollB0 * midR + roll_s1_R
            roll_s1_R = rollB1 * midR - rollA1 * rollR + roll_s2_R
            roll_s2_R = rollB2 * midR - rollA2 * rollR

            val xL = (rollL.toFloat() * tapeGain) / 32768.0f
            val xR = (rollR.toFloat() * tapeGain) / 32768.0f

            val satL = kotlin.math.tanh(xL * 1.55) * 32768.0f * (1.0f / (1.0f + drive * 0.35f))
            val satR = kotlin.math.tanh(xR * 1.55) * 32768.0f * (1.0f / (1.0f + drive * 0.35f))

            hissRng = (hissRng * 1664525L + 1013904223L) and 0xFFFFFFFFL
            val rawHiss = ((hissRng.toFloat() / 4294967296.0f) * 2.0f - 1.0f)
            hissFilterL = hissFilterL * 0.6f + rawHiss * 0.4f * hissAmp
            hissFilterR = hissFilterR * 0.6f + rawHiss * 0.4f * hissAmp

            val outL =
                (satL.toFloat() + hissFilterL).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt()
                    .toShort()
            val outR =
                (satR.toFloat() + hissFilterR).coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt()
                    .toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}

class AsmrVocalAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var proximity = 0.70f
    private var airSheen = 0.65f

    private var sampleRate = 44100

    private var airB0 = 1.0;
    private var airB1 = 0.0;
    private var airB2 = 0.0
    private var airA1 = 0.0;
    private var airA2 = 0.0
    private var air_s1_L = 0.0;
    private var air_s2_L = 0.0
    private var air_s1_R = 0.0;
    private var air_s2_R = 0.0

    private val binBufferSize = 256
    private val binBufferMask = binBufferSize - 1
    private val binBufferL = FloatArray(binBufferSize)
    private val binBufferR = FloatArray(binBufferSize)
    private var binWrite = 0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setProximity(proximity: Float) {
        this.proximity = proximity.coerceIn(0.0f, 1.0f)
    }

    fun setAirSheen(sheen: Float) {
        this.airSheen = sheen.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val hsFc = 12200.0
        val hsGainDb = 3.0 + airSheen.toDouble() * 5.5
        val bigA = 10.0.pow(hsGainDb / 40.0)
        val omega = 2.0 * PI * hsFc / sampleRate
        val sn = sin(omega)
        val cs = cos(omega)
        val alpha = sn / (2.0 * 0.7071)
        val sqrtA = sqrt(bigA)

        val a0 = (bigA + 1.0) - (bigA - 1.0) * cs + 2.0 * sqrtA * alpha
        val b0 = bigA * ((bigA + 1.0) + (bigA - 1.0) * cs + 2.0 * sqrtA * alpha)
        val b1 = -2.0 * bigA * ((bigA - 1.0) + (bigA + 1.0) * cs)
        val b2 = bigA * ((bigA + 1.0) + (bigA - 1.0) * cs - 2.0 * sqrtA * alpha)
        val a1 = 2.0 * ((bigA - 1.0) - (bigA + 1.0) * cs)
        val a2 = (bigA + 1.0) - (bigA - 1.0) * cs - 2.0 * sqrtA * alpha

        airB0 = b0 / a0; airB1 = b1 / a0; airB2 = b2 / a0; airA1 = a1 / a0; airA2 = a2 / a0
    }

    private fun resetState() {
        air_s1_L = 0.0; air_s2_L = 0.0
        air_s1_R = 0.0; air_s2_R = 0.0
        binBufferL.fill(0f); binBufferR.fill(0f)
        binWrite = 0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val delaySamples = (sampleRate * 0.00035).toInt().coerceIn(4, binBufferSize - 1)
        val centerBoost = proximity * 0.45f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val airL = airB0 * inL + air_s1_L
            air_s1_L = airB1 * inL - airA1 * airL + air_s2_L
            air_s2_L = airB2 * inL - airA2 * airL

            val airR = airB0 * inR + air_s1_R
            air_s1_R = airB1 * inR - airA1 * airR + air_s2_R
            air_s2_R = airB2 * inR - airA2 * airR

            val mid = (airL + airR) * 0.5f
            val side = (airL - airR) * 0.5f

            val intimateMid = mid * (1.0f + centerBoost)

            binBufferL[binWrite] = (intimateMid + side * 0.85f).toFloat()
            binBufferR[binWrite] = (intimateMid - side * 0.85f).toFloat()

            val delayedL = binBufferL[(binWrite - delaySamples + binBufferSize) and binBufferMask]
            val delayedR = binBufferR[(binWrite - delaySamples + binBufferSize) and binBufferMask]

            binWrite = (binWrite + 1) and binBufferMask

            val outSampleL = (binBufferL[binWrite] * 0.75f + delayedR * 0.25f)
            val outSampleR = (binBufferR[binWrite] * 0.75f + delayedL * 0.25f)

            val finalL = outSampleL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val finalR = outSampleR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(finalL)
            buffer.putShort(finalR)
        }
        buffer.flip()
    }
}

class NightDriveAudioProcessor : BaseAudioProcessor() {
    private var enabled = false
    private var cabinWidth = 0.65f
    private var roadRumble = 0.45f

    private var sampleRate = 44100

    private var subB0 = 1.0;
    private var subB1 = 0.0;
    private var subB2 = 0.0
    private var subA1 = 0.0;
    private var subA2 = 0.0
    private var sub_s1_L = 0.0;
    private var sub_s2_L = 0.0
    private var sub_s1_R = 0.0;
    private var sub_s2_R = 0.0

    private val cabinBufSize = 2048
    private val cabinMask = cabinBufSize - 1
    private val cabinBufL = FloatArray(cabinBufSize)
    private val cabinBufR = FloatArray(cabinBufSize)
    private var cabinWrite = 0

    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        if (!enabled) resetState()
        else updateCoefficients()
    }

    fun setCabinWidth(width: Float) {
        this.cabinWidth = width.coerceIn(0.0f, 1.0f)
    }

    fun setRoadRumble(rumble: Float) {
        this.roadRumble = rumble.coerceIn(0.0f, 1.0f)
        updateCoefficients()
    }

    private fun updateCoefficients() {
        if (sampleRate <= 0) return

        val subFc = 55.0
        val subGainDb = 4.5 + roadRumble.toDouble() * 5.0
        val bigA = 10.0.pow(subGainDb / 40.0)
        val omega = 2.0 * PI * subFc / sampleRate
        val sn = sin(omega);
        val cs = cos(omega);
        val alpha = sn / (2.0 * 1.6)
        val b0 = 1.0 + alpha * bigA;
        val b1 = -2.0 * cs;
        val b2 = 1.0 - alpha * bigA
        val a0 = 1.0 + alpha / bigA;
        val a1 = -2.0 * cs;
        val a2 = 1.0 - alpha / bigA

        subB0 = b0 / a0; subB1 = b1 / a0; subB2 = b2 / a0; subA1 = a1 / a0; subA2 = a2 / a0
    }

    private fun resetState() {
        sub_s1_L = 0.0; sub_s2_L = 0.0
        sub_s1_R = 0.0; sub_s2_R = 0.0
        cabinBufL.fill(0f); cabinBufR.fill(0f)
        cabinWrite = 0
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        updateCoefficients()
        resetState()
        return inputAudioFormat
    }

    override fun onFlush() {
        resetState()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || inputAudioFormat.channelCount != 2) {
            val buffer = replaceOutputBuffer(remaining)
            buffer.put(inputBuffer)
            buffer.flip()
            return
        }

        val buffer = replaceOutputBuffer(remaining)

        val d1 = (sampleRate * 0.008).toInt().coerceIn(10, cabinBufSize - 1)
        val d2 = (sampleRate * 0.018).toInt().coerceIn(20, cabinBufSize - 1)
        val cabinWet = cabinWidth * 0.40f

        while (inputBuffer.remaining() >= 4) {
            val inL = inputBuffer.getShort().toFloat()
            val inR = inputBuffer.getShort().toFloat()

            val subL = subB0 * inL + sub_s1_L
            sub_s1_L = subB1 * inL - subA1 * subL + sub_s2_L
            sub_s2_L = subB2 * inL - subA2 * subL

            val subR = subB0 * inR + sub_s1_R
            sub_s1_R = subB1 * inR - subA1 * subR + sub_s2_R
            sub_s2_R = subB2 * inR - subA2 * subR

            cabinBufL[cabinWrite] = subL.toFloat()
            cabinBufR[cabinWrite] = subR.toFloat()

            val refL = cabinBufL[(cabinWrite - d1 + cabinBufSize) and cabinMask] * 0.35f +
                    cabinBufR[(cabinWrite - d2 + cabinBufSize) and cabinMask] * 0.22f
            val refR = cabinBufR[(cabinWrite - d1 + cabinBufSize) and cabinMask] * 0.35f +
                    cabinBufL[(cabinWrite - d2 + cabinBufSize) and cabinMask] * 0.22f

            cabinWrite = (cabinWrite + 1) and cabinMask

            val mixL = subL.toFloat() * 0.90f + refL * cabinWet
            val mixR = subR.toFloat() * 0.90f + refR * cabinWet

            val satL = mixL / (1.0f + kotlin.math.abs(mixL) / 48000.0f)
            val satR = mixR / (1.0f + kotlin.math.abs(mixR) / 48000.0f)

            val outL = satL.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
            val outR = satR.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()

            buffer.putShort(outL)
            buffer.putShort(outR)
        }
        buffer.flip()
    }
}
