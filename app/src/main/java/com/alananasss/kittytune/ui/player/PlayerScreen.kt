package com.alananasss.kittytune.ui.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val track = viewModel.currentTrack ?: return
    // handle back press if lyrics sheet is open
    BackHandler(enabled = !viewModel.showLyricsSheet, onBack = onClose)

    val context = LocalContext.current
    val prefs = remember { PlayerPreferences(context) }
    val backgroundStyle = remember { prefs.getPlayerStyle() }

    val queueSheetState = rememberBottomSheetScaffoldState()
    var showEffectsSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

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
            .background(MaterialTheme.colorScheme.background)
            .pointerInput(Unit) { detectTapGestures { } }
    ) {
        // dynamic background based on settings
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
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
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
                    onCloseQueue = { scope.launch { queueSheetState.bottomSheetState.partialExpand() } }
                )
            }
        ) { paddingValues ->
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp).systemBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerHeader(onClose, viewModel)
                    Spacer(modifier = Modifier.weight(1f))

                    // album art with shadow
                    AsyncImage(
                        model = track.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f).shadow(24.dp, RoundedCornerShape(20.dp), spotColor = if (backgroundStyle == PlayerBackgroundStyle.THEME) Color.Black else animatedColor).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    // track info with marquee
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = track.title ?: stringResource(R.string.untitled_track), style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onBackground, maxLines = 1, modifier = Modifier.basicMarquee())
                            Text(
                                text = track.user?.username ?: stringResource(R.string.unknown_artist),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                maxLines = 1,
                                modifier = Modifier.basicMarquee().clickable { track.user?.id?.let { if (it > 0) viewModel.navigateToArtist(it) } }
                            )
                        }
                        IconButton(onClick = { viewModel.toggleLike() }) {
                            val heartColor by animateColorAsState(targetValue = if (viewModel.isLiked) Color(0xFFFF4081) else MaterialTheme.colorScheme.onSurfaceVariant, label = "heart")
                            Icon(imageVector = if (viewModel.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder, contentDescription = stringResource(R.string.player_like_action), tint = heartColor, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    PlayerProgress(viewModel)
                    Spacer(modifier = Modifier.height(16.dp))
                    PlayerControls(viewModel = viewModel, animatedMainColor = animatedColor, onEffectsClick = { showEffectsSheet = true }, onQueueClick = { scope.launch { if (queueSheetState.bottomSheetState.currentValue == SheetValue.Expanded) queueSheetState.bottomSheetState.partialExpand() else queueSheetState.bottomSheetState.expand() } })
                    Spacer(modifier = Modifier.weight(1f))
                }

                // overlay to close queue when tapping outside
                if (isQueueOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Transparent)
                            .zIndex(2f) // keep above player controls
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
fun PlayerHeader(onClose: () -> Unit, viewModel: PlayerViewModel) {
    val context = viewModel.currentContext

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Default.KeyboardArrowDown, stringResource(R.string.btn_close), tint = MaterialTheme.colorScheme.onBackground)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                stringResource(R.string.player_playing_now),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            if (context != null) {
                Text(
                    text = context.displayText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    modifier = Modifier
                        .basicMarquee()
                        .clickable { viewModel.navigateToContext() }
                )
            }
        }

        IconButton(onClick = {
            viewModel.currentTrack?.let {
                viewModel.showTrackOptions(it, fromPlayer = true)
            }
        }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.btn_options), tint = MaterialTheme.colorScheme.onBackground)
        }
    }
}

data class DockOptionItem(val icon: ImageVector, val text: String, val onClick: () -> Unit)

@Composable
fun MenuSheetContent(viewModel: PlayerViewModel) {
    val track = viewModel.trackForMenu ?: viewModel.currentTrack ?: return
    val context = LocalContext.current
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val storageTrigger by DownloadManager.storageTrigger.collectAsState()

    val isLocalFile = track.id < 0
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(if (isLocalFile) stringResource(R.string.menu_remove_local_q) else stringResource(R.string.menu_remove_download_q)) },
            text = { Text(if (isLocalFile) stringResource(R.string.menu_remove_local_body) else stringResource(R.string.menu_remove_download_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        DownloadManager.deleteTrack(track.id)
                        showDeleteDialog = false
                        viewModel.showMenuSheet = false
                    }
                ) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp).padding(horizontal = 8.dp)) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
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
                Text(
                    text = track.user?.username ?: stringResource(R.string.unknown_artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // build grid items list
        val gridItems = remember(viewModel.isMenuContextFromPlayer, isLocalFile, viewModel.menuContextPlaylistId) {
            mutableListOf<DockOptionItem>().apply {
                if (viewModel.isMenuContextFromPlayer) {
                    add(DockOptionItem(Icons.Rounded.Shuffle, context.getString(R.string.menu_shuffle)) { viewModel.toggleShuffle(); viewModel.showMenuSheet = false })
                    add(DockOptionItem(Icons.Rounded.Repeat, context.getString(R.string.menu_repeat)) { viewModel.toggleRepeatMode() })
                }

                if (!viewModel.isMenuContextFromPlayer) {
                    add(DockOptionItem(Icons.AutoMirrored.Rounded.PlaylistPlay, context.getString(R.string.menu_play_next)) { viewModel.insertNext(listOf(track)); viewModel.showMenuSheet = false })
                    add(DockOptionItem(Icons.AutoMirrored.Rounded.QueueMusic, context.getString(R.string.menu_add_queue)) { viewModel.addToQueue(listOf(track)); viewModel.showMenuSheet = false })
                }

                if (!isLocalFile) {
                    add(DockOptionItem(Icons.AutoMirrored.Rounded.Comment, context.getString(R.string.menu_comments)) { viewModel.openComments(track) })
                }

                add(DockOptionItem(Icons.Rounded.Info, context.getString(R.string.menu_details)) { viewModel.openTrackDetails(track) })
                add(DockOptionItem(Icons.Rounded.Description, context.getString(R.string.player_lyrics)) { viewModel.openLyrics(track) })
                add(DockOptionItem(Icons.Default.Add, context.getString(R.string.menu_add_playlist)) { viewModel.showMenuSheet = false; viewModel.showAddToPlaylistSheet = true })

                if (!isLocalFile) {
                    add(DockOptionItem(Icons.Default.Person, context.getString(R.string.menu_go_artist)) { track.user?.id?.let { viewModel.navigateToArtist(it) } })
                }

                if (viewModel.isMenuContextFromPlayer && !isLocalFile) {
                    add(DockOptionItem(Icons.Rounded.Radio, context.getString(R.string.menu_track_radio)) { viewModel.loadTrackStation() })
                }

                if (viewModel.menuContextPlaylistId != null && viewModel.menuContextPlaylistId!! < 0) {
                    add(DockOptionItem(Icons.Outlined.Delete, context.getString(R.string.menu_remove)) { viewModel.removeFromContextPlaylist(viewModel.menuContextPlaylistId!!, track) })
                }

                if (!viewModel.isMenuContextFromPlayer && track.id > 0 && !isLocalFile) {
                    add(DockOptionItem(Icons.Outlined.Share, context.getString(R.string.btn_share)) { viewModel.shareTrack(track) })
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            items(gridItems) { item ->
                val activeColor = MaterialTheme.colorScheme.primary
                val inactiveColor = MaterialTheme.colorScheme.onSurface
                var tint = inactiveColor
                var text = item.text

                // highlight active states
                if (item.text == stringResource(R.string.menu_shuffle) && viewModel.shuffleEnabled) {
                    tint = activeColor
                }
                if (item.text == stringResource(R.string.menu_repeat)) {
                    if (viewModel.repeatMode != RepeatMode.NONE) tint = activeColor
                    text = when (viewModel.repeatMode) {
                        RepeatMode.ALL -> stringResource(R.string.menu_repeat_all)
                        RepeatMode.ONE -> stringResource(R.string.menu_repeat_one)
                        else -> stringResource(R.string.menu_repeat)
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { item.onClick() }
                ) {
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
                    val isDownloaded = remember(trackId, storageTrigger) {
                        File(context.filesDir, "track_${trackId}.mp3").exists()
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable {
                            if (isDownloaded) {
                                showDeleteDialog = true
                            } else if (isDownloading) {
                                DownloadManager.cancelDownload(trackId)
                            } else {
                                DownloadManager.downloadTrack(track)
                            }
                        }
                    ) {
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
        Text(
            if (bulkTracks != null) stringResource(R.string.add_to_playlist_title_multi, bulkTracks.size) else stringResource(R.string.add_to_playlist_title_single),
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
                    if(newName.isNotBlank()) {
                        if (bulkTracks != null) viewModel.createAndAddTracksToPlaylist(newName, bulkTracks)
                        else if (singleTrack != null) viewModel.createAndAddToPlaylist(newName, singleTrack)
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.add_to_playlist_new), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
            itemsIndexed(items = viewModel.userPlaylists) { _, playlist ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (bulkTracks != null) viewModel.addTracksToPlaylist(playlist.id, bulkTracks)
                            else if (singleTrack != null) viewModel.addToPlaylist(playlist.id, singleTrack)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = playlist.localCoverPath ?: playlist.artworkUrl.ifEmpty { "https://picsum.photos/200" },
                        contentDescription = null,
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(playlist.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.playlist_num_tracks, playlist.trackCount), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@Composable
fun QueueContent(viewModel: PlayerViewModel, onCloseQueue: () -> Unit) {
    val itemHeight = 72.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }
    val view = LocalView.current

    Column(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.6f).background(MaterialTheme.colorScheme.surfaceContainer).pointerInput(Unit) { detectTapGestures { } }) {
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer)
                .pointerInput(Unit) { detectVerticalDragGestures { change, dragAmount -> change.consume(); if (dragAmount > 10) onCloseQueue() } }
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = stringResource(R.string.player_queue), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(horizontal = 24.dp))
        }

        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            itemsIndexed(items = viewModel.queueState, key = { index, track -> "${track.id}_$index" }) { index, track ->
                val isCurrent = track.id == viewModel.currentTrack?.id
                var offsetY by remember { mutableFloatStateOf(0f) }
                var isDragging by remember { mutableStateOf(false) }
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "scale")
                val backgroundColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainer

                Row(
                    modifier = Modifier.fillMaxWidth().height(itemHeight).zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer { translationY = offsetY; scaleX = scale; scaleY = scale; shadowElevation = elevation.toPx() }
                        .background(backgroundColor)
                        .clickable {
                            viewModel.skipToQueueItem(index)
                        }
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = track.fullResArtwork, contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = track.title ?: stringResource(R.string.generic_title), style = MaterialTheme.typography.bodyLarge, fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, maxLines = 1)
                        Text(text = track.user?.username ?: stringResource(R.string.generic_artist), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                    Icon(
                        imageVector = Icons.Rounded.DragHandle, contentDescription = "Move",
                        tint = if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(32.dp).pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { isDragging = true; view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                                onDragEnd = { isDragging = false; offsetY = 0f },
                                onDragCancel = { isDragging = false; offsetY = 0f },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    offsetY += dragAmount.y
                                    if (offsetY > itemHeightPx) {
                                        val nextIndex = index + 1
                                        if (nextIndex < viewModel.queueState.size) { viewModel.moveQueueItem(index, nextIndex); offsetY -= itemHeightPx; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    } else if (offsetY < -itemHeightPx) {
                                        val prevIndex = index - 1
                                        if (prevIndex >= 0) { viewModel.moveQueueItem(index, prevIndex); offsetY += itemHeightPx; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}
@Composable
fun PlayerProgress(viewModel: PlayerViewModel) {
    val view = LocalView.current
    var isDragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(0f) }
    val totalDuration = viewModel.duration.coerceAtLeast(1L).toFloat()
    val currentPos = viewModel.currentPosition.coerceAtMost(viewModel.duration).toFloat()

    val progressState = remember { Animatable(0f) }
    val sliderPosition = if (isDragging) dragPosition else progressState.value

    LaunchedEffect(currentPos, isDragging) {
        if (!isDragging) {
            val target = currentPos
            val distance = kotlin.math.abs(progressState.value - target)
            // snap if too far
            if (distance > 2000f) {
                progressState.animateTo(target, tween(400, easing = FastOutSlowInEasing))
            } else {
                progressState.animateTo(target, tween(1000, easing = LinearEasing))
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val rangeEnd = kotlin.math.max(totalDuration, sliderPosition)
        Slider(
            value = sliderPosition.coerceIn(0f, rangeEnd),
            valueRange = 0f..rangeEnd,
            onValueChange = {
                isDragging = true
                dragPosition = it
                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            },
            onValueChangeFinished = {
                viewModel.seekTo(dragPosition.toLong())
                isDragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onBackground,
                activeTrackColor = MaterialTheme.colorScheme.onBackground,
                inactiveTrackColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = makeTimeString(sliderPosition.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Text(
                text = makeTimeString(totalDuration.toLong()),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PlayerControls(viewModel: PlayerViewModel, onEffectsClick: () -> Unit, onQueueClick: () -> Unit, animatedMainColor: Color = MaterialTheme.colorScheme.primary) {
    val buttonWidth by animateDpAsState(targetValue = if (viewModel.isPlaying) 110.dp else 72.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "width")
    val buttonColor = if (viewModel.isPlaying) animatedMainColor else MaterialTheme.colorScheme.surfaceVariant

    val isButtonLight = buttonColor.luminance() > 0.4f

    val targetContentColor = if (viewModel.isPlaying) {
        if (isButtonLight) Color(0xFF1D1B20) else Color.White
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val contentColor by animateColorAsState(targetValue = targetContentColor, label = "contentColor")

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onEffectsClick) { Icon(Icons.Default.Equalizer, stringResource(R.string.player_effects), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(28.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            IconButton(onClick = { viewModel.smartPrevious() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.SkipPrevious, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp)) }

            // bouncy play button
            Box(modifier = Modifier.height(72.dp).width(buttonWidth).clip(CircleShape).background(buttonColor).clickable { viewModel.togglePlayPause() }, contentAlignment = Alignment.Center) {
                if (viewModel.isLoading) CircularProgressIndicator(color = contentColor, modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                else AnimatedContent(targetState = viewModel.isPlaying, transitionSpec = { (scaleIn() + fadeIn()).togetherWith(scaleOut() + fadeOut()) }, label = "icon") { isPlaying ->
                    Icon(imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null, tint = contentColor, modifier = Modifier.size(32.dp))
                }
            }

            IconButton(onClick = { viewModel.playNext() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Rounded.SkipNext, null, tint = MaterialTheme.colorScheme.onBackground, modifier = Modifier.size(36.dp)) }
        }
        IconButton(onClick = onQueueClick) { Icon(Icons.AutoMirrored.Rounded.QueueMusic, stringResource(R.string.player_queue), tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), modifier = Modifier.size(28.dp)) }
    }
}

@Composable
fun AudioControlDock(viewModel: PlayerViewModel) {
    val view = LocalView.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
        Text(stringResource(R.string.player_audio_settings), style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), modifier = Modifier.padding(bottom = 24.dp))
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(20.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(text = "${viewModel.effectsState.speed}x", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                FilterChip(
                    selected = viewModel.effectsState.isPitchEnabled,
                    onClick = { viewModel.togglePitchEnabled(!viewModel.effectsState.isPitchEnabled) },
                    label = { Text(stringResource(R.string.player_pitch)) },
                    leadingIcon = if (viewModel.effectsState.isPitchEnabled) { { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) } } else null,
                    shape = CircleShape,
                    colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary, selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary)
                )
            }
            Spacer(Modifier.height(16.dp))
            Slider(
                value = viewModel.effectsState.speed,
                onValueChange = { if (it != viewModel.effectsState.speed) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK); viewModel.setCustomSpeed(it) },
                valueRange = 0.5f..2.0f, steps = 14, modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.player_special_effects), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DockButton(stringResource(R.string.effect_bass_boost), Icons.Rounded.Bolt, viewModel.effectsState.isBassBoostEnabled, { viewModel.toggleBassBoost() }, Modifier.weight(1f))
            DockButton(stringResource(R.string.effect_8d), Icons.Rounded.SurroundSound, viewModel.effectsState.is8DEnabled, { viewModel.toggle8D() }, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DockButton(stringResource(R.string.effect_muffled), Icons.Rounded.BlurOn, viewModel.effectsState.isMuffledEnabled, { viewModel.toggleMuffled() }, Modifier.weight(1f))
            DockButton(stringResource(R.string.effect_reverb), Icons.Rounded.GraphicEq, viewModel.effectsState.isReverbEnabled, { viewModel.toggleReverb() }, Modifier.weight(1f))
        }
    }
}
@Composable
fun DockButton(label: String, icon: ImageVector, isActive: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val containerColor by animateColorAsState(if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, label = "Color")
    val contentColor by animateColorAsState(if (isActive) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, label = "ContentColor")
    val cornerRadius by animateDpAsState(targetValue = if (isActive) 100.dp else 20.dp, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow), label = "Corner")

    Surface(onClick = onClick, modifier = modifier.height(80.dp), shape = RoundedCornerShape(cornerRadius), color = containerColor) {
        Column(verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxSize()) {
            val iconScale by animateFloatAsState(if (isActive) 1.1f else 1f, label = "Scale")
            Icon(imageVector = icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(28.dp).graphicsLayer { scaleX = iconScale; scaleY = iconScale })
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelMedium, color = contentColor, fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium)
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsSheetContent(
    viewModel: PlayerViewModel,
    onClose: () -> Unit
) {
    val comments = viewModel.commentsList
    val isLoading = viewModel.isCommentsLoading
    val context = LocalContext.current

    var fakeCommentText by remember { mutableStateOf("") }

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
                            Icon(Icons.Rounded.KeyboardArrowDown, stringResource(R.string.btn_close))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            }
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 16.dp,
                shadowElevation = 16.dp,
                modifier = Modifier
                    .imePadding()
                    .navigationBarsPadding()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TextField(
                        value = fakeCommentText,
                        onValueChange = { fakeCommentText = it },
                        placeholder = { Text(stringResource(R.string.add_comment_hint)) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(24.dp),
                        colors = TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        maxLines = 4,
                        enabled = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = {
                            Toast.makeText(context, context.getString(R.string.dialog_comment_soon), Toast.LENGTH_SHORT).show()
                        })
                    )
                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            Toast.makeText(context, context.getString(R.string.dialog_comment_soon), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.Send,
                            stringResource(R.string.comment_send_action),
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) { innerPadding ->
        if (comments.isEmpty() && isLoading) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (comments.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Rounded.ChatBubbleOutline, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(R.string.comment_no_comments), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
            ) {
                items(
                    count = comments.size,
                    key = { index -> comments[index].id }
                ) { index ->
                    val comment = comments[index]
                    val userId = comment.user?.id ?: 0L
                    CommentRowItem(
                        comment = comment,
                        onNavigateToProfile = { if (userId != 0L) viewModel.navigateToArtist(userId) },
                        onSeekTo = { pos ->
                            val trackForComment = viewModel.selectedTrackForSheet
                            if (trackForComment != null) {
                                if (trackForComment.id == viewModel.currentTrack?.id) {
                                    viewModel.seekTo(pos)
                                } else {
                                    viewModel.playTrackAtPosition(trackForComment, pos)
                                }
                            }
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)
                    )
                }

                if (viewModel.commentNextHref != null) {
                    item {
                        LaunchedEffect(Unit) { viewModel.loadComments() }
                        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun CommentRowItem(
    comment: Comment,
    onNavigateToProfile: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val context = LocalContext.current
    val avatarUrl = comment.user?.avatarUrl
    val username = comment.user?.username ?: stringResource(R.string.comment_anonymous)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onNavigateToProfile() },
            contentAlignment = Alignment.Center
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
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = username,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false).clickable { onNavigateToProfile() }
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = getRelativeTime(comment.createdAt, context),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(4.dp))

            Text(
                text = comment.body,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 22.sp),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
            )

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
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(12.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = makeTimeString(comment.trackTimestamp),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable {
                            Toast.makeText(context, context.getString(R.string.dialog_like_soon), Toast.LENGTH_SHORT).show()
                        }
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FavoriteBorder,
                        contentDescription = stringResource(R.string.player_like_action),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )

                    if (comment.likesCount > 0) {
                        Spacer(Modifier.width(4.dp))
                        Text(
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
fun formatNumber(count: Int): String {
    if (count < 1000) return count.toString()
    val k = count / 1000.0
    val m = count / 1000000.0
    return when {
        m >= 1.0 -> String.format(Locale.US, "%.1fM", m)
        k >= 1.0 -> String.format(Locale.US, "%.1fk", k)
        else -> count.toString()
    }
}

fun getRelativeTime(dateStr: String, context: Context): String {
    try {
        val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        val date = format.parse(dateStr) ?: return ""
        val diff = System.currentTimeMillis() - date.time

        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        val weeks = days / 7
        val months = days / 30
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
    } catch (e: Exception) { return "" }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DetailsSheetContent(
    track: Track,
    onClose: () -> Unit,
    onOpenComments: () -> Unit,
    viewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    val displayFormat = SimpleDateFormat("d MMMM yyyy", Locale.getDefault())

    val isLocalFile = track.id < 0

    val releaseDateStr = remember(track) {
        if (isLocalFile) context.getString(R.string.detail_local_file) else {
            try {
                val dateStr = track.releaseDate ?: track.createdAt
                if (dateStr != null) {
                    val date = dateFormat.parse(dateStr)
                    displayFormat.format(date ?: Date())
                } else context.getString(R.string.detail_unknown)
            } catch (e: Exception) { context.getString(R.string.detail_unknown) }
        }
    }

    val tags = remember(track.tagList) { parseSoundCloudTags(track.tagList) }

    var fileSizeStr by remember { mutableStateOf("") }
    var fileFormatStr by remember { mutableStateOf("Audio") }
    var cleanPathStr by remember { mutableStateOf("") }

    LaunchedEffect(track) {
        if (isLocalFile && !track.description.isNullOrEmpty()) {
            val rawPath = track.description?.removePrefix("Fichier local: ") ?: ""
            try {
                val uri = Uri.parse(rawPath)
                val ext = uri.path?.substringAfterLast('.', "")?.uppercase()
                fileFormatStr = if (!ext.isNullOrEmpty()) ext else "MP3/Audio"

                // get local file size
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex != -1) {
                            val sizeBytes = it.getLong(sizeIndex)
                            val sizeMb = sizeBytes / (1024.0 * 1024.0)
                            fileSizeStr = context.getString(R.string.detail_file_size_formatted, sizeMb)
                        }
                    }
                }
                cleanPathStr = uri.path?.substringAfter("primary:")?.replace("/", " > ") ?: context.getString(R.string.detail_external_storage)
            } catch (e: Exception) { fileSizeStr = context.getString(R.string.detail_unknown) }
        }
    }

    LazyColumn(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding()) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp, top = 16.dp)) {
                AsyncImage(model = track.fullResArtwork, contentDescription = null, modifier = Modifier.size(80.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(track.title ?: stringResource(R.string.untitled_track), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(text = track.user?.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { onClose(); if (!isLocalFile) track.user?.id?.let { if (it > 0) viewModel.navigateToArtist(it) } })
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(16.dp))
        }

        if (isLocalFile) {
            item {
                Text(stringResource(R.string.detail_file_info), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(16.dp))
                DetailInfoRow(stringResource(R.string.detail_format), fileFormatStr)
                if (fileSizeStr.isNotEmpty()) DetailInfoRow(stringResource(R.string.detail_size), fileSizeStr)
                DetailInfoRow(stringResource(R.string.detail_duration), makeTimeString(track.durationMs ?: 0L))
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.detail_location), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text(text = cleanPathStr.ifEmpty { stringResource(R.string.storage_internal_mem) }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(32.dp))
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    DetailStatItem(Icons.Rounded.PlayArrow, formatNumber(track.playbackCount), stringResource(R.string.detail_stats_plays))
                    DetailStatItem(Icons.Rounded.Favorite, formatNumber(track.likesCount), stringResource(R.string.detail_stats_likes), onClick = { viewModel.navigateToTrackDetails(track.id, 0) })
                    DetailStatItem(Icons.Rounded.Repeat, formatNumber(track.repostsCount), stringResource(R.string.detail_stats_reposts), onClick = { viewModel.navigateToTrackDetails(track.id, 1) })
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                OutlinedButton(onClick = { onClose(); viewModel.navigateToTrackDetails(track.id) }, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(50), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)) {
                    Icon(Icons.Rounded.Hub, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.detail_see_similar), fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(16.dp))
            }
            item {
                Button(onClick = onOpenComments, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) {
                    Icon(Icons.AutoMirrored.Rounded.Comment, null); Spacer(Modifier.width(12.dp)); Text(stringResource(R.string.detail_see_comments, formatNumber(track.commentCount)), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
                }
                Spacer(Modifier.height(24.dp))
            }
            item {
                DetailInfoRow(stringResource(R.string.detail_release_date), releaseDateStr)
                if (!track.genre.isNullOrBlank()) DetailInfoRow(stringResource(R.string.detail_genre), track.genre)
                Spacer(Modifier.height(16.dp))
            }
            if (!track.description.isNullOrBlank()) {
                item {
                    Text(stringResource(R.string.detail_description), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    ExpandableDescription(text = track.description, onUrlClick = { url -> uriHandler.openUri(url) }, onMentionClick = { username -> onClose(); viewModel.resolveAndNavigateToArtist(username) })
                    Spacer(Modifier.height(24.dp))
                }
            }
            if (tags.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.detail_tags), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tags.forEach { tag ->
                            AssistChip(onClick = { onClose(); viewModel.navigateToTag(tag) }, label = { Text("#${tag.uppercase()}") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, labelColor = MaterialTheme.colorScheme.onSurface), border = null, shape = CircleShape)
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

fun parseSoundCloudTags(tagList: String?): List<String> {
    if (tagList.isNullOrBlank()) return emptyList()
    val tags = mutableListOf<String>()
    val pattern = Pattern.compile("\"([^\"]*)\"|(\\S+)")
    val matcher = pattern.matcher(tagList)
    while (matcher.find()) {
        if (matcher.group(1) != null) {
            tags.add(matcher.group(1)!!)
        } else {
            tags.add(matcher.group(2)!!)
        }
    }
    return tags
}

@Composable
fun DetailStatItem(icon: ImageVector, value: String, label: String, onClick: () -> Unit = {}) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(8.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
        Spacer(Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun DetailInfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ExpandableDescription(
    text: String,
    onUrlClick: (String) -> Unit,
    onMentionClick: (String) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }
    // regex for links and mentions
    val urlPattern = Pattern.compile("((https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|])")
    val mentionPattern = Pattern.compile("@[\\w-]+")
    val annotatedString = buildAnnotatedString {
        val fullText = text
        append(fullText)
        val urlMatcher = urlPattern.matcher(fullText)
        while (urlMatcher.find()) {
            addStringAnnotation(tag = "URL", annotation = urlMatcher.group(), start = urlMatcher.start(), end = urlMatcher.end())
            addStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold), start = urlMatcher.start(), end = urlMatcher.end())
        }
        val mentionMatcher = mentionPattern.matcher(fullText)
        while (mentionMatcher.find()) {
            addStringAnnotation(tag = "MENTION", annotation = mentionMatcher.group(), start = mentionMatcher.start(), end = mentionMatcher.end())
            addStyle(style = SpanStyle(color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold), start = mentionMatcher.start(), end = mentionMatcher.end())
        }
    }
    Column(modifier = Modifier.animateContentSize()) {
        ClickableText(
            text = annotatedString,
            style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 20.sp),
            maxLines = if (isExpanded) Int.MAX_VALUE else 5,
            overflow = TextOverflow.Ellipsis,
            onClick = { offset ->
                var isAnnotationClicked = false
                annotatedString.getStringAnnotations(start = offset, end = offset).firstOrNull()?.let { annotation ->
                    when (annotation.tag) {
                        "URL" -> { onUrlClick(annotation.item); isAnnotationClicked = true }
                        "MENTION" -> { onMentionClick(annotation.item); isAnnotationClicked = true }
                    }
                }
                if (!isAnnotationClicked) isExpanded = !isExpanded
            }
        )
        if (text.length > 200) {
            Text(text = if (isExpanded) stringResource(R.string.detail_show_less) else stringResource(R.string.detail_show_more), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp).clickable { isExpanded = !isExpanded })
        }
    }
}