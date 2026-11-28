package com.alananasss.kittytune.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** The export renders through the real pipeline: decoder, the player's effects, Sonic and the AAC encoder. */
@RunWith(AndroidJUnit4::class)
class AudioFxExportRenderTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun wav(file: File, seconds: Int) {
        val rate = 44_100
        val frames = rate * seconds
        val data = ByteBuffer.allocate(frames * 4).order(ByteOrder.LITTLE_ENDIAN)
        repeat(frames) { i ->
            val t = i / rate.toDouble()
            val v = ((0.15 * sin(2 * PI * 80 * t) + 0.1 * sin(2 * PI * 1000 * t)) * 32767).toInt().toShort()
            data.putShort(v); data.putShort(v)
        }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + frames * 4); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(2); putInt(rate); putInt(rate * 4); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(frames * 4)
        }
        file.outputStream().use { it.write(header.array()); it.write(data.array()) }
    }

    private fun durationMs(file: File): Long = MediaMetadataRetriever().run {
        setDataSource(file.absolutePath)
        extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong().also { release() }
    }

    /** Decodes [file] and returns the RMS of its 16-bit samples. */
    private fun rms(file: File): Double {
        val extractor = MediaExtractor().apply { setDataSource(file.absolutePath) }
        val format = extractor.getTrackFormat(0)
        extractor.selectTrack(0)
        val codec = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME)!!)
        codec.configure(format, null, null, 0)
        codec.start()
        val info = MediaCodec.BufferInfo()
        var sum = 0.0
        var n = 0L
        var inputDone = false
        while (true) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val size = extractor.readSampleData(codec.getInputBuffer(inIndex)!!, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            if (outIndex >= 0) {
                val out = codec.getOutputBuffer(outIndex)!!.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                while (out.hasRemaining()) { val s = out.get() / 32768.0; sum += s * s; n++ }
                codec.releaseOutputBuffer(outIndex, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            }
        }
        codec.stop(); codec.release(); extractor.release()
        return sqrt(sum / n.coerceAtLeast(1))
    }

    private fun render(source: File, effects: AudioEffectsState, out: File) = runBlocking {
        val error = withContext(Dispatchers.Main) {
            AudioFxExporter.render(
                context, MediaItem.fromUri(Uri.fromFile(source)), DefaultDataSource.Factory(context),
                effects, EqualizerState(), out,
            )
        }
        assertNull("export failed: $error", error)
    }

    @Test
    fun effectsSpeedAndPitchEndUpInTheFile() {
        val dir = File(context.cacheDir, "fx-export-test").apply { deleteRecursively(); mkdirs() }
        val source = File(dir, "source.wav").also { wav(it, 5) }
        val dry = File(dir, "dry.m4a")
        val wet = File(dir, "wet.m4a")
        render(source, AudioEffectsState(), dry)
        render(
            source,
            AudioEffectsState(
                speed = 0.8f, isPitchEnabled = true,
                isBassBoostEnabled = true, bassBoostIntensity = 0.2f,
                isEarrapeEnabled = true, earrapeIntensity = 0.1f,
            ),
            wet,
        )
        val dryMs = durationMs(dry)
        val wetMs = durationMs(wet)
        val dryRms = rms(dry)
        val wetRms = rms(wet)
        android.util.Log.i("AudioFxExportRenderTest", "dry $dryMs ms rms $dryRms (${dry.length()} B), wet $wetMs ms rms $wetRms (${wet.length()} B)")
        assertTrue("dry should keep its length, got $dryMs", dryMs in 4_800L..5_200L)
        assertTrue("0.8x of 5 s should last ~6.25 s, got $wetMs", wetMs in 6_000L..6_500L)
        assertTrue("bass boost + earrape should be louder: $wetRms vs $dryRms", wetRms > dryRms * 1.2)
    }
}
