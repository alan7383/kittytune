    package com.alananasss.kittytune.ui.player

    enum class RepeatMode { NONE, ALL, ONE }

    enum class LyricsProvider { MAX_QUALITY, OPEN_SOURCE }

    enum class NormalizationLevel { QUIET, NORMAL, LOUD }

    data class AudioEffectsState(
        val speed: Float = 1f,
        val isPitchEnabled: Boolean = true,
        val is8DEnabled: Boolean = false,
        val isMuffledEnabled: Boolean = false,
        val isBassBoostEnabled: Boolean = false,
        val isReverbEnabled: Boolean = false,
        val isRainEnabled: Boolean = false,
        val rainVolume: Float = 1.0f,
        val bassBoostIntensity: Float = 0.5f,
        val eightDSpeed: Float = 0.5f,
        val reverbIntensity: Float = 0.5f,
        val muffledIntensity: Float = 0.5f,
        val isEarrapeEnabled: Boolean = false,
        val earrapeIntensity: Float = 1.0f,
        val isMonoEnabled: Boolean = false,
        val isNormalizationEnabled: Boolean = false,
        val normalizationLevel: NormalizationLevel = NormalizationLevel.NORMAL,
        val isVintageMp3Enabled: Boolean = false,
        val vintageMp3Compression: Float = 0.75f,
        val isVocalRemoverEnabled: Boolean = false,
        val vocalRemoverLevel: Float = 1.0f,
        val isVocalBoostEnabled: Boolean = false,
        val vocalBoostIntensity: Float = 0.75f,
        val isFlangerEnabled: Boolean = false,
        val flangerIntensity: Float = 0.75f,
        val flangerSpeed: Float = 0.50f,
        val isPartyNextDoorEnabled: Boolean = false,
        val partyNextDoorIsolation: Float = 0.60f,
        val partyNextDoorReverb: Float = 0.50f,
        val partyNextDoorBassRumble: Float = 0.70f,
        val isSuperWideEnabled: Boolean = false,
        val superWideWidth: Float = 0.70f,
        val superWideDepth: Float = 0.50f,
        val isVinylLoFiEnabled: Boolean = false,
        val vinylCrackles: Float = 0.65f,
        val vinylFlutter: Float = 0.50f,
        val isPhaserEnabled: Boolean = false,
        val phaserSpeed: Float = 0.50f,
        val phaserFeedback: Float = 0.65f,
        val isMegaphoneEnabled: Boolean = false,
        val megaphoneTone: Float = 0.50f,
        val megaphoneDrive: Float = 0.60f,
        val isRobotVocoderEnabled: Boolean = false,
        val robotFrequency: Float = 0.40f,
        val robotMix: Float = 0.75f,
        val isChorusEnabled: Boolean = false,
        val chorusRate: Float = 0.30f,
        val chorusDepth: Float = 0.70f,
        val isUnderwaterEnabled: Boolean = false,
        val underwaterDepth: Float = 0.60f,
        val underwaterBubbles: Float = 0.40f,
        val isTranceGateEnabled: Boolean = false,
        val tranceGateSpeed: Float = 0.50f,
        val tranceGatePattern: Float = 0.85f,
        val tranceGateMix: Float = 0.90f,
        val isPingPongDelayEnabled: Boolean = false,
        val pingPongDelayTime: Float = 0.45f,
        val pingPongFeedback: Float = 0.60f,
        val isChiptuneEnabled: Boolean = false,
        val chiptuneBits: Float = 0.50f,
        val chiptuneSampleRate: Float = 0.55f,
        val isShimmerReverbEnabled: Boolean = false,
        val shimmerSize: Float = 0.65f,
        val shimmerMix: Float = 0.60f,
        val isRotarySpeakerEnabled: Boolean = false,
        val rotarySpeed: Float = 0.40f,
        val rotaryDepth: Float = 0.75f,
        val isTapeSaturationEnabled: Boolean = false,
        val tapeWarmth: Float = 0.65f,
        val tapeExciter: Float = 0.50f,
        val isSubOctaverEnabled: Boolean = false,
        val subOctaverLevel: Float = 0.70f,
        val subOctaverCutoff: Float = 0.50f,
        val isEmptyMallEnabled: Boolean = false,
        val emptyMallDistance: Float = 0.65f,
        val emptyMallReverb: Float = 0.55f,
        val isGramophoneEnabled: Boolean = false,
        val gramophoneAge: Float = 0.65f,
        val gramophoneHorn: Float = 0.60f,
        val isReverseEchoEnabled: Boolean = false,
        val reverseEchoTime: Float = 0.50f,
        val reverseEchoFeedback: Float = 0.55f,
        val isStadiumEnabled: Boolean = false,
        val stadiumSize: Float = 0.65f,
        val stadiumAtmosphere: Float = 0.60f,
        val isWalkmanEnabled: Boolean = false,
        val walkmanDrive: Float = 0.65f,
        val walkmanHiss: Float = 0.40f,
        val isAsmrVocalEnabled: Boolean = false,
        val asmrProximity: Float = 0.70f,
        val asmrAir: Float = 0.65f,
        val isNightDriveEnabled: Boolean = false,
        val nightDriveCabin: Float = 0.65f,
        val nightDriveRoad: Float = 0.45f,
        val ambientType: String = "rain",
        val isEqualizerEnabled: Boolean = false
    )

    data class PlaybackContext(
        val displayText: String,
        val navigationId: String,
        val imageUrl: String? = null,
        val artistName: String? = null,
        val isVerified: Boolean = false
    )

    data class EqualizerState(
        val isEnabled: Boolean = false,
        val preampDb: Float = 0f,
        val bandGainsDb: List<Float> = List(16) { 0f },
        val selectedPreset: String = "Flat"
    )

    data class EqualizerPreset(
        val name: String,
        val bandGainsDb: List<Float>,
        val preampDb: Float = 0f
    )

    object EqualizerPresets {
        val Flat = EqualizerPreset("Flat", List(16) { 0f })
        val BassBoost = EqualizerPreset("Bass Boost", listOf(6.0f, 5.5f, 4.5f, 3.5f, 2.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f), -1.5f)
        val TrebleBoost = EqualizerPreset("Treble Boost", listOf(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f, 5.5f, 6.0f, 6.0f, 6.0f), -1.5f)
        val Vocal = EqualizerPreset("Vocal", listOf(-2.0f, -1.5f, -1.0f, 0.0f, 1.5f, 3.0f, 4.0f, 4.5f, 4.0f, 3.0f, 2.0f, 1.0f, 0.0f, -1.0f, -1.5f, -2.0f), -1.0f)
        val Pop = EqualizerPreset("Pop", listOf(-1.0f, 0.0f, 1.5f, 2.5f, 3.0f, 2.0f, 0.0f, -1.0f, -1.0f, 0.0f, 1.5f, 2.5f, 3.0f, 3.5f, 3.0f, 2.5f), -1.0f)
        val Rock = EqualizerPreset("Rock", listOf(4.5f, 3.5f, 2.5f, 1.0f, -0.5f, -1.5f, -1.0f, 0.0f, 1.5f, 2.5f, 3.5f, 4.0f, 4.5f, 4.5f, 4.0f, 3.5f), -1.5f)
        val Electronic = EqualizerPreset("Electronic", listOf(5.5f, 5.0f, 3.5f, 2.0f, 0.5f, -1.0f, -1.5f, 0.0f, 1.0f, 2.5f, 4.0f, 4.5f, 5.0f, 5.5f, 5.0f, 4.5f), -2.0f)
        val HipHop = EqualizerPreset("Hip-Hop", listOf(6.0f, 5.5f, 4.5f, 3.0f, 1.5f, 0.0f, -0.5f, 0.5f, 1.5f, 2.0f, 2.5f, 3.0f, 3.5f, 3.5f, 3.0f, 2.5f), -2.0f)
        val Acoustic = EqualizerPreset("Acoustic", listOf(2.5f, 2.0f, 1.5f, 1.0f, 1.0f, 1.5f, 2.0f, 2.5f, 2.5f, 3.0f, 3.0f, 3.5f, 3.0f, 2.5f, 2.0f, 1.5f), -1.0f)
        val Jazz = EqualizerPreset("Jazz", listOf(3.0f, 2.5f, 2.0f, 1.0f, 0.5f, -0.5f, -0.5f, 0.5f, 1.5f, 2.0f, 2.0f, 2.5f, 2.5f, 2.0f, 1.5f, 1.0f), -0.5f)
        val Classical = EqualizerPreset("Classical", listOf(3.0f, 2.5f, 2.0f, 1.5f, 0.0f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 2.0f, 2.5f, 3.0f, 3.0f, 2.5f, 2.0f), -1.0f)
        val Metal = EqualizerPreset("Metal", listOf(5.0f, 4.5f, 3.0f, 1.0f, -1.5f, -2.5f, -2.0f, -1.0f, 1.0f, 2.5f, 4.0f, 5.0f, 5.5f, 5.0f, 4.5f, 4.0f), -2.0f)

        val all = listOf(Flat, BassBoost, TrebleBoost, Vocal, Pop, Rock, Electronic, HipHop, Acoustic, Jazz, Classical, Metal)

        fun getByName(name: String): EqualizerPreset = all.find { it.name.equals(name, ignoreCase = true) } ?: Flat
    }


