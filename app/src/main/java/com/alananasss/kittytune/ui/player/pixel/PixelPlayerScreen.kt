package com.alananasss.kittytune.ui.player.pixel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.util.lerp
import kotlin.math.absoluteValue
import kotlin.math.roundToLong
import coil.compose.AsyncImage
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.local.PlayerSliderStyle
import com.alananasss.kittytune.ui.player.slider.PlayerSliderTrack
import com.alananasss.kittytune.ui.player.slider.SquigglySlider
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.player.FluidArtworkBackground
import com.alananasss.kittytune.ui.player.InlineLyricsContent
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.QueueContent
import com.alananasss.kittytune.ui.player.SleepTimerDialog
import com.alananasss.kittytune.ui.player.TrackTrimDialog
import com.alananasss.kittytune.ui.player.cover.AnimatedArtwork
import com.alananasss.kittytune.ui.player.cover.CanvasVideo
import com.alananasss.kittytune.ui.theme.GoogleSansRounded
import com.alananasss.kittytune.utils.makeTimeString
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val track = viewModel.currentTrack ?: return
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenHeightPx = remember(configuration, density) {
        with(density) { configuration.screenHeightDp.dp.toPx() }
    }
    val sheetCollapsedTargetY = remember(screenHeightPx) {
        screenHeightPx * 0.88f
    }
    val predictiveBackProgress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(viewModel.isPlayerExpanded) {
        if (viewModel.isPlayerExpanded) {
            predictiveBackProgress.snapTo(0f)
        }
    }

    val handleClose: () -> Unit = {
        scope.launch {
            predictiveBackProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing)
            )
            onClose()
        }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        PredictiveBackHandler(enabled = !viewModel.showLyricsSheet) { progressFlow ->
            try {
                progressFlow.collect { backEvent ->
                    predictiveBackProgress.snapTo(backEvent.progress)
                }
                scope.launch {
                    predictiveBackProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
                    )
                    onClose()
                }
            } catch (_: kotlin.coroutines.cancellation.CancellationException) {
                scope.launch {
                    predictiveBackProgress.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    )
                }
            }
        }
    } else {
        BackHandler(enabled = !viewModel.showLyricsSheet, onBack = handleClose)
    }

    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }

    DisposableEffect(viewModel.showInlineLyrics) {
        val activity = context.findActivity()
        if (viewModel.showInlineLyrics) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    var backgroundStyle by remember { mutableStateOf(prefs.getPlayerStyle()) }
    val fadeUiEnabled by prefs.getAnimatedCoversFadeUiFlow().collectAsState(initial = prefs.getAnimatedCoversFadeUiEnabled())
    val backdropAnimatedUrl = if (fadeUiEnabled) {
        viewModel.currentAnimatedCoverTallUrl ?: viewModel.currentAnimatedCoverUrl
    } else null

    var pixelSlots by remember { mutableStateOf(List(4) { i -> prefs.getPixelSlot(i) }) }
    var sliderStyle by remember { mutableStateOf(prefs.getPlayerSliderStyle()) }

    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == PlayerPreferences.KEY_PLAYER_STYLE) {
                backgroundStyle = prefs.getPlayerStyle()
            } else if (key?.startsWith(PlayerPreferences.KEY_PIXEL_SLOT_PREFIX) == true) {
                pixelSlots = List(4) { i -> prefs.getPixelSlot(i) }
            } else if (key == PlayerPreferences.KEY_PLAYER_SLIDER_STYLE) {
                sliderStyle = prefs.getPlayerSliderStyle()
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isBlurMode = backgroundStyle == PlayerBackgroundStyle.BLUR || backgroundStyle == PlayerBackgroundStyle.APPLE_MUSIC

    val animatedColor by animateColorAsState(
        targetValue = viewModel.backgroundColor,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "backgroundColor"
    )

    // Palette tokens tailored for PixelPlayer
    val colorScheme = MaterialTheme.colorScheme
    val mainTextColor by animateColorAsState(
        targetValue = if (isBlurMode) Color.White else colorScheme.onSurface,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "mainText"
    )
    val subTextColor by animateColorAsState(
        targetValue = if (isBlurMode) Color.White.copy(alpha = 0.7f) else colorScheme.onSurfaceVariant,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "subText"
    )
    val topBarBtnBg by animateColorAsState(
        targetValue = if (isBlurMode) Color.White.copy(alpha = 0.15f) else colorScheme.onSurface.copy(alpha = 0.08f),
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "topBarBg"
    )
    val topBarBtnTint by animateColorAsState(
        targetValue = if (isBlurMode) Color.White else colorScheme.onSurface,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "topBarTint"
    )
    val activeChipBg by animateColorAsState(
        targetValue = if (isBlurMode) Color.White.copy(alpha = 0.35f) else colorScheme.primary,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "activeChipBg"
    )
    val activeChipTint by animateColorAsState(
        targetValue = if (isBlurMode) Color.White else colorScheme.onPrimary,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "activeChipTint"
    )

    val playPauseContainer = if (isBlurMode) Color.White.copy(alpha = 0.3f) else colorScheme.tertiaryFixedDim
    val playPauseContent = if (isBlurMode) Color.White else colorScheme.onTertiaryFixed
    val skipContainer = if (isBlurMode) Color.White.copy(alpha = 0.15f) else colorScheme.secondaryFixedDim
    val skipContent = if (isBlurMode) Color.White else colorScheme.onSecondaryFixed

    val sliderActiveColor = if (isBlurMode) Color.White else colorScheme.primary
    val sliderInactiveColor = if (isBlurMode) Color.White.copy(alpha = 0.25f) else colorScheme.onSurface.copy(alpha = 0.15f)

    var showQueueSheet by remember { mutableStateOf(false) }
    var showEffectsSheet by remember { mutableStateOf(false) }

    val pProgress = predictiveBackProgress.value
    val predictiveScaleX = 1f - (pProgress * 0.06f)
    val predictiveScaleY = 1f - (pProgress * 0.04f)
    val predictiveTranslationY = pProgress * sheetCollapsedTargetY
    val predictiveCorner = (pProgress * 32.dp.value).dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = predictiveScaleX
                scaleY = predictiveScaleY
                translationY = predictiveTranslationY
                transformOrigin = TransformOrigin(0.5f, 1.0f)
                shape = AbsoluteSmoothCornerShape(predictiveCorner, 60)
                clip = pProgress > 0.001f
                alpha = (1f - (pProgress * 0.15f)).coerceIn(0f, 1f)
            }
            .background(if (isBlurMode) Color.Black else colorScheme.surface)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            }
    ) {
        // Background layer
        when (backgroundStyle) {
            PlayerBackgroundStyle.BLUR -> {
                Crossfade(
                    targetState = track.fullResArtwork,
                    animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
                    label = "BlurBackgroundTransition"
                ) { artworkUrl ->
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(80.dp)
                            .alpha(0.6f)
                    )
                }
                if (!backdropAnimatedUrl.isNullOrBlank()) {
                    CanvasVideo(
                        canvasUrl = backdropAnimatedUrl,
                        isPlaying = viewModel.isPlaying,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(40.dp)
                            .alpha(0.85f)
                    )
                }
            }

            PlayerBackgroundStyle.GRADIENT -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    animatedColor.copy(alpha = 0.7f),
                                    animatedColor.copy(alpha = 0.3f),
                                    colorScheme.surface
                                )
                            )
                        )
                )
            }

            PlayerBackgroundStyle.THEME -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(colorScheme.surface)
                )
            }

            PlayerBackgroundStyle.APPLE_MUSIC -> {
                FluidArtworkBackground(
                    artworkUrl = track.fullResArtwork,
                    modifier = Modifier.fillMaxSize()
                ) {
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(120.dp)
                            .alpha(0.6f)
                    )
                }
                if (!backdropAnimatedUrl.isNullOrBlank()) {
                    CanvasVideo(
                        canvasUrl = backdropAnimatedUrl,
                        isPlaying = viewModel.isPlaying,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(40.dp)
                            .alpha(0.85f)
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.0f to Color.Black.copy(alpha = 0.35f),
                                0.35f to Color.Black.copy(alpha = 0.15f),
                                0.65f to Color.Black.copy(alpha = 0.30f),
                                1.0f to Color.Black.copy(alpha = 0.50f)
                            )
                        )
                )
            }
        }

        // Main Player Column Layout
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 20.dp)
                    .pointerInput(sheetCollapsedTargetY) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                if (predictiveBackProgress.value > 0.18f) {
                                    handleClose()
                                } else {
                                    scope.launch {
                                        predictiveBackProgress.animateTo(
                                            0f,
                                            spring(
                                                dampingRatio = Spring.DampingRatioLowBouncy,
                                                stiffness = Spring.StiffnessMediumLow
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                scope.launch {
                                    predictiveBackProgress.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioLowBouncy,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val delta = dragAmount / sheetCollapsedTargetY
                                scope.launch {
                                    predictiveBackProgress.snapTo(
                                        (predictiveBackProgress.value + delta).coerceIn(0f, 1f)
                                    )
                                }
                            }
                        )
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Collapse button with expressive morphing shapes
                FilledIconButton(
                    onClick = handleClose,
                    modifier = Modifier.size(42.dp),
                    shapes = IconButtonDefaults.shapes(),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = topBarBtnBg,
                        contentColor = topBarBtnTint
                    )
                ) {
                    Icon(
                        painter = painterResource(R.drawable.rounded_keyboard_arrow_down_24),
                        contentDescription = stringResource(R.string.btn_close),
                        tint = topBarBtnTint
                    )
                }

                // Center "Now Playing" title
                Text(
                    text = stringResource(R.string.player_now_playing),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = GoogleSansRounded,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = mainTextColor
                )

                // Right connected buttons: Lyrics + Queue
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lyrics Squircle
                    Box(
                        modifier = Modifier
                            .size(height = 42.dp, width = 50.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 50.dp,
                                    topEnd = 6.dp,
                                    bottomStart = 50.dp,
                                    bottomEnd = 6.dp
                                )
                            )
                            .background(if (viewModel.showInlineLyrics) activeChipBg else topBarBtnBg)
                            .clickable {
                                viewModel.showInlineLyrics = !viewModel.showInlineLyrics
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_lyrics_24),
                            contentDescription = stringResource(R.string.player_lyrics),
                            tint = if (viewModel.showInlineLyrics) activeChipTint else topBarBtnTint
                        )
                    }

                    // Queue Squircle
                    Box(
                        modifier = Modifier
                            .size(height = 42.dp, width = 50.dp)
                            .clip(
                                RoundedCornerShape(
                                    topStart = 6.dp,
                                    topEnd = 50.dp,
                                    bottomStart = 6.dp,
                                    bottomEnd = 50.dp
                                )
                            )
                            .background(topBarBtnBg)
                            .clickable { showQueueSheet = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.rounded_queue_music_24),
                            contentDescription = stringResource(R.string.player_queue),
                            tint = topBarBtnTint
                        )
                    }
                }
            }

            // Pager for swipe animation between queue tracks
            val queueSize = viewModel.queueState.size
            val currentIdx = viewModel.currentQueueIndex
            val pagerState = rememberPagerState(
                initialPage = currentIdx.coerceIn(0, (queueSize - 1).coerceAtLeast(0)),
                pageCount = { queueSize.coerceAtLeast(1) }
            )

            LaunchedEffect(viewModel.currentQueueIndex) {
                if (viewModel.currentQueueIndex in 0 until pagerState.pageCount &&
                    viewModel.currentQueueIndex != pagerState.currentPage
                ) {
                    try {
                        pagerState.animateScrollToPage(viewModel.currentQueueIndex)
                    } catch (_: Exception) {
                        pagerState.scrollToPage(viewModel.currentQueueIndex)
                    }
                }
            }

            LaunchedEffect(pagerState) {
                snapshotFlow { pagerState.settledPage }.collect { settledPage ->
                    if (settledPage != viewModel.currentQueueIndex && settledPage in viewModel.queueState.indices) {
                        viewModel.skipToQueueItem(settledPage)
                    }
                }
            }

            // Center Artwork Section (32.dp, 60 smooth corner) with swipe animation
            val showLyrics = viewModel.showInlineLyrics
            val lyricsAlpha by animateFloatAsState(
                targetValue = if (showLyrics) 1f else 0f,
                animationSpec = tween(400),
                label = "pixelLyricsAlpha"
            )
            val coverAlpha by animateFloatAsState(
                targetValue = if (showLyrics) 0f else 1f,
                animationSpec = tween(400),
                label = "pixelCoverAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                // Cover Layer (Carousel with swipe animation)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(coverAlpha)
                        .zIndex(if (showLyrics) 0f else 1f)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        pageSpacing = 16.dp,
                        contentPadding = PaddingValues(horizontal = 24.dp)
                    ) { page ->
                        val pageTrack = viewModel.queueState.getOrNull(page) ?: track
                        val pageOffset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                        val scale = lerp(0.88f, 1f, (1f - pageOffset.coerceIn(0f, 1f)))
                        val alpha = lerp(0.5f, 1f, (1f - pageOffset.coerceIn(0f, 1f)))

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    this.alpha = alpha
                                }
                                .shadow(
                                    elevation = 16.dp,
                                    shape = AbsoluteSmoothCornerShape(32.dp, 60),
                                    ambientColor = Color.Black.copy(alpha = 0.25f),
                                    spotColor = Color.Black.copy(alpha = 0.35f)
                                )
                                .clip(AbsoluteSmoothCornerShape(32.dp, 60))
                                .background(colorScheme.surfaceVariant)
                        ) {
                            AnimatedArtwork(
                                artworkUrl = pageTrack.fullResArtwork,
                                animatedCoverUrl = if (pageTrack.id == track.id) (viewModel.currentAnimatedCoverTallUrl ?: viewModel.currentAnimatedCoverUrl) else null,
                                isPlaying = viewModel.isPlaying && (pageTrack.id == track.id),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clickable {
                                        viewModel.showInlineLyrics = !viewModel.showInlineLyrics
                                    }
                            )
                        }
                    }
                }

                // Lyrics Layer
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                        .alpha(lyricsAlpha)
                        .zIndex(if (showLyrics) 1f else 0f)
                ) {
                    if (lyricsAlpha > 0f) {
                        InlineLyricsContent(viewModel = viewModel)
                    }
                }
            }

            // Track Metadata + Wavy Progress Slider
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Track Title (GoogleSansRounded Bold)
                Text(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontFamily = GoogleSansRounded,
                        color = mainTextColor
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.navigateToTrackDetails(track.id, 0) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Artist (GoogleSansRounded Medium)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.navigateToTrackArtist(track) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = track.displayArtist.ifBlank {
                            track.user?.username ?: stringResource(R.string.unknown_artist)
                        },
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = GoogleSansRounded,
                            color = subTextColor
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    val isAnyVerified = track.user?.verified == true || track.artists?.any { it.verified } == true
                    if (isAnyVerified) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Verified,
                            contentDescription = "Verified",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Expressive Wavy Slider
                val totalDuration = if (viewModel.duration > 1000) {
                    viewModel.duration
                } else {
                    track.durationMs?.takeIf { it > 1000 } ?: 180000L
                }

                val (smoothProgressState, _) = rememberSmoothProgress(
                    isPlayingProvider = { viewModel.isPlaying },
                    currentPositionProvider = { viewModel.currentPosition },
                    totalDuration = totalDuration,
                    trackId = track.id
                )

                var sliderDragValue by remember(track.id) { mutableStateOf<Float?>(null) }
                var targetSeekFraction by remember(track.id) { mutableFloatStateOf(-1f) }
                var lastSeekFinishedTime by remember(track.id) { mutableLongStateOf(0L) }

                LaunchedEffect(track.id) {
                    sliderDragValue = null
                    targetSeekFraction = -1f
                    lastSeekFinishedTime = 0L
                }

                LaunchedEffect(track.id) {
                    snapshotFlow { smoothProgressState.value }.collect { progress ->
                        if (sliderDragValue != null) return@collect
                        val target = targetSeekFraction
                        if (target < 0f) return@collect
                        val timeSinceSeek = System.currentTimeMillis() - lastSeekFinishedTime
                        val diff = kotlin.math.abs(progress - target)
                        if (timeSinceSeek > 5000L || diff < 0.04f) {
                            targetSeekFraction = -1f
                        }
                    }
                }

                val animatedProgressState = remember(smoothProgressState) {
                    derivedStateOf {
                        when {
                            sliderDragValue != null -> sliderDragValue!!
                            targetSeekFraction >= 0f -> targetSeekFraction
                            else -> smoothProgressState.value
                        }
                    }
                }

                val effectivePositionState = remember(totalDuration, animatedProgressState) {
                    derivedStateOf {
                        (animatedProgressState.value * totalDuration).roundToLong().coerceIn(0L, totalDuration)
                    }
                }

                val sliderColors = SliderDefaults.colors(
                    thumbColor = sliderActiveColor,
                    activeTrackColor = sliderActiveColor,
                    inactiveTrackColor = sliderInactiveColor
                )

                when (sliderStyle) {
                    PlayerSliderStyle.WAVY -> {
                        WavySliderExpressive(
                            value = { animatedProgressState.value },
                            onValueChange = { newFraction ->
                                sliderDragValue = newFraction
                                val targetMs = (newFraction * totalDuration).roundToLong()
                                viewModel.updateScrubPosition(targetMs)
                            },
                            onValueCommit = { finalFraction ->
                                val targetMs = (finalFraction * totalDuration).roundToLong()
                                targetSeekFraction = finalFraction
                                lastSeekFinishedTime = System.currentTimeMillis()
                                viewModel.seekTo(targetMs)
                                sliderDragValue = null
                            },
                            onValueChangeFinished = {
                                sliderDragValue?.let { finalFraction ->
                                    val targetMs = (finalFraction * totalDuration).roundToLong()
                                    targetSeekFraction = finalFraction
                                    lastSeekFinishedTime = System.currentTimeMillis()
                                    viewModel.seekTo(targetMs)
                                    sliderDragValue = null
                                }
                            },
                            isPlaying = viewModel.isPlaying,
                            activeTrackColor = sliderActiveColor,
                            inactiveTrackColor = sliderInactiveColor,
                            thumbColor = sliderActiveColor,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PlayerSliderStyle.BAR -> {
                        Slider(
                            value = animatedProgressState.value.coerceIn(0f, 1f),
                            valueRange = 0f..1f,
                            onValueChange = { newFraction ->
                                sliderDragValue = newFraction
                                val targetMs = (newFraction * totalDuration).roundToLong()
                                viewModel.updateScrubPosition(targetMs)
                            },
                            onValueChangeFinished = {
                                sliderDragValue?.let { finalFraction ->
                                    val targetMs = (finalFraction * totalDuration).roundToLong()
                                    targetSeekFraction = finalFraction
                                    lastSeekFinishedTime = System.currentTimeMillis()
                                    viewModel.seekTo(targetMs)
                                    sliderDragValue = null
                                }
                            },
                            colors = sliderColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PlayerSliderStyle.SLIM -> {
                        val trackInteractionSource = remember { MutableInteractionSource() }
                        val isTrackDragged by trackInteractionSource.collectIsDraggedAsState()
                        val isTrackPressed by trackInteractionSource.collectIsPressedAsState()
                        val isTrackActive = isTrackDragged || isTrackPressed || (sliderDragValue != null)

                        val trackHeight by animateDpAsState(
                            targetValue = if (isTrackActive) 16.dp else 10.dp,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "trackHeight"
                        )

                        val slimState = remember(track.id) {
                            SliderState(
                                value = animatedProgressState.value.coerceIn(0f, 1f),
                                steps = 0,
                                trackRange = 0f..1f
                            )
                        }
                        slimState.value = animatedProgressState.value.coerceIn(0f, 1f)

                        Slider(
                            state = slimState,
                            onValueChange = { newFraction ->
                                sliderDragValue = newFraction
                                val targetMs = (newFraction * totalDuration).roundToLong()
                                viewModel.updateScrubPosition(targetMs)
                            },
                            onValueChangeFinished = {
                                sliderDragValue?.let { finalFraction ->
                                    val targetMs = (finalFraction * totalDuration).roundToLong()
                                    targetSeekFraction = finalFraction
                                    lastSeekFinishedTime = System.currentTimeMillis()
                                    viewModel.seekTo(targetMs)
                                    sliderDragValue = null
                                }
                            },
                            interactionSource = trackInteractionSource,
                            thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                            track = { state ->
                                PlayerSliderTrack(
                                    sliderState = state,
                                    trackHeight = trackHeight,
                                    colors = sliderColors
                                )
                            },
                            colors = sliderColors,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    PlayerSliderStyle.SQUIGGLY -> {
                        SquigglySlider(
                            value = animatedProgressState.value.coerceIn(0f, 1f),
                            valueRange = 0f..1f,
                            onValueChange = { newFraction ->
                                sliderDragValue = newFraction
                                val targetMs = (newFraction * totalDuration).roundToLong()
                                viewModel.updateScrubPosition(targetMs)
                            },
                            onValueChangeFinished = {
                                sliderDragValue?.let { finalFraction ->
                                    val targetMs = (finalFraction * totalDuration).roundToLong()
                                    targetSeekFraction = finalFraction
                                    lastSeekFinishedTime = System.currentTimeMillis()
                                    viewModel.seekTo(targetMs)
                                    sliderDragValue = null
                                }
                            },
                            colors = sliderColors,
                            isPlaying = viewModel.isPlaying,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Timestamps: current on left, duration on right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = makeTimeString(effectivePositionState.value),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = GoogleSansRounded,
                            color = subTextColor
                        )
                    )
                    Text(
                        text = makeTimeString(totalDuration),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = GoogleSansRounded,
                            color = subTextColor
                        )
                    )
                }
            }

            // Controls Section: AnimatedPlaybackControls + BottomToggleRow
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedPlaybackControls(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    isPlayingProvider = { viewModel.isPlaying },
                    isLoadingProvider = { viewModel.isLoading },
                    onPrevious = { viewModel.smartPrevious() },
                    onPlayPause = { viewModel.togglePlayPause() },
                    onNext = { viewModel.playNext() },
                    height = 80.dp,
                    colorOtherButtons = skipContainer,
                    colorPlayPause = playPauseContainer,
                    tintPlayPauseIcon = playPauseContent,
                    tintOtherIcons = skipContent,
                    colorPreviousButton = skipContainer,
                    colorNextButton = skipContainer,
                    tintPreviousIcon = skipContent,
                    tintNextIcon = skipContent
                )

                Spacer(modifier = Modifier.height(14.dp))

                BottomToggleRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(66.dp)
                        .padding(horizontal = 24.dp),
                    slots = pixelSlots,
                    isShuffleEnabled = viewModel.shuffleEnabled,
                    repeatMode = viewModel.repeatMode,
                    isFavorite = viewModel.isLiked,
                    isLyricsActive = viewModel.showInlineLyrics || viewModel.isLyricsUnderCoverActive,
                    isSleepTimerActive = viewModel.isSleepTimerActive,
                    isHapticsActive = viewModel.isHapticsEnabled,
                    onShuffleToggle = { viewModel.toggleShuffle() },
                    onRepeatToggle = { viewModel.toggleRepeatMode() },
                    onFavoriteToggle = { viewModel.toggleLike() },
                    onQueueClick = { showQueueSheet = true },
                    onEffectsClick = { showEffectsSheet = true },
                    onLyricsClick = { viewModel.openLyrics() },
                    onShareClick = { viewModel.currentTrack?.let { viewModel.shareTrack(it) } },
                    onCommentsClick = {
                        viewModel.selectedTrackForSheet = viewModel.currentTrack
                        viewModel.showCommentsSheet = true
                    },
                    onSleepTimerClick = { viewModel.showSleepTimerDialog = true },
                    onHapticsToggle = { viewModel.toggleHaptics() },
                    onMoreClick = { viewModel.currentTrack?.let { viewModel.showTrackOptions(it, fromPlayer = true) } },
                    activeColorMain = colorScheme.primary,
                    activeColorSecondary = colorScheme.secondary,
                    activeColorTertiary = colorScheme.tertiary,
                    onActiveColorMain = colorScheme.onPrimary,
                    onActiveColorSecondary = colorScheme.onSecondary,
                    onActiveColorTertiary = colorScheme.onTertiary,
                    inactiveColor = if (isBlurMode) Color.White.copy(alpha = 0.12f) else colorScheme.onSurface.copy(alpha = 0.08f),
                    inactiveContentColor = if (isBlurMode) Color.White else colorScheme.onSurface,
                    containerColor = if (isBlurMode) Color.White.copy(alpha = 0.12f) else colorScheme.surfaceContainerLowest.copy(alpha = 0.7f)
                )
            }
        }

        // Queue Modal Bottom Sheet
        if (showQueueSheet) {
            KittyModalBottomSheet(
                onDismissRequest = { showQueueSheet = false },
                containerColor = colorScheme.surfaceContainer,
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
                Spacer(Modifier.height(16.dp))
            }
        }

        // Audio Effects Sheet
        if (showEffectsSheet) {
            KittyModalBottomSheet(
                onDismissRequest = { showEffectsSheet = false },
                containerColor = colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                com.alananasss.kittytune.ui.player.AudioControlDock(viewModel)
                Spacer(Modifier.height(32.dp))
            }
        }

        SleepTimerDialog(viewModel)
        TrackTrimDialog(viewModel)
    }
}

@Composable
private fun rememberSmoothProgress(
    isPlayingProvider: () -> Boolean,
    currentPositionProvider: () -> Long,
    totalDuration: Long,
    trackId: Any?,
    sampleWhilePlayingMs: Long = 180L,
    sampleWhilePausedMs: Long = 800L,
    isVisible: Boolean = true
): Pair<androidx.compose.runtime.State<Float>, androidx.compose.runtime.State<Long>> {
    var sampledPosition by remember(trackId) { mutableLongStateOf(0L) }
    var sampledFraction by remember(trackId) { mutableFloatStateOf(0f) }

    val latestPositionProvider by rememberUpdatedState(newValue = currentPositionProvider)
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestSampleWhilePlayingMs by rememberUpdatedState(sampleWhilePlayingMs)
    val latestSampleWhilePausedMs by rememberUpdatedState(sampleWhilePausedMs)
    val latestIsVisible by rememberUpdatedState(isVisible)

    val safeUpperBound = totalDuration.coerceAtLeast(0L)
    val safeDuration = totalDuration.coerceAtLeast(1L)

    LaunchedEffect(totalDuration, trackId) {
        fun sampleNow() {
            val rawPosition = latestPositionProvider()
            val clampedPosition = rawPosition.coerceIn(0L, safeUpperBound)
            sampledPosition = clampedPosition
            sampledFraction = (clampedPosition / safeDuration.toFloat()).coerceIn(0f, 1f)
        }

        sampleNow()

        while (isActive) {
            val visible = latestIsVisible
            val playing = latestIsPlayingProvider()

            if (!visible || !playing) {
                val initialPos = latestPositionProvider()
                snapshotFlow {
                    latestIsVisible && (latestIsPlayingProvider() || latestPositionProvider() != initialPos)
                }.first { it }

                sampleNow()
                if (!latestIsVisible || !latestIsPlayingProvider()) {
                    continue
                }
            }

            val delayMillis = latestSampleWhilePlayingMs
            delay(delayMillis.coerceAtLeast(1L))
            sampleNow()
        }
    }

    val fractionState = remember(trackId) {
        derivedStateOf { sampledFraction }
    }

    val displayedPositionState = remember(totalDuration, trackId) {
        derivedStateOf {
            sampledPosition.coerceIn(0L, totalDuration.coerceAtLeast(0L))
        }
    }

    return fractionState to displayedPositionState
}
