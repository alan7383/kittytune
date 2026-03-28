    package com.alananasss.kittytune.ui.player
    
    enum class RepeatMode { NONE, ALL, ONE }
    
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
        val isEarrapeEnabled: Boolean = false
    )
    
    data class PlaybackContext(
        val displayText: String,
        val navigationId: String,
        val imageUrl: String? = null,
        val artistName: String? = null,
        val isVerified: Boolean = false
    )


