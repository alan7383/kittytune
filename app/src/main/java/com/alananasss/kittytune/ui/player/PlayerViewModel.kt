package com.alananasss.kittytune.ui.player

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.annotation.StringRes
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.CopyOnWriteArrayList
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.*
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.ui.common.AchievementNotificationManager
import com.alananasss.kittytune.ui.common.AchievementNotification
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.LrcLibClient
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.network.LrcLibResponse
import com.alananasss.kittytune.data.network.MusixmatchClient
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudTelemetryTracker
import com.alananasss.kittytune.domain.*
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

enum class CommentSort(val value: String, @param:StringRes val labelResId: Int) {
    NEWEST("newest", R.string.sort_newest),
    TIMESTAMP("track-timestamp", R.string.sort_timestamp),
    OLDEST("oldest", R.string.sort_oldest),
}

enum class LyricsMode { SYNCED, PLAIN }

data class UnifiedLyricResult(
    val id: String,
    val name: String,
    val artistName: String,
    val albumName: String?,
    val durationSec: Double,
    val hasLineSync: Boolean,
    val hasWordSync: Boolean,
    val provider: String
)

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = com.alananasss.kittytune.utils.AppUtils.gson
    val api = RetrofitClient.create(application)
    private val context get() = getApplication<Application>().applicationContext
    private val playerPrefs = PlayerPreferences(context)
    private val tokenManager = TokenManager(context)

    suspend fun getWaveformForTrack(track: Track): FloatArray? {
        return com.alananasss.kittytune.data.WaveformRepository.getWaveform(context, track, api)
    }

    fun prefetchWaveformsForQueue(centerIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val start = (centerIndex - 2).coerceAtLeast(0)
            val end = (centerIndex + 5).coerceAtMost(_queue.size - 1)
            for (i in start..end) {
                _queue.getOrNull(i)?.let { t ->
                    com.alananasss.kittytune.data.WaveformRepository.prefetchWaveform(context, t, api)
                }
            }
        }
    }

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    var currentUserId by mutableLongStateOf(0L)
    var currentUser by mutableStateOf<User?>(null)
    var currentTrack by mutableStateOf<Track?>(null)
    var isPlaying by mutableStateOf(false)
    var playWhenReady by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var duration by mutableLongStateOf(0L)
    var currentPosition by mutableLongStateOf(0L)
    var isScrubbing by mutableStateOf(false)
    var isPlayerExpanded by mutableStateOf(false)
    var isSidePlayerOpen by mutableStateOf(false)
    var isTabletSplitMode by mutableStateOf(true)
    var isLiked by mutableStateOf(false)
    var backgroundColor by mutableStateOf(Color(0xFF1E1E1E))
    val hasLyrics by derivedStateOf { lyricsLines.isNotEmpty() || !rawPlainLyrics.isNullOrBlank() }
    var commentSort by mutableStateOf(CommentSort.NEWEST)

    data class WaveformReactionParticle(
        val id: String = java.util.UUID.randomUUID().toString(),
        val emoji: String,
        val avatarUrl: String?,
        val timestamp: Long
    )

    var activeWaveformReaction by mutableStateOf<WaveformReactionParticle?>(null)
    var trackReactionCounts by mutableStateOf<Map<String, Int>>(emptyMap())
    var trackReactionUsers by mutableStateOf<Map<String, List<TrackReactionUserItem>>>(emptyMap())
    var isReactionsLoading by mutableStateOf(false)

    var currentContext by mutableStateOf<PlaybackContext?>(null)

    @Volatile
    private var isRestoringSession = true

    val player: ExoPlayer
        get() {
            try {
                return MusicManager.player
            } catch (_: IllegalStateException) {
                MusicManager.init(context)
                MusicManager.player.addListener(playerListener)
                MusicManager.applyEffects(effectsState)
                return MusicManager.player
            }
        }

    var effectsState by mutableStateOf(playerPrefs.getLastEffects())
    var isPreciseSpeedEnabled by mutableStateOf(playerPrefs.getPreciseSpeedEnabled())

    var repeatMode by mutableStateOf(playerPrefs.getLastRepeatMode())
    var shuffleEnabled by mutableStateOf(playerPrefs.getLastShuffleEnabled())
    private var isAutoplayRadioLoading by mutableStateOf(false)
    private var boundPlayer: ExoPlayer? = null

    var repostedTrackIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var showMenuSheet by mutableStateOf(false)
    var navigateToPlaylistId by mutableStateOf<String?>(null)
    var trackForMenu by mutableStateOf<Track?>(null)
    var trackToEdit by mutableStateOf<Track?>(null)
    var menuContextPlaylistId by mutableStateOf<Long?>(null)
    var isMenuContextFromPlayer by mutableStateOf(false)

    var socialLikers by mutableStateOf<List<User>>(emptyList())
    var isSocialLikersLoading by mutableStateOf(false)
    private var socialProofTrackId: Long? = null

    var selectedTrackForSheet by mutableStateOf<Track?>(null)
    var isLocalDetailsMode by mutableStateOf(false)
    var localFilePathForDetails by mutableStateOf<String?>(null)

    var showDetailsSheet by mutableStateOf(false)

    var showCommentsSheet by mutableStateOf(false)
    val commentsList = mutableStateListOf<Comment>()
    var isCommentsLoading by mutableStateOf(false)
    var commentNextHref: String? = null
    var isPostingComment by mutableStateOf(false)
    var captchaUrl by mutableStateOf<String?>(null)

    var replyingToComment by mutableStateOf<Comment?>(null)
    private var pendingCommentBody: String? = null
    private var pendingCommentTimestamp: Long? = null

    private var _showAddToPlaylistSheet by mutableStateOf(false)
    var showAddToPlaylistSheet: Boolean
        get() = _showAddToPlaylistSheet
        set(value) {
            _showAddToPlaylistSheet = value
            if (value) fetchOnlinePlaylistsForAdd()
        }
    var tracksToAddInBulk by mutableStateOf<List<Track>?>(null)
    val userPlaylists = mutableStateListOf<LocalPlaylist>()

    private fun fetchOnlinePlaylistsForAdd() {
        if (TokenManager(context).isGuestMode() || !com.alananasss.kittytune.utils.NetworkUtils.isInternetAvailable(
                context
            )
        ) return
        viewModelScope.launch {
            try {
                val api = RetrofitClient.create(context)
                val me = api.getMe()
                val online = api.getUserCreatedPlaylists(me.id).collection
                val currentLocalIds = userPlaylists.map { it.id }.toSet()
                val newPlaylists = online.filter { !currentLocalIds.contains(it.id) }.map {
                    LocalPlaylist(
                        id = it.id,
                        title = it.title ?: "",
                        artist = it.user?.username ?: "",
                        artworkUrl = it.fullResArtwork,
                        trackCount = it.trackCount ?: 0,
                        isUserCreated = true
                    )
                }
                userPlaylists.addAll(newPlaylists)
            } catch (_: Exception) {
            }
        }
    }

    private val _originalQueue = CopyOnWriteArrayList<Track>()
    private val _queue = CopyOnWriteArrayList<Track>()
    val queue: List<Track> get() = _queue
    var queueState by mutableStateOf<List<Track>>(emptyList())
        private set
    var currentQueueIndex by mutableIntStateOf(-1)

    var isPreciseLyricsSearchEnabled by mutableStateOf(playerPrefs.getPreciseLyricsSearchEnabled())
    var showLyricsSheet by mutableStateOf(false)
    var lyricsLines = mutableStateListOf<LyricLine>()
    var isLyricsLoading by mutableStateOf(false)
    var isSearchingLyrics by mutableStateOf(false)
    var manualSearchQuery by mutableStateOf("")
    val lyricSearchResults = mutableStateListOf<LrcLibResponse>()
    var manualSearchProvider by mutableStateOf("MUSIXMATCH")
    val unifiedLyricSearchResults = mutableStateListOf<UnifiedLyricResult>()
    var isTranslatingLyrics by mutableStateOf(false)
    var lastFetchedMxmTrackId: Long? = null

    var lyricsFontSize by mutableFloatStateOf(playerPrefs.getLyricsFontSize())
    var lyricsAlignment by mutableStateOf(playerPrefs.getLyricsAlignment())
    var lyricsProvider by mutableStateOf(playerPrefs.getLyricsProvider())
        private set
    var isLyricsTranslationEnabled by mutableStateOf(playerPrefs.getLyricsTranslationEnabled())
        private set
    var lyricsTranslationLang by mutableStateOf(playerPrefs.getLyricsTranslationLang())
        private set
    var isRomanizationEnabled by mutableStateOf(playerPrefs.getLyricsRomanizationEnabled())
        private set
    var isWordSyncEnabled by mutableStateOf(playerPrefs.getLyricsWordSyncEnabled())
        private set
    var isAppleMusicEffectEnabled by mutableStateOf(playerPrefs.getLyricsAppleEffectEnabled())
        private set
    var lyricsMode by mutableStateOf(LyricsMode.SYNCED)
    var rawPlainLyrics by mutableStateOf<String?>(null)
    var showInlineLyrics by mutableStateOf(false)
    var lyricsOffset by mutableLongStateOf(0L)
    var showLyricsOffsetControls by mutableStateOf(false)

    var currentSessionListenMs = 0L
    private var hasPushedRecentlyPlayed = false
    var sleepTimerRemainingMs by mutableLongStateOf(0L)
    var sleepTimerEndOfTrack by mutableStateOf(false)
    var showSleepTimerDialog by mutableStateOf(false)
    val isSleepTimerActive: Boolean get() = sleepTimerRemainingMs > 0L || sleepTimerEndOfTrack
    private var sleepTimerJob: Job? = null
    private var preFadeVolume: Float = 1f

    private var pendingSeekPosition: Long? = null
    private var saveQueueJob: Job? = null
    private var lyricsJob: Job? = null
    private var queueChunkingJob: Job? = null
    private var trackInitJob: Job? = null
    private var playJob: Job? = null
    private var progressJob: Job? = null

    private val syncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.alananasss.kittytune.ACTION_FORCE_UPDATE") {
                syncStateFromPreferences()
            }
        }
    }

    private fun getString(resId: Int): String = com.alananasss.kittytune.utils.LocaleUtils.updateBaseContextLocale(getApplication()).getString(resId)
    private fun getString(resId: Int, vararg args: Any): String = com.alananasss.kittytune.utils.LocaleUtils.updateBaseContextLocale(getApplication()).getString(resId, *args)

    private fun parseIdFromMediaId(mediaId: String): Long {
        var cleanId = mediaId
        if (cleanId.startsWith(KittyTuneMediaLibrarySessionCallback.TRACK_PREFIX)) {
            cleanId = cleanId.removePrefix(KittyTuneMediaLibrarySessionCallback.TRACK_PREFIX)
        }
        if (cleanId.contains(KittyTuneMediaLibrarySessionCallback.CONTEXT_SEPARATOR)) {
            cleanId = cleanId.substringBefore(KittyTuneMediaLibrarySessionCallback.CONTEXT_SEPARATOR)
        }
        return cleanId.toLongOrNull() ?: mediaId.hashCode().toLong()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReadyState: Boolean, reason: Int) {
            playWhenReady = playWhenReadyState
        }

        override fun onIsPlayingChanged(isPlayingState: Boolean) {
            isPlaying = isPlayingState
            if (isPlayingState) {
                playWhenReady = true
                startProgressUpdate()
                SoundCloudTelemetryTracker.onTrackResumed(currentPosition)
            } else {
                SoundCloudTelemetryTracker.onTrackPaused(currentPosition)
            }
            saveStateAsync(saveQueue = false)
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            super.onTimelineChanged(timeline, reason)
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                syncQueueFromPlayer()
            }
        }

        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY) {
                isLoading = false
                if (MusicManager.player.duration > 0) duration = MusicManager.player.duration
                pendingSeekPosition?.let { MusicManager.player.seekTo(it); pendingSeekPosition = null }
            }
            if (state == Player.STATE_BUFFERING) isLoading = true

            if (state == Player.STATE_ENDED) {
                SoundCloudTelemetryTracker.onTrackCompleted()
                AchievementManager.increment("no_skip_50")
                incrementPlayCount()
                currentTrack?.let { track ->
                    if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                        if (repeatMode == RepeatMode.ONE) {
                            ListeningStatsRepository.recordEvent(track, "REPEAT_ONE_LOOP", currentSessionListenMs)
                        } else {
                            ListeningStatsRepository.recordEvent(track, "PLAY_COMPLETE", currentSessionListenMs)
                        }
                    }
                }
                currentSessionListenMs = 0L
                if (sleepTimerEndOfTrack) {
                    cancelSleepTimer()
                    MusicManager.player.pause()
                    showSleepTimerIslandNotification(isStarted = false)
                    emitUiEvent(getString(R.string.sleep_timer_cancelled))
                    return
                }

                if (repeatMode == RepeatMode.ONE) {
                    AchievementManager.increment("obsessed_50")
                    AchievementManager.increment("obsessed_200")
                    currentPosition = 0L
                    MusicManager.player.seekTo(0)
                    MusicManager.player.play()
                } else {
                    if (!MusicManager.isCrossfadingOut) {
                        playNext(manual = false, isCrossfade = playerPrefs.getCrossfadeEnabled())
                    }
                }
            }
        }

        @UnstableApi
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e("PlayerViewModel", "ExoPlayer error: ${error.errorCodeName}", error)

            val cause = error.cause
            val isAuthError = cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException &&
                    (cause.responseCode == 403 || cause.responseCode == 401)
            val isNetworkError = cause is androidx.media3.datasource.HttpDataSource.HttpDataSourceException
            val isSourceError = cause is androidx.media3.exoplayer.source.UnrecognizedInputFormatException

            if (isAuthError || isNetworkError || isSourceError) {
                if (currentQueueIndex >= 0 && currentQueueIndex < _queue.size) {
                    viewModelScope.launch {
                        player.playWhenReady = false
                        isLoading = true

                        if (isActive) {
                            playRobustly(
                                index = currentQueueIndex,
                                autoPlay = true,
                                startPosition = currentPosition,
                                allowSkipOnFailure = false
                            )
                        } else {
                            isLoading = false
                        }
                    }
                    return
                }
            }

            isLoading = false
            isPlaying = false
            playNext(manual = false, ignoreRepeatOne = true)
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                currentPosition = MusicManager.player.currentPosition

                saveStateAsync(saveQueue = false)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (mediaItem == null) return

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                if (repeatMode == RepeatMode.ALL && MusicManager.player.mediaItemCount == 1) {
                    MusicManager.player.pause()
                    playNext(manual = false)
                    return
                }
            }

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                val shiftCount = MusicManager.player.currentMediaItemIndex
                if (shiftCount > 0) {
                    currentQueueIndex += shiftCount
                    if (currentQueueIndex >= _queue.size && repeatMode == RepeatMode.ALL && _queue.isNotEmpty()) {
                        currentQueueIndex %= _queue.size
                    }
                    repeat(shiftCount) {
                        try {
                            MusicManager.player.removeMediaItem(0)
                        } catch (_: Exception) {
                        }
                    }
                    preloadNextTrack(currentQueueIndex + 1)
                }
            }

            val trackId = parseIdFromMediaId(mediaItem.mediaId)

            val expectedTrackId = _queue.getOrNull(currentQueueIndex)?.id
            if (expectedTrackId != null && expectedTrackId != trackId) {
                return
            }

            if (currentTrack?.id != trackId) {
                currentSessionListenMs = 0L
                hasPushedRecentlyPlayed = false
            }

            if (MusicManager.currentTrack?.id == trackId) {
                currentTrack = MusicManager.currentTrack
            } else if (currentTrack?.id != trackId) {
                val meta = mediaItem.mediaMetadata
                val source = if (mediaItem.mediaId.startsWith("yt_") || mediaItem.requestMetadata.mediaUri?.toString()
                        ?.contains("youtube") == true
                ) "youtube" else "soundcloud"

                currentTrack = Track(
                    id = trackId,
                    title = meta.title?.toString() ?: "Unknown",
                    durationMs = 0L,
                    artworkUrl = meta.artworkUri?.toString(),
                    user = User(0, meta.artist?.toString() ?: "Unknown", null),
                    permalinkUrl = "",
                    playbackCount = 0,
                    likesCount = 0,
                    repostsCount = 0,
                    commentCount = 0,
                    source = source
                )
            }
        }
    }

    fun resetPlaybackState(closePlayer: Boolean = true) {
        playJob?.cancel()
        isLoading = false
        isPlaying = false
        currentPosition = 0L
        currentQueueIndex = -1
        currentTrack = null
        MusicManager.currentTrack = null
        currentContext = null
        _queue.clear()
        _originalQueue.clear()
        updateQueueState()
        try {
            MusicManager.player.stop()
            MusicManager.player.clearMediaItems()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (closePlayer) {
            isPlayerExpanded = false
            showMenuSheet = false
            showDetailsSheet = false
            showCommentsSheet = false
            showLyricsSheet = false
        }
    }

    init {
        val filter = IntentFilter("com.alananasss.kittytune.ACTION_FORCE_UPDATE")
        ContextCompat.registerReceiver(context, syncReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        SoundCloudTelemetryTracker.init(context)
        MusicManager.init(context)
        bindToActivePlayer()
        MusicManager.applyEffects(effectsState)
        applyRepeatMode()

        viewModelScope.launch {
            MusicManager.onPlayerSwappedFlow.collect {
                bindToActivePlayer()
            }
        }

        viewModelScope.launch {
            MusicManager.trackUpdatedFlow.collect { updatedTrack ->
                if (currentTrack?.id == updatedTrack.id) {
                    currentTrack = updatedTrack
                    updatePlayerColors(updatedTrack)
                }
                val idx = _queue.indexOfFirst { it.id == updatedTrack.id }
                if (idx != -1) {
                    _queue[idx] = updatedTrack
                    updateQueueState()
                }
                val origIdx = _originalQueue.indexOfFirst { it.id == updatedTrack.id }
                if (origIdx != -1) {
                    _originalQueue[origIdx] = updatedTrack
                }
            }
        }

        viewModelScope.launch {
            MusicManager.trackDeletedFlow.collect { deletedTrackId ->
                val wasCurrentTrack = currentTrack?.id == deletedTrackId
                val idx = _queue.indexOfFirst { it.id == deletedTrackId }
                if (idx != -1) {
                    _queue.removeAt(idx)
                    updateQueueState()
                }
                _originalQueue.removeAll { it.id == deletedTrackId }

                if (_queue.isEmpty()) {
                    resetPlaybackState(closePlayer = true)
                    return@collect
                }

                if (wasCurrentTrack) {
                    if (idx != -1 && idx < _queue.size) {
                        val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
                        playTrackAtIndex(idx, addToHistory = false, isCrossfade = crossfadeEnabled)
                    } else {
                        if (repeatMode == RepeatMode.ALL) {
                            val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
                            playTrackAtIndex(0, addToHistory = false, isCrossfade = crossfadeEnabled)
                        } else {
                            val autoPlayEnabled = playerPrefs.getAutoplayEnabled()
                            val isYoutube = currentTrack?.source == "youtube"
                            if (autoPlayEnabled || isYoutube) {
                                viewModelScope.launch {
                                    val youtubeFallback = playerPrefs.getYouTubeFallbackEnabled()
                                    if (isYoutube || (currentTrack?.source == "soundcloud" && youtubeFallback)) {
                                        fetchAndPlayYoutubeRadio()
                                    } else {
                                        fetchAndQueueRadio()
                                    }
                                }
                            } else {
                                resetPlaybackState(closePlayer = true)
                            }
                        }
                    }
                } else if (idx != -1 && idx < currentQueueIndex) {
                    currentQueueIndex = (currentQueueIndex - 1).coerceAtLeast(0)
                }
            }
        }

        viewModelScope.launch {
            MusicManager.playlistDeletedFlow.collect { deletedPlaylistId ->
                val navId = currentContext?.navigationId
                val cleanNav = navId?.removePrefix("playlist:")
                    ?.removePrefix("downloaded_section:")
                    ?.removePrefix("local_playlist:")
                    ?.removePrefix("system_playlist:") ?: ""

                val matchesContext = navId != null && (
                        cleanNav == deletedPlaylistId.toString() ||
                                cleanNav.contains(deletedPlaylistId.toString()) ||
                                (cleanNav.isNotEmpty() && kotlin.math.abs(
                                    cleanNav.hashCode().toLong()
                                ) == deletedPlaylistId) ||
                                navId.contains(deletedPlaylistId.toString())
                        )

                if (matchesContext || _queue.isEmpty()) {
                    resetPlaybackState(closePlayer = true)
                }
            }
        }

        MusicManager.onNextClick = {
            val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
            playNext(manual = true, isCrossfade = crossfadeEnabled)
        }
        MusicManager.onPreviousClick = { smartPrevious() }

        MusicManager.onTrackChange = trackChangeHandler@{ newTrack ->
            if (sleepTimerEndOfTrack) {
                cancelSleepTimer()
                viewModelScope.launch(Dispatchers.Main) {
                    MusicManager.player.pause()
                    isPlaying = false
                    showSleepTimerIslandNotification(isStarted = false)
                }
                emitUiEvent(getString(R.string.sleep_timer_cancelled))
                return@trackChangeHandler
            }

            showInlineLyrics = false
            lyricsLines.clear()
            rawPlainLyrics = null

            val expectedTrackId = _queue.getOrNull(currentQueueIndex)?.id
            if (expectedTrackId != null && expectedTrackId != newTrack.id) {
                return@trackChangeHandler
            }

            var finalTrack = newTrack

            val currentMediaItem = MusicManager.player.currentMediaItem
            if (currentMediaItem != null) {
                val realId = parseIdFromMediaId(currentMediaItem.mediaId)
                if (realId != newTrack.id) {
                    finalTrack = newTrack.copy(id = realId)
                }
            }
            val foundInQueue = _queue.find { it.id == finalTrack.id }
            if (foundInQueue != null) {
                finalTrack = foundInQueue
            }

            currentTrack = finalTrack
            MusicManager.currentTrack = finalTrack

            updatePlayerColors(finalTrack)

            viewModelScope.launch {
                isLiked = LikeRepository.isTrackLiked(finalTrack.id)
                loadLyrics(finalTrack)
                AchievementManager.checkTrackNameSecret(finalTrack.title ?: "")

                if (finalTrack.source == "soundcloud" && finalTrack.id > 0 && (finalTrack.permalinkUrl.isNullOrEmpty() || finalTrack.user?.avatarUrl.isNullOrEmpty() || finalTrack.playbackCount == 0)) {
                    try {
                        val fullTracks = api.getTracksByIds(finalTrack.id.toString())
                        val fullTrack = fullTracks.firstOrNull()

                        if (fullTrack != null) {
                            currentTrack = fullTrack
                            MusicManager.currentTrack = fullTrack
                        }
                    } catch (_: Exception) {
                    }
                }
            }

            try {
                isPlaying = MusicManager.player.isPlaying
                duration = MusicManager.player.duration.coerceAtLeast(0L)
                currentPosition = MusicManager.player.currentPosition
                if (isPlaying) startProgressUpdate()
            } catch (_: Exception) {
            }
            saveStateAsync(saveQueue = false)
        }

        viewModelScope.launch {
            MusicManager.contextFlow.collect { ctx ->
                currentContext = ctx
                saveStateAsync(saveQueue = false)
            }
        }

        viewModelScope.launch {
            LikeRepository.likedTracks.collect { likedList ->
                currentTrack?.let { track ->
                    isLiked = likedList.any { it.id == track.id }
                }
            }
        }

        viewModelScope.launch {
            RepostRepository.repostedTrackIds.collect { ids ->
                repostedTrackIds = ids
            }
        }

        viewModelScope.launch {
            DownloadManager.getUserPlaylistsFlow().collect { playlists ->
                userPlaylists.clear()
                val sorted =
                    playlists.sortedWith(compareByDescending<LocalPlaylist> { it.isUserCreated || it.id < 0 }.thenByDescending { it.addedAt })
                userPlaylists.addAll(sorted)
            }
        }
        restoreSession()
        syncWithCurrentPlayback()
    }

    private fun bindToActivePlayer() {
        try {
            boundPlayer?.removeListener(playerListener)
        } catch (_: Exception) {
        }

        val activePlayer = MusicManager.player
        activePlayer.addListener(playerListener)
        boundPlayer = activePlayer

        try {
            isPlaying = activePlayer.isPlaying
            duration = activePlayer.duration.coerceAtLeast(0L)
            currentPosition = activePlayer.currentPosition
            if (isPlaying) startProgressUpdate()
        } catch (_: Exception) {
        }
    }

    fun toggleInlineLyrics() {
        showInlineLyrics = !showInlineLyrics
    }

    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(syncReceiver)
        try {
            MusicManager.player.removeListener(playerListener)
        } catch (_: IllegalStateException) {
        }
    }

    private fun syncStateFromPreferences() {
        viewModelScope.launch {
            val lastTrack = playerPrefs.getLastTrack()
            val lastQueue = playerPrefs.getLastQueue()
            val lastContext = playerPrefs.getLastContext()
            val lastShuffle = playerPrefs.getLastShuffleEnabled()
            val lastRepeat = playerPrefs.getLastRepeatMode()

            _queue.clear()
            _queue.addAll(lastQueue)
            _originalQueue.clear()
            _originalQueue.addAll(lastQueue)
            updateQueueState()

            currentTrack = lastTrack
            currentContext = lastContext
            shuffleEnabled = lastShuffle
            repeatMode = lastRepeat
            applyRepeatMode()

            if (lastTrack != null) {
                isLiked = LikeRepository.isTrackLiked(lastTrack.id)
                currentQueueIndex = _queue.indexOfFirst { it.id == lastTrack.id }.coerceAtLeast(0)
            }

            try {
                isPlaying = player.isPlaying
                duration = player.duration.coerceAtLeast(0L)
                currentPosition = player.currentPosition
                if (isPlaying) startProgressUpdate()
            } catch (_: Exception) {
            }
        }
    }

    fun isTrackReposted(trackId: Long): Boolean {
        return repostedTrackIds.contains(trackId)
    }

    fun repostTrack(track: Track, caption: String?) {
        RepostRepository.syncLocalState(track.id, true)
        emitUiEvent(getString(R.string.repost_success))

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = api.repostTrack(track.id)
                if (response.isSuccessful && !caption.isNullOrBlank()) {
                    delay(100.milliseconds)
                    api.addRepostCaption(track.id, RepostCaptionRequest(caption))
                }
            } catch (e: Exception) {
                e.printStackTrace()
                RepostRepository.syncLocalState(track.id, false)
            }
        }
    }

    fun deleteRepost(trackId: Long) {
        RepostRepository.removeRepost(trackId)
        emitUiEvent(getString(R.string.success_generic))
    }

    fun updateLyricsFontSize(size: Float) {
        lyricsFontSize = size
        playerPrefs.setLyricsFontSize(size)
    }

    fun updateLyricsAlignment(alignment: LyricsAlignment) {
        lyricsAlignment = alignment
        playerPrefs.setLyricsAlignment(alignment)
    }

    fun togglePreciseLyricsSearch(enabled: Boolean) {
        isPreciseLyricsSearchEnabled = enabled
        playerPrefs.setPreciseLyricsSearchEnabled(enabled)
        currentTrack?.let { loadLyrics(it) }
    }

    fun updateLyricsProvider(provider: com.alananasss.kittytune.ui.player.LyricsProvider) {
        lyricsProvider = provider; playerPrefs.setLyricsProvider(provider); reloadLyrics()
    }

    fun toggleLyricsTranslation(enabled: Boolean) {
        isLyricsTranslationEnabled = enabled
        playerPrefs.setLyricsTranslationEnabled(enabled)
        if (enabled && lyricsLines.isNotEmpty()) {
            fetchTranslationsForCurrentLines(lyricsTranslationLang)
        }
    }

    fun updateLyricsTranslationLang(lang: String) {
        lyricsTranslationLang = lang
        playerPrefs.setLyricsTranslationLang(lang)
        if (isLyricsTranslationEnabled && lyricsLines.isNotEmpty()) {
            fetchTranslationsForCurrentLines(lang)
        }
    }

    fun toggleRomanization(enabled: Boolean) {
        isRomanizationEnabled = enabled
        playerPrefs.setLyricsRomanizationEnabled(enabled)
        if (enabled && lyricsLines.isNotEmpty()) {
            fetchRomanizationForCurrentLines()
        }
    }

    fun toggleWordSync(enabled: Boolean) {
        isWordSyncEnabled = enabled
        playerPrefs.setLyricsWordSyncEnabled(enabled)
    }

    fun toggleAppleMusicEffect(enabled: Boolean) {
        isAppleMusicEffectEnabled = enabled
        playerPrefs.setLyricsAppleEffectEnabled(enabled)
    }

    private var translationJob: Job? = null

    private fun fetchTranslationsForCurrentLines(targetLang: String = lyricsTranslationLang) {
        translationJob?.cancel()
        if (lyricsLines.isEmpty()) return
        val originalLines = lyricsLines.map { it.text }.filter { it.isNotBlank() }.distinct()

        translationJob = viewModelScope.launch(Dispatchers.IO) {
            val translationMap =
                com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(originalLines, targetLang)
            withContext(Dispatchers.Main) {
                for (i in lyricsLines.indices) {
                    val oldLine = lyricsLines[i]
                    val newTranslation = translationMap[oldLine.text.trim()]
                    lyricsLines[i] = oldLine.copy(translation = oldLine.translation ?: newTranslation)
                }
            }
        }
    }

    private fun fetchRomanizationForCurrentLines() {
        if (lyricsLines.isEmpty()) return
        val originalLines = lyricsLines.map { it.text }.filter { it.isNotBlank() }.distinct()
        viewModelScope.launch(Dispatchers.IO) {
            val romMap = com.alananasss.kittytune.data.network.FreeTranslator.getRomanization(originalLines)
            withContext(Dispatchers.Main) {
                for (i in lyricsLines.indices) {
                    val oldLine = lyricsLines[i]
                    val rom = romMap[oldLine.text.trim()]
                    lyricsLines[i] = oldLine.copy(romanization = oldLine.romanization ?: rom)
                }
            }
        }
    }

    fun openLyrics(targetTrack: Track? = null, forceSheet: Boolean = false) {
        val target = targetTrack ?: currentTrack ?: return
        if (target.id != currentTrack?.id) playPlaylist(listOf(target), 0)

        if (!forceSheet && playerPrefs.getInlineLyricsEnabled()) {
            toggleInlineLyrics()
        } else {
            lyricsMode = if (lyricsLines.isNotEmpty()) {
                LyricsMode.SYNCED
            } else {
                LyricsMode.PLAIN
            }
            showMenuSheet = false
            showLyricsSheet = true
        }
    }

    private fun generateSearchQueries(title: String, uploader: String): List<String> {
        val queries = mutableSetOf<String>()
        val cleanArtist = uploader.replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()
        val cleanTitle = title.replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "").trim()

        var parsedArtist = cleanArtist
        var parsedTitle = cleanTitle
        if (cleanTitle.contains("-")) {
            val parts = cleanTitle.split("-", limit = 2)
            parsedArtist = parts[0].replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()
            parsedTitle = parts[1].trim()
        } else if (title.contains("-")) {
            val parts = title.split("-", limit = 2)
            parsedArtist = parts[0].replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()
            parsedTitle = parts[1].replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "").trim()
        }
        val ultraCleanTitle = parsedTitle.replace(Regex("(?i)\\s+(w/|feat\\.?|ft\\.?|prod\\.?|x(?=\\s)).*"), "").trim()

        if (ultraCleanTitle.isNotBlank() && parsedArtist.isNotBlank()) queries.add("$ultraCleanTitle $parsedArtist")
        if (ultraCleanTitle.isNotBlank() && cleanArtist.isNotBlank() && cleanArtist != parsedArtist) queries.add("$ultraCleanTitle $cleanArtist")
        if (parsedTitle.isNotBlank() && parsedArtist.isNotBlank()) queries.add("$parsedTitle $parsedArtist")
        if (ultraCleanTitle.isNotBlank()) queries.add(ultraCleanTitle)
        if (parsedTitle.isNotBlank()) queries.add(parsedTitle)
        queries.add(cleanTitle)

        return queries.filter { it.length > 2 }.toList()
    }

    private fun loadLyrics(track: Track) {
        lyricsJob?.cancel()
        lyricsLines.clear()
        lyricsOffset = 0L
        showLyricsOffsetControls = false
        isLyricsLoading = true
        isSearchingLyrics = false
        rawPlainLyrics = null

        val trackDurationMs = track.durationMs ?: 0L
        val trackDurationSec = trackDurationMs / 1000.0

        val queries = generateSearchQueries(track.title ?: "", track.user?.username ?: "")
        manualSearchQuery = queries.firstOrNull() ?: ""

        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            val preferLocal = playerPrefs.getLyricsPreferLocal()
            if (preferLocal) {
                val localTrack = DownloadManager.getLocalTrack(track.id)
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    val rawLyrics = LyricsUtils.extractLocalLyrics(localTrack.localAudioPath)
                    if (!rawLyrics.isNullOrBlank()) {
                        val parsed = LyricsUtils.parseLyricsContent(rawLyrics, trackDurationMs)
                        withContext(Dispatchers.Main) {
                            lyricsLines.addAll(parsed.ifEmpty { listOf(LyricLine(rawLyrics, 0, trackDurationMs)) })
                            lyricsMode = LyricsMode.SYNCED
                            isLyricsLoading = false
                        }
                        return@launch
                    }
                }
            }

            var bestMxmLineSync: Pair<List<LyricLine>, String?>? = null
            var bestLrcLineSync: LrcLibResponse? = null

            var finalLines = emptyList<LyricLine>()
            var finalPlain: String? = null

            val preferLrcLib = lyricsProvider == com.alananasss.kittytune.ui.player.LyricsProvider.OPEN_SOURCE

            for (query in queries) {
                if (!isActive) return@launch
                android.util.Log.d("LyricsFetch", "▶ Trying query: '$query' | preferLrcLib=$preferLrcLib")

                if (preferLrcLib) {
                    val lrcAll = try {
                        LrcLibClient.api.searchLyrics(query)
                    } catch (e: Exception) {
                        emptyList()
                    }
                    val lrcFiltered = lrcAll.filter { abs(it.duration - trackDurationSec) < 15.0 }
                    val topLrc = if (lrcFiltered.isNotEmpty()) lrcFiltered else lrcAll.take(10)
                    val lrcMatch = topLrc.maxByOrNull { if (!it.syncedLyrics.isNullOrEmpty()) 1 else 0 }

                    val lrcLines = lrcMatch?.syncedLyrics?.let { LyricsUtils.parseLyricsContent(it, trackDurationMs) }
                        ?: emptyList()
                    val lrcPlain = lrcMatch?.plainLyrics

                    if (lrcLines.isNotEmpty()) {
                        finalLines = lrcLines
                        finalPlain = lrcPlain
                        break
                    }
                    if (!lrcPlain.isNullOrBlank()) {
                        if (finalPlain == null) finalPlain = lrcPlain
                        break
                    }
                } else {
                    val (mxmAll, lrcAll) = kotlinx.coroutines.coroutineScope {
                        val mxmDeferred = async {
                            try {
                                MusixmatchClient.search(context, query)
                            } catch (e: Exception) {
                                android.util.Log.e("LyricsFetch", "MXM search exception: ${e.message}")
                                emptyList()
                            }
                        }
                        val lrcDeferred = async {
                            try {
                                LrcLibClient.api.searchLyrics(query)
                            } catch (e: Exception) {
                                emptyList<LrcLibResponse>()
                            }
                        }
                        Pair(mxmDeferred.await(), lrcDeferred.await())
                    }

                    android.util.Log.d(
                        "LyricsFetch",
                        "MXM search results: ${mxmAll.size} | LRC search results: ${lrcAll.size}"
                    )

                    val mxmFiltered =
                        mxmAll.filter { it.trackLength == 0 || abs(it.trackLength - trackDurationSec) < 15.0 }
                    val topMxm = if (mxmFiltered.isNotEmpty()) mxmFiltered else mxmAll.take(10)
                    val mxmMatch = topMxm.maxByOrNull { it.hasRichSync * 2 + it.hasSubtitles }

                    android.util.Log.d(
                        "LyricsFetch",
                        "MXM best match: ${mxmMatch?.trackName} | hasRichSync=${mxmMatch?.hasRichSync} | hasSubtitles=${mxmMatch?.hasSubtitles}"
                    )

                    val targetLang =
                        if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null
                    val mxmData = mxmMatch?.let {
                        MusixmatchClient.getLyricsData(
                            context,
                            it.trackId,
                            trackDurationMs,
                            targetLang,
                            isRomanizationEnabled
                        )
                    }

                    val lrcFiltered = lrcAll.filter { it.duration == 0.0 || abs(it.duration - trackDurationSec) < 15.0 }
                    val topLrc = if (lrcFiltered.isNotEmpty()) lrcFiltered else lrcAll.take(10)
                    val lrcMatch = topLrc.maxByOrNull { if (!it.syncedLyrics.isNullOrEmpty()) 1 else 0 }

                    val lrcLines = lrcMatch?.syncedLyrics?.let { LyricsUtils.parseLyricsContent(it, trackDurationMs) }
                        ?: emptyList()
                    val lrcPlain = lrcMatch?.plainLyrics

                    val mxmWordSync = mxmData != null && mxmData.first.any { !it.words.isNullOrEmpty() }
                    val lrcWordSync = lrcLines.any { !it.words.isNullOrEmpty() }
                    val mxmLineSync = mxmData != null && mxmData.first.size > 1
                    val lrcLineSync = lrcLines.size > 1
                    val hasMxmPlain = !mxmData?.second.isNullOrBlank()
                    val hasLrcPlain = !lrcPlain.isNullOrBlank()

                    android.util.Log.d(
                        "LyricsFetch",
                        "MXM: wordSync=$mxmWordSync lineSync=$mxmLineSync plain=$hasMxmPlain linesCount=${mxmData?.first?.size}"
                    )
                    android.util.Log.d(
                        "LyricsFetch",
                        "LRC: wordSync=$lrcWordSync lineSync=$lrcLineSync plain=$hasLrcPlain linesCount=${lrcLines.size}"
                    )
                    when {
                        mxmWordSync -> {
                            android.util.Log.d("LyricsFetch", "✅ Picked: MXM word-sync")
                            finalLines = mxmData!!.first
                            finalPlain = mxmData.second
                            break
                        }

                        lrcWordSync -> {
                            android.util.Log.d("LyricsFetch", "✅ Picked: LRC word-sync")
                            finalLines = lrcLines
                            finalPlain = lrcPlain
                            break
                        }

                        mxmLineSync -> {
                            android.util.Log.d("LyricsFetch", "✅ Picked: MXM line-sync")
                            bestMxmLineSync = mxmData
                            if (hasMxmPlain) finalPlain = mxmData!!.second
                            break
                        }

                        lrcLineSync -> {
                            android.util.Log.d("LyricsFetch", "✅ Picked: LRC line-sync (MXM had nothing)")
                            bestLrcLineSync = lrcMatch
                            if (hasLrcPlain) finalPlain = lrcPlain
                            break
                        }

                        hasMxmPlain -> {
                            if (finalPlain == null) finalPlain = mxmData!!.second
                        }

                        hasLrcPlain -> {
                            if (finalPlain == null) finalPlain = lrcPlain
                        }
                    }

                }
            }

            if (finalLines.isEmpty()) {
                if (bestMxmLineSync != null) {
                    finalLines = bestMxmLineSync!!.first
                    finalPlain = bestMxmLineSync!!.second
                } else if (bestLrcLineSync != null) {
                    finalLines =
                        bestLrcLineSync!!.syncedLyrics?.let { LyricsUtils.parseLyricsContent(it, trackDurationMs) }
                            ?: emptyList()
                    finalPlain = bestLrcLineSync!!.plainLyrics
                }
            }

            if (finalLines.isEmpty() && finalPlain.isNullOrBlank()) {
                for (query in queries) {
                    if (!isActive) break
                    val lrcPlain = try {
                        LrcLibClient.api.searchLyrics(query)
                            .firstOrNull { !it.plainLyrics.isNullOrBlank() }?.plainLyrics
                    } catch (e: Exception) {
                        null
                    }
                    if (lrcPlain != null) {
                        finalPlain = lrcPlain; break
                    }
                }
            }

            if (finalLines.isNotEmpty() && (preferLrcLib || (finalLines.firstOrNull()?.translation == null && finalLines.firstOrNull()?.romanization == null))) {
                val targetLang =
                    if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null
                val wantsRomanization = isRomanizationEnabled

                if (targetLang != null || wantsRomanization) {
                    val originalTexts = finalLines.map { it.text }.filter { it.isNotBlank() }.distinct()
                    val translations =
                        if (targetLang != null) com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(
                            originalTexts,
                            targetLang
                        ) else emptyMap()
                    val romanizations =
                        if (wantsRomanization) com.alananasss.kittytune.data.network.FreeTranslator.getRomanization(
                            originalTexts
                        ) else emptyMap()

                    finalLines = finalLines.map { line ->
                        line.copy(
                            translation = line.translation ?: translations[line.text.trim()],
                            romanization = line.romanization ?: romanizations[line.text.trim()]
                        )
                    }
                }
            }

            withContext(Dispatchers.Main) {
                lyricsLines.addAll(finalLines)
                rawPlainLyrics = finalPlain
                lyricsMode = if (finalLines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
                isLyricsLoading = false
                if (finalLines.isNotEmpty() || !rawPlainLyrics.isNullOrBlank()) isSearchingLyrics = false
            }
        }
    }

    fun reloadLyrics() {
        currentTrack?.let { loadLyrics(it) }
    }

    fun loadCustomLyrics(content: String) {
        viewModelScope.launch {
            val trackDuration = currentTrack?.durationMs ?: 0L
            val resultLines = LyricsUtils.parseLyricsContent(content, trackDuration)
            withContext(Dispatchers.Main) {
                lyricsLines.clear()
                lyricsLines.addAll(resultLines)
                rawPlainLyrics = if (resultLines.isNotEmpty()) {
                    resultLines.joinToString("\n") { it.text }
                } else {
                    content
                }
                lyricsMode = if (resultLines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
                isSearchingLyrics = false
                isLyricsLoading = false
            }
        }
    }

    fun adjustLyricsOffset(amount: Long) {
        lyricsOffset += amount
    }

    private suspend fun processLyricsResponse(response: LrcLibResponse?, trackDuration: Long) {
        val resultLines = when {
            response == null -> emptyList()
            !response.syncedLyrics.isNullOrEmpty() -> LyricsUtils.parseLyricsContent(
                response.syncedLyrics,
                trackDuration
            )

            else -> emptyList()
        }

        withContext(Dispatchers.Main) {
            lyricsLines.clear()
            lyricsLines.addAll(resultLines)

            rawPlainLyrics = response?.plainLyrics ?: response?.syncedLyrics

            lyricsMode = if (resultLines.isNotEmpty()) {
                LyricsMode.SYNCED
            } else {
                LyricsMode.PLAIN
            }

            isLyricsLoading = false
            if (resultLines.isNotEmpty() || !rawPlainLyrics.isNullOrBlank()) isSearchingLyrics = false
        }
    }

    fun searchLyricsManual(query: String, provider: String = manualSearchProvider) {
        if (query.isBlank()) return
        isLyricsLoading = true
        unifiedLyricSearchResults.clear()
        manualSearchProvider = provider

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (provider == "LRCLIB") {
                    val results = LrcLibClient.api.searchLyrics(query)
                    val mapped = results.map {
                        UnifiedLyricResult(
                            it.id.toString(),
                            it.name,
                            it.artistName,
                            it.albumName,
                            it.duration,
                            !it.syncedLyrics.isNullOrEmpty(),
                            false,
                            "LRCLIB"
                        )
                    }
                    withContext(Dispatchers.Main) { unifiedLyricSearchResults.addAll(mapped) }
                } else {
                    val results = MusixmatchClient.search(context, query)
                    val mapped = results.map {
                        UnifiedLyricResult(
                            it.trackId.toString(),
                            it.trackName,
                            it.artistName,
                            it.albumName,
                            it.trackLength.toDouble(),
                            it.hasSubtitles == 1,
                            it.hasRichSync == 1,
                            "MUSIXMATCH"
                        )
                    }
                    withContext(Dispatchers.Main) { unifiedLyricSearchResults.addAll(mapped) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                withContext(Dispatchers.Main) { isLyricsLoading = false }
            }
        }
    }

    fun selectLyricResult(result: LrcLibResponse) {
        viewModelScope.launch(Dispatchers.IO) { processLyricsResponse(result, duration) }
    }

    fun selectUnifiedLyricResult(result: UnifiedLyricResult) {
        viewModelScope.launch(Dispatchers.IO) {
            isLyricsLoading = true
            var finalLines = emptyList<LyricLine>()
            var finalPlain: String? = null

            if (result.provider == "LRCLIB") {
                try {
                    val lrcData = LrcLibClient.api.searchLyrics(result.name + " " + result.artistName)
                        .find { it.id.toString() == result.id }
                    val lyricsText = lrcData?.syncedLyrics
                    if (!lyricsText.isNullOrEmpty()) finalLines = LyricsUtils.parseLyricsContent(lyricsText, duration)
                    finalPlain = lrcData?.plainLyrics
                } catch (e: Exception) {
                }
            } else {
                val targetLang =
                    if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null
                lastFetchedMxmTrackId = result.id.toLongOrNull()
                val data = MusixmatchClient.getLyricsData(
                    context,
                    result.id.toLong(),
                    duration,
                    targetLang,
                    isRomanizationEnabled
                )
                finalLines = data.first
                finalPlain = data.second
            }

            withContext(Dispatchers.Main) {
                lyricsLines.clear()
                lyricsLines.addAll(finalLines)
                rawPlainLyrics = finalPlain
                lyricsMode = if (finalLines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
                isSearchingLyrics = false
                isLyricsLoading = false
            }
        }
    }

    fun navigateToTrackDetails(trackId: Long, initialTab: Int = 0) {
        showMenuSheet = false
        showDetailsSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "track_detail:$trackId?tab=$initialTab"
    }

    fun toggleFollowArtist(user: User?) {
        if (user == null || user.id <= 0) return
        viewModelScope.launch {
            try {
                com.alananasss.kittytune.data.DownloadManager.toggleSaveArtist(user)
                val isSaved = com.alananasss.kittytune.data.DownloadManager.isArtistSaved(user.id)
                val artistName = user.username ?: getString(R.string.the_artist)
                if (isSaved) {
                    emitUiEvent(getString(R.string.toast_subscribed_to, artistName))
                } else {
                    emitUiEvent(getString(R.string.toast_unsubscribed_from, artistName))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun navigateToEditTrack(track: Track) {
        showMenuSheet = false
        showDetailsSheet = false
        isPlayerExpanded = false
        trackToEdit = track
        navigateToPlaylistId = "edit_track:${track.id}"
    }

    fun shareTrack(track: Track) {
        val appContext = getApplication<Application>().applicationContext
        val urlToShare = track.permalinkUrl ?: "https://soundcloud.com/tracks/${track.id}"
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, urlToShare)
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.share_via)).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        appContext.startActivity(shareIntent)
        showMenuSheet = false
        AchievementManager.increment("social_star")
    }

    fun openTrackDetails(targetTrack: Track? = null) {
        val target = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        selectedTrackForSheet = target

        if (target.id < 0 || target.source == "local") {
            activateLocalDetailsMode(target)
            return
        }

        viewModelScope.launch {
            val isOffline = !com.alananasss.kittytune.utils.NetworkUtils.isInternetAvailable(getApplication())
            val navId = currentContext?.navigationId
            val isContextLocal =
                (menuContextPlaylistId != null && (menuContextPlaylistId == -2L || menuContextPlaylistId!! < 0))
                        || (navId == "downloads" || navId?.startsWith("local_playlist:") == true)

            if (isOffline && isContextLocal) {
                activateLocalDetailsMode(target)
            } else {
                isLocalDetailsMode = false
                localFilePathForDetails = null
                showMenuSheet = false
                showDetailsSheet = true

                if (target.source == "soundcloud" && target.id > 0 && (target.user?.id == 0L || target.playbackCount == 0)) {
                    try {
                        val fullTracks = api.getTracksByIds(target.id.toString())
                        val fullTrack = fullTracks.firstOrNull()
                        if (fullTrack != null) {
                            selectedTrackForSheet = fullTrack
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun activateLocalDetailsMode(target: Track) {
        isLocalDetailsMode = true
        viewModelScope.launch {
            val localTrack = DownloadManager.getLocalTrack(target.id)
            val prefix = getString(R.string.prefix_local_file_marker)
            localFilePathForDetails = localTrack?.localAudioPath ?: target.description?.removePrefix(prefix)
            showMenuSheet = false
            showDetailsSheet = true
        }
    }

    fun openComments(targetTrack: Track? = null) {
        val target = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        selectedTrackForSheet = target
        showMenuSheet = false
        showDetailsSheet = false
        showCommentsSheet = true
        if (currentUserId == 0L || currentUser == null) fetchUserProfile()
        loadComments(true, target)
    }

    fun onCommentSortChanged(sort: CommentSort) {
        if (commentSort == sort) return
        commentSort = sort
        loadComments(refresh = true)
    }

    fun navigateToExpandedQueue() {
        showMenuSheet = false
        showDetailsSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "expanded_queue"
    }

    fun resolveAndNavigateToArtist(username: String, artistId: Long? = null) {
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false

        if (artistId != null && artistId > 0) {
            navigateToPlaylistId = "profile:$artistId"
            return
        }

        val cleanName = username.replace("@", "")
            .replace(Regex("[\\p{C}\\p{Zl}\\p{Zp}]"), "")
            .trim()

        if (cleanName.isBlank()) return

        viewModelScope.launch {
            try {
                val resolvedObject = api.resolveUrl("https://soundcloud.com/$cleanName")
                val user = gson.fromJson(resolvedObject, User::class.java)
                if (user.id > 0) {
                    navigateToPlaylistId = "profile:${user.id}"
                }
            } catch (_: Exception) {
                emitUiEvent(getString(R.string.error_generic))
            }
        }
    }

    fun navigateToTag(tagName: String) {
        showDetailsSheet = false; isPlayerExpanded = false; navigateToPlaylistId = "tag:$tagName"
    }

    fun navigateToArtist(userId: Long) {
        if (userId <= 0) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "profile:$userId"
    }

    fun navigateToContext() {
        currentContext?.let { context ->
            var destination = context.navigationId
            if (destination.startsWith("playlist_detail:")) {
                destination = destination.removePrefix("playlist_detail:")
            } else if (destination.startsWith("playlist_")) {
                destination = destination.removePrefix("playlist_")
            }
            navigateToPlaylistId = destination
        }
    }

    fun onNavigationHandled() {
        navigateToPlaylistId = null
    }

    fun loadComments(refresh: Boolean = false, specificTrack: Track? = null) {
        val t = specificTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        if (refresh) {
            commentsList.clear(); commentNextHref = null
            loadTrackReactions(t.id)
        }
        if (!refresh && commentNextHref == null && commentsList.isNotEmpty()) return

        viewModelScope.launch {
            if (refresh) isCommentsLoading = true
            try {
                val response = if (refresh) {
                    api.getTrackComments(trackId = t.id, threaded = 1, filterReplies = 1, sort = commentSort.value)
                } else {
                    api.getCommentsNextPage(commentNextHref!!)
                }
                commentNextHref = response.next_href
                val newComments = response.collection.filter { c -> commentsList.none { it.id == c.id } }
                commentsList.addAll(newComments)
                checkCommentLikesStatus(t.id, newComments)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isCommentsLoading = false
            }
        }
    }

    private suspend fun checkCommentLikesStatus(trackId: Long, comments: List<Comment>) {
        if (comments.isEmpty()) return
        val targetUrns = mutableListOf<String>()
        comments.forEach { c ->
            targetUrns.add("soundcloud:comments:${c.id}")
            c.replies?.forEach { reply -> targetUrns.add("soundcloud:comments:${reply.id}") }
        }

        targetUrns.chunked(100).forEach { batchUrns ->
            val parentUrn = "soundcloud:tracks:$trackId"
            val query =
                "query UserInteractions(" + '$' + "parentUrn: String!, " + '$' + "interactionTypeUrn: String!, " + '$' + "targetUrns: [String!]!) { user: userInteractions(parentUrn: " + '$' + "parentUrn, interactionTypeUrn: " + '$' + "interactionTypeUrn, targetUrns: " + '$' + "targetUrns) { targetUrn, userInteraction, interactionCounts { count, interactionTypeValueUrn } } }"
            val variables = GraphQlVariablesUserCheck(parentUrn = parentUrn, targetUrns = batchUrns)
            val request = GraphQlRequest("UserInteractions", query, variables)
            try {
                val responseJson = api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                val data = gson.fromJson(responseJson, GraphQlResponseUserInteractions::class.java)
                data.data?.user?.forEach { interaction ->
                    val idStr = interaction.targetUrn.substringAfterLast(":")
                    val commentId = idStr.toLongOrNull() ?: 0L
                    val isLikedByMe = interaction.userInteraction != null
                    val totalLikes =
                        interaction.interactionCounts?.find { it.type == "sc:interactiontypevalue:like" }?.count
                    updateCommentInList(commentId, isLikedByMe, totalLikes)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateCommentInList(commentId: Long, isLiked: Boolean, count: Int?) {
        val index = commentsList.indexOfFirst { it.id == commentId }
        if (index != -1) {
            val c = commentsList[index]
            commentsList[index] = c.copy(isLiked = isLiked, likesCount = count ?: c.likesCount)
            return
        }
        for (i in commentsList.indices) {
            val parent = commentsList[i]
            val replyIndex = parent.replies?.indexOfFirst { it.id == commentId } ?: -1
            if (replyIndex != -1) {
                val replies = parent.replies!!.toMutableList()
                val r = replies[replyIndex]
                replies[replyIndex] = r.copy(isLiked = isLiked, likesCount = count ?: r.likesCount)
                commentsList[i] = parent.copy(replies = replies)
                return
            }
        }
    }

    fun emojiToCodepoint(emoji: String): String {
        val trimmed = emoji.trim()
        return when (trimmed) {
            "🔥" -> "1f525"
            "👏" -> "1f44f"
            "🥹" -> "1f979"
            "❤️" -> "2764"
            "😍" -> "1f60d"
            else -> {
                try {
                    trimmed.codePoints()
                        .filter { it != 0xFE0F }
                        .mapToObj { Integer.toHexString(it) }
                        .toArray()
                        .joinToString("-")
                } catch (e: Exception) {
                    trimmed
                }
            }
        }
    }

    fun codepointToEmoji(codepoint: String): String {
        val clean = codepoint.removePrefix("sc:interactiontypevalue:").trim().lowercase()
        return when (clean) {
            "1f525" -> "🔥"
            "1f44f" -> "👏"
            "1f979" -> "🥹"
            "2764", "2764-fe0f" -> "❤️"
            "1f60d" -> "😍"
            else -> {
                try {
                    val cps = clean.split("-").map { it.toInt(16) }
                    String(cps.toIntArray(), 0, cps.size)
                } catch (e: Exception) {
                    clean
                }
            }
        }
    }

    private fun parseTimestampFromUrn(urn: String?): Long {
        if (urn == null) return 0L
        val clean = urn.removePrefix("ts:").trim()
        if (clean.contains("#")) {
            val fragment = clean.substringAfter("#")
            if (fragment.contains("=")) {
                val value = fragment.substringAfter("=").toLongOrNull() ?: 0L
                return if (fragment.contains("seconds", ignoreCase = true)) value * 1000L else value
            }
            val raw = fragment.toLongOrNull() ?: 0L
            return if (raw in 1..9999L) raw * 1000L else raw
        }
        val raw = clean.toLongOrNull() ?: 0L
        return if (raw in 1..9999L) raw * 1000L else raw
    }

    fun loadTrackReactions(trackId: Long) {
        viewModelScope.launch {
            try {
                val parentUrn = "soundcloud:tracks:$trackId"
                val query =
                    "query InteractionCountsByParent(" + '$' + "parentUrn: String!, " + '$' + "interactionTypeUrn: String!) { interactionCountsByParent(parentUrn: " + '$' + "parentUrn, interactionTypeUrn: " + '$' + "interactionTypeUrn) { interactionCounts { count, interactionTypeValueUrn } } }"
                val variables = GraphQlVariablesReactionCounts(parentUrn = parentUrn)
                val request = GraphQlRequest("InteractionCountsByParent", query, variables)
                val responseJson = api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                val countsMap = mutableMapOf<String, Int>()
                val parentObj = responseJson.getAsJsonObject("data")?.getAsJsonObject("interactionCountsByParent")
                val countsArray = parentObj?.getAsJsonArray("interactionCounts")
                countsArray?.forEach { el ->
                    val obj = el.asJsonObject
                    val urn = obj.get("interactionTypeValueUrn")?.asString ?: ""
                    val count = obj.get("count")?.asInt ?: 0
                    val rawCodepoint = urn.substringAfter("sc:interactiontypevalue:", "")
                    val emoji = codepointToEmoji(rawCodepoint)
                    if (emoji.isNotEmpty() && count > 0) {
                        countsMap[emoji] = count
                    }
                }
                if (countsMap.isNotEmpty()) {
                    trackReactionCounts = countsMap
                    countsMap.keys.forEach { emoji ->
                        loadReactionUsersForEmoji(trackId, emoji)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun loadReactionUsersForEmoji(trackId: Long, emoji: String) {
        viewModelScope.launch {
            try {
                isReactionsLoading = true
                val parentUrn = "soundcloud:tracks:$trackId"
                val codepoint = emojiToCodepoint(emoji)
                val interactionTypeValueUrn = "sc:interactiontypevalue:$codepoint"
                val query =
                    "query PaginatedReactionUsers(" + '$' + "interactionTypeValueUrn: String!, " + '$' + "parentUrn: String!, " + '$' + "interactionTypeUrn: String!, " + '$' + "cursor: String!, " + '$' + "limit: Int!) { parentInteractions(interactionTypeValueUrn: " + '$' + "interactionTypeValueUrn, parentUrn: " + '$' + "parentUrn, interactionTypeUrn: " + '$' + "interactionTypeUrn, limit: " + '$' + "limit, cursor: " + '$' + "cursor) { pageInfo { endCursor, hasNextPage } interactions { targetUrn, user { urn, username, userAvatarUrlTemplate } } } }"
                val variables = GraphQlVariablesReactionUsers(
                    parentUrn = parentUrn,
                    interactionTypeValueUrn = interactionTypeValueUrn,
                    limit = 50
                )
                val request = GraphQlRequest("PaginatedReactionUsers", query, variables)
                val responseJson = api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                val parentObj = responseJson.getAsJsonObject("data")?.getAsJsonObject("parentInteractions")
                val interactionsArray = parentObj?.getAsJsonArray("interactions")
                val users = mutableListOf<TrackReactionUserItem>()
                interactionsArray?.forEachIndexed { index, el ->
                    val obj = el.asJsonObject
                    val targetUrn = obj.get("targetUrn")?.asString ?: ""
                    val userObj = obj.getAsJsonObject("user")
                    val username = userObj?.get("username")?.asString ?: "Utilisateur"
                    val avatarTemplate = userObj?.get("userAvatarUrlTemplate")?.asString
                    val avatarUrl = if (!avatarTemplate.isNullOrBlank()) {
                        avatarTemplate.replace("{size}", "t50x50")
                    } else null
                    val urn = userObj?.get("urn")?.asString ?: "user_$index"
                    val timestampMs = parseTimestampFromUrn(targetUrn)
                    users.add(
                        TrackReactionUserItem(
                            id = "${urn}_${targetUrn}_$index",
                            username = username,
                            avatarUrl = avatarUrl,
                            timestampSeconds = timestampMs
                        )
                    )
                }
                val updated = trackReactionUsers.toMutableMap()
                updated[emoji] = users
                trackReactionUsers = updated
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isReactionsLoading = false
            }
        }
    }

    fun startReplying(comment: Comment) {
        replyingToComment = comment
    }

    fun cancelReplying() {
        replyingToComment = null
    }

    fun postComment(body: String, timestamp: Long?) {
        val t = selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        if (body.isBlank()) return
        pendingCommentBody = body
        pendingCommentTimestamp = timestamp
        viewModelScope.launch {
            isPostingComment = true
            try {
                val finalTimestamp = replyingToComment?.trackTimestamp ?: timestamp ?: currentPosition
                val parentId = replyingToComment?.id
                var newComment = api.postComment(t.id, body, finalTimestamp, parentId)
                if (newComment.user == null || newComment.user.username.isNullOrEmpty()) {
                    if (currentUser != null) newComment = newComment.copy(user = currentUser)
                }
                if (parentId != null) {
                    val parentIndex = commentsList.indexOfFirst { it.id == parentId }
                    if (parentIndex != -1) {
                        val parent = commentsList[parentIndex]
                        val updatedReplies = (parent.replies ?: emptyList()) + newComment
                        commentsList[parentIndex] = parent.copy(replies = updatedReplies)
                    } else commentsList.add(0, newComment)
                } else commentsList.add(0, newComment)
                emitUiEvent(getString(R.string.success_generic))
                pendingCommentBody = null; pendingCommentTimestamp = null; replyingToComment = null
            } catch (e: Exception) {
                e.printStackTrace()
                if (e.toString().contains("403") || e.toString().contains("401")) {
                    captchaUrl = t.permalinkUrl ?: "https://soundcloud.com/tracks/${t.id}"
                    emitUiEvent(getString(R.string.error_security_check))
                } else emitUiEvent(getString(R.string.error_generic))
            } finally {
                isPostingComment = false
            }
        }
    }

    var userReactedItems by mutableStateOf<Set<String>>(emptySet())

    fun isUserReacted(emoji: String, trackId: Long, second: Long): Boolean {
        val key = "${trackId}_${emoji}_$second"
        if (userReactedItems.contains(key)) return true
        val users = trackReactionUsers[emoji]
        if (currentUserId > 0L && users != null) {
            return users.any { it.timestampSeconds / 1000L == second && it.id.contains(currentUserId.toString()) }
        }
        return false
    }

    fun toggleQuickReaction(emoji: String, targetTrack: Track? = null) {
        val t = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        val pos = currentPosition
        val seconds = (pos / 1000L).coerceAtLeast(0L)
        if (isUserReacted(emoji, t.id, seconds)) {
            removeQuickReaction(emoji, t, seconds)
        } else {
            sendQuickReaction(emoji, t)
        }
    }

    fun sendQuickReaction(emoji: String, targetTrack: Track? = null) {
        val t = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        val pos = currentPosition
        val seconds = (pos / 1000L).coerceAtLeast(0L)
        val userAvatar = currentUser?.avatarUrl

        activeWaveformReaction = WaveformReactionParticle(
            emoji = emoji,
            avatarUrl = userAvatar,
            timestamp = pos
        )

        val key = "${t.id}_${emoji}_$seconds"
        userReactedItems = userReactedItems + key

        viewModelScope.launch {
            try {
                val parentUrn = "soundcloud:tracks:${t.id}"
                val targetUrn = "$parentUrn#secondsTimestamp=$seconds"
                val codepoint = emojiToCodepoint(emoji)
                val interactionTypeValueUrn = "sc:interactiontypevalue:$codepoint"
                val mutation = """
                    mutation UpsertInteraction(${'$'}input: InteractionInput!) {
                        upsertInteraction(input: ${'$'}input) {
                            targetUrn
                        }
                    }
                """.trimIndent()
                val variables = GraphQlVariablesInteraction(
                    input = InteractionInput(
                        parentUrn = parentUrn,
                        targetUrn = targetUrn,
                        interactionTypeUrn = "sc:interactiontype:trackreaction",
                        interactionTypeValueUrn = interactionTypeValueUrn
                    )
                )
                val request = GraphQlRequest("UpsertInteraction", mutation, variables)
                api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                loadTrackReactions(t.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun removeQuickReaction(emoji: String, targetTrack: Track? = null, timestampSeconds: Long? = null) {
        val t = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        val pos = currentPosition
        val seconds = timestampSeconds ?: (pos / 1000L).coerceAtLeast(0L)

        val key = "${t.id}_${emoji}_$seconds"
        userReactedItems = userReactedItems - key

        viewModelScope.launch {
            try {
                val parentUrn = "soundcloud:tracks:${t.id}"
                val targetUrn = "$parentUrn#secondsTimestamp=$seconds"
                val codepoint = emojiToCodepoint(emoji)
                val interactionTypeValueUrn = "sc:interactiontypevalue:$codepoint"
                val mutation = """
                    mutation RemoveInteraction(${'$'}input: InteractionInput!) {
                        removeInteraction(input: ${'$'}input)
                    }
                """.trimIndent()
                val variables = GraphQlVariablesInteraction(
                    input = InteractionInput(
                        parentUrn = parentUrn,
                        targetUrn = targetUrn,
                        interactionTypeUrn = "sc:interactiontype:trackreaction",
                        interactionTypeValueUrn = interactionTypeValueUrn
                    )
                )
                val request = GraphQlRequest("RemoveInteraction", mutation, variables)
                api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                loadTrackReactions(t.id)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun onCaptchaSolved() {
        captchaUrl = null; SessionManager.reloadSession()
        if (pendingCommentBody != null) {
            emitUiEvent(getString(R.string.msg_retrying)); postComment(pendingCommentBody!!, pendingCommentTimestamp)
        }
    }

    fun toggleCommentLike(comment: Comment) {
        val foundIndex = commentsList.indexOfFirst { it.id == comment.id }
        var parentIndex = -1
        if (foundIndex == -1) {
            for (i in commentsList.indices) {
                if (commentsList[i].replies?.any { it.id == comment.id } == true) {
                    parentIndex = i; break
                }
            }
        }
        if (foundIndex == -1 && parentIndex == -1) return
        val isCurrentlyLiked = comment.isLiked
        val newLikedState = !isCurrentlyLiked
        val newCount = if (newLikedState) comment.likesCount + 1 else (comment.likesCount - 1).coerceAtLeast(0)
        if (foundIndex != -1) commentsList[foundIndex] = comment.copy(isLiked = newLikedState, likesCount = newCount)
        else {
            val parent = commentsList[parentIndex]
            val replies = parent.replies!!.toMutableList()
            val rIndex = replies.indexOfFirst { it.id == comment.id }
            replies[rIndex] = replies[rIndex].copy(isLiked = newLikedState, likesCount = newCount)
            commentsList[parentIndex] = parent.copy(replies = replies)
        }
        viewModelScope.launch {
            try {
                val parentUrn = "soundcloud:tracks:${selectedTrackForSheet?.id ?: currentTrack?.id}"
                val targetUrn = "soundcloud:comments:${comment.id}"
                val input = InteractionInput(parentUrn, targetUrn)
                if (newLikedState) {
                    val query =
                        "mutation UpsertInteraction(" + '$' + "input: InteractionInput!) { upsertInteraction(input: " + '$' + "input) { interactionTypeUrn } }"
                    val request = GraphQlRequest("UpsertInteraction", query, GraphQlVariablesInteraction(input))
                    api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                } else {
                    val query =
                        "mutation RemoveInteraction(" + '$' + "input: InteractionInput!) { removeInteraction(input: " + '$' + "input) }"
                    val request = GraphQlRequest("RemoveInteraction", query, GraphQlVariablesInteraction(input))
                    api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                }
            } catch (e: Exception) {
                e.printStackTrace(); emitUiEvent(getString(R.string.error_generic))
            }
        }
    }

    fun deleteComment(comment: Comment) {
        val index = commentsList.indexOfFirst { it.id == comment.id }
        if (index != -1) commentsList.removeAt(index)
        else {
            for (i in commentsList.indices) {
                if (commentsList[i].replies?.any { it.id == comment.id } == true) {
                    val parent = commentsList[i]
                    val newReplies = parent.replies!!.filter { it.id != comment.id }
                    commentsList[i] = parent.copy(replies = newReplies)
                    break
                }
            }
        }
        viewModelScope.launch {
            try {
                val response = api.deleteComment(comment.id)
                if (response.isSuccessful) emitUiEvent(getString(R.string.success_generic)) else emitUiEvent(getString(R.string.error_generic))
            } catch (e: Exception) {
                e.printStackTrace(); emitUiEvent(getString(R.string.error_generic))
            }
        }
    }

    fun fetchUserProfile() {
        if (tokenManager.isGuestMode() || tokenManager.getAccessToken().isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                val me = api.getMe()
                currentUserId = me.id
                currentUser = me
                SoundCloudTelemetryTracker.updateCurrentUserId(me.id)
            } catch (_: Exception) {
            }
        }
    }

    fun startRadioFromTrack(track: Track) {
        showMenuSheet = false
        navigateToPlaylistId = "station:${track.id}"
    }

    fun startYoutubeRadio(track: Track) {
        showMenuSheet = false
        track.permalinkUrl?.let {
            navigateToPlaylistId = "yt_radio:${Uri.encode(it)}"
        }
    }

    fun playPlaylist(
        tracks: List<Track>,
        startIndex: Int = 0,
        context: PlaybackContext? = null,
        maintainPlayerState: Boolean = false
    ) {
        if (tracks.isEmpty()) return
        if (!maintainPlayerState) {
            isPlayerExpanded = false
        }
        SoundCloudTelemetryTracker.onQueueReset()
        _originalQueue.clear(); _originalQueue.addAll(tracks)
        _queue.clear()
        this.currentContext = context
        MusicManager.updateContext(context)

        val effectiveStartIndex = if (startIndex in tracks.indices) startIndex else 0

        val isHistoryContext =
            context?.navigationId == "history" || context?.navigationId?.startsWith("history") == true

        if (shuffleEnabled) {
            val clickedTrack = tracks[effectiveStartIndex]
            val rest =
                tracks.filterIndexed { index, _ -> index != effectiveStartIndex }.shuffled()
            _queue.add(clickedTrack)
            _queue.addAll(rest)
            playTrackAtIndex(0, addToHistory = (context == null || isHistoryContext))
        } else {
            _queue.addAll(tracks)
            playTrackAtIndex(effectiveStartIndex, addToHistory = (context == null || isHistoryContext))
        }

        updateQueueState(); saveStateAsync(saveQueue = true)
        prefetchWaveformsForQueue(effectiveStartIndex)

        if (context != null && !isHistoryContext) {
            val isStation =
                context.navigationId.contains("station") || context.navigationId.contains("yt_radio")
            val isProfile = context.navigationId.contains("profile")
            val idLong = when (context.navigationId) {
                "likes" -> -1L
                "downloads" -> -2L
                else -> context.navigationId.substringAfter(":").toLongOrNull() ?: 0L
            }
            val cleanTitle = context.displayText.substringAfter("•").trim()

            val playlistCreator = if (context.artistName != null) User(
                0,
                context.artistName,
                null,
                verified = context.isVerified
            ) else null
            val safePermalink =
                if (context.navigationId.startsWith("yt_radio:")) context.navigationId else null
            val historyPlaylist = Playlist(
                id = idLong,
                title = cleanTitle,
                artworkUrl = context.imageUrl,
                calculatedArtworkUrl = null,
                trackCount = tracks.size,
                user = playlistCreator,
                tracks = null,
                permalinkUrl = safePermalink
            )

            HistoryRepository.addToHistory(historyPlaylist, isStation, isProfile)
        }
    }

    fun playTrackAtPosition(track: Track, position: Long) {
        pendingSeekPosition = position; playPlaylist(listOf(track), 0); showCommentsSheet = false; isPlayerExpanded =
            true
    }

    fun skipToQueueItem(index: Int, autoPlay: Boolean = isPlaying) {
        playTrackAtIndex(
            index,
            addToHistory = false,
            autoPlay = autoPlay
        ); AchievementManager.trackSkipped(); AchievementManager.increment("skipper_100"); AchievementManager.increment(
            "skipper_1000"
        )
    }

    private fun playTrackAtIndex(
        index: Int,
        addToHistory: Boolean = true,
        isCrossfade: Boolean = false,
        autoPlay: Boolean = isPlaying
    ) {
        if (index < 0 || index >= _queue.size) {
            currentContext = null; return
        }
        currentQueueIndex = index
        val trackToPlay = _queue[index]

        playWhenReady = autoPlay
        isLoading = true
        duration = trackToPlay.durationMs ?: 0L
        if (!isCrossfade) {
            currentPosition = 0L
            MusicManager.isCrossfadingOut = false
        }
        currentSessionListenMs = 0L
        hasPushedRecentlyPlayed = false

        currentTrack = trackToPlay; MusicManager.currentTrack = trackToPlay
        val intent = Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_FORCE_UPDATE }
        startServiceSafe(context, intent)

        trackInitJob?.cancel()
        trackInitJob = viewModelScope.launch {
            var finalTrack = trackToPlay
            if (finalTrack.source == "soundcloud" && trackToPlay.id > 0 && (trackToPlay.user?.id == 0L || trackToPlay.media == null || trackToPlay.playbackCount == 0)) {
                try {
                    val fullTrackList = api.getTracksByIds(trackToPlay.id.toString())
                    if (fullTrackList.isNotEmpty()) {
                        finalTrack = fullTrackList[0]; _queue[index] = finalTrack
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            currentTrack = finalTrack
            MusicManager.currentTrack = finalTrack
            isLiked = LikeRepository.isTrackLiked(finalTrack.id)
            loadLyrics(finalTrack)
            AchievementManager.checkTrackNameSecret(finalTrack.title ?: "")
            saveStateAsync(saveQueue = false)

            SoundCloudTelemetryTracker.onTrackStarted(
                track = finalTrack,
                context = currentContext,
                isManual = true,
                startPositionMs = if (isCrossfade) currentPosition else 0L
            )

            playRobustly(index, autoPlay = autoPlay, isCrossfade = isCrossfade)

            prefetchWaveformsForQueue(index)

            if (addToHistory && currentContext?.navigationId?.startsWith("station:") != true && currentContext?.navigationId?.startsWith(
                    "yt_radio:"
                ) != true
            ) {
                HistoryRepository.addToHistory(finalTrack)
            }
        }
    }

    fun playNext(manual: Boolean = true, isCrossfade: Boolean = false, ignoreRepeatOne: Boolean = false) {
        if (isAutoplayRadioLoading) return

        val shouldAutoPlay = if (manual) isPlaying else true

        if (manual && player.currentPosition > 2000) {
            incrementPlayCount()
        }
        if (manual) {
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "SKIP_NEXT", currentSessionListenMs)
                }
            }
        }

        if (!manual && !ignoreRepeatOne && repeatMode == RepeatMode.ONE) {
            AchievementManager.increment("obsessed_50")
            AchievementManager.increment("obsessed_200")
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "REPEAT_ONE_LOOP", currentSessionListenMs)
                }
            }
            currentSessionListenMs = 0L
            playTrackAtIndex(
                currentQueueIndex,
                addToHistory = false,
                isCrossfade = isCrossfade,
                autoPlay = shouldAutoPlay
            )
            return
        }

        val nextIndex = currentQueueIndex + 1

        if (manual) {
            AchievementManager.trackSkipped()
            AchievementManager.increment("skipper_100")
            AchievementManager.increment("skipper_1000")
        }

        if (nextIndex < _queue.size) {
            playTrackAtIndex(nextIndex, addToHistory = false, isCrossfade = isCrossfade, autoPlay = shouldAutoPlay)
        } else {
            if (repeatMode == RepeatMode.ALL) {
                playTrackAtIndex(0, addToHistory = false, isCrossfade = isCrossfade, autoPlay = shouldAutoPlay)
            } else {
                val autoPlayEnabled = playerPrefs.getAutoplayEnabled()
                val isYoutube = currentTrack?.source == "youtube"

                if (autoPlayEnabled || isYoutube) {
                    viewModelScope.launch {
                        val youtubeFallback = playerPrefs.getYouTubeFallbackEnabled()

                        if (isYoutube || (currentTrack?.source == "soundcloud" && youtubeFallback)) {
                            fetchAndPlayYoutubeRadio()
                        } else {
                            fetchAndQueueRadio()
                        }

                        val newNextIndex = currentQueueIndex + 1
                        if (newNextIndex < _queue.size) {
                            playTrackAtIndex(
                                newNextIndex,
                                addToHistory = false,
                                isCrossfade = isCrossfade,
                                autoPlay = shouldAutoPlay
                            )
                        } else {
                            MusicManager.player.pause()
                            MusicManager.player.seekTo(0)
                            saveStateAsync()
                        }
                    }
                } else {
                    MusicManager.player.pause()
                    MusicManager.player.seekTo(0)
                }
            }
        }
    }

    private suspend fun fetchAndPlayYoutubeRadio() {
        val lastTrack = currentTrack ?: return
        isAutoplayRadioLoading = true
        try {
            val videoId = lastTrack.permalinkUrl?.substringAfter("v=")?.substringBefore("&") ?: return
            val radioUrl = "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"

            withContext(Dispatchers.Main) {
                val ctx = PlaybackContext(
                    displayText = "YouTube Mix • ${lastTrack.title}",
                    navigationId = "yt_radio:${Uri.encode(lastTrack.permalinkUrl)}",
                    imageUrl = lastTrack.fullResArtwork,
                    artistName = lastTrack.user?.username,
                    isVerified = false
                )
                currentContext = ctx
                MusicManager.updateContext(ctx)
                saveStateAsync(saveQueue = false)
            }

            val youtubeService = ServiceList.YouTube
            val extractor = youtubeService.getPlaylistExtractor(radioUrl)

            withContext(Dispatchers.IO) {
                extractor.fetchPage()
            }

            val streamItems = extractor.initialPage.items.filterIsInstance<StreamInfoItem>()
            val radioTracks = streamItems.map {
                Track(
                    id = abs(it.url.hashCode().toLong()),
                    title = it.name,
                    user = User(
                        id = it.uploaderUrl?.hashCode()?.toLong() ?: 0L,
                        username = it.uploaderName,
                        avatarUrl = it.uploaderAvatars.firstOrNull()?.url
                    ),
                    artworkUrl = it.thumbnails.firstOrNull()?.url,
                    durationMs = it.duration * 1000,
                    permalinkUrl = it.url,
                    source = "youtube"
                )
            }

            if (radioTracks.isNotEmpty()) {
                val newTracks = radioTracks.filter { track -> _queue.none { it.id == track.id } }

                _queue.addAll(newTracks)
                _originalQueue.addAll(newTracks)
                updateQueueState()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAutoplayRadioLoading = false
        }
    }

    private suspend fun fetchAndQueueRadio() {
        val lastTrack = currentTrack ?: return
        isAutoplayRadioLoading = true
        try {
            val station = api.getTrackStation(lastTrack.id)
            val partialTracks = station.tracks
            if (!partialTracks.isNullOrEmpty()) {
                val newTrackIds = partialTracks.map { it.id }.filter { trackId -> _queue.none { it.id == trackId } }
                if (newTrackIds.isNotEmpty()) {
                    val unorderedFullTracks = api.getTracksByIds(newTrackIds.joinToString(","))
                    val trackMap = unorderedFullTracks.associateBy { it.id }
                    val orderedFullTracks = newTrackIds.mapNotNull { id -> trackMap[id] }
                    _queue.addAll(orderedFullTracks); _originalQueue.addAll(orderedFullTracks); updateQueueState()
                }
                if (currentContext == null) {
                    val ctx = PlaybackContext(
                        getString(R.string.context_station, lastTrack.title ?: ""),
                        "station:${lastTrack.id}",
                        lastTrack.fullResArtwork
                    )
                    currentContext = ctx
                    MusicManager.updateContext(ctx)
                }
            }
        } catch (_: Exception) {
        } finally {
            isAutoplayRadioLoading = false
        }
    }

    fun playPrevious(manual: Boolean = true, isCrossfade: Boolean = false) {
        val shouldAutoPlay = if (manual) isPlaying else true
        if (manual) {
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "SKIP_PREVIOUS", currentSessionListenMs)
                }
            }
        }

        val prevIndex = currentQueueIndex - 1
        if (prevIndex >= 0) {
            playTrackAtIndex(prevIndex, addToHistory = false, isCrossfade = isCrossfade, autoPlay = shouldAutoPlay)
        } else {
            playTrackAtIndex(0, addToHistory = false, isCrossfade = isCrossfade, autoPlay = shouldAutoPlay)
        }
    }

    fun smartPrevious() {
        if (player.currentPosition > 2000) {
            incrementPlayCount()
        }

        if (player.currentPosition > 5000) {
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "MANUAL_REPLAY", currentSessionListenMs)
                }
            }
            currentSessionListenMs = 0L
            currentPosition = 0L
            player.seekTo(0)
        } else {
            val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
            playPrevious(manual = true, isCrossfade = crossfadeEnabled)
        }
    }

    fun toggleShuffle() {
        shuffleEnabled =
            !shuffleEnabled; if (shuffleEnabled) applyShuffle() else revertShuffle(); updateQueueState(); saveStateAsync(
            saveQueue = true
        )
    }

    private fun applyShuffle(startIndex: Int = currentQueueIndex, sourceList: List<Track> = _originalQueue) {
        if (sourceList.isEmpty() || startIndex !in sourceList.indices) return

        val played = sourceList.subList(0, startIndex + 1)
        val upcoming =
            if (startIndex + 1 < sourceList.size) sourceList.subList(startIndex + 1, sourceList.size) else emptyList()

        val shuffledUpcoming = upcoming.shuffled()

        _queue.clear()
        _queue.addAll(played)
        _queue.addAll(shuffledUpcoming)
    }

    private fun revertShuffle() {
        val currentTrackId =
            currentTrack?.id ?: return; _queue.clear(); _queue.addAll(_originalQueue); currentQueueIndex =
            _queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0)
    }

    private fun applyRepeatMode() {
        val exoMode = when (repeatMode) {
            RepeatMode.NONE -> Player.REPEAT_MODE_OFF
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
        }
        MusicManager.player.repeatMode = exoMode
    }

    fun toggleRepeatMode() {
        repeatMode = when (repeatMode) {
            RepeatMode.NONE -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.NONE
        }
        applyRepeatMode()
        saveStateAsync(saveQueue = false)
    }

    fun syncQueueFromPlayer() {
    }

    fun updateQueueState() {
        queueState = _queue.toList()
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (from == to) return

        if (from < queueState.size && to < queueState.size) {
            val mut = queueState.toMutableList()
            val item = mut.removeAt(from)
            mut.add(to, item)
            queueState = mut
        }

        if (from < _queue.size && to < _queue.size) {
            val item = _queue.removeAt(from)
            _queue.add(to, item)
        }

        if (!shuffleEnabled && from < _originalQueue.size && to < _originalQueue.size + 1) {
            val originalItem = _originalQueue.removeAt(from)
            _originalQueue.add(to, originalItem)
        }

        if (currentTrack != null) {
            currentQueueIndex = _queue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
        }

        if (MusicManager.player.mediaItemCount > 1) {
            try {
                MusicManager.player.removeMediaItem(1)
            } catch (_: Exception) {
            }
        }
        preloadNextTrack(currentQueueIndex + 1)

        saveStateAsync(saveQueue = true)
    }

    fun removeTrackFromQueue(index: Int) {
        if (index !in _queue.indices) return

        val trackToRemove = _queue[index]

        _queue.removeAt(index)

        if (index < queueState.size) {
            val mut = queueState.toMutableList()
            mut.removeAt(index)
            queueState = mut
        }

        _originalQueue.removeAll { it.id == trackToRemove.id }

        if (currentTrack != null) {
            currentQueueIndex = _queue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
        }
        if (MusicManager.player.mediaItemCount > 1) {
            try {
                MusicManager.player.removeMediaItem(1)
            } catch (_: Exception) {
            }
        }
        preloadNextTrack(currentQueueIndex + 1)

        saveStateAsync(saveQueue = true)
    }

    fun insertNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val insertIndex = currentQueueIndex + 1

        val uniqueTracks = tracks.map { it.copy() }

        _queue.addAll(insertIndex, uniqueTracks)
        _originalQueue.addAll(insertIndex, uniqueTracks)
        updateQueueState()

        if (MusicManager.player.mediaItemCount > 1) {
            try {
                MusicManager.player.removeMediaItem(1)
            } catch (_: Exception) {
            }
        }
        preloadNextTrack(currentQueueIndex + 1)

        saveStateAsync(saveQueue = true)
        emitUiEvent(getString(R.string.menu_play_next))
    }

    fun togglePlayPause() {
        if (player.isPlaying || playWhenReady) {
            playWhenReady = false
            player.pause()
            saveStateAsync(savePositionOnly = true)
        } else {
            playWhenReady = true
            if (player.currentMediaItem == null && currentTrack != null) {
                pendingSeekPosition = currentPosition
                playPlaylist(
                    tracks = queueState.toList(),
                    startIndex = currentQueueIndex,
                    context = currentContext,
                    maintainPlayerState = true
                )
            } else {
                player.play()
            }
        }
    }

    fun seekTo(position: Long) {
        isScrubbing = false
        player.seekTo(position)
        currentPosition = position
        SoundCloudTelemetryTracker.onTrackSeeked(position)
        saveStateAsync(saveQueue = false)
    }

    /** Fetches the waveform_url for a track using the single-track endpoint (includes waveform_url). */
    suspend fun fetchWaveformUrl(trackId: Long): String? {
        return try {
            api.getTrackById(trackId).waveformUrl
        } catch (e: Exception) {
            android.util.Log.w("WaveformPlayer", "fetchWaveformUrl failed for $trackId: ${e.message}")
            null
        }
    }


    fun toggleLike() {
        val t = currentTrack ?: return
        isLiked = !isLiked

        if (isLiked) {
            LikeRepository.addLike(t)
            AchievementManager.increment("liker_50")
            AchievementManager.increment("liker_1000")
            AchievementManager.increment("liker_5000")
        } else {
            LikeRepository.removeLike(t.id)
        }
    }

    fun toggleTrackLike(track: Track) {
        if (track.id == currentTrack?.id) {
            toggleLike()
        } else {
            val isCurrentlyLiked = LikeRepository.isTrackLiked(track.id)
            if (isCurrentlyLiked) {
                LikeRepository.removeLike(track.id)
            } else {
                LikeRepository.addLike(track)
                AchievementManager.increment("liker_50")
                AchievementManager.increment("liker_1000")
                AchievementManager.increment("liker_5000")
            }
        }
    }

    fun togglePreciseSpeedEnabled(enabled: Boolean) {
        isPreciseSpeedEnabled = enabled; playerPrefs.setPreciseSpeedEnabled(enabled)
    }

    fun toggleRain() {
        val n = !effectsState.isRainEnabled; effectsState = effectsState.copy(isRainEnabled = n); applyEffectsAndSave()
    }

    fun setRainVolume(volume: Float) {
        effectsState =
            effectsState.copy(rainVolume = volume); MusicManager.applyEffects(effectsState); viewModelScope.launch(
            Dispatchers.IO
        ) { playerPrefs.saveEffects(effectsState) }
    }

    fun setCustomSpeed(speed: Float) {
        val factor = if (isPreciseSpeedEnabled) 20f else 10f;
        val r = (speed * factor).roundToInt() / factor; effectsState =
            effectsState.copy(speed = r); applyEffectsAndSave()
    }

    fun togglePitchEnabled(e: Boolean) {
        effectsState = effectsState.copy(isPitchEnabled = e); applyEffectsAndSave()
    }

    fun toggle8D() {
        effectsState = effectsState.copy(is8DEnabled = !effectsState.is8DEnabled); applyEffectsAndSave()
    }

    fun setEightDSpeed(v: Float) {
        effectsState = effectsState.copy(eightDSpeed = v); applyEffectsAndSave()
    }

    fun toggleMuffled() {
        val n = !effectsState.isMuffledEnabled; effectsState =
            effectsState.copy(isMuffledEnabled = n); applyEffectsAndSave()
    }

    fun setMuffledIntensity(v: Float) {
        effectsState = effectsState.copy(muffledIntensity = v); applyEffectsAndSave()
    }

    fun toggleBassBoost() {
        val n = !effectsState.isBassBoostEnabled; effectsState =
            effectsState.copy(isBassBoostEnabled = n); applyEffectsAndSave(); if (n) AchievementManager.increment(
            "bass_addict",
            1
        )
    }

    fun setBassBoostIntensity(v: Float) {
        effectsState = effectsState.copy(bassBoostIntensity = v); applyEffectsAndSave()
    }

    fun toggleReverb() {
        effectsState = effectsState.copy(isReverbEnabled = !effectsState.isReverbEnabled); applyEffectsAndSave()
    }

    fun setReverbIntensity(v: Float) {
        effectsState = effectsState.copy(reverbIntensity = v); applyEffectsAndSave()
    }

    fun toggleEarrape() {
        val n = !effectsState.isEarrapeEnabled; effectsState =
            effectsState.copy(isEarrapeEnabled = n); applyEffectsAndSave(); if (n) AchievementManager.increment(
            "bass_addict",
            1
        )
    }

    fun setEarrapeIntensity(v: Float) {
        effectsState = effectsState.copy(earrapeIntensity = v); applyEffectsAndSave()
    }

    fun toggleMono() {
        val n = !effectsState.isMonoEnabled; effectsState = effectsState.copy(isMonoEnabled = n); applyEffectsAndSave()
    }

    fun toggleNormalization() {
        val n = !effectsState.isNormalizationEnabled; effectsState =
            effectsState.copy(isNormalizationEnabled = n); applyEffectsAndSave()
    }

    fun setNormalizationLevel(level: com.alananasss.kittytune.ui.player.NormalizationLevel) {
        effectsState = effectsState.copy(normalizationLevel = level); applyEffectsAndSave()
    }

    fun toggleVintageMp3() {
        val n = !effectsState.isVintageMp3Enabled; effectsState =
            effectsState.copy(isVintageMp3Enabled = n); applyEffectsAndSave()
    }

    fun setVintageMp3Compression(v: Float) {
        effectsState = effectsState.copy(vintageMp3Compression = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleVocalRemover() {
        val n = !effectsState.isVocalRemoverEnabled; effectsState =
            effectsState.copy(isVocalRemoverEnabled = n); applyEffectsAndSave()
    }

    fun setVocalRemoverLevel(v: Float) {
        effectsState = effectsState.copy(vocalRemoverLevel = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleVocalBoost() {
        val n = !effectsState.isVocalBoostEnabled; effectsState =
            effectsState.copy(isVocalBoostEnabled = n); applyEffectsAndSave()
    }

    fun setVocalBoostIntensity(v: Float) {
        effectsState = effectsState.copy(vocalBoostIntensity = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleFlanger() {
        val n = !effectsState.isFlangerEnabled; effectsState =
            effectsState.copy(isFlangerEnabled = n); applyEffectsAndSave()
    }

    fun setFlangerIntensity(v: Float) {
        effectsState = effectsState.copy(flangerIntensity = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setFlangerSpeed(v: Float) {
        effectsState = effectsState.copy(flangerSpeed = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun togglePartyNextDoor() {
        val n = !effectsState.isPartyNextDoorEnabled; effectsState =
            effectsState.copy(isPartyNextDoorEnabled = n); applyEffectsAndSave()
    }

    fun setPartyNextDoorIsolation(v: Float) {
        effectsState = effectsState.copy(partyNextDoorIsolation = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setPartyNextDoorReverb(v: Float) {
        effectsState = effectsState.copy(partyNextDoorReverb = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setPartyNextDoorBassRumble(v: Float) {
        effectsState = effectsState.copy(partyNextDoorBassRumble = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleSuperWide() {
        val n = !effectsState.isSuperWideEnabled; effectsState =
            effectsState.copy(isSuperWideEnabled = n); applyEffectsAndSave()
    }

    fun setSuperWideWidth(v: Float) {
        effectsState = effectsState.copy(superWideWidth = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setSuperWideDepth(v: Float) {
        effectsState = effectsState.copy(superWideDepth = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleVinylLoFi() {
        val n = !effectsState.isVinylLoFiEnabled; effectsState =
            effectsState.copy(isVinylLoFiEnabled = n); applyEffectsAndSave()
    }

    fun setVinylCrackles(v: Float) {
        effectsState = effectsState.copy(vinylCrackles = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setVinylFlutter(v: Float) {
        effectsState = effectsState.copy(vinylFlutter = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun togglePhaser() {
        val n = !effectsState.isPhaserEnabled; effectsState =
            effectsState.copy(isPhaserEnabled = n); applyEffectsAndSave()
    }

    fun setPhaserSpeed(v: Float) {
        effectsState = effectsState.copy(phaserSpeed = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setPhaserFeedback(v: Float) {
        effectsState = effectsState.copy(phaserFeedback = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleMegaphone() {
        val n = !effectsState.isMegaphoneEnabled; effectsState =
            effectsState.copy(isMegaphoneEnabled = n); applyEffectsAndSave()
    }

    fun setMegaphoneTone(v: Float) {
        effectsState = effectsState.copy(megaphoneTone = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setMegaphoneDrive(v: Float) {
        effectsState = effectsState.copy(megaphoneDrive = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleRobotVocoder() {
        val n = !effectsState.isRobotVocoderEnabled; effectsState =
            effectsState.copy(isRobotVocoderEnabled = n); applyEffectsAndSave()
    }

    fun setRobotFrequency(v: Float) {
        effectsState = effectsState.copy(robotFrequency = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setRobotMix(v: Float) {
        effectsState = effectsState.copy(robotMix = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleChorus() {
        val n = !effectsState.isChorusEnabled; effectsState =
            effectsState.copy(isChorusEnabled = n); applyEffectsAndSave()
    }

    fun setChorusRate(v: Float) {
        effectsState = effectsState.copy(chorusRate = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setChorusDepth(v: Float) {
        effectsState = effectsState.copy(chorusDepth = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleUnderwater() {
        val n = !effectsState.isUnderwaterEnabled; effectsState =
            effectsState.copy(isUnderwaterEnabled = n); applyEffectsAndSave()
    }

    fun setUnderwaterDepth(v: Float) {
        effectsState = effectsState.copy(underwaterDepth = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setUnderwaterBubbles(v: Float) {
        effectsState = effectsState.copy(underwaterBubbles = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleTranceGate() {
        val n = !effectsState.isTranceGateEnabled; effectsState =
            effectsState.copy(isTranceGateEnabled = n); applyEffectsAndSave()
    }

    fun setTranceGateSpeed(v: Float) {
        effectsState = effectsState.copy(tranceGateSpeed = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setTranceGatePattern(v: Float) {
        effectsState = effectsState.copy(tranceGatePattern = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setTranceGateMix(v: Float) {
        effectsState = effectsState.copy(tranceGateMix = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun togglePingPongDelay() {
        val n = !effectsState.isPingPongDelayEnabled; effectsState =
            effectsState.copy(isPingPongDelayEnabled = n); applyEffectsAndSave()
    }

    fun setPingPongDelayTime(v: Float) {
        effectsState = effectsState.copy(pingPongDelayTime = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setPingPongFeedback(v: Float) {
        effectsState = effectsState.copy(pingPongFeedback = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleChiptune() {
        val n = !effectsState.isChiptuneEnabled; effectsState =
            effectsState.copy(isChiptuneEnabled = n); applyEffectsAndSave()
    }

    fun setChiptuneBits(v: Float) {
        effectsState = effectsState.copy(chiptuneBits = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setChiptuneSampleRate(v: Float) {
        effectsState = effectsState.copy(chiptuneSampleRate = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleShimmerReverb() {
        val n = !effectsState.isShimmerReverbEnabled; effectsState =
            effectsState.copy(isShimmerReverbEnabled = n); applyEffectsAndSave()
    }

    fun setShimmerSize(v: Float) {
        effectsState = effectsState.copy(shimmerSize = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setShimmerMix(v: Float) {
        effectsState = effectsState.copy(shimmerMix = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleRotarySpeaker() {
        val n = !effectsState.isRotarySpeakerEnabled; effectsState =
            effectsState.copy(isRotarySpeakerEnabled = n); applyEffectsAndSave()
    }

    fun setRotarySpeed(v: Float) {
        effectsState = effectsState.copy(rotarySpeed = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setRotaryDepth(v: Float) {
        effectsState = effectsState.copy(rotaryDepth = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleTapeSaturation() {
        val n = !effectsState.isTapeSaturationEnabled; effectsState =
            effectsState.copy(isTapeSaturationEnabled = n); applyEffectsAndSave()
    }

    fun setTapeWarmth(v: Float) {
        effectsState = effectsState.copy(tapeWarmth = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setTapeExciter(v: Float) {
        effectsState = effectsState.copy(tapeExciter = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleSubOctaver() {
        val n = !effectsState.isSubOctaverEnabled; effectsState =
            effectsState.copy(isSubOctaverEnabled = n); applyEffectsAndSave()
    }

    fun setSubOctaverLevel(v: Float) {
        effectsState = effectsState.copy(subOctaverLevel = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setSubOctaverCutoff(v: Float) {
        effectsState = effectsState.copy(subOctaverCutoff = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleEmptyMall() {
        val n = !effectsState.isEmptyMallEnabled; effectsState =
            effectsState.copy(isEmptyMallEnabled = n); applyEffectsAndSave()
    }

    fun setEmptyMallDistance(v: Float) {
        effectsState = effectsState.copy(emptyMallDistance = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setEmptyMallReverb(v: Float) {
        effectsState = effectsState.copy(emptyMallReverb = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleGramophone() {
        val n = !effectsState.isGramophoneEnabled; effectsState =
            effectsState.copy(isGramophoneEnabled = n); applyEffectsAndSave()
    }

    fun setGramophoneAge(v: Float) {
        effectsState = effectsState.copy(gramophoneAge = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setGramophoneHorn(v: Float) {
        effectsState = effectsState.copy(gramophoneHorn = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleReverseEcho() {
        val n = !effectsState.isReverseEchoEnabled; effectsState =
            effectsState.copy(isReverseEchoEnabled = n); applyEffectsAndSave()
    }

    fun setReverseEchoTime(v: Float) {
        effectsState = effectsState.copy(reverseEchoTime = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setReverseEchoFeedback(v: Float) {
        effectsState = effectsState.copy(reverseEchoFeedback = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleStadium() {
        val n = !effectsState.isStadiumEnabled; effectsState =
            effectsState.copy(isStadiumEnabled = n); applyEffectsAndSave()
    }

    fun setStadiumSize(v: Float) {
        effectsState = effectsState.copy(stadiumSize = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setStadiumAtmosphere(v: Float) {
        effectsState = effectsState.copy(stadiumAtmosphere = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleWalkman() {
        val n = !effectsState.isWalkmanEnabled; effectsState =
            effectsState.copy(isWalkmanEnabled = n); applyEffectsAndSave()
    }

    fun setWalkmanDrive(v: Float) {
        effectsState = effectsState.copy(walkmanDrive = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setWalkmanHiss(v: Float) {
        effectsState = effectsState.copy(walkmanHiss = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleAsmrVocal() {
        val n = !effectsState.isAsmrVocalEnabled; effectsState =
            effectsState.copy(isAsmrVocalEnabled = n); applyEffectsAndSave()
    }

    fun setAsmrProximity(v: Float) {
        effectsState = effectsState.copy(asmrProximity = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setAsmrAir(v: Float) {
        effectsState = effectsState.copy(asmrAir = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun toggleNightDrive() {
        val n = !effectsState.isNightDriveEnabled; effectsState =
            effectsState.copy(isNightDriveEnabled = n); applyEffectsAndSave()
    }

    fun setNightDriveCabin(v: Float) {
        effectsState = effectsState.copy(nightDriveCabin = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setNightDriveRoad(v: Float) {
        effectsState = effectsState.copy(nightDriveRoad = v.coerceIn(0f, 1f)); applyEffectsAndSave()
    }

    fun setAmbientType(type: String) {
        effectsState = effectsState.copy(ambientType = type); applyEffectsAndSave()
    }

    fun hasSeenEarrapeWarning(): Boolean = playerPrefs.hasSeenEarrapeWarning()
    fun setHasSeenEarrapeWarning(seen: Boolean) {
        playerPrefs.setHasSeenEarrapeWarning(seen)
    }

    var pinnedAudioFx by mutableStateOf(playerPrefs.getPinnedAudioFx())
        private set

    fun togglePinAudioFx(fxId: String): Boolean {
        val current = pinnedAudioFx.toMutableList()
        val isPinned = current.contains(fxId)
        if (isPinned) {
            current.remove(fxId)
        } else {
            current.add(fxId)
        }
        pinnedAudioFx = current
        playerPrefs.setPinnedAudioFx(current)
        return true
    }

    fun updatePinnedAudioFx(fxIds: List<String>) {
        pinnedAudioFx = fxIds
        playerPrefs.setPinnedAudioFx(fxIds)
    }

    fun resetPinnedAudioFx() {
        pinnedAudioFx = PlayerPreferences.DEFAULT_PINNED_AUDIO_FX
        playerPrefs.setPinnedAudioFx(PlayerPreferences.DEFAULT_PINNED_AUDIO_FX)
    }

    fun isAudioFxPinned(fxId: String): Boolean = pinnedAudioFx.contains(fxId)

    private fun applyEffectsAndSave() {
        MusicManager.applyEffects(effectsState); viewModelScope.launch(Dispatchers.IO) {
            playerPrefs.saveEffects(
                effectsState
            )
        }
    }

    fun loadSocialProof(specificTrack: Track? = null) {
        val t = specificTrack ?: trackForMenu ?: selectedTrackForSheet ?: currentTrack ?: return
        val trackId = t.id
        if (trackId <= 0 || t.source == "youtube") {
            socialLikers = emptyList()
            return
        }
        val cached = com.alananasss.kittytune.data.SocialProofRepository.getLikersForTrack(trackId)
        if (cached != null) {
            socialProofTrackId = trackId
            socialLikers = cached
            isSocialLikersLoading = false
            return
        }
        if (socialProofTrackId == trackId && socialLikers.isNotEmpty()) return
        socialProofTrackId = trackId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                isSocialLikersLoading = true
                val trackUrn = "soundcloud:tracks:$trackId"
                val query = """
                    query RelatedLikersForTracks(${'$'}input: AllTracksInput!) {
                        allTracks(allTracksInput: ${'$'}input) {
                           urn
                           relatedLikers {
                             users {
                               urn
                               permalink
                               username
                               avatarUrl
                               firstName
                               lastName
                               city
                               country
                               countryCode
                               tracksCount
                               playlistCount
                               followersCount
                               followingsCount
                               verified
                               isPro
                               description
                               userAvatarUrlTemplate
                               visualUrlTemplate
                               stationUrns
                               createdAt
                               badges
                             }
                           }
                        }
                    }
                """.trimIndent()
                val request = RelatedLikersRequest(
                    query = query,
                    variables = RelatedLikersVariables(
                        input = RelatedLikersInput(
                            trackKeys = listOf(RelatedLikersTrackKey(urn = trackUrn))
                        )
                    )
                )
                val response = api.getRelatedLikersGraphQL(request)
                val myId = try {
                    api.getMe().id
                } catch (e: Exception) {
                    0L
                }
                val myUrn = if (myId > 0) "soundcloud:users:$myId" else ""
                val apiUsers = response.data?.allTracks
                    ?.flatMap { it.relatedLikers?.users.orEmpty() }
                    ?.filter { !it.urn.isNullOrEmpty() && it.urn != myUrn }
                    .orEmpty()

                val mapped = apiUsers.mapNotNull { u ->
                    val userId = u.urn?.substringAfterLast(':')?.toLongOrNull() ?: 0L
                    if (userId > 0 && !u.username.isNullOrEmpty()) {
                        User(
                            id = userId,
                            username = u.username,
                            avatarUrl = u.avatarUrl,
                            verified = u.verified ?: false,
                            urn = u.urn
                        )
                    } else null
                }
                com.alananasss.kittytune.data.SocialProofRepository.putLikersForTrack(trackId, mapped)
                withContext(Dispatchers.Main) {
                    socialLikers = mapped
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    socialLikers = emptyList()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isSocialLikersLoading = false
                }
            }
        }
    }

    fun showTrackOptions(track: Track, playlistContextId: Long? = null, fromPlayer: Boolean = false) {
        trackForMenu = track
        menuContextPlaylistId = playlistContextId
        isMenuContextFromPlayer = fromPlayer
        loadSocialProof(track)
        showMenuSheet = true
    }

    fun prepareBulkAdd(tracks: List<Track>) {
        tracksToAddInBulk = tracks; trackForMenu = null; showAddToPlaylistSheet = true
    }

    fun addToPlaylist(playlistId: Long, track: Track) {
        DownloadManager.addTrackToPlaylist(playlistId, track); showAddToPlaylistSheet =
            false; emitUiEvent(getString(R.string.success_generic))
    }

    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) {
        DownloadManager.addTracksToPlaylistBulk(playlistId, tracks)
        viewModelScope.launch {
            showAddToPlaylistSheet = false
            emitUiEvent(getString(R.string.success_generic))
            AchievementManager.increment("playlist_creator")
            AchievementManager.increment("playlist_god")
        }
    }

    fun createAndAddToPlaylist(name: String, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = DownloadManager.createUserPlaylist(name)
            DownloadManager.addTrackToPlaylist(id, track)
            withContext(Dispatchers.Main) {
                showAddToPlaylistSheet = false
                emitUiEvent(getString(R.string.success_generic))
                AchievementManager.increment("playlist_creator")
            }
        }
    }

    fun createAndAddTracksToPlaylist(name: String, tracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = DownloadManager.createUserPlaylist(name)
            DownloadManager.addTracksToPlaylistBulk(id, tracks)
            withContext(Dispatchers.Main) {
                showAddToPlaylistSheet = false
                emitUiEvent(getString(R.string.success_generic))
                AchievementManager.increment("playlist_creator")
            }
        }
    }

    fun removeFromContextPlaylist(playlistId: Long, track: Track) {
        if (playlistId == -2L) {
            DownloadManager.deleteTrack(track.id)
        } else {
            val syncToCloud =
                playlistId > 0 && currentContext?.navigationId?.startsWith("downloaded_section:") != true && currentContext?.navigationId != "downloads"
            DownloadManager.removeTrackFromPlaylist(playlistId, track.id, syncToCloud = syncToCloud)
        }
        showMenuSheet = false
        emitUiEvent(getString(R.string.success_generic))
    }

    fun addToQueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return

        val uniqueTracks = tracks.map { it.copy() }

        val mediaItems = uniqueTracks.map { track ->
            buildMediaItem(track, null, null)
        }
        player.addMediaItems(mediaItems)

        _queue.addAll(uniqueTracks)
        _originalQueue.addAll(uniqueTracks)
        updateQueueState()
        saveStateAsync(saveQueue = true)
        emitUiEvent(getString(R.string.menu_add_queue))
    }

    fun downloadTrack(track: Track) {
        if (DownloadManager.isTrackDownloading(track.id)) return; DownloadManager.downloadTrack(track); AchievementManager.increment(
            "download_1000"
        )
    }

    private fun emitUiEvent(msg: String) {
        viewModelScope.launch { _uiEvent.emit(msg) }
    }

    private fun saveStateAsync(saveQueue: Boolean = false, savePositionOnly: Boolean = false) {
        if (isRestoringSession) return
        val t = currentTrack
        val p = currentPosition
        val c = currentContext
        val s = shuffleEnabled
        val r = repeatMode
        if (savePositionOnly) {
            viewModelScope.launch(Dispatchers.IO) { playerPrefs.savePosition(p) }
            return
        }
        if (saveQueue) {
            saveQueueJob?.cancel()
            saveQueueJob = viewModelScope.launch(Dispatchers.Main) {
                delay(500.milliseconds)
                val freshT = currentTrack
                val freshP = currentPosition
                val freshC = currentContext
                val freshS = shuffleEnabled
                val freshR = repeatMode
                val qSnapshot = _queue.toList()
                withContext(Dispatchers.IO) {
                    playerPrefs.savePlaybackState(freshT, freshP, qSnapshot, freshC, freshS, freshR)
                }
            }
        } else {
            val q = _queue.toList()
            viewModelScope.launch(Dispatchers.IO) {
                playerPrefs.savePlaybackState(t, p, q, c, s, r)
            }
        }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val tokenManager = TokenManager(context)
            val isGuest = tokenManager.isGuestMode()
            var lastSaveTime = System.currentTimeMillis()
            while (isActive && isPlaying) {
                try {
                    if (!isScrubbing) {
                        currentPosition = MusicManager.player.currentPosition.coerceAtLeast(0L)
                        currentSessionListenMs += 1000L
                        SoundCloudTelemetryTracker.onProgressUpdate(currentPosition)

                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime > 5000L) {
                            lastSaveTime = now
                            saveStateAsync(savePositionOnly = true)
                        }

                        val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
                        val crossfadeMs = playerPrefs.getCrossfadeDuration() * 1000L
                        val dur = if (MusicManager.player.duration > 0) MusicManager.player.duration else duration

                        if (crossfadeEnabled && dur > 0 && currentPosition >= (dur - crossfadeMs) && !MusicManager.isCrossfadingOut) {
                            MusicManager.isCrossfadingOut = true
                            playNext(manual = false, isCrossfade = true)
                        }
                    }
                    AchievementManager.addPlayTime(1, isGuest, effectsState.speed)
                    if (effectsState.isBassBoostEnabled || effectsState.isEarrapeEnabled) AchievementManager.increment(
                        "bass_addict",
                        1
                    )

                } catch (_: Exception) {
                }
                delay(1000.milliseconds)
            }
        }
    }

    fun updateScrubPosition(position: Long) {
        isScrubbing = true
        currentPosition = position
    }

    fun startSleepTimer(durationMs: Long) {
        cancelSleepTimer()

        val isFadeEnabled = playerPrefs.getSleepTimerFadeEnabled()
        val fadeDurationSec = if (isFadeEnabled) playerPrefs.getSleepTimerFadeDuration() else 0
        val fadeDurationMs = fadeDurationSec * 1000L
        preFadeVolume = player.volume

        val startTime = System.currentTimeMillis()
        val endTime = startTime + durationMs
        sleepTimerEndOfTrack = false

        showSleepTimerDialog = false
        showSleepTimerIslandNotification(isStarted = true, durationText = formatRemaining(durationMs))
        emitUiEvent(getString(R.string.sleep_timer_started))

        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = endTime - now

                if (remaining <= 0) {
                    player.volume = 0f
                    player.pause()
                    player.volume = preFadeVolume
                    sleepTimerRemainingMs = 0L
                    showSleepTimerIslandNotification(isStarted = false)
                    break
                }
                if (fadeDurationMs > 0L && remaining <= fadeDurationMs) {
                    val fraction = (remaining.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                    val volumeFraction = fraction * fraction
                    player.volume = (preFadeVolume * volumeFraction).coerceIn(0f, 1f)
                }

                sleepTimerRemainingMs = remaining
                delay(PlayerPreferences.SLEEP_TIMER_FADE_UPDATE_INTERVAL_MS.milliseconds)
            }
        }
    }

    private fun formatRemaining(durationMs: Long): String {
        val totalSeconds = (durationMs + 999) / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            hours > 0 -> getString(R.string.sleep_timer_hours_minutes_format, hours.toInt(), minutes.toInt())
            minutes > 0 -> getString(R.string.sleep_timer_minutes_seconds_format, minutes.toInt(), seconds.toInt())
            else -> getString(R.string.sleep_timer_seconds_format, seconds.toInt())
        }
    }

    fun startSleepTimerEndOfTrack() {
        cancelSleepTimer()
        sleepTimerRemainingMs = 0L
        sleepTimerEndOfTrack = true
        showSleepTimerDialog = false
        showSleepTimerIslandNotification(isStarted = true, durationText = getString(R.string.sleep_timer_end_of_track))
        emitUiEvent(getString(R.string.sleep_timer_started))
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (player.volume != preFadeVolume) {
            player.volume = preFadeVolume
        }
        sleepTimerRemainingMs = 0L
        sleepTimerEndOfTrack = false
    }

    fun formatSleepTimerRemaining(): String {
        if (sleepTimerEndOfTrack) return getString(R.string.sleep_timer_end_of_track)
        return formatRemaining(sleepTimerRemainingMs)
    }

    private fun showSleepTimerIslandNotification(isStarted: Boolean, durationText: String? = null) {
        viewModelScope.launch {
            val title = getString(R.string.sleep_timer_island_title)
            val subtitle = if (isStarted) {
                getString(R.string.sleep_timer_island_started_subtitle, durationText ?: "")
            } else {
                getString(R.string.sleep_timer_island_finished_subtitle)
            }

            AchievementNotificationManager.showNotification(
                AchievementNotification(
                    title = title,
                    subtitle = subtitle,
                    iconEmoji = "🌙",
                    xpReward = null
                )
            )
        }
    }

    private fun restoreSession() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lastQueue = playerPrefs.getLastQueue()
                val lastTrack = playerPrefs.getLastTrack()
                val lastPosition = playerPrefs.getLastPosition()
                val lastContext = playerPrefs.getLastContext()
                val lastShuffle = playerPrefs.getLastShuffleEnabled()
                val lastRepeat = playerPrefs.getLastRepeatMode()
                withContext(Dispatchers.Main) {
                    if (lastQueue.isNotEmpty()) {
                        _queue.clear(); _queue.addAll(lastQueue); _originalQueue.clear(); _originalQueue.addAll(
                            lastQueue
                        ); updateQueueState()
                    }
                    if (lastTrack != null) {
                        shuffleEnabled = lastShuffle; repeatMode = lastRepeat; currentContext = lastContext
                        MusicManager.updateContext(lastContext)

                        currentTrack = lastTrack
                        MusicManager.currentTrack = lastTrack; isLiked =
                            LikeRepository.isTrackLiked(lastTrack.id); loadLyrics(lastTrack)
                        currentQueueIndex = _queue.indexOfFirst { it.id == lastTrack.id }
                        if (currentQueueIndex == -1) {
                            _queue.add(0, lastTrack); _originalQueue.add(
                                0,
                                lastTrack
                            ); updateQueueState(); currentQueueIndex = 0
                        }
                        val currentPlayerMediaId = MusicManager.player.currentMediaItem?.mediaId
                        if (currentPlayerMediaId == lastTrack.id.toString()) {
                            isPlaying = MusicManager.player.isPlaying; duration =
                                MusicManager.player.duration.coerceAtLeast(
                                    lastTrack.durationMs ?: 0L
                                ); currentPosition = MusicManager.player.currentPosition; MusicManager.applyEffects(
                                effectsState
                            )
                        } else {
                            currentPosition = lastPosition
                            duration = lastTrack.durationMs ?: 0L
                            if (currentQueueIndex >= 0) {
                                playRobustly(currentQueueIndex, autoPlay = false, startPosition = lastPosition)
                            }
                        }
                        delay(200.milliseconds)
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_FORCE_UPDATE
                        }
                        startServiceSafe(context, intent)
                    }
                }
            } catch (_: Exception) {
            } finally {
                isRestoringSession = false
            }
        }
    }

    fun syncWithCurrentPlayback() {
        viewModelScope.launch(Dispatchers.Main) {
            if (MusicManager.currentTrack != null) {
                currentTrack = MusicManager.currentTrack
                isPlaying = try {
                    MusicManager.player.isPlaying
                } catch (_: Exception) {
                    false
                }
                duration = MusicManager.player.duration.coerceAtLeast(0L)
                currentPosition = MusicManager.player.currentPosition
            }

            withContext(Dispatchers.IO) {
                val savedQueue = playerPrefs.getLastQueue()
                val savedContext = playerPrefs.getLastContext()

                withContext(Dispatchers.Main) {
                    if (savedQueue.isNotEmpty()) {
                        _queue.clear()
                        _queue.addAll(savedQueue)
                        _originalQueue.clear()
                        _originalQueue.addAll(savedQueue)
                        updateQueueState()

                        if (currentTrack != null) {
                            currentQueueIndex = _queue.indexOfFirst { it.id == currentTrack!!.id }.coerceAtLeast(0)
                        }
                    }
                    if (savedContext != null) {
                        currentContext = savedContext
                        MusicManager.updateContext(savedContext)
                    }
                }
            }
        }
    }

    private suspend fun loadBitmap(url: String): Bitmap? {
        return try {
            val loader = ImageLoader(context);
            val request = ImageRequest.Builder(context).data(url).allowHardware(false)
                .build(); (loader.execute(request) as? SuccessResult)?.drawable.let { (it as? BitmapDrawable)?.bitmap }
        } catch (_: Exception) {
            null
        }
    }

    private fun updatePlayerColors(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(track.fullResArtwork)
            if (bitmap != null) {
                try {
                    val artFile = File(context.filesDir, "art_${track.id}.jpg")
                    if (!artFile.exists()) {
                        artFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                Palette.from(bitmap).generate { palette ->
                    val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                    val isDarkMode = nightModeFlags == Configuration.UI_MODE_NIGHT_YES

                    val bestColor = if (isDarkMode) {
                        palette?.lightVibrantSwatch?.rgb
                            ?: palette?.lightMutedSwatch?.rgb
                            ?: palette?.vibrantSwatch?.rgb
                            ?: run {
                                val dom = palette?.dominantSwatch?.rgb ?: 0xFF1E1E1E.toInt()
                                if (isColorDark(dom)) 0xFF424242.toInt() else dom
                            }
                    } else {
                        palette?.darkVibrantSwatch?.rgb
                            ?: palette?.vibrantSwatch?.rgb
                            ?: palette?.dominantSwatch?.rgb
                            ?: 0xFF1E1E1E.toInt()
                    }

                    backgroundColor = Color(bestColor)
                }
            } else {
                backgroundColor = Color(0xFF1E1E1E)
            }
        }
    }

    private fun startServiceSafe(context: Context, intent: Intent) {
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun playRobustly(
        index: Int,
        autoPlay: Boolean = true,
        startPosition: Long = 0L,
        allowSkipOnFailure: Boolean = true,
        isCrossfade: Boolean = false
    ) {
        if (index !in _queue.indices) return

        val trackToPlay = _queue[index]

        playJob?.cancel()
        playJob = viewModelScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(trackToPlay.fullResArtwork)

            var resolvedUrl: String? = null
            var offlineKeySetId: ByteArray? = null

            try {
                val db = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(context).downloadDao()
                val localTrack = db.getTrack(trackToPlay.id)
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    if (localTrack.localAudioPath.startsWith("exo_cache://")) {
                        val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                        val cachedStreamUrl = parts.getOrNull(1)
                        val tokenStr = parts.getOrNull(2)

                        if (!cachedStreamUrl.isNullOrEmpty()) {
                            resolvedUrl = cachedStreamUrl
                            if (!tokenStr.isNullOrEmpty()) {
                                offlineKeySetId = android.util.Base64.decode(tokenStr, android.util.Base64.NO_WRAP)
                            }
                        }
                    } else {
                        val isContentUri = localTrack.localAudioPath.startsWith("content://")
                        val fileExists = if (isContentUri) true else File(localTrack.localAudioPath).exists()
                        if (fileExists) {
                            resolvedUrl = localTrack.localAudioPath
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (resolvedUrl == null) {
                val resolved = StreamResolver.resolveStreamWithDrm(context, trackToPlay)
                resolvedUrl = resolved?.url
                if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                    MusicManager.putDrmToken(trackToPlay.id, resolved.licenseAuthToken)
                    Log.d("PlayerViewModel", "DRM token pre-cached for track ${trackToPlay.id}")
                }
            }

            if (resolvedUrl == null) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    isPlaying = false
                    if (!com.alananasss.kittytune.utils.NetworkUtils.isInternetAvailable(context)) {
                        if (_queue.isEmpty()) {
                            resetPlaybackState(closePlayer = true)
                        } else if (allowSkipOnFailure && index < _queue.size - 1) {
                            playNext(manual = false, ignoreRepeatOne = true)
                        } else {
                            try {
                                MusicManager.player.stop()
                                MusicManager.player.clearMediaItems()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    } else {
                        if (allowSkipOnFailure) playNext(manual = false, ignoreRepeatOne = true)
                    }
                }
                return@launch
            }

            val newMediaItem = buildMediaItem(trackToPlay, bitmap, resolvedUrl, offlineKeySetId)

            withContext(Dispatchers.Main) {
                try {
                    queueChunkingJob?.cancel()

                    if (isCrossfade) {
                        val crossfadeDurationMs = playerPrefs.getCrossfadeDuration() * 1000L
                        MusicManager.crossfadeToMediaItem(newMediaItem, startPosition, crossfadeDurationMs)
                    } else {
                        MusicManager.player.setMediaItem(newMediaItem, startPosition)
                        MusicManager.player.prepare()
                        if (autoPlay) {
                            val intent = Intent(context, PlaybackService::class.java)
                            startServiceSafe(context, intent)
                            MusicManager.player.play()
                        }
                    }

                    MusicManager.applyEffects(effectsState)
                    preloadNextTrack(index + 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                    isLoading = false
                    isPlaying = false
                }
            }
        }
    }

    private fun preloadNextTrack(nextIndex: Int) {
        val targetIndex = if (nextIndex >= _queue.size) {
            if (repeatMode == RepeatMode.ALL && _queue.isNotEmpty()) 0 else return
        } else nextIndex

        val nextTrack = _queue[targetIndex]

        viewModelScope.launch(Dispatchers.IO) {
            try {
                var resolvedUrl: String? = null
                var offlineKeySetId: ByteArray? = null

                val db = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(context).downloadDao()
                val localTrack = db.getTrack(nextTrack.id)
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    if (localTrack.localAudioPath.startsWith("exo_cache://")) {
                        val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                        resolvedUrl = parts.getOrNull(1)
                        val tokenStr = parts.getOrNull(2)
                        if (!tokenStr.isNullOrEmpty()) offlineKeySetId =
                            android.util.Base64.decode(tokenStr, android.util.Base64.NO_WRAP)
                    } else {
                        resolvedUrl = localTrack.localAudioPath
                    }
                }

                if (resolvedUrl == null) {
                    val resolved = StreamResolver.resolveStreamWithDrm(context, nextTrack)
                    resolvedUrl = resolved?.url
                    if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                        MusicManager.putDrmToken(nextTrack.id, resolved.licenseAuthToken)
                    }
                }

                if (resolvedUrl != null) {
                    val nextMediaItem = buildMediaItem(nextTrack, null, resolvedUrl, offlineKeySetId)
                    withContext(Dispatchers.Main) {
                        if (MusicManager.player.mediaItemCount == 1) {
                            MusicManager.player.addMediaItem(nextMediaItem)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness =
            1 - (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color) + 0.114 * android.graphics.Color.blue(
                color
            )) / 255; return darkness >= 0.5
    }

    private fun buildMediaItem(
        track: Track,
        bitmap: Bitmap?,
        urlOverride: String? = null,
        offlineKeySetId: ByteArray? = null
    ): MediaItem {
        val uri = when {
            urlOverride == null -> "soundtune://track/${track.id}".toUri()
            urlOverride.startsWith("http") || urlOverride.startsWith("content://") || urlOverride.startsWith("file://") -> urlOverride.toUri()
            else -> Uri.fromFile(File(urlOverride))
        }

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title ?: getString(R.string.untitled_track))
            .setArtist(track.displayArtist.ifBlank { getString(R.string.unknown_artist) })
            .setArtworkUri(track.fullResArtwork.toUri())

        if (bitmap != null) {
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(metadataBuilder.build())

        if (urlOverride != null && urlOverride.contains(".m3u8")) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)
        }

        val drmToken = MusicManager.getDrmToken(track.id)
        if (offlineKeySetId != null || drmToken != null) {
            builder.setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8)

            val drmBuilder = MediaItem.DrmConfiguration.Builder(androidx.media3.common.C.WIDEVINE_UUID)
            if (offlineKeySetId != null) {
                drmBuilder.setKeySetId(offlineKeySetId)
            }

            builder.setDrmConfiguration(drmBuilder.build())
        }

        return builder.build()
    }
}

private fun incrementPlayCount() {
    val achievements = listOf(
        "plays_1", "plays_100", "plays_1000", "plays_5000",
        "plays_10000", "plays_20000", "plays_50000", "plays_100000"
    )
    achievements.forEach { AchievementManager.increment(it) }
}
