package com.alananasss.kittytune.ui.library

import com.alananasss.kittytune.audio.haptics.performAppHaptic
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.ui.common.rememberAppHaptics
import com.alananasss.kittytune.ui.game.SnakeGameLauncher
import com.alananasss.kittytune.ui.game.SnakeGameMode
import com.alananasss.kittytune.ui.game.SnakeGameRequest
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.os.Build
import android.provider.MediaStore
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import com.alananasss.kittytune.utils.GifUtils
import kotlinx.coroutines.flow.first
import com.alananasss.kittytune.utils.formatPlaylistTotalDuration
import com.alananasss.kittytune.data.HistoryRepository
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import com.alananasss.kittytune.ui.upload.TrackArtworkCropDialog
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.SessionManager
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.domain.PlaylistUpdateRequest
import com.alananasss.kittytune.domain.getHighResAvatarUrl
import com.alananasss.kittytune.ui.common.TrackListItemShimmer
import com.alananasss.kittytune.ui.common.MiniSocialProofAvatars
import com.alananasss.kittytune.ui.common.SwipeToDeleteItem
import com.alananasss.kittytune.data.SocialProofRepository
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.File
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import com.alananasss.kittytune.data.local.toTrack
import com.alananasss.kittytune.audio.automix.TransitionPlan
import com.alananasss.kittytune.audio.automix.TransitionPreset
import com.alananasss.kittytune.audio.automix.TransitionStore
import com.alananasss.kittytune.ui.player.mix.CamelotKeyBadge
import com.alananasss.kittytune.ui.player.mix.EditTransitionScreen
import com.alananasss.kittytune.ui.player.mix.TransitionPlayheadColor
import com.alananasss.kittytune.ui.player.mix.transitionPresetIcon
import com.alananasss.kittytune.ui.player.mix.transitionPresetTitle
import com.alananasss.kittytune.utils.makeTimeString


enum class TrackSortBy {
    RECENTLY_ADDED, FIRST_ADDED, TITLE_AZ, ARTIST_AZ
}

/**
 * What a Mix-mode row renders to the right of the track title: the analyzed tempo on the first
 * line, the Camelot badge (coloured by the wheel) plus the track's duration on the second.
 * [fit] is the harmonic fit against the currently playing track — kept for future colouring
 * of the whole card, not just the badge.
 */
data class MixBadgeUi(
    val bpm: Int,
    val camelot: String?,
    val fit: com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer.MixKeyFit,
    val durationMs: Long?,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit,
    playerViewModel: PlayerViewModel,
    youtubeRadioViewModel: YoutubeRadioViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val api = remember { RetrofitClient.create(context) }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val prefs = remember { com.alananasss.kittytune.data.local.PlayerPreferences(context) }
    val showTotalDuration by prefs.getShowPlaylistTotalDurationFlow().collectAsState(initial = prefs.getShowPlaylistTotalDuration())
    val trackRemovalMethod by prefs.getTrackRemovalMethodFlow().collectAsState(initial = prefs.getTrackRemovalMethod())
    val isSwipeRemovalEnabled = trackRemovalMethod == com.alananasss.kittytune.data.local.TrackRemovalMethod.SWIPE_AND_MENU
    val storageTrigger by DownloadManager.storageTrigger.collectAsState()
    val isYoutubeRadio = playlistId.startsWith("yt_radio:")
    val isGuest = remember { TokenManager(context).isGuestMode() }
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    val debouncedStorageTrigger by DownloadManager.debouncedStorageTrigger.collectAsState(0)

    val tracks = remember { mutableStateListOf<Track>() }
    val downloadedPlaylists = remember { mutableStateListOf<Playlist>() }

    val likedTracksRepo by LikeRepository.likedTracks.collectAsState()
    val likedPlaylistsRepo by LikeRepository.likedPlaylists.collectAsState()
    var isAlbum by remember { mutableStateOf(false) }

    var playlistTitle by remember { mutableStateOf("") }
    var isNotFound by remember { mutableStateOf(false) }
    var playlistSharing by remember { mutableStateOf<String?>(null) }
    var playlistCover by remember { mutableStateOf<String?>(null) }
    var coverUpdateKey by remember { mutableLongStateOf(0L) }
    var playlistDescription by remember { mutableStateOf<String?>(null) }
    var playlistTagList by remember { mutableStateOf<String?>(null) }
    var playlistGenre by remember { mutableStateOf<String?>(null) }
    var playlistSetType by remember { mutableStateOf<String?>(null) }
    var playlistReleaseDate by remember { mutableStateOf<String?>(null) }
    var playlistPermalink by remember { mutableStateOf<String?>(null) }
    var playlistUrn by remember { mutableStateOf<String?>(null) }
    var playlistDurationMs by remember { mutableStateOf<Long?>(null) }
    var playlistUser by remember { mutableStateOf<User?>(null) }
    var playlistArtists by remember { mutableStateOf<List<com.alananasss.kittytune.data.spotify.SpotifyArtistRef>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var defaultIcon by remember { mutableStateOf<ImageVector?>(null) }
    // True when this radio was already saved to the library, so its snapshot is
    // shown instead of replacing it with a fresh (and potentially empty) fetch.
    var hasSavedRadio by remember(playlistId) { mutableStateOf(false) }

    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val playlistDownloadProgress by DownloadManager.playlistDownloadProgress.collectAsState()
    val downloadedIds by DownloadManager.downloadedIds.collectAsState()



    val isDownloadedView = playlistId.startsWith("downloaded_section:")
    val isDeezerArtist = playlistId.startsWith("deezer:artist:")
    val isTidalArtist = playlistId.startsWith("tidal:artist:")
    val isQobuzArtist = playlistId.startsWith("qobuz:artist:")
    val isArtistStation = playlistId.startsWith("station_artist:")
    val isArtistView = isDeezerArtist || isTidalArtist || isQobuzArtist || isArtistStation



    val decodedPlaylistId = remember(playlistId) {
        try {
            java.net.URLDecoder.decode(playlistId, "UTF-8")
        } catch (_: Exception) {
            playlistId
        }
    }

    /**
     * Display and provider-agnostic key for this route.
     *
     * Kept byte-for-byte as it has always been computed, because [stableId] is
     * derived from it and indexes the local download database. Normalizing it
     * "properly" would change every hash and orphan playlists the user already
     * downloaded, so it stays a frozen key rather than a source of truth.
     *
     * Spotify ids are NOT read from here. They go through [SpotifyRepository],
     * which normalizes them in one place via SpotifyIds; passing a half-stripped
     * string down here is what used to ship `spotify:playlist:playlist:<id>`
     * upstream and render an empty playlist.
     */
    val cleanIdStr = decodedPlaylistId
        .replace("spotify_playlist:", "")
        .replace("spotify_album:", "")
        .replace("spotify_radio:", "")
        .replace("station_spotify:", "")
        .replace("spotify:", "")
        .replace("deezer:album:", "")
        .replace("deezer:playlist:", "")
        .replace("deezer:artist:", "")
        .replace("deezer_radio:", "")
        .replace("tidal:album:", "")
        .replace("tidal:playlist:", "")
        .replace("tidal:artist:", "")
        .replace("tidal_radio:", "")
        .replace("qobuz:album:", "")
        .replace("qobuz:playlist:", "")
        .replace("qobuz:artist:", "")
        .replace("qobuz_radio:", "")
        .replace("station:", "")
        .replace("station_artist:", "")
        .replace("liked_by:", "")
        .replace("local_playlist:", "")
        .replace("yt_radio:", "")
        .replace("downloaded_section:", "")
        .replace("system_playlist:", "")

    val currentIdLong = cleanIdStr.toLongOrNull() ?: 0L

    val isSystemPlaylistRoute = playlistId.startsWith("system_playlist:") ||
            decodedPlaylistId.startsWith("system_playlist:") ||
            cleanIdStr.startsWith("soundcloud:system-playlists:") ||
            cleanIdStr.contains("discover/sets/") ||
            cleanIdStr.contains("your-playback")

    /**
     * Canonical id for external providers.
     *
     * Search/home build playlist ids with the provider-canonical hash
     * (SpotifyModels, DeezerSearchRepository, ...), while this screen used to
     * hash the cleaned route (`abs(cleanId.hashCode())`, or the raw numeric id
     * for Deezer). Same Spotify playlist therefore had two ids depending on
     * where it was liked from. External routes now use the canonical hash so
     * one playlist is one library entry; SoundCloud keeps the historic logic.
     */
    val stableId = remember(playlistId, cleanIdStr, currentIdLong, isSystemPlaylistRoute) {
        val lowerRoute = decodedPlaylistId.lowercase()
        when {
            lowerRoute.startsWith("spotify:album:") || lowerRoute.startsWith("spotify_album:") -> {
                val raw = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(decodedPlaylistId).trim()
                if (raw.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.spotifyStableId(raw)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("spotify:playlist:") || lowerRoute.startsWith("spotify_playlist:") -> {
                val raw = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(decodedPlaylistId).trim()
                if (raw.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.spotifyStableId(raw)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("spotify_radio:") || lowerRoute.startsWith("station_spotify:") -> {
                val raw = com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(decodedPlaylistId).trim()
                if (raw.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.spotifyStableId(raw)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("deezer:playlist:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.deezerPlaylistStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("deezer:album:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.deezerAlbumStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("tidal:playlist:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.tidalPlaylistStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("tidal:album:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.tidalAlbumStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("tidal_radio:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.tidalRadioStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("qobuz:playlist:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.qobuzPlaylistStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("qobuz:album:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.qobuzAlbumStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("deezer_radio:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.deezerRadioStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            lowerRoute.startsWith("qobuz_radio:") -> {
                if (cleanIdStr.isNotEmpty()) com.alananasss.kittytune.data.local.ExternalPlaylists.qobuzRadioStableId(cleanIdStr)
                else if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
            }
            else -> if (currentIdLong != 0L && !isSystemPlaylistRoute) currentIdLong else kotlin.math.abs(cleanIdStr.hashCode().toLong())
        }
    }

    /** Pre-fix id for migration: likes/rows stored under this must also count. */
    val legacyStableId = remember(playlistId, cleanIdStr, currentIdLong, isSystemPlaylistRoute) {
        com.alananasss.kittytune.data.local.ExternalPlaylists.legacyDetailStableId(cleanIdStr, currentIdLong, isSystemPlaylistRoute)
    }

    /**
     * The Spotify entity id for this route, or null when this is not a Spotify
     * route. Read from [decodedPlaylistId] rather than [cleanIdStr]: the Spotify
     * repositories normalize internally, so handing them the untouched route is
     * what keeps a full URN intact. Callers that receive null are not Spotify
     * routes and must not call into the repository.
     */
    val spotifyRouteId = remember(decodedPlaylistId) {
        val isSpotifyRoute = decodedPlaylistId.startsWith("spotify", ignoreCase = true) ||
                decodedPlaylistId.startsWith("station_spotify:", ignoreCase = true)
        if (!isSpotifyRoute) {
            null
        } else {
            com.alananasss.kittytune.data.spotify.SpotifyRepository.extractId(decodedPlaylistId)
                .takeIf { it.isNotBlank() }
        }
    }

    val playlistInDbCanonical by DownloadManager.isPlaylistInLibraryFlow(stableId).collectAsState(initial = null)
    val playlistInDbLegacy by DownloadManager.isPlaylistInLibraryFlow(legacyStableId).collectAsState(initial = null)
    val playlistInDb = playlistInDbCanonical ?: playlistInDbLegacy

    val effectiveBatchId = if (playlistId == "likes") DownloadManager.LIKES_BATCH_ID else stableId

    val isPlaylistDownloading = DownloadManager.isPlaylistDownloading(effectiveBatchId)
    val currentPlaylistProgress = playlistDownloadProgress[effectiveBatchId]

    var isUserCreated by remember { mutableStateOf(false) }
    var isLocalPlaylist by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
    var showPlaylistDetailsSheet by remember { mutableStateOf(false) }
    var showPlaylistSortSheet by remember { mutableStateOf(false) }

    var playlistPermalinkUrl by remember { mutableStateOf<String?>(null) }

    /**
     * Where tapping the creator's name should go, or null when there is nothing
     * resolvable to open.
     *
     * Only ever a real identifier. A playlist's creator name is a free-form
     * string, not an identity: for a Spotify community playlist the header shows
     * "By <owner name>" with `id == 0L`, no `urn` and no `permalink`. Routing on
     * that name built `profile:Hiver`, and the profile screen resolved it by
     * searching Spotify for an artist called "Hiver" — landing on an unrelated
     * verified profile with its own listener count. A name is never an identity,
     * so a name-only creator gets no link at all rather than a guessed one.
     *
     * Mirrors the desktop, which gates the same block on `id > 0` and renders
     * plain text otherwise.
     */
    val playlistCreatorNavId = remember(playlistUser) {
        com.alananasss.kittytune.domain.playlistCreatorNavId(playlistUser)
    }

    val isLikesScreen = playlistId == "likes" || playlistId.startsWith("liked_by:")
    val isSpotifyRadio = playlistId.startsWith("spotify_radio:") || playlistId.startsWith("station_spotify:")
    /*
     * A radio is saved under the route that knows how it was generated, not under
     * the playlist it happened to resolve to. Spotify's "radio" is often an
     * editorial playlist (or a synthetic one built from recommendations) and
     * YouTube's is a mix URL; neither can be fetched back from just its id, which
     * is why a liked radio used to open empty. Keeping the seed lets the library
     * reopen the same radio and match the tracks saved with it.
     */
    val radioRoutePermalink = remember(playlistId, spotifyRouteId) {
        when {
            playlistId.startsWith("yt_radio:") -> playlistId
            isSpotifyRadio -> spotifyRouteId?.let { "spotify_radio:$it" }
            playlistId.startsWith("tidal_radio:") -> playlistId
            playlistId.startsWith("deezer_radio:") -> playlistId
            playlistId.startsWith("qobuz_radio:") -> playlistId
            else -> null
        }
    }
    val isSpecialSystemScreen = isLikesScreen || playlistId == "downloads" || playlistId == "local_files" || isSystemPlaylistRoute || isSpotifyRadio || playlistId.startsWith("yt_radio:") || playlistId.startsWith("tidal_radio:") || playlistId.startsWith("deezer_radio:") || playlistId.startsWith("qobuz_radio:") || playlistId.startsWith("station:") || playlistId.startsWith("station_artist:") || playlistId.startsWith("spotify:")
    // Drag-and-drop is enabled for ANY playlist whose order the app can
    // persist: user-created, downloaded, local
    // (created in the app, id < 0), and anything saved to the library
    // (playlistInDb != null — importing creates refs with addedAt).
    //
    // isSpecialSystemScreen used to cut off the ENTIRE "spotify:" prefix — together with
    // imported spotify:playlist:… entries that do have refs in the database and whose
    // order is persisted by stableId. For them, drag-and-drop silently did nothing:
    // ReorderableItem simply was never built. Now imported playlists (playlists
    // that are neither radio nor albums) are exempted from that cut-off. Radio, stations,
    // albums, and system lists keep a fixed order.
    val isImportedReorderablePlaylist = playlistInDb != null &&
        (playlistId.startsWith("spotify:playlist:") || playlistId.startsWith("spotify_playlist:") ||
            playlistId.startsWith("deezer:playlist:") || playlistId.startsWith("tidal:playlist:") ||
            playlistId.startsWith("qobuz:playlist:") ||
            decodedPlaylistId.startsWith("spotify:playlist:") || decodedPlaylistId.startsWith("spotify_playlist:") ||
            decodedPlaylistId.startsWith("deezer:playlist:") || decodedPlaylistId.startsWith("tidal:playlist:") ||
            decodedPlaylistId.startsWith("qobuz:playlist:"))
    val isCanReorderGlobal = (isUserCreated || isDownloadedView || isLocalPlaylist || playlistInDb != null) &&
        (!isSpecialSystemScreen || isImportedReorderablePlaylist)

    val listState = rememberLazyListState()
    // Manual reorder counter: tracksToDisplay / tracksFilteredByVibe are
    // remember(...) lists, and mutating the SnapshotStateList does not change their keys.
    // Without this tick, drag-and-drop in a sorted/filtered view moved the
    // "raw" list while the screen kept showing a stale derived order.
    var reorderTick by remember { mutableStateOf(0) }
    val shareUrl = remember(playlistId, currentIdLong, playlistPermalinkUrl, playlistUser) {
        when {
            playlistId.startsWith("station_artist:") -> "https://soundcloud.com/discover/sets/artist-stations:$currentIdLong"
            playlistId.startsWith("station:") -> "https://soundcloud.com/discover/sets/track-stations:$currentIdLong"
            playlistId.startsWith("yt_radio:") -> {
                val decodedUrl = Uri.decode(cleanIdStr)
                val videoId = decodedUrl.substringAfter("v=").substringBefore("&")
                "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"
            }

            isSpotifyRadio -> spotifyRouteId?.let { "https://open.spotify.com/track/$it" } ?: ""

            playlistId.startsWith("liked_by:") -> {
                val user = playlistUser
                val profileUrl = playlistPermalinkUrl ?: user?.permalinkUrl
                if (profileUrl != null) {
                    "$profileUrl/likes"
                } else {
                    "https://soundcloud.com/discover/sets/liked-by::$currentIdLong"
                }
            }

            playlistId == "likes" -> {
                val user = playlistUser
                if (user != null && user.id > 0 && !user.permalinkUrl.isNullOrEmpty()) {
                    "${user.permalinkUrl}/likes"
                } else {
                    ""
                }
            }

            playlistId == "downloads" -> ""

            playlistId == "vk_likes" -> {
                val ownerId = playlistUser?.id ?: 0L
                if (ownerId > 0L) "https://vk.com/audios$ownerId" else "https://vk.com/audio"
            }

            playlistId.startsWith("vk_playlist:") -> {
                val parts = playlistId.removePrefix("vk_playlist:").split("_")
                val ownerId = parts.getOrNull(0).orEmpty()
                val vkPlaylistId = parts.getOrNull(1).orEmpty()
                val accessHash = parts.getOrNull(2).orEmpty()
                if (ownerId.isNotBlank() && vkPlaylistId.isNotBlank()) {
                    buildString {
                        append("https://vk.com/music/playlist/")
                        append(ownerId)
                        append("_")
                        append(vkPlaylistId)
                        if (accessHash.isNotBlank()) {
                            append("_")
                            append(accessHash)
                        }
                    }
                } else {
                    ""
                }
            }

            !playlistPermalinkUrl.isNullOrEmpty() -> playlistPermalinkUrl!!
            currentIdLong > 0 -> "https://soundcloud.com/playlists/$currentIdLong"
            else -> ""
        }
    }

    LaunchedEffect(playlistInDb, currentIdLong, playlistUser, playerViewModel.currentUserId) {
        if (isSpecialSystemScreen) {
            isLocalPlaylist = isDownloadedView
            isUserCreated = false
        } else {
            val currentUserId = playerViewModel.currentUserId.takeIf { it != 0L }
                ?: com.alananasss.kittytune.data.local.PlayerPreferences(context).getCachedUserId().takeIf { it != 0L }
            val currentUsername = playerViewModel.currentUser?.username
                ?: com.alananasss.kittytune.data.local.PlayerPreferences(context).getCachedUsername()

            val isOwnedByCurrentAccount = (playlistUser?.id != null && playlistUser?.id != 0L && playlistUser?.id == currentUserId) ||
                (!currentUsername.isNullOrBlank() && (playlistInDb?.artist?.equals(currentUsername, ignoreCase = true) == true || playlistUser?.username?.equals(currentUsername, ignoreCase = true) == true))

            val isLocalUser = currentIdLong < 0 || (playlistInDb?.isUserCreated == true) || isOwnedByCurrentAccount
            isLocalPlaylist = isDownloadedView || currentIdLong < 0
            isUserCreated = isLocalUser

            if (playlistInDb != null) {
                if (isLocalUser && !playlistInDb!!.isUserCreated) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                        try {
                            val dao = com.alananasss.kittytune.data.local.AppDatabase.getDatabase(context).downloadDao()
                            dao.updatePlaylist(playlistInDb!!.copy(isUserCreated = true))
                        } catch (_: Exception) {}
                    }
                }
                val dbTitle = playlistInDb!!.title
                if (!dbTitle.isNullOrBlank() && dbTitle != context.getString(R.string.untitled_track) && dbTitle != "Untitled Track") {
                    playlistTitle = dbTitle
                }
                val localCoverFile = java.io.File(context.filesDir, "playlist_cover_${currentIdLong}.jpg")
                val dbCover = playlistInDb!!.localCoverPath ?: if (localCoverFile.exists()) localCoverFile.absolutePath else playlistInDb!!.artworkUrl
                if (!dbCover.isNullOrBlank() && dbCover != playlistCover) {
                    playlistCover = dbCover
                    coverUpdateKey = System.currentTimeMillis()
                }
            } else if (currentIdLong > 0) {
                val localCoverFile = java.io.File(context.filesDir, "playlist_cover_${currentIdLong}.jpg")
                if (localCoverFile.exists() && playlistCover != localCoverFile.absolutePath) {
                    playlistCover = localCoverFile.absolutePath
                    coverUpdateKey = System.currentTimeMillis()
                }
            }
        }
    }

    LaunchedEffect(stableId, playlistId) {
        DownloadManager.trackRemovedFromPlaylist.collect { (targetPlaylistId, removedTrackId) ->
            if (targetPlaylistId == stableId || (playlistId == "downloads" && targetPlaylistId == -2L)) {
                tracks.removeAll { it.id == removedTrackId }
            }
        }
    }

    var tempCoverBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showCoverCropDialog by remember { mutableStateOf(false) }
    var pendingGifUri by remember { mutableStateOf<Uri?>(null) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null && stableId != 0L) {
                if (GifUtils.isGif(context.contentResolver, uri)) {
                    val size = GifUtils.contentSize(context.contentResolver, uri)
                    if (size < 0 || GifUtils.isAcceptableGifCoverSize(size)) {
                        pendingGifUri = uri
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.gif_cover_too_large, GifUtils.MAX_GIF_COVER_MB),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@rememberLauncherForActivityResult
                }
                try {
                    val bitmap = if (Build.VERSION.SDK_INT < 28) {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                    } else {
                        val source = ImageDecoder.createSource(context.contentResolver, uri)
                        ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                            decoder.isMutableRequired = true
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }
                    if (bitmap != null) {
                        tempCoverBitmap = bitmap
                        showCoverCropDialog = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    )

    if (showCoverCropDialog && tempCoverBitmap != null) {
        TrackArtworkCropDialog(
            bitmap = tempCoverBitmap!!,
            descRes = R.string.playlist_crop_artwork_desc,
            onDismiss = {
                showCoverCropDialog = false
                tempCoverBitmap = null
            },
            onSave = { croppedBitmap ->
                if (stableId != 0L) {
                    DownloadManager.updatePlaylistCover(
                        playlistId = stableId,
                        bitmap = croppedBitmap,
                        title = playlistTitle,
                        artist = playlistUser?.username
                    )
                    val newPath = java.io.File(context.filesDir, "playlist_cover_${stableId}.jpg").absolutePath
                    playlistCover = newPath
                    coverUpdateKey = System.currentTimeMillis()
                }
                showCoverCropDialog = false
                tempCoverBitmap = null
            }
        )
    }

    if (pendingGifUri != null) {
        GifCoverPreviewDialog(
            uri = pendingGifUri!!,
            onDismiss = { pendingGifUri = null },
            onConfirm = {
                if (stableId != 0L) {
                    DownloadManager.updatePlaylistCover(
                        playlistId = stableId,
                        uri = pendingGifUri!!,
                        title = playlistTitle,
                        artist = playlistUser?.username
                    )
                    val newPath = java.io.File(context.filesDir, "playlist_cover_${stableId}.jpg").absolutePath
                    playlistCover = newPath
                    coverUpdateKey = System.currentTimeMillis()
                    Toast.makeText(
                        context,
                        context.getString(R.string.gif_cover_set_done),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                pendingGifUri = null
            }
        )
    }

    val effectiveCoverModel: Any? = remember(playlistCover, coverUpdateKey) {
        if (playlistCover.isNullOrEmpty()) null
        else if (playlistCover!!.startsWith("/")) {
            coil.request.ImageRequest.Builder(context)
                .data(java.io.File(playlistCover!!))
                .memoryCacheKey("${playlistCover}_${coverUpdateKey}")
                .diskCacheKey("${playlistCover}_${coverUpdateKey}")
                .build()
        } else {
            playlistCover
        }
    }

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var playlistSearchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var playlistSortBy by remember { mutableStateOf(TrackSortBy.RECENTLY_ADDED) }

    // --- Playlist Mix (Spotify-Mix mode) ---------------------------------------------
    // Mix Mode turns every row into an analyzed DJ card (tempo + Camelot badge + duration),
    // renders a transition connector between every pair of rows, and exposes the smart-sort
    // action. Tapping a connector opens the full-screen transition editor.
    var isMixMode by rememberSaveable { mutableStateOf(false) }
    // The mixer belongs to the user's own created playlists only: likes, stations,
    // radios, charts and other foreign lists never carry a mix — so the moment the
    // list resolves as not-ours, the mode (and its overlays) drops back off.
    LaunchedEffect(isUserCreated) {
        if (!isUserCreated) isMixMode = false
    }
    // The pair being edited; non-null renders the full-screen EditTransitionScreen on top.
    var transitionEditor by remember { mutableStateOf<Pair<Track, Track>?>(null) }
    // The smart sort's order, as track ids. Kept separate from `tracks` so it survives
    // re-fetches and composes with the existing search/sort pipeline instead of fighting it.
    var mixOrderedIds by remember { mutableStateOf<List<Long>?>(null) }

    val currentPlayingTrack = playerViewModel.uiCurrentTrack
    // The reference the badges are coloured against: the key of the track playing right now.
    // Cold flow per playing track; flowOf(null) keeps the pipeline honest when Mix is off.
    val mixReferenceProfile by remember(isMixMode, currentPlayingTrack?.id) {
        if (isMixMode && currentPlayingTrack != null) {
            com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer.profileFlow(context, currentPlayingTrack)
        } else {
            kotlinx.coroutines.flow.flowOf(null)
        }
    }.collectAsState(initial = null)
    val mixReferenceKey = mixReferenceProfile?.camelot

    val vibes by com.alananasss.kittytune.data.VibesRepository.vibes.collectAsState()
    var selectedVibeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId, likedTracksRepo.size, isGuest) {
        if (playlistId == "likes" && !isGuest) {
            com.alananasss.kittytune.data.VibesRepository.loadVibes(likedTracksRepo)
        }
    }

    val rawTracks = if (playlistId == "likes") likedTracksRepo else tracks

    val tracksFilteredByVibe = remember(rawTracks, selectedVibeId, vibes, playlistId, reorderTick) {
        if (selectedVibeId == null || playlistId != "likes") {
            rawTracks
        } else {
            val selectedVibe = vibes.find { it.id == selectedVibeId }
            if (selectedVibe != null && selectedVibe.trackIds.isNotEmpty()) {
                rawTracks.filter { selectedVibe.trackIds.contains(it.id) }
            } else if (selectedVibe != null) {
                val idLower = selectedVibe.id.lowercase()
                val nameLower = selectedVibe.displayName.lowercase()
                rawTracks.filter { t ->
                    val genre = t.genre.orEmpty().lowercase()
                    val tagList = t.tagList.orEmpty().lowercase()
                    val title = t.title.orEmpty().lowercase()
                    genre.contains(idLower) || genre.contains(nameLower) || tagList.contains(idLower) || tagList.contains(
                        nameLower
                    ) || title.contains(idLower) || title.contains(nameLower)
                }
            } else {
                rawTracks
            }
        }
    }

    val tracksToDisplay = remember(tracksFilteredByVibe, playlistSearchQuery, playlistSortBy, mixOrderedIds, reorderTick) {
        val base = if (playlistSearchQuery.isEmpty() && playlistSortBy == TrackSortBy.RECENTLY_ADDED) {
            tracksFilteredByVibe
        } else {
            val filtered = if (playlistSearchQuery.isEmpty()) {
                tracksFilteredByVibe
            } else {
                tracksFilteredByVibe.filter {
                    it.title?.contains(playlistSearchQuery, ignoreCase = true) == true ||
                            it.user?.username?.contains(playlistSearchQuery, ignoreCase = true) == true
                }
            }
            when (playlistSortBy) {
                TrackSortBy.RECENTLY_ADDED -> filtered
                TrackSortBy.FIRST_ADDED -> filtered.reversed()
                TrackSortBy.TITLE_AZ -> filtered.sortedBy { it.title?.lowercase() ?: "" }
                TrackSortBy.ARTIST_AZ -> filtered.sortedBy { it.user?.username?.lowercase() ?: "" }
            }
        }
        // The smart mix order rides on top of the base pipeline: known ids first, in the
        // planned order; anything that arrived after the sort keeps its relative order.
        val orderedIds = mixOrderedIds
        if (orderedIds == null) base
        else {
            val byId = base.associateBy { it.id }
            orderedIds.mapNotNull { byId[it] } + base.filter { it.id !in orderedIds }
        }
    }

    val applySmartMixSort: () -> Unit = {
        scope.launch {
            val source = tracksToDisplay.toList()
            if (source.size > 1) {
                val profiles = com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer.profilesFor(context, source)
                val ordered = com.alananasss.kittytune.data.mix.MixQueuePlanner.greedyMixOrder(source) { profiles[it.id] }
                mixOrderedIds = ordered.map { it.id }
                Toast.makeText(context, R.string.mix_smart_sort_applied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (!isCanReorderGlobal) return@rememberReorderableLazyListState
            val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
            val toId = to.key as? Long ?: return@rememberReorderableLazyListState

            // ── Why by the DISPLAYED order, not by tracks ──────────────────────
            // The user drags a row through the list they SEE: that is tracksToDisplay
            // (search, sorting, the vibe filter, and the smart mix overlay
            // can all sit on top of tracks). Previously onMove reordered the "raw"
            // tracks: with any active transformation the indices did not match,
            // the move turned into a no-op and the row instantly snapped back —
            // "drag-and-drop does not work". Now the new sequence is built from
            // the visible layout and written back into every layer.
            val displayed = tracksToDisplay.toList()
            val displayFrom = displayed.indexOfFirst { it.id == fromId }
            val displayTo = displayed.indexOfFirst { it.id == toId }
            if (displayFrom == -1 || displayTo == -1 || displayFrom == displayTo) {
                return@rememberReorderableLazyListState
            }

            val newDisplayed = displayed.toMutableList().apply {
                // The library's standard formula: to.index is the insertion index after removal.
                add(displayTo, removeAt(displayFrom))
            }

            // 1) The smart sort overlay lives on top — sync it first.
            if (mixOrderedIds != null) {
                mixOrderedIds = newDisplayed.map { it.id }
            }

            // 2) Write the visible order back into the "raw" list: the shown tracks
            // go in the new order, while the hidden ones (filter/search) keep their relative
            // order in the tail.
            val displayedIds = newDisplayed.map { it.id }.toHashSet()
            val tail = tracks.filter { it.id !in displayedIds }
            val byId = tracks.associateBy { it.id }
            tracks.apply {
                clear()
                addAll(newDisplayed.mapNotNull { byId[it.id] })
                addAll(tail)
            }
            reorderTick++
            view.performAppHaptic(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    )

    var wasDragging by remember { mutableStateOf(false) }
    val isDragging = reorderableState.isAnyItemDragging
    LaunchedEffect(isDragging) {
        if (wasDragging && !isDragging) {
            val newOrder = tracks.map { it.id }
            // Refs are keyed by stableId — the same key that track
            // add/remove uses. This used to be currentIdLong: for regular
            // SoundCloud playlists the two matched, but for imported
            // spotify/deezer/tidal/qobuz lists the stable id is a hash, so
            // the order after drag-and-drop simply was not persisted.
            DownloadManager.reorderPlaylistTracks(stableId, newOrder)
            if (isUserCreated && !isDownloadedView && currentIdLong > 0) {
                DownloadManager.syncPlaylistOrderOnline(currentIdLong, newOrder)
            }
            playerViewModel.updatePlayingQueueOrderIfMatching(stableId, tracks.toList())
            if (isMixMode && tracks.size > 1) {
                val currentTracksSnapshot = tracks.toList()
                scope.launch {
                    val profiles = com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer.profilesFor(context, currentTracksSnapshot)
                    com.alananasss.kittytune.data.mix.MixQueuePlanner.recomputeTransitionsForReorderedTracks(
                        context = context,
                        playlistKey = playlistId,
                        tracks = currentTracksSnapshot,
                        profiles = profiles
                    )
                }
            }
        }
        wasDragging = isDragging
    }

    val totalDurationMs = remember(tracksToDisplay, playlistDurationMs, playlistSearchQuery, showTotalDuration) {
        if (!showTotalDuration) 0L
        else playlistDurationMs?.takeIf { it > 0L && playlistSearchQuery.isEmpty() }
            ?: tracksToDisplay.sumOf { it.durationMs ?: 0L }
    }

    val isFullyDownloaded = remember(
        tracksToDisplay,
        downloadedIds,
        isPlaylistDownloading,
        isDownloadedView,
        playlistInDb,
        playlistId
    ) {
        if (tracksToDisplay.isEmpty() || isPlaylistDownloading) false
        else if (isDownloadedView) true
        else if (downloadedIds.size < tracksToDisplay.size) false
        else {
            val allInDb = playlistId == "likes" || (playlistInDb != null && playlistInDb?.isDownloaded == true)
            allInDb && tracksToDisplay.all { downloadedIds.contains(it.id) }
        }
    }
    val refreshTrigger =
        if (playlistId == "downloads" || playlistId == "local_files" || currentIdLong < 0L || isDownloadedView) debouncedStorageTrigger else 0

    LaunchedEffect(playlistId, refreshTrigger) {
        if (playlistId.startsWith("yt_radio:")) {
            val db = AppDatabase.getDatabase(context).downloadDao()
            val savedRadio = if (stableId != 0L) db.getPlaylist(stableId) else null
            val savedTracks = if (savedRadio != null) db.getTracksForPlaylistSync(stableId) else emptyList()
            if (savedRadio != null && savedTracks.isNotEmpty()) {
                hasSavedRadio = true
                playlistTitle = savedRadio.title
                playlistCover = savedRadio.localCoverPath ?: savedRadio.artworkUrl
                playlistUser = User(0, savedRadio.artist, null)
                playlistPermalinkUrl = savedRadio.permalinkUrl
                defaultIcon = Icons.Rounded.Radio
                tracks.clear()
                tracks.addAll(savedTracks.map { it.toTrack() })
                isLoading = false
            } else {
                hasSavedRadio = false
                val encodedUrl = playlistId.removePrefix("yt_radio:")
                val url = Uri.decode(encodedUrl)
                youtubeRadioViewModel.loadInitial(url)
            }
            return@LaunchedEffect
        }

        if (tracks.isEmpty() && downloadedPlaylists.isEmpty()) {
            isLoading = true
        }

        val newTracks = mutableListOf<Track>()
        val newDownloadedPlaylists = mutableListOf<Playlist>()

        try {
            if (playerViewModel.currentUserId == 0L) {
                playerViewModel.fetchUserProfile()
            }

            val db = AppDatabase.getDatabase(context).downloadDao()

            when {
                playlistId == "vk_likes" -> {
                    playlistTitle = context.getString(R.string.lib_vk_saved_tracks)
                    defaultIcon = null
                    val vkRepo = com.alananasss.kittytune.data.vk.VkRepository.getInstance(context)
                    val vkUser = vkRepo.tokenManager.getUser()
                    playlistCover = vkUser?.photoMax
                    playlistUser = User(vkRepo.tokenManager.userId, vkUser?.fullName ?: "VKontakte", vkUser?.photoMax)
                    try {
                        val result = vkRepo.getUserAudios(offset = 0, count = 200)
                        newTracks.addAll(result.tracks)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                playlistId.startsWith("vk_playlist:") -> {
                    val raw = playlistId.removePrefix("vk_playlist:")
                    val parts = raw.split("_")
                    val ownerId = parts.getOrNull(0)?.toLongOrNull() ?: 0L
                    val pId = parts.getOrNull(1)?.toLongOrNull() ?: 0L
                    val accessHash = parts.getOrNull(2) ?: ""
                    val vkRepo = com.alananasss.kittytune.data.vk.VkRepository.getInstance(context)
                    val vkUser = vkRepo.tokenManager.getUser()
                    try {
                        val playlists = vkRepo.getPlaylists()
                        val currentPl = playlists.find { it.id == pId }
                        playlistTitle = currentPl?.title ?: "VK Playlist"
                        playlistCover = currentPl?.coverUrl
                        playlistUser = User(ownerId, vkUser?.fullName ?: "VKontakte", null)
                        val pTracks = vkRepo.getPlaylistAudios(ownerId, pId, accessHash)
                        newTracks.addAll(pTracks)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                playlistId == "likes" -> {
                    playlistTitle = context.getString(R.string.lib_liked_tracks)
                    defaultIcon = Icons.Rounded.Favorite
                    playlistUser = playerViewModel.currentUser ?: User(
                        id = playerViewModel.currentUserId,
                        username = prefs.getCachedUsername() ?: context.getString(R.string.me_artist),
                        avatarUrl = null
                    )
                }

                playlistId == "downloads" -> {
                    playlistTitle = context.getString(R.string.lib_downloads)
                    defaultIcon = Icons.Rounded.Folder
                    isLocalPlaylist = false
                    val localPlaylists = db.getDownloadedPlaylists().first()
                    newDownloadedPlaylists.addAll(localPlaylists.mapNotNull { local ->
                        val tracksInPlaylist = db.getTracksForPlaylistSync(local.id)
                        val realDownloadedCount = tracksInPlaylist.count { it.localAudioPath.isNotEmpty() }
                        if (realDownloadedCount == 0) return@mapNotNull null
                        val firstTrackArt =
                            tracksInPlaylist.firstOrNull { it.localArtworkPath.isNotEmpty() || it.artworkUrl.isNotEmpty() }
                                ?.let {
                                    it.localArtworkPath.ifEmpty { it.artworkUrl }
                                }
                        val finalArt = local.localCoverPath ?: local.artworkUrl.ifEmpty { firstTrackArt ?: "" }

                        Playlist(
                            id = local.id,
                            title = local.title,
                            artworkUrl = finalArt,
                            calculatedArtworkUrl = local.localCoverPath,
                            trackCount = realDownloadedCount,
                            user = User(0, local.artist, null),
                            tracks = null
                        )
                    })
                    val allDownloadedTracks = db.getAllTracksList().filter { it.localAudioPath.isNotEmpty() }
                    newTracks.addAll(allDownloadedTracks.map { local ->
                        local.toTrack(
                            artworkOverride = local.localArtworkPath.ifEmpty { local.artworkUrl },
                            isLiked = true
                        )
                    })
                }

                playlistId == "local_files" -> {
                    playlistTitle = context.getString(R.string.lib_local_media)
                    defaultIcon = Icons.Default.SdStorage
                    isLocalPlaylist = false
                    val allTracks = db.getAllTracksList()
                    val localFileTracks = allTracks.filter { it.id < 0 }
                    newTracks.addAll(localFileTracks.map { local ->
                        local.toTrack(
                            artworkOverride = local.localArtworkPath.ifEmpty { local.artworkUrl },
                            description = context.getString(
                                R.string.description_local_file,
                                local.localAudioPath
                            )
                        )
                    })
                }

                playlistId.startsWith("liked_by:") -> {
                    val targetUserId = currentIdLong
                    defaultIcon = Icons.Rounded.Favorite
                    val user = api.getUser(targetUserId)
                    playlistCover = user.avatarUrl.getHighResAvatarUrl() ?: user.avatarUrl
                    playlistUser = user
                    playlistPermalinkUrl = user.permalinkUrl

                    val allCollectedTracks = mutableListOf<Track>()
                    var likesResponse = api.getUserTrackLikes(targetUserId, limit = 200)
                    allCollectedTracks.addAll(likesResponse.collection.mapNotNull { it.track })

                    var nextUrl = likesResponse.next_href
                    var safetyPageCount = 0
                    while (nextUrl != null && safetyPageCount < 50) {
                        try {
                            val nextResponse = api.getTrackLikesNextPage(nextUrl!!)
                            allCollectedTracks.addAll(nextResponse.collection.mapNotNull { it.track })
                            nextUrl = nextResponse.next_href
                            safetyPageCount++
                        } catch (e: Exception) {
                            e.printStackTrace()
                            break
                        }
                    }
                    newTracks.addAll(allCollectedTracks.distinctBy { it.id })
                }

                else -> {
                    val isOffline = !com.alananasss.kittytune.utils.NetworkUtils.isInternetAvailable(context)
                    val isLocalUserPlaylist = currentIdLong < 0
                    // A saved radio is a snapshot: never let a fresh remote fetch
                    // replace (or fail to find) the tracks the user kept by liking it.
                    // Older builds saved the remote URL instead of the seed route, so
                    // recognise those rows too instead of leaving existing likes empty.
                    val savedRadio = if (stableId != 0L) db.getPlaylist(stableId)?.takeIf { local ->
                        radioRoutePermalink != null || local.permalinkUrl?.let { permalink ->
                            val lower = permalink.lowercase()
                            lower.startsWith("yt_radio:") ||
                                lower.startsWith("spotify_radio:") ||
                                lower.contains("youtube.com") ||
                                lower.contains("youtu.be") ||
                                (lower.contains("spotify") && local.title.contains("radio", ignoreCase = true))
                        } == true
                    } else null
                    val savedRadioTracks = if (savedRadio != null) db.getTracksForPlaylistSync(stableId) else emptyList()
                    // A route-based radio whose snapshot is still empty (liked from a card
                    // that had not loaded its tracks) should be fetched again instead of
                    // rendering an empty screen. Legacy rows without a route keep resolving
                    // locally so a numeric id is never mistaken for a SoundCloud playlist.
                    val forceLocal = isOffline || isDownloadedView || isLocalUserPlaylist ||
                        (savedRadio != null && (savedRadioTracks.isNotEmpty() || radioRoutePermalink == null))
                    val localPlaylist = if (stableId != 0L && forceLocal) savedRadio ?: db.getPlaylist(stableId) else null
                    if (localPlaylist != null) {
                        playlistTitle = localPlaylist.title
                        playlistCover = localPlaylist.localCoverPath ?: localPlaylist.artworkUrl
                        if (radioRoutePermalink != null) defaultIcon = Icons.Rounded.Radio

                        playlistUser = User(0, localPlaylist.artist, null)
                        isUserCreated = localPlaylist.isUserCreated || localPlaylist.id < 0 || isLocalUserPlaylist
                        isLocalPlaylist = isDownloadedView || isLocalUserPlaylist
                        playlistPermalinkUrl = localPlaylist.permalinkUrl

                        val playlistTracks = db.getTracksForPlaylistSync(stableId)
                        val filteredTracks = if (isDownloadedView) {
                            playlistTracks.filter { it.localAudioPath.isNotEmpty() }
                        } else {
                            playlistTracks
                        }

                        newTracks.addAll(filteredTracks.map { local -> local.toTrack() })

                        val brokenTracks = filteredTracks.filter { local ->
                            local.id > 0 && !local.artworkUrl.startsWith("http") &&
                                    (local.localArtworkPath.isEmpty() || !File(local.localArtworkPath).exists() || File(local.localArtworkPath).length() == 0L)
                        }
                        if (brokenTracks.isNotEmpty() && com.alananasss.kittytune.utils.NetworkUtils.isInternetAvailable(context)) {
                            try {
                                val idsStr = brokenTracks.map { it.id }.joinToString(",")
                                val onlineTracks = api.getTracksByIds(idsStr)
                                val onlineMap = onlineTracks.associateBy { it.id }

                                for (i in newTracks.indices) {
                                    val t = newTracks[i]
                                    val online = onlineMap[t.id]
                                    if (online != null && online.fullResArtwork.startsWith("http")) {
                                        newTracks[i] = t.copy(artworkUrl = online.fullResArtwork, user = online.user ?: t.user)
                                        val localInDb = db.getTrack(t.id)
                                        if (localInDb != null) {
                                            db.updateTrack(localInDb.copy(artworkUrl = online.fullResArtwork))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e("PlaylistDetailScreen", "Failed to fetch missing track artworks", e)
                            }
                        }
                    } else {
                        val isSystemPlaylistRoute = playlistId.startsWith("system_playlist:") || playlistId.startsWith("soundcloud:system-playlists:")
                        val isSpotifyAlbum = playlistId.startsWith("spotify:album:") || playlistId.startsWith("spotify_album:")
                        val isSpotifyPlaylist = playlistId.startsWith("spotify:playlist:") || playlistId.startsWith("spotify_playlist:")
                        val isSpotifyRadio = playlistId.startsWith("spotify_radio:") || playlistId.startsWith("station_spotify:")

                        val isDeezerAlbum = playlistId.startsWith("deezer:album:")
                        val isDeezerPlaylist = playlistId.startsWith("deezer:playlist:")

                        val isTidalAlbum = playlistId.startsWith("tidal:album:")
                        val isTidalPlaylist = playlistId.startsWith("tidal:playlist:")
                        val isTidalRadio = playlistId.startsWith("tidal_radio:")
                        val isDeezerRadio = playlistId.startsWith("deezer_radio:")
                        val isQobuzRadio = playlistId.startsWith("qobuz_radio:")

                        val isQobuzAlbum = playlistId.startsWith("qobuz:album:")
                        val isQobuzPlaylist = playlistId.startsWith("qobuz:playlist:")

                        val spotifyId = spotifyRouteId
                        if (isSpotifyAlbum) {
                            val album = spotifyId?.let {
                                com.alananasss.kittytune.data.spotify.SpotifyRepository.getAlbum(it)
                            }
                            if (album != null) {
                                isAlbum = true
                                playlistTitle = album.name
                                playlistCover = album.artworkUrl
                                playlistArtists = album.artists
                                val firstArtist = album.artists.firstOrNull()
                                playlistUser = User(
                                    id = kotlin.math.abs(firstArtist?.id?.hashCode()?.toLong() ?: 0L),
                                    username = album.artistName,
                                    avatarUrl = firstArtist?.avatarUrl ?: album.artworkUrl,
                                    urn = firstArtist?.id?.let { "spotify:artist:$it" },
                                    permalink = firstArtist?.id,
                                    verified = firstArtist?.verified ?: false
                                )
                                playlistReleaseDate = album.releaseDate
                                playlistPermalinkUrl = "https://open.spotify.com/album/${album.id}"
                                playlistUrn = "spotify:album:${album.id}"
                                newTracks.addAll(album.tracks.map { it.toTrack() })
                            }
                        } else if (isSpotifyPlaylist) {
                            val pl = spotifyId?.let {
                                com.alananasss.kittytune.data.spotify.SpotifyRepository.getPlaylist(it)
                            }
                            if (pl != null) {
                                isAlbum = false
                                playlistTitle = pl.name
                                playlistCover = pl.artworkUrl
                                playlistDescription = pl.description
                                playlistUser = User(
                                    id = 0L,
                                    username = pl.ownerName ?: "Spotify",
                                    avatarUrl = pl.artworkUrl
                                )
                                playlistPermalinkUrl = "https://open.spotify.com/playlist/${pl.id}"
                                playlistUrn = "spotify:playlist:${pl.id}"
                                newTracks.addAll(pl.tracks.map { it.toTrack() })
                            }
                        } else if (isDeezerAlbum) {
                            val pl = com.alananasss.kittytune.data.deezer.DeezerSearchRepository.getAlbum(cleanIdStr)
                            if (pl != null) {
                                isAlbum = true
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistUser = pl.user
                                playlistReleaseDate = pl.releaseDate
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isDeezerPlaylist) {
                            val pl = com.alananasss.kittytune.data.deezer.DeezerSearchRepository.getPlaylist(cleanIdStr)
                            if (pl != null) {
                                isAlbum = false
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistDescription = pl.description
                                playlistUser = pl.user
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isDeezerArtist) {
                            val pl = com.alananasss.kittytune.data.deezer.DeezerSearchRepository.getArtist(cleanIdStr)
                            if (pl != null) {
                                isAlbum = false
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistUser = pl.user
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isTidalAlbum) {
                            val pl = com.alananasss.kittytune.data.tidal.TidalSearchRepository.getAlbum(context, cleanIdStr)
                            if (pl != null) {
                                isAlbum = true
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistUser = pl.user
                                playlistReleaseDate = pl.releaseDate
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isTidalPlaylist) {
                            val pl = com.alananasss.kittytune.data.tidal.TidalSearchRepository.getPlaylist(context, cleanIdStr)
                            if (pl != null) {
                                isAlbum = false
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistDescription = pl.description
                                playlistUser = pl.user
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isTidalArtist) {
                            val pl = com.alananasss.kittytune.data.tidal.TidalSearchRepository.getArtist(context, cleanIdStr)
                            if (pl != null) {
                                isAlbum = false
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistUser = pl.user
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isTidalRadio) {
                            // TIDAL builds a radio around the seed itself: the seed, then
                            // the tracks it considers close. The route keeps the seed id
                            // so the same radio can be reopened from the library.
                            val radioTracks = com.alananasss.kittytune.data.tidal.TidalSearchRepository
                                .getRadio(context, cleanIdStr)
                            // TIDAL refuses to build a radio for some tracks; showing the
                            // track itself beats an empty screen.
                            val radio = radioTracks.ifEmpty {
                                listOfNotNull(
                                    com.alananasss.kittytune.data.tidal.TidalSearchRepository
                                        .getTrack(context, cleanIdStr)
                                )
                            }
                            if (radio.isNotEmpty()) {
                                val seed = radio.first()
                                isAlbum = false
                                playlistTitle = context.getString(R.string.tidal_radio_title, seed.title ?: "")
                                playlistCover = seed.artworkUrl
                                defaultIcon = Icons.Rounded.Radio
                                playlistUser = User(
                                    id = kotlin.math.abs("TIDAL".hashCode().toLong()),
                                    username = "TIDAL",
                                    avatarUrl = seed.artworkUrl
                                )
                                playlistPermalinkUrl = "https://tidal.com/browse/track/$cleanIdStr"
                                playlistUrn = "tidal_radio:$cleanIdStr"
                                newTracks.addAll(radio)
                            }
                        } else if (isQobuzAlbum) {
                            val pl = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository.getAlbum(context, cleanIdStr)
                            if (pl != null) {
                                isAlbum = true
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistUser = pl.user
                                playlistReleaseDate = pl.releaseDate
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isQobuzPlaylist) {
                            val pl = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository.getPlaylist(context, cleanIdStr)
                            if (pl != null) {
                                isAlbum = false
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistDescription = pl.description
                                playlistUser = pl.user
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isQobuzArtist) {
                            val pl = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository.getArtist(context, cleanIdStr)
                            if (pl != null) {
                                isAlbum = false
                                playlistTitle = pl.title.orEmpty()
                                playlistCover = pl.artworkUrl
                                playlistUser = pl.user
                                playlistPermalinkUrl = pl.permalinkUrl
                                playlistUrn = pl.urn
                                newTracks.addAll(pl.tracks ?: emptyList())
                            }
                        } else if (isDeezerRadio) {
                            // Deezer's own artist radio: a station around the artist,
                            // not just their greatest hits.
                            val radioTracks = com.alananasss.kittytune.data.deezer.DeezerSearchRepository
                                .getRadio(cleanIdStr)
                            if (radioTracks.isNotEmpty()) {
                                val seed = radioTracks.first()
                                isAlbum = false
                                playlistTitle = context.getString(
                                    R.string.deezer_radio_title,
                                    seed.user?.username ?: "Deezer"
                                )
                                playlistCover = seed.artworkUrl
                                defaultIcon = Icons.Rounded.Radio
                                playlistUser = User(
                                    id = seed.user?.id ?: 0L,
                                    username = seed.user?.username ?: "Deezer",
                                    avatarUrl = seed.user?.avatarUrl
                                )
                                playlistPermalinkUrl = "https://www.deezer.com/artist/$cleanIdStr"
                                playlistUrn = "deezer_radio:$cleanIdStr"
                                newTracks.addAll(radioTracks)
                            }
                        } else if (isQobuzRadio) {
                            // A Qobuz station around the seed: the album it belongs
                            // to, the artist, and the artists Qobuz lists as similar.
                            val radioTracks = com.alananasss.kittytune.data.qobuz.QobuzSearchRepository
                                .getRadio(cleanIdStr)
                            if (radioTracks.isNotEmpty()) {
                                val seed = radioTracks.first()
                                isAlbum = false
                                playlistTitle = context.getString(R.string.qobuz_radio_title, seed.title ?: "")
                                playlistCover = seed.artworkUrl
                                defaultIcon = Icons.Rounded.Radio
                                playlistUser = User(
                                    id = seed.user?.id ?: 0L,
                                    username = seed.user?.username ?: "Qobuz",
                                    avatarUrl = seed.user?.avatarUrl
                                )
                                playlistPermalinkUrl = "https://open.qobuz.com/track/$cleanIdStr"
                                playlistUrn = "qobuz_radio:$cleanIdStr"
                                newTracks.addAll(radioTracks)
                            }
                        } else if (isSpotifyRadio) {
                            val isArtistStation = playlistId.startsWith("station_artist:") || playlistId.startsWith("spotify:artist:") || playlistId.startsWith("spotify_artist:")
                            var radioPlaylist = spotifyId?.let {
                                com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadio(it, isArtist = isArtistStation)
                            }
                            if (radioPlaylist == null) {
                                radioPlaylist = spotifyId?.let {
                                    com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadio(it, isArtist = !isArtistStation)
                                }
                            }
                            if (radioPlaylist != null) {
                                isAlbum = false
                                playlistTitle = radioPlaylist.name
                                playlistCover = radioPlaylist.artworkUrl
                                defaultIcon = Icons.Rounded.Radio
                                playlistUser = User(
                                    id = kotlin.math.abs("Spotify".hashCode().toLong()),
                                    username = radioPlaylist.ownerName ?: "Spotify",
                                    avatarUrl = radioPlaylist.artworkUrl
                                )
                                playlistPermalinkUrl = "https://open.spotify.com/playlist/${radioPlaylist.id}"
                                playlistUrn = "spotify:playlist:${radioPlaylist.id}"
                                newTracks.addAll(radioPlaylist.tracks.map { it.toTrack() })
                            } else {
                                val seedTrack = spotifyId?.let {
                                com.alananasss.kittytune.data.spotify.SpotifyRepository.getTrack(it)
                            }
                                val radioTitle = if (seedTrack != null) context.getString(R.string.spotify_radio_title, seedTrack.name) else "Spotify Radio"
                                playlistTitle = radioTitle
                                playlistCover = seedTrack?.artworkUrl
                                defaultIcon = Icons.Rounded.Radio
                                isAlbum = false
                                if (seedTrack != null) {
                                    playlistUser = User(
                                        id = kotlin.math.abs(seedTrack.artists.firstOrNull()?.id?.hashCode()?.toLong() ?: 0L),
                                        username = seedTrack.artistName,
                                        avatarUrl = seedTrack.artists.firstOrNull()?.avatarUrl ?: seedTrack.artworkUrl,
                                        urn = seedTrack.artists.firstOrNull()?.id?.let { "spotify:artist:$it" }
                                    )
                                    playlistPermalinkUrl = seedTrack.shareUrl
                                    val radioList = spotifyId?.let {
                                        com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadioTracks(it)
                                    }.orEmpty()
                                    newTracks.addAll(radioList.map { it.toTrack() })
                                } else {
                                    val artist = spotifyId?.let {
                                        com.alananasss.kittytune.data.spotify.SpotifyRepository.getArtist(it)
                                    }
                                    if (artist != null) {
                                        playlistTitle = "${artist.name} Radio"
                                        playlistCover = artist.avatarUrl ?: artist.headerImageUrl
                                        playlistUser = User(
                                            id = kotlin.math.abs(artist.id.hashCode().toLong()),
                                            username = artist.name,
                                            avatarUrl = artist.avatarUrl,
                                            urn = "spotify:artist:${artist.id}"
                                        )
                                        newTracks.addAll(artist.topTracks.map { it.toTrack() })
                                    }
                                }
                            }
                        } else if (isSystemPlaylistRoute || currentIdLong > 0L) {
                            val isArtistStation = playlistId.startsWith("station_artist:")
                            val isTrackStation = playlistId.startsWith("station:")

                            val likedTrack = if (isTrackStation) {
                                LikeRepository.likedTracks.value.find { it.id == currentIdLong }
                            } else null
                            val historyItem = if (isTrackStation && likedTrack == null) {
                                try {
                                    HistoryRepository.getHistory().first().find { it.numericId == currentIdLong }
                                } catch (e: Exception) { null }
                            } else null

                            val isLocalSpotifyTrack = (likedTrack != null && (likedTrack.source == "spotify" || likedTrack.permalinkUrl?.contains("spotify") == true)) ||
                                    (historyItem != null && (historyItem.source == "spotify" || historyItem.originalUrl?.contains("spotify") == true))

                            val localFallback = if (stableId != 0L) db.getPlaylist(stableId) else null
                            val isLocalSpotify = localFallback?.permalinkUrl?.contains("spotify") == true

                            if (isLocalSpotifyTrack) {
                                val spotifyTrackId = likedTrack?.let {
                                    it.permalinkUrl?.substringAfter("track/")?.substringBefore("?")?.substringBefore("/")
                                        ?: it.permalink?.removePrefix("spotify:track:")
                                        ?: it.user?.urn?.removePrefix("spotify:track:")
                                } ?: historyItem?.let {
                                    it.originalUrl?.substringAfter("track/")?.substringBefore("?")?.substringBefore("/")
                                } ?: cleanIdStr

                                var radioPlaylist = com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadio(spotifyTrackId, isArtist = false)
                                if (radioPlaylist == null) {
                                    radioPlaylist = com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadio(spotifyTrackId, isArtist = true)
                                }
                                if (radioPlaylist != null) {
                                    isAlbum = false
                                    playlistTitle = radioPlaylist.name
                                    playlistCover = radioPlaylist.artworkUrl
                                    defaultIcon = Icons.Rounded.Radio
                                    playlistUser = User(
                                        id = kotlin.math.abs("Spotify".hashCode().toLong()),
                                        username = radioPlaylist.ownerName ?: "Spotify",
                                        avatarUrl = radioPlaylist.artworkUrl
                                    )
                                    playlistPermalinkUrl = "https://open.spotify.com/playlist/${radioPlaylist.id}"
                                    playlistUrn = "spotify:playlist:${radioPlaylist.id}"
                                    newTracks.addAll(radioPlaylist.tracks.map { it.toTrack() })
                                } else {
                                    val seedTrack = com.alananasss.kittytune.data.spotify.SpotifyRepository.getTrack(spotifyTrackId)
                                    val fallbackTitle = likedTrack?.title ?: historyItem?.title ?: "Spotify Radio"
                                    val radioTitle = if (seedTrack != null) context.getString(R.string.spotify_radio_title, seedTrack.name) else context.getString(R.string.spotify_radio_title, fallbackTitle)
                                    playlistTitle = radioTitle
                                    playlistCover = seedTrack?.artworkUrl ?: likedTrack?.artworkUrl ?: historyItem?.imageUrl
                                    defaultIcon = Icons.Rounded.Radio
                                    isAlbum = false
                                    if (seedTrack != null) {
                                        playlistUser = User(
                                            id = kotlin.math.abs(seedTrack.artists.firstOrNull()?.id?.hashCode()?.toLong() ?: 0L),
                                            username = seedTrack.artistName,
                                            avatarUrl = seedTrack.artists.firstOrNull()?.avatarUrl ?: seedTrack.artworkUrl,
                                            urn = seedTrack.artists.firstOrNull()?.id?.let { "spotify:artist:$it" }
                                        )
                                        playlistPermalinkUrl = seedTrack.shareUrl
                                        val radioList = com.alananasss.kittytune.data.spotify.SpotifyRepository.getRadioTracks(spotifyTrackId)
                                        newTracks.addAll(radioList.map { it.toTrack() })
                                    } else if (likedTrack != null) {
                                        newTracks.add(likedTrack)
                                    }
                                }
                            } else if (localFallback != null) {
                                playlistTitle = localFallback.title
                                playlistCover = localFallback.localCoverPath ?: localFallback.artworkUrl
                                playlistUser = User(0, localFallback.artist, null)
                                isUserCreated = localFallback.isUserCreated || localFallback.id < 0
                                isAlbum = localFallback.isAlbum
                                playlistPermalinkUrl = localFallback.permalinkUrl
                            }
                            isLocalPlaylist = isDownloadedView

                            if (isLocalSpotify && localFallback != null) {
                                val permalink = localFallback.permalinkUrl ?: ""
                                val isAlbumType = localFallback.isAlbum || permalink.contains("/album/")
                                val spotifyId = com.alananasss.kittytune.data.spotify.SpotifyRepository
                                    .extractId(permalink)

                                if (isAlbumType) {
                                    val album = com.alananasss.kittytune.data.spotify.SpotifyRepository.getAlbum(spotifyId)
                                    if (album != null) {
                                        isAlbum = true
                                        playlistTitle = album.name
                                        playlistCover = album.artworkUrl
                                        playlistUser = User(
                                            id = kotlin.math.abs(album.artists.firstOrNull()?.id?.hashCode()?.toLong() ?: 0L),
                                            username = album.artistName,
                                            avatarUrl = album.artists.firstOrNull()?.avatarUrl ?: album.artworkUrl,
                                            urn = album.artists.firstOrNull()?.id?.let { "spotify:artist:$it" }
                                        )
                                        playlistReleaseDate = album.releaseDate
                                        playlistPermalinkUrl = "https://open.spotify.com/album/${album.id}"
                                        playlistUrn = "spotify:album:${album.id}"
                                        newTracks.addAll(album.tracks.map { it.toTrack() })
                                    }
                                } else {
                                    val pl = com.alananasss.kittytune.data.spotify.SpotifyRepository.getPlaylist(spotifyId)
                                    if (pl != null) {
                                        isAlbum = false
                                        playlistTitle = pl.name
                                        playlistCover = pl.artworkUrl
                                        playlistDescription = pl.description
                                        playlistUser = User(
                                            id = 0L,
                                            username = pl.ownerName ?: "Spotify",
                                            avatarUrl = pl.artworkUrl
                                        )
                                        playlistPermalinkUrl = "https://open.spotify.com/playlist/${pl.id}"
                                        playlistUrn = "spotify:playlist:${pl.id}"
                                        newTracks.addAll(pl.tracks.map { it.toTrack() })
                                    }
                                }
                            } else {
                                val playlistObj = when {
                                    isSystemPlaylistRoute -> {
                                        if (cleanIdStr.startsWith("http://") || cleanIdStr.startsWith("https://") || cleanIdStr.contains("soundcloud.com") || cleanIdStr.contains("discover/sets/")) {
                                            val fullUrl = if (cleanIdStr.startsWith("http")) cleanIdStr else "https://soundcloud.com/${cleanIdStr.removePrefix("/")}"
                                            api.resolvePlaylist(fullUrl)
                                        } else {
                                            api.getSystemPlaylist(cleanIdStr)
                                        }
                                    }
                                    isArtistStation -> api.getArtistStation(currentIdLong)
                                    isTrackStation -> api.getTrackStation(currentIdLong)
                                    localFallback?.permalinkUrl != null && (localFallback.permalinkUrl.contains("discover/sets") || localFallback.permalinkUrl.contains("your-playback") || localFallback.permalinkUrl.contains("system-playlists")) -> {
                                        val fallbackUrl = localFallback.permalinkUrl!!
                                        val fullUrl = if (fallbackUrl.startsWith("http")) fallbackUrl else "https://soundcloud.com/${fallbackUrl.removePrefix("/")}"
                                        api.resolvePlaylist(fullUrl)
                                    }
                                    else -> {
                                        try {
                                            api.getPlaylist(currentIdLong)
                                        } catch (e: Exception) {
                                            if (e is retrofit2.HttpException && e.code() == 404) {
                                                var resolvedPl: Playlist? = null
                                                val cachedUserId = com.alananasss.kittytune.data.local.PlayerPreferences(context).getCachedUserId().takeIf { it != 0L }
                                                    ?: playerViewModel.currentUserId.takeIf { it != 0L }
                                                    ?: try { api.getMe().id } catch (_: Exception) { 0L }

                                                if (cachedUserId != 0L) {
                                                    for (yr in listOf(2027, 2026, 2025, 2024, 2023, 2022, 2021, 2020)) {
                                                        val urn = "soundcloud:system-playlists:your-playback:$cachedUserId:$yr"
                                                        val sysUrn = "system_playlist:$urn"
                                                        val h1 = kotlin.math.abs(urn.hashCode().toLong())
                                                        val h2 = kotlin.math.abs(sysUrn.hashCode().toLong())
                                                        val h3 = com.alananasss.kittytune.ui.yearlyplayback.YearlyPlaybackViewModel.extractPlaylistId(urn)
                                                        if (currentIdLong == h1 || currentIdLong == h2 || currentIdLong == h3) {
                                                            resolvedPl = try { api.getSystemPlaylist(urn) } catch (_: Exception) { null }
                                                            if (resolvedPl != null) break
                                                        }
                                                    }
                                                }

                                                if (resolvedPl == null) {
                                                    val hist = try {
                                                        db.getHistoryItemById(currentIdLong, "playlist:$currentIdLong")
                                                            ?: db.getHistoryItemById(currentIdLong, playlistId)
                                                    } catch (_: Exception) { null }

                                                    if (hist != null) {
                                                        val origUrl = hist.originalUrl
                                                        if (!origUrl.isNullOrBlank()) {
                                                            val urnToFetch = when {
                                                                origUrl.startsWith("system_playlist:") -> origUrl.removePrefix("system_playlist:")
                                                                origUrl.startsWith("soundcloud:system-playlists:") -> origUrl
                                                                else -> null
                                                            }
                                                            if (urnToFetch != null) {
                                                                resolvedPl = try { api.getSystemPlaylist(urnToFetch) } catch (_: Exception) { null }
                                                            }
                                                        }
                                                        if (resolvedPl == null && (hist.title.contains("Playback", ignoreCase = true) || hist.title.contains("Wrapped", ignoreCase = true))) {
                                                            val yr = Regex("\\b(20\\d\\d)\\b").find(hist.title)?.value
                                                            if (yr != null && cachedUserId != 0L) {
                                                                val urn = "soundcloud:system-playlists:your-playback:$cachedUserId:$yr"
                                                                resolvedPl = try { api.getSystemPlaylist(urn) } catch (_: Exception) { null }
                                                            }
                                                        }
                                                    }
                                                }

                                                if (resolvedPl != null) {
                                                    val plObj = resolvedPl
                                                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                                        try {
                                                            db.deleteHistoryItem("playlist:$currentIdLong")
                                                            db.deleteHistoryItem(playlistId)
                                                            HistoryRepository.addToHistory(plObj)
                                                        } catch (_: Exception) {}
                                                    }
                                                    resolvedPl
                                                } else if (!localFallback?.permalinkUrl.isNullOrEmpty()) {
                                                    val fallbackUrl = localFallback!!.permalinkUrl!!
                                                    val fullUrl = if (fallbackUrl.startsWith("http")) fallbackUrl else "https://soundcloud.com/${fallbackUrl.removePrefix("/")}"
                                                    api.resolvePlaylist(fullUrl)
                                                } else {
                                                    throw e
                                                }
                                            } else {
                                                throw e
                                            }
                                        }
                                    }
                                }
                                isAlbum = playlistObj.isRealAlbum

                            val rawFetchedTitle = playlistObj.title.takeIf { !it.isNullOrBlank() } ?: playlistTitle
                            playlistTitle = com.alananasss.kittytune.utils.SoundCloudLocalizationUtils.localizeSectionTitle(rawFetchedTitle, context)
                            val localCoverFile = java.io.File(context.filesDir, "playlist_cover_${stableId}.jpg")
                            val localInDb = db.getPlaylist(stableId)
                            val hasLocalCover = localInDb?.localCoverPath?.isNotEmpty() == true || localCoverFile.exists()
                            if (!playlistObj.artworkUrl.isNullOrBlank()) {
                                playlistCover = playlistObj.fullResArtwork
                            } else if (hasLocalCover) {
                                playlistCover = localInDb?.localCoverPath ?: localCoverFile.absolutePath
                            } else if (!playlistObj.fullResArtwork.isNullOrBlank()) {
                                playlistCover = playlistObj.fullResArtwork
                            }
                            if (localInDb != null) {
                                db.updatePlaylist(
                                    localInDb.copy(
                                        title = playlistObj.title.takeIf { !it.isNullOrBlank() } ?: localInDb.title,
                                        artworkUrl = playlistObj.fullResArtwork.takeIf { !it.isNullOrBlank() } ?: localInDb.artworkUrl,
                                        artist = playlistObj.user?.username ?: localInDb.artist,
                                        permalinkUrl = playlistObj.permalinkUrl ?: localInDb.permalinkUrl,
                                        trackCount = playlistObj.trackCount ?: playlistObj.tracks?.size ?: localInDb.trackCount,
                                        isAlbum = playlistObj.isRealAlbum
                                    )
                                )
                            }
                            playlistUser = playlistObj.user ?: playlistUser
                            val currentUserId = playerViewModel.currentUserId.takeIf { it != 0L }
                                ?: com.alananasss.kittytune.data.local.PlayerPreferences(context).getCachedUserId().takeIf { it != 0L }
                            val currentUsername = playerViewModel.currentUser?.username
                                ?: com.alananasss.kittytune.data.local.PlayerPreferences(context).getCachedUsername()
                            val isOwnedByCurrentAccount = (playlistUser?.id != null && playlistUser?.id != 0L && playlistUser?.id == currentUserId) ||
                                (!currentUsername.isNullOrBlank() && (playlistObj.user?.username?.equals(currentUsername, ignoreCase = true) == true || playlistUser?.username?.equals(currentUsername, ignoreCase = true) == true))
                            isUserCreated = currentIdLong < 0 || (localInDb?.isUserCreated == true) || isOwnedByCurrentAccount
                            playlistSharing = playlistObj.sharing
                            playlistDescription = com.alananasss.kittytune.utils.SoundCloudLocalizationUtils.localizeSectionSubtitle(playlistObj.description, context)
                            playlistTagList = playlistObj.tagList
                            playlistGenre = playlistObj.genre
                            playlistSetType = playlistObj.setType
                            playlistReleaseDate = playlistObj.releaseDate
                            playlistPermalink = playlistObj.permalink
                            playlistUrn = playlistObj.urn
                            playlistDurationMs = playlistObj.durationMs
                            playlistPermalinkUrl = playlistObj.permalinkUrl.takeIf { !it.isNullOrBlank() }
                                ?: if (isArtistStation) "https://soundcloud.com/discover/sets/artist-stations:$currentIdLong"
                                else if (isTrackStation) "https://soundcloud.com/discover/sets/track-stations:$currentIdLong"
                                else null
                            val rawTracks = playlistObj.tracks ?: emptyList()
                            val incompleteIds =
                                rawTracks.filter { it.title.isNullOrBlank() || it.user == null }.map { it.id }

                            if (incompleteIds.isNotEmpty()) {
                                val fetchedTracksMap = mutableMapOf<Long, Track>()
                                val chunks = incompleteIds.chunked(50)
                                chunks.forEach { batchIds ->
                                    try {
                                        val fetched = api.getTracksByIds(batchIds.joinToString(","))
                                        fetched.forEach { fetchedTracksMap[it.id] = it }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                val hydratedList = rawTracks.map { track ->
                                    if (track.title.isNullOrBlank() || track.user == null) fetchedTracksMap[track.id]
                                        ?: track else track
                                }
                                newTracks.addAll(hydratedList)
                            } else {
                                newTracks.addAll(rawTracks)
                            }
                        }
                    }
                }
            }
        }

            if (stableId != 0L && playlistId != "likes" && playlistId != "downloads" && playlistId != "local_files" && !playlistId.startsWith("vk_playlist:")) {
                val localDbTracks = db.getTracksForPlaylistSync(stableId)
                if (localDbTracks.isNotEmpty()) {
                    val localMappedTracks = localDbTracks.map { local -> local.toTrack() }
                    if (newTracks.isEmpty()) {
                        newTracks.addAll(localMappedTracks)
                    } else {
                        for (lt in localMappedTracks) {
                            if (newTracks.none { it.id == lt.id }) {
                                newTracks.add(lt)
                            }
                        }
                        // Manual track order in someone else's (non-user-created) playlist lives only
                        // in local refs (addedAt) — it must not be synced to the server.
                        // Previously every fresh network request wiped the manual reorder
                        // with the server order; now the user's order
                        // is restored on top of the network list. For imported
                        // spotify/deezer/tidal/qobuz playlists this also works:
                        // the branch used to be cut off by the umbrella isSpecialSystemScreen.
                        if (!isUserCreated && !isDownloadedView &&
                            (!isSpecialSystemScreen || isImportedReorderablePlaylist) &&
                            newTracks.size > 1
                        ) {
                            val rank = localDbTracks.mapIndexed { index, local -> local.id to index }.toMap()
                            val knownCount = newTracks.count { rank.containsKey(it.id) }
                            if (rank.isNotEmpty() && knownCount > 1) {
                                val known = newTracks.filter { rank.containsKey(it.id) }
                                    .sortedBy { rank[it.id]!! }
                                val unknown = newTracks.filter { !rank.containsKey(it.id) }
                                newTracks.clear()
                                newTracks.addAll(known + unknown)
                            }
                        }
                    }
                }
            }

            if (playlistId != "likes") {
                tracks.clear()
                tracks.addAll(newTracks)
            }

            if (playlistId == "downloads") {
                downloadedPlaylists.clear()
                downloadedPlaylists.addAll(newDownloadedPlaylists)
            }

        } catch (e: Exception) {
            e.printStackTrace()
            if (e is retrofit2.HttpException && e.code() == 404) {
                isNotFound = true
                if (playlistId.contains("your-playback")) {
                    val yr = playlistId.substringAfterLast(":", "")
                    if (yr.isNotEmpty()) {
                        playlistTitle = "SoundCloud Playback $yr"
                    }
                }
            }
        } finally {
            isLoading = false
        }
    }

    if (playlistId.startsWith("yt_radio:") && !hasSavedRadio) {
        playlistTitle = youtubeRadioViewModel.playlistTitle
        playlistCover = youtubeRadioViewModel.playlistCover
        playlistUser = youtubeRadioViewModel.playlistUser
        tracks.clear()
        tracks.addAll(youtubeRadioViewModel.tracks)
        isLoading = youtubeRadioViewModel.isLoading
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(if (isUserCreated) R.string.dialog_delete_playlist_title else R.string.dialog_delete_playlist_from_lib_title)) },
            text = { Text(stringResource(R.string.dialog_delete_playlist_msg)) },
            confirmButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    if (stableId != 0L) {
                        DownloadManager.deletePlaylist(
                            playlistId = stableId,
                            forceUserCreated = isUserCreated,
                            forcePermalink = playlistPermalinkUrl
                        )
                    }
                    showDeleteDialog = false
                    onBackClick()
                }) {
                    Text(stringResource(R.string.btn_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showRemoveDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDownloadDialog = false },
            title = { Text(stringResource(R.string.dialog_remove_download_title)) },
            text = { Text(stringResource(R.string.dialog_remove_download_msg)) },
            confirmButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    if (playlistId == "likes") {
                        DownloadManager.removeDownloads(tracksToDisplay)
                    } else if (stableId != 0L) {
                        DownloadManager.removePlaylistDownloads(stableId, tracksToDisplay)
                    }

                    showRemoveDownloadDialog = false
                    if (isDownloadedView) {
                        onBackClick()
                    }

                }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    showRemoveDownloadDialog = false
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showRenameDialog) {
        EditPlaylistScreen(
            initialTitle = playlistTitle,
            initialDescription = playlistDescription,
            initialSharing = playlistSharing,
            initialTagList = playlistTagList,
            initialGenre = playlistGenre,
            initialSetType = playlistSetType,
            initialReleaseDate = playlistReleaseDate,
            initialPermalink = playlistPermalink,
            playlistUser = playlistUser,
            onDismissRequest = { showRenameDialog = false },
            onSave = { newTitle, newDesc, newSharing, newTags, newGenre, newSetType, newReleaseDate, newPermalink ->
                if (currentIdLong != 0L) {
                    DownloadManager.editPlaylistMetadata(
                        playlistId = currentIdLong,
                        newTitle = newTitle,
                        newDescription = newDesc,
                        newSharing = newSharing,
                        newTagList = newTags,
                        newPermalink = newPermalink,
                        newGenre = newGenre,
                        newSetType = newSetType,
                        newReleaseDate = newReleaseDate,
                        syncToCloud = isUserCreated && !isDownloadedView && currentIdLong > 0
                    )
                    playlistTitle = newTitle
                    playlistDescription = newDesc
                    playlistSharing = newSharing
                    playlistTagList = newTags
                    playlistGenre = newGenre
                    playlistSetType = newSetType
                    playlistReleaseDate = newReleaseDate
                    playlistPermalink = newPermalink
                }
                showRenameDialog = false
            }
        )
    }

    val playbackContext = remember(playlistId, playlistTitle, playlistCover, playlistUser, isAlbum, isArtistView, playlistUrn) {
        val creatorName = playlistUser?.username
        val isVerified = playlistUser?.verified == true
        val effectiveNavId = when {
            playlistUrn?.startsWith("soundcloud:system-playlists:") == true -> "system_playlist:$playlistUrn"
            isSystemPlaylistRoute -> if (playlistId.startsWith("system_playlist:")) playlistId else "system_playlist:$cleanIdStr"
            else -> playlistId
        }

        when {
            playlistId == "likes" -> PlaybackContext(
                context.getString(R.string.context_playlist, context.getString(R.string.lib_liked_tracks)),
                "likes",
                playlistCover,
                artistName = null
            )

            playlistId == "downloads" -> PlaybackContext(
                context.getString(R.string.context_playlist, context.getString(R.string.lib_downloads)),
                "downloads",
                playlistCover,
                artistName = null
            )

            isArtistView -> PlaybackContext(
                context.getString(R.string.generic_artist) + " • " + playlistTitle,
                effectiveNavId,
                playlistCover,
                artistName = playlistTitle,
                isVerified = isVerified
            )

            playlistId.startsWith("station") || playlistId.startsWith("yt_radio:") -> PlaybackContext(
                context.getString(R.string.context_station, playlistTitle),
                effectiveNavId,
                playlistCover,
                artistName = null,
                isVerified = isVerified
            )

            isAlbum -> PlaybackContext(
                context.getString(R.string.context_album, playlistTitle),
                effectiveNavId,
                playlistCover,
                artistName = creatorName,
                isVerified = isVerified
            )

            else -> PlaybackContext(
                context.getString(R.string.context_playlist, playlistTitle),
                effectiveNavId,
                playlistCover,
                artistName = creatorName,
                isVerified = isVerified
            )
        }
    }

    if (showPlaylistSortSheet) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showPlaylistSortSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                val options = listOf(
                    TrackSortBy.RECENTLY_ADDED to stringResource(R.string.sort_recently_added),
                    TrackSortBy.FIRST_ADDED to stringResource(R.string.sort_first_added),
                    TrackSortBy.TITLE_AZ to stringResource(R.string.sort_title_az),
                    TrackSortBy.ARTIST_AZ to stringResource(R.string.sort_artist_az)
                )

                options.forEach { (sortType, label) ->
                    val isSelected = playlistSortBy == sortType
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performAppHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                playlistSortBy = sortType
                                showPlaylistSortSheet = false
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                        if (isSelected) {
                            Spacer(modifier = Modifier.weight(1f))
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                // Mix mode adds the harmonic smart sort on top of the plain orders.
                if (isMixMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                view.performAppHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                showPlaylistSortSheet = false
                                applySmartMixSort()
                            }
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.mix_smart_sort),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            Icons.AutoMirrored.Rounded.Sort,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background

    val windowSizeInfo = com.alananasss.kittytune.ui.common.rememberWindowSizeInfo()

    Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
        Scaffold(containerColor = Color.Transparent) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    if (playlistId == "likes") {
                        // The colour of the liked songs, from the top of their page: the covers' colours mixed and
                        // drawn toward the theme, drifting and shimmering while the likes are playing, fading out
                        // when they are not (desktop PR #77, mobile UI — the flat gradient stood here before).
                        Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                            LikesAura(
                                likes = rawTracks,
                                playerViewModel = playerViewModel,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            backgroundColor.copy(alpha = 0.3f),
                                            backgroundColor.copy(alpha = 0.8f),
                                            backgroundColor
                                        )
                                    )
                                )
                            )
                        }
                    } else if (!playlistCover.isNullOrEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                            AsyncImage(
                                model = effectiveCoverModel,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.6f),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier.fillMaxSize().background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            backgroundColor.copy(alpha = 0.3f),
                                            backgroundColor.copy(alpha = 0.8f),
                                            backgroundColor
                                        )
                                    )
                                )
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(300.dp).background(
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        backgroundColor
                                    )
                                )
                            )
                        )
                    }

                    Box(
                        modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 820.dp)
                            .fillMaxSize() else Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 180.dp)
                        ) {
                            item {
                                Spacer(modifier = Modifier.statusBarsPadding().height(90.dp))
                                Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                                    Box {
                                        Card(
                                            shape = if (isArtistView) CircleShape else RoundedCornerShape(12.dp),
                                            elevation = CardDefaults.cardElevation(12.dp),
                                            modifier = Modifier.size(160.dp)
                                        ) {
                                            if (!playlistCover.isNullOrEmpty()) AsyncImage(
                                                model = effectiveCoverModel,
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize().viewableCover(playlistCover),
                                                contentScale = ContentScale.Crop
                                            )
                                            else if (defaultIcon != null) Box(
                                                modifier = Modifier.fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    defaultIcon!!,
                                                    null,
                                                    modifier = Modifier.size(64.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            else Box(
                                                Modifier.fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    if (isArtistView) Icons.Rounded.Person else Icons.Default.MusicNote,
                                                    null,
                                                    modifier = Modifier.size(64.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                        if (isUserCreated) {
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                                                modifier = Modifier.align(Alignment.BottomEnd)
                                                    .offset(x = 8.dp, y = 8.dp).hapticClickable {
                                                    photoPickerLauncher.launch(
                                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                    )
                                                }) {
                                                Icon(
                                                    Icons.Outlined.Image,
                                                    stringResource(R.string.storage_change_btn),
                                                    modifier = Modifier.padding(8.dp),
                                                    tint = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(20.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = playlistTitle,
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onBackground,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (playlistSharing == "private" || playlistSharing == "secret") {
                                            Spacer(Modifier.width(8.dp))
                                            Icon(
                                                imageVector = androidx.compose.material.icons.Icons.Rounded.Lock,
                                                contentDescription = "Private",
                                                modifier = Modifier.size(24.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    // An album sung mostly by one artist is credited to that artist, not the account
                                    // that posted it: the tracks carry the real credit (desktop PR #77).
                                    val headerArtistName = remember(rawTracks.size, isAlbum, playlistUser) {
                                        if (isAlbum && playlistUser?.urn?.startsWith("spotify") != true) {
                                            albumCreditFor(rawTracks)
                                        } else {
                                            playlistUser?.username
                                        }
                                    }
                                    if (isArtistView) {
                                        val providerBadge = when {
                                            isQobuzArtist -> stringResource(R.string.generic_artist) + " • Qobuz"
                                            isDeezerArtist -> stringResource(R.string.generic_artist) + " • Deezer"
                                            isTidalArtist -> stringResource(R.string.generic_artist) + " • TIDAL"
                                            else -> stringResource(R.string.generic_artist)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                            ) {
                                                Text(
                                                    text = providerBadge,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    } else if (playlistUser != null && playlistCreatorNavId != null) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .hapticClickable {
                                                    val creator = playlistUser!!
                                                    if (playlistArtists.count { it.id.isNotBlank() } > 1) {
                                                        playerViewModel.navigateToArtistChoice(
                                                            playlistArtists,
                                                            creator.id
                                                        )
                                                    } else {
                                                        onNavigate(playlistCreatorNavId!!)
                                                    }
                                                }
                                                .padding(vertical = 4.dp, horizontal = 2.dp)
                                        ) {
                                            Text(
                                                text = stringResource(
                                                    R.string.playlist_by_user,
                                                    headerArtistName ?: ""
                                                ),
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            if (playlistUser?.verified == true) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(
                                                    Icons.Rounded.Verified,
                                                    null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    } else if (playlistUser?.username != null) {
                                        Text(
                                            text = stringResource(
                                                R.string.playlist_by_user,
                                                headerArtistName ?: ""
                                            ),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onBackground
                                                .copy(alpha = 0.7f)
                                        )
                                    }
                                    Spacer(Modifier.height(8.dp))

                                    val durationText = if (showTotalDuration) formatPlaylistTotalDuration(context.resources, totalDurationMs) else ""

                                    val trackCountText = when {
                                        isArtistView -> {
                                            val count = tracksToDisplay.size
                                            if (count == 0 && playlistSearchQuery.isNotEmpty()) {
                                                stringResource(R.string.no_tracks_found_filter)
                                            } else {
                                                val base = stringResource(R.string.new_releases_popular_tracks) + " • " + stringResource(R.string.playlist_num_tracks, count)
                                                if (durationText.isNotEmpty()) "$base • $durationText" else base
                                            }
                                        }
                                        playlistId.startsWith("yt_radio:") -> stringResource(R.string.radio) + " • YouTube"
                                        isLoading && playlistId != "likes" -> "..."
                                        else -> {
                                            val count = tracksToDisplay.size
                                            if (count == 0 && playlistSearchQuery.isNotEmpty()) {
                                                stringResource(R.string.no_tracks_found_filter)
                                            } else {
                                                // Order matters: the count and the TOTAL LISTEN
                                            // TIME come first, the release date trails last —
                                            // the date must never displace the duration. On
                                            // albums the duration shows even when the general
                                            // playlist-duration switch is off.
                                            val albumDurationText = if (isAlbum && durationText.isEmpty()) {
                                                formatPlaylistTotalDuration(context.resources, tracksToDisplay.sumOf { it.durationMs ?: 0L })
                                            } else durationText
                                            var base = stringResource(R.string.playlist_num_tracks, count)
                                            if (albumDurationText.isNotEmpty()) base = "$base • $albumDurationText"
                                            if (isAlbum) {
                                                val releaseText = when (remember { prefs.getAlbumReleaseDateDisplay() }) {
                                                    com.alananasss.kittytune.data.local.ReleaseDateDisplay.OFF -> null
                                                    com.alananasss.kittytune.data.local.ReleaseDateDisplay.FULL ->
                                                        playlistReleaseDate?.let { com.alananasss.kittytune.ui.profile.ReleaseDate.text(it) }
                                                    com.alananasss.kittytune.data.local.ReleaseDateDisplay.SHORT ->
                                                        playlistReleaseDate?.let { com.alananasss.kittytune.ui.profile.ReleaseDate.shortText(it) }
                                                }
                                                if (!releaseText.isNullOrEmpty()) base = "$base • $releaseText"
                                            }
                                            base
                                            }
                                        }
                                    }

                                    if (trackCountText.isNotEmpty()) {
                                        Text(
                                            text = trackCountText,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }

                                    Spacer(Modifier.height(16.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        if (((isLocalPlaylist && !isDownloadedView) || isUserCreated) && !isYoutubeRadio) {
                                            IconButton(
                                                onClick = hapticAction(HapticKind.UI) { newPlaylistName = playlistTitle; showRenameDialog = true },
                                                shapes = IconButtonDefaults.shapes()
                                            ) {
                                                Icon(
                                                    Icons.Outlined.Edit,
                                                    stringResource(R.string.profile_edit),
                                                    tint = MaterialTheme.colorScheme.onBackground
                                                )
                                            }
                                        }
                                        if (playlistId != "downloads" && playlistId != "likes" && playlistId != "local_files") {

                                            if (isUserCreated && !isDownloadedView) {
                                                IconButton(
                                                    onClick = hapticAction(HapticKind.UI) { showDeleteDialog = true },
                                                    shapes = IconButtonDefaults.shapes()
                                                ) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        stringResource(R.string.btn_delete),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            } else {
                                                val altSysId = playlistUrn?.let { com.alananasss.kittytune.ui.yearlyplayback.YearlyPlaybackViewModel.extractPlaylistId(it) }
                                                val isPlaylistLiked = likedPlaylistsRepo.contains(stableId) || likedPlaylistsRepo.contains(legacyStableId) || (altSysId != null && likedPlaylistsRepo.contains(altSysId))
                                                IconButton(
                                                    onClick = hapticAction(HapticKind.UI) {
                                                        val savedPermalink = radioRoutePermalink ?: playlistPermalinkUrl
                                                        if (!isPlaylistLiked) {
                                                            val targetPlaylist = Playlist(
                                                                id = stableId,
                                                                title = playlistTitle,
                                                                artworkUrl = playlistCover,
                                                                calculatedArtworkUrl = null,
                                                                trackCount = tracksToDisplay.size,
                                                                user = playlistUser ?: User(0, playlistUser?.username ?: "", null),
                                                                tracks = tracksToDisplay,
                                                                isAlbum = isAlbum,
                                                                // Save the route that can reopen this radio, not the
                                                                // transient remote playlist it resolved to.
                                                                permalinkUrl = savedPermalink ?: shareUrl,
                                                                urn = playlistUrn
                                                            )
                                                            DownloadManager.importPlaylistToLibrary(
                                                                playlist = targetPlaylist,
                                                                tracks = tracksToDisplay,
                                                                syncToCloud = radioRoutePermalink == null && !com.alananasss.kittytune.data.local.ExternalPlaylists.isExternalProvider(
                                                                    savedPermalink ?: shareUrl,
                                                                    playlistUrn
                                                                ) && !playlistId.startsWith("spotify") && !playlistId.startsWith("station_spotify"),
                                                                likePlaylist = true
                                                            )
                                                            if (legacyStableId != stableId && likedPlaylistsRepo.contains(legacyStableId)) {
                                                                LikeRepository.togglePlaylistLike(
                                                                    legacyStableId,
                                                                    false,
                                                                    savedPermalink,
                                                                    playlistUrn
                                                                )
                                                            }
                                                        } else {
                                                            LikeRepository.togglePlaylistLike(
                                                                stableId,
                                                                false,
                                                                savedPermalink,
                                                                playlistUrn
                                                            )
                                                            if (legacyStableId != stableId) {
                                                                LikeRepository.togglePlaylistLike(
                                                                    legacyStableId,
                                                                    false,
                                                                    savedPermalink,
                                                                    playlistUrn
                                                                )
                                                            }
                                                        }
                                                    },
                                                    shapes = IconButtonDefaults.shapes()
                                                ) {
                                                    if (isPlaylistLiked) {
                                                        Icon(
                                                            Icons.Rounded.Favorite,
                                                            stringResource(R.string.lib_liked_tracks),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    } else {
                                                        Icon(
                                                            Icons.Outlined.FavoriteBorder,
                                                            stringResource(R.string.menu_add_playlist),
                                                            tint = MaterialTheme.colorScheme.onBackground
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (!isYoutubeRadio && playlistId != "downloads" && tracksToDisplay.isNotEmpty()) {
                                            IconButton(
                                                onClick = hapticAction(HapticKind.UI) {
                                                    val targetBatchId =
                                                        if (playlistId == "likes") DownloadManager.LIKES_BATCH_ID else stableId

                                                    if (isPlaylistDownloading) {
                                                        DownloadManager.cancelBatch(targetBatchId)
                                                    } else if (isFullyDownloaded) {
                                                        showRemoveDownloadDialog = true
                                                    } else {
                                                        if (playlistId == "likes") {
                                                            DownloadManager.downloadBatch(
                                                                tracksToDisplay,
                                                                DownloadManager.LIKES_BATCH_ID
                                                            )
                                                        } else if (stableId != 0L) {
                                                            val fakePlaylist = Playlist(
                                                                id = stableId,
                                                                title = playlistTitle,
                                                                artworkUrl = playlistCover,
                                                                calculatedArtworkUrl = null,
                                                                trackCount = tracks.size,
                                                                user = playlistUser,
                                                                tracks = null,
                                                                permalinkUrl = playlistPermalinkUrl,
                                                                urn = playlistUrn,
                                                                isAlbum = isAlbum
                                                            )
                                                            DownloadManager.downloadPlaylist(
                                                                fakePlaylist,
                                                                tracks.toList()
                                                            )
                                                        }
                                                    }
                                                },
                                                shapes = IconButtonDefaults.shapes()
                                            ) {
                                                when {
                                                    isPlaylistDownloading -> {
                                                        Icon(Icons.Rounded.Close, stringResource(R.string.btn_cancel))
                                                    }

                                                    isFullyDownloaded -> {
                                                        Icon(
                                                            Icons.Rounded.DownloadDone,
                                                            stringResource(R.string.btn_downloaded),
                                                            tint = MaterialTheme.colorScheme.primary
                                                        )
                                                    }

                                                    else -> {
                                                        Icon(
                                                            Icons.Rounded.Download,
                                                            stringResource(R.string.btn_download),
                                                            tint = MaterialTheme.colorScheme.onBackground
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            item {
                                AnimatedVisibility(
                                    visible = isPlaylistDownloading && currentPlaylistProgress != null,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .padding(top = 8.dp, bottom = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        val percentage = ((currentPlaylistProgress ?: 0f) * 100).toInt()
                                        Text(
                                            text = stringResource(R.string.playlist_downloading_progress, percentage),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        LinearWavyProgressIndicator(
                                            progress = { currentPlaylistProgress ?: 0f },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            }

                            if (playlistId == "downloads" && downloadedPlaylists.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.lib_playlists),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (downloadedPlaylists.size > 4) {
                                            TextButton(onClick = hapticAction(HapticKind.UI) { onNavigate("downloaded_playlists") }) {
                                                Text(
                                                    stringResource(R.string.btn_see_all),
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                        }
                                    }
                                }

                                val displayList =
                                    if (downloadedPlaylists.size > 4) downloadedPlaylists.take(4) else downloadedPlaylists
                                val chunkedPlaylists = displayList.chunked(2)

                                items(chunkedPlaylists.size) { index ->
                                    val rowPlaylists = chunkedPlaylists[index]

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        for (playlist in rowPlaylists) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                PlaylistSquareCard(playlist = playlist) {
                                                    val id =
                                                        if (playlist.id < 0) "local_playlist:${playlist.id}" else playlist.id.toString()
                                                    onNavigate("downloaded_section:$id")
                                                }
                                            }
                                        }
                                        if (rowPlaylists.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }

                                if (tracksToDisplay.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.profile_tracks),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                                        )
                                    }
                                }
                            }
                            if (isLoading && playlistId != "likes") {
                                items(15) { TrackListItemShimmer() }
                            } else {
                                val isReallyEmpty = rawTracks.isEmpty()
                                        && (playlistId != "downloads" || downloadedPlaylists.isEmpty())
                                        && !isYoutubeRadio

                                if (isReallyEmpty) {
                                    item {
                                        EmptyPlaylistView(
                                            playlistId = playlistId,
                                            isUserCreated = isUserCreated,
                                            isNotFound = isNotFound,
                                            onBackClick = onBackClick
                                        )
                                    }
                                } else {
                                    if (isYoutubeRadio && rawTracks.isEmpty()) {
                                        item {
                                            Box(
                                                modifier = Modifier.fillMaxWidth().height(200.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                ContainedLoadingIndicator()
                                            }
                                        }
                                    }
                                    if (rawTracks.isNotEmpty()) {
                                        item(key = "search_and_buttons") {
                                            if (tracksToDisplay.isNotEmpty()) {
                                                // In Mix mode the playlist's play control transforms into the
                                                // Pioneer-CDJ-style round button — the green disc with the double
                                                // rim and the soft glow Spotify shows at the top of a mixed
                                                // playlist. Out of Mix mode the row stays the plain Play/Shuffle pair.
                                                AnimatedContent(
                                                    targetState = isMixMode,
                                                    transitionSpec = {
                                                        (fadeIn(animationSpec = tween(220)) +
                                                            scaleIn(initialScale = 0.86f, animationSpec = tween(220))) togetherWith
                                                            (fadeOut(animationSpec = tween(180)) +
                                                            scaleOut(targetScale = 0.92f, animationSpec = tween(180)))
                                                    },
                                                    label = "mix_play_transform",
                                                ) { mixOn ->
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                                    ) {
                                                        if (mixOn) {
                                                            MixCdjPlayButton(
                                                                onClick = hapticAction(HapticKind.UI) {
                                                                    playerViewModel.playPlaylist(
                                                                        tracksToDisplay,
                                                                        0,
                                                                        playbackContext
                                                                    )
                                                                },
                                                            )
                                                        }
                                                        if (!mixOn) {
                                                            Button(
                                                                onClick = hapticAction(HapticKind.UI) {
                                                                    playerViewModel.playPlaylist(
                                                                        tracksToDisplay,
                                                                        0,
                                                                        playbackContext
                                                                    )
                                                                },
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = MaterialTheme.colorScheme.primary,
                                                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                                                ),
                                                                shapes = ButtonDefaults.shapes(),
                                                                modifier = Modifier.weight(1f).height(50.dp)
                                                            ) {
                                                                Icon(Icons.Default.PlayArrow, null)
                                                                Spacer(Modifier.width(8.dp))
                                                                Text(
                                                                    stringResource(R.string.btn_play),
                                                                    style = MaterialTheme.typography.titleMedium,
                                                                    fontWeight = FontWeight.Bold,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                            }
                                                        }
                                                        FilledTonalButton(
                                                            onClick = { playerViewModel.playPlaylistShuffled(
                                                                    tracksToDisplay,
                                                                    context = playbackContext
                                                                )
                                                            },
                                                            colors = ButtonDefaults.filledTonalButtonColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                                contentColor = MaterialTheme.colorScheme.onSurface
                                                            ),
                                                            shapes = ButtonDefaults.shapes(),
                                                            modifier = Modifier.weight(1f).height(50.dp)
                                                        ) {
                                                            Icon(Icons.Default.Shuffle, null)
                                                            Spacer(Modifier.width(8.dp))
                                                            Text(
                                                                stringResource(R.string.btn_shuffle),
                                                                style = MaterialTheme.typography.titleMedium,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                }
                                                Spacer(Modifier.height(16.dp))
                                            }

                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                AnimatedVisibility(
                                                    visible = isSearchExpanded,
                                                    enter = expandHorizontally(expandFrom = Alignment.End) + fadeIn(),
                                                    exit = shrinkHorizontally(shrinkTowards = Alignment.End) + fadeOut(),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    OutlinedTextField(
                                                        value = playlistSearchQuery,
                                                        onValueChange = { playlistSearchQuery = it },
                                                        placeholder = { Text(stringResource(R.string.search_playlist_hint)) },
                                                        leadingIcon = { Icon(Icons.Default.Search, null) },
                                                        trailingIcon = {
                                                            IconButton(onClick = hapticAction(HapticKind.UI) {
                                                                playlistSearchQuery = ""
                                                                isSearchExpanded = false
                                                            }) {
                                                                Icon(Icons.Rounded.Close, null)
                                                            }
                                                        },
                                                        singleLine = true,
                                                        shape = CircleShape,
                                                        colors = OutlinedTextFieldDefaults.colors(
                                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(
                                                                alpha = 0.5f
                                                            )
                                                        )
                                                    )
                                                }

                                                AnimatedVisibility(visible = !isSearchExpanded) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = hapticAction(HapticKind.UI) { isSearchExpanded = true },
                                                            shapes = IconButtonDefaults.shapes(),
                                                            colors = IconButtonDefaults.iconButtonColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        ) {
                                                            Icon(Icons.Default.Search, "Search")
                                                        }
                                                        Spacer(Modifier.width(12.dp))
                                                        IconButton(
                                                            onClick = hapticAction(HapticKind.UI) { showPlaylistSortSheet = true },
                                                            shapes = IconButtonDefaults.shapes(),
                                                            colors = IconButtonDefaults.iconButtonColors(
                                                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        ) {
                                                            Icon(Icons.Rounded.Sort, "Sort")
                                                        }
                                                    }
                                                }
                                            }
                                            Spacer(Modifier.height(16.dp))
                                        }

                                        if (playlistId == "likes" && vibes.isNotEmpty() && !isGuest) {
                                            item(key = "likes_vibes_row") {
                                                androidx.compose.foundation.lazy.LazyRow(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(bottom = 16.dp),
                                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    items(vibes.size, key = { vibes[it].id }) { vibeIndex ->
                                                        val vibe = vibes[vibeIndex]
                                                        val isSelected = selectedVibeId == vibe.id

                                                        Button(
                                                            onClick = {
                                                                view.performAppHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                                                                selectedVibeId = if (isSelected) null else vibe.id
                                                            },
                                                            shapes = ButtonDefaults.shapes(),
                                                            colors = ButtonDefaults.buttonColors(
                                                                containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                                                                contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                                            ),
                                                            contentPadding = PaddingValues(
                                                                horizontal = 16.dp,
                                                                vertical = 0.dp
                                                            ),
                                                            modifier = Modifier.height(40.dp)
                                                        ) {
                                                            Text(
                                                                text = vibe.displayName.uppercase(java.util.Locale.getDefault()),
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (tracksToDisplay.isEmpty() && (playlistSearchQuery.isNotEmpty() || selectedVibeId != null)) {
                                            item {
                                                EmptyPlaylistView(
                                                    playlistId = playlistId,
                                                    isUserCreated = isUserCreated,
                                                    isEmptySearch = true
                                                )
                                            }
                                        }
                                    }

                                    itemsIndexed(items = tracksToDisplay, key = { _, t -> t.id }) { index, track ->
                                        if (index >= tracksToDisplay.size - 5 && playlistId.startsWith("yt_radio:")) {
                                            LaunchedEffect(Unit) {
                                                youtubeRadioViewModel.loadMore()
                                            }
                                        }

                                        // Mix mode badge: [124 BPM • 8A], coloured by the harmonic
                                        // fit of the row's key against the track playing now. The
                                        // per-row flow also warms the analysis cache the smart sort
                                        // reads, so by the time it runs the profiles are mostly there.
                                        val mixProfile = if (isMixMode) {
                                            com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer
                                                .profileFlow(context, track)
                                                .collectAsState(initial = null).value
                                        } else null
                                        val mixBadge = mixProfile?.let { profile ->
                                            MixBadgeUi(
                                                bpm = profile.bpm,
                                                camelot = profile.camelot,
                                                fit = com.alananasss.kittytune.data.mix.MixPlaylistAnalyzer.keyFit(
                                                    profile.camelot, mixReferenceKey,
                                                ),
                                                durationMs = track.durationMs,
                                            )
                                        }

                                        val progress = downloadProgress[track.id]
                                        val isDownloading = progress != null
                                        val isDownloaded = remember(track.id, downloadedIds) {
                                            downloadedIds.contains(track.id)
                                        }

                                        val isCanReorder = isCanReorderGlobal
                                        val canRemoveTrack = isUserCreated || isDownloadedView || playlistId == "downloads" || playlistId == "likes"
                                        val isSwipeEnabled = isSwipeRemovalEnabled && canRemoveTrack

                                        val contextId = when {
                                            isUserCreated || isDownloadedView -> stableId
                                            playlistId == "downloads" -> -2L
                                            playlistId == "likes" -> -1L
                                            else -> null
                                        }

                                        val onRemoveAction: () -> Unit = {
                                            when {
                                                playlistId == "likes" -> {
                                                    LikeRepository.removeLike(track.id)
                                                }
                                                playlistId == "downloads" -> {
                                                    tracks.removeAll { it.id == track.id }
                                                    DownloadManager.deleteTrack(track.id)
                                                }
                                                else -> {
                                                    tracks.removeAll { it.id == track.id }
                                                    DownloadManager.removeTrackFromPlaylist(
                                                        playlistId = stableId,
                                                        trackId = track.id,
                                                        syncToCloud = isUserCreated && !isDownloadedView && currentIdLong > 0
                                                    )
                                                }
                                            }
                                        }

                                        if (isCanReorder) {
                                            ReorderableItem(
                                                state = reorderableState,
                                                key = track.id
                                            ) { isDragging ->
                                                val elevation by animateDpAsState(
                                                    if (isDragging) 8.dp else 0.dp,
                                                    label = "elevation"
                                                )
                                                val scale by animateFloatAsState(
                                                    if (isDragging) 1.02f else 1f,
                                                    label = "scale"
                                                )
                                                val backgroundColor =
                                                    if (isDragging) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent

                                                // ── Separating drag and swipe gestures ─────────────
                                                // Swipe-to-dismiss (enabled by default) and the row's long press
                                                // competed for the gesture: the horizontal swipe
                                                // detector claimed the movement at the first touch-slop — even BEFORE
                                                // the long-press timer fired, and drag only worked with a perfectly
                                                // motionless finger. Now:
                                                //  • swipe enabled  → drag only via the grip icon
                                                //    (instant draggableHandle, no long press);
                                                //  • swipe disabled  → long press anywhere in the row.
                                                val rowDragModifier = Modifier.longPressDraggableHandle(
                                                    onDragStarted = {
                                                        view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS)
                                                    },
                                                    onDragStopped = {
                                                        view.performAppHaptic(HapticFeedbackConstants.GESTURE_END)
                                                    }
                                                )
                                                val gripHandleModifier = Modifier.draggableHandle(
                                                    onDragStarted = {
                                                        view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS)
                                                    },
                                                    onDragStopped = {
                                                        view.performAppHaptic(HapticFeedbackConstants.GESTURE_END)
                                                    }
                                                )

                                                val itemContent = @Composable {
                                                    TrackListItem(
                                                        track = track,
                                                        currentlyPlayingTrack = playerViewModel.uiCurrentTrack,
                                                        index = index,
                                                        isDownloading = isDownloading,
                                                        isDownloaded = isDownloaded,
                                                        downloadProgress = progress ?: 0,
                                                        showVerifiedBadge = false,
                                                        showLikeIndicator = playlistId != "likes",
                                                        horizontalPadding = 0.dp,
                                                        mixBadge = mixBadge,
                                                        // animateItem() is not needed here: ReorderableItem itself
                                                        // animates the placement of non-dragged rows —
                                                        // applying it twice conflicted with the drag offset.
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .then(
                                                                if (isDragging) {
                                                                    Modifier
                                                                        .clip(RoundedCornerShape(16.dp))
                                                                        .background(backgroundColor)
                                                                } else {
                                                                    Modifier
                                                                }
                                                            )
                                                            .graphicsLayer {
                                                                scaleX = scale
                                                                scaleY = scale
                                                                shadowElevation = elevation.toPx()
                                                            },
                                                        dragModifier = rowDragModifier,
                                                        handleModifier = gripHandleModifier,
                                                        onClick = hapticAction(HapticKind.UI) {
                                                            if (!isDragging) {
                                                                playerViewModel.playPlaylist(
                                                                    tracksToDisplay,
                                                                    index,
                                                                    playbackContext
                                                                )
                                                            }
                                                        },
                                                        onOptionClick = hapticAction(HapticKind.UI) {
                                                            playerViewModel.showTrackOptions(track, contextId)
                                                        }
                                                    )
                                                }

                                                SwipeToDeleteItem(
                                                    key = track.id,
                                                    enabled = isSwipeEnabled && !isDragging,
                                                    clipContent = !isDragging,
                                                    endZoneIgnoreWidth = 104.dp,
                                                    onDelete = onRemoveAction,
                                                    shape = RoundedCornerShape(16.dp),
                                                    horizontalPadding = 8.dp
                                                ) {
                                                    itemContent()
                                                }
                                            }
                                        } else {
                                            val itemContent = @Composable {
                                                TrackListItem(
                                                    track = track,
                                                    currentlyPlayingTrack = playerViewModel.uiCurrentTrack,
                                                    index = index,
                                                    isDownloading = isDownloading,
                                                    isDownloaded = isDownloaded,
                                                    downloadProgress = progress ?: 0,
                                                    showVerifiedBadge = false,
                                                    showLikeIndicator = playlistId != "likes",
                                                    horizontalPadding = 0.dp,
                                                    mixBadge = mixBadge,
                                                    modifier = Modifier.animateItem(),
                                                    onClick = hapticAction(HapticKind.UI) {
                                                        playerViewModel.playPlaylist(
                                                            tracksToDisplay,
                                                            index,
                                                            playbackContext
                                                        )
                                                    },
                                                    onOptionClick = hapticAction(HapticKind.UI) {
                                                        playerViewModel.showTrackOptions(track, contextId)
                                                    }
                                                )
                                            }

                                            SwipeToDeleteItem(
                                                key = track.id,
                                                enabled = isSwipeEnabled,
                                                clipContent = true,
                                                endZoneIgnoreWidth = 56.dp,
                                                onDelete = onRemoveAction,
                                                shape = RoundedCornerShape(16.dp),
                                                horizontalPadding = 8.dp
                                            ) {
                                                itemContent()
                                            }
                                        }

                                        // The inline transition connector: the compact preset
                                        // plate on the guide line between rows N and N+1.
                                        // Tapping it opens the full-screen transition editor
                                        // for exactly this pair, preloaded with its saved plan.
                                        if (isMixMode && index < tracksToDisplay.lastIndex) {
                                            val nextTrack = tracksToDisplay[index + 1]
                                            MixTransitionConnector(
                                                plan = TransitionStore.loadAny(context, playlistId, track.id, nextTrack.id)
                                                    ?: TransitionPlan(),
                                                modifier = Modifier.animateItem(),
                                                onClick = hapticAction(HapticKind.LIGHT) {
                                                    transitionEditor = track to nextTrack
                                                },
                                            )
                                        }
                                    }
                                    if (playlistId.startsWith("yt_radio:") && youtubeRadioViewModel.isLoadingMore) {
                                        item {
                                            Box(
                                                Modifier.fillMaxWidth().padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        TopAppBar(
                            title = {},
                            navigationIcon = {
                                IconButton(
                                    onClick = hapticAction(HapticKind.LIGHT, onBackClick),
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.3f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        stringResource(R.string.btn_close),
                                        tint = Color.White
                                    )
                                }
                            },
                            actions = {
                                // The Mix Mode toggle — Spotify's mixer-sliders icon, living
                                // strictly left of the overflow button. On: Spotify green with
                                // a badge dot; off: the bar's neutral white. Toggling shows or
                                // hides the BPM/Camelot badges and the inline transition
                                // connectors across the list.
                                //
                                // Gated to the user's OWN created playlists: likes, stations,
                                // radios, charts and every other foreign list never see the
                                // mixer — the transitions only make sense on a playlist the
                                // user curates themselves.
                                if (isUserCreated) {
                                    IconButton(
                                        onClick = hapticAction(HapticKind.MEDIUM) {
                                            isMixMode = !isMixMode
                                            if (!isMixMode) {
                                                transitionEditor = null
                                                mixOrderedIds = null
                                            }
                                        },
                                        shapes = IconButtonDefaults.shapes(),
                                        colors = IconButtonDefaults.iconButtonColors(
                                            containerColor = Color.Black.copy(alpha = 0.3f),
                                            contentColor = if (isMixMode) TransitionPlayheadColor else Color.White
                                        )
                                    ) {
                                        Box {
                                            Icon(
                                                Icons.Rounded.Tune,
                                                stringResource(if (isMixMode) R.string.mix_mode_disable else R.string.mix_mode_enable),
                                                tint = if (isMixMode) TransitionPlayheadColor else Color.White
                                            )
                                            if (isMixMode) {
                                                // The active badge dot pinned to the icon's
                                                // top-end, the Spotify-live-marker placement.
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.TopEnd)
                                                        .size(8.dp)
                                                        .background(TransitionPlayheadColor, CircleShape)
                                                        .border(1.5.dp, Color.Black.copy(alpha = 0.35f), CircleShape)
                                                )
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = hapticAction(HapticKind.UI) { showPlaylistOptionsSheet = true },
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = Color.Black.copy(alpha = 0.3f),
                                        contentColor = Color.White
                                    )
                                ) {
                                    Icon(
                                        Icons.Default.MoreVert,
                                        stringResource(R.string.btn_options),
                                        tint = Color.White
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp)
                        )
                    }
                }
            }

        if (showPlaylistOptionsSheet) {
            com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
                onDismissRequest = { showPlaylistOptionsSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                PlaylistOptionsSheet(
                    playlistId = stableId,
                    playlistTitle = playlistTitle,
                    playlistCover = playlistCover,
                    defaultIcon = defaultIcon,
                    tracks = tracksToDisplay,
                    isLocal = isLocalPlaylist || playlistId == "downloads",
                    shareUrl = shareUrl,
                    playerViewModel = playerViewModel,
                    isFullyDownloaded = isFullyDownloaded,
                    isDownloading = isPlaylistDownloading,
                    onDismiss = { showPlaylistOptionsSheet = false },
                    isYoutubeRadio = isYoutubeRadio,
                    playlistSharing = playlistSharing,
                    isUserOwned = isUserCreated,
                    isAlbum = isAlbum,
                    onSharingToggle = { newSharing ->
                        scope.launch {
                            try {
                                val onlinePlaylist = api.getPlaylist(currentIdLong)
                                val request = PlaylistUpdateRequest(
                                    trackUrns = (onlinePlaylist.tracks ?: emptyList()).filter { it.id > 0 }
                                        .map { "soundcloud:tracks:${it.id}" },
                                    title = onlinePlaylist.title ?: "",
                                    description = onlinePlaylist.description ?: "",
                                    genre = onlinePlaylist.genre ?: "",
                                    tagList = onlinePlaylist.tagList ?: "",
                                    isPublic = newSharing == "public"
                                )
                                var response = api.updatePlaylist(currentIdLong, request)
                                if (!response.isSuccessful) {
                                    var errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                                    val captchaUrl = SessionManager.extractDataDomeCaptchaUrl(errorBody)
                                    if (response.code() == 403 && captchaUrl != null) {
                                        val solved = SessionManager.awaitDataDomeChallenge(context, captchaUrl)
                                        if (solved) {
                                            response = api.updatePlaylist(currentIdLong, request)
                                            if (response.isSuccessful) {
                                                playlistSharing = newSharing
                                                android.widget.Toast.makeText(
                                                    context,
                                                    context.getString(R.string.success_generic),
                                                    android.widget.Toast.LENGTH_SHORT
                                                ).show()
                                                return@launch
                                            }
                                            errorBody = runCatching { response.errorBody()?.string() }.getOrNull()
                                        }
                                    }
                                    throw Exception("SoundCloud playlist update failed (${response.code()}): ${errorBody ?: response.message()}")
                                }
                                playlistSharing = newSharing
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.success_generic),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            } catch (e: Exception) {
                                e.printStackTrace()
                                android.widget.Toast.makeText(
                                    context,
                                    context.getString(R.string.error_generic),
                                    android.widget.Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    },
                    onDetailsClick = {
                        showPlaylistOptionsSheet = false
                        showPlaylistDetailsSheet = true
                    },
                    onDeleteClick = {
                        showPlaylistOptionsSheet = false
                        showDeleteDialog = true
                    }
                )
            }
        }

        if (showPlaylistDetailsSheet) {
            com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
                onDismissRequest = { showPlaylistDetailsSheet = false },
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                PlaylistDetailsSheet(
                    playlistId = when {
                        playlistId.startsWith("system_playlist:") || playlistId.startsWith("soundcloud:system-playlists:") || playlistId.startsWith("spotify:") || playlistId.startsWith("spotify_") -> playlistId
                        currentIdLong > 0L -> currentIdLong.toString()
                        else -> playlistId
                    },
                    onDismiss = { showPlaylistDetailsSheet = false },
                    onViewAll = { tabIndex ->
                        showPlaylistDetailsSheet = false
                        onNavigate("playlist_fans/$currentIdLong?tab=$tabIndex")
                    },
                    onNavigate = onNavigate,
                    onMentionClick = { username ->
                        haptics.perform(HapticKind.LIGHT)
                        showPlaylistDetailsSheet = false
                        playerViewModel.resolveAndNavigateToArtist(username)
                    },
                    initialDurationMs = totalDurationMs
                )
            }
        }

        // The full-screen transition editor, one per tapped connector. Its Save stores the
        // plan per pair (via TransitionStore, under EVERY route key the pair can be played
        // from) and Cancel simply removes the overlay.
        transitionEditor?.let { (trackA, trackB) ->
            DisposableEffect(Unit) {
                playerViewModel.isTransitionEditorActive = true
                onDispose {
                    playerViewModel.isTransitionEditorActive = false
                }
            }
            EditTransitionScreen(
                trackA = trackA,
                trackB = trackB,
                initialPlan = TransitionStore.loadAny(context, playlistId, trackA.id, trackB.id)
                    ?: TransitionPlan(),
                playlistKey = playlistId,
                onSave = { savedPlan ->
                    TransitionStore.saveAll(
                        context = context,
                        playlistKey = playlistId,
                        numericPlaylistId = currentIdLong,
                        fromId = trackA.id,
                        toId = trackB.id,
                        plan = savedPlan,
                    )
                    Toast.makeText(context, R.string.transition_saved, Toast.LENGTH_SHORT).show()
                },
                onDismiss = { transitionEditor = null },
            )
        }
    }
}

@Composable
fun PlaylistSquareCard(playlist: Playlist, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().hapticClickable { onClick() }) {
        AsyncImage(
            model = playlist.fullResArtwork,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = playlist.title ?: stringResource(R.string.generic_title),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontWeight = FontWeight.SemiBold
        )
        val subtitle = when {
            playlist.trackCount != null && playlist.trackCount > 0 -> stringResource(R.string.playlist_num_tracks, playlist.trackCount)
            !playlist.user?.username.isNullOrBlank() -> playlist.user?.username.orEmpty()
            else -> stringResource(R.string.lib_playlists)
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

data class DockOptionItem(val icon: ImageVector, val text: String, val onClick: () -> Unit)

@Composable
fun PlaylistOptionsSheet(
    playlistId: Long,
    playlistTitle: String,
    playlistCover: String?,
    defaultIcon: ImageVector? = null,
    tracks: List<Track>,
    isLocal: Boolean,
    shareUrl: String,
    playerViewModel: PlayerViewModel,
    isFullyDownloaded: Boolean,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    isYoutubeRadio: Boolean = false,
    playlistSharing: String? = null,
    isUserOwned: Boolean = false,
    isAlbum: Boolean = false,
    onSharingToggle: (String) -> Unit = {},
    onDetailsClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        AlertDialog(
            onDismissRequest = {
                showRemoveDownloadDialog = false
                onDismiss()
            },
            title = { Text(stringResource(R.string.dialog_remove_download_title)) },
            text = { Text(stringResource(R.string.dialog_remove_download_msg)) },
            confirmButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    if (playlistId == DownloadManager.LIKES_BATCH_ID) {
                        DownloadManager.removeDownloads(tracks)
                    } else if (playlistId != 0L) {
                        DownloadManager.removePlaylistDownloads(playlistId, tracks)
                    }
                    showRemoveDownloadDialog = false
                    onDismiss()
                }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = hapticAction(HapticKind.UI) {
                    showRemoveDownloadDialog = false
                    onDismiss()
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 24.dp).padding(horizontal = 8.dp)
        ) {
            if (!playlistCover.isNullOrEmpty()) {
                AsyncImage(
                    model = playlistCover,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = defaultIcon ?: Icons.Default.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = playlistTitle,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        val items = remember(isLocal, playlistId, isYoutubeRadio, isUserOwned, playlistSharing, onDeleteClick, isAlbum, tracks) {
            mutableListOf(
                DockOptionItem(
                    Icons.Rounded.PlayArrow,
                    context.getString(R.string.btn_play)
                ) { playerViewModel.playPlaylist(tracks, 0); onDismiss() },
                DockOptionItem(
                    Icons.Default.Shuffle,
                    context.getString(R.string.btn_shuffle)
                ) { playerViewModel.playPlaylistShuffled(tracks); onDismiss() }
            ).apply {
                if (!isYoutubeRadio) {
                    add(
                        DockOptionItem(
                            Icons.AutoMirrored.Rounded.PlaylistPlay,
                            context.getString(R.string.menu_play_next)
                        ) { playerViewModel.insertNext(tracks); onDismiss() })
                    add(
                        DockOptionItem(
                            Icons.AutoMirrored.Rounded.QueueMusic,
                            context.getString(R.string.menu_add_queue)
                        ) { playerViewModel.addToQueue(tracks); onDismiss() })
                    add(
                        DockOptionItem(
                            Icons.Default.Add,
                            context.getString(R.string.menu_add_playlist)
                        ) { playerViewModel.prepareBulkAdd(tracks); onDismiss() })
                }

                if (tracks.isNotEmpty()) {
                    add(
                        DockOptionItem(
                            Icons.Rounded.SportsEsports,
                            context.getString(R.string.game_eat_playlist)
                        ) {
                            SnakeGameLauncher.launch(
                                SnakeGameRequest(
                                    title = playlistTitle,
                                    mode = SnakeGameMode.PLAYLIST,
                                    tracks = tracks,
                                )
                            )
                            onDismiss()
                        })
                }

                if (isAlbum && tracks.isNotEmpty() && !isYoutubeRadio) {
                    add(
                        DockOptionItem(
                            Icons.Rounded.Favorite,
                            context.getString(R.string.menu_like_all_songs)
                        ) {
                            // Asks first: a record often ends with a silence, a voice note or a drone
                            // that nobody means to like (desktop PR #77).
                            LikeAllPrompt.ask(tracks)
                            onDismiss()
                        })
                }

                if (playlistId != 0L && !isYoutubeRadio && playlistId != DownloadManager.LIKES_BATCH_ID) {
                    add(
                        DockOptionItem(
                        icon = Icons.Rounded.Info,
                        text = context.getString(R.string.menu_playlist_details),
                        onClick = { onDetailsClick() }
                    ))
                }
                if (!isLocal && isUserOwned) {
                    val isPrivate =
                        playlistSharing == "private" || playlistSharing == "secret" || (playlistSharing == null && playlistTitle.contains(
                            "Private",
                            ignoreCase = true
                        ))
                    val shareIcon = if (isPrivate) Icons.Rounded.Public else Icons.Rounded.Lock
                    val shareText =
                        if (isPrivate) context.getString(R.string.menu_make_public) else context.getString(R.string.menu_make_private)

                    add(
                        DockOptionItem(
                        icon = shareIcon,
                        text = shareText,
                        onClick = {
                            val newSharing = if (isPrivate) "public" else "private"
                            onSharingToggle(newSharing)
                            onDismiss()
                        }
                    ))
                }
                if (onDeleteClick != null && playlistId != 0L && !isYoutubeRadio && playlistId != DownloadManager.LIKES_BATCH_ID) {
                    add(
                        DockOptionItem(
                            icon = Icons.Rounded.Delete,
                            text = context.getString(if (isUserOwned) R.string.menu_delete_playlist else R.string.dialog_delete_playlist_from_lib_title),
                            onClick = {
                                onDeleteClick()
                                onDismiss()
                            }
                        )
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            items(items) { item ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.hapticClickable { item.onClick() }) {
                    Icon(item.icon, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        item.text,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            if ((!isLocal || isFullyDownloaded) && tracks.isNotEmpty() && !isYoutubeRadio) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.hapticClickable {
                        if (isFullyDownloaded) {
                            showRemoveDownloadDialog = true
                        } else if (!isDownloading) {
                            if (playlistId == DownloadManager.LIKES_BATCH_ID) {
                                DownloadManager.downloadBatch(tracks, DownloadManager.LIKES_BATCH_ID)
                            } else {
                                val fakePlaylist =
                                    Playlist(playlistId, playlistTitle, playlistCover, null, tracks.size, null, null)
                                DownloadManager.downloadPlaylist(fakePlaylist, tracks)
                            }
                            onDismiss()
                        }
                    }) {
                        val icon =
                            if (isFullyDownloaded) Icons.Rounded.DownloadDone else if (isDownloading) Icons.Rounded.Downloading else Icons.Rounded.Download
                        val tint =
                            if (isFullyDownloaded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        val text =
                            if (isFullyDownloaded) stringResource(R.string.btn_downloaded) else stringResource(R.string.btn_download)

                        Icon(icon, null, modifier = Modifier.size(32.dp), tint = tint)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            color = tint
                        )
                    }
                }
            }

            if (shareUrl.isNotEmpty()) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.hapticClickable {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.btn_share)))
                        onDismiss()
                    }) {
                        Icon(
                            Icons.Outlined.Share,
                            null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.btn_share),
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TrackListItem(
    track: Track,
    currentlyPlayingTrack: Track? = null,
    index: Int,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int,
    modifier: Modifier = Modifier,
    showVerifiedBadge: Boolean = true,
    isVerifiedMatch: Boolean = false,
    showLikeIndicator: Boolean = true,
    horizontalPadding: Dp = 8.dp,
    /** Full-row drag handle (long-press) — applied to the row root. */
    dragModifier: Modifier = Modifier,
    /** Grip-icon handle (instant drag) — applied to the icon only. */
    handleModifier: Modifier = Modifier,
    /** Mix-mode badge ("124 BPM • 8A"), or null to hide it. Coloured by harmonic fit. */
    mixBadge: MixBadgeUi? = null,
    onClick: () -> Unit,
    onOptionClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val view = LocalView.current
    val blockedTrackIds by com.alananasss.kittytune.data.BlockManager.blockedTrackIdsFlow.collectAsState()
    val blockedArtistIds by com.alananasss.kittytune.data.BlockManager.blockedArtistIdsFlow.collectAsState()
    if (track.id in blockedTrackIds || (track.user?.id != null && track.user.id in blockedArtistIds)) {
        return
    }

    val isCurrent = currentlyPlayingTrack?.id == track.id
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val titleWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold

    val likedTrackIds by LikeRepository.likedTrackIds.collectAsState()
    val isTrackLiked = remember(track.id, likedTrackIds, showLikeIndicator) {
        if (showLikeIndicator) likedTrackIds.contains(track.id) else false
    }

    val socialLikersMap by SocialProofRepository.socialLikersMap.collectAsState()
    val socialLikers = socialLikersMap[track.id]

    LaunchedEffect(track.id) {
        if (track.id > 0 && track.source != "youtube") {
            SocialProofRepository.requestSocialProof(track.id)
        }
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent,
        modifier = modifier
            // The drag handle (long-press-then-drag) covers the WHOLE row when the caller
            // passes one; the grip icon below is then a purely visual affordance. Without
            // this the long press belonged to combinedClickable only and the row never
            // started dragging.
            .then(dragModifier)
            .padding(horizontal = horizontalPadding)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (dragModifier != Modifier) {
                    // Row with a drag handle: a PLAIN clickable without a long-press detector.
                    // combinedClickable always had its own long-press handler —
                    // even with an empty callback — and the two detectors competed for the gesture:
                    // the haptic fired but the drag never started. Now the long
                    // press belongs entirely to the drag handle.
                    Modifier.clickable(
                        onClick = {
                            view.performAppHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClick()
                        }
                    )
                } else {
                    Modifier.combinedClickable(
                        onClick = {
                            view.performAppHaptic(HapticFeedbackConstants.KEYBOARD_TAP)
                            onClick()
                        },
                        onLongClick = {
                            view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS)
                            if (onLongClick != null) onLongClick() else onOptionClick()
                        }
                    )
                }
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 8.dp)
                .height(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                val art = track.thumbnailUrl
                val imageModel: Any = remember(art) {
                    if (art.startsWith("/")) File(art) else art
                }
                AsyncImage(
                    model = imageModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .alpha(if (isDownloading) 0.3f else 1f),
                    contentScale = ContentScale.Crop
                )
                if (isDownloading) CircularWavyProgressIndicator(
                    progress = { downloadProgress / 100f },
                    modifier = Modifier.size(28.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )

                if (isCurrent && !isDownloading) {
                    Box(
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = stringResource(R.string.player_playing_now),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.title ?: stringResource(R.string.untitled_track),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = titleWeight,
                        color = titleColor,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (track.isGoPlus) {
                        Spacer(Modifier.width(6.dp))
                        com.alananasss.kittytune.ui.common.GoPlusBadge(compact = true)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isDownloaded && !isDownloading) {
                        Icon(
                            Icons.Rounded.DownloadDone,
                            stringResource(R.string.btn_downloaded),
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isVerifiedMatch) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Verified,
                            stringResource(R.string.search_verified_track),
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    } else if (showVerifiedBadge && track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Verified,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (showLikeIndicator && isTrackLiked) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (!socialLikers.isNullOrEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(4.dp))
                        MiniSocialProofAvatars(likers = socialLikers)
                    }
                }
            }
            // Mix-mode metadata, to the right of the title: tempo on top, the Camelot wheel
            // badge plus the track's duration underneath — the Spotify-Mix row layout.
            if (mixBadge != null) {
                Spacer(Modifier.width(8.dp))
                MixTrackMetaColumn(badge = mixBadge)
            }
            if (dragModifier != Modifier || handleModifier != Modifier) {
                Icon(
                    imageVector = Icons.Rounded.DragHandle,
                    contentDescription = stringResource(R.string.desc_move),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .size(48.dp)
                        .then(handleModifier)
                        .padding(12.dp)
                )
            }
            IconButton(
                onClick = hapticAction(HapticKind.LIGHT, onOptionClick),
                modifier = Modifier.size(48.dp),
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    stringResource(R.string.btn_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Playlist Mix UI pieces
// ---------------------------------------------------------------------------

/**
 * The Mix mode's play control: the Pioneer-CDJ-style round button — a green disc inside a
 * double rim with a soft glow, the look Spotify puts at the top of a mixed playlist in place
 * of the plain wide Play pill. Same action, deck-machine dress.
 */
@Composable
private fun MixCdjPlayButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(62.dp)
            .hapticClickable(kind = HapticKind.UI, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        // The soft glow behind the rims.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                TransitionPlayheadColor.copy(alpha = 0.30f),
                                Color.Transparent,
                            ),
                            radius = size.maxDimension,
                        ),
                        radius = size.maxDimension,
                    )
                },
        )
        // Outer rim — thin, dimmer.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(1.dp, TransitionPlayheadColor.copy(alpha = 0.45f), CircleShape),
        )
        // Middle rim — the CDJ's second ring.
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(1.5.dp, TransitionPlayheadColor.copy(alpha = 0.85f), CircleShape),
        )
        // The platter: the green disc with the play arrow.
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(TransitionPlayheadColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = stringResource(R.string.btn_play),
                tint = Color.White,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

/**
 * The inline transition connector between rows N and N+1 in Mix mode: a 2dp `surfaceVariant`
 * guide line with a compact rounded plate on it — the preset's icon beside its name and a
 * chevron (`Auto ›`, `Blend ›`, `Custom ›`). Tapping opens the transition editor for the pair.
 */
@Composable
private fun MixTransitionConnector(
    plan: TransitionPlan,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .padding(top = 2.dp, bottom = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        // The guide line the two rows hang from.
        Box(
            modifier = Modifier
                .width(2.dp)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
        // The connector plate — an oval chip with a 12dp corner radius.
        val showIcon = plan.preset in CONNECTOR_ICON_PRESETS
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .height(32.dp)
                .hapticClickable(kind = HapticKind.LIGHT, onClick = onClick),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                if (showIcon) {
                    Icon(
                        imageVector = transitionPresetIcon(plan.preset),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    text = transitionPresetTitle(plan.preset) + " ›",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Presets that draw their icon on the connector plate: ✦ Auto, ⚡ Slam, sliders Custom, ↔ Blend. */
private val CONNECTOR_ICON_PRESETS = setOf(
    TransitionPreset.AUTO,
    TransitionPreset.SLAM,
    TransitionPreset.CUSTOM,
    TransitionPreset.BLEND,
)

/**
 * The Mix-mode metadata column for a track row: tempo on the first line in the muted colour,
 * then the Camelot badge in its wheel colour beside the track's duration.
 */
@Composable
private fun MixTrackMetaColumn(
    badge: MixBadgeUi,
    modifier: Modifier = Modifier,
) {
    Column(horizontalAlignment = Alignment.End, modifier = modifier) {
        Text(
            text = if (badge.bpm > 0) stringResource(R.string.mix_badge_bpm_only, badge.bpm) else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(3.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            badge.camelot?.let { CamelotKeyBadge(code = it) }
            badge.durationMs?.takeIf { it > 0 }?.let {
                Text(
                    text = makeTimeString(it),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun EmptyPlaylistView(
    playlistId: String,
    isUserCreated: Boolean,
    isEmptySearch: Boolean = false,
    isNotFound: Boolean = false,
    onBackClick: () -> Unit = {}
) {
    val (kaomoji, title, subtitle) = when {
        isNotFound -> Triple(
            "( ╥ω╥ )",
            stringResource(R.string.yearly_playback_playlist_404_title),
            stringResource(R.string.yearly_playback_playlist_404_desc)
        )

        isEmptySearch -> Triple(
            stringResource(R.string.empty_playlist_search_kaomoji),
            stringResource(R.string.empty_playlist_search_title),
            stringResource(R.string.empty_playlist_search_subtitle)
        )

        playlistId == "downloads" -> Triple(
            stringResource(R.string.empty_downloads_kaomoji),
            stringResource(R.string.empty_downloads_title),
            stringResource(R.string.empty_downloads_subtitle)
        )

        isUserCreated -> Triple(
            stringResource(R.string.empty_user_playlist_kaomoji),
            stringResource(R.string.empty_user_playlist_title),
            stringResource(R.string.empty_user_playlist_subtitle)
        )

        else -> Triple(
            stringResource(R.string.empty_playlist_generic_kaomoji),
            stringResource(R.string.empty_playlist_generic),
            ""
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        Text(
            text = kaomoji,
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        if (subtitle.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        if (isNotFound) {
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = hapticAction(HapticKind.LIGHT, onBackClick),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(text = stringResource(R.string.btn_close))
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedPlaylistsScreen(
    onBackClick: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val db = remember { AppDatabase.getDatabase(context).downloadDao() }
    val downloadedPlaylists = remember { mutableStateListOf<Playlist>() }
    val windowSizeInfo = com.alananasss.kittytune.ui.common.rememberWindowSizeInfo()

    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val localPlaylists = db.getDownloadedPlaylists().first()
            val mapped = localPlaylists.mapNotNull { local ->
                val tracksInPlaylist = db.getTracksForPlaylistSync(local.id)
                val realDownloadedCount = tracksInPlaylist.count { it.localAudioPath.isNotEmpty() }
                if (realDownloadedCount == 0) return@mapNotNull null
                val firstTrackArt =
                    tracksInPlaylist.firstOrNull { it.localArtworkPath.isNotEmpty() || it.artworkUrl.isNotEmpty() }
                        ?.let { it.localArtworkPath.ifEmpty { it.artworkUrl } }
                val finalArt = local.localCoverPath ?: local.artworkUrl.ifEmpty { firstTrackArt ?: "" }
                Playlist(
                    id = local.id,
                    title = local.title,
                    artworkUrl = finalArt,
                    calculatedArtworkUrl = local.localCoverPath,
                    trackCount = realDownloadedCount,
                    user = User(0, local.artist, null),
                    tracks = null
                )
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                downloadedPlaylists.clear()
                downloadedPlaylists.addAll(mapped)
            }
        }
    }

    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp).fillMaxWidth() else Modifier.fillMaxWidth()
                ) {
                    TopAppBar(
                        title = { Text(stringResource(R.string.lib_playlists), fontWeight = FontWeight.Bold) },
                        navigationIcon = {
                            FilledTonalIconButton(
                                onClick = hapticAction(HapticKind.LIGHT, onBackClick),
                                shapes = IconButtonDefaults.shapes(),
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { inner ->
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (windowSizeInfo.isTablet) Modifier.widthIn(max = 840.dp) else Modifier)
                    .padding(inner)
                    .padding(bottom = 180.dp)
            ) {
                items(downloadedPlaylists) { playlist ->
                    PlaylistSquareCard(playlist = playlist) {
                        val id = if (playlist.id < 0) "local_playlist:${playlist.id}" else playlist.id.toString()
                        onNavigate("downloaded_section:$id")
                    }
                }
            }
        }
    }
}



