package com.alananasss.kittytune.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import com.alananasss.kittytune.ui.widget.MusicWidget
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "soundtune_playback_channel"
        private const val ACTION_CUSTOM_LIKE = "ACTION_CUSTOM_LIKE"
        const val ACTION_FORCE_UPDATE = "com.alananasss.kittytune.ACTION_FORCE_UPDATE"
        const val ACTION_WIDGET_PLAY_PAUSE = "com.alananasss.kittytune.ACTION_WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.alananasss.kittytune.ACTION_WIDGET_NEXT"
        const val ACTION_WIDGET_PREV = "com.alananasss.kittytune.ACTION_WIDGET_PREV"
        const val ACTION_WIDGET_LIKE = "com.alananasss.kittytune.ACTION_WIDGET_LIKE"
    }

    private var mediaSession: MediaSession? = null
    private lateinit var mediaSessionCompat: MediaSessionCompat
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private var lastNotifTitle: String? = null
    private var lastNotifArtist: String? = null
    private var lastNotifState: Int? = null
    private var lastLikedState: Boolean? = null
    private var updateJob: Job? = null
    private var discordJob: Job? = null
    private var currentAlbumArt: Bitmap? = null
    private var lastLoadedArtUrl: String? = null
    private var lastPlaybackSpeed: Float? = null
    private var discordRpc: DiscordRPC? = null
    private lateinit var prefs: PlayerPreferences

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() { MusicManager.player.play(); requestUpdate(delayed = true) }
        override fun onPause() { MusicManager.player.pause(); requestUpdate(delayed = true) }
        override fun onStop() { MusicManager.player.stop(); stopSelf() }
        override fun onSkipToNext() { MusicManager.onNextClick?.invoke() }
        override fun onSkipToPrevious() { MusicManager.onPreviousClick?.invoke() }
        override fun onSeekTo(pos: Long) { MusicManager.player.seekTo(pos); requestUpdate(delayed = true) }
        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == ACTION_CUSTOM_LIKE) handleLikeToggle()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FORCE_UPDATE -> requestUpdate(delayed = false, isForegroundServiceStart = true)
            ACTION_WIDGET_PLAY_PAUSE -> {
                if (MusicManager.player.isPlaying) sessionCallback.onPause() else sessionCallback.onPlay()
                MusicWidget.update(this)
            }
            ACTION_WIDGET_NEXT -> { sessionCallback.onSkipToNext(); MusicWidget.update(this) }
            ACTION_WIDGET_PREV -> { sessionCallback.onSkipToPrevious(); MusicWidget.update(this) }
            ACTION_WIDGET_LIKE -> { handleLikeToggle() }
        }
        if (intent != null) MediaButtonReceiver.handleIntent(mediaSessionCompat, intent)
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleLikeToggle() {
        val track = MusicManager.currentTrack ?: return
        if (LikeRepository.isTrackLiked(track.id)) LikeRepository.removeLike(track.id)
        else LikeRepository.addLike(track)
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        prefs = PlayerPreferences(this)
        MusicManager.init(this)
        createNotificationChannel()

        setMediaNotificationProvider(object : MediaNotification.Provider {
            override fun createNotification(
                mediaSession: MediaSession,
                customLayout: ImmutableList<CommandButton>,
                actionFactory: MediaNotification.ActionFactory,
                onNotificationChangedCallback: MediaNotification.Provider.Callback
            ): MediaNotification {
                val title = lastNotifTitle ?: getString(R.string.app_name)
                val artist = lastNotifArtist ?: ""
                return MediaNotification(
                    NOTIFICATION_ID,
                    buildNotification(title, artist, currentAlbumArt)
                )
            }

            override fun handleCustomCommand(
                session: MediaSession,
                action: String,
                extras: Bundle
            ): Boolean = false

            override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
                return MediaNotification.Provider.NotificationChannelInfo(
                    CHANNEL_ID,
                    getString(R.string.channel_name)
                )
            }
        })

        serviceScope.launch {
            LikeRepository.likedTracks.collect {
                if (::mediaSessionCompat.isInitialized) {
                    requestUpdate(delayed = false)
                    MusicWidget.update(this@PlaybackService)
                }
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

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setId("KittyTuneSession")
            .setSessionActivity(pendingIntent)
            .build()

        mediaSessionCompat = MediaSessionCompat(this, "KittyTuneCompat").apply {
            isActive = true
            setSessionActivity(pendingIntent)
            setCallback(sessionCallback)
        }

        MusicManager.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { requestUpdate(delayed = true) }
            override fun onIsPlayingChanged(isPlaying: Boolean) { requestUpdate(delayed = false) }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { requestUpdate(delayed = false) }
            override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                super.onPlaybackParametersChanged(playbackParameters)
                requestUpdate(delayed = false)
            }
        })

        updateCompatState()
        val defaultTitle = getString(R.string.app_name)
        val defaultArtist = getString(R.string.notification_content_default)
        updateCompatMetadata(defaultTitle, defaultArtist, null, 0L)

        try {
            val notification = buildNotification(defaultTitle, defaultArtist, null)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) { e.printStackTrace() }

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

    private fun requestUpdate(delayed: Boolean = true, isForegroundServiceStart: Boolean = false) {
        if (!::mediaSessionCompat.isInitialized) return
        if (isForegroundServiceStart) { updateJob?.cancel(); performUpdate(forceNotification = true); return }
        if (!delayed) { updateJob?.cancel(); performUpdate(); return }
        updateJob?.cancel()
        updateJob = serviceScope.launch { delay(50); performUpdate() }
    }

    private fun performUpdate(forceNotification: Boolean = false) {
        val player = MusicManager.player
        val metadata = player.mediaMetadata
        val currentTrack = MusicManager.currentTrack

        if (currentTrack != null) {
            initDiscordRpc()
            val currentContextText = MusicManager.contextFlow.value?.displayText
            discordJob?.cancel()
            discordJob = serviceScope.launch(Dispatchers.IO) { discordRpc?.updateTrack(currentTrack, currentContextText) }
        } else closeDiscordRpc()

        val currentSpeed = player.playbackParameters.speed
        val title = currentTrack?.title ?: metadata.title?.toString() ?: getString(R.string.app_name)
        val artist = currentTrack?.user?.username ?: metadata.artist?.toString() ?: ""
        val state = player.playbackState
        val isPlaying = player.isPlaying
        val currentId = currentTrack?.id ?: 0L
        val isLiked = LikeRepository.isTrackLiked(currentId)

        val newArtworkUrl = currentTrack?.fullResArtwork
        if (newArtworkUrl != lastLoadedArtUrl) {
            lastLoadedArtUrl = newArtworkUrl
            currentAlbumArt = null
            serviceScope.launch(Dispatchers.IO) {
                if (newArtworkUrl != null) {
                    val loader = imageLoader
                    val request = ImageRequest.Builder(this@PlaybackService).data(newArtworkUrl).allowHardware(false).build()
                    val result = (loader.execute(request) as? SuccessResult)?.drawable
                    val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    if (bitmap != null) {
                        currentAlbumArt = bitmap
                        launch(Dispatchers.Main) { performUpdate(forceNotification = true) }
                    }
                }
            }
        }

        if (!forceNotification && title == lastNotifTitle && artist == lastNotifArtist
            && state == lastNotifState && isPlaying == (lastNotifState == PlaybackStateCompat.STATE_PLAYING)
            && isLiked == lastLikedState && currentSpeed == lastPlaybackSpeed && currentAlbumArt != null) return

        lastNotifTitle = title
        lastNotifArtist = artist
        lastNotifState = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        lastLikedState = isLiked
        lastPlaybackSpeed = currentSpeed

        val duration = currentTrack?.durationMs?.takeIf { it > 0 } ?: player.duration.takeIf { it > 0 } ?: 0L
        updateCompatState()
        updateCompatMetadata(title, artist, currentAlbumArt, duration)

        val notification = buildNotification(title, artist, currentAlbumArt)
        try {
            if (forceNotification) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
                } else startForeground(NOTIFICATION_ID, notification)
            } else {
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                manager.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) { e.printStackTrace() }

        MusicWidget.update(this)
    }

    private fun updateCompatState() {
        val player = MusicManager.player
        val state = when (player.playbackState) {
            Player.STATE_BUFFERING -> PlaybackStateCompat.STATE_BUFFERING
            Player.STATE_READY -> if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            Player.STATE_ENDED -> PlaybackStateCompat.STATE_PAUSED
            Player.STATE_IDLE -> PlaybackStateCompat.STATE_PAUSED
            else -> PlaybackStateCompat.STATE_NONE
        }
        val currentId = MusicManager.currentTrack?.id ?: 0L
        val isLiked = LikeRepository.isTrackLiked(currentId)
        val likeIcon = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        val likeLabel = if (isLiked) getString(R.string.action_unlike) else getString(R.string.desc_like)

        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, player.currentPosition, player.playbackParameters.speed)
            .addCustomAction(PlaybackStateCompat.CustomAction.Builder(ACTION_CUSTOM_LIKE, likeLabel, likeIcon).build())

        mediaSessionCompat.setPlaybackState(playbackStateBuilder.build())
    }

    private fun updateCompatMetadata(title: String, artist: String, bitmap: Bitmap?, duration: Long) {
        val player = MusicManager.player
        val builder = MediaMetadataCompat.Builder()
        builder.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
        builder.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
        builder.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
        if (bitmap != null) {
            builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        } else {
            player.mediaMetadata.artworkData?.let { bytes ->
                try {
                    val embedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, embedBitmap)
                } catch (_: Exception) {}
            }
        }
        mediaSessionCompat.setMetadata(builder.build())
    }

    private fun buildNotification(title: String, artist: String, bitmap: Bitmap?): Notification {
        val player = MusicManager.player
        val isPlaying = player.isPlaying
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(artist)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpenIntent)
            .addAction(NotificationCompat.Action(R.drawable.ic_skip_previous, getString(R.string.desc_previous),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))

        if (isPlaying) {
            builder.addAction(NotificationCompat.Action(R.drawable.ic_play_arrow, getString(R.string.desc_pause),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)))
        } else {
            builder.addAction(NotificationCompat.Action(R.drawable.ic_play_arrow, getString(R.string.desc_play),
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)))
        }

        builder.addAction(NotificationCompat.Action(R.drawable.ic_skip_next, getString(R.string.desc_next),
            MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))

        builder.setStyle(MediaStyle()
            .setMediaSession(mediaSessionCompat.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)))

        if (bitmap != null) {
            builder.setLargeIcon(bitmap)
        } else {
            player.mediaMetadata.artworkData?.let { bytes ->
                try {
                    val embedBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    builder.setLargeIcon(embedBitmap)
                } catch (_: Exception) {}
            }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.channel_description)
                setShowBadge(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = MusicManager.player
        if (player.isPlaying) player.pause()
        player.stop()
        player.clearMediaItems()
        MusicManager.currentTrack = null
        MusicWidget.update(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        closeDiscordRpc()
        mediaSession?.run { release(); mediaSession = null }
        if (::mediaSessionCompat.isInitialized) mediaSessionCompat.release()
        super.onDestroy()
    }
}