    package com.alananasss.kittytune.ui.player.lyrics

    import androidx.compose.animation.*
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.animation.core.tween
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.interaction.MutableInteractionSource
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.material3.SingleChoiceSegmentedButtonRow
    import androidx.compose.material3.SegmentedButton
    import androidx.compose.material3.SegmentedButtonDefaults
    import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.foundation.rememberScrollState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.foundation.text.KeyboardActions
    import androidx.compose.foundation.text.KeyboardOptions
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
    import androidx.compose.runtime.*
    import androidx.compose.ui.Alignment
    import androidx.compose.ui.Modifier
    import androidx.compose.ui.draw.alpha
    import androidx.compose.ui.draw.blur
    import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.foundation.layout.navigationBarsPadding
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.data.local.PlayerPreferences
import com.alananasss.kittytune.data.network.LrcLibResponse
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

    val hasSynced = viewModel.lyricsLines.any { it.endTime > 0 }
    val hasPlain = !viewModel.rawPlainLyrics.isNullOrBlank()

    var showQuickSettingsDialog by remember { mutableStateOf(false) }
    var showUploadYamlDialog by remember { mutableStateOf(false) }

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

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (!isSearching) {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            stringResource(R.string.player_lyrics),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
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
                            Icon(Icons.Rounded.Settings, stringResource(R.string.pref_lyrics_title), tint = Color.White)
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
                    EmptyLyricsState(onManualSearch = { viewModel.isSearchingLyrics = true })
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
                                SyncedLyricsView(viewModel)
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
    val currentPosition = viewModel.currentPosition
    val adjustedPosition = currentPosition + viewModel.lyricsOffset
    val lyrics = viewModel.lyricsLines
    val listState = rememberLazyListState()
    val fontSize = viewModel.lyricsFontSize
    val alignment = when(viewModel.lyricsAlignment) {
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

    // Drift correction only if >400ms (seeks, skips) — no reset on normal updates
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
            listState.animateScrollToItem(index = activeIndex)
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

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .scale(scale)
                        .alpha(alpha)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { viewModel.seekTo(line.startTime) },
                    horizontalAlignment = when(viewModel.lyricsAlignment) {
                        LyricsAlignment.LEFT -> Alignment.Start
                        LyricsAlignment.CENTER -> Alignment.CenterHorizontally
                        LyricsAlignment.RIGHT -> Alignment.End
                    }
                ) {
                    val isWordSync = viewModel.isWordSyncEnabled
                    val isAppleEffect = viewModel.isAppleMusicEffectEnabled
                    val displayWords = if (isWordSync) line.words.orEmpty() else emptyList()

                    if (isActive && displayWords.isNotEmpty()) {
                        if (isAppleEffect) {
                            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                            val reconstructedText = remember(displayWords) { displayWords.joinToString("") { it.word } }
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
                                displayWords.forEach { word ->
                                    val isWordActive = (viewModel.currentPosition + viewModel.lyricsOffset) >= word.startTime
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
                                lineHeight = (fontSize * 1.2f).sp
                            ),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = if (isActive) 0.9f else 0.4f),
                            textAlign = alignment,
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        )
                    }
                    AnimatedVisibility(
                        visible = viewModel.isLyricsTranslationEnabled && !line.translation.isNullOrBlank(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Text(
                            text = line.translation ?: "",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = (fontSize * 0.70f).sp,
                                lineHeight = (fontSize * 1.0f).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            textAlign = alignment,
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
fun SearchLyricsView(
    viewModel: PlayerViewModel,
    onCloseSearch: () -> Unit
) {
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf(viewModel.manualSearchQuery) }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f))) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            IconButton(onClick = onCloseSearch) {
                Icon(Icons.Rounded.Close, stringResource(R.string.btn_close), tint = Color.White)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.lyrics_search_hint), color = Color.White.copy(0.5f)) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    cursorColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(0.5f)
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    viewModel.searchLyricsManual(query, viewModel.manualSearchProvider)
                    focusManager.clearFocus()
                })
            )
            IconButton(onClick = {
                viewModel.searchLyricsManual(query, viewModel.manualSearchProvider)
                focusManager.clearFocus()
            }) {
                Icon(Icons.Rounded.Search, stringResource(R.string.search_hint), tint = Color.White)
            }
        }

        if (viewModel.isLyricsLoading) {
            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
        }

        ExpressiveConnectedButtonGroup(
            options = listOf("MUSIXMATCH", "LRCLIB", "GENIUS"),
            selectedOption = viewModel.manualSearchProvider,
            onOptionSelected = { viewModel.searchLyricsManual(query, it) },
            modifier = Modifier.padding(horizontal = 16.dp),
            labelProvider = { provider ->
                Text(
                    text = when (provider) {
                        "MUSIXMATCH" -> "Musixmatch"
                        "LRCLIB" -> "LrcLib"
                        "GENIUS" -> "Genius"
                        else -> provider
                    }
                )
            }
        )

        val searchResults = remember(viewModel.unifiedLyricSearchResults.toList()) {
            viewModel.unifiedLyricSearchResults.toList()
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(items = searchResults, key = { it.id + it.provider }) { result ->
                Card(
                    onClick = { viewModel.selectUnifiedLyricResult(result) },
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(result.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(result.artistName, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.7f))
                            if (!result.albumName.isNullOrEmpty()) {
                                Text(result.albumName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(0.5f), maxLines = 1)
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(makeTimeString((result.durationSec * 1000).toLong()), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                if (result.hasLineSync) {
                                    Icon(Icons.Rounded.Timer, null, tint = Color.Yellow, modifier = Modifier.size(14.dp))
                                }
                                if (result.hasWordSync) {
                                    Icon(Icons.Rounded.Star, null, tint = Color.Green, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    result.provider,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = when (result.provider) {
                                        "MUSIXMATCH" -> Color(0xFFFF9800)
                                        "LRCLIB" -> Color(0xFF4CAF50)
                                        "GENIUS" -> Color(0xFFFFEB3B)
                                        else -> Color.White
                                    }
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
fun LyricsOffsetControls(
    offset: Long,
    onAdjust: (Long) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(), // Padding is managed by the parent
        shape = RoundedCornerShape(24.dp),
        color = Color.Black.copy(alpha = 0.8f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.lyrics_sync),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                // Proper formatting: +0.1s, -0.5s, 0.0s
                val seconds = offset / 1000.0
                val sign = if (offset > 0) "+" else ""
                val color = if (offset == 0L) Color.White.copy(0.7f) else MaterialTheme.colorScheme.primary

                Text(
                    text = String.format(java.util.Locale.US, "%s%.1fs", sign, seconds),
                    style = MaterialTheme.typography.titleMedium,
                    color = color,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 8.dp) // Small visual alignment
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // MINUS BUTTON (Active repetition)
                RepeatingIconButton(
                    onClick = { onAdjust(-100L) }, // -0.1s
                    icon = Icons.Rounded.Remove,
                    tint = Color.White
                )

                // RESET BUTTON (Simple click is enough)
                TextButton(onClick = onReset) {
                    Text("RESET", color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.SemiBold)
                }

                // PLUS BUTTON (Active repetition)
                RepeatingIconButton(
                    onClick = { onAdjust(100L) }, // +0.1s
                    icon = Icons.Rounded.Add,
                    tint = Color.White
                )
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

    // We use Surface instead of FilledIconButton to have total control over touch events
    Surface(
        shape = CircleShape, // Round shape like an IconButton
        color = Color.White.copy(0.1f), // Background color (translucent gray)
        modifier = modifier
            .size(48.dp) // Standard button size
            .clip(CircleShape) // Important for visual effect and touch
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Start coroutine for repetition
                        val job = scope.launch {
                            // 1. Immediate click on touch
                            currentOnClick()

                            // 2. Delay before starting repetition (e.g., 400ms)
                            delay(400)

                            // 3. Repetition loop while finger is pressed
                            while (isActive) {
                                currentOnClick()
                                delay(100) // Repetition speed (0.1s)
                            }
                        }

                        // Wait for user to release finger
                        tryAwaitRelease()

                        // Cancel loop as soon as it's released
                        job.cancel()
                    }
                )
            }
    ) {
        // Center icon in the Surface
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLyricsSettingsDialog(
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val fontSize = viewModel.lyricsFontSize
    val alignment = viewModel.lyricsAlignment
    var preferLocal by remember { mutableStateOf(prefs.getLyricsPreferLocal()) }
    var enableTranslation by remember { mutableStateOf(viewModel.isLyricsTranslationEnabled) }
    var targetLang by remember { mutableStateOf(viewModel.lyricsTranslationLang) }
    var showLangDialog by remember { mutableStateOf(false) }

    if (showLangDialog) {
        val systemLangCode = java.util.Locale.getDefault().language
        val systemLabel = stringResource(R.string.theme_system)
        val allLanguages = remember {
            val locales = java.util.Locale.getISOLanguages()
                .map { code ->
                    val loc = java.util.Locale.forLanguageTag(code)
                    code to loc.getDisplayLanguage(loc).replaceFirstChar { if (it.isLowerCase()) it.titlecase(loc) else it.toString() }
                }
                .filter { it.second.isNotBlank() && it.first.length == 2 }
                .distinctBy { it.first }
                .sortedBy { it.second }
            val list = mutableListOf<Pair<String, String>>()
            val systemLoc = locales.find { it.first == systemLangCode }
            if (systemLoc != null) list.add(systemLoc.first to "${systemLoc.second} ($systemLabel)")
            list.addAll(locales.filter { it.first != systemLangCode })
            list
        }
        AlertDialog(
            onDismissRequest = { showLangDialog = false },
            title = { Text(stringResource(R.string.pref_lyrics_translation_lang)) },
            text = {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                    items(allLanguages) { (code, name) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                targetLang = code; showLangDialog = false
                                viewModel.updateLyricsTranslationLang(code)
                            }.padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = (targetLang == code), onClick = null)
                            Spacer(Modifier.width(8.dp))
                            Text(name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showLangDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    com.alananasss.kittytune.ui.common.KittyModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.pref_lyrics_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.btn_close), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SettingsSectionLabel(stringResource(R.string.pref_lyrics_provider_title))
            Spacer(Modifier.height(8.dp))
            val providers = listOf(
                com.alananasss.kittytune.ui.player.LyricsProvider.MAX_QUALITY to stringResource(R.string.pref_lyrics_provider_musixmatch_short),
                com.alananasss.kittytune.ui.player.LyricsProvider.OPEN_SOURCE to stringResource(R.string.pref_lyrics_provider_lrclib_short)
            )
            ExpressiveConnectedButtonGroup(
                options = providers,
                selectedOption = providers.firstOrNull { it.first == viewModel.lyricsProvider } ?: providers.first(),
                onOptionSelected = { viewModel.updateLyricsProvider(it.first) },
                labelProvider = { (_, label) -> Text(label, maxLines = 1, fontWeight = FontWeight.Bold) }
            )

            Spacer(Modifier.height(20.dp))

            val currentOffsetMs = viewModel.lyricsOffset
            val currentOffsetSec = currentOffsetMs / 1000f
            val sign = if (currentOffsetMs > 0) "+" else ""
            val offsetColor = if (currentOffsetMs != 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant

            SettingsSectionLabel(
                text = stringResource(R.string.lyrics_sync),
                trailing = {
                    Text(
                        text = String.format(java.util.Locale.US, "%s%.2fs", sign, currentOffsetSec),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = offsetColor
                    )
                }
            )
            Spacer(Modifier.height(8.dp))
            val offsetOptions = remember {
                listOf(
                    "-1s" to -1000L,
                    "-.1s" to -100L,
                    "0s" to 0L,
                    "+.1s" to 100L,
                    "+1s" to 1000L
                )
            }
            var selectedOffsetOption by remember {
                mutableStateOf(offsetOptions.firstOrNull { it.second == currentOffsetMs } ?: if (currentOffsetMs == 0L) offsetOptions.firstOrNull { it.second == 0L } else null)
            }

            ExpressiveConnectedButtonGroup(
                options = offsetOptions,
                selectedOption = selectedOffsetOption,
                onOptionSelected = { option ->
                    selectedOffsetOption = option
                    if (option.second == 0L) viewModel.lyricsOffset = 0L
                    else viewModel.adjustLyricsOffset(option.second)
                },
                labelProvider = { (label, _) ->
                    Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            )

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel(
                text = stringResource(R.string.pref_lyrics_size),
                trailing = {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "${fontSize.roundToInt()} sp",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            )
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledIconButton(
                    onClick = { viewModel.updateLyricsFontSize((fontSize - 2f).coerceAtLeast(12f)) },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) { Icon(Icons.Rounded.Remove, null, modifier = Modifier.size(16.dp)) }
                Slider(
                    value = fontSize,
                    onValueChange = { viewModel.updateLyricsFontSize(it) },
                    valueRange = 12f..48f,
                    steps = 17,
                    modifier = Modifier.weight(1f)
                )
                FilledIconButton(
                    onClick = { viewModel.updateLyricsFontSize((fontSize + 2f).coerceAtMost(48f)) },
                    modifier = Modifier.size(36.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) { Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp)) }
            }

            Spacer(Modifier.height(20.dp))

            SettingsSectionLabel(stringResource(R.string.pref_lyrics_align))
            Spacer(Modifier.height(8.dp))
            val alignments = listOf(
                LyricsAlignment.LEFT to (Icons.AutoMirrored.Rounded.FormatAlignLeft to stringResource(R.string.align_left)),
                LyricsAlignment.CENTER to (Icons.Rounded.FormatAlignCenter to stringResource(R.string.align_center_simple)),
                LyricsAlignment.RIGHT to (Icons.AutoMirrored.Rounded.FormatAlignRight to stringResource(R.string.align_right))
            )
            ExpressiveConnectedButtonGroup(
                options = alignments,
                selectedOption = alignments.firstOrNull { it.first == alignment } ?: alignments.first(),
                onOptionSelected = { viewModel.updateLyricsAlignment(it.first) },
                iconProvider = { (_, iconAndLabel) ->
                    Icon(iconAndLabel.first, null, modifier = Modifier.size(16.dp))
                },
                labelProvider = { (_, iconAndLabel) ->
                    Text(iconAndLabel.second, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                }
            )

            Spacer(Modifier.height(20.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            LyricsToggleRow(
                title = stringResource(R.string.pref_lyrics_local),
                subtitle = stringResource(R.string.pref_lyrics_local_sub),
                checked = preferLocal,
                onCheckedChange = { preferLocal = it; prefs.setLyricsPreferLocal(it) }
            )

            LyricsToggleRow(
                title = stringResource(R.string.pref_lyrics_word_sync),
                subtitle = stringResource(R.string.pref_lyrics_word_sync_sub),
                checked = viewModel.isWordSyncEnabled,
                onCheckedChange = { viewModel.toggleWordSync(it) }
            )

            AnimatedVisibility(
                visible = viewModel.isWordSyncEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                LyricsToggleRow(
                    title = stringResource(R.string.pref_lyrics_apple_effect),
                    subtitle = stringResource(R.string.pref_lyrics_apple_effect_sub),
                    checked = viewModel.isAppleMusicEffectEnabled,
                    onCheckedChange = { viewModel.toggleAppleMusicEffect(it) }
                )
            }

            LyricsToggleRow(
                title = stringResource(R.string.pref_lyrics_translation_title),
                subtitle = stringResource(R.string.pref_lyrics_translation_sub),
                checked = enableTranslation,
                onCheckedChange = {
                    enableTranslation = it
                    viewModel.toggleLyricsTranslation(it)
                }
            )

            AnimatedVisibility(
                visible = enableTranslation,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    onClick = { showLangDialog = true },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(stringResource(R.string.pref_lyrics_translation_lang), style = MaterialTheme.typography.bodyMedium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(targetLang.uppercase(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            Icon(Icons.Rounded.ArrowDropDown, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }

            LyricsToggleRow(
                title = stringResource(R.string.pref_lyrics_romanization),
                subtitle = stringResource(R.string.pref_lyrics_romanization_sub),
                checked = viewModel.isRomanizationEnabled,
                onCheckedChange = { viewModel.toggleRomanization(it) }
            )

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))

            Button(
                onClick = { onDismiss(); viewModel.isSearchingLyrics = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shapes = ButtonDefaults.shapes()
            ) {
                Icon(Icons.Rounded.Search, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.lyrics_manual_search), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SettingsSectionLabel(
    text: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp)
        )
        trailing?.invoke()
    }
}

@Composable
private fun LyricsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    indented: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

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

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = true)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.custom_lyrics_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, stringResource(R.string.btn_close)) }
                }

                Spacer(Modifier.height(16.dp))

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
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.Underline),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { uriHandler.openUri(docUrl) }
                )

                Spacer(Modifier.height(24.dp))

                Button(
                    onClick = { launcher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(stringResource(R.string.btn_import_yaml))
                }
            }
        }
    }
}
