package com.alananasss.kittytune.data

import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.core.app.ServiceCompat
import androidx.media3.common.C
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
import com.alananasss.kittytune.data.local.NotificationExtraButton
import com.alananasss.kittytune.data.network.RetrofitClient
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import android.app.PendingIntent
import androidx.core.net.toUri

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
        const val CUSTOM_ACTION_DISLIKE = "com.alananasss.kittytune.CUSTOM_ACTION_DISLIKE"
        const val CUSTOM_ACTION_SHUFFLE = "com.alananasss.kittytune.CUSTOM_ACTION_SHUFFLE"
        const val CUSTOM_ACTION_ADD_TO_PLAYLIST = "com.alananasss.kittytune.CUSTOM_ACTION_ADD_TO_PLAYLIST"
        const val CUSTOM_ACTION_HAPTICS = "com.alananasss.kittytune.CUSTOM_ACTION_HAPTICS"
        const val CUSTOM_ACTION_SHARE = "com.alananasss.kittytune.CUSTOM_ACTION_SHARE"
        const val CUSTOM_ACTION_DOWNLOAD = "com.alananasss.kittytune.CUSTOM_ACTION_DOWNLOAD"
    }

    private var mediaSession: MediaLibrarySession? = null
    private var librarySessionCallback: KittyTuneMediaLibrarySessionCallback? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private var updateJob: Job? = null
    private var discordJob: Job? = null
    private var discordRpc: DiscordRPC? = null
    private var automotiveLyricsJob: Job? = null
    private var automotiveLyricsGeneration = 0
    private var currentForwardingPlayer: KittyTuneForwardingPlayer? = null
    private lateinit var prefs: PlayerPreferences

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        prefs = PlayerPreferences(this)
        MusicManager.init(this)
        LikeRepository.init(this)
        SocialProofRepository.init(this)
        HistoryRepository.init(this)
        com.alananasss.kittytune.data.RecognitionHistoryRepository.init(this)
        RepostRepository.init(this)
        DownloadManager.init(this)
        BlockManager.init(this)

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

        serviceScope.launch {
            MusicManager.isLoadingTrackFlow.collect { isLoading ->
                currentForwardingPlayer?.notifyLoadingStateChanged()
                requestUpdate(delayed = false)
            }
        }

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)


        librarySessionCallback = KittyTuneMediaLibrarySessionCallback(
            context = this,
            likeRepository = LikeRepository,
            api = RetrofitClient.create(this),
            serviceScope = serviceScope,
            onControllerConnected = { requestUpdate(delayed = false) }
        )

        mediaSession = MediaLibrarySession.Builder(this, createForwardingPlayer(), librarySessionCallback!!)
            .setId("KittyTuneSession")
            .setSessionActivity(pendingIntent)
            .build()

        val serviceListener = object : Player.Listener {
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
        }

        MusicManager.player.addListener(serviceListener)

        serviceScope.launch {
            MusicManager.onPlayerSwappedFlow.collect { swappedCount ->
                if (swappedCount > 0) {
                    MusicManager.lastPlayer?.removeListener(serviceListener)
                    MusicManager.player.addListener(serviceListener)
                    mediaSession?.player = createForwardingPlayer()
                }
            }
        }

        // Keep a connected controller so that Media3 automatically posts the notification
        val sessionToken = androidx.media3.session.SessionToken(this, android.content.ComponentName(this, PlaybackService::class.java))
        val controllerFuture = androidx.media3.session.MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, androidx.core.content.ContextCompat.getMainExecutor(this))

        // Android Auto synchronized lyrics observer
        serviceScope.launch {
            combine(
                librarySessionCallback!!.isAutomotiveControllerConnected,
                prefs.getAndroidAutoSyncedLyricsFlow()
            ) { automotiveConnected, lyricsEnabled ->
                android.util.Log.d("KittyTuneAA", "combine flow: auto=$automotiveConnected, pref=$lyricsEnabled")
                automotiveConnected && lyricsEnabled
            }.collectLatest { shouldRun ->
                android.util.Log.d("KittyTuneAA", "automotiveLyricsObserver: shouldRun=$shouldRun")
                automotiveLyricsJob?.cancel()
                if (!shouldRun) {
                    automotiveLyricsJob = null
                    val currentTrack = MusicManager.currentTrack
                    if (currentTrack != null) {
                        val original = currentTrack.displayArtist.ifBlank { currentTrack.user?.username ?: getString(R.string.unknown_artist) }
                        replaceCurrentMediaSubtitle(currentTrack.id.toString(), original)
                    }
                    return@collectLatest
                }

                automotiveLyricsJob = launch {
                    runAutomotiveLyricsWorker()
                }
            }
        }

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

        val extraSetting = prefs.getNotificationExtraButton()
        val extraButton: CommandButton? = when (extraSetting) {
            NotificationExtraButton.DISLIKE -> {
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.notif_btn_dislike))
                    .setIconResId(R.drawable.ic_heart_broken)
                    .setSessionCommand(SessionCommand(CUSTOM_ACTION_DISLIKE, Bundle.EMPTY))
                    .setEnabled(true)
                    .build()
            }
            NotificationExtraButton.SHUFFLE -> {
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.notif_btn_shuffle))
                    .setIconResId(R.drawable.rounded_shuffle_24)
                    .setSessionCommand(SessionCommand(CUSTOM_ACTION_SHUFFLE, Bundle.EMPTY))
                    .setEnabled(true)
                    .build()
            }
            NotificationExtraButton.REPEAT -> {
                repeatButton
            }
            NotificationExtraButton.ADD_TO_LAST_PLAYLIST -> {
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.notif_btn_add_playlist))
                    .setIconResId(R.drawable.ic_playlist_add)
                    .setSessionCommand(SessionCommand(CUSTOM_ACTION_ADD_TO_PLAYLIST, Bundle.EMPTY))
                    .setEnabled(true)
                    .build()
            }
            NotificationExtraButton.HAPTICS -> {
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.notif_btn_haptics))
                    .setIconResId(R.drawable.ic_vibration)
                    .setSessionCommand(SessionCommand(CUSTOM_ACTION_HAPTICS, Bundle.EMPTY))
                    .setEnabled(true)
                    .build()
            }
            NotificationExtraButton.SHARE -> {
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.notif_btn_share))
                    .setIconResId(R.drawable.ic_share)
                    .setSessionCommand(SessionCommand(CUSTOM_ACTION_SHARE, Bundle.EMPTY))
                    .setEnabled(true)
                    .build()
            }
            NotificationExtraButton.DOWNLOAD -> {
                CommandButton.Builder()
                    .setDisplayName(getString(R.string.notif_btn_download))
                    .setIconResId(R.drawable.ic_download)
                    .setSessionCommand(SessionCommand(CUSTOM_ACTION_DOWNLOAD, Bundle.EMPTY))
                    .setEnabled(true)
                    .build()
            }
            NotificationExtraButton.OFF -> null
        }

        val defaultLayout = if (extraButton != null) {
            ImmutableList.of(likeButton, extraButton)
        } else {
            ImmutableList.of(likeButton)
        }
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

    private fun replaceCurrentMediaSubtitle(
        mediaId: String,
        subtitle: CharSequence?,
        isLyric: Boolean = false,
    ) {
        val player = MusicManager.player
        val index = player.currentMediaItemIndex
        if (index == androidx.media3.common.C.INDEX_UNSET || index !in 0 until player.mediaItemCount) return

        val mediaItem = player.getMediaItemAt(index)
        val rawItemMediaId = mediaItem.mediaId
        val matchesId = rawItemMediaId == mediaId ||
                rawItemMediaId.contains(mediaId) ||
                rawItemMediaId.removePrefix("track:").substringBefore(":context:") == mediaId

        android.util.Log.d("KittyTuneAA", "replaceSubtitle: rawId=$rawItemMediaId, target=$mediaId, matches=$matchesId, currentSub=${mediaItem.mediaMetadata.subtitle}, newSub=$subtitle, isLyric=$isLyric")
        if (!matchesId) return
        if (mediaItem.mediaMetadata.subtitle == subtitle && mediaItem.mediaMetadata.artist == subtitle) return

        try {
            val title = mediaItem.mediaMetadata.title
            val metaBuilder = mediaItem.mediaMetadata.buildUpon()
                .setSubtitle(subtitle)
                .setArtist(subtitle)
            if (isLyric && title != null) {
                metaBuilder.setDisplayTitle(title)
            }
            player.replaceMediaItem(
                index,
                mediaItem.buildUpon()
                    .setMediaMetadata(metaBuilder.build())
                    .build()
            )
        } catch (e: Exception) {
            android.util.Log.w("KittyTuneAA", "replaceMediaItem failed: ${e.message}")
        }
    }

    private suspend fun runAutomotiveLyricsWorker() {
        val generation = ++automotiveLyricsGeneration
        android.util.Log.d("KittyTuneAA", "runAutomotiveLyricsWorker started (gen=$generation)")
        try {
            var lastSegmentKey: Pair<Int, Int>? = null
            var lastSubtitle: CharSequence? = null
            var currentMediaId: String? = null
            var originalSubtitle: String? = null

            while (serviceScope.isActive && generation == automotiveLyricsGeneration) {
                val currentTrack = MusicManager.currentTrack
                val player = MusicManager.player
                if (currentTrack == null || !player.isPlaying) {
                    delay(400)
                    continue
                }

                val mediaId = currentTrack.id.toString()
                if (mediaId != currentMediaId) {
                    currentMediaId = mediaId
                    originalSubtitle = currentTrack.displayArtist.ifBlank { currentTrack.user?.username ?: getString(R.string.unknown_artist) }
                    lastSegmentKey = null
                    lastSubtitle = null
                }

                var lines = MusicManager.currentLyricsFlow.value
                if (lines.isEmpty()) {
                    val cached = LyricsCache.get(currentTrack.id, null, null, false)
                    if (cached != null && cached.found && cached.lines.isNotEmpty()) {
                        MusicManager.currentLyricsFlow.value = cached.lines
                        lines = cached.lines
                    } else {
                        withContext(Dispatchers.IO) {
                            try {
                                val provider = com.alananasss.kittytune.data.lyrics.providers.LyricsProviders.get(
                                    com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider.BETTER_LYRICS
                                ) ?: com.alananasss.kittytune.data.lyrics.providers.LyricsProviders.get(
                                    com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider.LRCLIB
                                )
                                val durationSec = ((currentTrack.durationMs ?: 0L) / 1000L).toInt()
                                val res = provider?.getLyrics(
                                    id = currentTrack.id.toString(),
                                    title = currentTrack.title ?: "",
                                    artist = currentTrack.displayArtist,
                                    album = null,
                                    duration = durationSec
                                )
                                val lrc = res?.getOrNull()
                                    if (!lrc.isNullOrBlank()) {
                                        val parsed = com.alananasss.kittytune.ui.player.lyrics.LyricsUtils.parseLyricsContent(lrc, currentTrack.durationMs ?: 0L)
                                        if (parsed.isNotEmpty()) {
                                            LyricsCache.put(
                                                currentTrack.id,
                                                LyricsCache.Entry(found = true, lines = parsed, plain = null)
                                            )
                                            MusicManager.currentLyricsFlow.value = parsed
                                        }
                                    }
                            } catch (_: Exception) {}
                        }
                        lines = MusicManager.currentLyricsFlow.value
                    }
                }

                if (lines.isEmpty()) {
                    if (lastSubtitle != originalSubtitle) {
                        replaceCurrentMediaSubtitle(mediaId, originalSubtitle, isLyric = false)
                        lastSubtitle = originalSubtitle
                    }
                    delay(1000)
                    continue
                }

                val lyricsOffsetMs = MusicManager.lyricsOffsetMs
                val trackDurationMs = player.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET && it > 0L }
                val positionMs = player.currentPosition.coerceAtLeast(0L)

                val currentLine = com.alananasss.kittytune.data.auto.AndroidAutoLyrics.currentLine(
                    lines = lines,
                    positionMs = positionMs,
                    offsetMs = lyricsOffsetMs,
                    trackDurationMs = trackDurationMs
                )

                val isLyric = currentLine != null
                val subtitle = currentLine?.text ?: originalSubtitle
                val segmentKey = currentLine?.let { it.index to it.segmentIndex }

                if (segmentKey != lastSegmentKey || subtitle != lastSubtitle) {
                    replaceCurrentMediaSubtitle(mediaId, subtitle, isLyric = isLyric)
                    lastSegmentKey = segmentKey
                    lastSubtitle = subtitle
                }

                delay(com.alananasss.kittytune.data.auto.AndroidAutoLyrics.UPDATE_INTERVAL_MS)
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("PlaybackService", "Automotive lyrics update error: ${e.message}")
        } finally {
            if (generation == automotiveLyricsGeneration) {
                val currentTrack = MusicManager.currentTrack
                if (currentTrack != null) {
                    val original = currentTrack.displayArtist.ifBlank { currentTrack.user?.username ?: getString(R.string.unknown_artist) }
                    replaceCurrentMediaSubtitle(currentTrack.id.toString(), original, isLyric = false)
                }
            }
        }
    }

    private fun createForwardingPlayer(): KittyTuneForwardingPlayer {
        return KittyTuneForwardingPlayer(MusicManager.player).also {
            currentForwardingPlayer = it
        }
    }

    inner class KittyTuneForwardingPlayer(delegate: Player) : ForwardingPlayer(delegate) {
        private val wrappedListeners = java.util.concurrent.CopyOnWriteArrayList<Player.Listener>()
        private val listenerMap = java.util.concurrent.ConcurrentHashMap<Player.Listener, Player.Listener>()

        override fun getDuration(): Long {
            val superDuration = super.getDuration()
            if (superDuration > 0L && superDuration != C.TIME_UNSET) {
                return superDuration
            }
            val trackDuration = MusicManager.currentTrack?.actualDurationMs ?: 0L
            if (trackDuration > 0L) {
                return trackDuration
            }
            return superDuration
        }

        override fun isCurrentMediaItemSeekable(): Boolean {
            if (super.isCurrentMediaItemSeekable()) return true
            return MusicManager.currentTrack != null
        }

        override fun isCurrentMediaItemDynamic(): Boolean {
            if (MusicManager.currentTrack != null) return false
            return super.isCurrentMediaItemDynamic()
        }

        override fun isCurrentMediaItemLive(): Boolean {
            if (MusicManager.currentTrack != null) return false
            return super.isCurrentMediaItemLive()
        }

        override fun getMediaMetadata(): androidx.media3.common.MediaMetadata {
            val superMeta = super.getMediaMetadata()
            val track = MusicManager.currentTrack
            if (track != null && (superMeta.title.isNullOrEmpty() || superMeta.durationMs == null || superMeta.durationMs == 0L)) {
                val artist = track.displayArtist.ifBlank { getString(R.string.unknown_artist) }
                return superMeta.buildUpon()
                    .setTitle(superMeta.title?.takeIf { it.isNotEmpty() } ?: track.title ?: getString(R.string.untitled_track))
                    .setArtist(superMeta.artist?.takeIf { it.isNotEmpty() } ?: artist)
                    .setSubtitle(superMeta.subtitle?.takeIf { it.isNotEmpty() } ?: artist)
                    .setIsPlayable(true)
                    .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(superMeta.artworkUri ?: track.fullResArtwork.toUri())
                    .setDurationMs(track.actualDurationMs)
                    .build()
            }
            return superMeta
        }

        override fun getCurrentMediaItem(): androidx.media3.common.MediaItem? {
            val superItem = super.getCurrentMediaItem()
            if (superItem != null) return superItem
            val track = MusicManager.currentTrack ?: return null
            val artist = track.displayArtist.ifBlank { getString(R.string.unknown_artist) }
            val meta = androidx.media3.common.MediaMetadata.Builder()
                .setTitle(track.title ?: getString(R.string.untitled_track))
                .setArtist(artist)
                .setSubtitle(artist)
                .setIsPlayable(true)
                .setMediaType(androidx.media3.common.MediaMetadata.MEDIA_TYPE_MUSIC)
                .setArtworkUri(track.fullResArtwork.toUri())
                .setDurationMs(track.actualDurationMs)
                .build()
            return androidx.media3.common.MediaItem.Builder()
                .setMediaId(track.id.toString())
                .setUri("soundtune://track/${track.id}".toUri())
                .setMediaMetadata(meta)
                .build()
        }

        override fun getAvailableCommands(): Player.Commands {
            return super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_GET_TIMELINE)
                .add(Player.COMMAND_GET_METADATA)
                .add(Player.COMMAND_PLAY_PAUSE)
                .build()
        }

        override fun isCommandAvailable(command: Int): Boolean {
            return when (command) {
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM, Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE, Player.COMMAND_GET_METADATA,
                Player.COMMAND_PLAY_PAUSE -> true
                else -> super.isCommandAvailable(command)
            }
        }

        override fun getPlaybackState(): Int {
            val base = super.getPlaybackState()
            return if (MusicManager.isLoadingTrack || (MusicManager.currentTrack != null && base == Player.STATE_IDLE)) {
                Player.STATE_BUFFERING
            } else {
                base
            }
        }

        override fun getPlayWhenReady(): Boolean {
            return if (MusicManager.isLoadingTrack) {
                MusicManager.playWhenReadyRequested
            } else {
                super.getPlayWhenReady()
            }
        }

        override fun isPlaying(): Boolean {
            return if (MusicManager.isLoadingTrack) {
                false
            } else {
                super.isPlaying()
            }
        }

        override fun getCurrentPosition(): Long {
            return if (MusicManager.isLoadingTrack) {
                MusicManager.pendingSeekPositionMs ?: 0L
            } else {
                super.getCurrentPosition()
            }
        }

        override fun seekTo(positionMs: Long) {
            if (MusicManager.isLoadingTrack) {
                MusicManager.pendingSeekPositionMs = positionMs
                notifyPositionDiscontinuity(positionMs)
            } else {
                super.seekTo(positionMs)
            }
        }

        override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
            if (MusicManager.isLoadingTrack) {
                MusicManager.pendingSeekPositionMs = positionMs
                notifyPositionDiscontinuity(positionMs)
            } else {
                super.seekTo(mediaItemIndex, positionMs)
            }
        }

        override fun play() {
            if (MusicManager.isLoadingTrack) {
                MusicManager.playWhenReadyRequested = true
                notifyLoadingStateChanged()
            } else {
                super.play()
            }
        }

        override fun pause() {
            if (MusicManager.isLoadingTrack) {
                MusicManager.playWhenReadyRequested = false
                notifyLoadingStateChanged()
            } else {
                super.pause()
            }
        }

        override fun seekToNext() { MusicManager.onNextClick?.invoke() }
        override fun seekToPrevious() { MusicManager.onPreviousClick?.invoke() }
        override fun seekToNextMediaItem() { MusicManager.onNextClick?.invoke() }
        override fun seekToPreviousMediaItem() { MusicManager.onPreviousClick?.invoke() }

        override fun addListener(listener: Player.Listener) {
            val wrapped = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val effectiveState = if (MusicManager.isLoadingTrack || (MusicManager.currentTrack != null && playbackState == Player.STATE_IDLE)) {
                        Player.STATE_BUFFERING
                    } else {
                        playbackState
                    }
                    listener.onPlaybackStateChanged(effectiveState)
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    val effective = if (MusicManager.isLoadingTrack) MusicManager.playWhenReadyRequested else playWhenReady
                    listener.onPlayWhenReadyChanged(effective, reason)
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    val effective = if (MusicManager.isLoadingTrack) false else isPlaying
                    listener.onIsPlayingChanged(effective)
                }

                override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                    listener.onMediaItemTransition(mediaItem, reason)
                }

                override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                    listener.onTimelineChanged(timeline, reason)
                }

                override fun onEvents(player: Player, events: Player.Events) {
                    listener.onEvents(this@KittyTuneForwardingPlayer, events)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    listener.onPositionDiscontinuity(oldPosition, newPosition, reason)
                }
            }
            listenerMap[listener] = wrapped
            wrappedListeners.add(wrapped)
            super.addListener(wrapped)
        }

        override fun removeListener(listener: Player.Listener) {
            val wrapped = listenerMap.remove(listener) ?: listener
            wrappedListeners.remove(wrapped)
            super.removeListener(wrapped)
        }

        fun notifyLoadingStateChanged() {
            val state = getPlaybackState()
            val pwr = getPlayWhenReady()
            val playing = isPlaying
            for (l in wrappedListeners) {
                try {
                    l.onPlaybackStateChanged(state)
                    l.onPlayWhenReadyChanged(pwr, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                    l.onIsPlayingChanged(playing)
                } catch (_: Exception) {}
            }
        }

        private fun notifyPositionDiscontinuity(positionMs: Long) {
            val oldPos = Player.PositionInfo(null, 0, null, null, 0, 0L, 0L, 0, 0)
            val newPos = Player.PositionInfo(null, 0, null, null, 0, positionMs, positionMs, 0, 0)
            for (l in wrappedListeners) {
                try {
                    l.onPositionDiscontinuity(oldPos, newPos, Player.DISCONTINUITY_REASON_SEEK)
                } catch (_: Exception) {}
            }
        }
    }

    override fun onDestroy() {
        closeDiscordRpc()
        automotiveLyricsJob?.cancel()
        librarySessionCallback?.release()
        mediaSession?.run { release(); mediaSession = null }
        super.onDestroy()
    }
}
