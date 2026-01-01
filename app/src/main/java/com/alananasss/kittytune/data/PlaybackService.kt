package com.alananasss.kittytune.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.alananasss.kittytune.MainActivity
import com.alananasss.kittytune.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    companion object {
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "soundtune_playback_channel"
        private const val ACTION_CUSTOM_LIKE = "ACTION_CUSTOM_LIKE"
        // added: action to force a refresh
        const val ACTION_FORCE_UPDATE = "com.alananasss.kittytune.ACTION_FORCE_UPDATE"
    }

    private var mediaSession: MediaSession? = null
    private lateinit var mediaSessionCompat: MediaSessionCompat

    private val serviceScope = CoroutineScope(Dispatchers.Main)

    private val sessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() { MusicManager.player.play() }
        override fun onPause() { MusicManager.player.pause() }
        override fun onStop() { MusicManager.player.stop(); stopSelf() }
        override fun onSkipToNext() { MusicManager.onNextClick?.invoke() }
        // MODIFIÉ : La logique est maintenant entièrement gérée par le PlayerViewModel via le MusicManager
        override fun onSkipToPrevious() {
            MusicManager.onPreviousClick?.invoke()
        }
        override fun onSeekTo(pos: Long) { MusicManager.player.seekTo(pos) }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            if (action == ACTION_CUSTOM_LIKE) {
                handleLikeToggle()
            }
        }
    }

    // command handler
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_FORCE_UPDATE) {
            // viewmodel told us it's ready, show the panel
            updateNotification()
        }
        if (intent != null) {
            MediaButtonReceiver.handleIntent(mediaSessionCompat, intent)
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun handleLikeToggle() {
        val track = MusicManager.currentTrack ?: return
        val isLiked = LikeRepository.isTrackLiked(track.id)

        if (isLiked) {
            LikeRepository.removeLike(track.id)
        } else {
            LikeRepository.addLike(track)
        }
        // La mise à jour est maintenant automatique grâce au collecteur de flux
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        MusicManager.init(this)
        createNotificationChannel()

        // AJOUTÉ : Observe les changements de "likes" en temps réel
        serviceScope.launch {
            LikeRepository.likedTracks.collect {
                if (::mediaSessionCompat.isInitialized) {
                    updateNotification()
                }
            }
        }

        val sessionIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, sessionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, MusicManager.player)
            .setId("KittyTuneSession")
            .setSessionActivity(pendingIntent)
            .build()

        mediaSessionCompat = MediaSessionCompat(this, "KittyTuneCompat").apply {
            isActive = true
            setSessionActivity(pendingIntent)
            setCallback(sessionCallback)
        }

        MusicManager.player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { updateNotification() }
            override fun onIsPlayingChanged(isPlaying: Boolean) { updateNotification() }
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) { updateNotification() }
        })

        // basic init
        updateCompatState()
        updateCompatMetadata()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    private fun updateNotification() {
        if (!::mediaSessionCompat.isInitialized) return
        updateCompatState()
        updateCompatMetadata()

        // important: notify the system
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun updateCompatState() {
        val player = MusicManager.player
        val state = if (player.isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED

        val currentId = MusicManager.currentTrack?.id ?: 0L // Utilise la source de vérité
        val isLiked = LikeRepository.isTrackLiked(currentId)

        val likeIcon = if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
        val likeLabel = if (isLiked) "Unlike" else "Like"

        val playbackStateBuilder = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_STOP or PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, player.currentPosition, player.playbackParameters.speed)
            .addCustomAction(
                PlaybackStateCompat.CustomAction.Builder(
                    ACTION_CUSTOM_LIKE,
                    likeLabel,
                    likeIcon
                ).build()
            )

        mediaSessionCompat.setPlaybackState(playbackStateBuilder.build())
    }

    private fun updateCompatMetadata() {
        val player = MusicManager.player
        val currentMedia = player.currentMediaItem?.mediaMetadata ?: return

        val builder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, currentMedia.title.toString())
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, currentMedia.artist.toString())
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.duration.coerceAtLeast(0))

        currentMedia.artworkData?.let { bytes ->
            try {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                builder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
            } catch (e: Exception) { e.printStackTrace() }
        }

        mediaSessionCompat.setMetadata(builder.build())
    }

    private fun buildNotification(): Notification {
        val player = MusicManager.player
        val metadata = player.mediaMetadata
        val isPlaying = player.isPlaying

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(metadata.title ?: "SoundTune")
            .setContentText(metadata.artist ?: "Lecture en cours")
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingOpenIntent)

            .addAction(NotificationCompat.Action(android.R.drawable.ic_media_previous, "Précédent", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))

        if (isPlaying) {
            builder.addAction(NotificationCompat.Action(android.R.drawable.ic_media_pause, "Pause", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PAUSE)))
        } else {
            builder.addAction(NotificationCompat.Action(android.R.drawable.ic_media_play, "Jouer", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_PLAY)))
        }

        builder.addAction(NotificationCompat.Action(android.R.drawable.ic_media_next, "Suivant", MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))

        builder.setStyle(
            MediaStyle()
                .setMediaSession(mediaSessionCompat.sessionToken)
                .setShowActionsInCompactView(0, 1, 2)
                .setShowCancelButton(true)
                .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP))
        )

        metadata.artworkData?.let { bytes ->
            try {
                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                builder.setLargeIcon(bitmap)
            } catch (e: Exception) { e.printStackTrace() }
        }

        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Lecteur Musique", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Contrôles du lecteur"
                setShowBadge(false)
                setSound(null, null)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        mediaSession?.run { release(); mediaSession = null }
        if (::mediaSessionCompat.isInitialized) mediaSessionCompat.release()
        super.onDestroy()
    }
}