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
    import androidx.compose.material.icons.rounded.Verified
    import androidx.compose.material.icons.rounded.RestartAlt
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
import com.alananasss.kittytune.ui.player.SleepScreen
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
            onDismiss = { showQuickSettingsDialog = false },
            onFocusMode = { viewModel.isSleepScreenOpen = true }
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
                    extraBlur = true,
                    animate = viewModel.uiIsPlaying
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

        val isThemeBg = backgroundStyle == PlayerBackgroundStyle.THEME
        val topContentColor = if (isThemeBg) MaterialTheme.colorScheme.onSurface else Color.White
        val topDimColor = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.7f)

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
                                            color = topContentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.clickable { viewModel.navigateToTrackArtist(currentTrack) }
                                        ) {
                                            Text(
                                                text = currentTrack.displayArtist.ifBlank { currentTrack.user?.username.orEmpty() },
                                                style = MaterialTheme.typography.bodySmall,
                                                color = topDimColor,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (currentTrack.user?.verified == true) {
                                                Spacer(Modifier.width(3.dp))
                                                Icon(
                                                    Icons.Rounded.Verified,
                                                    null,
                                                    tint = topDimColor,
                                                    modifier = Modifier.size(12.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text(
                                    stringResource(R.string.player_lyrics),
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = topContentColor
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onClose) {
                                Icon(Icons.Rounded.Close, stringResource(R.string.btn_close), tint = topContentColor)
                            }
                        },
                        actions = {
                            IconButton(onClick = { showUploadYamlDialog = true }) {
                                Icon(Icons.Rounded.Add, stringResource(R.string.custom_lyrics_title), tint = topContentColor)
                            }
                            IconButton(onClick = { showQuickSettingsDialog = true }) {
                                val tint = if (viewModel.lyricsOffset != 0L) MaterialTheme.colorScheme.primary else topContentColor
                                Icon(Icons.Rounded.Tune, stringResource(R.string.pref_lyrics_title), tint = tint)
                            }
                            IconButton(onClick = { viewModel.isSearchingLyrics = true }) {
                                Icon(Icons.Rounded.Search, stringResource(R.string.lyrics_manual_search), tint = topContentColor)
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
                                        LyricsEnhancedView(
                                            viewModel = viewModel,
                                            textColorOverride = if (isThemeBg) MaterialTheme.colorScheme.onSurface else Color.White
                                        )
                                    }
                                    com.alananasss.kittytune.data.local.LyricsUiStyle.CLASSIC -> {
                                        SyncedLyricsView(viewModel = viewModel, isThemeBg = isThemeBg)
                                    }
                                }
                            }
                            LyricsMode.PLAIN -> {
                                PlainLyricsView(viewModel = viewModel, isThemeBg = isThemeBg)
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
                                hasPlain = hasPlain,
                                isThemeBg = isThemeBg
                            )
                        }
                    }
                }
            }
        }
    }

    AnimatedVisibility(
        visible = viewModel.isSleepScreenOpen,
        enter = fadeIn(tween(600)),
        exit = fadeOut(tween(300))
    ) {
        SleepScreen(
            viewModel = viewModel,
            accent = MaterialTheme.colorScheme.primary,
            onWake = { viewModel.isSleepScreenOpen = false }
        )
    }
    }
}

@Composable
fun LyricsModeSelector(
    currentMode: LyricsMode,
    onModeSelected: (LyricsMode) -> Unit,
    hasSynced: Boolean,
    hasPlain: Boolean,
    modifier: Modifier = Modifier,
    isThemeBg: Boolean = false
) {
    Surface(
        color = if (isThemeBg) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f) else Color.Black.copy(alpha = 0.6f),
        shape = CircleShape,
        modifier = modifier.height(38.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isThemeBg) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.15f)
        )
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (hasSynced) {
                LyricsModeChip(
                    text = stringResource(R.string.lyrics_mode_synced),
                    isSelected = currentMode == LyricsMode.SYNCED,
                    onClick = { onModeSelected(LyricsMode.SYNCED) },
                    isThemeBg = isThemeBg
                )
            }

            if (hasPlain) {
                LyricsModeChip(
                    text = stringResource(R.string.lyrics_mode_plain),
                    isSelected = currentMode == LyricsMode.PLAIN,
                    onClick = { onModeSelected(LyricsMode.PLAIN) },
                    enabled = hasPlain,
                    isThemeBg = isThemeBg
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
    enabled: Boolean = true,
    isThemeBg: Boolean = false
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (isThemeBg) MaterialTheme.colorScheme.primary else Color.White
        } else Color.Transparent,
        animationSpec = tween(300),
        label = "bgColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) {
            if (isThemeBg) MaterialTheme.colorScheme.onPrimary else Color.Black
        } else {
            if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 0.85f else 0.4f)
            else Color.White.copy(alpha = if (enabled) 0.7f else 0.3f)
        },
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
fun SyncedLyricsView(viewModel: PlayerViewModel, isThemeBg: Boolean = false) {
    val lyrics = viewModel.lyricsLines
    val listState = key(viewModel.uiCurrentTrack?.id) {
        rememberLazyListState(
            initialFirstVisibleItemIndex = LyricsUtils.activeLineIndex(lyrics, viewModel.uiCurrentPosition + viewModel.lyricsOffset).coerceAtLeast(0)
        )
    }
    val fontSize = viewModel.lyricsFontSize
    val lyricsFontFamily = rememberLyricsFontFamily(viewModel.lyricsFont)
    val alignment = when(viewModel.lyricsAlignment) {
        LyricsAlignment.LEFT -> TextAlign.Left
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.RIGHT -> TextAlign.Right
    }

    val activeColor = if (isThemeBg) MaterialTheme.colorScheme.onSurface else Color.White
    val inactiveColor = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant else Color.White.copy(alpha = 0.45f)
    val unsungColor = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.35f)
    val backingColor = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.7f)
    val translationColor = if (isThemeBg) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.75f)

    val smoothDrawPosition = rememberSmoothPosition(
        positionMs = { viewModel.uiCurrentPosition },
        isPlaying = viewModel.uiIsPlaying,
        speed = viewModel.effectsState.speed,
    )

    val fadeBrush = remember {
        Brush.verticalGradient(
            0f to Color.Transparent,
            0.04f to Color.Black,
            0.85f to Color.Black,
            1f to Color.Transparent
        )
    }

    val activeIndex by remember(lyrics) {
        derivedStateOf { LyricsUtils.activeLineIndex(lyrics, viewModel.uiCurrentPosition + viewModel.lyricsOffset) }
    }

    val readingByHand = FollowActiveLine(listState, activeIndex, centred = true, contentKey = lyrics.size to lyrics.firstOrNull()?.startTime)
    val focusIndex = rememberFocusLine(listState, activeIndex, readingByHand)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeight = maxHeight
        val halfHeight = screenHeight / 2
        val topPadding = halfHeight

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPadding, bottom = halfHeight),
            modifier = Modifier
                .fillMaxSize()
                .revealWhenPlaced(listState, activeIndex, contentKey = lyrics.size to lyrics.firstOrNull()?.startTime)
                .stopAtLastLine(listState)
                .fadingEdge(fadeBrush),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            itemsIndexed(lyrics, key = { _, line -> "${line.startTime}-${line.endTime}" }) { index, line ->
                val isActive = index == activeIndex

                val treatment = LyricLineStyling.treatmentFor(
                    style = viewModel.lyricsDisplayStyle,
                    distance = if (focusIndex < 0) 0 else index - focusIndex,
                    blurEnabled = viewModel.lyricsLineBlurEnabled && !readingByHand,
                )

                val scale by animateFloatAsState(treatment.scale, tween(400), label = "scale")
                val alpha by animateFloatAsState(treatment.alpha, tween(400), label = "alpha")
                val blurRadius by androidx.compose.animation.core.animateDpAsState(
                    treatment.blur, tween(400), label = "blur"
                )

                val hzAlignment = when(alignment) {
                    TextAlign.Left -> Alignment.Start
                    TextAlign.Center -> Alignment.CenterHorizontally
                    TextAlign.Right -> Alignment.End
                    else -> Alignment.CenterHorizontally
                }

                // Hoisted out of the per-item content: it is identical for every line of the
                // track and does not depend on the item, so it was pure per-item waste.
                val isDuetActive = viewModel.isDuetActiveForTrack(viewModel.uiCurrentTrack)
                val effectiveSinger = if (isDuetActive) {
                    line.singer?.takeIf { it != LyricSinger.DEFAULT }
                        ?: when (line.agent?.trim()?.lowercase()) {
                            "v2", "singer2", "2" -> LyricSinger.SINGER_2
                            "v1", "singer1", "1" -> LyricSinger.SINGER_1
                            "both", "group", "all", "v1000", "v2000", "3", "v3" -> LyricSinger.BOTH
                            else -> LyricSinger.DEFAULT
                        }
                } else {
                    LyricSinger.DEFAULT
                }

                val lineTextAlign = when (effectiveSinger) {
                    LyricSinger.SINGER_1 -> TextAlign.Start
                    LyricSinger.SINGER_2 -> TextAlign.End
                    LyricSinger.BOTH -> TextAlign.Center
                    else -> alignment
                }
                val lineHzAlignment = when (effectiveSinger) {
                    LyricSinger.SINGER_1 -> Alignment.Start
                    LyricSinger.SINGER_2 -> Alignment.End
                    LyricSinger.BOTH -> Alignment.CenterHorizontally
                    else -> hzAlignment
                }

                val splitBacking = viewModel.isSplitBackingVocalsEnabled
                val (mainLine, backingLine) = remember(line, splitBacking) {
                    if (splitBacking) separateBackingVocals(line) else line to null
                }

                Column(
                    horizontalAlignment = lineHzAlignment,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = if (effectiveSinger == LyricSinger.SINGER_2) 36.dp else 24.dp,
                            end = if (effectiveSinger == LyricSinger.SINGER_1) 36.dp else 24.dp
                        )
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                pivotFractionX = when (lineHzAlignment) {
                                    Alignment.Start -> 0f
                                    Alignment.End -> 1f
                                    else -> 0.5f
                                },
                                pivotFractionY = 0.5f,
                            )
                        }
                        .alpha(alpha)
                        .then(
                            if (blurRadius > 0.dp) {
                                Modifier.blur(blurRadius)
                            } else Modifier
                        )
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            LyricsUtils.seekTargetFor(
                                line = line,
                                lyricsOffsetMs = viewModel.lyricsOffsetAtLyricTime(line.startTime),
                                durationMs = viewModel.uiDuration,
                            )?.let(viewModel::seekTo)
                        }
                ) {
                    val lineFont = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize * 1.4).sp,
                        fontFamily = lyricsFontFamily
                    )

                    LyricLineText(
                        line = mainLine,
                        isActive = isActive,
                        positionMs = { smoothDrawPosition.value + viewModel.lyricsOffset },
                        wordSync = viewModel.isWordSyncEnabled,
                        fillEffect = viewModel.isAppleMusicEffectEnabled,
                        activeStyle = lineFont.copy(fontWeight = FontWeight.ExtraBold),
                        inactiveStyle = lineFont.copy(fontWeight = FontWeight.Bold),
                        activeColor = activeColor,
                        inactiveColor = inactiveColor,
                        unsungColor = unsungColor,
                        textAlign = lineTextAlign,
                    )

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
                                color = backingColor,
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
                        val romanizationColor = if (isThemeBg) MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.9f else 0.4f) else Color.White.copy(alpha = if (isActive) 0.9f else 0.5f)
                        Text(
                            text = line.romanization ?: "",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = (fontSize * 0.85f).sp,
                                lineHeight = (fontSize * 1.2f).sp,
                                fontFamily = lyricsFontFamily
                            ),
                            color = romanizationColor,
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
                            color = translationColor,
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
            AnimatedContent(
                targetState = viewModel.showLyricsOffsetControls,
                transitionSpec = {
                    if (targetState) {
                        (slideInVertically { height -> height } + fadeIn())
                            .togetherWith(fadeOut(animationSpec = tween(100)))
                    } else {
                        (fadeIn(animationSpec = tween(100, delayMillis = 150)))
                            .togetherWith(slideOutVertically { height -> height } + fadeOut())
                    }
                },
                contentAlignment = Alignment.BottomCenter,
                label = "controls_anim"
            ) { showControls ->
                if (showControls) {
                    LyricsOffsetControls(
                        offset = viewModel.lyricsOffset,
                        onAdjust = { viewModel.adjustLyricsOffset(it) },
                        onReset = { viewModel.resetLyricsOffset() },
                        onClose = { viewModel.showLyricsOffsetControls = false },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    WrongLyricsButton(onClick = { viewModel.isSearchingLyrics = true })
                }
            }
        }
    }
}

@Composable
fun LyricsOffsetControls(
    offset: Long,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.widthIn(max = 420.dp).fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Timer, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.lyrics_sync),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.btn_close), tint = scheme.onSurfaceVariant)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RepeatingIconButton(onClick = { onAdjust(-OFFSET_STEP_MS) }, icon = Icons.Rounded.Remove, tint = scheme.onSurface)
                val shiftColor by androidx.compose.animation.animateColorAsState(
                    if (offset == 0L) scheme.onSurfaceVariant else scheme.primary, label = "lyricsOffsetColor"
                )
                Text(
                    text = String.format(java.util.Locale.US, "%s%.1f s", if (offset > 0) "+" else "", offset / 1000.0),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = shiftColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
                RepeatingIconButton(onClick = { onAdjust(OFFSET_STEP_MS) }, icon = Icons.Rounded.Add, tint = scheme.onSurface)
            }

            TextButton(
                onClick = onReset,
                enabled = offset != 0L,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp, end = 12.dp),
            ) {
                Icon(Icons.Rounded.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.btn_reset), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun RepeatingIconButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val currentOnClick by rememberUpdatedState(onClick)
    val scope = rememberCoroutineScope()
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        currentOnClick()
                        val repeat = scope.launch {
                            delay(REPEAT_START_DELAY_MS)
                            while (isActive) {
                                currentOnClick()
                                delay(REPEAT_INTERVAL_MS)
                            }
                        }
                        tryAwaitRelease()
                        repeat.cancel()
                    }
                )
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
    }
}

private const val OFFSET_STEP_MS = 100L
private const val REPEAT_START_DELAY_MS = 400L
private const val REPEAT_INTERVAL_MS = 100L

@Composable
fun PlainLyricsView(viewModel: PlayerViewModel, isThemeBg: Boolean = false) {
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

    val listState = key(viewModel.uiCurrentTrack?.id) { rememberLazyListState() }
    var lastUserScrollMs by remember { mutableLongStateOf(0L) }

    FollowPlainLyrics(
        listState = listState,
        enabled = viewModel.isPlainAutoScrollEnabled,
        speed = viewModel.effectivePlainAutoScrollSpeed,
        lineCount = lines.size,
        positionMs = { viewModel.uiCurrentPosition },
        isPlaying = { viewModel.uiIsPlaying },
        playbackSpeed = { viewModel.effectsState.speed },
        lastManualScrollMs = { lastUserScrollMs },
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .revealWhenPlaced(listState, activeIndex = -1, contentKey = lines)
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
                    color = if (isThemeBg) MaterialTheme.colorScheme.onSurface else Color.White,
                    textAlign = alignment,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        FloatingActionButton(
            onClick = {
                clipboardManager.setText(AnnotatedString(text))
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
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
