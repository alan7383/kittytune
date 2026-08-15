package com.alananasss.kittytune.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.alananasss.kittytune.R

/**
 * Handles real studio-quality ambient soundscape loops (Rain, Fireplace, Ocean Waves, Cozy Cafe) playing alongside music.
 */
class RainPlayer(private val context: Context) {

    private var exoPlayer: ExoPlayer? = null
    private var isEnabled = false
    private var volume = 0.5f
    private var currentType = "rain"

    private fun getRawResId(type: String): Int {
        return when (type) {
            "fireplace" -> R.raw.fireplace
            "ocean" -> R.raw.ocean
            "cafe" -> R.raw.cafe
            else -> R.raw.rain
        }
    }

    fun setVolume(volume: Float) {
        this.volume = volume.coerceIn(0f, 1f)
        exoPlayer?.volume = this.volume
    }

    fun setAmbientType(type: String) {
        if (currentType == type) return
        currentType = type
        if (isEnabled) {
            loadAndPlayMedia()
        }
    }

    private fun initExoPlayer() {
        if (exoPlayer == null) {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
            }
        }
    }

    private fun loadAndPlayMedia() {
        initExoPlayer()
        val rawResId = getRawResId(currentType)
        val uri = "android.resource://${context.packageName}/$rawResId"
        exoPlayer?.apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            volume = this@RainPlayer.volume
            play()
        }
    }

    fun setEnabled(enabled: Boolean) {
        if (this.isEnabled != enabled) {
            this.isEnabled = enabled
            if (enabled) {
                loadAndPlayMedia()
            } else {
                exoPlayer?.pause()
            }
        }
    }

    fun release() {
        exoPlayer?.release()
        exoPlayer = null
        isEnabled = false
    }
}
