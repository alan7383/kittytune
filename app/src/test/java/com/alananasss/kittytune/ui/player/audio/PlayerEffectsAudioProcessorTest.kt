package com.alananasss.kittytune.ui.player.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import com.alananasss.kittytune.audio.AudioMode
import com.alananasss.kittytune.audio.AudioRoutingMatrix
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal fun playerEffects(): Array<BypassableAudioProcessor> = arrayOf(
    VocalRemoverAudioProcessor(), VocalBoostAudioProcessor(), TapeSaturationAudioProcessor(),
    SubOctaverAudioProcessor(), ChorusAudioProcessor(), FlangerAudioProcessor(), PhaserAudioProcessor(),
    RotarySpeakerAudioProcessor(), RobotVocoderAudioProcessor(), TranceGateAudioProcessor(),
    UnderwaterAudioProcessor(), PartyNextDoorAudioProcessor(), EmptyMallAudioProcessor(),
    SuperWideAudioProcessor(), PingPongDelayAudioProcessor(), ReverseEchoAudioProcessor(),
    StadiumAudioProcessor(), CassetteWalkmanAudioProcessor(), AsmrVocalAudioProcessor(),
    NightDriveAudioProcessor(), FxAudioProcessor(), ReverbAudioProcessor(), ShimmerReverbAudioProcessor(),
    EightDAudioProcessor(), EarrapeAudioProcessor(), MonoAudioProcessor(), VinylLoFiAudioProcessor(),
    GramophoneAudioProcessor(), MegaphoneRadioAudioProcessor(), ChiptuneAudioProcessor(),
    VintageMp3AudioProcessor(),
)

internal fun enable(effect: BypassableAudioProcessor, enabled: Boolean) {
    if (effect is FxAudioProcessor) effect.setEffects(enabled, enabled)
    else effect.javaClass.getMethod("setEnabled", Boolean::class.javaPrimitiveType).invoke(effect, enabled)
}

internal fun pcm(frames: Int, channels: Int = 2, seed: Int = 0): ByteBuffer =
    ByteBuffer.allocateDirect(frames * channels * 2).order(ByteOrder.nativeOrder()).apply {
        repeat(frames * channels) { putShort((((it + seed) * 997 % 24001) - 12000).toShort()) }
        flip()
    }

internal fun bytes(buffer: ByteBuffer): ByteArray = ByteArray(buffer.remaining()).also { buffer.get(it) }

internal fun reference(effects: Array<BypassableAudioProcessor>, input: ByteBuffer): ByteBuffer {
    var block = input
    for (effect in effects) {
        effect.queueInput(block)
        check(!block.hasRemaining())
        block = effect.output
    }
    return block
}

class PlayerEffectsAudioProcessorTest {
    private val originalMode = AudioRoutingMatrix.modeProvider

    @After fun restoreRouting() { AudioRoutingMatrix.modeProvider = originalMode }

    private fun configured(effects: Array<BypassableAudioProcessor>, channels: Int = 2): PlayerEffectsAudioProcessor {
        AudioRoutingMatrix.modeProvider = { AudioMode.STANDARD }
        return PlayerEffectsAudioProcessor(effects).apply {
            configure(AudioProcessor.AudioFormat(48000, channels, C.ENCODING_PCM_16BIT))
            flush()
        }
    }

    private fun compare(enabled: Set<Int>, channels: Int = 2) {
        val old = playerEffects()
        val fresh = playerEffects()
        old.forEachIndexed { i, e -> enable(e, i in enabled) }
        fresh.forEachIndexed { i, e -> enable(e, i in enabled) }
        val group = configured(fresh, channels)
        val format = AudioProcessor.AudioFormat(48000, channels, C.ENCODING_PCM_16BIT)
        old.forEach { it.configure(format); it.flush() }
        for ((index, frames) in listOf(1, 127, 1024, 4096, 3, 8192, 256).withIndex()) {
            val expected = bytes(reference(old, pcm(frames, channels, index)))
            val input = pcm(frames, channels, index)
            group.queueInput(input)
            assertFalse(input.hasRemaining())
            assertArrayEquals("effects=$enabled channels=$channels chunk=$index", expected, bytes(group.output))
        }
    }

    @Test fun disabledEffectsAreByteExactForMonoAndStereo() {
        compare(emptySet(), 1)
        compare(emptySet(), 2)
    }

    @Test fun everyEffectAndCombinedPresetsKeepExactOutput() {
        repeat(31) { compare(setOf(it)) }
        compare((0..30).toSet())
        compare(setOf(1, 4, 20, 21, 23))
        compare(setOf(0, 8, 21), 1)
    }

    @Test fun liveTogglesWorkWithoutReconfigureOrFlush() {
        val old = playerEffects()
        val fresh = playerEffects()
        val group = configured(fresh)
        old.forEach { it.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); it.flush() }
        repeat(12) { step ->
            old.forEachIndexed { i, e -> enable(e, i % 4 == step % 4 && step % 3 != 0) }
            fresh.forEachIndexed { i, e -> enable(e, i % 4 == step % 4 && step % 3 != 0) }
            val expected = bytes(reference(old, pcm(1024, seed = step)))
            group.queueInput(pcm(1024, seed = step))
            assertArrayEquals(expected, bytes(group.output))
        }
    }

    @Test fun outputOwnsItsBytesWhileSinkConsumesItInParts() {
        val group = configured(playerEffects())
        val input = pcm(1024)
        val expected = bytes(input.duplicate())
        group.queueInput(input)
        input.clear()
        while (input.hasRemaining()) input.put(0)
        val output = group.output
        val actual = ByteArray(output.remaining())
        output.get(actual, 0, 7)
        assertFalse(group.output.hasRemaining())
        output.get(actual, 7, actual.size - 7)
        assertArrayEquals(expected, actual)
    }

    @Test fun enabledChildCannotOverwriteTheGroupsPublishedOutput() {
        val effect = EightDAudioProcessor().apply { setEnabled(true) }
        val group = configured(arrayOf(effect))
        group.queueInput(pcm(1024))
        val output = group.output
        val expected = bytes(output.duplicate())
        // A child's reusable output is not the group's published output.
        effect.queueInput(pcm(1024, seed = 900))
        bytes(effect.output)
        assertArrayEquals(expected, bytes(output))
    }

    @Test fun eosFlushAndResetFollowProcessorLifecycle() {
        val group = configured(playerEffects())
        group.queueInput(pcm(64))
        group.queueEndOfStream()
        assertFalse(group.isEnded)
        bytes(group.output)
        assertTrue(group.isEnded)
        group.flush()
        assertFalse(group.isEnded)
        group.queueInput(pcm(128))
        group.flush()
        assertFalse(group.output.hasRemaining())
        group.reset()
        assertFalse(group.isActive)
        group.configure(AudioProcessor.AudioFormat(44100, 1, C.ENCODING_PCM_16BIT))
        group.flush()
        group.queueInput(pcm(128, 1))
        assertArrayEquals(bytes(pcm(128, 1)), bytes(group.output))
    }

    @Test fun floatInputOptsOutAndPendingConfigDoesNotChangeCurrentBlock() {
        val old = playerEffects()
        val fresh = playerEffects()
        enable(old[23], true)
        enable(fresh[23], true)
        val group = configured(fresh)
        val previousFormat = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)
        old.forEach { it.configure(previousFormat); it.flush() }
        val nextFormat = AudioProcessor.AudioFormat(96000, 2, C.ENCODING_PCM_FLOAT)
        old.forEach { it.configure(nextFormat) }
        assertEquals(AudioProcessor.AudioFormat.NOT_SET,
            group.configure(nextFormat))
        val expected = bytes(reference(old, pcm(256)))
        group.queueInput(pcm(256))
        assertArrayEquals(expected, bytes(group.output))
        group.flush()
        assertFalse(group.isActive)
    }

    @Test fun reverbCanBeReenabledAfterADirtyDelayLineWithoutReconfigure() {
        val effect = ReverbAudioProcessor().apply { setEnabled(true) }
        val group = configured(arrayOf(effect))
        group.queueInput(pcm(10000))
        bytes(group.output)
        effect.setEnabled(false)
        group.queueInput(pcm(1024))
        bytes(group.output)
        effect.setEnabled(true)
        val clean = ReverbAudioProcessor().apply { setEnabled(true) }
        clean.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT))
        clean.flush()
        clean.queueInput(pcm(10000, seed = 123))
        group.queueInput(pcm(10000, seed = 123))
        assertArrayEquals(bytes(clean.output), bytes(group.output))
    }

    @Test fun routingBypassKeepsEnabledEffectsBitExact() {
        val effects = playerEffects()
        effects.forEach { enable(it, true) }
        val group = configured(effects)
        AudioRoutingMatrix.modeProvider = { AudioMode.BIT_PERFECT }
        group.queueInput(pcm(1024))
        assertArrayEquals(bytes(pcm(1024)), bytes(group.output))
    }

    @Test fun flushResetsEnabledStatefulEffectsLikeTheOriginalChain() {
        val old = playerEffects()
        val fresh = playerEffects()
        listOf(old, fresh).forEach { effects -> effects.forEach { enable(it, true) } }
        val group = configured(fresh)
        old.forEach { it.configure(AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_16BIT)); it.flush() }
        repeat(3) { pass ->
            val expected = bytes(reference(old, pcm(4096, seed = pass)))
            group.queueInput(pcm(4096, seed = pass))
            assertArrayEquals(expected, bytes(group.output))
            group.flush()
            old.forEach { it.flush() }
        }
    }
}
