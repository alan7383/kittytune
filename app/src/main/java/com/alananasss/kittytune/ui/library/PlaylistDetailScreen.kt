    package com.alananasss.kittytune.ui.library
    
    import android.content.Intent
    import android.net.Uri
    import android.view.HapticFeedbackConstants
    import androidx.activity.compose.BackHandler
    import androidx.activity.compose.rememberLauncherForActivityResult
    import androidx.activity.result.PickVisualMediaRequest
    import androidx.activity.result.contract.ActivityResultContracts
    import androidx.compose.animation.*
    import androidx.compose.animation.core.animateDpAsState
    import androidx.compose.animation.core.animateFloatAsState
    import androidx.compose.foundation.background
    import androidx.compose.foundation.clickable
    import androidx.compose.foundation.gestures.scrollBy
    import androidx.compose.foundation.layout.*
    import androidx.compose.foundation.lazy.LazyColumn
    import androidx.compose.foundation.lazy.grid.GridCells
    import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
    import androidx.compose.foundation.lazy.grid.items
    import androidx.compose.foundation.lazy.itemsIndexed
    import androidx.compose.foundation.lazy.rememberLazyListState
    import androidx.compose.foundation.shape.CircleShape
    import androidx.compose.foundation.shape.RoundedCornerShape
    import androidx.compose.material.icons.Icons
    import androidx.compose.material.icons.automirrored.filled.ArrowBack
    import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
    import androidx.compose.material.icons.automirrored.rounded.QueueMusic
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
    import androidx.compose.ui.graphics.Brush
    import androidx.compose.ui.graphics.Color
    import androidx.compose.ui.graphics.graphicsLayer
    import androidx.compose.ui.graphics.vector.ImageVector
    import androidx.compose.ui.layout.ContentScale
    import androidx.compose.ui.platform.LocalContext
    import androidx.compose.ui.platform.LocalDensity
    import androidx.compose.ui.platform.LocalView
    import androidx.compose.ui.res.stringResource
    import androidx.compose.ui.text.font.FontWeight
    import androidx.compose.ui.text.style.TextAlign
    import androidx.compose.ui.text.style.TextOverflow
    import androidx.compose.ui.unit.dp
    import androidx.compose.ui.zIndex
    import androidx.lifecycle.viewmodel.compose.viewModel
    import coil.compose.AsyncImage
    import com.alananasss.kittytune.R
    import com.alananasss.kittytune.data.DownloadManager
    import com.alananasss.kittytune.data.LikeRepository
    import com.alananasss.kittytune.data.local.AppDatabase
    import com.alananasss.kittytune.data.local.LocalTrack
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
    import sh.calvin.reorderable.ReorderableItem
    import sh.calvin.reorderable.rememberReorderableLazyListState
    
    @OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
    @Composable
    fun PlaylistDetailScreen(
        playlistId: String,
        onBackClick: () -> Unit,
        onNavigate: (String) -> Unit,
        playerViewModel: PlayerViewModel,
        youtubeRadioViewModel: YoutubeRadioViewModel = viewModel()
    ) {
        val context = LocalContext.current
        val view = LocalView.current
        val density = LocalDensity.current
        val storageTrigger by DownloadManager.storageTrigger.collectAsState()
        val isYoutubeRadio = playlistId.startsWith("yt_radio:")
    
        val tracks = remember { mutableStateListOf<Track>() }
        val downloadedPlaylists = remember { mutableStateListOf<Playlist>() }
    
        val likedTracksRepo by LikeRepository.likedTracks.collectAsState()
        var isAlbum by remember { mutableStateOf(false) }
    
        var playlistTitle by remember { mutableStateOf("") }
        var playlistCover by remember { mutableStateOf<String?>(null) }
        var playlistUser by remember { mutableStateOf<User?>(null) }
        var isLoading by remember { mutableStateOf(true) }
        var defaultIcon by remember { mutableStateOf<ImageVector?>(null) }
    
        val downloadProgress by DownloadManager.downloadProgress.collectAsState()
        val playlistDownloadProgress by DownloadManager.playlistDownloadProgress.collectAsState()
        val downloadedIds by DownloadManager.downloadedIds.collectAsState()
    
        var showAllPlaylists by remember { mutableStateOf(false) }
    
        BackHandler(enabled = showAllPlaylists) {
            showAllPlaylists = false
        }
    
        val isDownloadedView = playlistId.startsWith("downloaded_section:")
    
        val cleanIdStr = playlistId.replace("station_artist:", "")
            .replace("station:", "")
            .replace("liked_by:", "")
            .replace("local_playlist:", "")
            .replace("yt_radio:", "")
            .replace("downloaded_section:", "")
    
    
        val currentIdLong = cleanIdStr.toLongOrNull() ?: 0L
    
        val stableId = remember(playlistId, currentIdLong) {
            if (currentIdLong != 0L) currentIdLong else playlistId.hashCode().toLong()
        }
    
        val playlistInDb by DownloadManager.isPlaylistInLibraryFlow(stableId).collectAsState(initial = null)
    
        val effectiveBatchId = if (playlistId == "likes") DownloadManager.LIKES_BATCH_ID else currentIdLong
    
        val isPlaylistDownloading = DownloadManager.isPlaylistDownloading(effectiveBatchId)
        val currentPlaylistProgress = playlistDownloadProgress[effectiveBatchId]
    
        var isUserCreated by remember { mutableStateOf(false) }
        var isLocalPlaylist by remember { mutableStateOf(false) }
        var showPlaylistOptionsSheet by remember { mutableStateOf(false) }
        var showPlaylistDetailsSheet by remember { mutableStateOf(false) }
    
        var playlistPermalinkUrl by remember { mutableStateOf<String?>(null) }
    
        val listState = rememberLazyListState()
        val reorderableState = rememberReorderableLazyListState(
            lazyListState = listState,
            onMove = { from, to ->
                val fromId = from.key as? Long ?: return@rememberReorderableLazyListState
                val toId = to.key as? Long ?: return@rememberReorderableLazyListState
    
                val fromIndex = tracks.indexOfFirst { it.id == fromId }
                val toIndex = tracks.indexOfFirst { it.id == toId }
    
                if (fromIndex != -1 && toIndex != -1 && fromIndex != toIndex) {
                    // Update UI list
                    val item = tracks.removeAt(fromIndex)
                    tracks.add(toIndex, item)
                    // Update Database
                    DownloadManager.swapTrackOrder(currentIdLong, fromId, toId)
                    // Haptic
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                }
            }
        )
    
        val shareUrl = remember(playlistId, currentIdLong, playlistPermalinkUrl, playlistUser) {
            when {
                playlistId.startsWith("station_artist:") -> "https://soundcloud.com/discover/sets/artist-stations:$currentIdLong"
                playlistId.startsWith("station:") -> "https://soundcloud.com/discover/sets/track-stations:$currentIdLong"
                playlistId.startsWith("yt_radio:") -> {
                    val decodedUrl = Uri.decode(cleanIdStr)
                    val videoId = decodedUrl.substringAfter("v=").substringBefore("&")
                    "https://www.youtube.com/watch?v=$videoId&list=RD$videoId"
                }
    
    
                playlistId.startsWith("liked_by:") -> {
                    val user = playlistUser
                    val profileUrl = playlistPermalinkUrl ?: user?.permalinkUrl
                    if (profileUrl != null) {
                        "$profileUrl/likes"
                    } else {
                        "https://soundcloud.com/discover/sets/liked-by::$currentIdLong"
                    }
                }
    
                playlistId == "likes" -> {
                    val user = playlistUser
                    if (user != null && user.id > 0 && !user.permalinkUrl.isNullOrEmpty()) {
                        "${user.permalinkUrl}/likes"
                    } else {
                        ""
                    }
                }
    
                playlistId == "downloads" -> ""
                currentIdLong > 0 -> playlistPermalinkUrl ?: "https://soundcloud.com/playlists/$currentIdLong"
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
    
        val tracksToDisplay = if (playlistId == "likes") likedTracksRepo else tracks
    
        val downloadedCount = remember(tracksToDisplay, downloadedIds) {
            if (tracksToDisplay.isEmpty()) 0
            else tracksToDisplay.count { track -> track.id < 0 || downloadedIds.contains(track.id) }
        }
    
        val isFullyDownloaded = remember(tracksToDisplay.size, downloadedCount, isPlaylistDownloading) {
            if (tracksToDisplay.isEmpty()) false
            else {
                val ratio = downloadedCount.toFloat() / tracksToDisplay.size.toFloat()
                downloadedCount == tracksToDisplay.size || (ratio > 0.9f && !isPlaylistDownloading)
            }
        }
        val refreshTrigger = if (playlistId == "downloads" || playlistId == "local_files") storageTrigger else 0
    
    
        LaunchedEffect(playlistId, refreshTrigger) {
            if (playlistId.startsWith("yt_radio:")) {
                val encodedUrl = playlistId.removePrefix("yt_radio:")
                val url = Uri.decode(encodedUrl)
                youtubeRadioViewModel.loadInitial(url)
                return@LaunchedEffect
            }
    
            if (tracks.isEmpty() && downloadedPlaylists.isEmpty()) {
                isLoading = true
            }
    
            val newTracks = mutableListOf<Track>()
            val newDownloadedPlaylists = mutableListOf<Playlist>()
    
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
                            playlistUser = User(0, context.getString(R.string.me_artist), null)
                        }
                    }
    
                    playlistId == "downloads" -> {
                        playlistTitle = context.getString(R.string.lib_downloads)
                        defaultIcon = Icons.Rounded.Folder
                        isLocalPlaylist = false
                        val localPlaylists = db.getDownloadedPlaylists().first()
                        newDownloadedPlaylists.addAll(localPlaylists.map { local ->
                            val tracksInPlaylist = db.getTracksForPlaylistSync(local.id)
                            val realDownloadedCount = tracksInPlaylist.count { it.localAudioPath.isNotEmpty() }
    
                            Playlist(
                                id = local.id,
                                title = local.title,
                                artworkUrl = local.artworkUrl,
                                calculatedArtworkUrl = local.localCoverPath,
                                trackCount = realDownloadedCount,
                                user = User(0, local.artist, null),
                                tracks = null
                            )
                        })
                        val orphanTracks = db.getOrphanTracksList()
                        newTracks.addAll(orphanTracks.map { local ->
                            Track(
                                id = local.id,
                                title = local.title,
                                artworkUrl = local.localArtworkPath.ifEmpty { local.artworkUrl },
                                durationMs = local.duration,
                                user = User(0, local.artist, null),
                                isLiked = true
                            )
                        })
                    }
    
                    playlistId == "local_files" -> {
                        playlistTitle = context.getString(R.string.lib_local_media)
                        defaultIcon = Icons.Default.SdStorage
                        isLocalPlaylist = false
                        val allTracks = db.getAllTracks().first()
                        val localFileTracks = allTracks.filter { it.id < 0 }
                        newTracks.addAll(localFileTracks.map { local ->
                            Track(
                                id = local.id,
                                title = local.title,
                                artworkUrl = local.localArtworkPath.ifEmpty { local.artworkUrl },
                                durationMs = local.duration,
                                user = User(0, local.artist, null),
                                description = context.getString(R.string.description_local_file, local.localAudioPath)
                            )
                        })
                    }
    
                    playlistId.startsWith("liked_by:") -> {
                        val targetUserId = currentIdLong
                        defaultIcon = Icons.Rounded.Favorite
                        val user = api.getUser(targetUserId)
                        playlistTitle = "Liked by ${user.username}"
                        playlistCover = user.avatarUrl?.replace("large", "t500x500")
                        playlistUser = user
                        playlistPermalinkUrl = user.permalinkUrl
    
                        val allCollectedTracks = mutableListOf<Track>()
                        var likesResponse = api.getUserTrackLikes(targetUserId, limit = 200)
                        allCollectedTracks.addAll(likesResponse.collection.mapNotNull { it.track })
    
                        var nextUrl = likesResponse.next_href
                        var safetyPageCount = 0
                        while (nextUrl != null && safetyPageCount < 50) {
                            try {
                                val nextResponse = api.getTrackLikesNextPage(nextUrl!!)
                                allCollectedTracks.addAll(nextResponse.collection.mapNotNull { it.track })
                                nextUrl = nextResponse.next_href
                                safetyPageCount++
                            } catch (e: Exception) {
                                e.printStackTrace()
                                break
                            }
                        }
                        newTracks.addAll(allCollectedTracks.distinctBy { it.id })
                    }
    
                    else -> {
                        val localPlaylist = if (currentIdLong != 0L) db.getPlaylist(currentIdLong) else null
                        if (localPlaylist != null) {
                            playlistTitle = localPlaylist.title
                            playlistCover = localPlaylist.localCoverPath ?: localPlaylist.artworkUrl
    
                            playlistUser = User(0, localPlaylist.artist, null)
                            isUserCreated = localPlaylist.isUserCreated || currentIdLong < 0
                            isLocalPlaylist = true
                            playlistPermalinkUrl = localPlaylist.permalinkUrl
    
                            val playlistTracks = db.getTracksForPlaylistSync(currentIdLong)
                            val filteredTracks = if (isDownloadedView) {
                                playlistTracks.filter { it.localAudioPath.isNotEmpty() }
                            } else {
                                playlistTracks
                            }
    
                            newTracks.addAll(filteredTracks.map { local ->
                                Track(
                                    id = local.id,
                                    title = local.title,
                                    artworkUrl = local.localArtworkPath.ifEmpty { local.artworkUrl },
                                    durationMs = local.duration,
                                    user = User(0, local.artist, null)
                                )
                            })
                        } else {
                            if (currentIdLong > 0L) {
                                val isArtistStation = playlistId.startsWith("station_artist:")
                                val isTrackStation = playlistId.startsWith("station:")
    
                                val playlistObj = when {
                                    isArtistStation -> api.getArtistStation(currentIdLong)
                                    isTrackStation -> api.getTrackStation(currentIdLong)
                                    else -> api.getPlaylist(currentIdLong)
                                }
                                isAlbum = playlistObj.isAlbum
    
                                playlistTitle = playlistObj.title.takeIf { !it.isNullOrBlank() } ?: context.getString(R.string.radio)
                                playlistCover = playlistObj.fullResArtwork
                                playlistUser = playlistObj.user
                                playlistPermalinkUrl = playlistObj.permalinkUrl
                                val rawTracks = playlistObj.tracks ?: emptyList()
                                val incompleteIds = rawTracks.filter { it.title.isNullOrBlank() || it.user == null }.map { it.id }
    
                                if (incompleteIds.isNotEmpty()) {
                                    val fetchedTracksMap = mutableMapOf<Long, Track>()
                                    val chunks = incompleteIds.chunked(50)
                                    chunks.forEach { batchIds ->
                                        try {
                                            val fetched = api.getTracksByIds(batchIds.joinToString(","))
                                            fetched.forEach { fetchedTracksMap[it.id] = it }
                                        } catch (e: Exception) { e.printStackTrace() }
                                    }
                                    val hydratedList = rawTracks.map { track -> if (track.title.isNullOrBlank() || track.user == null) fetchedTracksMap[track.id] ?: track else track }
                                    newTracks.addAll(hydratedList)
                                } else {
                                    newTracks.addAll(rawTracks)
                                }
                            }
                        }
                    }
                }
    
                if (playlistId != "likes") {
                    tracks.clear()
                    tracks.addAll(newTracks)
                }
    
                if (playlistId == "downloads") {
                    downloadedPlaylists.clear()
                    downloadedPlaylists.addAll(newDownloadedPlaylists)
                }
    
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoading = false
            }
        }
    
        if (playlistId.startsWith("yt_radio:")) {
            playlistTitle = youtubeRadioViewModel.playlistTitle
            playlistCover = youtubeRadioViewModel.playlistCover
            playlistUser = youtubeRadioViewModel.playlistUser
            tracks.clear()
            tracks.addAll(youtubeRadioViewModel.tracks)
            isLoading = youtubeRadioViewModel.isLoading
        }
    
    
        if (showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text(stringResource(if (isUserCreated) R.string.dialog_delete_playlist_title else R.string.dialog_delete_playlist_from_lib_title)) },
                text = { Text(stringResource(R.string.dialog_delete_playlist_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (stableId != 0L) {
                            DownloadManager.deletePlaylist(stableId)
                        }
                        showDeleteDialog = false
                        onBackClick()
                    }) {
                        Text(stringResource(R.string.btn_confirm), color = MaterialTheme.colorScheme.error)
                    }
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
                            DownloadManager.deletePlaylist(currentIdLong)
                        }
    
                        showRemoveDownloadDialog = false
                        if (isDownloadedView) {
                            onBackClick()
                        }
    
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
    
        val playbackContext = remember(playlistId, playlistTitle, playlistCover, playlistUser, isAlbum) {
            val creatorName = playlistUser?.username
            val isVerified = playlistUser?.verified == true
    
            when {
                playlistId == "likes" -> PlaybackContext(
                    context.getString(R.string.context_playlist, context.getString(R.string.lib_liked_tracks)),
                    "likes",
                    playlistCover,
                    artistName = null
                )
                playlistId == "downloads" -> PlaybackContext(
                    context.getString(R.string.context_playlist, context.getString(R.string.lib_downloads)),
                    "downloads",
                    playlistCover,
                    artistName = null
                )
                playlistId.startsWith("station") || playlistId.startsWith("yt_radio:") -> PlaybackContext(
                    context.getString(R.string.context_station, playlistTitle),
                    playlistId,
                    playlistCover,
                    artistName = null,
                    isVerified = isVerified
                )
                isAlbum -> PlaybackContext(
                    context.getString(R.string.context_album, playlistTitle),
                    playlistId,
                    playlistCover,
                    artistName = creatorName,
                    isVerified = isVerified
                )
                else -> PlaybackContext(
                    context.getString(R.string.context_playlist, playlistTitle),
                    playlistId,
                    playlistCover,
                    artistName = creatorName,
                    isVerified = isVerified
                )
            }
        }
    
        val backgroundColor = MaterialTheme.colorScheme.background
    
        Box(modifier = Modifier.fillMaxSize().background(backgroundColor)) {
            if (!showAllPlaylists) {
                Scaffold(containerColor = Color.Transparent) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!playlistCover.isNullOrEmpty()) {
                            Box(modifier = Modifier.fillMaxWidth().height(500.dp)) {
                                AsyncImage(model = playlistCover, contentDescription = null, modifier = Modifier.fillMaxSize().blur(100.dp).alpha(0.6f), contentScale = ContentScale.Crop)
                                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(colors = listOf(Color.Transparent, backgroundColor.copy(alpha = 0.3f), backgroundColor.copy(alpha = 0.8f), backgroundColor))))
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, backgroundColor))))
                        }
    
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 120.dp)
                        ) {
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
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { playerViewModel.navigateToArtist(playlistUser!!.id) }) {
                                            Text(text = stringResource(R.string.playlist_by_user, playlistUser!!.username ?: ""), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                            if (playlistUser?.verified == true) {
                                                Spacer(Modifier.width(4.dp))
                                                Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    } else if (playlistUser?.username != null) {
                                        Text(text = stringResource(R.string.playlist_by_user, playlistUser?.username ?: stringResource(R.string.me_artist)), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
                                    }
                                    Spacer(Modifier.height(8.dp))
    
    
                                    val trackCountText = when {
                                        playlistId.startsWith("yt_radio:") -> stringResource(R.string.radio) + " • YouTube"
                                        isLoading && playlistId != "likes" -> "..."
                                        else -> stringResource(R.string.playlist_num_tracks, tracksToDisplay.size + downloadedPlaylists.sumOf { it.trackCount ?: 0 })                                }
    
                                    if(trackCountText.isNotEmpty()) {
                                        Text(
                                            text = trackCountText,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
    
    
                                    Spacer(Modifier.height(16.dp))
    
                                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
                                        val stableId = if (currentIdLong != 0L) currentIdLong else playlistId.hashCode().toLong()
                                        if ((isLocalPlaylist || isUserCreated) && !isYoutubeRadio) {
                                            IconButton(onClick = { newPlaylistName = playlistTitle; showRenameDialog = true }) {
                                                Icon(Icons.Outlined.Edit, stringResource(R.string.profile_edit), tint = MaterialTheme.colorScheme.onBackground)
                                            }
                                        }
                                        if (playlistId != "downloads" && playlistId != "likes" && playlistId != "local_files") {
    
                                            if (isUserCreated) {
                                                IconButton(onClick = { showDeleteDialog = true }) {
                                                    Icon(Icons.Default.Delete, stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                                                }
                                            } else {
                                                IconButton(onClick = {
                                                    if (isLocalPlaylist) {
                                                        showDeleteDialog = true
                                                    } else {
                                                        val tracksToSave = if (isYoutubeRadio) emptyList() else tracksToDisplay.toList()
                                                        if (isYoutubeRadio || tracksToSave.isNotEmpty()) {
                                                            val fakePlaylist = Playlist(
                                                                id = stableId,
                                                                title = playlistTitle,
                                                                artworkUrl = playlistCover,
                                                                calculatedArtworkUrl = null,
                                                                trackCount = if (isYoutubeRadio) 0 else tracksToSave.size,
                                                                user = playlistUser,
                                                                tracks = null,
                                                                permalinkUrl = if (isYoutubeRadio) playlistId else playlistPermalinkUrl
                                                            )
                                                            DownloadManager.importPlaylistToLibrary(fakePlaylist, tracksToSave)
                                                        }
                                                    }
                                                }) {
                                                    if (isLocalPlaylist) {
                                                        Icon(Icons.Rounded.Favorite, stringResource(R.string.lib_liked_tracks), tint = MaterialTheme.colorScheme.primary)
                                                    } else {
                                                        Icon(Icons.Outlined.FavoriteBorder, stringResource(R.string.menu_add_playlist), tint = MaterialTheme.colorScheme.onBackground)
                                                    }
                                                }
                                            }
                                        }
    
                                        if (!isYoutubeRadio && playlistId != "downloads" && tracksToDisplay.isNotEmpty()) {
                                            IconButton(onClick = {
                                                val targetBatchId = if (playlistId == "likes") DownloadManager.LIKES_BATCH_ID else stableId
    
                                                if (isFullyDownloaded || isDownloadedView) {
                                                    showRemoveDownloadDialog = true
                                                } else if (isPlaylistDownloading) {
                                                    DownloadManager.cancelBatch(targetBatchId)
                                                } else {
                                                    if (playlistId == "likes") {
                                                        DownloadManager.downloadBatch(tracksToDisplay.toList(), DownloadManager.LIKES_BATCH_ID)
                                                    } else if (stableId != 0L) {
                                                        val fakePlaylist = Playlist(stableId, playlistTitle, playlistCover, null, tracks.size, playlistUser, null)
                                                        DownloadManager.downloadPlaylist(fakePlaylist, tracks.toList())
                                                    }
                                                }
                                            }) {
                                                when {
                                                    isFullyDownloaded || isDownloadedView -> {
                                                        Icon(Icons.Rounded.Delete, stringResource(R.string.btn_delete), tint = MaterialTheme.colorScheme.error)
                                                    }
                                                    isPlaylistDownloading -> {
                                                        Icon(Icons.Rounded.Close, stringResource(R.string.btn_cancel))
                                                    }
                                                    else -> {
                                                        Icon(Icons.Rounded.Download, stringResource(R.string.btn_download), tint = MaterialTheme.colorScheme.onBackground)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
    
                            item {
                                AnimatedVisibility(
                                    visible = isPlaylistDownloading && currentPlaylistProgress != null,
                                    enter = fadeIn() + expandVertically(),
                                    exit = fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 24.dp)
                                            .padding(top = 8.dp, bottom = 24.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        val percentage = ((currentPlaylistProgress ?: 0f) * 100).toInt()
                                        Text(
                                            text = stringResource(R.string.playlist_downloading_progress, percentage),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(Modifier.height(8.dp))

                                        LinearWavyProgressIndicator(
                                            progress = { currentPlaylistProgress ?: 0f },
                                            modifier = Modifier.fillMaxWidth(),
                                            color = MaterialTheme.colorScheme.primary,
                                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                                        )
                                    }
                                }
                            }
    
                            if (playlistId == "downloads" && downloadedPlaylists.isNotEmpty()) {
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = stringResource(R.string.lib_playlists),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        if (downloadedPlaylists.size > 4) {
                                            TextButton(onClick = { showAllPlaylists = true }) {
                                                Text(stringResource(R.string.btn_see_all))
                                            }
                                        }
                                    }
                                }
    
    
                                val displayList = if (downloadedPlaylists.size > 4) downloadedPlaylists.take(4) else downloadedPlaylists
                                val chunkedPlaylists = displayList.chunked(2)
    
                                items(chunkedPlaylists.size) { index ->
                                    val rowPlaylists = chunkedPlaylists[index]
    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        for (playlist in rowPlaylists) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                PlaylistSquareCard(playlist = playlist) {
                                                    val id = if(playlist.id < 0) "local_playlist:${playlist.id}" else playlist.id.toString()
                                                    onNavigate("downloaded_section:$id")
                                                }
                                            }
                                        }
                                        if (rowPlaylists.size == 1) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
    
                                if (tracksToDisplay.isNotEmpty()) {
                                    item {
                                        Text(
                                            text = stringResource(R.string.profile_tracks),
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
                                        )
                                    }
                                }
                            }
                            if (isLoading && playlistId != "likes") {
                                items(15) { TrackListItemShimmer() }
                            } else {
                                val isReallyEmpty = tracksToDisplay.isEmpty()
                                        && (playlistId != "downloads" || downloadedPlaylists.isEmpty())
                                        && !isYoutubeRadio
    
                                if (isReallyEmpty) {
                                    item {
                                        EmptyPlaylistView(
                                            playlistId = playlistId,
                                            isUserCreated = isUserCreated
                                        )
                                    }
                                } else {
                                    if (isYoutubeRadio && tracksToDisplay.isEmpty()) {
                                        item {
                                            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                                                ContainedLoadingIndicator()
                                            }
                                        }
                                    }
                                    if (tracksToDisplay.isNotEmpty()) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                                            ) {
                                                Button(
                                                    onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList(), 0, playbackContext) },
                                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary),
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier.weight(1f).height(50.dp)
                                                ) {
                                                    Icon(Icons.Default.PlayArrow, null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.btn_play), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                }
                                                FilledTonalButton(
                                                    onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList().shuffled(), context = playbackContext) },
                                                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest, contentColor = MaterialTheme.colorScheme.onSurface),
                                                    shape = RoundedCornerShape(50),
                                                    modifier = Modifier.weight(1f).height(50.dp)
                                                ) {
                                                    Icon(Icons.Default.Shuffle, null)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(stringResource(R.string.btn_shuffle), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                            Spacer(Modifier.height(16.dp))
                                        }
                                    }
    
                                    itemsIndexed(items = tracksToDisplay, key = { _, t -> t.id }) { index, track ->
                                        if (index >= tracksToDisplay.size - 5 && playlistId.startsWith("yt_radio:")) {
                                            LaunchedEffect(Unit) {
                                                youtubeRadioViewModel.loadMore()
                                            }
                                        }
    
                                        val progress = downloadProgress[track.id]
                                        val isDownloading = progress != null
                                        val isDownloaded = remember(track.id, downloadedIds) {
                                            (track.id < 0 && track.source != "youtube") || downloadedIds.contains(track.id)
                                        }

                                        if (isUserCreated) {
                                            ReorderableItem(
                                                state = reorderableState,
                                                key = track.id
                                            ) { isDragging ->
                                                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                                                val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "scale")
                                                val backgroundColor = if (isDragging) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.background
    
                                                var isDeleted by remember { mutableStateOf(false) }
                                                val dismissState = rememberSwipeToDismissBoxState(
                                                    confirmValueChange = {
                                                        if (it == SwipeToDismissBoxValue.EndToStart) {
                                                            isDeleted = true
                                                            true
                                                        } else false
                                                    }
                                                )
    
                                                AnimatedVisibility(
                                                    visible = !isDeleted,
                                                    exit = shrinkVertically() + fadeOut(),
                                                    modifier = Modifier.zIndex(if (isDragging) 5f else 0f)
                                                ) {
                                                    SwipeToDismissBox(
                                                        state = dismissState,
                                                        backgroundContent = {
                                                            val color = MaterialTheme.colorScheme.errorContainer
                                                            Box(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .background(color)
                                                                    .padding(horizontal = 24.dp),
                                                                contentAlignment = Alignment.CenterEnd
                                                            ) {
                                                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                                            }
                                                        },
                                                        enableDismissFromStartToEnd = false
                                                    ) {
                                                        TrackListItem(
                                                            track = track,
                                                            currentlyPlayingTrack = playerViewModel.currentTrack,
                                                            index = index,
                                                            isDownloading = isDownloading,
                                                            isDownloaded = isDownloaded,
                                                            downloadProgress = progress ?: 0,
                                                            showVerifiedBadge = false,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .background(backgroundColor)
                                                                .graphicsLayer {
                                                                    scaleX = scale
                                                                    scaleY = scale
                                                                    shadowElevation = elevation.toPx()
                                                                },
                                                            dragModifier = Modifier.draggableHandle(
                                                                onDragStarted = {
                                                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                                }
                                                            ),
                                                            onClick = {
                                                                if (!isDragging) {
                                                                    playerViewModel.playPlaylist(tracksToDisplay.toList(), index, playbackContext)
                                                                }
                                                            },
                                                            onOptionClick = {
                                                                val contextId = if (playlistId == "downloads") -2L else if (isUserCreated) currentIdLong else null
                                                                playerViewModel.showTrackOptions(track, contextId)
                                                            }
                                                        )
                                                    }
                                                }
    
                                                LaunchedEffect(isDeleted) {
                                                    if (isDeleted) {
                                                        delay(500)
                                                        tracks.remove(track)
                                                        DownloadManager.removeTrackFromPlaylist(currentIdLong, track.id)
                                                    }
                                                }
                                            }
                                        } else {
                                            TrackListItem(
                                                track = track,
                                                currentlyPlayingTrack = playerViewModel.currentTrack,
                                                index = index,
                                                isDownloading = isDownloading,
                                                isDownloaded = isDownloaded,
                                                downloadProgress = progress ?: 0,
                                                showVerifiedBadge = false,
                                                onClick = { playerViewModel.playPlaylist(tracksToDisplay.toList(), index, playbackContext) },
                                                onOptionClick = {
                                                    val contextId = if (playlistId == "downloads") -2L else if (isUserCreated) currentIdLong else null
                                                    playerViewModel.showTrackOptions(track, contextId)
                                                }
                                            )
                                        }
                                    }
                                    if (playlistId.startsWith("yt_radio:") && youtubeRadioViewModel.isLoadingMore) {
                                        item {
                                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                                LoadingIndicator(color = MaterialTheme.colorScheme.primary)
                                            }
                                        }
                                    }
                                }
                            }
                        }
    
                        TopAppBar(
                            title = {},
                            navigationIcon = { 
                                IconButton(
                                    onClick = onBackClick, 
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f), contentColor = Color.White)
                                ) { 
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_close), tint = Color.White) 
                                } 
                            },
                            actions = {
                                IconButton(
                                    onClick = { showPlaylistOptionsSheet = true },
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.3f), contentColor = Color.White)
                                ) {
                                    Icon(Icons.Default.MoreVert, stringResource(R.string.btn_options), tint = Color.White)
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                            modifier = Modifier.statusBarsPadding().padding(horizontal = 8.dp)
                        )
                    }
                }
            }
    
            AnimatedVisibility(
                visible = showAllPlaylists,
                enter = slideInHorizontally { it },
                exit = slideOutHorizontally { it },
                modifier = Modifier.zIndex(2f)
            ) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.lib_playlists), fontWeight = FontWeight.Bold) },
                            navigationIcon = {
                                FilledTonalIconButton(
                                    onClick = { showAllPlaylists = false },
                                    shapes = IconButtonDefaults.shapes(),
                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.btn_back))
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.background
                ) { inner ->
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(inner).padding(bottom = 100.dp)
                    ) {
                        items(downloadedPlaylists) { playlist ->
                            PlaylistSquareCard(playlist = playlist) {
                                val id = if(playlist.id < 0) "local_playlist:${playlist.id}" else playlist.id.toString()
                                onNavigate(id)
                            }
                        }
                    }
                }
            }
    
            if (showPlaylistOptionsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showPlaylistOptionsSheet = false },
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                ) {
                    PlaylistOptionsSheet(
                        playlistId = if(playlistId == "likes") DownloadManager.LIKES_BATCH_ID else currentIdLong,
                        playlistTitle = playlistTitle,
                        playlistCover = playlistCover,
                        defaultIcon = defaultIcon,
                        tracks = tracksToDisplay.toList(),
                        isLocal = isLocalPlaylist || playlistId == "downloads",
                        shareUrl = shareUrl,
                        playerViewModel = playerViewModel,
                        isFullyDownloaded = isFullyDownloaded,
                        isDownloading = isPlaylistDownloading,
                        onDismiss = { showPlaylistOptionsSheet = false },
                        isYoutubeRadio = isYoutubeRadio,
                        onDetailsClick = {
                            showPlaylistOptionsSheet = false
                            showPlaylistDetailsSheet = true
                        }
                    )
                }
            }
    
            if (showPlaylistDetailsSheet) {
                ModalBottomSheet(
                    onDismissRequest = { showPlaylistDetailsSheet = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    PlaylistDetailsSheet(
                        playlistId = currentIdLong,
                        onDismiss = { showPlaylistDetailsSheet = false },
                        onViewAll = { tabIndex ->
                            showPlaylistDetailsSheet = false
                            onNavigate("playlist_fans/$currentIdLong?tab=$tabIndex")
                        },
                        onNavigate = onNavigate,
                        onMentionClick = { username ->
                            showPlaylistDetailsSheet = false
                            playerViewModel.resolveAndNavigateToArtist(username)
                        }
                    )
                }
            }
        }
    }
    
    @Composable
    fun PlaylistSquareCard(playlist: Playlist, onClick: () -> Unit) {
        Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
            AsyncImage(
                model = playlist.fullResArtwork,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = playlist.title ?: stringResource(R.string.generic_title),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.playlist_num_tracks, playlist.trackCount ?: 0),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    
    data class DockOptionItem(val icon: ImageVector, val text: String, val onClick: () -> Unit)
    
    @Composable
    fun PlaylistOptionsSheet(
        playlistId: Long,
        playlistTitle: String,
        playlistCover: String?,
        defaultIcon: ImageVector? = null,
        tracks: List<Track>,
        isLocal: Boolean,
        shareUrl: String,
        playerViewModel: PlayerViewModel,
        isFullyDownloaded: Boolean,
        isDownloading: Boolean,
        onDismiss: () -> Unit,
        isYoutubeRadio: Boolean = false,
        onDetailsClick: () -> Unit = {}
    ) {
        val context = LocalContext.current
        var showRemoveDownloadDialog by remember { mutableStateOf(false) }
    
        if (showRemoveDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showRemoveDownloadDialog = false },
                title = { Text(stringResource(R.string.dialog_remove_download_title)) },
                text = { Text(stringResource(R.string.dialog_remove_download_msg)) },
                confirmButton = {
                    TextButton(onClick = {
                        if (playlistId == DownloadManager.LIKES_BATCH_ID) {
                            DownloadManager.removeDownloads(tracks)
                        } else if (playlistId != 0L) {
                            DownloadManager.removePlaylistDownloads(playlistId)
                        }
                        showRemoveDownloadDialog = false
                        onDismiss()
                    }) { Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { showRemoveDownloadDialog = false }) { Text(stringResource(R.string.btn_cancel)) } }
            )
        }
    
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
    
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 24.dp).padding(horizontal = 8.dp)) {
                if (!playlistCover.isNullOrEmpty()) {
                    AsyncImage(
                        model = playlistCover,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = defaultIcon ?: Icons.Default.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(text = playlistTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            val items = remember(isLocal, playlistId, isYoutubeRadio) {
                mutableListOf(
                    DockOptionItem(Icons.Rounded.PlayArrow, context.getString(R.string.btn_play)) { playerViewModel.playPlaylist(tracks, 0); onDismiss() },
                    DockOptionItem(Icons.Default.Shuffle, context.getString(R.string.btn_shuffle)) { playerViewModel.playPlaylist(tracks.shuffled(), 0); onDismiss() }
                ).apply {
                    if (!isYoutubeRadio) {
                        add(DockOptionItem(Icons.AutoMirrored.Rounded.PlaylistPlay, context.getString(R.string.menu_play_next)) { playerViewModel.insertNext(tracks); onDismiss() })
                        add(DockOptionItem(Icons.AutoMirrored.Rounded.QueueMusic, context.getString(R.string.menu_add_queue)) { playerViewModel.addToQueue(tracks); onDismiss() })
                        add(DockOptionItem(Icons.Default.Add, context.getString(R.string.menu_add_playlist)) { playerViewModel.prepareBulkAdd(tracks); onDismiss() })
                    }
    
                    if (!isLocal && playlistId > 0) {
                        add(DockOptionItem(
                            icon = Icons.Rounded.Info,
                            text = context.getString(R.string.menu_playlist_details),
                            onClick = { onDetailsClick() }
                        ))
                    }
                }
            }
    
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
                if (!isLocal && tracks.isNotEmpty() && !isYoutubeRadio) {
                    item {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                            if (isFullyDownloaded) {
                                showRemoveDownloadDialog = true
                            } else if (!isDownloading) {
                                if (playlistId == DownloadManager.LIKES_BATCH_ID) {
                                    DownloadManager.downloadBatch(tracks, DownloadManager.LIKES_BATCH_ID)
                                } else {
                                    val fakePlaylist = Playlist(playlistId, playlistTitle, playlistCover, null, tracks.size, null, null)
                                    DownloadManager.downloadPlaylist(fakePlaylist, tracks)
                                }
                            }
                            onDismiss()
                        }) {
                            val icon = if (isFullyDownloaded) Icons.Rounded.Delete else if(isDownloading) Icons.Rounded.Downloading else Icons.Rounded.Download
                            val tint = if (isFullyDownloaded) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                            val text = if(isFullyDownloaded) stringResource(R.string.btn_delete) else stringResource(R.string.btn_download)
    
                            Icon(icon, null, modifier = Modifier.size(32.dp), tint = tint)
                            Spacer(Modifier.height(8.dp))
                            Text(text, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, color = tint)
                        }
                    }
                }
    
                if (shareUrl.isNotEmpty()) {
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
        showVerifiedBadge: Boolean = true,
        dragModifier: Modifier = Modifier,
        onClick: () -> Unit,
        onOptionClick: () -> Unit
    ) {
        val isCurrent = currentlyPlayingTrack?.id == track.id
        val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        val titleWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold

        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            modifier = modifier.padding(horizontal = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 8.dp)
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
                    if (isDownloading) CircularWavyProgressIndicator(progress = { downloadProgress / 100f }, modifier = Modifier.size(28.dp), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f))

                    if (isCurrent && !isDownloading) {
                        Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)), contentAlignment = Alignment.Center) {
                            Icon(imageVector = Icons.Rounded.GraphicEq, contentDescription = stringResource(R.string.player_playing_now), tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = track.title ?: stringResource(R.string.untitled_track), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = titleWeight, color = titleColor)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDownloaded && !isDownloading) { Icon(Icons.Rounded.DownloadDone, stringResource(R.string.btn_downloaded), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(4.dp)) }
                        Text(text = track.user?.username ?: stringResource(R.string.unknown_artist), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (showVerifiedBadge && track.user?.verified == true) {
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.Verified, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
                if (dragModifier != Modifier) {
                    Icon(
                        imageVector = Icons.Rounded.DragHandle,
                        contentDescription = stringResource(R.string.desc_move),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .size(48.dp)
                            .padding(12.dp)
                            .then(dragModifier)
                    )
                }
                IconButton(onClick = onOptionClick, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.MoreVert, stringResource(R.string.btn_options), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
    
    @Composable
    fun EmptyPlaylistView(
        playlistId: String,
        isUserCreated: Boolean
    ) {
        val (kaomoji, title, subtitle) = when {
            playlistId == "downloads" -> Triple(
                stringResource(R.string.empty_downloads_kaomoji),
                stringResource(R.string.empty_downloads_title),
                stringResource(R.string.empty_downloads_subtitle)
            )
            isUserCreated -> Triple(
                stringResource(R.string.empty_user_playlist_kaomoji),
                stringResource(R.string.empty_user_playlist_title),
                stringResource(R.string.empty_user_playlist_subtitle)
            )
            else -> Triple(
                stringResource(R.string.empty_playlist_generic_kaomoji),
                stringResource(R.string.empty_playlist_generic),
                ""
            )
        }
    
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(48.dp))
    
            Text(
                text = kaomoji,
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 16.dp)
            )
    
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
    
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
    
            Spacer(Modifier.height(48.dp))
        }
    }


