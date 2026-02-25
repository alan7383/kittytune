package com.alananasss.kittytune.ui.player

import android.content.Context
import android.net.Uri
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Comment
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import android.content.SharedPreferences
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.domain.Comment
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.utils.makeTimeString
import com.alananasss.kittytune.ui.utils.fadingEdge
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clipToBounds
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.LayoutDirection
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.ui.player.lyrics.WrongLyricsButton
import android.view.WindowManager
import android.app.Activity
import android.content.ContextWrapper

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

    val activeIndex = remember(currentPosition, lyrics) {
        val exactMatch = lyrics.indexOfFirst { currentPosition >= it.startTime && currentPosition < it.endTime }

        if (exactMatch != -1) {
            exactMatch
        } else {
            lyrics.indexOfLast { currentPosition >= it.startTime }
        }
    }

    LaunchedEffect(activeIndex, currentPosition) {
        if (!listState.isScrollInProgress) {
            if (activeIndex >= 0) {
                listState.animateScrollToItem(index = activeIndex, scrollOffset = 0)
            } else {
                val firstLineTime = lyrics.firstOrNull()?.startTime ?: 0L
                if (currentPosition < firstLineTime) {
                    listState.animateScrollToItem(0)
                }
            }
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
                val targetScale = if (isActive) 1.05f else 0.95f
                val targetAlpha = if (isActive) 1f else 0.5f
                val targetBlur = if (isActive) 0.dp else 1.dp

                val scale by animateFloatAsState(targetScale, tween(400), label = "scale")
                val alpha by animateFloatAsState(targetAlpha, tween(400), label = "alpha")

                Text(
                    text = line.text,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.4).sp
                    ),
                    color = Color.White,
                    textAlign = alignment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .blur(targetBlur)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.seekTo(line.startTime) }
                )
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
                bottom = 120.dp,
                start = 24.dp,
                end = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            items(lines) { line ->
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
    val subContentColor = if (isBlurMode) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
    val iconTint = if (isBlurMode) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
    val queueSheetState = rememberBottomSheetScaffoldState()
    var showEffectsSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isQueueVisible = queueSheetState.bottomSheetState.targetValue == SheetValue.Expanded ||
            queueSheetState.bottomSheetState.currentValue == SheetValue.Expanded

    val isQueueOpen = queueSheetState.bottomSheetState.currentValue == SheetValue.Expanded

    BackHandler(enabled = isQueueOpen) {
        scope.launch { queueSheetState.bottomSheetState.partialExpand() }
    }

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

        BottomSheetScaffold(
            scaffoldState = queueSheetState,
            sheetPeekHeight = 0.dp,
            sheetContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetSwipeEnabled = false,
            sheetDragHandle = null,
            containerColor = Color.Transparent,
            sheetContent = {
                QueueContent(
                    viewModel = viewModel,
                    isQueueOpen = isQueueVisible,
                    onCloseQueue = { scope.launch { queueSheetState.bottomSheetState.partialExpand() } }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerHeader(onClose, viewModel, mainContentColor, subContentColor, animatedColor)
                    Spacer(modifier = Modifier.weight(1f))

                    AnimatedContent(
                        targetState = viewModel.showInlineLyrics,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) togetherWith
                                    fadeOut(animationSpec = tween(400))
                        },
                        label = "ArtworkLyricsToggle",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    ) { showLyrics ->
                        if (showLyrics) {
                            // Conteneur transparent pour les paroles (Effet InnerTune)
                            Box(modifier = Modifier.fillMaxSize()) {
                                InlineLyricsContent(viewModel = viewModel)
                            }
                        } else {
                            // Conteneur stylisé (Carte) uniquement pour la pochette
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .shadow(24.dp, RoundedCornerShape(20.dp), spotColor = if (isBlurMode) Color.Black else animatedColor)
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
                    Spacer(modifier = Modifier.height(32.dp))

                    Column(modifier = Modifier.fillMaxWidth()) {

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
                                            tint = if (viewModel.showInlineLyrics) animatedColor else iconTint.copy(alpha = 0.8f), // Couleur active
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

                    PlayerProgress(viewModel, mainContentColor)

                    Spacer(modifier = Modifier.height(16.dp))

                    PlayerControls(
                        viewModel = viewModel,
                        animatedMainColor = animatedColor,
                        contentColorOverride = mainContentColor,
                        onEffectsClick = { showEffectsSheet = true },
                        onQueueClick = { scope.launch { if (queueSheetState.bottomSheetState.currentValue == SheetValue.Expanded) queueSheetState.bottomSheetState.partialExpand() else queueSheetState.bottomSheetState.expand() } }
                    )

                    Spacer(modifier = Modifier.weight(1f))
                }

                if (isQueueOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .zIndex(2f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                scope.launch { queueSheetState.bottomSheetState.partialExpand() }
                            }
                    )
                }
            }
        }

        if (showEffectsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showEffectsSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                AudioControlDock(viewModel)
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun PlayerHeader(
    onClose: () -> Unit,
    viewModel: PlayerViewModel,
    contentColor: Color,
    subContentColor: Color,
    accentColor: Color
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
        IconButton(onClick = onClose) {
            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.btn_close), tint = contentColor)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
        ) {
            Text(
                stringResource(R.string.player_playing_now),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp, shadow = textShadow),
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
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline),
                    trailingIcon = { Text(text = "${caption.length}/$maxChars", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 8.dp)) }
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
            confirmButton = { TextButton(onClick = { DownloadManager.deleteTrack(track.id); showDeleteDialog = false; viewModel.showMenuSheet = false }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showRepostDialog) {
        RepostDialog(onDismiss = { showRepostDialog = false }, onConfirm = { caption -> viewModel.repostTrack(track, caption); showRepostDialog = false; viewModel.showMenuSheet = false })
    }

    if (showDeleteRepostConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteRepostConfirm = false },
            title = { Text(stringResource(R.string.dialog_repost_delete_title)) },
            text = { Text(stringResource(R.string.dialog_repost_delete_msg)) },
            confirmButton = { TextButton(onClick = { viewModel.deleteRepost(track.id); showDeleteRepostConfirm = false; viewModel.showMenuSheet = false }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.btn_delete)) } },
            dismissButton = { TextButton(onClick = { showDeleteRepostConfirm = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp).padding(horizontal = 8.dp)) {
            AsyncImage(model = track.fullResArtwork, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text = track.title ?: stringResource(R.string.untitled_track), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = track.user?.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        val gridItems = remember(viewModel.isMenuContextFromPlayer, isLocalFile, viewModel.menuContextPlaylistId, isReposted, track.source) {
            mutableListOf<DockOptionItem>().apply {
                if (viewModel.isMenuContextFromPlayer) {
                    add(DockOptionItem(Icons.Rounded.Shuffle, context.getString(R.string.menu_shuffle)) { viewModel.toggleShuffle(); viewModel.showMenuSheet = false })
                    add(DockOptionItem(Icons.Rounded.Repeat, context.getString(R.string.menu_repeat)) { viewModel.toggleRepeatMode() })
                }
                if (!viewModel.isMenuContextFromPlayer) {
                    add(DockOptionItem(Icons.AutoMirrored.Rounded.PlaylistPlay, context.getString(R.string.menu_play_next)) { viewModel.insertNext(listOf(track)); viewModel.showMenuSheet = false })
                    add(DockOptionItem(Icons.AutoMirrored.Rounded.QueueMusic, context.getString(R.string.menu_add_queue)) { viewModel.addToQueue(listOf(track)); viewModel.showMenuSheet = false })
                }
                if (track.source != "youtube" && !isLocalFile) {
                    add(DockOptionItem(Icons.AutoMirrored.Rounded.Comment, context.getString(R.string.menu_comments)) { viewModel.openComments(track) })
                }
                if (track.source != "youtube" && !isLocalFile) {
                    if (isReposted) {
                        add(DockOptionItem(Icons.Rounded.Repeat, context.getString(R.string.menu_reposted)) { showDeleteRepostConfirm = true })
                    } else {
                        add(DockOptionItem(Icons.Rounded.Repeat, context.getString(R.string.menu_repost)) { showRepostDialog = true })
                    }
                }
                if (track.source != "youtube") {
                    add(DockOptionItem(Icons.Rounded.Info, context.getString(R.string.menu_details)) { viewModel.openTrackDetails(track) })
                }
                add(DockOptionItem(Icons.Rounded.Description, context.getString(R.string.player_lyrics)) { viewModel.openLyrics(track, forceSheet = true)  })
                add(DockOptionItem(Icons.Default.Add, context.getString(R.string.menu_add_playlist)) { viewModel.showMenuSheet = false; viewModel.showAddToPlaylistSheet = true })
                if (track.source != "youtube" && !isLocalFile) {
                    add(DockOptionItem(Icons.Default.Person, context.getString(R.string.menu_go_artist)) { track.user?.id?.let { viewModel.navigateToArtist(it) } })
                }
                if (!isLocalFile) {
                    add(DockOptionItem(Icons.Rounded.Radio, context.getString(R.string.menu_track_radio)) {
                        if (track.source == "youtube") {
                            viewModel.startYoutubeRadio(track)
                        } else {
                            viewModel.startRadioFromTrack(track)
                        }
                    })
                }
                if (!isLocalFile) {
                    add(DockOptionItem(Icons.Outlined.Share, context.getString(R.string.btn_share)) { viewModel.shareTrack(track) })
                }
                if (viewModel.menuContextPlaylistId != null && viewModel.menuContextPlaylistId!! < 0) {
                    add(DockOptionItem(Icons.Outlined.Delete, context.getString(R.string.menu_remove)) { viewModel.removeFromContextPlaylist(viewModel.menuContextPlaylistId!!, track) })
                }
            }
        }

        LazyVerticalGrid(columns = GridCells.Fixed(3), verticalArrangement = Arrangement.spacedBy(24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
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
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { item.onClick() }) {
                    Icon(item.icon, null, modifier = Modifier.size(32.dp), tint = tint)
                    Spacer(Modifier.height(8.dp))
                    Text(text = text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = tint)
                }
            }
            if (!isLocalFile) {
                item {
                    val trackId = track.id
                    val isDownloading = DownloadManager.isTrackDownloading(trackId)
                    val downloadProgressVal = downloadProgress[trackId]
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        if (isDownloaded) showDeleteDialog = true else if (isDownloading) DownloadManager.cancelDownload(trackId) else viewModel.downloadTrack(track)
                    }) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(32.dp)) {
                            if (isDownloading) {
                                val animatedProgress by animateFloatAsState(targetValue = (downloadProgressVal ?: 0) / 100f, label = "progress")
                                CircularProgressIndicator(progress = { animatedProgress }, modifier = Modifier.fillMaxSize(), strokeWidth = 3.dp)
                                Icon(Icons.Outlined.Cancel, null, modifier = Modifier.size(18.dp))
                            } else {
                                val icon = if (isDownloaded) Icons.Default.Delete else Icons.Rounded.Download
                                val tint = if (isDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                Icon(icon, null, modifier = Modifier.fillMaxSize(), tint = tint)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        val textLabel = if (isDownloaded) stringResource(R.string.btn_delete) else if (isDownloading) stringResource(R.string.btn_cancel) else stringResource(R.string.btn_download)
                        val textColor = if (isDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                        Text(textLabel, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = textColor)
                    }
                }
            }
        }
    }
}


@Composable
fun AddToPlaylistContent(viewModel: PlayerViewModel) {
    val singleTrack = viewModel.trackForMenu
    val bulkTracks = viewModel.tracksToAddInBulk
    if (singleTrack == null && bulkTracks == null) return
    var showCreateInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(if (bulkTracks != null) stringResource(R.string.add_to_playlist_title_multi, bulkTracks.size) else stringResource(R.string.add_to_playlist_title_single), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 16.dp))
        if (showCreateInput) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(value = newName, onValueChange = { newName = it }, label = { Text(stringResource(R.string.lib_create_playlist_hint)) }, modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if(newName.isNotBlank()) { if (bulkTracks != null) viewModel.createAndAddTracksToPlaylist(newName, bulkTracks) else if (singleTrack != null) viewModel.createAndAddToPlaylist(newName, singleTrack) } }) { Text(stringResource(R.string.btn_ok)) }
            }
            Spacer(Modifier.height(16.dp))
        } else {
            Surface(onClick = { showCreateInput = true }, shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) { Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(Modifier.width(8.dp)); Text(stringResource(R.string.add_to_playlist_new), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold) }
            }
            Spacer(Modifier.height(16.dp))
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            itemsIndexed(items = viewModel.userPlaylists) { _, playlist ->
                Row(modifier = Modifier.fillMaxWidth().clickable { if (bulkTracks != null) viewModel.addTracksToPlaylist(playlist.id, bulkTracks) else if (singleTrack != null) viewModel.addToPlaylist(playlist.id, singleTrack) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(model = playlist.localCoverPath ?: playlist.artworkUrl.ifEmpty { "https://picsum.photos/200" }, contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(16.dp))
                    Column { Text(playlist.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold); Text(stringResource(R.string.playlist_num_tracks, playlist.trackCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@Composable
fun QueueContent(viewModel: PlayerViewModel, isQueueOpen: Boolean, onCloseQueue: () -> Unit) {
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

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.6f)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) {
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        if (dragAmount > 10) onCloseQueue()
                    }
                }
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.player_queue),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.padding(horizontal = 24.dp)
            )
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
                    val backgroundColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer

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
                                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                                }
                            }
                        }

                        Icon(
                            imageVector = Icons.Rounded.DragHandle,
                            contentDescription = stringResource(R.string.desc_move),
                            tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
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
    val buttonWidth by animateDpAsState(targetValue = if (viewModel.isPlaying) 110.dp else 72.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "width")
    val buttonColor = if (viewModel.isPlaying) animatedMainColor else contentColorOverride.copy(alpha = 0.2f)
    val isButtonLight = buttonColor.luminance() > 0.4f
    val playIconColor = if (viewModel.isPlaying) { if (isButtonLight) Color(0xFF1D1B20) else Color.White } else contentColorOverride

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEffectsClick) {
            Icon(Icons.Default.Equalizer, stringResource(R.string.player_effects), tint = contentColorOverride.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { viewModel.smartPrevious() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipPrevious, null, tint = contentColorOverride, modifier = Modifier.size(36.dp))
            }
            Box(modifier = Modifier.height(72.dp).width(buttonWidth).clip(CircleShape).background(buttonColor).clickable { viewModel.togglePlayPause() }, contentAlignment = Alignment.Center) {
                if (viewModel.isLoading) CircularProgressIndicator(color = playIconColor, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                else AnimatedContent(targetState = viewModel.isPlaying, transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) }, label = "icon") { isPlaying ->
                    Icon(imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = playIconColor, modifier = Modifier.size(32.dp))
                }
            }
            IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.SkipNext, null, tint = contentColorOverride, modifier = Modifier.size(36.dp))
            }
        }
        IconButton(onClick = onQueueClick) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.player_queue), tint = contentColorOverride.copy(alpha = 0.7f), modifier = Modifier.size(28.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AudioControlDock(viewModel: PlayerViewModel) {
    val view = LocalView.current; val isPrecise = viewModel.isPreciseSpeedEnabled; var showRainVolumeDialog by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(stringResource(R.string.player_audio_settings), style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 24.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Speed, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Text(text = "${viewModel.effectsState.speed}x", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) }
                val isPitchActive = viewModel.effectsState.isPitchEnabled; val pitchContainerColor by animateColorAsState(targetValue = if (isPitchActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, label = "pitchContainer"); val pitchContentColor by animateColorAsState(targetValue = if (isPitchActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "pitchContent")
                Surface(onClick = { viewModel.togglePitchEnabled(!isPitchActive) }, shape = CircleShape, color = pitchContainerColor, border = if (isPitchActive) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), contentColor = pitchContentColor) { Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AnimatedVisibility(visible = isPitchActive) { Row { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)) } }; Text(stringResource(R.string.player_pitch), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold) } }
            }
            Spacer(Modifier.height(24.dp)); Slider(value = viewModel.effectsState.speed, onValueChange = { if (it != viewModel.effectsState.speed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); viewModel.setCustomSpeed(it) }, valueRange = 0.5f..2.0f, steps = if (isPrecise) 29 else 14, modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(32.dp)); Text(stringResource(R.string.player_special_effects), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp, start = 4.dp))
        FlowRow(maxItemsInEachRow = 2, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            val itemModifier = Modifier.weight(1f).fillMaxWidth()
            FxTile(stringResource(R.string.effect_bass_boost), Icons.Rounded.Bolt, viewModel.effectsState.isBassBoostEnabled, { viewModel.toggleBassBoost() }, null, itemModifier)
            FxTile(stringResource(R.string.effect_8d), Icons.Rounded.SurroundSound, viewModel.effectsState.is8DEnabled, { viewModel.toggle8D() }, null, itemModifier, MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.onTertiary)
            FxTile(stringResource(R.string.effect_muffled), Icons.Rounded.BlurOn, viewModel.effectsState.isMuffledEnabled, { viewModel.toggleMuffled() }, null, itemModifier, MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.onSecondary)
            FxTile(stringResource(R.string.effect_reverb), Icons.Rounded.GraphicEq, viewModel.effectsState.isReverbEnabled, { viewModel.toggleReverb() }, null, itemModifier)
            FxTile(stringResource(R.string.effect_rain), Icons.Rounded.WaterDrop, viewModel.effectsState.isRainEnabled, { viewModel.toggleRain() }, { showRainVolumeDialog = true }, itemModifier, Color(0xFF81D4FA), Color(0xFF004BA0))
        }
        Spacer(Modifier.height(32.dp))
        if (showRainVolumeDialog) { AlertDialog(onDismissRequest = { showRainVolumeDialog = false }, icon = { Icon(Icons.Rounded.WaterDrop, null) }, title = { Text(stringResource(R.string.effect_rain)) }, text = { Column { Text(stringResource(R.string.label_volume, (viewModel.effectsState.rainVolume * 100).toInt()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.align(Alignment.CenterHorizontally)); Spacer(Modifier.height(16.dp)); Slider(value = viewModel.effectsState.rainVolume, onValueChange = { viewModel.setRainVolume(it) }, valueRange = 0f..1f) } }, confirmButton = { TextButton(onClick = { showRainVolumeDialog = false }) { Text(stringResource(R.string.btn_ok)) } }) }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FxTile(label: String, icon: ImageVector, isActive: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)? = null, modifier: Modifier = Modifier, activeColor: Color = MaterialTheme.colorScheme.primary, activeContentColor: Color = MaterialTheme.colorScheme.onPrimary) {
    val containerColor by animateColorAsState(targetValue = if (isActive) activeColor else MaterialTheme.colorScheme.surfaceContainerHigh, animationSpec = tween(300), label = "containerColor")
    val contentColor by animateColorAsState(targetValue = if (isActive) activeContentColor else MaterialTheme.colorScheme.onSurface, animationSpec = tween(300), label = "contentColor")
    val cornerRadius by animateDpAsState(targetValue = if (isActive) 100.dp else 20.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "cornerRadius")
    val iconScale by animateFloatAsState(targetValue = if (isActive) 1.2f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium), label = "iconScale")
    Surface(modifier = modifier.height(84.dp).clip(RoundedCornerShape(cornerRadius)).combinedClickable(onClick = onClick, onLongClick = onLongClick), shape = RoundedCornerShape(cornerRadius), color = containerColor) { Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) { Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }); Spacer(modifier = Modifier.height(8.dp)); Text(text = label, style = MaterialTheme.typography.labelMedium, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
}

@Composable
fun DockButton(label: String, icon: ImageVector, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "Color")
    val contentColor by animateColorAsState(if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "ContentColor")
    val cornerRadius by animateDpAsState(targetValue = if (isActive) 100.dp else 20.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "Corner")
    Surface(onClick = onClick, modifier = modifier.height(80.dp), shape = RoundedCornerShape(cornerRadius), color = containerColor) { Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) { val iconScale by animateFloatAsState(if (isActive) 1.1f else 1f, label = "Scale"); Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale }); Spacer(modifier = Modifier.height(4.dp)); Text(text = label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium) } }
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


    LaunchedEffect(replyingTo) { if (replyingTo != null) { val username = replyingTo.user?.username ?: ""; commentText = "@$username: " } }
    val isPosting = viewModel.isPostingComment

    if (commentToDelete != null) {
        AlertDialog(onDismissRequest = { commentToDelete = null }, title = { Text(stringResource(R.string.dialog_delete_comment_title)) }, text = { Text(stringResource(R.string.dialog_delete_comment_msg)) }, confirmButton = { TextButton(onClick = { viewModel.deleteComment(commentToDelete!!); commentToDelete = null }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(stringResource(R.string.btn_delete)) } }, dismissButton = { TextButton(onClick = { commentToDelete = null }) { Text(stringResource(R.string.btn_cancel)) } }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    }

    Scaffold(
        topBar = { Column { CenterAlignedTopAppBar(title = { Text(stringResource(R.string.menu_comments), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)) }, navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.btn_close)) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface))
            ; HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) } },
        bottomBar = {
            Column(modifier = Modifier.imePadding().navigationBarsPadding()) {
                AnimatedVisibility(visible = replyingTo != null && !isGuest) { Surface(color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) { Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) { Text(text = "Replying to ${replyingTo?.user?.username}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer); IconButton(onClick = { viewModel.cancelReplying(); commentText = "" }, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, tint = MaterialTheme.colorScheme.onSecondaryContainer) } } } }
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 16.dp, shadowElevation = 16.dp) {
                    if (isGuest) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp).height(56.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(24.dp)).clickable { Toast.makeText(context, context.getString(R.string.login_to_comment), Toast.LENGTH_SHORT).show() }, contentAlignment = Alignment.Center) { Text(text = stringResource(R.string.login_to_comment), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold) }
                    } else {
                        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                            TextField(value = commentText, onValueChange = { commentText = it }, placeholder = { Text(if(replyingTo != null) "Write a reply..." else stringResource(R.string.add_comment_hint)) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(24.dp), colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)), maxLines = 4, enabled = !isPosting, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { if (commentText.isNotBlank()) { viewModel.postComment(commentText, null); commentText = "" } }))
                            Spacer(Modifier.width(8.dp))
                            IconButton(onClick = { if (commentText.isNotBlank()) { viewModel.postComment(commentText, null); commentText = "" } }, enabled = !isPosting && commentText.isNotBlank(), modifier = Modifier.size(48.dp).background(if (commentText.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, CircleShape)) { if (isPosting) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp) } else { Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.comment_send_action), tint = if (commentText.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 2.dp)) } }
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (comments.isEmpty() && isLoading) { Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
        else if (comments.isEmpty()) { Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.ChatBubbleOutline, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.surfaceVariant); Spacer(Modifier.height(16.dp)); Text(stringResource(R.string.comment_no_comments), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) } } }
        else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(innerPadding), contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp)) {
                item {
                    Box(modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)) {
                        OutlinedButton(onClick = { isSortMenuExpanded = true }) {
                            Text(
                                text = stringResource(id = R.string.sorted_by, stringResource(id = commentSort.labelResId)),
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
                                            Icon(Icons.Rounded.Check, contentDescription = stringResource(id = R.string.desc_selected))
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
                        CommentRowItem(comment = comment, isMine = (userId == myId), isReply = false, isGuest = isGuest, onNavigateToProfile = { if (userId != 0L) viewModel.navigateToArtist(userId) }, onSeekTo = { pos -> val t = viewModel.selectedTrackForSheet; if (t != null) { if (t.id == viewModel.currentTrack?.id) viewModel.seekTo(pos) else viewModel.playTrackAtPosition(t, pos) } }, onToggleLike = { viewModel.toggleCommentLike(comment) }, onReply = { viewModel.startReplying(comment) }, onDelete = { commentToDelete = comment })
                        comment.replies?.forEach { reply ->
                            val rUserId = reply.user?.id ?: 0L
                            CommentRowItem(comment = reply, isMine = (rUserId == myId), isReply = true, isGuest = isGuest, onNavigateToProfile = { if (rUserId != 0L) viewModel.navigateToArtist(rUserId) }, onSeekTo = { pos -> val t = viewModel.selectedTrackForSheet; if (t != null) { if (t.id == viewModel.currentTrack?.id) viewModel.seekTo(reply.trackTimestamp ?: 0) else viewModel.playTrackAtPosition(t, reply.trackTimestamp ?: 0) } }, onToggleLike = { viewModel.toggleCommentLike(reply) }, onReply = { viewModel.startReplying(comment) }, onDelete = { commentToDelete = reply })
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 72.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
                if (viewModel.commentNextHref != null) { item { LaunchedEffect(Unit) { viewModel.loadComments() }; Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) } } }
            }
        }
    }
}

@Composable
fun CommentRowItem(comment: Comment, isMine: Boolean, isReply: Boolean, isGuest: Boolean, onNavigateToProfile: () -> Unit, onSeekTo: (Long) -> Unit, onToggleLike: () -> Unit, onReply: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val avatarUrl = comment.user?.avatarUrl
    val username = comment.user?.username ?: stringResource(R.string.comment_anonymous)
    val isVisuallyReply = isReply || comment.body.trim().startsWith("@")
    val startPadding = if (isVisuallyReply) 56.dp else 16.dp
    val avatarSize = if (isVisuallyReply) 40.dp else 48.dp

    Row(modifier = Modifier.fillMaxWidth().padding(start = startPadding, end = 16.dp, top = 12.dp, bottom = 12.dp)) {
        Box(modifier = Modifier.size(avatarSize).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { onNavigateToProfile() }, contentAlignment = Alignment.Center) {
            if (!avatarUrl.isNullOrEmpty()) { AsyncImage(model = avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            else { Icon(Icons.Default.Person, contentDescription = stringResource(R.string.comment_default_avatar), modifier = Modifier.size(if(isVisuallyReply) 24.dp else 28.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(text = username, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = if(isVisuallyReply) 13.sp else 14.sp), color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).clickable { onNavigateToProfile() })
                Spacer(Modifier.width(8.dp))
                // verify icon for comments user
                if (comment.user?.verified == true) {
                    Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(text = getRelativeTime(comment.createdAt, context), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(text = comment.body, style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 20.sp, fontSize = 15.sp), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (comment.trackTimestamp != null) { Surface(onClick = { onSeekTo(comment.trackTimestamp) }, shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f), contentColor = MaterialTheme.colorScheme.primary) { Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(12.dp)); Spacer(Modifier.width(4.dp)); Text(text = makeTimeString(comment.trackTimestamp), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)) } } }
                Spacer(modifier = Modifier.weight(1f))
                if (isMine) { IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) { Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) }; Spacer(Modifier.width(4.dp)) }
                IconButton(onClick = { if (isGuest) Toast.makeText(context, context.getString(R.string.login_to_interact), Toast.LENGTH_SHORT).show() else onReply() }, modifier = Modifier.size(32.dp)) { Icon(Icons.AutoMirrored.Rounded.Reply, contentDescription = "Reply", tint = if (isGuest) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=0.3f) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { if (isGuest) Toast.makeText(context, context.getString(R.string.login_to_interact), Toast.LENGTH_SHORT).show() else onToggleLike() }.padding(4.dp)) {
                    val icon = if (comment.isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder
                    val tint = if (comment.isLiked) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant.let { if (isGuest) it.copy(alpha = 0.3f) else it }
                    Icon(imageVector = icon, contentDescription = stringResource(R.string.player_like_action), tint = tint, modifier = Modifier.size(16.dp))
                    if (comment.likesCount > 0) { Spacer(Modifier.width(4.dp)); Text(text = formatNumber(comment.likesCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsSheetContent(track: Track, onClose: () -> Unit, onOpenComments: () -> Unit, viewModel: PlayerViewModel) {
    val context = LocalContext.current; val uriHandler = LocalUriHandler.current; val isLocalMode = viewModel.isLocalDetailsMode; val localPath = viewModel.localFilePathForDetails
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); val displayFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())
    val releaseDateStr = remember(track) { try { val dateStr = track.releaseDate ?: track.createdAt; if (dateStr != null) { val date = dateFormat.parse(dateStr); displayFormat.format(date ?: Date()) } else context.getString(R.string.detail_unknown) } catch (e: Exception) { context.getString(R.string.detail_unknown) } }
    val tags = remember(track.tagList) { parseSoundCloudTags(track.tagList) }
    var fileSizeStr by remember { mutableStateOf("") }; var fileFormatStr by remember { mutableStateOf(context.getString(R.string.format_default)) }; var cleanPathStr by remember { mutableStateOf("") }; var bitrateStr by remember { mutableStateOf("") }

    LaunchedEffect(isLocalMode, localPath) {
        if (isLocalMode && !localPath.isNullOrEmpty()) {
            try {
                val file = File(localPath)
                if (file.exists()) {
                    val sizeMb = file.length() / (1024.0 * 1024.0); fileSizeStr = context.getString(R.string.detail_file_size_formatted, sizeMb); fileFormatStr = file.extension.uppercase(); cleanPathStr = file.absolutePath.replace("/storage/emulated/0", context.getString(R.string.storage_internal_mem))
                    val durationSec = (track.durationMs ?: 0L) / 1000; if (durationSec > 0) { val bitrate = ((file.length() * 8) / durationSec) / 1000; bitrateStr = "$bitrate kbps" }
                } else if (localPath.startsWith("content://")) {
                    try {
                        val uri = Uri.parse(localPath); val type = context.contentResolver.getType(uri); fileFormatStr = type?.split("/")?.last()?.uppercase() ?: context.getString(R.string.format_fallback)
                        context.contentResolver.query(uri, null, null, null, null)?.use { cursor -> if (cursor.moveToFirst()) { val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE); if (sizeIndex != -1) { val sizeBytes = cursor.getLong(sizeIndex); val sizeMb = sizeBytes / (1024.0 * 1024.0); fileSizeStr = context.getString(R.string.detail_file_size_formatted, sizeMb) } } }
                        val rawPath = uri.path ?: localPath; val decodedPath = try { java.net.URLDecoder.decode(rawPath, "UTF-8") } catch (e: Exception) { rawPath }
                        cleanPathStr = when { decodedPath.contains("primary:") -> context.getString(R.string.storage_internal_mem) + "/" + decodedPath.substringAfter("primary:"); else -> decodedPath }
                    } catch (e: Exception) { cleanPathStr = localPath }
                }
            } catch (e: Exception) { fileSizeStr = context.getString(R.string.detail_unknown); cleanPathStr = localPath ?: "" }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)) { AsyncImage(model = track.fullResArtwork, contentDescription = null, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop); Spacer(Modifier.width(16.dp)); Column {
                Text(text = track.title ?: stringResource(R.string.untitled_track), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                // updated verified row in details sheet
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { if (track.id > 0) { onClose(); track.user?.id?.let { if (it > 0) viewModel.navigateToArtist(it) } } }) {
                    Text(text = track.user?.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    if (track.user?.verified == true) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    }
                }
            } }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant); Spacer(Modifier.height(16.dp))
        }
        if (isLocalMode) {
            item {
                Text(text = stringResource(R.string.detail_file_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(16.dp))
                val formatText = if (bitrateStr.isNotEmpty()) "$fileFormatStr • $bitrateStr" else fileFormatStr
                DetailInfoRow(stringResource(R.string.detail_format), formatText)
                if (fileSizeStr.isNotEmpty()) DetailInfoRow(stringResource(R.string.detail_size), fileSizeStr)
                DetailInfoRow(stringResource(R.string.detail_duration), makeTimeString(track.durationMs ?: 0L))
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)); Spacer(Modifier.height(16.dp))
                Text(text = stringResource(R.string.detail_location), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Spacer(Modifier.height(8.dp))
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text(text = if (cleanPathStr.isNotEmpty()) cleanPathStr else localPath ?: stringResource(R.string.storage_internal_mem), style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace), color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(12.dp)) }
                Spacer(Modifier.height(32.dp))
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { DetailStatItem(icon = Icons.Rounded.PlayArrow, value = formatNumber(track.playbackCount), label = stringResource(R.string.detail_stats_plays)); DetailStatItem(icon = Icons.Rounded.Favorite, value = formatNumber(track.likesCount), label = stringResource(R.string.detail_stats_likes), onClick = { viewModel.navigateToTrackDetails(track.id, 0) }); DetailStatItem(icon = Icons.Rounded.Repeat, value = formatNumber(track.repostsCount), label = stringResource(R.string.detail_stats_reposts), onClick = { viewModel.navigateToTrackDetails(track.id, 1) }) }
                Spacer(Modifier.height(24.dp))
            }
            item { OutlinedButton(onClick = { onClose(); viewModel.navigateToTrackDetails(track.id) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) { Icon(Icons.Rounded.Hub, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.detail_see_similar), fontWeight = FontWeight.SemiBold) }; Spacer(Modifier.height(16.dp)) }
            item { Button(onClick = onOpenComments, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Icon(Icons.AutoMirrored.Rounded.Comment, null); Spacer(Modifier.width(12.dp)); Text(text = stringResource(R.string.detail_see_comments, formatNumber(track.commentCount)), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)) }; Spacer(Modifier.height(24.dp)) }
            item { DetailInfoRow(stringResource(R.string.detail_release_date), releaseDateStr); if (!track.genre.isNullOrBlank()) { DetailInfoRow(stringResource(R.string.detail_genre), track.genre) }; Spacer(Modifier.height(16.dp)) }
            if (!track.description.isNullOrBlank()) { item { Text(stringResource(R.string.detail_description), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); ExpandableDescription(text = track.description, onUrlClick = { url -> uriHandler.openUri(url) }, onMentionClick = { username -> onClose(); viewModel.resolveAndNavigateToArtist(username) }); Spacer(Modifier.height(24.dp)) } }
            if (tags.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.detail_tags), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { tag -> AssistChip(onClick = { onClose(); viewModel.navigateToTag(tag) }, label = { Text("#${tag.uppercase()}") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, labelColor = MaterialTheme.colorScheme.onSurface), border = null, shape = CircleShape) } }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
fun DetailStatItem(icon: ImageVector, value: String, label: String, onClick: () -> Unit = {}) { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp)); Spacer(Modifier.height(4.dp)); Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
@Composable
fun DetailInfoRow(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) } }

@Composable
fun ExpandableDescription(text: String, onUrlClick: (String) -> Unit, onMentionClick: (String) -> Unit) {
    var isExpanded by remember { mutableStateOf(false) }
    val urlPattern = Pattern.compile("((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])")
    val mentionPattern = Pattern.compile("@[\\w-]+")
    val annotatedString = buildAnnotatedString {
        val fullText = text; append(fullText)
        val urlMatcher = urlPattern.matcher(fullText); while (urlMatcher.find()) { addStringAnnotation(tag = "URL", annotation = urlMatcher.group(), start = urlMatcher.start(), end = urlMatcher.end()); addStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold), start = urlMatcher.start(), end = urlMatcher.end()) }
        val mentionMatcher = mentionPattern.matcher(fullText); while (mentionMatcher.find()) { addStringAnnotation(tag = "MENTION", annotation = mentionMatcher.group(), start = mentionMatcher.start(), end = mentionMatcher.end()); addStyle(style = SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold), start = mentionMatcher.start(), end = mentionMatcher.end()) }
    }
    Column(modifier = Modifier.animateContentSize()) {
        ClickableText(text = annotatedString, style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp), maxLines = if (isExpanded) Int.MAX_VALUE else 5, overflow = TextOverflow.Ellipsis, onClick = { offset ->
            var isAnnotationClicked = false; annotatedString.getStringAnnotations(start = offset, end = offset).firstOrNull()?.let { annotation -> when (annotation.tag) { "URL" -> { onUrlClick(annotation.item); isAnnotationClicked = true }; "MENTION" -> { onMentionClick(annotation.item); isAnnotationClicked = true } } }
            if (!isAnnotationClicked) isExpanded = !isExpanded
        })
        if (text.length > 200) { Text(text = if (isExpanded) stringResource(R.string.detail_show_less) else stringResource(R.string.detail_show_more), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).clickable { isExpanded = !isExpanded }) }
    }
}

fun formatNumber(count: Int): String {
    if (count < 1000) return count.toString()
    val k = count / 1000.0; val m = count / 1000000.0
    return when { m >= 1.0 -> String.format(Locale.US, "%.1fM", m); k >= 1.0 -> String.format(Locale.US, "%.1fk", k); else -> count.toString() }
}

fun getRelativeTime(dateStr: String, context: Context): String {
    try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US); val date = format.parse(dateStr) ?: return ""
        val diff = System.currentTimeMillis() - date.time; val seconds = diff / 1000; val minutes = seconds / 60; val hours = minutes / 60; val days = hours / 24; val weeks = days / 7; val months = days / 30; val years = days / 365
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
    } catch (e: Exception) { return "" }
}

fun parseSoundCloudTags(tagList: String?): List<String> {
    if (tagList.isNullOrBlank()) return emptyList()
    val tags = mutableListOf<String>(); val pattern = Pattern.compile("\"([^\"]*)\"|(\\S+)")
    val matcher = pattern.matcher(tagList)
    while (matcher.find()) { if (matcher.group(1) != null) tags.add(matcher.group(1)!!) else tags.add(matcher.group(2)!!) }
    return tags
}
private fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}