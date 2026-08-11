package com.alananasss.kittytune.ui.musicimport

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.musicimport.MusicImportGraphQL.ExternalPlaylist
import com.alananasss.kittytune.ui.common.SettingsGroup
import com.alananasss.kittytune.ui.common.SettingsItem
import com.alananasss.kittytune.ui.common.SettingsScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicImportSelectionScreen(
    platformProviderName: String,
    onBackClick: () -> Unit,
    onStartTransfer: () -> Unit,
    viewModel: MusicImportSelectionViewModel = viewModel()
) {
    val context = LocalContext.current

    LaunchedEffect(platformProviderName) {
        viewModel.init(platformProviderName)
    }

    val platform = viewModel.platform

    SettingsScaffold(
        title = platform?.let { stringResource(it.labelRes()) }
            ?: stringResource(R.string.music_import_title),
        onBackClick = onBackClick,
        actions = {
            IconButton(
                onClick = {
                    viewModel.logout(onLoggedOut = onBackClick)
                },
                shapes = IconButtonDefaults.shapes()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Logout,
                    contentDescription = stringResource(R.string.btn_logout),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (viewModel.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 180.dp)
                ) {
                    if (viewModel.error != null) {
                        item {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = if (viewModel.retryAfter != null) stringResource(R.string.music_import_load_error) + " (Retry after ${viewModel.retryAfter}s) - ${viewModel.error}" else stringResource(R.string.music_import_load_error) + " - ${viewModel.error}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = { viewModel.loadExternalContent() },
                                    shapes = ButtonDefaults.shapes()
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    if (viewModel.likedTracksCount > 0) {
                        item {
                            SettingsGroup(
                                title = stringResource(R.string.music_import_likes_header),
                                items = listOf(
                                    { shape ->
                                        SettingsItem(
                                            shape = shape,
                                            title = stringResource(R.string.music_import_likes_title),
                                            subtitle = stringResource(R.string.music_import_likes_subtitle, viewModel.likedTracksCount),
                                            icon = Icons.Rounded.Favorite,
                                            hasSwitch = true,
                                            switchState = viewModel.includeLikes,
                                            onSwitchChange = { viewModel.toggleLikes() }
                                        )
                                    }
                                )
                            )
                        }
                    }

                    item {
                        SettingsGroup(
                            title = stringResource(R.string.music_import_playlists_header),
                            items = viewModel.playlists.map { playlist ->
                                { shape ->
                                    PlaylistSelectionItem(
                                        shape = shape,
                                        playlist = playlist,
                                        selected = playlist.id in viewModel.selectedPlaylistIds,
                                        onToggle = { viewModel.togglePlaylist(playlist.id) }
                                    )
                                }
                            }
                        )
                    }

                    if (viewModel.playlists.isEmpty() && viewModel.likedTracksCount == 0) {
                        item {
                            Text(
                                text = stringResource(R.string.music_import_empty),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(20.dp)
                            )
                        }
                    }
                }

                Button(
                    onClick = { viewModel.importSelected(onStartTransfer) },
                    enabled = viewModel.selectedPlaylistIds.isNotEmpty() || viewModel.includeLikes,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .height(52.dp)
                ) {
                    if (viewModel.isImporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.music_import_start),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistSelectionItem(
    shape: androidx.compose.ui.graphics.Shape,
    playlist: ExternalPlaylist,
    selected: Boolean,
    onToggle: () -> Unit
) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        shape = shape,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(46.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                if (playlist.imageUrl != null) {
                    AsyncImage(
                        model = playlist.imageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = stringResource(
                        R.string.music_import_playlist_count,
                        playlist.totalItems ?: 0
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Checkbox(
                checked = selected,
                onCheckedChange = { onToggle() }
            )
        }
    }
}
