package com.alananasss.kittytune.ui.library

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.ExitToApp
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.TokenManager
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.LibraryFolder
import com.alananasss.kittytune.data.local.LocalArtist
import com.alananasss.kittytune.data.network.RetrofitClient
import com.alananasss.kittytune.domain.Playlist
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.KittyModalBottomSheet
import com.alananasss.kittytune.ui.common.ShimmerBox
import com.alananasss.kittytune.ui.common.ShimmerLine
import com.alananasss.kittytune.ui.player.PlayerViewModel
import com.alananasss.kittytune.ui.profile.ArtistAvatar

private data class PlaylistActionItem(
    val icon: ImageVector,
    val text: String,
    val tint: Color? = null,
    val onClick: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
    onLoginClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onLikedTracksClick: () -> Unit,
    onProfileClick: () -> Unit,
    onHistoryClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    playerViewModel: PlayerViewModel,
    libraryViewModel: LibraryViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isGuest = TokenManager(context).isGuestMode()

    val listState = rememberLazyGridState()

    var isFabMenuExpanded by remember { mutableStateOf(false) }

    BackHandler(enabled = isFabMenuExpanded) { isFabMenuExpanded = false }
    BackHandler(enabled = !isFabMenuExpanded && libraryViewModel.currentFolderId != null) {
        libraryViewModel.navigateUp()
    }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (libraryViewModel.isOfflineMode || libraryViewModel.userProfile?.id == 0L) {
                    libraryViewModel.loadData(forceRefresh = true)
                }
            }

            override fun onLost(network: Network) {
                libraryViewModel.isOfflineMode = true
            }
        }
        val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
        try {
            connectivityManager.registerNetworkCallback(request, networkCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        onDispose {
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: Exception) {
            }
        }
    }

    LaunchedEffect(Unit) {
        libraryViewModel.loadData()
    }

    LaunchedEffect(
        libraryViewModel.selectedFilter,
        libraryViewModel.currentFolderId,
        libraryViewModel.isSortDescending,
        libraryViewModel.ownershipFilter,
        libraryViewModel.stationFilter,
        libraryViewModel.sortOption
    ) {
        listState.scrollToItem(0)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                libraryViewModel.loadData(forceRefresh = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }
    var isCreatingPlaylist by remember { mutableStateOf(false) }

    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var folderToRename by remember { mutableStateOf<LibraryFolder?>(null) }
    var renameFolderName by remember { mutableStateOf("") }

    var folderToDelete by remember { mutableStateOf<LibraryFolder?>(null) }

    var selectedPlaylistForMenu by remember { mutableStateOf<Playlist?>(null) }
    var selectedFolderForMenu by remember { mutableStateOf<LibraryFolder?>(null) }

    var showMoveTargetSheet by remember { mutableStateOf(false) }
    var movingItemKey by remember { mutableStateOf<String?>(null) }
    var movingFolderId by remember { mutableStateOf<Long?>(null) }

    var playlistForDetails by remember { mutableStateOf<Playlist?>(null) }
    var showPlaylistDetailsSheet by remember { mutableStateOf(false) }
    var isFolderPlayMenuExpanded by remember { mutableStateOf(false) }

    var showLikedTracksMenu by remember { mutableStateOf(false) }
    var showDownloadsMenu by remember { mutableStateOf(false) }
    var showDeleteLikedTracksDialog by remember { mutableStateOf(false) }

    val likedTracks by LikeRepository.likedTracks.collectAsState()
    val downloadedIds by DownloadManager.downloadedIds.collectAsState()

    val isLikesDownloading = DownloadManager.isPlaylistDownloading(DownloadManager.LIKES_BATCH_ID)
    val downloadedLikesCount = remember(likedTracks, downloadedIds) {
        likedTracks.count { it.id < 0 || downloadedIds.contains(it.id) }
    }
    val isLikesFullyDownloaded = remember(likedTracks.size, downloadedLikesCount, isLikesDownloading) {
        if (likedTracks.isEmpty()) false
        else downloadedLikesCount == likedTracks.size || (downloadedLikesCount.toFloat() / likedTracks.size.toFloat() > 0.9f && !isLikesDownloading)
    }
    val itemMetas by libraryViewModel.allItemMetas.collectAsState()
    val isLikedPinned = itemMetas["liked_tracks"]?.isPinned ?: (!isGuest)
    val isDownloadsPinned = itemMetas["downloads"]?.isPinned ?: false

    val scope = rememberCoroutineScope()

    fun playPlaylistHelper(
        playlist: Playlist,
        shuffle: Boolean = false,
        insertNext: Boolean = false,
        addToQueue: Boolean = false,
        prepareBulkAdd: Boolean = false
    ) {
        scope.launch(Dispatchers.IO) {
            val tracks: List<Track> = if (!playlist.tracks.isNullOrEmpty()) {
                playlist.tracks!!
            } else {
                val local = AppDatabase.getDatabase(context).downloadDao().getTracksForPlaylistSync(playlist.id)
                if (local.isNotEmpty()) {
                    local.map {
                        Track(
                            id = it.id,
                            title = it.title,
                            user = User(0, it.artist, null),
                            artworkUrl = it.artworkUrl,
                            durationMs = it.duration
                        )
                    }
                } else if (playlist.id > 0) {
                    try {
                        val online = RetrofitClient.create(context).getPlaylist(playlist.id)
                        online.tracks ?: emptyList()
                    } catch (e: Exception) {
                        emptyList()
                    }
                } else emptyList()
            }

            if (tracks.isNotEmpty()) {
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    if (shuffle) {
                        playerViewModel.playPlaylist(tracks.shuffled(), 0)
                    } else if (insertNext) {
                        playerViewModel.insertNext(tracks)
                    } else if (addToQueue) {
                        playerViewModel.addToQueue(tracks)
                    } else if (prepareBulkAdd) {
                        playerViewModel.prepareBulkAdd(tracks)
                    } else {
                        playerViewModel.playPlaylist(tracks, 0)
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isCreatingPlaylist) showCreateDialog = false },
            title = { Text(stringResource(R.string.lib_create_playlist_title)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.lib_create_playlist_hint)) },
                    singleLine = true,
                    enabled = !isCreatingPlaylist
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newPlaylistName.isNotBlank() && !isCreatingPlaylist) {
                            val playlistName = newPlaylistName
                            isCreatingPlaylist = true
                            val targetFolderId = libraryViewModel.currentFolderId
                            scope.launch {
                                val id = DownloadManager.createUserPlaylist(playlistName)
                                if (id > 0) {
                                    val api = com.alananasss.kittytune.data.network.RetrofitClient.create(context)
                                    for (i in 0..15) {
                                        try {
                                            api.getPlaylist(id)
                                            break
                                        } catch (e: Exception) {
                                            kotlinx.coroutines.delay(1000)
                                        }
                                    }
                                }
                                val navId = if (id < 0) "local_playlist:$id" else id.toString()
                                if (targetFolderId != null) {
                                    val key = if (id < 0) "local_playlist:$id" else "playlist_$id"
                                    libraryViewModel.moveItemToFolder(key, targetFolderId)
                                }
                                libraryViewModel.loadData(forceRefresh = true)
                                isCreatingPlaylist = false
                                showCreateDialog = false
                                newPlaylistName = ""
                                onPlaylistClick(navId)
                            }
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !isCreatingPlaylist
                ) {
                    if (isCreatingPlaylist) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Text(stringResource(R.string.btn_create))
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showCreateDialog = false },
                    shapes = ButtonDefaults.shapes(),
                    enabled = !isCreatingPlaylist
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = {
                showCreateFolderDialog = false
                newFolderName = ""
            },
            title = { Text(stringResource(R.string.lib_create_folder_title)) },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text(stringResource(R.string.lib_create_folder_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            libraryViewModel.createFolder(
                                name = newFolderName,
                                parentFolderId = libraryViewModel.currentFolderId,
                                itemToMoveKey = movingItemKey,
                                folderToMoveId = movingFolderId
                            )
                            newFolderName = ""
                            movingItemKey = null
                            movingFolderId = null
                            showCreateFolderDialog = false
                            showMoveTargetSheet = false
                        }
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_create))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCreateFolderDialog = false
                        newFolderName = ""
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (folderToRename != null) {
        val folder = folderToRename!!
        LaunchedEffect(folder) {
            renameFolderName = folder.name
        }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text(stringResource(R.string.dialog_rename_folder_title)) },
            text = {
                OutlinedTextField(
                    value = renameFolderName,
                    onValueChange = { renameFolderName = it },
                    label = { Text(stringResource(R.string.dialog_rename_folder_hint)) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (renameFolderName.isNotBlank()) {
                            libraryViewModel.renameFolder(folder.id, renameFolderName)
                            folderToRename = null
                        }
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { folderToRename = null },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (folderToDelete != null) {
        val folder = folderToDelete!!
        AlertDialog(
            onDismissRequest = { folderToDelete = null },
            title = { Text(stringResource(R.string.dialog_delete_folder_title)) },
            text = { Text(stringResource(R.string.dialog_delete_folder_msg)) },
            confirmButton = {
                Button(
                    onClick = {
                        libraryViewModel.deleteFolder(folder)
                        folderToDelete = null
                    },
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.onError)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { folderToDelete = null },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (selectedPlaylistForMenu != null) {
        val playlist = selectedPlaylistForMenu!!
        val canonicalKey = LibraryItem.getPlaylistCanonicalKey(playlist)
        val isItemPinned = libraryViewModel.displayedItems.find { it.key == canonicalKey }?.isPinned ?: false
        val isInsideFolder = libraryViewModel.currentFolderId != null
        val permalink =
            playlist.permalinkUrl ?: if (playlist.id > 0) "https://soundcloud.com/playlists/${playlist.id}" else ""
        val likedPlaylistsRepo by com.alananasss.kittytune.data.LikeRepository.likedPlaylists.collectAsState()
        val isPlaylistLiked = remember(playlist.id, likedPlaylistsRepo) {
            com.alananasss.kittytune.data.LikeRepository.isPlaylistLiked(playlist.id)
        }

        val primaryColor = MaterialTheme.colorScheme.primary
        val actionItems = remember(playlist, isItemPinned, isInsideFolder, primaryColor, isPlaylistLiked) {
            mutableListOf<PlaylistActionItem>().apply {
                add(PlaylistActionItem(Icons.Rounded.PlayArrow, context.getString(R.string.btn_play)) {
                    playPlaylistHelper(playlist, shuffle = false)
                    selectedPlaylistForMenu = null
                })
                add(PlaylistActionItem(Icons.Default.Shuffle, context.getString(R.string.btn_shuffle)) {
                    playPlaylistHelper(playlist, shuffle = true)
                    selectedPlaylistForMenu = null
                })
                add(
                    PlaylistActionItem(
                        Icons.AutoMirrored.Rounded.PlaylistPlay,
                        context.getString(R.string.menu_play_next)
                    ) {
                        playPlaylistHelper(playlist, insertNext = true)
                        selectedPlaylistForMenu = null
                    })
                add(
                    PlaylistActionItem(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        context.getString(R.string.menu_add_queue)
                    ) {
                        playPlaylistHelper(playlist, addToQueue = true)
                        selectedPlaylistForMenu = null
                    })
                add(PlaylistActionItem(Icons.Default.Add, context.getString(R.string.menu_add_playlist)) {
                    playPlaylistHelper(playlist, prepareBulkAdd = true)
                    selectedPlaylistForMenu = null
                })
                if (playlist.id > 0 || playlist.urn?.startsWith("soundcloud:system-playlists:") == true) {
                    add(
                        PlaylistActionItem(
                            icon = if (isPlaylistLiked) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                            text = if (isPlaylistLiked) context.getString(R.string.action_unlike) else context.getString(
                                R.string.player_like_action
                            ),
                            tint = if (isPlaylistLiked) primaryColor else null
                        ) {
                            com.alananasss.kittytune.data.LikeRepository.togglePlaylistLike(
                                playlist.id,
                                !isPlaylistLiked,
                                playlist.permalinkUrl ?: "",
                                playlist.urn ?: ""
                            )
                        }
                    )
                    add(PlaylistActionItem(Icons.Rounded.Info, context.getString(R.string.menu_playlist_details)) {
                        val targetPlaylist = playlist
                        selectedPlaylistForMenu = null
                        playlistForDetails = targetPlaylist
                        showPlaylistDetailsSheet = true
                    })
                }
                if (!isInsideFolder) {
                    add(
                        PlaylistActionItem(
                            icon = Icons.Rounded.PushPin,
                            text = if (isItemPinned) context.getString(R.string.menu_unpin_playlist) else context.getString(
                                R.string.menu_pin_playlist
                            ),
                            tint = primaryColor
                        ) {
                            libraryViewModel.togglePinItem(canonicalKey)
                            selectedPlaylistForMenu = null
                        })
                }
                add(PlaylistActionItem(Icons.Rounded.Folder, context.getString(R.string.menu_move_to_folder)) {
                    movingItemKey = canonicalKey
                    movingFolderId = null
                    selectedPlaylistForMenu = null
                    showMoveTargetSheet = true
                })
                if (isInsideFolder) {
                    add(
                        PlaylistActionItem(
                            Icons.AutoMirrored.Rounded.DriveFileMove,
                            context.getString(R.string.menu_move_to_library)
                        ) {
                            libraryViewModel.moveItemToFolder(canonicalKey, null)
                            selectedPlaylistForMenu = null
                        })
                }
                if (permalink.isNotEmpty()) {
                    add(PlaylistActionItem(Icons.Outlined.Share, context.getString(R.string.btn_share)) {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, permalink)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.btn_share)))
                        selectedPlaylistForMenu = null
                    })
                }
            }
        }

        KittyModalBottomSheet(
            onDismissRequest = { selectedPlaylistForMenu = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    AsyncImage(
                        model = playlist.fullResArtwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = playlist.title ?: stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stringResource(R.string.lib_playlists)} • ${
                                playlist.user?.username ?: stringResource(
                                    R.string.me_artist
                                )
                            }",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(actionItems) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { item.onClick() }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.text,
                                modifier = Modifier.size(32.dp),
                                tint = item.tint ?: MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                color = item.tint ?: MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showPlaylistDetailsSheet && playlistForDetails != null) {
        val p = playlistForDetails!!
        val rawId = if (p.urn?.startsWith("soundcloud:system-playlists:") == true) {
            p.urn!!
        } else if (p.id < 0) {
            "local_playlist:${p.id}"
        } else {
            p.id.toString()
        }
        KittyModalBottomSheet(
            onDismissRequest = {
                showPlaylistDetailsSheet = false
                playlistForDetails = null
            },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            PlaylistDetailsSheet(
                playlistId = rawId,
                onDismiss = {
                    showPlaylistDetailsSheet = false
                    playlistForDetails = null
                },
                onViewAll = { tabIndex ->
                    showPlaylistDetailsSheet = false
                    playlistForDetails = null
                    onPlaylistClick("playlist_fans/${p.id}?tab=$tabIndex")
                },
                onNavigate = { id ->
                    showPlaylistDetailsSheet = false
                    playlistForDetails = null
                    onPlaylistClick(id)
                },
                onMentionClick = { username ->
                    showPlaylistDetailsSheet = false
                    playlistForDetails = null
                    playerViewModel.resolveAndNavigateToArtist(username)
                }
            )
        }
    }

    if (showDeleteLikedTracksDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteLikedTracksDialog = false },
            title = { Text(stringResource(R.string.dialog_remove_download_title)) },
            text = { Text(stringResource(R.string.dialog_remove_download_msg)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        DownloadManager.removeDownloads(likedTracks)
                        showDeleteLikedTracksDialog = false
                    },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteLikedTracksDialog = false },
                    shapes = ButtonDefaults.shapes()
                ) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showLikedTracksMenu) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val likedActionItems =
            remember(isLikedPinned, isLikesFullyDownloaded, isLikesDownloading, likedTracks, primaryColor) {
                mutableListOf<PlaylistActionItem>().apply {
                    add(
                        PlaylistActionItem(
                            icon = Icons.Rounded.PushPin,
                            text = if (isLikedPinned) context.getString(R.string.menu_unpin_playlist) else context.getString(
                                R.string.menu_pin_playlist
                            ),
                            tint = primaryColor
                        ) {
                            libraryViewModel.togglePinItem("liked_tracks", defaultPinned = !isGuest)
                            showLikedTracksMenu = false
                        })

                    val dlIcon =
                        if (isLikesFullyDownloaded) Icons.Rounded.Delete else if (isLikesDownloading) Icons.Rounded.Downloading else Icons.Rounded.Download
                    val dlText =
                        if (isLikesFullyDownloaded) context.getString(R.string.btn_delete) else context.getString(R.string.btn_download)
                    val dlTint = if (isLikesFullyDownloaded) Color(0xFFE53935) else null

                    add(
                        PlaylistActionItem(
                            icon = dlIcon,
                            text = dlText,
                            tint = dlTint
                        ) {
                            showLikedTracksMenu = false
                            if (isLikesFullyDownloaded) {
                                showDeleteLikedTracksDialog = true
                            } else if (isLikesDownloading) {
                                DownloadManager.cancelBatch(DownloadManager.LIKES_BATCH_ID)
                            } else {
                                DownloadManager.downloadBatch(likedTracks, DownloadManager.LIKES_BATCH_ID)
                            }
                        })
                }
            }

        KittyModalBottomSheet(
            onDismissRequest = { showLikedTracksMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.lib_liked_tracks),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        val likedUser = libraryViewModel.userProfile?.username
                            ?: if (isGuest) stringResource(R.string.lib_guest_mode) else "User"
                        Text(
                            text = "${stringResource(R.string.lib_playlists)} • $likedUser",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(likedActionItems) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { item.onClick() }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.text,
                                modifier = Modifier.size(32.dp),
                                tint = item.tint ?: MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                color = item.tint ?: MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDownloadsMenu) {
        val primaryColor = MaterialTheme.colorScheme.primary
        val downloadsActionItems = remember(isDownloadsPinned, primaryColor) {
            listOf(
                PlaylistActionItem(
                    icon = Icons.Rounded.PushPin,
                    text = if (isDownloadsPinned) context.getString(R.string.menu_unpin_playlist) else context.getString(
                        R.string.menu_pin_playlist
                    ),
                    tint = primaryColor
                ) {
                    libraryViewModel.togglePinItem("downloads", defaultPinned = false)
                    showDownloadsMenu = false
                }
            )
        }

        KittyModalBottomSheet(
            onDismissRequest = { showDownloadsMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                        .padding(horizontal = 8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.lib_downloads),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${stringResource(R.string.lib_playlists)} • ${stringResource(R.string.lib_downloads_subtitle)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(downloadsActionItems) { item ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { item.onClick() }
                                .padding(8.dp)
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.text,
                                modifier = Modifier.size(32.dp),
                                tint = item.tint ?: MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = item.text,
                                style = MaterialTheme.typography.labelMedium,
                                textAlign = TextAlign.Center,
                                color = item.tint ?: MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedFolderForMenu != null) {
        val folder = selectedFolderForMenu!!
        val isFolderPinned = folder.isPinned
        val isInsideFolder = libraryViewModel.currentFolderId != null
        val matchingFolderItem = libraryViewModel.displayedItems.filterIsInstance<LibraryItem.FolderItem>()
            .find { it.folder.id == folder.id }
        val plCount = matchingFolderItem?.playlistCount ?: 0
        val fCount = matchingFolderItem?.folderCount ?: 0
        val plText =
            if (plCount <= 1) stringResource(R.string.lib_folder_counts_playlist_singular, plCount) else stringResource(
                R.string.lib_folder_counts_playlist_plural,
                plCount
            )
        val fText = if (fCount <= 1) stringResource(
            R.string.lib_folder_counts_folder_singular,
            fCount
        ) else stringResource(R.string.lib_folder_counts_folder_plural, fCount)
        val subtitle =
            if (plCount == 0 && fCount == 0) stringResource(R.string.lib_folder_empty) else if (fCount > 0) "$plText, $fText" else plText

        ModalBottomSheet(
            onDismissRequest = { selectedFolderForMenu = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = folder.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (!isInsideFolder) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(if (isFolderPinned) R.string.menu_unpin_folder else R.string.menu_pin_folder))
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.PushPin,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier.clickable {
                            libraryViewModel.togglePinFolder(folder.id)
                            selectedFolderForMenu = null
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_move_to_folder)) },
                    leadingContent = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedFolderForMenu = null
                        movingFolderId = folder.id
                        movingItemKey = null
                        showMoveTargetSheet = true
                    }
                )

                if (isInsideFolder) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.menu_move_to_library)) },
                        leadingContent = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null) },
                        modifier = Modifier.clickable {
                            libraryViewModel.moveFolderToFolder(folder.id, null)
                            selectedFolderForMenu = null
                        }
                    )
                }

                ListItem(
                    headlineContent = { Text(stringResource(R.string.menu_rename_folder)) },
                    leadingContent = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    modifier = Modifier.clickable {
                        selectedFolderForMenu = null
                        folderToRename = folder
                    }
                )

                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.menu_delete_folder),
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                    },
                    modifier = Modifier.clickable {
                        selectedFolderForMenu = null
                        folderToDelete = folder
                    }
                )
            }
        }
    }

    if (showMoveTargetSheet) {
        val availableFolders = libraryViewModel.getAvailableTargetFolders(movingFolderId)
        val isInsideFolder = libraryViewModel.currentFolderId != null

        ModalBottomSheet(
            onDismissRequest = {
                showMoveTargetSheet = false
                movingItemKey = null
                movingFolderId = null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 32.dp)
            ) {
                Text(
                    text = stringResource(R.string.select_folder_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.lib_create_folder_title),
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    leadingContent = {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CreateNewFolder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        showMoveTargetSheet = false
                        showCreateFolderDialog = true
                    }
                )

                if (isInsideFolder) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.menu_move_to_library)) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.DriveFileMove, contentDescription = null)
                            }
                        },
                        modifier = Modifier.clickable {
                            if (movingItemKey != null) {
                                libraryViewModel.moveItemToFolder(movingItemKey!!, null)
                            } else if (movingFolderId != null) {
                                libraryViewModel.moveFolderToFolder(movingFolderId!!, null)
                            }
                            showMoveTargetSheet = false
                            movingItemKey = null
                            movingFolderId = null
                        }
                    )
                }

                availableFolders.forEach { targetFolder ->
                    ListItem(
                        headlineContent = { Text(targetFolder.name) },
                        leadingContent = {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        modifier = Modifier.clickable {
                            if (movingItemKey != null) {
                                libraryViewModel.moveItemToFolder(movingItemKey!!, targetFolder.id)
                            } else if (movingFolderId != null) {
                                libraryViewModel.moveFolderToFolder(movingFolderId!!, targetFolder.id)
                            }
                            showMoveTargetSheet = false
                            movingItemKey = null
                            movingFolderId = null
                        }
                    )
                }
            }
        }
    }

    val showLogin =
        libraryViewModel.userProfile == null && !libraryViewModel.isLoading && !libraryViewModel.isOfflineMode && !isGuest
    val isInsideFolder = libraryViewModel.currentFolder != null

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .statusBarsPadding()
                    .padding(top = 8.dp, bottom = 4.dp)
            ) {
                if (libraryViewModel.isOfflineMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .clickable { onPlaylistClick("downloads") }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.WifiOff,
                                null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = stringResource(R.string.lib_offline_mode),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.offline_banner_action_downloads),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (isGuest && !libraryViewModel.isOfflineMode) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .clickable { onLoginClick() }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Rounded.Person,
                            null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.lib_guest_mode),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                AnimatedContent(
                    targetState = libraryViewModel.currentFolder,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(
                                animationSpec = tween(
                                    280,
                                    easing = FastOutSlowInEasing
                                )
                            ) { width -> (width * 0.15f).toInt() } + fadeIn(animationSpec = tween(280)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(
                                            220,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) { width -> (-width * 0.15f).toInt() } + fadeOut(animationSpec = tween(180)))
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(
                                    280,
                                    easing = FastOutSlowInEasing
                                )
                            ) { width -> (-width * 0.15f).toInt() } + fadeIn(animationSpec = tween(280)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(
                                            220,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) { width -> (width * 0.15f).toInt() } + fadeOut(animationSpec = tween(180)))
                        }
                    },
                    label = "FolderTopBarTransition"
                ) { currentFolder ->
                    if (currentFolder != null) {
                        val username = libraryViewModel.userProfile?.username ?: stringResource(R.string.app_name)

                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { libraryViewModel.navigateUp() },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                                }
                                Text(
                                    text = currentFolder.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                )
                                IconButton(
                                    onClick = { selectedFolderForMenu = currentFolder },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Options")
                                }
                                IconButton(
                                    onClick = { isFabMenuExpanded = true },
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = "Add")
                                }
                                Box {
                                    val splitColors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    SplitButton(
                                        leadingButton = {
                                            SplitButtonDefaults.LeadingButton(
                                                onClick = {
                                                    libraryViewModel.playFolder(
                                                        currentFolder.id,
                                                        playerViewModel,
                                                        shuffle = false,
                                                        recursive = false
                                                    )
                                                },
                                                colors = splitColors
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = stringResource(R.string.lib_folder_play_ordered),
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        },
                                        trailingButton = {
                                            SplitButtonDefaults.TrailingButton(
                                                checked = isFolderPlayMenuExpanded,
                                                onCheckedChange = { isFolderPlayMenuExpanded = it },
                                                colors = splitColors
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Rounded.ArrowDropDown,
                                                    contentDescription = "Options",
                                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }
                                        }
                                    )

                                    DropdownMenu(
                                        expanded = isFolderPlayMenuExpanded,
                                        onDismissRequest = { isFolderPlayMenuExpanded = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lib_folder_play_ordered)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.PlayArrow,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                isFolderPlayMenuExpanded = false
                                                libraryViewModel.playFolder(
                                                    currentFolder.id,
                                                    playerViewModel,
                                                    shuffle = false,
                                                    recursive = false
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lib_folder_play_shuffle)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Shuffle,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                isFolderPlayMenuExpanded = false
                                                libraryViewModel.playFolder(
                                                    currentFolder.id,
                                                    playerViewModel,
                                                    shuffle = true,
                                                    recursive = false
                                                )
                                            }
                                        )
                                        HorizontalDivider()
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lib_folder_play_recursive_ordered)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Folder,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                isFolderPlayMenuExpanded = false
                                                libraryViewModel.playFolder(
                                                    currentFolder.id,
                                                    playerViewModel,
                                                    shuffle = false,
                                                    recursive = true
                                                )
                                            }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.lib_folder_play_recursive_shuffle)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Rounded.Shuffle,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            },
                                            onClick = {
                                                isFolderPlayMenuExpanded = false
                                                libraryViewModel.playFolder(
                                                    currentFolder.id,
                                                    playerViewModel,
                                                    shuffle = true,
                                                    recursive = true
                                                )
                                            }
                                        )
                                    }
                                }
                            }

                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.playlist_by_user, username),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        Column {
                            SearchBarHeader(
                                query = libraryViewModel.searchQuery,
                                onQueryChange = { libraryViewModel.searchQuery = it },
                                avatarUrl = libraryViewModel.userProfile?.avatarUrl,
                                onProfileClick = onProfileClick,
                                isGuest = isGuest
                            )

                            FilterChipsRow(libraryViewModel)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!showLogin || isGuest) {
                val bottomNavHeight = 90.dp
                val miniPlayerHeight = if (playerViewModel.currentTrack != null) 72.dp else 0.dp
                val totalBottomPadding = bottomNavHeight + miniPlayerHeight

                FloatingActionButtonMenu(
                    expanded = isFabMenuExpanded,
                    button = {
                        val initialFabColor = MaterialTheme.colorScheme.primaryContainer
                        val finalFabColor = MaterialTheme.colorScheme.secondaryContainer
                        val initialFabContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        val finalFabContentColor = MaterialTheme.colorScheme.onSecondaryContainer

                        ToggleFloatingActionButton(
                            checked = isFabMenuExpanded,
                            onCheckedChange = { isFabMenuExpanded = it },
                            containerColor = ToggleFloatingActionButtonDefaults.containerColor(
                                initialColor = initialFabColor,
                                finalColor = finalFabColor
                            )
                        ) {
                            val iconColor = androidx.compose.ui.graphics.lerp(
                                initialFabContentColor,
                                finalFabContentColor,
                                checkedProgress
                            )
                            val imageVector by remember {
                                derivedStateOf {
                                    if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add
                                }
                            }
                            Icon(
                                imageVector = imageVector,
                                contentDescription = if (isFabMenuExpanded) stringResource(R.string.btn_cancel) else stringResource(
                                    R.string.lib_create_playlist_title
                                ),
                                tint = iconColor,
                                modifier = Modifier.rotate(checkedProgress * 135f)
                            )
                        }
                    },
                    modifier = Modifier.padding(bottom = totalBottomPadding)
                ) {
                    FloatingActionButtonMenuItem(
                        onClick = {
                            isFabMenuExpanded = false
                            onImportClick()
                        },
                        text = { Text(stringResource(R.string.music_import_title)) },
                        icon = { Icon(Icons.Rounded.SwapHoriz, contentDescription = null) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            isFabMenuExpanded = false
                            showCreateFolderDialog = true
                        },
                        text = { Text(stringResource(R.string.lib_create_folder_title)) },
                        icon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) }
                    )
                    FloatingActionButtonMenuItem(
                        onClick = {
                            isFabMenuExpanded = false
                            showCreateDialog = true
                        },
                        text = { Text(stringResource(R.string.lib_create_playlist_title)) },
                        icon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = null) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (showLogin && !isGuest && !libraryViewModel.isOfflineMode) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = onLoginClick,
                            shapes = ButtonDefaults.shapes()
                        ) { Text(stringResource(R.string.login_soundcloud)) }
                    }
                }
            } else {
                AnimatedContent(
                    targetState = libraryViewModel.currentFolderId,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally(
                                animationSpec = tween(
                                    300,
                                    easing = FastOutSlowInEasing
                                )
                            ) { width -> (width * 0.25f).toInt() } + fadeIn(animationSpec = tween(300)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(
                                            240,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) { width -> (-width * 0.25f).toInt() } + fadeOut(animationSpec = tween(200)))
                        } else {
                            (slideInHorizontally(
                                animationSpec = tween(
                                    300,
                                    easing = FastOutSlowInEasing
                                )
                            ) { width -> (-width * 0.25f).toInt() } + fadeIn(animationSpec = tween(300)))
                                .togetherWith(
                                    slideOutHorizontally(
                                        animationSpec = tween(
                                            240,
                                            easing = FastOutSlowInEasing
                                        )
                                    ) { width -> (width * 0.25f).toInt() } + fadeOut(animationSpec = tween(200)))
                        }
                    },
                    label = "FolderContentTransition",
                    modifier = Modifier.fillMaxSize()
                ) { folderId ->
                    Column(modifier = Modifier.fillMaxSize()) {
                        SortAndLayoutControls(
                            viewModel = libraryViewModel,
                            onHistoryClick = onHistoryClick
                        )

                        if (libraryViewModel.isLoading && libraryViewModel.displayedItems.isEmpty() && folderId == null) {
                            LibraryShimmerGrid(isGridLayout = libraryViewModel.isGridLayout)
                        } else if (folderId != null && libraryViewModel.displayedItems.isEmpty()) {
                            EmptyFolderView()
                        } else {
                            LibraryContentGrid(
                                listState = listState,
                                viewModel = libraryViewModel,
                                isLikedPinned = isLikedPinned,
                                isDownloadsPinned = isDownloadsPinned,
                                onLikedTracksClick = onLikedTracksClick,
                                onLikedTracksLongClick = { showLikedTracksMenu = true },
                                onDownloadsClick = { onPlaylistClick("downloads") },
                                onDownloadsLongClick = { showDownloadsMenu = true },
                                onPlaylistClick = onPlaylistClick,
                                onArtistClick = { artistId -> onPlaylistClick("profile:$artistId") },
                                onPlaylistLongClick = { playlist -> selectedPlaylistForMenu = playlist },
                                onFolderLongClick = { folder -> selectedFolderForMenu = folder },
                                isGuest = isGuest
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = isFabMenuExpanded,
                enter = fadeIn(animationSpec = tween(200)),
                exit = fadeOut(animationSpec = tween(200))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            isFabMenuExpanded = false
                        }
                )
            }
        }
    }
}

@Composable
fun LibraryContentGrid(
    listState: LazyGridState,
    viewModel: LibraryViewModel,
    isLikedPinned: Boolean,
    isDownloadsPinned: Boolean,
    onLikedTracksClick: () -> Unit,
    onLikedTracksLongClick: () -> Unit,
    onDownloadsClick: () -> Unit,
    onDownloadsLongClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    onArtistClick: (Long) -> Unit,
    onPlaylistLongClick: (Playlist) -> Unit,
    onFolderLongClick: (LibraryFolder) -> Unit,
    isGuest: Boolean
) {
    val columns = if (viewModel.isGridLayout) GridCells.Fixed(3) else GridCells.Fixed(1)
    val isSyncing by viewModel.isSyncing.collectAsState()

    val shouldShowPlaylists =
        (viewModel.selectedFilter == null || viewModel.selectedFilter == LibraryFilter.PLAYLISTS) && viewModel.currentFolderId == null

    LazyVerticalGrid(
        state = listState,
        columns = columns,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (viewModel.isGridLayout) 16.dp else 8.dp)
    ) {
        if (shouldShowPlaylists) {
            item(span = { GridItemSpan(1) }, key = "liked_tracks") {
                Box(modifier = Modifier.animateItem()) {
                    val likedUser = viewModel.userProfile?.username
                        ?: if (isGuest) stringResource(R.string.lib_guest_mode) else "User"
                    val subtitle = if (isGuest) stringResource(R.string.lib_liked_subtitle_local)
                    else if (isSyncing) stringResource(R.string.lib_liked_subtitle_syncing)
                    else "${stringResource(R.string.lib_playlists)} • $likedUser"

                    StaticLibraryCard(
                        title = stringResource(R.string.lib_liked_tracks),
                        subtitle = subtitle,
                        icon = Icons.Rounded.Favorite,
                        isGrid = viewModel.isGridLayout,
                        onClick = onLikedTracksClick,
                        onLongClick = onLikedTracksLongClick,
                        isLoading = isSyncing,
                        isPinned = isLikedPinned
                    )
                }
            }

            item(span = { GridItemSpan(1) }, key = "downloads") {
                Box(modifier = Modifier.animateItem()) {
                    StaticLibraryCard(
                        title = stringResource(R.string.lib_downloads),
                        subtitle = "${stringResource(R.string.lib_playlists)} • ${stringResource(R.string.lib_downloads_subtitle)}",
                        icon = Icons.Rounded.Folder,
                        isGrid = viewModel.isGridLayout,
                        onClick = onDownloadsClick,
                        onLongClick = onDownloadsLongClick,
                        isLoading = false,
                        isPinned = isDownloadsPinned
                    )
                }
            }

            if (viewModel.showLocalMedia) {
                item(span = { GridItemSpan(1) }, key = "local_media") {
                    Box(modifier = Modifier.animateItem()) {
                        StaticLibraryCard(
                            title = stringResource(R.string.lib_local_media),
                            subtitle = "${stringResource(R.string.lib_playlists)} • ${stringResource(R.string.lib_local_media_subtitle)}",
                            icon = Icons.Default.SdStorage,
                            isGrid = viewModel.isGridLayout,
                            onClick = { onPlaylistClick("local_files") },
                            isLoading = false
                        )
                    }
                }
            }
        }

        items(
            items = viewModel.displayedItems,
            key = { item -> item.key }
        ) { item ->
            Box(modifier = Modifier.animateItem()) {
                when (item) {
                    is LibraryItem.FolderItem -> {
                        FolderLibraryCard(
                            folderItem = item,
                            isGrid = viewModel.isGridLayout,
                            isInsideFolder = viewModel.currentFolderId != null,
                            onClick = { viewModel.navigateToFolder(item.folder) },
                            onLongClick = { onFolderLongClick(item.folder) }
                        )
                    }

                    is LibraryItem.PlaylistItem -> {
                        val permalink = item.playlist.permalinkUrl
                        val isYoutubeShortcut = permalink != null && permalink.startsWith("yt_radio:")

                        val navId = if (isYoutubeShortcut) {
                            android.net.Uri.encode(permalink!!)
                        } else if (item.playlist.urn?.startsWith("soundcloud:system-playlists:") == true) {
                            "system_playlist:${item.playlist.urn}"
                        } else {
                            if (item.playlist.id < 0) "local_playlist:${item.playlist.id}" else item.playlist.id.toString()
                        }

                        DynamicPlaylistCard(
                            playlist = item.playlist,
                            isGrid = viewModel.isGridLayout,
                            isPinned = item.isPinned,
                            isInsideFolder = viewModel.currentFolderId != null,
                            onClick = { onPlaylistClick(navId) },
                            onLongClick = { onPlaylistLongClick(item.playlist) }
                        )
                    }

                    is LibraryItem.ArtistItem -> {
                        ArtistLibraryCard(
                            artist = item.artist,
                            isGrid = viewModel.isGridLayout,
                            onClick = { onArtistClick(item.artist.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryShimmerGrid(isGridLayout: Boolean) {
    val columns = if (isGridLayout) GridCells.Fixed(3) else GridCells.Fixed(1)
    LazyVerticalGrid(
        columns = columns,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 180.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(if (isGridLayout) 16.dp else 8.dp),
        userScrollEnabled = false
    ) {
        items(12) {
            if (isGridLayout) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    ShimmerBox(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(Modifier.height(6.dp))
                    ShimmerLine(modifier = Modifier.fillMaxWidth(0.85f).height(13.dp))
                    Spacer(Modifier.height(4.dp))
                    ShimmerLine(modifier = Modifier.fillMaxWidth(0.6f).height(11.dp))
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBox(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        ShimmerLine(modifier = Modifier.fillMaxWidth(0.65f).height(15.dp))
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerLine(modifier = Modifier.fillMaxWidth(0.45f).height(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchBarHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    avatarUrl: String?,
    onProfileClick: () -> Unit,
    isGuest: Boolean
) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        placeholder = { Text(stringResource(R.string.search_library_hint)) },
        leadingIcon = {
            Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_library_hint))
        },
        trailingIcon = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 6.dp)
            ) {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onProfileClick() }
                ) {
                    ArtistAvatar(
                        avatarUrl = if (isGuest) null else avatarUrl,
                        modifier = Modifier.size(32.dp).clip(CircleShape),
                        enableViewer = false
                    )
                }
            }
        },
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        singleLine = true
    )
}

@Composable
fun FilterChipsRow(viewModel: LibraryViewModel) {
    val filters = remember {
        listOf(
            LibraryFilter.PLAYLISTS,
            LibraryFilter.ALBUMS,
            LibraryFilter.ARTISTS,
            LibraryFilter.STATIONS
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        ExpressiveConnectedButtonGroup(
            options = filters,
            selectedOption = viewModel.selectedFilter,
            onOptionSelected = { filter ->
                viewModel.selectedFilter = if (viewModel.selectedFilter == filter) null else filter
            },
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            labelProvider = { filter ->
                Text(
                    text = stringResource(filter.stringRes),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        )
    }
}

@Composable
fun SortAndLayoutControls(
    viewModel: LibraryViewModel,
    onHistoryClick: () -> Unit = {}
) {
    val isRoot = viewModel.currentFolderId == null
    val view = androidx.compose.ui.platform.LocalView.current

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isRoot && viewModel.selectedFilter == LibraryFilter.STATIONS) {
            val filterText = when (viewModel.stationFilter) {
                StationFilter.ALL -> stringResource(R.string.filter_all)
                StationFilter.TRACKS -> stringResource(R.string.profile_tab_tracks)
                StationFilter.ARTISTS -> stringResource(R.string.lib_artists)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        viewModel.stationFilter = when (viewModel.stationFilter) {
                            StationFilter.ALL -> StationFilter.TRACKS
                            StationFilter.TRACKS -> StationFilter.ARTISTS
                            StationFilter.ARTISTS -> StationFilter.ALL
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = filterText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = filterText,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else if (isRoot && (viewModel.selectedFilter == null || viewModel.selectedFilter == LibraryFilter.PLAYLISTS || viewModel.selectedFilter == LibraryFilter.ALBUMS)) {
            val filterText = when (viewModel.ownershipFilter) {
                OwnershipFilter.ALL -> stringResource(R.string.filter_all)
                OwnershipFilter.CREATED -> stringResource(R.string.filter_created)
                OwnershipFilter.LIKED -> stringResource(R.string.filter_liked)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        viewModel.ownershipFilter = when (viewModel.ownershipFilter) {
                            OwnershipFilter.ALL -> OwnershipFilter.CREATED
                            OwnershipFilter.CREATED -> OwnershipFilter.LIKED
                            OwnershipFilter.LIKED -> OwnershipFilter.ALL
                        }
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = filterText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Rounded.FilterList,
                    contentDescription = filterText,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        viewModel.isSortDescending = !viewModel.isSortDescending
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lib_sort_recently_added),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (viewModel.isSortDescending) Icons.Rounded.ArrowDownward else Icons.Rounded.ArrowUpward,
                    contentDescription = stringResource(R.string.lib_sort_recently_added),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            if (viewModel.currentFolderId == null) {
                IconButton(
                    onClick = {
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        onHistoryClick()
                    },
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.History,
                        contentDescription = stringResource(R.string.history_title),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(
                onClick = {
                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    viewModel.isGridLayout = !viewModel.isGridLayout
                },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = if (viewModel.isGridLayout) Icons.Default.ViewList else Icons.Default.GridView,
                    contentDescription = stringResource(R.string.btn_options),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun EmptyFolderView() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.empty_folder_kaomoji),
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Text(
            text = stringResource(R.string.empty_folder_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.empty_folder_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun StaticLibraryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isGrid: Boolean,
    isLoading: Boolean,
    isPinned: Boolean = false,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    if (isGrid) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(32.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLoading) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isPinned) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (isLoading) {
                    Box(modifier = Modifier.align(Alignment.BottomCenter)) {
                        LinearWavyProgressIndicator(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.Transparent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPinned) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderLibraryCard(
    folderItem: LibraryItem.FolderItem,
    isGrid: Boolean,
    isInsideFolder: Boolean,
    onLongClick: () -> Unit = {},
    onClick: () -> Unit
) {
    val plCount = folderItem.playlistCount
    val fCount = folderItem.folderCount
    val plText = if (plCount <= 1) stringResource(
        R.string.lib_folder_counts_playlist_singular,
        plCount
    ) else stringResource(R.string.lib_folder_counts_playlist_plural, plCount)
    val fText = if (fCount <= 1) stringResource(
        R.string.lib_folder_counts_folder_singular,
        fCount
    ) else stringResource(R.string.lib_folder_counts_folder_plural, fCount)
    val finalSubtitle =
        if (plCount == 0 && fCount == 0) stringResource(R.string.lib_folder_empty) else if (fCount > 0) "$plText, $fText" else plText
    val showPin = folderItem.isPinned && !isInsideFolder

    if (isGrid) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = folderItem.folder.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPin) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = finalSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Folder,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = folderItem.folder.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showPin) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = finalSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DynamicPlaylistCard(
    playlist: Playlist,
    isGrid: Boolean,
    isPinned: Boolean = false,
    isInsideFolder: Boolean = false,
    modifier: Modifier = Modifier,
    onLongClick: () -> Unit = {},
    onOptionClick: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    val art = playlist.fullResArtwork
    val isRadioShortcut = playlist.permalinkUrl?.startsWith("yt_radio:") == true
    val typePrefix = if (isRadioShortcut) stringResource(R.string.radio) else stringResource(R.string.lib_playlists)
    val authorText = playlist.user?.username ?: stringResource(R.string.me_artist)
    val finalSubtitle = if (isRadioShortcut) "$typePrefix • YouTube" else "$typePrefix • $authorText"
    val showPin = isPinned && !isInsideFolder

    if (isGrid) {
        Column(
            modifier = modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .fillMaxWidth()
        ) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = playlist.title ?: stringResource(R.string.app_name),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (playlist.sharing == "private" || playlist.sharing == "secret") {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Private",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showPin) {
                    Icon(
                        imageVector = Icons.Rounded.PushPin,
                        contentDescription = "Pinned",
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                }
                Text(
                    text = finalSubtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    } else {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = playlist.title ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (playlist.sharing == "private" || playlist.sharing == "secret") {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Private",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showPin) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = "Pinned",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = finalSubtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (onOptionClick != null) {
                IconButton(
                    onClick = onOptionClick,
                    shapes = IconButtonDefaults.shapes()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = "Options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ArtistLibraryCard(artist: LocalArtist, isGrid: Boolean, onClick: () -> Unit) {
    if (isGrid) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            ArtistAvatar(
                avatarUrl = artist.avatarUrl,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = artist.username,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = stringResource(R.string.menu_go_artist),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistAvatar(
                avatarUrl = artist.avatarUrl,
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = artist.username,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.menu_go_artist),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
