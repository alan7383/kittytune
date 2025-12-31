package com.alananasss.kittytune.ui.player

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.*
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.LrcLibClient
import com.alananasss.kittytune.data.network.LrcLibResponse
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.*
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.alananasss.kittytune.utils.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val api = RetrofitClient.create(application)
    private val context = application.applicationContext
    private val playerPrefs = PlayerPreferences(context)

    private val _uiEvent = MutableSharedFlow<String>()
    val uiEvent = _uiEvent.asSharedFlow()

    // player ui states
    var currentUserId: Long = 0L
    var currentTrack by mutableStateOf<Track?>(null)
    var isPlaying by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var duration by mutableLongStateOf(0L)
    var currentPosition by mutableLongStateOf(0L)
    var isPlayerExpanded by mutableStateOf(false)
    var isLiked by mutableStateOf(false)
    var backgroundColor by mutableStateOf(Color(0xFF1E1E1E))

    var currentContext by mutableStateOf<PlaybackContext?>(null)

    // audio effects
    var effectsState by mutableStateOf(playerPrefs.getLastEffects())

    // queue logic
    var repeatMode by mutableStateOf(playerPrefs.getLastRepeatMode())
    var shuffleEnabled by mutableStateOf(playerPrefs.getLastShuffleEnabled())
    private var isAutoplayRadioLoading by mutableStateOf(false)

    // bottom sheets states
    var showMenuSheet by mutableStateOf(false)
    var navigateToPlaylistId by mutableStateOf<String?>(null)
    var trackForMenu by mutableStateOf<Track?>(null)
    var menuContextPlaylistId by mutableStateOf<Long?>(null)
    var isMenuContextFromPlayer by mutableStateOf(false)

    var selectedTrackForSheet by mutableStateOf<Track?>(null)

    var showDetailsSheet by mutableStateOf(false)
    var showCommentsSheet by mutableStateOf(false)
    val commentsList = mutableStateListOf<Comment>()
    var isCommentsLoading by mutableStateOf(false)
    var commentNextHref: String? = null

    var showAddToPlaylistSheet by mutableStateOf(false)
    var tracksToAddInBulk by mutableStateOf<List<Track>?>(null)
    val userPlaylists = mutableStateListOf<LocalPlaylist>()

    // queue data
    private val _originalQueue = mutableListOf<Track>()
    private val _queue = mutableListOf<Track>()
    val queue: List<Track> get() = _queue
    val queueState = mutableStateListOf<Track>()
    private var currentQueueIndex = -1

    val exoPlayer: ExoPlayer

    // --- LYRICS ---
    var showLyricsSheet by mutableStateOf(false)
    var lyricsLines = mutableStateListOf<LyricLine>()
    var isLyricsLoading by mutableStateOf(false)
    var isSearchingLyrics by mutableStateOf(false)
    var manualSearchQuery by mutableStateOf("")
    val lyricSearchResults = mutableStateListOf<LrcLibResponse>()

    // lyrics settings
    var lyricsFontSize by mutableFloatStateOf(playerPrefs.getLyricsFontSize())
    var lyricsAlignment by mutableStateOf(playerPrefs.getLyricsAlignment())

    private var pendingSeekPosition: Long? = null

    // helper to get strings
    private fun getString(resId: Int): String = getApplication<Application>().getString(resId)
    private fun getString(resId: Int, vararg args: Any): String = getApplication<Application>().getString(resId, *args)


    init {
        MusicManager.init(context)
        exoPlayer = MusicManager.player
        MusicManager.applyEffects(effectsState)

        MusicManager.onNextClick = { playNext(manual = true) }
        MusicManager.onPreviousClick = { smartPrevious() }

        // sync like state
        viewModelScope.launch {
            LikeRepository.likedTracks.collect { likedList ->
                currentTrack?.let { track ->
                    isLiked = likedList.any { it.id == track.id }
                }
            }
        }

        exoPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingState: Boolean) {
                isPlaying = isPlayingState
                saveStateAsync()
                if (isPlayingState) startProgressUpdate()
            }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    isLoading = false
                    if (exoPlayer.duration > 0) duration = exoPlayer.duration
                    pendingSeekPosition?.let { exoPlayer.seekTo(it); pendingSeekPosition = null }
                }
                if (state == Player.STATE_BUFFERING) isLoading = true
                if (state == Player.STATE_ENDED) {
                    AchievementManager.increment("no_skip_50")
                    AchievementManager.increment("plays_1")

                    // handle repeat one vs next track
                    if (repeatMode == RepeatMode.ONE) {
                        AchievementManager.increment("obsessed_50")
                        AchievementManager.increment("obsessed_200")
                        exoPlayer.seekTo(0)
                        exoPlayer.play()
                    } else {
                        playNext(manual = false)
                    }
                }
            }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                isLoading = false; isPlaying = false; playNext(manual = false)
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveStateAsync()
            }
        })

        // load user playlists for the add menu
        viewModelScope.launch {
            DownloadManager.getAllPlaylistsFlow().collect { playlists ->
                userPlaylists.clear()
                val sorted = playlists.sortedWith(compareByDescending<LocalPlaylist> { it.isUserCreated || it.id < 0 }.thenByDescending { it.addedAt })
                userPlaylists.addAll(sorted)
            }
        }
        restoreSession()
    }

    // --- LYRICS SETTINGS ---
    fun updateLyricsFontSize(size: Float) {
        lyricsFontSize = size
        playerPrefs.setLyricsFontSize(size)
    }

    fun updateLyricsAlignment(alignment: LyricsAlignment) {
        lyricsAlignment = alignment
        playerPrefs.setLyricsAlignment(alignment)
    }

    // --- NAVIGATION & UI ---

    fun navigateToTrackDetails(trackId: Long, initialTab: Int = 0) { showMenuSheet = false; showDetailsSheet = false; navigateToPlaylistId = "track_detail:$trackId?tab=$initialTab" }
    fun shareTrack(track: Track) {
        val appContext = getApplication<Application>().applicationContext
        val sendIntent = Intent().apply { action = Intent.ACTION_SEND; putExtra(Intent.EXTRA_TEXT, "https://soundcloud.com/tracks/${track.id}"); type = "text/plain" }
        val shareIntent = Intent.createChooser(sendIntent, getString(R.string.btn_share)).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK }
        appContext.startActivity(shareIntent)
        showMenuSheet = false
        AchievementManager.increment("social_butterfly"); AchievementManager.increment("social_star")
    }
    fun openTrackDetails(targetTrack: Track? = null) { val target = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return; selectedTrackForSheet = target; showMenuSheet = false; showDetailsSheet = true }
    fun openComments(targetTrack: Track? = null) { val target = targetTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return; selectedTrackForSheet = target; showMenuSheet = false; showDetailsSheet = false; showCommentsSheet = true; loadComments(true, target) }
    fun resolveAndNavigateToArtist(username: String) {
        showDetailsSheet = false; isPlayerExpanded = false
        val cleanName = username.replace("@", "").trim()
        if (cleanName.isBlank()) return
        viewModelScope.launch { try { val user = api.resolveUrl("https://soundcloud.com/$cleanName"); if (user.id > 0) navigateToPlaylistId = "profile:${user.id}" } catch (e: Exception) { emitUiEvent(getString(R.string.error_generic)) } }
    }
    fun navigateToTag(tagName: String) { showDetailsSheet = false; isPlayerExpanded = false; navigateToPlaylistId = "tag:$tagName" }
    fun loadComments(refresh: Boolean = false, specificTrack: Track? = null) {
        val t = specificTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        if (refresh) { commentsList.clear(); commentNextHref = null }
        if (!refresh && commentNextHref == null && commentsList.isNotEmpty()) return
        viewModelScope.launch { if (refresh) isCommentsLoading = true; try { val response = if (refresh) api.getTrackComments(t.id) else api.getCommentsNextPage(commentNextHref!!); commentNextHref = response.next_href; commentsList.addAll(response.collection.filter { c -> commentsList.none { it.id == c.id } }) } catch (e: Exception) { e.printStackTrace() } finally { isCommentsLoading = false } }
    }
    fun navigateToContext() { currentContext?.let { navigateToPlaylistId = it.navigationId } }

    // --- LYRICS ---
    private fun loadLyrics(track: Track) {
        lyricsLines.clear()
        isLyricsLoading = true
        isSearchingLyrics = false

        val cleanTitle = cleanTitleNoise(track.title ?: "")
        val query = "$cleanTitle ${track.user?.username ?: ""}"
        manualSearchQuery = query

        viewModelScope.launch(Dispatchers.IO) {
            val preferLocal = playerPrefs.getLyricsPreferLocal()
            var localLyricsFound = false

            if (preferLocal) {
                // check for embedded lyrics in local file
                if (track.id < 0) {
                    val localTrack = DownloadManager.getLocalTrack(track.id)
                    if (localTrack != null && File(localTrack.localAudioPath).exists()) {
                        val rawLyrics = LyricsUtils.extractLocalLyrics(localTrack.localAudioPath)
                        if (!rawLyrics.isNullOrBlank()) {
                            val parsed = LyricsUtils.parseLrc(rawLyrics, track.durationMs ?: 0L)
                            val finalLines = if (parsed.isNotEmpty()) parsed else listOf(LyricLine(rawLyrics, 0, track.durationMs ?: 0L))
                            withContext(Dispatchers.Main) {
                                lyricsLines.addAll(finalLines)
                                isLyricsLoading = false
                            }
                            localLyricsFound = true
                        }
                    }
                }
                else {
                    val file = File(context.filesDir, "track_${track.id}.mp3")
                    if (file.exists()) {
                        val rawLyrics = LyricsUtils.extractLocalLyrics(file.absolutePath)
                        if (!rawLyrics.isNullOrBlank()) {
                            val parsed = LyricsUtils.parseLrc(rawLyrics, track.durationMs ?: 0L)
                            val finalLines = if (parsed.isNotEmpty()) parsed else listOf(LyricLine(rawLyrics, 0, track.durationMs ?: 0L))
                            withContext(Dispatchers.Main) {
                                lyricsLines.addAll(finalLines)
                                isLyricsLoading = false
                            }
                            localLyricsFound = true
                        }
                    }
                }
            }

            // fetch from web if no local lyrics
            if (!localLyricsFound) {
                try {
                    val results = LrcLibClient.api.searchLyrics(query)
                    val trackDurationSec = (track.durationMs ?: 0L) / 1000.0
                    // try to match duration to find the best sync
                    val bestMatch = results.filter { abs(it.duration - trackDurationSec) < 4.0 }.find { !it.syncedLyrics.isNullOrEmpty() } ?: results.firstOrNull()
                    processLyricsResponse(bestMatch, track.durationMs ?: 0L)
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { isLyricsLoading = false }
                }
            }
        }
    }

    private suspend fun processLyricsResponse(response: LrcLibResponse?, trackDuration: Long) {
        val resultLines = when { response == null -> emptyList(); !response.syncedLyrics.isNullOrEmpty() -> LyricsUtils.parseLrc(response.syncedLyrics, trackDuration); !response.plainLyrics.isNullOrEmpty() -> { val lines = response.plainLyrics.split("\n").filter { it.isNotBlank() }; val durPerLine = trackDuration / lines.size.coerceAtLeast(1); lines.mapIndexed { i, txt -> LyricLine(txt, i * durPerLine, (i + 1) * durPerLine) } } else -> emptyList() }
        withContext(Dispatchers.Main) { lyricsLines.clear(); lyricsLines.addAll(resultLines); isLyricsLoading = false; if (resultLines.isNotEmpty()) isSearchingLyrics = false }
    }
    fun searchLyricsManual(query: String) { if (query.isBlank()) return; isLyricsLoading = true; lyricSearchResults.clear(); viewModelScope.launch(Dispatchers.IO) { try { val results = LrcLibClient.api.searchLyrics(query); withContext(Dispatchers.Main) { lyricSearchResults.addAll(results) } } catch (e: Exception) { e.printStackTrace() } finally { withContext(Dispatchers.Main) { isLyricsLoading = false } } } }
    fun selectLyricResult(result: LrcLibResponse) { viewModelScope.launch(Dispatchers.IO) { processLyricsResponse(result, duration) } }
    // remove junk like (official video) for better search
    private fun cleanTitleNoise(title: String): String = title.replace(Regex("\\(.*?\\)|\\[.*?\\]"), "").replace(Regex("(?i)(official video|lyrics|ft\\.|feat\\.|prod\\.)"), "").trim()
    fun openLyrics(targetTrack: Track? = null) { val target = targetTrack ?: currentTrack ?: return; if (target.id != currentTrack?.id) playPlaylist(listOf(target), 0); showMenuSheet = false; showLyricsSheet = true }

    // --- PLAYBACK CONTROL ---
    fun playPlaylist(tracks: List<Track>, startIndex: Int = 0, context: PlaybackContext? = null) {
        if (tracks.isEmpty()) return; isPlayerExpanded = false; _originalQueue.clear(); _originalQueue.addAll(tracks); _queue.clear(); this.currentContext = context; val effectiveStartIndex = if (startIndex in tracks.indices) startIndex else 0; if (shuffleEnabled) applyShuffle(effectiveStartIndex, tracks) else _queue.addAll(tracks); updateQueueState(); if (context != null) { val isStation = context.navigationId.contains("station"); val isProfile = context.navigationId.contains("profile"); val idLong = context.navigationId.substringAfter(":").toLongOrNull() ?: 0L; val historyPlaylist = Playlist(idLong, context.displayText.substringAfter("•").trim(), context.imageUrl, null, tracks.size, null, null); HistoryRepository.addToHistory(historyPlaylist, isStation, isProfile) }; playTrackAtIndex(if(shuffleEnabled) 0 else effectiveStartIndex, addToHistory = (context == null))
    }
    fun playTrackAtPosition(track: Track, position: Long) { pendingSeekPosition = position; playPlaylist(listOf(track), 0); showCommentsSheet = false; isPlayerExpanded = true }
    fun skipToQueueItem(index: Int) { playTrackAtIndex(index, addToHistory = false); AchievementManager.trackSkipped(); AchievementManager.increment("skipper_100"); AchievementManager.increment("skipper_1000") }
    private fun playTrackAtIndex(index: Int, addToHistory: Boolean = true) {
        if (index < 0 || index >= _queue.size) { currentContext = null; return }
        currentQueueIndex = index; val trackToPlay = _queue[index]; isLoading = true; duration = trackToPlay.durationMs ?: 0L; currentPosition = 0L
        viewModelScope.launch {
            var finalTrack = trackToPlay
            // fetch full info if needed (missing stream url)
            if (trackToPlay.user?.id == 0L || trackToPlay.media == null) {
                try { val fullTrackList = api.getTracksByIds(trackToPlay.id.toString()); if (fullTrackList.isNotEmpty()) { finalTrack = fullTrackList[0]; _queue[index] = finalTrack; if (!_originalQueue.contains(finalTrack)) { val originalIndex = _originalQueue.indexOfFirst { it.id == finalTrack.id }; if (originalIndex != -1) _originalQueue[originalIndex] = finalTrack }; updateQueueState() } } catch (e: Exception) { e.printStackTrace() }
            }
            currentTrack = finalTrack; isLiked = LikeRepository.isTrackLiked(finalTrack.id); loadLyrics(finalTrack)
            MusicManager.currentTrack = finalTrack
            AchievementManager.checkTrackNameSecret(finalTrack.title ?: "")
            AchievementManager.increment("plays_1"); AchievementManager.increment("plays_100"); AchievementManager.increment("plays_1000"); AchievementManager.increment("plays_5000"); AchievementManager.increment("plays_10000"); AchievementManager.increment("plays_20000"); AchievementManager.increment("plays_50000"); AchievementManager.increment("plays_100000")
            saveStateAsync(); playRobustly(finalTrack); if (addToHistory && currentContext?.navigationId?.startsWith("station:") != true) HistoryRepository.addToHistory(finalTrack)
        }
    }
    fun playNext(manual: Boolean = true) {
        if (isAutoplayRadioLoading) return; val nextIndex = currentQueueIndex + 1
        if (manual) { AchievementManager.trackSkipped(); AchievementManager.increment("skipper_100"); AchievementManager.increment("skipper_1000") }

        if (nextIndex < _queue.size) {
            playTrackAtIndex(nextIndex, addToHistory = false)
        } else {
            if (repeatMode == RepeatMode.ALL) {
                playTrackAtIndex(0, addToHistory = false)
            } else {
                val autoPlayEnabled = playerPrefs.getAutoplayEnabled()
                if (autoPlayEnabled) {
                    viewModelScope.launch {
                        // try to fetch station for endless playback
                        fetchAndQueueRadio()
                        val newNextIndex = currentQueueIndex + 1
                        if (newNextIndex < _queue.size) playTrackAtIndex(newNextIndex, addToHistory = false)
                        else { exoPlayer.pause(); exoPlayer.seekTo(0); currentContext = null; saveStateAsync() }
                    }
                } else {
                    exoPlayer.pause()
                    exoPlayer.seekTo(0)
                }
            }
        }
    }
    private suspend fun fetchAndQueueRadio() {
        val lastTrack = currentTrack ?: return; isAutoplayRadioLoading = true; try { val station = api.getTrackStation(lastTrack.id); if (station.tracks != null && station.tracks.isNotEmpty()) { val newTracks = station.tracks.filter { t -> _queue.none { it.id == t.id } }; _queue.addAll(newTracks); _originalQueue.addAll(newTracks); updateQueueState(); if (currentContext == null) currentContext = PlaybackContext("Radio • ${lastTrack.title}", "station:${lastTrack.id}", lastTrack.fullResArtwork) } } catch (e: Exception) { e.printStackTrace() } finally { isAutoplayRadioLoading = false }
    }
    fun smartPrevious() { if (exoPlayer.currentPosition > 3000) exoPlayer.seekTo(0) else { val prev = currentQueueIndex - 1; if (prev >= 0) playTrackAtIndex(prev, addToHistory = false) else exoPlayer.seekTo(0) } }
    fun toggleShuffle() { shuffleEnabled = !shuffleEnabled; if (shuffleEnabled) applyShuffle() else revertShuffle(); updateQueueState(); saveStateAsync() }
    private fun applyShuffle(startIndex: Int = currentQueueIndex, sourceList: List<Track> = _originalQueue) { if (sourceList.isEmpty() || startIndex < 0 || startIndex >= _queue.size) return; val upcoming = _queue.subList(startIndex + 1, _queue.size).shuffled(); val played = _queue.subList(0, startIndex + 1).toList(); _queue.clear(); _queue.addAll(played); _queue.addAll(upcoming) }
    private fun revertShuffle() { val currentTrackId = currentTrack?.id ?: return; _queue.clear(); _queue.addAll(_originalQueue); currentQueueIndex = _queue.indexOfFirst { it.id == currentTrackId }.coerceAtLeast(0) }
    fun toggleRepeatMode() { repeatMode = when (repeatMode) { RepeatMode.NONE -> RepeatMode.ALL; RepeatMode.ALL -> RepeatMode.ONE; RepeatMode.ONE -> RepeatMode.NONE }; saveStateAsync() }
    private fun updateQueueState() { queueState.clear(); queueState.addAll(_queue) }
    fun moveQueueItem(from: Int, to: Int) { if (from == to) return; val item = _queue.removeAt(from); _queue.add(to, item); if (!shuffleEnabled && from < _originalQueue.size && to < _originalQueue.size + 1) { val originalItem = _originalQueue.removeAt(from); _originalQueue.add(to, originalItem) }; updateQueueState(); saveStateAsync() }
    fun insertNext(tracks: List<Track>) { if (tracks.isEmpty()) return; val insertIndex = currentQueueIndex + 1; _queue.addAll(insertIndex, tracks); _originalQueue.addAll(insertIndex, tracks); updateQueueState(); saveStateAsync(); emitUiEvent(getString(R.string.menu_play_next)) }
    fun togglePlayPause() { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() }
    fun seekTo(position: Long) { exoPlayer.seekTo(position); currentPosition = position; saveStateAsync() }

    // complex logic to play from file, cache or network
    private suspend fun playRobustly(track: Track, autoPlay: Boolean = true, startPosition: Long = 0L) {
        viewModelScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(track.fullResArtwork)
            if (bitmap != null) {
                // extract vibe color from album art
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
            }
            withContext(Dispatchers.Main) {
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                try {
                    resolveAndPlay(track, bitmap, true, autoPlay, startPosition)
                } catch (e: Exception) {
                    try {
                        resolveAndPlay(track, bitmap, false, autoPlay, startPosition)
                    } catch (e2: Exception) {
                        if (autoPlay) playNext(manual = false)
                    }
                }
            }
        }
    }

    private fun isColorDark(color: Int): Boolean {
        val darkness = 1 - (0.299 * android.graphics.Color.red(color) + 0.587 * android.graphics.Color.green(color) + 0.114 * android.graphics.Color.blue(color)) / 255
        return darkness >= 0.5
    }

    // finds the actual audio url
    private suspend fun resolveAndPlay(track: Track, artworkBitmap: Bitmap?, useToken: Boolean, autoPlay: Boolean = true, startPosition: Long = 0L) {
        val localFile = File(context.filesDir, "track_${track.id}.mp3")
        if (localFile.exists() && localFile.length() > 0) {
            val mediaItem = buildMediaItem(track, Uri.fromFile(localFile), artworkBitmap)
            preparePlayer(mediaItem, startPosition, autoPlay)
            return
        }

        if (track.id < 0) {
            val localTrack = DownloadManager.getLocalTrack(track.id)
            if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                val mediaItem = buildMediaItem(track, Uri.parse(localTrack.localAudioPath), artworkBitmap)
                preparePlayer(mediaItem, startPosition, autoPlay)
                return
            }
        }

        var validTrack = track
        if (track.media == null || track.media.transcodings.isNullOrEmpty()) {
            val fetched = withContext(Dispatchers.IO) { try { api.getTracksByIds(track.id.toString()) } catch (e: Exception) { emptyList() } }
            if (fetched.isNotEmpty()) validTrack = fetched[0] else throw Exception("Impossible de récupérer les infos")
        }

        // try to find the best quality stream
        val transcodings = validTrack.media?.transcodings ?: throw Exception("Aucun format")
        val qualityPref = playerPrefs.getAudioQuality()
        val target = if (qualityPref == "HIGH") {
            transcodings.find { it.format?.protocol == "progressive" } ?: transcodings.find { it.format?.protocol == "hls" }
        } else {
            transcodings.find { it.format?.mimeType?.contains("opus") == true } ?: transcodings.find { it.format?.protocol == "hls" } ?: transcodings.find { it.format?.protocol == "progressive" }
        } ?: throw Exception("Aucun format valide")
        val client = OkHttpClient(); val urlWithId = if (target.url.contains("?")) "${target.url}&client_id=${Config.CLIENT_ID}" else "${target.url}?client_id=${Config.CLIENT_ID}"; val requestBuilder = Request.Builder().url(urlWithId); if (useToken) { val token = TokenManager(context).getAccessToken(); if (!token.isNullOrEmpty() && token != "null") requestBuilder.header("Authorization", "OAuth $token") }; val response = withContext(Dispatchers.IO) { client.newCall(requestBuilder.build()).execute() }; if (!response.isSuccessful) throw Exception("Erreur Stream: ${response.code}"); val finalStreamUrl = JSONObject(response.body!!.string()).getString("url"); val mediaItem = buildMediaItem(validTrack, Uri.parse(finalStreamUrl), artworkBitmap); preparePlayer(mediaItem, startPosition, autoPlay)
    }

    private fun buildMediaItem(track: Track, uri: Uri, bitmap: Bitmap?): MediaItem { val metadataBuilder = MediaMetadata.Builder().setTitle(track.title ?: "Unknown").setArtist(track.user?.username ?: "Unknown").setArtworkUri(Uri.parse(track.fullResArtwork)); if (bitmap != null) { val stream = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream); metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER) }; return MediaItem.Builder().setUri(uri).setMediaId(track.id.toString()).setMediaMetadata(metadataBuilder.build()).build() }
    private suspend fun preparePlayer(mediaItem: MediaItem, startPos: Long, autoPlay: Boolean) { withContext(Dispatchers.Main) { exoPlayer.setMediaItem(mediaItem); exoPlayer.prepare(); if (startPos > 0) exoPlayer.seekTo(startPos); if (autoPlay) { startPlaybackServiceSafe(); exoPlayer.play() }; MusicManager.applyEffects(effectsState) } }
    private fun startPlaybackServiceSafe() { try { val intent = Intent(context, PlaybackService::class.java); if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent) } catch (e: Exception) { e.printStackTrace() } }

    // audio effects toggles
    fun setCustomSpeed(speed: Float) { val r = (speed * 10).roundToInt() / 10f; effectsState = effectsState.copy(speed = r); applyEffectsAndSave() }
    fun togglePitchEnabled(e: Boolean) { effectsState = effectsState.copy(isPitchEnabled = e); applyEffectsAndSave() }
    fun toggle8D() { effectsState = effectsState.copy(is8DEnabled = !effectsState.is8DEnabled); applyEffectsAndSave() }
    fun toggleMuffled() { val n = !effectsState.isMuffledEnabled; effectsState = effectsState.copy(isMuffledEnabled = n, isBassBoostEnabled = if(n) false else effectsState.isBassBoostEnabled); applyEffectsAndSave() }
    fun toggleBassBoost() { val n = !effectsState.isBassBoostEnabled; effectsState = effectsState.copy(isBassBoostEnabled = n, isMuffledEnabled = if(n) false else effectsState.isMuffledEnabled); applyEffectsAndSave(); if (n) AchievementManager.increment("bass_lover", 1) }
    fun toggleReverb() { effectsState = effectsState.copy(isReverbEnabled = !effectsState.isReverbEnabled); applyEffectsAndSave() }
    private fun applyEffectsAndSave() { MusicManager.applyEffects(effectsState); viewModelScope.launch(Dispatchers.IO) { playerPrefs.saveEffects(effectsState) } }
    fun toggleLike() {
        val t = currentTrack ?: return; isLiked = !isLiked
        if (isLiked) { LikeRepository.addLike(t); AchievementManager.increment("liker_50"); AchievementManager.increment("liker_1000"); AchievementManager.increment("liker_5000") } else { LikeRepository.removeLike(t.id) }
        val tokenManager = TokenManager(context); if (!tokenManager.isGuestMode() && !tokenManager.getAccessToken().isNullOrEmpty()) { viewModelScope.launch(Dispatchers.IO) { try { if (isLiked) api.likeComment(t.id) } catch (e: Exception) { e.printStackTrace() } } }
    }
    fun showTrackOptions(track: Track, playlistContextId: Long? = null, fromPlayer: Boolean = false) { trackForMenu = track; menuContextPlaylistId = playlistContextId; isMenuContextFromPlayer = fromPlayer; showMenuSheet = true }
    fun prepareBulkAdd(tracks: List<Track>) { tracksToAddInBulk = tracks; trackForMenu = null; showAddToPlaylistSheet = true }
    fun addToPlaylist(playlistId: Long, track: Track) { DownloadManager.addTrackToPlaylist(playlistId, track); showAddToPlaylistSheet = false; emitUiEvent(getString(R.string.success_generic)) }
    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>) { viewModelScope.launch(Dispatchers.IO) { tracks.forEach { DownloadManager.addTrackToPlaylist(playlistId, it) }; withContext(Dispatchers.Main) { showAddToPlaylistSheet = false; emitUiEvent(getString(R.string.success_generic)); AchievementManager.increment("playlist_creator") } } }
    fun createAndAddToPlaylist(name: String, track: Track) { val id = DownloadManager.createUserPlaylist(name); DownloadManager.addTrackToPlaylist(id, track); showAddToPlaylistSheet = false; emitUiEvent(getString(R.string.success_generic)); AchievementManager.increment("playlist_creator") }
    fun createAndAddTracksToPlaylist(name: String, tracks: List<Track>) { viewModelScope.launch(Dispatchers.IO) { val id = DownloadManager.createUserPlaylist(name); tracks.forEach { DownloadManager.addTrackToPlaylist(id, it) }; withContext(Dispatchers.Main) { showAddToPlaylistSheet = false; emitUiEvent(getString(R.string.success_generic)); AchievementManager.increment("playlist_creator") } } }
    fun removeFromContextPlaylist(playlistId: Long, track: Track) { DownloadManager.removeTrackFromPlaylist(playlistId, track.id); showMenuSheet = false; emitUiEvent(getString(R.string.success_generic)) }
    fun addToQueue(tracks: List<Track>) { if (tracks.isEmpty()) return; _queue.addAll(tracks); _originalQueue.addAll(tracks); updateQueueState(); saveStateAsync(); emitUiEvent(getString(R.string.menu_add_queue)) }
    fun downloadTrack(track: Track) { if (DownloadManager.isTrackDownloading(track.id)) return; DownloadManager.downloadTrack(track); AchievementManager.increment("download_100"); AchievementManager.increment("download_1000") }
    fun fetchUserProfile() { viewModelScope.launch { try { currentUserId = api.getMe().id } catch (_: Exception) {} } }
    fun loadTrackStation() { val t = currentTrack ?: return; showMenuSheet = false; navigateToPlaylistId = "station:${t.id}"; HistoryRepository.addToHistory(Playlist(t.id, "Station: ${t.title}", t.artworkUrl, null, 0, t.user, null), isStation = true) }
    fun navigateToArtist(id: Long) { showMenuSheet = false; showCommentsSheet = false; showDetailsSheet = false; isPlayerExpanded = false; navigateToPlaylistId = "profile:$id" }
    fun onNavigationHandled() { navigateToPlaylistId = null }
    private fun emitUiEvent(msg: String) { viewModelScope.launch { _uiEvent.emit(msg) } }
    private fun saveStateAsync() { val t = currentTrack; val p = exoPlayer.currentPosition; val q = _queue.toList(); val c = currentContext; viewModelScope.launch(Dispatchers.IO) { playerPrefs.savePlaybackState(t, p, q, c, shuffleEnabled, repeatMode) } }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            val tokenManager = TokenManager(context); val isGuest = tokenManager.isGuestMode()
            while (isActive && isPlaying) {
                currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
                AchievementManager.addPlayTime(1, isGuest, effectsState.speed)
                if (effectsState.isBassBoostEnabled) AchievementManager.increment("bass_addict", 1)
                delay(1000)
            }
        }
    }

    // bring back the state from last time
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
                        _queue.clear()
                        _queue.addAll(lastQueue)
                        _originalQueue.clear()
                        _originalQueue.addAll(lastQueue)
                        updateQueueState()
                    }

                    if (lastTrack != null) {
                        shuffleEnabled = lastShuffle
                        repeatMode = lastRepeat
                        currentContext = lastContext
                        currentTrack = lastTrack

                        // crucial: let the service know what track we have immediately
                        // this fixes the notification and like button state
                        MusicManager.currentTrack = lastTrack

                        // check like status
                        isLiked = LikeRepository.isTrackLiked(lastTrack.id)

                        loadLyrics(lastTrack)

                        currentQueueIndex = _queue.indexOfFirst { it.id == lastTrack.id }
                        if (currentQueueIndex == -1) {
                            _queue.add(0, lastTrack)
                            _originalQueue.add(0, lastTrack)
                            updateQueueState()
                            currentQueueIndex = 0
                        }

                        val currentPlayerMediaId = exoPlayer.currentMediaItem?.mediaId
                        val isSameTrack = currentPlayerMediaId == lastTrack.id.toString()

                        if (isSameTrack) {
                            isPlaying = exoPlayer.isPlaying
                            duration = exoPlayer.duration.coerceAtLeast(lastTrack.durationMs ?: 0L)
                            currentPosition = exoPlayer.currentPosition
                            MusicManager.applyEffects(effectsState)
                        } else {
                            currentPosition = lastPosition
                            duration = lastTrack.durationMs ?: 0L
                            playRobustly(lastTrack, autoPlay = false, startPosition = lastPosition)
                        }
                        delay(200)
                        val intent = Intent(context, PlaybackService::class.java).apply {
                            action = PlaybackService.ACTION_FORCE_UPDATE
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }    private suspend fun loadBitmap(url: String): Bitmap? { return try { val loader = ImageLoader(context); val request = ImageRequest.Builder(context).data(url).allowHardware(false).build(); (loader.execute(request) as? SuccessResult)?.drawable.let { (it as? BitmapDrawable)?.bitmap } } catch (e: Exception) { null } }
}