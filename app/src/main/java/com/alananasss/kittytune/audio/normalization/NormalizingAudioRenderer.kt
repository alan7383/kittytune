package com.alananasss.kittytune.audio.normalization

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.MediaSource

/**
 * The stock audio renderer, plus one thing: it says which track the audio it hands to the sink
 * belongs to, at the exact point one track's audio ends and the next one's begins.
 *
 * A gapless switch between playlist items flushes nothing in the sink, so an audio processor cannot
 * tell on its own that a new track started — the old normalizer only noticed when the music changed,
 * a second or two late. The renderer knows: [onProcessedStreamChange] runs once the previous stream's
 * last buffer is in the sink and before the next stream's first one, which is where
 * [onTrackStarted] must be told.
 */
@UnstableApi
class NormalizingAudioRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    enableDecoderFallback: Boolean,
    eventHandler: Handler?,
    eventListener: AudioRendererEventListener?,
    audioSink: AudioSink,
    private val onTrackStarted: (mediaId: String?) -> Unit,
) : MediaCodecAudioRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    audioSink,
) {
    /** Which media id each stream is, by its offset — the key the output side knows streams by. */
    private val mediaIdsByOffset = LinkedHashMap<Long, String?>()
    private val period = Timeline.Period()
    private val window = Timeline.Window()

    override fun onStreamChanged(
        formats: Array<out Format>,
        startPositionUs: Long,
        offsetUs: Long,
        mediaPeriodId: MediaSource.MediaPeriodId,
    ) {
        mediaIdsByOffset[offsetUs] = mediaIdOf(mediaPeriodId)
        while (mediaIdsByOffset.size > 8) mediaIdsByOffset.remove(mediaIdsByOffset.keys.first())
        super.onStreamChanged(formats, startPositionUs, offsetUs, mediaPeriodId)
        // The first stream, or one following streams that never output anything, takes over at once
        // and without an onProcessedStreamChange.
        if (outputStreamOffsetUs == offsetUs) publish()
    }

    override fun onProcessedStreamChange() {
        super.onProcessedStreamChange()
        publish()
    }

    override fun onPositionReset(positionUs: Long, joining: Boolean, sampleStreamIsResetToKeyFrame: Boolean) {
        super.onPositionReset(positionUs, joining, sampleStreamIsResetToKeyFrame)
        publish()
    }

    private fun publish() {
        val offset = outputStreamOffsetUs
        if (mediaIdsByOffset.containsKey(offset)) onTrackStarted(mediaIdsByOffset[offset])
    }

    private fun mediaIdOf(id: MediaSource.MediaPeriodId): String? = runCatching {
        val timeline = timeline
        if (timeline.isEmpty) return@runCatching null
        val windowIndex = timeline.getPeriodByUid(id.periodUid, period).windowIndex
        timeline.getWindow(windowIndex, window).mediaItem.mediaId
    }.getOrNull()
}
