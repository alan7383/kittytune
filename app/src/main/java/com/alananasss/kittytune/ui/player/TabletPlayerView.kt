package com.alananasss.kittytune.ui.player

import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticClickable
import com.alananasss.kittytune.ui.common.hapticCombinedClickable
import android.content.Context
import android.content.SharedPreferences
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.ui.player.cover.AnimatedArtwork
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.InlineLyricsSide
import com.alananasss.kittytune.data.local.LyricsUnderCoverPlacement
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.audio.haptics.performAppHaptic
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.rememberWindowSizeInfo
import com.alananasss.kittytune.ui.player.rememberInlineLyricsSide
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.player.lyrics.PlayerInlineLyrics

// Full-screen tablet player.
//
// - Landscape: square (never cropped) cover on the left, controls on the right. When inline
//   lyrics are toggled on, the cover STAYS on the left and the lyrics take the place of the
//   transport controls on the right.
// - Portrait: single centered column whose cover is sized to leave room for the header, the
//   title/artist block, the progress bar and the transport controls - the same way the phone
//   layout computes it, so the cover no longer eats the whole screen.
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TabletFullScreenPlayerView(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    mainContentColor: Color,
    subContentColor: Color,
    iconTint: Color,
    animatedColor: Color,
    isBlurMode: Boolean,
    modifier: Modifier = Modifier
) {
    val track = viewModel.uiCurrentTrack ?: return
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        val windowInfo = rememberWindowSizeInfo(maxWidth, maxHeight)
        // Landscape / EXPANDED tablets unlock the true two-pane player; portrait tablets
        // keep a single centered column that is wider than a phone's.
        val isLandscapeTablet = windowInfo.isTablet && windowInfo.isLandscape
        // "Text on the left / on the right" setting: tablets follow it too. LEFT swaps the
        // cover pane for the lyrics, RIGHT (default) keeps the lyrics in the right pane.
        // Lyrics opened by tapping the title (lyricsViaTitleCycle) always take
        // the classic "cover on the left - lyrics on the right" route, bypassing
        // the mini-style pane: the user cycles modes by tapping and expects this layout.
        val inlineLyricsSide = rememberInlineLyricsSide()
        // Mini-style landscape lyrics works on tablets as well: with it on, the lyrics
        // always take the LEFT pane while the right pane keeps its standard controls -
        // the compact-cover rework stays a phone-only affair.
        val context = androidx.compose.ui.platform.LocalContext.current
        val miniLandscapeLyrics = remember { com.alananasss.kittytune.data.local.PlayerPreferences(context).getLandscapeMiniLyrics() }
        val lyricsOnLeft = isLandscapeTablet &&
                (inlineLyricsSide == InlineLyricsSide.LEFT ||
                        (miniLandscapeLyrics && viewModel.showInlineLyrics && !viewModel.lyricsViaTitleCycle))

        // "Full screen" mode for mini-style lyrics on tablets: reset when the lyrics are closed
        var isImmersiveLyrics by remember { mutableStateOf(false) }
        LaunchedEffect(viewModel.showInlineLyrics) {
            if (!viewModel.showInlineLyrics) isImmersiveLyrics = false
        }

        // Portrait: the cover is measured against the ACTUAL leftover space (a nested
        // BoxWithConstraints), so it is always a perfect square that fits both width and
        // height - the artwork is never cropped and never overflows the column.

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top bar: hidden in the mini-style lyrics full-screen mode
            AnimatedVisibility(visible = !(miniLandscapeLyrics && viewModel.showInlineLyrics && isImmersiveLyrics)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose, shapes = IconButtonDefaults.shapes()) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.btn_close),
                            tint = mainContentColor
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp)
                ) {
                    Text(
                        text = viewModel.playbackDeviceLabel ?: stringResource(R.string.player_playing_now),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = subContentColor
                    )
                    Text(
                        text = viewModel.currentContext?.displayText ?: track.title ?: "",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = mainContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.hapticClickable { viewModel.navigateToContext() }
                    )
                }

                IconButton(
                    onClick = hapticAction(HapticKind.UI) { viewModel.showTrackOptions(track, fromPlayer = true) },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.btn_options),
                        tint = mainContentColor
                    )
                }
            }
            } // AnimatedVisibility (top bar)

            if (isLandscapeTablet) {
                // ── Landscape tablet: full square cover on the left, controls (or inline lyrics)
                //    on the right ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(top = 12.dp, bottom = 4.dp)
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Cover is a perfect square bounded by the pane itself: it can never
                        // bleed past the screen edge, and it is capped so it doesn't dominate
                        // the whole side of the layout.
                        val landscapeCoverSize = (minOf(maxWidth - 48.dp, maxHeight - 40.dp))
                            .coerceAtLeast(200.dp)
                            .coerceAtMost(460.dp)
                        TabletArtworkPane(
                            viewModel = viewModel,
                            track = track,
                            accentColor = animatedColor,
                            isBlurMode = isBlurMode,
                            fillPane = true,
                            fixedSize = landscapeCoverSize,
                            // "Text on the left" mode: the lyrics replace the cover here,
                            // no X - the text button in the controls column toggles back.
                            alwaysShowCover = !lyricsOnLeft,
                            modifier = Modifier
                        )
                    }

                    Spacer(Modifier.width(36.dp))

                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight()
                    ) {
                        AnimatedContent(
                            targetState = !lyricsOnLeft && viewModel.showInlineLyrics,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(300))
                            },
                            label = "TabletLandscapeControlsOrLyrics",
                            modifier = Modifier.fillMaxSize()
                        ) { isLyrics ->
                            if (isLyrics) {
                                // Lyrics replace the pause/skip cluster on the right; the cover
                                // remains visible on the left. The top-end X exits back to the
                                // familiar controls view.
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    InlineLyricsContent(viewModel = viewModel)
                                    IconButton(
                                        onClick = hapticAction(HapticKind.UI) { viewModel.toggleInlineLyrics() },
                                        shapes = IconButtonDefaults.shapes(),
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Close,
                                            contentDescription = stringResource(R.string.btn_close),
                                            tint = mainContentColor
                                        )
                                    }
                                }
                            } else if (miniLandscapeLyrics && viewModel.showInlineLyrics && !viewModel.lyricsViaTitleCycle) {
                                // ── Mini-style lyrics: lyrics already in the left column; on the right -
                                //    the cover at the title, the "Full screen" pill, slider and transport ──
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    com.alananasss.kittytune.ui.player.lyrics.MiniLyricsSidePane(
                                        viewModel = viewModel,
                                        isImmersive = isImmersiveLyrics,
                                        onToggleImmersive = { isImmersiveLyrics = !isImmersiveLyrics },
                                        onCloseLyrics = { viewModel.showInlineLyrics = false },
                                        onOpenMenu = { viewModel.showTrackOptions(track, fromPlayer = true) },
                                        accentColor = mainContentColor.copy(alpha = 0.18f),
                                        onAccentColor = mainContentColor,
                                        sliderComposable = {
                                            PlayerProgress(viewModel, mainContentColor)
                                        },
                                        codecBadgeComposable = {
                                            com.alananasss.kittytune.ui.player.CodecBadge(
                                                viewModel = viewModel,
                                                textColor = mainContentColor
                                            )
                                        },
                                        playbackControlsComposable = {
                                            PlayerControls(
                                                viewModel = viewModel,
                                                animatedMainColor = animatedColor,
                                                contentColorOverride = mainContentColor,
                                                onEffectsClick = { showEffectsSheet = true },
                                                onQueueClick = { showQueueSheet = true }
                                            )
                                        },
                                        modifier = Modifier
                                            .widthIn(max = 520.dp)
                                            .fillMaxWidth()
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    TabletPlayerControlsColumn(
                                        viewModel = viewModel,
                                        track = track,
                                        mainContentColor = mainContentColor,
                                        subContentColor = subContentColor,
                                        iconTint = iconTint,
                                        accentColor = animatedColor,
                                        onShowEffects = { showEffectsSheet = true },
                                        onShowQueue = { showQueueSheet = true },
                                        modifier = Modifier
                                            .widthIn(max = 520.dp)
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // ─────────────────────────────────────────────────────────────
                // Mini-style lyrics in TABLET PORTRAIT: lyrics on top, cover
                // next to the title, slider and transport below; "Full screen"
                // hides the pane. The landscape path is in the branch above.
                // Lyrics opened by tapping the title take the classic
                // route (text instead of the cover) - as in landscape.
                // ─────────────────────────────────────────────────────────────
                if (miniLandscapeLyrics && viewModel.showInlineLyrics && !viewModel.lyricsViaTitleCycle) {
                    com.alananasss.kittytune.ui.player.lyrics.MiniImmersiveLyricsView(
                        viewModel = viewModel,
                        isImmersive = isImmersiveLyrics,
                        onToggleImmersive = { isImmersiveLyrics = !isImmersiveLyrics },
                        onCloseLyrics = { viewModel.showInlineLyrics = false },
                        onOpenMenu = { viewModel.showTrackOptions(track, fromPlayer = true) },
                        accentColor = mainContentColor.copy(alpha = 0.18f),
                        onAccentColor = mainContentColor,
                        sliderComposable = {
                            PlayerProgress(viewModel, mainContentColor)
                        },
                        codecBadgeComposable = {
                            com.alananasss.kittytune.ui.player.CodecBadge(
                                viewModel = viewModel,
                                textColor = mainContentColor
                            )
                        },
                        playbackControlsComposable = {
                            PlayerControls(
                                viewModel = viewModel,
                                animatedMainColor = animatedColor,
                                contentColorOverride = mainContentColor,
                                onEffectsClick = { showEffectsSheet = true },
                                onQueueClick = { showQueueSheet = true }
                            )
                        },
                        applyInsets = false
                    )
                } else {
                // ── Portrait tablet: one centered column, cover sized to the window ──
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        BoxWithConstraints(contentAlignment = Alignment.Center) {
                            val coverSize = (minOf(maxWidth - 16.dp, maxHeight - 24.dp))
                                .coerceAtLeast(200.dp)
                            TabletArtworkPane(
                                viewModel = viewModel,
                                track = track,
                                accentColor = animatedColor,
                                isBlurMode = isBlurMode,
                                fillPane = false,
                                fixedSize = coverSize,
                                carousel = true,
                                modifier = Modifier
                            )
                        }
                    }

                    TabletPlayerControlsColumn(
                        viewModel = viewModel,
                        track = track,
                        mainContentColor = mainContentColor,
                        subContentColor = subContentColor,
                        iconTint = iconTint,
                        accentColor = animatedColor,
                        onShowEffects = { showEffectsSheet = true },
                        onShowQueue = { showQueueSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    )
                }
                } // Mini-style lyrics in tablet portrait: closes the else above
            }
        }
    }

    if (showEffectsSheet) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showEffectsSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            AudioControlDock(viewModel)
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showQueueSheet) {
        com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
            onDismissRequest = { showQueueSheet = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            QueueContent(
                viewModel = viewModel,
                isQueueOpen = true,
                onCloseQueue = { showQueueSheet = false },
                onOpenExpandedQueue = {
                    showQueueSheet = false
                    viewModel.navigateToExpandedQueue()
                }
            )
        }
    }
}

// Cover art / inline lyrics area for the tablet player.
//
// @param fillPane true (landscape two-pane): the square cover fills the pane height;
//                 false (portrait): the cover is a fixed square sized by the caller.
// @param fixedSize explicit square size for the portrait layout (computed from the window).
// @param alwaysShowCover keeps the artwork visible even when inline lyrics are active
//        (landscape lyrics mode: lyrics live on the right, cover stays on the left).
// @param carousel portrait-tablet only: render a swipeable queue carousel where the
//        previous/next album edges peek in from the screen edges, like the phone modern
//        player. The landscape two-pane player keeps the single fixed cover.
@Composable
private fun TabletArtworkPane(
    viewModel: PlayerViewModel,
    track: Track,
    accentColor: Color,
    isBlurMode: Boolean,
    fillPane: Boolean,
    fixedSize: Dp? = null,
    alwaysShowCover: Boolean = false,
    carousel: Boolean = false,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = viewModel.showInlineLyrics && !alwaysShowCover,
        transitionSpec = {
            fadeIn(tween(300)) togetherWith fadeOut(tween(300))
        },
        label = "TabletCoverOrLyrics",
        modifier = modifier
    ) { isLyrics ->
        if (isLyrics) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                InlineLyricsContent(viewModel = viewModel)
            }
        } else {
            val cardShape = if (fillPane) RoundedCornerShape(24.dp) else RoundedCornerShape(20.dp)
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (carousel) {
                    TabletArtworkCarousel(
                        viewModel = viewModel,
                        accentColor = accentColor,
                        isBlurMode = isBlurMode,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                val coverModifier = if (fixedSize != null) {
                    Modifier.size(fixedSize)
                } else {
                    Modifier.fillMaxHeight().aspectRatio(1f, matchHeightConstraintsFirst = true)
                }
                Box(
                    modifier = coverModifier
                        .shadow(
                            elevation = if (fillPane) 28.dp else 20.dp,
                            shape = cardShape,
                            spotColor = if (isBlurMode) Color.Black else accentColor
                        )
                        .clip(cardShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .hapticClickable {
                            if (viewModel.hasLyrics) {
                                viewModel.openLyrics()
                            }
                        }
                ) {
                    // AnimatedArtwork = static cover + the animated cover video overlay
                    // (CanvasVideo), exactly like the phone modern player - so animated
                    // covers actually play on the tablet too.
                    AnimatedArtwork(
                        artworkUrl = track.fullResArtwork,
                        animatedCoverUrl = viewModel.currentAnimatedCoverUrl,
                        isPlaying = viewModel.uiIsPlaying,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .viewableCover(track.fullResArtwork)
                    )
                }
                }
            }
        }
    }
}

// Portrait-tablet cover carousel: a swipeable HorizontalPager synced with the playback
// queue. The square is deliberately capped 96dp below the pane width so the neighbouring
// covers peek in from both screen edges, mirroring the phone modern player.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabletArtworkCarousel(
    viewModel: PlayerViewModel,
    accentColor: Color,
    isBlurMode: Boolean,
    modifier: Modifier = Modifier
) {
    val track = viewModel.uiCurrentTrack ?: return

    val pagerState = rememberPagerState(
        initialPage = viewModel.uiCurrentQueueIndex.coerceAtLeast(0),
        pageCount = { viewModel.uiQueueState.size.takeIf { it > 0 } ?: 1 }
    )

    LaunchedEffect(viewModel.uiCurrentQueueIndex) {
        if (viewModel.uiCurrentQueueIndex >= 0 && viewModel.uiCurrentQueueIndex != pagerState.currentPage && viewModel.uiCurrentQueueIndex < pagerState.pageCount) {
            try {
                pagerState.animateScrollToPage(viewModel.uiCurrentQueueIndex)
            } catch (e: Exception) {
                pagerState.scrollToPage(viewModel.uiCurrentQueueIndex)
            }
        }
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            if (settledPage != viewModel.uiCurrentQueueIndex && settledPage in viewModel.uiQueueState.indices) {
                viewModel.skipToQueueItem(settledPage)
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val coverSize = minOf(maxWidth - 96.dp, maxHeight - 24.dp)
            .coerceAtLeast(200.dp)
        val peekPadding = ((maxWidth - coverSize) / 2).coerceAtLeast(0.dp)

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            contentPadding = PaddingValues(horizontal = peekPadding)
        ) { page ->
            val pageTrack = viewModel.uiQueueState.getOrNull(page) ?: track
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .shadow(
                            elevation = 20.dp,
                            shape = RoundedCornerShape(20.dp),
                            spotColor = if (isBlurMode) Color.Black else accentColor
                        )
                        .clip(RoundedCornerShape(20.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .hapticClickable {
                            if (viewModel.hasLyrics) {
                                viewModel.openLyrics()
                            }
                        }
                ) {
                    AnimatedArtwork(
                        artworkUrl = pageTrack.fullResArtwork,
                        animatedCoverUrl = if (pageTrack.id == track.id) viewModel.currentAnimatedCoverUrl else null,
                        isPlaying = viewModel.uiIsPlaying,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .viewableCover(pageTrack.fullResArtwork)
                    )
                }
            }
        }
    }
}

// Title + artist + badges, progress and transport controls.
// Shared by the portrait and landscape tablet layouts so they stay in sync.
//
// Tapping the title/artist block cycles the lyrics modes: title -> lyric line
// instead of the title ("Lyrics under cover" placement: above / instead of) ->
// the lyrics pane (cover left, text right in landscape) -> back to the title.
// Long-press opens the track details.
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TabletPlayerControlsColumn(
    viewModel: PlayerViewModel,
    track: Track,
    mainContentColor: Color,
    subContentColor: Color,
    iconTint: Color,
    accentColor: Color,
    onShowEffects: () -> Unit,
    onShowQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playerPrefs = remember { PlayerPreferences(context) }
    var lyricsUnderCoverPlacement by remember { mutableStateOf(playerPrefs.getLyricsUnderCoverPlacement()) }
    DisposableEffect(context) {
        val sp = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PlayerPreferences.KEY_LYRICS_UNDER_COVER_PLACEMENT) {
                lyricsUnderCoverPlacement = playerPrefs.getLyricsUnderCoverPlacement()
            }
        }
        sp.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                // Lyrics shown ABOVE the title+artist block (placement setting: above).
                // Hidden while the lyrics pane is open (otherwise the text doubles).
                if (viewModel.isLyricsUnderCoverActive &&
                    !viewModel.showInlineLyrics &&
                    lyricsUnderCoverPlacement == LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST
                ) {
                    PlayerInlineLyrics(
                        viewModel = viewModel,
                        textColor = mainContentColor,
                        onClick = hapticAction(HapticKind.UI) { viewModel.cycleLyricsFromTitle() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }

                AnimatedContent(
                    targetState = viewModel.isLyricsUnderCoverActive &&
                        !viewModel.showInlineLyrics &&
                        lyricsUnderCoverPlacement == LyricsUnderCoverPlacement.REPLACE_TITLE_ARTIST,
                    transitionSpec = {
                        (fadeIn(tween(300)) togetherWith fadeOut(tween(200)))
                    },
                    label = "TabletTitleLyricsUnderCover"
                ) { showLyricsLine ->
                    if (showLyricsLine) {
                        // Lyrics INSTEAD of the title and artist (placement setting: replace).
                        // Tapping the line advances the title-tap cycle.
                        PlayerInlineLyrics(
                            viewModel = viewModel,
                            textColor = mainContentColor,
                            onClick = hapticAction(HapticKind.UI) { viewModel.cycleLyricsFromTitle() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (viewModel.hasLyrics) {
                                        // Three-step lyrics cycle on title tap
                                        // (as in KittyTune Modern): tap 1 - a lyrics line
                                        // instead of/above the title, tap 2 - the lyrics pane
                                        // (cover on the left - lyrics on the right in landscape),
                                        // tap 3 - no lyrics. Long-press opens the track
                                        // page, as before.
                                        Modifier.hapticCombinedClickable(
                                            onClick = { viewModel.cycleLyricsFromTitle() },
                                            onLongClick = { viewModel.navigateToTrackDetails(track.id, 0) }
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            PremiumMarqueeText(
                                text = track.title ?: stringResource(R.string.untitled_track),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = mainContentColor,
                                edgeGradientWidth = 20.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f, fill = false)
                                        .hapticClickable {
                                            viewModel.navigateToTrackArtist(track)
                                        }
                                ) {
                                    Text(
                                        text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                        color = subContentColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (track.user?.verified == true) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Rounded.Verified,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                val aiResult by com.alananasss.kittytune.audio.ai.AiDetectionManager.result.collectAsState()
                                com.alananasss.kittytune.ui.player.ai.AiDetectionBadge(
                                    result = aiResult,
                                    textColor = mainContentColor,
                                    onClick = { viewModel.showAiDetectionSheet = true },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                                com.alananasss.kittytune.ui.common.TrackSourceBadge(
                                    track = track,
                                    resolvedSource = viewModel.currentStreamSource,
                                    modifier = Modifier.padding(start = 6.dp)
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedVisibility(
                    visible = viewModel.hasLyrics,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(200))
                ) {
                    val view = LocalView.current
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .combinedClickable(
                                onClick = { viewModel.openLyrics() },
                                onLongClick = {
                                    view.performAppHaptic(HapticFeedbackConstants.LONG_PRESS)
                                    viewModel.openLyrics(forceSheet = true)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Description,
                            contentDescription = stringResource(R.string.player_lyrics),
                            tint = if (viewModel.showInlineLyrics) accentColor else iconTint.copy(alpha = 0.8f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                IconButton(
                    onClick = { viewModel.toggleLike() },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = if (viewModel.uiIsLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.desc_like),
                        tint = if (viewModel.uiIsLiked) accentColor else iconTint,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (viewModel.isYourMixActive) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = hapticAction(HapticKind.UI) { viewModel.dislikeCurrentTrackInMix() },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ThumbDown,
                            contentDescription = stringResource(R.string.mix_dislike),
                            tint = iconTint,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            PlayerProgress(viewModel, mainContentColor)
        }

        Box(modifier = Modifier.fillMaxWidth()) {
            PlayerControls(
                viewModel = viewModel,
                animatedMainColor = accentColor,
                contentColorOverride = mainContentColor,
                onEffectsClick = onShowEffects,
                onQueueClick = onShowQueue
            )
        }
    }
}
