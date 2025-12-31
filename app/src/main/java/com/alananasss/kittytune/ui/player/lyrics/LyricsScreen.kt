package com.alananasss.kittytune.ui.player.lyrics

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.local.LyricsAlignment
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.utils.makeTimeString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val currentPosition = viewModel.currentPosition
    val lyrics = viewModel.lyricsLines
    val listState = rememberLazyListState()

    val isSearching = viewModel.isSearchingLyrics

    val fontSize = viewModel.lyricsFontSize
    val alignment = when(viewModel.lyricsAlignment) {
        LyricsAlignment.LEFT -> TextAlign.Left
        LyricsAlignment.CENTER -> TextAlign.Center
        LyricsAlignment.RIGHT -> TextAlign.Right
    }

    val activeIndex = remember(currentPosition, lyrics) {
        lyrics.indexOfFirst { currentPosition >= it.startTime && currentPosition < it.endTime }
            .takeIf { it != -1 }
            ?: lyrics.indexOfLast { currentPosition >= it.startTime }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0 && !listState.isScrollInProgress && !isSearching) {
            listState.animateScrollToItem(index = activeIndex)
        }
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
                        IconButton(onClick = { viewModel.isSearchingLyrics = true }) {
                            Icon(Icons.Rounded.Search, stringResource(R.string.lyrics_manual_search), tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
            }
        }
    ) { innerPadding ->

        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            val screenHeight = maxHeight
            val halfHeight = screenHeight / 2

            if (isSearching) {
                SearchLyricsView(
                    viewModel = viewModel,
                    onCloseSearch = { viewModel.isSearchingLyrics = false }
                )
            }
            else {
                if (lyrics.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(stringResource(R.string.lyrics_no_data), color = Color.White.copy(0.7f), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.isSearchingLyrics = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                        ) {
                            Icon(Icons.Rounded.Search, null)
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.lyrics_manual_search))
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = halfHeight - 50.dp, bottom = halfHeight),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        itemsIndexed(lyrics) { index, line ->
                            val isActive = index == activeIndex

                            val targetScale = if (isActive) 1.05f else 0.95f
                            val targetAlpha = if (isActive) 1f else 0.5f
                            val blurRadius = if (isActive) 0.dp else 2.dp

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
                                    .blur(blurRadius)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { viewModel.seekTo(line.startTime) }
                            )
                        }
                    }

                    Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)) {
                        Surface(
                            onClick = { viewModel.isSearchingLyrics = true },
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
            }
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
                    viewModel.searchLyricsManual(query)
                    focusManager.clearFocus()
                })
            )
            IconButton(onClick = {
                viewModel.searchLyricsManual(query)
                focusManager.clearFocus()
            }) {
                Icon(Icons.Rounded.Search, stringResource(R.string.search_hint), tint = Color.White)
            }
        }

        if (viewModel.isLyricsLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = Color.White)
        }

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.lyricSearchResults) { result ->
                Card(
                    onClick = { viewModel.selectLyricResult(result) },
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
                            Text(makeTimeString((result.duration * 1000).toLong()), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(0.7f))
                            if (!result.syncedLyrics.isNullOrEmpty()) {
                                Icon(Icons.Rounded.Timer, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}