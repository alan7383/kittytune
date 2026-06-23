package com.alananasss.kittytune.data

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.widget.MusicWidget
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.RetrofitClient
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.app.PendingIntent

class PlaybackService : MediaLibraryService() {

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
    }

    private var mediaSession: MediaLibrarySession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private var updateJob: Job? = null
    private var discordJob: Job? = null
    private var discordRpc: DiscordRPC? = null
    private lateinit var prefs: PlayerPreferences

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        prefs = PlayerPreferences(this)
        MusicManager.init(this)
        LikeRepository.init(this)
        HistoryRepository.init(this)
        com.alananasss.kittytune.data.RecognitionHistoryRepository.init(this)
        RepostRepository.init(this)
        DownloadManager.init(this)

        // Use DefaultMediaNotificationProvider — pure Media3, no compat hacks
        val defaultNotificationProvider = DefaultMediaNotificationProvider(
            this,
            { NOTIFICATION_ID },
            CHANNEL_ID,
            R.string.channel_name
        ).apply {
            setSmallIcon(R.drawable.ic_notification)
        }

        setMediaNotificationProvider(defaultNotificationProvider)

        // Watch for like changes to update custom layout (like button state)
        serviceScope.launch {
            LikeRepository.likedTracks.collect {
                requestUpdate(delayed = false)
                MusicWidget.update(this@PlaybackService)
            }
        }

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val forwardingPlayer = object : ForwardingPlayer(MusicManager.player) {
            override fun getAvailableCommands(): Player.Commands {
                return super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .build()
            }

            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun seekToNext() { MusicManager.onNextClick?.invoke() }
            override fun seekToPrevious() { MusicManager.onPreviousClick?.invoke() }
            override fun seekToNextMediaItem() { MusicManager.onNextClick?.invoke() }
            override fun seekToPreviousMediaItem() { MusicManager.onPreviousClick?.invoke() }
        }

        val librarySessionCallback = KittyTuneMediaLibrarySessionCallback(
            context = this,
            likeRepository = LikeRepository,
            api = RetrofitClient.create(this),
            serviceScope = serviceScope,
            onControllerConnected = { requestUpdate(delayed = false) }
        )

        mediaSession = MediaLibrarySession.Builder(this, forwardingPlayer, librarySessionCallback)
            .setId("KittyTuneSession")
            .setSessionActivity(pendingIntent)
            .build()

        MusicManager.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { requestUpdate(delayed = true) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { requestUpdate(delayed = false) }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { requestUpdate(delayed = false) }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                super.onPlaybackParametersChanged(playbackParameters)
                requestUpdate(delayed = false)
            }
            override fun onRepeatModeChanged(repeatMode: Int) {
                super.onRepeatModeChanged(repeatMode)
                requestUpdate(delayed = false)
            }
        })

        // Keep a connected controller so that Media3 automatically posts the notification
        val sessionToken = androidx.media3.session.SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val controllerFuture = androidx.media3.session.MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, androidx.core.content.ContextCompat.getMainExecutor(this))

        initDiscordRpc()
    }

    private fun initDiscordRpc() {
        val token = prefs.getDiscordToken()
        val enabled = prefs.getDiscordRpcEnabled()
        if (enabled && !token.isNullOrEmpty()) {
            if (discordRpc == null) discordRpc = DiscordRPC(this, token)
        } else closeDiscordRpc()
    }

    private fun closeDiscordRpc() { discordRpc?.closeRPC(); discordRpc = null }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FORCE_UPDATE -> requestUpdate(delayed = false)
            ACTION_WIDGET_PLAY_PAUSE -> {
                if (MusicManager.player.isPlaying) MusicManager.player.pause() else MusicManager.player.play()
                MusicWidget.update(this)
            }
            ACTION_WIDGET_NEXT -> { MusicManager.onNextClick?.invoke(); MusicWidget.update(this) }
            ACTION_WIDGET_PREV -> { MusicManager.onPreviousClick?.invoke(); MusicWidget.update(this) }
            ACTION_WIDGET_LIKE -> { handleLikeToggle() }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleLikeToggle() {
        val track = MusicManager.currentTrack ?: return
        if (LikeRepository.isTrackLiked(track.id)) LikeRepository.removeLike(track.id)
        else LikeRepository.addLike(track)
    }

    private fun requestUpdate(delayed: Boolean = true) {
        if (!delayed) { updateJob?.cancel(); performUpdate(); return }
        updateJob?.cancel()
        updateJob = serviceScope.launch { delay(50); performUpdate() }
    }

    @OptIn(UnstableApi::class)
    private fun performUpdate() {
        // Update custom layout (like/repeat buttons) for the Media3 session
        updateMedia3CustomLayout()

        val currentTrack = MusicManager.currentTrack

        // Discord RPC
        if (currentTrack != null) {
            initDiscordRpc()
            val currentContextText = MusicManager.contextFlow.value?.displayText
            val isPlaying = MusicManager.player.isPlaying
            val position = MusicManager.player.currentPosition
            discordJob?.cancel()
            discordJob = serviceScope.launch(Dispatchers.IO) {
                delay(500)
                discordRpc?.updatePresence(currentTrack, currentContextText, isPlaying, position)
            }
        } else closeDiscordRpc()

        // Widget update
        MusicWidget.update(this)
    }

    @OptIn(UnstableApi::class)
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
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_LIKE, Bundle.EMPTY))
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
            .setSessionCommand(SessionCommand(CUSTOM_ACTION_REPEAT, Bundle.EMPTY))
            .setEnabled(true)
            .build()
            
        val defaultLayout = ImmutableList.of(likeButton)
        val autoLayout = ImmutableList.of(likeButton, repeatButton)
        
        session.setCustomLayout(defaultLayout)
        
        for (controller in session.connectedControllers) {
            val pkg = controller.packageName ?: ""
            if (pkg.contains("gearhead") || pkg.contains("automotive")) {
                session.setCustomLayout(controller, autoLayout)
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (prefs.getStopOnTaskClear()) {
            val player = MusicManager.player
            player.stop()
            player.clearMediaItems()
            MusicManager.currentTrack = null
            MusicWidget.update(this)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        super.onTaskRemoved(rootIntent)
        // User removed the task while paused: drop foreground promotion so the process can idle
        if (MusicManager.player.isPlaying.not()) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
        }
    }

    override fun onDestroy() {
        closeDiscordRpc()
        mediaSession?.run { release(); mediaSession = null }
        super.onDestroy()
    }
}