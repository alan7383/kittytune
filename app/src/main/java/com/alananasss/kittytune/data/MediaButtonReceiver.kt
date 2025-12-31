package com.alananasss.kittytune.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class MediaButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player = MusicManager.player

        when (intent.action) {
            "ACTION_PLAY" -> {
                player.play()
            }
            "ACTION_PAUSE" -> {
                player.pause()
            }
            "ACTION_NEXT" -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNext()
                }
            }
            "ACTION_PREVIOUS" -> {
                // smart previous logic
                // restart song if we are more than 3s in
                if (player.currentPosition > 3000) {
                    player.seekTo(0)
                } else if (player.hasPreviousMediaItem()) {
                    player.seekToPrevious()
                } else {
                    player.seekTo(0)
                }
            }
            "ACTION_STOP" -> {
                player.pause()
                // could also kill the service here but pause is fine for now
            }
        }
    }
}