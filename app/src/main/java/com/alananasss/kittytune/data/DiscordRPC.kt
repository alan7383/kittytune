package com.alananasss.kittytune.data

import android.content.Context
import android.util.Log
import com.alananasss.kittytune.domain.Track
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.RpcImage
import kotlinx.coroutines.CancellationException
import com.alananasss.kittytune.data.local.DiscordStatusDisplay
import com.alananasss.kittytune.data.local.PlayerPreferences

class DiscordRPC(
    val context: Context,
    token: String
) : KizzyRPC(token) {

    private val applicationId = "1473071817693331540"
    private val logoAssetId = "1473370878195794073"
    private var lastTrackId: Long = -1L

    suspend fun updateTrack(track: Track, contextName: String?) {
        if (track.id == lastTrackId) return
        lastTrackId = track.id

        val isPlaying = try {
            MusicManager.player.isPlaying
        } catch (e: Exception) {
            false
        }
        val position = try {
            MusicManager.player.currentPosition
        } catch (e: Exception) {
            0L
        }
        val duration = try {
            MusicManager.player.duration
        } catch (e: Exception) {
            0L
        }

        val startTime = if (isPlaying && duration > 0) {
            System.currentTimeMillis() - position
        } else {
            null
        }

        try {
            sendPresence(
                track = track,
                contextName = contextName,
                largeImage = RpcImage.DiscordImage(logoAssetId),
                startTime = startTime
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            lastTrackId = -1L
            Log.e("DiscordRPC", "RPC Error: ${e.message}")
        }
    }

    private suspend fun sendPresence(
        track: Track,
        contextName: String?,
        largeImage: RpcImage?,
        startTime: Long?
    ) {
        val artistName = track.user?.username ?: "Unknown Artist"
        val trackTitle = track.title ?: "Unknown Title"
        val playlistInfo = contextName ?: "KittyTune"

        val rpcButtons = mutableListOf<Pair<String, String>>()
        if (!track.permalinkUrl.isNullOrEmpty() && track.permalinkUrl.startsWith("http")) {
            rpcButtons.add("Listen" to track.permalinkUrl)
        }

        val prefs = PlayerPreferences(context)
        val displayMode = prefs.getDiscordStatusDisplay()
        val activityName: String
        val detailsLine: String
        val stateLine: String

        when (displayMode) {
            DiscordStatusDisplay.ARTIST -> {
                activityName = artistName
                detailsLine = trackTitle
                stateLine = "on KittyTune"
            }

            DiscordStatusDisplay.SONG -> {
                activityName = trackTitle
                detailsLine = "by $artistName"
                stateLine = "on KittyTune"
            }

            DiscordStatusDisplay.ACTIVITY -> {
                activityName = "KittyTune"
                detailsLine = trackTitle
                stateLine = "by $artistName"
            }
        }

        setActivity(
            applicationId = applicationId,
            name = activityName,
            details = detailsLine,
            state = stateLine,
            largeImage = largeImage,
            largeText = playlistInfo,
            smallImage = null,
            smallText = null,
            buttons = rpcButtons.takeIf { it.isNotEmpty() },
            type = Type.LISTENING,
            startTime = startTime,
            endTime = null
        )
    }
}
