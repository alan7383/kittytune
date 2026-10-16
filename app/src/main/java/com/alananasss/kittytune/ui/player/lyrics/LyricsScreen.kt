package com.alananasss.kittytune.ui.player.lyrics

import androidx.activity.compose.BackHandler
import com.alananasss.kittytune.domain.deduplicateArtistString
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.interaction.MutableInteractionSource
    import androidx.compose.foundation.interaction.collectIsFocusedAsState
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.material3.SingleChoiceSegmentedButtonRow
    import androidx.compose.material3.SegmentedButton
    import androidx.compose.material3.SegmentedButtonDefaults
    import androidx.compose.material3.FilterChip
    import androidx.compose.material3.FilterChipDefaults
    import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.KeyboardOptions
    import androidx.compose.foundation.text.input.TextFieldLineLimits
    import androidx.compose.foundation.text.input.TextFieldState
    import androidx.compose.foundation.verticalScroll
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.rounded.Close
    import androidx.compose.material.icons.rounded.Add
    import androidx.compose.material.icons.rounded.ArrowDropDown
    import androidx.compose.material.icons.rounded.ContentCopy
    import androidx.compose.material.icons.rounded.FormatAlignCenter
    import androidx.compose.material.icons.rounded.Remove
    import androidx.compose.material.icons.rounded.Search
    import androidx.compose.material.icons.rounded.Settings
    import androidx.compose.material.icons.rounded.Star
    import androidx.compose.material.icons.rounded.Timer
    import androidx.compose.material.icons.rounded.Tune
    import androidx.compose.material.icons.automirrored.rounded.FormatAlignLeft
    import androidx.compose.material.icons.automirrored.rounded.FormatAlignRight
    import androidx.compose.material3.*
    import com.alananasss.kittytune.ui.common.Slider
    import com.alananasss.kittytune.ui.common.moveCaretWithArrowKeys
    import androidx.compose.runtime.*
    import com.alananasss.kittytune.ui.theme.rememberLyricsFontFamily
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.platform.LocalUriHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.navigationBarsPadding
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.MusicManager
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerBackgroundStyle
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.LrcLibResponse
import com.alananasss.kittytune.ui.player.FluidArtworkBackground
import com.alananasss.kittytune.ui.player.LyricsMode
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.player.UnifiedLyricResult
import com.alananasss.kittytune.utils.makeTimeString
import com.alananasss.kittytune.ui.utils.fadingEdge
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val isSearching = viewModel.isSearchingLyrics
    val currentTrack = viewModel.uiCurrentTrack

    val hasSynced = viewModel.lyricsLines.any { it.endTime > 0 }
    val hasPlain = !viewModel.rawPlainLyrics.isNullOrBlank()

    var showQuickSettingsDialog by remember { mutableStateOf(false) }
    var showUploadYamlDialog by remember { mutableStateOf(false) }
    BackHandler {
        when {
            showQuickSettingsDialog -> showQuickSettingsDialog = false
            showUploadYamlDialog -> showUploadYamlDialog = false
            isSearching -> viewModel.isSearchingLyrics = false
            else -> onClose()
        }
    }

    if (showQuickSettingsDialog) {
        QuickLyricsSettingsDialog(
            viewModel = viewModel,
            onDismiss = { showQuickSettingsDialog = false }
        )
    }

    if (showUploadYamlDialog) {
        CustomLyricsDialog(
            viewModel = viewModel,
            onDismiss = { showUploadYamlDialog = false }
        )
    }

    val context = LocalContext.current
    val activity = context as? android.app.Activity
    DisposableEffect(Unit) {
        activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
    val prefs = remember { PlayerPreferences(context) }
    val backgroundStyle = remember { prefs.getPlayerStyle() }

    Box(modifier = Modifier.fillMaxSize()) {
        when (backgroundStyle) {
            PlayerBackgroundStyle.APPLE_MUSIC -> {
                FluidArtworkBackground(
                    artworkUrl = currentTrack?.fullResArtwork,
                    modifier = Modifier.fillMaxSize(),
                    extraBlur = true
                ) {
                    LyricsMeshBackground { }
                }
                // Extra dark scrim (0.30f) to make lyrics ultra-readable
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.30f)))
            }
            PlayerBackgroundStyle.BLUR -> {
                AsyncImage(
                    model = currentTrack?.fullResArtwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(120.dp).alpha(0.55f)
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
            PlayerBackgroundStyle.THEME -> {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
            }
            PlayerBackgroundStyle.GRADIENT -> {
                LyricsMeshBackground { }
            }
        }

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                if (!isSearching) {
                    CenterAlignedTopAppBar(
                        title = {
                            if (currentTrack != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    AsyncImage(
                                        model = currentTrack.fullResArtwork,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Column(
                                        horizontalAlignment = Alignment.Start,
                                        modifier = Modifier.widthIn(max = 200.dp)
                                    ) {
                                        Text(
                                            text = currentTrack.title ?: "",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = currentTrack.displayArtist.ifBlank { currentTrack.user?.username.orEmpty() },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.7f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    stringResource(R.string.player_lyrics),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.btn_close), tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showUploadYamlDialog = true }) {
                                Icon(Icons.Rounded.Add, stringResource(R.string.custom_lyrics_title), tint = Color.White)
                            }
                            IconButton(onClick = { showQuickSettingsDialog = true }) {
                                val tint = if (viewModel.lyricsOffset != 0L) MaterialTheme.colorScheme.primary else Color.White
                                Icon(Icons.Rounded.Settings, stringResource(R.string.pref_lyrics_title), tint = tint)
                            }
                            IconButton(onClick = { viewModel.isSearchingLyrics = true }) {
                                Icon(Icons.Rounded.Search, stringResource(R.string.lyrics_manual_search), tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }
            }
        ) { innerPadding ->
            BoxWithConstraints(modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)) {

            if (isSearching) {
                SearchLyricsView(
                    viewModel = viewModel,
                    onCloseSearch = { viewModel.isSearchingLyrics = false }
                )
            } else {
                if (viewModel.lyricsLines.isEmpty() && viewModel.rawPlainLyrics.isNullOrBlank()) {
                    if (viewModel.isLyricsLoading) {
                        SearchingLyricsState()
                    } else {
                        EmptyLyricsState(onManualSearch = { viewModel.isSearchingLyrics = true })
                    }
                } else {
                    AnimatedContent(
                        targetState = viewModel.lyricsMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.95f) togetherWith
                                    fadeOut(animationSpec = tween(300))
                        },
                        label = "LyricsModeTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { mode ->
                        when (mode) {
                            LyricsMode.SYNCED -> {
                                when (viewModel.lyricsUiStyle) {
                                    com.alananasss.kittytune.data.local.LyricsUiStyle.ENHANCED -> {
                                        LyricsEnhancedView(viewModel)
                                    }
                                    com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC -> {
                                        SyncedLyricsView(viewModel)
                                    }
                                }
                            }
                            LyricsMode.PLAIN -> {
                                PlainLyricsView(viewModel)
                            }
                        }
                    }

                    if (hasSynced && hasPlain) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(top = 16.dp)
                                .zIndex(10f)
                        ) {
                            LyricsModeSelector(
                                currentMode = viewModel.lyricsMode,
                                onModeSelected = { viewModel.lyricsMode = it },
                                hasSynced = hasSynced,
                                hasPlain = hasPlain
                            )
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
fun LyricsModeSelector(
    currentMode: LyricsMode,
    onModeSelected: (LyricsMode) -> Unit,
    hasSynced: Boolean,
    hasPlain: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color.Black.copy(alpha = 0.6f),
        shape = CircleShape,
        modifier = modifier.height(38.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasSynced) {
                LyricsModeChip(
                    text = stringResource(R.string.lyrics_mode_synced),
                    isSelected = currentMode == LyricsMode.SYNCED,
                    onClick = { onModeSelected(LyricsMode.SYNCED) }
                )
            }

            if (hasPlain) {
                LyricsModeChip(
                    text = stringResource(R.string.lyrics_mode_plain),
                    isSelected = currentMode == LyricsMode.PLAIN,
                    onClick = { onModeSelected(LyricsMode.PLAIN) },
                    enabled = hasPlain
                )
            }
        }
    }
}

@Composable
fun LyricsModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.Transparent,
        animationSpec = tween(300),
        label = "bgColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) Color.Black else Color.White.copy(alpha = if (enabled) 0.7f else 0.3f),
        animationSpec = tween(300),
        label = "textColor"
    )

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun SyncedLyricsView(viewModel: PlayerViewModel) {
    val currentPosition = viewModel.uiCurrentPosition
    val adjustedPosition = currentPosition + viewModel.lyricsOffset
    val lyrics = viewModel.lyricsLines
    // Opened on the line being sung, not on line one. Two minutes into a track the first frame
    // used to be the top of the song, which read as a jolt on every open before the follow logic
    // caught up.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = LyricsUtils.activeLineIndex(lyrics, adjustedPosition).coerceAtLeast(0)
    )
    val fontSize = viewModel.lyricsFontSize
    val lyricsFontFamily = rememberLyricsFontFamily(viewModel.lyricsFont)
    val alignment = when(viewModel.lyricsAlignment) {
        LyricsAlignment.LEFT -> TextAlign.Left
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.RIGHT -> TextAlign.Right
    }

    val fadeBrush = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.04f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent
        )
    }

    var smoothDrawPosition by remember { mutableFloatStateOf(currentPosition.toFloat()) }

    // High-precision smooth frame interpolation loop with PLL drift tracking
    LaunchedEffect(viewModel.uiCurrentTrack?.id) {
        var smoothPosition = MusicManager.player.currentPosition.coerceAtLeast(0L).toDouble()
        var lastOutputPosition = smoothPosition.toFloat()
        var lastFrameNanos = 0L

        while (isActive) {
            val isSliderActive = viewModel.isScrubbing
            val rawPosition = if (isSliderActive) {
                viewModel.uiCurrentPosition.toDouble()
            } else {
                MusicManager.player.currentPosition.coerceAtLeast(0L).toDouble()
            }
            val isPlayingState = MusicManager.player.isPlaying

            if (isSliderActive || !isPlayingState) {
                smoothPosition = rawPosition
                lastOutputPosition = rawPosition.toFloat()
                lastFrameNanos = 0L
                smoothDrawPosition = lastOutputPosition
                delay(50L)
            } else {
                val frameNanos = withFrameNanos { frameTimeNanos -> frameTimeNanos }

                if (lastFrameNanos == 0L) {
                    lastFrameNanos = frameNanos
                    smoothPosition = rawPosition
                    lastOutputPosition = rawPosition.toFloat()
                } else {
                    val elapsedNanos = frameNanos - lastFrameNanos
                    lastFrameNanos = frameNanos

                    val currentSpeed = viewModel.effectsState.speed
                    val deltaMs = (elapsedNanos / 1_000_000.0).coerceIn(0.0, 100.0) * currentSpeed
                    val driftMs = rawPosition - smoothPosition

                    if (kotlin.math.abs(driftMs) > 300.0 || rawPosition < lastOutputPosition - 500.0) {
                        smoothPosition = rawPosition
                        lastOutputPosition = rawPosition.toFloat()
                    } else {
                        val rateCorrection = (driftMs / 250.0).coerceIn(-0.5, 0.5)
                        val effectiveSpeed = (1.0 + rateCorrection).coerceIn(0.2, 1.8)
                        smoothPosition += deltaMs * effectiveSpeed

                        val target = smoothPosition.toFloat()
                        if (target >= lastOutputPosition) {
                            lastOutputPosition = target
                        } else {
                            smoothPosition = lastOutputPosition.toDouble()
                        }
                    }
                }
                smoothDrawPosition = lastOutputPosition
            }
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

    var isManualScrolling by remember { mutableStateOf(false) }
    var isUserTouching by remember { mutableStateOf(false) }
    var lastManualScrollTime by remember { mutableLongStateOf(0L) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) {
                    isManualScrolling = true
                    lastManualScrollTime = System.currentTimeMillis()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (isManualScrolling) {
                    lastManualScrollTime = System.currentTimeMillis()
                }
                return Velocity.Zero
            }
        }
    }

    LaunchedEffect(isManualScrolling, isUserTouching, lastManualScrollTime) {
        if (isManualScrolling) {
            while (isUserTouching || listState.isScrollInProgress) {
                lastManualScrollTime = System.currentTimeMillis()
                delay(100L)
            }
            while (isActive) {
                val elapsed = System.currentTimeMillis() - lastManualScrollTime
                val remaining = 3000L - elapsed
                if (remaining <= 0) break
                delay(remaining.coerceAtLeast(10L))
                if (isUserTouching || listState.isScrollInProgress) {
                    lastManualScrollTime = System.currentTimeMillis()
                }
            }
            if (!isUserTouching && !listState.isScrollInProgress) {
                isManualScrolling = false
                if (activeIndex >= 0) {
                    listState.returnToLine(activeIndex) { 0 }
                }
            }
        }
    }

    LaunchedEffect(activeIndex, isManualScrolling) {
        if (activeIndex >= 0 && !isManualScrolling) {
            val distance = kotlin.math.abs(activeIndex - listState.firstVisibleItemIndex)
            if (distance > 15) {
                listState.scrollToItem((activeIndex - 2).coerceAtLeast(0))
            }
            listState.animateScrollToItem(index = activeIndex)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed) {
                            isUserTouching = true
                            isManualScrolling = true
                            lastManualScrollTime = System.currentTimeMillis()
                        } else if (isUserTouching) {
                            isUserTouching = false
                            lastManualScrollTime = System.currentTimeMillis()
                        }
                    }
                }
            }
            .nestedScroll(nestedScrollConnection)
    ) {
        val screenHeight = maxHeight
        val halfHeight = screenHeight / 2
        val topPadding = halfHeight - 50.dp

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = halfHeight),
            modifier = Modifier
                .fillMaxSize()
                .stopAtLastLine(listState)
                .fadingEdge(fadeBrush),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val isActive = index == activeIndex
                val targetScale = 1.0f
                val targetAlpha = if (isActive) 1.0f else (if (index < activeIndex) 0.45f else 0.70f)

                // The backing vocals of the line — the second phrase sung over the first — shown under the
                // main line while it is sung (issue #66): the words the source marked as background, or the
                // ones in round brackets, split out with their own timing.
                val splitBacking = viewModel.isSplitBackingVocalsEnabled
                val (mainLine, backingLine) = remember(line, splitBacking) {
                    if (splitBacking) separateBackingVocals(line) else line to null
                }

                val scale by animateFloatAsState(targetScale, tween(400), label = "scale")
                val alpha by animateFloatAsState(targetAlpha, tween(400), label = "alpha")

                val isDuetActive = viewModel.isDuetActiveForTrack(viewModel.uiCurrentTrack)
                val effectiveSinger = if (isDuetActive) {
                    line.singer.takeIf { it != LyricSinger.DEFAULT } ?: when (line.agent?.trim()?.lowercase()) {
                        "v2", "singer2", "2" -> LyricSinger.SINGER_2
                        "v1", "singer1", "1" -> LyricSinger.SINGER_1
                        "both", "group", "all", "v1000", "v2000", "3", "v3" -> LyricSinger.BOTH
                        else -> LyricSinger.DEFAULT
                    }
                } else {
                    LyricSinger.DEFAULT
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // Duet lines are constrained to ~72% width and pushed to their side
                        .padding(
                            start = if (effectiveSinger == LyricSinger.SINGER_2) 100.dp else 24.dp,
                            end = if (effectiveSinger == LyricSinger.SINGER_1) 100.dp else 24.dp
                        )
                        .scale(scale)
                        .alpha(alpha)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isManualScrolling = false
                            isUserTouching = false
                            viewModel.seekTo(line.startTime)
                        },
                    horizontalAlignment = when(effectiveSinger) {
                        LyricSinger.SINGER_1 -> Alignment.Start
                        LyricSinger.SINGER_2 -> Alignment.End
                        LyricSinger.BOTH -> Alignment.CenterHorizontally
                        else -> when(viewModel.lyricsAlignment) {
                            LyricsAlignment.LEFT -> Alignment.Start
                            LyricsAlignment.CENTER -> Alignment.CenterHorizontally
                            LyricsAlignment.RIGHT -> Alignment.End
                        }
                    }
                ) {
                    val isWordSync = viewModel.isWordSyncEnabled
                    val isAppleEffect = viewModel.isAppleMusicEffectEnabled
                    val displayWords = if (isWordSync) mainLine.words.orEmpty() else emptyList()
                    // effective textAlign follows the same singer override
                    val lineTextAlign = when(effectiveSinger) {
                        LyricSinger.SINGER_1 -> TextAlign.Start
                        LyricSinger.SINGER_2 -> TextAlign.End
                        LyricSinger.BOTH -> TextAlign.Center
                        else -> alignment
                    }

                    if (isActive && displayWords.isNotEmpty()) {
                        val formattedWords = remember(displayWords, mainLine.text) {
                            formatLyricWordContents(mainLine.text, displayWords)
                        }
                        if (isAppleEffect) {
                            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                            val reconstructedText = remember(formattedWords) { formattedWords.joinToString("") }
                            // Reused across draws: the karaoke clip path is rebuilt at display rate
                            // while playing, and a fresh native Path each frame was pure allocation
                            // churn under the GC.
                            val karaokePath = remember { Path() }
                            val wordRanges = remember(formattedWords) {
                                val ranges = mutableListOf<Pair<Int, Int>>()
                                var currentLen = 0
                                for (w in formattedWords) {
                                    ranges.add(currentLen to currentLen + w.length)
                                    currentLen += w.length
                                }
                                ranges
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = reconstructedText,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * 1.4).sp,
                                        fontFamily = lyricsFontFamily
                                    ),
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = lineTextAlign,
                                    modifier = Modifier.fillMaxWidth(),
                                    onTextLayout = { textLayoutResult = it }
                                )
                                Text(
                                    text = reconstructedText,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = fontSize.sp,
                                        lineHeight = (fontSize * 1.4).sp,
                                        fontFamily = lyricsFontFamily
                                    ),
                                    color = Color.White,
                                    textAlign = lineTextAlign,
                                    modifier = Modifier.fillMaxWidth().drawWithContent {
                                        val currentPos = smoothDrawPosition + viewModel.lyricsOffset
                                        val layout = textLayoutResult ?: return@drawWithContent
                                        val path = karaokePath.apply { reset() }
                                        val safeTextLength = (reconstructedText.length - 1).coerceAtLeast(0)
                                        for (i in displayWords.indices) {
                                            val w = displayWords[i]
                                            val range = wordRanges[i]
                                            if (range.first >= range.second) continue
                                            if (currentPos >= w.endTime) {
                                                for (c in range.first until range.second) path.addRect(layout.getBoundingBox(c.coerceIn(0, safeTextLength)))
                                            } else if (currentPos >= w.startTime) {
                                                val progress = ((currentPos - w.startTime).toFloat() / (w.endTime - w.startTime).coerceAtLeast(1L)).coerceIn(0f, 1f)
                                                val exactProgressChars = progress * (range.second - range.first)
                                                val fullySungChars = exactProgressChars.toInt()
                                                val charFraction = exactProgressChars - fullySungChars
                                                for (c in range.first until range.first + fullySungChars) path.addRect(layout.getBoundingBox(c.coerceIn(0, safeTextLength)))
                                                val partialCharIdx = range.first + fullySungChars
                                                if (partialCharIdx < range.second) {
                                                    val cBbox = layout.getBoundingBox(partialCharIdx.coerceIn(0, safeTextLength))
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
                            val reconstructedText = buildAnnotatedString {
                                displayWords.forEachIndexed { idx, word ->
                                    val isWordActive = (viewModel.uiCurrentPosition + viewModel.lyricsOffset) >= word.startTime
                                    val wordColor = if (isWordActive) Color.White else Color.White.copy(alpha = 0.5f)
                                    val wordText = formattedWords.getOrElse(idx) { word.word }
                                    withStyle(SpanStyle(color = wordColor)) { append(wordText) }
                                }
                            }
                            Text(
                                text = reconstructedText,
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = fontSize.sp,
                                    lineHeight = (fontSize * 1.4).sp,
                                    fontFamily = lyricsFontFamily
                                ),
                                textAlign = lineTextAlign,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        val textColor = if (isActive) Color.White else Color.White.copy(alpha = 0.5f)
                        val shadow = if (isActive && isAppleEffect) {
                            val shadowBlur = (14f * viewModel.lyricsGlowFactor).coerceIn(4f, 28f)
                            androidx.compose.ui.graphics.Shadow(
                                color = Color.White.copy(alpha = (0.45f * viewModel.lyricsGlowFactor).coerceIn(0.1f, 0.8f)),
                                offset = androidx.compose.ui.geometry.Offset.Zero,
                                blurRadius = shadowBlur
                            )
                        } else null
                        Text(
                            text = mainLine.text,
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.Bold,
                                fontSize = fontSize.sp,
                                lineHeight = (fontSize * 1.4).sp,
                                fontFamily = lyricsFontFamily,
                                shadow = shadow
                            ),
                            color = textColor,
                            textAlign = lineTextAlign,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // The backing line: shown only while the line is sung, smaller and slightly apart, the way
                    // a second voice answers the first rather than a caption.
                    if (backingLine != null) {
                        AnimatedVisibility(
                            visible = isActive,
                            enter = fadeIn(tween(BACKING_ANIM_MS)) + expandVertically(
                                tween(BACKING_ANIM_MS),
                                expandFrom = Alignment.Top
                            ) + slideInVertically(tween(BACKING_ANIM_MS)) { -it / 3 },
                            exit = fadeOut(tween(BACKING_ANIM_MS)) + shrinkVertically(
                                tween(BACKING_ANIM_MS),
                                shrinkTowards = Alignment.Top
                            ),
                        ) {
                            Text(
                                text = backingLine.text,
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = (fontSize * 0.72f).sp,
                                    lineHeight = (fontSize * 1.0f).sp,
                                    fontFamily = lyricsFontFamily
                                ),
                                color = Color.White.copy(alpha = 0.8f),
                                textAlign = lineTextAlign,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = viewModel.isRomanizationEnabled && !line.romanization.isNullOrBlank(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Text(
                            text = line.romanization ?: "",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (fontSize * 0.85f).sp,
                                lineHeight = (fontSize * 1.2f).sp,
                                fontFamily = lyricsFontFamily
                            ),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.9f else 0.4f),
                            textAlign = lineTextAlign,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = viewModel.isLyricsTranslationEnabled
                                && !line.translation.isNullOrBlank()
                                && !line.translation.trim().equals(line.text.trim(), ignoreCase = true),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Text(
                            text = line.translation ?: "",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSize * 0.70f).sp,
                                lineHeight = (fontSize * 1.0f).sp,
                                fontFamily = lyricsFontFamily
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = lineTextAlign,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
        ) {
            WrongLyricsButton(
                onClick = { viewModel.isSearchingLyrics = true }
            )
        }
    }
}

@Composable
fun PlainLyricsView(viewModel: PlayerViewModel) {
    val text = viewModel.rawPlainLyrics ?: stringResource(R.string.lyrics_no_data)
    val clipboardManager = LocalClipboardManager.current

    val fontSize = viewModel.lyricsFontSize
    val alignment = when(viewModel.lyricsAlignment) {
        LyricsAlignment.LEFT -> TextAlign.Left
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.RIGHT -> TextAlign.Right
    }

    val lines = remember(text) { text.split("\n") }

    val fadeBrush = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.04f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .fadingEdge(fadeBrush),
            contentPadding = PaddingValues(top = 70.dp, bottom = 180.dp, start = 24.dp, end = 24.dp),
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
            Icon(Icons.Rounded.ContentCopy, stringResource(R.string.lyrics_copy_text), modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
fun SearchingLyricsState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator(
            color = Color.White,
            modifier = Modifier.size(36.dp),
            strokeWidth = 3.dp
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.lyrics_searching),
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun EmptyLyricsState(onManualSearch: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            stringResource(R.string.lyrics_no_data),
            color = Color.White.copy(0.7f),
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onManualSearch,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White.copy(alpha = 0.1f),
                contentColor = Color.White
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.lyrics_manual_search),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun WrongLyricsButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.4f),
            contentColor = Color.White.copy(alpha = 0.8f)
        ) {
            Text(
                stringResource(R.string.lyrics_wrong),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomLyricsDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val content = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (!content.isNullOrBlank()) {
                    viewModel.loadCustomLyrics(content)
                    onDismiss()
                }
            } catch (e: Exception) {
                // Ignore for now
            }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        dragHandle = {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Add,
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.custom_lyrics_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.btn_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                text = stringResource(R.string.custom_lyrics_desc_1),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.custom_lyrics_desc_2),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(16.dp))

            val uriHandler = LocalUriHandler.current
            val docUrl = "https://lrclib.net/lyricsfile"
            Text(
                text = stringResource(R.string.custom_lyrics_doc),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = TextDecoration.Underline
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable { uriHandler.openUri(docUrl) }
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { launcher.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(stringResource(R.string.btn_import_yaml))
            }
        }
    }
}

/** How long the backing-vocal line takes to appear under the line being sung, and to leave. */
private const val BACKING_ANIM_MS = 420
