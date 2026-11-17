    package com.alananasss.kittytune.ui.player

    import com.google.gson.annotations.SerializedName

    enum class RepeatMode {
        @SerializedName("NONE") NONE,
        @SerializedName("ALL") ALL,
        @SerializedName("ONE") ONE
    }

    enum class LyricsProvider {
        @SerializedName("MAX_QUALITY") MAX_QUALITY,
        @SerializedName("OPEN_SOURCE") OPEN_SOURCE
    }

    enum class NormalizationLevel {
        @SerializedName("QUIET") QUIET,
        @SerializedName("NORMAL") NORMAL,
        @SerializedName("LOUD") LOUD
    }

    data class AudioEffectsState(
        @SerializedName("speed") val speed: Float = 1f,
        @SerializedName("isPitchEnabled") val isPitchEnabled: Boolean = true,
        @SerializedName("is8DEnabled") val is8DEnabled: Boolean = false,
        @SerializedName("isMuffledEnabled") val isMuffledEnabled: Boolean = false,
        @SerializedName("isBassBoostEnabled") val isBassBoostEnabled: Boolean = false,
        @SerializedName("isReverbEnabled") val isReverbEnabled: Boolean = false,
        @SerializedName("isRainEnabled") val isRainEnabled: Boolean = false,
        @SerializedName("rainVolume") val rainVolume: Float = 1.0f,
        @SerializedName("bassBoostIntensity") val bassBoostIntensity: Float = 0.5f,
        @SerializedName("eightDSpeed") val eightDSpeed: Float = 0.5f,
        @SerializedName("reverbIntensity") val reverbIntensity: Float = 0.5f,
        @SerializedName("muffledIntensity") val muffledIntensity: Float = 0.5f,
        @SerializedName("isEarrapeEnabled") val isEarrapeEnabled: Boolean = false,
        @SerializedName("earrapeIntensity") val earrapeIntensity: Float = 1.0f,
        @SerializedName("isMonoEnabled") val isMonoEnabled: Boolean = false,
        @SerializedName("isNormalizationEnabled") val isNormalizationEnabled: Boolean = false,
        @SerializedName("normalizationLevel") val normalizationLevel: NormalizationLevel = NormalizationLevel.NORMAL,
        @SerializedName("isVintageMp3Enabled") val isVintageMp3Enabled: Boolean = false,
        @SerializedName("vintageMp3Compression") val vintageMp3Compression: Float = 0.75f,
        @SerializedName("isVocalRemoverEnabled") val isVocalRemoverEnabled: Boolean = false,
        @SerializedName("vocalRemoverLevel") val vocalRemoverLevel: Float = 1.0f,
        @SerializedName("isVocalBoostEnabled") val isVocalBoostEnabled: Boolean = false,
        @SerializedName("vocalBoostIntensity") val vocalBoostIntensity: Float = 0.75f,
        @SerializedName("isFlangerEnabled") val isFlangerEnabled: Boolean = false,
        @SerializedName("flangerIntensity") val flangerIntensity: Float = 0.75f,
        @SerializedName("flangerSpeed") val flangerSpeed: Float = 0.50f,
        @SerializedName("isPartyNextDoorEnabled") val isPartyNextDoorEnabled: Boolean = false,
        @SerializedName("partyNextDoorIsolation") val partyNextDoorIsolation: Float = 0.60f,
        @SerializedName("partyNextDoorReverb") val partyNextDoorReverb: Float = 0.50f,
        @SerializedName("partyNextDoorBassRumble") val partyNextDoorBassRumble: Float = 0.70f,
        @SerializedName("isSuperWideEnabled") val isSuperWideEnabled: Boolean = false,
        @SerializedName("superWideWidth") val superWideWidth: Float = 0.70f,
        @SerializedName("superWideDepth") val superWideDepth: Float = 0.50f,
        @SerializedName("isVinylLoFiEnabled") val isVinylLoFiEnabled: Boolean = false,
        @SerializedName("vinylCrackles") val vinylCrackles: Float = 0.65f,
        @SerializedName("vinylFlutter") val vinylFlutter: Float = 0.50f,
        @SerializedName("isPhaserEnabled") val isPhaserEnabled: Boolean = false,
        @SerializedName("phaserSpeed") val phaserSpeed: Float = 0.50f,
        @SerializedName("phaserFeedback") val phaserFeedback: Float = 0.65f,
        @SerializedName("isMegaphoneEnabled") val isMegaphoneEnabled: Boolean = false,
        @SerializedName("megaphoneTone") val megaphoneTone: Float = 0.50f,
        @SerializedName("megaphoneDrive") val megaphoneDrive: Float = 0.60f,
        @SerializedName("isRobotVocoderEnabled") val isRobotVocoderEnabled: Boolean = false,
        @SerializedName("robotFrequency") val robotFrequency: Float = 0.40f,
        @SerializedName("robotMix") val robotMix: Float = 0.75f,
        @SerializedName("isChorusEnabled") val isChorusEnabled: Boolean = false,
        @SerializedName("chorusRate") val chorusRate: Float = 0.30f,
        @SerializedName("chorusDepth") val chorusDepth: Float = 0.70f,
        @SerializedName("isUnderwaterEnabled") val isUnderwaterEnabled: Boolean = false,
        @SerializedName("underwaterDepth") val underwaterDepth: Float = 0.60f,
        @SerializedName("underwaterBubbles") val underwaterBubbles: Float = 0.40f,
        @SerializedName("isTranceGateEnabled") val isTranceGateEnabled: Boolean = false,
        @SerializedName("tranceGateSpeed") val tranceGateSpeed: Float = 0.50f,
        @SerializedName("tranceGatePattern") val tranceGatePattern: Float = 0.85f,
        @SerializedName("tranceGateMix") val tranceGateMix: Float = 0.90f,
        @SerializedName("isPingPongDelayEnabled") val isPingPongDelayEnabled: Boolean = false,
        @SerializedName("pingPongDelayTime") val pingPongDelayTime: Float = 0.45f,
        @SerializedName("pingPongFeedback") val pingPongFeedback: Float = 0.60f,
        @SerializedName("isChiptuneEnabled") val isChiptuneEnabled: Boolean = false,
        @SerializedName("chiptuneBits") val chiptuneBits: Float = 0.50f,
        @SerializedName("chiptuneSampleRate") val chiptuneSampleRate: Float = 0.55f,
        @SerializedName("isShimmerReverbEnabled") val isShimmerReverbEnabled: Boolean = false,
        @SerializedName("shimmerSize") val shimmerSize: Float = 0.65f,
        @SerializedName("shimmerMix") val shimmerMix: Float = 0.60f,
        @SerializedName("isRotarySpeakerEnabled") val isRotarySpeakerEnabled: Boolean = false,
        @SerializedName("rotarySpeed") val rotarySpeed: Float = 0.40f,
        @SerializedName("rotaryDepth") val rotaryDepth: Float = 0.75f,
        @SerializedName("isTapeSaturationEnabled") val isTapeSaturationEnabled: Boolean = false,
        @SerializedName("tapeWarmth") val tapeWarmth: Float = 0.65f,
        @SerializedName("tapeExciter") val tapeExciter: Float = 0.50f,
        @SerializedName("isSubOctaverEnabled") val isSubOctaverEnabled: Boolean = false,
        @SerializedName("subOctaverLevel") val subOctaverLevel: Float = 0.70f,
        @SerializedName("subOctaverCutoff") val subOctaverCutoff: Float = 0.50f,
        @SerializedName("isEmptyMallEnabled") val isEmptyMallEnabled: Boolean = false,
        @SerializedName("emptyMallDistance") val emptyMallDistance: Float = 0.65f,
        @SerializedName("emptyMallReverb") val emptyMallReverb: Float = 0.55f,
        @SerializedName("isGramophoneEnabled") val isGramophoneEnabled: Boolean = false,
        @SerializedName("gramophoneAge") val gramophoneAge: Float = 0.65f,
        @SerializedName("gramophoneHorn") val gramophoneHorn: Float = 0.60f,
        @SerializedName("isReverseEchoEnabled") val isReverseEchoEnabled: Boolean = false,
        @SerializedName("reverseEchoTime") val reverseEchoTime: Float = 0.50f,
        @SerializedName("reverseEchoFeedback") val reverseEchoFeedback: Float = 0.55f,
        @SerializedName("isStadiumEnabled") val isStadiumEnabled: Boolean = false,
        @SerializedName("stadiumSize") val stadiumSize: Float = 0.65f,
        @SerializedName("stadiumAtmosphere") val stadiumAtmosphere: Float = 0.60f,
        @SerializedName("isWalkmanEnabled") val isWalkmanEnabled: Boolean = false,
        @SerializedName("walkmanDrive") val walkmanDrive: Float = 0.65f,
        @SerializedName("walkmanHiss") val walkmanHiss: Float = 0.40f,
        @SerializedName("isAsmrVocalEnabled") val isAsmrVocalEnabled: Boolean = false,
        @SerializedName("asmrProximity") val asmrProximity: Float = 0.70f,
        @SerializedName("asmrAir") val asmrAir: Float = 0.65f,
        @SerializedName("isNightDriveEnabled") val isNightDriveEnabled: Boolean = false,
        @SerializedName("nightDriveCabin") val nightDriveCabin: Float = 0.65f,
        @SerializedName("nightDriveRoad") val nightDriveRoad: Float = 0.45f,
        @SerializedName("ambientType") val ambientType: String = "rain",
        @SerializedName("isEqualizerEnabled") val isEqualizerEnabled: Boolean = false
    )

    data class PlaybackContext(
        @SerializedName("displayText") val displayText: String,
        @SerializedName("navigationId") val navigationId: String,
        @SerializedName("imageUrl") val imageUrl: String? = null,
        @SerializedName("artistName") val artistName: String? = null,
        @SerializedName("isVerified") val isVerified: Boolean = false
    )

    data class EqualizerState(
        @SerializedName("isEnabled") val isEnabled: Boolean = false,
        @SerializedName("preampDb") val preampDb: Float = 0f,
        @SerializedName("bandGainsDb") val bandGainsDb: List<Float> = List(16) { 0f },
        @SerializedName("selectedPreset") val selectedPreset: String = "Flat",
        // Set only while an automatic headphone profile is applied. Nullable so old saved states,
        // which predate Auto EQ, still deserialize cleanly.
        @SerializedName("autoProfileId") val autoProfileId: String? = null,
        @SerializedName("autoProfileName") val autoProfileName: String? = null,
        @SerializedName("autoProfileSource") val autoProfileSource: String? = null,
        @SerializedName("autoProfileDeviceKey") val autoProfileDeviceKey: String? = null,
        @SerializedName("autoProfileDeviceName") val autoProfileDeviceName: String? = null,
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


