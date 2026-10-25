/**
 * Developed by Jason-Marshall Fastner, Germany <jasonfastner@protonmail.com>
 * Questions, feedback, or beat-matching debates? Feel free to reach out via email!
 * 
 * Note: Cats always land on their feet, and with this engine, your transitions will too.
 */
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
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Precision
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.*
import com.alananasss.kittytune.data.sync.SyncLog
import com.alananasss.kittytune.data.sync.ConnectManager
import com.alananasss.kittytune.data.sync.ConnectPeerState
import com.alananasss.kittytune.data.sync.ConnectMessage
import com.alananasss.kittytune.data.sync.PlaybackSnapshot
import com.alananasss.kittytune.data.sync.SyncPlayback
import com.alananasss.kittytune.data.sync.SyncScheduler
import com.alananasss.kittytune.data.spotify.SpotifyArtistRef
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.data.local.RadioEligibility
import com.alananasss.kittytune.ui.common.AchievementNotificationManager
import com.alananasss.kittytune.ui.common.AchievementNotification
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.LyricsDisplayState
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.gridTrust
import com.alananasss.kittytune.data.local.trustedAnchorMs
import com.alananasss.kittytune.data.network.LrcLibClient
import com.alananasss.kittytune.data.ListeningStatsRepository
import com.alananasss.kittytune.data.network.LrcLibResponse
import com.alananasss.kittytune.data.network.MusixmatchClient
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.data.network.SoundCloudTelemetryTracker
import com.alananasss.kittytune.domain.*
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricSinger
import com.alananasss.kittytune.ui.player.lyrics.LyricsUtils
import com.google.gson.Gson
import kotlinx.coroutines.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

import com.alananasss.kittytune.data.lyrics.providers.*
import com.alananasss.kittytune.data.lyrics.clients.*
import com.alananasss.kittytune.KittyTuneApp
import com.alananasss.kittytune.data.BlockManager
import com.alananasss.kittytune.audio.ai.AiDetectionManager
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.filter

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
    val provider: String,
    val rawContent: String? = null,
    val previewText: String? = null
)

private const val MIX_AFTER_SEEK_MS = 2_000L
private const val LYRICS_MIX_LEAD_MS = 2_500L

class PlayerViewModel(application: Application) : AndroidViewModel(application),
    com.alananasss.kittytune.data.together.TogetherPlayer {

    private var lastSeekTimestamp: Long = 0L
    private var remotePeer by mutableStateOf<ConnectPeerState?>(null)
    private var remotePosition by mutableLongStateOf(0L)
    private var remoteScrubPosition by mutableLongStateOf(0L)
    private var remoteTrackLiked by mutableStateOf(false)
    private var remoteTicker: Job? = null
    private var remoteVolumeJob: Job? = null
    private var applyingConnectCommand = false
    val playbackDeviceLabel: String?
        get() = ConnectManager.selectedDevice.value?.let { id ->
            val peer = remotePeer
            val name = peer?.name ?: com.alananasss.kittytune.data.sync.SyncPeers.find(id)?.label.orEmpty()
            val app = com.alananasss.kittytune.KittyTuneApp.instance
            if (peer?.connected == true) app.getString(R.string.connect_playing_on, name)
            else "$name · ${app.getString(R.string.connect_offline)}"
        }
    val uiIsLiked: Boolean get() = if (remotePeer?.snapshot != null) remotePeer?.snapshot?.isLiked ?: remoteTrackLiked else isLiked
    var uiCurrentTrack: Track?
        get() = remotePeer?.snapshot?.let { it.queue.getOrNull(it.currentIndex) } ?: currentTrack
        set(value) { currentTrack = value }
    var uiIsPlaying: Boolean
        get() = if (ConnectManager.selectedDevice.value != null) remotePeer?.connected == true && remotePeer?.snapshot?.isPlaying == true else isPlaying
        set(value) { isPlaying = value }
    var uiDuration: Long
        get() = remotePeer?.snapshot?.let { it.queue.getOrNull(it.currentIndex)?.durationMs ?: 0L } ?: duration
        set(value) { duration = value }
    var uiCurrentPosition: Long
        get() = if (remotePeer?.snapshot != null) { if (isScrubbing) remoteScrubPosition else remotePosition } else currentPosition
        set(value) { if (remotePeer?.snapshot != null) remoteScrubPosition = value else currentPosition = value }
    var uiQueueState: List<Track>
        get() = remotePeer?.snapshot?.queue ?: queueState
        set(value) { queueState = value }
    var uiCurrentQueueIndex: Int
        get() = remotePeer?.snapshot?.currentIndex ?: currentQueueIndex
        set(value) { currentQueueIndex = value }
    var uiShuffleEnabled: Boolean
        get() = remotePeer?.snapshot?.shuffleEnabled ?: shuffleEnabled
        set(value) { shuffleEnabled = value }
    var uiRepeatMode: RepeatMode
        get() = remotePeer?.snapshot?.repeatMode?.let { runCatching { RepeatMode.valueOf(it) }.getOrDefault(RepeatMode.NONE) } ?: repeatMode
        set(value) { repeatMode = value }

    var playedTrackIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    private fun routeRemote(action: String, value: Long = 0, value2: Long = 0): Boolean {
        if (applyingConnectCommand) return false
        val id = ConnectManager.selectedDevice.value ?: return false
        ConnectManager.command(id, action, value, value2 = value2)
        return true
    }

    private fun observeConnectPlayer() {
        viewModelScope.launch {
            ConnectManager.feedback.collect { message ->
                if (message.isNotBlank() && message != "✓") _uiEvent.emit(message)
            }
        }
        viewModelScope.launch {
            combine(ConnectManager.peers, ConnectManager.selectedDevice, ConnectManager.visible) { peers, id, visible ->
                Triple(id, id?.let(peers::get), visible)
            }.collect { (id, peer, visible) ->
                if (id != null && isPlaying) MusicManager.player.pause()
                remotePeer = peer
                val displayedTrack = uiCurrentTrack
                val remoteLiked = peer?.snapshot?.isLiked
                remoteTrackLiked = remoteLiked ?: (displayedTrack?.let { LikeRepository.isTrackLiked(it.id) } == true)
                if (remoteLiked != null && displayedTrack != null) {
                    val localLiked = LikeRepository.isTrackLiked(displayedTrack.id)
                    if (remoteLiked != localLiked) {
                        if (remoteLiked) LikeRepository.addLike(displayedTrack) else LikeRepository.removeLike(displayedTrack.id)
                    }
                }
                val remoteSpeed = peer?.snapshot?.speed
                if (remoteSpeed != null && ConnectManager.selectedDevice.value != null && effectsState.speed != remoteSpeed) {
                    effectsState = effectsState.copy(speed = remoteSpeed)
                }
                remotePosition = peer?.position() ?: 0L
                remoteTicker?.cancel()
                if (visible && peer?.connected == true && peer.snapshot?.isPlaying == true) {
                    remoteTicker = viewModelScope.launch {
                        while (isActive) { if (!isScrubbing) remotePosition = peer.position(); delay(250) }
                    }
                }
            }
        }
    }

    private val gson = com.alananasss.kittytune.utils.AppUtils.gson
    val api = RetrofitClient.create(application)
    private val context get() = getApplication<Application>().applicationContext
    val playerPrefs = PlayerPreferences(context)
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

    var currentUserId by mutableLongStateOf(playerPrefs.getCachedUserId())
    var currentUser by mutableStateOf<User?>(
        if (playerPrefs.getCachedUserId() != 0L) {
            User(playerPrefs.getCachedUserId(), playerPrefs.getCachedUsername() ?: "", null)
        } else null
    )
    var currentTrack by mutableStateOf<Track?>(null)
    var currentStreamSource by mutableStateOf<String?>(null)
    var fullPlayerSourceIndicatorEnabled by mutableStateOf(playerPrefs.getFullPlayerSourceIndicatorEnabled())
        private set

    fun updateFullPlayerSourceIndicatorEnabled(enabled: Boolean) {
        fullPlayerSourceIndicatorEnabled = enabled
        playerPrefs.setFullPlayerSourceIndicatorEnabled(enabled)
    }

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
    var currentAnimatedCoverUrl by mutableStateOf<String?>(null)
    var currentAnimatedCoverTallUrl by mutableStateOf<String?>(null)
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

    val automixDebugInfo = com.alananasss.kittytune.audio.automix.AutomixManager.automixDebugInfo
    val isAutomixing = com.alananasss.kittytune.audio.automix.AutomixManager.isAutomixing
    val mixBeatsLeft = com.alananasss.kittytune.audio.automix.AutomixManager.mixBeatsLeft

    @Volatile
    private var isRestoringSession = true
    private var syncPlaybackReady = false
    private var lastAppliedRemotePlaybackAtMs = 0L

    val player: ExoPlayer
        get() {
            try {
                return MusicManager.player
            } catch (_: IllegalStateException) {
                MusicManager.init(context)
                MusicManager.player.addListener(playerListener)
                MusicManager.applyEffects(effectsState)
                MusicManager.applyEqualizer(equalizerState)
                return MusicManager.player
            }
        }

    var effectsState by mutableStateOf(playerPrefs.getLastEffects().copy(isEqualizerEnabled = playerPrefs.getEqualizerState().isEnabled))
    var equalizerState by mutableStateOf(playerPrefs.getEqualizerState())
    var isPreciseSpeedEnabled by mutableStateOf(playerPrefs.getPreciseSpeedEnabled())

    var isHapticsEnabled by mutableStateOf(playerPrefs.getHapticsEnabled())
        private set
    var hapticsStrength by mutableStateOf(playerPrefs.getHapticsStrength())
        private set

    fun toggleHaptics(enabled: Boolean? = null) {
        val newState = enabled ?: !isHapticsEnabled
        isHapticsEnabled = newState
        playerPrefs.setHapticsEnabled(newState)
        com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(context).setHapticsEnabled(newState)
    }

    fun updateHapticsStrength(strength: Float) {
        val clamped = strength.coerceIn(0f, 100f)
        hapticsStrength = clamped
        playerPrefs.setHapticsStrength(clamped)
        com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(context).setVibrationStrength(clamped)
    }

    var repeatMode by mutableStateOf(playerPrefs.getLastRepeatMode())
    var shuffleEnabled by mutableStateOf(playerPrefs.getLastShuffleEnabled())
    var isQueuePreserveUpcomingEnabled by mutableStateOf(playerPrefs.getQueuePreserveUpcomingOnJump())
    private var isAutoplayRadioLoading by mutableStateOf(false)
    private var boundPlayer: ExoPlayer? = null

    fun toggleQueuePreserveUpcoming(enabled: Boolean? = null) {
        val newState = enabled ?: !isQueuePreserveUpcomingEnabled
        isQueuePreserveUpcomingEnabled = newState
        playerPrefs.setQueuePreserveUpcomingOnJump(newState)
    }

    var repostedTrackIds by mutableStateOf<Set<Long>>(emptySet())
        private set

    var dismissedTrack by mutableStateOf<Track?>(null)
    var dismissedQueue by mutableStateOf<List<Track>>(emptyList())
    var dismissedQueueIndex by mutableIntStateOf(-1)
    var dismissedPosition by mutableLongStateOf(0L)
    var showDismissUndoBar by mutableStateOf(false)
    var isMiniPlayerDismissing by mutableStateOf(false)

    var showAiSkipUndoBar by mutableStateOf(false)
    var aiSkippedTrack by mutableStateOf<Track?>(null)
    private var aiSkippedQueueIndex = -1
    private var aiSkippedWasBlocked = false
    val whitelistedAiTrackIds = mutableSetOf<Long>()

    var showMenuSheet by mutableStateOf(false)
    var showAiDetectionSheet by mutableStateOf(false)
    var showConnectSheet by mutableStateOf(false)
    var showConnectDialog by mutableStateOf(false)

    /**
     * The track whose share card is being composed, or null while no card is open.
     *
     * Held as the track rather than a flag so the sheet keeps showing the one the user picked
     * even if playback moves on underneath it - a card that changed its artwork mid-compose
     * would be shared as something the user never approved.
     */
    var shareCardTrack by mutableStateOf<Track?>(null)
        private set

    /** Artwork for [shareCardTrack], loaded once when the sheet opens. */
    var shareCardArtwork by mutableStateOf<Bitmap?>(null)
        private set

    /**
     * Lines of the song to offer on the card, taken around where playback stands.
     *
     * Captured when the sheet opens rather than followed live: the card is a still, and lyrics
     * that moved on between the preview and the send would share a line the user never saw.
     */
    var shareCardLyrics by mutableStateOf<List<String>>(emptyList())
        private set

    fun openShareCard(track: Track) {
        shareCardTrack = track
        shareCardArtwork = null
        shareCardLyrics = lyricsSnippetAt(currentPosition)
        showMenuSheet = false
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadBitmap(track.fullResArtwork) }
            // The sheet may already be gone, or moved on to another track, by the time a slow
            // artwork arrives; dropping it then keeps a stale cover off the current card.
            if (shareCardTrack?.id == track.id) shareCardArtwork = bitmap
        }
    }

    fun dismissShareCard() {
        shareCardTrack = null
        shareCardArtwork = null
        shareCardLyrics = emptyList()
    }

    /**
     * Picks up to two lines starting at the one playing at [positionMs].
     *
     * Two is what actually fits the card without overrunning it: each line can itself wrap,
     * so more than that crowds past the fixed card height. Starting at the current line rather
     * than centring on it means the card carries the part that is about to be sung, which is
     * what someone shares a lyric for.
     */
    private fun lyricsSnippetAt(positionMs: Long): List<String> {
        val lines = lyricsLines.filter { it.text.isNotBlank() }
        if (lines.isEmpty()) return emptyList()
        val start = lines.indexOfLast { it.startTime <= positionMs }.coerceAtLeast(0)
        return lines.drop(start).take(2).map { it.text }
    }
    var navigateToPlaylistId by mutableStateOf<String?>(null)
    var trackForMenu by mutableStateOf<Track?>(null)
    var trackToEdit by mutableStateOf<Track?>(null)
    var menuContextPlaylistId by mutableStateOf<Long?>(null)
    var menuContextQueueIndex by mutableStateOf<Int?>(null)
    var isMenuContextFromPlayer by mutableStateOf(false)

    var socialLikers by mutableStateOf<List<User>>(emptyList())
    var isSocialLikersLoading by mutableStateOf(false)
    private var socialProofTrackId: Long? = null

    var selectedTrackForSheet by mutableStateOf<Track?>(null)
    var isLocalDetailsMode by mutableStateOf(false)
    var localFilePathForDetails by mutableStateOf<String?>(null)

    var showDetailsSheet by mutableStateOf(false)

    var showSelectArtistDialog by mutableStateOf(false)
    var selectedArtistDialogTrack by mutableStateOf<Track?>(null)
    var selectArtistOptions by mutableStateOf<List<SpotifyArtistRef>>(emptyList())

    var showCommentsSheet by mutableStateOf(false)
    val commentsList = mutableStateListOf<Comment>()
    var isCommentsLoading by mutableStateOf(false)
    var commentNextHref: String? = null
    private var commentNextCursor: String? = null
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
            if (value) {
                fetchOnlinePlaylistsForAdd()
            } else {
                targetPlaylistForBulkAdd = null
                tracksToAddInBulk = null
            }
        }
    var tracksToAddInBulk by mutableStateOf<List<Track>?>(null)
    var targetPlaylistForBulkAdd by mutableStateOf<LocalPlaylist?>(null)
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
                val onlineMap = online.associateBy { it.id }

                for (i in userPlaylists.indices) {
                    val current = userPlaylists[i]
                    val onlinePl = onlineMap[current.id]
                    if (onlinePl != null) {
                        val realCount = onlinePl.trackCount ?: current.trackCount
                        val localCoverFile = java.io.File(context.filesDir, "playlist_cover_${current.id}.jpg")
                        val localCoverPath = current.localCoverPath ?: if (localCoverFile.exists()) localCoverFile.absolutePath else null
                        val realArt = localCoverPath ?: onlinePl.fullResArtwork.takeIf { it.isNotBlank() } ?: current.artworkUrl
                        val realTitle = onlinePl.title.takeIf { !it.isNullOrBlank() } ?: current.title
                        if (realCount != current.trackCount || realArt != current.artworkUrl || realTitle != current.title || localCoverPath != current.localCoverPath) {
                            userPlaylists[i] = current.copy(
                                trackCount = realCount,
                                artworkUrl = realArt,
                                localCoverPath = localCoverPath,
                                title = realTitle
                            )
                        }
                    }
                }

                val currentLocalIds = userPlaylists.map { it.id }.toSet()
                val newPlaylists = online.filter { !currentLocalIds.contains(it.id) }.map {
                    val localCoverFile = java.io.File(context.filesDir, "playlist_cover_${it.id}.jpg")
                    val localCover = if (localCoverFile.exists()) localCoverFile.absolutePath else null
                    LocalPlaylist(
                        id = it.id,
                        title = it.title ?: "",
                        artist = it.user?.username ?: "",
                        artworkUrl = localCover ?: it.fullResArtwork,
                        localCoverPath = localCover,
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
    var showEqualizerScreen by mutableStateOf(false)
    var isSleepScreenOpen by mutableStateOf(false)
    var lyricsLines = mutableStateListOf<LyricLine>()
    var lyricsRevision by mutableIntStateOf(0)
    var isLyricsLoading by mutableStateOf(false)
    var isSearchingLyrics by mutableStateOf(false)
    var isManualSearchLoading by mutableStateOf(false)
    var manualSearchQuery by mutableStateOf("")
    val lyricSearchResults = mutableStateListOf<LrcLibResponse>()
    var manualSearchProvider by mutableStateOf("ALL")
    val unifiedLyricSearchResults = mutableStateListOf<UnifiedLyricResult>()
    val pendingLyricSources = mutableStateListOf<com.alananasss.kittytune.data.lyrics.providers.PreferredLyricsProvider>()
    var lyricSearchTrackId by mutableStateOf<Long?>(null)
    var currentLyricsSource by mutableStateOf<String?>(null)
    private var manualSearchGeneration = 0
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
    var isDuetViewEnabled by mutableStateOf(playerPrefs.getLyricsDuetViewEnabled())
        private set
    var isSplitBackingVocalsEnabled by mutableStateOf(playerPrefs.getLyricsSplitBackingVocalsEnabled())
        private set
    var isRevealWordsEnabled by mutableStateOf(playerPrefs.getLyricsRevealWordsEnabled())
        private set
    var duetBlacklist by mutableStateOf(playerPrefs.getLyricsDuetBlacklist())
        private set
    var lyricsUiStyle by mutableStateOf(playerPrefs.getLyricsUiStyle())
        private set
    var lyricsLineBlurEnabled by mutableStateOf(playerPrefs.getLyricsLineBlurEnabled())
        private set
    var lyricsLrcBounceEnabled by mutableStateOf(playerPrefs.getLyricsLrcBounceEnabled())
        private set
    var lyricsBounceFactor by mutableFloatStateOf(playerPrefs.getLyricsBounceFactor())
        private set
    var lyricsGlowFactor by mutableFloatStateOf(playerPrefs.getLyricsGlowFactor())
        private set
    var lyricsFillTransitionWidth by mutableFloatStateOf(playerPrefs.getLyricsFillTransitionWidth())
        private set
    var lyricsLineSpacing by mutableFloatStateOf(playerPrefs.getLyricsLineSpacing())
        private set
    var lyricsFont by mutableStateOf(playerPrefs.getLyricsFont())
        private set
    var lyricsMode by mutableStateOf(LyricsMode.SYNCED)
    var rawPlainLyrics by mutableStateOf<String?>(null)
    var lyricsDisplayState by mutableStateOf(LyricsDisplayState.OFF)
    var showInlineLyrics: Boolean
        get() = lyricsDisplayState == LyricsDisplayState.COVER_REPLACED
        set(value) {
            lyricsDisplayState = if (value) LyricsDisplayState.COVER_REPLACED else LyricsDisplayState.OFF
        }
    val isLyricsUnderCoverActive: Boolean
        get() = (lyricsDisplayState == LyricsDisplayState.UNDER_COVER || playerPrefs.getLyricsUnderCoverAlwaysVisible()) &&
                hasLyrics && lyricsLines.isNotEmpty() && lyricsDisplayState != LyricsDisplayState.COVER_REPLACED
    var lyricsSync by mutableStateOf(com.alananasss.kittytune.data.LyricsSync.NONE)
        private set

    val lyricsOffset: Long get() = lyricsSync.offsetAt(currentPosition)

    var oneLineLyricsEnabled by mutableStateOf(playerPrefs.getOneLineLyricsEnabled())
    var fullPlayerCoverFeather by mutableStateOf(playerPrefs.getFullPlayerCoverFeather())
    var fullPlayerCoverFeatherAmount by mutableFloatStateOf(playerPrefs.getFullPlayerCoverFeatherAmount())
    var fullPlayerCoverScale by mutableFloatStateOf(playerPrefs.getFullPlayerCoverScale())
    var fullPlayerBgStyle by mutableStateOf(playerPrefs.getFullPlayerBgStyle())
    var fullPlayerLayout by mutableStateOf(playerPrefs.getFullPlayerLayout())
    var fullPlayerInfoAlign by mutableStateOf(playerPrefs.getFullPlayerInfoAlign())
    var fullPlayerHeartSide by mutableStateOf(playerPrefs.getFullPlayerHeartSide())
    var lyricsDisplayStyle by mutableStateOf(playerPrefs.getLyricsDisplayStyle())
    var lyricsHorizontalMargin by mutableFloatStateOf(playerPrefs.getLyricsHorizontalMargin())
    var lyricsVerticalOffset by mutableFloatStateOf(playerPrefs.getLyricsVerticalOffset())
    var lyricsActiveScale by mutableFloatStateOf(playerPrefs.getLyricsActiveScale())
    var lyricsWheelLines by mutableFloatStateOf(playerPrefs.getLyricsWheelLines())

    var isPlainAutoScrollEnabled by mutableStateOf(true)
    var plainAutoScrollSpeed by mutableFloatStateOf(1.5f)
    var trackAutoScrollSpeed by mutableStateOf<Float?>(null)
    val effectivePlainAutoScrollSpeed: Float get() = trackAutoScrollSpeed ?: plainAutoScrollSpeed

    var lyricsFullScreenFontSize by mutableFloatStateOf(playerPrefs.getLyricsFontSize())
    var lyricsSidebarFontSize by mutableFloatStateOf(playerPrefs.getLyricsFontSize())
    var lyricsFullScreenUiStyle by mutableStateOf(playerPrefs.getLyricsUiStyle())
    var lyricsSidebarUiStyle by mutableStateOf(playerPrefs.getLyricsUiStyle())
    var lyricsFullScreenAlignment by mutableStateOf(playerPrefs.getLyricsAlignment())
    var lyricsSidebarAlignment by mutableStateOf(playerPrefs.getLyricsAlignment())
    var lyricsFullScreenDisplayStyle by mutableStateOf(playerPrefs.getLyricsDisplayStyle())
    var lyricsSidebarDisplayStyle by mutableStateOf(playerPrefs.getLyricsDisplayStyle())
    var lyricsFullScreenLineSpacing by mutableFloatStateOf(playerPrefs.getLyricsLineSpacing())
    var lyricsSidebarLineSpacing by mutableFloatStateOf(playerPrefs.getLyricsLineSpacing())
    var lyricsFullScreenHorizontalMargin by mutableFloatStateOf(playerPrefs.getLyricsHorizontalMargin())
    var lyricsSidebarHorizontalMargin by mutableFloatStateOf(playerPrefs.getLyricsHorizontalMargin())
    var lyricsFullScreenVerticalOffset by mutableFloatStateOf(playerPrefs.getLyricsVerticalOffset())
    var lyricsSidebarVerticalOffset by mutableFloatStateOf(playerPrefs.getLyricsVerticalOffset())
    var lyricsFullScreenActiveScale by mutableFloatStateOf(playerPrefs.getLyricsActiveScale())
    var lyricsSidebarActiveScale by mutableFloatStateOf(playerPrefs.getLyricsActiveScale())
    var lyricsFullScreenLineBlurEnabled by mutableStateOf(playerPrefs.getLyricsLineBlurEnabled())
    var lyricsSidebarLineBlurEnabled by mutableStateOf(playerPrefs.getLyricsLineBlurEnabled())
    var fullPlayerScreensaverEnabled by mutableStateOf(false)

    val lyricsSplitBackingVocals: Boolean get() = isSplitBackingVocalsEnabled
    val lyricsRevealWords: Boolean get() = isRevealWordsEnabled

    fun lyricsOffsetAtLyricTime(lyricTimeMs: Long): Long =
        lyricTimeMs - lyricsSync.audioPositionFor(lyricTimeMs)

    var showLyricsOffsetControls by mutableStateOf(false)

    /**
     * The listen in progress, and the track it belongs to (issue #33).
     *
     * Replaces a bare counter that was incremented by one second per wall-clock tick and then written only
     * when the track ended in one of five specific ways. Both halves of that were wrong.
     *
     * *Wall-clock ticks are not listening time.* The counter climbed at the same rate at 2× speed as at
     * 1×, and a seek backwards over the same chorus counted the chorus twice. What it measured was how long
     * the app had been open with something playing.
     *
     * *A listen that ended any other way was thrown away.* Closing the app, stopping, or loading something
     * else discarded the counter without writing anything — you could listen to a whole album and have none
     * of it recorded. Every ending now goes through [flushListenSession].
     *
     * The accounting is [com.alananasss.kittytune.data.stats.ListenSessionAccumulator], which is the same
     * file the desktop uses and is tested on its own.
     */
    private var listenSession: com.alananasss.kittytune.data.stats.ListenSessionAccumulator? = null
    private var listenSessionTrack: Track? = null

    /** Media milliseconds heard in the current listen. */
    val currentSessionListenMs: Long get() = listenSession?.listenedMs ?: 0L

    /** Starts accounting for [track] from [startPositionMs], writing out whatever was in progress first. */
    private fun beginListenSession(track: Track?, startPositionMs: Long = 0L) {
        flushListenSession("TRACK_CHANGE")
        listenSessionTrack = track
        listenSession = track?.let {
            com.alananasss.kittytune.data.stats.ListenSessionAccumulator(startPositionMs)
        }
    }

    /**
     * Makes sure the track that is playing has a session, whichever code path started it.
     *
     * Auto-advance inside the player does not go through [playTrackAtIndex], so relying on the explicit
     * calls alone left gapless transitions unrecorded. Called from the progress loop, where "something is
     * playing" is known to be true.
     */
    private fun ensureListenSession() {
        val track = currentTrack ?: return
        if (listenSession != null && listenSessionTrack?.id == track.id) return
        beginListenSession(track, currentPosition)
        listenSession?.onPlaying(currentPosition)
    }

    // --- Cumulative Session Stats (since app start / listening session) ---
    private var _sessionListenMs = mutableLongStateOf(0L)
    private var _sessionPlaysCount = mutableIntStateOf(0)

    /** Total media ms heard during this app session (including current track's progress). */
    val sessionTotalListenMs: Long
        get() = _sessionListenMs.longValue + (listenSession?.listenedMs ?: 0L)

    /** Whether the current track has accumulated enough listen time to count as a play. */
    val currentTrackCountsAsPlay: Boolean
        get() = currentTrack?.let {
            com.alananasss.kittytune.data.stats.ListenRules.countsAsPlay(
                listenSession?.listenedMs ?: 0L,
                it.durationMs ?: 0L
            )
        } ?: false

    /** Total tracks that reached a valid play during this app session. */
    val effectiveSessionPlays: Int
        get() = _sessionPlaysCount.intValue + if (currentTrackCountsAsPlay) 1 else 0

    /**
     * Writes the listen in progress, if any of it was heard, and clears it.
     *
     * Safe to call repeatedly and from anywhere: the session is cleared first, so two callers racing to end
     * the same listen cannot record it twice.
     *
     * @param reason how the listen ended, kept for detail only — no aggregate depends on it any more,
     *   beyond counting deliberate replays and loops as the things they are.
     */
    fun flushListenSession(reason: String) {
        val session = listenSession ?: return
        val track = listenSessionTrack
        listenSession = null
        listenSessionTrack = null
        if (track == null) return

        val wasPlay = com.alananasss.kittytune.data.stats.ListenRules.countsAsPlay(session.listenedMs, track.durationMs ?: 0L)
        if (wasPlay) {
            _sessionPlaysCount.intValue += 1
        }
        _sessionListenMs.longValue += session.listenedMs

        // The wave learns from this listen, whatever the stats setting says: a skip that heard nothing
        // of the song teaches it more than a completed play does (PR #77).
        if (currentContext?.navigationId == MY_WAVE_NAV_ID) {
            when (reason) {
                "PLAY_COMPLETE" -> com.alananasss.kittytune.data.wave.WaveFeedback.onCompleted(track)
                "SKIP_NEXT", "SKIP_PREVIOUS", "MANUAL_REPLAY" ->
                    com.alananasss.kittytune.data.wave.WaveFeedback.onSkipped(track, session.listenedMs, track.durationMs ?: 0L)
            }
        }

        if (!playerPrefs.getListeningStatsEnabled()) return

        // Nothing heard at all is not a listen and not a skip — it is a track that was loaded. Recording it
        // would put a row in the table that every aggregate then has to exclude, and would make the skip
        // rate a measure of how often the next button was pressed while something loaded.
        if (session.listenedMs <= 0L) return

        ListeningStatsRepository.recordEvent(
            track = track,
            eventType = reason,
            listenDurationMs = session.listenedMs,
            furthestPositionMs = session.furthestPositionMs,
        )
    }

    private var hasPushedRecentlyPlayed = false
    var sleepTimerRemainingMs by mutableLongStateOf(0L)
    var sleepTimerEndOfTrack by mutableStateOf(false)
    var showSleepTimerDialog by mutableStateOf(false)
    // The night screen (desktop PR #77's sleep screen, mobile): a true-black clock left on while the
    // music plays; opened from the player's menu, woken by a tap or the back button.
    var showNightScreen by mutableStateOf(false)
    val isSleepTimerActive: Boolean
        get() = if (remotePeer?.snapshot != null) {
            (remotePeer?.snapshot?.sleepTimerRemainingMs ?: 0L) > 0L || remotePeer?.snapshot?.sleepTimerEndOfTrack == true
        } else {
            sleepTimerRemainingMs > 0L || sleepTimerEndOfTrack
        }
    private var sleepTimerJob: Job? = null
    private var preFadeVolume: Float = 1f

    private var pendingSeekPosition: Long? = null
    private var saveQueueJob: Job? = null
    private companion object {
        val STATION_STEM_REGEX = Regex("""(?i)\s*([\(\[\-]\s*(slowed(\s*\+\s*reverb)?|sped\s*up|nightcore|hardstyle\s*edit)\s*[\)\]]?)""")
        fun normalizeStationTitle(title: String?): String {
            if (title.isNullOrBlank()) return ""
            return STATION_STEM_REGEX.replace(title, "").trim().lowercase()
        }

        /**
         * How often the trim watcher looks at the clock (issue #33).
         *
         * Twenty milliseconds, not the progress loop's one second: a whole second of the verse you asked to
         * remove is precisely what would make this feel half-finished.
         */
        const val TRIM_TICK_MS = 20L
        const val TOGETHER_NAV_PREFIX = "together:"
        const val TRAILER_NAV_PREFIX = "trailer:"
        private const val TRAILER_SONGS = 10
        private const val TRAILER_SNIPPET_MS = 20_000L
        private const val TRAILER_TICK_MS = 100L
        private const val TRAILER_SETTLE_MS = 900L
        private const val TRAILER_SLACK_MS = 1_500L
        private const val TRAILER_START = 0.3
        const val MY_WAVE_NAV_ID = "my_wave"
        private const val TOGETHER_MAX_DRIFT_MS = 2_000L
        private const val WAVE_LOW_WATER = 4

        /** One source gets this long to answer before the parallel lyrics race moves on without it. */
        private const val LYRICS_SOURCE_TIMEOUT_MS = 7_000L

        /** How long each volume ramp takes. Short enough not to read as a gap, long enough to leave no click. */
        const val TRIM_FADE_MS = 90L

        /** Steps in that ramp. Enough to be smooth at 90 ms, few enough to cost nothing. */
        const val TRIM_FADE_STEPS = 9

        /**
         * Below this, a jump is treated as "playback had not really started" and the fade-out is skipped — a
         * kept range beginning at 00:30 fires on the first tick, and a ramp-down from an intro nobody heard is
         * just a wobble at the top of every play.
         */
        const val TRIM_SILENT_SEEK_MS = 600L

        /** How long to wait for audio to come back after a trim seek before fading in regardless. */
        const val TRIM_RESUME_TIMEOUT_MS = 1_500L
    }

    // --- trim / smart skip -----------------------------------------------------------------------

    /**
     * The current track's trim, if it has one (issue #33).
     *
     * The desktop's feature, brought over. SoundCloud is full of re-uploads that exist only because someone
     * wanted a song without its guest verse or without a long intro; this is that, done in the player. A few
     * remembered timestamps, skipped over on the fly. The audio file is never touched, so clearing the trim
     * gives the original back.
     */
    var currentTrim by mutableStateOf(com.alananasss.kittytune.audio.TrackTrim.none())
        private set

    /** Whether the trim editor is open. */
    var showTrimDialog by mutableStateOf(false)

    /** Whether the DJ Flow developer debug console is open. */
    /**
     * True while the player UI that renders the live beat grid is on screen.
     *
     * The 25 Hz position ticker exists only to drive those animations. With the screen off or
     * the player closed there is nothing to animate, and running it anyway costs close to a
     * full CPU core for no visible effect - measured at ~232 % vs ~135 % with DJ Flow off.
     */
    /** Guards against repeated skip presses; see [requestSkipNext]. */
    private var lastSkipRequestAt: Long = 0L

    var isDjBeatUiVisible by mutableStateOf(false)

    var showDjDebugSheet by mutableStateOf(false)

    private var djCustomCrossfadeDurationMs: Long? = null
    private var djCustomStartPositionMs: Long? = null
    private var djCustomTempoRatio: Float? = null
    private var djCustomPhaseOffsetMs: Long? = null

    /** Modular DJ Flow controller orchestrating non-stop harmonic transitions. */
    val djFlowController: DjFlowController = DjFlowController(
        context = getApplication<Application>().applicationContext,
        scope = viewModelScope,
        prefs = playerPrefs,
        onSeekTo = { pos ->
            try {
                player.seekTo(pos)
                currentPosition = pos
            } catch (_: Exception) {}
        }
    ).apply {
        onExecuteTransition = { targetTrack, crossfadeDurationMs ->
            if (targetTrack != null) {
                val existingIdx = _queue.indexOfFirst { it.id == targetTrack.id }
                if (existingIdx > currentQueueIndex + 1) {
                    val item = _queue.removeAt(existingIdx)
                    _queue.add(currentQueueIndex + 1, item)
                    queueState = _queue.toList()
                } else if (existingIdx < 0) {
                    _queue.add(currentQueueIndex + 1, targetTrack)
                    queueState = _queue.toList()
                }
            }
            val djState = flowState.value
            djCustomCrossfadeDurationMs = crossfadeDurationMs
            djCustomStartPositionMs = djState.incomingDropPointMs
            djCustomTempoRatio = djState.sync.tempoRatio
            djCustomPhaseOffsetMs = djFlowController.livePhaseOffsetMs
            MusicManager.beginCrossfadeRequest()
            com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(null)
            playNext(manual = false, isCrossfade = true)
        }
        onRequestAutonomousTransition = { targetTrack, startPositionMs, crossfadeDurationMs, tempoRatio, phaseOffsetMs ->
            val existingIdx = _queue.indexOfFirst { it.id == targetTrack.id }
            if (existingIdx > currentQueueIndex + 1) {
                val item = _queue.removeAt(existingIdx)
                _queue.add(currentQueueIndex + 1, item)
                queueState = _queue.toList()
            } else if (existingIdx < 0) {
                _queue.add(currentQueueIndex + 1, targetTrack)
                queueState = _queue.toList()
            }
            djCustomCrossfadeDurationMs = crossfadeDurationMs
            djCustomStartPositionMs = startPositionMs
            djCustomTempoRatio = tempoRatio
            djCustomPhaseOffsetMs = phaseOffsetMs
            MusicManager.beginCrossfadeRequest()
            com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(null)
            playNext(manual = false, isCrossfade = true)
        }
        onRequestPromoteTrack = { fromIdx, toIdx ->
            moveQueueItem(fromIdx, toIdx)
        }
        onRequestPrebuffer = { targetTrack, startPos ->
            if (!MusicManager.isCrossfadingOut && !MusicManager.isPrebuffered(targetTrack.id)) {
                val automixPlan = com.alananasss.kittytune.audio.automix.AutomixManager.currentAutomixPlan
                triggerPrebuffer(targetTrack, automixPlan)
            }
        }
        onRequestAppendToQueue = { tracks ->
            addToQueue(tracks)
        }
        onRequestPlayTracks = { tracks ->
            playPlaylist(tracks)
        }
    }

    private var trimJob: Job? = null
    private var trimWatchJob: Job? = null

    /** Set while a trim jump is fading, so the watcher does not fire again mid-jump. */
    @Volatile
    private var trimJumpInProgress = false

    /** Loads the trim for [trackId], or clears it. Cheap enough to call on every track change. */
    private fun loadTrimFor(trackId: Long?) {
        trimJob?.cancel()
        if (trackId == null) {
            currentTrim = com.alananasss.kittytune.audio.TrackTrim.none()
            return
        }
        trimJob = viewModelScope.launch {
            currentTrim = com.alananasss.kittytune.data.TrackTrimRepository.get(trackId)
        }
    }

    /** Replaces the trim for the track playing now, and applies it immediately. */
    fun saveCurrentTrim(trim: com.alananasss.kittytune.audio.TrackTrim) {
        val trackId = currentTrack?.id ?: return
        currentTrim = trim
        viewModelScope.launch {
            com.alananasss.kittytune.data.TrackTrimRepository.put(trackId, trim)
            // Applied at once rather than at the next track: editing a trim while listening to the part you
            // are removing and having it keep playing is a confusing way to find out it worked.
            applyTrimNow()
        }
    }

    fun clearCurrentTrim() {
        val trackId = currentTrack?.id ?: return
        currentTrim = com.alananasss.kittytune.audio.TrackTrim.none()
        viewModelScope.launch { com.alananasss.kittytune.data.TrackTrimRepository.remove(trackId) }
    }

    /**
     * Watches the clock for the moment a trim applies.
     *
     * Its own loop rather than a hook in the progress updater, which reports once a second: a whole second of
     * the verse you asked to remove is exactly the thing that would make this feel unfinished. The tick reads
     * one position, so running it fifty times faster costs nothing measurable.
     */
    private fun startTrimWatcher() {
        if (trimWatchJob != null) return
        trimWatchJob = viewModelScope.launch {
            while (isActive) {
                if (!isPlaying || trimJumpInProgress || currentTrim.isEmpty) {
                    delay(500L)
                    continue
                }
                delay(TRIM_TICK_MS)
                if (!isPlaying || trimJumpInProgress || currentTrim.isEmpty) continue
                applyTrimNow()
            }
        }
    }

    private suspend fun applyTrimNow() {
        val trim = currentTrim
        if (trim.isEmpty) return
        val duration = if (player.duration > 0) player.duration else (currentTrack?.actualDurationMs ?: 0L)
        when (val action = trim.actionFor(player.currentPosition, duration)) {
            is com.alananasss.kittytune.audio.TrimAction.Continue -> Unit

            is com.alananasss.kittytune.audio.TrimAction.JumpTo -> jumpWithFade(action.positionMs)

            is com.alananasss.kittytune.audio.TrimAction.Finished -> {
                // Treated as the track running out, so repeat, the queue and the listening statistics all see
                // what they would have seen if the file itself had ended here.
                trimJumpInProgress = true
                val restore = player.volume
                try {
                    fadeVolumeTo(0f)
                    flushListenSession("PLAY_COMPLETE")
                    playNext(manual = false)
                } finally {
                    player.volume = restore
                    trimJumpInProgress = false
                }
            }
        }
    }

    /**
     * Seeks to [positionMs] with a short fade either side.
     *
     * The fade is why this sounds like an edit rather than a fault: a bare seek cuts the waveform mid-cycle
     * and clicks. Measured on the desktop, the whole transition is about 300 ms, most of it the seek draining
     * and re-priming the decoder — which is why the ramp back waits for audio to actually resume instead of
     * running on the wall clock and finishing during the silence (issue #33).
     */
    private suspend fun jumpWithFade(positionMs: Long) {
        trimJumpInProgress = true
        val restore = player.volume
        try {
            // A sleep-timer fade is already driving the volume; taking it over would leave the timer's own
            // restore fighting ours. The jump still happens, just without the ramp.
            val canFade = !isSleepTimerActive

            // Fading *out* of something nobody has heard yet is a stutter, not a transition: a trim that moves
            // the start of the track fires within a tick of playback beginning.
            val fromTheTop = player.currentPosition < TRIM_SILENT_SEEK_MS
            if (canFade && !fromTheTop) fadeVolumeTo(0f) else if (canFade) player.volume = 0f

            player.seekTo(positionMs)
            currentPosition = positionMs
            listenSession?.onSeek(positionMs)

            if (canFade) {
                awaitPlaybackResumed(positionMs)
                fadeVolumeTo(restore)
            }
        } finally {
            if (!isSleepTimerActive) player.volume = restore
            trimJumpInProgress = false
        }
    }

    /**
     * Waits until the player is actually producing audio again after a seek to [seekedToMs].
     *
     * Bounded, because a seek that never completes must not leave the track muted: past the deadline the fade
     * runs anyway and the worst case is the hard edge we had before.
     */
    private suspend fun awaitPlaybackResumed(seekedToMs: Long) {
        val deadline = System.currentTimeMillis() + TRIM_RESUME_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (!isPlaying) return
            if (player.currentPosition > seekedToMs) return
            delay(TRIM_TICK_MS)
        }
    }

    private suspend fun fadeVolumeTo(target: Float) {
        val from = player.volume
        for (step in 1..TRIM_FADE_STEPS) {
            val fraction = step.toFloat() / TRIM_FADE_STEPS
            player.volume = (from + (target - from) * fraction).coerceIn(0f, 1f)
            delay(TRIM_FADE_MS / TRIM_FADE_STEPS)
        }
        player.volume = target.coerceIn(0f, 1f)
    }

    private var lyricsJob: Job? = null

    /** Resolves the *next* track's lyrics while this one plays, so they are up the instant it starts. */
    private var lyricsPrefetchJob: Job? = null

    /**
     * Weight given to the lyrics provider listed first in the settings, when two results are otherwise
     * equally good.
     *
     * Far below the gap between sync tiers, and below any meaningful difference in match quality: a
     * preference should settle a tie, not override a better match or a genuinely synced result from the other
     * provider (issue #33).
     */
    private val PROVIDER_PREFERENCE_BONUS = 0.05f
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

    private fun getString(resId: Int): String =
        com.alananasss.kittytune.utils.LocaleUtils.updateBaseContextLocale(getApplication()).getString(resId)

    private fun getString(resId: Int, vararg args: Any): String =
        com.alananasss.kittytune.utils.LocaleUtils.updateBaseContextLocale(getApplication()).getString(resId, *args)

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
            val transitionActive = MusicManager.isCrossfadingOut || MusicManager.isTransitionRunning()
            if (!isPlayingState && transitionActive && MusicManager.playWhenReadyRequested) {
                return
            }
            isPlaying = isPlayingState
            // The gap while paused must not count, and what comes after it must.
            if (isPlayingState) listenSession?.onPlaying(currentPosition) else listenSession?.onPaused()
            if (isPlayingState) {
                restoreDismissedPlayerStateIfNeeded()
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
                currentPosition = MusicManager.player.currentPosition.coerceAtLeast(0L)
                if (MusicManager.player.duration > 0) {
                    val exoDuration = MusicManager.player.duration
                    val trackDuration = currentTrack?.actualDurationMs ?: 0L
                    duration = exoDuration

                    // Detect preview / wrong-stream situations: if ExoPlayer reports a duration
                    // that dramatically differs from the track's expected duration, it is very
                    // likely that the resolved stream is a preview, a snippet, or an entirely
                    // different recording. Log this so it is diagnosable (issue #33).
                    if (trackDuration > 60_000L && exoDuration > 0L) {
                        val ratio = exoDuration.toDouble() / trackDuration.toDouble()
                        if (ratio < 0.3 || ratio > 5.0) {
                            Log.w(
                                "PlayerViewModel",
                                "Duration mismatch: ExoPlayer=${exoDuration}ms vs Track=${trackDuration}ms " +
                                        "(ratio=${String.format("%.2f", ratio)}) for '${currentTrack?.title}' — " +
                                        "possible preview or wrong stream"
                            )
                        }
                    }
                }
                pendingSeekPosition?.let {
                    MusicManager.player.seekTo(it)
                    currentPosition = it
                    pendingSeekPosition = null
                }
            }
            if (state == Player.STATE_BUFFERING) isLoading = true

            if (state == Player.STATE_ENDED) {
                SoundCloudTelemetryTracker.onTrackCompleted()
                AchievementManager.increment("no_skip_50")
                incrementPlayCount()
                flushListenSession(
                    if (repeatMode == RepeatMode.ONE) "REPEAT_ONE_LOOP" else "PLAY_COMPLETE"
                )
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
                    if (MusicManager.isTransitionRunning()) {
                        Log.d("PlayerViewModel", "STATE_ENDED ignored: crossfade transition is actively executing")
                    } else if (trackInitJob?.isActive == true || playJob?.isActive == true) {
                        Log.d("PlayerViewModel", "STATE_ENDED while next track is loading; awaiting resolution completion")
                    } else {
                        MusicManager.cancelCrossfade()
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
            try {
                MusicManager.player.pause()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            super.onPositionDiscontinuity(oldPosition, newPosition, reason)

            com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(context)
                .onPositionDiscontinuity(newPosition.positionMs)

            if (reason == Player.DISCONTINUITY_REASON_SEEK || reason == Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT) {
                currentPosition = MusicManager.player.currentPosition
                // The distance jumped over is not listening; what follows it is. Without this, dragging
                // the scrubber to the end credited the whole track.
                listenSession?.onSeek(currentPosition)
                saveStateAsync(saveQueue = false)
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            super.onMediaItemTransition(mediaItem, reason)
            if (mediaItem == null) return

            com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(context)
                .onPositionDiscontinuity(0L)

            if (MusicManager.isCrossfadingOut) {
                return
            }

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                if (repeatMode == RepeatMode.ALL && MusicManager.player.mediaItemCount == 1) {
                    MusicManager.player.pause()
                    playNext(manual = false)
                    return
                }
            }

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                if (repeatMode == RepeatMode.ONE) {
                    return
                }
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
            if (trackId != 0L && BlockManager.isTrackBlocked(trackId)) {
                playNext(manual = false)
                return
            }

            val expectedTrackId = _queue.getOrNull(currentQueueIndex)?.id
            if (expectedTrackId != null && expectedTrackId != trackId) {
                return
            }

            if (currentTrack?.id != trackId) {
                // Whatever was playing has ended, however it ended. The new track's session is opened by
                // the progress loop, which knows the position it actually started from.
                flushListenSession("TRACK_CHANGE")
                loadTrimFor(MusicManager.currentTrack?.id)
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
        MusicManager.isLoadingTrack = false
        MusicManager.pendingSeekPositionMs = null
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
            showEqualizerScreen = false
        }
    }

    fun dismissMiniPlayerAndShowUndo() {
        val track = currentTrack ?: return
        dismissedTrack = track
        dismissedQueue = queueState.toList()
        dismissedQueueIndex = currentQueueIndex
        dismissedPosition = currentPosition
        showDismissUndoBar = true
        isMiniPlayerDismissing = false

        try {
            MusicManager.player.pause()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentTrack = null
        MusicManager.currentTrack = null
        isPlaying = false
    }

    fun undoDismissMiniPlayer() {
        val track = dismissedTrack ?: return
        currentTrack = track
        MusicManager.currentTrack = track
        if (dismissedQueue.isNotEmpty()) {
            _queue.clear()
            _queue.addAll(dismissedQueue)
            updateQueueState()
        }
        currentQueueIndex = dismissedQueueIndex
        currentPosition = dismissedPosition
        seekTo(dismissedPosition)
        togglePlayPause()
        showDismissUndoBar = false
        dismissedTrack = null
    }

    fun hideDismissUndoBar() {
        showDismissUndoBar = false
    }

    fun restoreDismissedPlayerStateIfNeeded(): Boolean {
        if (currentTrack != null) return false
        val trackToRestore = dismissedTrack
            ?: _queue.getOrNull(currentQueueIndex)
            ?: dismissedQueue.getOrNull(dismissedQueueIndex)
        if (trackToRestore != null) {
            currentTrack = trackToRestore
            MusicManager.currentTrack = trackToRestore
            if (_queue.isEmpty() && dismissedQueue.isNotEmpty()) {
                _queue.addAll(dismissedQueue)
                updateQueueState()
            }
            if (currentQueueIndex < 0 && dismissedQueueIndex >= 0) {
                currentQueueIndex = dismissedQueueIndex
            }
            showDismissUndoBar = false
            dismissedTrack = null
            return true
        }
        return false
    }

    fun onAiTrackSkipped(track: Track, queueIndex: Int, wasBlocked: Boolean) {
        aiSkippedTrack = track
        aiSkippedQueueIndex = queueIndex
        aiSkippedWasBlocked = wasBlocked
        showAiSkipUndoBar = true
    }

    fun undoAiSkip() {
        val track = aiSkippedTrack ?: return
        val queueIdx = aiSkippedQueueIndex
        showAiSkipUndoBar = false
        whitelistedAiTrackIds.add(track.id)
        viewModelScope.launch {
            if (aiSkippedWasBlocked) {
                BlockManager.unblockTrack(track.id)
            }
            if (queueIdx in 0 until _queue.size && _queue[queueIdx].id == track.id) {
                skipToQueueItem(queueIdx)
            } else {
                playTrackAtPosition(track, 0L)
            }
        }
        aiSkippedTrack = null
    }

    fun hideAiSkipUndoBar() {
        showAiSkipUndoBar = false
        aiSkippedTrack = null
    }

    /**
     * Seeds the app's palette from the cover that is playing (issue #33).
     *
     * Keyed on the artwork URL rather than on the track, so a queue of one album's tracks sharing a sleeve
     * extracts once instead of once per track — and a track whose metadata is refreshed mid-play does not
     * repaint the app for no reason.
     *
     * Failure is silent and means "keep the colour the user chose", which is a better answer than a grey
     * approximation of a black sleeve.
     */
    private fun observeArtworkColors() {
        viewModelScope.launch {
            snapshotFlow { currentTrack }
                .distinctUntilChangedBy { it?.fullResArtwork }
                .collect { track ->
                    val artworkColors =
                        com.alananasss.kittytune.ui.theme.CoverSeed.extractColors(context, track?.fullResArtwork)
                    com.alananasss.kittytune.ui.theme.ThemeState.coverSeedColor = artworkColors.seedColor
                    com.alananasss.kittytune.ui.theme.ThemeState.coverMeshColors = artworkColors.meshColors
                }
        }
    }

    private fun observeAnimatedCovers() {
        viewModelScope.launch {
            combine(
                snapshotFlow { currentTrack },
                playerPrefs.getAnimatedCoversFlow()
            ) { track: Track?, enabled: Boolean ->
                track to enabled
            }
            .distinctUntilChanged()
            .collectLatest { (track, enabled) ->
                Log.d("PlayerViewModel", "observeAnimatedCovers triggered: enabled=$enabled, track=${track?.title} by ${track?.displayArtist}")
                currentAnimatedCoverUrl = null
                currentAnimatedCoverTallUrl = null
                if (!enabled || track == null || com.alananasss.kittytune.data.DataSaver.isActive(getApplication())) return@collectLatest

                val title = track.title?.trim().orEmpty()
                val artist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }.trim()
                val album = track.publisherMetadata?.albumTitle
                val durationSec = (track.actualDurationMs / 1000L).toInt().takeIf { it > 0 }
                val isrc = track.publisherMetadata?.isrc

                val resolved = com.alananasss.kittytune.data.cover.AnimatedCoverResolver.resolve(
                    title = title,
                    artist = artist,
                    album = album,
                    durationSeconds = durationSec,
                    isrc = isrc
                )
                if (currentTrack?.id == track.id) {
                    currentAnimatedCoverUrl = resolved.squareUrl
                    currentAnimatedCoverTallUrl = resolved.tallUrl
                }
            }
        }
    }

    init {
        val filter = IntentFilter("com.alananasss.kittytune.ACTION_FORCE_UPDATE")
        ContextCompat.registerReceiver(context, syncReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        SoundCloudTelemetryTracker.init(context)
        MusicManager.init(context)
        BlockManager.init(context)
        com.alananasss.kittytune.data.audio.HeadphoneDetector.start(context)
        viewModelScope.launch {
            com.alananasss.kittytune.data.audio.HeadphoneDetector.device.collect { device ->
                maybeRestoreAutoEq(device)
            }
        }
        com.alananasss.kittytune.data.together.Together.init(context)
        com.alananasss.kittytune.data.together.Together.start(this)

        // Auto-skip when the current track is blocked
        BlockManager.onCurrentTrackBlocked = {
            viewModelScope.launch(Dispatchers.Main) {
                djFlowController.cancelTransition()
                playNext(manual = false, isCrossfade = false, ignoreRepeatOne = true)
            }
        }

        BlockManager.onTrackBlocked = { blockedTrackId ->
            viewModelScope.launch(Dispatchers.Main) {
                if (currentTrack?.id == blockedTrackId) {
                    djFlowController.cancelTransition()
                    playNext(manual = false, isCrossfade = false, ignoreRepeatOne = true)
                }
                val removed = _queue.removeAll { it.id == blockedTrackId }
                if (removed) {
                    queueState = _queue.toList()
                }
            }
        }

        BlockManager.onArtistBlocked = { blockedArtistId ->
            viewModelScope.launch(Dispatchers.Main) {
                if (currentTrack?.user?.id == blockedArtistId) {
                    djFlowController.cancelTransition()
                    playNext(manual = false, isCrossfade = false, ignoreRepeatOne = true)
                }
                val removed = _queue.removeAll { it.user?.id == blockedArtistId }
                if (removed) {
                    queueState = _queue.toList()
                }
            }
        }

        // Observe ArtifactNet results: auto-skip + block if user enabled it
        viewModelScope.launch {
            AiDetectionManager.result
                .filterNotNull()
                .filter { it.status == AiDetectionManager.Status.DONE }
                .collect { result ->
                    val prefs = PlayerPreferences(context)
                    val isAi = result.score >= prefs.aiScoreThreshold
                    if (isAi && prefs.aiAutoSkip) {
                        val track = currentTrack ?: return@collect
                        // Check if whitelisted for this session (e.g. user previously clicked Undo)
                        if (whitelistedAiTrackIds.contains(track.id)) {
                            return@collect
                        }
                        // Requirement 1: Spare favorite / liked tracks (default true)
                        if (prefs.aiSpareFavorites && (isLiked || LikeRepository.likedTracks.value.any { it.id == track.id })) {
                            android.util.Log.d("PlayerViewModel", "Track '${track.title}' is liked; sparing from AI auto-skip")
                            return@collect
                        }

                        val skippedTrack = track
                        val queueIdx = currentQueueIndex
                        val wasBlocked = prefs.aiAutoBlock
                        if (wasBlocked) {
                            BlockManager.blockTrack(
                                skippedTrack,
                                reason = BlockManager.REASON_AI_GENERATED,
                                skipIfCurrent = true,
                                currentlyPlayingId = skippedTrack.id
                            )
                        } else {
                            djFlowController.cancelTransition()
                            playNext(manual = false, isCrossfade = false, ignoreRepeatOne = true)
                        }
                        // Requirement 2: Show Undo Bar with exact UI
                        onAiTrackSkipped(skippedTrack, queueIdx, wasBlocked)
                    }
                }
        }

        // Observe track and queue changes for background AI pre-analysis of the next 1–2 tracks
        viewModelScope.launch(Dispatchers.Default) {
            snapshotFlow {
                val trackId = currentTrack?.id ?: 0L
                val queueSize = queueState.size
                val idx = currentQueueIndex
                Triple(trackId, queueSize, idx)
            }.collect { (_, _, idx) ->
                if (AiDetectionManager.isModelReady(context)) {
                    val q = queueState
                    if (idx in q.indices) {
                        val next1 = q.getOrNull(idx + 1)
                        val next2 = q.getOrNull(idx + 2)
                        val candidates = listOfNotNull(next1, next2)
                        if (candidates.isNotEmpty()) {
                            AiDetectionManager.preAnalyzeQueue(context, candidates)
                        }
                    }
                }
            }
        }

        bindToActivePlayer()
        MusicManager.applyEffects(effectsState)
        MusicManager.applyEqualizer(equalizerState)
        applyRepeatMode()
        observeArtworkColors()
        observeAnimatedCovers()
        startTrimWatcher()

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
                            val radioKind = RadioEligibility.radioKind(currentTrack)
                            val isYoutube = radioKind == RadioEligibility.Kind.YOUTUBE
                            if ((autoPlayEnabled || isYoutube) && radioKind != RadioEligibility.Kind.NONE) {
                                viewModelScope.launch {
                                    val youtubeFallback = playerPrefs.getYouTubeFallbackEnabled()
                                    when (radioKind) {
                                        RadioEligibility.Kind.SPOTIFY -> fetchAndQueueSpotifyRadio()
                                        RadioEligibility.Kind.TIDAL -> fetchAndQueueTidalRadio()
                                        RadioEligibility.Kind.DEEZER -> fetchAndQueueDeezerRadio()
                                        RadioEligibility.Kind.QOBUZ -> fetchAndQueueQobuzRadio()
                                        RadioEligibility.Kind.YOUTUBE -> fetchAndPlayYoutubeRadio()
                                        RadioEligibility.Kind.SOUND_CLOUD -> {
                                            if (currentTrack?.source == "soundcloud" && youtubeFallback) {
                                                fetchAndPlayYoutubeRadio()
                                            } else {
                                                fetchAndQueueRadio()
                                            }
                                        }
                                        RadioEligibility.Kind.NONE -> Unit
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

        MusicManager.onNextClick = { requestSkipNext() }
        MusicManager.onPreviousClick = { smartPrevious() }
        MusicManager.onShuffleClick = { toggleShuffle() }

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
            MusicManager.currentLyricsFlow.value = emptyList()
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
                        .map { pl ->
                            if (pl.localCoverPath.isNullOrEmpty()) {
                                val localCoverFile = java.io.File(context.filesDir, "playlist_cover_${pl.id}.jpg")
                                if (localCoverFile.exists()) {
                                    pl.copy(localCoverPath = localCoverFile.absolutePath)
                                } else pl
                            } else pl
                        }
                userPlaylists.addAll(sorted)
            }
        }
        ConnectManager.configure(::connectSnapshot, ::handleConnectCommand)
        observeConnectPlayer()
        restoreSession()
        syncWithCurrentPlayback()
        viewModelScope.launch {
            SyncPlayback.latest.collect { snapshot ->
                if (syncPlaybackReady && SyncPlayback.enabled && snapshot != null &&
                    snapshot.deviceId != SyncLog.deviceId && snapshot.isPlaying && isPlaying && !ConnectManager.hasLivePeer()) {
                    playWhenReady = false
                    player.pause()
                }
            }
        }
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
            val transitionActive = MusicManager.isCrossfadingOut || MusicManager.isTransitionRunning()
            isPlaying = activePlayer.isPlaying || (transitionActive && MusicManager.playWhenReadyRequested)
            duration = activePlayer.duration.coerceAtLeast(0L)
            currentPosition = activePlayer.currentPosition
            if (isPlaying) startProgressUpdate()
        } catch (_: Exception) {
        }
    }

    fun toggleInlineLyrics() {
        val multiStateEnabled = playerPrefs.getLyricsMultiStateToggle()
        val underCoverEnabled = playerPrefs.getLyricsUnderCoverEnabled()
        val hasSyncedLyrics = lyricsLines.isNotEmpty()

        if (multiStateEnabled && underCoverEnabled && hasSyncedLyrics) {
            lyricsDisplayState = when (lyricsDisplayState) {
                LyricsDisplayState.OFF -> LyricsDisplayState.UNDER_COVER
                LyricsDisplayState.UNDER_COVER -> LyricsDisplayState.COVER_REPLACED
                LyricsDisplayState.COVER_REPLACED -> LyricsDisplayState.OFF
            }
        } else {
            lyricsDisplayState = if (lyricsDisplayState == LyricsDisplayState.COVER_REPLACED) {
                LyricsDisplayState.OFF
            } else {
                LyricsDisplayState.COVER_REPLACED
            }
        }
    }

    override fun onCleared() {
        remoteTicker?.cancel()
        remoteVolumeJob?.cancel()
        super.onCleared()
        // The listen in progress is written here rather than dropped. This is the ending that used to lose
        // the most: an app swiped away mid-album recorded nothing at all (issue #33).
        flushListenSession("APP_EXIT")
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
            val cleanQueue = BlockManager.filterBlocked(lastQueue)
            val cleanTrack = if (lastTrack != null && !BlockManager.isBlocked(lastTrack)) lastTrack else null
            val lastContext = playerPrefs.getLastContext()
            val lastShuffle = playerPrefs.getLastShuffleEnabled()
            val lastRepeat = playerPrefs.getLastRepeatMode()

            _queue.clear()
            _queue.addAll(cleanQueue)
            _originalQueue.clear()
            _originalQueue.addAll(cleanQueue)
            updateQueueState()

            currentTrack = cleanTrack
            currentContext = lastContext
            shuffleEnabled = lastShuffle
            repeatMode = lastRepeat
            applyRepeatMode()

            if (cleanTrack != null) {
                isLiked = LikeRepository.isTrackLiked(cleanTrack.id)
                currentQueueIndex = _queue.indexOfFirst { it.id == cleanTrack.id }.coerceAtLeast(0)
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
        com.alananasss.kittytune.audio.haptics.HapticUtil.performConfirmHaptic(context)
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
        com.alananasss.kittytune.audio.haptics.HapticUtil.performRejectHaptic(context)
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
        lyricsRevision++
        if (enabled && lyricsLines.isNotEmpty()) {
            fetchTranslationsForCurrentLines(lyricsTranslationLang)
        }
    }

    fun updateLyricsTranslationLang(lang: String) {
        lyricsTranslationLang = lang
        playerPrefs.setLyricsTranslationLang(lang)
        lyricsRevision++
        if (isLyricsTranslationEnabled && lyricsLines.isNotEmpty()) {
            fetchTranslationsForCurrentLines(lang)
        }
    }

    fun setLyricsTranslationLanguage(lang: String) = updateLyricsTranslationLang(lang)

    fun toggleRomanization(enabled: Boolean) {
        isRomanizationEnabled = enabled
        playerPrefs.setLyricsRomanizationEnabled(enabled)
        lyricsRevision++
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

    fun toggleDuetView(enabled: Boolean) {
        isDuetViewEnabled = enabled
        playerPrefs.setLyricsDuetViewEnabled(enabled)
    }

    fun toggleSplitBackingVocals(enabled: Boolean) {
        isSplitBackingVocalsEnabled = enabled
        playerPrefs.setLyricsSplitBackingVocalsEnabled(enabled)
    }

    fun toggleRevealWords(enabled: Boolean) {
        isRevealWordsEnabled = enabled
        playerPrefs.setLyricsRevealWordsEnabled(enabled)
    }

    fun isTrackDuetBlacklisted(trackId: Long): Boolean = duetBlacklist.contains(trackId.toString())

    fun toggleTrackDuetBlacklist(trackId: Long) {
        val currentlyBlacklisted = isTrackDuetBlacklisted(trackId)
        playerPrefs.setTrackDuetBlacklisted(trackId, !currentlyBlacklisted)
        duetBlacklist = playerPrefs.getLyricsDuetBlacklist()
    }

    fun isDuetActiveForTrack(track: Track?): Boolean =
        isDuetViewEnabled && (track == null || !isTrackDuetBlacklisted(track.id))

    fun updateLyricsUiStyle(style: com.alananasss.kittytune.data.local.LyricsUiStyle) {
        lyricsUiStyle = style
        playerPrefs.setLyricsUiStyle(style)
    }

    fun updateLyricsLineBlurEnabled(enabled: Boolean) {
        lyricsLineBlurEnabled = enabled
        playerPrefs.setLyricsLineBlurEnabled(enabled)
    }

    fun updateLyricsLrcBounceEnabled(enabled: Boolean) {
        lyricsLrcBounceEnabled = enabled
        playerPrefs.setLyricsLrcBounceEnabled(enabled)
    }

    fun updateLyricsBounceFactor(factor: Float) {
        lyricsBounceFactor = factor
        playerPrefs.setLyricsBounceFactor(factor)
    }

    fun updateLyricsGlowFactor(factor: Float) {
        lyricsGlowFactor = factor
        playerPrefs.setLyricsGlowFactor(factor)
    }

    fun updateLyricsFillTransitionWidth(width: Float) {
        lyricsFillTransitionWidth = width
        playerPrefs.setLyricsFillTransitionWidth(width)
    }

    fun updateLyricsLineSpacing(spacing: Float) {
        lyricsLineSpacing = spacing
        playerPrefs.setLyricsLineSpacing(spacing)
    }

    fun updateLyricsFont(font: com.alananasss.kittytune.data.local.LyricsFont) {
        lyricsFont = font
        playerPrefs.setLyricsFont(font)
    }

    fun updateLyricsSplitBackingVocals(enabled: Boolean) = toggleSplitBackingVocals(enabled)
    fun updateLyricsRevealWords(enabled: Boolean) {
        isRevealWordsEnabled = enabled
        playerPrefs.setLyricsRevealWordsEnabled(enabled)
    }

    fun setTrackAutoScrollSpeed(speed: Float) { trackAutoScrollSpeed = speed.coerceIn(0.25f, 4.0f) }
    fun clearTrackAutoScrollSpeed() { trackAutoScrollSpeed = null }
    fun updatePlainAutoScrollSpeed(speed: Float) { plainAutoScrollSpeed = speed.coerceIn(0.25f, 4.0f) }

    fun updateOneLineLyricsEnabled(enabled: Boolean) {
        oneLineLyricsEnabled = enabled
        playerPrefs.setOneLineLyricsEnabled(enabled)
    }

    fun updateFullPlayerCoverFeather(enabled: Boolean) {
        fullPlayerCoverFeather = enabled
        playerPrefs.setFullPlayerCoverFeather(enabled)
    }

    fun updateFullPlayerCoverFeatherAmount(amount: Float) {
        fullPlayerCoverFeatherAmount = amount.coerceIn(0f, 1f)
        playerPrefs.setFullPlayerCoverFeatherAmount(fullPlayerCoverFeatherAmount)
    }

    fun updateFullPlayerCoverScale(scale: Float) {
        fullPlayerCoverScale = scale
        playerPrefs.setFullPlayerCoverScale(scale)
    }

    fun updateFullPlayerBgStyle(style: com.alananasss.kittytune.data.local.FullPlayerBgStyle) {
        fullPlayerBgStyle = style
        playerPrefs.setFullPlayerBgStyle(style)
    }

    fun updateFullPlayerLayout(layout: com.alananasss.kittytune.data.local.FullPlayerLayout) {
        fullPlayerLayout = layout
        playerPrefs.setFullPlayerLayout(layout)
    }

    fun updateFullPlayerInfoAlign(align: com.alananasss.kittytune.data.local.FullPlayerInfoAlign) {
        fullPlayerInfoAlign = align
        playerPrefs.setFullPlayerInfoAlign(align)
    }

    fun updateFullPlayerHeartSide(side: com.alananasss.kittytune.data.local.FullPlayerHeartSide) {
        fullPlayerHeartSide = side
        playerPrefs.setFullPlayerHeartSide(side)
    }

    fun updateLyricsDisplayStyle(style: com.alananasss.kittytune.data.local.LyricsDisplayStyle) {
        lyricsDisplayStyle = style
        lyricsFullScreenDisplayStyle = style
        lyricsSidebarDisplayStyle = style
        playerPrefs.setLyricsDisplayStyle(style)
    }

    fun updateLyricsHorizontalMargin(margin: Float) {
        lyricsHorizontalMargin = margin
        lyricsFullScreenHorizontalMargin = margin
        lyricsSidebarHorizontalMargin = margin
        playerPrefs.setLyricsHorizontalMargin(margin)
    }

    fun updateLyricsVerticalOffset(offset: Float) {
        lyricsVerticalOffset = offset
        lyricsFullScreenVerticalOffset = offset
        lyricsSidebarVerticalOffset = offset
        playerPrefs.setLyricsVerticalOffset(offset)
    }

    fun updateLyricsActiveScale(scale: Float) {
        lyricsActiveScale = scale
        lyricsFullScreenActiveScale = scale
        lyricsSidebarActiveScale = scale
        playerPrefs.setLyricsActiveScale(scale)
    }

    fun updateLyricsWheelLines(lines: Float) {
        lyricsWheelLines = lines
        playerPrefs.setLyricsWheelLines(lines)
    }

    fun updateFullPlayerScreensaverEnabled(enabled: Boolean) {
        fullPlayerScreensaverEnabled = enabled
    }

    fun updateLyricsFullScreenFontSize(size: Float) {
        lyricsFullScreenFontSize = size
        updateLyricsFontSize(size)
    }

    fun updateLyricsSidebarFontSize(size: Float) {
        lyricsSidebarFontSize = size
        updateLyricsFontSize(size)
    }

    fun updateLyricsFullScreenUiStyle(style: com.alananasss.kittytune.data.local.LyricsUiStyle) {
        lyricsFullScreenUiStyle = style
        updateLyricsUiStyle(style)
    }

    fun updateLyricsSidebarUiStyle(style: com.alananasss.kittytune.data.local.LyricsUiStyle) {
        lyricsSidebarUiStyle = style
        updateLyricsUiStyle(style)
    }

    fun updateLyricsFullScreenAlignment(alignment: com.alananasss.kittytune.data.local.LyricsAlignment) {
        lyricsFullScreenAlignment = alignment
        updateLyricsAlignment(alignment)
    }

    fun updateLyricsSidebarAlignment(alignment: com.alananasss.kittytune.data.local.LyricsAlignment) {
        lyricsSidebarAlignment = alignment
        updateLyricsAlignment(alignment)
    }

    fun updateLyricsFullScreenDisplayStyle(style: com.alananasss.kittytune.data.local.LyricsDisplayStyle) {
        lyricsFullScreenDisplayStyle = style
        updateLyricsDisplayStyle(style)
    }

    fun updateLyricsSidebarDisplayStyle(style: com.alananasss.kittytune.data.local.LyricsDisplayStyle) {
        lyricsSidebarDisplayStyle = style
        updateLyricsDisplayStyle(style)
    }

    fun updateLyricsFullScreenLineSpacing(spacing: Float) {
        lyricsFullScreenLineSpacing = spacing
        updateLyricsLineSpacing(spacing)
    }

    fun updateLyricsSidebarLineSpacing(spacing: Float) {
        lyricsSidebarLineSpacing = spacing
        updateLyricsLineSpacing(spacing)
    }

    fun updateLyricsFullScreenHorizontalMargin(margin: Float) {
        lyricsFullScreenHorizontalMargin = margin
        updateLyricsHorizontalMargin(margin)
    }

    fun updateLyricsSidebarHorizontalMargin(margin: Float) {
        lyricsSidebarHorizontalMargin = margin
        updateLyricsHorizontalMargin(margin)
    }

    fun updateLyricsFullScreenVerticalOffset(offset: Float) {
        lyricsFullScreenVerticalOffset = offset
        updateLyricsVerticalOffset(offset)
    }

    fun updateLyricsSidebarVerticalOffset(offset: Float) {
        lyricsSidebarVerticalOffset = offset
        updateLyricsVerticalOffset(offset)
    }

    fun updateLyricsFullScreenActiveScale(scale: Float) {
        lyricsFullScreenActiveScale = scale
        updateLyricsActiveScale(scale)
    }

    fun updateLyricsSidebarActiveScale(scale: Float) {
        lyricsSidebarActiveScale = scale
        updateLyricsActiveScale(scale)
    }

    fun updateLyricsFullScreenLineBlurEnabled(enabled: Boolean) {
        lyricsFullScreenLineBlurEnabled = enabled
        updateLyricsLineBlurEnabled(enabled)
    }

    fun updateLyricsSidebarLineBlurEnabled(enabled: Boolean) {
        lyricsSidebarLineBlurEnabled = enabled
        updateLyricsLineBlurEnabled(enabled)
    }

    fun resetLyricsTypography(isFullScreen: Boolean, isSidebar: Boolean = false) {
        if (isFullScreen) {
            updateLyricsFullScreenFontSize(42f)
            updateLyricsFullScreenLineSpacing(0f)
            updateLyricsFullScreenHorizontalMargin(0f)
            updateLyricsFullScreenVerticalOffset(0.38f)
            updateLyricsFullScreenActiveScale(1.00f)
        } else if (isSidebar) {
            updateLyricsSidebarFontSize(22f)
            updateLyricsSidebarLineSpacing(0f)
            updateLyricsSidebarHorizontalMargin(0f)
            updateLyricsSidebarVerticalOffset(0.38f)
            updateLyricsSidebarActiveScale(1.00f)
        } else {
            updateLyricsFontSize(26f)
            updateLyricsLineSpacing(1.3f)
            updateLyricsHorizontalMargin(0f)
            updateLyricsVerticalOffset(0.38f)
            updateLyricsActiveScale(1.00f)
        }
    }

    private var translationJob: Job? = null

    private fun fetchTranslationsForCurrentLines(targetLang: String = lyricsTranslationLang) {
        translationJob?.cancel()
        if (lyricsLines.isEmpty()) return
        val trackId = lastFetchedMxmTrackId
        val originalLines = lyricsLines.map { it.text }.filter { it.isNotBlank() }.distinct()

        isTranslatingLyrics = true
        translationJob = viewModelScope.launch(Dispatchers.IO) {
            // The crowd's translations first when Musixmatch has them for this exact track: a human's reading
            // of the line beats a machine's every time.
            val known = mutableMapOf<String, String>()
            if (trackId != null) {
                try {
                    val token = MusixmatchClient.getValidToken(context)
                    val translationsRes = MusixmatchClient.api.getTranslations(trackId = trackId, lang = targetLang, token = token)
                    translationsRes.message.body?.translationsList?.forEach { wrapper ->
                        wrapper.translation?.let { t ->
                            known[t.matchedLine.trim()] = t.description.trim()
                        }
                    }
                } catch (_: Exception) {
                }
            }
            // Only what the reader cannot read already: foreign sentences whole, foreign words in their own
            // language's lines word by word, and nothing at all under lines in their own language.
            val smart = com.alananasss.kittytune.util.SmartTranslation.forLines(originalLines, targetLang, known = known)
            withContext(Dispatchers.Main) {
                for (i in lyricsLines.indices) {
                    val oldLine = lyricsLines[i]
                    val newTranslation = smart[oldLine.text.trim()]
                    lyricsLines[i] = oldLine.copy(translation = newTranslation)
                }
                isTranslatingLyrics = false
                lyricsRevision++
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
                    if (rom != null) {
                        lyricsLines[i] = oldLine.copy(romanization = rom)
                    }
                }
                lyricsRevision++
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

    private fun parseArtistAndTitle(title: String, uploader: String): Pair<String, String> {
        val pairs = LyricsMatcher.generateCandidatePairs(title, uploader)
        val best = pairs.firstOrNull() ?: return Pair(uploader.trim(), title.trim())
        return Pair(best.second, best.first)
    }

    private fun generateSearchQueries(title: String, uploader: String, rawUploader: String? = null): List<String> {
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

        if (!rawUploader.isNullOrBlank()) {
            val cleanRaw = rawUploader.replace(Regex("[^\\p{L}\\p{Nd}\\s\\-&'$]"), "").trim()
            if (cleanRaw.isNotBlank() && cleanRaw != cleanArtist && cleanRaw != parsedArtist) {
                if (ultraCleanTitle.isNotBlank()) queries.add("$ultraCleanTitle $cleanRaw")
                if (parsedTitle.isNotBlank()) queries.add("$parsedTitle $cleanRaw")
            }
        }

        return queries.filter { it.length > 2 }.toList()
    }

    /**
     * Finds the lyrics for [track] and puts them on screen (issue #33).
     *
     * The desktop's design, brought over. What this replaces was a loop over generated queries that kept
     * `bestMxmLineSync`, `bestLrcLineSync` and a **single shared `finalPlain`** across every iteration — so a
     * query that turned up only plain text left it behind for a later query that found synced lines but no
     * plain text, and the screen then showed the words of one song beside the timings of another.
     *
     * The rewrite keeps each provider result whole ([LyricsCandidate]: its lines and its own plain text
     * together), ranks them by real synchronisation first and match quality second ([LyricsMatcher]), caches
     * the answer, and prefetches the next track so the lyrics are on screen the moment it starts rather than
     * ten seconds in.
     */
    private fun loadLyrics(track: Track) {
        lyricsJob?.cancel()
        lyricsLines.clear()
        MusicManager.currentLyricsFlow.value = emptyList()
        lyricsSync = com.alananasss.kittytune.data.LyricsSync.NONE
        MusicManager.lyricsOffsetMs = 0L
        showLyricsOffsetControls = false
        isLyricsLoading = true
        isSearchingLyrics = false
        rawPlainLyrics = null
        currentLyricsSource = null
        pendingLyricSources.clear()

        val effectiveArtist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
        val (parsedArtist, parsedTitle) = parseArtistAndTitle(track.title ?: "", effectiveArtist)
        val queries = generateSearchQueries(track.title ?: "", effectiveArtist, track.user?.username)
        manualSearchQuery = if (parsedTitle.isNotBlank() && parsedArtist.isNotBlank()) "$parsedTitle $parsedArtist" else (queries.firstOrNull() ?: "")

        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            val savedSync = com.alananasss.kittytune.data.LyricsOffsetRepository.get(track.id)
            withContext(Dispatchers.Main) {
                lyricsSync = savedSync
                MusicManager.lyricsOffsetMs = savedSync.offsetAt(currentPosition)
            }

            val variant = currentLyricsVariant()

            // Cache first: a hit puts the lyrics up in this frame instead of after a dozen HTTP round trips,
            // which is what made them arrive well into the song and show "unavailable" until then.
            val cached = LyricsCache.get(
                track.id,
                variant.providerPreference,
                variant.translationLang,
                variant.romanized,
            )
            if (cached != null && cached.found) {
                withContext(Dispatchers.Main) {
                    applyLyricsPayload(LyricsPayload(cached.lines, cached.plain, cached.provider))
                }
                prefetchQueueLyrics()
                return@launch
            }

            val local = loadEmbeddedLyrics(track)
            if (local != null) {
                withContext(Dispatchers.Main) { applyLyricsPayload(local) }
                prefetchQueueLyrics()
                return@launch
            }

            val payload = resolveLyrics(track, queries, variant)
            if (!isActive) return@launch

            LyricsCache.put(
                track.id,
                LyricsCache.Entry(
                    found = payload != null && !payload.isEmpty,
                    lines = payload?.lines.orEmpty(),
                    plain = payload?.plain,
                    provider = payload?.provider,
                    providerPreference = variant.providerPreference,
                    translationLang = variant.translationLang,
                    romanized = variant.romanized,
                ),
            )

            withContext(Dispatchers.Main) {
                applyLyricsPayload(payload ?: LyricsPayload(emptyList(), null, null))
            }
            prefetchQueueLyrics()
        }
    }

    private data class LyricsVariant(
        val providerPreference: String,
        val translationLang: String?,
        val romanized: Boolean,
    )

    private fun currentLyricsVariant() = LyricsVariant(
        providerPreference = lyricsProvider.name,
        translationLang =
            if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null,
        romanized = isRomanizationEnabled,
    )

    /** A resolved lookup, ready either to be shown or to be parked in the cache. */
    private data class LyricsPayload(
        val lines: List<LyricLine>,
        val plain: String?,
        val provider: String?,
    ) {
        val isEmpty: Boolean get() = lines.isEmpty() && plain.isNullOrBlank()
    }

    /**
     * One provider result in the running, with its lyrics and its plain text kept together.
     *
     * They travel as a pair on purpose — see [loadLyrics] for what sharing one `finalPlain` between queries
     * used to do.
     */
    private data class LyricsCandidate(
        val lines: List<LyricLine>,
        val plain: String?,
        val provider: String,
        val matchScore: Float,
        val titleSimilarity: Float = 0f,
        val providerBonus: Float = 0f,
    ) {
        val rank: Float get() = LyricsMatcher.rank(syncTier, matchScore, titleSimilarity, providerBonus)

        val syncTier: Int get() = LyricsMatcher.syncTier(lines, plain)

        val isUsable: Boolean get() = syncTier > LyricsMatcher.SYNC_TIER_NONE
    }

    /** Publishes a resolved lookup to the screen. Main thread only. */
    private fun applyLyricsPayload(payload: LyricsPayload) {
        lyricsLines.clear()
        lyricsLines.addAll(payload.lines)
        MusicManager.currentLyricsFlow.value = payload.lines
        // Last line of defence: never publish an unparsed XML document as plain lyrics.
        rawPlainLyrics = payload.plain?.takeUnless { LyricsUtils.looksLikeTtml(it) }
        lyricsMode = if (payload.lines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
        isLyricsLoading = false
        lyricsRevision++
        currentLyricsSource = payload.provider.takeUnless { payload.isEmpty }
        val tr = currentTrack
        if (tr != null) {
            refineLyricVoices(tr)
            searchLyricsInBackground(tr, manualSearchQuery)
        }

        if (isLyricsTranslationEnabled && lyricsLines.isNotEmpty() && lyricsLines.none { !it.translation.isNullOrBlank() }) {
            fetchTranslationsForCurrentLines(lyricsTranslationLang)
        }
        if (isRomanizationEnabled && lyricsLines.isNotEmpty() && lyricsLines.none { !it.romanization.isNullOrBlank() }) {
            fetchRomanizationForCurrentLines()
        }
    }

    // --- Genius duet voices --------------------------------------------------------------------------

    /** Who sings each line, per track, as worked out from Genius: an empty list when Genius could not tell. */
    private val lyricVoicesCache = com.alananasss.kittytune.core.BoundedCache<Long, List<LyricSinger>>(64)
    private var lyricVoicesJob: Job? = null

    /**
     * Gives the synced lines on screen the voice Genius says sings each of them, for the duet view: two artists
     * on two sides, and no duet for a song one artist sings throughout. Runs after the lyrics are shown and only
     * touches them if they are still the ones it looked at.
     * See [com.alananasss.kittytune.data.lyrics.GeniusVoices] — the desktop app's version of the same
     * idea, kept in step: a song is a duet on the phone exactly when it is one on the desktop.
     */
    private fun refineLyricVoices(track: Track) {
        lyricVoicesJob?.cancel()
        if (!isDuetViewEnabled || lyricsLines.isEmpty()) return
        val lines = lyricsLines.toList()
        lyricVoicesJob = viewModelScope.launch(Dispatchers.IO) {
            val voices = lyricVoicesCache[track.id] ?: findLyricVoices(track, lines).also { lyricVoicesCache[track.id] = it }
            if (voices.size != lines.size) return@launch
            withContext(Dispatchers.Main) {
                if (currentTrack?.id != track.id || lyricsLines.toList() != lines) return@withContext
                val voiced = lines.mapIndexed { i, line -> line.copy(singer = voices[i], agent = null) }
                lyricsLines.clear()
                lyricsLines.addAll(voiced)
                lyricsRevision++
            }
        }
    }

    /** The voices for [lines] from the track's Genius page, or an empty list when there is no page or no answer. */
    private suspend fun findLyricVoices(track: Track, lines: List<LyricLine>): List<LyricSinger> {
        val effectiveArtist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
        val (artist, title) = parseArtistAndTitle(track.title ?: "", effectiveArtist)
        val songTitle = LyricsMatcher.titleParts(title).firstOrNull() ?: title
        val target = LyricsMatcher.Target(
            title = songTitle,
            artist = artist,
            durationMs = track.durationMs ?: 0L,
            alternativeArtists = listOfNotNull(track.displayArtist, track.user?.username).filter { it.isNotBlank() },
        )
        val hit = com.alananasss.kittytune.data.network.GeniusClient.search("$songTitle $artist".trim())
            .filter { LyricsMatcher.isAcceptable(it.title, it.artist, target) }
            .maxByOrNull { LyricsMatcher.score(it.title, it.artist, 0.0, target) }
            ?: return emptyList()
        // The HTML, for the formatting that says who sings each line of a shared part; plain text as a fallback.
        val page = com.alananasss.kittytune.data.network.GeniusClient.lyricsHtml(hit.id)
            ?.let(com.alananasss.kittytune.data.lyrics.GeniusVoices::parseHtml)
            ?: com.alananasss.kittytune.data.network.GeniusClient.lyrics(hit.id)
                ?.let(com.alananasss.kittytune.data.lyrics.GeniusVoices::parsePlain)
            ?: return emptyList()
        // Who is credited, in order: the artist part of the title ("OD1NOKO + Kai Angel - song"), the account, the
        // Genius credit, and anyone the title adds with "feat." — some uploads name the second artist only there.
        val featured = Regex("""(?i)(?:\(|\[|\s)(?:feat\.?|ft\.?|featuring|with)\s+([^)\]]+)""")
            .findAll(track.title.orEmpty()).flatMap { com.alananasss.kittytune.data.lyrics.GeniusVoices.splitNames(it.groupValues[1]).asSequence() }.toList()
        val credited = com.alananasss.kittytune.data.lyrics.GeniusVoices.splitNames(artist) +
            com.alananasss.kittytune.data.lyrics.GeniusVoices.splitNames(effectiveArtist) +
            com.alananasss.kittytune.data.lyrics.GeniusVoices.splitNames(hit.artistNames.orEmpty()) + featured
        return com.alananasss.kittytune.data.lyrics.GeniusVoices.voicesFor(lines, page, credited).orEmpty()
    }

    /** Lyrics tagged into the downloaded file, when the user asked for those to come first. */
    private suspend fun loadEmbeddedLyrics(track: Track): LyricsPayload? {
        if (!playerPrefs.getLyricsPreferLocal()) return null
        val localTrack = DownloadManager.getLocalTrack(track.id) ?: return null
        if (localTrack.localAudioPath.isEmpty()) return null
        val raw = LyricsUtils.extractLocalLyrics(localTrack.localAudioPath)
        if (raw.isNullOrBlank()) return null
        val trackDurationMs = track.actualDurationMs
        val parsed = LyricsUtils.parseLyricsContent(raw, trackDurationMs)
        // Embedded TTML that failed to parse must not be shown as raw tags; let the online
        // providers try instead.
        if (parsed.isEmpty() && LyricsUtils.looksLikeTtml(raw)) return null
        return LyricsPayload(
            lines = parsed.ifEmpty { listOf(LyricLine(raw, 0, trackDurationMs)) },
            plain = raw.takeIf { parsed.isEmpty() },
            provider = "LOCAL",
        )
    }

    /**
     * Resolves the next track's lyrics into the cache while this one plays.
     *
     * The whole reason the lyrics can be on screen at the instant a track starts instead of after its own
     * round of searching.
     */
    private fun prefetchQueueLyrics() {
        val next = _queue.getOrNull(currentQueueIndex + 1) ?: return
        if (next.id == currentTrack?.id) return
        val variant = currentLyricsVariant()
        if (LyricsCache.get(next.id, variant.providerPreference, variant.translationLang, variant.romanized) != null) {
            return
        }

        lyricsPrefetchJob?.cancel()
        lyricsPrefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val effectiveArtist = next.displayArtist.ifBlank { next.user?.username.orEmpty() }
            val queries = generateSearchQueries(next.title ?: "", effectiveArtist, next.user?.username)
            val payload = runCatching { resolveLyrics(next, queries, variant) }.getOrNull()
            if (!isActive) return@launch
            LyricsCache.put(
                next.id,
                LyricsCache.Entry(
                    found = payload != null && !payload.isEmpty,
                    lines = payload?.lines.orEmpty(),
                    plain = payload?.plain,
                    provider = payload?.provider,
                    providerPreference = variant.providerPreference,
                    translationLang = variant.translationLang,
                    romanized = variant.romanized,
                ),
            )
        }
    }

    private suspend fun searchGenericProviderCandidate(
        prefProvider: PreferredLyricsProvider,
        target: LyricsMatcher.Target,
        trackDurationMs: Long,
        trackId: String,
        albumTitle: String?,
    ): LyricsCandidate? {
        val provider = LyricsProviders.all[prefProvider] ?: return null
        val candidates = LyricsMatcher.generateCandidatePairs(target.title, target.artist)
        for ((candTitle, candArtist) in candidates) {
            if (!currentCoroutineContext().isActive) return null
            try {
                val result = provider.getLyrics(
                    id = trackId,
                    title = candTitle,
                    artist = candArtist,
                    album = albumTitle,
                    duration = (trackDurationMs / 1000).toInt(),
                )
                val raw = result.getOrNull()?.takeIf { it.isNotBlank() } ?: continue
                val parsed = LyricsUtils.parseLyricsContent(raw, trackDurationMs)
                // A TTML payload that failed to parse is unusable: showing the raw <tt> markup
                // is worse than letting the next provider answer.
                if (parsed.isEmpty() && LyricsUtils.looksLikeTtml(raw)) continue
                val plain = if (parsed.isEmpty()) raw else null
                val matchScore = LyricsMatcher.score(candTitle, candArtist, (trackDurationMs / 1000.0), target)
                val titleSim = LyricsMatcher.titleSimilarity(candTitle, target)
                return LyricsCandidate(
                    lines = parsed,
                    plain = plain,
                    provider = prefProvider.name,
                    matchScore = matchScore.coerceAtLeast(0.85f),
                    titleSimilarity = titleSim.coerceAtLeast(0.85f),
                )
            } catch (e: Exception) {
                // Continue to next candidate pair
            }
        }
        return null
    }

    /**
     * Runs the whole provider search for [track] without touching any UI state, so the same code serves the
     * track being played and the prefetch of the one after it.
     *
     * @return the best result found, or null when no provider had anything usable.
     */
    private suspend fun resolveLyrics(
        track: Track,
        queries: List<String>,
        variant: LyricsVariant,
    ): LyricsPayload? = coroutineScope {
        PaxsenixClient.setApiKey(playerPrefs.getPaxsenixApiKey())
        val effectiveArtist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
        val (parsedArtist, parsedTitle) = parseArtistAndTitle(track.title ?: "", effectiveArtist)
        val target = LyricsMatcher.Target(
            title = parsedTitle.ifBlank { track.title ?: "" },
            artist = parsedArtist.ifBlank { effectiveArtist },
            durationMs = track.actualDurationMs,
            alternativeTitles = listOfNotNull(track.title, parsedTitle, track.title?.let { LyricsMatcher.cleanNoiseAndBrackets(it) }).filter { it.isNotBlank() }.distinct(),
            alternativeArtists = listOfNotNull(
                track.displayArtist,
                parsedArtist,
                track.publisherMetadata?.artist,
                track.user?.username,
            ).filter { it.isNotBlank() }.distinct(),
        )
        val trackDurationMs = track.actualDurationMs
        val enabledProviders = playerPrefs.getLyricsProviderOrder()
            .filter { playerPrefs.getLyricsProviderEnabled(it) }
        val orderedProviders = DataSaver.restrictLyricsProviders(context, enabledProviders)
            .ifEmpty { if (DataSaver.isLyricsRestrictionActive(context)) emptyList() else DefaultLyricsProviderOrder }

        // Ask every source at once (issue #33 on desktop): sequential-with-first-usable let plain text
        // from the first provider beat line-timed lyrics from the fifth. Identity (a confident match)
        // now outranks sync tier, which outranks match rank, which outranks the user's provider order.
        suspend fun candidateFrom(providerId: PreferredLyricsProvider): LyricsCandidate? = when (providerId) {
            PreferredLyricsProvider.MUSIXMATCH -> {
                val query = queries.firstOrNull() ?: "${target.title} ${target.artist}".trim()
                var found: LyricsCandidate? = null
                for (spelling in LyricsMatcher.queryVariants(query, track.title.orEmpty())) {
                    found = searchMusixmatchCandidates(spelling, target, trackDurationMs, variant).firstOrNull()
                    if (found != null) break
                }
                found ?: com.alananasss.kittytune.data.lyrics.TitleSpellings.respelled(query)?.let { respelled ->
                    searchMusixmatchCandidates(respelled, target, trackDurationMs, variant).firstOrNull()
                }
            }
            PreferredLyricsProvider.LRCLIB -> {
                // The whole title first, then each song of a two-song title on its own.
                val lrcLibQueries = (queries.firstOrNull()?.let { LyricsMatcher.queryVariants(it, track.title.orEmpty()) }.orEmpty() +
                    LyricsMatcher.titleParts(target.title).map { "$it ${target.artist}".trim() })
                    .ifEmpty { listOf("${target.title} ${target.artist}".trim()) }
                var found: LyricsCandidate? = null
                for (q in lrcLibQueries) {
                    found = searchLrcLibCandidates(q, target, trackDurationMs)
                        .filter { it.isUsable }
                        .maxByOrNull { it.rank }
                    if (found != null) break
                }
                // "NEWYORK" as SoundCloud titles it is only found as "NEW YORK": the spelling Genius gives it.
                found ?: queries.firstOrNull()
                    ?.let { com.alananasss.kittytune.data.lyrics.TitleSpellings.respelled(it) }
                    ?.let { respelled ->
                        searchLrcLibCandidates(respelled, target, trackDurationMs).filter { it.isUsable }.maxByOrNull { it.rank }
                    }
            }
            PreferredLyricsProvider.GENIUS -> {
                null // Checked at end as fallback: no timings, cannot compete in the race
            }
            else -> {
                searchGenericProviderCandidate(
                    prefProvider = providerId,
                    target = target,
                    trackDurationMs = trackDurationMs,
                    trackId = track.id.toString(),
                    albumTitle = track.publisherMetadata?.albumTitle,
                )
            }
        }

        val answers = orderedProviders.map { providerId ->
            async(Dispatchers.IO) {
                withTimeoutOrNull(LYRICS_SOURCE_TIMEOUT_MS) { runCatching { candidateFrom(providerId) }.getOrNull() }
            }
        }.awaitAll()

        val best = answers.withIndex()
            .mapNotNull { (i, c) -> c?.takeIf { it.isUsable }?.let { i to it } }
            .maxWithOrNull(
                compareBy<Pair<Int, LyricsCandidate>> { (_, c) -> if (c.matchScore >= LyricsMatcher.CONFIDENT_MATCH) 1 else 0 }
                    .thenBy { (_, c) -> c.syncTier }
                    .thenBy { (_, c) -> c.rank }
                    .thenByDescending { (i, _) -> i } // earlier provider order wins ties
            )?.second

        // Genius only once everything else has come up empty
        val geniusAllowed = playerPrefs.getLyricsProviderEnabled(PreferredLyricsProvider.GENIUS) &&
            DataSaver.isLyricsProviderAllowed(context, PreferredLyricsProvider.GENIUS)
        val candidate = best
            ?: (if (geniusAllowed) searchGeniusCandidate(queries, target) else null)
            ?: return@coroutineScope null
        val syncedLines =
            if (candidate.syncTier >= LyricsMatcher.SYNC_TIER_LINE) candidate.lines else emptyList()
        LyricsPayload(
            lines = decorateLyrics(syncedLines, variant),
            plain = candidate.plain
                ?: candidate.lines.takeIf { it.isNotEmpty() }?.joinToString("\n") { it.text },
            provider = candidate.provider,
        )
    }

    /**
     * LrcLib hits for one query. Every hit keeps its own plain text alongside its own timings, so the two can
     * never be mixed between songs.
     */
    private suspend fun searchLrcLibCandidates(
        query: String,
        target: LyricsMatcher.Target,
        trackDurationMs: Long,
    ): List<LyricsCandidate> {
        val results = try {
            LrcLibClient.api.searchLyrics(query)
        } catch (e: Exception) {
            emptyList()
        }
        return results
            .filter { LyricsMatcher.isAcceptable(it.name, it.artistName, target) }
            .map { result ->
                val lines = result.syncedLyrics
                    ?.takeIf { it.isNotBlank() }
                    ?.let { LyricsUtils.parseLyricsContent(it, trackDurationMs) }
                    ?: emptyList()
                val candTitle = result.name
                val titleSim = LyricsMatcher.titleSimilarity(candTitle, target)
                LyricsCandidate(
                    lines = lines,
                    plain = result.plainLyrics,
                    provider = "LRCLIB",
                    matchScore = LyricsMatcher.score(result.name, result.artistName, result.duration, target),
                    titleSimilarity = titleSim,
                )
            }
    }

    /**
     * Musixmatch hits for one query.
     *
     * Search returns metadata only, so the actual lyrics cost a second round trip per track — which is why
     * exactly one entry gets fetched: the one that both matches best and advertises the richest sync.
     */
    private suspend fun searchMusixmatchCandidates(
        query: String,
        target: LyricsMatcher.Target,
        trackDurationMs: Long,
        variant: LyricsVariant,
    ): List<LyricsCandidate> {
        val results = try {
            MusixmatchClient.search(context, query)
        } catch (e: Exception) {
            emptyList()
        }
        val pick = results
            .filter { LyricsMatcher.isAcceptable(it.trackName, it.artistName, target) }
            .maxByOrNull { hit ->
                val syncTier = when {
                    hit.hasRichSync == 1 -> LyricsMatcher.SYNC_TIER_WORD
                    hit.hasSubtitles == 1 -> LyricsMatcher.SYNC_TIER_LINE
                    else -> LyricsMatcher.SYNC_TIER_PLAIN
                }
                val score = LyricsMatcher.score(
                    hit.trackName, hit.artistName, hit.trackLength.toDouble(), target
                )
                val titleSim = LyricsMatcher.titleSimilarity(hit.trackName, target)
                LyricsMatcher.rank(
                    syncTier = syncTier,
                    matchScore = score,
                    titleSimilarity = titleSim,
                )
            } ?: return emptyList()

        val data = try {
            MusixmatchClient.getLyricsData(
                context,
                pick.trackId,
                trackDurationMs,
                variant.translationLang,
                variant.romanized,
            )
        } catch (e: Exception) {
            return emptyList()
        }
        return listOf(
            LyricsCandidate(
                lines = data.first,
                plain = data.second,
                provider = "MUSIXMATCH",
                matchScore = LyricsMatcher.score(
                    pick.trackName,
                    pick.artistName,
                    pick.trackLength.toDouble(),
                    target,
                ),
                titleSimilarity = LyricsMatcher.titleSimilarity(pick.trackName, target),
            )
        )
    }

    /** The best Genius page for any of the generated queries, as plain text. */
    private suspend fun searchGeniusCandidate(
        queries: List<String>,
        target: LyricsMatcher.Target,
    ): LyricsCandidate? {
        // The first few queries are the tightest; the looser tail is not worth another round trip against a
        // provider that cannot give timings anyway.
        for (query in queries.take(3)) {
            if (!currentCoroutineContext().isActive) return null
            val pick = com.alananasss.kittytune.data.network.GeniusClient.search(query)
                .filter { LyricsMatcher.isAcceptable(it.title, it.artist, target) }
                .maxByOrNull { LyricsMatcher.score(it.title, it.artist, 0.0, target) }
                ?: continue
            val plain = com.alananasss.kittytune.data.network.GeniusClient.lyrics(pick.id) ?: continue
            return LyricsCandidate(
                lines = emptyList(),
                plain = plain,
                provider = "GENIUS",
                matchScore = LyricsMatcher.score(pick.title, pick.artist, 0.0, target),
                titleSimilarity = LyricsMatcher.titleSimilarity(pick.title, target),
            )
        }
        return null
    }

    /** Adds the translation and romanisation the user asked for, when the provider did not. */
    private suspend fun decorateLyrics(
        lines: List<LyricLine>,
        variant: LyricsVariant,
    ): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val wantsTranslation = variant.translationLang != null && lines.none { it.translation != null }
        val wantsRomanization = variant.romanized && lines.none { it.romanization != null }
        if (!wantsTranslation && !wantsRomanization) return lines

        val texts = lines.map { it.text }.filter { it.isNotBlank() }.distinct()
        val translations = if (wantsTranslation) {
            // Only the lines the reader cannot already read: foreign sentences whole, foreign words glossed
            // word by word, nothing under their own lines.
            com.alananasss.kittytune.util.SmartTranslation.forLines(texts, variant.translationLang!!)
        } else {
            emptyMap()
        }
        val romanizations = if (wantsRomanization) {
            com.alananasss.kittytune.data.network.FreeTranslator.getRomanization(texts)
        } else {
            emptyMap()
        }
        return lines.map { line ->
            val trans = (line.translation ?: translations[line.text.trim()])
                ?.takeIf { !it.trim().equals(line.text.trim(), ignoreCase = true) }
            val rom = (line.romanization ?: romanizations[line.text.trim()])
                ?.takeIf { !it.trim().equals(line.text.trim(), ignoreCase = true) }
            line.copy(
                translation = trans,
                romanization = rom,
            )
        }
    }


    fun reloadLyrics() {
        currentTrack?.let { loadLyrics(it) }
    }

    fun loadCustomLyrics(content: String) {
        viewModelScope.launch {
            val trackDuration = currentTrack?.actualDurationMs ?: 0L
            val resultLines = LyricsUtils.parseLyricsContent(content, trackDuration)
            withContext(Dispatchers.Main) {
                lyricsLines.clear()
                lyricsLines.addAll(resultLines)
                rawPlainLyrics = when {
                    resultLines.isNotEmpty() -> resultLines.joinToString("\n") { it.text }
                    // Unparsed TTML stays hidden rather than dumped as raw XML tags.
                    LyricsUtils.looksLikeTtml(content) -> null
                    else -> content
                }
                lyricsMode = if (resultLines.isNotEmpty()) LyricsMode.SYNCED else LyricsMode.PLAIN
                isSearchingLyrics = false
                isLyricsLoading = false
            }
        }
    }

    /** Nudges the offset here: the single one, or with two points the one nearer the playhead. */
    fun adjustLyricsOffset(amount: Long) {
        updateLyricsSync(lyricsSync.adjustedAt(currentPosition, amount))
    }

    fun resetLyricsOffset() {
        updateLyricsSync(com.alananasss.kittytune.data.LyricsSync.NONE)
    }

    /** Puts the first sync point at the playhead, keeping the offset the lyrics have there. */
    fun pinLyricsSyncStart() {
        updateLyricsSync(lyricsSync.withStartAt(currentPosition))
    }

    /**
     * Puts the second sync point at the playhead. Returns false, changing nothing, when it is too close to the
     * first one or the two would make the lyrics drift implausibly fast.
     */
    fun pinLyricsSyncEnd(): Boolean {
        val pinned = lyricsSync.withEndAt(currentPosition) ?: return false
        updateLyricsSync(pinned)
        return true
    }

    /** Back to a single offset, the one the lyrics have at the playhead. */
    fun clearLyricsSyncEnd() {
        updateLyricsSync(lyricsSync.singleAt(currentPosition))
    }

    private fun updateLyricsSync(sync: com.alananasss.kittytune.data.LyricsSync) {
        lyricsSync = sync
        MusicManager.lyricsOffsetMs = sync.offsetAt(currentPosition)
        val trackId = currentTrack?.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            com.alananasss.kittytune.data.LyricsOffsetRepository.put(trackId, sync)
        }
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

            rawPlainLyrics = (response?.plainLyrics ?: response?.syncedLyrics)
            ?.takeUnless { LyricsUtils.looksLikeTtml(it) }

            lyricsMode = if (resultLines.isNotEmpty()) {
                LyricsMode.SYNCED
            } else {
                LyricsMode.PLAIN
            }
            isLyricsLoading = false
            currentTrack?.let { refineLyricVoices(it) }
        }
    }

    /**
     * Searches a provider for lyrics to pick by hand.
     *
     * Only one search runs at a time, and the results replace rather than accumulate. Both halves of that
     * were needed: pressing the button twice used to start two searches that each cleared the list and then
     * appended to it, so the second one's results landed on top of the first one's — and since both searched
     * the same thing, every row appeared twice. `LazyColumn` keys rows by id, and a duplicate key is fatal
     * (issue #33).
     *
     * The de-duplication is a second, independent guard: a provider is entitled to return the same track
     * twice, and when it does, the list must not be able to bring the app down.
     */
    private var manualLyricSearchJob: Job? = null

    /**
     * Every source is asked on its own and its results join the list as soon as it answers, so the fast
     * sources are on screen while LrcLib is still thinking; the list used to stay empty until the slowest
     * source was done. Results belong to the track they were searched for and are dropped with it.
     */
    fun searchLyricsManual(query: String, provider: String = manualSearchProvider) =
        startManualLyricsSearch(query, provider, onSettled = null)

    private fun startManualLyricsSearch(query: String, provider: String, onSettled: ((LyricsMatcher.Target) -> Unit)?) {
        manualSearchProvider = provider
        if (query.isBlank()) return
        val track = currentTrack ?: return
        manualLyricSearchJob?.cancel()
        val generation = ++manualSearchGeneration
        unifiedLyricSearchResults.clear()
        lyricSearchTrackId = track.id
        val sources = ManualLyricsSearch.providersFor(provider, playerPrefs, context)
        pendingLyricSources.clear()
        pendingLyricSources.addAll(sources)
        isManualSearchLoading = sources.isNotEmpty()

        val effectiveArtist = track.displayArtist.ifBlank { track.user?.username.orEmpty() }
        val searchTrack = LyricsSearchTrack(
            id = track.id.toString(),
            title = track.title.orEmpty(),
            artist = effectiveArtist.trim(),
            album = track.publisherMetadata?.albumTitle,
            durationMs = track.actualDurationMs,
        )
        val (parsedArtist, parsedTitle) = parseArtistAndTitle(track.title ?: "", effectiveArtist)
        val target = LyricsMatcher.Target(
            title = parsedTitle.ifBlank { track.title.orEmpty() },
            artist = parsedArtist.ifBlank { effectiveArtist },
            durationMs = searchTrack.durationMs,
            alternativeTitles = (listOfNotNull(track.title) + LyricsMatcher.titleParts(parsedTitle)).filter { it.isNotBlank() },
            alternativeArtists = listOfNotNull(track.displayArtist, track.user?.username).filter { it.isNotBlank() },
        )

        manualLyricSearchJob = viewModelScope.launch {
            PaxsenixClient.setApiKey(playerPrefs.getPaxsenixApiKey())
            try {
                supervisorScope {
                    sources.forEach { source ->
                        launch(Dispatchers.IO) {
                            val found = withTimeoutOrNull(ManualLyricsSearch.PROVIDER_TIMEOUT_MS) {
                                ManualLyricsSearch.search(source, query, searchTrack)
                            }.orEmpty()
                            withContext(Dispatchers.Main) {
                                if (generation != manualSearchGeneration) return@withContext
                                val merged = ManualLyricsSearch.merge(unifiedLyricSearchResults.toList(), found, target)
                                unifiedLyricSearchResults.clear()
                                unifiedLyricSearchResults.addAll(merged)
                                pendingLyricSources.remove(source)
                            }
                        }
                    }
                }
                onSettled?.invoke(target)
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    if (generation == manualSearchGeneration) {
                        pendingLyricSources.clear()
                        isManualSearchLoading = false
                    }
                }
            }
        }
    }

    /**
     * Runs the search-by-hand for [track] in the background once its lyrics are settled, so the manual search
     * opens on results already there instead of starting from nothing.
     */
    private fun searchLyricsInBackground(track: Track, query: String) {
        viewModelScope.launch {
            lyricsJob?.join()
            delay(600L)
            if (currentTrack?.id != track.id) return@launch
            if (lyricSearchTrackId == track.id && unifiedLyricSearchResults.isNotEmpty()) return@launch
            startManualLyricsSearch(query, ManualLyricsSearch.ALL) { target -> upgradeToSyncedLyrics(track, target) }
        }
    }

    /**
     * The automatic lookup settled on plain text or nothing, while the search for the manual list found this song
     * with timings: those are used.
     */
    private fun upgradeToSyncedLyrics(track: Track, target: LyricsMatcher.Target) {
        if (currentTrack?.id != track.id || lyricsMode == LyricsMode.SYNCED || isLyricsLoading) return
        val best = unifiedLyricSearchResults.firstOrNull {
            (it.hasLineSync || it.hasWordSync) && ManualLyricsSearch.isConfidentMatch(it, target)
        } ?: return
        selectUnifiedLyricResult(best, isManualPick = false)
    }

    fun selectLyricResult(result: LrcLibResponse) {
        viewModelScope.launch(Dispatchers.IO) { processLyricsResponse(result, duration) }
    }

    fun selectUnifiedLyricResult(result: UnifiedLyricResult, isManualPick: Boolean = true) {
        lyricsJob?.cancel()
        val pickedForTrackId = currentTrack?.id
        viewModelScope.launch(Dispatchers.IO) {
            isLyricsLoading = true
            var finalLines = emptyList<LyricLine>()
            var finalPlain: String? = null

            if (result.rawContent != null) {
                finalLines = LyricsUtils.parseLyricsContent(result.rawContent, duration)
                if (finalLines.isEmpty() && !LyricsUtils.looksLikeTtml(result.rawContent)) {
                    finalPlain = result.rawContent
                }
            } else {
                when (result.provider.uppercase()) {
                    "LRCLIB" -> {
                        try {
                            val lrcData = LrcLibClient.api.getLyricsById(result.id.toLong())
                            val lyricsText = lrcData.syncedLyrics ?: lrcData.plainLyrics
                            if (!lyricsText.isNullOrEmpty()) {
                                finalLines = LyricsUtils.parseLyricsContent(lyricsText, duration)
                            }
                            finalPlain = lrcData.plainLyrics
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }

                    "GENIUS" -> {
                        finalPlain = com.alananasss.kittytune.data.network.GeniusClient
                            .lyrics(result.id.toLongOrNull() ?: 0L)
                    }

                    "MUSIXMATCH" -> {
                        val targetLang =
                            if (playerPrefs.getLyricsTranslationEnabled()) playerPrefs.getLyricsTranslationLang() else null
                        lastFetchedMxmTrackId = result.id.toLongOrNull()
                        val data =
                            MusixmatchClient.getLyricsData(context, result.id.toLong(), duration, targetLang, isRomanizationEnabled)
                        finalLines = data.first
                        finalPlain = data.second
                    }

                    else -> {
                        val p = LyricsProviders.all[PreferredLyricsProvider.fromName(result.provider)]
                        if (p != null) {
                            val raw = p.getLyrics(
                                id = result.id,
                                title = result.name,
                                artist = result.artistName,
                                album = result.albumName,
                                duration = (duration / 1000).toInt()
                            ).getOrNull()
                            if (!raw.isNullOrBlank()) {
                                finalLines = LyricsUtils.parseLyricsContent(raw, duration)
                                if (finalLines.isEmpty() && !LyricsUtils.looksLikeTtml(raw)) finalPlain = raw
                            }
                        }
                    }
                }
            }

            if (!isManualPick && currentTrack?.id != pickedForTrackId) {
                withContext(Dispatchers.Main) { isLyricsLoading = false }
                return@launch
            }
            withContext(Dispatchers.Main) {
                if (finalLines.isNotEmpty()) {
                    lyricsLines.clear()
                    lyricsLines.addAll(finalLines)
                    MusicManager.currentLyricsFlow.value = finalLines
                    rawPlainLyrics = finalPlain
                    lyricsMode = LyricsMode.SYNCED
                    currentTrack?.let { refineLyricVoices(it) }
                } else if (!finalPlain.isNullOrBlank()) {
                    lyricsLines.clear()
                    MusicManager.currentLyricsFlow.value = emptyList()
                    rawPlainLyrics = finalPlain
                    lyricsMode = LyricsMode.PLAIN
                } else {
                    lyricsLines.clear()
                    MusicManager.currentLyricsFlow.value = emptyList()
                    rawPlainLyrics = null
                    lyricsMode = LyricsMode.PLAIN
                }
                isLyricsLoading = false
                isSearchingLyrics = false
                currentLyricsSource = result.provider.takeIf { finalLines.isNotEmpty() || !finalPlain.isNullOrBlank() }
            }

            if (isManualPick && (finalLines.isNotEmpty() || !finalPlain.isNullOrBlank())) {
                currentTrack?.id?.let {
                    LyricsCache.invalidate(it)
                }
            } else if (!isManualPick && finalLines.isNotEmpty() && pickedForTrackId != null) {
                val variant = currentLyricsVariant()
                LyricsCache.put(
                    pickedForTrackId,
                    LyricsCache.Entry(
                        found = true,
                        lines = finalLines,
                        plain = finalPlain,
                        provider = result.provider,
                        providerPreference = variant.providerPreference,
                        translationLang = variant.translationLang,
                        romanized = variant.romanized,
                    )
                )
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
        com.alananasss.kittytune.audio.haptics.HapticUtil.performConfirmHaptic(context)
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
                    return@launch
                }
            } catch (_: Exception) {
                try {
                    val search = com.alananasss.kittytune.data.spotify.SpotifyRepository.search(cleanName)
                    val match = search.artists.firstOrNull { it.name.equals(cleanName, ignoreCase = true) }
                        ?: search.artists.firstOrNull()
                    if (match != null && match.id.isNotBlank()) {
                        navigateToSpotifyArtist(match.id)
                        return@launch
                    }
                } catch (_: Exception) {}
                emitUiEvent(getString(R.string.error_generic))
            }
        }
    }

    fun navigateToTag(tagName: String) {
        showDetailsSheet = false; isPlayerExpanded = false; navigateToPlaylistId = "tag:$tagName"
    }

    fun openSelectArtistDialog(
        artists: List<SpotifyArtistRef>,
        track: Track?
    ) {
        showMenuSheet = false
        showDetailsSheet = false
        showCommentsSheet = false
        selectArtistOptions = artists
        selectedArtistDialogTrack = track
        showSelectArtistDialog = true
    }

    fun dismissSelectArtistDialog() {
        showSelectArtistDialog = false
        selectedArtistDialogTrack = null
        selectArtistOptions = emptyList()
    }

    private fun navigableArtists(
        artists: List<SpotifyArtistRef>?
    ): List<SpotifyArtistRef> =
        artists.orEmpty().filter { it.id.isNotBlank() || it.name.isNotBlank() }.distinctBy { it.id.ifBlank { it.name } }

    fun navigateToArtistChoice(
        artists: List<SpotifyArtistRef>?,
        fallbackUserId: Long? = null
    ) {
        val navigable = navigableArtists(artists)
        when {
            navigable.size > 1 -> openSelectArtistDialog(navigable, null)
            navigable.size == 1 -> onArtistSelected(navigable.first())
            else -> fallbackUserId?.takeIf { it > 0 }?.let { navigateToArtist(it) }
        }
    }

    fun onArtistSelected(artist: SpotifyArtistRef) {
        val track = selectedArtistDialogTrack
        dismissSelectArtistDialog()
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false

        val isVk = track?.source == "vk"
            || track?.user?.urn?.startsWith("vk:") == true
            || track?.permalinkUrl?.contains("vk.com") == true
            || artist.id.startsWith("vk:")

        if (isVk) {
            val slug = artist.id.removePrefix("vk:artist:").removePrefix("vk:").trim()
            val target = if (slug.isNotBlank()) slug else artist.name.trim()
            if (target.isNotBlank()) {
                navigateToPlaylistId = "profile:vk:artist:$target"
            }
            return
        }

        val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(
            artist.id.ifBlank { artist.uri ?: "" }
        )
        if (cleanId.isNotBlank()) {
            navigateToSpotifyArtist(cleanId)
        } else if (artist.name.isNotBlank()) {
            resolveAndNavigateToArtist(artist.name)
        }
    }

    fun navigateToTrackArtist(track: Track?) {
        if (track == null) return
        val navigable = navigableArtists(track.artists)
        if (navigable.size > 1) {
            openSelectArtistDialog(navigable, track)
            return
        }

        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false

        if (navigable.size == 1) {
            val single = navigable.first()
            val isVk = track.source == "vk"
                || track.user?.urn?.startsWith("vk:") == true
                || track.permalinkUrl?.contains("vk.com") == true
                || single.id.startsWith("vk:")
            if (isVk) {
                val slug = single.id.removePrefix("vk:artist:").removePrefix("vk:").trim()
                val target = if (slug.isNotBlank()) slug else single.name.trim()
                if (target.isNotBlank()) {
                    navigateToPlaylistId = "profile:vk:artist:$target"
                }
                return
            }
            val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(
                single.id.ifBlank { single.uri ?: "" }
            )
            if (cleanId.isNotBlank()) {
                navigateToSpotifyArtist(cleanId)
                return
            }
        }

        if (track.source == "vk" || track.user?.urn?.startsWith("vk:") == true || track.permalinkUrl?.contains("vk.com") == true) {
            val artistName = track.displayArtist.ifBlank { track.user?.username ?: "" }.trim()
            val userId = track.user?.id ?: 0L
            val navId = when {
                track.user?.urn?.startsWith("vk:") == true -> "profile:${track.user?.urn}"
                artistName.isNotBlank() -> "profile:vk:artist:$artistName"
                userId != 0L -> "profile:vk:user:$userId"
                else -> "profile:vk:artist:${track.title}"
            }
            navigateToPlaylistId = navId
            return
        }

        if (track.source == "spotify" || track.user?.urn?.startsWith("spotify:artist:") == true) {
            val artistId = track.artists?.firstOrNull()?.id
                ?: track.user?.permalink
                ?: track.user?.urn?.removePrefix("spotify:artist:")
                ?: ""
            if (artistId.isNotBlank()) {
                navigateToSpotifyArtist(artistId)
                return
            }
        }

        if (track.source in listOf("deezer", "tidal", "qobuz") ||
            track.user?.urn?.startsWith("deezer:") == true ||
            track.user?.urn?.startsWith("tidal:") == true ||
            track.user?.urn?.startsWith("qobuz:") == true
        ) {
            val artistUrn = track.user?.urn ?: track.user?.permalink ?: track.artists?.firstOrNull()?.id?.let { "${track.source}:artist:$it" }
            if (!artistUrn.isNullOrBlank()) {
                navigateToPlaylistId = artistUrn
                return
            }
        }

        if (track.user != null && track.user!!.id > 0) {
            navigateToUser(track.user)
        } else if (track.displayArtist.isNotBlank()) {
            resolveAndNavigateToArtist(track.displayArtist)
        }
    }

    fun navigateToArtist(userId: Long) {
        val currentT = currentTrack
        if (currentT != null) {
            val navigable = navigableArtists(currentT.artists)
            if (navigable.size > 1) {
                openSelectArtistDialog(navigable, currentT)
                return
            }
        }
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        val currentU = currentTrack?.user
        if (currentTrack?.source == "vk" || currentU?.urn?.startsWith("vk:") == true) {
            val artistName = currentTrack?.displayArtist?.ifBlank { currentU?.username ?: "" }?.trim() ?: ""
            val navId = when {
                currentU?.urn?.startsWith("vk:") == true -> "profile:${currentU.urn}"
                artistName.isNotBlank() -> "profile:vk:artist:$artistName"
                userId != 0L -> "profile:vk:user:$userId"
                else -> "profile:vk:artist:${currentTrack?.title}"
            }
            navigateToPlaylistId = navId
            return
        }
        if (userId == 0L) {
            currentTrack?.let { navigateToTrackArtist(it) }
            return
        }
        if (currentU != null && (currentU.id == userId || currentU.numericId == userId)) {
            val urn = currentU.urn
            if (urn != null) {
                when {
                    urn.startsWith("spotify:artist:") -> {
                        navigateToPlaylistId = "profile:$urn"
                        return
                    }
                    urn.startsWith("deezer:artist:") || urn.startsWith("tidal:artist:") || urn.startsWith("qobuz:artist:") -> {
                        navigateToPlaylistId = urn
                        return
                    }
                }
            }
        }
        if (currentTrack?.source in listOf("deezer", "tidal", "qobuz")) {
            currentTrack?.let {
                navigateToTrackArtist(it)
                return
            }
        }
        navigateToPlaylistId = "profile:$userId"
    }

    fun navigateToUser(user: User?) {
        if (user == null) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        val navId = user.profileNavId
        if (navId.isNotBlank()) {
            navigateToPlaylistId = navId
            return
        }
        if (user.id in 1..999999999999L) {
            navigateToPlaylistId = "profile:${user.id}"
        } else if (!user.username.isNullOrBlank()) {
            resolveAndNavigateToArtist(user.username)
        }
    }

    fun navigateToSpotifyArtist(artistId: String) {
        val cleanId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(artistId)
        if (cleanId.isBlank()) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "spotify_artist:$cleanId"
    }

    fun navigateToAlbum(albumId: String) {
        if (albumId.startsWith("deezer:album:") || albumId.startsWith("tidal:album:") || albumId.startsWith("qobuz:album:")) {
            showDetailsSheet = false
            showMenuSheet = false
            showCommentsSheet = false
            isPlayerExpanded = false
            navigateToPlaylistId = albumId
            return
        }
        val cleanId = albumId.removePrefix("spotify:album:").removePrefix("spotify_album:").removePrefix("spotify:")
        if (cleanId.isBlank()) return
        showDetailsSheet = false
        showMenuSheet = false
        showCommentsSheet = false
        isPlayerExpanded = false
        navigateToPlaylistId = "spotify:album:$cleanId"
    }

    fun navigateToContext() {
        currentContext?.let { context ->
            var destination = context.navigationId
            if (destination.startsWith("playlist_detail:")) {
                destination = destination.removePrefix("playlist_detail:")
            } else if (destination.startsWith("playlist_")) {
                destination = destination.removePrefix("playlist_")
            }
            destination = destination.removePrefix(TRAILER_NAV_PREFIX)
            navigateToPlaylistId = destination
        }
    }

    fun onNavigationHandled() {
        navigateToPlaylistId = null
    }

    fun loadComments(refresh: Boolean = false, specificTrack: Track? = null) {
        val t = specificTrack ?: selectedTrackForSheet ?: trackForMenu ?: currentTrack ?: return
        if (refresh) {
            commentsList.clear()
            commentNextHref = null
            commentNextCursor = null
            loadTrackReactions(t.id)
        }
        if (!refresh && commentNextHref == null && commentsList.isNotEmpty()) return

        viewModelScope.launch {
            if (refresh) isCommentsLoading = true
            try {
                android.util.Log.d("PlayerViewModel", "loadComments: trackId=${t.id}, refresh=$refresh")
                val trackUrn = "soundcloud:tracks:${t.id}"
                val creatorId = t.user?.id ?: 0L
                val creatorUrn = if (creatorId > 0) "soundcloud:users:$creatorId" else null
                val creatorArtwork = t.user?.avatarUrl ?: t.artworkUrl
                val creatorName = t.displayArtist.ifBlank { t.user?.username ?: "" }

                val gqlSort = when (commentSort) {
                    CommentSort.NEWEST -> "NEWEST"
                    CommentSort.OLDEST -> "OLDEST"
                    CommentSort.TIMESTAMP -> "TIMESTAMP"
                }

                val cursor = if (refresh) null else commentNextCursor
                val canUseGraphQl = refresh || (cursor != null && commentNextHref?.startsWith("graphql_cursor:") == true)

                var loadedViaGraphQl = false
                if (canUseGraphQl) {
                    try {
                        val query = """
query CommentsWithReactions(
  ${'$'}trackUrn: ID!
  ${'$'}creatorUrn: String
  ${'$'}options: TrackCommentsOptionsInput
) {
  trackComments(trackUrn: ${'$'}trackUrn, options: ${'$'}options) {
    comments {
      body
      trackTime
      createdAtTimestamp
      urn
      user {
        urn
        username
        permalink
        userAvatarUrlTemplate
        verified
      }
      reactions {
        creatorReaction(creatorUrn: ${'$'}creatorUrn)
        reactionCounts {
          reactionTypeValueUrn
          count
        }
        userReaction
      }
      replies {
        comments {
          body
          trackTime
          createdAtTimestamp
          urn
          user {
            urn
            username
            permalink
            userAvatarUrlTemplate
            verified
          }
          reactions {
            creatorReaction(creatorUrn: ${'$'}creatorUrn)
            reactionCounts {
              reactionTypeValueUrn
              count
            }
            userReaction
          }
        }
        total
        pageInfo {
          endCursor
          hasNextPage
        }
      }
    }
    pageInfo {
      endCursor
      hasNextPage
    }
  }
}
                        """.trimIndent()

                        val options = GraphQlCommentsOptionsInput(
                            sort = gqlSort,
                            first = 25,
                            repliesSort = "DESCENDING",
                            repliesFirst = 3,
                            after = cursor
                        )
                        val variables = GraphQlCommentsVariables(
                            trackUrn = trackUrn,
                            creatorUrn = creatorUrn,
                            options = options
                        )
                        val request = GraphQlRequest("CommentsWithReactions", query, variables)
                        val responseJson = api.postGraphQl("https://graph.soundcloud.com/graphql", request)
                        val data = gson.fromJson(responseJson, GraphQlCommentsResponse::class.java)
                        val trackComments = data.data?.trackComments
                        val rawComments = trackComments?.comments

                        if (rawComments != null) {
                            android.util.Log.d("PlayerViewModel", "loadComments: GraphQL returned ${rawComments.size} comments")
                            val mappedComments = rawComments.map { it.toComment(creatorArtwork, creatorName) }
                            val newComments = mappedComments.filter { c -> commentsList.none { it.id == c.id } }
                            commentsList.addAll(newComments)

                            val pageInfo = trackComments.pageInfo
                            if (pageInfo?.hasNextPage == true && !pageInfo.endCursor.isNullOrBlank()) {
                                commentNextCursor = pageInfo.endCursor
                                commentNextHref = "graphql_cursor:${pageInfo.endCursor}"
                            } else {
                                commentNextCursor = null
                                commentNextHref = null
                            }
                            loadedViaGraphQl = true
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PlayerViewModel", "loadComments: GraphQL error: ${e.message}", e)
                    }
                }

                if (!loadedViaGraphQl) {
                    android.util.Log.d("PlayerViewModel", "loadComments: falling back to REST")
                    val response = if (refresh) {
                        api.getTrackComments(trackId = t.id, threaded = 1, filterReplies = 1, sort = commentSort.value)
                    } else if (commentNextHref != null && !commentNextHref!!.startsWith("graphql_cursor:")) {
                        api.getCommentsNextPage(commentNextHref!!)
                    } else {
                        null
                    }
                    if (response != null) {
                        commentNextHref = response.next_href
                        commentNextCursor = null
                        val newComments = response.collection.filter { c -> commentsList.none { it.id == c.id } }
                        commentsList.addAll(newComments)
                        checkCommentLikesStatus(t.id, newComments)
                    }
                }
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
                playerPrefs.setCachedUserId(me.id)
                playerPrefs.setCachedUsername(me.username)
                com.alananasss.kittytune.data.together.Together.defaultName = me.username ?: "Listener"
                SoundCloudTelemetryTracker.updateCurrentUserId(me.id)
            } catch (_: Exception) {
            }
        }
    }

    fun startRadioFromTrack(track: Track) {
        showMenuSheet = false
        when (RadioEligibility.radioKind(track)) {
            RadioEligibility.Kind.SPOTIFY -> {
                // A restored/imported track only keeps its share link, not its
                // `permalink`, so read the Spotify id from the URL or artist urn
                // before falling back to the app's stable id.
                val trackId = track.permalink?.takeIf { it.isNotBlank() && !it.contains("/") }
                    ?: track.permalinkUrl?.let {
                        com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(it)
                    }
                    ?: track.user?.urn?.let {
                        com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(it)
                    }
                    ?: track.id.toString()
                navigateToPlaylistId = "spotify_radio:$trackId"
            }
            RadioEligibility.Kind.TIDAL -> {
                val trackId = com.alananasss.kittytune.data.tidal.TidalSearchRepository.extractTrackId(track) ?: return
                navigateToPlaylistId = "tidal_radio:$trackId"
            }
            RadioEligibility.Kind.DEEZER -> {
                val artistId = deezerArtistId(track) ?: return
                navigateToPlaylistId = "deezer_radio:$artistId"
            }
            RadioEligibility.Kind.QOBUZ -> {
                val trackId = qobuzTrackId(track) ?: return
                navigateToPlaylistId = "qobuz_radio:$trackId"
            }
            RadioEligibility.Kind.YOUTUBE -> startYoutubeRadio(track)
            RadioEligibility.Kind.SOUND_CLOUD -> {
                navigateToPlaylistId = "station:${track.id}"
            }
            RadioEligibility.Kind.NONE -> return
        }
    }

    /** The Deezer artist a track belongs to: the radio is built around them. */
    private fun deezerArtistId(track: Track): String? {
        track.user?.urn?.removePrefix("deezer:artist:")?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }
        val id = track.user?.id ?: 0L
        return if (id > 0L) id.toString() else null
    }

    /** The Qobuz id of a track, from its permalink or its share link. */
    private fun qobuzTrackId(track: Track): String? {
        track.permalink?.removePrefix("qobuz:track:")?.trim()
            ?.takeIf { it.isNotBlank() && it != "null" }
            ?.let { return it }
        val candidate = track.permalinkUrl?.substringAfterLast('/')?.substringBefore('?')?.trim().orEmpty()
        return candidate.takeIf { it.isNotBlank() }
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
        maintainPlayerState: Boolean = false,
        autoPlay: Boolean = true,
        respectShuffle: Boolean = true,
    ) {
        if (!applyingConnectCommand && autoPlay && ConnectManager.selectedDevice.value != null && tracks.isNotEmpty()) {
            val index = startIndex.coerceIn(0, tracks.lastIndex)
            val start = if (tracks.size > 500) (index - 250).coerceIn(0, tracks.size - 500) else 0
            val state = PlaybackSnapshot(SyncLog.deviceId, System.currentTimeMillis(), tracks.drop(start).take(500),
                index - start, pendingSeekPosition ?: 0L, true, false, uiRepeatMode.name)
            ConnectManager.command(ConnectManager.selectedDevice.value!!, "transfer", state = state)
            pendingSeekPosition = null
            return
        }

        // Leaving the app's own navigation for something that is not the shared playlist means leaving it.
        if (!applyingTogether && context?.navigationId?.startsWith(TOGETHER_NAV_PREFIX) != true) {
            com.alananasss.kittytune.data.together.Together.stopListening()
        }

        SyncPlayback.claimLocal()
        val cleanTracks = BlockManager.filterBlocked(tracks)
        if (cleanTracks.isEmpty()) return
        if (!maintainPlayerState) {
            isPlayerExpanded = false
        }
        SoundCloudTelemetryTracker.onQueueReset()
        _originalQueue.clear(); _originalQueue.addAll(cleanTracks)
        _queue.clear()
        playedTrackIds = emptySet()
        this.currentContext = context
        MusicManager.updateContext(context)

        val targetTrack = tracks.getOrNull(startIndex)
        val effectiveStartIndex = cleanTracks.indexOfFirst { it.id == targetTrack?.id }.takeIf { it != -1 } ?: (if (startIndex in cleanTracks.indices) startIndex else 0)

        val isHistoryContext =
            context?.navigationId == "history" || context?.navigationId?.startsWith("history") == true

        if (shuffleEnabled && respectShuffle) {
            val clickedTrack = cleanTracks[effectiveStartIndex]
            val rest =
                cleanTracks.filterIndexed { index, _ -> index != effectiveStartIndex }.shuffled()
            _queue.add(clickedTrack)
            _queue.addAll(rest)
            playTrackAtIndex(0, addToHistory = (context == null || isHistoryContext), autoPlay = autoPlay)
        } else {
            _queue.addAll(cleanTracks)
            playTrackAtIndex(effectiveStartIndex, addToHistory = (context == null || isHistoryContext), autoPlay = autoPlay)
        }

        updateQueueState(); saveStateAsync(saveQueue = true)
        prefetchWaveformsForQueue(effectiveStartIndex)

        if (context != null && !isHistoryContext && context.navigationId != "your_mix" && !context.navigationId.contains("your_mix") &&
            context.navigationId != MY_WAVE_NAV_ID && !context.navigationId.contains("wave") &&
            !context.displayText.equals("MyWave", ignoreCase = true) && !context.displayText.contains("MyWave", ignoreCase = true)
        ) {
            // A trailer is the page it was started from, not a page of its own: the row
            // has to name the artist, or it names nobody and opens someone else.
            val historyRoute = com.alananasss.kittytune.domain.HistoryRoute.unwrap(context.navigationId)
            val isStation =
                historyRoute.contains("station") || historyRoute.contains("yt_radio") || historyRoute.contains("spotify_radio")
            val isProfile = historyRoute.contains("profile") || historyRoute.contains("spotify_artist")
            val idLong = when (historyRoute) {
                "likes" -> -1L
                "downloads" -> -2L
                else -> com.alananasss.kittytune.domain.HistoryRoute.numericId(historyRoute) ?: 0L
            }
            val cleanTitle = context.displayText.substringAfter("•").trim()

            val playlistCreator = if (context.artistName != null) User(
                0,
                context.artistName,
                null,
                verified = context.isVerified
            ) else null
            val historyPlaylist = Playlist(
                id = if (idLong != 0L) idLong else kotlin.math.abs(historyRoute.hashCode().toLong()),
                title = cleanTitle,
                artworkUrl = context.imageUrl,
                calculatedArtworkUrl = null,
                trackCount = tracks.size,
                user = playlistCreator,
                tracks = null,
                permalinkUrl = historyRoute,
                urn = historyRoute
            )

            HistoryRepository.addToHistory(historyPlaylist, isStation, isProfile)
        }
    }

    fun playTrackAtPosition(track: Track, position: Long) {
        com.alananasss.kittytune.audio.haptics.HapticUtil.performUIHaptic(context)
        pendingSeekPosition = position; playPlaylist(listOf(track), 0); showCommentsSheet = false; isPlayerExpanded =
            true
    }

    /**
     * A collection's Shuffle button. It turns shuffle mode on rather than handing over a pre-shuffled copy:
     * with the copy, the original order was lost and shuffle stayed off, so the next tap on a track of the
     * same list played straight down from it instead of jumping in ahead of a shuffled queue (issue #66).
     */
    fun playPlaylistShuffled(tracks: List<Track>, context: PlaybackContext? = null) {
        if (tracks.isEmpty()) return
        com.alananasss.kittytune.audio.haptics.HapticUtil.performHeavyHaptic(getApplication<Application>())
        if (!shuffleEnabled) {
            shuffleEnabled = true
        }
        playPlaylist(tracks, startIndex = tracks.indices.random(), context = context)
    }

    fun skipToQueueItem(index: Int, autoPlay: Boolean = true) {
        if (routeRemote("queue", index.toLong())) return
        val keepsUpcoming = isQueuePreserveUpcomingEnabled || shuffleEnabled
        if (keepsUpcoming && index > currentQueueIndex && currentQueueIndex >= 0 && index < _queue.size) {
            val targetIndex = currentQueueIndex + 1
            moveQueueItem(index, targetIndex)
            playTrackAtIndex(
                targetIndex,
                addToHistory = false,
                autoPlay = autoPlay
            )
        } else {
            playTrackAtIndex(
                index,
                addToHistory = false,
                autoPlay = autoPlay
            )
        }
        AchievementManager.trackSkipped()
        AchievementManager.increment("skipper_100")
        AchievementManager.increment("skipper_1000")
    }

    private fun playTrackAtIndex(
        index: Int,
        addToHistory: Boolean = true,
        isCrossfade: Boolean = false,
        autoPlay: Boolean = true
    ) {
        if (index < 0 || index >= _queue.size) {
            MusicManager.cancelCrossfade()
            currentContext = null
            return
        }
        val candidate = _queue[index]
        if (BlockManager.isBlocked(candidate)) {
            _queue.removeAt(index)
            _originalQueue.removeAll { it.id == candidate.id }
            updateQueueState()
            playTrackAtIndex(index, addToHistory, isCrossfade, autoPlay)
            return
        }
        currentQueueIndex = index
        val trackToPlay = _queue[index]
        playedTrackIds = playedTrackIds + trackToPlay.id

        playWhenReady = autoPlay
        progressJob?.cancel()
        playJob?.cancel()
        playJob = null
        trackInitJob?.cancel()
        trackInitJob = null
        queueChunkingJob?.cancel()
        queueChunkingJob = null
        isLoading = true
        duration = trackToPlay.actualDurationMs
        currentPosition = 0L

        MusicManager.pendingSeekPositionMs = null
        MusicManager.playWhenReadyRequested = autoPlay
        MusicManager.currentTrack = trackToPlay
        currentTrack = trackToPlay
        MusicManager.isLoadingTrack = true

        if (!isCrossfade) {
            MusicManager.cancelCrossfade()
            try {
                MusicManager.player.pause()
                MusicManager.player.stop()
                val artist = trackToPlay.displayArtist.ifBlank { getString(R.string.unknown_artist) }
                val tempMetadata = MediaMetadata.Builder()
                    .setTitle(trackToPlay.title ?: getString(R.string.untitled_track))
                    .setArtist(artist)
                    .setSubtitle(artist)
                    .setIsPlayable(true)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setArtworkUri(trackToPlay.fullResArtwork.toUri())
                    .setDurationMs(trackToPlay.actualDurationMs)
                    .build()
                val placeholderItem = MediaItem.Builder()
                    .setMediaId(trackToPlay.id.toString())
                    .setUri("soundtune://track/${trackToPlay.id}".toUri())
                    .setMediaMetadata(tempMetadata)
                    .build()
                MusicManager.player.setMediaItem(placeholderItem)
            } catch (_: Exception) {}
        }
        beginListenSession(trackToPlay)
        // Loaded before the stream reaches the player, so a trim that moves the starting point applies as a
        // start position rather than as a jump the listener hears (issue #33).
        loadTrimFor(trackToPlay.id)
        hasPushedRecentlyPlayed = false

        currentTrack = trackToPlay; MusicManager.currentTrack = trackToPlay
        updatePlayerColors(trackToPlay)
        djFlowController.onTrackChanged(trackToPlay)
        feedHapticBeatGrid(trackToPlay)
        djFlowController.onQueueUpdated(_queue.toList(), currentQueueIndex)
        val intent = Intent(context, PlaybackService::class.java).apply { action = PlaybackService.ACTION_FORCE_UPDATE }
        startServiceSafe(context, intent)

        trackInitJob = viewModelScope.launch {
            var finalTrack = trackToPlay

            val isLocalOrDownloaded = finalTrack.source == "local" ||
                withContext(Dispatchers.IO) {
                    try {
                        val db = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(context).downloadDao()
                        db.getTrack(trackToPlay.id)?.localAudioPath?.isNotEmpty() == true
                    } catch (_: Exception) { false }
                }

            if (!isLocalOrDownloaded && finalTrack.source == "soundcloud" && trackToPlay.id > 0 && (trackToPlay.user?.id == 0L || trackToPlay.media == null || trackToPlay.playbackCount == 0)) {
                try {
                    val fullTrackList = api.getTracksByIds(trackToPlay.id.toString())
                    if (fullTrackList.isNotEmpty()) {
                        finalTrack = fullTrackList[0]
                        if (index in _queue.indices && _queue[index].id == trackToPlay.id) {
                            _queue[index] = finalTrack
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if ((finalTrack.source == "spotify" || finalTrack.user?.urn?.startsWith("spotify") == true) && finalTrack.user != null) {
                val firstArtist = finalTrack.artists?.firstOrNull()
                if (firstArtist != null && firstArtist.id.isNotBlank()) {
                    val updatedUser = finalTrack.user!!.copy(
                        verified = firstArtist.verified || finalTrack.user!!.verified,
                        avatarUrl = firstArtist.avatarUrl ?: finalTrack.user!!.avatarUrl,
                        urn = "spotify:artist:${firstArtist.id}",
                        permalink = firstArtist.id
                    )
                    finalTrack = finalTrack.copy(user = updatedUser)
                    if (index in _queue.indices && _queue[index].id == trackToPlay.id) {
                        _queue[index] = finalTrack
                    }
                }
            }

            if (!isActive || currentTrack?.id != trackToPlay.id) return@launch

            currentTrack = finalTrack
            MusicManager.currentTrack = finalTrack
            if (finalTrack.fullResArtwork != trackToPlay.fullResArtwork) {
                updatePlayerColors(finalTrack)
            }
            isLiked = LikeRepository.isTrackLiked(finalTrack.id)
            loadLyrics(finalTrack)
            AchievementManager.checkTrackNameSecret(finalTrack.title ?: "")
            saveStateAsync(saveQueue = false)

            val automixPlan = com.alananasss.kittytune.audio.automix.AutomixManager.currentAutomixPlan
            val djState = djFlowController.flowState.value
            val startPos = if (isTrailerActive) {
                val total = finalTrack.actualDurationMs.takeIf { it > 0L } ?: finalTrack.durationMs ?: 0L
                if (total > 0L) ClipWindow.forTrailer(total, TRAILER_SNIPPET_MS, TRAILER_START).startMs else 0L
            } else if (isCrossfade) {
                if (djCustomStartPositionMs != null) {
                    val custom = djCustomStartPositionMs!!
                    djCustomStartPositionMs = null
                    custom
                } else if (djState.isActive && djState.incomingDropPointMs != null && djState.incomingDropPointMs > 0L) {
                    djState.incomingDropPointMs
                } else if (automixPlan != null) {
                    automixPlan.incomingStartMs
                } else 0L
            } else 0L

            SoundCloudTelemetryTracker.onTrackStarted(
                track = finalTrack,
                context = currentContext,
                isManual = true,
                startPositionMs = startPos
            )

            playRobustly(index, autoPlay = autoPlay, startPosition = startPos, isCrossfade = isCrossfade, trackOverride = finalTrack)

            prefetchWaveformsForQueue(index)

            // The host's song changed: the shared playlist wants to know at once, not at the next tick.
            com.alananasss.kittytune.data.together.Together.onHostPlaybackChanged()

            if (addToHistory && currentContext?.navigationId?.startsWith("station:") != true && currentContext?.navigationId?.startsWith(
                    "yt_radio:"
                ) != true
            ) {
                HistoryRepository.addToHistory(finalTrack)
            }
        }
    }

    /**
     * Single entry point for every "next" the user triggers — the on-screen buttons, a
     * headphone click, the notification, the widget and the media session all route here.
     *
     * Two things have to be handled that a direct [playNext] call does not:
     *
     * **Rapid presses.** Each skip starts a track load, and there was no debounce anywhere: on a
     * headphone button that repeats, or an impatient tap-tap-tap, loads stacked on top of each
     * other. With DJ Flow that also re-entered the transition planner mid-transition, which is
     * how three crossfades ended up running inside 0.7 s.
     *
     * **What a skip means in DJ Flow.** With the engine running, a cut to the next track is the
     * one thing the feature exists to avoid. The first press therefore blends — immediately, not
     * on the next phrase, because a button that appears to do nothing for three seconds feels
     * broken. A second press while that blend is running means the listener wants out now, so it
     * falls through to a hard skip.
     */
    /**
     * Hands the analysed beat grid to the haptics, so the vibration follows the music's own beat
     * rather than a second, independent transient detector guessing at it.
     *
     * Deliberately not routed through [DjFlowController]: haptics are a playback feature and
     * must keep working for people who never switch DJ Flow on. The grid is read straight from
     * the analysis cache, and the bar position is only passed on when the downbeat was actually
     * trusted — a wrong "this is beat 1" drives the strongest pulse in the bar.
     */
    private fun feedHapticBeatGrid(track: Track?) {
        val haptics = com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(context)
        if (track == null) {
            haptics.clearTrackBeatGrid()
            return
        }
        haptics.clearTrackBeatGrid()
        haptics.onPositionDiscontinuity(0L)
        viewModelScope.launch(Dispatchers.IO) {
            val info = try {
                AppDatabase.getDatabase(context).beatInfoDao().getBeatInfo(track.id.toString())
            } catch (_: Exception) {
                null
            } ?: return@launch
            if (info.bpm <= 1f) return@launch

            val trust = info.gridTrust
            withContext(Dispatchers.Main) {
                haptics.onTrackBeatGrid(
                    bpm = info.bpm,
                    anchorMs = info.trustedAnchorMs,
                    // Bar emphasis needs a real downbeat; below that the grid still fixes the
                    // timing but the bar shape is left flat.
                    beatsPerBar = if (trust == com.alananasss.kittytune.data.local.GridTrust.BEAT) 0 else 4,
                )
            }
        }
    }

    fun requestSkipNext() {
        if (routeRemote("next")) return
        com.alananasss.kittytune.audio.haptics.HapticUtil.performUIHaptic(context)
        val now = android.os.SystemClock.elapsedRealtime()
        val dj = djFlowController.flowState.value
        val action = SkipDecision.decide(
            sinceLastPressMs = now - lastSkipRequestAt,
            djActive = dj.isActive,
            hasNextMatch = dj.nextTrackMatch != null,
            transitionRunning = MusicManager.isCrossfadingOut ||
                dj.transitionPhase != TransitionPhase.IDLE,
        )
        if (action == SkipAction.IGNORE) return
        lastSkipRequestAt = now

        when (action) {
            SkipAction.BLEND -> {
                android.util.Log.d("PlayerViewModel", "Skip -> DJ blend")
                djFlowController.triggerTransition()
            }
            SkipAction.HARD_SKIP -> {
                android.util.Log.d("PlayerViewModel", "Skip -> hard cut")
                // Stop the engine finishing a fade into a track the listener just skipped past.
                djFlowController.cancelTransition()
                playNext(manual = true, isCrossfade = playerPrefs.getCrossfadeEnabled())
            }
            SkipAction.IGNORE -> {}
        }
    }

    fun playNext(
        manual: Boolean = true,
        isCrossfade: Boolean = playerPrefs.getCrossfadeEnabled(),
        ignoreRepeatOne: Boolean = false
    ) {
        if (manual && routeRemote("next")) return
        if (isAutoplayRadioLoading) return
        if (manual && !applyingTogether && com.alananasss.kittytune.data.together.Together.active.value != null) {
            com.alananasss.kittytune.data.together.Together.control(
                com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_NEXT, currentPosition
            )
        }

        if (manual && player.currentPosition > 2000) {
            incrementPlayCount()
        }
        if (manual) flushListenSession("SKIP_NEXT")

        if (!manual && !ignoreRepeatOne && repeatMode == RepeatMode.ONE) {
            AchievementManager.increment("obsessed_50")
            AchievementManager.increment("obsessed_200")
            flushListenSession("REPEAT_ONE_LOOP")
            playTrackAtIndex(
                currentQueueIndex,
                addToHistory = false,
                isCrossfade = isCrossfade,
                autoPlay = true
            )
            return
        }

        val nextIndex = currentQueueIndex + 1

        // The wave tops itself up before it runs dry (PR #77): the listener never sees the end.
        if (!manual && nextIndex >= _queue.size - WAVE_LOW_WATER && currentContext?.navigationId == MY_WAVE_NAV_ID) {
            viewModelScope.launch { refillWaveIfLow() }
        }

        if (manual) {
            AchievementManager.trackSkipped()
            AchievementManager.increment("skipper_100")
            AchievementManager.increment("skipper_1000")
        }

        if (nextIndex < _queue.size) {
            playTrackAtIndex(nextIndex, addToHistory = false, isCrossfade = isCrossfade, autoPlay = true)
        } else {
            if (repeatMode == RepeatMode.ALL) {
                playTrackAtIndex(0, addToHistory = false, isCrossfade = isCrossfade, autoPlay = true)
            } else {
                val autoPlayEnabled = playerPrefs.getAutoplayEnabled()
                val radioKind = RadioEligibility.radioKind(currentTrack)
                val isYoutube = radioKind == RadioEligibility.Kind.YOUTUBE

                if ((autoPlayEnabled || isYoutube) && radioKind != RadioEligibility.Kind.NONE) {
                    viewModelScope.launch {
                        val youtubeFallback = playerPrefs.getYouTubeFallbackEnabled()

                        when (radioKind) {
                            RadioEligibility.Kind.SPOTIFY -> fetchAndQueueSpotifyRadio()
                            RadioEligibility.Kind.TIDAL -> fetchAndQueueTidalRadio()
                            RadioEligibility.Kind.DEEZER -> fetchAndQueueDeezerRadio()
                            RadioEligibility.Kind.QOBUZ -> fetchAndQueueQobuzRadio()
                            RadioEligibility.Kind.YOUTUBE -> fetchAndPlayYoutubeRadio()
                            RadioEligibility.Kind.SOUND_CLOUD -> {
                                if (currentTrack?.source == "soundcloud" && youtubeFallback) {
                                    fetchAndPlayYoutubeRadio()
                                } else {
                                    fetchAndQueueRadio()
                                }
                            }
                            RadioEligibility.Kind.NONE -> Unit
                        }

                        val newNextIndex = currentQueueIndex + 1
                        if (newNextIndex < _queue.size) {
                            playTrackAtIndex(
                                newNextIndex,
                                addToHistory = false,
                                isCrossfade = isCrossfade,
                                autoPlay = true
                            )
                        } else {
                            MusicManager.cancelCrossfade()
                            MusicManager.player.pause()
                            MusicManager.player.seekTo(0)
                            saveStateAsync()
                        }
                    }
                } else {
                    MusicManager.cancelCrossfade()
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
                    artistName = lastTrack.displayArtist.ifBlank { lastTrack.user?.username.orEmpty() },
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
                val newTracks = radioTracks.filter { track -> _queue.none { it.id == track.id } && !BlockManager.isBlocked(track) }
                val existingSignatures = _queue.map { "${normalizeStationTitle(it.title)}|${it.displayArtist.lowercase().trim()}" }.toMutableSet()
                val dedupedTracks = newTracks.filter { track ->
                    val norm = normalizeStationTitle(track.title)
                    if (norm.isBlank()) true
                    else existingSignatures.add("$norm|${track.displayArtist.lowercase().trim()}")
                }

                _queue.addAll(dedupedTracks)
                _originalQueue.addAll(dedupedTracks)
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
        if (!RadioEligibility.canStartRadio(lastTrack)) return
        isAutoplayRadioLoading = true
        try {
            val station = api.getTrackStation(lastTrack.id)
            val partialTracks = station.tracks
            if (!partialTracks.isNullOrEmpty()) {
                val newTrackIds = partialTracks.map { it.id }.filter { trackId -> _queue.none { it.id == trackId } }
                if (newTrackIds.isNotEmpty()) {
                    val unorderedFullTracks = api.getTracksByIds(newTrackIds.joinToString(","))
                    val trackMap = unorderedFullTracks.associateBy { it.id }
                    val orderedFullTracks = newTrackIds.mapNotNull { id -> trackMap[id] }.filter { !BlockManager.isBlocked(it) }
                    val existingSignatures = _queue.map { "${normalizeStationTitle(it.title)}|${it.displayArtist.lowercase().trim()}" }.toMutableSet()
                    val dedupedTracks = orderedFullTracks.filter { track ->
                        val norm = normalizeStationTitle(track.title)
                        if (norm.isBlank()) true
                        else existingSignatures.add("$norm|${track.displayArtist.lowercase().trim()}")
                    }
                    _queue.addAll(dedupedTracks); _originalQueue.addAll(dedupedTracks); updateQueueState()
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

    private suspend fun fetchAndQueueSpotifyRadio() {
        val lastTrack = currentTrack ?: return
        isAutoplayRadioLoading = true
        try {
            val rawId = lastTrack.permalink?.takeIf { it.isNotBlank() && !it.contains("/") }
                ?: lastTrack.permalinkUrl?.let { com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(it) }
                ?: lastTrack.user?.urn?.let { com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(it) }
                ?: return

            val spotifyId = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(rawId)
            if (spotifyId.isBlank()) return

            val radioPlaylist =
                com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadio(spotifyId, isArtist = false)
            val rawTracks =
                radioPlaylist?.tracks ?: com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadioTracks(
                    spotifyId
                )

            val tracksToAdd =
                rawTracks.drop(1).map { it.toTrack() }.filter { track -> _queue.none { it.id == track.id } && !BlockManager.isBlocked(track) }

            if (tracksToAdd.isNotEmpty()) {
                val existingSignatures = _queue.map { "${normalizeStationTitle(it.title)}|${it.displayArtist.lowercase().trim()}" }.toMutableSet()
                val dedupedTracks = tracksToAdd.filter { track ->
                    val norm = normalizeStationTitle(track.title)
                    if (norm.isBlank()) true
                    else existingSignatures.add("$norm|${track.displayArtist.lowercase().trim()}")
                }
                _queue.addAll(dedupedTracks)
                _originalQueue.addAll(dedupedTracks)
                updateQueueState()
            }
            if (currentContext == null) {
                val ctx = PlaybackContext(
                    displayText = getString(R.string.context_station, lastTrack.title ?: ""),
                    navigationId = "spotify_radio:$spotifyId",
                    imageUrl = lastTrack.fullResArtwork,
                    artistName = lastTrack.displayArtist.ifBlank { lastTrack.user?.username.orEmpty() },
                    isVerified = lastTrack.user?.verified == true
                )
                currentContext = ctx
                MusicManager.updateContext(ctx)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAutoplayRadioLoading = false
        }
    }

    /**
     * End-of-queue autoplay for TIDAL catalogue tracks: ask TIDAL for the radio
     * around the seed, append its tracks (skipping the seed itself) and register
     * the station context so it lands in history like the other radios.
     */
    private suspend fun fetchAndQueueTidalRadio() {
        val lastTrack = currentTrack ?: return
        val tidalId = com.alananasss.kittytune.data.tidal.TidalSearchRepository.extractTrackId(lastTrack) ?: return
        isAutoplayRadioLoading = true
        try {
            val radioTracks = com.alananasss.kittytune.data.tidal.TidalSearchRepository
                .getRadio(getApplication(), tidalId)

            val tracksToAdd = radioTracks
                .filter { it.id != lastTrack.id }
                .distinctBy { it.id }
                .filter { track -> _queue.none { it.id == track.id } }
                .filter { !BlockManager.isBlocked(it) }

            if (tracksToAdd.isNotEmpty()) {
                val existingSignatures = _queue.map { "${normalizeStationTitle(it.title)}|${it.displayArtist.lowercase().trim()}" }.toMutableSet()
                val dedupedTracks = tracksToAdd.filter { track ->
                    val norm = normalizeStationTitle(track.title)
                    if (norm.isBlank()) true
                    else existingSignatures.add("$norm|${track.displayArtist.lowercase().trim()}")
                }
                _queue.addAll(dedupedTracks)
                _originalQueue.addAll(dedupedTracks)
                updateQueueState()
            }
            if (currentContext == null) {
                val ctx = PlaybackContext(
                    displayText = getString(R.string.context_station, lastTrack.title ?: ""),
                    navigationId = "tidal_radio:$tidalId",
                    imageUrl = lastTrack.fullResArtwork,
                    artistName = lastTrack.displayArtist.ifBlank { lastTrack.user?.username.orEmpty() },
                    isVerified = lastTrack.user?.verified == true
                )
                currentContext = ctx
                MusicManager.updateContext(ctx)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAutoplayRadioLoading = false
        }
    }

    /**
     * End-of-queue autoplay for Deezer catalogue tracks: ask Deezer for the
     * artist radio around the seed and append its tracks, so the queue keeps
     * playing music of the same kind instead of stopping dead.
     */
    private suspend fun fetchAndQueueDeezerRadio() {
        val lastTrack = currentTrack ?: return
        val artistId = deezerArtistId(lastTrack) ?: return
        isAutoplayRadioLoading = true
        try {
            val radioTracks = com.alananasss.kittytune.data.deezer.DeezerSearchRepository.getRadio(artistId)

            val tracksToAdd = radioTracks
                .filter { it.id != lastTrack.id }
                .distinctBy { it.id }
                .filter { track -> _queue.none { it.id == track.id } }
                .filter { !BlockManager.isBlocked(it) }

            if (tracksToAdd.isNotEmpty()) {
                _queue.addAll(tracksToAdd)
                _originalQueue.addAll(tracksToAdd)
                updateQueueState()
            }
            if (currentContext == null) {
                val ctx = PlaybackContext(
                    displayText = getString(R.string.context_station, lastTrack.title ?: ""),
                    navigationId = "deezer_radio:$artistId",
                    imageUrl = lastTrack.fullResArtwork,
                    artistName = lastTrack.displayArtist.ifBlank { lastTrack.user?.username.orEmpty() },
                    isVerified = lastTrack.user?.verified == true
                )
                currentContext = ctx
                MusicManager.updateContext(ctx)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAutoplayRadioLoading = false
        }
    }

    /**
     * End-of-queue autoplay for Qobuz catalogue tracks: build a station around
     * the seed from Qobuz's own catalogue (the album it belongs to, the artist,
     * and the artists Qobuz lists as similar) and append it to the queue.
     */
    private suspend fun fetchAndQueueQobuzRadio() {
        val lastTrack = currentTrack ?: return
        val trackId = qobuzTrackId(lastTrack) ?: return
        isAutoplayRadioLoading = true
        try {
            val radioTracks = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository.getRadio(trackId)

            val tracksToAdd = radioTracks
                .filter { it.id != lastTrack.id }
                .distinctBy { it.id }
                .filter { track -> _queue.none { it.id == track.id } }
                .filter { !BlockManager.isBlocked(it) }

            if (tracksToAdd.isNotEmpty()) {
                _queue.addAll(tracksToAdd)
                _originalQueue.addAll(tracksToAdd)
                updateQueueState()
            }
            if (currentContext == null) {
                val ctx = PlaybackContext(
                    displayText = getString(R.string.context_station, lastTrack.title ?: ""),
                    navigationId = "qobuz_radio:$trackId",
                    imageUrl = lastTrack.fullResArtwork,
                    artistName = lastTrack.displayArtist.ifBlank { lastTrack.user?.username.orEmpty() },
                    isVerified = lastTrack.user?.verified == true
                )
                currentContext = ctx
                MusicManager.updateContext(ctx)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isAutoplayRadioLoading = false
        }
    }

    fun playPrevious(
        manual: Boolean = true,
        isCrossfade: Boolean = playerPrefs.getCrossfadeEnabled()
    ) {
        if (manual) {
            com.alananasss.kittytune.audio.haptics.HapticUtil.performUIHaptic(context)
            flushListenSession("SKIP_PREVIOUS")
        }

        val prevIndex = currentQueueIndex - 1
        if (prevIndex >= 0) {
            playTrackAtIndex(prevIndex, addToHistory = false, isCrossfade = isCrossfade, autoPlay = true)
        } else {
            playTrackAtIndex(0, addToHistory = false, isCrossfade = isCrossfade, autoPlay = true)
        }
    }

    fun smartPrevious() {
        if (routeRemote("previous")) return
        if (!applyingTogether && com.alananasss.kittytune.data.together.Together.active.value != null) {
            com.alananasss.kittytune.data.together.Together.control(
                com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_PREVIOUS, currentPosition
            )
        }
        val pos = maxOf(player.currentPosition, currentPosition)
        if (pos > 2000) {
            incrementPlayCount()
        }

        // "Previous" always means the track before this one; rewinding first made it take two presses
        // (issue #66). Only the first track of the queue, with nothing before it, starts over.
        val prev = currentQueueIndex - 1
        if (prev < 0) {
            flushListenSession("MANUAL_REPLAY")
            beginListenSession(currentTrack)
            seekTo(0L)
            playWhenReady = true
            player.play()
        } else {
            val crossfadeEnabled = playerPrefs.getCrossfadeEnabled()
            playPrevious(manual = true, isCrossfade = crossfadeEnabled)
        }
    }

    fun toggleShuffle() {
        if (routeRemote("shuffle", if (uiShuffleEnabled) 0 else 1)) return
        com.alananasss.kittytune.audio.haptics.HapticUtil.performMediumHaptic(context)
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
        MusicManager.setRepeatMode(exoMode)
        if (repeatMode == RepeatMode.ONE) {
            while (MusicManager.player.mediaItemCount > 1) {
                try {
                    MusicManager.player.removeMediaItem(1)
                } catch (_: Exception) {}
            }
        }
    }

    fun toggleRepeatMode() {
        if (routeRemote("repeat", when (uiRepeatMode) { RepeatMode.NONE -> 1; RepeatMode.ALL -> 2; RepeatMode.ONE -> 0 })) return
        com.alananasss.kittytune.audio.haptics.HapticUtil.performMediumHaptic(context)
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
        djFlowController.onQueueUpdated(queueState, currentQueueIndex)
    }

    fun moveQueueItem(from: Int, to: Int) {
        if (routeRemote("move", from.toLong(), to.toLong())) return
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
        if (routeRemote("remove", index.toLong())) return
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
        val cleanTracks = BlockManager.filterBlocked(tracks)
        if (cleanTracks.isEmpty()) return
        val insertIndex = currentQueueIndex + 1

        val uniqueTracks = cleanTracks.map { it.copy() }

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
        if (routeRemote(if (uiIsPlaying) "pause" else "play")) return
        if (!applyingTogether && com.alananasss.kittytune.data.together.Together.active.value != null) {
            com.alananasss.kittytune.data.together.Together.control(
                if (player.isPlaying) com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_PAUSE
                else com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_PLAY,
                currentPosition
            )
        }
        SyncPlayback.claimLocal()
        com.alananasss.kittytune.audio.haptics.PlayerHapticManager.triggerInteractionHaptic(
            context,
            com.alananasss.kittytune.data.local.PlayerPreferences.KEY_HAPTICS_PLAY_PAUSE
        )
        if (player.isPlaying || playWhenReady) {
            playWhenReady = false
            player.pause()
            saveStateAsync(savePositionOnly = true)
        } else {
            playWhenReady = true
            restoreDismissedPlayerStateIfNeeded()
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

    fun pause() {
        if (routeRemote("pause")) return
        if (player.isPlaying || playWhenReady) {
            playWhenReady = false
            player.pause()
            saveStateAsync(savePositionOnly = true)
        }
    }

    fun seekTo(position: Long) {
        if (routeRemote("seek", position)) { isScrubbing = false; return }
        if (!applyingTogether && com.alananasss.kittytune.data.together.Together.active.value != null) {
            com.alananasss.kittytune.data.together.Together.control(
                com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_SEEK, position
            )
        }
        SyncPlayback.claimLocal()
        MusicManager.releasePrebuffered()
        com.alananasss.kittytune.audio.haptics.PlayerHapticManager.triggerInteractionHaptic(
            context,
            com.alananasss.kittytune.data.local.PlayerPreferences.KEY_HAPTICS_SEEK
        )
        isScrubbing = false
        lastSeekTimestamp = System.currentTimeMillis()
        if (MusicManager.isLoadingTrack) {
            MusicManager.pendingSeekPositionMs = position
        } else {
            player.seekTo(position)
        }
        MusicManager.notifySeek(position)
        currentPosition = position
        com.alananasss.kittytune.audio.haptics.PlayerHapticManager.getInstance(context).onPositionDiscontinuity(position)
        SoundCloudTelemetryTracker.onTrackSeeked(position)
        saveStateAsync(saveQueue = false)
    }

    // ─── Shared playlist ─────────────────────────────────────────────────────────────────────────────
    // See [com.alananasss.kittytune.data.together.Together]. While the player follows the host, its own actions
    // are not sent back out.

    private var applyingTogether = false

    // Bumped by every incoming host State so a stale deferred action knows it was overtaken; see
    // followHost's 600 ms pause for why that matters.
    private var togetherFollowGeneration = 0

    /** How many "play next" requests have gone in after the song playing, so the next one goes after them. */
    private var togetherNextOffset = 0

    private fun togetherContext(): PlaybackContext {
        val code = com.alananasss.kittytune.data.together.Together.active.value.orEmpty()
        val name = com.alananasss.kittytune.data.together.Together.rooms.value[code]?.name.orEmpty()
        return PlaybackContext(
            displayText = name.ifBlank { getString(R.string.together_title) },
            navigationId = TOGETHER_NAV_PREFIX + code
        )
    }

    override fun sharedCurrent() = currentTrack?.let { com.alananasss.kittytune.data.together.SharedTrack.of(it) }
    override fun sharedPositionMs() = currentPosition
    override fun sharedIsPlaying() = isPlaying
    override fun sharedUpcoming() =
        _queue.drop(currentQueueIndex + 1).map { com.alananasss.kittytune.data.together.SharedTrack.of(it) }
    override fun sharedAutomix() = playerPrefs.getAutomixEnabled()

    override fun followHost(
        track: com.alananasss.kittytune.data.together.SharedTrack,
        positionMs: Long,
        isPlaying: Boolean,
        upcoming: List<com.alananasss.kittytune.data.together.SharedTrack>?,
        automix: Boolean,
    ) {
        applyingTogether = true
        try {
            // Every State bumps this: a pending deferred pause from an older State is overtaken by
            // any newer one, whether it pauses (applies it immediately below) or resumes (the
            // stale pause must not fire after it).
            val generation = ++togetherFollowGeneration
            if (playerPrefs.getAutomixEnabled() != automix) playerPrefs.setAutomixEnabled(automix)
            if (currentTrack?.id != track.id) {
                pendingSeekPosition = positionMs
                val tracks = listOf(track) + upcoming.orEmpty().filter { it.id != track.id }
                playPlaylist(
                    tracks.map { it.toTrack() }, 0, togetherContext(),
                    maintainPlayerState = true, autoPlay = true, respectShuffle = false
                )
                // The 600 ms grace lets playPlaylist spin up before pausing into the host's state.
                // It must not outlive a newer State: a host that resumed within the window had its
                // older "paused" State still pause the follower afterwards, contradicting the
                // fresher one — so the deferred pause only fires when no newer State has arrived.
                if (!isPlaying) {
                    viewModelScope.launch {
                        delay(600)
                        if (generation == togetherFollowGeneration) {
                            applyingTogether = true
                            try {
                                pause()
                            } finally {
                                applyingTogether = false
                            }
                        }
                    }
                }
                return
            }
            if (kotlin.math.abs(currentPosition - positionMs) > TOGETHER_MAX_DRIFT_MS) seekTo(positionMs)
            if (isPlaying != this.isPlaying) togglePlayPause()
            if (upcoming != null) {
                val mine = _queue.drop(currentQueueIndex + 1).map { it.id }
                if (mine != upcoming.map { it.id }) {
                    clearUpcoming()
                    appendToQueue(upcoming.map { it.toTrack() })
                }
            }
        } finally {
            applyingTogether = false
        }
    }

    override fun hostControl(action: String, positionMs: Long) {
        applyingTogether = true
        try {
            when (action) {
                com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_PAUSE -> if (isPlaying) togglePlayPause()
                com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_PLAY -> if (!isPlaying) togglePlayPause()
                com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_SEEK -> seekTo(positionMs)
                com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_NEXT -> playNext(manual = true)
                com.alananasss.kittytune.data.together.TogetherMessage.CONTROL_PREVIOUS -> smartPrevious()
            }
        } finally {
            applyingTogether = false
        }
        com.alananasss.kittytune.data.together.Together.onHostPlaybackChanged()
    }

    override fun hostPlayNext(track: com.alananasss.kittytune.data.together.SharedTrack) {
        val at = (currentQueueIndex + 1 + togetherNextOffset).coerceIn(0, _queue.size)
        val item = track.toTrack()
        _queue.add(at, item)
        _originalQueue.add(item)
        togetherNextOffset++
        updateQueueState()
        if (MusicManager.player.mediaItemCount > 1) {
            try {
                MusicManager.player.removeMediaItem(1)
            } catch (_: Exception) {
            }
        }
        preloadNextTrack(currentQueueIndex + 1)
        saveStateAsync(saveQueue = true)
        com.alananasss.kittytune.data.together.Together.onHostPlaybackChanged()
    }

    override fun hostPlay(tracks: List<com.alananasss.kittytune.data.together.SharedTrack>, index: Int) {
        applyingTogether = true
        try {
            playPlaylist(
                tracks.map { it.toTrack() }, index, togetherContext(),
                maintainPlayerState = true, autoPlay = true, respectShuffle = false
            )
        } finally {
            applyingTogether = false
        }
    }

    /** Whether automix may be switched here: not by a listener following someone else's shared playlist. */
    val mayChangeAutomix: Boolean get() = com.alananasss.kittytune.data.together.Together.mayChangeAutomix()

    /** Drops everything after the song playing, in both the live queue and the unshuffled original. */
    fun clearUpcoming() {
        while (_queue.size > currentQueueIndex + 1) _queue.removeAt(_queue.size - 1)
        val playing = currentTrack?.id
        if (playing != null) {
            val idx = _originalQueue.indexOfFirst { it.id == playing }
            if (idx >= 0 && idx + 1 < _originalQueue.size) {
                _originalQueue.subList(idx + 1, _originalQueue.size).clear()
            }
        }
        togetherNextOffset = 0
        updateQueueState()
        if (MusicManager.player.mediaItemCount > 1) {
            try {
                MusicManager.player.removeMediaItem(1)
            } catch (_: Exception) {
            }
        }
        saveStateAsync(saveQueue = true)
    }

    /** Adds [tracks] to the end of the live queue, as a shared playlist's host re-plays its own tail. */
    private fun appendToQueue(tracks: List<Track>) {
        val clean = com.alananasss.kittytune.data.BlockManager.filterBlocked(tracks)
        if (clean.isEmpty()) return
        _queue.addAll(clean)
        _originalQueue.addAll(clean)
        updateQueueState()
        saveStateAsync(saveQueue = true)
    }

    // ─── Artist trailer ──────────────────────────────────────────────────────────────────────────────
    private var trailerJob: Job? = null

    /**
     * An artist's trailer: twenty seconds of each of their top songs, from the part a third of the way in where the
     * hook usually is, one after another, for a first impression of someone new (issue #66). Ends on the last song;
     * pressing anything else leaves it.
     */
    fun playTrailer(tracks: List<Track>, context: PlaybackContext) {
        if (tracks.isEmpty()) return
        com.alananasss.kittytune.audio.haptics.HapticUtil.performUIHaptic(this.context)
        val trailerContext = context.copy(navigationId = TRAILER_NAV_PREFIX + context.navigationId)
        // The first song opens at its window, not at its start and then jumping there once its length is known.
        val firstTrackTotal = tracks.first().actualDurationMs.takeIf { it > 0L } ?: tracks.first().durationMs?.takeIf { it > 0L }
        firstTrackTotal?.let { total ->
            pendingSeekPosition = ClipWindow.forTrailer(total, TRAILER_SNIPPET_MS, TRAILER_START).startMs
        }
        playPlaylist(tracks.take(TRAILER_SONGS), 0, trailerContext, maintainPlayerState = true, autoPlay = true, respectShuffle = false)
        trailerJob?.cancel()
        trailerJob = viewModelScope.launch {
            var settledAt = 0L
            while (isActive && isTrailerActive && currentContext?.navigationId == trailerContext.navigationId) {
                delay(TRAILER_TICK_MS)
                val window = clipWindow ?: continue
                if (isLoading || System.currentTimeMillis() < settledAt) continue
                val position = player.currentPosition
                when {
                    position < window.startMs - TRAILER_SLACK_MS -> {
                        settledAt = System.currentTimeMillis() + TRAILER_SETTLE_MS
                        player.seekTo(window.startMs)
                        currentPosition = window.startMs
                    }
                    position >= window.endMs -> {
                        settledAt = System.currentTimeMillis() + TRAILER_SETTLE_MS
                        if (currentQueueIndex >= _queue.lastIndex) {
                            player.seekTo(window.startMs)
                            currentPosition = window.startMs
                            pause()
                        } else {
                            playNext(manual = true, isCrossfade = false)
                        }
                    }
                }
            }
        }
    }

    /** Whether the queue is a trailer, every song of it only its window. */
    val isTrailerActive: Boolean get() = currentContext?.navigationId?.startsWith(TRAILER_NAV_PREFIX) == true

    /**
     * The part of the song playing now that is shown as the song, or null when all of it is. Known once the song's
     * length is; until then it is shown whole.
     */
    val clipWindow: ClipWindow?
        get() {
            if (!isTrailerActive || duration <= 0L) return null
            return ClipWindow.forTrailer(duration, TRAILER_SNIPPET_MS, TRAILER_START)
        }

    val uiClipWindow: ClipWindow? get() = clipWindow

    /** How long [track] is shown to be: its window in a trailer, null when it is shown at its own length. */
    fun clipLengthOf(track: Track): Long? {
        if (!isTrailerActive) return null
        val total = track.actualDurationMs.takeIf { it > 0L } ?: track.durationMs?.takeIf { it > 0L } ?: return null
        return ClipWindow.forTrailer(total, TRAILER_SNIPPET_MS, TRAILER_START).lengthMs
    }

    /** How far through what is shown as the song a moment is, 0 to 1. */
    fun progressOf(songMs: Float): Float {
        val window = clipWindow
        if (window != null) return ((songMs - window.startMs) / window.lengthMs).coerceIn(0f, 1f)
        return if (duration > 0L) (songMs / duration).coerceIn(0f, 1f) else 0f
    }

    // ─── MyWave ─────────────────────────────────────────────────────────────────────────────
    // See [com.alananasss.kittytune.data.wave.MyWave]: a personal radio that keeps playing.

    val isMyWaveActive: Boolean get() = currentContext?.navigationId == MY_WAVE_NAV_ID

    val waveMode: com.alananasss.kittytune.data.wave.MyWave.WaveMode
        get() = playerPrefs.getWaveMode()

    var isWaveLoading by mutableStateOf(false)
        private set

    private var waveJob: Job? = null

    /** Starts the wave, or switches its mode; the current song, if any, keeps playing through it. */
    fun startMyWave(mode: com.alananasss.kittytune.data.wave.MyWave.WaveMode = waveMode) {
        com.alananasss.kittytune.audio.haptics.HapticUtil.performUIHaptic(context)
        val previousMode = playerPrefs.getWaveMode()
        playerPrefs.setWaveMode(mode)
        waveJob?.cancel()
        if (isMyWaveActive && previousMode == mode) {
            togglePlayPause()
            return
        }
        waveJob = viewModelScope.launch {
            isWaveLoading = true
            try {
                if (isMyWaveActive) {
                    // Changing the mode mid-wave: replace what has not played yet, keep the song playing.
                    val played = _queue.take(currentQueueIndex + 1).map { it.id }.toSet()
                    val batch = com.alananasss.kittytune.data.wave.MyWave.nextBatch(mode, played)
                    if (batch.isNotEmpty()) {
                        clearUpcoming()
                        appendToQueue(batch)
                    }
                } else {
                    val played = _queue.take(currentQueueIndex + 1).map { it.id }.toSet()
                    var batch = com.alananasss.kittytune.data.wave.MyWave.quickBatch(mode, played)
                    if (batch.isEmpty()) batch = com.alananasss.kittytune.data.wave.MyWave.nextBatch(mode, played)
                    if (batch.isEmpty()) {
                        emitUiEvent(getString(R.string.wave_nothing_yet))
                        return@launch
                    }
                    playPlaylist(
                        tracks = batch,
                        startIndex = 0,
                        context = PlaybackContext(displayText = getString(R.string.wave_title), navigationId = MY_WAVE_NAV_ID),
                        maintainPlayerState = isPlaying,
                        autoPlay = true,
                        respectShuffle = false,
                    )
                    val rest = com.alananasss.kittytune.data.wave.MyWave.nextBatch(mode, played + batch.map { it.id }.toSet())
                    if (rest.isNotEmpty()) appendToQueue(rest)
                }
            } finally {
                isWaveLoading = false
            }
        }
    }

    /** The queue under the wave is refilled before it runs out, so the wave never ends on its own. */
    private suspend fun refillWaveIfLow() {
        val mode = playerPrefs.getWaveMode()
        val played = _queue.take(currentQueueIndex + 1).map { it.id }.toSet() +
            _queue.drop(currentQueueIndex + 1).map { it.id }.toSet()
        val batch = com.alananasss.kittytune.data.wave.MyWave.nextBatch(mode, played)
        if (batch.isNotEmpty()) appendToQueue(batch)
    }

    /** A thumbs-down inside the wave: the track is blocked, the artist silenced, the next one plays. */
    fun dislikeCurrentTrackInWave() {
        val track = currentTrack ?: return
        com.alananasss.kittytune.data.wave.WaveFeedback.onDisliked(track)
        playNext(manual = true)
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
        if (routeRemote("like", if (uiIsLiked) 0L else 1L)) {
            val t = uiCurrentTrack
            if (t != null) {
                remoteTrackLiked = !uiIsLiked
                if (!uiIsLiked) {
                    LikeRepository.addLike(t)
                    AchievementManager.increment("liker_50")
                    AchievementManager.increment("liker_1000")
                    AchievementManager.increment("liker_5000")
                } else {
                    LikeRepository.removeLike(t.id)
                }
            }
            com.alananasss.kittytune.audio.haptics.PlayerHapticManager.triggerInteractionHaptic(
                context,
                com.alananasss.kittytune.data.local.PlayerPreferences.KEY_HAPTICS_LIKE
            )
            return
        }
        val t = currentTrack ?: return
        isLiked = !isLiked
        com.alananasss.kittytune.audio.haptics.PlayerHapticManager.triggerInteractionHaptic(
            context,
            com.alananasss.kittytune.data.local.PlayerPreferences.KEY_HAPTICS_LIKE
        )

        if (isLiked) {
            LikeRepository.addLike(t)
            com.alananasss.kittytune.data.wave.WaveFeedback.onLiked(t)
            AchievementManager.increment("liker_50")
            AchievementManager.increment("liker_1000")
            AchievementManager.increment("liker_5000")
        } else {
            LikeRepository.removeLike(t.id)
        }
        ConnectManager.publish(connectSnapshot())
    }

    val isYourMixActive: Boolean
        get() = if (remotePeer?.snapshot != null) remotePeer?.snapshot?.isYourMixActive == true else currentContext?.navigationId == "your_mix"

    fun dislikeCurrentTrackInMix() {
        if (routeRemote("dislike")) return
        val track = currentTrack ?: return
        playerPrefs.addMixDislikedTrack(track.id)
        if (isLiked) {
            isLiked = false
            LikeRepository.removeLike(track.id)
        }
        val currentIndex = currentQueueIndex
        if (_queue.size > 1 && currentIndex in _queue.indices) {
            playNext(manual = true)
            removeTrackFromQueue(currentIndex)
        } else {
            playNext(manual = true)
        }
    }

    fun toggleTrackLike(track: Track) {
        if (remotePeer?.snapshot != null && track.id == uiCurrentTrack?.id) {
            toggleLike()
            return
        }
        if (track.id == currentTrack?.id) {
            toggleLike()
        } else {
            val isCurrentlyLiked = LikeRepository.isTrackLiked(track.id)
            if (isCurrentlyLiked) {
                com.alananasss.kittytune.audio.haptics.HapticUtil.performRejectHaptic(context)
                LikeRepository.removeLike(track.id)
            } else {
                com.alananasss.kittytune.audio.haptics.HapticUtil.performConfirmHaptic(context)
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
        if (routeRemote("speed", (speed * 100f).toLong())) {
            effectsState = effectsState.copy(speed = speed)
            return
        }
        val factor = if (isPreciseSpeedEnabled) 20f else 10f;
        val r = (speed * factor).roundToInt() / factor; effectsState =
            effectsState.copy(speed = r); applyEffectsAndSave()
        ConnectManager.publish(connectSnapshot())
    }

    fun resetAllEffects() {
        effectsState = AudioEffectsState(
            speed = effectsState.speed,
            isPitchEnabled = effectsState.isPitchEnabled,
            ambientType = effectsState.ambientType
        )
        applyEffectsAndSave()
        ConnectManager.publish(connectSnapshot())
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

    fun disableEffects(fxIds: Collection<String>) {
        if (fxIds.isEmpty()) return
        var state = effectsState
        var changed = false
        if (fxIds.contains("equalizer") && equalizerState.isEnabled) {
            equalizerState = equalizerState.copy(isEnabled = false)
            applyEqualizerAndSave()
            ConnectManager.publish(connectSnapshot())
        }
        for (fxId in fxIds) {
            val updated = when (fxId) {
                "bass_boost" -> if (state.isBassBoostEnabled) state.copy(isBassBoostEnabled = false) else null
                "sub_octaver" -> if (state.isSubOctaverEnabled) state.copy(isSubOctaverEnabled = false) else null
                "tape_saturation" -> if (state.isTapeSaturationEnabled) state.copy(isTapeSaturationEnabled = false) else null
                "vocal_boost" -> if (state.isVocalBoostEnabled) state.copy(isVocalBoostEnabled = false) else null
                "vocal_remover" -> if (state.isVocalRemoverEnabled) state.copy(isVocalRemoverEnabled = false) else null
                "normalization" -> if (state.isNormalizationEnabled) state.copy(isNormalizationEnabled = false) else null
                "earrape" -> if (state.isEarrapeEnabled) state.copy(isEarrapeEnabled = false) else null
                "eight_d" -> if (state.is8DEnabled) state.copy(is8DEnabled = false) else null
                "super_wide" -> if (state.isSuperWideEnabled) state.copy(isSuperWideEnabled = false) else null
                "chorus" -> if (state.isChorusEnabled) state.copy(isChorusEnabled = false) else null
                "flanger" -> if (state.isFlangerEnabled) state.copy(isFlangerEnabled = false) else null
                "phaser" -> if (state.isPhaserEnabled) state.copy(isPhaserEnabled = false) else null
                "ping_pong" -> if (state.isPingPongDelayEnabled) state.copy(isPingPongDelayEnabled = false) else null
                "reverse_echo" -> if (state.isReverseEchoEnabled) state.copy(isReverseEchoEnabled = false) else null
                "reverb" -> if (state.isReverbEnabled) state.copy(isReverbEnabled = false) else null
                "shimmer_reverb" -> if (state.isShimmerReverbEnabled) state.copy(isShimmerReverbEnabled = false) else null
                "stadium" -> if (state.isStadiumEnabled) state.copy(isStadiumEnabled = false) else null
                "rotary_speaker" -> if (state.isRotarySpeakerEnabled) state.copy(isRotarySpeakerEnabled = false) else null
                "asmr_vocal" -> if (state.isAsmrVocalEnabled) state.copy(isAsmrVocalEnabled = false) else null
                "mono" -> if (state.isMonoEnabled) state.copy(isMonoEnabled = false) else null
                "rain" -> if (state.isRainEnabled) state.copy(isRainEnabled = false) else null
                "underwater" -> if (state.isUnderwaterEnabled) state.copy(isUnderwaterEnabled = false) else null
                "empty_mall" -> if (state.isEmptyMallEnabled) state.copy(isEmptyMallEnabled = false) else null
                "party_next_door" -> if (state.isPartyNextDoorEnabled) state.copy(isPartyNextDoorEnabled = false) else null
                "night_drive" -> if (state.isNightDriveEnabled) state.copy(isNightDriveEnabled = false) else null
                "muffled" -> if (state.isMuffledEnabled) state.copy(isMuffledEnabled = false) else null
                "cassette_walkman" -> if (state.isWalkmanEnabled) state.copy(isWalkmanEnabled = false) else null
                "vinyl_lofi" -> if (state.isVinylLoFiEnabled) state.copy(isVinylLoFiEnabled = false) else null
                "gramophone" -> if (state.isGramophoneEnabled) state.copy(isGramophoneEnabled = false) else null
                "vintage_mp3" -> if (state.isVintageMp3Enabled) state.copy(isVintageMp3Enabled = false) else null
                "chiptune" -> if (state.isChiptuneEnabled) state.copy(isChiptuneEnabled = false) else null
                "megaphone" -> if (state.isMegaphoneEnabled) state.copy(isMegaphoneEnabled = false) else null
                "robot_vocoder" -> if (state.isRobotVocoderEnabled) state.copy(isRobotVocoderEnabled = false) else null
                "trance_gate" -> if (state.isTranceGateEnabled) state.copy(isTranceGateEnabled = false) else null
                else -> null
            }
            if (updated != null) {
                state = updated
                changed = true
            }
        }
        if (changed) {
            effectsState = state
            applyEffectsAndSave()
            ConnectManager.publish(connectSnapshot())
        }
    }

    fun disableEffect(fxId: String) {
        disableEffects(listOf(fxId))
    }

    fun togglePinAudioFx(fxId: String): Boolean {
        val current = pinnedAudioFx.toMutableList()
        val isPinned = current.contains(fxId)
        if (isPinned) {
            current.remove(fxId)
            disableEffect(fxId)
        } else {
            current.add(fxId)
        }
        pinnedAudioFx = current
        playerPrefs.setPinnedAudioFx(current)
        return true
    }

    fun updatePinnedAudioFx(fxIds: List<String>) {
        val removed = pinnedAudioFx.filterNot { fxIds.contains(it) }
        disableEffects(removed)
        pinnedAudioFx = fxIds
        playerPrefs.setPinnedAudioFx(fxIds)
    }

    fun resetPinnedAudioFx() {
        val defaults = PlayerPreferences.DEFAULT_PINNED_AUDIO_FX
        val removed = pinnedAudioFx.filterNot { defaults.contains(it) }
        disableEffects(removed)
        pinnedAudioFx = defaults
        playerPrefs.setPinnedAudioFx(defaults)
    }

    fun isAudioFxPinned(fxId: String): Boolean = pinnedAudioFx.contains(fxId)

    private fun applyEffectsAndSave() {
        MusicManager.applyEffects(effectsState); viewModelScope.launch(Dispatchers.IO) {
            playerPrefs.saveEffects(
                effectsState
            )
        }
    }

    private fun applyEqualizerAndSave() {
        effectsState = effectsState.copy(isEqualizerEnabled = equalizerState.isEnabled)
        MusicManager.applyEqualizer(equalizerState)
        viewModelScope.launch(Dispatchers.IO) {
            playerPrefs.saveEqualizerState(equalizerState)
        }
    }

    fun toggleEqualizer() {
        equalizerState = equalizerState.copy(isEnabled = !equalizerState.isEnabled)
        applyEqualizerAndSave()
    }

    fun setEqualizerBand(bandIndex: Int, gainDb: Float) {
        val currentGains = equalizerState.bandGainsDb.toMutableList()
        if (bandIndex in currentGains.indices) {
            currentGains[bandIndex] = gainDb.coerceIn(-12f, 12f)
            equalizerState = equalizerState.copy(
                bandGainsDb = currentGains,
                selectedPreset = "Custom",
                autoProfileId = null,
                autoProfileName = null,
                autoProfileSource = null,
                autoProfileDeviceKey = null,
                autoProfileDeviceName = null,
            )
            applyEqualizerAndSave()
        }
    }

    fun setEqualizerPreamp(preampDb: Float) {
        equalizerState = equalizerState.copy(preampDb = preampDb.coerceIn(-12f, 12f))
        applyEqualizerAndSave()
    }

    fun applyEqualizerPreset(preset: EqualizerPreset) {
        equalizerState = equalizerState.copy(
            bandGainsDb = preset.bandGainsDb,
            preampDb = preset.preampDb,
            selectedPreset = preset.name,
            autoProfileId = null,
            autoProfileName = null,
            autoProfileSource = null,
            autoProfileDeviceKey = null,
            autoProfileDeviceName = null,
        )
        applyEqualizerAndSave()
    }

    fun resetEqualizer() {
        applyEqualizerPreset(EqualizerPresets.Flat)
    }

    private var lastAutoEqDeviceKey: String? = null

    /** Applies a profile downloaded from AutoEq and remembers it for the connected headphone. */
    fun applyAutoEqProfile(
        data: com.alananasss.kittytune.data.audio.AutoEqProfileData,
        device: com.alananasss.kittytune.data.audio.ConnectedHeadphone?,
    ) {
        equalizerState = equalizerState.copy(
            isEnabled = true,
            bandGainsDb = data.gainsDb,
            preampDb = data.preampDb,
            selectedPreset = data.profile.name,
            autoProfileId = data.profile.id,
            autoProfileName = data.profile.name,
            autoProfileSource = data.profile.attribution,
            autoProfileDeviceKey = device?.key,
            autoProfileDeviceName = device?.name,
        )
        applyEqualizerAndSave()
        if (device != null) {
            playerPrefs.saveEqProfileForDevice(
                com.alananasss.kittytune.data.audio.SavedEqProfile(
                    profileId = data.profile.id,
                    profileName = data.profile.name,
                    source = data.profile.attribution,
                    deviceKey = device.key,
                    deviceName = device.name,
                    gainsDb = data.gainsDb,
                    preampDb = data.preampDb,
                )
            )
        }
    }

    fun removeAutoEqProfile(deviceKey: String? = null) {
        val key = deviceKey ?: equalizerState.autoProfileDeviceKey
        if (!key.isNullOrBlank()) playerPrefs.clearSavedEqProfileForDevice(key)
        applyEqualizerPreset(EqualizerPresets.Flat)
    }

    fun setAutoEqAutoApply(enabled: Boolean) {
        playerPrefs.setAutoEqAutoApply(enabled)
        if (enabled) {
            lastAutoEqDeviceKey = null
            maybeRestoreAutoEq(
                com.alananasss.kittytune.data.audio.HeadphoneDetector.device.value
            )
        }
    }

    private fun maybeRestoreAutoEq(device: com.alananasss.kittytune.data.audio.ConnectedHeadphone?) {
        if (device == null) {
            lastAutoEqDeviceKey = null
            return
        }
        if (device.key == lastAutoEqDeviceKey) return
        lastAutoEqDeviceKey = device.key
        if (!playerPrefs.getAutoEqAutoApply()) return
        val saved = playerPrefs.getSavedEqProfileForDevice(device.key) ?: return
        if (equalizerState.isEnabled &&
            equalizerState.autoProfileId == saved.profileId &&
            equalizerState.autoProfileDeviceKey == device.key
        ) return
        equalizerState = equalizerState.copy(
            isEnabled = true,
            bandGainsDb = saved.gainsDb,
            preampDb = saved.preampDb,
            selectedPreset = saved.profileName,
            autoProfileId = saved.profileId,
            autoProfileName = saved.profileName,
            autoProfileSource = saved.source,
            autoProfileDeviceKey = device.key,
            autoProfileDeviceName = device.name,
        )
        applyEqualizerAndSave()
        emitUiEvent(getString(R.string.equalizer_auto_restored, saved.profileName))
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

    fun showTrackOptions(track: Track, playlistContextId: Long? = null, fromPlayer: Boolean = false, queueIndex: Int? = null) {
        trackForMenu = track
        menuContextPlaylistId = playlistContextId
        menuContextQueueIndex = queueIndex
        isMenuContextFromPlayer = fromPlayer
        loadSocialProof(track)
        showMenuSheet = true
    }

    fun prepareBulkAdd(tracks: List<Track>) {
        tracksToAddInBulk = tracks
        trackForMenu = null
        targetPlaylistForBulkAdd = null
        showAddToPlaylistSheet = true
    }

    fun selectTargetPlaylistForBulk(playlist: LocalPlaylist) {
        targetPlaylistForBulkAdd = playlist
    }

    fun clearTargetPlaylistForBulk() {
        targetPlaylistForBulkAdd = null
    }

    fun createTargetPlaylistForBulk(name: String, onCreated: (LocalPlaylist) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = DownloadManager.createUserPlaylist(name)
            val newLocal = LocalPlaylist(
                id = id,
                title = name,
                artist = "",
                artworkUrl = "",
                trackCount = 0,
                isUserCreated = true
            )
            withContext(Dispatchers.Main) {
                userPlaylists.add(0, newLocal)
                targetPlaylistForBulkAdd = newLocal
                onCreated(newLocal)
            }
        }
    }

    /**
     * Bookmarks the playlist that was just used, so the notification's "Add to last playlist"
     * action has a destination.
     *
     * The id and the name are stored together: storing only the id left the action with a
     * playlist it could not name, and it gave up instead of adding the track.
     */
    private fun rememberLastUsedPlaylist(playlistId: Long, playlistTitle: String?) {
        val title = playlistTitle?.takeIf { it.isNotBlank() }
            ?: userPlaylists.firstOrNull { it.id == playlistId }?.title
        playerPrefs.setLastUsedPlaylist(playlistId, title)
    }

    fun addToPlaylist(playlistId: Long, track: Track, playlistTitle: String? = null) {
        rememberLastUsedPlaylist(playlistId, playlistTitle)
        DownloadManager.addTrackToPlaylist(playlistId, track)
        showAddToPlaylistSheet = false
        targetPlaylistForBulkAdd = null
        emitUiEvent(getString(R.string.success_generic))
    }

    fun addTracksToPlaylist(playlistId: Long, tracks: List<Track>, playlistTitle: String? = null) {
        rememberLastUsedPlaylist(playlistId, playlistTitle)
        DownloadManager.addTracksToPlaylistBulk(playlistId, tracks)
        viewModelScope.launch {
            showAddToPlaylistSheet = false
            targetPlaylistForBulkAdd = null
            tracksToAddInBulk = null
            val msg = if (!playlistTitle.isNullOrBlank()) {
                getString(R.string.tracks_added_to_playlist_success, tracks.size, playlistTitle)
            } else {
                getString(R.string.success_generic)
            }
            emitUiEvent(msg)
            AchievementManager.increment("playlist_creator")
            AchievementManager.increment("playlist_god")
        }
    }

    fun createAndAddToPlaylist(name: String, track: Track) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = DownloadManager.createUserPlaylist(name)
            DownloadManager.addTrackToPlaylist(id, track)
            rememberLastUsedPlaylist(id, name)
            withContext(Dispatchers.Main) {
                showAddToPlaylistSheet = false
                targetPlaylistForBulkAdd = null
                emitUiEvent(getString(R.string.success_generic))
                AchievementManager.increment("playlist_creator")
            }
        }
    }

    fun createAndAddTracksToPlaylist(name: String, tracks: List<Track>) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = DownloadManager.createUserPlaylist(name)
            DownloadManager.addTracksToPlaylistBulk(id, tracks)
            rememberLastUsedPlaylist(id, name)
            withContext(Dispatchers.Main) {
                showAddToPlaylistSheet = false
                targetPlaylistForBulkAdd = null
                tracksToAddInBulk = null
                emitUiEvent(getString(R.string.tracks_added_to_playlist_success, tracks.size, name))
                AchievementManager.increment("playlist_creator")
                AchievementManager.increment("playlist_god")
            }
        }
    }

    fun removeFromContextPlaylist(playlistId: Long, track: Track) {
        if (playlistId == -2L) {
            DownloadManager.deleteTrack(track.id)
        } else if (playlistId == -1L) {
            LikeRepository.removeLike(track.id)
        } else {
            val syncToCloud =
                playlistId > 0 && currentContext?.navigationId?.startsWith("downloaded_section:") != true && currentContext?.navigationId != "downloads"
            DownloadManager.removeTrackFromPlaylist(playlistId, track.id, syncToCloud = syncToCloud)
        }
        showMenuSheet = false
        emitUiEvent(getString(R.string.success_generic))
    }

    fun addToQueue(tracks: List<Track>) {
        if (!applyingConnectCommand && ConnectManager.selectedDevice.value != null && tracks.isNotEmpty()) {
            ConnectManager.command(ConnectManager.selectedDevice.value!!, "append", state = PlaybackSnapshot(
                SyncLog.deviceId, System.currentTimeMillis(), tracks.take(500), 0, 0, false, false, "NONE"))
            return
        }

        val cleanTracks = BlockManager.filterBlocked(tracks)
        if (cleanTracks.isEmpty()) return

        val uniqueTracks = cleanTracks.map { it.copy() }

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
        if (DownloadManager.isTrackDownloading(track.id)) return
        com.alananasss.kittytune.audio.haptics.HapticUtil.performConfirmHaptic(context)
        DownloadManager.downloadTrack(track); AchievementManager.increment(
            "download_1000"
        )
    }

    private fun emitUiEvent(msg: String) {
        viewModelScope.launch { _uiEvent.emit(msg) }
    }


    /** Captured on the UI thread; a live controller never overwrites the local player. */
    private fun connectVolume(): Float {
        val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
        return audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() /
            audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    private fun connectSnapshot(): PlaybackSnapshot? {
        if (queueState.isEmpty() || currentQueueIndex !in queueState.indices) return null
        val start = if (queueState.size > 500) (currentQueueIndex - 250).coerceIn(0, queueState.size - 500) else 0
        return PlaybackSnapshot(
            SyncLog.deviceId, System.currentTimeMillis(), queueState.drop(start).take(500),
            currentQueueIndex - start, currentPosition.coerceAtLeast(0L), isPlaying, shuffleEnabled, repeatMode.name,
            volume = connectVolume(), isLiked = isLiked, isYourMixActive = isYourMixActive,
            speed = effectsState.speed,
            sleepTimerRemainingMs = sleepTimerRemainingMs.takeIf { isSleepTimerActive && !sleepTimerEndOfTrack },
            sleepTimerEndOfTrack = sleepTimerEndOfTrack.takeIf { isSleepTimerActive }
        )
    }

    private suspend fun handleConnectCommand(command: ConnectMessage) {
        require(SyncPlayback.enabled) { "Playback sync is disabled" }
        applyingConnectCommand = true
        ConnectManager.activateLocalRenderer()
        try {
        SyncPlayback.claimLocal()
        when (command.action) {
            "play" -> { if (!isPlaying) togglePlayPause() }
            "pause" -> pause()
            "next" -> playNext(manual = true)
            "previous" -> smartPrevious()
            "volume" -> {
                val audio = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                val max = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                audio.setStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                    (max * command.value.coerceIn(0, 1000) / 1000f).roundToInt(), 0)
            }
            "seek" -> seekTo(command.value.coerceAtLeast(0L))
            "shuffle" -> if (shuffleEnabled != (command.value == 1L)) toggleShuffle()
            "repeat" -> {
                val target = when (command.value) { 0L -> RepeatMode.NONE; 1L -> RepeatMode.ALL; 2L -> RepeatMode.ONE; else -> error("Invalid repeat mode") }
                repeatMode = target
                applyRepeatMode()
            }
            "like" -> {
                val shouldLike = command.value == 1L
                val t = currentTrack ?: uiCurrentTrack
                if (t != null && isLiked != shouldLike) {
                    isLiked = shouldLike
                    if (shouldLike) {
                        LikeRepository.addLike(t)
                        com.alananasss.kittytune.data.wave.WaveFeedback.onLiked(t)
                        AchievementManager.increment("liker_50")
                        AchievementManager.increment("liker_1000")
                        AchievementManager.increment("liker_5000")
                    } else {
                        LikeRepository.removeLike(t.id)
                    }
                    ConnectManager.publish(connectSnapshot())
                }
            }
            "dislike" -> {
                dislikeCurrentTrackInMix()
                ConnectManager.publish(connectSnapshot())
            }
            "speed" -> {
                val s = (command.value / 100f).coerceIn(0.25f, 3.0f)
                setCustomSpeed(s)
                ConnectManager.publish(connectSnapshot())
            }
            "sleep_timer" -> {
                when (command.value) {
                    0L -> cancelSleepTimer()
                    -1L -> startSleepTimerEndOfTrack()
                    else -> if (command.value > 0L) startSleepTimer(command.value)
                }
                ConnectManager.publish(connectSnapshot())
            }
            "queue", "remove" -> {
                val state = connectSnapshot() ?: error("Queue is empty")
                require(command.value in state.queue.indices.map { it.toLong() }) { "Invalid queue item" }
                val start = if (queueState.size > 500) (currentQueueIndex - 250).coerceIn(0, queueState.size - 500) else 0
                val index = command.value.toInt() + start
                if (command.action == "queue") skipToQueueItem(index) else {
                    require(index != currentQueueIndex) { "Cannot remove the playing track" }
                    removeTrackFromQueue(index)
                }
            }
            "move" -> {
                val state = connectSnapshot() ?: error("Queue is empty")
                require(command.value in 0L until state.queue.size.toLong() && command.value2 in 0L until state.queue.size.toLong())
                val start = if (queueState.size > 500) (currentQueueIndex - 250).coerceIn(0, queueState.size - 500) else 0
                moveQueueItem(command.value.toInt() + start, command.value2.toInt() + start)
            }
            "append" -> {
                val tracks = command.state?.queue ?: error("No tracks")
                require(tracks.size in 1..500 && tracks.none { it.source == "local" })
                addToQueue(tracks)
            }
            "transfer" -> {
                ConnectManager.selectDevice(null)
                val remote = requireNotNull(command.state) { "No playback state" }
                require(remote.queue.size in 1..500 && remote.currentIndex in remote.queue.indices) { "Invalid queue" }
                require(remote.positionMs >= 0 && remote.repeatMode in listOf("NONE", "ALL", "ONE")) { "Invalid playback state" }
                val track = remote.queue[remote.currentIndex]
                require(track.source != "local") { "Local files are unavailable on another device" }
                val portable = remote.queue.filter { it.source != "local" }
                val index = portable.indexOfFirst { it.id == track.id }
                require(index >= 0)
                shuffleEnabled = false
                pendingSeekPosition = remote.positionMs
                playPlaylist(portable, index, maintainPlayerState = true, autoPlay = remote.isPlaying)
                currentPosition = remote.positionMs
                shuffleEnabled = remote.shuffleEnabled
                repeatMode = RepeatMode.valueOf(remote.repeatMode)
                applyRepeatMode()
                kotlinx.coroutines.withTimeout(20_000) {
                    while (player.playbackState != Player.STATE_READY ||
                        player.currentMediaItem?.mediaId?.removePrefix("yt_") != track.id.toString()) {
                        player.playerError?.let { throw IllegalStateException("Unable to prepare this track", it) }
                        kotlinx.coroutines.delay(100)
                    }
                }
                player.seekTo(remote.positionMs)
                currentPosition = remote.positionMs
            }
            else -> error("Unknown player command")
        }
        saveStateAsync(saveQueue = true)
        } finally { applyingConnectCommand = false }
    }

    private fun saveStateAsync(saveQueue: Boolean = false, savePositionOnly: Boolean = false) {
        if (isRestoringSession) return
        ConnectManager.publish(connectSnapshot())
        if (syncPlaybackReady) {
            val q = queueState.toList()
            val index = q.indexOfFirst { it.id == currentTrack?.id }
            if (index >= 0) {
                val position = currentPosition
                val playing = isPlaying
                val shuffle = shuffleEnabled
                val repeat = repeatMode.name
                val liked = isLiked
                val mix = isYourMixActive
                val spd = effectsState.speed
                val timerMs = sleepTimerRemainingMs.takeIf { isSleepTimerActive && !sleepTimerEndOfTrack }
                val timerEnd = sleepTimerEndOfTrack.takeIf { isSleepTimerActive }
                viewModelScope.launch(Dispatchers.IO) {
                    SyncPlayback.publish(q, index, position, playing, shuffle, repeat, liked, mix, spd, timerMs, timerEnd)
                }
            }
        }
        val t = currentTrack
        val p = currentPosition
        val c = currentContext
        val s = shuffleEnabled
        val r = repeatMode
        if (savePositionOnly) {
            viewModelScope.launch(Dispatchers.IO) { playerPrefs.savePosition(p) }
            return
        }
        // All non-position saves are coalesced through the same 500ms debounced job:
        // previously the saveQueue=false path (13 call sites: play/pause, track change, queue
        // edits...) immediately re-serialized the ENTIRE queue to JSON and rewrote the prefs
        // file on every single event. The debounced job reads fresh state when it fires, so
        // exactly the same data lands on disk, minus the redundant writes.
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
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            val tokenManager = TokenManager(context)
            val isGuest = tokenManager.isGuestMode()
            var lastSaveTime = System.currentTimeMillis()
            var lastAutomixCheckTime = 0L
            // Playtime is counted in seconds, so it has to follow the clock rather than the
            // loop: the tick rate varies from 40 ms to 500 ms, and adding 1 per tick counted
            // 25 seconds per second in DJ mode and 2 outside it.
            var lastPlayTimeCreditAt = System.currentTimeMillis()
            // Preferences change when the user opens settings, not 25 times a second.
            var prefsReadAt = 0L
            var crossfadeEnabledCached = false
            var automixEnabledCached = false
            var crossfadeMsCached = 0L
            var gaplessCached = false
            var endOfTrackStallTicks = 0
            while (isActive && isPlaying) {
                try {
                    if (!isScrubbing && !isLoading) {
                        currentPosition = MusicManager.player.currentPosition.coerceAtLeast(0L)
                        // Feeds the haptic latency measurement. The player's position already
                        // accounts for the sink's buffering, which is what makes it comparable
                        // to how much audio the haptic processor has written.
                        com.alananasss.kittytune.audio.haptics.PlayerHapticManager
                            .getInstance(context).onAudiblePosition(currentPosition)
                        // Media milliseconds actually travelled, not seconds on the clock.
                        ensureListenSession()
                        listenSession?.onPosition(currentPosition)
                        SoundCloudTelemetryTracker.onProgressUpdate(currentPosition)

                        val now = System.currentTimeMillis()
                        if (now - lastSaveTime > 5000L) {
                            lastSaveTime = now
                            saveStateAsync(savePositionOnly = true)
                        }

                        if (now - prefsReadAt > 1000L) {
                            prefsReadAt = now
                            crossfadeEnabledCached = playerPrefs.getCrossfadeEnabled()
                            automixEnabledCached = playerPrefs.getAutomixEnabled()
                            crossfadeMsCached = playerPrefs.getCrossfadeDuration() * 1000L
                            gaplessCached = playerPrefs.getCrossfadeGapless()
                        }
                        val crossfadeEnabled = crossfadeEnabledCached
                        val automixEnabled = automixEnabledCached
                        val crossfadeMs = crossfadeMsCached
                        val exoDur = if (MusicManager.player.duration > 0) MusicManager.player.duration else 0L
                        val trackDur = currentTrack?.actualDurationMs ?: 0L
                        // Prefer ExoPlayer's reported duration when available; it is the ground
                        // truth for the stream that is actually playing. Fall back to the Track's
                        // duration only when ExoPlayer hasn't determined one yet. This prevents
                        // crossfade/automix from misfiring when the Track metadata carries a stale
                        // or incorrect value (e.g. 5 hours) that does not match the stream
                        // (issue #33).
                        val dur = if (exoDur > 0L) exoDur else trackDur
                        djFlowController.onPositionUpdated(currentPosition, dur)

                        val currTrack = currentTrack
                        val nextIdx = if (repeatMode == RepeatMode.ONE) currentQueueIndex else currentQueueIndex + 1
                        val nextTrack = if (nextIdx in _queue.indices) _queue[nextIdx] else if (repeatMode == RepeatMode.ALL && _queue.isNotEmpty()) _queue[0] else null

                        val isGaplessAlbum = gaplessCached && currTrack != null && nextTrack != null && run {
                            val currAlbum = currTrack.publisherMetadata?.albumTitle?.takeIf { it.isNotBlank() }
                                ?: currTrack.publisherMetadata?.releaseTitle?.takeIf { it.isNotBlank() }
                                ?: currTrack.publisherMetadata?.albumId?.takeIf { it.isNotBlank() }
                            val nextAlbum = nextTrack.publisherMetadata?.albumTitle?.takeIf { it.isNotBlank() }
                                ?: nextTrack.publisherMetadata?.releaseTitle?.takeIf { it.isNotBlank() }
                                ?: nextTrack.publisherMetadata?.albumId?.takeIf { it.isNotBlank() }
                            currAlbum != null && currAlbum == nextAlbum
                        }

                        val isDjFlowActive = djFlowController.flowState.value.isActive
                        if ((automixEnabled || isDjFlowActive) && currTrack != null) {
                            if (now - lastAutomixCheckTime > 2000L) {
                                lastAutomixCheckTime = now
                                com.alananasss.kittytune.audio.automix.AutomixManager.maybeAnalyzeBeat(currTrack, com.alananasss.kittytune.audio.automix.BeatAnalysisPriority.IMMEDIATE)
                                if (nextTrack != null) {
                                    if (nextTrack.id != currTrack.id) {
                                        com.alananasss.kittytune.audio.automix.AutomixManager.maybeAnalyzeBeat(nextTrack, com.alananasss.kittytune.audio.automix.BeatAnalysisPriority.IMMEDIATE)
                                    }
                                    // LOOKAHEAD decoding spins up full headless ExoPlayers
                                    // (+ DRM sessions). Analyzing queue items +2/+3 the moment a
                                    // 5-minute track starts used to burn CPU/RAM for minutes
                                    // while nothing needs it yet; deferring until the current
                                    // track is under ~2.5 minutes from its end keeps every
                                    // transition seamlessly mixed (analysis takes ~10-20s) while
                                    // not competing with playback for most of the song.
                                    val remainingMs = if (dur > 0L) (dur - currentPosition).coerceAtLeast(0L) else 0L
                                    if (remainingMs < 150_000L) {
                                        val lookaheadTrack1 = _queue.getOrNull(currentQueueIndex + 2)
                                        if (lookaheadTrack1 != null) {
                                            com.alananasss.kittytune.audio.automix.AutomixManager.maybeAnalyzeBeat(lookaheadTrack1, com.alananasss.kittytune.audio.automix.BeatAnalysisPriority.LOOKAHEAD)
                                        }
                                        val lookaheadTrack2 = _queue.getOrNull(currentQueueIndex + 3)
                                        if (lookaheadTrack2 != null) {
                                            com.alananasss.kittytune.audio.automix.AutomixManager.maybeAnalyzeBeat(lookaheadTrack2, com.alananasss.kittytune.audio.automix.BeatAnalysisPriority.LOOKAHEAD)
                                        }
                                    }
                                    if (isGaplessAlbum) {
                                        com.alananasss.kittytune.audio.automix.AutomixManager.clearPlan()
                                    } else if (dur > 0L) {
                                        com.alananasss.kittytune.audio.automix.AutomixManager.computeAutomixPlan(currTrack, nextTrack, currentPosition, dur, playerPrefs)
                                    }
                                }
                            }

                            val plan = if (isGaplessAlbum) null else com.alananasss.kittytune.audio.automix.AutomixManager.currentAutomixPlan
                            if (plan != null && dur > 0L) {
                                val triggerTime = holdMixForLyrics(plan.triggerTimeMs, dur, plan.overlapMs)
                                val remainingToTrigger = triggerTime - currentPosition
                                val outBpm = com.alananasss.kittytune.audio.automix.AutomixManager.automixDebugInfo.value?.outBpm ?: 0f
                                if (outBpm > 0f) {
                                    val periodMs = 60000f / outBpm
                                    val beatsLeft = kotlin.math.ceil(remainingToTrigger / periodMs).toInt()
                                    if (!MusicManager.isCrossfadingOut && beatsLeft in 1..16) {
                                        com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(beatsLeft)
                                    } else {
                                        com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(null)
                                    }
                                }

                                if (nextTrack != null && remainingToTrigger in 1000L..12000L && !MusicManager.isCrossfadingOut && !MusicManager.isPrebuffered(nextTrack.id)) {
                                    triggerPrebuffer(nextTrack, plan)
                                }

                                val settledAfterSeek = System.currentTimeMillis() - lastSeekTimestamp > MIX_AFTER_SEEK_MS
                                if (currentPosition >= triggerTime && settledAfterSeek && !MusicManager.isCrossfadingOut) {
                                    MusicManager.beginCrossfadeRequest()
                                    com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(null)
                                    playNext(manual = false, isCrossfade = true)
                                }
                            } else if (!isGaplessAlbum && crossfadeEnabled && dur > 0L) {
                                val remainingToCrossfade = (dur - crossfadeMs) - currentPosition
                                if (nextTrack != null && remainingToCrossfade in 1000L..12000L && !MusicManager.isCrossfadingOut && !MusicManager.isPrebuffered(nextTrack.id)) {
                                    triggerPrebuffer(nextTrack, null)
                                }
                                if (currentPosition >= (dur - crossfadeMs) && !MusicManager.isCrossfadingOut) {
                                    com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(null)
                                    MusicManager.beginCrossfadeRequest()
                                    playNext(manual = false, isCrossfade = true)
                                }
                            } else {
                                com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(null)
                            }
                        } else if (!isGaplessAlbum && crossfadeEnabled && dur > 0L) {
                            val remainingToCrossfade = (dur - crossfadeMs) - currentPosition
                            if (nextTrack != null && remainingToCrossfade in 1000L..12000L && !MusicManager.isCrossfadingOut && !MusicManager.isPrebuffered(nextTrack.id)) {
                                triggerPrebuffer(nextTrack, null)
                            }
                            if (currentPosition >= (dur - crossfadeMs) && !MusicManager.isCrossfadingOut) {
                                com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(null)
                                MusicManager.beginCrossfadeRequest()
                                playNext(manual = false, isCrossfade = true)
                            }
                        } else {
                            com.alananasss.kittytune.audio.automix.AutomixManager.setMixBeatsLeft(null)
                        }

                        // AudioSink EOS deadlock & stall fallback:
                        // If track has reached 100% (final 100ms or past dur) and playback stalled without ExoPlayer firing STATE_ENDED:
                        if (dur > 2000L && currentPosition >= (dur - 100L)) {
                            if (!MusicManager.isTransitionRunning() && trackInitJob?.isActive != true && playJob?.isActive != true) {
                                endOfTrackStallTicks++
                                val threshold = if (djFlowController.flowState.value.isActive && (isDjBeatUiVisible || showDjDebugSheet)) 30 else 5
                                if (endOfTrackStallTicks >= threshold) {
                                    Log.w("PlayerViewModel", "Detected end-of-track EOS stall ($currentPosition / $dur ms). Forcing queue advance.")
                                    endOfTrackStallTicks = 0
                                    MusicManager.cancelCrossfade()
                                    playNext(manual = false, isCrossfade = false)
                                }
                            } else {
                                endOfTrackStallTicks = 0
                            }
                        } else {
                            endOfTrackStallTicks = 0
                        }
                    }
                    // Credit whole seconds of wall time, independent of the tick rate. Each
                    // call writes nine achievement counters to SharedPreferences, so doing it
                    // per tick was also 225 writes a second in DJ mode.
                    val sinceCredit = System.currentTimeMillis() - lastPlayTimeCreditAt
                    if (sinceCredit >= 1000L) {
                        val seconds = (sinceCredit / 1000L).toInt()
                        lastPlayTimeCreditAt += seconds * 1000L
                        AchievementManager.addPlayTime(seconds, isGuest, effectsState.speed)
                        if (effectsState.isBassBoostEnabled || effectsState.isEarrapeEnabled) {
                            AchievementManager.increment("bass_addict", seconds)
                        }
                    }

                } catch (_: Exception) {
                }
                val durForDelay = if (MusicManager.player.duration > 0) MusicManager.player.duration else duration
                val isDjActive = djFlowController.flowState.value.isActive
                val sleepTime = if (isDjActive && (isDjBeatUiVisible || showDjDebugSheet)) {
                    40L // 25 fps beat ticker, only while something is on screen to animate
                } else if (isDjActive) {
                    // DJ Flow running with nothing to draw: still poll often enough that the
                    // autonomous trigger fires on time, but stop burning a core on animation
                    // frames nobody sees. The transition prebuffers seconds ahead and the decks
                    // are phase-locked from live positions afterwards, so a quarter second of
                    // trigger jitter is absorbed rather than heard.
                    250L
                } else if (durForDelay > 0 && (durForDelay - currentPosition) < 25000L) {
                    200L
                } else {
                    500L
                }
                delay(sleepTime)
            }
        }
    }

    /**
     * Moves a mix's start later while the song still has words to sing, so the mix does not fade out the last
     * verse (issue #66). The analysis finds where the loud part of the track ends, which can be a verse short of
     * the last line. Never later than the latest start that still leaves room for the overlap, and only with
     * lyrics that are timed.
     */
    private fun holdMixForLyrics(planTrigger: Long, durationMs: Long, overlapMs: Long): Long {
        if (lyricsLines.isEmpty() || lyricsMode != LyricsMode.SYNCED) return planTrigger
        val last = lyricsLines.lastOrNull { !it.isInstrumental && it.text.isNotBlank() } ?: return planTrigger
        val lastEnd = if (last.words.isNotEmpty()) last.words.maxOf { it.endTime }
        else last.startTime + minOf((last.endTime - last.startTime).coerceAtLeast(0L), 5_000L)
        // Lyrics time to audio time.
        val lastEndInAudio = lastEnd - lyricsOffsetAtLyricTime(lastEnd)
        val wanted = lastEndInAudio - LYRICS_MIX_LEAD_MS
        val latest = durationMs - overlapMs.coerceAtLeast(3_000L)
        return if (wanted > planTrigger && latest > planTrigger) minOf(wanted, latest) else planTrigger
    }

    fun updateScrubPosition(position: Long) {
        isScrubbing = true
        lastSeekTimestamp = System.currentTimeMillis()
        currentPosition = position
    }

    fun startSleepTimer(durationMs: Long) {
        if (routeRemote("sleep_timer", durationMs)) {
            showSleepTimerDialog = false
            return
        }
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
        ConnectManager.publish(connectSnapshot())

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
        if (routeRemote("sleep_timer", -1L)) {
            showSleepTimerDialog = false
            return
        }
        cancelSleepTimer()
        sleepTimerRemainingMs = 0L
        sleepTimerEndOfTrack = true
        showSleepTimerDialog = false
        showSleepTimerIslandNotification(isStarted = true, durationText = getString(R.string.sleep_timer_end_of_track))
        emitUiEvent(getString(R.string.sleep_timer_started))
        ConnectManager.publish(connectSnapshot())
    }

    fun cancelSleepTimer() {
        if (routeRemote("sleep_timer", 0L)) {
            showSleepTimerDialog = false
            return
        }
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (player.volume != preFadeVolume) {
            player.volume = preFadeVolume
        }
        sleepTimerRemainingMs = 0L
        sleepTimerEndOfTrack = false
        ConnectManager.publish(connectSnapshot())
    }

    fun formatSleepTimerRemaining(): String {
        if (remotePeer?.snapshot != null) {
            if (remotePeer?.snapshot?.sleepTimerEndOfTrack == true) return getString(R.string.sleep_timer_end_of_track)
            val ms = remotePeer?.snapshot?.sleepTimerRemainingMs ?: 0L
            return formatRemaining(ms)
        }
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
                val cleanQueue = BlockManager.filterBlocked(lastQueue)
                val lastTrack = playerPrefs.getLastTrack()
                val cleanTrack = if (lastTrack != null && !BlockManager.isBlocked(lastTrack)) lastTrack else null
                val lastPosition = playerPrefs.getLastPosition()
                val lastContext = playerPrefs.getLastContext()
                val lastShuffle = playerPrefs.getLastShuffleEnabled()
                val lastRepeat = playerPrefs.getLastRepeatMode()
                withContext(Dispatchers.Main) {
                    if (cleanQueue.isNotEmpty()) {
                        _queue.clear(); _queue.addAll(cleanQueue); _originalQueue.clear(); _originalQueue.addAll(
                            cleanQueue
                        ); updateQueueState()
                    }
                    if (cleanTrack != null) {
                        shuffleEnabled = lastShuffle; repeatMode = lastRepeat; currentContext = lastContext
                        MusicManager.updateContext(lastContext)

                        currentTrack = cleanTrack
                        MusicManager.currentTrack = cleanTrack; isLiked =
                            LikeRepository.isTrackLiked(cleanTrack.id); loadLyrics(cleanTrack)
                        currentQueueIndex = _queue.indexOfFirst { it.id == cleanTrack.id }
                        if (currentQueueIndex == -1) {
                            _queue.add(0, cleanTrack); _originalQueue.add(
                                0,
                                cleanTrack
                            ); updateQueueState(); currentQueueIndex = 0
                        }
                        val currentPlayerMediaId = MusicManager.player.currentMediaItem?.mediaId
                        if (currentPlayerMediaId == cleanTrack.id.toString()) {
                            isPlaying = MusicManager.player.isPlaying; duration =
                                MusicManager.player.duration.coerceAtLeast(
                                    cleanTrack.actualDurationMs
                                ); currentPosition = MusicManager.player.currentPosition; MusicManager.applyEffects(
                                effectsState
                            )
                        } else {
                            currentPosition = lastPosition
                            duration = cleanTrack.actualDurationMs
                            if (currentQueueIndex >= 0) {
                                playRobustly(currentQueueIndex, autoPlay = false, startPosition = lastPosition)
                            }
                        }
                    }

                    // Started unconditionally, outside the restored-track block above. Nothing else
                    // starts it until playback begins, so with an empty saved queue - a fresh
                    // install, or "Remember queue" off, which makes getLastTrack() null - there was
                    // no media session, no notification, and the lock screen and media keys stayed
                    // dead until play was pressed inside the app.
                    delay(200.milliseconds)
                    val intent = Intent(context, PlaybackService::class.java).apply {
                        action = PlaybackService.ACTION_FORCE_UPDATE
                    }
                    startServiceSafe(context, intent)
                }
            } catch (_: Exception) {
            } finally {
                isRestoringSession = false
            }
            restoreSyncedPlayback()
        }
    }

    /** An active local play always takes precedence over a remote restore. */
    private suspend fun restoreSyncedPlayback() {
        runCatching { SyncScheduler.syncAll("playback restore") }
        withContext(Dispatchers.Main) {
            val wasReady = syncPlaybackReady
            var appliedRemote = false
            val remote = SyncPlayback.current()?.takeIf {
                it.deviceId != SyncLog.deviceId && it.updatedAtMs > lastAppliedRemotePlaybackAtMs
            }
            if (!isPlaying && remote != null) {
                val track = remote.queue.getOrNull(remote.currentIndex)
                if (track != null && track.source != "local" && !BlockManager.isBlocked(track)) {
                    val portable = remote.queue.filter { it.source != "local" && !BlockManager.isBlocked(it) }
                    val index = portable.indexOfFirst { it.id == track.id }
                    if (index >= 0) {
                        val shouldPlay = false // Live playback moves only through an explicit device selection.
                        val position = if (shouldPlay) remote.projectedPosition(System.currentTimeMillis())
                            else remote.positionMs
                        shuffleEnabled = false // preserve the sender's exact queue order
                        pendingSeekPosition = position
                        playPlaylist(portable, index, autoPlay = shouldPlay)
                        currentPosition = position
                        appliedRemote = true
                        lastAppliedRemotePlaybackAtMs = remote.updatedAtMs
                        shuffleEnabled = remote.shuffleEnabled
                        repeatMode = runCatching { RepeatMode.valueOf(remote.repeatMode) }
                            .getOrDefault(RepeatMode.NONE)
                        applyRepeatMode()
                    }
                }
            }
            syncPlaybackReady = true
            if (!appliedRemote && !wasReady) saveStateAsync(saveQueue = true)
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
                val cleanQueue = BlockManager.filterBlocked(savedQueue)
                val savedContext = playerPrefs.getLastContext()

                withContext(Dispatchers.Main) {
                    if (cleanQueue.isNotEmpty()) {
                        _queue.clear()
                        _queue.addAll(cleanQueue)
                        _originalQueue.clear()
                        _originalQueue.addAll(cleanQueue)
                        updateQueueState()

                        if (currentTrack != null) {
                            if (BlockManager.isBlocked(currentTrack!!)) {
                                playNext(manual = false)
                            } else {
                                currentQueueIndex = _queue.indexOfFirst { it.id == currentTrack!!.id }.coerceAtLeast(0)
                            }
                        }
                    }
                    if (savedContext != null) {
                        currentContext = savedContext
                        MusicManager.updateContext(savedContext)
                    }
                }
            }
            // ON_RESUME already calls this method. Fetch after the local queue refresh so it
            // cannot overwrite a newer queue received from the other device.
            if (syncPlaybackReady && SyncPlayback.enabled) restoreSyncedPlayback()
        }
    }

    private suspend fun loadBitmap(url: String, size: Int = 800): Bitmap? {
        return try {
            val request = ImageRequest.Builder(context)
                .data(url)
                .size(size, size)
                .precision(Precision.INEXACT)
                .allowHardware(false)
                .build()
            (context.imageLoader.execute(request) as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
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

                val mesh = com.alananasss.kittytune.ui.theme.ArtworkPalette.meshPalette(bitmap)
                withContext(Dispatchers.Main) {
                    com.alananasss.kittytune.ui.theme.ThemeState.coverMeshColors = mesh
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

    private var prebufferJob: Job? = null

    private fun triggerPrebuffer(nextTrack: Track, plan: com.alananasss.kittytune.audio.automix.AutomixPlan?) {
        if (MusicManager.isPrebuffered(nextTrack.id)) return
        prebufferJob?.cancel()
        prebufferJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                var resolvedUrl: String? = null
                var offlineKeySetId: ByteArray? = null

                val db = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(context).downloadDao()
                var localTrack = db.getTrack(nextTrack.id)
                if (localTrack == null || localTrack.localAudioPath.isEmpty()) {
                    val fallbackTitle = nextTrack.title ?: ""
                    val fallbackArtist = nextTrack.user?.username ?: ""
                    if (fallbackTitle.isNotBlank()) {
                        localTrack = db.findDownloadedTrack(fallbackTitle, fallbackArtist)
                    }
                }
                if (localTrack != null && localTrack.localAudioPath.isNotEmpty()) {
                    if (localTrack.localAudioPath.startsWith("exo_cache://")) {
                        val parts = localTrack.localAudioPath.removePrefix("exo_cache://").split("::", limit = 3)
                        resolvedUrl = parts.getOrNull(1)
                        val tokenStr = parts.getOrNull(2)
                        if (!tokenStr.isNullOrEmpty()) {
                            offlineKeySetId = android.util.Base64.decode(tokenStr, android.util.Base64.NO_WRAP)
                            MusicManager.putDrmToken(nextTrack.id, tokenStr)
                        }
                    } else {
                        val fileExists = if (localTrack.localAudioPath.startsWith("content://")) true else File(localTrack.localAudioPath).exists()
                        if (fileExists) resolvedUrl = localTrack.localAudioPath
                    }
                }

                var resolvedMimeType: String? = null
                if (resolvedUrl == null) {
                    val resolved = StreamResolver.resolveStreamWithDrm(context, nextTrack)
                    resolvedUrl = resolved?.url
                    resolvedMimeType = resolved?.mimeType
                    if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                        MusicManager.putDrmToken(nextTrack.id, resolved.licenseAuthToken)
                    }
                }

                if (resolvedUrl != null) {
                    val bitmap = loadBitmap(nextTrack.fullResArtwork)
                    val mediaItem = buildMediaItem(nextTrack, bitmap, resolvedUrl, offlineKeySetId, resolvedMimeType)
                    withContext(Dispatchers.Main) {
                        MusicManager.prebufferTransition(mediaItem, nextTrack, plan)
                    }
                }
            } catch (e: Exception) {
                Log.w("PlayerViewModel", "triggerPrebuffer failed for track ${nextTrack.id}: ${e.message}")
            }
        }
    }

    private fun playRobustly(
        index: Int,
        autoPlay: Boolean = true,
        startPosition: Long = 0L,
        allowSkipOnFailure: Boolean = true,
        isCrossfade: Boolean = false,
        trackOverride: Track? = null
    ) {
        val trackToPlay = trackOverride ?: _queue.getOrNull(index) ?: return

        if (isCrossfade && MusicManager.isPrebuffered(trackToPlay.id)) {
            val emptyItem = MediaItem.Builder().setMediaId(trackToPlay.id.toString()).build()
            viewModelScope.launch(Dispatchers.Main) {
                if (currentTrack?.id != trackToPlay.id || !isActive) return@launch
                try {
                    val djState = djFlowController.flowState.value
                    val isDjFlowOn = djState.isActive
                    val crossfadeDurationMs = if (djCustomCrossfadeDurationMs != null) {
                        val d = djCustomCrossfadeDurationMs!!
                        djCustomCrossfadeDurationMs = null
                        d
                    } else if (isDjFlowOn && djState.plannedTransitionDurationMs > 0L) {
                        djState.plannedTransitionDurationMs
                    } else {
                        playerPrefs.getCrossfadeDuration() * 1000L
                    }
                    val tempoRatio = if (djCustomTempoRatio != null) {
                        val r = djCustomTempoRatio!!
                        djCustomTempoRatio = null
                        r
                    } else if (isDjFlowOn) {
                        djState.sync.tempoRatio
                    } else 1.0f

                    val phaseOffset = if (djCustomPhaseOffsetMs != null) {
                        val p = djCustomPhaseOffsetMs!!
                        djCustomPhaseOffsetMs = null
                        p
                    } else if (isDjFlowOn) {
                        djFlowController.livePhaseOffsetMs
                    } else 0L

                    val automixPlan = com.alananasss.kittytune.audio.automix.AutomixManager.currentAutomixPlan
                    val isDjConstantEnergy = isDjFlowOn && djState.isConstantEnergyEnabled
                    MusicManager.crossfadeToMediaItem(
                        emptyItem,
                        startPosition,
                        crossfadeDurationMs,
                        automixPlan,
                        isDjConstantEnergy = isDjConstantEnergy,
                        djFlowActive = isDjFlowOn,
                        djTempoRatio = tempoRatio,
                        djPhaseOffsetMs = phaseOffset,
                        outgoingGrid = if (isDjFlowOn) djState.sync.outgoingGrid else null,
                        incomingGrid = if (isDjFlowOn) djState.sync.incomingGrid else null
                    )
                    isLoading = false
                    isPlaying = true
                    MusicManager.isLoadingTrack = false
                    currentPosition = startPosition
                    duration = if (MusicManager.player.duration > 0) MusicManager.player.duration else trackToPlay.actualDurationMs
                    startProgressUpdate()
                    MusicManager.applyEffects(effectsState)
                    preloadNextTrack(index + 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                    MusicManager.cancelCrossfade()
                    isLoading = false
                    isPlaying = false
                    MusicManager.isLoadingTrack = false
                    if (allowSkipOnFailure && currentQueueIndex + 1 < _queue.size) {
                        playNext(manual = false, isCrossfade = false)
                    }
                }
            }
            return
        }

        if (!isCrossfade) {
            MusicManager.releasePrebuffered()
        }

        playJob?.cancel()
        playJob = viewModelScope.launch(Dispatchers.IO) {
            val bitmap = loadBitmap(trackToPlay.fullResArtwork)

            var resolvedUrl: String? = null
            var offlineKeySetId: ByteArray? = null

            try {
                val db = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(context).downloadDao()
                var localTrack = db.getTrack(trackToPlay.id)
                if (localTrack == null || localTrack.localAudioPath.isEmpty()) {
                    val fallbackTitle = trackToPlay.title ?: ""
                    val fallbackArtist = trackToPlay.user?.username ?: ""
                    if (fallbackTitle.isNotBlank()) {
                        localTrack = db.findDownloadedTrack(fallbackTitle, fallbackArtist)
                    }
                }
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

            var resolvedMimeType: String? = null
            var resolvedStream: com.alananasss.kittytune.data.ResolvedStream? = null
            val isLocal = trackToPlay.source == "local" || (resolvedUrl != null && !resolvedUrl.startsWith("http"))

            if (resolvedUrl == null) {
                val resolved = StreamResolver.resolveStreamWithDrm(context, trackToPlay)
                resolvedStream = resolved
                resolvedUrl = resolved?.url
                resolvedMimeType = resolved?.mimeType
                if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                    MusicManager.putDrmToken(trackToPlay.id, resolved.licenseAuthToken)
                    Log.d("PlayerViewModel", "DRM token pre-cached for track ${trackToPlay.id}")
                }
            }

            var retryAttempt = 0
            while (resolvedUrl == null && retryAttempt < 2 && isActive) {
                retryAttempt++
                Log.d(
                    "PlayerViewModel",
                    "Retrying stream resolution for track ${trackToPlay.id} (attempt $retryAttempt/2)..."
                )
                kotlinx.coroutines.delay(1200L * retryAttempt)
                try {
                    val retryResolved = StreamResolver.resolveStreamWithDrm(context, trackToPlay)
                    resolvedStream = retryResolved
                    resolvedUrl = retryResolved?.url
                    resolvedMimeType = retryResolved?.mimeType
                    if (retryResolved?.isDrmProtected == true && retryResolved.licenseAuthToken != null) {
                        MusicManager.putDrmToken(trackToPlay.id, retryResolved.licenseAuthToken)
                    }
                } catch (e: Exception) {
                    Log.w("PlayerViewModel", "Retry resolution attempt $retryAttempt failed: ${e.message}")
                }
            }

            val finalStreamSource = if (isLocal) {
                "local"
            } else {
                resolvedStream?.source ?: trackToPlay.source ?: "soundcloud"
            }

            if (resolvedUrl == null) {
                withContext(Dispatchers.Main) {
                    if (currentTrack?.id != trackToPlay.id || !isActive) {
                        MusicManager.cancelCrossfade()
                        return@withContext
                    }
                    isLoading = false
                    isPlaying = false
                    MusicManager.isLoadingTrack = false
                    MusicManager.cancelCrossfade()
                    try {
                        MusicManager.player.pause()
                        MusicManager.player.stop()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    // A subscription-only track is not a connection problem, and telling the user to
                    // check their internet over and over for a track that will never resolve is worse
                    // than useless. Name the real reason when the API has told us what it is.
                    val msg = if (StreamResolver.isKnownRestricted(trackToPlay)) {
                        context.getString(R.string.restricted_playback_error)
                    } else if (playerPrefs.getProxyEnabled()) {
                        context.getString(R.string.proxy_playback_error)
                    } else {
                        context.getString(R.string.network_playback_error)
                    }
                    android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()

                    if (allowSkipOnFailure && currentQueueIndex + 1 < _queue.size) {
                        Log.i("PlayerViewModel", "Skipping to next track after stream resolution failure for ${trackToPlay.id}")
                        playNext(manual = false, isCrossfade = false)
                    }
                }
                return@launch
            }

            val newMediaItem = buildMediaItem(trackToPlay, bitmap, resolvedUrl, offlineKeySetId, resolvedMimeType)

            withContext(Dispatchers.Main) {
                if (currentTrack?.id != trackToPlay.id || !isActive) {
                    Log.d("PlayerViewModel", "Discarding stale stream resolution for track ${trackToPlay.id}, current track is ${currentTrack?.id}")
                    return@withContext
                }
                currentStreamSource = finalStreamSource
                AiDetectionManager.onTrackChanged(trackToPlay.id, trackToPlay, context, resolvedUrl)
                try {
                    queueChunkingJob?.cancel()

                    if (isCrossfade) {
                        val crossfadeDurationMs = if (djCustomCrossfadeDurationMs != null) {
                            val d = djCustomCrossfadeDurationMs!!
                            djCustomCrossfadeDurationMs = null
                            d
                        } else if (djFlowController.flowState.value.let { it.isActive && it.plannedTransitionDurationMs > 0L }) {
                            djFlowController.flowState.value.plannedTransitionDurationMs
                        } else {
                            playerPrefs.getCrossfadeDuration() * 1000L
                        }
                        val tempoRatio = if (djCustomTempoRatio != null) {
                            val r = djCustomTempoRatio!!
                            djCustomTempoRatio = null
                            r
                        } else if (djFlowController.flowState.value.isActive) {
                            djFlowController.flowState.value.sync.tempoRatio
                        } else 1.0f

                        val phaseOffset = if (djCustomPhaseOffsetMs != null) {
                            val p = djCustomPhaseOffsetMs!!
                            djCustomPhaseOffsetMs = null
                            p
                        } else if (djFlowController.flowState.value.isActive) {
                            djFlowController.livePhaseOffsetMs
                        } else 0L

                        val automixPlan = com.alananasss.kittytune.audio.automix.AutomixManager.currentAutomixPlan
                        val djState = djFlowController.flowState.value
                        val isDjFlowOn = djState.isActive
                        MusicManager.crossfadeToMediaItem(
                            newMediaItem,
                            startPosition,
                            crossfadeDurationMs,
                            automixPlan,
                            isDjConstantEnergy = isDjFlowOn && djState.isConstantEnergyEnabled,
                            djFlowActive = isDjFlowOn,
                            djTempoRatio = tempoRatio,
                            djPhaseOffsetMs = phaseOffset,
                            outgoingGrid = if (isDjFlowOn) djState.sync.outgoingGrid else null,
                            incomingGrid = if (isDjFlowOn) djState.sync.incomingGrid else null
                        )
                        isLoading = false
                        isPlaying = true
                        MusicManager.isLoadingTrack = false
                        currentPosition = startPosition
                        duration = if (MusicManager.player.duration > 0) MusicManager.player.duration else trackToPlay.actualDurationMs
                        startProgressUpdate()
                    } else {
                        val effectiveStart = MusicManager.pendingSeekPositionMs ?: startPosition
                        MusicManager.pendingSeekPositionMs = null
                        MusicManager.player.setMediaItem(newMediaItem, effectiveStart)
                        MusicManager.player.prepare()
                        if (autoPlay && MusicManager.playWhenReadyRequested) {
                            val intent = Intent(context, PlaybackService::class.java)
                            startServiceSafe(context, intent)
                            MusicManager.player.play()
                        }
                        MusicManager.isLoadingTrack = false
                    }

                    MusicManager.applyEffects(effectsState)
                    preloadNextTrack(index + 1)
                } catch (e: Exception) {
                    e.printStackTrace()
                    MusicManager.cancelCrossfade()
                    isLoading = false
                    isPlaying = false
                    MusicManager.isLoadingTrack = false
                    if (allowSkipOnFailure && currentQueueIndex + 1 < _queue.size) {
                        Log.i("PlayerViewModel", "Skipping to next track after error for ${trackToPlay.id}")
                        playNext(manual = false, isCrossfade = false)
                    }
                }
            }
        }
    }

    private fun preloadNextTrack(nextIndex: Int) {
        if (repeatMode == RepeatMode.ONE || playerPrefs.getCrossfadeEnabled()) {
            return
        }

        val targetIndex = if (nextIndex >= _queue.size) {
            if (repeatMode == RepeatMode.ALL && _queue.isNotEmpty()) 0 else return
        } else nextIndex

        val nextTrack = _queue[targetIndex]
        if (BlockManager.isBlocked(nextTrack)) return

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

                var resolvedMimeType: String? = null
                if (resolvedUrl == null) {
                    val resolved = StreamResolver.resolveStreamWithDrm(context, nextTrack)
                    resolvedUrl = resolved?.url
                    resolvedMimeType = resolved?.mimeType
                    if (resolved?.isDrmProtected == true && resolved.licenseAuthToken != null) {
                        MusicManager.putDrmToken(nextTrack.id, resolved.licenseAuthToken)
                    }
                }

                if (resolvedUrl != null) {
                    val nextMediaItem = buildMediaItem(nextTrack, null, resolvedUrl, offlineKeySetId, resolvedMimeType)
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
        offlineKeySetId: ByteArray? = null,
        mimeTypeOverride: String? = null,
    ): MediaItem {
        val uri = when {
            urlOverride == null -> "soundtune://track/${track.id}".toUri()
            urlOverride.contains("://") -> urlOverride.toUri()
            else -> Uri.fromFile(File(urlOverride))
        }

        val artist = track.displayArtist.ifBlank { getString(R.string.unknown_artist) }
        val metadataBuilder = MediaMetadata.Builder()
            .setTitle(track.title ?: getString(R.string.untitled_track))
            .setArtist(artist)
            .setSubtitle(artist)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setArtworkUri(track.fullResArtwork.toUri())
            .setDurationMs(track.actualDurationMs)

        track.publisherMetadata?.albumTitle?.takeIf { it.isNotBlank() }?.let { albumTitle ->
            metadataBuilder.setAlbumTitle(albumTitle)
        }

        if (bitmap != null) {
            val scaledBitmap = if (bitmap.width > 720 || bitmap.height > 720) {
                val maxDim = maxOf(bitmap.width, bitmap.height)
                val targetW = (bitmap.width * 720) / maxDim
                val targetH = (bitmap.height * 720) / maxDim
                Bitmap.createScaledBitmap(bitmap, targetW.coerceAtLeast(1), targetH.coerceAtLeast(1), true)
            } else {
                bitmap
            }
            val stream = ByteArrayOutputStream()
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            metadataBuilder.setArtworkData(stream.toByteArray(), MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        }

        val builder = MediaItem.Builder()
            .setUri(uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(metadataBuilder.build())

        val resolvedMimeType = when {
            mimeTypeOverride != null -> mimeTypeOverride
            urlOverride != null && (urlOverride.contains(".m3u8") || urlOverride.contains("m3u8")) -> androidx.media3.common.MimeTypes.APPLICATION_M3U8
            urlOverride != null && (urlOverride.contains(".mpd") || urlOverride.contains("mpd") || com.alananasss.kittytune.audio.providers.tidal.TidalAudioProvider.isLiveManifestUri(urlOverride)) -> androidx.media3.common.MimeTypes.APPLICATION_MPD
            else -> null
        }

        if (resolvedMimeType != null) {
            builder.setMimeType(resolvedMimeType)
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
