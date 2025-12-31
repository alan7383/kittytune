package com.alananasss.kittytune.ui.library

import android.content.Intent
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.TrackListItemShimmer
import com.alananasss.kittytune.ui.player.PlaybackContext
import com.alananasss.kittytune.ui.player.PlayerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val tracks = remember { mutableStateListOf<Track>() }
    val likedTracksRepo by LikeRepository.likedTracks.collectAsState()

    var playlistTitle by remember { mutableStateOf("") }
    var playlistCover by remember { mutableStateOf<String?>(null) }
    var playlistUser by remember { mutableStateOf<User?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var defaultIcon by remember { mutableStateOf<ImageVector?>(null) }

    val downloadProgress by DownloadManager.downloadProgress.collectAsState()
    val storageTrigger by DownloadManager.storageTrigger.collectAsState()
    var localDownloadRefreshKey by remember { mutableIntStateOf(0) }

    // extract numeric id from compound strings
    val cleanIdStr = playlistId.replace("station_artist:", "")
        .replace("station:", "")
        .replace("local_playlist:", "")
    val currentIdLong = cleanIdStr.toLongOrNull() ?: 0L

    val playlistInDb by DownloadManager.isPlaylistInLibraryFlow(currentIdLong).collectAsState(initial = null)

    var isUserCreated by remember { mutableStateOf(false) }
    var isLocalPlaylist by remember { mutableStateOf(false) }
    var showPlaylistOptionsSheet by remember { mutableStateOf(false) }

    val shareUrl = remember(playlistId, currentIdLong) {
        when {
            playlistId.startsWith("station_artist:") -> "https://soundcloud.com/discover/stations/artist/$currentIdLong"
            playlistId.startsWith("station:") -> "https://soundcloud.com/discover/stations/track/$currentIdLong"
            playlistId == "likes" -> "https://soundcloud.com/you/likes"
            playlistId == "downloads" -> ""
            currentIdLong > 0 -> "https://soundcloud.com/playlists/$currentIdLong"
            else -> ""
        }
    }

    LaunchedEffect(playlistInDb) {
        if (playlistInDb != null) {
            isLocalPlaylist = true
            isUserCreated = playlistInDb!!.isUserCreated || currentIdLong < 0
            playlistTitle = playlistInDb!!.title
            playlistCover = playlistInDb!!.localCoverPath ?: playlistInDb!!.artworkUrl
        } else {
            isLocalPlaylist = currentIdLong < 0
            isUserCreated = currentIdLong < 0
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> if (uri != null && currentIdLong != 0L) DownloadManager.updatePlaylistCover(currentIdLong, uri) }
    )

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    var isFullyDownloaded by remember { mutableStateOf(false) }

    val tracksToDisplay = if (playlistId == "likes") likedTracksRepo else tracks

    LaunchedEffect(tracksToDisplay.size, downloadProgress, localDownloadRefreshKey, storageTrigger) {
        if (tracksToDisplay.isNotEmpty()) {
            isFullyDownloaded = tracksToDisplay.all { track -> File(context.filesDir, "track_${track.id}.mp3").exists() }
        } else {
            isFullyDownloaded = false
        }
    }

    // --- MAIN DATA LOADING ---
    LaunchedEffect(playlistId) {
        isLoading = true
        tracks.clear()
        try {
            val api = RetrofitClient.create(context)
            val db = AppDatabase.getDatabase(context).downloadDao()

            when {
                playlistId == "likes" -> {
                    playlistTitle = context.getString(R.string.lib_liked_tracks)
                    defaultIcon = Icons.Rounded.Favorite
                    try {
                        val user = api.getMe()
                        playlistUser = user
                    } catch (e: Exception) {
                        playlistUser = User(0, context.getString(R.string.me_artist), null) // fallback
                    }
                    isLoading = false
                }
                playlistId == "downloads" -> {
                    playlistTitle = context.getString(R.string.lib_downloads)
                    defaultIcon = Icons.Rounded.Folder
                    isLocalPlaylist = false
                    val localTracks = db.getAllTracks().first()
                    tracks.addAll(localTracks.map { local ->
                        Track(local.id, local.title, local.artworkUrl, local.duration, User(0, local.artist, null), isLiked = true)
                    })
                    isLoading = false
                }
                playlistId == "local_files" -> {
                    playlistTitle = context.getString(R.string.lib_local_media)
                    defaultIcon = Icons.Default.SdStorage
                    isLocalPlaylist = false

                    val allTracks = db.getAllTracks().first()
                    // only grab files with negative ids (local scans)
                    val localFileTracks = allTracks.filter { it.id < 0 }

                    tracks.addAll(localFileTracks.map { local ->
                        Track(
                            id = local.id,
                            title = local.title,
                            artworkUrl = local.artworkUrl,
                            durationMs = local.duration,
                            user = User(0, local.artist, null),
                            description = "Fichier local: ${local.localAudioPath}"
                        )
                    })
                    isLoading = false
                }
                else -> {
                    val localPlaylist = if (currentIdLong != 0L) db.getPlaylist(currentIdLong) else null
                    if (localPlaylist != null) {
                        // LOAD FROM DB
                        playlistTitle = localPlaylist.title
                        playlistCover = localPlaylist.localCoverPath ?: localPlaylist.artworkUrl
                        playlistUser = User(0, localPlaylist.artist, null)
                        isUserCreated = localPlaylist.isUserCreated || currentIdLong < 0
                        isLocalPlaylist = true

                        launch {
                            db.getTracksForPlaylist(currentIdLong).collect { playlistTracks ->
                                tracks.clear()
                                tracks.addAll(playlistTracks.map { local ->
                                    Track(local.id, local.title, local.artworkUrl, local.duration, User(0, local.artist, null))
                                })
                                isLoading = false
                            }
                        }
                    } else {
                        // LOAD FROM NETWORK
                        if (currentIdLong > 0L) {
                            val isArtistStation = playlistId.startsWith("station_artist:")
                            val isTrackStation = playlistId.startsWith("station:")
                            val playlistObj = when {
                                isArtistStation -> api.getArtistStation(currentIdLong)
                                isTrackStation -> api.getTrackStation(currentIdLong)
                                else -> api.getPlaylist(currentIdLong)
                            }
                            playlistTitle = playlistObj.title.takeIf { !it.isNullOrBlank() } ?: context.getString(R.string.radio)
                            playlistCover = playlistObj.fullResArtwork
                            playlistUser = playlistObj.user
                            val rawTracks = playlistObj.tracks ?: emptyList()

                            // soundcloud sometimes returns incomplete track objects in playlists
                            // we need to fetch full details for those
                            val incompleteIds = rawTracks.filter { it.title.isNullOrBlank() || it.user == null }.map { it.id }
                            if (incompleteIds.isNotEmpty()) {
                                val fetchedTracksMap = mutableMapOf<Long, Track>()
                                val chunks = incompleteIds.chunked(50)
                                chunks.forEach { batchIds ->
                                    try {
                                        val fetched = api.getTracksByIds(batchIds.joinToString(","))
                                        fetched.forEach { fetchedTracksMap[it.id] = it }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                val hydratedList = rawTracks.map { track ->
                                    if (track.title.isNullOrBlank() || track.user == null) fetchedTracksMap[track.id] ?: track else track
                                }
                                tracks.addAll(hydratedList)
                            } else {
                                tracks.addAll(rawTracks)
                            }
                            isLoading = false
                        }
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace(); isLoading = false }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(if (isUserCreated) R.string.dialog_delete_playlist_title else R.string.dialog_delete_playlist_from_lib_title)) },
            text = { Text(stringResource(R.string.dialog_delete_playlist_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    if (currentIdLong != 0L) { DownloadManager.deletePlaylist(currentIdLong); onBackClick() }
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.btn_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showRemoveDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDownloadDialog = false },
            title = { Text(stringResource(R.string.dialog_remove_download_title)) },
            text = { Text(stringResource(R.string.dialog_remove_download_msg)) },
            confirmButton = {
                TextButton(onClick = {
                    if (currentIdLong != 0L) {
                        DownloadManager.removePlaylistDownloads(currentIdLong)
                        scope.launch { delay(100); localDownloadRefreshKey++ }
                    }
                    showRemoveDownloadDialog = false
                }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showRemoveDownloadDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.dialog_rename_playlist_title)) },
            text = { OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it }, label = { Text(stringResource(R.string.dialog_rename_playlist_hint)) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { if (currentIdLong != 0L && newPlaylistName.isNotBlank()) DownloadManager.renamePlaylist(currentIdLong, newPlaylistName); showRenameDialog = false }) { Text(stringResource(R.string.btn_save)) } },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
        )
    }

    val playbackContext = remember(playlistId, playlistTitle, playlistCover) {
        when {
            playlistId == "likes" -> PlaybackContext(context.getString(R.string.context_playlist, context.getString(R.string.lib_liked_tracks)), "likes", playlistCover)
            playlistId == "downloads" -> PlaybackContext(context.getString(R.string.context_playlist, context.getString(R.string.lib_downloads)), "downloads", playlistCover)
            playlistId.startsWith("station") -> PlaybackContext(context.getString(R.string.context_station, playlistTitle), playlistId, playlistCover)
            else -> PlaybackContext(context.getString(R.string.context_playlist, playlistTitle), playlistId, playlistCover)
        }
    }

    val backgroundColor = MaterialTheme.colorScheme.background

    Scaffold(containerColor = backgroundColor) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (!playlistCover.isNullOrEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                    AsyncImage(model = playlistCover, contentDescription = null, modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.6f), contentScale = ContentScale.Crop)
                    Box(modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                backgroundColor.copy(alpha = 0.3f),
                                backgroundColor.copy(alpha = 0.8f),
                                backgroundColor
                            )
                        )
                    ))
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, backgroundColor))))
            }

            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
                item {
                    Spacer(modifier = Modifier.statusBarsPadding().height(90.dp))
                    Column(modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                        Box {
                            Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(12.dp), modifier = Modifier.size(160.dp)) {
                                if (!playlistCover.isNullOrEmpty()) AsyncImage(model = playlistCover, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else if (defaultIcon != null) Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) { Icon(defaultIcon!!, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary) }
                                else Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            if (isUserCreated) {
                                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f), modifier = Modifier.align(Alignment.BottomEnd).offset(x = 8.dp, y = 8.dp).clickable { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                                    Icon(Icons.Outlined.Image, stringResource(R.string.storage_change_btn), modifier = Modifier.padding(8.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                        Text(text = playlistTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        if (playlistUser != null && playlistUser!!.id > 0) {
                            Text(text = stringResource(R.string.playlist_by_user, playlistUser!!.username ?: ""), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.clickable { playerViewModel.navigateToArtist(playlistUser!!.id) })
                        } else {
                            Text(text = stringResource(R.string.playlist_by_user, playlistUser?.username ?: stringResource(R.string.me_artist)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                        }
                        Spacer(Modifier.height(8.dp))

                        val trackCountText = if (isLoading && playlistId != "likes") "..." else stringResource(R.string.playlist_num_tracks, tracksToDisplay.size)
                        Text(text = trackCountText, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f), style = MaterialTheme.typography.bodyMedium)

                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                            if (isUserCreated || isLocalPlaylist) {
                                IconButton(onClick = { newPlaylistName = playlistTitle; showRenameDialog = true }) { Icon(Icons.Outlined.Edit, stringResource(R.string.profile_edit), tint = MaterialTheme.colorScheme.onBackground) }
                            }

                            if (playlistId != "downloads" && playlistId != "likes" && !playlistId.contains("station") && !isUserCreated) {
                                IconButton(onClick = {
                                    if (isLocalPlaylist) showDeleteDialog = true
                                    else {
                                        val cleanId = currentIdLong
                                        if (cleanId != 0L && tracks.isNotEmpty()) {
                                            val fakePlaylist = Playlist(cleanId, playlistTitle, playlistCover, null, tracks.size, playlistUser, null)
                                            DownloadManager.importPlaylistToLibrary(fakePlaylist, tracks.toList())
                                        }
                                    }
                                }) {
                                    if (isLocalPlaylist) Icon(Icons.Rounded.Favorite, stringResource(R.string.lib_liked_tracks), tint = MaterialTheme.colorScheme.primary)
                                    else Icon(Icons.Outlined.FavoriteBorder, stringResource(R.string.menu_add_playlist), tint = MaterialTheme.colorScheme.onBackground)
                                }
                            }

                            if (isUserCreated) {
                                IconButton(onClick = { showDeleteDialog = true }) { Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error) }
                            }

                            if (playlistId != "downloads" && playlistId != "likes") {
                                IconButton(onClick = {
                                    val cleanId = currentIdLong
                                    if (cleanId != 0L) {
                                        if (isFullyDownloaded) {
                                            showRemoveDownloadDialog = true
                                        } else {
                                            val fakePlaylist = Playlist(cleanId, playlistTitle, playlistCover, null, tracks.size, playlistUser, null)
                                            DownloadManager.downloadPlaylist(fakePlaylist, tracks.toList())
                                        }
                                    }
                                }) {
                                    if (isFullyDownloaded) Icon(Icons.Rounded.DownloadDone, stringResource(R.string.btn_downloaded), tint = MaterialTheme.colorScheme.primary)
                                    else Icon(Icons.Rounded.Download, stringResource(R.string.btn_download), tint = MaterialTheme.colorScheme.onBackground)
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList(), 0, playbackContext) },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).height(50.dp) // added weight
                            ) {
                                Icon(Icons.Default.PlayArrow, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.btn_play),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1, // added maxLines
                                    overflow = TextOverflow.Ellipsis // added overflow
                                )
                            }
                            FilledTonalButton(
                                onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList().shuffled(), context = playbackContext) },
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
                                shape = RoundedCornerShape(50),
                                modifier = Modifier.weight(1f).height(50.dp) // added weight
                            ) {
                                Icon(Icons.Default.Shuffle, null)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.btn_shuffle),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1, // added maxLines
                                    overflow = TextOverflow.Ellipsis // added overflow
                                )
                            }
                        }
                    }
                }

                if (downloadProgress.isNotEmpty()) {
                    item { LinearProgressIndicator(progress = { 0.5f }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surfaceVariant) }
                }

                if (isLoading && playlistId != "likes") {
                    items(15) {
                        TrackListItemShimmer()
                    }
                } else {
                    itemsIndexed(items = tracksToDisplay, key = { index, t -> "${t.id}_$index" }) { index, track ->
                        val progress = downloadProgress[track.id]
                        val isDownloading = progress != null
                        val isDownloaded = remember(track.id, storageTrigger, localDownloadRefreshKey) {
                            File(context.filesDir, "track_${track.id}.mp3").exists()
                        }

                        var offsetY by remember { mutableFloatStateOf(0f) }
                        var isDragging by remember { mutableStateOf(false) }
                        val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                        val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "scale")

                        TrackListItem(
                            track = track,
                            currentlyPlayingTrack = playerViewModel.currentTrack,
                            index = index,
                            isDownloading = isDownloading,
                            isDownloaded = isDownloaded,
                            downloadProgress = progress ?: 0,
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer { translationY = offsetY; scaleX = scale; scaleY = scale; shadowElevation = elevation.toPx() }
                                .background(if (isDragging) MaterialTheme.colorScheme.surfaceContainer else Color.Transparent)
                                .pointerInput(Unit) {
                                    // only allow drag reordering on custom playlists
                                    if (isUserCreated) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { isDragging = true; view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) },
                                            onDragEnd = { isDragging = false; offsetY = 0f },
                                            onDragCancel = { isDragging = false; offsetY = 0f },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                offsetY += dragAmount.y
                                                val itemHeight = 72.dp.toPx()
                                                if (offsetY > itemHeight) {
                                                    val next = index + 1
                                                    if (next < tracks.size) {
                                                        val item = tracks.removeAt(index); tracks.add(next, item)
                                                        DownloadManager.swapTrackOrder(currentIdLong, track.id, tracks[index].id)
                                                        offsetY -= itemHeight; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    }
                                                } else if (offsetY < -itemHeight) {
                                                    val prev = index - 1
                                                    if (prev >= 0) {
                                                        val item = tracks.removeAt(index); tracks.add(prev, item)
                                                        DownloadManager.swapTrackOrder(currentIdLong, track.id, tracks[index].id)
                                                        offsetY += itemHeight; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                },
                            onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList(), index, playbackContext) },
                            onOptionClick = { playerViewModel.showTrackOptions(track, if(isUserCreated) currentIdLong else null) }
                        )
                    }
                }
            }

            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = onBackClick, modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_close), tint = Color.White) } },
                actions = {
                    IconButton(
                        onClick = { showPlaylistOptionsSheet = true },
                        modifier = Modifier.background(Color.Black.copy(alpha = 0.3f), CircleShape)
                    ) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.btn_options), tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp)
            )
        }

        if (showPlaylistOptionsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showPlaylistOptionsSheet = false },
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                PlaylistOptionsSheet(
                    playlistId = currentIdLong,
                    playlistTitle = playlistTitle,
                    playlistCover = playlistCover,
                    tracks = tracksToDisplay.toList(),
                    isLocal = isLocalPlaylist || playlistId == "downloads",
                    shareUrl = shareUrl,
                    playerViewModel = playerViewModel,
                    onDismiss = { showPlaylistOptionsSheet = false }
                )
            }
        }
    }
}

data class DockOptionItem(val icon: ImageVector, val text: String, val onClick: () -> Unit)

@Composable
fun PlaylistOptionsSheet(
    playlistId: Long,
    playlistTitle: String,
    playlistCover: String?,
    tracks: List<Track>,
    isLocal: Boolean,
    shareUrl: String,
    playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp).padding(horizontal = 8.dp)) {
            AsyncImage(model = playlistCover, contentDescription = null, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentScale = ContentScale.Crop)
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = playlistTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }

        val items = listOf(
            DockOptionItem(Icons.Rounded.PlayArrow, stringResource(R.string.btn_play)) { playerViewModel.playPlaylist(tracks, 0); onDismiss() },
            DockOptionItem(Icons.Default.Shuffle, stringResource(R.string.btn_shuffle)) { playerViewModel.playPlaylist(tracks.shuffled(), 0); onDismiss() },
            DockOptionItem(Icons.Default.Radio, stringResource(R.string.menu_track_radio)) { onDismiss() },
            DockOptionItem(Icons.Rounded.PlaylistPlay, stringResource(R.string.menu_play_next)) { playerViewModel.insertNext(tracks); onDismiss() },
            DockOptionItem(Icons.Rounded.QueueMusic, stringResource(R.string.menu_add_queue)) { playerViewModel.addToQueue(tracks); onDismiss() },
            DockOptionItem(Icons.Default.Add, stringResource(R.string.menu_add_playlist)) { playerViewModel.prepareBulkAdd(tracks); onDismiss() }
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            items(items) { item ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { item.onClick() }) {
                    Icon(item.icon, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(item.text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { /* TODO */ }) {
                    Icon(Icons.Rounded.Download, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.btn_download), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                }
            }

            if (!isLocal && shareUrl.isNotEmpty()) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.btn_share)))
                        onDismiss()
                    }) {
                        Icon(Icons.Outlined.Share, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.btn_share), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

@Composable
fun TrackListItem(
    track: Track,
    currentlyPlayingTrack: Track? = null,
    index: Int,
    isDownloading: Boolean,
    isDownloaded: Boolean,
    downloadProgress: Int,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onOptionClick: () -> Unit
) {
    val isCurrent = currentlyPlayingTrack?.id == track.id
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    val titleWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold

    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(72.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(contentAlignment = Alignment.Center) {
            AsyncImage(
                model = track.fullResArtwork,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .alpha(if (isDownloading) 0.3f else 1f),
                contentScale = ContentScale.Crop
            )
            if (isDownloading) CircularProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.size(28.dp), color = Color.White, strokeWidth = 3.dp, trackColor = Color.White.copy(alpha = 0.3f))

            if (isCurrent && !isDownloading) {
                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                    Icon(imageVector = Icons.Rounded.GraphicEq, contentDescription = stringResource(R.string.player_playing_now), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title ?: stringResource(R.string.untitled_track), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = titleWeight, color = titleColor)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDownloaded && !isDownloading) { Icon(Icons.Rounded.DownloadDone, stringResource(R.string.btn_downloaded), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
                Text(text = track.user?.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onOptionClick, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.btn_options), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}