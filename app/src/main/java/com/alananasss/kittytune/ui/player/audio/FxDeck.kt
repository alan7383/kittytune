package com.alananasss.kittytune.ui.player.audio

import androidx.media3.common.audio.AudioProcessor
import com.alananasss.kittytune.ui.player.AudioEffectsState
import com.alananasss.kittytune.ui.player.EqualizerState

/**
 * One complete set of the creative effects and the equalizer, configured from the player's state.
 *
 * The player owns one per deck and the "export with effects" renderer builds its own: both configure
 * them through [applyEffects]/[applyEqualizer], so a file sounds like what was heard, by construction.
 */
class FxDeck {
    val eightD = EightDAudioProcessor()
    val fx = FxAudioProcessor()
    val reverb = ReverbAudioProcessor()
    val earrape = EarrapeAudioProcessor()
    val mono = MonoAudioProcessor()
    val vintageMp3 = VintageMp3AudioProcessor()
    val vocalRemover = VocalRemoverAudioProcessor()
    val vocalBoost = VocalBoostAudioProcessor()
    val flanger = FlangerAudioProcessor()
    val partyNextDoor = PartyNextDoorAudioProcessor()
    val superWide = SuperWideAudioProcessor()
    val vinylLoFi = VinylLoFiAudioProcessor()
    val phaser = PhaserAudioProcessor()
    val megaphone = MegaphoneRadioAudioProcessor()
    val robotVocoder = RobotVocoderAudioProcessor()
    val chorus = ChorusAudioProcessor()
    val underwater = UnderwaterAudioProcessor()
    val tranceGate = TranceGateAudioProcessor()
    val pingPongDelay = PingPongDelayAudioProcessor()
    val chiptune = ChiptuneAudioProcessor()
    val shimmerReverb = ShimmerReverbAudioProcessor()
    val rotarySpeaker = RotarySpeakerAudioProcessor()
    val tapeSaturation = TapeSaturationAudioProcessor()
    val subOctaver = SubOctaverAudioProcessor()
    val emptyMall = EmptyMallAudioProcessor()
    val gramophone = GramophoneAudioProcessor()
    val reverseEcho = ReverseEchoAudioProcessor()
    val stadium = StadiumAudioProcessor()
    val cassetteWalkman = CassetteWalkmanAudioProcessor()
    val asmrVocal = AsmrVocalAudioProcessor()
    val nightDrive = NightDriveAudioProcessor()
    val equalizer = EqualizerAudioProcessor()

    fun applyEffects(state: AudioEffectsState) {
        eightD.apply {
            setEnabled(state.is8DEnabled)
            setSpeed(state.eightDSpeed)
        }
        fx.apply {
            setEffects(state.isMuffledEnabled, state.isBassBoostEnabled)
            setBassBoostGain(state.bassBoostIntensity)
            setMuffledCutoff(state.muffledIntensity)
        }
        reverb.apply {
            setEnabled(state.isReverbEnabled)
            setDecay(state.reverbIntensity)
        }
        earrape.apply {
            setEnabled(state.isEarrapeEnabled)
            setIntensity(state.earrapeIntensity)
        }
        mono.apply {
            setEnabled(state.isMonoEnabled)
        }
        vintageMp3.apply {
            setEnabled(state.isVintageMp3Enabled)
            setCompression(state.vintageMp3Compression)
        }
        vocalRemover.apply {
            setEnabled(state.isVocalRemoverEnabled)
            setSuppressionLevel(state.vocalRemoverLevel)
        }
        vocalBoost.apply {
            setEnabled(state.isVocalBoostEnabled)
            setIntensity(state.vocalBoostIntensity)
        }
        flanger.apply {
            setEnabled(state.isFlangerEnabled)
            setIntensity(state.flangerIntensity)
            setSpeed(state.flangerSpeed)
        }
        partyNextDoor.apply {
            setEnabled(state.isPartyNextDoorEnabled)
            setIsolation(state.partyNextDoorIsolation)
            setReverb(state.partyNextDoorReverb)
            setBassRumble(state.partyNextDoorBassRumble)
        }
        superWide.apply {
            setEnabled(state.isSuperWideEnabled)
            setWidth(state.superWideWidth)
            setDepth(state.superWideDepth)
        }
        vinylLoFi.apply {
            setEnabled(state.isVinylLoFiEnabled)
            setCrackles(state.vinylCrackles)
            setFlutter(state.vinylFlutter)
        }
        phaser.apply {
            setEnabled(state.isPhaserEnabled)
            setSpeed(state.phaserSpeed)
            setFeedback(state.phaserFeedback)
        }
        megaphone.apply {
            setEnabled(state.isMegaphoneEnabled)
            setTone(state.megaphoneTone)
            setDrive(state.megaphoneDrive)
        }
        robotVocoder.apply {
            setEnabled(state.isRobotVocoderEnabled)
            setFrequency(state.robotFrequency)
            setMix(state.robotMix)
        }
        chorus.apply {
            setEnabled(state.isChorusEnabled)
            setRate(state.chorusRate)
            setDepth(state.chorusDepth)
        }
        underwater.apply {
            setEnabled(state.isUnderwaterEnabled)
            setDepth(state.underwaterDepth)
            setBubbles(state.underwaterBubbles)
        }
        tranceGate.apply {
            setEnabled(state.isTranceGateEnabled)
            setSpeed(state.tranceGateSpeed)
            setPattern(state.tranceGatePattern)
            setMix(state.tranceGateMix)
        }
        pingPongDelay.apply {
            setEnabled(state.isPingPongDelayEnabled)
            setDelayTime(state.pingPongDelayTime)
            setFeedback(state.pingPongFeedback)
        }
        chiptune.apply {
            setEnabled(state.isChiptuneEnabled)
            setBits(state.chiptuneBits)
            setSampleRateDown(state.chiptuneSampleRate)
        }
        shimmerReverb.apply {
            setEnabled(state.isShimmerReverbEnabled)
            setSize(state.shimmerSize)
            setShimmerMix(state.shimmerMix)
        }
        rotarySpeaker.apply {
            setEnabled(state.isRotarySpeakerEnabled)
            setSpeed(state.rotarySpeed)
            setDepth(state.rotaryDepth)
        }
        tapeSaturation.apply {
            setEnabled(state.isTapeSaturationEnabled)
            setWarmth(state.tapeWarmth)
            setExciter(state.tapeExciter)
        }
        subOctaver.apply {
            setEnabled(state.isSubOctaverEnabled)
            setSubLevel(state.subOctaverLevel)
            setSubCutoff(state.subOctaverCutoff)
        }
        emptyMall.apply {
            setEnabled(state.isEmptyMallEnabled)
            setDistance(state.emptyMallDistance)
            setGlassReverb(state.emptyMallReverb)
        }
        gramophone.apply {
            setEnabled(state.isGramophoneEnabled)
            setShellacAge(state.gramophoneAge)
            setHornResonance(state.gramophoneHorn)
        }
        reverseEcho.apply {
            setEnabled(state.isReverseEchoEnabled)
            setTime(state.reverseEchoTime)
            setFeedback(state.reverseEchoFeedback)
        }
        stadium.apply {
            setEnabled(state.isStadiumEnabled)
            setStadiumSize(state.stadiumSize)
            setAtmosphere(state.stadiumAtmosphere)
        }
        cassetteWalkman.apply {
            setEnabled(state.isWalkmanEnabled)
            setDrive(state.walkmanDrive)
            setTapeHiss(state.walkmanHiss)
        }
        asmrVocal.apply {
            setEnabled(state.isAsmrVocalEnabled)
            setProximity(state.asmrProximity)
            setAirSheen(state.asmrAir)
        }
        nightDrive.apply {
            setEnabled(state.isNightDriveEnabled)
            setCabinWidth(state.nightDriveCabin)
            setRoadRumble(state.nightDriveRoad)
        }
    }

    fun applyEqualizer(state: EqualizerState) {
        equalizer.setParameters(state.isEnabled, state.preampDb, state.bandGainsDb.toFloatArray())
    }

    /** In the player's order (see MusicManager's sink), for an offline chain. */
    fun processors(): List<AudioProcessor> = listOf(
        equalizer, vocalRemover, vocalBoost, tapeSaturation, subOctaver, chorus, flanger, phaser, rotarySpeaker, robotVocoder, tranceGate, underwater, partyNextDoor, emptyMall, superWide, pingPongDelay, reverseEcho, stadium, cassetteWalkman, asmrVocal, nightDrive, fx, reverb, shimmerReverb, eightD, earrape, mono, vinylLoFi, gramophone, megaphone, chiptune, vintageMp3,
    )
}
