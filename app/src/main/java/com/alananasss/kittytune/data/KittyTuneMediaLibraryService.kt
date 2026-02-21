    package com.alananasss.kittytune.data
    
    import android.app.PendingIntent
    import android.content.Intent
    import android.os.Bundle
    import androidx.media3.common.ForwardingPlayer
    import androidx.media3.common.Player
    import androidx.media3.session.CommandButton
    import androidx.media3.session.DefaultMediaNotificationProvider // Import nécessaire
    import androidx.media3.session.MediaLibraryService
    import androidx.media3.session.MediaSession
    import androidx.media3.session.SessionCommand
    import com.alananasss.kittytune.MainActivity
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.network.RetrofitClient
    import com.google.common.collect.ImmutableList
    import kotlinx.coroutines.CoroutineScope
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.SupervisorJob
    import kotlinx.coroutines.cancel
    import kotlinx.coroutines.flow.collectLatest
    import kotlinx.coroutines.launch
    
    class KittyTuneMediaLibraryService : MediaLibraryService() {
    
        private var mediaLibrarySession: MediaLibrarySession? = null
        private lateinit var sessionCallback: KittyTuneMediaLibrarySessionCallback
        private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
        companion object {
            const val CUSTOM_ACTION_LIKE = "com.alananasss.kittytune.ACTION_LIKE"
            const val CUSTOM_ACTION_REPEAT = "com.alananasss.kittytune.ACTION_REPEAT"
        }
    
        override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            super.onStartCommand(intent, flags, startId)
            return START_STICKY
        }
    
        override fun onCreate() {
            super.onCreate()
    
            // Init managers
            MusicManager.init(this)
            LikeRepository.init(this)
            HistoryRepository.init(this)
            RepostRepository.init(this)
            DownloadManager.init(this)
    
            val notificationProvider = DefaultMediaNotificationProvider(this)
            notificationProvider.setSmallIcon(R.drawable.ic_notification)
            setMediaNotificationProvider(notificationProvider)
            sessionCallback = KittyTuneMediaLibrarySessionCallback(
                context = this,
                likeRepository = LikeRepository,
                api = RetrofitClient.create(this),
                serviceScope = serviceScope
            )
    
            val player = MusicManager.player
    
            if (player.isPlaying) {
            }
    
            val forwardingPlayer = object : ForwardingPlayer(player) {
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
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                        else -> super.isCommandAvailable(command)
                    }
                }
    
                override fun seekToNext() { MusicManager.onNextClick?.invoke() }
                override fun seekToNextMediaItem() { MusicManager.onNextClick?.invoke() }
                override fun seekToPrevious() { MusicManager.onPreviousClick?.invoke() }
                override fun seekToPreviousMediaItem() { MusicManager.onPreviousClick?.invoke() }
            }
    
            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
    
            mediaLibrarySession = MediaLibrarySession.Builder(this, forwardingPlayer, sessionCallback)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()
    
            player.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    super.onMediaItemTransition(mediaItem, reason)
                    mediaLibrarySession?.let { onUpdateNotification(it) }
                    updateAndroidAutoLayout()
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    super.onIsPlayingChanged(isPlaying)
                    mediaLibrarySession?.let { onUpdateNotification(it) }
                    updateAndroidAutoLayout()
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    super.onPlaybackStateChanged(playbackState)
                    mediaLibrarySession?.let { onUpdateNotification(it) }
                    updateAndroidAutoLayout()
                }
                override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
                    super.onPlaybackParametersChanged(playbackParameters)
                    mediaLibrarySession?.let { onUpdateNotification(it) }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    super.onPlayerError(error)
                    mediaLibrarySession?.let { onUpdateNotification(it) }
                }
                override fun onRepeatModeChanged(repeatMode: Int) {
                    super.onRepeatModeChanged(repeatMode)
                    updateAndroidAutoLayout()
                }
            })
    
            observeDataChanges()
        }
    
        private fun observeDataChanges() {
            serviceScope.launch {
                LikeRepository.likedTracks.collectLatest {
                    notifyAuto(KittyTuneMediaLibrarySessionCallback.LIKES_ID)
                    notifyAuto(KittyTuneMediaLibrarySessionCallback.LIBRARY_ROOT_ID)
                    updateAndroidAutoLayout()
                }
            }
    
            serviceScope.launch {
                DownloadManager.getAllPlaylistsFlow().collectLatest {
                    notifyAuto(KittyTuneMediaLibrarySessionCallback.LIBRARY_ROOT_ID)
                }
            }
            serviceScope.launch {
                DownloadManager.libraryUpdated.collect {
                    notifyAuto(KittyTuneMediaLibrarySessionCallback.LIBRARY_ROOT_ID)
                }
            }
        }
        private fun updateAndroidAutoLayout() {
            val session = mediaLibrarySession ?: return
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
            session.setCustomLayout(ImmutableList.of(likeButton, repeatButton))
        }
    
        private fun notifyAuto(parentId: String) {
            mediaLibrarySession?.notifyChildrenChanged(
                parentId,
                Int.MAX_VALUE,
                null
            )
        }
    
        override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
            return mediaLibrarySession
        }
    
        override fun onTaskRemoved(rootIntent: Intent?) {
            val player = mediaLibrarySession?.player ?: return
            if (!player.playWhenReady || player.mediaItemCount == 0) {
                stopSelf()
            }
        }
    
        override fun onDestroy() {
            serviceScope.cancel()
            mediaLibrarySession?.run {
                release()
                mediaLibrarySession = null
            }
            super.onDestroy()
        }
    }


