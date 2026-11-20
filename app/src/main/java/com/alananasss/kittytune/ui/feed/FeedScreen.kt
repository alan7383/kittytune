package com.alananasss.kittytune.ui.feed

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.audio.haptics.HapticUtil
import com.alananasss.kittytune.data.DataSaver
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.cover.FeedVisual
import com.alananasss.kittytune.data.cover.FeedVisualResolver
import com.alananasss.kittytune.data.cover.FeedVisualSource
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.wave.WaveFeedback
import com.alananasss.kittytune.ui.common.TrackInfoSheet
import com.alananasss.kittytune.ui.game.TrackHighlights
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.cover.CanvasVideo
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.min

/**
 * The feed tab (SoundCloud-style): a vertical, TikTok-like feed of audio previews. Two modes
 * live in the pill at the top — Discover (the smart wave) and Following (artists the user follows).
 * Settling on a card auto-plays its best ~30 seconds ([TrackHighlights]) on repeat until the user
 * opens the full player; the right rail carries like, dislike, comments and add-to-playlist (a
 * thumbs-down blocks the track and its artist and starts the next card, like in My Wave), tapping
 * the song title opens the track info sheet, the author row (avatar + username) opens the artist's
 * profile, and the big play button (bottom right, wrapped in a progress ring) pauses / resumes the
 * preview instead of opening the full player — the full player opens by tapping the card itself.
 * The mini player is always hidden on the feed tab, so the bottom overlay is just the bottom bar
 * and all of the card's buttons sit lower.
 */
private const val PREVIEW_MS = 30_000L

@Composable
private fun feedBottomClearance(): Dp {
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return navInset + 100.dp
}

private val FeedBackground = Color(0xFF0D0D12)

@Composable
fun FeedScreen(playerViewModel: PlayerViewModel) {
    val feedViewModel: FeedViewModel = viewModel()
    var activeTab by rememberSaveable { mutableStateOf(FeedViewModel.FeedTab.DISCOVER) }

    LaunchedEffect(activeTab) {
        if (activeTab == FeedViewModel.FeedTab.FOLLOWING) feedViewModel.ensureFollowingLoaded()
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                feedViewModel.applyOfficialOnly(PlayerPreferences(context).getFeedOfficialOnlyEnabled())
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FeedBackground)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        FeedModeTabs(
            active = activeTab,
            onSelect = { activeTab = it },
        )
        Spacer(Modifier.height(6.dp))
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (activeTab) {
                FeedViewModel.FeedTab.DISCOVER -> FeedPager(
                    posts = feedViewModel.discover,
                    isLoading = feedViewModel.discoverLoading && feedViewModel.discover.isEmpty(),
                    emptyText = stringResource(R.string.feed_empty_discover),
                    playerViewModel = playerViewModel,
                    feedViewModel = feedViewModel,
                    onPageSettled = { page ->
                        if (page >= feedViewModel.discover.size - 3) feedViewModel.loadDiscoverMore()
                    },
                )
                FeedViewModel.FeedTab.FOLLOWING -> {
                    if (!feedViewModel.isLoggedIn) {
                        FeedMessage(
                            text = stringResource(R.string.feed_sign_in_hint),
                            icon = {
                                Icon(
                                    Icons.Rounded.RssFeed,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(42.dp),
                                )
                            },
                        )
                    } else if (feedViewModel.followingError && feedViewModel.following.isEmpty()) {
                        FeedMessage(
                            text = stringResource(R.string.feed_error),
                            trailing = {
                                TextButton(onClick = { feedViewModel.resetFollowing() }) {
                                    Text(stringResource(R.string.btn_retry))
                                }
                            },
                        )
                    } else {
                        FeedPager(
                            posts = feedViewModel.following,
                            isLoading = feedViewModel.followingLoading && feedViewModel.following.isEmpty(),
                            emptyText = stringResource(R.string.feed_empty_following),
                            playerViewModel = playerViewModel,
                            feedViewModel = feedViewModel,
                            onPageSettled = { page ->
                                if (page >= feedViewModel.following.size - 3) feedViewModel.loadMoreFollowing()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedModeTabs(
    active: FeedViewModel.FeedTab,
    onSelect: (FeedViewModel.FeedTab) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(100.dp),
        color = Color.White.copy(alpha = 0.08f),
    ) {
        Row(modifier = Modifier.padding(4.dp)) {
            listOf(
                FeedViewModel.FeedTab.DISCOVER to R.string.feed_tab_discover,
                FeedViewModel.FeedTab.FOLLOWING to R.string.feed_tab_following,
            ).forEach { (tab, labelRes) ->
                val selected = tab == active
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(if (selected) scheme.primary else Color.Transparent)
                        .clickable { onSelect(tab) }
                        .padding(horizontal = 24.dp, vertical = 9.dp),
                ) {
                    Text(
                        text = stringResource(labelRes),
                        color = if (selected) scheme.onPrimary else Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedPager(
    posts: List<FeedPost>,
    isLoading: Boolean,
    emptyText: String,
    playerViewModel: PlayerViewModel,
    feedViewModel: FeedViewModel,
    onPageSettled: (Int) -> Unit,
) {
    if (isLoading) {
        FeedMessage(text = stringResource(R.string.feed_loading), icon = { FeedSpinner() })
        return
    }
    if (posts.isEmpty()) {
        FeedMessage(text = emptyText)
        return
    }

    val pagerState = rememberPagerState(pageCount = { posts.size })

    var previewTrackId by remember { mutableStateOf<Long?>(null) }
    var previewStartMs by remember { mutableLongStateOf(0L) }
    var fullListen by remember { mutableStateOf(false) }

    val activePost = posts.getOrNull(pagerState.settledPage)
    LaunchedEffect(pagerState.settledPage, activePost?.track?.id) {
        val post = activePost ?: return@LaunchedEffect
        onPageSettled(pagerState.settledPage.coerceIn(0, posts.lastIndex))
        fullListen = false
        val start = runCatching { TrackHighlights.startMs(post.track) }.getOrDefault(0L)
        previewStartMs = start
        previewTrackId = post.track.id
        if (playerViewModel.uiCurrentTrack?.id != post.track.id) {
            playerViewModel.playPlaylist(
                tracks = listOf(post.track),
                startIndex = 0,
                startAtPositionMs = start,
                respectShuffle = false,
            )
        }
    }

    val currentTrackId = playerViewModel.uiCurrentTrack?.id
    val durationMs = playerViewModel.uiCurrentTrack?.actualDurationMs ?: 0L
    val position = playerViewModel.currentPosition
    val isPlaying = playerViewModel.uiIsPlaying
    val playerExpanded = playerViewModel.isPlayerExpanded

    LaunchedEffect(previewTrackId, previewStartMs, fullListen, currentTrackId, durationMs) {
        if (fullListen || currentTrackId == null || currentTrackId != previewTrackId) return@LaunchedEffect
        val loopEnd = if (durationMs > 0) min(previewStartMs + PREVIEW_MS, durationMs) else previewStartMs + PREVIEW_MS
        snapshotFlow { playerViewModel.currentPosition }.collect { pos ->
            if (pos >= loopEnd) {
                playerViewModel.seekTo(previewStartMs)
                if (!playerViewModel.uiIsPlaying) playerViewModel.togglePlayPause()
            }
        }
    }

    LaunchedEffect(playerExpanded) {
        if (!playerExpanded && fullListen) {
            fullListen = false
            if (playerViewModel.uiCurrentTrack?.id == previewTrackId && !playerViewModel.uiIsPlaying) {
                playerViewModel.togglePlayPause()
            }
        }
    }

    // ── Moving picture for the settled card ──────────────────────────────────────────
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val feedVisualConfig by prefs.getFeedVisualConfigFlow()
        .collectAsState(initial = FeedVisualResolver.readConfig(prefs).let {
            PlayerPreferences.FeedVisualConfig(it.animatedCovers, it.spotifyCanvas, it.youtubeSamples, it.priorityRaw)
        })
    var feedVisual by remember { mutableStateOf<FeedVisual?>(null) }
    var failedSources by remember(activePost?.track?.id, feedVisualConfig) {
        mutableStateOf<Set<FeedVisualSource>>(emptySet())
    }

    LaunchedEffect(activePost?.track?.id, feedVisualConfig, failedSources) {
        feedVisual = null
        val track = activePost?.track ?: return@LaunchedEffect
        if (DataSaver.isActive(context)) return@LaunchedEffect
        val config = FeedVisualResolver.Config(
            animatedCovers = feedVisualConfig.animatedCovers,
            spotifyCanvas = feedVisualConfig.spotifyCanvas,
            youtubeSamples = feedVisualConfig.youtubeSamples,
            priorityRaw = feedVisualConfig.priorityRaw,
        )
        feedVisual = FeedVisualResolver.resolve(track, config, excludeSources = failedSources)
        if (failedSources.isEmpty()) {
            val index = posts.indexOfFirst { it.track.id == track.id }
            kotlinx.coroutines.coroutineScope {
                for (next in index + 1..index + 2) {
                    posts.getOrNull(next)?.let { post ->
                        launch { FeedVisualResolver.resolve(post.track, config) }
                    }
                }
            }
        }
    }

    val previewActive = currentTrackId != null && currentTrackId == previewTrackId
    val ringFraction = when {
        previewActive && !fullListen ->
            if (durationMs > 0 && previewStartMs + PREVIEW_MS > durationMs) {
                (position.toFloat() / durationMs).coerceIn(0f, 1f)
            } else {
                ((position - previewStartMs).toFloat() / PREVIEW_MS).coerceIn(0f, 1f)
            }
        previewActive && durationMs > 0 -> (position.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }

    val scope = rememberCoroutineScope()
    val onDislike: () -> Unit = {
        val page = pagerState.settledPage
        posts.getOrNull(page)?.let { playerViewModel.dislikeFeedTrack(it.track) }
        val target = page + 1
        if (target <= posts.lastIndex) {
            scope.launch { pagerState.animateScrollToPage(target) }
        } else if (playerViewModel.uiIsPlaying) {
            playerViewModel.togglePlayPause()
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        beyondViewportPageCount = 1,
    ) { page ->
        val post = posts[page]
        val isSettled = page == pagerState.settledPage
        FeedCard(
            post = post,
            ringFraction = if (isSettled) ringFraction else 0f,
            isPreviewPlaying = previewActive && isPlaying && isSettled,
            feedVisual = if (isSettled) feedVisual else null,
            playerViewModel = playerViewModel,
            feedViewModel = feedViewModel,
            onOpenPlayer = { openFullPlayer(post, playerViewModel) { fullListen = true } },
            onDislike = onDislike,
            onVisualError = { src ->
                FeedVisualResolver.invalidate(post.track.id)
                if (isSettled) {
                    failedSources = failedSources + src
                }
            },
        )
    }
}

private fun openFullPlayer(
    post: FeedPost,
    playerViewModel: PlayerViewModel,
    markFullListen: () -> Unit,
) {
    markFullListen()
    if (playerViewModel.uiCurrentTrack?.id != post.track.id) {
        playerViewModel.playPlaylist(tracks = listOf(post.track), startIndex = 0, respectShuffle = false)
    }
    playerViewModel.isPlayerExpanded = true
}

@Composable
private fun FeedCard(
    post: FeedPost,
    ringFraction: Float,
    isPreviewPlaying: Boolean,
    playerViewModel: PlayerViewModel,
    feedViewModel: FeedViewModel,
    onOpenPlayer: () -> Unit,
    onDislike: () -> Unit,
    feedVisual: FeedVisual? = null,
    onVisualError: (FeedVisualSource) -> Unit = {},
) {
    val likedIds by LikeRepository.likedTrackIds.collectAsState()
    val isLiked = likedIds.contains(post.track.id)
    val author = post.author ?: post.track.user
    val authorId = author?.id ?: 0L
    val showFollow = authorId > 0L

    LaunchedEffect(authorId, showFollow) {
        if (showFollow) feedViewModel.checkFollowState(authorId)
    }

    val isFollowing = feedViewModel.followState[authorId]
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val likeScale = remember { Animatable(1f) }
    val ringColor = MaterialTheme.colorScheme.primary

    var showTrackInfo by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onOpenPlayer() },
    ) {
        // Full-bleed artwork
        AsyncImage(
            model = post.track.fullResArtwork,
            contentDescription = post.track.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        // Moving picture over the static artwork
        feedVisual?.let { visual ->
            key(visual.url) {
                CanvasVideo(
                    canvasUrl = visual.url,
                    isPlaying = true,
                    modifier = Modifier.fillMaxSize(),
                    clipStartMs = visual.clipStartMs,
                    clipEndMs = visual.clipEndMs,
                    userAgent = visual.userAgent,
                    onPlaybackError = { onVisualError(visual.source) },
                )
            }
        }

        // Scrims keep the white text readable top and bottom
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.42f),
                        0.22f to Color.Transparent,
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.86f),
                    )
                ),
        )

        // Right rail: like, dislike, comments, add to playlist
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            FeedRailButton(
                icon = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(R.string.feed_like),
                count = post.track.likesCount,
                tint = if (isLiked) ringColor else Color.White,
                iconScale = likeScale.value,
                onClick = {
                    if (!isLiked) {
                        scope.launch {
                            likeScale.snapTo(0.7f)
                            likeScale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 380f))
                        }
                    }
                    playerViewModel.toggleTrackLike(post.track)
                },
            )
            var isDisliked by remember(post.track.id) {
                mutableStateOf(WaveFeedback.isBlocked(post.track.id))
            }
            FeedRailButton(
                icon = if (isDisliked) Icons.Rounded.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = stringResource(R.string.feed_dislike),
                count = null,
                tint = if (isDisliked) ringColor else Color.White,
                onClick = {
                    HapticUtil.performUIHaptic(context)
                    isDisliked = true
                    onDislike()
                    Toast.makeText(context, R.string.notif_dislike_toast, Toast.LENGTH_SHORT).show()
                },
            )
            FeedRailButton(
                icon = Icons.AutoMirrored.Rounded.Comment,
                contentDescription = stringResource(R.string.feed_comments),
                count = post.track.commentCount,
                tint = Color.White,
                onClick = {
                    HapticUtil.performUIHaptic(context)
                    playerViewModel.openComments(post.track)
                },
            )
            FeedRailButton(
                icon = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.feed_add_to_playlist),
                count = null,
                tint = Color.White,
                onClick = {
                    HapticUtil.performUIHaptic(context)
                    playerViewModel.prepareBulkAdd(listOf(post.track))
                },
            )
        }

        // Bottom-left block: repost label, caption, title, artist, author + follow
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.76f)
                .padding(start = 16.dp, bottom = feedBottomClearance()),
        ) {
            if (post.isRepost) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Repeat,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = stringResource(R.string.feed_reposted_by, author?.username.orEmpty()),
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(3.dp))
            }
            post.caption?.let { caption ->
                Text(
                    text = caption,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(5.dp))
            }
            Text(
                text = post.track.title ?: stringResource(R.string.untitled_track),
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable {
                    HapticUtil.performUIHaptic(context)
                    showTrackInfo = true
                },
            )
            Text(
                text = post.track.displayArtist,
                color = Color.White.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            if (showFollow) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = author?.avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.14f))
                            .clickable {
                                HapticUtil.performUIHaptic(context)
                                playerViewModel.navigateToUser(author)
                            },
                    )
                    Spacer(Modifier.width(9.dp))
                    Text(
                        text = author?.username.orEmpty(),
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clickable {
                                HapticUtil.performUIHaptic(context)
                                playerViewModel.navigateToUser(author)
                            },
                    )
                    Spacer(Modifier.width(10.dp))
                    FilledTonalButton(
                        onClick = {
                            HapticUtil.performUIHaptic(context)
                            feedViewModel.toggleFollow(authorId)
                        },
                        enabled = isFollowing != null,
                        shape = RoundedCornerShape(100.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = if (isFollowing == true) Color.White.copy(alpha = 0.16f) else ringColor,
                            contentColor = if (isFollowing == true) Color.White else MaterialTheme.colorScheme.onPrimary,
                            disabledContainerColor = Color.White.copy(alpha = 0.10f),
                            disabledContentColor = Color.White.copy(alpha = 0.5f),
                        ),
                        modifier = Modifier.height(32.dp),
                    ) {
                        Text(
                            text = stringResource(if (isFollowing == true) R.string.feed_unfollow else R.string.feed_follow),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        // Play button, bottom right, wrapped in the preview progress ring
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = feedBottomClearance())
                .size(64.dp)
                .clickable {
                    HapticUtil.performUIHaptic(context)
                    playerViewModel.togglePlayPause()
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.size(64.dp)) {
                val stroke = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                drawCircle(
                    color = Color.White.copy(alpha = 0.28f),
                    radius = size.minDimension / 2f - stroke.width / 2f,
                    style = stroke,
                )
                if (ringFraction > 0.005f) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f,
                        sweepAngle = 360f * ringFraction,
                        useCenter = false,
                        style = stroke,
                    )
                }
            }
            FilledTonalIconButton(
                onClick = {
                    HapticUtil.performUIHaptic(context)
                    playerViewModel.togglePlayPause()
                },
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = Color.White,
                    contentColor = Color(0xFF14141A),
                ),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    if (isPreviewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(if (isPreviewPlaying) R.string.btn_pause else R.string.btn_play),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }

    TrackInfoSheet(
        track = post.track,
        resolvedSource = playerViewModel.currentStreamSource,
        expanded = showTrackInfo,
        onDismiss = { showTrackInfo = false },
    )
}

@Composable
private fun FeedRailButton(
    icon: ImageVector,
    contentDescription: String,
    count: Int?,
    tint: Color,
    iconScale: Float = 1f,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledTonalIconButton(
            onClick = onClick,
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.14f),
                contentColor = tint,
            ),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                icon,
                contentDescription = contentDescription,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    },
            )
        }
        if (count != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = formatFeedCount(count),
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
private fun FeedMessage(
    text: String,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.height(14.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (trailing != null) {
            Spacer(Modifier.height(8.dp))
            trailing()
        }
    }
}

@Composable
private fun FeedSpinner() {
    CircularProgressIndicator(
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(40.dp),
    )
}

private fun formatFeedCount(count: Int): String = when {
    count >= 1_000_000 -> formatCompact(count / 1_000_000f) + "M"
    count >= 1_000 -> formatCompact(count / 1_000f) + "K"
    else -> count.toString()
}

private fun formatCompact(value: Float): String {
    val rounded = String.format(Locale.US, "%.1f", value)
    return if (rounded.endsWith(".0")) rounded.dropLast(2) else rounded
}
