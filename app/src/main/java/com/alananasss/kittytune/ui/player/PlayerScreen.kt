package com.alananasss.kittytune.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.alananasss.kittytune.ui.common.WindowSizeInfo
import com.alananasss.kittytune.ui.common.WindowHeightSizeClass
import com.alananasss.kittytune.ui.common.viewableCover
import com.alananasss.kittytune.ui.common.rememberWindowSizeInfo
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.ui.player.lyrics.WrongLyricsButton
import com.alananasss.kittytune.ui.player.lyrics.LyricLine
import com.alananasss.kittytune.ui.player.lyrics.LyricWord
import com.alananasss.kittytune.ui.utils.fadingEdge
import com.alananasss.kittytune.utils.makeTimeString
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import kotlinx.coroutines.*
import kotlin.math.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.view.ViewConfiguration
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PremiumMarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    edgeGradientWidth: Dp = 16.dp,
    delayMillis: Int = 2000,
    velocity: Dp = 30.dp,
    spacing: Dp = 48.dp
) {
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val spacingPx = with(density) { spacing.toPx() }

    SubcomposeLayout(modifier = modifier.clipToBounds()) { constraints ->
        val infiniteConstraints = constraints.copy(maxWidth = Constraints.Infinity)
        val placeable = subcompose("text_measure") {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }[0].measure(infiniteConstraints)

        val containerWidth = constraints.maxWidth.toFloat()
        val textWidth = placeable.width.toFloat()
        val isOverflowing = textWidth > containerWidth

        if (!isOverflowing) {
            val content = subcompose("static_content") {
                Text(
                    text = text,
                    style = style,
                    color = color,
                    maxLines = 1,
                    textAlign = textAlign
                )
            }[0].measure(constraints)

            layout(content.width, content.height) {
                content.place(0, 0)
            }
        } else {
            val content = subcompose("animated_content") {
                val offsetX = remember { Animatable(0f) }
                val startGradientAlpha = remember { Animatable(0f) }

                val gradientWidthPx = edgeGradientWidth.toPx()
                val cycleDistance = textWidth + spacingPx
                val duration = ((cycleDistance / velocity.toPx()) * 1000).toInt()

                LaunchedEffect(text, containerWidth, textWidth, isRtl) {
                    offsetX.snapTo(0f)
                    startGradientAlpha.snapTo(0f)

                    if (isOverflowing) {
                        while (isActive) {
                            delay(delayMillis.toLong())
                            launch {
                                startGradientAlpha.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(durationMillis = 500, easing = LinearEasing)
                                )
                            }
                            val targetValue = if (isRtl) cycleDistance else -cycleDistance
                            offsetX.animateTo(
                                targetValue = targetValue,
                                animationSpec = tween(durationMillis = duration, easing = LinearEasing)
                            )
                            startGradientAlpha.animateTo(
                                targetValue = 0f,
                                animationSpec = tween(durationMillis = 500, easing = LinearEasing)
                            )
                            offsetX.snapTo(0f)
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()

                            val alpha = startGradientAlpha.value
                            if (alpha > 0f) {
                                val startBrush = Brush.horizontalGradient(
                                    0f to if (isRtl) Color.Transparent else Color.Black.copy(alpha = alpha),
                                    1f to if (isRtl) Color.Black.copy(alpha = alpha) else Color.Transparent,
                                    startX = if (isRtl) size.width - gradientWidthPx else 0f,
                                    endX = if (isRtl) size.width else gradientWidthPx
                                )
                                drawRect(
                                    brush = startBrush,
                                    topLeft = Offset(if (isRtl) size.width - gradientWidthPx else 0f, 0f),
                                    size = Size(width = gradientWidthPx, height = size.height),
                                    blendMode = BlendMode.DstOut
                                )
                            }
                            val endBrush = Brush.horizontalGradient(
                                0f to if (isRtl) Color.Black else Color.Transparent,
                                1f to if (isRtl) Color.Transparent else Color.Black,
                                startX = if (isRtl) 0f else size.width - gradientWidthPx,
                                endX = if (isRtl) gradientWidthPx else size.width
                            )
                            drawRect(
                                brush = endBrush,
                                topLeft = Offset(if (isRtl) 0f else size.width - gradientWidthPx, 0f),
                                size = Size(width = gradientWidthPx, height = size.height),
                                blendMode = BlendMode.DstOut
                            )
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .wrapContentWidth(unbounded = true, align = Alignment.Start)
                            .graphicsLayer {
                                translationX = offsetX.value
                            }
                    ) {
                        Text(text = text, style = style, color = color, maxLines = 1, softWrap = false)
                        Spacer(modifier = Modifier.width(spacing))
                        Text(text = text, style = style, color = color, maxLines = 1, softWrap = false)
                        Spacer(modifier = Modifier.width(spacing))
                    }
                }
            }[0].measure(constraints)

            layout(content.width, content.height) {
                content.place(0, 0)
            }
        }
    }
}

@Composable
fun SyncedLyricsView(viewModel: PlayerViewModel, showControls: Boolean = true) {
    val currentPosition = viewModel.currentPosition
    val adjustedPosition = currentPosition + viewModel.lyricsOffset
    val lyrics = viewModel.lyricsLines
    val listState = rememberLazyListState()
    val fontSize = viewModel.lyricsFontSize
    val alignment = when (viewModel.lyricsAlignment) {
        LyricsAlignment.LEFT -> TextAlign.Left
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.RIGHT -> TextAlign.Right
    }

    val fadeBrush = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.15f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent
        )
    }

    // Delta-based interpolation engine: does NOT restart on currentPosition
    val isPlaying = viewModel.isPlaying
    val speed = viewModel.effectsState.speed
    var smoothDrawPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }

    // Delta-based loop: only relaunches if isPlaying or speed changes
    LaunchedEffect(isPlaying, speed) {
        var lastFrameNanos = System.nanoTime()
        while (isActive && isPlaying) {
            withFrameNanos { frameNanos ->
                val deltaMs = (frameNanos - lastFrameNanos) / 1_000_000f
                lastFrameNanos = frameNanos
                smoothDrawPosition += deltaMs * speed
            }
        }
    }

    // Drift correction only if >400ms (seeks) — no reset on normal updates
    LaunchedEffect(currentPosition) {
        val drift = kotlin.math.abs(smoothDrawPosition - currentPosition)
        if (drift > 400f) {
            smoothDrawPosition = currentPosition.toFloat()
        }
    }

    val activeIndex by remember(lyrics, viewModel.lyricsOffset) {
        derivedStateOf {
            val pos = (smoothDrawPosition + viewModel.lyricsOffset).toLong()
            lyrics.indexOfFirst { pos >= it.startTime && pos < it.endTime }
                .takeIf { it != -1 }
                ?: lyrics.indexOfLast { pos >= it.startTime }
        }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !listState.isScrollInProgress) {
            listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val halfHeight = screenHeight / 2
        val topPadding = halfHeight - 50.dp

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = halfHeight),
            modifier = Modifier
                .fillMaxSize()
                .fadingEdge(fadeBrush),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeIndex
                val targetScale = 1.0f
                val targetAlpha = if (isActive) 1.0f else (if (index < activeIndex) 0.45f else 0.70f)

                val scale by animateFloatAsState(targetScale, tween(400), label = "scale")
                val alpha by animateFloatAsState(targetAlpha, tween(400), label = "alpha")

                val hzAlignment = when (alignment) {
                    TextAlign.Left -> Alignment.Start
                    TextAlign.Center -> Alignment.CenterHorizontally
                    TextAlign.Right -> Alignment.End
                    else -> Alignment.CenterHorizontally
                }

                val linePaddingValues = when (alignment) {
                    TextAlign.Left -> PaddingValues(start = 8.dp, end = 24.dp)
                    TextAlign.Right -> PaddingValues(start = 24.dp, end = 8.dp)
                    else -> PaddingValues(horizontal = 16.dp)
                }

                Column(
                    horizontalAlignment = hzAlignment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(linePaddingValues)
                        .scale(scale)
                        .alpha(alpha)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.seekTo(line.startTime) }
                ) {
                    val isWordSync = viewModel.isWordSyncEnabled
                    val isAppleEffect = viewModel.isAppleMusicEffectEnabled
                    val displayWords = if (isWordSync) line.words.orEmpty() else emptyList()

                    if (isActive && displayWords.isNotEmpty()) {
                        if (isAppleEffect) {
                            // Apple Music-style: smooth character-level clip reveal
                            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                            val reconstructedText = remember(displayWords) {
                                displayWords.joinToString("") { it.word }
                            }
                            val wordRanges = remember(displayWords) {
                                val ranges = mutableListOf<Pair<Int, Int>>()
                                var currentLen = 0
                                for (w in displayWords) {
                                    ranges.add(currentLen to currentLen + w.word.length)
                                    currentLen += w.word.length
                                }
                                ranges
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                // Dimmed background text
                                Text(
                                    text = reconstructedText,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * 1.4).sp
                                    ),
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = alignment,
                                    modifier = Modifier.fillMaxWidth(),
                                    onTextLayout = { textLayoutResult = it }
                                )
                                // Bright foreground text clipped by progress
                                Text(
                                    text = reconstructedText,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * 1.4).sp
                                    ),
                                    color = Color.White,
                                    textAlign = alignment,
                                    modifier = Modifier.fillMaxWidth().drawWithContent {
                                        val currentPos = smoothDrawPosition + viewModel.lyricsOffset
                                        val layout = textLayoutResult ?: return@drawWithContent
                                        val path = Path()
                                        val safeTextLength = (reconstructedText.length - 1).coerceAtLeast(0)
                                        for (i in displayWords.indices) {
                                            val w = displayWords[i]
                                            val range = wordRanges[i]
                                            if (range.first >= range.second) continue
                                            if (currentPos >= w.endTime) {
                                                for (c in range.first until range.second) {
                                                    path.addRect(layout.getBoundingBox(c.coerceIn(0, safeTextLength)))
                                                }
                                            } else if (currentPos >= w.startTime) {
                                                val progress = ((currentPos - w.startTime).toFloat() /
                                                        (w.endTime - w.startTime).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                                val exactProgressChars = progress * (range.second - range.first)
                                                val fullySungChars = exactProgressChars.toInt()
                                                val charFraction = exactProgressChars - fullySungChars
                                                for (c in range.first until range.first + fullySungChars) {
                                                    path.addRect(layout.getBoundingBox(c.coerceIn(0, safeTextLength)))
                                                }
                                                val partialCharIdx = range.first + fullySungChars
                                                if (partialCharIdx < range.second) {
                                                    val cBbox = layout.getBoundingBox(
                                                        partialCharIdx.coerceIn(0, safeTextLength)
                                                    )
                                                    val cX = cBbox.left + (cBbox.right - cBbox.left) * charFraction
                                                    path.addRect(Rect(cBbox.left, cBbox.top, cX, cBbox.bottom))
                                                }
                                            }
                                        }
                                        clipPath(path) { this@drawWithContent.drawContent() }
                                    }
                                )
                            }
                        } else {
                            // Simple word-by-word color highlight
                            val reconstructedText = buildAnnotatedString {
                                displayWords.forEach { word ->
                                    val isWordActive = (adjustedPosition) >= word.startTime
                                    val wordColor = if (isWordActive) Color.White else Color.White.copy(alpha = 0.5f)
                                    withStyle(SpanStyle(color = wordColor)) { append(word.word) }
                                }
                            }
                            Text(
                                text = reconstructedText,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.4).sp
                                ),
                                textAlign = alignment,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        // No word sync or line not active — plain text
                        val textColor = if (isActive) Color.White else Color.White.copy(alpha = 0.5f)
                        Text(
                            text = line.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.4).sp
                            ),
                            color = textColor,
                            textAlign = alignment,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (showControls) {
            WrongLyricsButton(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
                onClick = { viewModel.isSearchingLyrics = true }
            )
        }
    }
}

@Composable
fun PlainLyricsView(viewModel: PlayerViewModel, showControls: Boolean = true) {
    val text = viewModel.rawPlainLyrics ?: stringResource(R.string.lyrics_no_data)
    val clipboardManager = LocalClipboardManager.current

    val fontSize = viewModel.lyricsFontSize
    val alignment = when (viewModel.lyricsAlignment) {
        LyricsAlignment.LEFT -> TextAlign.Left
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.RIGHT -> TextAlign.Right
    }

    val startPadding = when (alignment) {
        TextAlign.Left -> 8.dp
        TextAlign.Right -> 24.dp
        else -> 16.dp
    }

    val endPadding = when (alignment) {
        TextAlign.Left -> 24.dp
        TextAlign.Right -> 8.dp
        else -> 16.dp
    }

    val lines = remember(text) { text.split("\n") }

    val fadeBrush = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.15f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .fadingEdge(fadeBrush),
            contentPadding = PaddingValues(
                top = 70.dp,
                bottom = 180.dp,
                start = startPadding,
                end = endPadding
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(items = lines) { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.4).sp
                    ),
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = alignment,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (showControls) {
            FloatingActionButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(text))
                },
                containerColor = Color.White.copy(alpha = 0.2f),
                contentColor = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(24.dp)
                    .size(48.dp)
            ) {
                Icon(
                    Icons.Rounded.ContentCopy,
                    stringResource(R.string.lyrics_copy_text),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun InlineLyricsContent(viewModel: PlayerViewModel) {
    if (viewModel.lyricsLines.isNotEmpty()) {
        SyncedLyricsView(viewModel = viewModel, showControls = false)
    } else {
        PlainLyricsView(viewModel = viewModel, showControls = false)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    var useNewDesign by remember { mutableStateOf(prefs.getNewPlayerDesignEnabled()) }

    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "new_player_design_enabled") {
                useNewDesign = prefs.getNewPlayerDesignEnabled()
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    if (useNewDesign) {
        NewPlayerScreen(viewModel, onClose)
    } else {
        OldPlayerScreen(viewModel, onClose)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val track = viewModel.currentTrack ?: return
    BackHandler(enabled = !viewModel.showLyricsSheet, onBack = onClose)

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
    val backgroundStyle = remember { prefs.getPlayerStyle() }
    var showLyricsButtonEnabled by remember { mutableStateOf(prefs.getShowLyricsButtonEnabled()) }
    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_lyrics_button_enabled") {
                showLyricsButtonEnabled = prefs.getShowLyricsButtonEnabled()
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isBlurMode = backgroundStyle == PlayerBackgroundStyle.BLUR

    val mainContentColor = if (isBlurMode) Color.White else MaterialTheme.colorScheme.onBackground
    val subContentColor =
        if (isBlurMode) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val iconTint = if (isBlurMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val animatedColor by animateColorAsState(
        targetValue = viewModel.backgroundColor,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "backgroundColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isBlurMode) Color.Black else MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            }
    ) {
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
                        modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.6f)
                    )
                }
            }

            PlayerBackgroundStyle.GRADIENT -> {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedColor.copy(alpha = 0.7f),
                                animatedColor.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                )
            }

            PlayerBackgroundStyle.THEME -> {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val windowSizeInfo = rememberWindowSizeInfo(maxWidth, maxHeight)
            val isPhoneLandscape =
                windowSizeInfo.isLandscape && !windowSizeInfo.isTablet
            if (isPhoneLandscape) {
                PhoneLandscapePlayerView(
                    viewModel = viewModel,
                    onClose = onClose,
                    onEffectsClick = { showEffectsSheet = true },
                    onQueueClick = { showQueueSheet = true },
                    mainContentColor = mainContentColor,
                    subContentColor = subContentColor,
                    iconTint = iconTint,
                    animatedColor = animatedColor,
                    isBlurMode = isBlurMode
                )
            } else if (windowSizeInfo.isTablet) {
                TabletFullScreenPlayerView(
                    viewModel = viewModel,
                    onClose = {
                        viewModel.isPlayerExpanded = false
                        viewModel.isSidePlayerOpen = false
                    },
                    onToggleSplitMode = {
                        viewModel.isPlayerExpanded = false
                        viewModel.isSidePlayerOpen = true
                    },
                    mainContentColor = mainContentColor,
                    subContentColor = subContentColor,
                    iconTint = iconTint,
                    animatedColor = animatedColor,
                    isBlurMode = isBlurMode
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                        PlayerHeader(
                            onClose = onClose,
                            viewModel = viewModel,
                            contentColor = mainContentColor,
                            subContentColor = subContentColor,
                            accentColor = animatedColor,
                            onCollapseToSide = if (windowSizeInfo.showTabletDock) {
                                {
                                    viewModel.isPlayerExpanded = false
                                    viewModel.isSidePlayerOpen = true
                                }
                            } else null
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                        initialPage = viewModel.currentQueueIndex.coerceAtLeast(0),
                        pageCount = { viewModel.queueState.size.takeIf { it > 0 } ?: 1 }
                    )

                    LaunchedEffect(viewModel.currentQueueIndex) {
                        if (viewModel.currentQueueIndex >= 0 && viewModel.currentQueueIndex != pagerState.currentPage && viewModel.currentQueueIndex < pagerState.pageCount) {
                            try {
                                pagerState.animateScrollToPage(viewModel.currentQueueIndex)
                            } catch (e: Exception) {
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

                    val showLyrics = viewModel.showInlineLyrics
                    val lyricsAlpha by animateFloatAsState(
                        targetValue = if (showLyrics) 1f else 0f,
                        tween(400),
                        label = "lyricsAlpha"
                    )
                    val coverAlpha by animateFloatAsState(
                        targetValue = if (showLyrics) 0f else 1f,
                        tween(400),
                        label = "coverAlpha"
                    )

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(modifier = Modifier.fillMaxWidth().alpha(coverAlpha).zIndex(if (showLyrics) 0f else 1f)) {
                            if (viewModel.queueState.isNotEmpty()) {
                                androidx.compose.foundation.pager.HorizontalPager(
                                    state = pagerState,
                                    modifier = Modifier.fillMaxWidth(),
                                    pageSpacing = 16.dp,
                                    contentPadding = PaddingValues(24.dp)
                                ) { page ->
                                    val pageTrack = viewModel.queueState.getOrNull(page) ?: track
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .shadow(
                                                24.dp,
                                                RoundedCornerShape(20.dp),
                                                spotColor = if (isBlurMode) Color.Black else animatedColor
                                            )
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        AsyncImage(
                                            model = pageTrack.fullResArtwork,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .padding(24.dp)
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .shadow(
                                            24.dp,
                                            RoundedCornerShape(20.dp),
                                            spotColor = if (isBlurMode) Color.Black else animatedColor
                                        )
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = track.fullResArtwork,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().alpha(lyricsAlpha).zIndex(if (showLyrics) 1f else 0f)) {
                            if (lyricsAlpha > 0f) {
                                Box(modifier = Modifier.padding(24.dp).fillMaxWidth().aspectRatio(1f)) {
                                    InlineLyricsContent(viewModel = viewModel)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {

                            Column(
                                modifier = Modifier.weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                PremiumMarqueeText(
                                    text = track.title ?: stringResource(R.string.untitled_track),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = mainContentColor,
                                    edgeGradientWidth = 24.dp,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable {
                                        track.user?.id?.let {
                                            if (it > 0) viewModel.navigateToArtist(
                                                it
                                            )
                                        }
                                    }
                                ) {
                                    PremiumMarqueeText(
                                        text = track.user?.username
                                            ?: stringResource(R.string.unknown_artist),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = subContentColor,
                                        edgeGradientWidth = 16.dp,
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
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AnimatedVisibility(
                                    visible = viewModel.hasLyrics && showLyricsButtonEnabled,
                                    enter = fadeIn(animationSpec = tween(400)),
                                    exit = fadeOut(animationSpec = tween(200))
                                ) {
                                    IconButton(
                                        onClick = { viewModel.openLyrics() },
                                        modifier = Modifier.size(44.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Description,
                                            contentDescription = stringResource(R.string.player_lyrics),
                                            tint = if (viewModel.showInlineLyrics) animatedColor else iconTint.copy(
                                                alpha = 0.8f
                                            ), // Active color
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                }

                                val view = LocalView.current
                                IconButton(
                                    onClick = {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        viewModel.toggleLike()
                                    },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    val targetColor =
                                        if (viewModel.isLiked) animatedColor else iconTint
                                    val heartColor by animateColorAsState(
                                        targetValue = targetColor,
                                        animationSpec = tween(300),
                                        label = "color"
                                    )

                                    AnimatedContent(
                                        targetState = viewModel.isLiked,
                                        transitionSpec = {
                                            if (targetState) {
                                                (fadeIn(tween(300)) + scaleIn(
                                                    initialScale = 0.7f,
                                                    animationSpec = tween(
                                                        300,
                                                        easing = LinearOutSlowInEasing
                                                    )
                                                ))
                                                    .togetherWith(fadeOut(tween(200)))
                                            } else {
                                                (fadeIn(tween(300)) + scaleIn(
                                                    initialScale = 1.0f,
                                                    animationSpec = tween(300)
                                                ))
                                                    .togetherWith(
                                                        fadeOut(tween(200)) + scaleOut(
                                                            targetScale = 0.7f,
                                                            animationSpec = tween(200)
                                                        )
                                                    )
                                            }
                                        },
                                        label = "LikeAnimation"
                                    ) { isLiked ->
                                        Icon(
                                            imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                            contentDescription = stringResource(R.string.player_like_action),
                                            tint = heartColor,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))

                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        PlayerProgress(viewModel, mainContentColor)
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        PlayerControls(
                            viewModel = viewModel,
                            animatedMainColor = animatedColor,
                            contentColorOverride = mainContentColor,
                            onEffectsClick = { showEffectsSheet = true },
                            onQueueClick = { showQueueSheet = true }
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
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
                Spacer(Modifier.height(16.dp))
            }
        }

        SleepTimerDialog(viewModel)
    }
}

@Composable
fun PlayerHeader(
    onClose: () -> Unit,
    viewModel: PlayerViewModel,
    contentColor: Color,
    subContentColor: Color,
    accentColor: Color,
    onCollapseToSide: (() -> Unit)? = null
) {
    val context = viewModel.currentContext

    val textShadow = androidx.compose.ui.graphics.Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
        blurRadius = 6f
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onCollapseToSide != null) {
                IconButton(onClick = onCollapseToSide) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ViewSidebar,
                        contentDescription = "Side Player",
                        tint = contentColor
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.btn_close), tint = contentColor)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        ) {
            Text(
                stringResource(R.string.player_playing_now),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    shadow = textShadow
                ),
                color = subContentColor
            )

            if (context != null) {
                PremiumMarqueeText(
                    text = context.displayText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        shadow = textShadow
                    ),
                    color = accentColor,
                    textAlign = TextAlign.Center,
                    edgeGradientWidth = 12.dp,
                    modifier = Modifier.clickable { viewModel.navigateToContext() }
                )
            }
        }

        IconButton(onClick = { viewModel.currentTrack?.let { viewModel.showTrackOptions(it, fromPlayer = true) } }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.btn_options), tint = contentColor)
        }
    }
}

data class DockOptionItem(val icon: ImageVector, val text: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepostDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var caption by remember { mutableStateOf("") }
    val maxChars = 140
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_repost_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { if (it.length <= maxChars) caption = it },
                    placeholder = { Text(stringResource(R.string.dialog_repost_caption_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    trailingIcon = {
                        Text(
                            text = "${caption.length}/$maxChars",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(caption) }) { Text(stringResource(R.string.dialog_repost_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) } }
    )
}

@Composable
fun MenuSheetContent(viewModel: PlayerViewModel) {
    val track = viewModel.trackForMenu ?: viewModel.currentTrack ?: return
    val context = LocalContext.current
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val storageTrigger by DownloadManager.storageTrigger.collectAsState()
    val isLocalFile = track.id < 0 && track.source != "youtube"

    val isReposted = viewModel.isTrackReposted(track.id)
    var showRepostDialog by remember { mutableStateOf(false) }
    var showDeleteRepostConfirm by remember { mutableStateOf(false) }

    var showDeleteDialog by remember { mutableStateOf(false) }
    val isDownloaded by produceState(initialValue = false, track.id, storageTrigger) {
        val localTrack = DownloadManager.getLocalTrack(track.id)
        value = localTrack?.localAudioPath?.isNotEmpty() == true
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (isLocalFile) stringResource(R.string.menu_remove_local_q) else stringResource(R.string.menu_remove_download_q)) },
            text = { Text(if (isLocalFile) stringResource(R.string.menu_remove_local_body) else stringResource(R.string.menu_remove_download_body)) },
            confirmButton = {
                TextButton(onClick = {
                    DownloadManager.deleteTrack(track.id); showDeleteDialog = false; viewModel.showMenuSheet = false
                }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    if (showRepostDialog) {
        RepostDialog(
            onDismiss = { showRepostDialog = false },
            onConfirm = { caption ->
                viewModel.repostTrack(track, caption); showRepostDialog = false; viewModel.showMenuSheet = false
            })
    }

    if (showDeleteRepostConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteRepostConfirm = false },
            title = { Text(stringResource(R.string.dialog_repost_delete_title)) },
            text = { Text(stringResource(R.string.dialog_repost_delete_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteRepost(track.id); showDeleteRepostConfirm = false; viewModel.showMenuSheet = false
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(
                        stringResource(R.string.btn_delete)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDeleteRepostConfirm = false
                }) { Text(stringResource(R.string.btn_cancel)) }
            }
        )
    }

    val gridItems = mutableListOf<DockOptionItem>().apply {
        if (viewModel.isMenuContextFromPlayer) {
            add(
                DockOptionItem(
                    Icons.Rounded.Shuffle,
                    stringResource(R.string.menu_shuffle)
                ) { viewModel.toggleShuffle() })
            add(
                DockOptionItem(
                    Icons.Rounded.Repeat,
                    stringResource(R.string.menu_repeat)
                ) { viewModel.toggleRepeatMode() })
        }
        if (!viewModel.isMenuContextFromPlayer) {
            add(
                DockOptionItem(
                    Icons.AutoMirrored.Rounded.PlaylistPlay,
                    stringResource(R.string.menu_play_next)
                ) { viewModel.insertNext(listOf(track)); viewModel.showMenuSheet = false })
            add(
                DockOptionItem(
                    Icons.AutoMirrored.Rounded.QueueMusic,
                    stringResource(R.string.menu_add_queue)
                ) { viewModel.addToQueue(listOf(track)); viewModel.showMenuSheet = false })
        }
        if (track.source != "youtube" && !isLocalFile) {
            add(
                DockOptionItem(
                    Icons.AutoMirrored.Rounded.Comment,
                    stringResource(R.string.menu_comments)
                ) { viewModel.openComments(track) })
        }
        if (track.source != "youtube" && !isLocalFile) {
            if (isReposted) {
                add(
                    DockOptionItem(
                        Icons.Rounded.Repeat,
                        stringResource(R.string.menu_reposted)
                    ) { showDeleteRepostConfirm = true })
            } else {
                add(DockOptionItem(Icons.Rounded.Repeat, stringResource(R.string.menu_repost)) {
                    showRepostDialog = true
                })
            }
        }
        if (track.source != "youtube") {
            add(DockOptionItem(Icons.Rounded.Info, stringResource(R.string.menu_details)) {
                viewModel.openTrackDetails(
                    track
                )
            })
        }
        add(DockOptionItem(Icons.Rounded.Description, stringResource(R.string.player_lyrics)) {
            viewModel.openLyrics(
                track,
                forceSheet = true
            )
        })
        add(DockOptionItem(Icons.Default.Add, stringResource(R.string.menu_add_playlist)) {
            viewModel.showMenuSheet = false; viewModel.showAddToPlaylistSheet = true
        })
        if (track.source != "youtube" && !isLocalFile) {
            add(
                DockOptionItem(
                    Icons.Default.Person,
                    stringResource(R.string.menu_go_artist)
                ) { track.user?.id?.let { viewModel.navigateToArtist(it) } })
        }
        if (!isLocalFile) {
            add(DockOptionItem(Icons.Rounded.Radio, stringResource(R.string.menu_track_radio)) {
                if (track.source == "youtube") {
                    viewModel.startYoutubeRadio(track)
                } else {
                    viewModel.startRadioFromTrack(track)
                }
            })
        }
        if (!isLocalFile) {
            add(
                DockOptionItem(
                    Icons.Outlined.Share,
                    stringResource(R.string.btn_share)
                ) { viewModel.shareTrack(track) })
        }
        if (viewModel.menuContextPlaylistId != null && viewModel.menuContextPlaylistId!! < 0) {
            add(
                DockOptionItem(
                    Icons.Outlined.Delete,
                    stringResource(R.string.menu_remove)
                ) { viewModel.removeFromContextPlaylist(viewModel.menuContextPlaylistId!!, track) })
        }
        if (viewModel.isMenuContextFromPlayer) {
            add(
                DockOptionItem(
                    Icons.Rounded.Bedtime,
                    stringResource(R.string.sleep_timer_title)
                ) { viewModel.showSleepTimerDialog = true })
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).padding(bottom = 24.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 24.dp).padding(horizontal = 8.dp)
                ) {
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant).viewableCover(track.fullResArtwork),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            text = track.title ?: stringResource(R.string.untitled_track),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = track.user?.username ?: stringResource(R.string.unknown_artist),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (track.user?.verified == true) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    Icons.Rounded.Verified,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        items(gridItems) { item ->
            val activeColor = MaterialTheme.colorScheme.primary
            val inactiveColor = MaterialTheme.colorScheme.onSurface
            var tint = inactiveColor
            var text = item.text

            if (item.text == stringResource(R.string.menu_shuffle) && viewModel.shuffleEnabled) tint = activeColor
            if (item.text == stringResource(R.string.menu_reposted)) tint = activeColor
            if (item.text == stringResource(R.string.menu_repeat)) {
                if (viewModel.repeatMode != com.alananasss.kittytune.ui.player.RepeatMode.NONE) tint = activeColor
                text = when (viewModel.repeatMode) {
                    com.alananasss.kittytune.ui.player.RepeatMode.ALL -> stringResource(R.string.menu_repeat_all)
                    com.alananasss.kittytune.ui.player.RepeatMode.ONE -> stringResource(R.string.menu_repeat_one)
                    else -> stringResource(R.string.menu_repeat)
                }
            }
            if (item.text == stringResource(R.string.sleep_timer_title) && viewModel.isSleepTimerActive) {
                tint = activeColor
                text = viewModel.formatSleepTimerRemaining()
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { item.onClick() }) {
                Icon(item.icon, null, modifier = Modifier.size(32.dp), tint = tint)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    color = tint
                )
            }
        }
        if (!isLocalFile) {
            item {
                val trackId = track.id
                val isDownloading = DownloadManager.isTrackDownloading(trackId)
                val downloadProgressVal = downloadProgress[trackId]
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                    if (isDownloaded) showDeleteDialog = true else if (isDownloading) DownloadManager.cancelDownload(
                        trackId
                    ) else viewModel.downloadTrack(track)
                }) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                        if (isDownloading) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = (downloadProgressVal ?: 0) / 100f,
                                label = "progress"
                            )
                            CircularWavyProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize()
                            )
                            Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(18.dp))
                        } else {
                            val icon = if (isDownloaded) Icons.Default.Delete else Icons.Rounded.Download
                            val tint =
                                if (isDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            Icon(icon, null, modifier = Modifier.fillMaxSize(), tint = tint)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    val textLabel =
                        if (isDownloaded) stringResource(R.string.btn_delete) else if (isDownloading) stringResource(R.string.btn_cancel) else stringResource(
                            R.string.btn_download
                        )
                    val textColor =
                        if (isDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    Text(
                        textLabel,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        color = textColor
                    )
                }
            }
        }
    }
}

@Composable
fun AddToPlaylistContent(viewModel: PlayerViewModel) {
    val singleTrack = viewModel.trackForMenu ?: viewModel.currentTrack
    val bulkTracks = viewModel.tracksToAddInBulk
    if (singleTrack == null && bulkTracks == null) return
    var showCreateInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(
            if (bulkTracks != null) stringResource(
                R.string.add_to_playlist_title_multi,
                bulkTracks.size
            ) else stringResource(R.string.add_to_playlist_title_single),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 16.dp)
        )
        if (showCreateInput) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text(stringResource(R.string.lib_create_playlist_hint)) },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        if (bulkTracks != null) viewModel.createAndAddTracksToPlaylist(
                            newName,
                            bulkTracks
                        ) else if (singleTrack != null) viewModel.createAndAddToPlaylist(newName, singleTrack)
                    }
                }) { Text(stringResource(R.string.btn_ok)) }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Surface(
                onClick = { showCreateInput = true },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth().height(56.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        Icons.Default.Add,
                        null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    ); Spacer(Modifier.width(8.dp)); Text(
                    stringResource(R.string.add_to_playlist_new),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            itemsIndexed(items = viewModel.userPlaylists) { _, playlist ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable {
                        if (bulkTracks != null) viewModel.addTracksToPlaylist(
                            playlist.id,
                            bulkTracks
                        ) else if (singleTrack != null) viewModel.addToPlaylist(playlist.id, singleTrack)
                    }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = playlist.localCoverPath ?: playlist.artworkUrl.ifEmpty { "https://picsum.photos/200" },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(
                            playlist.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        ); Text(
                        stringResource(R.string.playlist_num_tracks, playlist.trackCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepTimerDialog(viewModel: PlayerViewModel) {
    if (!viewModel.showSleepTimerDialog) return

    var sliderValue by remember { mutableFloatStateOf(30f) }
    var showCustomInput by remember { mutableStateOf(false) }
    var customMinutes by remember { mutableStateOf("") }
    val selectedMinutes = sliderValue.toInt()
    val calendar = remember(selectedMinutes) {
        java.util.Calendar.getInstance().apply {
            add(java.util.Calendar.MINUTE, selectedMinutes)
        }
    }
    val stopTimeText = remember(selectedMinutes) {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(calendar.time)
    }

    AlertDialog(
        onDismissRequest = { viewModel.showSleepTimerDialog = false },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Icon(
                Icons.Rounded.Bedtime,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = null,
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (viewModel.isSleepTimerActive) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = viewModel.formatSleepTimerRemaining(),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            if (!viewModel.sleepTimerEndOfTrack) {
                                val activeStopTimeText = remember(viewModel.sleepTimerRemainingMs) {
                                    val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                                    val cal = java.util.Calendar.getInstance()
                                    cal.timeInMillis = System.currentTimeMillis() + viewModel.sleepTimerRemainingMs
                                    sdf.format(cal.time)
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.sleep_timer_stop_at, activeStopTimeText),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = {
                                    viewModel.cancelSleepTimer()
                                    viewModel.showSleepTimerDialog = false
                                },
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                ),
                                shapes = ButtonDefaults.shapes()
                            ) {
                                Icon(Icons.Rounded.Close, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.sleep_timer_cancel))
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(16.dp))
                }

                Text(
                    text = stringResource(R.string.sleep_timer_slider_minutes, selectedMinutes),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.sleep_timer_stop_at, stopTimeText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                // Slider
                val view = LocalView.current
                var lastStepValue by remember { mutableIntStateOf(sliderValue.toInt()) }
                Slider(
                    value = sliderValue,
                    onValueChange = {
                        val newStep = it.toInt()
                        if (newStep != lastStepValue) {
                            lastStepValue = newStep
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        sliderValue = it
                    },
                    valueRange = 5f..120f,
                    steps = 22,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        stringResource(R.string.sleep_timer_5),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.sleep_timer_hours_minutes_format, 2, 0).replace(" 0m", "")
                            .replace(" 0p", ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(Modifier.height(16.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf(15, 30, 45, 60, 90).forEach { mins ->
                        val label = when (mins) {
                            60 -> stringResource(R.string.sleep_timer_60)
                            90 -> stringResource(R.string.sleep_timer_90)
                            else -> stringResource(R.string.sleep_timer_minutes_format, mins)
                        }
                        FilterChip(
                            selected = selectedMinutes == mins,
                            onClick = { sliderValue = mins.toFloat() },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            shape = RoundedCornerShape(12.dp)
                        )
                    }
                    FilterChip(
                        selected = viewModel.sleepTimerEndOfTrack,
                        onClick = { viewModel.startSleepTimerEndOfTrack() },
                        label = {
                            Text(
                                stringResource(R.string.sleep_timer_end_of_track),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )

                    FilterChip(
                        selected = showCustomInput,
                        onClick = { showCustomInput = !showCustomInput },
                        label = {
                            Text(
                                stringResource(R.string.sleep_timer_custom),
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp)) },
                        shape = RoundedCornerShape(12.dp)
                    )
                }

                AnimatedVisibility(
                    visible = showCustomInput,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp)
                    ) {
                        OutlinedTextField(
                            value = customMinutes,
                            onValueChange = { newVal -> customMinutes = newVal.filter { it.isDigit() }.take(4) },
                            label = { Text(stringResource(R.string.sleep_timer_custom_hint)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            onClick = {
                                val minutes = customMinutes.toLongOrNull()
                                if (minutes != null && minutes > 0) {
                                    viewModel.startSleepTimer(minutes * 60 * 1000L)
                                }
                            },
                            enabled = customMinutes.isNotBlank() && (customMinutes.toLongOrNull() ?: 0) > 0,
                            shapes = ButtonDefaults.shapes()
                        ) {
                            Text(stringResource(R.string.btn_ok))
                        }
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.showSleepTimerDialog = false }) {
                Text(stringResource(R.string.btn_cancel))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.startSleepTimer(selectedMinutes.toLong() * 60 * 1000L)
                },
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(stringResource(R.string.btn_ok))
            }
        }
    )
}

@Composable
fun QueueContent(
    viewModel: PlayerViewModel,
    isQueueOpen: Boolean,
    onCloseQueue: () -> Unit,
    onOpenExpandedQueue: () -> Unit
) {
    val view = LocalView.current
    val listState = rememberLazyListState()

    val reorderableState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            viewModel.moveQueueItem(from.index, to.index)
            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
        }
    )

    LaunchedEffect(isQueueOpen, viewModel.currentTrack) {
        if (isQueueOpen) {
            val track = viewModel.currentTrack
            if (track != null && viewModel.queueState.isNotEmpty()) {
                val index = viewModel.queueState.indexOfFirst { it.id == track.id }
                if (index >= 0) listState.scrollToItem(kotlin.math.max(0, index - 2))
            }
        }
    }

    val windowSizeInfo = rememberWindowSizeInfo()
    val isPhoneLandscape = windowSizeInfo.isLandscape && !windowSizeInfo.isTablet

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isPhoneLandscape) Modifier.fillMaxHeight() else Modifier.fillMaxHeight(0.7f))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(top = 24.dp, bottom = 16.dp, start = 24.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.player_queue),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = {
                    onCloseQueue()
                    onOpenExpandedQueue()
                },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.Rounded.OpenInFull,
                    contentDescription = "Expand Queue",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            itemsIndexed(items = viewModel.queueState, key = { _, track -> track.id }) { index, track ->
                ReorderableItem(
                    state = reorderableState,
                    key = track.id
                ) { isDragging ->

                    val isCurrent = track.id == viewModel.currentTrack?.id
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                    val backgroundColor =
                        if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(72.dp)
                            .shadow(elevation)
                            .background(backgroundColor)
                            .clickable { viewModel.skipToQueueItem(index) }
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = track.fullResArtwork,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title ?: stringResource(R.string.generic_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = track.user?.username ?: stringResource(R.string.generic_artist),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                                if (track.user?.verified == true) {
                                    Spacer(Modifier.width(4.dp))
                                    Icon(
                                        Icons.Rounded.Verified,
                                        null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.desc_move),
                            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = 0.5f
                            ),
                            modifier = Modifier
                                .size(32.dp)
                                .draggableHandle(
                                    onDragStarted = {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    },
                                    onDragStopped = {
                                        view.performHapticFeedback(HapticFeedbackConstants.GESTURE_END)
                                    }
                                )
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerProgress(viewModel: PlayerViewModel, textColor: Color) {
    val view = LocalView.current
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    var lastValidDuration by remember { mutableFloatStateOf(180000f) }
    if (viewModel.duration > 1000) {
        lastValidDuration = viewModel.duration.toFloat()
    }
    val totalDuration = if (viewModel.duration > 1000) viewModel.duration.toFloat() else lastValidDuration

    val rawPosition = viewModel.currentPosition.toFloat()

    var currentTrackId by remember { mutableStateOf(viewModel.currentTrack?.id) }
    var isTransitioning by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.currentTrack?.id) {
        if (viewModel.currentTrack?.id != currentTrackId) {
            currentTrackId = viewModel.currentTrack?.id
            isTransitioning = true
            delay(1500)
            isTransitioning = false
        }
    }

    LaunchedEffect(rawPosition) {
        if (isTransitioning && rawPosition < 2000f) {
            isTransitioning = false
        }
    }

    val targetPos = when {
        isTransitioning -> 0f
        rawPosition > totalDuration -> 0f
        else -> rawPosition
    }

    val progressState = remember { Animatable(0f) }
    val sliderPosition = if (isDragging) dragPosition else progressState.value
    LaunchedEffect(targetPos, isDragging) {
        if (isDragging) {
            progressState.snapTo(dragPosition)
        } else {
            val currentVal = progressState.value
            val diff = targetPos - currentVal
            val absDiff = kotlin.math.abs(diff)

            if (targetPos < 1000f && currentVal > 2000f) {
                progressState.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
            } else if (absDiff > 2000f) {
                progressState.animateTo(targetPos, tween(300, easing = FastOutSlowInEasing))
            } else if (diff > 0) {
                progressState.animateTo(targetPos, tween(1000, easing = LinearEasing))
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition.coerceIn(0f, totalDuration),
            valueRange = 0f..totalDuration,
            onValueChange = {
                isDragging = true
                dragPosition = it
                viewModel.updateScrubPosition(it.toLong())
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            },
            onValueChangeFinished = {
                viewModel.seekTo(dragPosition.toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = textColor,
                activeTrackColor = textColor,
                inactiveTrackColor = textColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = makeTimeString(if (isDragging) dragPosition.toLong() else progressState.value.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
            Text(
                text = makeTimeString(totalDuration.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlayerControls(
    viewModel: PlayerViewModel,
    onEffectsClick: () -> Unit,
    onQueueClick: () -> Unit,
    animatedMainColor: Color = MaterialTheme.colorScheme.primary,
    contentColorOverride: Color
) {
    val isButtonLight = animatedMainColor.luminance() > 0.4f
    val playIconColor = if (isButtonLight) Color(0xFF1D1B20) else Color.White
    val sideButtonContainerColor = contentColorOverride.copy(alpha = 0.15f)
    val sideButtonContentColor = contentColorOverride

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            val backInteractionSource = remember { MutableInteractionSource() }
            val nextInteractionSource = remember { MutableInteractionSource() }
            val playPauseInteractionSource = remember { MutableInteractionSource() }

            val isPlayPausePressed by playPauseInteractionSource.collectIsPressedAsState()
            val isBackPressed by backInteractionSource.collectIsPressedAsState()
            val isNextPressed by nextInteractionSource.collectIsPressedAsState()

            val playPauseWeight by animateFloatAsState(
                targetValue = if (isPlayPausePressed) 1.9f
                else if (isBackPressed || isNextPressed) 1.1f
                else 1.3f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                label = "playPauseWeight"
            )

            val backButtonWeight by animateFloatAsState(
                targetValue = if (isBackPressed) 0.65f
                else if (isPlayPausePressed) 0.35f
                else 0.45f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                label = "backButtonWeight"
            )

            val nextButtonWeight by animateFloatAsState(
                targetValue = if (isNextPressed) 0.65f
                else if (isPlayPausePressed) 0.35f
                else 0.45f,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
                label = "nextButtonWeight"
            )

            Box(
                modifier = Modifier
                    .height(68.dp)
                    .weight(backButtonWeight)
                    .clip(RoundedCornerShape(50))
                    .background(sideButtonContainerColor)
                    .clickable(
                        interactionSource = backInteractionSource,
                        indication = ripple()
                    ) { viewModel.smartPrevious() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = sideButtonContentColor, modifier = Modifier.size(32.dp))
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .height(68.dp)
                    .weight(playPauseWeight)
                    .clip(RoundedCornerShape(50))
                    .background(animatedMainColor)
                    .clickable(
                        interactionSource = playPauseInteractionSource,
                        indication = ripple()
                    ) { viewModel.togglePlayPause() },
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = Pair(viewModel.isLoading, viewModel.isPlaying),
                    transitionSpec = {
                        val springSpec = spring<Float>(dampingRatio = 0.6f, stiffness = 1000f)
                        (scaleIn(initialScale = 0.8f, animationSpec = springSpec) + fadeIn(tween(100)))
                            .togetherWith(
                                scaleOut(
                                    targetScale = 0.8f,
                                    animationSpec = springSpec
                                ) + fadeOut(tween(100))
                            )
                            .using(
                                SizeTransform(
                                    clip = false,
                                    sizeAnimationSpec = { _, _ -> spring(dampingRatio = 0.6f, stiffness = 1000f) })
                            )
                    },
                    label = "playPauseLoading"
                ) { (isLoading, isPlaying) ->
                    if (isLoading) {
                        LoadingIndicator(color = playIconColor, modifier = Modifier.size(32.dp))
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = playIconColor,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(if (isPlaying) R.string.action_pause else R.string.action_play),
                                color = playIconColor,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .height(68.dp)
                    .weight(nextButtonWeight)
                    .clip(RoundedCornerShape(50))
                    .background(sideButtonContainerColor)
                    .clickable(
                        interactionSource = nextInteractionSource,
                        indication = ripple()
                    ) { viewModel.playNext() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.SkipNext, null, tint = sideButtonContentColor, modifier = Modifier.size(32.dp))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val leftStartShape = RoundedCornerShape(
                topStart = 50.dp, bottomStart = 50.dp,
                topEnd = 3.dp, bottomEnd = 3.dp
            )
            val leftEndShape = RoundedCornerShape(
                topStart = 3.dp, bottomStart = 3.dp,
                topEnd = 50.dp, bottomEnd = 50.dp
            )

            val pillContainerColor = contentColorOverride.copy(alpha = 0.12f)
            val pillContentColor = contentColorOverride

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledIconButton(
                    onClick = onEffectsClick,
                    shape = leftStartShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = pillContainerColor,
                        contentColor = pillContentColor
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.Default.Equalizer,
                        stringResource(R.string.player_effects),
                        modifier = Modifier.size(22.dp)
                    )
                }
                FilledIconButton(
                    onClick = { viewModel.toggleShuffle() },
                    shape = leftEndShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (viewModel.shuffleEnabled) animatedMainColor else pillContainerColor,
                        contentColor = if (viewModel.shuffleEnabled) playIconColor else pillContentColor
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(22.dp))
                }
            }

            val rightStartShape = RoundedCornerShape(
                topStart = 50.dp, bottomStart = 50.dp,
                topEnd = 3.dp, bottomEnd = 3.dp
            )
            val rightEndShape = RoundedCornerShape(
                topStart = 3.dp, bottomStart = 3.dp,
                topEnd = 50.dp, bottomEnd = 50.dp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val repeatActive = viewModel.repeatMode != RepeatMode.NONE
                FilledIconButton(
                    onClick = { viewModel.toggleRepeatMode() },
                    shape = rightStartShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (repeatActive) animatedMainColor else pillContainerColor,
                        contentColor = if (repeatActive) playIconColor else pillContentColor
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = when (viewModel.repeatMode) {
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
                FilledIconButton(
                    onClick = onQueueClick,
                    shape = rightEndShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = pillContainerColor,
                        contentColor = pillContentColor
                    ),
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        stringResource(R.string.player_queue),
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AudioControlDock(viewModel: PlayerViewModel) {
    val view = LocalView.current;
    val isPrecise = viewModel.isPreciseSpeedEnabled
    var showRainVolumeDialog by remember { mutableStateOf(false) }
    var showBassBoostDialog by remember { mutableStateOf(false) }
    var showEarrapeDialog by remember { mutableStateOf(false) }
    var showEarrapeWarning by remember { mutableStateOf(false) }
    var showEightDDialog by remember { mutableStateOf(false) }
    var showMuffledDialog by remember { mutableStateOf(false) }
    var showReverbDialog by remember { mutableStateOf(false) }
    var showNormalizationDialog by remember { mutableStateOf(false) }
    var showVintageMp3Dialog by remember { mutableStateOf(false) }
    var showVocalRemoverDialog by remember { mutableStateOf(false) }
    var showVocalBoostDialog by remember { mutableStateOf(false) }
    var showFlangerDialog by remember { mutableStateOf(false) }
    var showPartyNextDoorDialog by remember { mutableStateOf(false) }
    var showSuperWideDialog by remember { mutableStateOf(false) }
    var showVinylLoFiDialog by remember { mutableStateOf(false) }
    var showPhaserDialog by remember { mutableStateOf(false) }
    var showMegaphoneDialog by remember { mutableStateOf(false) }
    var showRobotVocoderDialog by remember { mutableStateOf(false) }
    var showChorusDialog by remember { mutableStateOf(false) }
    var showUnderwaterDialog by remember { mutableStateOf(false) }
    var showTranceGateDialog by remember { mutableStateOf(false) }
    var showPingPongDelayDialog by remember { mutableStateOf(false) }
    var showChiptuneDialog by remember { mutableStateOf(false) }
    var showShimmerReverbDialog by remember { mutableStateOf(false) }
    var showRotarySpeakerDialog by remember { mutableStateOf(false) }
    var showTapeSaturationDialog by remember { mutableStateOf(false) }
    var showSubOctaverDialog by remember { mutableStateOf(false) }
    var showEmptyMallDialog by remember { mutableStateOf(false) }
    var showGramophoneDialog by remember { mutableStateOf(false) }
    var showReverseEchoDialog by remember { mutableStateOf(false) }
    var showStadiumDialog by remember { mutableStateOf(false) }
    var showWalkmanDialog by remember { mutableStateOf(false) }
    var showAsmrVocalDialog by remember { mutableStateOf(false) }
    var showNightDriveDialog by remember { mutableStateOf(false) }
    var showStudioEditSheet by remember { mutableStateOf(false) }

    val allEffects = getAudioFxDefinitions(
        onOpenBassBoostDialog = { showBassBoostDialog = true },
        onOpenEarrapeDialog = { showEarrapeDialog = true },
        onOpenEightDDialog = { showEightDDialog = true },
        onOpenMuffledDialog = { showMuffledDialog = true },
        onOpenReverbDialog = { showReverbDialog = true },
        onOpenRainDialog = { showRainVolumeDialog = true },
        onOpenNormalizationDialog = { showNormalizationDialog = true },
        onOpenVintageMp3Dialog = { showVintageMp3Dialog = true },
        onOpenVocalRemoverDialog = { showVocalRemoverDialog = true },
        onOpenVocalBoostDialog = { showVocalBoostDialog = true },
        onOpenFlangerDialog = { showFlangerDialog = true },
        onOpenPartyNextDoorDialog = { showPartyNextDoorDialog = true },
        onOpenSuperWideDialog = { showSuperWideDialog = true },
        onOpenVinylLoFiDialog = { showVinylLoFiDialog = true },
        onOpenPhaserDialog = { showPhaserDialog = true },
        onOpenMegaphoneDialog = { showMegaphoneDialog = true },
        onOpenRobotVocoderDialog = { showRobotVocoderDialog = true },
        onOpenChorusDialog = { showChorusDialog = true },
        onOpenUnderwaterDialog = { showUnderwaterDialog = true },
        onOpenTranceGateDialog = { showTranceGateDialog = true },
        onOpenPingPongDelayDialog = { showPingPongDelayDialog = true },
        onOpenChiptuneDialog = { showChiptuneDialog = true },
        onOpenShimmerReverbDialog = { showShimmerReverbDialog = true },
        onOpenRotarySpeakerDialog = { showRotarySpeakerDialog = true },
        onOpenTapeSaturationDialog = { showTapeSaturationDialog = true },
        onOpenSubOctaverDialog = { showSubOctaverDialog = true },
        onOpenEmptyMallDialog = { showEmptyMallDialog = true },
        onOpenGramophoneDialog = { showGramophoneDialog = true },
        onOpenReverseEchoDialog = { showReverseEchoDialog = true },
        onOpenStadiumDialog = { showStadiumDialog = true },
        onOpenWalkmanDialog = { showWalkmanDialog = true },
        onOpenAsmrVocalDialog = { showAsmrVocalDialog = true },
        onOpenNightDriveDialog = { showNightDriveDialog = true },
        onShowEarrapeWarning = { showEarrapeWarning = true }
    )

    val pinnedTiles = remember(viewModel.pinnedAudioFx, allEffects) {
        viewModel.pinnedAudioFx.mapNotNull { id -> allEffects.find { it.id == id } }
    }
    val pages = remember(pinnedTiles) {
        if (pinnedTiles.isEmpty()) emptyList() else pinnedTiles.chunked(6)
    }
    val pagerState = rememberPagerState(pageCount = { max(1, pages.size) })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp) // extra padding at the bottom for safety
    ) {
        Text(
            stringResource(R.string.player_audio_settings),
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(bottom = 24.dp)
        )
        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Speed,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    ); Spacer(Modifier.width(12.dp)); Text(
                    text = "${viewModel.effectsState.speed}x",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                }
                val isPitchActive = viewModel.effectsState.isPitchEnabled;
                val pitchContainerColor by animateColorAsState(
                    targetValue = if (isPitchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                    label = "pitchContainer"
                );
                val pitchContentColor by animateColorAsState(
                    targetValue = if (isPitchActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    label = "pitchContent"
                )
                Surface(
                    onClick = { viewModel.togglePitchEnabled(!isPitchActive) },
                    shape = CircleShape,
                    color = pitchContainerColor,
                    border = if (isPitchActive) null else BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    contentColor = pitchContentColor
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedVisibility(visible = isPitchActive) {
                            Row {
                                Icon(
                                    Icons.Default.Check,
                                    null,
                                    modifier = Modifier.size(16.dp)
                                ); Spacer(Modifier.width(8.dp))
                            }
                        }; Text(
                        stringResource(R.string.player_pitch),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    }
                }
            }
            Spacer(Modifier.height(24.dp)); Slider(
            value = viewModel.effectsState.speed,
            onValueChange = {
                if (it != viewModel.effectsState.speed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); viewModel.setCustomSpeed(
                it
            )
            },
            valueRange = 0.5f..2.0f,
            steps = if (isPrecise) 29 else 14,
            modifier = Modifier.fillMaxWidth()
        )
        }
        Spacer(Modifier.height(28.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp, start = 4.dp, end = 4.dp)
        ) {
            Text(
                stringResource(R.string.player_special_effects),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(2.dp))
            Text(
                stringResource(R.string.audio_fx_long_press_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        }
        if (pages.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.edit_tiles_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            val rowHeightDp = 84.dp
            val rowSpacingDp = 12.dp

            val calculatePageHeight: (Int) -> Dp = { pageIdx ->
                val itemsCount = pages.getOrNull(pageIdx)?.size ?: 0
                if (itemsCount == 0) 0.dp else {
                    val rows = (itemsCount + 1) / 2
                    rowHeightDp * rows + rowSpacingDp * (rows - 1)
                }
            }

            val currentPage = pagerState.currentPage
            val offsetFraction = pagerState.currentPageOffsetFraction
            val targetPage = if (offsetFraction > 0f) {
                (currentPage + 1).coerceAtMost(pages.lastIndex)
            } else if (offsetFraction < 0f) {
                (currentPage - 1).coerceAtLeast(0)
            } else {
                currentPage
            }

            val currentHeight = calculatePageHeight(currentPage)
            val targetHeight = calculatePageHeight(targetPage)
            val fraction = abs(offsetFraction).coerceIn(0f, 1f)
            val pagerHeight = currentHeight + (targetHeight - currentHeight) * fraction

            HorizontalPager(
                state = pagerState,
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(pagerHeight)
            ) { pageIndex ->
                val pageItems = pages.getOrElse(pageIndex) { emptyList() }
                val pageOffset = abs((pagerState.currentPage - pageIndex) + pagerState.currentPageOffsetFraction)
                val pageAlpha = (1f - pageOffset * 0.35f).coerceIn(0f, 1f)
                val pageScale = (1f - pageOffset * 0.04f).coerceIn(0.95f, 1f)

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            alpha = pageAlpha
                            scaleX = pageScale
                            scaleY = pageScale
                        }
                ) {
                    pageItems.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            rowItems.forEach { fx ->
                                FxTile(
                                    label = stringResource(fx.titleRes),
                                    icon = fx.icon,
                                    isActive = fx.isActive(viewModel.effectsState),
                                    onClick = { fx.onToggle(viewModel) { showEarrapeWarning = true } },
                                    onLongClick = fx.onOpenDialog,
                                    modifier = if (rowItems.size == 1) Modifier.fillMaxWidth() else Modifier.weight(1f),
                                    activeColor = fx.activeColor(),
                                    activeContentColor = fx.activeContentColor()
                                )
                            }
                        }
                    }
                }
            }

            if (pages.size > 1) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(pages.size) { iteration ->
                        val isSelected = pagerState.currentPage == iteration
                        val indicatorWidth by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 6.dp,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "indicatorWidth"
                        )
                        val indicatorColor by animateColorAsState(
                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                            animationSpec = tween(200),
                            label = "indicatorColor"
                        )
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .height(6.dp)
                                .width(indicatorWidth)
                                .clip(CircleShape)
                                .background(indicatorColor)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = { showStudioEditSheet = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f)
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.DashboardCustomize,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.btn_explore_all_effects),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(26.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "${pinnedTiles.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
        if (showRainVolumeDialog) {
            AlertDialog(
                onDismissRequest = { showRainVolumeDialog = false },
                icon = { Icon(Icons.Rounded.WaterDrop, null) },
                title = { Text(stringResource(R.string.effect_ambient_sound)) },
                text = {
                    Column {
                        val currentType = viewModel.effectsState.ambientType
                        val volume = viewModel.effectsState.rainVolume

                        val rainLabel = stringResource(R.string.ambient_rain)
                        val fireLabel = stringResource(R.string.ambient_fireplace)
                        val oceanLabel = stringResource(R.string.ambient_ocean)
                        val cafeLabel = stringResource(R.string.ambient_cafe)

                        val ambientOptions = remember(rainLabel, fireLabel, oceanLabel, cafeLabel) {
                            listOf(
                                "rain" to rainLabel,
                                "fireplace" to fireLabel,
                                "ocean" to oceanLabel,
                                "cafe" to cafeLabel
                            )
                        }
                        val selectedOption = ambientOptions.firstOrNull { it.first == currentType } ?: ambientOptions.first()

                        ExpressiveConnectedButtonGroup(
                            options = ambientOptions,
                            selectedOption = selectedOption,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (type, _) ->
                                viewModel.setAmbientType(type)
                                if (!viewModel.effectsState.isRainEnabled) viewModel.toggleRain()
                            },
                            labelProvider = { (_, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        Text(
                            stringResource(R.string.label_ambient_volume, (volume * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = volume,
                            onValueChange = {
                                viewModel.setRainVolume(it)
                                if (!viewModel.effectsState.isRainEnabled) viewModel.toggleRain()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_ambient_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRainVolumeDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }
        if (showBassBoostDialog) {
            AlertDialog(
                onDismissRequest = { showBassBoostDialog = false },
                icon = { Icon(Icons.Rounded.Bolt, null) },
                title = { Text(stringResource(R.string.effect_bass_boost)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.label_intensity,
                                (viewModel.effectsState.bassBoostIntensity * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = viewModel.effectsState.bassBoostIntensity,
                            onValueChange = {
                                viewModel.setBassBoostIntensity(it)
                                if (!viewModel.effectsState.isBassBoostEnabled) viewModel.toggleBassBoost()
                            },
                            valueRange = 0f..5.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        val presets = remember {
                            listOf(
                                0.5f to "50%",
                                1.0f to "100%",
                                2.0f to "200%",
                                5.0f to "500%"
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - viewModel.effectsState.bassBoostIntensity) < 0.05f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (value, _) ->
                                viewModel.setBassBoostIntensity(value)
                                if (!viewModel.effectsState.isBassBoostEnabled) viewModel.toggleBassBoost()
                            },
                            labelProvider = { (_, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showBassBoostDialog = false
                    }) { Text(stringResource(R.string.btn_ok)) }
                }
            )
        }
        if (showEarrapeDialog) {
            AlertDialog(
                onDismissRequest = { showEarrapeDialog = false },
                icon = { Icon(Icons.AutoMirrored.Rounded.VolumeUp, null) },
                title = { Text(stringResource(R.string.btn_earrape)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.label_intensity,
                                (viewModel.effectsState.earrapeIntensity * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = viewModel.effectsState.earrapeIntensity,
                            onValueChange = {
                                viewModel.setEarrapeIntensity(it)
                                if (!viewModel.effectsState.isEarrapeEnabled) {
                                    if (!viewModel.hasSeenEarrapeWarning()) {
                                        showEarrapeWarning = true
                                    } else {
                                        viewModel.toggleEarrape()
                                    }
                                }
                            },
                            valueRange = 0f..5.0f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        val presets = remember {
                            listOf(
                                0.5f to "50%",
                                1.0f to "100%",
                                2.0f to "200%",
                                5.0f to "500%"
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - viewModel.effectsState.earrapeIntensity) < 0.05f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (value, _) ->
                                viewModel.setEarrapeIntensity(value)
                                if (!viewModel.effectsState.isEarrapeEnabled) {
                                    if (!viewModel.hasSeenEarrapeWarning()) {
                                        showEarrapeWarning = true
                                    } else {
                                        viewModel.toggleEarrape()
                                    }
                                }
                            },
                            labelProvider = { (_, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showEarrapeDialog = false
                    }) { Text(stringResource(R.string.btn_ok)) }
                }
            )
        }
        if (showEarrapeWarning) {
            var countdown by remember { mutableStateOf(5) }
            LaunchedEffect(Unit) {
                while (countdown > 0) {
                    delay(1000)
                    countdown--
                }
            }
            AlertDialog(
                onDismissRequest = { showEarrapeWarning = false },
                title = { Text(stringResource(R.string.warning_title)) },
                text = { Text(stringResource(R.string.earrape_warning)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.setHasSeenEarrapeWarning(true)
                            viewModel.toggleEarrape()
                            showEarrapeWarning = false
                        },
                        enabled = countdown == 0
                    ) {
                        Text(
                            if (countdown > 0) "${stringResource(R.string.btn_ok)} (${countdown}s)" else stringResource(
                                R.string.btn_ok
                            )
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showEarrapeWarning = false
                    }) { Text(stringResource(R.string.btn_cancel)) }
                }
            )
        }
        if (showEightDDialog) {
            AlertDialog(
                onDismissRequest = { showEightDDialog = false },
                icon = { Icon(Icons.Rounded.SurroundSound, null) },
                title = { Text(stringResource(R.string.effect_8d)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.label_speed_8d,
                                (viewModel.effectsState.eightDSpeed * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ); Spacer(Modifier.height(16.dp)); Slider(
                        value = viewModel.effectsState.eightDSpeed,
                        onValueChange = { viewModel.setEightDSpeed(it); if (!viewModel.effectsState.is8DEnabled) viewModel.toggle8D() },
                        valueRange = 0f..1f
                    )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showEightDDialog = false
                    }) { Text(stringResource(R.string.btn_ok)) }
                })
        }
        if (showMuffledDialog) {
            AlertDialog(
                onDismissRequest = { showMuffledDialog = false },
                icon = { Icon(Icons.Rounded.BlurOn, null) },
                title = { Text(stringResource(R.string.effect_muffled)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.label_cutoff,
                                (viewModel.effectsState.muffledIntensity * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ); Spacer(Modifier.height(16.dp)); Slider(
                        value = viewModel.effectsState.muffledIntensity,
                        onValueChange = { viewModel.setMuffledIntensity(it); if (!viewModel.effectsState.isMuffledEnabled) viewModel.toggleMuffled() },
                        valueRange = 0f..1f
                    )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showMuffledDialog = false
                    }) { Text(stringResource(R.string.btn_ok)) }
                })
        }
        if (showReverbDialog) {
            AlertDialog(
                onDismissRequest = { showReverbDialog = false },
                icon = { Icon(Icons.Rounded.GraphicEq, null) },
                title = { Text(stringResource(R.string.effect_reverb)) },
                text = {
                    Column {
                        Text(
                            stringResource(
                                R.string.label_intensity,
                                (viewModel.effectsState.reverbIntensity * 100).toInt()
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ); Spacer(Modifier.height(16.dp)); Slider(
                        value = viewModel.effectsState.reverbIntensity,
                        onValueChange = { viewModel.setReverbIntensity(it); if (!viewModel.effectsState.isReverbEnabled) viewModel.toggleReverb() },
                        valueRange = 0f..1f
                    )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        showReverbDialog = false
                    }) { Text(stringResource(R.string.btn_ok)) }
                })
        }
        if (showNormalizationDialog) {
            AlertDialog(
                onDismissRequest = { showNormalizationDialog = false },
                icon = { Icon(Icons.Rounded.VolumeDown, null) },
                title = { Text(stringResource(R.string.pref_norm_title)) },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.pref_norm_sub),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))
                        val normOptions = remember {
                            listOf(
                                NormalizationLevel.QUIET to R.string.pref_norm_level_quiet,
                                NormalizationLevel.NORMAL to R.string.pref_norm_level_normal,
                                NormalizationLevel.LOUD to R.string.pref_norm_level_loud
                            )
                        }
                        val selectedNormOption = normOptions.firstOrNull { it.first == viewModel.effectsState.normalizationLevel }
                        ExpressiveConnectedButtonGroup(
                            options = normOptions,
                            selectedOption = selectedNormOption,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (level, _) ->
                                viewModel.setNormalizationLevel(level)
                                if (!viewModel.effectsState.isNormalizationEnabled) {
                                    viewModel.toggleNormalization()
                                }
                            },
                            labelProvider = { (_, labelRes) ->
                                Text(
                                    text = stringResource(labelRes),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNormalizationDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showVintageMp3Dialog) {
            AlertDialog(
                onDismissRequest = { showVintageMp3Dialog = false },
                icon = { Icon(Icons.Rounded.Radio, null) },
                title = { Text(stringResource(R.string.effect_vintage_mp3)) },
                text = {
                    Column {
                        val compression = viewModel.effectsState.vintageMp3Compression
                        val percent = (compression * 100).toInt()
                        val bitrateDesc = when {
                            compression < 0.15f -> "128 kbps"
                            compression < 0.40f -> "64 kbps"
                            compression < 0.65f -> "32 kbps"
                            compression < 0.90f -> "16 kbps"
                            else -> "8 kbps"
                        }
                        Text(
                            stringResource(R.string.label_vintage_mp3_compression, percent, bitrateDesc),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = compression,
                            onValueChange = {
                                viewModel.setVintageMp3Compression(it)
                                if (!viewModel.effectsState.isVintageMp3Enabled) {
                                    viewModel.toggleVintageMp3()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        val presets = remember {
                            listOf(
                                0.25f to "64k",
                                0.50f to "32k",
                                0.75f to "16k",
                                1.00f to "8k"
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - compression) < 0.12f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (value, _) ->
                                viewModel.setVintageMp3Compression(value)
                                if (!viewModel.effectsState.isVintageMp3Enabled) {
                                    viewModel.toggleVintageMp3()
                                }
                            },
                            labelProvider = { (_, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.fx_vintage_mp3_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVintageMp3Dialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showVocalRemoverDialog) {
            AlertDialog(
                onDismissRequest = { showVocalRemoverDialog = false },
                icon = { Icon(Icons.Rounded.MicOff, null) },
                title = { Text(stringResource(R.string.effect_vocal_remover)) },
                text = {
                    Column {
                        val level = viewModel.effectsState.vocalRemoverLevel
                        val percent = (level * 100).toInt()
                        Text(
                            stringResource(R.string.label_vocal_remover_level, percent),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = level,
                            onValueChange = {
                                viewModel.setVocalRemoverLevel(it)
                                if (!viewModel.effectsState.isVocalRemoverEnabled) {
                                    viewModel.toggleVocalRemover()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        val presets = remember {
                            listOf(
                                0.50f to "50%",
                                0.80f to "80%",
                                1.00f to "100%"
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - level) < 0.05f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (value, _) ->
                                viewModel.setVocalRemoverLevel(value)
                                if (!viewModel.effectsState.isVocalRemoverEnabled) {
                                    viewModel.toggleVocalRemover()
                                }
                            },
                            labelProvider = { (_, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.fx_vocal_remover_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVocalRemoverDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showVocalBoostDialog) {
            AlertDialog(
                onDismissRequest = { showVocalBoostDialog = false },
                icon = { Icon(Icons.Rounded.RecordVoiceOver, null) },
                title = { Text(stringResource(R.string.effect_vocal_boost)) },
                text = {
                    Column {
                        val level = viewModel.effectsState.vocalBoostIntensity
                        val percent = (level * 100).toInt()
                        Text(
                            stringResource(R.string.label_vocal_boost_level, percent),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(16.dp))
                        Slider(
                            value = level,
                            onValueChange = {
                                viewModel.setVocalBoostIntensity(it)
                                if (!viewModel.effectsState.isVocalBoostEnabled) {
                                    viewModel.toggleVocalBoost()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        val presets = remember {
                            listOf(
                                0.50f to "50%",
                                0.75f to "75%",
                                1.00f to "100%"
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - level) < 0.05f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (value, _) ->
                                viewModel.setVocalBoostIntensity(value)
                                if (!viewModel.effectsState.isVocalBoostEnabled) {
                                    viewModel.toggleVocalBoost()
                                }
                            },
                            labelProvider = { (_, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.fx_vocal_boost_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVocalBoostDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showFlangerDialog) {
            AlertDialog(
                onDismissRequest = { showFlangerDialog = false },
                icon = { Icon(Icons.Rounded.Air, null) },
                title = { Text(stringResource(R.string.effect_flanger)) },
                text = {
                    Column {
                        val intensity = viewModel.effectsState.flangerIntensity
                        val speed = viewModel.effectsState.flangerSpeed

                        Text(
                            stringResource(R.string.label_flanger_intensity, (intensity * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = intensity,
                            onValueChange = {
                                viewModel.setFlangerIntensity(it)
                                if (!viewModel.effectsState.isFlangerEnabled) {
                                    viewModel.toggleFlanger()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.label_flanger_speed, (speed * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = speed,
                            onValueChange = {
                                viewModel.setFlangerSpeed(it)
                                if (!viewModel.effectsState.isFlangerEnabled) {
                                    viewModel.toggleFlanger()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))
                        val slowLabel = stringResource(R.string.preset_flanger_slow)
                        val classicLabel = stringResource(R.string.preset_flanger_classic)
                        val turbineLabel = stringResource(R.string.preset_flanger_turbine)
                        val presets = remember(slowLabel, classicLabel, turbineLabel) {
                            listOf(
                                Triple(0.60f, 0.25f, slowLabel),
                                Triple(0.75f, 0.50f, classicLabel),
                                Triple(0.95f, 0.85f, turbineLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - intensity) < 0.1f && kotlin.math.abs(it.second - speed) < 0.1f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (presetIntensity, presetSpeed, _) ->
                                viewModel.setFlangerIntensity(presetIntensity)
                                viewModel.setFlangerSpeed(presetSpeed)
                                if (!viewModel.effectsState.isFlangerEnabled) {
                                    viewModel.toggleFlanger()
                                }
                            },
                            labelProvider = { (_, _, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.fx_flanger_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showFlangerDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showPartyNextDoorDialog) {
            AlertDialog(
                onDismissRequest = { showPartyNextDoorDialog = false },
                icon = { Icon(Icons.Rounded.MeetingRoom, null) },
                title = { Text(stringResource(R.string.effect_party_next_door)) },
                text = {
                    Column {
                        val isolation = viewModel.effectsState.partyNextDoorIsolation
                        val reverb = viewModel.effectsState.partyNextDoorReverb
                        val rumble = viewModel.effectsState.partyNextDoorBassRumble

                        // 1. Wall Isolation Slider
                        Text(
                            stringResource(R.string.label_pnd_isolation, (isolation * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = isolation,
                            onValueChange = {
                                viewModel.setPartyNextDoorIsolation(it)
                                if (!viewModel.effectsState.isPartyNextDoorEnabled) {
                                    viewModel.togglePartyNextDoor()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        // 2. Room Reverb Slider
                        Text(
                            stringResource(R.string.label_pnd_reverb, (reverb * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = reverb,
                            onValueChange = {
                                viewModel.setPartyNextDoorReverb(it)
                                if (!viewModel.effectsState.isPartyNextDoorEnabled) {
                                    viewModel.togglePartyNextDoor()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        // 3. Bass Rumble Slider
                        Text(
                            stringResource(R.string.label_pnd_rumble, (rumble * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = rumble,
                            onValueChange = {
                                viewModel.setPartyNextDoorBassRumble(it)
                                if (!viewModel.effectsState.isPartyNextDoorEnabled) {
                                    viewModel.togglePartyNextDoor()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val bathroomLabel = stringResource(R.string.preset_pnd_bathroom)
                        val hallwayLabel = stringResource(R.string.preset_pnd_hallway)
                        val nextDoorLabel = stringResource(R.string.preset_pnd_next_door)
                        val presets = remember(bathroomLabel, hallwayLabel, nextDoorLabel) {
                            listOf(
                                Triple(0.60f, 0.70f, bathroomLabel),
                                Triple(0.30f, 0.35f, hallwayLabel),
                                Triple(0.90f, 0.45f, nextDoorLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - isolation) < 0.1f && kotlin.math.abs(it.second - reverb) < 0.1f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (presetIso, presetRev, _) ->
                                val targetRumble = when {
                                    presetIso > 0.8f -> 0.90f
                                    presetIso < 0.4f -> 0.50f
                                    else -> 0.70f
                                }
                                viewModel.setPartyNextDoorIsolation(presetIso)
                                viewModel.setPartyNextDoorReverb(presetRev)
                                viewModel.setPartyNextDoorBassRumble(targetRumble)
                                if (!viewModel.effectsState.isPartyNextDoorEnabled) {
                                    viewModel.togglePartyNextDoor()
                                }
                            },
                            labelProvider = { (_, _, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_party_next_door_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPartyNextDoorDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showSuperWideDialog) {
            AlertDialog(
                onDismissRequest = { showSuperWideDialog = false },
                icon = { Icon(Icons.Rounded.SurroundSound, null) },
                title = { Text(stringResource(R.string.effect_super_wide)) },
                text = {
                    Column {
                        val width = viewModel.effectsState.superWideWidth
                        val depth = viewModel.effectsState.superWideDepth

                        // 1. Stereo Width Slider
                        Text(
                            stringResource(R.string.label_sw_width, (width * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = width,
                            onValueChange = {
                                viewModel.setSuperWideWidth(it)
                                if (!viewModel.effectsState.isSuperWideEnabled) {
                                    viewModel.toggleSuperWide()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        // 2. Spatial Depth Slider
                        Text(
                            stringResource(R.string.label_sw_depth, (depth * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = depth,
                            onValueChange = {
                                viewModel.setSuperWideDepth(it)
                                if (!viewModel.effectsState.isSuperWideEnabled) {
                                    viewModel.toggleSuperWide()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val naturalLabel = stringResource(R.string.preset_sw_natural)
                        val cinematicLabel = stringResource(R.string.preset_sw_cinematic)
                        val holographicLabel = stringResource(R.string.preset_sw_holographic)
                        val presets = remember(naturalLabel, cinematicLabel, holographicLabel) {
                            listOf(
                                Triple(0.45f, 0.35f, naturalLabel),
                                Triple(0.70f, 0.60f, cinematicLabel),
                                Triple(1.00f, 0.85f, holographicLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - width) < 0.08f && kotlin.math.abs(it.second - depth) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (presetWidth, presetDepth, _) ->
                                viewModel.setSuperWideWidth(presetWidth)
                                viewModel.setSuperWideDepth(presetDepth)
                                if (!viewModel.effectsState.isSuperWideEnabled) {
                                    viewModel.toggleSuperWide()
                                }
                            },
                            labelProvider = { (_, _, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_super_wide_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSuperWideDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showVinylLoFiDialog) {
            AlertDialog(
                onDismissRequest = { showVinylLoFiDialog = false },
                icon = { Icon(Icons.Rounded.Album, null) },
                title = { Text(stringResource(R.string.effect_vinyl_lofi)) },
                text = {
                    Column {
                        val crackles = viewModel.effectsState.vinylCrackles
                        val flutter = viewModel.effectsState.vinylFlutter

                        // 1. Crackles & Dust Slider
                        Text(
                            stringResource(R.string.label_vinyl_crackles, (crackles * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = crackles,
                            onValueChange = {
                                viewModel.setVinylCrackles(it)
                                if (!viewModel.effectsState.isVinylLoFiEnabled) {
                                    viewModel.toggleVinylLoFi()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        // 2. Tape Flutter Slider
                        Text(
                            stringResource(R.string.label_vinyl_flutter, (flutter * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = flutter,
                            onValueChange = {
                                viewModel.setVinylFlutter(it)
                                if (!viewModel.effectsState.isVinylLoFiEnabled) {
                                    viewModel.toggleVinylLoFi()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val chillLabel = stringResource(R.string.preset_vinyl_chill)
                        val vintageLabel = stringResource(R.string.preset_vinyl_vintage)
                        val cassetteLabel = stringResource(R.string.preset_vinyl_cassette)
                        val presets = remember(chillLabel, vintageLabel, cassetteLabel) {
                            listOf(
                                Triple(0.40f, 0.35f, chillLabel),
                                Triple(0.75f, 0.45f, vintageLabel),
                                Triple(0.35f, 0.80f, cassetteLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - crackles) < 0.08f && kotlin.math.abs(it.second - flutter) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (presetCrackles, presetFlutter, _) ->
                                viewModel.setVinylCrackles(presetCrackles)
                                viewModel.setVinylFlutter(presetFlutter)
                                if (!viewModel.effectsState.isVinylLoFiEnabled) {
                                    viewModel.toggleVinylLoFi()
                                }
                            },
                            labelProvider = { (_, _, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_vinyl_lofi_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showVinylLoFiDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showPhaserDialog) {
            AlertDialog(
                onDismissRequest = { showPhaserDialog = false },
                icon = { Icon(Icons.Rounded.Waves, null) },
                title = { Text(stringResource(R.string.effect_phaser)) },
                text = {
                    Column {
                        val speed = viewModel.effectsState.phaserSpeed
                        val feedback = viewModel.effectsState.phaserFeedback

                        Text(
                            stringResource(R.string.label_phaser_speed, (speed * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = speed,
                            onValueChange = {
                                viewModel.setPhaserSpeed(it)
                                if (!viewModel.effectsState.isPhaserEnabled) {
                                    viewModel.togglePhaser()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        // 2. Liquid Resonance Feedback Slider
                        Text(
                            stringResource(R.string.label_phaser_feedback, (feedback * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = feedback,
                            onValueChange = {
                                viewModel.setPhaserFeedback(it)
                                if (!viewModel.effectsState.isPhaserEnabled) {
                                    viewModel.togglePhaser()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val cosmicLabel = stringResource(R.string.preset_phaser_cosmic)
                        val liquidLabel = stringResource(R.string.preset_phaser_liquid)
                        val daftLabel = stringResource(R.string.preset_phaser_daft)
                        val presets = remember(cosmicLabel, liquidLabel, daftLabel) {
                            listOf(
                                Triple(0.25f, 0.60f, cosmicLabel),
                                Triple(0.50f, 0.75f, liquidLabel),
                                Triple(0.80f, 0.85f, daftLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - speed) < 0.08f && kotlin.math.abs(it.second - feedback) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (presetSpeed, presetFeedback, _) ->
                                viewModel.setPhaserSpeed(presetSpeed)
                                viewModel.setPhaserFeedback(presetFeedback)
                                if (!viewModel.effectsState.isPhaserEnabled) {
                                    viewModel.togglePhaser()
                                }
                            },
                            labelProvider = { (_, _, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_phaser_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPhaserDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showMegaphoneDialog) {
            AlertDialog(
                onDismissRequest = { showMegaphoneDialog = false },
                icon = { Icon(Icons.Rounded.Campaign, null) },
                title = { Text(stringResource(R.string.effect_megaphone)) },
                text = {
                    Column {
                        val tone = viewModel.effectsState.megaphoneTone
                        val drive = viewModel.effectsState.megaphoneDrive

                        // 1. Tone / Horn Resonance Slider
                        Text(
                            stringResource(R.string.label_megaphone_tone, (tone * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = tone,
                            onValueChange = {
                                viewModel.setMegaphoneTone(it)
                                if (!viewModel.effectsState.isMegaphoneEnabled) {
                                    viewModel.toggleMegaphone()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        // 2. Speaker Overdrive Slider
                        Text(
                            stringResource(R.string.label_megaphone_drive, (drive * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = drive,
                            onValueChange = {
                                viewModel.setMegaphoneDrive(it)
                                if (!viewModel.effectsState.isMegaphoneEnabled) {
                                    viewModel.toggleMegaphone()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val hornLabel = stringResource(R.string.preset_megaphone_horn)
                        val radioLabel = stringResource(R.string.preset_megaphone_radio)
                        val walkieLabel = stringResource(R.string.preset_megaphone_walkie)
                        val presets = remember(hornLabel, radioLabel, walkieLabel) {
                            listOf(
                                Triple(1.00f, 0.85f, hornLabel),
                                Triple(0.05f, 0.20f, radioLabel),
                                Triple(0.60f, 0.60f, walkieLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - tone) < 0.08f && kotlin.math.abs(it.second - drive) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (presetTone, presetDrive, _) ->
                                viewModel.setMegaphoneTone(presetTone)
                                viewModel.setMegaphoneDrive(presetDrive)
                                if (!viewModel.effectsState.isMegaphoneEnabled) {
                                    viewModel.toggleMegaphone()
                                }
                            },
                            labelProvider = { (_, _, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_megaphone_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showMegaphoneDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showRobotVocoderDialog) {
            AlertDialog(
                onDismissRequest = { showRobotVocoderDialog = false },
                icon = { Icon(Icons.Rounded.SmartToy, null) },
                title = { Text(stringResource(R.string.effect_robot_vocoder)) },
                text = {
                    Column {
                        val frequency = viewModel.effectsState.robotFrequency
                        val mix = viewModel.effectsState.robotMix

                        // 1. Carrier Frequency Slider
                        Text(
                            stringResource(R.string.label_robot_frequency, (frequency * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = frequency,
                            onValueChange = {
                                viewModel.setRobotFrequency(it)
                                if (!viewModel.effectsState.isRobotVocoderEnabled) {
                                    viewModel.toggleRobotVocoder()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        // 2. Robot Mix Intensity Slider
                        Text(
                            stringResource(R.string.label_robot_mix, (mix * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = mix,
                            onValueChange = {
                                viewModel.setRobotMix(it)
                                if (!viewModel.effectsState.isRobotVocoderEnabled) {
                                    viewModel.toggleRobotVocoder()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val cyborgLabel = stringResource(R.string.preset_robot_cyborg)
                        val daftLabel = stringResource(R.string.preset_robot_daft)
                        val alienLabel = stringResource(R.string.preset_robot_alien)
                        val presets = remember(cyborgLabel, daftLabel, alienLabel) {
                            listOf(
                                Triple(0.15f, 0.80f, cyborgLabel),
                                Triple(0.38f, 0.75f, daftLabel),
                                Triple(0.82f, 0.90f, alienLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - frequency) < 0.08f && kotlin.math.abs(it.second - mix) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (presetFreq, presetMix, _) ->
                                viewModel.setRobotFrequency(presetFreq)
                                viewModel.setRobotMix(presetMix)
                                if (!viewModel.effectsState.isRobotVocoderEnabled) {
                                    viewModel.toggleRobotVocoder()
                                }
                            },
                            labelProvider = { (_, _, label) ->
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_robot_vocoder_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRobotVocoderDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showChorusDialog) {
            AlertDialog(
                onDismissRequest = { showChorusDialog = false },
                icon = { Icon(Icons.Rounded.Grain, null) },
                title = { Text(stringResource(R.string.effect_chorus)) },
                text = {
                    Column {
                        val rate = viewModel.effectsState.chorusRate
                        val depth = viewModel.effectsState.chorusDepth

                        Text(
                            stringResource(R.string.label_chorus_rate, (rate * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = rate,
                            onValueChange = {
                                viewModel.setChorusRate(it)
                                if (!viewModel.effectsState.isChorusEnabled) viewModel.toggleChorus()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_chorus_depth, (depth * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = depth,
                            onValueChange = {
                                viewModel.setChorusDepth(it)
                                if (!viewModel.effectsState.isChorusEnabled) viewModel.toggleChorus()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val junoLabel = stringResource(R.string.preset_chorus_juno)
                        val dreamLabel = stringResource(R.string.preset_chorus_dream)
                        val shimmerLabel = stringResource(R.string.preset_chorus_shimmer)
                        val presets = remember(junoLabel, dreamLabel, shimmerLabel) {
                            listOf(
                                Triple(0.30f, 0.70f, junoLabel),
                                Triple(0.15f, 0.90f, dreamLabel),
                                Triple(0.75f, 0.45f, shimmerLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - rate) < 0.08f && kotlin.math.abs(it.second - depth) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pRate, pDepth, _) ->
                                viewModel.setChorusRate(pRate)
                                viewModel.setChorusDepth(pDepth)
                                if (!viewModel.effectsState.isChorusEnabled) viewModel.toggleChorus()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_chorus_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChorusDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showUnderwaterDialog) {
            AlertDialog(
                onDismissRequest = { showUnderwaterDialog = false },
                icon = { Icon(Icons.Rounded.Waves, null) },
                title = { Text(stringResource(R.string.effect_underwater)) },
                text = {
                    Column {
                        val depth = viewModel.effectsState.underwaterDepth
                        val bubbles = viewModel.effectsState.underwaterBubbles

                        Text(
                            stringResource(R.string.label_underwater_depth, (depth * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = depth,
                            onValueChange = {
                                viewModel.setUnderwaterDepth(it)
                                if (!viewModel.effectsState.isUnderwaterEnabled) viewModel.toggleUnderwater()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_underwater_bubbles, (bubbles * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = bubbles,
                            onValueChange = {
                                viewModel.setUnderwaterBubbles(it)
                                if (!viewModel.effectsState.isUnderwaterEnabled) viewModel.toggleUnderwater()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val submergedLabel = stringResource(R.string.preset_underwater_submerged)
                        val abyssLabel = stringResource(R.string.preset_underwater_abyss)
                        val scubaLabel = stringResource(R.string.preset_underwater_scuba)
                        val presets = remember(submergedLabel, abyssLabel, scubaLabel) {
                            listOf(
                                Triple(0.45f, 0.30f, submergedLabel),
                                Triple(0.85f, 0.60f, abyssLabel),
                                Triple(0.50f, 0.90f, scubaLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - depth) < 0.08f && kotlin.math.abs(it.second - bubbles) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pDepth, pBubbles, _) ->
                                viewModel.setUnderwaterDepth(pDepth)
                                viewModel.setUnderwaterBubbles(pBubbles)
                                if (!viewModel.effectsState.isUnderwaterEnabled) viewModel.toggleUnderwater()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_underwater_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showUnderwaterDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showTranceGateDialog) {
            AlertDialog(
                onDismissRequest = { showTranceGateDialog = false },
                icon = { Icon(Icons.Rounded.ElectricBolt, null) },
                title = { Text(stringResource(R.string.effect_trance_gate)) },
                text = {
                    Column {
                        val speed = viewModel.effectsState.tranceGateSpeed
                        val pattern = viewModel.effectsState.tranceGatePattern
                        val mix = viewModel.effectsState.tranceGateMix

                        Text(
                            stringResource(R.string.label_trance_gate_speed, (speed * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = speed,
                            onValueChange = {
                                viewModel.setTranceGateSpeed(it)
                                if (!viewModel.effectsState.isTranceGateEnabled) viewModel.toggleTranceGate()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            stringResource(R.string.label_trance_gate_pattern, (pattern * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = pattern,
                            onValueChange = {
                                viewModel.setTranceGatePattern(it)
                                if (!viewModel.effectsState.isTranceGateEnabled) viewModel.toggleTranceGate()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            stringResource(R.string.label_trance_gate_mix, (mix * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = mix,
                            onValueChange = {
                                viewModel.setTranceGateMix(it)
                                if (!viewModel.effectsState.isTranceGateEnabled) viewModel.toggleTranceGate()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val t16Label = stringResource(R.string.preset_trance_gate_16)
                        val dropLabel = stringResource(R.string.preset_trance_gate_drop)
                        val tremoloLabel = stringResource(R.string.preset_trance_gate_tremolo)
                        val presets = remember(t16Label, dropLabel, tremoloLabel) {
                            listOf(
                                Triple(0.65f, 0.90f, t16Label),
                                Triple(0.40f, 1.00f, dropLabel),
                                Triple(0.30f, 0.05f, tremoloLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - speed) < 0.08f && kotlin.math.abs(it.second - pattern) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pSpeed, pPattern, _) ->
                                viewModel.setTranceGateSpeed(pSpeed)
                                viewModel.setTranceGatePattern(pPattern)
                                viewModel.setTranceGateMix(0.90f)
                                if (!viewModel.effectsState.isTranceGateEnabled) viewModel.toggleTranceGate()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_trance_gate_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTranceGateDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showPingPongDelayDialog) {
            AlertDialog(
                onDismissRequest = { showPingPongDelayDialog = false },
                icon = { Icon(Icons.Rounded.SyncAlt, null) },
                title = { Text(stringResource(R.string.effect_ping_pong)) },
                text = {
                    Column {
                        val delayTime = viewModel.effectsState.pingPongDelayTime
                        val feedback = viewModel.effectsState.pingPongFeedback

                        Text(
                            stringResource(R.string.label_ping_pong_time, (delayTime * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = delayTime,
                            onValueChange = {
                                viewModel.setPingPongDelayTime(it)
                                if (!viewModel.effectsState.isPingPongDelayEnabled) viewModel.togglePingPongDelay()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_ping_pong_feedback, (feedback * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = feedback,
                            onValueChange = {
                                viewModel.setPingPongFeedback(it)
                                if (!viewModel.effectsState.isPingPongDelayEnabled) viewModel.togglePingPongDelay()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val dubLabel = stringResource(R.string.preset_ping_pong_dub)
                        val bounceLabel = stringResource(R.string.preset_ping_pong_bounce)
                        val slapLabel = stringResource(R.string.preset_ping_pong_slap)
                        val presets = remember(dubLabel, bounceLabel, slapLabel) {
                            listOf(
                                Triple(0.45f, 0.65f, dubLabel),
                                Triple(0.70f, 0.72f, bounceLabel),
                                Triple(0.10f, 0.35f, slapLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - delayTime) < 0.08f && kotlin.math.abs(it.second - feedback) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pTime, pFb, _) ->
                                viewModel.setPingPongDelayTime(pTime)
                                viewModel.setPingPongFeedback(pFb)
                                if (!viewModel.effectsState.isPingPongDelayEnabled) viewModel.togglePingPongDelay()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_ping_pong_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showPingPongDelayDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showChiptuneDialog) {
            AlertDialog(
                onDismissRequest = { showChiptuneDialog = false },
                icon = { Icon(Icons.Rounded.Gamepad, null) },
                title = { Text(stringResource(R.string.effect_chiptune)) },
                text = {
                    Column {
                        val bits = viewModel.effectsState.chiptuneBits
                        val sr = viewModel.effectsState.chiptuneSampleRate

                        Text(
                            stringResource(R.string.label_chiptune_bits, (bits * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = bits,
                            onValueChange = {
                                viewModel.setChiptuneBits(it)
                                if (!viewModel.effectsState.isChiptuneEnabled) viewModel.toggleChiptune()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_chiptune_sr, (sr * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = sr,
                            onValueChange = {
                                viewModel.setChiptuneSampleRate(it)
                                if (!viewModel.effectsState.isChiptuneEnabled) viewModel.toggleChiptune()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val gameboyLabel = stringResource(R.string.preset_chiptune_gameboy)
                        val nesLabel = stringResource(R.string.preset_chiptune_nes)
                        val arcadeLabel = stringResource(R.string.preset_chiptune_arcade)
                        val presets = remember(gameboyLabel, nesLabel, arcadeLabel) {
                            listOf(
                                Triple(0.45f, 0.40f, gameboyLabel),
                                Triple(0.70f, 0.65f, nesLabel),
                                Triple(0.90f, 0.85f, arcadeLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - bits) < 0.08f && kotlin.math.abs(it.second - sr) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pBits, pSr, _) ->
                                viewModel.setChiptuneBits(pBits)
                                viewModel.setChiptuneSampleRate(pSr)
                                if (!viewModel.effectsState.isChiptuneEnabled) viewModel.toggleChiptune()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_chiptune_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showChiptuneDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showShimmerReverbDialog) {
            AlertDialog(
                onDismissRequest = { showShimmerReverbDialog = false },
                icon = { Icon(Icons.Rounded.Flare, null) },
                title = { Text(stringResource(R.string.effect_shimmer_reverb)) },
                text = {
                    Column {
                        val size = viewModel.effectsState.shimmerSize
                        val mix = viewModel.effectsState.shimmerMix

                        Text(
                            stringResource(R.string.label_shimmer_size, (size * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = size,
                            onValueChange = {
                                viewModel.setShimmerSize(it)
                                if (!viewModel.effectsState.isShimmerReverbEnabled) viewModel.toggleShimmerReverb()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_shimmer_mix, (mix * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = mix,
                            onValueChange = {
                                viewModel.setShimmerMix(it)
                                if (!viewModel.effectsState.isShimmerReverbEnabled) viewModel.toggleShimmerReverb()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val cloudLabel = stringResource(R.string.preset_shimmer_cloud)
                        val angelLabel = stringResource(R.string.preset_shimmer_angel)
                        val hallLabel = stringResource(R.string.preset_shimmer_hall)
                        val presets = remember(cloudLabel, angelLabel, hallLabel) {
                            listOf(
                                Triple(0.65f, 0.60f, cloudLabel),
                                Triple(0.85f, 0.85f, angelLabel),
                                Triple(0.50f, 0.25f, hallLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - size) < 0.08f && kotlin.math.abs(it.second - mix) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pSize, pMix, _) ->
                                viewModel.setShimmerSize(pSize)
                                viewModel.setShimmerMix(pMix)
                                if (!viewModel.effectsState.isShimmerReverbEnabled) viewModel.toggleShimmerReverb()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_shimmer_reverb_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showShimmerReverbDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showRotarySpeakerDialog) {
            AlertDialog(
                onDismissRequest = { showRotarySpeakerDialog = false },
                icon = { Icon(Icons.Rounded.RotateRight, null) },
                title = { Text(stringResource(R.string.effect_rotary_speaker)) },
                text = {
                    Column {
                        val speed = viewModel.effectsState.rotarySpeed
                        val depth = viewModel.effectsState.rotaryDepth

                        Text(
                            stringResource(R.string.label_rotary_speed, (speed * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = speed,
                            onValueChange = {
                                viewModel.setRotarySpeed(it)
                                if (!viewModel.effectsState.isRotarySpeakerEnabled) viewModel.toggleRotarySpeaker()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_rotary_depth, (depth * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = depth,
                            onValueChange = {
                                viewModel.setRotaryDepth(it)
                                if (!viewModel.effectsState.isRotarySpeakerEnabled) viewModel.toggleRotarySpeaker()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val choraleLabel = stringResource(R.string.preset_rotary_chorale)
                        val tremoloLabel = stringResource(R.string.preset_rotary_tremolo)
                        val psychLabel = stringResource(R.string.preset_rotary_psychedelic)
                        val presets = remember(choraleLabel, tremoloLabel, psychLabel) {
                            listOf(
                                Triple(0.15f, 0.65f, choraleLabel),
                                Triple(0.75f, 0.80f, tremoloLabel),
                                Triple(0.50f, 0.95f, psychLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - speed) < 0.08f && kotlin.math.abs(it.second - depth) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pSpeed, pDepth, _) ->
                                viewModel.setRotarySpeed(pSpeed)
                                viewModel.setRotaryDepth(pDepth)
                                if (!viewModel.effectsState.isRotarySpeakerEnabled) viewModel.toggleRotarySpeaker()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_rotary_speaker_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showRotarySpeakerDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showTapeSaturationDialog) {
            AlertDialog(
                onDismissRequest = { showTapeSaturationDialog = false },
                icon = { Icon(Icons.Rounded.Whatshot, null) },
                title = { Text(stringResource(R.string.effect_tape_saturation)) },
                text = {
                    Column {
                        val warmth = viewModel.effectsState.tapeWarmth
                        val exciter = viewModel.effectsState.tapeExciter

                        Text(
                            stringResource(R.string.label_tape_warmth, (warmth * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = warmth,
                            onValueChange = {
                                viewModel.setTapeWarmth(it)
                                if (!viewModel.effectsState.isTapeSaturationEnabled) viewModel.toggleTapeSaturation()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_tape_exciter, (exciter * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = exciter,
                            onValueChange = {
                                viewModel.setTapeExciter(it)
                                if (!viewModel.effectsState.isTapeSaturationEnabled) viewModel.toggleTapeSaturation()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val warmLabel = stringResource(R.string.preset_tape_warm)
                        val studerLabel = stringResource(R.string.preset_tape_studer)
                        val exciterLabel = stringResource(R.string.preset_tape_exciter)
                        val presets = remember(warmLabel, studerLabel, exciterLabel) {
                            listOf(
                                Triple(0.60f, 0.40f, warmLabel),
                                Triple(0.80f, 0.65f, studerLabel),
                                Triple(0.40f, 0.90f, exciterLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - warmth) < 0.08f && kotlin.math.abs(it.second - exciter) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pWarmth, pExciter, _) ->
                                viewModel.setTapeWarmth(pWarmth)
                                viewModel.setTapeExciter(pExciter)
                                if (!viewModel.effectsState.isTapeSaturationEnabled) viewModel.toggleTapeSaturation()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_tape_saturation_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showTapeSaturationDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showSubOctaverDialog) {
            AlertDialog(
                onDismissRequest = { showSubOctaverDialog = false },
                icon = { Icon(Icons.Rounded.Speaker, null) },
                title = { Text(stringResource(R.string.effect_sub_octaver)) },
                text = {
                    Column {
                        val level = viewModel.effectsState.subOctaverLevel
                        val cutoff = viewModel.effectsState.subOctaverCutoff

                        Text(
                            stringResource(R.string.label_sub_level, (level * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = level,
                            onValueChange = {
                                viewModel.setSubOctaverLevel(it)
                                if (!viewModel.effectsState.isSubOctaverEnabled) viewModel.toggleSubOctaver()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_sub_cutoff, (cutoff * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = cutoff,
                            onValueChange = {
                                viewModel.setSubOctaverCutoff(it)
                                if (!viewModel.effectsState.isSubOctaverEnabled) viewModel.toggleSubOctaver()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val b808Label = stringResource(R.string.preset_sub_808)
                        val deepLabel = stringResource(R.string.preset_sub_deep)
                        val punchLabel = stringResource(R.string.preset_sub_punch)
                        val presets = remember(b808Label, deepLabel, punchLabel) {
                            listOf(
                                Triple(0.80f, 0.65f, b808Label),
                                Triple(0.65f, 0.20f, deepLabel),
                                Triple(0.90f, 0.85f, punchLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - level) < 0.08f && kotlin.math.abs(it.second - cutoff) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pLevel, pCutoff, _) ->
                                viewModel.setSubOctaverLevel(pLevel)
                                viewModel.setSubOctaverCutoff(pCutoff)
                                if (!viewModel.effectsState.isSubOctaverEnabled) viewModel.toggleSubOctaver()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_sub_octaver_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showSubOctaverDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showEmptyMallDialog) {
            AlertDialog(
                onDismissRequest = { showEmptyMallDialog = false },
                icon = { Icon(Icons.Rounded.Storefront, null) },
                title = { Text(stringResource(R.string.effect_empty_mall)) },
                text = {
                    Column {
                        val distance = viewModel.effectsState.emptyMallDistance
                        val reverb = viewModel.effectsState.emptyMallReverb

                        Text(
                            stringResource(R.string.label_empty_mall_distance, (distance * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = distance,
                            onValueChange = {
                                viewModel.setEmptyMallDistance(it)
                                if (!viewModel.effectsState.isEmptyMallEnabled) viewModel.toggleEmptyMall()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_empty_mall_reverb, (reverb * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = reverb,
                            onValueChange = {
                                viewModel.setEmptyMallReverb(it)
                                if (!viewModel.effectsState.isEmptyMallEnabled) viewModel.toggleEmptyMall()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val m1995Label = stringResource(R.string.preset_mall_1995)
                        val distantLabel = stringResource(R.string.preset_mall_distant)
                        val liminalLabel = stringResource(R.string.preset_mall_liminal)
                        val presets = remember(m1995Label, distantLabel, liminalLabel) {
                            listOf(
                                Triple(0.65f, 0.60f, m1995Label),
                                Triple(0.90f, 0.75f, distantLabel),
                                Triple(0.45f, 0.85f, liminalLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - distance) < 0.08f && kotlin.math.abs(it.second - reverb) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pDist, pRev, _) ->
                                viewModel.setEmptyMallDistance(pDist)
                                viewModel.setEmptyMallReverb(pRev)
                                if (!viewModel.effectsState.isEmptyMallEnabled) viewModel.toggleEmptyMall()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_empty_mall_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showEmptyMallDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showGramophoneDialog) {
            AlertDialog(
                onDismissRequest = { showGramophoneDialog = false },
                icon = { Icon(Icons.Rounded.History, null) },
                title = { Text(stringResource(R.string.effect_gramophone)) },
                text = {
                    Column {
                        val age = viewModel.effectsState.gramophoneAge
                        val horn = viewModel.effectsState.gramophoneHorn

                        Text(
                            stringResource(R.string.label_gramophone_age, (age * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = age,
                            onValueChange = {
                                viewModel.setGramophoneAge(it)
                                if (!viewModel.effectsState.isGramophoneEnabled) viewModel.toggleGramophone()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_gramophone_horn, (horn * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = horn,
                            onValueChange = {
                                viewModel.setGramophoneHorn(it)
                                if (!viewModel.effectsState.isGramophoneEnabled) viewModel.toggleGramophone()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val shellacLabel = stringResource(R.string.preset_gramo_shellac)
                        val cylLabel = stringResource(R.string.preset_gramo_cylinder)
                        val falloutLabel = stringResource(R.string.preset_gramo_fallout)
                        val presets = remember(shellacLabel, cylLabel, falloutLabel) {
                            listOf(
                                Triple(0.60f, 0.65f, shellacLabel),
                                Triple(0.85f, 0.90f, cylLabel),
                                Triple(0.40f, 0.40f, falloutLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - age) < 0.08f && kotlin.math.abs(it.second - horn) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pAge, pHorn, _) ->
                                viewModel.setGramophoneAge(pAge)
                                viewModel.setGramophoneHorn(pHorn)
                                if (!viewModel.effectsState.isGramophoneEnabled) viewModel.toggleGramophone()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_gramophone_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showGramophoneDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showReverseEchoDialog) {
            AlertDialog(
                onDismissRequest = { showReverseEchoDialog = false },
                icon = { Icon(Icons.Rounded.CompareArrows, null) },
                title = { Text(stringResource(R.string.effect_reverse_echo)) },
                text = {
                    Column {
                        val time = viewModel.effectsState.reverseEchoTime
                        val fb = viewModel.effectsState.reverseEchoFeedback

                        Text(
                            stringResource(R.string.label_reverse_time, (time * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = time,
                            onValueChange = {
                                viewModel.setReverseEchoTime(it)
                                if (!viewModel.effectsState.isReverseEchoEnabled) viewModel.toggleReverseEcho()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_reverse_feedback, (fb * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = fb,
                            onValueChange = {
                                viewModel.setReverseEchoFeedback(it)
                                if (!viewModel.effectsState.isReverseEchoEnabled) viewModel.toggleReverseEcho()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val psychLabel = stringResource(R.string.preset_reverse_psych)
                        val ghostLabel = stringResource(R.string.preset_reverse_ghost)
                        val tameLabel = stringResource(R.string.preset_reverse_tame)
                        val presets = remember(psychLabel, ghostLabel, tameLabel) {
                            listOf(
                                Triple(0.55f, 0.60f, psychLabel),
                                Triple(0.85f, 0.75f, ghostLabel),
                                Triple(0.35f, 0.45f, tameLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - time) < 0.08f && kotlin.math.abs(it.second - fb) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pTime, pFb, _) ->
                                viewModel.setReverseEchoTime(pTime)
                                viewModel.setReverseEchoFeedback(pFb)
                                if (!viewModel.effectsState.isReverseEchoEnabled) viewModel.toggleReverseEcho()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_reverse_echo_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showReverseEchoDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showStadiumDialog) {
            AlertDialog(
                onDismissRequest = { showStadiumDialog = false },
                icon = { Icon(Icons.Rounded.SurroundSound, null) },
                title = { Text(stringResource(R.string.effect_stadium)) },
                text = {
                    Column {
                        val size = viewModel.effectsState.stadiumSize
                        val atmosphere = viewModel.effectsState.stadiumAtmosphere

                        Text(
                            stringResource(R.string.label_stadium_size, (size * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = size,
                            onValueChange = {
                                viewModel.setStadiumSize(it)
                                if (!viewModel.effectsState.isStadiumEnabled) viewModel.toggleStadium()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_stadium_atmosphere, (atmosphere * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = atmosphere,
                            onValueChange = {
                                viewModel.setStadiumAtmosphere(it)
                                if (!viewModel.effectsState.isStadiumEnabled) viewModel.toggleStadium()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val s50kLabel = stringResource(R.string.preset_stadium_50k)
                        val arenaLabel = stringResource(R.string.preset_stadium_arena)
                        val festivalLabel = stringResource(R.string.preset_stadium_festival)
                        val presets = remember(s50kLabel, arenaLabel, festivalLabel) {
                            listOf(
                                Triple(0.75f, 0.70f, s50kLabel),
                                Triple(0.55f, 0.50f, arenaLabel),
                                Triple(0.90f, 0.85f, festivalLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - size) < 0.08f && kotlin.math.abs(it.second - atmosphere) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pSize, pAtm, _) ->
                                viewModel.setStadiumSize(pSize)
                                viewModel.setStadiumAtmosphere(pAtm)
                                if (!viewModel.effectsState.isStadiumEnabled) viewModel.toggleStadium()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_stadium_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showStadiumDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showWalkmanDialog) {
            AlertDialog(
                onDismissRequest = { showWalkmanDialog = false },
                icon = { Icon(Icons.Rounded.Radio, null) },
                title = { Text(stringResource(R.string.effect_cassette_walkman)) },
                text = {
                    Column {
                        val drive = viewModel.effectsState.walkmanDrive
                        val hiss = viewModel.effectsState.walkmanHiss

                        Text(
                            stringResource(R.string.label_walkman_drive, (drive * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = drive,
                            onValueChange = {
                                viewModel.setWalkmanDrive(it)
                                if (!viewModel.effectsState.isWalkmanEnabled) viewModel.toggleWalkman()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_walkman_hiss, (hiss * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = hiss,
                            onValueChange = {
                                viewModel.setWalkmanHiss(it)
                                if (!viewModel.effectsState.isWalkmanEnabled) viewModel.toggleWalkman()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val w1984Label = stringResource(R.string.preset_walkman_1984)
                        val chromeLabel = stringResource(R.string.preset_walkman_chrome)
                        val lofiLabel = stringResource(R.string.preset_walkman_lofi)
                        val presets = remember(w1984Label, chromeLabel, lofiLabel) {
                            listOf(
                                Triple(0.65f, 0.40f, w1984Label),
                                Triple(0.45f, 0.20f, chromeLabel),
                                Triple(0.85f, 0.75f, lofiLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - drive) < 0.08f && kotlin.math.abs(it.second - hiss) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pDrive, pHiss, _) ->
                                viewModel.setWalkmanDrive(pDrive)
                                viewModel.setWalkmanHiss(pHiss)
                                if (!viewModel.effectsState.isWalkmanEnabled) viewModel.toggleWalkman()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_walkman_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showWalkmanDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showAsmrVocalDialog) {
            AlertDialog(
                onDismissRequest = { showAsmrVocalDialog = false },
                icon = { Icon(Icons.Rounded.RecordVoiceOver, null) },
                title = { Text(stringResource(R.string.effect_asmr_vocal)) },
                text = {
                    Column {
                        val proximity = viewModel.effectsState.asmrProximity
                        val air = viewModel.effectsState.asmrAir

                        Text(
                            stringResource(R.string.label_asmr_proximity, (proximity * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = proximity,
                            onValueChange = {
                                viewModel.setAsmrProximity(it)
                                if (!viewModel.effectsState.isAsmrVocalEnabled) viewModel.toggleAsmrVocal()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_asmr_air, (air * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = air,
                            onValueChange = {
                                viewModel.setAsmrAir(it)
                                if (!viewModel.effectsState.isAsmrVocalEnabled) viewModel.toggleAsmrVocal()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val whisperLabel = stringResource(R.string.preset_asmr_whisper)
                        val studioLabel = stringResource(R.string.preset_asmr_studio)
                        val sheenLabel = stringResource(R.string.preset_asmr_sheen)
                        val presets = remember(whisperLabel, studioLabel, sheenLabel) {
                            listOf(
                                Triple(0.85f, 0.60f, whisperLabel),
                                Triple(0.50f, 0.45f, studioLabel),
                                Triple(0.70f, 0.90f, sheenLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - proximity) < 0.08f && kotlin.math.abs(it.second - air) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pProx, pAir, _) ->
                                viewModel.setAsmrProximity(pProx)
                                viewModel.setAsmrAir(pAir)
                                if (!viewModel.effectsState.isAsmrVocalEnabled) viewModel.toggleAsmrVocal()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_asmr_vocal_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showAsmrVocalDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showNightDriveDialog) {
            AlertDialog(
                onDismissRequest = { showNightDriveDialog = false },
                icon = { Icon(Icons.Rounded.DirectionsCar, null) },
                title = { Text(stringResource(R.string.effect_night_drive)) },
                text = {
                    Column {
                        val cabin = viewModel.effectsState.nightDriveCabin
                        val road = viewModel.effectsState.nightDriveRoad

                        Text(
                            stringResource(R.string.label_night_drive_cabin, (cabin * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = cabin,
                            onValueChange = {
                                viewModel.setNightDriveCabin(it)
                                if (!viewModel.effectsState.isNightDriveEnabled) viewModel.toggleNightDrive()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(14.dp))

                        Text(
                            stringResource(R.string.label_night_drive_road, (road * 100).toInt()),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                        Spacer(Modifier.height(8.dp))
                        Slider(
                            value = road,
                            onValueChange = {
                                viewModel.setNightDriveRoad(it)
                                if (!viewModel.effectsState.isNightDriveEnabled) viewModel.toggleNightDrive()
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(16.dp))

                        val sedanLabel = stringResource(R.string.preset_night_sedan)
                        val hwyLabel = stringResource(R.string.preset_night_highway)
                        val coupeLabel = stringResource(R.string.preset_night_coupe)
                        val presets = remember(sedanLabel, hwyLabel, coupeLabel) {
                            listOf(
                                Triple(0.60f, 0.45f, sedanLabel),
                                Triple(0.80f, 0.70f, hwyLabel),
                                Triple(0.45f, 0.35f, coupeLabel)
                            )
                        }
                        val selectedPreset = presets.firstOrNull {
                            kotlin.math.abs(it.first - cabin) < 0.08f && kotlin.math.abs(it.second - road) < 0.08f
                        }
                        ExpressiveConnectedButtonGroup(
                            options = presets,
                            selectedOption = selectedPreset,
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 8.dp),
                            onOptionSelected = { (pCab, pRoad, _) ->
                                viewModel.setNightDriveCabin(pCab)
                                viewModel.setNightDriveRoad(pRoad)
                                if (!viewModel.effectsState.isNightDriveEnabled) viewModel.toggleNightDrive()
                            },
                            labelProvider = { (_, _, label) ->
                                Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Text(
                            text = stringResource(R.string.fx_night_drive_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showNightDriveDialog = false }) {
                        Text(stringResource(R.string.btn_ok))
                    }
                }
            )
        }

        if (showStudioEditSheet) {
            com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
                onDismissRequest = { showStudioEditSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                AudioFxStudioSheet(
                    viewModel = viewModel,
                    allEffects = allEffects,
                    onOpenBassBoostDialog = { showBassBoostDialog = true },
                    onOpenEarrapeDialog = { showEarrapeDialog = true },
                    onOpenEightDDialog = { showEightDDialog = true },
                    onOpenMuffledDialog = { showMuffledDialog = true },
                    onOpenReverbDialog = { showReverbDialog = true },
                    onOpenRainDialog = { showRainVolumeDialog = true },
                    onOpenNormalizationDialog = { showNormalizationDialog = true },
                    onOpenVintageMp3Dialog = { showVintageMp3Dialog = true },
                    onOpenVocalRemoverDialog = { showVocalRemoverDialog = true },
                    onOpenVocalBoostDialog = { showVocalBoostDialog = true },
                    onOpenFlangerDialog = { showFlangerDialog = true },
                    onOpenPartyNextDoorDialog = { showPartyNextDoorDialog = true },
                    onOpenSuperWideDialog = { showSuperWideDialog = true },
                    onOpenVinylLoFiDialog = { showVinylLoFiDialog = true },
                    onOpenPhaserDialog = { showPhaserDialog = true },
                    onOpenMegaphoneDialog = { showMegaphoneDialog = true },
                    onOpenRobotVocoderDialog = { showRobotVocoderDialog = true },
                    onOpenChorusDialog = { showChorusDialog = true },
                    onOpenUnderwaterDialog = { showUnderwaterDialog = true },
                    onOpenTranceGateDialog = { showTranceGateDialog = true },
                    onOpenPingPongDelayDialog = { showPingPongDelayDialog = true },
                    onOpenChiptuneDialog = { showChiptuneDialog = true },
                    onOpenShimmerReverbDialog = { showShimmerReverbDialog = true },
                    onOpenRotarySpeakerDialog = { showRotarySpeakerDialog = true },
                    onOpenTapeSaturationDialog = { showTapeSaturationDialog = true },
                    onOpenSubOctaverDialog = { showSubOctaverDialog = true },
                    onOpenEmptyMallDialog = { showEmptyMallDialog = true },
                    onOpenGramophoneDialog = { showGramophoneDialog = true },
                    onOpenReverseEchoDialog = { showReverseEchoDialog = true },
                    onOpenStadiumDialog = { showStadiumDialog = true },
                    onOpenWalkmanDialog = { showWalkmanDialog = true },
                    onOpenAsmrVocalDialog = { showAsmrVocalDialog = true },
                    onOpenNightDriveDialog = { showNightDriveDialog = true },
                    onShowEarrapeWarning = { showEarrapeWarning = true },
                    onDismiss = { showStudioEditSheet = false }
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FxTile(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    activeContentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(300), label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(300), label = "contentColor"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (isActive) 1.2f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMedium
        ), label = "iconScale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    var longPressConsumed by remember { mutableStateOf(false) }

    if (onLongClick != null) {
        LaunchedEffect(interactionSource, onLongClick) {
            var longPressJob: Job? = null
            interactionSource.interactions.collect { interaction ->
                when (interaction) {
                    is PressInteraction.Press -> {
                        longPressConsumed = false
                        longPressJob = launch {
                            delay(android.view.ViewConfiguration.getLongPressTimeout().toLong())
                            longPressConsumed = true
                            onLongClick()
                        }
                    }

                    is PressInteraction.Release, is PressInteraction.Cancel -> {
                        longPressJob?.cancel()
                    }
                }
            }
        }
    }

    FilledTonalButton(
        onClick = {
            if (!longPressConsumed) onClick()
            longPressConsumed = false
        },
        modifier = modifier.height(84.dp),
        shapes = ButtonDefaults.shapes(),
        interactionSource = interactionSource,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

data class AudioFxDefinition(
    val id: String,
    val titleRes: Int,
    val icon: ImageVector,
    val categoryRes: Int,
    val isActive: (AudioEffectsState) -> Boolean,
    val onToggle: (PlayerViewModel, onEarrapeWarning: () -> Unit) -> Unit,
    val onOpenDialog: (() -> Unit)? = null,
    val activeColor: @Composable () -> Color = { MaterialTheme.colorScheme.primary },
    val activeContentColor: @Composable () -> Color = { MaterialTheme.colorScheme.onPrimary }
)

@Composable
fun getAudioFxDefinitions(
    onOpenBassBoostDialog: () -> Unit,
    onOpenEarrapeDialog: () -> Unit,
    onOpenEightDDialog: () -> Unit,
    onOpenMuffledDialog: () -> Unit,
    onOpenReverbDialog: () -> Unit,
    onOpenRainDialog: () -> Unit,
    onOpenNormalizationDialog: () -> Unit,
    onOpenVintageMp3Dialog: () -> Unit,
    onOpenVocalRemoverDialog: () -> Unit,
    onOpenVocalBoostDialog: () -> Unit,
    onOpenFlangerDialog: () -> Unit,
    onOpenPartyNextDoorDialog: () -> Unit,
    onOpenSuperWideDialog: () -> Unit,
    onOpenVinylLoFiDialog: () -> Unit,
    onOpenPhaserDialog: () -> Unit,
    onOpenMegaphoneDialog: () -> Unit,
    onOpenRobotVocoderDialog: () -> Unit,
    onOpenChorusDialog: () -> Unit,
    onOpenUnderwaterDialog: () -> Unit,
    onOpenTranceGateDialog: () -> Unit,
    onOpenPingPongDelayDialog: () -> Unit,
    onOpenChiptuneDialog: () -> Unit,
    onOpenShimmerReverbDialog: () -> Unit,
    onOpenRotarySpeakerDialog: () -> Unit,
    onOpenTapeSaturationDialog: () -> Unit,
    onOpenSubOctaverDialog: () -> Unit,
    onOpenEmptyMallDialog: () -> Unit,
    onOpenGramophoneDialog: () -> Unit,
    onOpenReverseEchoDialog: () -> Unit,
    onOpenStadiumDialog: () -> Unit,
    onOpenWalkmanDialog: () -> Unit,
    onOpenAsmrVocalDialog: () -> Unit,
    onOpenNightDriveDialog: () -> Unit,
    onShowEarrapeWarning: () -> Unit
): List<AudioFxDefinition> = listOf(
    AudioFxDefinition(
        id = "bass_boost",
        titleRes = R.string.effect_bass_boost,
        icon = Icons.Rounded.Bolt,
        categoryRes = R.string.category_power_eq,
        isActive = { it.isBassBoostEnabled },
        onToggle = { vm, _ -> vm.toggleBassBoost() },
        onOpenDialog = onOpenBassBoostDialog,
        activeColor = { MaterialTheme.colorScheme.primary },
        activeContentColor = { MaterialTheme.colorScheme.onPrimary }
    ),
    AudioFxDefinition(
        id = "sub_octaver",
        titleRes = R.string.effect_sub_octaver,
        icon = Icons.Rounded.Speaker,
        categoryRes = R.string.category_power_eq,
        isActive = { it.isSubOctaverEnabled },
        onToggle = { vm, _ -> vm.toggleSubOctaver() },
        onOpenDialog = onOpenSubOctaverDialog,
        activeColor = { Color(0xFFD500F9) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "tape_saturation",
        titleRes = R.string.effect_tape_saturation,
        icon = Icons.Rounded.Whatshot,
        categoryRes = R.string.category_power_eq,
        isActive = { it.isTapeSaturationEnabled },
        onToggle = { vm, _ -> vm.toggleTapeSaturation() },
        onOpenDialog = onOpenTapeSaturationDialog,
        activeColor = { Color(0xFFFF6E40) },
        activeContentColor = { Color(0xFF3E1200) }
    ),
    AudioFxDefinition(
        id = "vocal_boost",
        titleRes = R.string.effect_vocal_boost,
        icon = Icons.Rounded.RecordVoiceOver,
        categoryRes = R.string.category_power_eq,
        isActive = { it.isVocalBoostEnabled },
        onToggle = { vm, _ -> vm.toggleVocalBoost() },
        onOpenDialog = onOpenVocalBoostDialog,
        activeColor = { Color(0xFF00B0FF) },
        activeContentColor = { Color(0xFF002244) }
    ),
    AudioFxDefinition(
        id = "vocal_remover",
        titleRes = R.string.effect_vocal_remover,
        icon = Icons.Rounded.MicOff,
        categoryRes = R.string.category_power_eq,
        isActive = { it.isVocalRemoverEnabled },
        onToggle = { vm, _ -> vm.toggleVocalRemover() },
        onOpenDialog = onOpenVocalRemoverDialog,
        activeColor = { Color(0xFFE91E63) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "normalization",
        titleRes = R.string.pref_norm_title,
        icon = Icons.Rounded.VolumeDown,
        categoryRes = R.string.category_power_eq,
        isActive = { it.isNormalizationEnabled },
        onToggle = { vm, _ -> vm.toggleNormalization() },
        onOpenDialog = onOpenNormalizationDialog,
        activeColor = { MaterialTheme.colorScheme.primary },
        activeContentColor = { MaterialTheme.colorScheme.onPrimary }
    ),
    AudioFxDefinition(
        id = "earrape",
        titleRes = R.string.btn_earrape,
        icon = Icons.AutoMirrored.Rounded.VolumeUp,
        categoryRes = R.string.category_power_eq,
        isActive = { it.isEarrapeEnabled },
        onToggle = { vm, showWarn ->
            if (!vm.hasSeenEarrapeWarning()) showWarn() else vm.toggleEarrape()
        },
        onOpenDialog = onOpenEarrapeDialog,
        activeColor = { MaterialTheme.colorScheme.error },
        activeContentColor = { MaterialTheme.colorScheme.onError }
    ),

    AudioFxDefinition(
        id = "eight_d",
        titleRes = R.string.effect_8d,
        icon = Icons.Rounded.SurroundSound,
        categoryRes = R.string.category_spatial,
        isActive = { it.is8DEnabled },
        onToggle = { vm, _ -> vm.toggle8D() },
        onOpenDialog = onOpenEightDDialog,
        activeColor = { MaterialTheme.colorScheme.tertiary },
        activeContentColor = { MaterialTheme.colorScheme.onTertiary }
    ),
    AudioFxDefinition(
        id = "super_wide",
        titleRes = R.string.effect_super_wide,
        icon = Icons.Rounded.SurroundSound,
        categoryRes = R.string.category_spatial,
        isActive = { it.isSuperWideEnabled },
        onToggle = { vm, _ -> vm.toggleSuperWide() },
        onOpenDialog = onOpenSuperWideDialog,
        activeColor = { Color(0xFF26C6DA) },
        activeContentColor = { Color(0xFF00363A) }
    ),
    AudioFxDefinition(
        id = "chorus",
        titleRes = R.string.effect_chorus,
        icon = Icons.Rounded.Grain,
        categoryRes = R.string.category_spatial,
        isActive = { it.isChorusEnabled },
        onToggle = { vm, _ -> vm.toggleChorus() },
        onOpenDialog = onOpenChorusDialog,
        activeColor = { Color(0xFF5C6BC0) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "flanger",
        titleRes = R.string.effect_flanger,
        icon = Icons.Rounded.Air,
        categoryRes = R.string.category_spatial,
        isActive = { it.isFlangerEnabled },
        onToggle = { vm, _ -> vm.toggleFlanger() },
        onOpenDialog = onOpenFlangerDialog,
        activeColor = { Color(0xFF00E5FF) },
        activeContentColor = { Color(0xFF003840) }
    ),
    AudioFxDefinition(
        id = "phaser",
        titleRes = R.string.effect_phaser,
        icon = Icons.Rounded.Waves,
        categoryRes = R.string.category_spatial,
        isActive = { it.isPhaserEnabled },
        onToggle = { vm, _ -> vm.togglePhaser() },
        onOpenDialog = onOpenPhaserDialog,
        activeColor = { Color(0xFF7C4DFF) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "ping_pong",
        titleRes = R.string.effect_ping_pong,
        icon = Icons.Rounded.SyncAlt,
        categoryRes = R.string.category_spatial,
        isActive = { it.isPingPongDelayEnabled },
        onToggle = { vm, _ -> vm.togglePingPongDelay() },
        onOpenDialog = onOpenPingPongDelayDialog,
        activeColor = { Color(0xFF64DD17) },
        activeContentColor = { Color(0xFF1B3B00) }
    ),
    AudioFxDefinition(
        id = "reverse_echo",
        titleRes = R.string.effect_reverse_echo,
        icon = Icons.Rounded.CompareArrows,
        categoryRes = R.string.category_spatial,
        isActive = { it.isReverseEchoEnabled },
        onToggle = { vm, _ -> vm.toggleReverseEcho() },
        onOpenDialog = onOpenReverseEchoDialog,
        activeColor = { Color(0xFF00E5FF) },
        activeContentColor = { Color(0xFF003B46) }
    ),
    AudioFxDefinition(
        id = "reverb",
        titleRes = R.string.effect_reverb,
        icon = Icons.Rounded.GraphicEq,
        categoryRes = R.string.category_spatial,
        isActive = { it.isReverbEnabled },
        onToggle = { vm, _ -> vm.toggleReverb() },
        onOpenDialog = onOpenReverbDialog,
        activeColor = { MaterialTheme.colorScheme.primary },
        activeContentColor = { MaterialTheme.colorScheme.onPrimary }
    ),
    AudioFxDefinition(
        id = "shimmer_reverb",
        titleRes = R.string.effect_shimmer_reverb,
        icon = Icons.Rounded.Flare,
        categoryRes = R.string.category_spatial,
        isActive = { it.isShimmerReverbEnabled },
        onToggle = { vm, _ -> vm.toggleShimmerReverb() },
        onOpenDialog = onOpenShimmerReverbDialog,
        activeColor = { Color(0xFFFF4081) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "stadium",
        titleRes = R.string.effect_stadium,
        icon = Icons.Rounded.SurroundSound,
        categoryRes = R.string.category_spatial,
        isActive = { it.isStadiumEnabled },
        onToggle = { vm, _ -> vm.toggleStadium() },
        onOpenDialog = onOpenStadiumDialog,
        activeColor = { Color(0xFF00E676) },
        activeContentColor = { Color(0xFF003815) }
    ),
    AudioFxDefinition(
        id = "rotary_speaker",
        titleRes = R.string.effect_rotary_speaker,
        icon = Icons.Rounded.RotateRight,
        categoryRes = R.string.category_spatial,
        isActive = { it.isRotarySpeakerEnabled },
        onToggle = { vm, _ -> vm.toggleRotarySpeaker() },
        onOpenDialog = onOpenRotarySpeakerDialog,
        activeColor = { Color(0xFFFF6D00) },
        activeContentColor = { Color(0xFF3E1200) }
    ),
    AudioFxDefinition(
        id = "asmr_vocal",
        titleRes = R.string.effect_asmr_vocal,
        icon = Icons.Rounded.RecordVoiceOver,
        categoryRes = R.string.category_spatial,
        isActive = { it.isAsmrVocalEnabled },
        onToggle = { vm, _ -> vm.toggleAsmrVocal() },
        onOpenDialog = onOpenAsmrVocalDialog,
        activeColor = { Color(0xFFFF4081) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "mono",
        titleRes = R.string.pref_audio_mono,
        icon = Icons.Rounded.Headphones,
        categoryRes = R.string.category_spatial,
        isActive = { it.isMonoEnabled },
        onToggle = { vm, _ -> vm.toggleMono() },
        onOpenDialog = null,
        activeColor = { MaterialTheme.colorScheme.secondary },
        activeContentColor = { MaterialTheme.colorScheme.onSecondary }
    ),

    AudioFxDefinition(
        id = "rain",
        titleRes = R.string.effect_ambient_sound,
        icon = Icons.Rounded.WaterDrop,
        categoryRes = R.string.category_ambience_filters,
        isActive = { it.isRainEnabled },
        onToggle = { vm, _ -> vm.toggleRain() },
        onOpenDialog = onOpenRainDialog,
        activeColor = { Color(0xFF81D4FA) },
        activeContentColor = { Color(0xFF004BA0) }
    ),
    AudioFxDefinition(
        id = "underwater",
        titleRes = R.string.effect_underwater,
        icon = Icons.Rounded.Waves,
        categoryRes = R.string.category_ambience_filters,
        isActive = { it.isUnderwaterEnabled },
        onToggle = { vm, _ -> vm.toggleUnderwater() },
        onOpenDialog = onOpenUnderwaterDialog,
        activeColor = { Color(0xFF00838F) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "empty_mall",
        titleRes = R.string.effect_empty_mall,
        icon = Icons.Rounded.Storefront,
        categoryRes = R.string.category_ambience_filters,
        isActive = { it.isEmptyMallEnabled },
        onToggle = { vm, _ -> vm.toggleEmptyMall() },
        onOpenDialog = onOpenEmptyMallDialog,
        activeColor = { Color(0xFF00BFA5) },
        activeContentColor = { Color(0xFF003730) }
    ),
    AudioFxDefinition(
        id = "party_next_door",
        titleRes = R.string.effect_party_next_door,
        icon = Icons.Rounded.MeetingRoom,
        categoryRes = R.string.category_ambience_filters,
        isActive = { it.isPartyNextDoorEnabled },
        onToggle = { vm, _ -> vm.togglePartyNextDoor() },
        onOpenDialog = onOpenPartyNextDoorDialog,
        activeColor = { Color(0xFFAB47BC) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "night_drive",
        titleRes = R.string.effect_night_drive,
        icon = Icons.Rounded.DirectionsCar,
        categoryRes = R.string.category_ambience_filters,
        isActive = { it.isNightDriveEnabled },
        onToggle = { vm, _ -> vm.toggleNightDrive() },
        onOpenDialog = onOpenNightDriveDialog,
        activeColor = { Color(0xFF2979FF) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "muffled",
        titleRes = R.string.effect_muffled,
        icon = Icons.Rounded.BlurOn,
        categoryRes = R.string.category_ambience_filters,
        isActive = { it.isMuffledEnabled },
        onToggle = { vm, _ -> vm.toggleMuffled() },
        onOpenDialog = onOpenMuffledDialog,
        activeColor = { MaterialTheme.colorScheme.secondary },
        activeContentColor = { MaterialTheme.colorScheme.onSecondary }
    ),

    AudioFxDefinition(
        id = "cassette_walkman",
        titleRes = R.string.effect_cassette_walkman,
        icon = Icons.Rounded.Radio,
        categoryRes = R.string.category_retro_vintage,
        isActive = { it.isWalkmanEnabled },
        onToggle = { vm, _ -> vm.toggleWalkman() },
        onOpenDialog = onOpenWalkmanDialog,
        activeColor = { Color(0xFFFFAB00) },
        activeContentColor = { Color(0xFF3E2700) }
    ),
    AudioFxDefinition(
        id = "vinyl_lofi",
        titleRes = R.string.effect_vinyl_lofi,
        icon = Icons.Rounded.Album,
        categoryRes = R.string.category_retro_vintage,
        isActive = { it.isVinylLoFiEnabled },
        onToggle = { vm, _ -> vm.toggleVinylLoFi() },
        onOpenDialog = onOpenVinylLoFiDialog,
        activeColor = { Color(0xFFFFB300) },
        activeContentColor = { Color(0xFF3E2723) }
    ),
    AudioFxDefinition(
        id = "gramophone",
        titleRes = R.string.effect_gramophone,
        icon = Icons.Rounded.History,
        categoryRes = R.string.category_retro_vintage,
        isActive = { it.isGramophoneEnabled },
        onToggle = { vm, _ -> vm.toggleGramophone() },
        onOpenDialog = onOpenGramophoneDialog,
        activeColor = { Color(0xFF8D6E63) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "vintage_mp3",
        titleRes = R.string.effect_vintage_mp3,
        icon = Icons.Rounded.Radio,
        categoryRes = R.string.category_retro_vintage,
        isActive = { it.isVintageMp3Enabled },
        onToggle = { vm, _ -> vm.toggleVintageMp3() },
        onOpenDialog = onOpenVintageMp3Dialog,
        activeColor = { Color(0xFFFFB74D) },
        activeContentColor = { Color(0xFF5D2B00) }
    ),
    AudioFxDefinition(
        id = "chiptune",
        titleRes = R.string.effect_chiptune,
        icon = Icons.Rounded.Gamepad,
        categoryRes = R.string.category_retro_vintage,
        isActive = { it.isChiptuneEnabled },
        onToggle = { vm, _ -> vm.toggleChiptune() },
        onOpenDialog = onOpenChiptuneDialog,
        activeColor = { Color(0xFFE040FB) },
        activeContentColor = { Color.White }
    ),
    AudioFxDefinition(
        id = "megaphone",
        titleRes = R.string.effect_megaphone,
        icon = Icons.Rounded.Campaign,
        categoryRes = R.string.category_retro_vintage,
        isActive = { it.isMegaphoneEnabled },
        onToggle = { vm, _ -> vm.toggleMegaphone() },
        onOpenDialog = onOpenMegaphoneDialog,
        activeColor = { Color(0xFFFF7043) },
        activeContentColor = { Color(0xFF3E1200) }
    ),
    AudioFxDefinition(
        id = "robot_vocoder",
        titleRes = R.string.effect_robot_vocoder,
        icon = Icons.Rounded.SmartToy,
        categoryRes = R.string.category_retro_vintage,
        isActive = { it.isRobotVocoderEnabled },
        onToggle = { vm, _ -> vm.toggleRobotVocoder() },
        onOpenDialog = onOpenRobotVocoderDialog,
        activeColor = { Color(0xFF00E676) },
        activeContentColor = { Color(0xFF003314) }
    ),
    AudioFxDefinition(
        id = "trance_gate",
        titleRes = R.string.effect_trance_gate,
        icon = Icons.Rounded.ElectricBolt,
        categoryRes = R.string.category_retro_vintage,
        isActive = { it.isTranceGateEnabled },
        onToggle = { vm, _ -> vm.toggleTranceGate() },
        onOpenDialog = onOpenTranceGateDialog,
        activeColor = { Color(0xFFFF9100) },
        activeContentColor = { Color(0xFF3E1A00) }
    )
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AudioFxStudioSheet(
    viewModel: PlayerViewModel,
    allEffects: List<AudioFxDefinition>,
    onOpenBassBoostDialog: () -> Unit,
    onOpenEarrapeDialog: () -> Unit,
    onOpenEightDDialog: () -> Unit,
    onOpenMuffledDialog: () -> Unit,
    onOpenReverbDialog: () -> Unit,
    onOpenRainDialog: () -> Unit,
    onOpenNormalizationDialog: () -> Unit,
    onOpenVintageMp3Dialog: () -> Unit,
    onOpenVocalRemoverDialog: () -> Unit,
    onOpenVocalBoostDialog: () -> Unit,
    onOpenFlangerDialog: () -> Unit,
    onOpenPartyNextDoorDialog: () -> Unit,
    onOpenSuperWideDialog: () -> Unit,
    onOpenVinylLoFiDialog: () -> Unit,
    onOpenPhaserDialog: () -> Unit,
    onOpenMegaphoneDialog: () -> Unit,
    onOpenRobotVocoderDialog: () -> Unit,
    onOpenChorusDialog: () -> Unit,
    onOpenUnderwaterDialog: () -> Unit,
    onOpenTranceGateDialog: () -> Unit,
    onOpenPingPongDelayDialog: () -> Unit,
    onOpenChiptuneDialog: () -> Unit,
    onOpenShimmerReverbDialog: () -> Unit,
    onOpenRotarySpeakerDialog: () -> Unit,
    onOpenTapeSaturationDialog: () -> Unit,
    onOpenSubOctaverDialog: () -> Unit,
    onOpenEmptyMallDialog: () -> Unit,
    onOpenGramophoneDialog: () -> Unit,
    onOpenReverseEchoDialog: () -> Unit,
    onOpenStadiumDialog: () -> Unit,
    onOpenWalkmanDialog: () -> Unit,
    onOpenAsmrVocalDialog: () -> Unit,
    onOpenNightDriveDialog: () -> Unit,
    onShowEarrapeWarning: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current

    var isDraggingTile by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val categories = remember(allEffects) {
        listOf(
            Triple(R.string.category_power_eq, Icons.Rounded.Bolt, allEffects.filter { it.categoryRes == R.string.category_power_eq }),
            Triple(R.string.category_spatial, Icons.Rounded.SurroundSound, allEffects.filter { it.categoryRes == R.string.category_spatial }),
            Triple(R.string.category_ambience_filters, Icons.Rounded.WaterDrop, allEffects.filter { it.categoryRes == R.string.category_ambience_filters }),
            Triple(R.string.category_retro_vintage, Icons.Rounded.Radio, allEffects.filter { it.categoryRes == R.string.category_retro_vintage })
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState, enabled = !isDraggingTile)
            .padding(horizontal = 24.dp)
            .padding(top = 8.dp, bottom = 36.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Surface(
                onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    viewModel.resetPinnedAudioFx()
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_reset),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.edit_tiles_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.edit_tiles_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.audio_fx_long_press_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )

        Spacer(Modifier.height(20.dp))

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                DraggablePinnedTilesGrid(
                    pinnedList = viewModel.pinnedAudioFx,
                    allEffects = allEffects,
                    viewModel = viewModel,
                    onDragStateChanged = { isDraggingTile = it },
                    onRemoveFx = { fxId ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        viewModel.togglePinAudioFx(fxId)
                    }
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        categories.forEach { (catTitleRes, catIcon, fxList) ->
            if (fxList.isNotEmpty()) {
                AvailableCategorySection(
                    categoryTitleRes = catTitleRes,
                    categoryIcon = catIcon,
                    effects = fxList,
                    viewModel = viewModel,
                    onToggleFx = { fxId ->
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        viewModel.togglePinAudioFx(fxId)
                    }
                )
            }
        }
    }
}

@Composable
fun DraggablePinnedTilesGrid(
    pinnedList: List<String>,
    allEffects: List<AudioFxDefinition>,
    viewModel: PlayerViewModel,
    onDragStateChanged: (Boolean) -> Unit,
    onRemoveFx: (String) -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current

    var currentOrder by remember(pinnedList) { mutableStateOf(pinnedList) }
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragTouchOffsetInItem by remember { mutableStateOf(Offset.Zero) }
    var currentFingerPos by remember { mutableStateOf(Offset.Zero) }

    LaunchedEffect(draggedId) {
        onDragStateChanged(draggedId != null)
    }

    val pinnedDefs = remember(currentOrder, allEffects) {
        currentOrder.mapNotNull { id -> allEffects.find { it.id == id } }
    }

    if (pinnedDefs.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = stringResource(R.string.edit_tiles_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val spacingDp = 10.dp
    val itemHeightDp = 76.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        val spacingPx = with(density) { spacingDp.toPx() }
        val itemHeightPx = with(density) { itemHeightDp.toPx() }
        val itemWidthPx = (totalWidthPx - spacingPx) / 2f
        val itemWidthDp = with(density) { itemWidthPx.toDp() }

        val totalRows = (currentOrder.size + 1) / 2
        val totalHeightDp = if (totalRows == 0) 0.dp else (itemHeightDp * totalRows + spacingDp * (totalRows - 1))

        Box(modifier = Modifier.fillMaxWidth().height(totalHeightDp)) {
            pinnedDefs.forEach { fx ->
                val index = currentOrder.indexOf(fx.id)
                if (index == -1) return@forEach

                val isDragging = draggedId == fx.id

                val slotCol = index % 2
                val slotRow = index / 2
                val slotXPx = slotCol * (itemWidthPx + spacingPx)
                val slotYPx = slotRow * (itemHeightPx + spacingPx)

                val targetXPx = if (isDragging) (currentFingerPos.x - dragTouchOffsetInItem.x) else slotXPx
                val targetYPx = if (isDragging) (currentFingerPos.y - dragTouchOffsetInItem.y) else slotYPx

                val animatedXPx by animateFloatAsState(
                    targetValue = targetXPx,
                    animationSpec = if (isDragging) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "itemX_${fx.id}"
                )
                val animatedYPx by animateFloatAsState(
                    targetValue = targetYPx,
                    animationSpec = if (isDragging) snap() else spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "itemY_${fx.id}"
                )

                val scale by animateFloatAsState(
                    targetValue = if (isDragging) 1.08f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy),
                    label = "itemScale_${fx.id}"
                )
                val zIndex = if (isDragging) 100f else 1f

                Box(
                    modifier = Modifier
                        .offset { IntOffset(animatedXPx.roundToInt(), animatedYPx.roundToInt()) }
                        .width(itemWidthDp)
                        .height(itemHeightDp)
                        .zIndex(zIndex)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shadowElevation = if (isDragging) 16.dp.toPx() else 0f
                            shape = RoundedCornerShape(22.dp)
                            clip = false
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offsetInItem ->
                                    val curIdx = currentOrder.indexOf(fx.id)
                                    if (curIdx != -1) {
                                        val col = curIdx % 2
                                        val row = curIdx / 2
                                        val itemOriginX = col * (itemWidthPx + spacingPx)
                                        val itemOriginY = row * (itemHeightPx + spacingPx)

                                        draggedId = fx.id
                                        dragTouchOffsetInItem = offsetInItem
                                        currentFingerPos = Offset(itemOriginX + offsetInItem.x, itemOriginY + offsetInItem.y)
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (draggedId == fx.id) {
                                        currentFingerPos += dragAmount

                                        val hoveredCol = if (currentFingerPos.x > totalWidthPx / 2f) 1 else 0
                                        val maxRow = (currentOrder.size - 1) / 2
                                        val hoveredRow = (currentFingerPos.y / (itemHeightPx + spacingPx)).toInt().coerceIn(0, maxRow)
                                        val targetIdx = (hoveredRow * 2 + hoveredCol).coerceIn(0, currentOrder.lastIndex)

                                        val curIdx = currentOrder.indexOf(fx.id)
                                        if (curIdx != -1 && targetIdx != curIdx) {
                                            val updated = currentOrder.toMutableList().apply {
                                                removeAt(curIdx)
                                                add(targetIdx, fx.id)
                                            }
                                            currentOrder = updated
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (draggedId == fx.id) {
                                        viewModel.updatePinnedAudioFx(currentOrder)
                                        draggedId = null
                                    }
                                },
                                onDragCancel = {
                                    if (draggedId == fx.id) {
                                        viewModel.updatePinnedAudioFx(currentOrder)
                                        draggedId = null
                                    }
                                }
                            )
                        }
                ) {
                    ActiveQSTile(
                        fx = fx,
                        isActive = fx.isActive(viewModel.effectsState),
                        onRemove = { onRemoveFx(fx.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveQSTile(
    fx: AudioFxDefinition,
    isActive: Boolean,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeColor = fx.activeColor()
    val activeContentColor = fx.activeContentColor()

    val containerColor by animateColorAsState(
        targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(250),
        label = "activeContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "activeContentColor"
    )

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        border = if (isActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isActive) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = fx.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isActive) activeContentColor else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(fx.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(30.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AvailableCategorySection(
    categoryTitleRes: Int,
    categoryIcon: ImageVector,
    effects: List<AudioFxDefinition>,
    viewModel: PlayerViewModel,
    onToggleFx: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 4.dp, bottom = 12.dp)
        ) {
            Icon(
                imageVector = categoryIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(categoryTitleRes),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            effects.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { fx ->
                        val isPinned = viewModel.isAudioFxPinned(fx.id)
                        AvailableTile(
                            fx = fx,
                            isPinned = isPinned,
                            onClick = { onToggleFx(fx.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
fun AvailableTile(
    fx: AudioFxDefinition,
    isPinned: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        targetValue = if (isPinned) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surfaceContainerHighest,
        animationSpec = tween(250),
        label = "availContainerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isPinned) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
        animationSpec = tween(250),
        label = "availContentColor"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (isPinned) 0.2f else 0.4f)),
        modifier = modifier.height(72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 12.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (isPinned) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = fx.icon,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isPinned) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(fx.titleRes),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Surface(
                shape = CircleShape,
                color = if (isPinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                contentColor = if (isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isPinned) Icons.Rounded.Check else Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DockButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        label = "Color"
    )
    val contentColor by animateColorAsState(
        if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        label = "ContentColor"
    )
    val iconScale by animateFloatAsState(
        if (isActive) 1.1f else 1f, label = "Scale"
    )

    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(80.dp),
        shapes = ButtonDefaults.shapes(),
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheetContent(viewModel: PlayerViewModel, onClose: () -> Unit) {
    val comments = viewModel.commentsList
    val isLoading = viewModel.isCommentsLoading
    val context = LocalContext.current
    val myId = viewModel.currentUserId
    val isGuest = myId == 0L // Guest check
    val replyingTo = viewModel.replyingToComment
    var commentText by remember { mutableStateOf("") }
    var commentToDelete by remember { mutableStateOf<Comment?>(null) }
    val commentSort = viewModel.commentSort
    val tabs = remember { CommentSort.values() }
    var isSortMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(replyingTo) {
        if (replyingTo != null) {
            val username = replyingTo.user?.username ?: ""; commentText = "@$username: "
        }
    }
    val isPosting = viewModel.isPostingComment

    if (commentToDelete != null) {
        AlertDialog(
            onDismissRequest = { commentToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_comment_title)) },
            text = { Text(stringResource(R.string.dialog_delete_comment_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteComment(commentToDelete!!); commentToDelete = null
                }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text(
                        stringResource(R.string.btn_delete)
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    commentToDelete = null
                }) { Text(stringResource(R.string.btn_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    Scaffold(
        topBar = {
            Column {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.menu_comments),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onClose) {
                            Icon(
                                Icons.Rounded.KeyboardArrowDown,
                                stringResource(R.string.btn_close)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
                )
                ; HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
        },
        bottomBar = {
            Column(modifier = Modifier.imePadding().navigationBarsPadding()) {
                AnimatedVisibility(visible = replyingTo != null && !isGuest) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Replying to ${replyingTo?.user?.username}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            ); IconButton(
                            onClick = { viewModel.cancelReplying(); commentText = "" },
                            modifier = Modifier.size(24.dp)
                        ) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) }
                        }
                    }
                }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 16.dp, shadowElevation = 16.dp) {
                    if (isGuest) {
                        val loginToCommentMsg = stringResource(R.string.login_to_comment)
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp).background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                RoundedCornerShape(24.dp)
                            ).clickable { Toast.makeText(context, loginToCommentMsg, Toast.LENGTH_SHORT).show() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = loginToCommentMsg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.Bottom
                        ) {
                            TextField(
                                value = commentText,
                                onValueChange = { commentText = it },
                                placeholder = { Text(if (replyingTo != null) "Write a reply..." else stringResource(R.string.add_comment_hint)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(24.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                ),
                                maxLines = 4,
                                enabled = !isPosting,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                                keyboardActions = KeyboardActions(onSend = {
                                    if (commentText.isNotBlank()) {
                                        viewModel.postComment(commentText, null); commentText = ""
                                    }
                                })
                            )
                            Spacer(Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (commentText.isNotBlank()) {
                                        viewModel.postComment(commentText, null); commentText = ""
                                    }
                                },
                                enabled = !isPosting && commentText.isNotBlank(),
                                modifier = Modifier.size(48.dp).background(
                                    if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    CircleShape
                                )
                            ) {
                                if (isPosting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Rounded.Send,
                                        stringResource(R.string.comment_send_action),
                                        tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (comments.isEmpty() && isLoading) {
            Box(
                Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { ContainedLoadingIndicator() }
        } else if (comments.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.ChatBubbleOutline,
                        null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.surfaceVariant
                    ); Spacer(Modifier.height(16.dp)); Text(
                    stringResource(R.string.comment_no_comments),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)
            ) {
                item {
                    Box(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)) {
                        OutlinedButton(onClick = { isSortMenuExpanded = true }) {
                            Text(
                                text = stringResource(
                                    id = R.string.sorted_by,
                                    stringResource(id = commentSort.labelResId)
                                ),
                                style = MaterialTheme.typography.labelLarge
                            )
                            Icon(Icons.Rounded.ArrowDropDown, contentDescription = null)
                        }

                        DropdownMenu(
                            expanded = isSortMenuExpanded,
                            onDismissRequest = { isSortMenuExpanded = false }
                        ) {
                            tabs.forEach { sortOption ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(id = sortOption.labelResId)) },
                                    onClick = {
                                        viewModel.onCommentSortChanged(sortOption)
                                        isSortMenuExpanded = false
                                    },
                                    trailingIcon = {
                                        if (sortOption == commentSort) {
                                            Icon(
                                                Icons.Rounded.Check,
                                                contentDescription = stringResource(id = R.string.desc_selected)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                items(count = comments.size, key = { index -> comments[index].id }) { index ->
                    val comment = comments[index]
                    Column {
                        val userId = comment.user?.id ?: 0L
                        CommentRowItem(
                            comment = comment,
                            isMine = (userId == myId),
                            isReply = false,
                            isGuest = isGuest,
                            onNavigateToProfile = { if (userId != 0L) viewModel.navigateToArtist(userId) },
                            onSeekTo = { pos ->
                                val t = viewModel.selectedTrackForSheet; if (t != null) {
                                if (t.id == viewModel.currentTrack?.id) viewModel.seekTo(pos) else viewModel.playTrackAtPosition(
                                    t,
                                    pos
                                )
                            }
                            },
                            onToggleLike = { viewModel.toggleCommentLike(comment) },
                            onReply = { viewModel.startReplying(comment) },
                            onDelete = { commentToDelete = comment })
                        comment.replies?.forEach { reply ->
                            val rUserId = reply.user?.id ?: 0L
                            CommentRowItem(
                                comment = reply,
                                isMine = (rUserId == myId),
                                isReply = true,
                                isGuest = isGuest,
                                onNavigateToProfile = { if (rUserId != 0L) viewModel.navigateToArtist(rUserId) },
                                onSeekTo = { pos ->
                                    val t = viewModel.selectedTrackForSheet; if (t != null) {
                                    if (t.id == viewModel.currentTrack?.id) viewModel.seekTo(
                                        reply.trackTimestamp ?: 0
                                    ) else viewModel.playTrackAtPosition(t, reply.trackTimestamp ?: 0)
                                }
                                },
                                onToggleLike = { viewModel.toggleCommentLike(reply) },
                                onReply = { viewModel.startReplying(comment) },
                                onDelete = { commentToDelete = reply })
                        }
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                        )
                    }
                }
                if (viewModel.commentNextHref != null) {
                    item {
                        LaunchedEffect(Unit) { viewModel.loadComments() }; Box(
                        Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center
                    ) { LoadingIndicator(modifier = Modifier.size(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
fun CommentRowItem(
    comment: Comment,
    isMine: Boolean,
    isReply: Boolean,
    isGuest: Boolean,
    onNavigateToProfile: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleLike: () -> Unit,
    onReply: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val avatarUrl = comment.user?.avatarUrl
    val username = comment.user?.username ?: stringResource(R.string.comment_anonymous)
    val isVisuallyReply = isReply || comment.body.trim().startsWith("@")
    val startPadding = if (isVisuallyReply) 56.dp else 16.dp
    val avatarSize = if (isVisuallyReply) 40.dp else 48.dp
    val loginToInteractMsg = stringResource(R.string.login_to_interact)

    var translatedText by remember { mutableStateOf<String?>(null) }
    var showTranslation by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(modifier = Modifier.fillMaxWidth().padding(start = startPadding, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Box(
            modifier = Modifier.size(avatarSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onNavigateToProfile() }, contentAlignment = Alignment.Center
        ) {
            if (!avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Default.Person,
                    contentDescription = stringResource(R.string.comment_default_avatar),
                    modifier = Modifier.size(if (isVisuallyReply) 24.dp else 28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isVisuallyReply) 13.sp else 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable { onNavigateToProfile() })
                Spacer(Modifier.width(8.dp))
                if (comment.user?.verified == true) {
                    Icon(
                        Icons.Rounded.Verified,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = getRelativeTime(comment.createdAt, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = if (showTranslation && !translatedText.isNullOrEmpty()) translatedText!! else comment.body,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp, fontSize = 15.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )

            val currentLocale = java.util.Locale.getDefault()
            val langName = remember(currentLocale) {
                currentLocale.getDisplayLanguage(currentLocale)
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(currentLocale) else it.toString() }
            }
            val langCode = currentLocale.language
            val context = LocalContext.current

            var isTargetLanguage by remember(comment.body, langCode) { mutableStateOf(false) }
            LaunchedEffect(comment.body, langCode) {
                val cleanText = comment.body.replace(Regex("[^\\p{L}\\p{Nd}\\s]"), "").trim()
                if (cleanText.isBlank()) {
                    isTargetLanguage = true
                } else {
                    val languageIdentifier = com.google.mlkit.nl.languageid.LanguageIdentification.getClient()
                    languageIdentifier.identifyLanguage(cleanText)
                        .addOnSuccessListener { language ->
                            if (language == langCode || language == "und") {
                                isTargetLanguage = true
                            }
                        }
                }
            }

            if (translatedText == null && !isTranslating && !isTargetLanguage) {
                Text(
                    text = stringResource(R.string.comment_translate, langName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            isTranslating = true
                            scope.launch(Dispatchers.IO) {
                                val res = com.alananasss.kittytune.data.network.FreeTranslator.translateMissing(
                                    listOf(comment.body),
                                    langCode
                                )
                                val t = res[comment.body.trim()]
                                withContext(Dispatchers.Main) {
                                    if (t != null && t.lowercase() != comment.body.trim().lowercase()) {
                                        translatedText = t
                                        showTranslation = true
                                    } else {
                                        translatedText = ""
                                    }
                                    isTranslating = false
                                }
                            }
                        }
                        .padding(vertical = 2.dp)
                )
            } else if (isTranslating) {
                Text(
                    text = stringResource(R.string.comment_translating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else if (!translatedText.isNullOrEmpty()) {
                Text(
                    text = if (showTranslation) stringResource(R.string.comment_see_original) else stringResource(
                        R.string.comment_translate,
                        langName
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showTranslation = !showTranslation }
                        .padding(vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.trackTimestamp != null) {
                    Surface(
                        onClick = { onSeekTo(comment.trackTimestamp) },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                null,
                                modifier = Modifier.size(12.dp)
                            ); Spacer(Modifier.width(4.dp)); Text(
                            text = makeTimeString(comment.trackTimestamp),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                if (isMine) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }; Spacer(Modifier.width(4.dp))
                }
                IconButton(onClick = {
                    if (isGuest) Toast.makeText(context, loginToInteractMsg, Toast.LENGTH_SHORT).show() else onReply()
                }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Reply,
                        contentDescription = "Reply",
                        tint = if (isGuest) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                        if (isGuest) Toast.makeText(context, loginToInteractMsg, Toast.LENGTH_SHORT)
                            .show() else onToggleLike()
                    }.padding(4.dp)
                ) {
                    val icon = if (comment.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
                    val tint =
                        if (comment.isLiked) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant.let {
                            if (isGuest) it.copy(alpha = 0.3f) else it
                        }
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(R.string.player_like_action),
                        tint = tint,
                        modifier = Modifier.size(16.dp)
                    )
                    if (comment.likesCount > 0) {
                        Spacer(Modifier.width(4.dp)); Text(
                            text = formatNumber(comment.likesCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsSheetContent(track: Track, onClose: () -> Unit, onOpenComments: () -> Unit, viewModel: PlayerViewModel) {
    val context = LocalContext.current;
    val uriHandler = LocalUriHandler.current;
    val isLocalMode = viewModel.isLocalDetailsMode;
    val localPath = viewModel.localFilePathForDetails
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
    val displayFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    val releaseDateStr = remember(track) {
        try {
            val dateStr = track.releaseDate ?: track.createdAt; if (dateStr != null) {
                val date = dateFormat.parse(dateStr); displayFormat.format(date ?: Date())
            } else context.getString(R.string.detail_unknown)
        } catch (e: Exception) {
            context.getString(R.string.detail_unknown)
        }
    }
    val tags = remember(track.tagList) { parseSoundCloudTags(track.tagList) }
    var fileSizeStr by remember { mutableStateOf("") };
    var fileFormatStr by remember { mutableStateOf(context.getString(R.string.format_default)) };
    var cleanPathStr by remember { mutableStateOf("") };
    var bitrateStr by remember { mutableStateOf("") }

    LaunchedEffect(isLocalMode, localPath) {
        if (isLocalMode && !localPath.isNullOrEmpty()) {
            try {
                val file = File(localPath)
                if (file.exists()) {
                    val sizeMb = file.length() / (1024.0 * 1024.0); fileSizeStr =
                        context.getString(R.string.detail_file_size_formatted, sizeMb); fileFormatStr =
                        file.extension.uppercase(); cleanPathStr = file.absolutePath.replace(
                        "/storage/emulated/0",
                        context.getString(R.string.storage_internal_mem)
                    )
                    val durationSec = (track.durationMs ?: 0L) / 1000; if (durationSec > 0) {
                        val bitrate = ((file.length() * 8) / durationSec) / 1000; bitrateStr = "$bitrate kbps"
                    }
                } else if (localPath.startsWith("content://")) {
                    try {
                        val uri = Uri.parse(localPath);
                        val type = context.contentResolver.getType(uri); fileFormatStr =
                            type?.split("/")?.last()?.uppercase() ?: context.getString(R.string.format_fallback)
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            if (cursor.moveToFirst()) {
                                val sizeIndex =
                                    cursor.getColumnIndex(android.provider.OpenableColumns.SIZE); if (sizeIndex != -1) {
                                    val sizeBytes = cursor.getLong(sizeIndex);
                                    val sizeMb = sizeBytes / (1024.0 * 1024.0); fileSizeStr =
                                        context.getString(R.string.detail_file_size_formatted, sizeMb)
                                }
                            }
                        }
                        val rawPath = uri.path ?: localPath;
                        val decodedPath = try {
                            java.net.URLDecoder.decode(rawPath, "UTF-8")
                        } catch (e: Exception) {
                            rawPath
                        }
                        cleanPathStr = when {
                            decodedPath.contains("primary:") -> context.getString(R.string.storage_internal_mem) + "/" + decodedPath.substringAfter(
                                "primary:"
                            ); else -> decodedPath
                        }
                    } catch (e: Exception) {
                        cleanPathStr = localPath
                    }
                }
            } catch (e: Exception) {
                fileSizeStr = context.getString(R.string.detail_unknown); cleanPathStr = localPath ?: ""
            }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)
            ) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant).viewableCover(track.fullResArtwork),
                    contentScale = ContentScale.Crop
                ); Spacer(Modifier.width(16.dp)); Column {
                Text(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // updated verified row in details sheet
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    if (track.id > 0) {
                        onClose(); track.user?.id?.let { if (it > 0) viewModel.navigateToArtist(it) }
                    }
                }) {
                    Text(
                        text = track.user?.username ?: stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Rounded.Verified,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); Spacer(Modifier.height(16.dp))
        }
        if (isLocalMode) {
            item {
                Text(
                    text = stringResource(R.string.detail_file_info),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ); Spacer(Modifier.height(16.dp))
                val formatText = if (bitrateStr.isNotEmpty()) "$fileFormatStr • $bitrateStr" else fileFormatStr
                DetailInfoRow(stringResource(R.string.detail_format), formatText)
                if (fileSizeStr.isNotEmpty()) DetailInfoRow(stringResource(R.string.detail_size), fileSizeStr)
                DetailInfoRow(stringResource(R.string.detail_duration), makeTimeString(track.durationMs ?: 0L))
                Spacer(Modifier.height(16.dp)); HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(
                    alpha = 0.5f
                )
            ); Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.detail_location),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                ); Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (cleanPathStr.isNotEmpty()) cleanPathStr else localPath
                            ?: stringResource(R.string.storage_internal_mem),
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(Modifier.height(32.dp))
            }
        } else {
            item {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DetailStatItem(
                        icon = Icons.Rounded.PlayArrow,
                        value = formatNumber(track.playbackCount),
                        label = stringResource(R.string.detail_stats_plays)
                    ); DetailStatItem(
                    icon = Icons.Rounded.Favorite,
                    value = formatNumber(track.likesCount),
                    label = stringResource(R.string.detail_stats_likes),
                    onClick = { viewModel.navigateToTrackDetails(track.id, 0) }); DetailStatItem(
                    icon = Icons.Rounded.Repeat,
                    value = formatNumber(track.repostsCount),
                    label = stringResource(R.string.detail_stats_reposts),
                    onClick = { viewModel.navigateToTrackDetails(track.id, 1) })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                OutlinedButton(
                    onClick = { onClose(); viewModel.navigateToTrackDetails(track.id) },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shapes = ButtonDefaults.shapes(),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Icon(Icons.Rounded.Hub, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(
                    stringResource(R.string.detail_see_similar),
                    fontWeight = FontWeight.SemiBold
                )
                }; Spacer(Modifier.height(16.dp))
            }
            item {
                Button(
                    onClick = onOpenComments,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Comment,
                        null
                    ); Spacer(Modifier.width(12.dp)); Text(
                    text = stringResource(
                        R.string.detail_see_comments,
                        formatNumber(track.commentCount)
                    ), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                }; Spacer(Modifier.height(24.dp))
            }
            item {
                DetailInfoRow(
                    stringResource(R.string.detail_release_date),
                    releaseDateStr
                ); if (!track.genre.isNullOrBlank()) {
                DetailInfoRow(stringResource(R.string.detail_genre), track.genre)
            }; Spacer(Modifier.height(16.dp))
            }
            if (!track.description.isNullOrBlank()) {
                item {
                    Text(
                        stringResource(R.string.detail_description),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    ); Spacer(Modifier.height(8.dp)); ExpandableDescription(
                    text = track.description,
                    onUrlClick = { url -> uriHandler.openUri(url) },
                    onMentionClick = { username -> onClose(); viewModel.resolveAndNavigateToArtist(username) }); Spacer(
                    Modifier.height(24.dp)
                )
                }
            }
            if (tags.isNotEmpty()) {
                item {
                    Text(
                        stringResource(R.string.detail_tags),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    ); Spacer(Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            AssistChip(
                                onClick = { onClose(); viewModel.navigateToTag(tag) },
                                label = { Text("#${tag.uppercase()}") },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    labelColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = null,
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun DetailStatItem(icon: ImageVector, value: String, label: String, onClick: () -> Unit = {}) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick).padding(8.dp)
    ) {
        Icon(
            icon,
            null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        ); Spacer(Modifier.height(4.dp)); Text(
        value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
    ); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ); Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ExpandableDescription(text: String, onUrlClick: (String) -> Unit, onMentionClick: (String) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    val urlPattern = Pattern.compile("((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])")
    val mentionPattern = Pattern.compile("@[\\w-]+")
    val annotatedString = buildAnnotatedString {
        val fullText = text; append(fullText)
        val urlMatcher = urlPattern.matcher(fullText); while (urlMatcher.find()) {
        addStringAnnotation(
            tag = "URL",
            annotation = urlMatcher.group(),
            start = urlMatcher.start(),
            end = urlMatcher.end()
        ); addStyle(
            style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold),
            start = urlMatcher.start(),
            end = urlMatcher.end()
        )
    }
        val mentionMatcher = mentionPattern.matcher(fullText); while (mentionMatcher.find()) {
        addStringAnnotation(
            tag = "MENTION",
            annotation = mentionMatcher.group(),
            start = mentionMatcher.start(),
            end = mentionMatcher.end()
        ); addStyle(
            style = SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold),
            start = mentionMatcher.start(),
            end = mentionMatcher.end()
        )
    }
    }
    Column(modifier = Modifier.animateContentSize()) {
        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 20.sp
            ),
            maxLines = if (isExpanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onClick = { offset ->
                var isAnnotationClicked = false; annotatedString.getStringAnnotations(start = offset, end = offset)
                .firstOrNull()?.let { annotation ->
                    when (annotation.tag) {
                        "URL" -> {
                            onUrlClick(annotation.item); isAnnotationClicked = true
                        }; "MENTION" -> {
                        onMentionClick(annotation.item); isAnnotationClicked = true
                    }
                    }
                }
                if (!isAnnotationClicked) isExpanded = !isExpanded
            })
        if (text.length > 200) {
            Text(
                text = if (isExpanded) stringResource(R.string.detail_show_less) else stringResource(R.string.detail_show_more),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp).clickable { isExpanded = !isExpanded })
        }
    }
}

fun formatNumber(count: Int): String {
    if (count < 1000) return count.toString()
    val k = count / 1000.0;
    val m = count / 1000000.0
    return when {
        m >= 1.0 -> String.format(Locale.US, "%.1fM", m); k >= 1.0 -> String.format(
            Locale.US,
            "%.1fk",
            k
        ); else -> count.toString()
    }
}

fun getRelativeTime(dateStr: String, context: Context): String {
    try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        val date = format.parse(dateStr) ?: return ""
        val diff = System.currentTimeMillis() - date.time;
        val seconds = diff / 1000;
        val minutes = seconds / 60;
        val hours = minutes / 60;
        val days = hours / 24;
        val weeks = days / 7;
        val months = days / 30;
        val years = days / 365
        return when {
            seconds < 60 -> context.getString(R.string.time_now)
            minutes < 60 -> context.getString(R.string.time_minutes_ago, minutes)
            hours < 24 -> context.getString(R.string.time_hours_ago, hours)
            days < 7 -> context.getString(R.string.time_days_ago, days)
            weeks < 5 -> context.getString(R.string.time_weeks_ago, weeks)
            months < 12 -> context.getString(R.string.time_months_ago, months)
            years == 1L -> context.getString(R.string.time_one_year_ago)
            else -> context.getString(R.string.time_years_ago, years)
        }
    } catch (e: Exception) {
        return ""
    }
}

fun parseSoundCloudTags(tagList: String?): List<String> {
    if (tagList.isNullOrBlank()) return emptyList()
    val tags = mutableListOf<String>();
    val pattern = Pattern.compile("\"([^\"]*)\"|(\\S+)")
    val matcher = pattern.matcher(tagList)
    while (matcher.find()) {
        if (matcher.group(1) != null) tags.add(matcher.group(1)!!) else tags.add(matcher.group(2)!!)
    }
    return tags
}

private fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OldPlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val track = viewModel.currentTrack ?: return
    BackHandler(enabled = !viewModel.showLyricsSheet, onBack = onClose)

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
    val backgroundStyle = remember { prefs.getPlayerStyle() }
    var showLyricsButtonEnabled by remember { mutableStateOf(prefs.getShowLyricsButtonEnabled()) }
    DisposableEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences("player_state", Context.MODE_PRIVATE)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "show_lyrics_button_enabled") {
                showLyricsButtonEnabled = prefs.getShowLyricsButtonEnabled()
            }
        }
        sharedPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            sharedPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val isBlurMode = backgroundStyle == PlayerBackgroundStyle.BLUR
    val windowSizeInfo = rememberWindowSizeInfo()

    val mainContentColor = if (isBlurMode) Color.White else MaterialTheme.colorScheme.onBackground
    val subContentColor =
        if (isBlurMode) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val iconTint = if (isBlurMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    var showEffectsSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val animatedColor by animateColorAsState(
        targetValue = viewModel.backgroundColor,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "backgroundColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isBlurMode) Color.Black else MaterialTheme.colorScheme.background)
            .pointerInput(Unit) {
                detectTapGestures(onTap = {})
            }
    ) {
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
                        modifier = Modifier.fillMaxSize().blur(80.dp).alpha(0.6f)
                    )
                }
            }

            PlayerBackgroundStyle.GRADIENT -> {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                animatedColor.copy(alpha = 0.7f),
                                animatedColor.copy(alpha = 0.3f),
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
                )
            }

            PlayerBackgroundStyle.THEME -> {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                    PlayerHeader(
                        onClose = onClose,
                        viewModel = viewModel,
                        contentColor = mainContentColor,
                        subContentColor = subContentColor,
                        accentColor = animatedColor,
                        onCollapseToSide = if (windowSizeInfo.showTabletDock) {
                            {
                                viewModel.isPlayerExpanded = false
                                viewModel.isSidePlayerOpen = true
                            }
                        } else null
                    )
                }
                Spacer(modifier = Modifier.weight(1f))

                val pagerState = androidx.compose.foundation.pager.rememberPagerState(
                    initialPage = viewModel.currentQueueIndex.coerceAtLeast(0),
                    pageCount = { viewModel.queueState.size.takeIf { it > 0 } ?: 1 }
                )

                LaunchedEffect(viewModel.currentQueueIndex) {
                    if (viewModel.currentQueueIndex >= 0 && viewModel.currentQueueIndex != pagerState.currentPage && viewModel.currentQueueIndex < pagerState.pageCount) {
                        try {
                            pagerState.animateScrollToPage(viewModel.currentQueueIndex)
                        } catch (e: Exception) {
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
                val showLyrics = viewModel.showInlineLyrics
                val lyricsAlpha by animateFloatAsState(
                    targetValue = if (showLyrics) 1f else 0f,
                    tween(400),
                    label = "lyricsAlpha"
                )
                val coverAlpha by animateFloatAsState(
                    targetValue = if (showLyrics) 0f else 1f,
                    tween(400),
                    label = "coverAlpha"
                )

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(modifier = Modifier.fillMaxWidth().alpha(coverAlpha).zIndex(if (showLyrics) 0f else 1f)) {
                        if (viewModel.queueState.isNotEmpty()) {
                            androidx.compose.foundation.pager.HorizontalPager(
                                state = pagerState,
                                modifier = Modifier.fillMaxWidth(),
                                pageSpacing = 16.dp,
                                contentPadding = PaddingValues(24.dp)
                            ) { page ->
                                val pageTrack = viewModel.queueState.getOrNull(page) ?: track
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = animatedColor)
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    AsyncImage(
                                        model = pageTrack.fullResArtwork,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(24.dp)
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = animatedColor)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                AsyncImage(
                                    model = track.fullResArtwork,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().alpha(lyricsAlpha).zIndex(if (showLyrics) 1f else 0f)) {
                        if (lyricsAlpha > 0f) {
                            Box(modifier = Modifier.padding(24.dp).fillMaxWidth().aspectRatio(1f)) {
                                InlineLyricsContent(viewModel = viewModel)
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {

                        Column(
                            modifier = Modifier.weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            PremiumMarqueeText(
                                text = track.title ?: stringResource(R.string.untitled_track),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = mainContentColor,
                                edgeGradientWidth = 24.dp,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    track.user?.id?.let {
                                        if (it > 0) viewModel.navigateToArtist(
                                            it
                                        )
                                    }
                                }
                            ) {
                                PremiumMarqueeText(
                                    text = track.user?.username
                                        ?: stringResource(R.string.unknown_artist),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = subContentColor,
                                    edgeGradientWidth = 16.dp,
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
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AnimatedVisibility(
                                visible = viewModel.hasLyrics && showLyricsButtonEnabled,
                                enter = fadeIn(animationSpec = tween(400)),
                                exit = fadeOut(animationSpec = tween(200))
                            ) {
                                IconButton(
                                    onClick = { viewModel.openLyrics() },
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Description,
                                        contentDescription = stringResource(R.string.player_lyrics),
                                        tint = if (viewModel.showInlineLyrics) animatedColor else iconTint.copy(alpha = 0.8f), // Active color
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }

                            val view = LocalView.current
                            IconButton(
                                onClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    viewModel.toggleLike()
                                },
                                modifier = Modifier.size(44.dp)
                            ) {
                                val targetColor =
                                    if (viewModel.isLiked) animatedColor else iconTint
                                val heartColor by animateColorAsState(
                                    targetValue = targetColor,
                                    animationSpec = tween(300),
                                    label = "color"
                                )

                                AnimatedContent(
                                    targetState = viewModel.isLiked,
                                    transitionSpec = {
                                        if (targetState) {
                                            (fadeIn(tween(300)) + scaleIn(
                                                initialScale = 0.7f,
                                                animationSpec = tween(
                                                    300,
                                                    easing = LinearOutSlowInEasing
                                                )
                                            ))
                                                .togetherWith(fadeOut(tween(200)))
                                        } else {
                                            (fadeIn(tween(300)) + scaleIn(
                                                initialScale = 1.0f,
                                                animationSpec = tween(300)
                                            ))
                                                .togetherWith(
                                                    fadeOut(tween(200)) + scaleOut(
                                                        targetScale = 0.7f,
                                                        animationSpec = tween(200)
                                                    )
                                                )
                                        }
                                    },
                                    label = "LikeAnimation"
                                ) { isLiked ->
                                    Icon(
                                        imageVector = if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                                        contentDescription = stringResource(R.string.player_like_action),
                                        tint = heartColor,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.weight(1f))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    OldPlayerProgress(viewModel, mainContentColor)
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    OldPlayerControls(
                        viewModel = viewModel,
                        animatedMainColor = animatedColor,
                        contentColorOverride = mainContentColor,
                        onEffectsClick = { showEffectsSheet = true },
                        onQueueClick = { showQueueSheet = true }
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
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
                Spacer(Modifier.height(16.dp))
            }
        }

        SleepTimerDialog(viewModel)
    }
}

@Composable
fun OldPlayerProgress(viewModel: PlayerViewModel, textColor: Color) {
    val view = LocalView.current
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }

    var lastValidDuration by remember { mutableFloatStateOf(180000f) }
    if (viewModel.duration > 1000) {
        lastValidDuration = viewModel.duration.toFloat()
    }
    val totalDuration = if (viewModel.duration > 1000) viewModel.duration.toFloat() else lastValidDuration

    val rawPosition = viewModel.currentPosition.toFloat()

    var currentTrackId by remember { mutableStateOf(viewModel.currentTrack?.id) }
    var isTransitioning by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel.currentTrack?.id) {
        if (viewModel.currentTrack?.id != currentTrackId) {
            currentTrackId = viewModel.currentTrack?.id
            isTransitioning = true
            delay(1500)
            isTransitioning = false
        }
    }

    LaunchedEffect(rawPosition) {
        if (isTransitioning && rawPosition < 2000f) {
            isTransitioning = false
        }
    }

    val targetPos = when {
        isTransitioning -> 0f
        rawPosition > totalDuration -> 0f
        else -> rawPosition
    }

    val progressState = remember { Animatable(0f) }
    val sliderPosition = if (isDragging) dragPosition else progressState.value
    LaunchedEffect(targetPos, isDragging) {
        if (isDragging) {
            progressState.snapTo(dragPosition)
        } else {
            val currentVal = progressState.value
            val diff = targetPos - currentVal
            val absDiff = kotlin.math.abs(diff)

            if (targetPos < 1000f && currentVal > 2000f) {
                progressState.animateTo(0f, tween(600, easing = FastOutSlowInEasing))
            } else if (absDiff > 2000f) {
                progressState.animateTo(targetPos, tween(300, easing = FastOutSlowInEasing))
            } else if (diff > 0) {
                progressState.animateTo(targetPos, tween(1000, easing = LinearEasing))
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderPosition.coerceIn(0f, totalDuration),
            valueRange = 0f..totalDuration,
            onValueChange = {
                isDragging = true
                dragPosition = it
                viewModel.updateScrubPosition(it.toLong())
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            },
            onValueChangeFinished = {
                viewModel.seekTo(dragPosition.toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = textColor,
                activeTrackColor = textColor,
                inactiveTrackColor = textColor.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = makeTimeString(if (isDragging) dragPosition.toLong() else progressState.value.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
            Text(
                text = makeTimeString(totalDuration.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OldPlayerControls(
    viewModel: PlayerViewModel,
    onEffectsClick: () -> Unit,
    onQueueClick: () -> Unit,
    animatedMainColor: Color = MaterialTheme.colorScheme.primary,
    contentColorOverride: Color
) {
    val buttonWidth by animateDpAsState(
        targetValue = if (viewModel.isPlaying) 110.dp else 72.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "width"
    )
    val buttonColor = if (viewModel.isPlaying) animatedMainColor else contentColorOverride.copy(alpha = 0.2f)
    val isButtonLight = buttonColor.luminance() > 0.4f
    val playIconColor = if (viewModel.isPlaying) {
        if (isButtonLight) Color(0xFF1D1B20) else Color.White
    } else contentColorOverride

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onEffectsClick) {
            Icon(
                Icons.Default.Equalizer,
                stringResource(R.string.player_effects),
                tint = contentColorOverride.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { viewModel.smartPrevious() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = contentColorOverride, modifier = Modifier.size(36.dp))
            }
            Box(
                modifier = Modifier.height(72.dp).width(buttonWidth).clip(CircleShape).background(buttonColor)
                    .clickable { viewModel.togglePlayPause() }, contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = Pair(viewModel.isLoading, viewModel.isPlaying),
                    transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) },
                    label = "playPauseLoading"
                ) { (isLoading, isPlaying) ->
                    if (isLoading) {
                        LoadingIndicator(color = playIconColor, modifier = Modifier.size(32.dp))
                    } else {
                        Icon(
                            imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            tint = playIconColor,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
            IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipNext, null, tint = contentColorOverride, modifier = Modifier.size(36.dp))
            }
        }
        IconButton(onClick = onQueueClick) {
            Icon(
                Icons.AutoMirrored.Rounded.QueueMusic,
                stringResource(R.string.player_queue),
                tint = contentColorOverride.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LandscapePlayerView(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    mainContentColor: Color,
    subContentColor: Color,
    iconTint: Color,
    animatedColor: Color,
    isBlurMode: Boolean
) {
    val track = viewModel.currentTrack ?: return
    var selectedRightTab by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            val windowSizeInfo = rememberWindowSizeInfo()
            PlayerHeader(
                onClose = onClose,
                viewModel = viewModel,
                contentColor = mainContentColor,
                subContentColor = subContentColor,
                accentColor = animatedColor,
                onCollapseToSide = if (windowSizeInfo.showTabletDock) {
                    {
                        viewModel.isPlayerExpanded = false
                        viewModel.isSidePlayerOpen = true
                    }
                } else null
            )

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(vertical = 8.dp)
                    .aspectRatio(1f)
                    .shadow(
                        16.dp,
                        RoundedCornerShape(16.dp),
                        spotColor = if (isBlurMode) Color.Black else animatedColor
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PremiumMarqueeText(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = mainContentColor,
                    edgeGradientWidth = 16.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = track.user?.username ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = subContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                PlayerProgress(viewModel, mainContentColor)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.toggleShuffle() }) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (viewModel.shuffleEnabled) animatedColor else iconTint.copy(alpha = 0.7f)
                    )
                }
                IconButton(onClick = { viewModel.smartPrevious() }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = "Previous",
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = animatedColor,
                    modifier = Modifier.size(52.dp)
                ) {
                    IconButton(onClick = { viewModel.togglePlayPause() }) {
                        Icon(
                            imageVector = if (viewModel.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                IconButton(onClick = { viewModel.playNext() }) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = "Next",
                        tint = iconTint,
                        modifier = Modifier.size(32.dp)
                    )
                }
                IconButton(onClick = { viewModel.toggleRepeatMode() }) {
                    Icon(
                        imageVector = when (viewModel.repeatMode) {
                            RepeatMode.ONE -> Icons.Rounded.RepeatOne
                            else -> Icons.Rounded.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (viewModel.repeatMode != RepeatMode.NONE) animatedColor else iconTint.copy(alpha = 0.7f)
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.85f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                SecondaryTabRow(
                    selectedTabIndex = selectedRightTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    Tab(
                        selected = selectedRightTab == 0,
                        onClick = { selectedRightTab = 0 },
                        text = { Text(stringResource(R.string.player_queue), fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedRightTab == 1,
                        onClick = { selectedRightTab = 1 },
                        text = { Text(stringResource(R.string.player_lyrics), fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Rounded.Description, null, modifier = Modifier.size(18.dp)) }
                    )
                    Tab(
                        selected = selectedRightTab == 2,
                        onClick = { selectedRightTab = 2 },
                        text = { Text(stringResource(R.string.menu_details), fontWeight = FontWeight.Bold) },
                        icon = { Icon(Icons.Rounded.Info, null, modifier = Modifier.size(18.dp)) }
                    )
                }

                Spacer(Modifier.height(8.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedRightTab) {
                        0 -> PlayerQueueSideContent(viewModel)
                        1 -> InlineLyricsContent(viewModel)
                        2 -> PlayerTrackDetailsSideContent(viewModel, track)
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerQueueSideContent(viewModel: PlayerViewModel) {
    val queueState = viewModel.queueState
    val currentTrack = viewModel.currentTrack
    val listState = rememberLazyListState()

    LaunchedEffect(currentTrack) {
        if (currentTrack != null && queueState.isNotEmpty()) {
            val index = queueState.indexOfFirst { it.id == currentTrack.id }
            if (index >= 0) {
                listState.scrollToItem(kotlin.math.max(0, index - 1))
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(queueState, key = { index, item -> "${item.id}_$index" }) { index, track ->
            val isCurrent = track.id == currentTrack?.id
            Surface(
                onClick = { viewModel.skipToQueueItem(index) },
                shape = RoundedCornerShape(12.dp),
                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = track.title ?: stringResource(R.string.untitled_track),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            ),
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = track.user?.username ?: stringResource(R.string.unknown_artist),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerTrackDetailsSideContent(viewModel: PlayerViewModel, track: Track) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.menu_details),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.unknown_artist),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        track.user?.username ?: "-",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        stringResource(R.string.untitled_track),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        makeTimeString(track.durationMs ?: 0L),
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.toggleLike() },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (viewModel.isLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (viewModel.isLiked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) {
                Icon(
                    if (viewModel.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(if (viewModel.isLiked) stringResource(R.string.filter_liked) else stringResource(R.string.player_like_action))
            }

            OutlinedButton(
                onClick = { viewModel.navigateToTrackDetails(track.id, 0) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.menu_details))
            }
        }
    }
}

@Composable
fun PhoneLandscapePlayerView(
    viewModel: PlayerViewModel,
    onClose: () -> Unit,
    onEffectsClick: () -> Unit,
    onQueueClick: () -> Unit,
    mainContentColor: Color,
    subContentColor: Color,
    iconTint: Color,
    animatedColor: Color,
    isBlurMode: Boolean
) {
    val track = viewModel.currentTrack ?: return

    Row(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(0.45f).fillMaxHeight(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.btn_close), tint = mainContentColor)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .shadow(
                        24.dp,
                        RoundedCornerShape(24.dp),
                        spotColor = if (isBlurMode) Color.Black else animatedColor
                    )
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = track.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Column(
            modifier = Modifier.weight(0.55f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.size(48.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val contextStr = viewModel.currentContext
                    Text(
                        text = stringResource(R.string.player_playing_now),
                        style = MaterialTheme.typography.labelMedium,
                        color = mainContentColor,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    if (contextStr != null) {
                        Text(
                            text = contextStr.displayText,
                            style = MaterialTheme.typography.labelSmall,
                            color = subContentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                IconButton(onClick = { viewModel.currentTrack?.let { viewModel.showTrackOptions(it, fromPlayer = true) } }) {
                    Icon(Icons.Default.MoreVert, stringResource(R.string.btn_options), tint = mainContentColor)
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PremiumMarqueeText(
                    text = track.title ?: stringResource(R.string.untitled_track),
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = mainContentColor,
                    edgeGradientWidth = 24.dp,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = track.user?.username ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.titleMedium,
                    color = subContentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                PlayerProgress(viewModel, mainContentColor)
            }

            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                PlayerControls(
                    viewModel = viewModel,
                    animatedMainColor = animatedColor,
                    contentColorOverride = mainContentColor,
                    onEffectsClick = onEffectsClick,
                    onQueueClick = onQueueClick
                )
            }
        }
    }
}
