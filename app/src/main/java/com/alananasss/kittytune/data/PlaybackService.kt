package com.alananasss.kittytune.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.ui.widget.MusicWidget
import com.alananasss.kittytune.utils.CoilBitmapLoader
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService(), Player.Listener {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "soundtune_playback_channel"

        const val ACTION_FORCE_UPDATE = "com.alananasss.kittytune.ACTION_FORCE_UPDATE"
        const val ACTION_WIDGET_PLAY_PAUSE = "com.alananasss.kittytune.ACTION_WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.alananasss.kittytune.ACTION_WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.alananasss.kittytune.ACTION_WIDGET_PREV"
        const val ACTION_WIDGET_LIKE = "com.alananasss.kittytune.ACTION_WIDGET_LIKE"

        const val CUSTOM_ACTION_LIKE = "com.alananasss.kittytune.CUSTOM_ACTION_LIKE"
        const val CUSTOM_ACTION_REPEAT = "com.alananasss.kittytune.CUSTOM_ACTION_REPEAT"

        val commandLike = SessionCommand(CUSTOM_ACTION_LIKE, Bundle.EMPTY)
        val commandRepeat = SessionCommand(CUSTOM_ACTION_REPEAT, Bundle.EMPTY)
    }

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private var discordJob: Job? = null
    private var discordRpc: DiscordRPC? = null
    private lateinit var prefs: PlayerPreferences

    override fun onCreate() {
        super.onCreate()

        prefs = PlayerPreferences(this)
        MusicManager.init(this)
        LikeRepository.init(this)
        HistoryRepository.init(this)
        RepostRepository.init(this)
        DownloadManager.init(this)

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.app_name
            ).apply {
                setSmallIcon(R.drawable.ic_notification)
            }
        )

        serviceScope.launch {
            LikeRepository.likedTracks.collect {
                updateMedia3CustomLayout()
                MusicWidget.update(this@PlaybackService)
            }
        }

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val forwardingPlayer = object : ForwardingPlayer(MusicManager.player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    .build()
            }

            override fun isCurrentMediaItemSeekable(): Boolean {
                return true
            }

            override fun seekToNext() { MusicManager.onNextClick?.invoke() }
            override fun seekToPrevious() { MusicManager.onPreviousClick?.invoke() }

            override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
                val baseMetadata = super.getMediaMetadata()
                val track = MusicManager.currentTrack ?: return baseMetadata
                
                return baseMetadata.buildUpon()
                    .setTitle(track.title)
                    .setArtist(track.user?.username)
                    .build()
            }

            override fun getDuration(): Long {
                val exoPlayerDuration = super.getDuration()
                
                if (exoPlayerDuration == androidx.media3.common.C.TIME_UNSET || exoPlayerDuration <= 0L) {
                    val apiDuration = MusicManager.currentTrack?.durationMs 
                    
                    if (apiDuration != null && apiDuration > 0) {
                        return apiDuration
                    }
                    
                    return 180_000L
                }
                return exoPlayerDuration
            }

            override fun getCurrentPosition(): Long {
                val realPosition = super.getCurrentPosition()
                // Si le lecteur est en train de charger (BUFFERING) une nouvelle musique
                if (super.getPlaybackState() == Player.STATE_BUFFERING && super.getDuration() == androidx.media3.common.C.TIME_UNSET) {
                    return 0L
                }
                return realPosition
            }

            override fun getBufferedPosition(): Long {
                if (super.getPlaybackState() == Player.STATE_BUFFERING && super.getDuration() == androidx.media3.common.C.TIME_UNSET) {
                    return 0L
                }
                return super.getBufferedPosition()
            }
        }

        val librarySessionCallback = KittyTuneMediaLibrarySessionCallbackWrapper(
            baseCallback = KittyTuneMediaLibrarySessionCallback(
                context = this,
                likeRepository = LikeRepository,
                api = RetrofitClient.create(this),
                serviceScope = serviceScope,
                onControllerConnected = { updateMedia3CustomLayout() }
            )
        )

        mediaSession = MediaLibrarySession.Builder(this, forwardingPlayer, librarySessionCallback)
            .setId("KittyTuneSession")
            .setSessionActivity(pendingIntent)
            .setBitmapLoader(CoilBitmapLoader(this, serviceScope))
            .build()

        MusicManager.player.addListener(this)
        initDiscordRpc()

        setListener(
            object : MediaSessionService.Listener {
                override fun onForegroundServiceStartNotAllowedException() {}
            }
        )

        if (!ensureStartedAsForegroundOrStop()) {
            return
        }

        updateState()
    }

    private fun ensureStartedAsForegroundOrStop(): Boolean {
        return try {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, getString(R.string.app_name), NotificationManager.IMPORTANCE_LOW)
            )
            val pending = PendingIntent.getActivity(
                this, 0, Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val trackTitle = MusicManager.currentTrack?.title ?: "Chargement..."

            val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(trackTitle)
                .setContentText(getString(R.string.app_name))
                .setSmallIcon(R.drawable.ic_notification)
                .setContentIntent(pending)
                .setStyle(androidx.media3.session.MediaStyleNotificationHelper.MediaStyle(mediaSession!!))
                .setOngoing(true)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            stopSelf()
            false
        }
    }

    override fun onPlaybackStateChanged(playbackState: Int) { updateState() }
    override fun onIsPlayingChanged(isPlaying: Boolean) { updateState() }

    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
        super.onMediaMetadataChanged(mediaMetadata)
        updateState()
        mediaSession?.let {
            try {
                super.onUpdateNotification(it, MusicManager.player.playWhenReady)
            } catch (e: Exception) {
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        super.onMediaItemTransition(mediaItem, reason)
        updateState()
        
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            serviceScope.launch {
                kotlinx.coroutines.delay(400)
                updateState()
                
                mediaSession?.let {
                    try {
                        super.onUpdateNotification(it, MusicManager.player.playWhenReady)
                    } catch (e: Exception) {
                    }
                }
            }
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) { updateState() }

    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean
    ) {
        try {
            super.onUpdateNotification(session, startInForegroundRequired)
        } catch (e: Exception) {
        }
    }

    private fun updateState() {
        updateMedia3CustomLayout()
        MusicWidget.update(this)

        val currentTrack = MusicManager.currentTrack
        if (currentTrack != null) {
            initDiscordRpc()
            val currentContextText = MusicManager.contextFlow.value?.displayText
            discordJob?.cancel()
            discordJob = serviceScope.launch(Dispatchers.IO) {
                discordRpc?.updateTrack(currentTrack, currentContextText)
            }
        } else {
            closeDiscordRpc()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_FORCE_UPDATE -> updateState()
            ACTION_WIDGET_PLAY_PAUSE -> {
                if (MusicManager.player.isPlaying) MusicManager.player.pause() else MusicManager.player.play()
                MusicWidget.update(this)
            }
            ACTION_WIDGET_NEXT -> { MusicManager.onNextClick?.invoke(); MusicWidget.update(this) }
            ACTION_WIDGET_PREV -> { MusicManager.onPreviousClick?.invoke(); MusicWidget.update(this) }
            ACTION_WIDGET_LIKE -> handleLikeToggle()
        }
        return result
    }

    fun handleLikeToggle() {
        val track = MusicManager.currentTrack ?: return
        if (LikeRepository.isTrackLiked(track.id)) LikeRepository.removeLike(track.id)
        else LikeRepository.addLike(track)
        updateState()
    }

    private fun updateMedia3CustomLayout() {
        val session = mediaSession ?: return
        val currentTrack = MusicManager.currentTrack ?: return
        val player = MusicManager.player

        val isLiked = LikeRepository.isTrackLiked(currentTrack.id)
        val likeIcon = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        val likeLabel = if (isLiked) R.string.action_unlike else R.string.desc_like

        val likeButton = CommandButton.Builder()
            .setDisplayName(getString(likeLabel))
            .setIconResId(likeIcon)
            .setSessionCommand(commandLike)
            .setEnabled(true)
            .build()

        val repeatMode = player.repeatMode
        val (repeatIcon, repeatLabel) = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> Pair(R.drawable.ic_repeat_one, R.string.menu_repeat_one)
            Player.REPEAT_MODE_ALL -> Pair(R.drawable.ic_repeat, R.string.menu_repeat_all)
            else -> Pair(R.drawable.ic_repeat_off, R.string.menu_repeat)
        }

        val repeatButton = CommandButton.Builder()
            .setDisplayName(getString(repeatLabel))
            .setIconResId(repeatIcon)
            .setSessionCommand(commandRepeat)
            .setEnabled(true)
            .build()

        val standardLayout = ImmutableList.of(likeButton)
        val autoLayout = ImmutableList.of(likeButton, repeatButton)

        session.setCustomLayout(standardLayout)

        session.connectedControllers.forEach { controller ->
            if (controller.packageName == "com.google.android.projection.gearhead") {
                session.setCustomLayout(controller, autoLayout)
            }
        }
    }

    private fun initDiscordRpc() {
        val token = prefs.getDiscordToken()
        val enabled = prefs.getDiscordRpcEnabled()
        if (enabled && !token.isNullOrEmpty()) {
            if (discordRpc == null) discordRpc = DiscordRPC(this, token)
        } else closeDiscordRpc()
    }

    private fun closeDiscordRpc() {
        discordRpc?.closeRPC()
        discordRpc = null
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val player = MusicManager.player
        runCatching {
            player.stop()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            pauseAllPlayersAndStopSelf()
        }.onFailure {
            runCatching { pauseAllPlayersAndStopSelf() }.onFailure { stopSelf() }
        }
    }

    override fun onDestroy() {
        closeDiscordRpc()
        mediaSession?.run {
            player.removeListener(this@PlaybackService)
            release()
            mediaSession = null
        }
        MusicManager.releasePlayer()
        super.onDestroy()
    }

    private inner class KittyTuneMediaLibrarySessionCallbackWrapper(
        private val baseCallback: MediaLibrarySession.Callback
    ) : MediaLibrarySession.Callback by baseCallback {

        override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult {
            val connectionResult = baseCallback.onConnect(session, controller)
            return MediaSession.ConnectionResult.accept(
                connectionResult.availableSessionCommands.buildUpon()
                    .add(commandLike)
                    .add(commandRepeat)
                    .build(),
                connectionResult.availablePlayerCommands
            )
        }

        override fun onCustomCommand(session: MediaSession, controller: MediaSession.ControllerInfo, customCommand: SessionCommand, args: Bundle): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                CUSTOM_ACTION_LIKE -> handleLikeToggle()
                CUSTOM_ACTION_REPEAT -> {
                    val player = session.player
                    player.repeatMode = when (player.repeatMode) {
                        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val player = MusicManager.player

            if (player.mediaItemCount > 0) {
                val items = mutableListOf<MediaItem>()
                for (i in 0 until player.mediaItemCount) {
                    items.add(player.getMediaItemAt(i))
                }

                val startIndex = player.currentMediaItemIndex.coerceAtLeast(0)
                val startPos = player.currentPosition.coerceAtLeast(0L)

                return Futures.immediateFuture(
                    MediaSession.MediaItemsWithStartPosition(items, startIndex, startPos)
                )
            }

            return Futures.immediateFailedFuture(UnsupportedOperationException("No items available to resume"))
        }
    }
}