package com.alananasss.kittytune.ui.player.apple

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.AppleMusicControlsAlign
import com.alananasss.kittytune.data.local.AppleMusicPortraitArt
import com.alananasss.kittytune.data.local.AppleTabletCoverMode
import com.alananasss.kittytune.data.local.LyricsUnderCoverPlacement
import com.alananasss.kittytune.data.local.PlayerActionButtonSlot
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.common.HapticKind
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.common.hapticAction
import com.alananasss.kittytune.ui.common.hapticCombinedClickable
import com.alananasss.kittytune.ui.player.AppleMusicV2Background
import com.alananasss.kittytune.ui.player.AudioControlDock
import com.alananasss.kittytune.ui.player.CodecBadge
import com.alananasss.kittytune.ui.player.InlineLyricsContent
import com.alananasss.kittytune.ui.player.PlayerControls
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.RepeatMode
import com.alananasss.kittytune.ui.player.SleepTimerDialog
import com.alananasss.kittytune.ui.player.playerDismissGesture
import com.alananasss.kittytune.ui.player.lyrics.MiniImmersiveLyricsView
import com.alananasss.kittytune.ui.player.lyrics.PlayerInlineLyrics
import com.alananasss.kittytune.ui.player.slider.PlayerSlider
import com.alananasss.kittytune.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * A full port of the Apple Music (Liquid Glass) player from the mini-style player.
 *
 * Portrait (phone and tablet), the "Portrait artwork" setting:
 * - Full-bleed (default): the artwork fills the entire top of the screen and
 *   smoothly fades out toward the bottom (DstIn gradient mask), controls panel below.
 * - "No artwork" (SQUARE): on a PHONE the artwork is removed from portrait on
 *   request — only the controls panel remains, centered; on a TABLET the square
 *   cover stays, centered above the controls (an empty portrait panel looked
 *   broken on the big screen). In landscape SQUARE keeps the square artwork on
 *   the left with the controls panel on the right.
 *
 * The animated (motion) artwork follows two switches:
 * - "Animated album covers" (master): the video plays right on the artwork —
 *   the full-bleed overlay, the landscape square cover, the tablet portrait
 *   cover and the iPad "Centred cover" alike.
 * - "Fade animated cover into UI backdrop": only this switch controls the
 *   blurred background layer — with it off the backdrop stays a static blurred
 *   image, exactly like in the KittyTune Modern and Pixel players.
 *
 *
 * Landscape (phone and tablet): the two-column Apple Music interface — a large
 * rounded artwork on the left, a glass controls panel on the right. The
 * "Landscape controls position" setting moves the panel: Right — to the right edge,
 * Centre — centered and noticeably below the geometric center.
 *
 * Tablet + landscape: a separate Apple Music design for iPad — a large
 * artwork, title/artist in the bottom-left corner, a row of glass buttons
 * (like, lyrics, queue, menu) in the bottom-right, full-width progress bar;
 * the "lyrics" button opens synchronized lyrics in the right half of the screen.
 * It is enabled NOT automatically, but with a toggle button inside the player —
 * a tablet icon in the top-right corner (tablet + landscape only).
 *
 * Settings the player listens to live (no restart required):
 * - "Apple Music inspired" — the Apple transport (white buttons without
 *   backgrounds) or the usual KittyTune Modern buttons.
 * - "Hide volume slider" — shows/hides the volume slider below the transport
 *   (a thin track with a capsule thumb that controls the system volume).
 * - "Slider style" — the global progress slider style (Bar / Wavy / Slim /
 *   Squiggly / Expressive). If a style has never been chosen, the signature
 *   thin Apple slider with a vertical capsule thumb is used.
 *
 * Exiting the player: a swipe down anywhere (the same velocity-aware gesture as
 * in the other KittyTune players), the system "Back" button / predictive back
 * (Android 14+), or the close pill at the top. The gesture no longer leaks
 * through to the menu behind the player.
 *
 * Lyrics (in both modes, phone and tablet): mini-style lyrics — the text in the
 * main column, the artwork next to the track title on the right/bottom, and a
 * "Full screen" mode that hides everything except the text, title, artwork,
 * and slider.
 */
@Composable
fun AppleMusicPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    // The separate "iPad" design from Player Personalization: the iPad layout
    // is force-enabled — ONLY on a TABLET and ONLY in
    // LANDSCAPE orientation, without any toggle icon. On phones and in
    // portrait this design shows the regular Apple Music player.
    forceIpadLayout: Boolean = false
) {
    val track = viewModel.uiCurrentTrack ?: return

    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    // ─────────────────────────────────────────────────────────────────────
    // Live Apple Music player settings (Settings → Player Personalization)
    // ─────────────────────────────────────────────────────────────────────
    var appleMusicInspired by remember { mutableStateOf(prefs.getAppleMusicInspired()) }
    var hideVolumeSlider by remember { mutableStateOf(prefs.getHideVolumeSlider()) }
    var controlsAlign by remember { mutableStateOf(prefs.getAppleMusicControlsAlign()) }
    // The iPad player artwork: centered or fullscreen ("iPad" design).
    var tabletCoverMode by remember { mutableStateOf(prefs.getAppleTabletCoverMode()) }
    var portraitArt by remember { mutableStateOf(prefs.getAppleMusicPortraitArt()) }
    var sliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyleOrNull()) }
    // The four bottom-row Liquid Glass buttons in on-screen order: left,
    // two middle, right. In storage: left = slot 1 (always lyrics),
    // right = slot 0 (always queue) — at the user's request lyrics and
    // queue are swapped relative to classic Apple Music;
    // middle = slots 2/3 (speaker and sleep timer by default) — ONLY they
    // are configurable, from the Player Action Panel (2 buttons); the queue
    // and lyrics from settings are hidden.
    var bottomSlots by remember {
        mutableStateOf(
            listOf(
                prefs.getAppleMusicSlot(1),
                prefs.getAppleMusicSlot(2),
                prefs.getAppleMusicSlot(3),
                prefs.getAppleMusicSlot(0)
            )
        )
    }

    // The "Audio settings" sheet (as in KittyTune Modern): opened by the
    // Audio FX button in the bottom row — it used to open the equalizer by mistake.
    var showEffectsSheet by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            when (key) {
                PlayerPreferences.KEY_APPLE_MUSIC_INSPIRED -> appleMusicInspired = prefs.getAppleMusicInspired()
                PlayerPreferences.KEY_HIDE_VOLUME_SLIDER -> hideVolumeSlider = prefs.getHideVolumeSlider()
                PlayerPreferences.KEY_APPLE_MUSIC_CONTROLS_ALIGN -> controlsAlign = prefs.getAppleMusicControlsAlign()
                PlayerPreferences.KEY_APPLE_TABLET_COVER_MODE ->
                    tabletCoverMode = prefs.getAppleTabletCoverMode()
                PlayerPreferences.KEY_APPLE_MUSIC_PORTRAIT_ART -> portraitArt = prefs.getAppleMusicPortraitArt()
                PlayerPreferences.KEY_PLAYER_SLIDER_STYLE -> sliderStyle = prefs.getPlayerSliderStyleOrNull()
                "${PlayerPreferences.KEY_APPLE_MUSIC_SLOT_PREFIX}0",
                "${PlayerPreferences.KEY_APPLE_MUSIC_SLOT_PREFIX}1",
                "${PlayerPreferences.KEY_APPLE_MUSIC_SLOT_PREFIX}2",
                "${PlayerPreferences.KEY_APPLE_MUSIC_SLOT_PREFIX}3" ->
                    bottomSlots = listOf(
                        prefs.getAppleMusicSlot(1),
                        prefs.getAppleMusicSlot(2),
                        prefs.getAppleMusicSlot(3),
                        prefs.getAppleMusicSlot(0)
                    )
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // ── Dismiss gesture: swipe down anywhere on the player + system "Back" ──
    // The same mechanism as in the Modern/classic players: the gesture is
    // handed off to vertically scrollable children (lyrics), but no longer
    // leaks through to the menu behind the player.
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val dismissTargetY = remember(screenHeightPx) { screenHeightPx * 0.88f }
    val dismissProgress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { dismissProgress.snapTo(0f) }

    val handleClose: () -> Unit = {
        scope.launch {
            dismissProgress.animateTo(
                1f,
                animationSpec = tween(150, easing = LinearEasing)
            )
            onClose()
        }
    }

    // "Back" first closes the lyrics, then the player itself.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = !viewModel.showLyricsSheet) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    if (!viewModel.showInlineLyrics) dismissProgress.snapTo(backEvent.progress)
                }
                if (viewModel.showInlineLyrics) viewModel.showInlineLyrics = false else handleClose()
            } catch (e: Exception) {
                scope.launch {
                    dismissProgress.animateTo(
                        0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        }
    } else {
        BackHandler(enabled = !viewModel.showLyricsSheet) {
            if (viewModel.showInlineLyrics) viewModel.showInlineLyrics = false else handleClose()
        }
    }

    // The lyrics "Full screen" mode: reset when leaving the lyrics
    var isImmersiveLyrics by remember { mutableStateOf(false) }
    LaunchedEffect(viewModel.showInlineLyrics) {
        if (!viewModel.showInlineLyrics) isImmersiveLyrics = false
    }

    val artworkUrl = track.fullResArtwork
    // The motion artwork that rides ON the artwork itself: for square covers the
    // SQUARE canvas is used (the tall one is reserved for the full-bleed
    // backdrop and the iPad full-screen cover, where its portrait aspect fits).
    val canvasUrl = viewModel.currentAnimatedCoverTallUrl ?: viewModel.currentAnimatedCoverUrl
    val squareCanvasUrl = viewModel.currentAnimatedCoverUrl

    // "Fade animated cover into UI backdrop": the blurred background layer plays
    // the motion artwork ONLY while this setting is enabled — the same gating as
    // in the KittyTune Modern and Pixel players. Before the fix the Apple Music
    // backdrop ignored the switch and animated even with it off (most visible in
    // the landscape "No artwork" layout, where the background was the only
    // moving element). Live: the toggle applies without reopening the player.
    val fadeUiEnabled by prefs.getAnimatedCoversFadeUiFlow().collectAsState(
        initial = prefs.getAnimatedCoversFadeUiEnabled()
    )
    val backdropCanvasUrl = if (fadeUiEnabled) canvasUrl else null

    // Track clipping (ClipWindow): progress is counted in "shown" milliseconds
    val clip = viewModel.uiClipWindow
    val shownOf = { songMs: Long -> clip?.toShown(songMs) ?: songMs }
    val songOf = { shownMs: Long -> clip?.toSong(shownMs) ?: shownMs }
    val totalDuration = (clip?.lengthMs
        ?: viewModel.uiDuration.takeIf { it > 1000L }
        ?: track.actualDurationMs.takeIf { it > 1000L }
        ?: 180000L).coerceAtLeast(1L)

    val progressFraction = rememberAppleProgressFraction(
        viewModel = viewModel,
        totalDuration = totalDuration,
        shownOf = shownOf
    )
    val onSeekFraction: (Float) -> Unit = { fraction ->
        viewModel.seekTo(songOf((fraction * totalDuration).toLong()))
    }

    // Artwork "shrink" animation while paused — shared by landscape and portrait
    val coverScale by animateFloatAsState(
        targetValue = if (viewModel.uiIsPlaying) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "appleCoverScale"
    )

    val pProgress = dismissProgress.value
    val clampedProgress = if (pProgress.isFinite()) pProgress.coerceIn(0f, 1f) else 0f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .playerDismissGesture(
                dismissTargetY = dismissTargetY,
                dismissProgress = dismissProgress,
                scope = scope,
                onDismiss = handleClose
            )
            .graphicsLayer {
                scaleX = (1f - (clampedProgress * 0.06f)).coerceIn(0.8f, 1f)
                scaleY = (1f - (clampedProgress * 0.04f)).coerceIn(0.8f, 1f)
                translationY = pProgress.coerceAtLeast(0f) * dismissTargetY
                transformOrigin = TransformOrigin(0.5f, 1.0f)
                shape = RoundedCornerShape((clampedProgress * 32.dp.value).coerceAtLeast(0f).dp)
                // An explicit receiver is required: the local val clip (the track
                // clip window) above in the function shadows GraphicsLayerScope.clip
                // (K2 allows assignment to the local val, hence "'val' cannot be reassigned").
                this.clip = clampedProgress > 0.001f
                alpha = (1f - (clampedProgress * 0.15f)).coerceIn(0f, 1f)
            }
            .background(Color(0xFF101318))
    ) {
        val isLandscape = maxWidth > maxHeight
        val isTablet = maxWidth >= 600.dp && maxHeight >= 600.dp
        // iPad design: ONLY tablet, ONLY landscape and ONLY
        // from the separate "iPad" design in Player Personalization
        // (forceIpadLayout). The toggle icon in the top-right corner
        // was removed on request: the Apple Music design should not have it,
        // the iPad layout can only be enabled by picking the matching design.
        // On phones and in portrait this layout is never shown —
        // the regular Apple Music player works as before.
        val isTabletLandscape = isTablet && isLandscape && forceIpadLayout
        // Local copies of constraints for nested non-inline lambdas (the content
        // Column/Box, etc.): K2 forbids accessing members of the outer
        // implicit receiver (BoxWithConstraintsScope) inside such lambdas.
        val playerMaxWidth = maxWidth
        val playerMaxHeight = maxHeight

        // ── Apple Music background: heavily blurred artwork + animated (video) artwork.
        //    The animated layer follows the "Fade animated cover into UI backdrop"
        //    switch (backdropCanvasUrl is null when it is off). ──
        AppleMusicV2Background(
            artworkUrl = artworkUrl,
            animatedCoverUrl = backdropCanvasUrl,
            isPlaying = viewModel.uiIsPlaying,
            modifier = Modifier.fillMaxSize()
        )

        if (isTabletLandscape) {
            // =================================================================
            // TABLET + LANDSCAPE: the Apple Music design for iPad — for the song
            // view and for lyrics alike. The "lyrics" button in the bottom-right
            // row toggles the lyrics panel right inside this layout.
            // =================================================================
            AppleTabletLandscapePlayer(
                viewModel = viewModel,
                track = track,
                artworkUrl = artworkUrl,
                animatedCoverUrl = canvasUrl,
                squareAnimatedCoverUrl = squareCanvasUrl,
                progressFraction = progressFraction.value,
                totalDuration = totalDuration,
                onSeek = onSeekFraction,
                sliderStyle = sliderStyle,
                coverMode = tabletCoverMode
            )
        } else if (viewModel.showInlineLyrics) {
            // =================================================================
            // LYRICS (mini-style): in landscape the text is on the left / panel on
            // the right, in portrait the text is on top / panel on the bottom;
            // "Full screen" hides the transport.
            // =================================================================
            MiniImmersiveLyricsView(
                viewModel = viewModel,
                isImmersive = isImmersiveLyrics,
                onToggleImmersive = { isImmersiveLyrics = !isImmersiveLyrics },
                onCloseLyrics = { viewModel.showInlineLyrics = false },
                onOpenMenu = { viewModel.showTrackOptions(track, fromPlayer = true) },
                accentColor = Color.White.copy(alpha = 0.22f),
                onAccentColor = Color.White,
                sliderComposable = {
                    AppleProgressBlock(
                        viewModel = viewModel,
                        fraction = progressFraction.value,
                        totalDuration = totalDuration,
                        onSeek = onSeekFraction,
                        sliderStyle = sliderStyle
                    )
                },
                playbackControlsComposable = {
                    AppleTransportSlot(
                        onEffectsClick = {},
                        viewModel = viewModel,
                        track = track,
                        appleMusicInspired = appleMusicInspired,
                        compact = isLandscape && !isTablet
                    )
                },
                bottomRowComposable = {
                    // The bottom liquid row — only with the Apple styling enabled:
                    // with "Apple Music inspired" off, its buttons are removed.
                    if (appleMusicInspired) {
                        AppleBottomLiquidBar(
                            viewModel = viewModel,
                            track = track,
                            bottomSlots = bottomSlots,
                            onFullscreenLyrics = {
                                viewModel.showInlineLyrics = true
                                isImmersiveLyrics = true
                            },
                            isLyricsActive = true,
                            onQueueClick = { viewModel.navigateToExpandedQueue() },
                            onLyricsClick = { viewModel.showInlineLyrics = false },
                            onEffectsClick = { showEffectsSheet = true }
                        )
                    }
                }
            )
        } else {
            // ── Scrim: readability of the white Liquid Glass elements ──
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.06f),
                                Color.Black.copy(alpha = 0.26f),
                                Color.Black.copy(alpha = 0.50f)
                            )
                        )
                    )
            )

            if (isLandscape) {
                if (portraitArt == AppleMusicPortraitArt.SQUARE) {
                    // =================================================================
                    // APPLE MUSIC: LANDSCAPE MODE, SQUARE ARTWORK
                    // (phone and tablet). Artwork on the left, glass controls
                    // panel on the right, as in Apple Music.
                    // =================================================================
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(
                                start = if (isTablet) 48.dp else 20.dp,
                                end = if (isTablet) 48.dp else 20.dp,
                                top = if (isTablet) 24.dp else 10.dp,
                                bottom = if (isTablet) 24.dp else 10.dp
                            ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: the artwork (a perfect square bounded by the pane,
                        // so it is never cropped and never runs off the screen)
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(if (isTablet) 0.46f else 0.42f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.Center
                        ) {
                                                        val paneCoverSize = minOf(maxWidth, maxHeight)
                                .coerceAtLeast(180.dp)
                            Box(
                                modifier = Modifier
                                    .size(paneCoverSize)
                                    .scale(coverScale)
                                    .shadow(
                                        elevation = 28.dp,
                                        shape = RoundedCornerShape(if (isTablet) 28.dp else 20.dp)
                                    )
                                    .clip(RoundedCornerShape(if (isTablet) 28.dp else 20.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = artworkUrl,
                                    contentDescription = track.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                if (squareCanvasUrl != null) {
                                    com.alananasss.kittytune.ui.player.cover.CanvasVideo(
                                        canvasUrl = squareCanvasUrl,
                                        isPlaying = viewModel.uiIsPlaying,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(if (isTablet) 40.dp else 20.dp))

                        // Right side: the controls panel at the right edge, as in Apple Music
                        Box(
                            modifier = Modifier
                                .weight(if (isTablet) 0.54f else 0.58f)
                                .fillMaxHeight(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            AppleControlsColumn(
                                viewModel = viewModel,
                                track = track,
                                bottomSlots = bottomSlots,
                                onFullscreenLyrics = {
                                    viewModel.showInlineLyrics = true
                                    isImmersiveLyrics = true
                                },
                                compactTransport = !isTablet,
                                showVolumeSlider = !hideVolumeSlider,
                                appleMusicInspired = appleMusicInspired,
                                sliderStyle = sliderStyle,
                                progressFraction = progressFraction.value,
                                totalDuration = totalDuration,
                                onSeek = onSeekFraction,
                                isLyricsActive = false,
                                onEffectsClick = { showEffectsSheet = true },
                                modifier = Modifier
                                    .widthIn(max = 520.dp)
                                    .fillMaxHeight()
                            )
                        }
                    }
                } else {
                    // =================================================================
                    // APPLE MUSIC: LANDSCAPE MODE, FULL-SCREEN BACKGROUND
                    // (phone and tablet). The sharp artwork fills the whole screen and
                    // fades out toward the bottom, the glass controls panel floats
                    // on top of it. The "Landscape controls position" setting (visible
                    // only for this mode): Right — the panel at the right edge,
                    // Centre — the panel in the center of the screen.
                    // =================================================================
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.00f to Color.Black,
                                                0.72f to Color.Black,
                                                0.92f to Color.Black.copy(alpha = 0.4f),
                                                1.00f to Color.Transparent
                                            )
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (canvasUrl != null) {
                                com.alananasss.kittytune.ui.player.cover.CanvasVideo(
                                    canvasUrl = canvasUrl,
                                    isPlaying = viewModel.uiIsPlaying,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // The floating controls panel on top of the artwork
                        // BoxWithConstraints: the container height is needed to
                        // shift the "centered" panel genuinely below the center.
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .windowInsetsPadding(WindowInsets.safeDrawing)
                                .padding(
                                    start = if (isTablet) 48.dp else 20.dp,
                                    end = if (isTablet) 48.dp else 20.dp,
                                    top = if (isTablet) 24.dp else 10.dp,
                                    bottom = if (isTablet) 24.dp else 10.dp
                                )
                        ) {
                            // "Centered" = noticeably below the geometric center:
                            // a fixed downward shift, 7% of the container height
                            // (24…64dp). The old BiasAlignment(0f, 0.2f) achieved
                            // nothing at all — on a short landscape screen the
                            // panel takes up almost the whole height, leaving no
                            // room for BiasAlignment to work with.
                            val centreLowShift = (maxHeight * 0.07f).coerceIn(24.dp, 64.dp)
                            AppleControlsColumn(
                                viewModel = viewModel,
                                track = track,
                                bottomSlots = bottomSlots,
                                onFullscreenLyrics = {
                                    viewModel.showInlineLyrics = true
                                    isImmersiveLyrics = true
                                },
                                compactTransport = !isTablet,
                                showVolumeSlider = !hideVolumeSlider,
                                appleMusicInspired = appleMusicInspired,
                                sliderStyle = sliderStyle,
                                progressFraction = progressFraction.value,
                                totalDuration = totalDuration,
                                onSeek = onSeekFraction,
                                isLyricsActive = false,
                                onEffectsClick = { showEffectsSheet = true },
                                modifier = Modifier
                                    .widthIn(max = if (isTablet) 500.dp else 420.dp)
                                    .align(
                                        when (controlsAlign) {
                                            // "Centered" (formerly "Slightly below center"):
                                            // the panel centered + shifted down.
                                            AppleMusicControlsAlign.CENTER_LOW -> Alignment.Center
                                            AppleMusicControlsAlign.RIGHT -> Alignment.CenterEnd
                                        }
                                    )
                                    .offset(
                                        y = if (controlsAlign == AppleMusicControlsAlign.CENTER_LOW) {
                                            centreLowShift
                                        } else {
                                            0.dp
                                        }
                                    )
                            )
                        }
                    }
                }
            } else {
                // =================================================================
                // APPLE MUSIC: PORTRAIT MODE (phone and tablet)
                // The "Portrait artwork" setting: Full-bleed (artwork across the
                // whole top with a fade) or SQUARE (no artwork — controls panel only).
                // =================================================================
                if (portraitArt == AppleMusicPortraitArt.SQUARE) {
                    // Artwork removed from portrait on request: only the
                    // controls panel remains, centered. In landscape the
                    // artwork is unchanged (square on the left, panel on the right).
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing)
                            .padding(horizontal = if (isTablet) 48.dp else 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isTablet) {
                            // ── Tablet portrait: the square (animated) cover stays.
                            //    Same treatment as the landscape "No artwork" mode:
                            //    a rounded square artwork, centered above the panel. ──
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                val portraitCoverSize = minOf(
                                    playerMaxWidth * 0.58f,
                                    playerMaxHeight * 0.40f
                                ).coerceAtLeast(180.dp)
                                Box(
                                    modifier = Modifier
                                        .size(portraitCoverSize)
                                        .scale(coverScale)
                                        .shadow(
                                            elevation = 28.dp,
                                            shape = RoundedCornerShape(28.dp)
                                        )
                                        .clip(RoundedCornerShape(28.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AsyncImage(
                                        model = artworkUrl,
                                        contentDescription = track.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (squareCanvasUrl != null) {
                                        com.alananasss.kittytune.ui.player.cover.CanvasVideo(
                                            canvasUrl = squareCanvasUrl,
                                            isPlaying = viewModel.uiIsPlaying,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }

                            AppleControlsColumn(
                                viewModel = viewModel,
                                track = track,
                                bottomSlots = bottomSlots,
                                onFullscreenLyrics = {
                                    viewModel.showInlineLyrics = true
                                    isImmersiveLyrics = true
                                },
                                compactTransport = false,
                                showVolumeSlider = !hideVolumeSlider,
                                appleMusicInspired = appleMusicInspired,
                                sliderStyle = sliderStyle,
                                progressFraction = progressFraction.value,
                                totalDuration = totalDuration,
                                onSeek = onSeekFraction,
                                isLyricsActive = false,
                                onEffectsClick = { showEffectsSheet = true },
                                modifier = Modifier
                                    .widthIn(max = 560.dp)
                                    .fillMaxWidth()
                            )
                            Spacer(Modifier.height(24.dp))
                        } else {
                            // ── Phone portrait: the artwork is removed on request —
                            //    only the controls panel remains, centered. ──
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                AppleControlsColumn(
                                    viewModel = viewModel,
                                    track = track,
                                    bottomSlots = bottomSlots,
                                    onFullscreenLyrics = {
                                        viewModel.showInlineLyrics = true
                                        isImmersiveLyrics = true
                                    },
                                    compactTransport = false,
                                    showVolumeSlider = !hideVolumeSlider,
                                    appleMusicInspired = appleMusicInspired,
                                    sliderStyle = sliderStyle,
                                    progressFraction = progressFraction.value,
                                    totalDuration = totalDuration,
                                    onSeek = onSeekFraction,
                                    isLyricsActive = false,
                                    onEffectsClick = { showEffectsSheet = true },
                                    modifier = Modifier
                                        .widthIn(max = 480.dp)
                                        .fillMaxWidth()
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                } else {
                    // ── Full-bleed: sharp artwork in the upper part, smooth
                    //    fade toward the bottom via a DstIn gradient mask ──
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.64f)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.00f to Color.Black,
                                                0.72f to Color.Black,
                                                0.92f to Color.Black.copy(alpha = 0.4f),
                                                1.00f to Color.Transparent
                                            )
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            if (canvasUrl != null) {
                                com.alananasss.kittytune.ui.player.cover.CanvasVideo(
                                    canvasUrl = canvasUrl,
                                    isPlaying = viewModel.uiIsPlaying,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }

                        // Bottom section with the Apple Music Liquid Glass controls
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .widthIn(max = if (isTablet) 560.dp else 480.dp)
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(
                                    start = if (isTablet) 40.dp else 26.dp,
                                    end = if (isTablet) 40.dp else 26.dp,
                                    top = if (isTablet) 28.dp else 16.dp,
                                    bottom = if (isTablet) 28.dp else 16.dp
                                )
                        ) {
                            AppleControlsColumn(
                                viewModel = viewModel,
                                track = track,
                                bottomSlots = bottomSlots,
                                onFullscreenLyrics = {
                                    viewModel.showInlineLyrics = true
                                    isImmersiveLyrics = true
                                },
                                compactTransport = false,
                                showVolumeSlider = !hideVolumeSlider,
                                appleMusicInspired = appleMusicInspired,
                                sliderStyle = sliderStyle,
                                progressFraction = progressFraction.value,
                                totalDuration = totalDuration,
                                onSeek = onSeekFraction,
                                isLyricsActive = false,
                                onEffectsClick = { showEffectsSheet = true },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        // ── Close pill: on top of the whole player in any orientation (except
        //    fullscreen lyrics), enlarged 88×30dp tap target.
        //    Swipe down and the system "Back" also close the player. ──
        AnimatedVisibility(
            visible = !(viewModel.showInlineLyrics && isImmersiveLyrics),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(10f)
        ) {
            AppleClosePill(
                onClose = handleClose,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 8.dp)
            )
        }

        // ── The iPad design toggle button was removed: in the Apple Music
        //    design nothing in the top right switches to the iPad layout — the only
        //    way to enable it is to pick the "iPad" design in Player
        //    Personalization. ──

        // ── Sleep timer: the dialog was never wired up to the Apple design at all
        //    (the timer button "would not enable and would not show the menu") — now
        //    the menu opens right from the player, from the bottom-row capsule. ──
        SleepTimerDialog(viewModel)

        // ── "Audio settings" as in KittyTune Modern: speed/pitch and effect
        //    cards (Bass Boost, 8D Audio, Reverb…), not an equalizer.
        if (showEffectsSheet) {
            KittyModalBottomSheet(
                onDismissRequest = { showEffectsSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ) {
                AudioControlDock(viewModel)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * The Apple Music controls panel (portrait and landscape, phone and tablet):
 * title/artist, a progress slider with timecodes and a codec badge,
 * the transport (Apple or KittyTune — per the setting), the volume slider (if
 * not hidden) and the bottom row [Queue | Two customizable icons | Lyrics].
 * With "Apple Music inspired" off, all Apple elements (the ⋮/♡ buttons next to
 * the title, the volume slider, the bottom liquid row) are removed — what
 * remains is a clean player with KittyTune Modern buttons.
 */
@Composable
private fun AppleControlsColumn(
    viewModel: PlayerViewModel,
    track: Track,
    bottomSlots: List<PlayerActionButtonSlot>,
    onFullscreenLyrics: () -> Unit,
    compactTransport: Boolean,
    showVolumeSlider: Boolean,
    appleMusicInspired: Boolean,
    sliderStyle: PlayerSliderStyle?,
    progressFraction: Float,
    totalDuration: Long,
    onSeek: (Float) -> Unit,
    isLyricsActive: Boolean,
    onEffectsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Track title, artist and two round glass buttons (⋮ and ♡) —
        // only with the Apple styling enabled.
        AppleTitleAndGlassActions(
            title = track.title ?: stringResource(R.string.untitled_track),
            artist = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
            isLiked = viewModel.uiIsLiked,
            showActions = appleMusicInspired,
            onArtistClick = { viewModel.navigateToTrackArtist(track) },
            onMoreClick = { viewModel.showTrackOptions(track, fromPlayer = true) },
            onLikeClick = { viewModel.toggleLike() }
        )

        Spacer(Modifier.height(16.dp))

        // Slider + timecodes + codec badge (in a single block)
        AppleProgressBlock(
            viewModel = viewModel,
            fraction = progressFraction,
            totalDuration = totalDuration,
            onSeek = onSeek,
            sliderStyle = sliderStyle
        )

        Spacer(Modifier.height(if (compactTransport) 12.dp else 18.dp))

        // Transport: Apple style or KittyTune Modern buttons (per the setting)
        AppleTransportSlot(
            viewModel = viewModel,
            track = track,
            appleMusicInspired = appleMusicInspired,
            compact = compactTransport,
            onEffectsClick = onEffectsClick
        )

        // The volume slider — an Apple styling element: hidden both by the
        // "Hide volume slider" setting and by turning "Apple Music inspired" off.
        if (appleMusicInspired && showVolumeSlider) {
            Spacer(Modifier.height(6.dp))
            AppleVolumeRow()
        }

        // The bottom liquid row [lyrics | capsule | queue] — also only with
        // the Apple styling enabled.
        if (appleMusicInspired) {
            Spacer(Modifier.height(if (compactTransport) 12.dp else 16.dp))
            AppleBottomLiquidBar(
                viewModel = viewModel,
                track = track,
                bottomSlots = bottomSlots,
                onFullscreenLyrics = onFullscreenLyrics,
                isLyricsActive = isLyricsActive,
                onQueueClick = { viewModel.navigateToExpandedQueue() },
                onLyricsClick = { viewModel.showInlineLyrics = !viewModel.showInlineLyrics },
                onEffectsClick = onEffectsClick
            )
        }
    }
}

/**
 * Transport slot: "Apple Music inspired" on — large white Apple buttons
 * (FastRewind / Play-Pause / FastForward without backgrounds); off — the familiar
 * KittyTune Modern buttons (the same ones as in the modern player design).
 */
@Composable
private fun AppleTransportSlot(
    viewModel: PlayerViewModel,
    track: Track,
    appleMusicInspired: Boolean,
    compact: Boolean,
    onEffectsClick: () -> Unit
) {
    if (appleMusicInspired) {
        AppleTransportControls(
            isPlaying = viewModel.uiIsPlaying,
            onPrevious = { viewModel.smartPrevious() },
            onPlayPause = { viewModel.togglePlayPause() },
            onNext = { viewModel.requestSkipNext() },
            compact = compact
        )
    } else {
        PlayerControls(
            viewModel = viewModel,
            onEffectsClick = onEffectsClick,
            onQueueClick = { viewModel.navigateToExpandedQueue() },
            animatedMainColor = viewModel.backgroundColor,
            contentColorOverride = Color.White,
            compact = compact
        )
    }
}

/**
 * The Apple progress slider + timecodes with a codec badge.
 *
 * The slider style comes from the global setting ("Slider style"):
 * Bar / Wavy / Slim / Squiggly / Expressive — in the white Liquid Glass palette.
 * If a style has never been chosen (null), the signature thin Apple slider
 * is drawn: a 5dp track (8dp while dragging) and a vertical capsule thumb.
 */
@Composable
private fun AppleProgressBlock(
    viewModel: PlayerViewModel,
    fraction: Float,
    totalDuration: Long,
    onSeek: (Float) -> Unit,
    sliderStyle: PlayerSliderStyle?
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val activeFraction = (if (isDragging) dragFraction else fraction).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (sliderStyle == null) {
            AppleCapsuleSlider(
                fraction = activeFraction,
                onFractionChanged = { value ->
                    dragFraction = value
                    isDragging = true
                },
                onFractionChangeFinished = {
                    isDragging = false
                    onSeek(dragFraction)
                }
            )
        } else {
            PlayerSlider(
                value = activeFraction,
                onValueChange = { value ->
                    dragFraction = value
                    isDragging = true
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek(dragFraction)
                },
                sliderStyle = sliderStyle,
                isPlaying = viewModel.uiIsPlaying,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(4.dp))

        AppleTimestampsRow(
            currentPos = (activeFraction * totalDuration).toLong(),
            duration = totalDuration,
            viewModel = viewModel
        )
    }
}

/**
 * The Apple volume slider: a thin track with a capsule thumb between the
 * speaker icons. It controls the system STREAM_MUSIC volume in real time and
 * stays in sync with the hardware buttons. Tapping the icons — mute/max.
 * Hidden by the "Hide volume slider" setting.
 */
@Composable
private fun AppleVolumeRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }
    var volumeFraction by remember {
        mutableFloatStateOf(
            audioManager?.let { am ->
                val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                if (max > 0) am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else 0.7f
            } ?: 0.7f
        )
    }

    // Sync with the hardware volume buttons
    LaunchedEffect(audioManager) {
        while (true) {
            val am = audioManager ?: break
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max > 0) {
                volumeFraction = am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
            }
            delay(400)
        }
    }

    fun applyVolume(fraction: Float) {
        val am = audioManager ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max <= 0) return
        val target = (fraction * max).roundToInt().coerceIn(0, max)
        volumeFraction = target.toFloat() / max
        runCatching { am.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0) }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Rounded.VolumeOff,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { applyVolume(0f) }
                .padding(6.dp)
        )

        AppleCapsuleSlider(
            fraction = volumeFraction,
            onFractionChanged = { },
            onFractionChangeFinished = null,
            onLiveFraction = { applyVolume(it) },
            idleTrackHeight = 4.dp,
            draggedTrackHeight = 7.dp,
            thumbHeight = 13.dp,
            thumbWidth = 4.dp,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
        )

        Icon(
            imageVector = Icons.Rounded.VolumeUp,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.72f),
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable { applyVolume(1f) }
                .padding(6.dp)
        )
    }
}

/**
 * The close pill at the top: an enlarged tap target (88×30dp) around the visual
 * 38×4.5dp bar — closing the player is easy even if the gesture did not fire.
 */
@Composable
private fun AppleClosePill(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .width(88.dp)
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClose() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(38.dp)
                .height(4.5.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.38f))
        )
    }
}

/**
 * A thin Apple Music-style capsule slider: a track [idleTrackHeight] thick
 * (thickening to [draggedTrackHeight] while dragging) and a vertical
 * capsule thumb. Tapping and horizontal dragging change the value;
 * [onLiveFraction] fires continuously during the gesture (for volume),
 * [onFractionChangeFinished] on release (for seeking).
 */
@Composable
private fun AppleCapsuleSlider(
    fraction: Float,
    onFractionChanged: (Float) -> Unit,
    onFractionChangeFinished: (() -> Unit)?,
    modifier: Modifier = Modifier,
    idleTrackHeight: Dp = 5.dp,
    draggedTrackHeight: Dp = 8.dp,
    thumbHeight: Dp = 16.dp,
    thumbWidth: Dp = 5.dp,
    onLiveFraction: ((Float) -> Unit)? = null
) {
    var isDragging by remember { mutableStateOf(false) }
    val trackHeight by animateDpAsState(
        targetValue = if (isDragging) draggedTrackHeight else idleTrackHeight,
        animationSpec = tween(150),
        label = "appleCapsuleTrackHeight"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(thumbHeight + 8.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    onFractionChanged(frac)
                    onLiveFraction?.invoke(frac)
                    onFractionChangeFinished?.invoke()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onFractionChanged(frac)
                        onLiveFraction?.invoke(frac)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val frac = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        onFractionChanged(frac)
                        onLiveFraction?.invoke(frac)
                    },
                    onDragEnd = {
                        isDragging = false
                        onFractionChangeFinished?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                    }
                )
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val totalW = maxWidth
        // Background track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.24f))
        )
        // Filled part of the track
        Box(
            modifier = Modifier
                .width(totalW * fraction.coerceIn(0f, 1f))
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.85f))
        )
        // Vertical capsule thumb
        Box(
            modifier = Modifier
                .offset(x = (totalW * fraction.coerceIn(0f, 1f) - 3.dp).coerceAtLeast(0.dp))
                .width(thumbWidth)
                .height(thumbHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.95f))
        )
    }
}

/**
 * Title + artist on the left and two round glass buttons (⋮ menu and ♡ like)
 * on the right — the Apple Music header from the mini-style player.
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppleTitleAndGlassActions(
    title: String,
    artist: String,
    isLiked: Boolean,
    showActions: Boolean,
    onArtistClick: () -> Unit,
    onMoreClick: () -> Unit,
    onLikeClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier.basicMarquee()
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = artist,
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable { onArtistClick() }
            )
        }

        // The glass buttons (⋮ and ♡) — an Apple styling element: with
        // "Apple Music inspired" off only the title and artist remain.
        if (showActions) {
            Spacer(Modifier.width(14.dp))

            // Round glass menu button (⋮)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable { onMoreClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.btn_options),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(Modifier.width(10.dp))

            // Round glass like button (♡)
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f))
                    .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable { onLikeClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = stringResource(R.string.desc_like),
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Timecodes on the left/right, with the codec/quality badge between them (as in the mini-style player). */
@Composable
private fun AppleTimestampsRow(
    currentPos: Long,
    duration: Long,
    viewModel: PlayerViewModel
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = makeTimeString(currentPos),
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        CodecBadge(viewModel = viewModel, textColor = Color.White)
        Text(
            text = makeTimeString(duration),
            color = Color.White.copy(alpha = 0.78f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/** Large white Apple buttons: FastRewind / Play-Pause / FastForward. */
@Composable
private fun AppleTransportControls(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    compact: Boolean = false
) {
    val sideIconSize = if (compact) 42.dp else 50.dp
    val centerIconSize = if (compact) 52.dp else 62.dp

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = hapticAction(HapticKind.UI) { onPrevious() },
            modifier = Modifier.size(sideIconSize + 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FastRewind,
                contentDescription = stringResource(R.string.desc_previous),
                tint = Color.White,
                modifier = Modifier.size(sideIconSize)
            )
        }

        IconButton(
            onClick = hapticAction(HapticKind.UI) { onPlayPause() },
            modifier = Modifier.size(centerIconSize + 14.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.btn_pause else R.string.btn_play),
                tint = Color.White,
                modifier = Modifier.size(centerIconSize)
            )
        }

        IconButton(
            onClick = hapticAction(HapticKind.UI) { onNext() },
            modifier = Modifier.size(sideIconSize + 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.FastForward,
                contentDescription = stringResource(R.string.desc_next),
                tint = Color.White,
                modifier = Modifier.size(sideIconSize)
            )
        }
    }
}

/**
 * The Apple Music bottom row:
 * [Lyrics | Two customizable icons in ONE capsule | Queue].
 * The outer buttons are fixed and hidden from the settings;
 * only the two middle icons are configurable — and with ANY choice they stay
 * in a single glass capsule with a divider (previously swapping the icons
 * broke the capsule into two separate round buttons).
 */
@Composable
private fun AppleBottomLiquidBar(
    viewModel: PlayerViewModel,
    track: Track,
    bottomSlots: List<PlayerActionButtonSlot>,
    onFullscreenLyrics: () -> Unit,
    isLyricsActive: Boolean,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onEffectsClick: () -> Unit
) {
    // On-screen order: left, middle 1, middle 2, right.
    val firstSlot = bottomSlots.getOrNull(1) ?: PlayerActionButtonSlot.CONNECT
    val secondSlot = bottomSlots.getOrNull(2) ?: PlayerActionButtonSlot.SLEEP_TIMER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The left button — always lyrics (a round button with active-state
        // highlighting).
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (isLyricsActive) Color.White.copy(alpha = 0.25f) else Color.Transparent)
                .clickable { onLyricsClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_lyrics_24),
                contentDescription = stringResource(R.string.player_lyrics),
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(24.dp)
            )
        }

        // The central double glass capsule: ONE capsule under any actions.
        // If one of the icons is "None", a single round button is drawn instead.
        when {
            firstSlot == PlayerActionButtonSlot.NONE && secondSlot == PlayerActionButtonSlot.NONE -> Unit
            firstSlot == PlayerActionButtonSlot.NONE ->
                AppleGlassActionButton(
                    slot = secondSlot,
                    viewModel = viewModel,
                    track = track,
                    onFullscreenLyrics = onFullscreenLyrics,
                    onQueueClick = onQueueClick,
                    onLyricsClick = onLyricsClick,
                    onEffectsClick = onEffectsClick,
                    isLyricsActive = isLyricsActive
                )
            secondSlot == PlayerActionButtonSlot.NONE ->
                AppleGlassActionButton(
                    slot = firstSlot,
                    viewModel = viewModel,
                    track = track,
                    onFullscreenLyrics = onFullscreenLyrics,
                    onQueueClick = onQueueClick,
                    onLyricsClick = onLyricsClick,
                    onEffectsClick = onEffectsClick,
                    isLyricsActive = isLyricsActive
                )
            else ->
                AppleGlassCapsule(
                    firstSlot = firstSlot,
                    secondSlot = secondSlot,
                    viewModel = viewModel,
                    track = track,
                    onFullscreenLyrics = onFullscreenLyrics,
                    onQueueClick = onQueueClick,
                    onLyricsClick = onLyricsClick,
                    onEffectsClick = onEffectsClick,
                    isLyricsActive = isLyricsActive
                )
        }

        // The right button — always the queue (a plain icon, as in Apple Music).
        IconButton(onClick = hapticAction(HapticKind.UI) { onQueueClick() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(R.string.player_queue),
                tint = Color.White.copy(alpha = 0.88f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * The Liquid Glass double capsule: ONE plate with two 58dp halves and a thin
 * divider. Each half holds its own configurable action from the Player Action
 * Panel; an active state (shuffle on, like, sleep timer, etc.) highlights the
 * half more brightly.
 */
@Composable
private fun AppleGlassCapsule(
    firstSlot: PlayerActionButtonSlot,
    secondSlot: PlayerActionButtonSlot,
    viewModel: PlayerViewModel,
    track: Track,
    onFullscreenLyrics: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onEffectsClick: () -> Unit,
    isLyricsActive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = Color.White.copy(alpha = 0.18f),
        modifier = Modifier
            .height(44.dp)
            .border(0.8.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(24.dp))
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppleCapsuleHalf(
                slot = firstSlot,
                viewModel = viewModel,
                track = track,
                onFullscreenLyrics = onFullscreenLyrics,
                onQueueClick = onQueueClick,
                onLyricsClick = onLyricsClick,
                onEffectsClick = onEffectsClick,
                isLyricsActive = isLyricsActive
            )

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(24.dp)
                    .background(Color.White.copy(alpha = 0.18f))
            )

            AppleCapsuleHalf(
                slot = secondSlot,
                viewModel = viewModel,
                track = track,
                onFullscreenLyrics = onFullscreenLyrics,
                onQueueClick = onQueueClick,
                onLyricsClick = onLyricsClick,
                onEffectsClick = onEffectsClick,
                isLyricsActive = isLyricsActive
            )
        }
    }
}

/**
 * One half of the double capsule: an action icon, its own click handling and
 * active-state highlighting (a brighter fill over the capsule glass).
 */
@Composable
private fun AppleCapsuleHalf(
    slot: PlayerActionButtonSlot,
    viewModel: PlayerViewModel,
    track: Track,
    onFullscreenLyrics: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onEffectsClick: () -> Unit,
    isLyricsActive: Boolean
) {
    val isActive = appleSlotIsActive(slot, viewModel, isLyricsActive)
    val iconVector = appleSlotIcon(slot, viewModel) ?: return
    Box(
        modifier = Modifier
            .width(58.dp)
            .fillMaxHeight()
            .background(if (isActive) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable {
                appleSlotAction(
                    slot,
                    viewModel,
                    track,
                    onFullscreenLyrics,
                    onQueueClick,
                    onLyricsClick,
                    onEffectsClick
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = stringResource(slot.titleRes),
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * A round Liquid Glass-style action button (like the lyrics button):
 * 42dp, translucent fill, thin outline, white icon. Supports all actions from
 * the Player Action Panel; the active state is highlighted with a brighter
 * fill. NONE draws nothing.
 */
@Composable
private fun AppleGlassActionButton(
    slot: PlayerActionButtonSlot,
    viewModel: PlayerViewModel,
    track: Track,
    onFullscreenLyrics: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onEffectsClick: () -> Unit,
    isLyricsActive: Boolean = false
) {
    if (slot == PlayerActionButtonSlot.NONE) return

    val isActive = appleSlotIsActive(slot, viewModel, isLyricsActive)
    val iconVector = appleSlotIcon(slot, viewModel) ?: return

    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (isActive) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.18f))
            .border(0.8.dp, Color.White.copy(alpha = 0.22f), CircleShape)
            .clickable {
                appleSlotAction(
                    slot,
                    viewModel,
                    track,
                    onFullscreenLyrics,
                    onQueueClick,
                    onLyricsClick,
                    onEffectsClick
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = iconVector,
            contentDescription = stringResource(slot.titleRes),
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(20.dp)
        )
    }
}

/** Icon for an Apple-design bottom-row button action. */
private fun appleSlotIcon(
    slot: PlayerActionButtonSlot,
    viewModel: PlayerViewModel
): ImageVector? = when (slot) {
    PlayerActionButtonSlot.LIKE -> if (viewModel.uiIsLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
    PlayerActionButtonSlot.COMMENTS -> Icons.AutoMirrored.Rounded.Comment
    PlayerActionButtonSlot.SHARE -> Icons.Rounded.Share
    PlayerActionButtonSlot.QUEUE -> Icons.AutoMirrored.Rounded.QueueMusic
    PlayerActionButtonSlot.AUDIO_FX -> Icons.Rounded.Equalizer
    PlayerActionButtonSlot.SHUFFLE -> Icons.Rounded.Shuffle
    PlayerActionButtonSlot.REPEAT -> if (viewModel.uiRepeatMode == RepeatMode.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat
    PlayerActionButtonSlot.LYRICS -> Icons.Rounded.Description
    PlayerActionButtonSlot.FULLSCREEN_LYRICS -> Icons.Rounded.OpenInFull
    PlayerActionButtonSlot.SLEEP_TIMER -> Icons.Rounded.Timer
    PlayerActionButtonSlot.HAPTICS -> Icons.Rounded.Vibration
    PlayerActionButtonSlot.CONNECT, PlayerActionButtonSlot.DEVICES -> Icons.Rounded.Speaker
    PlayerActionButtonSlot.MORE -> Icons.Rounded.MoreVert
    PlayerActionButtonSlot.NONE -> null
}

/** Whether the action is active (for highlighting the button/capsule half). */
private fun appleSlotIsActive(
    slot: PlayerActionButtonSlot,
    viewModel: PlayerViewModel,
    isLyricsActive: Boolean
): Boolean = when (slot) {
    PlayerActionButtonSlot.LIKE -> viewModel.uiIsLiked
    PlayerActionButtonSlot.SHUFFLE -> viewModel.uiShuffleEnabled
    PlayerActionButtonSlot.REPEAT -> viewModel.uiRepeatMode != RepeatMode.NONE
    PlayerActionButtonSlot.SLEEP_TIMER -> viewModel.isSleepTimerActive
    PlayerActionButtonSlot.HAPTICS -> viewModel.isHapticsEnabled
    PlayerActionButtonSlot.LYRICS -> isLyricsActive
    PlayerActionButtonSlot.CONNECT, PlayerActionButtonSlot.DEVICES ->
        com.alananasss.kittytune.data.sync.ConnectManager.selectedDevice.value != null
    else -> false
}

/**
 * Click on an Apple-design bottom-row button action. Audio FX opens the
 * "Audio settings" sheet (a sheet with AudioControlDock, as in KittyTune Modern),
 * not the equalizer; the sleep timer gets its own menu (the dialog is wired to
 * the player screen).
 */
private fun appleSlotAction(
    slot: PlayerActionButtonSlot,
    viewModel: PlayerViewModel,
    track: Track,
    onFullscreenLyrics: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onEffectsClick: () -> Unit
) {
    when (slot) {
        PlayerActionButtonSlot.LIKE -> viewModel.toggleLike()
        PlayerActionButtonSlot.COMMENTS -> viewModel.openComments(viewModel.uiCurrentTrack)
        PlayerActionButtonSlot.SHARE -> viewModel.uiCurrentTrack?.let { viewModel.openShareCard(it) }
        PlayerActionButtonSlot.QUEUE -> onQueueClick()
        PlayerActionButtonSlot.AUDIO_FX -> onEffectsClick()
        PlayerActionButtonSlot.SHUFFLE -> viewModel.toggleShuffle()
        PlayerActionButtonSlot.REPEAT -> viewModel.toggleRepeatMode()
        PlayerActionButtonSlot.LYRICS -> onLyricsClick()
        PlayerActionButtonSlot.FULLSCREEN_LYRICS -> onFullscreenLyrics()
        PlayerActionButtonSlot.SLEEP_TIMER -> viewModel.showSleepTimerDialog = true
        PlayerActionButtonSlot.HAPTICS -> viewModel.toggleHaptics()
        PlayerActionButtonSlot.CONNECT, PlayerActionButtonSlot.DEVICES -> viewModel.showConnectSheet = true
        PlayerActionButtonSlot.MORE -> viewModel.showTrackOptions(track, fromPlayer = true)
        PlayerActionButtonSlot.NONE -> {}
    }
}

/**
 * A smooth progress fraction for the Apple slider: extrapolates the player
 * position between ViewModel updates (roughly once a second), so the thumb
 * moves at frame rate instead of jumping.
 */
@Composable
private fun rememberAppleProgressFraction(
    viewModel: PlayerViewModel,
    totalDuration: Long,
    shownOf: (Long) -> Long
): State<Float> {
    val result = remember { mutableFloatStateOf(0f) }
    var anchorPos by remember { mutableLongStateOf(0L) }
    var anchoredAt by remember { mutableLongStateOf(0L) }

    LaunchedEffect(totalDuration) {
        anchorPos = shownOf(viewModel.uiCurrentPosition)
        anchoredAt = System.currentTimeMillis()
        while (true) {
            withFrameMillis {
                val now = System.currentTimeMillis()
                val reported = shownOf(viewModel.uiCurrentPosition)
                if (reported != anchorPos) {
                    anchorPos = reported
                    anchoredAt = now
                }
                val elapsed = if (viewModel.uiIsPlaying) (now - anchoredAt) else 0L
                val pos = (anchorPos + elapsed).coerceIn(0L, totalDuration)
                result.floatValue = if (totalDuration > 0L) {
                    (pos.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
                } else 0f
            }
        }
    }

    return result
}

// ═══════════════════════════════════════════════════════════════════════════
// iPad design: ONLY tablet and ONLY landscape (enabled either
// with the toggle icon inside the Apple Music player or by the separate "iPad"
// design from Player Personalization; not shown on phones or in portrait).
// Replicates Apple Music on iPad: artwork in the center (on the left with
// lyrics), title and artist at the bottom left, four glass buttons at the
// bottom right (like, lyrics, queue, menu), full-width progress with the time
// on the left and the remainder on the right. The "lyrics" button opens
// synchronized lyrics in the right half. Tapping the artwork pauses/resumes;
// while paused prev / play / next appear on the artwork.
//
// Tapping the track title cycles the song-text modes:
//   title → a text line instead of the title ("Lyrics under cover")
//   → the lyrics panel (artwork on the left — text on the right) → back to the title.
//
// The "Artwork in player" setting ("iPad" design): "Middle" — the classic
// iPad; "Full screen" — the sharp artwork fills the whole background, controls
// float on top of it.
// ═══════════════════════════════════════════════════════════════════════════

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun AppleTabletLandscapePlayer(
    viewModel: PlayerViewModel,
    track: Track,
    artworkUrl: String?,
    animatedCoverUrl: String?,
    // The SQUARE motion artwork for the centered cover ("Centred cover"): the
    // tall URL above is reserved for the full-screen cover, where its portrait
    // aspect actually fills the screen. Before the fix the centered artwork was
    // permanently static — the animated cover played nowhere in this mode.
    squareAnimatedCoverUrl: String?,
    progressFraction: Float,
    totalDuration: Long,
    onSeek: (Float) -> Unit,
    sliderStyle: PlayerSliderStyle?,
    coverMode: AppleTabletCoverMode
) {
    val showLyrics = viewModel.showInlineLyrics
    val isFullscreenCover = coverMode == AppleTabletCoverMode.FULL_SCREEN

    // The position of the text line near the title ("above the title" /
    // "instead of the title") — the same "Lyrics under cover" setting as in
    // KittyTune Modern; live: changes apply without restarting the player.
    val context = LocalContext.current
    val playerPrefs = remember { PlayerPreferences(context) }
    var lyricsUnderCoverPlacement by remember { mutableStateOf(playerPrefs.getLyricsUnderCoverPlacement()) }
    DisposableEffect(context) {
        val sp = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val placementListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PlayerPreferences.KEY_LYRICS_UNDER_COVER_PLACEMENT) {
                lyricsUnderCoverPlacement = playerPrefs.getLyricsUnderCoverPlacement()
            }
        }
        sp.registerOnSharedPreferenceChangeListener(placementListener)
        onDispose { sp.unregisterOnSharedPreferenceChangeListener(placementListener) }
    }

    // "Full-screen artwork": the sharp artwork (and the animated artwork, if
    // any) fills the whole screen ABOVE the blurred Apple background and BELOW
    // the scrim — controls float on top of it, like in the Apple Music
    // fullscreen landscape.
    if (isFullscreenCover) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (animatedCoverUrl != null) {
                com.alananasss.kittytune.ui.player.cover.CanvasVideo(
                    canvasUrl = animatedCoverUrl,
                    isPlaying = viewModel.uiIsPlaying,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // The same scrim for white-element readability as in the other modes.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Black.copy(alpha = 0.06f),
                        Color.Black.copy(alpha = 0.26f),
                        Color.Black.copy(alpha = 0.50f)
                    )
                )
            )
    )

    // In fullscreen mode we add a darkening toward the bottom: the title,
    // buttons and progress sit directly on the artwork, with no panel padding.
    if (isFullscreenCover) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.12f),
                            Color.Black.copy(alpha = 0.42f)
                        )
                    )
                )
        )
    }

    // The artwork slightly "deflates" while paused — as in the other Apple modes.
    val coverScale by animateFloatAsState(
        targetValue = if (viewModel.uiIsPlaying) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f),
        label = "appleTabletCoverScale"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(
                start = 56.dp,
                end = 56.dp,
                top = 24.dp,
                bottom = 14.dp
            )
    ) {
        // ── Central zone: artwork (+ lyrics panel on the right) ──
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Local copies of constraints for nested lambdas (K2).
            val zoneWidth = maxWidth
            val zoneHeight = maxHeight

            if (isFullscreenCover) {
                // ── "Full-screen artwork": the artwork itself is already drawn
                //    by the background, so the central zone keeps only the pause
                //    tap zone (glass prev / play / next while paused); with
                //    lyrics — a tap zone on the left and text on the right over
                //    a soft darkening. ──
                if (showLyrics) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        AppleTabletFullscreenCoverZone(
                            viewModel = viewModel,
                            modifier = Modifier
                                .weight(0.40f)
                                .fillMaxHeight()
                        )

                        Box(
                            modifier = Modifier
                                .weight(0.60f)
                                .fillMaxHeight()
                                .padding(start = 30.dp)
                        ) {
                            // No background rectangle: the text sits directly on the blurred
                            // artwork, as in Apple Music on iPad. The gray gradient scrim here
                            // looked like an extra "gray window" on top of the artwork.
                            InlineLyricsContent(viewModel = viewModel)
                        }
                    }
                } else {
                    AppleTabletFullscreenCoverZone(
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            } else if (showLyrics) {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Artwork on the left, vertically centered (as on iPad).
                    Box(
                        modifier = Modifier
                            .weight(0.40f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center
                    ) {
                        val lyricsCoverSize = minOf(zoneWidth * 0.40f * 0.85f, zoneHeight * 0.86f)
                        AppleTabletArtwork(
                            artworkUrl = artworkUrl,
                            animatedCoverUrl = squareAnimatedCoverUrl,
                            contentDescription = track.title,
                            size = lyricsCoverSize,
                            coverScale = coverScale,
                            isPlaying = viewModel.uiIsPlaying,
                            onToggle = { viewModel.togglePlayPause() },
                            onPrevious = { viewModel.smartPrevious() },
                            onNext = { viewModel.requestSkipNext() }
                        )
                    }

                    // Synchronized lyrics on the right (style from the lyrics settings).
                    Box(
                        modifier = Modifier
                            .weight(0.60f)
                            .fillMaxHeight()
                            .padding(start = 30.dp)
                    ) {
                        // No background rectangle: the gray gradient scrim (0.45 → 0.75)
                        // looked like a "gray window" that should not be there — the text reads
                        // directly on the blurred artwork, as in Apple Music on iPad.
                        InlineLyricsContent(viewModel = viewModel)
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val coverSize = minOf(zoneHeight * 0.87f, zoneWidth * 0.40f)
                    AppleTabletArtwork(
                        artworkUrl = artworkUrl,
                        animatedCoverUrl = squareAnimatedCoverUrl,
                        contentDescription = track.title,
                        size = coverSize,
                        coverScale = coverScale,
                        isPlaying = viewModel.uiIsPlaying,
                        onToggle = { viewModel.togglePlayPause() },
                        onPrevious = { viewModel.smartPrevious() },
                        onNext = { viewModel.requestSkipNext() }
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Bottom row: title/artist on the left, buttons on the right ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // The text line ABOVE the title and artist — the
                // "Lyrics under cover: above the title" setting. Hidden when
                // the lyrics panel is already open (otherwise the text doubles up).
                if (viewModel.isLyricsUnderCoverActive &&
                    !showLyrics &&
                    lyricsUnderCoverPlacement == LyricsUnderCoverPlacement.ABOVE_TITLE_ARTIST
                ) {
                    PlayerInlineLyrics(
                        viewModel = viewModel,
                        textColor = Color.White,
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
                    label = "AppleTabletTitleLyrics"
                ) { showLyricsLine ->
                    if (showLyricsLine) {
                        // 1 tap on the title: a song-text line replaces the title
                        // and artist. Tapping it advances to the next mode of the cycle.
                        PlayerInlineLyrics(
                            viewModel = viewModel,
                            textColor = Color.White,
                            onClick = hapticAction(HapticKind.UI) { viewModel.cycleLyricsFromTitle() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (viewModel.hasLyrics) {
                                        // Three-step text cycle on title tap
                                        // (as in KittyTune Modern): 1 tap — a text line
                                        // instead of/above the title, 2 taps — the lyrics
                                        // panel on the right, 3 taps — no text. A long press
                                        // opens the track page, as before.
                                        Modifier.hapticCombinedClickable(
                                            onClick = { viewModel.cycleLyricsFromTitle() },
                                            onLongClick = { viewModel.navigateToTrackDetails(track.id, 0) }
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            Text(
                                text = track.title ?: stringResource(R.string.untitled_track),
                                color = Color.White,
                                fontSize = 27.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                modifier = Modifier.basicMarquee()
                            )
                            Spacer(Modifier.height(3.dp))
                            Text(
                                text = track.displayArtist.ifBlank { stringResource(R.string.unknown_artist) },
                                color = Color.White.copy(alpha = 0.74f),
                                fontSize = 17.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.clickable { viewModel.navigateToTrackArtist(track) }
                            )
                        }
                    }
                }
            }

            AppleTabletActionRail(
                viewModel = viewModel,
                track = track,
                // Highlighted both when the panel on the right is open and when
                // the text line is shown instead of/above the title.
                isLyricsActive = showLyrics || viewModel.isLyricsUnderCoverActive,
                // The "lyrics" button works as in KittyTune Modern: the same
                // multi-step cycle — 1 tap: a text line instead of the title
                // (or above it, per the setting), 2 taps: the lyrics panel
                // on the right, 3 taps: no text. With the multi-step setting
                // disabled — a plain toggle of the panel.
                onLyricsClick = { viewModel.toggleInlineLyrics() },
                onQueueClick = { viewModel.navigateToExpandedQueue() },
                onMoreClick = { viewModel.showTrackOptions(track, fromPlayer = true) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // ── Full-width progress: slider + time on the left, remainder on the right ──
        AppleTabletProgressSection(
            viewModel = viewModel,
            fraction = progressFraction,
            totalDuration = totalDuration,
            onSeek = onSeek,
            sliderStyle = sliderStyle
        )
    }
}

/**
 * The tap zone of the iPad player's "Full-screen artwork": the whole zone (or
 * its left part, when lyrics are open) toggles pause; while paused, glass
 * prev / play / next appear in the center — as on the artwork in the classic
 * iPad mode.
 */
@Composable
private fun AppleTabletFullscreenCoverZone(
    viewModel: PlayerViewModel,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) { viewModel.togglePlayPause() },
        contentAlignment = Alignment.Center
    ) {
        if (!viewModel.uiIsPlaying) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppleTabletGlassButton(
                    onClick = hapticAction(HapticKind.UI) { viewModel.smartPrevious() },
                    buttonSize = 46.dp,
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    borderColor = Color.White.copy(alpha = 0.26f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastRewind,
                        contentDescription = stringResource(R.string.desc_previous),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AppleTabletGlassButton(
                    onClick = hapticAction(HapticKind.UI) { viewModel.togglePlayPause() },
                    buttonSize = 46.dp,
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    borderColor = Color.White.copy(alpha = 0.26f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.btn_play),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                AppleTabletGlassButton(
                    onClick = hapticAction(HapticKind.UI) { viewModel.requestSkipNext() },
                    buttonSize = 46.dp,
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    borderColor = Color.White.copy(alpha = 0.26f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = stringResource(R.string.desc_next),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/**
 * The iPad player artwork: rounded corners, shadow, a slight "deflation" while
 * paused. Tapping the artwork pauses/resumes; while paused, glass circles with
 * prev / play / next appear on top as temporary controls.
 */
@Composable
private fun AppleTabletArtwork(
    artworkUrl: String?,
    animatedCoverUrl: String?,
    contentDescription: String?,
    size: Dp,
    coverScale: Float,
    isPlaying: Boolean,
    onToggle: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size)
            .scale(coverScale)
            .shadow(elevation = 32.dp, shape = RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Animated cover video overlay (square motion artwork).
        if (!animatedCoverUrl.isNullOrBlank()) {
            com.alananasss.kittytune.ui.player.cover.CanvasVideo(
                canvasUrl = animatedCoverUrl,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Controls appear only while paused: during playback the
        // artwork stays clean, as in Apple Music on iPad.
        if (!isPlaying) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                AppleTabletGlassButton(
                    onClick = hapticAction(HapticKind.UI) { onPrevious() },
                    buttonSize = 46.dp,
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    borderColor = Color.White.copy(alpha = 0.26f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastRewind,
                        contentDescription = stringResource(R.string.desc_previous),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                AppleTabletGlassButton(
                    onClick = hapticAction(HapticKind.UI) { onToggle() },
                    buttonSize = 46.dp,
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    borderColor = Color.White.copy(alpha = 0.26f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = stringResource(R.string.btn_play),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                AppleTabletGlassButton(
                    onClick = hapticAction(HapticKind.UI) { onNext() },
                    buttonSize = 46.dp,
                    containerColor = Color.Black.copy(alpha = 0.45f),
                    borderColor = Color.White.copy(alpha = 0.26f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FastForward,
                        contentDescription = stringResource(R.string.desc_next),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

/** The iPad player's row of four glass buttons: like, lyrics, queue, menu. */
@Composable
private fun AppleTabletActionRail(
    viewModel: PlayerViewModel,
    track: Track,
    isLyricsActive: Boolean,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
    onMoreClick: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Like
        AppleTabletGlassButton(onClick = hapticAction(HapticKind.UI) { viewModel.toggleLike() }) {
            Icon(
                imageVector = if (viewModel.uiIsLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = stringResource(R.string.desc_like),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Lyrics: toggles the lyrics panel on the right
        AppleTabletGlassButton(
            onClick = hapticAction(HapticKind.UI) { onLyricsClick() },
            containerColor = if (isLyricsActive) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.16f)
        ) {
            Icon(
                painter = painterResource(R.drawable.rounded_lyrics_24),
                contentDescription = stringResource(R.string.player_lyrics),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Queue
        AppleTabletGlassButton(onClick = hapticAction(HapticKind.UI) { onQueueClick() }) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.QueueMusic,
                contentDescription = stringResource(R.string.player_queue),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }

        // Track menu
        AppleTabletGlassButton(onClick = hapticAction(HapticKind.UI) { onMoreClick() }) {
            Icon(
                imageVector = Icons.Rounded.MoreHoriz,
                contentDescription = stringResource(R.string.btn_options),
                tint = Color.White,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

/**
 * The iPad player progress: a slider (style from the global setting, as in
 * the other modes), below it the time on the left and the "-remainder" on the
 * right — as in Apple Music on iPad. No codec badge is shown on this row.
 */
@Composable
private fun AppleTabletProgressSection(
    viewModel: PlayerViewModel,
    fraction: Float,
    totalDuration: Long,
    onSeek: (Float) -> Unit,
    sliderStyle: PlayerSliderStyle?
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val activeFraction = (if (isDragging) dragFraction else fraction).coerceIn(0f, 1f)

    Column(modifier = Modifier.fillMaxWidth()) {
        if (sliderStyle == null) {
            AppleCapsuleSlider(
                fraction = activeFraction,
                onFractionChanged = { value ->
                    dragFraction = value
                    isDragging = true
                },
                onFractionChangeFinished = {
                    isDragging = false
                    onSeek(dragFraction)
                }
            )
        } else {
            PlayerSlider(
                value = activeFraction,
                onValueChange = { value ->
                    dragFraction = value
                    isDragging = true
                },
                onValueChangeFinished = {
                    isDragging = false
                    onSeek(dragFraction)
                },
                sliderStyle = sliderStyle,
                isPlaying = viewModel.uiIsPlaying,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.30f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = makeTimeString((activeFraction * totalDuration).toLong()),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "-" + makeTimeString(
                    (totalDuration - (activeFraction * totalDuration).toLong()).coerceAtLeast(0L)
                ),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/** Round glass button of the iPad player (Liquid Glass). */
@Composable
private fun AppleTabletGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 52.dp,
    containerColor: Color = Color.White.copy(alpha = 0.16f),
    borderColor: Color = Color.White.copy(alpha = 0.22f),
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(buttonSize)
            .clip(CircleShape)
            .background(containerColor)
            .border(0.8.dp, borderColor, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}
