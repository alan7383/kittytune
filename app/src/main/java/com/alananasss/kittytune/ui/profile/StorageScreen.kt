package com.alananasss.kittytune.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.MusicOff
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.data.local.LocalPlaylist
import com.alananasss.kittytune.data.local.LocalTrack
import com.alananasss.kittytune.ui.common.ExpressiveConnectedButtonGroup
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold

data class DeleteAction(val message: String, val action: () -> Unit)

@Composable
fun StorageScreen(
    onBackClick: () -> Unit,
    viewModel: StorageViewModel = viewModel(),
    onNavigateToPlaylist: (String) -> Unit = {},
    onNavigateToDownloads: (Int) -> Unit = {}
) {
    val context = LocalContext.current

    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }

    var showDeleteDialog by remember { mutableStateOf<DeleteAction?>(null) }

    if (showDeleteDialog != null) {
        val deleteAction = showDeleteDialog!!
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            icon = { Icon(Icons.Outlined.DeleteForever, null) },
            title = { Text(stringResource(R.string.dialog_clean_title)) },
            text = { Text(deleteAction.message) },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteAction.action()
                        showDeleteDialog = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text(stringResource(R.string.btn_cancel)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    SettingsScaffold(
        title = stringResource(R.string.pref_storage_title),
        onBackClick = onBackClick
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 180.dp)
        ) {
            // GAUGE (Kept separate as it's not a list item)
            item {
                Box(modifier = Modifier.padding(16.dp)) {
                    val appBytes = if (viewModel.totalAppSize > 0L) viewModel.totalAppSize
                        else (viewModel.appCodeSize + viewModel.audioSize + viewModel.imageSize + viewModel.cacheSize + viewModel.databaseSize)
                    DetailedStorageGauge(
                        usedSpaceBytes = viewModel.usedSpace,
                        totalSpaceBytes = viewModel.totalSpace,
                        freeSpaceBytes = viewModel.freeSpace,
                        appBytes = appBytes,
                        audioBytes = viewModel.audioSize,
                        formatSize = viewModel::formatSize
                    )
                }
            }

            // LOCATION
            item {
                SettingsGroup(
                    title = stringResource(R.string.storage_location),
                    items = listOf(
                        { shape ->
                            LocationSelectorItem(
                                shape = shape,
                                currentPath = viewModel.currentPath,
                                isExternal = viewModel.isExternal,
                                onChangeClick = { folderPicker.launch(null) },
                                onResetClick = { viewModel.resetToDefault() }
                            )
                        }
                    )
                )
            }

            // DOWNLOADS — desktop StorageDownloadsPage port:
            // "Musique téléchargée" (count + size, click to see tracks/playlists).
            item {
                val count = viewModel.downloadedCount
                val size = viewModel.formatSize(viewModel.audioSize)
                val playlistCount = viewModel.downloadedPlaylists.size
                SettingsGroup(
                    title = stringResource(R.string.storage_group_downloads),
                    items = listOf(
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.storage_downloaded_music),
                                subtitle = stringResource(R.string.storage_downloaded_summary, count, size),
                                icon = Icons.Rounded.DownloadDone,
                                onClick = { onNavigateToDownloads(0) }
                            )
                        },
                        { shape ->
                            SettingsItem(
                                shape = shape,
                                title = stringResource(R.string.storage_tab_playlists),
                                subtitle = stringResource(
                                    R.string.storage_downloaded_playlists_summary,
                                    playlistCount
                                ),
                                icon = Icons.Rounded.Folder,
                                onClick = { onNavigateToDownloads(1) }
                            )
                        }
                    )
                )
            }

            // DETAILS & CLEANUP (Éléments nettoyables / supprimables)
            item {
                val appStorageTotal = if (viewModel.totalAppSize > 0L) {
                    viewModel.totalAppSize
                } else {
                    (viewModel.audioSize + viewModel.imageSize + viewModel.cacheSize + viewModel.appCodeSize + viewModel.databaseSize).coerceAtLeast(1L)
                }
                SettingsGroup(
                    title = stringResource(R.string.menu_details),
                    items = listOf(
                        // 1. MUSIQUE TÉLÉCHARGÉE
                        { shape ->
                            StorageItemRow(
                                shape = shape,
                                icon = Icons.Outlined.MusicNote,
                                title = stringResource(R.string.storage_cat_audio),
                                size = viewModel.audioSize,
                                totalBytes = appStorageTotal,
                                formatSize = viewModel::formatSize,
                                isDeletable = viewModel.downloadedCount > 0,
                                // Card opens the dedicated downloads screen, trash deletes.
                                onClick = { onNavigateToDownloads(0) },
                                onDeleteClick = {
                                    showDeleteDialog = DeleteAction(
                                        context.getString(
                                            R.string.storage_delete_all_confirm,
                                            viewModel.downloadedCount
                                        ),
                                        { viewModel.cleanAudio() }
                                    )
                                }
                            )
                        },
                        // 2. IMAGES & POCHETTES
                        { shape ->
                            StorageItemRow(
                                shape = shape,
                                icon = Icons.Outlined.Image,
                                title = stringResource(R.string.storage_cat_images),
                                size = viewModel.imageSize,
                                totalBytes = appStorageTotal,
                                formatSize = viewModel::formatSize,
                                isDeletable = viewModel.imageSize > 0L,
                                onClick = {
                                    showDeleteDialog = DeleteAction(
                                        context.getString(R.string.dialog_clean_images_msg),
                                        { viewModel.cleanImages() }
                                    )
                                }
                            )
                        },
                        // 3. CACHE DE L'APPLICATION
                        { shape ->
                            StorageItemRow(
                                shape = shape,
                                icon = Icons.Outlined.Cached,
                                title = stringResource(R.string.storage_cat_cache),
                                size = viewModel.cacheSize,
                                totalBytes = appStorageTotal,
                                formatSize = viewModel::formatSize,
                                isDeletable = viewModel.cacheSize > 0L,
                                onClick = {
                                    showDeleteDialog = DeleteAction(
                                        context.getString(R.string.dialog_clean_cache_msg),
                                        { viewModel.cleanCache() }
                                    )
                                }
                            )
                        }
                    )
                )
            }

            // SYSTEM (Éléments NON supprimables — placés tout en bas comme dans Settings APK)
            item {
                val appStorageTotal = if (viewModel.totalAppSize > 0L) {
                    viewModel.totalAppSize
                } else {
                    (viewModel.audioSize + viewModel.imageSize + viewModel.cacheSize + viewModel.appCodeSize + viewModel.databaseSize).coerceAtLeast(1L)
                }
                SettingsGroup(
                    title = stringResource(R.string.storage_system_label),
                    items = listOf(
                        // 1. APPLICATION (Code APK et bibliothèques natives)
                        { shape ->
                            StorageItemRow(
                                shape = shape,
                                icon = Icons.Outlined.Android,
                                title = stringResource(R.string.storage_cat_app),
                                size = viewModel.appCodeSize,
                                totalBytes = appStorageTotal,
                                formatSize = viewModel::formatSize,
                                isDeletable = false
                            )
                        },
                        // 2. BASE DE DONNÉES & INDEX
                        { shape ->
                            StorageItemRow(
                                shape = shape,
                                icon = Icons.Outlined.Storage,
                                title = stringResource(R.string.storage_cat_db),
                                size = viewModel.databaseSize,
                                totalBytes = appStorageTotal,
                                formatSize = viewModel::formatSize,
                                isDeletable = false
                            )
                        }
                    )
                )
            }
        }
    }
}

enum class DownloadsCategory {
    Tracks,
    Playlists
}

sealed class StorageDeletionConfirmation {
    data class Track(val track: LocalTrack) : StorageDeletionConfirmation()
    data class Playlist(val playlist: LocalPlaylist) : StorageDeletionConfirmation()
    data class BulkTracks(val ids: Set<Long>, val totalBytes: Long) : StorageDeletionConfirmation()
    data class BulkPlaylists(val ids: Set<Long>, val totalBytes: Long) : StorageDeletionConfirmation()
}

/**
 * Dedicated Downloads Management Screen with M3 Expressive Connected Button Groups,
 * live search filtering, bulk selection, total freed size calculations, and confirmation dialogs.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StorageDownloadsScreen(
    viewModel: StorageViewModel = viewModel(),
    initialTab: Int = 0,
    onBackClick: () -> Unit,
    onNavigateToPlaylist: (String) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var currentCategory by remember(initialTab) {
        mutableStateOf(if (initialTab == 1) DownloadsCategory.Playlists else DownloadsCategory.Tracks)
    }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    var selectedTrackIds by remember { mutableStateOf(emptySet<Long>()) }
    var selectedPlaylistIds by remember { mutableStateOf(emptySet<Long>()) }
    var pendingDeletion by remember { mutableStateOf<StorageDeletionConfirmation?>(null) }

    val tracks = viewModel.downloadedTracks
    val playlists = viewModel.downloadedPlaylists
    val counts = viewModel.playlistDownloadedCounts
    val sizes = viewModel.playlistSizes

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else {
            val q = searchQuery.trim().lowercase()
            tracks.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
    }

    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists
        else {
            val q = searchQuery.trim().lowercase()
            playlists.filter { it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) }
        }
    }

    val isTracksTab = currentCategory == DownloadsCategory.Tracks
    val selectedCount = if (isTracksTab) selectedTrackIds.size else selectedPlaylistIds.size
    val totalCount = if (isTracksTab) filteredTracks.size else filteredPlaylists.size
    val isAllSelected = totalCount > 0 && selectedCount == totalCount
    val selectedBytes = if (isTracksTab) {
        viewModel.getTracksTotalSize(selectedTrackIds)
    } else {
        viewModel.getPlaylistsTotalSize(selectedPlaylistIds)
    }

    // Confirmation dialog
    if (pendingDeletion != null) {
        val target = pendingDeletion!!
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            icon = {
                Icon(
                    Icons.Outlined.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = {
                Text(
                    text = when (target) {
                        is StorageDeletionConfirmation.Track -> stringResource(R.string.dialog_clean_title)
                        is StorageDeletionConfirmation.Playlist -> stringResource(R.string.dialog_clean_title)
                        is StorageDeletionConfirmation.BulkTracks -> stringResource(R.string.storage_delete_count, target.ids.size)
                        is StorageDeletionConfirmation.BulkPlaylists -> stringResource(R.string.storage_delete_count, target.ids.size)
                    }
                )
            },
            text = {
                Text(
                    text = when (target) {
                        is StorageDeletionConfirmation.Track -> stringResource(R.string.storage_delete_single_track_confirm, target.track.title)
                        is StorageDeletionConfirmation.Playlist -> stringResource(R.string.storage_delete_single_playlist_confirm, target.playlist.title)
                        is StorageDeletionConfirmation.BulkTracks -> stringResource(
                            R.string.storage_delete_selected_tracks_confirm,
                            target.ids.size,
                            viewModel.formatSize(target.totalBytes)
                        )
                        is StorageDeletionConfirmation.BulkPlaylists -> stringResource(
                            R.string.storage_delete_selected_playlists_confirm,
                            target.ids.size,
                            viewModel.formatSize(target.totalBytes)
                        )
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (target) {
                            is StorageDeletionConfirmation.Track -> {
                                viewModel.deleteTrack(target.track.id)
                                selectedTrackIds = selectedTrackIds - target.track.id
                            }
                            is StorageDeletionConfirmation.Playlist -> {
                                viewModel.deletePlaylist(target.playlist.id)
                                selectedPlaylistIds = selectedPlaylistIds - target.playlist.id
                            }
                            is StorageDeletionConfirmation.BulkTracks -> {
                                viewModel.deleteTracks(target.ids)
                                selectedTrackIds = emptySet()
                            }
                            is StorageDeletionConfirmation.BulkPlaylists -> {
                                viewModel.deletePlaylists(target.ids)
                                selectedPlaylistIds = emptySet()
                            }
                        }
                        pendingDeletion = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletion = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        )
    }

    SettingsScaffold(
        title = if (selectedCount > 0) {
            stringResource(R.string.storage_selected_format, selectedCount)
        } else {
            stringResource(R.string.storage_manage_downloads_title)
        },
        subtitle = if (selectedCount > 0) {
            stringResource(R.string.storage_freed_space_hint, viewModel.formatSize(selectedBytes))
        } else {
            null
        },
        onBackClick = {
            if (selectedCount > 0) {
                if (isTracksTab) selectedTrackIds = emptySet()
                else selectedPlaylistIds = emptySet()
            } else {
                onBackClick()
            }
        },
        actions = {
            // Search toggle
            IconButton(onClick = {
                isSearchVisible = !isSearchVisible
                if (!isSearchVisible) searchQuery = ""
            }) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = stringResource(R.string.storage_search_downloads_hint),
                    tint = if (isSearchVisible || searchQuery.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Select all / Deselect all
            if (totalCount > 0) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    if (isTracksTab) {
                        selectedTrackIds = if (isAllSelected) emptySet() else filteredTracks.map { it.id }.toSet()
                    } else {
                        selectedPlaylistIds = if (isAllSelected) emptySet() else filteredPlaylists.map { it.id }.toSet()
                    }
                }) {
                    Icon(
                        imageVector = if (isAllSelected) Icons.Outlined.Deselect else Icons.Outlined.SelectAll,
                        contentDescription = if (isAllSelected) stringResource(R.string.storage_deselect_all) else stringResource(R.string.storage_select_all),
                        tint = if (selectedCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
            Column(modifier = Modifier.fillMaxSize()) {
                // Expressive Connected Button Group (Tabs)
                ExpressiveConnectedButtonGroup(
                    options = listOf(DownloadsCategory.Tracks, DownloadsCategory.Playlists),
                    selectedOption = currentCategory,
                    onOptionSelected = { cat ->
                        currentCategory = cat
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    labelProvider = { cat ->
                        val label = when (cat) {
                            DownloadsCategory.Tracks -> "${stringResource(R.string.storage_tab_tracks)} (${tracks.size})"
                            DownloadsCategory.Playlists -> "${stringResource(R.string.storage_tab_playlists)} (${playlists.size})"
                        }
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    },
                    iconProvider = { cat ->
                        val icon = when (cat) {
                            DownloadsCategory.Tracks -> Icons.Outlined.MusicNote
                            DownloadsCategory.Playlists -> Icons.AutoMirrored.Rounded.QueueMusic
                        }
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                )

                // Search field (collapsible / animated)
                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                stringResource(R.string.storage_search_downloads_hint),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Outlined.Clear, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(20.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Transparent
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    )
                }

                // List content
                if (isTracksTab) {
                    if (filteredTracks.isEmpty()) {
                        if (searchQuery.isNotEmpty()) {
                            StorageEmptyState(
                                icon = Icons.Rounded.SearchOff,
                                title = stringResource(R.string.storage_no_search_results, searchQuery),
                                subtitle = stringResource(R.string.storage_search_downloads_hint),
                                onResetSearch = { searchQuery = "" }
                            )
                        } else {
                            StorageEmptyState(
                                icon = Icons.Rounded.MusicOff,
                                title = stringResource(R.string.storage_downloaded_empty),
                                subtitle = stringResource(R.string.storage_audio_subtitle, 0),
                                onActionClick = { onNavigateToPlaylist("playlist_detail/downloads") },
                                actionText = stringResource(R.string.lib_downloads)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredTracks, key = { it.id }) { track ->
                                val isSelected = track.id in selectedTrackIds
                                DownloadedTrackItem(
                                    track = track,
                                    sizeLabel = viewModel.trackSizeLabel(track),
                                    isSelected = isSelected,
                                    onToggleSelect = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTrackIds = if (isSelected) selectedTrackIds - track.id else selectedTrackIds + track.id
                                    },
                                    onDeleteClick = {
                                        pendingDeletion = StorageDeletionConfirmation.Track(track)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    if (filteredPlaylists.isEmpty()) {
                        if (searchQuery.isNotEmpty()) {
                            StorageEmptyState(
                                icon = Icons.Rounded.SearchOff,
                                title = stringResource(R.string.storage_no_search_results, searchQuery),
                                subtitle = stringResource(R.string.storage_search_downloads_hint),
                                onResetSearch = { searchQuery = "" }
                            )
                        } else {
                            StorageEmptyState(
                                icon = Icons.Rounded.Folder,
                                title = stringResource(R.string.storage_downloaded_empty),
                                subtitle = stringResource(R.string.storage_downloaded_playlists_summary, 0),
                                onActionClick = { onNavigateToPlaylist("playlist_detail/downloads") },
                                actionText = stringResource(R.string.lib_downloads)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredPlaylists, key = { it.id }) { playlist ->
                                val isSelected = playlist.id in selectedPlaylistIds
                                val dlCount = counts[playlist.id] ?: playlist.trackCount
                                val plSize = sizes[playlist.id] ?: 0L
                                val plSizeFormatted = if (plSize > 0L) viewModel.formatSize(plSize) else ""
                                DownloadedPlaylistItem(
                                    playlist = playlist,
                                    downloadedCount = dlCount,
                                    sizeFormatted = plSizeFormatted,
                                    isSelected = isSelected,
                                    isSelectionMode = selectedPlaylistIds.isNotEmpty(),
                                    onToggleSelect = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedPlaylistIds = if (isSelected) selectedPlaylistIds - playlist.id else selectedPlaylistIds + playlist.id
                                    },
                                    onClick = {
                                        onNavigateToPlaylist("playlist_detail/downloaded_section:${playlist.id}")
                                    },
                                    onDeleteClick = {
                                        pendingDeletion = StorageDeletionConfirmation.Playlist(playlist)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Gros bouton bas comme UpdateScreen, toujours visible : grisé quand rien de sélectionné.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        if (selectedCount == 0) return@Button
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        pendingDeletion = if (isTracksTab) {
                            StorageDeletionConfirmation.BulkTracks(selectedTrackIds, selectedBytes)
                        } else {
                            StorageDeletionConfirmation.BulkPlaylists(selectedPlaylistIds, selectedBytes)
                        }
                    },
                    enabled = selectedCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        Icons.Outlined.DeleteForever,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (selectedCount > 0 && selectedBytes > 0L) {
                            stringResource(R.string.storage_delete_count, selectedCount) +
                                " • " + stringResource(
                                R.string.storage_freed_space_hint,
                                viewModel.formatSize(selectedBytes)
                            )
                        } else if (selectedCount > 0) {
                            stringResource(R.string.storage_delete_count, selectedCount)
                        } else {
                            stringResource(R.string.btn_delete)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadedTrackItem(
    track: LocalTrack,
    sizeLabel: String,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        onClick = onToggleSelect,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = track.localArtworkPath.ifEmpty { track.artworkUrl },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = track.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = sizeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.btn_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun DownloadedPlaylistItem(
    playlist: LocalPlaylist,
    downloadedCount: Int,
    sizeFormatted: String,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onToggleSelect: () -> Unit,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        onClick = if (isSelectionMode) onToggleSelect else onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            else
                MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        shape = RoundedCornerShape(16.dp),
        border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggleSelect() },
                colors = CheckboxDefaults.colors(
                    checkedColor = MaterialTheme.colorScheme.primary,
                    uncheckedColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.width(8.dp))
            AsyncImage(
                model = playlist.localCoverPath ?: playlist.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = playlist.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                val tracksCountText = "$downloadedCount ${stringResource(R.string.storage_tab_tracks).lowercase()}"
                val subtitle = if (playlist.artist.isNotBlank() && playlist.artist != "SoundCloud") {
                    "$tracksCountText · ${playlist.artist}"
                } else {
                    tracksCountText
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (sizeFormatted.isNotBlank()) {
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = sizeFormatted,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Outlined.DeleteOutline,
                    contentDescription = stringResource(R.string.btn_delete),
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun StorageEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onResetSearch: (() -> Unit)? = null,
    onActionClick: (() -> Unit)? = null,
    actionText: String? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onResetSearch != null) {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onResetSearch) {
                Text(stringResource(R.string.storage_reset))
            }
        }
        if (onActionClick != null && actionText != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onActionClick) {
                Text(actionText)
            }
        }
    }
}

/**
 * Port exact de UsageProgressBarPreference + SettingsLibProgressBarStyle.Linear.Expressive
 * de com.android.settings (layout preference_usage_progress_bar_expressive.xml) :
 * - usage_summary : Espace utilisé sur l'appareil (grand chiffre gras + unité/label aligné sur la baseline)
 * - total_summary : Total capacité de l'appareil ("sur 128 Go") aligné à droite
 * - LinearProgressIndicator M3 Expressive : animPercent 0→100, trackCornerRadius arrondi, gap de 4dp
 * - bottom_summary : Résumé de l'empreinte KittyTune et de l'espace disponible
 */
@Composable
fun DetailedStorageGauge(
    usedSpaceBytes: Long,
    totalSpaceBytes: Long,
    freeSpaceBytes: Long,
    appBytes: Long,
    audioBytes: Long,
    formatSize: (Long) -> String
) {
    val totalBytes = if (totalSpaceBytes > 0L) totalSpaceBytes else (appBytes + freeSpaceBytes).coerceAtLeast(1L)
    val usedBytes = if (usedSpaceBytes > 0L) usedSpaceBytes else (totalBytes - freeSpaceBytes).coerceAtLeast(appBytes)

    // Animated progress de l'espace de stockage de l'appareil
    val targetPercent = ((usedBytes.toFloat() / totalBytes.toFloat()) * 100f).toInt().coerceIn(1, 100)
    val animPercent by animateIntAsState(
        targetValue = targetPercent,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "storagePercent"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Ligne usage_summary / total_summary
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            val usedText = formatSize(usedBytes)
            val numMatch = Regex("[\\d]+[.,٫]?[\\d]*").find(usedText)
            if (numMatch != null) {
                Text(
                    text = numMatch.value,
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = usedText.removeRange(numMatch.range).trim() + " " + stringResource(R.string.storage_used),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .weight(1f)
                        .alignByBaseline(),
                    maxLines = 1
                )
            } else {
                Text(
                    text = "$usedText ${stringResource(R.string.storage_used)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )
            }
            // total_summary : aligné à droite
            Text(
                text = stringResource(R.string.storage_total_summary, formatSize(totalBytes)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                modifier = Modifier.alignByBaseline(),
                maxLines = 1
            )
        }

        Spacer(Modifier.height(2.dp))

        // LinearProgressIndicator — port exact de SettingsLibProgressBarStyle.Linear.Expressive
        LinearProgressIndicator(
            progress = { animPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.secondaryContainer,
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            gapSize = 4.dp
        )

        Spacer(Modifier.height(4.dp))

        // bottom_summary : description claire de KittyTune et de l'espace libre
        val bottomText = if (audioBytes > 0L) {
            stringResource(
                R.string.storage_gauge_bottom_with_music,
                formatSize(appBytes),
                formatSize(audioBytes),
                formatSize(freeSpaceBytes)
            )
        } else {
            stringResource(
                R.string.storage_gauge_bottom_no_music,
                formatSize(appBytes),
                formatSize(freeSpaceBytes)
            )
        }
        Text(
            text = bottomText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LegendItem(color: Color, label: String, size: String, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(size, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun LocationSelectorItem(
    shape: Shape,
    currentPath: String,
    isExternal: Boolean,
    onChangeClick: () -> Unit,
    onResetClick: () -> Unit
) {
    Card(
        onClick = onChangeClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = shape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 72.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = if (isExternal) Icons.Rounded.SdStorage else Icons.Rounded.Folder

            // Pastille Monet uniforme comme SettingsItem
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = currentPath,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.storage_change_cta),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isExternal) {
                IconButton(onClick = onResetClick) {
                    Icon(Icons.Outlined.Restore, stringResource(R.string.storage_reset), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Icon(Icons.Outlined.FolderOpen, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            }
        }
    }
}

/**
 * Port exact de StorageItemPreference + layout storage_item.xml de com.android.settings :
 * - Icône à gauche (24dp)
 * - Ligne supérieure : Titre (16sp) à gauche, Taille (16sp) à droite
 * - Bouton de suppression corbeille (si supprimable)
 * - Ligne inférieure : LinearProgressIndicator M3 Expressive
 *   (trackThickness = 4dp, indicatorTrackGapSize = 4dp, trackCornerRadius = 4dp, trackColor = secondaryContainer)
 *   progress = animateFloatAsState(itemBytes / totalBytes)
 */
@Composable
fun StorageItemRow(
    shape: Shape,
    icon: ImageVector,
    title: String,
    size: Long,
    totalBytes: Long,
    formatSize: (Long) -> String,
    isDeletable: Boolean = false,
    onClick: () -> Unit = {},
    onDeleteClick: (() -> Unit)? = null
) {
    val animSize by animateFloatAsState(targetValue = size.toFloat(), label = "itemSize")
    val resolvedTotal = totalBytes.coerceAtLeast(1L)
    val targetProgress = if (size > 0L) {
        (size.toFloat() / resolvedTotal.toFloat()).coerceIn(0.008f, 1f)
    } else {
        0f
    }
    val animProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "itemProgress"
    )

    val deleteAction = onDeleteClick ?: onClick
    val cardClick: () -> Unit = if (isDeletable) onClick else ({ })

    Card(
        onClick = cardClick,
        enabled = true,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = shape,
        modifier = Modifier.fillMaxWidth().heightIn(min = 68.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                // Ligne supérieure : Titre et Taille (alignés sur la même ligne)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = formatSize(animSize.toLong()),
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 16.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )

                    if (isDeletable) {
                        Spacer(Modifier.width(4.dp))
                        IconButton(
                            onClick = deleteAction,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Outlined.DeleteForever,
                                contentDescription = stringResource(R.string.btn_delete),
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))

                // Mini LinearProgressIndicator M3 Expressive (port exact de storage_item.xml : height=4dp, gap=4dp)
                LinearProgressIndicator(
                    progress = { animProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
                    gapSize = 4.dp
                )
            }
        }
    }
}
