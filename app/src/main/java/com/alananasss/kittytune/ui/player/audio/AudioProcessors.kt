    package com.alananasss.kittytune.ui.player.audio
    
    import androidx.media3.common.audio.AudioProcessor
    import androidx.media3.common.audio.BaseAudioProcessor
    import java.nio.ByteBuffer
    import kotlin.math.PI
    import kotlin.math.cos
    import kotlin.math.sin
    
    // --- 1. 8D AUDIO (Auto-Pan) ---
    class EightDAudioProcessor : BaseAudioProcessor() {
        private var enabled = false
        private var time: Double = 0.0
    
        fun setEnabled(enabled: Boolean) {
            this.enabled = enabled
            if (!enabled) time = 0.0
        }
    
        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            return inputAudioFormat // no format change
        }
    
        override fun onFlush() {
            if (enabled) {
                time = 0.0
            }
        }
    
        override fun queueInput(inputBuffer: ByteBuffer) {
            val remaining = inputBuffer.remaining()
            if (remaining == 0) return
    
            // if disabled, just pass through
            if (!enabled) {
                val buffer = replaceOutputBuffer(remaining)
                buffer.put(inputBuffer)
                buffer.flip()
                return
            }
    
            // make sure we have room
            val buffer = replaceOutputBuffer(remaining)
    
            while (inputBuffer.hasRemaining()) {
                // stereo required for 8d effect
                if (inputAudioFormat.channelCount == 2) {
                    // read 16-bit pcm
                    val left = inputBuffer.getShort().toFloat()
                    val right = inputBuffer.getShort().toFloat()
    
                    // simple oscillator logic
                    time += 0.00001 // rotation speed
                    val pan = sin(time) // -1.0 to 1.0
    
                    // volume modulation
                    val leftVol = (1.0 - pan) / 2.0
                    val rightVol = (1.0 + pan) / 2.0
    
                    val newLeft = (left * leftVol).toInt().toShort()
                    val newRight = (right * rightVol).toInt().toShort()
    
                    buffer.putShort(newLeft)
                    buffer.putShort(newRight)
                } else {
                    // mono pass-through
                    buffer.putShort(inputBuffer.getShort())
                }
            }
            buffer.flip()
        }
    }
    
    // --- 2. MULTI-FX (Simultaneous Bass Boost + Muffled) ---
    class FxAudioProcessor : BaseAudioProcessor() {
    
        private var isMuffled = false
        private var isBassBoost = false
    
        // --- MUFFLED filter vars (Low Pass) ---
        // coefficients
        private var b0_m = 0f; private var b1_m = 0f; private var b2_m = 0f
        private var a1_m = 0f; private var a2_m = 0f
        // history (state)
        private var x1_m = 0f; private var x2_m = 0f
        private var y1_m = 0f; private var y2_m = 0f
    
        // --- BASS BOOST filter vars (Low Shelf) ---
        // coefficients
        private var b0_b = 0f; private var b1_b = 0f; private var b2_b = 0f
        private var a1_b = 0f; private var a2_b = 0f
        // history (state)
        private var x1_b = 0f; private var x2_b = 0f
        private var y1_b = 0f; private var y2_b = 0f
    
        fun setEffects(muffled: Boolean, bassBoost: Boolean) {
            if (this.isMuffled != muffled || this.isBassBoost != bassBoost) {
                this.isMuffled = muffled
                this.isBassBoost = bassBoost
                // reset states to avoid audio pops when switching
                resetStates()
                calculateCoefficients()
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
    
            // 1. calc muffled coeffs (low pass)
            if (isMuffled) {
                val f0 = 800f // cutoff freq
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
    
            // 2. calc bass boost coeffs (low shelf)
            if (isBassBoost) {
                val f0 = 100f // frequency
                val gain = 10f // gain in dB
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
    
            // if no effects, simple pass-through
            if (!isMuffled && !isBassBoost) {
                val buffer = replaceOutputBuffer(remaining)
                buffer.put(inputBuffer)
                buffer.flip()
                return
            }
    
            val buffer = replaceOutputBuffer(remaining)
    
            while (inputBuffer.hasRemaining()) {
                // read original sample
                var x = inputBuffer.getShort().toFloat()
    
                // step 1: apply muffled if active
                if (isMuffled) {
                    val y = b0_m * x + b1_m * x1_m + b2_m * x2_m - a1_m * y1_m - a2_m * y2_m
                    // update muffled history
                    x2_m = x1_m; x1_m = x
                    y2_m = y1_m; y1_m = y
                    // sample becomes filter result
                    x = y
                }
    
                // step 2: apply bass boost if active (on previous result)
                if (isBassBoost) {
                    val y = b0_b * x + b1_b * x1_b + b2_b * x2_b - a1_b * y1_b - a2_b * y2_b
                    // update bass history
                    x2_b = x1_b; x1_b = x
                    y2_b = y1_b; y1_b = y
                    // sample becomes filter result
                    x = y
                }
    
                // final clamp to prevent clipping
                val out = x.coerceIn(Short.MIN_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
                buffer.putShort(out)
            }
            buffer.flip()
        }
    }
    
    // --- 3. REVERB (Simple Delay Line) ---
    class ReverbAudioProcessor : BaseAudioProcessor() {
        private var enabled = false
        private var buffer: ShortArray = ShortArray(0)
        private var cursor = 0
        // params: 150ms delay, 0.5 decay
        private val delayMs = 150
        private val decay = 0.5f
    
        fun setEnabled(enabled: Boolean) {
            if (this.enabled != enabled) {
                this.enabled = enabled
                // clear buffer on disable
                if (!enabled) buffer = ShortArray(0)
            }
        }
    
        override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
            // calculate buffer size based on sample rate
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
    
                // get the past sample
                val delayedSample = buffer[cursor]
    
                // mix: input + (echo * decay)
                // clamp to avoid crackling
                val outputSample = (inputSample + delayedSample * decay).toInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    
                // write output
                outputBuffer.putShort(outputSample)
    
                // feed back into delay line
                buffer[cursor] = outputSample
    
                cursor++
                if (cursor >= buffer.size) cursor = 0
            }
            outputBuffer.flip()
        }
    }


