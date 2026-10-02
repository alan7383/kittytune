package com.alananasss.kittytune.ui.yearlyplayback

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.yearlyplayback.Block
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackState
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackTarget
import com.alananasss.kittytune.data.yearlyplayback.YearlyPlaybackVariant

/**
 * Dynamic ambient theme palette for story slides.
 * Replaces harsh solid bars with a continuous, glowing color canvas matching the slide artboard.
 */
data class StoryPalette(
    val topColor: Color,
    val centerGlow: Color,
    val bottomColor: Color
)

private fun getSlidePalette(block: Block?, index: Int): StoryPalette {
    if (block == null) {
        return StoryPalette(
            topColor = Color(0xFF1E0A04),
            centerGlow = Color(0xFFFF5500),
            bottomColor = Color(0xFF140502)
        )
    }
    val id = block.artboardId.lowercase()
    return when {
        // Intro / Cover / 2025 / Welcome: Fiery orange flame matching SoundCloud 2025 Wrapped
        index == 0 || id.contains("intro") || id.contains("cover") || id.contains("2025") -> {
            StoryPalette(
                topColor = Color(0xFF220B04),
                centerGlow = Color(0xFFFF5500),
                bottomColor = Color(0xFF150402)
            )
        }
        // Top Artists: Crimson & fiery ember
        id.contains("artist") -> {
            StoryPalette(
                topColor = Color(0xFF220808),
                centerGlow = Color(0xFFE53935),
                bottomColor = Color(0xFF140404)
            )
        }
        // Top Tracks: Vibrant electric magenta & purple
        id.contains("track") -> {
            StoryPalette(
                topColor = Color(0xFF180826),
                centerGlow = Color(0xFF9C27B0),
                bottomColor = Color(0xFF0F041B)
            )
        }
        // Genres: Electric cyan & deep indigo
        id.contains("genre") -> {
            StoryPalette(
                topColor = Color(0xFF071228),
                centerGlow = Color(0xFF0091EA),
                bottomColor = Color(0xFF040A18)
            )
        }
        // Listening time / Minutes: Radiant amber & gold
        id.contains("minute") || id.contains("time") -> {
            StoryPalette(
                topColor = Color(0xFF261803),
                centerGlow = Color(0xFFFFA000),
                bottomColor = Color(0xFF160D02)
            )
        }
        // Habits / Personality / Mood: Emerald & teal aura
        id.contains("habit") || id.contains("mood") || id.contains("aura") || id.contains("personality") -> {
            StoryPalette(
                topColor = Color(0xFF041A16),
                centerGlow = Color(0xFF00897B),
                bottomColor = Color(0xFF02100E)
            )
        }
        // Summary slide: Fiery orange glow matching 2025 Playback Summary artwork
        id.contains("summary") -> {
            StoryPalette(
                topColor = Color(0xFF240804),
                centerGlow = Color(0xFFFF5500),
                bottomColor = Color(0xFF5A1402)
            )
        }
        // Save Playlist: SoundCloud iconic warm sunset
        block is Block.SavePlaylist || id.contains("save") || id.contains("playlist") -> {
            StoryPalette(
                topColor = Color(0xFF1E0A16),
                centerGlow = Color(0xFFFF5500),
                bottomColor = Color(0xFF4A1002)
            )
        }
        // Open Insights: Deep violet with warm base
        block is Block.OpenInsights || id.contains("insight") -> {
            StoryPalette(
                topColor = Color(0xFF180826),
                centerGlow = Color(0xFF7C4DFF),
                bottomColor = Color(0xFF2B0A18)
            )
        }
        // Fallback by index modulo with diverse rich palettes
        else -> {
            val fallbacks = listOf(
                StoryPalette(Color(0xFF220B04), Color(0xFFFF5500), Color(0xFF5A1402)),
                StoryPalette(Color(0xFF240808), Color(0xFFE53935), Color(0xFF4A0A02)),
                StoryPalette(Color(0xFF1C0826), Color(0xFF9C27B0), Color(0xFF3B0818)),
                StoryPalette(Color(0xFF0C1428), Color(0xFF0091EA), Color(0xFF08182A)),
                StoryPalette(Color(0xFF281804), Color(0xFFFFA000), Color(0xFF4A2002)),
                StoryPalette(Color(0xFF061C16), Color(0xFF00897B), Color(0xFF062018)),
                StoryPalette(Color(0xFF240804), Color(0xFFFF5500), Color(0xFF5A1402))
            )
            fallbacks[index % fallbacks.size]
        }
    }
}

/**
 * Full-screen immersive Story Player for SoundCloud Wrapped / Yearly Playback.
 *
 * Implements 100% parity with SoundCloud's decompiled:
 * com.soundcloud.android.yearlyplayback.YearlyPlaybackFragment
 *
 * Built with Material Design 3 guidelines:
 * - Edge-to-edge support with safeDrawing insets
 * - Dynamic color palettes with smooth background gradients
 * - Segmented step indicator
 * - 400ms hold-to-pause and left-right navigation gestures
 * - Share action using Android Intent
 */
@Composable
fun YearlyPlaybackScreen(
    onClose: () -> Unit,
    onOpenStats: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit = {},
    modifier: Modifier = Modifier,
    year: Int = 2025,
    target: YearlyPlaybackTarget = YearlyPlaybackTarget.FAN,
    variant: YearlyPlaybackVariant = YearlyPlaybackVariant.CURRENT_YEAR,
    viewModel: YearlyPlaybackViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val currentIndex by viewModel.currentIndex.collectAsState()
    val isPaused by viewModel.isPaused.collectAsState()
    val blocks by viewModel.blocks.collectAsState()

    var showShareSheet by remember { mutableStateOf(false) }
    var screenshotFile by remember { mutableStateOf<File?>(null) }
    var screenshotBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pendingSaveFile by remember { mutableStateOf<File?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { destinationUri: Uri? ->
        if (destinationUri != null) {
            val fileToSave = pendingSaveFile ?: screenshotFile
            val bmp = screenshotBitmap
            coroutineScope.launch(Dispatchers.IO) {
                val ok = YearlyPlaybackShareManager.saveScreenshotToUri(
                    context = context,
                    destinationUri = destinationUri,
                    sourceFile = fileToSave,
                    bitmap = bmp
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.share_screenshot_saved_toast)
                        else context.getString(R.string.share_screenshot_save_failed_toast),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    val activity = remember(context) {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) break
            ctx = ctx.baseContext
        }
        ctx as? Activity
    }

    androidx.compose.runtime.LaunchedEffect(year, target) {
        val selectedVariant = when (year) {
            2025 -> YearlyPlaybackVariant.CURRENT_YEAR
            2024 -> YearlyPlaybackVariant.PREVIOUS_YEAR
            else -> YearlyPlaybackVariant.MOCK
        }
        viewModel.loadData(target, selectedVariant, year)
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            viewModel.stop()
        }
    }

    val view = androidx.compose.ui.platform.LocalView.current
    androidx.compose.runtime.DisposableEffect(view) {
        val window = (view.context as? android.app.Activity)?.window
        val insetsController = window?.let { androidx.core.view.WindowCompat.getInsetsController(it, view) }
        val prevLightStatus = insetsController?.isAppearanceLightStatusBars ?: false
        val prevLightNav = insetsController?.isAppearanceLightNavigationBars ?: false
        insetsController?.isAppearanceLightStatusBars = false
        insetsController?.isAppearanceLightNavigationBars = false
        onDispose {
            insetsController?.isAppearanceLightStatusBars = prevLightStatus
            insetsController?.isAppearanceLightNavigationBars = prevLightNav
        }
    }

    val riveFile by viewModel.riveFile.collectAsState()
    val riveView = remember(riveFile) {
        riveFile?.let { file ->
            app.rive.runtime.kotlin.RiveAnimationView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                setRiveFile(
                    file,
                    fit = app.rive.runtime.kotlin.core.Fit.CONTAIN,
                    alignment = app.rive.runtime.kotlin.core.Alignment.CENTER
                )
            }
        }
    }

    BackHandler {
        onClose()
    }

    val backgroundModifier = when (state) {
        is YearlyPlaybackState.Display -> Modifier
        else -> Modifier.background(MaterialTheme.colorScheme.surface)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(backgroundModifier)
    ) {
        when (val s = state) {
            is YearlyPlaybackState.Prepare -> {
                LoadingStateView(
                    onClose = onClose,
                    modifier = Modifier.fillMaxSize()
                )
            }

            is YearlyPlaybackState.Empty -> {
                EmptyStateView(
                    onClose = onClose,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is YearlyPlaybackState.Error -> {
                ErrorStateView(
                    onRetry = { viewModel.loadData(target, variant, year) },
                    onClose = onClose,
                    modifier = Modifier.align(Alignment.Center)
                )
            }

            is YearlyPlaybackState.Display -> {
                // Dynamically resolved color palette matching current slide content
                val currentPalette = remember(s.block, currentIndex) {
                    getSlidePalette(s.block, currentIndex)
                }
                val animatedTopColor by animateColorAsState(
                    targetValue = currentPalette.topColor,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "topColor"
                )
                val animatedCenterGlow by animateColorAsState(
                    targetValue = currentPalette.centerGlow,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "centerGlow"
                )
                val animatedBottomColor by animateColorAsState(
                    targetValue = currentPalette.bottomColor,
                    animationSpec = tween(600, easing = FastOutSlowInEasing),
                    label = "bottomColor"
                )

                // Main Story Display - Full-bleed immersive layer matching SoundCloud
                Box(modifier = Modifier.fillMaxSize()) {
                    // Ambient Blurred Canvas (Base layer filling 100% of screen behind status and nav bars)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        animatedTopColor,
                                        animatedTopColor.copy(alpha = 0.92f),
                                        animatedBottomColor.copy(alpha = 0.92f),
                                        animatedBottomColor
                                    )
                                )
                            )
                    ) {
                        // Ambient glowing blurred orbs for radiant atmospheric depth
                        Box(
                            modifier = Modifier
                                .size(360.dp)
                                .align(Alignment.TopCenter)
                                .offset(y = (-40).dp)
                                .blur(60.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(animatedCenterGlow.copy(alpha = 0.35f), Color.Transparent)
                                    ),
                                    shape = CircleShape
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(420.dp)
                                .align(Alignment.Center)
                                .blur(70.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(animatedCenterGlow.copy(alpha = 0.28f), Color.Transparent)
                                    ),
                                    shape = CircleShape
                                )
                        )
                        Box(
                            modifier = Modifier
                                .size(360.dp)
                                .align(Alignment.BottomCenter)
                                .offset(y = 40.dp)
                                .blur(60.dp)
                                .background(
                                    Brush.radialGradient(
                                        listOf(animatedCenterGlow.copy(alpha = 0.35f), Color.Transparent)
                                    ),
                                    shape = CircleShape
                                )
                        )
                    }

                    val isInteractiveSlide = s.block is Block.SavePlaylist || s.block is Block.OpenInsights

                    if (!isInteractiveSlide) {
                        // Content Layer (Middle) - Full uncropped vector animation with true edge alpha feathering
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(9f / 16f)
                                .align(Alignment.Center)
                                .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            0.00f to Color.Transparent,
                                            0.07f to Color.Black,
                                            0.93f to Color.Black,
                                            1.00f to Color.Transparent
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        ) {
                            if (s.block is Block.ArtBoard) {
                                if (riveView != null) {
                                    AnimationBlock(
                                        riveAnimationView = riveView,
                                        artBoard = s.block,
                                        isPaused = isPaused,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    ArtBoardSlide(block = s.block)
                                }
                            }
                        }

                        // Story Gesture Overlay on TOP of Rive animation with full bounds
                        // Press & Hold (>400ms) pauses animation and audio; release resumes
                        StoryGestureOverlay(
                            onPrevious = { viewModel.previousBlock() },
                            onNext = { viewModel.nextBlock() },
                            onPause = {
                                riveView?.pause()
                                viewModel.pausePlayback()
                            },
                            onResume = {
                                riveView?.play()
                                viewModel.resumePlayback()
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Gesture overlay in background for sides navigation and hold-to-pause
                        StoryGestureOverlay(
                            onPrevious = { viewModel.previousBlock() },
                            onNext = { viewModel.nextBlock() },
                            onPause = {
                                riveView?.pause()
                                viewModel.pausePlayback()
                            },
                            onResume = {
                                riveView?.play()
                                viewModel.resumePlayback()
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Interactive Content Layer in Foreground (Buttons, Clickable Artwork)
                        Box(modifier = Modifier.fillMaxSize()) {
                            when (val block = s.block) {
                                is Block.SavePlaylist -> SavePlaylistSlide(
                                    block = block,
                                    onSaveToggle = { viewModel.toggleSavePlaylist(it) },
                                    onNavigateToPlaylist = onNavigateToPlaylist
                                )
                                is Block.OpenInsights -> OpenInsightsSlide(
                                    block = block,
                                    onOpenStats = onOpenStats
                                )
                                is Block.ArtBoard -> {}
                            }
                        }
                    }

                    // Header Layer (Top: StepIndicator + TopBanner) overlaid on top of content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        StepIndicator(
                            totalSteps = s.totalBlocks,
                            currentStep = currentIndex,
                            durationMs = s.durationMs,
                            isPaused = isPaused,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
                        )

                        TopBanner(
                            target = viewModel.target,
                            onShare = {
                                val act = activity ?: (view.context as? Activity)
                                if (act != null) {
                                    riveView?.pause()
                                    viewModel.pausePlayback()
                                    viewModel.onShareClicked()
                                    YearlyPlaybackShareManager.captureScreenshot(act, view) { file, bmp ->
                                        screenshotFile = file
                                        screenshotBitmap = bmp
                                        showShareSheet = true
                                    }
                                } else {
                                    viewModel.shareCurrentSlide(context)
                                }
                            },
                            onClose = onClose,
                            showBranding = s.block !is Block.SavePlaylist
                        )
                    }
                }
            }
        }

        if (showShareSheet) {
            val curBlock = (state as? YearlyPlaybackState.Display)?.block
            val shareUrl = remember(curBlock) {
                YearlyPlaybackShareManager.buildShareUrl(curBlock, YearlyPlaybackShareManager.ShareTarget.COPY_LINK)
            }
            val shareText = remember(curBlock, shareUrl) {
                YearlyPlaybackShareManager.buildShareText(context, curBlock, shareUrl)
            }

            YearlyPlaybackShareSheet(
                bitmap = screenshotBitmap,
                onDismiss = {
                    showShareSheet = false
                    riveView?.play()
                    viewModel.resumePlayback()
                },
                onSelectOption = { target ->
                    if (target == YearlyPlaybackShareManager.ShareTarget.SAVE_TO_DEVICE) {
                        pendingSaveFile = screenshotFile
                        val defaultFileName = "SoundCloud_Wrapped_2025_${System.currentTimeMillis() / 1000}.png"
                        createDocumentLauncher.launch(defaultFileName)
                        showShareSheet = false
                        riveView?.play()
                        viewModel.resumePlayback()
                    } else {
                        val targetShareUrl = YearlyPlaybackShareManager.buildShareUrl(curBlock, target)
                        val targetShareText = YearlyPlaybackShareManager.buildShareText(context, curBlock, targetShareUrl)
                        YearlyPlaybackShareManager.share(
                            context = context,
                            target = target,
                            screenshotFile = screenshotFile,
                            shareUrl = targetShareUrl,
                            shareText = targetShareText
                        )
                        showShareSheet = false
                        riveView?.play()
                        viewModel.resumePlayback()
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingStateView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars),
        contentAlignment = Alignment.Center
    ) {
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.btn_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            modifier = Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            ContainedLoadingIndicator()
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.yearly_playback_loading_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.yearly_playback_loading_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyStateView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Headphones,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = stringResource(R.string.yearly_playback_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.yearly_playback_empty_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(32.dp))

        FilledTonalButton(
            onClick = onClose,
            shape = ButtonDefaults.shape,
            colors = ButtonDefaults.filledTonalButtonColors()
        ) {
            Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = stringResource(R.string.yearly_playback_close))
        }
    }
}

@Composable
private fun ErrorStateView(
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = stringResource(R.string.yearly_playback_error_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.yearly_playback_error_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(28.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRetry,
                shape = ButtonDefaults.shape,
                colors = ButtonDefaults.buttonColors()
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.btn_retry))
            }

            FilledTonalButton(
                onClick = onClose,
                shape = ButtonDefaults.shape,
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Text(text = stringResource(R.string.yearly_playback_close))
            }
        }
    }
}
