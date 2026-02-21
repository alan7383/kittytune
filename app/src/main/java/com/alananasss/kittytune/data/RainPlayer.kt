    package com.alananasss.kittytune.data
    
    import android.content.Context
    import androidx.media3.common.C
    import androidx.media3.common.MediaItem
    import androidx.media3.common.Player
    import androidx.media3.exoplayer.ExoPlayer
    import com.alananasss.kittytune.R
    
    /**
     * Handles the ambient rain loop playing alongside the main music.
     */
    class RainPlayer(private val context: Context) {
    
        private var player: ExoPlayer? = null
        private var isEnabled = false
    
        fun setVolume(volume: Float) {
            player?.volume = volume
        }
    
        private fun initPlayer() {
            // Lazy load the player to save resources until needed
            if (player == null) {
                player = ExoPlayer.Builder(context).build().apply {
                    repeatMode = Player.REPEAT_MODE_ONE
                    setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.rain}"))
                    prepare()
                }
            }
        }
    
        fun setEnabled(enabled: Boolean) {
            this.isEnabled = enabled
            if (enabled) {
                initPlayer()
                player?.play()
            } else {
                player?.pause()
            }
        }
    
        fun release() {
            player?.release()
            player = null
        }
    }


