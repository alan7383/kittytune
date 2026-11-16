package com.alananasss.kittytune.audio.usb

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.DefaultAudioSink

// Audio processor chain with true Bit-Perfect support.
//
// When Bit-Perfect mode is active (AudioRoutingMatrix.isBitPerfectActive() == true),
// the built-in SilenceSkippingAudioProcessor and SonicAudioProcessor, as well as the
// user's DSP processors, are fully removed from the signal path. ONLY [tail]
// (BitPerfectContainerAudioProcessor) is handed to the sink. This prevents
// 24/32-bit Hi-Res FLAC/WAV files from being silently down-converted to 16 bits.
@UnstableApi
class BitPerfectAudioProcessorChain(
    userProcessors: Array<AudioProcessor>,
    private val tail: AudioProcessor,
) : DefaultAudioSink.DefaultAudioProcessorChain(*userProcessors) {

    private val dspChainWithTail: Array<AudioProcessor> = arrayOf(*super.getAudioProcessors(), tail)
    private val bitPerfectDirectChain: Array<AudioProcessor> = arrayOf(tail)

    override fun getAudioProcessors(): Array<AudioProcessor> {
        return if (com.alananasss.kittytune.audio.AudioRoutingMatrix.isBitPerfectActive()) {
            bitPerfectDirectChain
        } else {
            dspChainWithTail
        }
    }
}
