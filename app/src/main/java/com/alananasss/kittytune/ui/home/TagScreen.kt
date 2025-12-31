package com.alananasss.kittytune.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.alananasss.kittytune.R
import com.alananasss.kittytune.data.DownloadManager
import com.alananasss.kittytune.ui.library.TrackListItem
import com.alananasss.kittytune.ui.player.PlayerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagScreen(
    tagName: String,
    onBackClick: () -> Unit,
    playerViewModel: PlayerViewModel,
    tagViewModel: TagViewModel = viewModel()
) {
    val context = LocalContext.current
    val downloadProgress by DownloadManager.downloadProgress.collectAsState()

    LaunchedEffect(tagName) {
        tagViewModel.loadTag(tagName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "#${tagName.uppercase()}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.btn_close))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (tagViewModel.uiState) {
                "LOADING" -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                "EMPTY" -> {
                    Text(stringResource(R.string.no_results), modifier = Modifier.align(Alignment.Center))
                }
                "ERROR" -> {
                    Text(stringResource(R.string.error_generic), modifier = Modifier.align(Alignment.Center))
                }
                "SUCCESS" -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 120.dp)
                    ) {
                        itemsIndexed(tagViewModel.tracks) { index, track ->
                            if (index >= tagViewModel.tracks.size - 3) {
                                LaunchedEffect(Unit) {
                                    tagViewModel.loadMore()
                                }
                            }

                            val progress = downloadProgress[track.id]
                            val isDownloaded = File(context.filesDir, "track_${track.id}.mp3").exists()

                            TrackListItem(
                                track = track,
                                currentlyPlayingTrack = playerViewModel.currentTrack,
                                index = index,
                                isDownloading = progress != null,
                                isDownloaded = isDownloaded,
                                downloadProgress = progress ?: 0,
                                onClick = {
                                    playerViewModel.playPlaylist(tagViewModel.tracks.toList(), index, context = null)
                                },
                                onOptionClick = { playerViewModel.showTrackOptions(track) }
                            )
                        }

                        item {
                            Spacer(Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }
}