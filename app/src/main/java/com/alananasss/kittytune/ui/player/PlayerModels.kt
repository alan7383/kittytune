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
        val rainVolume: Float = 1.0f
    )
    
    data class PlaybackContext(
        val displayText: String,
        val navigationId: String,
        val imageUrl: String? = null,
        val artistName: String? = null,
        val isVerified: Boolean = false
    )


