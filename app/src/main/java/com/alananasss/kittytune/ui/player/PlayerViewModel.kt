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
import android.os.Build
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.palette.graphics.Palette
import androidx.compose.runtime.mutableLongStateOf
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
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.*
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.annotation.StringRes

enum class CommentSort(val value: String, @StringRes val labelResId: Int) {
    NEWEST("newest", R.string.sort_newest),
    TIMESTAMP("track-timestamp", R.string.sort_timestamp),
    OLDEST("oldest", R.string.sort_oldest)
}

enum class LyricsMode { SYNCED, PLAIN }

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val gson = Gson()
    private val api = RetrofitClient.create(application)
    private val context = application.applicationContext
    private val playerPrefs = PlayerPreferences(context)
    private val tokenManager = TokenManager(context)

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    var currentUserId by mutableLongStateOf(0L)
    var currentUser by mutableStateOf<User?>(null)
    var currentTrack by mutableStateOf<Track?>(null)
    var isPlaying by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var duration by mutableLongStateOf(0L)
    var currentPosition by mutableLongStateOf(0L)
    var isScrubbing by mutableStateOf(false)
    var isPlayerExpanded by mutableStateOf(false)
    var isLiked by mutableStateOf(false)
    var backgroundColor by mutableStateOf(Color(0xFF1E1E1E))
    val hasLyrics by derivedStateOf { lyricsLines.isNotEmpty() || !rawPlainLyrics.isNullOrBlank() }
    var commentSort by mutableStateOf(CommentSort.NEWEST)

    var currentContext by mutableStateOf<PlaybackContext?>(null)

    val player: ExoPlayer
        get() {
            try {
                return MusicManager.player
            } catch (e: IllegalStateException) {
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

    var repostedTrackIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var showMenuSheet by mutableStateOf(false)
    var navigateToPlaylistId by mutableStateOf<String?>(null)
    var trackForMenu by mutableStateOf<Track?>(null)
    var menuContextPlaylistId by mutableStateOf<Long?>(null)
    var isMenuContextFromPlayer by mutableStateOf(false)

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

    var showAddToPlaylistSheet by mutableStateOf(false)
    var tracksToAddInBulk by mutableStateOf<List<Track>?>(null)
    val userPlaylists = mutableStateListOf<LocalPlaylist>()

    private val _originalQueue = mutableListOf<Track>()
    private val _queue = mutableListOf<Track>()
    val queue: List<Track> get() = _queue
    val queueState = mutableStateListOf<Track>()
    var currentQueueIndex by mutableIntStateOf(-1)

    var isPreciseLyricsSearchEnabled by mutableStateOf(playerPrefs.getPreciseLyricsSearchEnabled())
    var showLyricsSheet by mutableStateOf(false)
    var lyricsLines = mutableStateListOf<LyricLine>()
    var isLyricsLoading by mutableStateOf(false)
    var isSearchingLyrics by mutableStateOf(false)
    var manualSearchQuery by mutableStateOf("")
    val lyricSearchResults = mutableStateListOf<LrcLibResponse>()

    var lyricsFontSize by mutableFloatStateOf(playerPrefs.getLyricsFontSize())
    var lyricsAlignment by mutableStateOf(playerPrefs.getLyricsAlignment())
    var lyricsMode by mutableStateOf(LyricsMode.SYNCED)
    var rawPlainLyrics by mutableStateOf<String?>(null)
    var showInlineLyrics by mutableStateOf(false)
    var lyricsOffset by mutableLongStateOf(0L)
    var showLyricsOffsetControls by mutableStateOf(false)

    var currentSessionListenMs = 0L
    private var hasPushedRecentlyPlayed = false

    // Sleep Timer
    var sleepTimerRemainingMs by mutableLongStateOf(0L)
    var sleepTimerEndOfTrack by mutableStateOf(false)
    var showSleepTimerDialog by mutableStateOf(false)
    val isSleepTimerActive: Boolean get() = sleepTimerRemainingMs > 0L || sleepTimerEndOfTrack
    private var sleepTimerJob: Job? = null
    private var preFadeVolume: Float = 1f

    private var pendingSeekPosition: Long? = null
    private var saveQueueJob: Job? = null
    private var lyricsJob: Job? = null

    private val syncReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.alananasss.kittytune.ACTION_FORCE_UPDATE") {
                syncStateFromPreferences()
            }
        }
    }

    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)
    private fun getString(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)

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
        override fun onIsPlayingChanged(isPlayingState: Boolean) {
            isPlaying = isPlayingState
            saveStateAsync(saveQueue = false)
            if (isPlayingState) startProgressUpdate()
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
                AchievementManager.increment("no_skip_50")
                incrementPlayCount()

                // Record listening stats
                currentTrack?.let { track ->
                    if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                        if (repeatMode == RepeatMode.ONE) {
                            ListeningStatsRepository.recordEvent(track, "REPEAT_ONE_LOOP", currentSessionListenMs)
                        } else {
                            ListeningStatsRepository.recordEvent(track, "PLAY_COMPLETE", currentSessionListenMs)
                        }
                    }
                }

                // Reset session listen tracking after a complete play or loop
                currentSessionListenMs = 0L

                // Sleep timer: end of track mode
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
                    playNext(manual = false)
                }
            }
        }

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
            playNext(manual = false)
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

            val trackId = parseIdFromMediaId(mediaItem.mediaId)

            if (currentTrack?.id != trackId) {
                currentSessionListenMs = 0L // reset listen time on track change
                hasPushedRecentlyPlayed = false
            }

            if (MusicManager.currentTrack?.id == trackId) {
                currentTrack = MusicManager.currentTrack
            } else if (currentTrack?.id != trackId) {
                val meta = mediaItem.mediaMetadata
                val source = if (mediaItem.mediaId.startsWith("yt_") || mediaItem.requestMetadata.mediaUri?.toString()?.contains("youtube") == true) "youtube" else "soundcloud"

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

    init {
        val filter = IntentFilter("com.alananasss.kittytune.ACTION_FORCE_UPDATE")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(syncReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(syncReceiver, filter)
        }

        MusicManager.init(context)
        MusicManager.player.addListener(playerListener)
        MusicManager.applyEffects(effectsState)
        applyRepeatMode()


        MusicManager.onNextClick = { playNext(manual = true) }
        MusicManager.onPreviousClick = { smartPrevious() }

        MusicManager.onTrackChange = trackChangeHandler@{ newTrack ->
            // Sleep timer: end of track mode — stop here
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

            var finalTrack = newTrack

            val currentMediaItem = MusicManager.player.currentMediaItem
            if (currentMediaItem != null) {
                val realId = parseIdFromMediaId(currentMediaItem.mediaId)
                if (realId != newTrack.id) {
                    finalTrack = newTrack.copy(id = realId)
                }
            }

            currentTrack = finalTrack

            val playerIndex = MusicManager.player.currentMediaItemIndex

            if (playerIndex >= 0 && playerIndex < _queue.size) {
                currentQueueIndex = playerIndex

                val trackAtPosition = _queue[playerIndex]
                if (trackAtPosition.id == finalTrack.id) {
                    finalTrack = trackAtPosition
                }
            }
            updatePlayerColors(finalTrack)

            viewModelScope.launch {
                isLiked = LikeRepository.isTrackLiked(finalTrack.id)
                loadLyrics(finalTrack)
                AchievementManager.checkTrackNameSecret(finalTrack.title ?: "")

                if (finalTrack.id > 0 && (finalTrack.permalinkUrl.isNullOrEmpty() || finalTrack.user?.avatarUrl.isNullOrEmpty())) {
                    try {
                        val fullTracks = api.getTracksByIds(finalTrack.id.toString())
                        val fullTrack = fullTracks.firstOrNull()

                        if (fullTrack != null) {
                            currentTrack = fullTrack
                            MusicManager.currentTrack = fullTrack
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            try {
                isPlaying = MusicManager.player.isPlaying
                duration = MusicManager.player.duration.coerceAtLeast(0L)
                currentPosition = MusicManager.player.currentPosition
                if (isPlaying) startProgressUpdate()
            } catch (e: Exception) {
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
            DownloadManager.getAllPlaylistsFlow().collect { playlists ->
                userPlaylists.clear()
                val sorted = playlists.sortedWith(compareByDescending<LocalPlaylist> { it.isUserCreated || it.id < 0 }.thenByDescending { it.addedAt })
                userPlaylists.addAll(sorted)
            }
        }
        restoreSession()
        syncWithCurrentPlayback()
    }

    fun toggleInlineLyrics() {
        showInlineLyrics = !showInlineLyrics
    }


    override fun onCleared() {
        super.onCleared()
        context.unregisterReceiver(syncReceiver)
        try {
            MusicManager.player.removeListener(playerListener)
        } catch (e: IllegalStateException) {
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
                if(isPlaying) startProgressUpdate()
            } catch (e: Exception) {
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
                    delay(100)
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

    fun openLyrics(targetTrack: Track? = null, forceSheet: Boolean = false) {
        val target = targetTrack ?: currentTrack ?: return
        if (target.id != currentTrack?.id) playPlaylist(listOf(target), 0)

        if (!forceSheet && playerPrefs.getInlineLyricsEnabled()) {
            toggleInlineLyrics()
        } else {
            if (lyricsLines.isNotEmpty()) {
                lyricsMode = LyricsMode.SYNCED
            } else {
                lyricsMode = LyricsMode.PLAIN
            }
            showMenuSheet = false
            showLyricsSheet = true
        }
    }

    private fun loadLyrics(track: Track) {
        lyricsJob?.cancel()
        lyricsLines.clear()
        lyricsOffset = 0L
        showLyricsOffsetControls = false
        isLyricsLoading = true
        isSearchingLyrics = false
        rawPlainLyrics = null

        val originalTitle = track.title ?: ""
        val cleanTitle = cleanTitleNoise(originalTitle)
        val uploader = track.user?.username ?: ""

        val searchQueries = mutableListOf<String>()

        if (isPreciseLyricsSearchEnabled) {
            if (cleanTitle.contains(" - ")) {
                searchQueries.add(cleanTitle)
                searchQueries.add(cleanTitle.substringAfter(" - ").trim())
                searchQueries.add("$cleanTitle $uploader".trim())
            } else {
                searchQueries.add("$cleanTitle $uploader".trim())
                searchQueries.add(cleanTitle)
            }
        } else {
            searchQueries.add(cleanTitle)
        }

        manualSearchQuery = searchQueries.first()
        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            val preferLocal = playerPrefs.getLyricsPreferLocal()
            var localLyricsFound = false

            if (preferLocal) {
                val localTrack = DownloadManager.getLocalTrack(track.id)
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    val rawLyrics = LyricsUtils.extractLocalLyrics(localTrack.localAudioPath)
                    if (!rawLyrics.isNullOrBlank()) {
                        val parsed = LyricsUtils.parseLrc(rawLyrics, track.durationMs ?: 0L)
                        val finalLines = if (parsed.isNotEmpty()) parsed else listOf(LyricLine(rawLyrics, 0, track.durationMs ?: 0L))
                        withContext(Dispatchers.Main) {
                            lyricsLines.addAll(finalLines)
                            lyricsMode = LyricsMode.SYNCED
                            isLyricsLoading = false
                        }
                        localLyricsFound = true
                    }
                }
            }

            if (!localLyricsFound) {
                try {
                    val trackDurationSec = (track.durationMs ?: 0L) / 1000.0
                    var results: List<LrcLibResponse> = emptyList()

                    for (query in searchQueries) {
                        if (!isActive) return@launch

                        if (query.isBlank()) continue
                        results = LrcLibClient.api.searchLyrics(query)

                        if (results.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                manualSearchQuery = query
                            }
                            break
                        }
                    }

                    if (!isActive) return@launch

                    val bestMatch = results.filter { abs(it.duration - trackDurationSec) < 4.0 }.find { !it.syncedLyrics.isNullOrEmpty() } ?: results.firstOrNull()
                    processLyricsResponse(bestMatch, track.durationMs ?: 0L)
                } catch (e: Exception) {
                    if (e !is kotlinx.coroutines.CancellationException) {
                        withContext(Dispatchers.Main) { isLyricsLoading = false }
                    }
                }
            }
        }
    }

    fun adjustLyricsOffset(amount: Long) {
        lyricsOffset += amount
    }

    private suspend fun processLyricsResponse(response: LrcLibResponse?, trackDuration: Long) {
        val resultLines = when {
            response == null -> emptyList()
            !response.syncedLyrics.isNullOrEmpty() -> LyricsUtils.parseLrc(response.syncedLyrics, trackDuration)

            else -> emptyList()
        }

        withContext(Dispatchers.Main) {
            lyricsLines.clear()
            lyricsLines.addAll(resultLines)

            rawPlainLyrics = response?.plainLyrics ?: response?.syncedLyrics

            if (resultLines.isNotEmpty()) {
                lyricsMode = LyricsMode.SYNCED
            } else {
                lyricsMode = LyricsMode.PLAIN
            }

            isLyricsLoading = false
            if (resultLines.isNotEmpty() || !rawPlainLyrics.isNullOrBlank()) isSearchingLyrics = false
        }
    }

    fun searchLyricsManual(query: String) { if (query.isBlank()) return; isLyricsLoading = true; lyricSearchResults.clear(); viewModelScope.launch(Dispatchers.IO) { try { val results = LrcLibClient.api.searchLyrics(query); withContext(Dispatchers.Main) { lyricSearchResults.addAll(results) } } catch (e: Exception) { e.printStackTrace() } finally { withContext(Dispatchers.Main) { isLyricsLoading = false } } } }
    fun selectLyricResult(result: LrcLibResponse) { viewModelScope.launch(Dispatchers.IO) { processLyricsResponse(result, duration) } }
    private fun cleanTitleNoise(title: String): String = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").replace(Regex("(?i)(official video|lyrics|ft\\.|feat\\.|prod\\.)"), "").trim()

    fun navigateToTrackDetails(trackId: Long, initialTab: Int = 0) { showMenuSheet = false; showDetailsSheet = false; navigateToPlaylistId = "track_detail:$trackId?tab=$initialTab" }

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

        if (target.id < 0) {
            activateLocalDetailsMode(target)
            return
        }

        viewModelScope.launch {
            val localTrack = DownloadManager.getLocalTrack(target.id)
            val isDownloaded = localTrack != null && localTrack.localAudioPath.isNotEmpty()

            if (isDownloaded) {
                activateLocalDetailsMode(target)
            } else {
                var isContextLocal = false
                if (menuContextPlaylistId != null) {
                    if (menuContextPlaylistId == -2L || menuContextPlaylistId!! < 0) {
                        isContextLocal = true
                    }
                } else if (target.id == currentTrack?.id) {
                    val navId = currentContext?.navigationId
                    if (navId == "downloads" || navId?.startsWith("local_playlist:") == true) {
                        isContextLocal = true
                    }
                }

                if (isContextLocal) {
                    activateLocalDetailsMode(target)
                } else {
                    isLocalDetailsMode = false
                    localFilePathForDetails = null
                    showMenuSheet = false
                    showDetailsSheet = true
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
            } catch (e: Exception) {
                emitUiEvent(getString(R.string.error_generic))
            }
        }
    }

    fun navigateToTag(tagName: String) { showDetailsSheet = false; isPlayerExpanded = false; navigateToPlaylistId = "tag:$tagName" }

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
            }
            else if (destination.startsWith("playlist_")) {
                destination = destination.removePrefix("playlist_")
            }
            navigateToPlaylistId = destination
        }
    }

    fun onNavigationHandled() { navigateToPlaylistId = null }

    fun loadComments(refresh: Boolean = false, specificTrack: Track? = null) {
        val t = specificTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        if (refresh) { commentsList.clear(); commentNextHref = null }
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
            val query = "query UserInteractions(\$parentUrn: String!, \$interactionTypeUrn: String!, \$targetUrns: [String!]!) { user: userInteractions(parentUrn: \$parentUrn, interactionTypeUrn: \$interactionTypeUrn, targetUrns: \$targetUrns) { targetUrn, userInteraction, interactionCounts { count, interactionTypeValueUrn } } }"
            val variables = GraphQlVariablesUserCheck(parentUrn = parentUrn, targetUrns = batchUrns)
            val request = GraphQlRequest("UserInteractions", query, variables)
            try {
                val responseJson = api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                val data = gson.fromJson(responseJson, GraphQlResponseUserInteractions::class.java)
                data.data?.user?.forEach { interaction ->
                    val idStr = interaction.targetUrn.substringAfterLast(":")
                    val commentId = idStr.toLongOrNull() ?: 0L
                    val isLikedByMe = interaction.userInteraction != null
                    val totalLikes = interaction.interactionCounts?.find { it.type == "sc:interactiontypevalue:like" }?.count
                    updateCommentInList(commentId, isLikedByMe, totalLikes)
                }
            } catch (e: Exception) { e.printStackTrace() }
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

    fun startReplying(comment: Comment) { replyingToComment = comment }
    fun cancelReplying() { replyingToComment = null }

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
            } finally { isPostingComment = false }
        }
    }

    fun onCaptchaSolved() {
        captchaUrl = null; SessionManager.reloadSession()
        if (pendingCommentBody != null) { emitUiEvent(getString(R.string.msg_retrying)); postComment(pendingCommentBody!!, pendingCommentTimestamp) }
    }

    fun toggleCommentLike(comment: Comment) {
        var foundIndex = -1; var parentIndex = -1
        foundIndex = commentsList.indexOfFirst { it.id == comment.id }
        if (foundIndex == -1) { for (i in commentsList.indices) { if (commentsList[i].replies?.any { it.id == comment.id } == true) { parentIndex = i; break } } }
        if (foundIndex == -1 && parentIndex == -1) return
        val isCurrentlyLiked = comment.isLiked
        val newLikedState = !isCurrentlyLiked
        val newCount = if (newLikedState) comment.likesCount + 1 else (comment.likesCount - 1).coerceAtLeast(0)
        if (foundIndex != -1) commentsList[foundIndex] = comment.copy(isLiked = newLikedState, likesCount = newCount)
        else if (parentIndex != -1) {
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
                    val query = "mutation UpsertInteraction(\$input: InteractionInput!) { upsertInteraction(input: \$input) { interactionTypeUrn } }"
                    val request = GraphQlRequest("UpsertInteraction", query, GraphQlVariablesInteraction(input))
                    api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                } else {
                    val query = "mutation RemoveInteraction(\$input: InteractionInput!) { removeInteraction(input: \$input) }"
                    val request = GraphQlRequest("RemoveInteraction", query, GraphQlVariablesInteraction(input))
                    api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                }
            } catch (e: Exception) { e.printStackTrace(); emitUiEvent(getString(R.string.error_generic)) }
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
            } catch (e: Exception) { e.printStackTrace(); emitUiEvent(getString(R.string.error_generic)) }
        }
    }

    fun fetchUserProfile() {
        if (tokenManager.isGuestMode() || tokenManager.getAccessToken().isNullOrEmpty()) return

        viewModelScope.launch {
            try {
                val me = api.getMe()
                currentUserId = me.id
                currentUser = me
            } catch (_: Exception) {}
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

    fun playPlaylist(tracks: List<Track>, startIndex: Int = 0, context: PlaybackContext? = null, maintainPlayerState: Boolean = false) {
        if (tracks.isEmpty()) return
        if (!maintainPlayerState) {
            isPlayerExpanded = false
        }
        _originalQueue.clear(); _originalQueue.addAll(tracks)
        _queue.clear()
        this.currentContext = context
        MusicManager.updateContext(context)

        val effectiveStartIndex = if (startIndex in tracks.indices) startIndex else 0

        if (shuffleEnabled) {
            val clickedTrack = tracks[effectiveStartIndex]
            val rest =
                tracks.filterIndexed { index, _ -> index != effectiveStartIndex }.shuffled()
            _queue.add(clickedTrack)
            _queue.addAll(rest)
            playTrackAtIndex(0, addToHistory = (context == null))
        } else {
            _queue.addAll(tracks)
            playTrackAtIndex(effectiveStartIndex, addToHistory = (context == null))
        }

        updateQueueState(); saveStateAsync(saveQueue = true)

        if (context != null) {
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

    fun playTrackAtPosition(track: Track, position: Long) { pendingSeekPosition = position; playPlaylist(listOf(track), 0); showCommentsSheet = false; isPlayerExpanded = true }
    fun skipToQueueItem(index: Int) { playTrackAtIndex(index, addToHistory = false); AchievementManager.trackSkipped(); AchievementManager.increment("skipper_100"); AchievementManager.increment("skipper_1000") }

    private fun playTrackAtIndex(index: Int, addToHistory: Boolean = true) {
        if (index < 0 || index >= _queue.size) { currentContext = null; return }
        currentQueueIndex = index
        val trackToPlay = _queue[index]
        isLoading = true; duration = trackToPlay.durationMs ?: 0L; currentPosition = 0L
        currentSessionListenMs = 0L
        hasPushedRecentlyPlayed = false
        currentTrack = trackToPlay; MusicManager.currentTrack = trackToPlay
        val intent = Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_FORCE_UPDATE }
        startServiceSafe(context, intent)

        viewModelScope.launch {
            var finalTrack = trackToPlay
            if (finalTrack.source == "soundcloud" && trackToPlay.id > 0 && (trackToPlay.user?.id == 0L || trackToPlay.media == null)) {
                try {
                    val fullTrackList = api.getTracksByIds(trackToPlay.id.toString())
                    if (fullTrackList.isNotEmpty()) { finalTrack = fullTrackList[0]; _queue[index] = finalTrack }
                } catch (e: Exception) { e.printStackTrace() }
            }
            currentTrack = finalTrack
            MusicManager.currentTrack = finalTrack
            isLiked = LikeRepository.isTrackLiked(finalTrack.id)
            loadLyrics(finalTrack)
            AchievementManager.checkTrackNameSecret(finalTrack.title ?: "")
            saveStateAsync(saveQueue = false)

            playRobustly(index, autoPlay = true)

            if (addToHistory && currentContext?.navigationId?.startsWith("station:") != true && currentContext?.navigationId?.startsWith("yt_radio:") != true) {
                HistoryRepository.addToHistory(finalTrack)
            }
        }
    }

    fun playNext(manual: Boolean = true) {
        if (isAutoplayRadioLoading) return

        if (manual && player.currentPosition > 2000) {
            incrementPlayCount()
        }

        // Record skip stats
        if (manual) {
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "SKIP_NEXT", currentSessionListenMs)
                }
            }
        }

        val nextIndex = currentQueueIndex + 1

        if (manual) {
            AchievementManager.trackSkipped()
            AchievementManager.increment("skipper_100")
            AchievementManager.increment("skipper_1000")
        }

        if (nextIndex < _queue.size) {
            playTrackAtIndex(nextIndex, addToHistory = false)
        } else {
            if (repeatMode == RepeatMode.ALL) {
                playTrackAtIndex(0, addToHistory = false)
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
                            playTrackAtIndex(newNextIndex, addToHistory = false)
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
                    durationMs = (it.duration ?: 0) * 1000,
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
                    val ctx = PlaybackContext(getString(R.string.context_station, lastTrack.title ?: ""), "station:${lastTrack.id}", lastTrack.fullResArtwork)
                    currentContext = ctx
                    MusicManager.updateContext(ctx)
                }
            }
        } catch (e: Exception) { e.printStackTrace() } finally { isAutoplayRadioLoading = false }
    }

    fun smartPrevious() {
        if (player.currentPosition > 2000) {
            incrementPlayCount()
        }

        if (player.currentPosition > 3000) {
            // User is restarting the same track — this is a manual replay
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "MANUAL_REPLAY", currentSessionListenMs)
                }
            }
            currentSessionListenMs = 0L
            currentPosition = 0L
            player.seekTo(0)
        } else {
            // User is going to previous track
            currentTrack?.let { track ->
                if (playerPrefs.getListeningStatsEnabled() && currentSessionListenMs > 0) {
                    ListeningStatsRepository.recordEvent(track, "SKIP_PREVIOUS", currentSessionListenMs)
                }
            }
            val realIndex = player.currentMediaItemIndex

            val activeIndex = if (realIndex >= 0 && realIndex < _queue.size) realIndex else currentQueueIndex

            val prev = activeIndex - 1
            if (prev >= 0) {
                playTrackAtIndex(prev, addToHistory = false)
            } else {
                currentPosition = 0L
                player.seekTo(0)
            }
        }
    }
    fun toggleShuffle() { shuffleEnabled = !shuffleEnabled; if (shuffleEnabled) applyShuffle() else revertShuffle(); updateQueueState(); saveStateAsync(saveQueue = true) }

    private fun applyShuffle(startIndex: Int = currentQueueIndex, sourceList: List<Track> = _originalQueue) {
        if (sourceList.isEmpty() || startIndex < 0 || startIndex >= sourceList.size) return

        val played = if (startIndex < sourceList.size) sourceList.subList(0, startIndex + 1) else emptyList()
        val upcoming = if (startIndex + 1 < sourceList.size) sourceList.subList(startIndex + 1, sourceList.size) else emptyList()

        val shuffledUpcoming = upcoming.shuffled()

        _queue.clear()
        _queue.addAll(played)
        _queue.addAll(shuffledUpcoming)
    }

    private fun revertShuffle() { val currentTrackId = currentTrack?.id ?: return; _queue.clear(); _queue.addAll(_originalQueue); currentQueueIndex = _queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0) }

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
        viewModelScope.launch(Dispatchers.Main) {
            val player = MusicManager.player
            val count = player.mediaItemCount
            if (count == 0) return@launch

            val newQueue = mutableListOf<Track>()
            for (i in 0 until count) {
                val item = player.getMediaItemAt(i)
                val meta = item.mediaMetadata

                val id = parseIdFromMediaId(item.mediaId)

                val track = Track(
                    id = id,
                    title = meta.title?.toString(),
                    user = User(0, meta.artist?.toString() ?: "Unknown", null),
                    artworkUrl = meta.artworkUri?.toString(),
                    durationMs = 0L
                )
                newQueue.add(track)
            }

            if (_queue.size != newQueue.size || _queue.firstOrNull()?.id != newQueue.firstOrNull()?.id) {
                _queue.clear()
                _queue.addAll(newQueue)
                _originalQueue.clear()
                _originalQueue.addAll(newQueue)
                updateQueueState()
            }

            val playerIndex = player.currentMediaItemIndex
            if (playerIndex in 0 until _queue.size) {
                currentQueueIndex = playerIndex
                val trackFromQueue = _queue[playerIndex]
                currentTrack = trackFromQueue
                MusicManager.currentTrack = trackFromQueue
            }

            saveStateAsync(saveQueue = true)
        }
    }

    fun updateQueueState() { queueState.clear(); queueState.addAll(_queue) }

    fun moveQueueItem(from: Int, to: Int) {
        if (from == to) return

        if (from < queueState.size && to < queueState.size) {
            val item = queueState.removeAt(from)
            queueState.add(to, item)
        }

        if (from < _queue.size && to < _queue.size) {
            val item = _queue.removeAt(from)
            _queue.add(to, item)

            try {
                MusicManager.player.moveMediaItem(from, to)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        if (!shuffleEnabled && from < _originalQueue.size && to < _originalQueue.size + 1) {
            val originalItem = _originalQueue.removeAt(from)
            _originalQueue.add(to, originalItem)
        }

        if (currentTrack != null) {
            currentQueueIndex = _queue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
        }

        saveStateAsync(saveQueue = true)
    }

    fun removeTrackFromQueue(index: Int) {
        if (index !in 0 until _queue.size) return

        val trackToRemove = _queue[index]

        // 1. Remove from _queue
        _queue.removeAt(index)

        // 2. Remove from queueState
        if (index < queueState.size) {
            queueState.removeAt(index)
        }

        // 3. Remove from _originalQueue if present (matching by id)
        _originalQueue.removeAll { it.id == trackToRemove.id }

        // 4. Remove from Player
        try {
            MusicManager.player.removeMediaItem(index)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // 5. Update index if current track was moved
        if (currentTrack != null) {
            currentQueueIndex = _queue.indexOfFirst { it.id == currentTrack?.id }.coerceAtLeast(0)
        }

        saveStateAsync(saveQueue = true)
    }

    fun insertNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        val insertIndex = currentQueueIndex + 1

        val uniqueTracks = tracks.map { it.copy() }

        val mediaItems = uniqueTracks.map { track ->
            buildMediaItem(track, null, null)
        }
        player.addMediaItems(insertIndex, mediaItems)

        _queue.addAll(insertIndex, uniqueTracks)
        _originalQueue.addAll(insertIndex, uniqueTracks)
        updateQueueState()
        saveStateAsync(saveQueue = true)
        emitUiEvent(getString(R.string.menu_play_next))
    }
    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
        } else {
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
        saveStateAsync(saveQueue = false)
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

    fun togglePreciseSpeedEnabled(enabled: Boolean) { isPreciseSpeedEnabled = enabled; playerPrefs.setPreciseSpeedEnabled(enabled) }
    fun toggleRain() { val n = !effectsState.isRainEnabled; effectsState = effectsState.copy(isRainEnabled = n); applyEffectsAndSave() }
    fun setRainVolume(volume: Float) { effectsState = effectsState.copy(rainVolume = volume); MusicManager.applyEffects(effectsState); viewModelScope.launch(Dispatchers.IO) { playerPrefs.saveEffects(effectsState) } }
    fun setCustomSpeed(speed: Float) { val factor = if (isPreciseSpeedEnabled) 20f else 10f; val r = (speed * factor).roundToInt() / factor; effectsState = effectsState.copy(speed = r); applyEffectsAndSave() }
    fun togglePitchEnabled(e: Boolean) { effectsState = effectsState.copy(isPitchEnabled = e); applyEffectsAndSave() }
    fun toggle8D() { effectsState = effectsState.copy(is8DEnabled = !effectsState.is8DEnabled); applyEffectsAndSave() }
    fun setEightDSpeed(v: Float) { effectsState = effectsState.copy(eightDSpeed = v); applyEffectsAndSave() }
    fun toggleMuffled() { val n = !effectsState.isMuffledEnabled; effectsState = effectsState.copy(isMuffledEnabled = n); applyEffectsAndSave() }
    fun setMuffledIntensity(v: Float) { effectsState = effectsState.copy(muffledIntensity = v); applyEffectsAndSave() }
    fun toggleBassBoost() { val n = !effectsState.isBassBoostEnabled; effectsState = effectsState.copy(isBassBoostEnabled = n); applyEffectsAndSave(); if (n) AchievementManager.increment("bass_addict", 1) }
    fun setBassBoostIntensity(v: Float) { effectsState = effectsState.copy(bassBoostIntensity = v); applyEffectsAndSave() }
    fun toggleReverb() { effectsState = effectsState.copy(isReverbEnabled = !effectsState.isReverbEnabled); applyEffectsAndSave() }
    fun setReverbIntensity(v: Float) { effectsState = effectsState.copy(reverbIntensity = v); applyEffectsAndSave() }
    fun toggleEarrape() { val n = !effectsState.isEarrapeEnabled; effectsState = effectsState.copy(isEarrapeEnabled = n); applyEffectsAndSave(); if (n) AchievementManager.increment("bass_addict", 1) }

    fun hasSeenEarrapeWarning(): Boolean = playerPrefs.hasSeenEarrapeWarning()
    fun setHasSeenEarrapeWarning(seen: Boolean) { playerPrefs.setHasSeenEarrapeWarning(seen) }

    private fun applyEffectsAndSave() { MusicManager.applyEffects(effectsState); viewModelScope.launch(Dispatchers.IO) { playerPrefs.saveEffects(effectsState) } }

    fun showTrackOptions(track: Track, playlistContextId: Long? = null, fromPlayer: Boolean = false) { trackForMenu = track; menuContextPlaylistId = playlistContextId; isMenuContextFromPlayer = fromPlayer; showMenuSheet = true }
    fun prepareBulkAdd(tracks: List<Track>) { tracksToAddInBulk = tracks; trackForMenu = null; showAddToPlaylistSheet = true }
    fun addToPlaylist(playlistId: Long, track: Track) { DownloadManager.addTrackToPlaylist(playlistId, track); showAddToPlaylistSheet = false; emitUiEvent(getString(R.string.success_generic)) }
    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) {
        DownloadManager.addTracksToPlaylistBulk(playlistId, tracks)
        viewModelScope.launch {
            showAddToPlaylistSheet = false
            emitUiEvent(getString(R.string.success_generic))
            AchievementManager.increment("playlist_creator")
            AchievementManager.increment("playlist_god")
        }
    }    fun createAndAddToPlaylist(name: String, track: Track) { val id = DownloadManager.createUserPlaylist(name); DownloadManager.addTrackToPlaylist(id, track); showAddToPlaylistSheet = false; emitUiEvent(getString(R.string.success_generic)); AchievementManager.increment("playlist_creator") }
    fun createAndAddTracksToPlaylist(name: String, tracks: List<Track>) { viewModelScope.launch(Dispatchers.IO) { val id = DownloadManager.createUserPlaylist(name); tracks.forEach { DownloadManager.addTrackToPlaylist(id, it) }; withContext(Dispatchers.Main) { showAddToPlaylistSheet = false; emitUiEvent(getString(R.string.success_generic)); AchievementManager.increment("playlist_creator") } } }
    fun removeFromContextPlaylist(playlistId: Long, track: Track) { DownloadManager.removeTrackFromPlaylist(playlistId, track.id); showMenuSheet = false; emitUiEvent(getString(R.string.success_generic)) }
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
    fun downloadTrack(track: Track) { if (DownloadManager.isTrackDownloading(track.id)) return; DownloadManager.downloadTrack(track); AchievementManager.increment("download_1000") }

    private fun emitUiEvent(msg: String) { viewModelScope.launch { _uiEvent.emit(msg) } }
    private fun saveStateAsync(saveQueue: Boolean = false) {
        val t = currentTrack
        val p = MusicManager.player.currentPosition
        val c = currentContext
        val s = shuffleEnabled
        val r = repeatMode
        if (saveQueue) {
            saveQueueJob?.cancel()
            saveQueueJob = viewModelScope.launch(Dispatchers.IO) {
                delay(500)
                val qSnapshot = _queue.toList()
                playerPrefs.savePlaybackState(t, p, qSnapshot, c, s, r)
            }
        } else {
            viewModelScope.launch(Dispatchers.IO) {
                val q = _queue.toList()
                playerPrefs.savePlaybackState(t, p, q, c, s, r)
            }
        }
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            val tokenManager = TokenManager(context)
            val isGuest = tokenManager.isGuestMode()
            while (isActive && isPlaying) {
                try {
                    if (!isScrubbing) {
                        currentPosition = MusicManager.player.currentPosition.coerceAtLeast(0L)
                        currentSessionListenMs += 1000L
                        Log.d("PlayerViewModel", "Listen MS: $currentSessionListenMs")
                        
                        if (currentSessionListenMs >= 30_000L && !hasPushedRecentlyPlayed) {
                            Log.d("PlayerViewModel", "Threshold reached, pushing history for track ${currentTrack?.id}")
                            hasPushedRecentlyPlayed = true
                            pushRecentlyPlayedToSoundCloud(currentTrack)
                        }
                    }
                    AchievementManager.addPlayTime(1, isGuest, effectsState.speed)
                    if (effectsState.isBassBoostEnabled || effectsState.isEarrapeEnabled) AchievementManager.increment("bass_addict", 1)

                } catch (e: Exception) {
                }
                delay(1000)
            }
        }
    }

    private fun pushRecentlyPlayedToSoundCloud(track: Track?) {
        Log.d("PlayerViewModel", "pushRecentlyPlayedToSoundCloud called for track ${track?.id}, source: ${track?.source}")
        track ?: return
        if (track.source != null && track.source != "soundcloud") {
            Log.d("PlayerViewModel", "Ignoring track because source is not soundcloud: ${track.source}")
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val tokenManager = TokenManager(context)
                if (tokenManager.isGuestMode()) {
                    Log.d("PlayerViewModel", "Skipping history sync for track ${track.id} (Guest Mode)")
                    return@launch
                }

                val now = System.currentTimeMillis()

                // Sync individual track history
                val trackUrn = "soundcloud:tracks:${track.id}"
                
                // 1) api-mobile endpoint (used by the Android app)
                val trackEntry = com.alananasss.kittytune.data.network.ApiRecentlyPlayed(
                    playedAt = now,
                    urn = trackUrn
                )
                val trackCollection = com.alananasss.kittytune.data.network.ApiCollection(
                    collection = listOf(trackEntry)
                )
                val trackResponse = api.pushPlayHistory(trackCollection)
                if (trackResponse.isSuccessful) {
                    Log.d("PlayerViewModel", "Synced track ${track.id} to mobile history")
                } else {
                    Log.w("PlayerViewModel", "Failed to sync mobile history: ${trackResponse.code()}")
                }

                // 2) api-v2 endpoint (used by the Web app)
                val bodyV2 = com.google.gson.JsonObject().apply { addProperty("track_urn", trackUrn) }
                val respV2 = api.pushPlayHistoryV2Me(bodyV2)
                if (respV2.isSuccessful) {
                    Log.d("PlayerViewModel", "Synced track ${track.id} to web history")
                } else {
                    Log.w("PlayerViewModel", "Failed to sync web history: ${respV2.code()}")
                }

            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Error syncing history", e)
            }
        }
    }

    fun updateScrubPosition(position: Long) {
        isScrubbing = true
        currentPosition = position
    }

    // ─── Sleep Timer ────────────────────────────────────────────

    fun startSleepTimer(durationMs: Long) {
        cancelSleepTimer()

        val isFadeEnabled = playerPrefs.getSleepTimerFadeEnabled()
        val fadeDurationSec = if (isFadeEnabled) playerPrefs.getSleepTimerFadeDuration() else 0
        val fadeDurationMs = fadeDurationSec * 1000L
        val totalDurationMs = durationMs

        // Save current volume before any fade
        preFadeVolume = player.volume

        val startTime = System.currentTimeMillis()
        val endTime = startTime + totalDurationMs
        sleepTimerEndOfTrack = false

        showSleepTimerDialog = false
        showSleepTimerIslandNotification(isStarted = true, durationText = formatRemaining(totalDurationMs))
        emitUiEvent(getString(R.string.sleep_timer_started))

        sleepTimerJob = viewModelScope.launch(Dispatchers.Main) {
            while (isActive) {
                val now = System.currentTimeMillis()
                val remaining = endTime - now

                if (remaining <= 0) {
                    // Timer elapsed: smooth cut to 0 then pause
                    player.volume = 0f
                    player.pause()
                    // Restore original volume (playback is paused = no sound)
                    player.volume = preFadeVolume
                    sleepTimerRemainingMs = 0L
                    showSleepTimerIslandNotification(isStarted = false)
                    break
                }

                // Progressive fade-out zone
                if (fadeDurationMs > 0L && remaining <= fadeDurationMs) {
                    val fraction = (remaining.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
                    // Quadratic curve: perceived volume decreases naturally
                    val volumeFraction = fraction * fraction
                    player.volume = (preFadeVolume * volumeFraction).coerceIn(0f, 1f)
                }

                sleepTimerRemainingMs = remaining
                delay(PlayerPreferences.SLEEP_TIMER_FADE_UPDATE_INTERVAL_MS)
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
        // Restore original volume if a fade was in progress
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
                    if (lastQueue.isNotEmpty()) { _queue.clear(); _queue.addAll(lastQueue); _originalQueue.clear(); _originalQueue.addAll(lastQueue); updateQueueState() }
                    if (lastTrack != null) {
                        shuffleEnabled = lastShuffle; repeatMode = lastRepeat; currentContext = lastContext
                        MusicManager.updateContext(lastContext)

                        currentTrack = lastTrack
                        MusicManager.currentTrack = lastTrack; isLiked = LikeRepository.isTrackLiked(lastTrack.id); loadLyrics(lastTrack)
                        currentQueueIndex = _queue.indexOfFirst { it.id == lastTrack.id }
                        if (currentQueueIndex == -1) { _queue.add(0, lastTrack); _originalQueue.add(0, lastTrack); updateQueueState(); currentQueueIndex = 0 }
                        val currentPlayerMediaId = MusicManager.player.currentMediaItem?.mediaId
                        if (currentPlayerMediaId == lastTrack.id.toString()) {
                            isPlaying = MusicManager.player.isPlaying; duration = MusicManager.player.duration.coerceAtLeast(lastTrack.durationMs ?: 0L); currentPosition = MusicManager.player.currentPosition; MusicManager.applyEffects(effectsState)
                        } else {
                            currentPosition = lastPosition; duration = lastTrack.durationMs ?: 0L;
                            if (currentQueueIndex >= 0) {
                                playRobustly(currentQueueIndex, autoPlay = false, startPosition = lastPosition)
                            }
                        }
                        delay(200); val intent = Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_FORCE_UPDATE }; startServiceSafe(context, intent)
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun syncWithCurrentPlayback() {
        viewModelScope.launch(Dispatchers.Main) {
            if (MusicManager.currentTrack != null) {
                currentTrack = MusicManager.currentTrack
                isPlaying = try { MusicManager.player.isPlaying } catch (e: Exception) { false }
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

    private suspend fun loadBitmap(url: String): Bitmap? { return try { val loader = ImageLoader(context); val request = ImageRequest.Builder(context).data(url).allowHardware(false).build(); (loader.execute(request) as? SuccessResult)?.drawable.let { (it as? BitmapDrawable)?.bitmap } } catch (e: Exception) { null } }

    private fun updatePlayerColors(track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(track.fullResArtwork)
            if (bitmap != null) {
                try {
                    val artFile = File(context.filesDir, "art_${track.id}.jpg")
                    if (!artFile.exists()) {
                        artFile.outputStream().use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out) }
                    }
                } catch (e: Exception) { e.printStackTrace() }

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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun playRobustly(index: Int, autoPlay: Boolean = true, startPosition: Long = 0L, allowSkipOnFailure: Boolean = true) {
        if (index !in _queue.indices) return

        val trackToPlay = _queue[index]

        viewModelScope.launch(Dispatchers.IO) {
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
                        val fileExists = if (isContentUri) true else java.io.File(localTrack.localAudioPath).exists()
                        if (fileExists) {
                            resolvedUrl = localTrack.localAudioPath
                        }
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }

            if (resolvedUrl == null) {
                // Not downloaded, resolve from network
                val resolved = com.alananasss.kittytune.data.StreamResolver.resolveStreamWithDrm(context, trackToPlay)
                resolvedUrl = resolved?.url

                // Cache DRM token if present (streaming JWT)
                if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                    MusicManager.putDrmToken(trackToPlay.id, resolved.licenseAuthToken)
                    Log.d("PlayerViewModel", "DRM token pre-cached for track ${trackToPlay.id}")
                }
            }

            withContext(Dispatchers.Main) {
                try {
                    val mediaItems = _queue.map { track ->
                        if (track.id == trackToPlay.id) {
                            buildMediaItem(track, bitmap, resolvedUrl, offlineKeySetId)
                        } else {
                            buildMediaItem(track, null, null, null)
                        }
                    }

                    MusicManager.player.setMediaItems(mediaItems, index, startPosition)
                    MusicManager.player.prepare()

                    if (autoPlay) {
                        val intent = Intent(context, PlaybackService::class.java)
                        startServiceSafe(context, intent)
                        MusicManager.player.play()
                    }

                    MusicManager.applyEffects(effectsState)

                } catch (e: Exception) {
                    e.printStackTrace()
                    isLoading = false
                    isPlaying = false
                }
            }
        }
    }

    private fun preparePlayer(mediaItem: MediaItem, startPos: Long, autoPlay: Boolean) {
        try {
            performPrepare(mediaItem, startPos, autoPlay)
        } catch (e: Exception) {
            if (e is IllegalStateException || e.message?.contains("dead thread", ignoreCase = true) == true) {
                Log.e("PlayerViewModel", "zombie player detected! resurrecting...", e)
                try {
                    MusicManager.releasePlayer()
                    MusicManager.init(context)
                    MusicManager.player.addListener(playerListener)
                    performPrepare(mediaItem, startPos, autoPlay)
                } catch (retryEx: Exception) {
                    retryEx.printStackTrace()
                }
            } else {
                e.printStackTrace()
            }
        }
    }

    private fun performPrepare(mediaItem: MediaItem, startPos: Long, autoPlay: Boolean) {
        MusicManager.player.setMediaItem(mediaItem)
        MusicManager.player.prepare()
        if (startPos > 0) MusicManager.player.seekTo(startPos)
        if (autoPlay) {
            val intent = Intent(context, PlaybackService::class.java)
            startServiceSafe(context, intent)
            MusicManager.player.play()
        }
        MusicManager.applyEffects(effectsState)
    }

    private fun isColorDark(color: Int): Boolean { val darkness = 1 - (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color) + 0.114 * android.graphics.Color.blue(color)) / 255; return darkness >= 0.5 }

    private fun buildMediaItem(track: Track, bitmap: Bitmap?, urlOverride: String? = null, offlineKeySetId: ByteArray? = null): MediaItem {
        val uri = if (urlOverride != null) Uri.parse(urlOverride) else Uri.parse("soundtune://track/${track.id}")

        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title ?: getString(R.string.untitled_track))
            .setArtist(track.user?.username ?: getString(R.string.unknown_artist))
            .setArtworkUri(Uri.parse(track.fullResArtwork))

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

        // Configure Widevine DRM if a license token is cached for this track
        val drmToken = MusicManager.getDrmToken(track.id)
        if (offlineKeySetId != null || drmToken != null) {
            // CENC tracks are always HLS — force HLS MIME type so DefaultMediaSourceFactory
            // creates an HlsMediaSource instead of ProgressiveMediaSource
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
